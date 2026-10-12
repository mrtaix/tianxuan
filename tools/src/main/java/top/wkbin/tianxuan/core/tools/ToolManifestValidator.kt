package top.wkbin.tianxuan.core.tools

import top.wkbin.tianxuan.core.model.ToolManifest

/**
 * Validates the data-only part of a registry before it can affect the app.
 * Manifests never carry executable shell commands; adapters remain allow-listed
 * in the app and are selected by tool id.
 */
object ToolManifestValidator {
    private val idPattern = Regex("[a-z0-9][a-z0-9-]{1,63}")

    /**
     * `version` 直接参与本地插件的落盘路径（`plugins/<id>/<version>/`），因此必须
     * 用白名单约束字符集，而不是只判非空。此前只做 `isNotBlank()`，形如
     * `"../../shared_prefs/x"` 的版本号会让 `File(localRoot, "$id/$version")`
     * 归一化后**逃出** `files/plugins`，把攻击者可控的 ZIP 内容写到应用私有目录之外
     * （同版本覆盖分支还会先 `renameTo` 把目标搬走、再 `deleteRecursively`）。
     * 白名单同时覆盖 `1.2.0`、`1.2.0-beta.1`、`v2`、`2025.10.07` 等常见写法。
     */
    private val versionPattern = Regex("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")
    private val allowedDependencies = setOf("node", "python", "git", "curl", "ca-certificates")
    private val allowedLaunchTypes = setOf("one_shot", "pty", "web", "service", "command")
    private val allowedPermissions = setOf("NETWORK", "WORKSPACE_READ", "WORKSPACE_WRITE", "LOCAL_WEB")
    private val allowedUpdateStrategies = setOf("REINSTALL", "IN_PLACE")
    private val allowedInstallMethods = setOf("SCRIPT", "LOCAL_PACKAGE")

    fun validateAll(manifests: List<ToolManifest>): List<ToolManifest> {
        require(manifests.isNotEmpty()) { "Danh sách công cụ không được rỗng" }
        manifests.forEach { manifest ->
            require(manifest.schemaVersion == 1) { "Schema danh sách công cụ không được hỗ trợ: ${manifest.schemaVersion}" }
            require(idPattern.matches(manifest.id)) { "ID công cụ bất hợp pháp: ${manifest.id}" }
            require(manifest.name.isNotBlank()) { "Tên công cụ không được rỗng: ${manifest.id}" }
            require(manifest.description.isNotBlank()) { "Mô tả công cụ không được rỗng: ${manifest.id}" }
            require(manifest.publisher.length <= 128) { "Tên nhà phát hành công cụ quá dài: ${manifest.id}" }
            require(manifest.version.isNotBlank()) { "Phiên bản công cụ không được rỗng: ${manifest.id}" }
            // 锚定匹配：即便版本串两侧被插入空白/控制字符也不会被放行。
            require(versionPattern.matches(manifest.version)) {
                "Phiên bản công cụ bất hợp pháp: ${manifest.id} / ${manifest.version}"
            }
            val latest = manifest.latestVersion
            require(latest == null || latest.isNotBlank()) {
                "Phiên bản mới nhất của công cụ không được rỗng: ${manifest.id}"
            }
            require(latest == null || versionPattern.matches(latest)) {
                "Phiên bản công cụ mới nhất bất hợp pháp: ${manifest.id} / $latest"
            }
            require(manifest.architectures.any { it.equals("ARM64", ignoreCase = true) }) {
                "Công cụ không hỗ trợ ARM64: ${manifest.id}"
            }
            require(manifest.launchType in allowedLaunchTypes) {
                "Loại khởi chạy không được hỗ trợ: ${manifest.launchType}"
            }
            require(manifest.updateStrategy in allowedUpdateStrategies) {
                "Chiến lược cập nhật không được hỗ trợ: ${manifest.updateStrategy}"
            }
            require(manifest.installMethod in allowedInstallMethods) { "Phương thức cài đặt không được hỗ trợ: ${manifest.installMethod}" }
            require(manifest.source in setOf("REMOTE", "LOCAL")) { "Nguồn công cụ không được hỗ trợ: ${manifest.source}" }
            if (manifest.source == "LOCAL") {
                require(manifest.offlineOnly) { "Plugin cục bộ bắt buộc khai báo offlineOnly=true: ${manifest.id}" }
                require(manifest.installMethod == "LOCAL_PACKAGE") { "Plugin cục bộ phải dùng LOCAL_PACKAGE: ${manifest.id}" }
            }
            manifest.permissions.forEach { permission ->
                require(permission in allowedPermissions) { "Quyền công cụ không được hỗ trợ: $permission" }
            }
            manifest.homepage?.let { homepage ->
                require(homepage.startsWith("https://", ignoreCase = true)) {
                    "Trang chủ công cụ phải dùng HTTPS: ${manifest.id}"
                }
            }
            manifest.servicePort?.let { port ->
                require(port in 1..65535) { "Cổng dịch vụ cục bộ không hợp lệ: ${manifest.id}" }
                require(manifest.launchType in setOf("web", "service")) {
                    "Chỉ công cụ web/service mới có thể khai báo dịch vụ cục bộ: ${manifest.id}"
                }
                require(manifest.servicePath.startsWith('/') && !manifest.servicePath.contains("..")) {
                    "Đường dẫn dịch vụ cục bộ không an toàn: ${manifest.id}"
                }
            }
            if (!manifest.offlineOnly) {
                manifest.dependencies.forEach { dependency ->
                    val parsed = ManifestDependencyParser.parse(dependency)
                    require(parsed != null && parsed.name in allowedDependencies) {
                        "Loại phụ thuộc không được hỗ trợ: $dependency"
                    }
                }
            }
        }
        require(manifests.map { it.id }.toSet().size == manifests.size) { "ID công cụ bị trùng" }
        return manifests
    }
}
