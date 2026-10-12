package top.wkbin.tianxuan.runtime

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.wkbin.tianxuan.core.common.files.SafeFileTree
import top.wkbin.tianxuan.core.common.result.AppError
import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.common.result.ErrorCode
import top.wkbin.tianxuan.core.database.WorkspaceRepository

/** Boundary-safe file operations for a registered workspace. */
class WorkspaceFileService(
    private val pathManager: RuntimePathManager,
    private val workspaceRepository: WorkspaceRepository,
) {
    suspend fun listFiles(projectName: String, relativePath: String = ""): AppResult<List<WorkspaceFileItem>> = ioResult("Đọc danh sách tệp thất bại") {
        val directory = resolve(projectName, relativePath)
        check(directory.isDirectory) { "Không phải thư mục: ${displayPath(projectName, relativePath)}" }
        val root = projectRoot(projectName)
        directory.listFiles().orEmpty().map { file ->
            WorkspaceFileItem(
                name = file.name,
                relativePath = file.toRelativeString(root).replace(File.separatorChar, '/'),
                isDirectory = file.isDirectory,
                sizeBytes = if (file.isFile) file.length() else 0L,
                lastModified = file.lastModified(),
                extension = if (file.isFile) file.extension.lowercase() else "",
            )
        }.sortedWith(compareBy<WorkspaceFileItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    suspend fun readFile(projectName: String, relativePath: String): AppResult<String> = ioResult("Đọc tệp thất bại") {
        val file = resolve(projectName, relativePath)
        check(file.isFile) { "Không phải tệp: ${displayPath(projectName, relativePath)}" }
        check(file.length() <= WorkspaceManager.MAX_FILE_READ_BYTES) {
            "Tệp quá lớn (${file.length()} byte, giới hạn ${WorkspaceManager.MAX_FILE_READ_BYTES / 1024 / 1024} MB)"
        }
        file.readText(Charsets.UTF_8)
    }

    suspend fun writeFile(projectName: String, relativePath: String, content: String): AppResult<Unit> = ioResult("Lưu tệp thất bại") {
        require(content.length <= WorkspaceManager.MAX_FILE_WRITE_CHARS) {
            "Nội dung quá dài (${content.length} ký tự, giới hạn ${WorkspaceManager.MAX_FILE_WRITE_CHARS})"
        }
        val file = resolve(projectName, relativePath, allowMissing = true)
        require(!file.isDirectory) { "Mục tiêu là thư mục: ${displayPath(projectName, relativePath)}" }
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, ".${file.name}.tmp-${System.nanoTime()}")
        try {
            temporary.writeText(content, Charsets.UTF_8)
            if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
        } finally {
            temporary.delete()
        }
    }

    suspend fun createFile(projectName: String, relativePath: String): AppResult<Unit> = ioResult("Tạo tệp thất bại") {
        val file = resolve(projectName, relativePath, allowMissing = true)
        check(!file.exists()) { "Tệp đã tồn tại: ${file.name}" }
        file.parentFile?.mkdirs()
        check(file.createNewFile()) { "Không thể tạo tệp" }
    }

    suspend fun createDirectory(projectName: String, relativePath: String): AppResult<Unit> = ioResult("Tạo thư mục thất bại") {
        val directory = resolve(projectName, relativePath, allowMissing = true)
        check(!directory.exists()) { "Thư mục đã tồn tại: ${directory.name}" }
        check(directory.mkdirs()) { "Không thể tạo thư mục" }
    }

    suspend fun renameItem(projectName: String, oldRelativePath: String, newName: String): AppResult<Unit> = ioResult("Đổi tên thất bại") {
        val safeName = newName.trim()
        require(safeName.isNotBlank() && '/' !in safeName && '\\' !in safeName) { "Tên mới không hợp lệ" }
        val file = resolve(projectName, oldRelativePath)
        val target = File(file.parentFile, safeName)
        check(!target.exists()) { "Mục tiêu đã tồn tại: $safeName" }
        check(file.renameTo(target)) { "Đổi tên thất bại" }
    }

    suspend fun deleteItem(projectName: String, relativePath: String): AppResult<Unit> = ioResult("Xóa thất bại") {
        val file = resolve(projectName, relativePath)
        check(file.canonicalFile != projectRoot(projectName).canonicalFile) { "Không thể xóa thư mục gốc vùng làm việc qua giao diện này" }
        if (file.isDirectory) SafeFileTree.delete(file) else check(file.delete()) { "Xóa tệp thất bại" }
    }

    private suspend fun projectRoot(projectName: String): File {
        if (projectName == "sdcard") File("/storage/emulated/0").takeIf { it.exists() }?.let { return it }
        require(isValidProjectName(projectName)) { "Tên dự án không hợp lệ: $projectName" }
        val entity = workspaceRepository.findByName(projectName) ?: error("Dự án không tồn tại：$projectName")
        return File(entity.path).also { check(it.isDirectory) { "Thư mục liên kết không tồn tại: $projectName" } }
    }

    private suspend fun resolve(projectName: String, relativePath: String, allowMissing: Boolean = false): File {
        val root = projectRoot(projectName)
        val trimmed = relativePath.trim().removePrefix("/workspace/$projectName").removePrefix("/sdcard").removePrefix("/")
        val segments = trimmed.split('/', '\\').filter { it.isNotEmpty() && it != "." }
        require(segments.none { it == ".." }) { "Đường dẫn chứa toán tử vượt giới hạn (..)" }
        val candidate = segments.fold(root) { parent, segment -> File(parent, segment) }
        val canonical = candidate.canonicalFile
        require(isInside(root.canonicalFile, canonical)) { "Đường dẫn vượt phạm vi: $relativePath" }
        if (!allowMissing) require(candidate.exists()) { "Mục tiêu không tồn tại: ${displayPath(projectName, relativePath)}" }
        return candidate
    }

    private suspend fun displayPath(projectName: String, relativePath: String): String =
        if (projectName == "sdcard") "/sdcard/${relativePath.trimStart('/')}"
        else "${linuxPathFor(projectRoot(projectName))}/${relativePath.trimStart('/')}"

    private fun linuxPathFor(directory: File): String {
        val canonical = directory.canonicalFile
        val internal = pathManager.workspaceDir.canonicalFile
        val shared = WorkspaceManager.SHARED_STORAGE_ROOT.canonicalFile
        return when {
            isInside(internal, canonical) -> "/workspace/${canonical.toRelativeString(internal).replace(File.separatorChar, '/')}"
            isInside(shared, canonical) -> "/sdcard/${canonical.toRelativeString(shared).replace(File.separatorChar, '/')}"
            else -> error("Thư mục không nằm trong không gian liên kết được")
        }.trimEnd('/')
    }

    private fun isInside(root: File, candidate: File): Boolean =
        candidate == root || candidate.absolutePath.startsWith(root.absolutePath + File.separator)

    private fun isValidProjectName(name: String): Boolean = name.isNotEmpty() &&
        name.length <= WorkspaceManager.MAX_PROJECT_NAME_LENGTH && name.first().isLetterOrDigit() &&
        name.drop(1).all { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }

    private suspend fun <T> ioResult(fallback: String, block: suspend () -> T): AppResult<T> = withContext(Dispatchers.IO) {
        try {
            AppResult.Success(block())
        } catch (throwable: Throwable) {
            AppResult.Failure(AppError(ErrorCode.IO, throwable.message ?: fallback, throwable))
        }
    }
}
