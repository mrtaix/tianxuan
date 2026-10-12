package top.wkbin.tianxuan.harness.mcp

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 工作区感知的 MCP 预设推荐器（Harness 2.0 Phase 3.1 的第一块切片）。
 *
 * 扫描工作区顶层目录的文件特征，匹配内置 MCP 预设的启用信号：
 * - `.git` 目录 → Git 协同中心
 * - `*.db / *.sqlite*` → SQLite 探索器
 * - `*.apk` → Android 逆向与 APK 审计
 * - 代码文件达到密度阈值 → CodeGraph 代码知识图谱
 *
 * 只做轻量顶层扫描（不递归、限制条目数），结果供 UI 提示用户一键启用；
 * 不自动启用任何服务，启用决定权始终在用户。
 */
class McpWorkspaceRecommender() {

    data class Recommendation(
        val presetId: String,
        val presetName: String,
        val reason: String,
    )

    suspend fun recommend(workspaceDir: File?): List<Recommendation> = withContext(Dispatchers.IO) {
        val root = workspaceDir?.takeIf { it.isDirectory } ?: return@withContext emptyList()
        val entries = runCatching { root.listFiles()?.toList().orEmpty() }
            .getOrDefault(emptyList())
            .take(MAX_SCAN_ENTRIES)
        if (entries.isEmpty()) return@withContext emptyList()

        val recommendations = mutableListOf<Recommendation>()
        if (entries.any { it.isDirectory && it.name == ".git" }) {
            recommendations += Recommendation(
                presetId = "mcp_git",
                presetName = "Trung tâm cộng tác kho Git",
                reason = "Phát hiện kho Git (.git), sau khi bật Agent có thể trực tiếp phân tích lịch sử commit, nhánh và Diff",
            )
        }
        if (entries.any { it.isFile && it.extension.lowercase() in SQLITE_EXTENSIONS }) {
            recommendations += Recommendation(
                presetId = "mcp_sqlite",
                presetName = "Trình khám phá cơ sở dữ liệu SQLite",
                reason = "Phát hiện tệp cơ sở dữ liệu SQLite, sau khi bật Agent có thể truy vấn và phân tích dữ liệu trực tiếp",
            )
        }
        if (entries.any { it.isFile && it.extension.lowercase() == "apk" }) {
            recommendations += Recommendation(
                presetId = "mcp_apktool",
                presetName = "Dịch ngược Android và kiểm toán APK",
                reason = "Phát hiện tệp APK, sau khi bật Agent có thể dịch ngược, phân tích quyền trong manifest và mã Smali",
            )
        }
        val codeFileCount = entries.count { it.isFile && it.extension.lowercase() in CODE_EXTENSIONS }
        if (codeFileCount >= CODE_FILE_THRESHOLD) {
            recommendations += Recommendation(
                presetId = "mcp_codegraph",
                presetName = "Đồ thị tri thức mã CodeGraph",
                reason = "Phát hiện $codeFileCount tệp mã, sau khi bật có thể lập chỉ mục ký hiệu, giảm số lượt gọi khi khám phá mã",
            )
        }
        recommendations
    }

    companion object {
        private val SQLITE_EXTENSIONS = setOf("db", "sqlite", "sqlite3")
        private val CODE_EXTENSIONS = setOf(
            "kt", "java", "py", "js", "ts", "jsx", "tsx",
            "c", "cc", "cpp", "h", "hpp", "rs", "go", "smali",
        )
        private const val CODE_FILE_THRESHOLD = 3
        private const val MAX_SCAN_ENTRIES = 500
    }
}
