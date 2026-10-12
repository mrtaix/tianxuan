package top.wkbin.tianxuan.runtime

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import top.wkbin.tianxuan.core.common.files.SafeFileTree
import top.wkbin.tianxuan.core.common.result.AppError
import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.common.result.ErrorCode
import top.wkbin.tianxuan.core.database.WorkspaceRepository
import top.wkbin.tianxuan.core.database.WorkspaceEntity
import top.wkbin.tianxuan.template.ProjectTemplateEngine
import top.wkbin.tianxuan.template.TemplateProjectType
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

enum class ProjectType {
    ANDROID,
    FLUTTER,
    REVERSE,
    GENERAL;

    val displayName: String
        get() = when (this) {
            ANDROID -> "Android"
            FLUTTER -> "Flutter"
            REVERSE -> "Reverse APK"
            GENERAL -> "Chung"
        }
}

enum class ProjectImportSource {
    LOCAL_ARCHIVE,
    GITHUB,
    TEMPLATE,
}

enum class GitTransport {
    HTTP,
    SSH,
}

data class ProjectArchiveSource(
    val uri: String,
    val fileName: String,
)

enum class ProjectTemplate {
    EMPTY,
    APK_REVERSE,
    GIT_IMPORT;

    val displayName: String
        get() = when (this) {
            EMPTY -> "Dự án trống (Empty)"
            APK_REVERSE -> "Reverse APK"
            GIT_IMPORT -> "Nhập từ Git"
        }
}

/**
 * APK 逆向模板的安装包来源：
 * - [FromInstalledApp]：从本机已安装应用提取安装包（applicationInfo.sourceDir）；
 * - [FromFileUri]：通过系统文件管理器（SAF OpenDocument）选择 .apk 文件。
 */
sealed class ApkImportSource {
    data class FromInstalledApp(
        val packageName: String,
        val appLabel: String,
    ) : ApkImportSource()

    data class FromFileUri(
        val uri: String,
        val fileName: String,
    ) : ApkImportSource()

    val displayName: String
        get() = when (this) {
            is FromInstalledApp -> "$appLabel ($packageName)"
            is FromFileUri -> fileName
        }
}

data class WorkspaceProject(
    val name: String,
    val path: String,
    val linuxPath: String,
    val sizeBytes: Long,
    val ownsDirectory: Boolean = true,
    val projectType: ProjectType = ProjectType.GENERAL,
    val packageName: String = "",
)

enum class WorkspaceStorage { INTERNAL, SHARED }

data class WorkspaceFileItem(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val extension: String = "",
)

/** 工作区：目录在 App 私有挂载点，元数据（路径/创建时间）存 Room。 */
class WorkspaceManager(
    private val context: Context,
    private val pathManager: RuntimePathManager,
    private val workspaceDao: WorkspaceRepository,
    private val fileService: WorkspaceFileService,
    private val linuxRuntime: Lazy<LinuxRuntime>,
    private val projectTemplateEngine: ProjectTemplateEngine,
    private val buildScriptRepository: Lazy<top.wkbin.tianxuan.core.database.BuildScriptRepository>? = null,
) {
    constructor(
        pathManager: RuntimePathManager,
        workspaceDao: WorkspaceRepository,
        projectTemplateEngine: ProjectTemplateEngine,
    ) : this(
        ContextWrapper(null),
        pathManager,
        workspaceDao,
        WorkspaceFileService(pathManager, workspaceDao),
        lazy<LinuxRuntime> { error("Linux runtime is unavailable in this test constructor") },
        projectTemplateEngine,
        null,
    )
    fun observeProjects(): Flow<List<WorkspaceProject>> = workspaceDao.observeAll().map { entities ->
        dropParentDirectories(entities).mapNotNull(::projectFromEntity)
    }.flowOn(Dispatchers.IO)

    suspend fun listProjects(): List<WorkspaceProject> = withContext(Dispatchers.IO) {
        pathManager.workspaceDir.mkdirs()
        // 目录为准；缺失的目录从 Room 补录
        val known = workspaceDao.listAll().associateBy { it.name }
        val knownPaths = known.values.mapNotNull { runCatching { File(it.path).canonicalPath }.getOrNull() }.toSet()
        val directories = pathManager.workspaceDir.listFiles()
            .orEmpty()
            .filter { it.isDirectory && isValidProjectName(it.name) && !File(it, UNLINKED_MARKER).exists() }
        directories.forEach { directory ->
            if (directory.name !in known && directory.canonicalPath !in knownPaths) {
                workspaceDao.upsert(
                    WorkspaceEntity(directory.name, directory.absolutePath, System.currentTimeMillis()),
                )
            }
        }
        // 先收集所有有效实体，再过滤掉作为其他项目父目录的实体
        // （避免嵌套路径创建项目时，父目录被误注册为独立项目）
        val allEntities = workspaceDao.listAll().filter { it.name in known || File(it.path).isDirectory }
        dropParentDirectories(allEntities)
            .filter { entity -> File(entity.path).isDirectory }
            .sortedBy { it.name.lowercase() }
            .mapNotNull(::projectFromEntity)
    }

    /**
     * 丢掉「别的项目的父目录」那些实体：嵌套路径创建项目时，父目录会被顺带
     * 注册成一条实体，不剔除就会在列表里多出一个指向父目录的假项目。
     *
     * 原先 observeProjects 与 listProjects 各写了一份逐字相同的实现，现合并——
     * 两处口径必须一致，否则列表流与一次性查询会给出不同结果。
     *
     * canonicalPath 取不到的实体一律保留：那通常意味着目录暂时不可访问，
     * 此时宁可多显示，也不要凭空隐藏用户的项目。
     */
    private fun dropParentDirectories(entities: List<WorkspaceEntity>): List<WorkspaceEntity> {
        val projectPaths = entities
            .mapNotNull { runCatching { File(it.path).canonicalPath }.getOrNull() }
            .toSet()
        return entities.filter { entity ->
            val entityCanonical = runCatching { File(entity.path).canonicalPath }.getOrNull()
                ?: return@filter true
            projectPaths.none { otherPath ->
                otherPath != entityCanonical && otherPath.startsWith(entityCanonical + File.separator)
            }
        }
    }

    suspend fun createProject(
        name: String,
        storage: WorkspaceStorage = WorkspaceStorage.INTERNAL,
        directoryPath: String = "",
        template: ProjectTemplate = ProjectTemplate.EMPTY,
        packageName: String = "",
        apkSource: ApkImportSource? = null,
        exportApkToDownload: Boolean = false,
        gitUrl: String = "",
        templateVariables: Map<String, String> = emptyMap(),
        templateId: String = "",
        trustTemplateScripts: Boolean = false,
    ): AppResult<WorkspaceProject> = withContext(Dispatchers.IO) {
        try {
            val safeName = name.trim()
            require(isValidProjectName(safeName)) { "Tên phải bắt đầu bằng chữ hoặc số, chỉ được chứa chữ, số, dấu chấm, gạch dưới và gạch nối" }
            require(safeName != "sdcard") { "sdcard là tên dành riêng của không gian chia sẻ hệ thống" }
            check(workspaceDao.findByName(safeName) == null) { "Dự án đã tồn tại: $safeName" }
            val resolvedTemplateId = templateId
            val resolvedManifest = resolvedTemplateId.takeIf(String::isNotBlank)?.let(projectTemplateEngine::inspect)
            resolvedManifest?.variables?.firstOrNull { it.name == "projectName" }?.let { variable ->
                require(!variable.required || safeName.isNotBlank()) { "Tên dự án không được rỗng" }
                if (variable.validationRegex.isNotBlank()) {
                    require(Regex(variable.validationRegex).matches(safeName)) {
                        variable.description.ifBlank { "Tên dự án không khớp định dạng yêu cầu của mẫu đã chọn" }
                    }
                }
            }
            pathManager.workspaceDir.mkdirs()
            val base = when (storage) {
                WorkspaceStorage.INTERNAL -> pathManager.workspaceDir
                WorkspaceStorage.SHARED -> SHARED_STORAGE_ROOT
            }
            check(base.isDirectory || base.mkdirs()) { "Không gian liên kết không khả dụng: ${base.absolutePath}" }
            val prefix = if (storage == WorkspaceStorage.INTERNAL) "/workspace/" else "/sdcard/"
            val requested = directoryPath.trim().replace('\\', '/').removePrefix(prefix).trim('/')
            val relative = requested.ifBlank { safeName }
            requireValidRelativePath(relative)
            val directory = File(base, relative).canonicalFile
            check(isInside(base.canonicalFile, directory) && directory != base.canonicalFile) { "Thư mục liên kết vượt phạm vi" }
            val duplicate = workspaceDao.listAll().any {
                it.name != safeName && runCatching { File(it.path).canonicalFile == directory }.getOrDefault(false)
            }
            check(!duplicate) { "Thư mục này đã liên kết dự án khác" }
            val existed = directory.exists()
            if (template == ProjectTemplate.GIT_IMPORT) {
                require(isValidGitUrl(gitUrl)) { "Địa chỉ kho Git phải là địa chỉ HTTPS, SSH hoặc git@" }
                require(!existed || directory.isDirectory) { "Mục tiêu Git import không phải thư mục" }
                require(!existed || directory.listFiles().orEmpty().isEmpty()) { "Thư mục mục tiêu nhập Git phải rỗng" }
                if (!existed) require(directory.mkdirs()) { "Không thể tạo thư mục nhập Git" }
            } else {
                check((existed && directory.isDirectory) || (!existed && directory.mkdirs())) { "Không thể tạo hoặc truy cập thư mục liên kết" }
                if (template != ProjectTemplate.EMPTY || templateId.isNotBlank()) {
                    check(!existed || directory.listFiles().orEmpty().isEmpty()) { "Thư mục đích của mẫu phải rỗng" }
                }
            }
            File(directory, UNLINKED_MARKER).delete()

            // 模板初始化处理
            // APK 逆向模板：包名无需用户输入，由导入的安装包决定（无则留空）
            val needsPackageName = resolvedManifest?.variables.orEmpty().any {
                it.name == "packageName" || it.name == "packagePath"
            }
            var effectivePackage = ""
            if (needsPackageName) {
                val packageDefault = resolvedManifest?.variables?.firstOrNull { it.name == "packageName" }?.defaultValue.orEmpty()
                val cleanPkg = templateVariables["packageName"].orEmpty().trim()
                    .ifBlank { packageName.trim() }
                    .ifBlank { packageDefault }
                    .ifBlank { "com.example.${safeName.lowercase().filter { it.isLetterOrDigit() }}" }
                require(PACKAGE_NAME.matches(cleanPkg)) { "Tên gói phải là tên gói Java/Kotlin hợp lệ: $cleanPkg" }
                effectivePackage = cleanPkg
            }
            if (resolvedTemplateId.isNotBlank() && resolvedManifest != null) {
                val suppliedValues = templateVariables.toMutableMap().apply {
                    this["projectName"] = safeName
                    this["projectPath"] = linuxPathFor(directory)
                    putIfAbsent("appName", safeName)
                    if (needsPackageName) {
                        this["packageName"] = effectivePackage
                        this["packagePath"] = effectivePackage.replace('.', '/')
                    } else {
                        remove("packageName")
                        remove("packagePath")
                    }
                }
                val values = projectTemplateEngine.resolvedValues(resolvedManifest, suppliedValues)
                val hasScripts = resolvedManifest.hooks.beforeCreate.isNotBlank() || resolvedManifest.hooks.afterCreate.isNotBlank()
                require(!hasScripts || trustTemplateScripts) { "Mẫu này chứa script dựng, vui lòng xem trước và cấp quyền rõ ràng" }
                if (resolvedManifest.hooks.beforeCreate.isNotBlank()) {
                    executeTemplateHook(
                        resolvedTemplateId,
                        "before-create",
                        resolvedManifest.hooks.beforeCreate,
                        directory,
                        values,
                    )
                }
                projectTemplateEngine.materialize(
                    resolvedTemplateId,
                    directory,
                    values,
                )
                if (resolvedManifest.hooks.afterCreate.isNotBlank()) {
                    executeTemplateHook(
                        resolvedTemplateId,
                        "after-create",
                        resolvedManifest.hooks.afterCreate,
                        directory,
                        values,
                    )
                }
                projectTemplateEngine.validateMaterialized(resolvedTemplateId, directory, values)
                writeProjectTypeMetadata(
                    directory = directory,
                    type = when (resolvedManifest.projectType) {
                        TemplateProjectType.ANDROID -> ProjectType.ANDROID
                        TemplateProjectType.FLUTTER -> ProjectType.FLUTTER
                        TemplateProjectType.GENERAL -> ProjectType.GENERAL
                    },
                    source = ProjectImportSource.TEMPLATE,
                )
            } else when (template) {
                ProjectTemplate.APK_REVERSE -> {
                    val imported = importApkForReverse(directory, safeName, apkSource)
                    effectivePackage = imported.packageName
                    if (exportApkToDownload) {
                        exportApkToDownload(imported.apkFileName, directory, safeName)
                    }
                }
                ProjectTemplate.GIT_IMPORT -> cloneGitRepository(directory, gitUrl, cleanupOnFailure = !existed)
                ProjectTemplate.EMPTY -> { /* 保持空目录 */ }
            }

            val ownsDirectory = storage == WorkspaceStorage.INTERNAL && !existed
            workspaceDao.upsert(
                WorkspaceEntity(safeName, directory.absolutePath, System.currentTimeMillis(), ownsDirectory),
            )
            val createdEntity = workspaceDao.findByName(safeName)
            if (createdEntity == null) {
                AppResult.Failure(AppError(ErrorCode.IO, "Truy vấn thất bại sau khi ghi dự án", null))
            } else {
                val created = projectFromEntity(createdEntity)
                if (created == null) {
                    AppResult.Failure(AppError(ErrorCode.IO, "Chuyển đổi dữ liệu dự án thất bại", null))
                } else {
                    AppResult.Success(created)
                }
            }
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "Tạo dự án thất bại", throwable))
        }
    }

    /**
     * Imports a ZIP project archive into an internal sandbox directory and records the user-selected
     * project label. Extraction always happens under /workspace and rejects path traversal entries.
     */
    suspend fun importProjectArchive(
        name: String,
        directoryPath: String = "",
        projectType: ProjectType,
        source: ProjectArchiveSource,
    ): AppResult<WorkspaceProject> = withContext(Dispatchers.IO) {
        importProject(name, directoryPath, projectType, ProjectImportSource.LOCAL_ARCHIVE) { directory, cleanupOnFailure ->
            try {
                extractProjectArchive(source, directory)
            } catch (throwable: Throwable) {
                if (cleanupOnFailure) SafeFileTree.delete(directory)
                throw throwable
            }
        }
    }

    /** Imports a GitHub repository over the explicitly selected HTTP(S) or SSH transport. */
    suspend fun importGithubProject(
        name: String,
        directoryPath: String = "",
        projectType: ProjectType,
        gitUrl: String,
        transport: GitTransport,
        onProgress: ((String) -> Unit)? = null,
    ): AppResult<WorkspaceProject> = withContext(Dispatchers.IO) {
        importProject(name, directoryPath, projectType, ProjectImportSource.GITHUB) { directory, cleanupOnFailure ->
            require(isValidGitUrlForTransport(gitUrl, transport)) {
                when (transport) {
                    GitTransport.HTTP -> "Địa chỉ HTTP phải bắt đầu bằng http:// hoặc https://"
                    GitTransport.SSH -> "Địa chỉ SSH phải dùng định dạng ssh:// hoặc git@host:path"
                }
            }
            cloneGitRepository(directory, gitUrl, cleanupOnFailure, onProgress)
        }
    }

    /** Compresses all regular project files and exports the ZIP into a SAF-selected local directory. */
    suspend fun exportProject(name: String, targetTreeUri: String): AppResult<String> = withContext(Dispatchers.IO) {
        try {
            require(isValidProjectName(name)) { "Tên dự án không hợp lệ" }
            val entity = workspaceDao.findByName(name) ?: error("Dự án không tồn tại: $name")
            val projectDir = File(entity.path).canonicalFile
            check(projectDir.isDirectory) { "Thư mục dự án không tồn tại: $name" }

            val treeUri = Uri.parse(targetTreeUri)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val fileName = "$name-${System.currentTimeMillis()}.zip"
            val outputUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "application/zip",
                fileName,
            ) ?: error("Không thể tạo tệp xuất trong thư mục đã chọn")
            val output = context.contentResolver.openOutputStream(outputUri, "w")
                ?: error("Không thể ghi tệp xuất")
            output.use { stream -> writeProjectZip(projectDir, stream) }
            AppResult.Success(fileName)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "Export dự án thất bại", throwable))
        }
    }

    suspend fun deleteProject(name: String): AppResult<Unit> = withContext(Dispatchers.IO) {
        try {
            require(isValidProjectName(name)) { "Tên dự án không hợp lệ" }
            val entity = workspaceDao.findByName(name) ?: error("Dự án không tồn tại: $name")
            val directory = File(entity.path)
            if (entity.ownsDirectory && directory.exists()) {
                SafeFileTree.delete(directory)
            } else if (directory.isDirectory) {
                File(directory, UNLINKED_MARKER).writeText("unlinkedAt=${System.currentTimeMillis()}\n")
            }
            workspaceDao.delete(name)
            runCatching { buildScriptRepository?.value?.unbind(name) }
            AppResult.Success(Unit)
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "Xóa dự án thất bại", throwable))
        }
    }

    suspend fun linuxWorkingDirectory(name: String): String {
        if (name == "sdcard") return "/sdcard"
        require(isValidProjectName(name)) { "Tên dự án không hợp lệ" }
        val entity = workspaceDao.findByName(name) ?: error("Dự án không tồn tại: $name")
        check(File(entity.path).isDirectory) { "Thư mục liên kết không tồn tại: $name" }
        return linuxPathFor(File(entity.path))
    }

    /** 会话关联的工作区目录；返回 null 表示不关联。 */
    suspend fun workspaceForName(name: String?): String? {
        if (name.isNullOrBlank()) return null
        return runCatching { linuxWorkingDirectory(name) }.getOrNull()
    }

    // ==================== 项目内文件管理 API ====================

    /**
     * 该项目根目录是否位于宿主共享存储（/storage/emulated/0）之下。
     * 共享存储受 Android 11+ "Truy cập tất cả tệp" 权限约束：未授权时系统会过滤
     * 其他应用的文件（典型表现是只列出文件夹），需要引导用户授权。
     */
    suspend fun usesSharedStorage(projectName: String): Boolean = withContext(Dispatchers.IO) {
        if (projectName == "sdcard") return@withContext true
        val entityPath = workspaceDao.findByName(projectName)?.path ?: return@withContext false
        val canonical = runCatching { File(entityPath).canonicalPath }.getOrDefault(entityPath)
        val sharedRoot = runCatching { SHARED_STORAGE_ROOT.canonicalPath }.getOrDefault(SHARED_STORAGE_ROOT.absolutePath)
        canonical == sharedRoot || canonical.startsWith(sharedRoot + File.separator)
    }

    /** 列出项目指定相对路径下的所有文件与子目录（目录优先排序）。 */
    suspend fun listFiles(projectName: String, relativePath: String = ""): AppResult<List<WorkspaceFileItem>> =
        fileService.listFiles(projectName, relativePath)

    /** 读取文件内容（UTF-8，限制单文件最大读取大小）。 */
    suspend fun readFile(projectName: String, relativePath: String): AppResult<String> =
        fileService.readFile(projectName, relativePath)

    /** 写入文件内容（原子临时文件替换）。 */
    suspend fun writeFile(projectName: String, relativePath: String, content: String): AppResult<Unit> =
        fileService.writeFile(projectName, relativePath, content)

    /** 创建新文件（空文件）。 */
    suspend fun createFile(projectName: String, relativePath: String): AppResult<Unit> =
        fileService.createFile(projectName, relativePath)

    /** 创建新目录。 */
    suspend fun createDirectory(projectName: String, relativePath: String): AppResult<Unit> =
        fileService.createDirectory(projectName, relativePath)

    /** 重命名文件或目录。 */
    suspend fun renameItem(projectName: String, oldRelativePath: String, newName: String): AppResult<Unit> =
        fileService.renameItem(projectName, oldRelativePath, newName)

    /** 删除文件或目录。 */
    suspend fun deleteItem(projectName: String, relativePath: String): AppResult<Unit> =
        fileService.deleteItem(projectName, relativePath)

    private fun isInside(root: File, candidate: File): Boolean =
        candidate.absolutePath == root.absolutePath ||
            candidate.absolutePath.startsWith(root.absolutePath + File.separator)

    /**
     * 校验「相对目录」不含空段、`.` 或 `..`。
     *
     * 「关联到已有目录」这条规则原先在两个入口各写了一份（内部沙盒 / 共享空间），
     * 且已开始漂移（一处 `base.canonicalFile`、一处 `base`）。合并为一份：
     * 同一规则写两遍、其中一份悄悄改坏，正是 unpackApk 漏掉解压上限的成因。
     */
    private fun requireValidRelativePath(relative: String) {
        require(relative.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Thư mục liên kết chứa đường dẫn không hợp lệ"
        }
    }

    private fun projectFromEntity(entity: WorkspaceEntity): WorkspaceProject? {
        val directory = File(entity.path)
        if (!directory.isDirectory) return null
        val type = detectProjectType(directory)
        val pkg = extractPackageName(directory, type)
        return WorkspaceProject(
            name = entity.name,
            path = entity.path,
            linuxPath = linuxPathFor(directory),
            sizeBytes = sizeOf(directory),
            ownsDirectory = entity.ownsDirectory,
            projectType = type,
            packageName = pkg,
        )
    }


    private fun detectProjectType(directory: File): ProjectType {
        readProjectTypeMetadata(directory)?.let { return it }
        return when {
            File(directory, "pubspec.yaml").exists() -> ProjectType.FLUTTER
            File(directory, "settings.gradle.kts").exists() ||
                File(directory, "app/build.gradle.kts").exists() ||
                File(directory, "build.gradle").exists() -> ProjectType.ANDROID
            // APK 逆向工程：优先使用导入元数据标记，再兼容 .apk/解包目录
            File(directory, "apk-info.properties").isFile ||
                directory.listFiles().orEmpty().any { it.isFile && it.extension.equals("apk", ignoreCase = true) } ||
                (File(directory, "unpacked").isDirectory && File(directory, "unpacked").listFiles().orEmpty()
                    .any { it.isFile && it.name.startsWith("classes") && it.extension == "dex" }) -> ProjectType.REVERSE
            else -> ProjectType.GENERAL
        }
    }

    private fun extractPackageName(directory: File, type: ProjectType): String {
        return runCatching {
            when (type) {
                ProjectType.ANDROID -> {
                    val appBuild = File(directory, "app/build.gradle.kts").takeIf { it.exists() }
                        ?: File(directory, "app/build.gradle").takeIf { it.exists() }
                    val content = appBuild?.readText()
                    val namespaceMatch = Regex("""(?:namespace|applicationId)\s*=\s*["']([^"']+)["']""").find(content ?: "")
                    namespaceMatch?.groupValues?.get(1) ?: ""
                }
                ProjectType.FLUTTER -> {
                    val pubspec = File(directory, "pubspec.yaml").takeIf { it.exists() }
                    val nameMatch = Regex("""name:\s*([a-zA-Z0-9_]+)""").find(pubspec?.readText() ?: "")
                    nameMatch?.groupValues?.get(1) ?: ""
                }
                ProjectType.REVERSE -> {
                    val info = File(directory, "apk-info.properties").takeIf { it.exists() }?.readText().orEmpty()
                    Regex("""packageName\s*=\s*(.+)""").find(info)?.groupValues?.get(1)?.trim() ?: ""
                }
                ProjectType.GENERAL -> ""
            }
        }.getOrDefault("")
    }

    private fun isValidGitUrl(url: String): Boolean =
        url.trim().let { value ->
            value.startsWith("https://") || value.startsWith("http://") ||
                value.startsWith("ssh://") || Regex("^[A-Za-z0-9_.-]+@[A-Za-z0-9_.-]+:.+").matches(value)
        }

    private fun isValidGitUrlForTransport(url: String, transport: GitTransport): Boolean =
        url.trim().let { value ->
            when (transport) {
                GitTransport.HTTP -> value.startsWith("https://") || value.startsWith("http://")
                GitTransport.SSH -> value.startsWith("ssh://") ||
                    Regex("^[A-Za-z0-9_.-]+@[A-Za-z0-9_.-]+:.+").matches(value)
            }
        }

    private suspend fun cloneGitRepository(
        directory: File,
        url: String,
        cleanupOnFailure: Boolean,
        onProgress: ((String) -> Unit)? = null,
    ) {
        val result = linuxRuntime.value.execute(
            ShellCommand(
                // --progress 让 git 在非 TTY 管道下也输出克隆进度（remote:/Receiving objects: 等）
                commandLine = "git clone --depth 1 --progress -- ${shellQuote(url.trim())} ${shellQuote(linuxPathFor(directory))}",
                timeoutMs = GIT_CLONE_TIMEOUT_SECONDS * 1_000L,
                onOutput = onProgress,
            ),
        )
        check(result.isSuccess) {
            if (cleanupOnFailure) SafeFileTree.delete(directory)
            val output = (result.stderr + "\n" + result.stdout).trim().takeLast(1200)
            "Git clone thất bại: ${output.ifBlank { "请确认 Git 已安装、仓库地址和认证配置可用" }}"
        }
        SafeFileTree.delete(File(directory, ".git/hooks"))
    }

    private suspend fun importProject(
        name: String,
        directoryPath: String,
        projectType: ProjectType,
        source: ProjectImportSource,
        materialize: suspend (directory: File, cleanupOnFailure: Boolean) -> Unit,
    ): AppResult<WorkspaceProject> {
        return try {
            val safeName = name.trim()
            require(isValidProjectName(safeName)) { "Tên phải bắt đầu bằng chữ hoặc số, chỉ được chứa chữ, số, dấu chấm, gạch dưới và gạch nối" }
            require(safeName != "sdcard") { "sdcard là tên dành riêng của không gian chia sẻ hệ thống" }
            check(workspaceDao.findByName(safeName) == null) { "Dự án đã tồn tại: $safeName" }
            pathManager.workspaceDir.mkdirs()
            val base = pathManager.workspaceDir.canonicalFile
            check(base.isDirectory || base.mkdirs()) { "Thư mục sandbox nội bộ không khả dụng" }
            val requested = directoryPath.trim().replace('\\', '/').removePrefix("/workspace/").trim('/')
            val relative = requested.ifBlank { safeName }
            requireValidRelativePath(relative)
            val directory = File(base, relative).canonicalFile
            check(isInside(base, directory) && directory != base) { "Thư mục liên kết vượt phạm vi" }
            val duplicate = workspaceDao.listAll().any {
                runCatching { File(it.path).canonicalFile == directory }.getOrDefault(false)
            }
            check(!duplicate) { "Thư mục này đã liên kết dự án khác" }
            val existed = directory.exists()
            require(!existed || directory.isDirectory) { "Mục tiêu nhập không phải thư mục" }
            require(!existed || directory.listFiles().orEmpty().isEmpty()) { "Thư mục đích khi nhập phải trống" }
            if (!existed) require(directory.mkdirs()) { "Không thể tạo thư mục nhập" }
            materialize(directory, !existed)
            writeProjectTypeMetadata(directory, projectType, source)
            workspaceDao.upsert(
                WorkspaceEntity(safeName, directory.absolutePath, System.currentTimeMillis(), ownsDirectory = !existed),
            )
            val createdEntity = workspaceDao.findByName(safeName)
            if (createdEntity == null) {
                AppResult.Failure(AppError(ErrorCode.IO, "Truy vấn thất bại sau khi ghi dự án", null))
            } else {
                val created = projectFromEntity(createdEntity)
                if (created == null) {
                    AppResult.Failure(AppError(ErrorCode.IO, "Chuyển đổi dữ liệu dự án thất bại", null))
                } else {
                    AppResult.Success(created)
                }
            }
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: "Nhập dự án thất bại", throwable))
        }
    }

    private fun extractProjectArchive(source: ProjectArchiveSource, directory: File) {
        require(source.fileName.endsWith(".zip", ignoreCase = true)) { "Nhập cục bộ hiện chỉ hỗ trợ gói nén dự án ZIP" }
        val input = context.contentResolver.openInputStream(Uri.parse(source.uri))
            ?: error("Không thể đọc gói nén dự án đã chọn (ủy quyền URI có thể đã hết hạn, hãy chọn lại)")
        extractProjectArchive(input, source.fileName, directory)
    }

    internal fun extractProjectArchive(input: java.io.InputStream, fileName: String, directory: File) {
        require(fileName.endsWith(".zip", ignoreCase = true)) { "Nhập cục bộ hiện chỉ hỗ trợ gói nén dự án ZIP" }
        val staging = File(directory, IMPORT_STAGING_DIRECTORY).canonicalFile
        check(isInside(directory.canonicalFile, staging)) { "Thư mục lưu tạm nhập vượt biên" }
        SafeFileTree.delete(staging)
        check(staging.mkdirs()) { "Không thể tạo thư mục tạm khi nhập" }
        var entryCount = 0
        var totalBytes = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    require(entryCount <= MAX_ARCHIVE_ENTRIES) { "Số lượng tệp trong gói nén quá nhiều" }
                    val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                    require(normalizedName.isNotBlank()) { "Gói nén chứa đường dẫn rỗng" }
                    require(normalizedName.split('/').none { it == ".." }) { "Gói nén chứa đường dẫn vượt phạm vi: ${entry.name}" }
                    val target = File(staging, normalizedName).canonicalFile
                    require(isInside(staging, target) && target != staging) { "Gói nén chứa đường dẫn vượt phạm vi: ${entry.name}" }
                    if (entry.isDirectory) {
                        check(target.isDirectory || target.mkdirs()) { "Không thể tạo thư mục：${entry.name}" }
                    } else {
                        check(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true) {
                            "Không thể tạo thư mục：${entry.name}"
                        }
                        target.outputStream().buffered().use { output ->
                            val copied = copyCapped(zip, output, entry.name)
                            totalBytes += copied
                        }
                        require(totalBytes <= MAX_ARCHIVE_TOTAL_BYTES) { "Gói nén sau khi giải nén có dung lượng quá lớn" }
                    }
                    zip.closeEntry()
                }
            }
            require(entryCount > 0) { "Gói nén dự án rỗng" }
            val meaningful = staging.listFiles().orEmpty().filterNot { it.name == "__MACOSX" }
            val contentRoot = meaningful.singleOrNull()?.takeIf { it.isDirectory } ?: staging
            val children = contentRoot.listFiles().orEmpty().filterNot { it.name == "__MACOSX" }
            require(children.isNotEmpty()) { "Gói nén dự án không có tệp nào để nhập" }
            children.forEach { child ->
                val destination = File(directory, child.name).canonicalFile
                check(isInside(directory.canonicalFile, destination) && !destination.exists()) { "Xung đột tệp nhập: ${child.name}" }
                check(child.renameTo(destination)) { "Không thể ghi tệp nhập: ${child.name}" }
            }
        } finally {
            SafeFileTree.delete(staging)
        }
    }

    private fun writeProjectZip(projectDir: File, output: java.io.OutputStream) {
        ZipOutputStream(output.buffered()).use { zip ->
            projectDir.walkTopDown()
                .onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
                .filter { it != projectDir && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
                .forEach { file ->
                    val relative = file.toRelativeString(projectDir).replace(File.separatorChar, '/')
                    if (relative == UNLINKED_MARKER || relative == IMPORT_STAGING_DIRECTORY) return@forEach
                    val entryName = if (file.isDirectory) "$relative/" else relative
                    zip.putNextEntry(ZipEntry(entryName).apply { time = file.lastModified() })
                    if (file.isFile) file.inputStream().buffered().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    private fun writeProjectTypeMetadata(directory: File, type: ProjectType, source: ProjectImportSource) {
        File(directory, PROJECT_METADATA_FILE).writeText(
            "type=${type.name}\nsource=${source.name}\nimportedAt=${System.currentTimeMillis()}\n",
            Charsets.UTF_8,
        )
    }

    private fun readProjectTypeMetadata(directory: File): ProjectType? = runCatching {
        val metadata = File(directory, PROJECT_METADATA_FILE)
        if (!metadata.isFile) return@runCatching null
        val typeName = metadata.useLines { lines ->
            lines.firstOrNull { it.startsWith("type=") }?.substringAfter("type=")?.trim()
        }
        ProjectType.entries.firstOrNull { it.name == typeName }
    }.getOrNull()

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private suspend fun executeTemplateHook(
        templateId: String,
        stage: String,
        relativePath: String,
        projectDir: File,
        values: Map<String, String>,
    ) {
        val hookFile = File(projectDir, ".tianxuan-template-$stage-${UUID.randomUUID()}.sh")
        try {
            hookFile.writeBytes(projectTemplateEngine.readHook(templateId, relativePath))
            hookFile.setExecutable(true)
            val result = linuxRuntime.value.execute(
                ShellCommand(
                    commandLine = "sh ${shellQuote(linuxPathFor(hookFile))}",
                    workingDirectory = linuxPathFor(projectDir),
                    environment = values.mapKeys { (name, _) -> "TIANXUAN_VAR_${name.uppercase()}" } +
                        ("TIANXUAN_PROJECT_DIR" to linuxPathFor(projectDir)),
                    timeoutMs = TEMPLATE_HOOK_TIMEOUT_MS,
                ),
            )
            check(result.isSuccess) {
                "Thực thi script mẫu thất bại ($stage): ${result.stderr.ifBlank { result.stdout }.takeLast(2_000)}"
            }
        } finally {
            hookFile.delete()
        }
    }

    // ==================== APK 逆向模板 ====================

    private data class ImportedApk(
        val packageName: String,
        val apkFileName: String,
        val sourceLabel: String,
        val sourceKind: String,
    )

    /**
     * APK 逆向模板初始化：把安装包导入工程目录并做第一层"Giải nén"。
     *
     * 产物结构（以工程名 [name] 为例）：
     * ```
     * <project>/
     * ├── <name>.apk            # 原始安装包（可直接交给 jadx / apktool / MT 管理器）
     * ├── unpacked/             # 标准 ZIP 解包产物（dex / res / assets / lib / 二进制 AXML）
     * │   ├── AndroidManifest.xml
     * │   ├── classes.dex
     * │   ├── resources.arsc
     * │   └── ...
     * ├── apk-info.properties   # 来源与元数据（工程包名读取处）
     * └── REVERSE.md            # 逆向工作流指引（jadx / apktool / MCP）
     * ```
     */
    private fun importApkForReverse(
        projectDir: File,
        name: String,
        apkSource: ApkImportSource?,
    ): ImportedApk {
        requireNotNull(apkSource) { "Mẫu dịch ngược APK bắt buộc phải chọn nguồn gói cài đặt (ứng dụng đã cài hoặc tệp APK)" }
        projectDir.mkdirs()

        // 1. 解析来源并拷贝安装包到工程目录
        val apkFileName: String
        val sourceLabel: String
        val sourceKind: String
        val packageHint: String
        val apkFile: File
        when (apkSource) {
            is ApkImportSource.FromInstalledApp -> {
                val info = runCatching {
                    context.packageManager.getApplicationInfo(apkSource.packageName, 0)
                }.getOrElse { error("Không thể đọc thông tin ứng dụng đã cài: ${apkSource.packageName}") }
                val source = File(info.sourceDir)
                require(source.isFile) { "Gói cài ứng dụng không đọc được: ${source.absolutePath}" }
                apkFileName = "${apkSource.packageName}.apk"
                sourceLabel = apkSource.appLabel
                sourceKind = "installed-app"
                packageHint = apkSource.packageName
                apkFile = File(projectDir, apkFileName).canonicalFile
                check(isInside(projectDir.canonicalFile, apkFile)) { "Đường dẫn xuất APK vượt biên" }
                source.copyTo(apkFile, overwrite = true)
            }
            is ApkImportSource.FromFileUri -> {
                val uri = android.net.Uri.parse(apkSource.uri)
                val safeBase = apkSource.fileName
                    .substringAfterLast('/')
                    .substringAfterLast('\\')
                    .filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
                    .trim('.')
                    .ifBlank { "target" }
                val displayName = if (safeBase.endsWith(".apk", ignoreCase = true)) safeBase else "$safeBase.apk"
                apkFileName = displayName
                sourceLabel = apkSource.displayName.ifBlank { displayName }
                sourceKind = "file-uri"
                packageHint = ""
                apkFile = File(projectDir, apkFileName).canonicalFile
                check(isInside(projectDir.canonicalFile, apkFile)) { "Đường dẫn xuất APK vượt biên" }
                val input = context.contentResolver.openInputStream(uri)
                    ?: error("Không đọc được tệp APK đã chọn (ủy quyền URI có thể đã hết hạn, hãy chọn lại)")
                input.use { source ->
                    apkFile.outputStream().use { output -> source.copyTo(output) }
                }
            }
        }
        require(apkFile.isFile && apkFile.length() > 0) { "Nhập gói cài đặt thất bại: $apkFileName" }

        // 2. 标准 ZIP 解包 -> unpacked/
        val unpackedDir = File(projectDir, "unpacked").apply { mkdirs() }
        unpackApk(apkFile, unpackedDir)

        // 3. 写入元数据与逆向工作流指引
        File(projectDir, "apk-info.properties").writeText(
            buildString {
                appendLine("apk=${apkFile.name}")
                appendLine("apkSizeBytes=${apkFile.length()}")
                appendLine("source=$sourceKind")
                appendLine("sourceLabel=$sourceLabel")
                appendLine("packageName=$packageHint")
                appendLine("importedAt=${System.currentTimeMillis()}")
            },
            Charsets.UTF_8,
        )
        writeReverseReadme(projectDir, name, apkFile.name, unpackedDir, sourceLabel)

        return ImportedApk(
            packageName = packageHint,
            apkFileName = apkFileName,
            sourceLabel = sourceLabel,
            sourceKind = sourceKind,
        )
    }

    /** 用标准 ZIP 读取器把 APK 逐条目解包到 [unpackedDir]（防 zip-slip、防解压炸弹）。 */
    internal fun unpackApk(apkFile: File, unpackedDir: File) {
        val unpackedCanonical = unpackedDir.canonicalFile
        // 上限与 extractProjectArchive 共用同一组常量：此前这里只防了路径穿越，
        // 没有数量/体积上限，而导入的 APK 是**用户自选的任意文件**——
        // 一个百 KB 级的包就能声明 GB 级解压体积，一路写到磁盘满或 OOM。
        var entryCount = 0
        var totalBytes = 0L
        java.util.zip.ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.isDirectory) return@forEach
                entryCount++
                require(entryCount <= MAX_ARCHIVE_ENTRIES) { "Số lượng tệp APK quá nhiều" }
                val rawName = entry.name.replace('\\', '/')
                // 防 zip-slip：拒绝绝对路径与 .. 穿越
                if (rawName.startsWith("/") || rawName.split('/').any { it == ".." }) return@forEach
                val target = File(unpackedDir, rawName)
                if (!isInside(unpackedCanonical, target.canonicalFile)) return@forEach
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    target.outputStream().buffered().use { output ->
                        totalBytes += copyCapped(input, output, entry.name)
                    }
                }
                require(totalBytes <= MAX_ARCHIVE_TOTAL_BYTES) { "APK sau khi giải nén có dung lượng quá lớn" }
            }
        }
    }

    /**
     * 把 [input] 逐块拷进 [output]，撞上单文件上限即抛错；返回拷入的字节数，
     * 总体积额度由调用方累加后自行判定（那是**整包**额度，不能每文件各算）。
     *
     * 这条循环原本只写在 extractProjectArchive 里，unpackApk 那条路径完全没有，
     * 而 APK 是用户自选的任意文件。两处共用一份实现，后续不会各改各的。
     */
    private fun copyCapped(input: java.io.InputStream, output: java.io.OutputStream, entryName: String): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            entryBytes += read
            require(entryBytes <= MAX_ARCHIVE_ENTRY_BYTES) { "Một tệp trong gói nén quá lớn: $entryName" }
            output.write(buffer, 0, read)
        }
        return entryBytes
    }

    /**
     * 把工程内导入的 APK 同步导出到宿主公共下载目录（best-effort，供宿主侧 MT 管理器等外部工具直接读取；
     * Android 11+ 需已授予"Truy cập tất cả tệp"权限，未授权时静默跳过，不影响工程创建）。
     */
    private fun exportApkToDownload(apkFileName: String, projectDir: File, projectName: String) {
        runCatching {
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadDir.exists() && !downloadDir.mkdirs()) return
            val source = File(projectDir, apkFileName)
            if (!source.isFile) return
            source.copyTo(File(downloadDir, "$projectName.apk"), overwrite = true)
        }
    }

    private fun linuxPathFor(directory: File): String {
        val canonical = directory.canonicalFile
        val internal = pathManager.workspaceDir.canonicalFile
        val shared = SHARED_STORAGE_ROOT.canonicalFile
        return when {
            isInside(internal, canonical) -> "/workspace/${canonical.toRelativeString(internal).replace(File.separatorChar, '/')}"
            isInside(shared, canonical) -> "/sdcard/${canonical.toRelativeString(shared).replace(File.separatorChar, '/')}"
            else -> error("Thư mục không nằm trong không gian liên kết được")
        }.trimEnd('/')
    }

    private fun sizeOf(file: File): Long = file.walkTopDown()
        .onEnter { directory -> !java.nio.file.Files.isSymbolicLink(directory.toPath()) }
        .filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
        .sumOf { it.length() }

    private fun isValidProjectName(name: String): Boolean {
        if (name.isEmpty() || name.length > MAX_PROJECT_NAME_LENGTH) return false
        if (!name.first().isLetterOrDigit()) return false
        return name.drop(1).all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
    }

    companion object {
        private const val UNLINKED_MARKER = ".tianxuan-unlinked-project"
        private const val PROJECT_METADATA_FILE = ".tianxuan-project.properties"
        private const val IMPORT_STAGING_DIRECTORY = ".tianxuan-import-staging"
        private const val MAX_ARCHIVE_ENTRIES = 100_000
        private const val MAX_ARCHIVE_ENTRY_BYTES = 1024L * 1024L * 1024L
        private const val MAX_ARCHIVE_TOTAL_BYTES = 4L * 1024L * 1024L * 1024L
        private const val GIT_CLONE_TIMEOUT_SECONDS = 15 * 60L
        private const val TEMPLATE_HOOK_TIMEOUT_MS = 60_000L
        private val PACKAGE_NAME = Regex("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+")
        const val MAX_PROJECT_NAME_LENGTH = 64
        const val MAX_FILE_READ_BYTES = 4 * 1024 * 1024L // 4 MB
        const val MAX_FILE_WRITE_CHARS = 4 * 1024 * 1024 // 4 M 字符
        val SHARED_STORAGE_ROOT: File = File("/storage/emulated/0")
    }
}
