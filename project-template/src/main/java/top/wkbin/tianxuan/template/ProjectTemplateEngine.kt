package top.wkbin.tianxuan.template

import android.content.Context
import java.io.File
import kotlinx.serialization.json.Json

class ProjectTemplateEngine(
    private val context: Context,
) {
    private val templatesDir = File(context.filesDir, "linux-runtime/templates")
    private val json = Json { ignoreUnknownKeys = false }
    private val bundledDirectories: Map<String, String> by lazy {
        runCatching {
            discoverBundledDirectories().associateBy { relativePath ->
                val bytes = context.assets.open("templates/$relativePath/${ProjectTemplateManifest.MANIFEST_PATH}")
                    .use { it.readBytes() }
                json.decodeFromString<ProjectTemplateManifest>(bytes.toString(Charsets.UTF_8)).id
            }
        }.getOrDefault(emptyMap())
    }

    fun inspect(id: String): ProjectTemplateManifest {
        val source = resolveSource(id)
        val manifest = json.decodeFromString<ProjectTemplateManifest>(source.read(ProjectTemplateManifest.MANIFEST_PATH).toString(Charsets.UTF_8))
        require(manifest.id == id) { "Định danh mẫu không khớp: ${manifest.id}" }
        require(manifest.schemaVersion == ProjectTemplateManifest.CURRENT_SCHEMA_VERSION) {
            "Phiên bản đặc tả mẫu không được hỗ trợ: ${manifest.schemaVersion}"
        }
        return manifest
    }

    fun resolvedValues(manifest: ProjectTemplateManifest, supplied: Map<String, String>): Map<String, String> =
        manifest.variables.associate { variable ->
            variable.name to if (variable.name in supplied) supplied.getValue(variable.name) else variable.defaultValue
        } + supplied

    fun materialize(id: String, projectDir: File, values: Map<String, String>): ProjectTemplateManifest {
        val source = resolveSource(id)
        val manifest = inspect(id)
        val resolvedValues = resolvedValues(manifest, values)
        validateVariables(manifest, resolvedValues)

        fun replaceVariables(text: String): String {
            var result = text
            resolvedValues.forEach { (name, value) -> result = result.replace("{{$name}}", value) }
            val unresolved = PLACEHOLDER.find(result)?.value
            require(unresolved == null) { "Mẫu chứa biến chưa gán giá trị: $unresolved" }
            return result
        }

        fun outputPath(relativePath: String): String {
            val packagePath = resolvedValues["packagePath"].orEmpty()
            return replaceVariables(
                relativePath
                    .replace("TIANXUAN_PACKAGE_PATH", packagePath),
            ).removeSuffix(".template")
        }

        fun write(relativePath: String, bytes: ByteArray) {
            val normalizedPath = relativePath.replace('\\', '/')
            val previewPaths = listOf(manifest.previewImage)
                .filter(String::isNotBlank)
                .map { it.replace('\\', '/') }
                .toSet()
            if (relativePath == ProjectTemplateManifest.MANIFEST_PATH ||
                normalizedPath.startsWith("template-hooks/") ||
                normalizedPath in previewPaths
            ) return
            val target = File(projectDir, outputPath(relativePath)).canonicalFile
            require(target.path.startsWith(projectDir.canonicalPath + File.separator)) { "Đường dẫn mẫu vượt giới hạn: $relativePath" }
            target.parentFile?.mkdirs()
            if (isTextFile(relativePath, target.name)) {
                target.writeText(replaceVariables(bytes.toString(Charsets.UTF_8)), Charsets.UTF_8)
            } else target.writeBytes(bytes)
        }

        val sourceFiles = source.files().toList()
        val validationPaths = manifest.validation.requiredFiles + manifest.validation.forbiddenFiles +
            manifest.validation.contentRules.map { it.path }
        require(
            (sourceFiles + validationPaths).none { "TIANXUAN_PACKAGE_PATH" in it } ||
                resolvedValues["packagePath"].orEmpty().isNotBlank(),
        ) { "Mẫu đã dùng TIANXUAN_PACKAGE_PATH nhưng chưa khai báo packageName hoặc packagePath" }
        sourceFiles.forEach { relativePath -> write(relativePath, source.read(relativePath)) }
        return manifest
    }

    fun validateMaterialized(id: String, projectDir: File, values: Map<String, String>) {
        val manifest = inspect(id)
        val resolvedValues = resolvedValues(manifest, values)
        validateVariables(manifest, resolvedValues)
        fun replaceVariables(text: String): String {
            var result = text
            resolvedValues.forEach { (name, value) -> result = result.replace("{{$name}}", value) }
            val unresolved = PLACEHOLDER.find(result)?.value
            require(unresolved == null) { "Mẫu chứa biến chưa gán giá trị: $unresolved" }
            return result
        }
        fun outputPath(relativePath: String): String = replaceVariables(
            relativePath.replace("TIANXUAN_PACKAGE_PATH", resolvedValues["packagePath"].orEmpty()),
        ).removeSuffix(".template")
        validateOutput(manifest, projectDir, ::outputPath, ::replaceVariables)
    }

    fun readHook(id: String, relativePath: String): ByteArray {
        val normalized = relativePath.replace('\\', '/')
        require(!normalized.startsWith('/') && !WINDOWS_ABSOLUTE_PATH.containsMatchIn(normalized) &&
            normalized.startsWith("template-hooks/") && normalized.split('/').none { it == ".." }
        ) {
            "Script template phải nằm trong thư mục template-hooks/"
        }
        return resolveSource(id).read(normalized)
    }

    private fun validateVariables(manifest: ProjectTemplateManifest, values: Map<String, String>) {
        manifest.variables.forEach { variable ->
            val value = values[variable.name].orEmpty()
            require(!variable.required || value.isNotBlank()) { "Biến mẫu không được rỗng: ${variable.label}" }
            if (value.isNotBlank() && variable.validationRegex.isNotBlank()) {
                // NOTE: validationRegex 来自模板清单（可能由外部导入），坏正则会抛
                // PatternSyntaxException；统一按"Xác minh không đạt"处理而非崩溃。
                val valid = runCatching { Regex(variable.validationRegex).matches(value) }.getOrDefault(false)
                require(valid) { "Định dạng biến mẫu không hợp lệ: ${variable.label}" }
            }
            if (variable.inputType == ProjectTemplateInputType.SELECT && value.isNotBlank()) {
                require(variable.options.any { it.value == value }) { "Tùy chọn biến mẫu không hợp lệ: ${variable.label}" }
            }
        }
    }

    private fun resolveSource(id: String): TemplateSource = bundledDirectories[id]?.let { relativePath ->
        AssetTemplateSource("templates/$relativePath")
    } ?: DirectoryTemplateSource(findInstalledDirectory(id))

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

    private fun findInstalledDirectory(id: String): File = templatesDir.walkTopDown()
        .filter(File::isDirectory)
        .firstOrNull { directory ->
            val manifest = File(directory, ProjectTemplateManifest.MANIFEST_PATH)
            manifest.isFile && runCatching {
                json.decodeFromString<ProjectTemplateManifest>(manifest.readText(Charsets.UTF_8)).id == id
            }.getOrDefault(false)
        } ?: error("Mẫu dự án không khả dụng: $id")

    private fun validateOutput(
        manifest: ProjectTemplateManifest,
        projectDir: File,
        outputPath: (String) -> String,
        replaceVariables: (String) -> String,
    ) {
        fun validatedFile(relativePath: String): File {
            val file = File(projectDir, outputPath(relativePath)).canonicalFile
            require(file.path.startsWith(projectDir.canonicalPath + File.separator)) {
                "Đường dẫn kiểm tra mẫu vượt giới hạn: $relativePath"
            }
            return file
        }
        manifest.validation.requiredFiles.forEach { relativePath ->
            require(validatedFile(relativePath).isFile) { "Mẫu thiếu tệp bắt buộc: ${outputPath(relativePath)}" }
        }
        manifest.validation.forbiddenFiles.forEach { relativePath ->
            require(!validatedFile(relativePath).exists()) { "Template tạo ra tệp bị cấm: ${outputPath(relativePath)}" }
        }
        manifest.validation.contentRules.forEach { rule ->
            val relativePath = outputPath(rule.path)
            val file = validatedFile(rule.path)
            require(file.isFile) { "Tệp kiểm tra nội dung mẫu không tồn tại: $relativePath" }
            val content = file.readText(Charsets.UTF_8)
            if (rule.equals.isNotEmpty()) {
                require(content.trim() == replaceVariables(rule.equals)) { "Nội dung tệp mẫu không đáp ứng yêu cầu: $relativePath" }
            }
            rule.contains.forEach { expected ->
                require(replaceVariables(expected) in content) { "Tệp mẫu thiếu nội dung bắt buộc: $relativePath" }
            }
            rule.excludes.forEach { forbidden ->
                require(replaceVariables(forbidden) !in content) { "Tệp mẫu chứa nội dung bị cấm: $relativePath" }
            }
        }
    }

    private fun isTextFile(relativePath: String, name: String): Boolean = relativePath.endsWith(".template") ||
        name.substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS || name in TEXT_FILE_NAMES

    private interface TemplateSource {
        fun read(relativePath: String): ByteArray
        fun files(): Sequence<String>
    }

    private inner class AssetTemplateSource(private val root: String) : TemplateSource {
        override fun read(relativePath: String): ByteArray =
            context.assets.open("$root/$relativePath").use { it.readBytes() }

        override fun files(): Sequence<String> = sequence {
            suspend fun SequenceScope<String>.visit(assetPath: String, relativePath: String) {
                val children = context.assets.list(assetPath).orEmpty()
                if (children.isEmpty()) yield(relativePath) else children.forEach { child ->
                    visit("$assetPath/$child", if (relativePath.isBlank()) child else "$relativePath/$child")
                }
            }
            visit(root, "")
        }
    }

    private class DirectoryTemplateSource(private val root: File) : TemplateSource {
        override fun read(relativePath: String): ByteArray {
            val file = File(root, relativePath).canonicalFile
            require(file.path.startsWith(root.canonicalPath + File.separator) && file.isFile) { "Tệp template không tồn tại: $relativePath" }
            return file.readBytes()
        }

        override fun files(): Sequence<String> = root.walkTopDown().filter(File::isFile)
            .map { it.relativeTo(root).invariantSeparatorsPath }
    }

    companion object {
        private val PLACEHOLDER = Regex("\\{\\{[A-Za-z][A-Za-z0-9_]*\\}\\}")
        private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/")
        private val TEXT_EXTENSIONS = setOf(
            "c", "cc", "cpp", "css", "dart", "gradle", "h", "hpp", "html", "java", "js", "json",
            "kt", "kts", "md", "properties", "pro", "py", "rs", "sh", "toml", "ts", "txt", "xml",
            "yaml", "yml",
        )
        private val TEXT_FILE_NAMES = setOf(
            "Dockerfile", "Gemfile", "Makefile", "Podfile", "analysis_options.yaml", "gradlew", "pubspec.yaml",
            "xposed_init",
        )
    }
}
