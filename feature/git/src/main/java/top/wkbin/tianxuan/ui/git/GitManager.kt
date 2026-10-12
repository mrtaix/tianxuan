package top.wkbin.tianxuan.ui.git

import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import java.io.File
import java.io.IOException
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.CreateBranchCommand
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ListBranchCommand
import org.eclipse.jgit.api.errors.CannotDeleteCurrentBranchException
import org.eclipse.jgit.api.errors.CheckoutConflictException
import org.eclipse.jgit.api.errors.InvalidRemoteException
import org.eclipse.jgit.api.errors.RefAlreadyExistsException
import org.eclipse.jgit.errors.RepositoryNotFoundException
import org.eclipse.jgit.errors.TransportException
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.lib.BranchConfig
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.lib.RepositoryBuilder
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import java.io.ByteArrayOutputStream
import org.eclipse.jgit.transport.RefSpec
import org.eclipse.jgit.transport.RemoteRefUpdate
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider

/** 当前路径不是 Git 仓库 */
class GitNotARepoException : IOException("NOT_A_GIT_REPO")

/** 操作结果：成功带摘要文案，失败带友好错误 */
sealed class GitOpResult {
    data class Ok(val message: String) : GitOpResult()
    data class Failed(val message: String) : GitOpResult()
}

/**
 * Git 操作服务（MGit 同款 JGit 技术栈）：
 * - 直接在宿主侧打开工作区目录的仓库，不依赖沙箱内安装 git；
 * - 显式注入空的 system/user 配置，规避 Android 上 user.home / /etc/gitconfig 缺失的问题；
 * - push/pull 走 HTTPS + Token（按 host 从 GitCredentialsStore 取）。
 */
class GitManager(
    private val credentialsStore: GitCredentialsStore,
    private val linuxRuntime: Lazy<LinuxRuntime>,
) {

    fun isRepository(hostPath: String): Boolean = runCatching {
        RepositoryBuilder().findGitDir(File(hostPath)).gitDir != null
    }.getOrDefault(false)

    /** 是否浅克隆（.git/shallow 存在）：浅克隆只有 depth 条提交，提交树/远程分支不完整 */
    fun isShallow(hostPath: String): Boolean = runCatching {
        val git = openRepository(hostPath)
        try {
            File(git.repository.directory, "shallow").isFile
        } finally {
            git.close()
        }
    }.getOrDefault(false)

    /**
     * 补全浅克隆历史（git fetch --unshallow）。
     * JGit 不支持 unshallow，走沙箱 CLI git（与「从 Git 导入」克隆同一执行通道）。
     * [linuxPath] 是项目在沙箱内的路径（/workspace/...）。
     */
    suspend fun unshallow(linuxPath: String, onProgress: (String) -> Unit): GitOpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = linuxRuntime.value.execute(
                    ShellCommand(
                        commandLine = "git -C ${shellEscape(linuxPath)} fetch --unshallow --progress origin",
                        timeoutMs = 10 * 60_000L,
                        onOutput = { line -> if (line.isNotBlank()) onProgress(line.trim()) },
                    ),
                )
                if (result.isSuccess) {
                    GitOpResult.Ok("Đã bổ sung đầy đủ lịch sử commit")
                } else {
                    val output = (result.stderr + "\n" + result.stdout).trim().takeLast(300)
                    GitOpResult.Failed(
                        "Bổ sung lịch sử thất bại:" + output.ifBlank { "Hãy xác nhận sandbox đã cài Git và mạng khả dụng (kho riêng tư cần cấu hình credential trong terminal trước)" },
                    )
                }
            }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
        }

    private fun shellEscape(value: String): String {
        val trimmed = value.trim()
        // POSIX sh：单引号包裹 + 内部单引号拆成 '\''，任意路径（含 $、`、"、空格）都安全。
        // 此前双引号只处理空格，不转义 $/`/"——特殊项目名可向沙箱 shell 注入命令（CWE-78）。
        if (trimmed.isEmpty()) return "''"
        return "'" + trimmed.replace("'", "'\\''") + "'"
    }

    // ------------------------------------------------------------------
    // 提交（改动文件 → 暂存 → commit）
    // ------------------------------------------------------------------

    /** 单个文件的改动类型 */
    enum class ChangeType { ADDED, MODIFIED, DELETED, UNTRACKED, CONFLICT }

    data class GitFileChange(
        val path: String,
        val changeType: ChangeType,
        val staged: Boolean,
    )

    /** 工作区全部改动（暂存区 + 未暂存 + 未跟踪），按路径排序 */
    suspend fun listChanges(hostPath: String): List<GitFileChange> = withContext(Dispatchers.IO) {
        withGit(hostPath) { git ->
            val status = git.status().call()
            val merged = mutableListOf<GitFileChange>()
            fun put(paths: Set<String>, type: ChangeType, staged: Boolean) {
                paths.forEach { filePath ->
                    // 同一文件可能同时出现在多个集合（如 staged modified + 又有改动），
                    // 后加的覆盖先加的：保持"Trạng thái mới nhất"一条
                    merged.removeAll { it.path == filePath }
                    merged.add(GitFileChange(filePath, type, staged))
                }
            }
            put(status.conflicting.toSet(), ChangeType.CONFLICT, staged = true)
            put(status.added.toSet(), ChangeType.ADDED, staged = true)
            put(status.changed.toSet(), ChangeType.MODIFIED, staged = true)
            put(status.removed.toSet(), ChangeType.DELETED, staged = true)
            put(status.modified.toSet(), ChangeType.MODIFIED, staged = false)
            put(status.missing.toSet(), ChangeType.DELETED, staged = false)
            put(status.untracked.toSet(), ChangeType.UNTRACKED, staged = false)
            merged.sortedBy { it.path }
        }
    }

    /** 暂存 / 取消暂存单个文件 */
    suspend fun stageFile(hostPath: String, path: String, stage: Boolean): GitOpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                withGit(hostPath) { git ->
                    if (stage) {
                        git.add().addFilepattern(path).call()
                    } else {
                        git.reset().addPath(path).call()
                    }
                    GitOpResult.Ok("")
                }
            }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
        }

    /** 暂存全部改动（冲突文件除外，需手动解决） */
    suspend fun stageAll(hostPath: String): GitOpResult = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                git.add().addFilepattern(".").call()
                GitOpResult.Ok("")
            }
        }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
    }

    /** 提交暂存区；message 为空则失败（不产生空信息提交） */
    suspend fun commit(hostPath: String, message: String): GitOpResult = withContext(Dispatchers.IO) {
        runCatching {
            if (message.isBlank()) return@withContext GitOpResult.Failed("Thông tin commit không được rỗng")
            withGit(hostPath) { git ->
                ensureUserIdent(git.repository)
                val result = git.commit().setMessage(message.trim()).call()
                GitOpResult.Ok("Đã commit ${result.id.abbreviate(7).name()}: ${result.shortMessage}")
            }
        }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
    }

    /**
     * 生成供 AI 拟写 commit message 的改动摘要：
     * 文件类型清单 + 真实 unified diff 内容（单文件 1500 字符截断，总量 [maxTotalChars]）。
     */
    suspend fun buildDiffSummary(hostPath: String, maxTotalChars: Int = 8000): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val changes = listChanges(hostPath)
                if (changes.isEmpty()) return@withContext "(Không thay đổi)"
                val sb = StringBuilder()
                sb.append("Tệp thay đổi:\n")
                changes.forEach { c ->
                    sb.append("- [").append(
                        when (c.changeType) {
                            ChangeType.ADDED -> "Thêm mới"
                            ChangeType.MODIFIED -> "Sửa đổi"
                            ChangeType.DELETED -> "删除"
                            ChangeType.UNTRACKED -> "Chưa theo dõi"
                            ChangeType.CONFLICT -> "Xung đột"
                        },
                    ).append("] ").append(c.path).append('\n')
                }
                sb.append("\nDiff workspace (gồm thay đổi chưa stage):\n")
                sb.append(formatWorkingDiff(hostPath, maxTotalChars))
                sb.toString()
            }.getOrDefault("(Không thể sinh tóm tắt thay đổi)")
        }

    /** 工作区 unified diff 文本；单文件 1500 字符截断、总量 [maxTotalChars] 截断 */
    private fun formatWorkingDiff(hostPath: String, maxTotalChars: Int): String {
        withGit(hostPath) { git ->
            val sb = StringBuilder()
            val entries = runCatching { git.diff().call() }.getOrDefault(emptyList())
            for (entry in entries) {
                if (sb.length >= maxTotalChars) break
                val entryOut = ByteArrayOutputStream()
                DiffFormatter(entryOut).use { formatter ->
                    formatter.setRepository(git.repository)
                    formatter.setDetectRenames(true)
                    runCatching { formatter.format(entry) }
                }
                var chunk = entryOut.toString("UTF-8")
                if (chunk.length > 1500) chunk = chunk.take(1500) + "\n… (diff tệp này đã bị cắt)\n"
                sb.append(chunk)
            }
            return sb.toString().take(maxTotalChars)
        }
        return ""
    }

    /** 单个提交的完整信息（含 diff：与第一父提交比较；根提交与空树比较） */
    suspend fun getCommitDetail(hostPath: String, hash: String): String = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                val repo = git.repository
                val walk = RevWalk(repo)
                val commit = walk.parseCommit(repo.resolve(hash) ?: return@withGit "Commit không tồn tại")
                walk.use {
                    val sb = StringBuilder()
                    sb.append("commit ").append(commit.id.name()).append('\n')
                    sb.append("Author: ").append(commit.authorIdent.name)
                        .append(" <").append(commit.authorIdent.emailAddress).append(">\n")
                    sb.append("Date:   ").append(java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(java.util.Date(commit.commitTime * 1000L))).append("\n\n")
                    sb.append(commit.fullMessage.trim()).append("\n\n")
                    sb.append(formatCommitDiff(walk, repo, commit))
                    sb.toString().take(20_000)
                }
            }
        }.getOrDefault("Không thể đọc chi tiết commit")
    }

    private fun formatCommitDiff(walk: RevWalk, repo: Repository, commit: RevCommit): String {
        val parent = commit.parents.firstOrNull()
        val parentIter = CanonicalTreeParser().apply {
            val treeId = parent?.let { walk.parseCommit(it).tree } ?: emptyTreeId(repo)
            repo.newObjectReader().use { reader -> reset(reader, treeId) }
        }
        val commitIter = CanonicalTreeParser().apply {
            repo.newObjectReader().use { reader -> reset(reader, commit.tree) }
        }
        val out = ByteArrayOutputStream()
        DiffFormatter(out).use { formatter ->
            formatter.setRepository(repo)
            formatter.setDetectRenames(true)
            runCatching {
                formatter.format(parentIter, commitIter)
            }
        }
        return out.toString("UTF-8").take(16_000)
    }

    /** 空树 ObjectId：根提交与"Không có cha"比较时用（Git 的固定空树哈希） */
    private fun emptyTreeId(@Suppress("UNUSED_PARAMETER") repo: Repository): org.eclipse.jgit.lib.ObjectId =
        org.eclipse.jgit.lib.ObjectId.fromString("4b825dc642cb6eb9a060e54bf8d69288fbee4904")

    /** 取消全部暂存（index 重置到 HEAD，工作区文件不动） */
    suspend fun unstageAll(hostPath: String): GitOpResult = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                git.reset().call()
                GitOpResult.Ok("Đã hủy toàn bộ staging")
            }
        }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
    }

    /**
     * 丢弃单个文件的改动（危险操作）：
     * - 已跟踪文件：checkout 恢复到 HEAD 版本；
     * - 未跟踪文件：直接删除。
     */
    suspend fun discardFile(hostPath: String, path: String): GitOpResult = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                val changes = listChanges(hostPath)
                val change = changes.firstOrNull { it.path == path }
                    ?: return@withGit GitOpResult.Failed("Tệp không nằm trong danh sách thay đổi: $path")
                if (change.changeType == ChangeType.UNTRACKED) {
                    val target = File(File(hostPath).canonicalFile, path)
                    if (target.exists()) target.delete()
                    GitOpResult.Ok("Đã xóa tệp chưa theo dõi $path")
                } else {
                    git.checkout().addPath(path).call()
                    GitOpResult.Ok("Đã khôi phục $path")
                }
            }
        }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
    }

    /** 推送预览：当前分支领先远程的提交（哈希 + 主题），供确认对话框展示 */
    suspend fun pushPreview(hostPath: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                val repo = git.repository
                val headId = repo.resolve(Constants.HEAD) ?: return@withContext emptyList()
                val branch = repo.branch ?: return@withContext emptyList()
                val remoteId = BranchConfig(repo.config, branch).trackingBranch?.let { repo.resolve(it) }
                if (remoteId == null) {
                    // 无上游：整个分支都是待推送内容
                    RevWalk(repo).use { walk ->
                        walk.sort(RevSort.COMMIT_TIME_DESC)
                        walk.markStart(walk.parseCommit(headId))
                        walk.take(30).map { it.abbreviate(7).name() to it.shortMessage }
                    }
                } else {
                    RevWalk(repo).use { walk ->
                        walk.sort(RevSort.COMMIT_TIME_DESC)
                        walk.markStart(walk.parseCommit(headId))
                        walk.markUninteresting(walk.parseCommit(remoteId))
                        walk.take(30).map { it.abbreviate(7).name() to it.shortMessage }
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    // ------------------------------------------------------------------
    // 标签管理
    // ------------------------------------------------------------------

    data class GitTagInfo(val name: String, val commitId: String, val message: String?)

    suspend fun listTags(hostPath: String): List<GitTagInfo> = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                val repo = git.repository
                repo.refDatabase.getRefsByPrefix(Constants.R_TAGS)
                    .map { ref ->
                        val peeled = ref.peeledObjectId ?: ref.objectId
                        val message = runCatching {
                            RevWalk(repo).use { walk ->
                                (walk.parseTag(peeled)?.let { tag ->
                                    tag.fullMessage.take(200)
                                })
                            }
                        }.getOrNull()
                        GitTagInfo(
                            name = ref.name.removePrefix(Constants.R_TAGS),
                            commitId = peeled.name(),
                            message = message,
                        )
                    }
                    .sortedByDescending { it.name }
            }
        }.getOrDefault(emptyList())
    }

    /** 创建标签：[message] 非空为附注标签，否则轻量标签 */
    suspend fun createTag(hostPath: String, name: String, message: String): GitOpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                if (name.isBlank()) return@withContext GitOpResult.Failed("Tên nhãn không được rỗng")
                withGit(hostPath) { git ->
                    if (message.isBlank()) {
                        git.tag().setName(name.trim()).call()
                    } else {
                        git.tag().setName(name.trim()).setMessage(message.trim()).call()
                    }
                    GitOpResult.Ok("Đã tạo nhãn ${name.trim()}")
                }
            }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
        }

    suspend fun deleteTag(hostPath: String, name: String): GitOpResult = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                git.tagDelete().setTags(name).call()
                GitOpResult.Ok("Đã xóa nhãn $name")
            }
        }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
    }

    private fun openRepository(hostPath: String): Git {
        val dir = File(hostPath)
        val gitDir = RepositoryBuilder().findGitDir(dir).gitDir
            ?: throw GitNotARepoException()
        // Android 无 /etc/gitconfig 与常规 user.home：JGit 对不存在的系统/用户配置会容忍跳过，
        // 提交身份在 ensureUserIdent 中写入仓库本地 config 兜底。
        return Git(
            RepositoryBuilder()
                .setGitDir(gitDir)
                .setMustExist(true)
                .build(),
        )
    }

    private inline fun <T> withGit(hostPath: String, block: (Git) -> T): T {
        val git = openRepository(hostPath)
        try {
            return block(git)
        } finally {
            git.close()
        }
    }

    /** pull 产生合并提交时需要提交者身份：本地 config 缺失则补默认值 */
    private fun ensureUserIdent(repository: Repository) {
        val config = repository.config
        var dirty = false
        if (config.getString("user", null, "name") == null) {
            config.setString("user", null, "name", "TianXuan")
            dirty = true
        }
        if (config.getString("user", null, "email") == null) {
            config.setString("user", null, "email", "tianxuan@device.local")
            dirty = true
        }
        if (dirty) config.save()
    }

    // ------------------------------------------------------------------
    // 概览：当前分支 / 分支列表 / 远程地址 / 领先落后 / 工作区状态
    // ------------------------------------------------------------------

    suspend fun loadOverview(hostPath: String): GitOverview = withContext(Dispatchers.IO) {
        withGit(hostPath) { git ->
            val repo = git.repository
            val currentBranch = repo.branch ?: ""
            val detached = repo.findRef(Constants.HEAD)?.isSymbolic != true
            val headId = repo.resolve(Constants.HEAD)

            val allRefs = runCatching { git.branchList().setListMode(ListBranchCommand.ListMode.ALL).call() }
                .getOrDefault(emptyList())
            val local = mutableListOf<GitBranchInfo>()
            val remote = mutableListOf<GitBranchInfo>()
            for (ref: Ref in allRefs) {
                val commitId = ref.objectId?.name() ?: continue
                when {
                    ref.name.startsWith(Constants.R_HEADS) -> local += GitBranchInfo(
                        fullName = ref.name,
                        shortName = ref.name.removePrefix(Constants.R_HEADS),
                        isCurrent = !detached && ref.name == Constants.R_HEADS + currentBranch,
                        isRemote = false,
                        commitId = commitId,
                    )
                    ref.name.startsWith(Constants.R_REMOTES) -> remote += GitBranchInfo(
                        fullName = ref.name,
                        shortName = ref.name.removePrefix(Constants.R_REMOTES),
                        isCurrent = false,
                        isRemote = true,
                        commitId = commitId,
                    )
                }
            }

            val remoteUrl = runCatching {
                git.remoteList().call()
                    .firstOrNull { it.name == "origin" } ?: git.remoteList().call().firstOrNull()
            }.getOrNull()?.urIs?.firstOrNull()?.toString() ?: ""

            val status = runCatching { git.status().call() }.getOrNull()
            val uncommitted = status?.let {
                it.added.size + it.changed.size + it.removed.size + it.modified.size +
                    it.conflicting.size + it.missing.size
            } ?: 0

            var ahead = 0
            var behind = 0
            if (!detached && headId != null) {
                val tracking = BranchConfig(repo.config, currentBranch).trackingBranch
                val remoteId = tracking?.let { repo.resolve(it) }
                if (remoteId != null) {
                    RevWalk(repo).use { walk ->
                        walk.markStart(walk.parseCommit(headId))
                        walk.markUninteresting(walk.parseCommit(remoteId))
                        ahead = walk.count()
                    }
                    RevWalk(repo).use { walk ->
                        walk.markStart(walk.parseCommit(remoteId))
                        walk.markUninteresting(walk.parseCommit(headId))
                        behind = walk.count()
                    }
                }
            }

            GitOverview(
                currentBranch = if (detached) headId?.name()?.take(7) ?: "detached" else currentBranch,
                isDetached = detached,
                localBranches = local.sortedWith(compareByDescending<GitBranchInfo> { it.isCurrent }.thenBy { it.shortName }),
                remoteBranches = remote.sortedBy { it.shortName },
                remoteUrl = remoteUrl,
                aheadCount = ahead,
                behindCount = behind,
                uncommittedChanges = uncommitted,
                untrackedFiles = status?.untracked?.size ?: 0,
            )
        }
    }

    // ------------------------------------------------------------------
    // 提交记录树（MGit 风格泳道图）
    // ------------------------------------------------------------------

    suspend fun loadCommitGraph(hostPath: String, limit: Int = 300): List<GitCommitRow> =
        withContext(Dispatchers.IO) {
            withGit(hostPath) { git ->
                val repo = git.repository

                // 提交 → refs（分支/远程/标签）标注
                val labels = mutableMapOf<String, MutableList<GitRefLabel>>()
                fun putLabel(targetName: String, label: GitRefLabel) {
                    labels.getOrPut(targetName) { mutableListOf() } += label
                }
                repo.exactRef(Constants.HEAD)?.objectId?.let { putLabel(it.name(), GitRefLabel("HEAD", GitRefType.HEAD)) }
                runCatching { repo.refDatabase.getRefsByPrefix("refs/") }.getOrDefault(emptyList())
                    .forEach { ref ->
                        val target = ref.peeledObjectId ?: ref.objectId ?: return@forEach
                        val label = when {
                            ref.name.startsWith(Constants.R_HEADS) ->
                                GitRefLabel(ref.name.removePrefix(Constants.R_HEADS), GitRefType.BRANCH)
                            ref.name.startsWith(Constants.R_REMOTES) ->
                                GitRefLabel(ref.name.removePrefix(Constants.R_REMOTES), GitRefType.REMOTE)
                            ref.name.startsWith(Constants.R_TAGS) ->
                                GitRefLabel(ref.name.removePrefix(Constants.R_TAGS), GitRefType.TAG)
                            else -> null
                        }
                        if (label != null) putLabel(target.name(), label)
                    }

                val headId = repo.resolve(Constants.HEAD) ?: return@withGit emptyList()
                RevWalk(repo).use { walk ->
                    walk.sort(RevSort.TOPO)
                    walk.sort(RevSort.COMMIT_TIME_DESC)
                    walk.markStart(walk.parseCommit(headId))
                    // 所有本地分支也纳入起点，形成完整拓扑森林
                    runCatching { repo.refDatabase.getRefsByPrefix(Constants.R_HEADS) }
                        .getOrDefault(emptyList())
                        .forEach { ref -> ref.objectId?.let { walk.markStart(walk.parseCommit(it)) } }

                    val commits = mutableListOf<RevCommit>()
                    for (c in walk) {
                        commits += c
                        if (commits.size >= limit) break
                    }
                    buildGraphRows(commits, labels)
                }
            }
        }

    /** 泳道布局：经典 active-lane 算法，输出每行绘制几何信息（见 [GitCommitRow]）。 */
    private fun buildGraphRows(
        commits: List<RevCommit>,
        labels: Map<String, List<GitRefLabel>>,
    ): List<GitCommitRow> {
        val lanes = mutableListOf<RevCommit>()
        val rows = mutableListOf<GitCommitRow>()
        for (commit in commits) {
            if (commit !in lanes) lanes += commit
            val positions = mutableListOf<Int>()
            lanes.forEachIndexed { index, lane -> if (lane === commit) positions += index }
            val myLane = positions.first()
            val parents = commit.parents

            val preStraight = lanes.indices.filter { it !in positions }
            val mergeIn = positions.drop(1)

            // 收缩：多余泳道并入 myLane（合并点）
            mergeIn.sortedDescending().forEach { lanes.removeAt(it) }

            val parentLanes: List<Int>
            val postStraight: List<Int>
            if (parents.isEmpty()) {
                lanes.removeAt(myLane)
                parentLanes = emptyList()
                postStraight = lanes.indices.toList()
            } else {
                lanes[myLane] = parents[0]
                parents.drop(1).forEachIndexed { offset, parent ->
                    lanes.add(myLane + 1 + offset, parent)
                }
                parentLanes = (0 until parents.size).map { myLane + it }
                postStraight = lanes.indices.filter { it !in parentLanes }
            }

            rows += GitCommitRow(
                hash = commit.id.name(),
                shortHash = commit.abbreviate(7).name(),
                subject = commit.shortMessage,
                author = commit.authorIdent?.name.orEmpty(),
                commitTime = commit.commitTime * 1000L,
                refs = labels[commit.id.name()].orEmpty(),
                lane = myLane,
                preStraight = preStraight,
                mergeInPositions = mergeIn,
                parentLanes = parentLanes,
                postStraight = postStraight,
                laneCount = maxOf(lanes.size, myLane + 1),
            )
        }
        return rows
    }

    // ------------------------------------------------------------------
    // 分支操作
    // ------------------------------------------------------------------

    suspend fun checkout(hostPath: String, branch: GitBranchInfo): GitOpResult = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                val repo = git.repository
                if (branch.isRemote) {
                    // 远程分支：在本地创建同名跟踪分支（等价 git checkout <name> 的 DWIM 行为）
                    val localName = branch.shortName.substringAfter('/')
                    git.checkout()
                        .setCreateBranch(true)
                        .setName(localName)
                        .setStartPoint(branch.fullName)
                        .call()
                    repo.config.setString("branch", localName, "remote", "origin")
                    repo.config.setString("branch", localName, "merge", "refs/heads/$localName")
                    repo.config.save()
                    GitOpResult.Ok("Đã chuyển sang $localName (theo dõi ${branch.shortName})")
                } else {
                    git.checkout().setName(branch.shortName).call()
                    GitOpResult.Ok("Đã chuyển sang ${branch.shortName}")
                }
            }
        }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
    }

    suspend fun createBranch(hostPath: String, name: String, fromCurrent: Boolean): GitOpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                withGit(hostPath) { git ->
                    git.branchCreate()
                        .setName(name)
                        .setStartPoint(if (fromCurrent) Constants.HEAD else "origin/${git.repository.branch}")
                        .call()
                    GitOpResult.Ok("Đã tạo nhánh $name")
                }
            }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
        }

    suspend fun deleteBranch(hostPath: String, branch: GitBranchInfo): GitOpResult = withContext(Dispatchers.IO) {
        runCatching {
            withGit(hostPath) { git ->
                val results = git.branchDelete().setBranchNames(branch.fullName).call()
                if (results.isEmpty()) {
                    GitOpResult.Failed("Không thể xóa ${branch.shortName} (có thể chứa commit chưa gộp)")
                } else {
                    GitOpResult.Ok("Đã xóa nhánh ${branch.shortName}")
                }
            }
        }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
    }

    // ------------------------------------------------------------------
    // 推送 / 拉取（HTTPS + Token）
    // ------------------------------------------------------------------

    private suspend fun credentialsProviderFor(hostPath: String): Pair<String?, UsernamePasswordCredentialsProvider?> =
        withContext(Dispatchers.IO) {
            val remoteUrl = runCatching {
                withGit(hostPath) { git ->
                    git.remoteList().call().firstOrNull { it.name == "origin" }?.urIs?.firstOrNull()?.toString() ?: ""
                }
            }.getOrDefault("")
            if (remoteUrl.isBlank()) return@withContext null to null
            val host = GitCredentialsStore.extractHost(remoteUrl)
            val cred = credentialsStore.get(host)
            host to cred?.let { UsernamePasswordCredentialsProvider(it.first, it.second) }
        }

    suspend fun push(hostPath: String, onProgress: (GitProgress) -> Unit): GitOpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val (host, cp) = credentialsProviderFor(hostPath)
                withGit(hostPath) { git ->
                    val repo = git.repository
                    if (repo.findRef(Constants.HEAD)?.isSymbolic != true) {
                        return@withGit GitOpResult.Failed("Đang ở trạng thái detached HEAD, hãy chuyển về nhánh trước rồi push")
                    }
                    val branch = repo.branch ?: return@withGit GitOpResult.Failed("Không thể xác định nhánh hiện tại")
                    val results = git.push()
                        .setRemote("origin")
                        .setRefSpecs(RefSpec("refs/heads/$branch:refs/heads/$branch"))
                        .apply { cp?.let { setCredentialsProvider(it) } }
                        .setProgressMonitor(progressMonitor(onProgress))
                        .call()
                    val updates = results.flatMap { it.remoteUpdates }
                    val ok = updates.count { it.status == RemoteRefUpdate.Status.OK || it.status == RemoteRefUpdate.Status.UP_TO_DATE }
                    val rejected = updates.filter { it.status != RemoteRefUpdate.Status.OK && it.status != RemoteRefUpdate.Status.UP_TO_DATE }
                    when {
                        updates.isEmpty() -> GitOpResult.Ok("Push hoàn tất (không có thay đổi)")
                        rejected.isEmpty() -> GitOpResult.Ok("Đã push $branch → origin ($ok tham chiếu)")
                        else -> GitOpResult.Failed("Push bị từ chối: ${rejected.first().status}" + rejected.firstOrNull()?.message.orEmpty().let { if (it.isBlank()) "" else " · $it" })
                    }
                }
            }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
        }

    suspend fun pull(hostPath: String, onProgress: (GitProgress) -> Unit): GitOpResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val (host, cp) = credentialsProviderFor(hostPath)
                withGit(hostPath) { git ->
                    val repo = git.repository
                    ensureUserIdent(repo)
                    val branch = repo.branch ?: return@withGit GitOpResult.Failed("Không thể xác định nhánh hiện tại")
                    val tracking = BranchConfig(repo.config, branch).trackingBranch
                    val cmd = git.pull().setRemote("origin")
                    if (tracking == null) cmd.setRemoteBranchName(branch)
                    cp?.let { cmd.setCredentialsProvider(it) }
                    cmd.setProgressMonitor(progressMonitor(onProgress))
                    val result = cmd.call()

                    val merge = result.mergeResult
                    when (merge?.mergeStatus) {
                        org.eclipse.jgit.api.MergeResult.MergeStatus.ALREADY_UP_TO_DATE ->
                            GitOpResult.Ok("Đã là mới nhất, không cần kéo về")
                        org.eclipse.jgit.api.MergeResult.MergeStatus.FAST_FORWARD ->
                            GitOpResult.Ok("Đã pull và fast-forward tới ${merge.newHead?.name()?.take(7)}")
                        org.eclipse.jgit.api.MergeResult.MergeStatus.MERGED ->
                            GitOpResult.Ok("Đã kéo và gộp (tạo commit gộp)")
                        org.eclipse.jgit.api.MergeResult.MergeStatus.CONFLICTING ->
                            GitOpResult.Failed("Kéo xong nhưng có xung đột (${merge.conflicts.size} tệp), vui lòng giải quyết thủ công")
                        else -> GitOpResult.Ok("Pull hoàn tất")
                    }
                }
            }.getOrElse { GitOpResult.Failed(friendlyError(it)) }
        }

    // ------------------------------------------------------------------
    // 辅助
    // ------------------------------------------------------------------

    private fun progressMonitor(onProgress: (GitProgress) -> Unit): org.eclipse.jgit.lib.ProgressMonitor =
        object : org.eclipse.jgit.lib.ProgressMonitor {
            private var title = ""
            private var total = 0
            private var done = 0

            override fun start(totalTasks: Int) = Unit

            override fun beginTask(name: String, totalWork: Int) {
                title = name
                total = totalWork
                done = 0
                onProgress(GitProgress(name, 0, totalWork))
            }

            override fun update(completed: Int) {
                done += completed
                onProgress(GitProgress(title, done, total))
            }

            override fun endTask() = Unit

            override fun isCancelled(): Boolean = false

            override fun showDuration(enabled: Boolean) = Unit
        }

    companion object {
        fun friendlyError(e: Throwable): String = when (e) {
            is GitNotARepoException -> "NOT_A_GIT_REPO"
            is RepositoryNotFoundException -> "NOT_A_GIT_REPO"
            is CannotDeleteCurrentBranchException -> "Không thể xóa nhánh đang ở hiện tại, hãy chuyển sang nhánh khác trước"
            is CheckoutConflictException -> "Workspace có thay đổi chưa commit xung đột với nhánh mục tiêu, hãy commit hoặc stash trước"
            is RefAlreadyExistsException -> "Nhánh đã tồn tại"
            is InvalidRemoteException -> "Chưa cấu hình kho từ xa origin, không thể push/pull"
            is TransportException -> {
                val message = e.message.orEmpty()
                when {
                    message.contains("not authorized", true) || message.contains("Authentication", true) ->
                        "Xác thực thất bại: hãy cấu hình tên người dùng + Personal Access Token của host repo đó trong cài đặt thông tin đăng nhập"
                    message.contains("Support for password authentication was removed", true) ->
                        "GitHub đã ngừng xác thực mật khẩu: hãy dùng Personal Access Token làm mật khẩu"
                    message.contains("not found", true) ->
                        "Kho từ xa không tồn tại hoặc không có quyền truy cập (kho riêng cần cấu hình Token)"
                    else -> message.ifBlank { e.toString() }
                }
            }
            else -> e.message?.ifBlank { null } ?: e.toString()
        }
    }
}
