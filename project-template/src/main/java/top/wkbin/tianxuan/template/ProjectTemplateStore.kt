package top.wkbin.tianxuan.template

import top.wkbin.tianxuan.core.common.files.SafeFileTree
import android.content.Context
import android.graphics.BitmapFactory
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

data class InstalledProjectTemplate(
    val manifest: ProjectTemplateManifest,
    val directory: File,
    val previewFile: File? = null,
    val bundledAssetPath: String? = null,
) {
    val isBundled: Boolean get() = bundledAssetPath != null
}

/**
 * Repository for portable `.zip` project templates. Imported packages are data-only here:
 * lifecycle hooks are stored but never executed without an explicit trust decision by a caller.
 */
class ProjectTemplateStore(
    private val context: Context,
) {
    private val templatesDir = File(context.filesDir, "linux-runtime/templates")
    private val json = Json { ignoreUnknownKeys = false; prettyPrint = true }

    fun list(): List<InstalledProjectTemplate> {
        val installed = templatesDir.walkTopDown()
            .filter { it.isDirectory && File(it, ProjectTemplateManifest.MANIFEST_PATH).isFile }
            .mapNotNull { directory -> runCatching { readInstalledTemplate(directory) }.getOrNull() }
            .toList()
        val bundled = discoverBundledDirectories().mapNotNull { relativePath ->
            runCatching {
                val manifest = context.assets.open("templates/$relativePath/${ProjectTemplateManifest.MANIFEST_PATH}")
                    .bufferedReader(Charsets.UTF_8).use { json.decodeFromString<ProjectTemplateManifest>(it.readText()) }
                validateManifest(manifest, allowBuiltinId = true)
                listOf(manifest.hooks.beforeCreate, manifest.hooks.afterCreate).filter(String::isNotBlank).forEach { hookPath ->
                    context.assets.open("templates/$relativePath/$hookPath").use { input ->
                        require(input.readBytes().size <= MAX_HOOK_BYTES) { "Script mẫu quá lớn: $hookPath" }
                    }
                }
                val syncedDirectory = File(templatesDir, relativePath)
                fun bundledPreview(path: String): File {
                    require(path.substringAfterLast('.', "").lowercase() in PREVIEW_EXTENSIONS) {
                        "Ảnh xem trước template chỉ hỗ trợ PNG, JPEG, WebP hoặc GIF"
                    }
                    val previewFile = File(syncedDirectory, path).takeIf(File::isFile) ?: run {
                        val suffix = path.substringAfterLast('.', "png")
                        val cacheName = path.substringBeforeLast('.').substringAfterLast('/').ifBlank { "preview" }
                        val cached = File(context.cacheDir, "template-previews/${manifest.id}-$cacheName.$suffix")
                        cached.parentFile?.mkdirs()
                        context.assets.open("templates/$relativePath/$path").use { input ->
                            cached.outputStream().use(input::copyTo)
                        }
                        cached
                    }
                    require(previewFile.length() <= MAX_PREVIEW_BYTES) { "Ảnh xem trước template quá lớn" }
                    validatePreviewDimensions(previewFile)
                    return previewFile
                }
                val preview = manifest.previewImage.takeIf(String::isNotBlank)?.let(::bundledPreview)
                InstalledProjectTemplate(
                    manifest = manifest,
                    directory = syncedDirectory,
                    previewFile = preview,
                    bundledAssetPath = "templates/$relativePath",
                )
            }.getOrNull()
        }
        return (installed + bundled)
            .distinctBy { it.manifest.id }
            .sortedWith(
            compareBy<InstalledProjectTemplate> { it.manifest.projectType.ordinal }
                .thenBy { it.manifest.category.sortOrder }
                .thenBy { it.manifest.category.name.lowercase() }
                .thenBy { it.manifest.name.lowercase() },
            )
    }

    fun find(id: String): InstalledProjectTemplate? = list().firstOrNull { it.manifest.id == id }

    fun hookScripts(id: String): List<Pair<String, String>> {
        val template = find(id) ?: error("Mẫu không tồn tại: $id")
        return listOf(template.manifest.hooks.beforeCreate, template.manifest.hooks.afterCreate)
            .filter(String::isNotBlank)
            .distinct()
            .map { relativePath ->
                val normalized = relativePath.replace('\\', '/')
                require(!normalized.startsWith('/') && !WINDOWS_ABSOLUTE_PATH.containsMatchIn(normalized) &&
                    normalized.startsWith("template-hooks/") && normalized.split('/').none { it == ".." }
                ) {
                    "Đường dẫn script mẫu không hợp lệ"
                }
                val bytes = template.bundledAssetPath?.let { root ->
                    context.assets.open("$root/$normalized").use { it.readBytes() }
                } ?: File(template.directory, normalized).canonicalFile.also { file ->
                    require(file.path.startsWith(template.directory.canonicalPath + File.separator) && file.isFile) {
                        "Script mẫu không tồn tại: $relativePath"
                    }
                }.readBytes()
                require(bytes.size <= MAX_HOOK_BYTES) { "Script mẫu quá lớn: $relativePath" }
                relativePath to bytes.toString(Charsets.UTF_8)
            }
    }

    fun importZip(input: InputStream): InstalledProjectTemplate {
        templatesDir.mkdirs()
        val staging = File(templatesDir, ".import-${UUID.randomUUID()}").canonicalFile
        check(staging.mkdirs()) { "Không thể tạo thư mục tạm nhập mẫu" }
        try {
            extractSafely(input, staging)
            val roots = staging.listFiles().orEmpty().filterNot { it.name == "__MACOSX" }
            val packageRoot = roots.singleOrNull()?.takeIf(File::isDirectory)?.let { nested ->
                if (File(nested, ProjectTemplateManifest.MANIFEST_PATH).isFile) nested else staging
            } ?: staging
            val installed = readInstalledTemplate(packageRoot)
            require(ID_PATTERN.matches(installed.manifest.id)) { "id mẫu không hợp lệ: ${installed.manifest.id}" }
            require(!installed.manifest.id.startsWith(BUILTIN_ID_PREFIX)) { "id mẫu không được dùng tiền tố dành riêng: $BUILTIN_ID_PREFIX" }
            require(installed.manifest.schemaVersion == ProjectTemplateManifest.CURRENT_SCHEMA_VERSION) {
                "Phiên bản quy chuẩn mẫu không được hỗ trợ: ${installed.manifest.schemaVersion}"
            }
            val typeDirectory = installed.manifest.projectType.name.lowercase()
            val target = File(templatesDir, "$typeDirectory/${installed.manifest.id}").canonicalFile
            check(target.path.startsWith(templatesDir.canonicalPath + File.separator)) { "Đường dẫn mục tiêu template vượt giới hạn" }
            target.parentFile?.mkdirs()
            check(!target.exists()) { "Mẫu đã tồn tại: ${installed.manifest.id}" }
            check(packageRoot.renameTo(target)) { "Không thể cài mẫu: ${installed.manifest.id}" }
            return readInstalledTemplate(target)
        } finally {
            SafeFileTree.delete(staging)
        }
    }

    fun exportZip(id: String, output: OutputStream) {
        val template = find(id) ?: error("Mẫu không tồn tại: $id")
        ZipOutputStream(output.buffered()).use { zip ->
            fun write(relative: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry("${template.manifest.id}/$relative"))
                zip.write(bytes)
                zip.closeEntry()
            }
            val assetRoot = template.bundledAssetPath
            if (assetRoot != null) {
                fun visit(assetPath: String, relativePath: String) {
                    val children = context.assets.list(assetPath).orEmpty()
                    if (children.isEmpty()) {
                        context.assets.open(assetPath).use { write(relativePath, it.readBytes()) }
                    } else children.forEach { child ->
                        visit("$assetPath/$child", if (relativePath.isBlank()) child else "$relativePath/$child")
                    }
                }
                visit(assetRoot, "")
            } else {
                template.directory.walkTopDown().filter(File::isFile).forEach { file ->
                    write(file.relativeTo(template.directory).invariantSeparatorsPath, file.readBytes())
                }
            }
        }
    }

    fun delete(id: String) {
        val template = find(id) ?: error("Mẫu không tồn tại: $id")
        require(!template.isBundled) { "Không thể xóa mẫu tích hợp" }
        require(template.directory.path.startsWith(templatesDir.canonicalPath + File.separator)) { "Thư mục mẫu vượt biên" }
        SafeFileTree.delete(template.directory)
        check(!template.directory.exists()) { "Xóa mẫu thất bại: $id" }
    }

    private fun readInstalledTemplate(directory: File): InstalledProjectTemplate {
        val manifestFile = File(directory, ProjectTemplateManifest.MANIFEST_PATH)
        val manifest = json.decodeFromString<ProjectTemplateManifest>(manifestFile.readText(Charsets.UTF_8))
        validateManifest(manifest, allowBuiltinId = false)
        val canonicalDirectory = directory.canonicalFile
        fun installedPreview(relativePath: String): File {
            require(!File(relativePath).isAbsolute && !WINDOWS_ABSOLUTE_PATH.containsMatchIn(relativePath.replace('\\', '/')) &&
                relativePath.replace('\\', '/').split('/').none { it == ".." }
            ) {
                "Đường dẫn ảnh xem trước mẫu vượt phạm vi"
            }
            require(relativePath.substringAfterLast('.', "").lowercase() in PREVIEW_EXTENSIONS) {
                "Ảnh xem trước template chỉ hỗ trợ PNG, JPEG, WebP hoặc GIF"
            }
            return File(canonicalDirectory, relativePath).canonicalFile.also { file ->
                require(file.path.startsWith(canonicalDirectory.path + File.separator) && file.isFile) {
                    "Ảnh xem trước mẫu không tồn tại"
                }
                require(file.length() <= MAX_PREVIEW_BYTES) { "Ảnh xem trước template quá lớn" }
                validatePreviewDimensions(file)
            }
        }
        val preview = manifest.previewImage.takeIf(String::isNotBlank)?.let(::installedPreview)
        listOf(manifest.hooks.beforeCreate, manifest.hooks.afterCreate).filter(String::isNotBlank).forEach { hookPath ->
            val hook = File(canonicalDirectory, hookPath).canonicalFile
            require(hook.path.startsWith(canonicalDirectory.path + File.separator) && hook.isFile) {
                "Script mẫu không tồn tại: $hookPath"
            }
            require(hook.length() <= MAX_HOOK_BYTES) { "Script mẫu quá lớn: $hookPath" }
        }
        return InstalledProjectTemplate(manifest, canonicalDirectory, preview)
    }

    private fun validateManifest(manifest: ProjectTemplateManifest, allowBuiltinId: Boolean) {
        require(manifest.schemaVersion == ProjectTemplateManifest.CURRENT_SCHEMA_VERSION) {
            "Phiên bản đặc tả mẫu không được hỗ trợ: ${manifest.schemaVersion}"
        }
        require(ID_PATTERN.matches(manifest.id)) { "Template id không hợp lệ: ${manifest.id}" }
        require(allowBuiltinId || !manifest.id.startsWith(BUILTIN_ID_PREFIX)) { "ID mẫu dùng tiền tố dành riêng" }
        require(CATEGORY_ID_PATTERN.matches(manifest.category.id)) { "id phân loại mẫu không hợp lệ" }
        require(manifest.variables.map { it.name }.distinct().size == manifest.variables.size) { "Tên biến template không được trùng" }
        manifest.variables.forEach { variable ->
            require(VARIABLE_NAME_PATTERN.matches(variable.name)) { "Tên biến template không hợp lệ: ${variable.name}" }
            require(variable.name !in FIXED_VARIABLES || !variable.prompt) {
                "Tên dự án và đường dẫn do Tri Phường cung cấp thống nhất, không thể khai báo là trường động: ${variable.name}"
            }
            require(variable.validationRegex.isBlank() || runCatching { Regex(variable.validationRegex) }.isSuccess) {
                "Regex biến mẫu không hợp lệ: ${variable.name}"
            }
            if (variable.inputType == ProjectTemplateInputType.SELECT) {
                require(variable.options.isNotEmpty()) { "Biến chọn phải cung cấp tùy chọn: ${variable.name}" }
                require(variable.options.map { it.value }.distinct().size == variable.options.size) {
                    "Giá trị tùy chọn của biến không được trùng lặp: ${variable.name}"
                }
            }
            if (!variable.prompt && variable.required && variable.defaultValue.isBlank()) {
                require(variable.name in DERIVED_VARIABLES) { "Biến ẩn bắt buộc phải cung cấp giá trị mặc định: ${variable.name}" }
            }
        }
        fun validateTemplatePath(path: String) {
            val normalized = path.replace('\\', '/')
            require(normalized.isNotBlank() && !normalized.startsWith('/') &&
                !WINDOWS_ABSOLUTE_PATH.containsMatchIn(normalized) && normalized.split('/').none { it == ".." }
            ) { "Đường dẫn kiểm tra mẫu không hợp lệ: $path" }
        }
        manifest.validation.requiredFiles.forEach(::validateTemplatePath)
        manifest.validation.forbiddenFiles.forEach(::validateTemplatePath)
        manifest.validation.contentRules.forEach { rule -> validateTemplatePath(rule.path) }
        require(manifest.validation.requiredFiles.distinct().size == manifest.validation.requiredFiles.size) {
            "Tệp bắt buộc của mẫu không được trùng lặp"
        }
        require(manifest.validation.forbiddenFiles.distinct().size == manifest.validation.forbiddenFiles.size) {
            "Tệp cấm của mẫu không được trùng lặp"
        }
        listOf(manifest.hooks.beforeCreate, manifest.hooks.afterCreate).filter(String::isNotBlank).forEach { hook ->
            val normalized = hook.replace('\\', '/')
            require(!normalized.startsWith('/') && !WINDOWS_ABSOLUTE_PATH.containsMatchIn(normalized) &&
                normalized.startsWith("template-hooks/") && normalized.split('/').none { it == ".." }
            ) {
                "Script template phải nằm trong thư mục template-hooks/"
            }
        }
    }

    private fun validatePreviewDimensions(file: File) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Không thể giải mã ảnh xem trước của mẫu" }
        require(bounds.outWidth == PREVIEW_SIZE && bounds.outHeight == PREVIEW_SIZE) {
            "Ảnh xem trước mẫu phải là ${PREVIEW_SIZE}×${PREVIEW_SIZE} pixel (1:1)"
        }
    }

    private fun discoverBundledDirectories(): List<String> {
        val result = mutableListOf<String>()
        fun visit(relativePath: String) {
            val assetPath = if (relativePath.isBlank()) "templates" else "templates/$relativePath"
            val children = context.assets.list(assetPath).orEmpty()
            if (ProjectTemplateManifest.MANIFEST_PATH in children) {
                result += relativePath
            } else children.forEach { child ->
                val childRelative = if (relativePath.isBlank()) child else "$relativePath/$child"
                if (context.assets.list("templates/$childRelative").orEmpty().isNotEmpty()) visit(childRelative)
            }
        }
        visit("")
        return result
    }

    private fun extractSafely(input: InputStream, targetDir: File) {
        var entries = 0
        var totalBytes = 0L
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entries <= MAX_ENTRIES) { "Số lượng tệp trong gói mẫu quá nhiều" }
                val name = entry.name.replace('\\', '/')
                require(!name.startsWith('/') && !WINDOWS_ABSOLUTE_PATH.containsMatchIn(name)) {
                    "Gói mẫu chứa đường dẫn tuyệt đối"
                }
                require(name.isNotBlank() && name.split('/').none { it == ".." }) { "Gói mẫu chứa đường dẫn vượt giới hạn" }
                val target = File(targetDir, name).canonicalFile
                require(target.path.startsWith(targetDir.canonicalPath + File.separator)) { "Gói mẫu chứa đường dẫn vượt giới hạn" }
                if (entry.isDirectory) {
                    check(target.isDirectory || target.mkdirs()) { "Không thể tạo thư mục mẫu" }
                } else {
                    check(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true)
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var fileBytes = 0L
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            totalBytes += read
                            require(fileBytes <= MAX_FILE_BYTES && totalBytes <= MAX_TOTAL_BYTES) { "Dung lượng gói mẫu quá lớn" }
                            output.write(buffer, 0, read)
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        require(entries > 0) { "Gói mẫu rỗng" }
    }

    private companion object {
        val ID_PATTERN = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
        val CATEGORY_ID_PATTERN = Regex("^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
        val VARIABLE_NAME_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*$")
        val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/")
        const val BUILTIN_ID_PREFIX = "builtin."
        val DERIVED_VARIABLES = setOf(
            "projectName", "appName", "packageName", "packagePath", "projectPath",
        )
        val FIXED_VARIABLES = setOf("projectName", "projectPath", "packagePath")
        val PREVIEW_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif")
        const val MAX_ENTRIES = 5_000
        const val MAX_FILE_BYTES = 16L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 128L * 1024 * 1024
        const val MAX_PREVIEW_BYTES = 4L * 1024 * 1024
        const val MAX_HOOK_BYTES = 1024L * 1024
        const val PREVIEW_SIZE = 270
    }
}
