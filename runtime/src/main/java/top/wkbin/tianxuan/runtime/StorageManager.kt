package top.wkbin.tianxuan.runtime

import android.content.Context
import android.os.StatFs
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileSystemException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.core.common.result.AppError
import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.common.result.ErrorCode
import top.wkbin.tianxuan.runtime.proot.ProotMountLayout

/** 风险描述删除后果；READONLY 表示需要通过所属业务管理，而非文件批量删除。 */
enum class StorageRiskLevel { SAFE, CAUTION, DANGEROUS, READONLY }

data class StorageEntry(
    val id: String,
    val name: String,
    val detail: String = "",
    val bytes: Long,
    val cleanable: Boolean = false,
    val riskLevel: StorageRiskLevel = StorageRiskLevel.READONLY,
    val cleanupHint: String? = null,
    val path: String? = null,
    val subItems: List<StorageEntry> = emptyList(),
    val reclaimableBytes: Long = 0L,
)

data class StorageCategory(
    val id: String,
    val name: String,
    val description: String = "",
    val bytes: Long,
    val cleanable: Boolean = false,
    val riskLevel: StorageRiskLevel = StorageRiskLevel.READONLY,
    val cleanupHint: String? = null,
    val entries: List<StorageEntry> = emptyList(),
    val reclaimableBytes: Long = 0L,
)

data class StorageUsage(
    val availableBytes: Long = 0L,
    val safeCleanableBytes: Long = 0L,
    val cautionCleanableBytes: Long = 0L,
    val categories: List<StorageCategory> = emptyList(),
    val scannedAt: Long = System.currentTimeMillis(),
    val projectCleanableBytes: Long = 0L,
    val scanWarnings: List<String> = emptyList(),
    val excludedMountPointCount: Int = 0,
) {
    /** 文件逻辑大小，不等同于 Android 设置中的应用磁盘分配量。 */
    val totalManagedBytes: Long get() = categories.sumOf { it.bytes }
}

/**
 * 每个文件按最长路径规则只归属一次。扫描结果同时产生清理计划；执行不解析 UI ID 为路径，
 * 不递归删除目录，也不删除扫描后新增、修改或替换的文件。未知数据始终保留在所属类别。
 */
class StorageManager(
    private val context: Context,
    private val pathManager: RuntimePathManager,
    private val runtime: Lazy<LinuxRuntime>,
) {
    private enum class Cleanup { NONE, OLD_LOG, DEPENDENCY, PROJECT_CACHE }
    private data class Rule(
        val root: Path,
        val category: String,
        val name: String,
        val hint: String,
        val cleanup: Cleanup = Cleanup.NONE,
        val risk: StorageRiskLevel = StorageRiskLevel.READONLY,
    ) {
        val id: String get() = "$category:$root"
    }
    private data class Target(val path: Path, val size: Long, val modified: java.nio.file.attribute.FileTime, val key: Any?)
    private data class Plan(val rule: Rule, val targets: List<Target>)
    private val mutex = Mutex()
    private var plans: Map<String, Plan> = emptyMap()
    private var inspected = false
    private val categoryInfo = linkedMapOf(
        "projects" to ("Tệp dự án và cá nhân" to "Dự án, thư mục cá nhân và cache dự án; mã nguồn và tệp xuất do vùng làm việc quản lý"),
        "environment" to ("Linux và môi trường phát triển" to "Xem hệ thống, SDK và runtime chia sẻ theo bản phát hành; gỡ cài qua quản lý môi trường hoặc công cụ"),
        "plugins" to ("Dữ liệu plugin và model" to "Chương trình plugin, dữ liệu riêng tư và model; đặt lại hoặc gỡ cài đặt vui lòng dùng công cụ tương ứng"),
        "conversations" to ("Phiên và tệp đính kèm" to "Cơ sở dữ liệu phiên, tệp đính kèm và tệp sinh ra; khi xóa cần kiểm tra tham chiếu phiên"),
        "cache" to ("Cache và tài nguyên cài đặt" to "Cache phụ thuộc có thể dọn theo nhu cầu; gói nguồn cài đặt, bản tạm và sao lưu rollback được giữ riêng"),
        "app_data" to ("Ứng dụng và chẩn đoán" to "Cấu hình, Skills, log và dữ liệu khác; khuyến nghị dọn log lưu trữ hết hạn"),
    )

    suspend fun inspect(): StorageUsage = withContext(Dispatchers.IO) { mutex.withLock { scan() } }

    private suspend fun scan(): StorageUsage {
        inspected = false
        plans = emptyMap()
        val now = System.currentTimeMillis()
        val coroutine = currentCoroutineContext()
        val rules = buildRules().sortedByDescending { it.root.nameCount }
        val totals = mutableMapOf<Rule, Long>()
        val targets = mutableMapOf<Rule, MutableList<Target>>()
        val warnings = mutableListOf<String>()
        val seenKeys = mutableSetOf<Any>()
        val roots = managedRoots()
        val mountCandidates = children(pathManager.distrosDir).filter { it.isDirectory }
            .flatMap { ProotMountLayout.placeholderCandidates(pathManager.rootfsDir(it.name)) }.toSet()
        val excludedMountPoints = mutableSetOf<Path>()
        fun excludeMountPoint(path: Path): Boolean {
            if (path !in mountCandidates || !safePath(path)) return false
            val stat = runCatching { Os.lstat(path.toString()) }.getOrNull() ?: return false
            val excluded = ProotMountLayout.isRestrictedPlaceholder(
                path, mountCandidates, OsConstants.S_ISDIR(stat.st_mode), stat.st_mode and 0xFFF,
                stat.st_uid, android.os.Process.myUid(),
            )
            if (excluded) excludedMountPoints.add(path)
            return excluded
        }
        for (root in roots) {
            if (!safePath(root) || !Files.exists(root, NOFOLLOW_LINKS)) continue
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                    coroutine.ensureActive()
                    if (excludeMountPoint(dir)) return FileVisitResult.SKIP_SUBTREE
                    return FileVisitResult.CONTINUE
                }
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    coroutine.ensureActive()
                    if (!attrs.isRegularFile || attrs.isSymbolicLink) return FileVisitResult.CONTINUE
                    // 同一 inode 的硬链接只计一次，也不作为可释放文件（仍可能有其他链接）。
                    val key = attrs.fileKey()
                    if (key != null && !seenKeys.add(key)) return FileVisitResult.CONTINUE
                    val rule = rules.firstOrNull { file.startsWith(it.root) } ?: return FileVisitResult.CONTINUE
                    totals[rule] = (totals[rule] ?: 0L) + attrs.size()
                    if (eligible(rule, file, attrs, now) && singleLink(file)) {
                        targets.getOrPut(rule) { mutableListOf() }.add(Target(file, attrs.size(), attrs.lastModifiedTime(), key))
                    }
                    return FileVisitResult.CONTINUE
                }
                override fun visitFileFailed(file: Path, exc: IOException?): FileVisitResult {
                    if (excludeMountPoint(file)) return FileVisitResult.CONTINUE
                    if (warnings.size < 20) warnings.add(scanFailureDetails(file, exc))
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null && excludeMountPoint(dir)) return FileVisitResult.CONTINUE
                    if (exc != null && warnings.size < 20) warnings.add(scanFailureDetails(dir, exc))
                    return FileVisitResult.CONTINUE
                }
            })
        }
        val entries = totals.map { (rule, bytes) ->
            val reclaimable = targets[rule].orEmpty().sumOf { it.size }
            rule to StorageEntry(
                id = rule.id, name = rule.name, detail = rule.hint, bytes = bytes,
                cleanable = reclaimable > 0, riskLevel = rule.risk, cleanupHint = rule.hint,
                path = rule.root.toString(), reclaimableBytes = reclaimable,
            )
        }
        val categories = categoryInfo.map { (id, info) ->
            val children = entries.filter { it.first.category == id }.map { it.second }.sortedByDescending { it.bytes }
            val cleanable = children.filter { it.cleanable }
            StorageCategory(
                id = id, name = info.first, description = info.second, bytes = children.sumOf { it.bytes },
                cleanable = cleanable.isNotEmpty(),
                riskLevel = cleanable.maxByOrNull { it.riskLevel.ordinal }?.riskLevel ?: StorageRiskLevel.READONLY,
                cleanupHint = "Chỉ xử lý các tệp đã tồn tại và không thay đổi khi quét trong các mục có thể dọn dưới đây. Phụ thuộc cần tải lại, cache dự án cần tạo lại; môi trường đang dùng hãy dừng trước.",
                entries = children, reclaimableBytes = children.sumOf { it.reclaimableBytes },
            )
        }
        plans = targets.mapKeys { it.key.id }.mapValues { (id, files) -> Plan(rules.first { it.id == id }, files.toList()) }
        inspected = true
        return StorageUsage(
            availableBytes = availableBytes(),
            categories = categories, scannedAt = now, scanWarnings = warnings,
            excludedMountPointCount = excludedMountPoints.size,
            safeCleanableBytes = entries.filter { it.second.riskLevel == StorageRiskLevel.SAFE }.sumOf { it.second.reclaimableBytes },
            cautionCleanableBytes = entries.filter { it.second.riskLevel == StorageRiskLevel.CAUTION }.sumOf { it.second.reclaimableBytes },
            projectCleanableBytes = entries.filter { it.first.cleanup == Cleanup.PROJECT_CACHE }.sumOf { it.second.reclaimableBytes },
        )
    }

    private fun buildRules(): List<Rule> = buildList {
        fun addRule(file: File, category: String, name: String, hint: String, cleanup: Cleanup = Cleanup.NONE,
                    risk: StorageRiskLevel = StorageRiskLevel.READONLY) {
            add(Rule(storagePath(file), category, name, hint, cleanup, risk))
        }
        val preserve = "Do chức năng sở hữu quản lý; không trực tiếp xóa tệp hàng loạt"
        addRule(context.filesDir.parentFile!!, "app_data", "Dữ liệu riêng của ứng dụng", "Chứa tùy chọn, WebView, cấu hình sao lưu và dữ liệu khác, do chức năng sở hữu quản lý")
        addRule(context.filesDir, "app_data", "Cấu hình ứng dụng và các tệp khác", preserve)
        addRule(pathManager.baseDir, "app_data", "Siêu dữ liệu runtime và các tệp khác", preserve)
        addRule(context.getDatabasePath("tianxuan.db").parentFile!!, "conversations", "Cơ sở dữ liệu phiên và ứng dụng", "Xóa bản ghi qua quản lý phiên, giữ lại cơ sở dữ liệu và tệp WAL/SHM")
        runCatching { context.cacheDir }.getOrNull()?.let {
            addRule(it, "cache", "Nhập ứng dụng và cache hệ thống", "Có thể chứa tệp đang nhập, do chức năng sở hữu quản lý")
        }
        runCatching { context.codeCacheDir }.getOrNull()?.let {
            addRule(it, "app_data", "Cache mã ứng dụng", "Do Android quản lý")
        }
        addRule(pathManager.cacheDir, "cache", "Tải xuống và lưu trữ cài đặt offline", "Giữ lại tệp cài đặt ngoại tuyến và đang tải; quản lý cài đặt nên xác nhận hoàn tất rồi mới xóa")
        addRule(File(context.filesDir, "plugins"), "cache", "Gói nguồn plugin cục bộ", "Cài đặt lại và công thức plugin có thể phụ thuộc gói nguồn, vui lòng xử lý trong quản lý plugin")
        addRule(pathManager.stagingRootfsDir, "cache", "Lưu tạm cài đặt toàn cục", "Tất cả dữ liệu của giao dịch cài đặt, không tham gia dọn dẹp một chạm")
        addRule(pathManager.tmpDir, "app_data", "Tệp tạm khi chạy", "Có thể bị PRoot và tiến trình sử dụng, không tham gia dọn một chạm")
        val logHint = "Chỉ dọn nhật ký lưu trữ .log.gz / .log.số quá 7 ngày và chưa sửa đổi; giữ lại nhật ký hiện tại"
        addRule(pathManager.logsDir, "app_data", "Log chạy", logHint, Cleanup.OLD_LOG, StorageRiskLevel.SAFE)
        addRule(pathManager.attachmentsDir, "conversations", "Tệp đính kèm và tệp tạo của hội thoại", "Có thể là bản sao duy nhất; vui lòng đối chiếu trong hội thoại hoặc quản lý tệp trước khi xóa", risk = StorageRiskLevel.DANGEROUS)
        addRule(File(pathManager.attachmentsDir, "skills"), "app_data", "Skills tùy chỉnh", preserve)
        addRule(pathManager.workspaceDir, "projects", "Các tệp khác trong vùng làm việc", "Bao gồm tệp đã xuất và mục chưa nhận dạng; vui lòng quản lý trong workspace")
        children(pathManager.workspaceDir).filter { it.isDirectory }.forEach { project ->
            addRule(project, "projects", "Dự án ${project.name} · tệp", "Mã nguồn, phụ thuộc và sản phẩm; không tự xóa dựa trên tên build/dist/out/target")
            // 只识别有工程标志的专用缓存，不推断自定义构建输出路径。
            val gradle = File(project, "settings.gradle").isFile || File(project, "settings.gradle.kts").isFile ||
                File(project, "build.gradle").isFile || File(project, "build.gradle.kts").isFile
            if (gradle) addRule(File(project, ".gradle"), "projects", "Dự án ${project.name} · Cache Gradle",
                "Dọn cache .gradle của dự án; lần build sau sẽ tạo lại. Giữ lại build, dist, out, target và mã nguồn", Cleanup.PROJECT_CACHE, StorageRiskLevel.CAUTION)
            if (File(project, "pubspec.yaml").isFile) addRule(File(project, ".dart_tool"), "projects", "Dự án ${project.name} · Cache Dart",
                "Dọn .dart_tool; cần chạy lại pub get và build", Cleanup.PROJECT_CACHE, StorageRiskLevel.CAUTION)
        }
        children(pathManager.distrosDir).filter { it.isDirectory }.forEach { distro ->
            val id = distro.name
            val rfs = pathManager.rootfsDir(id)
            val home = pathManager.homeDir(id)
            val tianxuan = pathManager.tianxuanRootDir(id)
            addRule(distro, "environment", "$id · Hệ thống và tệp môi trường khác", "Bao gồm hệ thống Linux và phần mềm tự cài, dung lượng thay đổi theo sử dụng")
            addRule(home, "projects", "$id · Thư mục cá nhân /root", "Mount home bền vững độc lập; chứa tệp cá nhân, cấu hình và phụ thuộc chưa nhận diện")
            addRule(File(rfs, "root"), "environment", "$id · /root gốc trong image", "Thư mục này bị mount home độc lập đè lên, giữ dữ liệu gốc của image")
            addRule(File(rfs, "home"), "projects", "$id · Thư mục người dùng khác", preserve)
            addRule(pathManager.rootfsPreviousDir(id), "cache", "$id · Sao lưu rollback nâng cấp", "Xóa sẽ mất khả năng rollback; do transaction nâng cấp xác nhận và xử lý", risk = StorageRiskLevel.DANGEROUS)
            addRule(pathManager.stagingRootfsDir(id), "cache", "$id · Lưu tạm cài đặt", "Có thể đang cài đặt hoặc chờ khôi phục, không tham gia dọn một chạm")
            addRule(File(tianxuan, "imports"), "cache", "$id · Tài nguyên cài đặt plugin", "Có thể đang được công thức hoặc giao dịch cài đặt sử dụng, không tham gia dọn một chạm")
            addRule(File(tianxuan, "flutter-cache-arm64"), "cache", "$id · Tài nguyên cài đặt Flutter", "Do quản lý cài đặt xác nhận hoàn tất rồi xử lý")
            addRule(File(rfs, "tmp"), "app_data", "$id · Tệp tạm", "Dữ liệu chạy tiến trình, không tham gia dọn một chạm")
            addRule(File(rfs, "var/tmp"), "app_data", "$id · Tệp tạm bền vững", "Có thể cần giữ qua khởi động lại, không tham gia dọn dẹp một chạm")
            addRule(File(rfs, "var/log"), "app_data", "$id · Log hệ thống", logHint, Cleanup.OLD_LOG, StorageRiskLevel.SAFE)
            listOf(pathManager.tianxuanToolsDir(id), pathManager.tianxuanDataDir(id)).forEach { dir ->
                addRule(dir, "plugins", "$id · ${dir.name}", preserve)
                children(dir).forEach { plugin ->
                    addRule(plugin, "plugins", "$id · ${plugin.name} · ${if (dir.name == "data") "数据与模型" else "程序"}",
                        "Vui lòng gỡ cài hoặc reset qua chức năng plugin để duy trì nhất quán tham chiếu và registry")
                }
            }
            listOf(File(tianxuan, "toolchains"), pathManager.tianxuanRuntimesDir(id), File(tianxuan, "compat"), File(rfs, "opt")).forEach { dir ->
                children(dir).filter { it.name != "tianxuan" }.forEach { sdk ->
                    addRule(sdk, "environment", "$id · ${sdk.name}", "Bộ công cụ phát triển hoặc runtime chia sẻ; trước khi gỡ cần kiểm tra tham chiếu plugin và dự án")
                }
            }
            listOf(".rustup", ".nvm", ".sdkman", ".pyenv").forEach {
                addRule(File(home, it), "environment", "$id · $it", "Môi trường ngôn ngữ do người dùng cài, do trình quản lý phiên bản tương ứng quản lý")
            }
            addRule(File(home, ".gradle/wrapper/dists"), "environment", "$id · Bản phân phối Gradle Wrapper",
                "Chứa thành phần chạy đã giải nén và dấu cài đặt; quản lý theo phiên bản đầy đủ, không thể chỉ xóa các tệp cũ trong đó")
            addRule(File(home, ".gradle/caches"), "cache", "$id · Chuyển đổi Gradle và cache khác",
                "Một phần cache chứa siêu dữ liệu toàn vẹn, do Gradle quản lý; phụ thuộc tải xuống được liệt kê riêng")
            addRule(File(home, ".cache/yarn"), "cache", "$id · Cache Yarn",
                "Có thể chứa gói đã giải nén và dấu toàn vẹn, vui lòng quản lý qua Yarn")
            val dependencyHint = "Chỉ xóa các tệp cache quá 7 ngày và chưa sửa đổi; cần tải lại qua mạng, xây dựng ngoại tuyến có thể bị ảnh hưởng. Vui lòng dừng terminal, xây dựng và dịch vụ trước"
            // 仅归档/下载缓存；不清理 pub 全局激活、npm npx 环境、Go 解压源码或 pnpm store。
            listOf(
                ".gradle/caches/modules-2/files-2.1" to "Gradle tải phụ thuộc",
                ".cache/pip" to "Cache tải xuống Pip",
                ".npm/_cacache" to "Cache tải NPM",
                ".cargo/registry/cache" to "Cargo tải lưu trữ",
                "go/pkg/mod/cache/download" to "Cache tải Go",
            ).forEach { (relative, name) ->
                addRule(File(home, relative), "cache", "$id · $name", dependencyHint, Cleanup.DEPENDENCY, StorageRiskLevel.CAUTION)
            }
            addRule(File(rfs, "var/cache/apt/archives"), "cache", "$id · Gói phần mềm APT đã tải",
                dependencyHint, Cleanup.DEPENDENCY, StorageRiskLevel.CAUTION)
        }
    }

    private fun children(dir: File): List<File> = if (safePath(storagePath(dir))) dir.listFiles().orEmpty().filter {
        !Files.isSymbolicLink(it.toPath())
    } else emptyList()

    /** lstat inspects the entry itself, including dangling links, without following its target. */
    private fun scanFailureDetails(path: Path, error: IOException?): String = buildString {
        append("Chưa đọc đầy đủ:").append(path)
        append("\nLỗi:").append(error?.javaClass?.simpleName ?: "Lỗi đọc không xác định")
        (error as? FileSystemException)?.reason?.takeIf { it.isNotBlank() }?.let {
            append(" · ").append(it)
        }
        // Mirrors the useful metadata inspection in MTDataFilesProvider, not its SAF export.
        // No chmod: a scan must not alter guest permissions or expose app-private files.
        runCatching {
            val stat = Os.lstat(path.toString())
            append("\nQuyền:").append(Integer.toOctalString(stat.st_mode and 0xFFF))
            append(" · UID：").append(stat.st_uid).append(" · GID：").append(stat.st_gid)
            append(" · UID ứng dụng:").append(android.os.Process.myUid())
            if (OsConstants.S_ISLNK(stat.st_mode)) {
                append("\nMục tiêu liên kết:").append(Os.readlink(path.toString()))
            } else if (OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == android.os.Process.myUid()) {
                val needed = OsConstants.S_IRUSR or OsConstants.S_IXUSR
                if (stat.st_mode and needed != needed) {
                    append("\nQuyền sở hữu thư mục này thiếu quyền đọc hoặc truy cập; khác với cấp quyền lưu trữ chia sẻ.")
                }
            }
        }.onFailure {
            append("\nĐọc metadata thất bại:").append(it.javaClass.simpleName)
            it.message?.let { message -> append(" · ").append(message) }
        }
    }

    /** Resolve only the trusted Android app root alias, never an arbitrary child symlink. */
    private fun storagePath(file: File): Path {
        val appRoot = context.filesDir.parentFile!!
        val logicalRoot = appRoot.toPath().toAbsolutePath().normalize()
        val path = file.toPath().toAbsolutePath().normalize()
        return if (path.startsWith(logicalRoot)) appRoot.canonicalFile.toPath().resolve(logicalRoot.relativize(path)) else path
    }

    private fun managedRoots(): List<Path> {
        val paths = listOfNotNull(context.filesDir.parentFile, context.getDatabasePath("tianxuan.db").parentFile,
            runCatching { context.cacheDir }.getOrNull(), runCatching { context.codeCacheDir }.getOrNull())
            .map(::storagePath).distinct().sortedBy { it.nameCount }
        return paths.filter { path -> paths.none { it != path && path.startsWith(it) } }
    }

    /** 检查全部祖先，不能仅检查叶子链接；PRoot 内的绝对/相对链接均不遍历。 */
    private fun safePath(path: Path): Boolean {
        var cursor: Path? = path.toAbsolutePath().normalize()
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) return false
            cursor = cursor.parent
        }
        return true
    }

    private fun singleLink(path: Path): Boolean = try {
        (Files.getAttribute(path, "unix:nlink", NOFOLLOW_LINKS) as Number).toInt() == 1
    } catch (_: UnsupportedOperationException) {
        // Windows 单元测试文件系统不提供 unix 属性；Android/Linux 支持 nlink。
        System.getProperty("os.name").orEmpty().startsWith("Windows")
    } catch (_: IllegalArgumentException) {
        System.getProperty("os.name").orEmpty().startsWith("Windows")
    } catch (_: IOException) { false }

    private fun eligible(rule: Rule, path: Path, attrs: BasicFileAttributes, now: Long): Boolean {
        if (rule.cleanup == Cleanup.NONE || now - attrs.lastModifiedTime().toMillis() < RETENTION_MS) return false
        val relative = rule.root.relativize(path)
        if (relative.any { it.toString() in setOf("partial", ".git") }) return false
        val name = path.fileName.toString()
        if (name == "lock" || name.endsWith(".lock") || name.endsWith(".part") || name.endsWith(".lck")) return false
        return rule.cleanup != Cleanup.OLD_LOG || name.endsWith(".log.gz") || Regex(".*\\.log\\.[0-9]+(?:\\.gz)?").matches(name)
    }

    suspend fun quickSafeClean(): AppResult<Long> = clean { it.rule.risk == StorageRiskLevel.SAFE }

    /** 历史 API 名保留：只清理已识别项目缓存，绝不猜测 build/dist/out/target 的用途。 */
    suspend fun cleanProjectBuildArtifacts(projectName: String? = null): AppResult<Long> = clean(requireMatch = projectName != null) { plan ->
        plan.rule.cleanup == Cleanup.PROJECT_CACHE && (projectName == null ||
            plan.rule.root.parent == storagePath(pathManager.workspaceDir).resolve(projectName))
    }

    suspend fun clearCategory(categoryId: String): AppResult<Unit> =
        clean(requireMatch = true) { it.rule.category == categoryId }.map { }

    suspend fun clearEntry(categoryId: String, entryId: String): AppResult<Unit> =
        clean(requireMatch = true) { it.rule.category == categoryId && it.rule.id == entryId }.map { }

    suspend fun clearCache(): AppResult<Unit> = clearCategory("cache")

    private suspend fun clean(requireMatch: Boolean = false, select: (Plan) -> Boolean): AppResult<Long> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    check(inspected) { "Hãy làm mới mức chiếm dụng lưu trữ và xem phạm vi dọn trước" }
                    val selected = plans.values.filter(select)
                    check(!requireMatch || selected.isNotEmpty()) { "Mục này không thể dọn trực tiếp hoặc đã mất hiệu lực, hãy làm mới rồi thử lại" }
                    var released = 0L
                    var skipped = 0
                    var failed = 0
                    runtime.value.withStorageCleanup {
                        for (plan in selected) for (target in plan.targets) {
                            currentCoroutineContext().ensureActive()
                            try {
                                check(target.path.startsWith(plan.rule.root) && safePath(target.path)) { "Đường dẫn đã thay đổi" }
                                if (!Files.exists(target.path, NOFOLLOW_LINKS)) { skipped++; continue }
                                val attrs = Files.readAttributes(target.path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
                                if (!attrs.isRegularFile || attrs.isSymbolicLink || attrs.size() != target.size ||
                                    attrs.lastModifiedTime() != target.modified || attrs.fileKey() != target.key ||
                                    !singleLink(target.path) || !eligible(plan.rule, target.path, attrs, System.currentTimeMillis())) {
                                    skipped++
                                    continue
                                }
                                if (Files.deleteIfExists(target.path)) released += target.size
                            } catch (cancelled: CancellationException) { throw cancelled
                            } catch (_: Exception) { failed++ }
                        }
                    }
                    plans = emptyMap()
                    inspected = false
                    // 部分完成不能伪装成成功，保留明确结果供 UI 呈现。
                    if (failed > 0 || skipped > 0) AppResult.Failure(AppError(ErrorCode.IO,
                        "Đã xóa $released byte; bỏ qua $skipped tệp đã thay đổi hoặc không tồn tại, thất bại $failed tệp. Vui lòng làm mới để xem dung lượng còn lại"))
                    else AppResult.Success(released)
                } catch (cancelled: CancellationException) { throw cancelled
                } catch (error: Exception) {
                    AppResult.Failure(AppError(ErrorCode.IO, error.message ?: "Dọn thất bại", error))
                }
            }
        }

    fun hasEnoughSpace(requiredBytes: Long): Boolean = availableBytes() >= requiredBytes
    private fun availableBytes(): Long = try {
        StatFs(context.filesDir.absolutePath).availableBytes
    } catch (_: Exception) { context.filesDir.usableSpace }

    private companion object { const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000 }
}
