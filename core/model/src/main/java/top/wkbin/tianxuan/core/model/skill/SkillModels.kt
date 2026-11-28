package top.wkbin.tianxuan.core.model.skill

import kotlinx.serialization.Serializable

/**
 * Ranh giới quyền hệ thống mà skill xin cấp.
 */
@Serializable
enum class SkillPermission(
    val id: String,
    val label: String,
    val description: String,
    val isHighRisk: Boolean,
) {
    EXEC_COMMAND(
        id = "exec_command",
        label = "Thực thi lệnh",
        description = "Cho phép thực thi lệnh Shell hoặc gọi script đi kèm trong sandbox Linux PRoot",
        isHighRisk = true,
    ),
    NETWORK(
        id = "network",
        label = "Truy cập mạng",
        description = "Cho phép gửi yêu cầu mạng tới máy chủ Internet công cộng hoặc mạng nội bộ",
        isHighRisk = true,
    ),
    FILE_WRITE(
        id = "file_write",
        label = "Ghi tệp hệ thống",
        description = "Cho phép tạo, sửa hoặc xoá tệp trong workspace hoặc bên trong sandbox",
        isHighRisk = false,
    ),
    BROWSER_AUTOMATION(
        id = "browser_automation",
        label = "Điều khiển trình duyệt",
        description = "Cho phép điều khiển trình duyệt headless tích hợp để render trang, chụp ảnh nhanh hoặc phân tích DOM",
        isHighRisk = true,
    ),
    BACKGROUND_SERVICE(
        id = "background_service",
        label = "Thường trú nền",
        description = "Cho phép đăng ký và khởi chạy dịch vụ tiến trình nền thường trú dài hạn trong sandbox",
        isHighRisk = false,
    ),
    SYSTEM_PROBE(
        id = "system_probe",
        label = "Dò hệ thống",
        description = "Cho phép dò mức sử dụng CPU, bộ nhớ của sandbox và danh sách tiến trình đang chạy",
        isHighRisk = false,
    );

    companion object {
        fun fromIdOrNull(id: String): SkillPermission? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}

/**
 * Khai báo siêu dữ liệu chuẩn của gói skill (tương ứng Frontmatter của SKILL.md hoặc manifest.json).
 */
@Serializable
data class SkillManifest(
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val description: String,
    val author: String = "TianXuan Community",
    val license: String? = "Apache-2.0",
    val icon: String = "Sparkles",
    val category: String = "Phổ thông",
    val tags: List<String> = emptyList(),
    val minTianXuanVersion: String = "1.0.0",
    val compatibleRuntimes: List<String> = listOf("debian", "proot"),
    val permissions: List<SkillPermission> = emptyList(),
    val requiredTools: List<String> = emptyList(),
    val triggerCommand: String? = null,
    val homepage: String? = null,
    val repository: String? = null,
)

/**
 * Tập hợp template prompt khai báo đã được phân rã (theo chuẩn TianXuan).
 */
@Serializable
data class PromptTemplateBundle(
    val skillMd: String? = null,
    val agentMd: String? = null,
    val soulMd: String? = null,
    val toolsMd: String? = null,
    val userMd: String? = null,
    val memoryMd: String? = null,
    val extraFiles: Map<String, String> = emptyMap(),
) {
    /**
     * Tự động lắp ghép để sinh system prompt.
     */
    fun composeSystemPrompt(resourceGuestPath: String? = null): String = buildString {
        // 1. Điểm vào và tổng cương của skill
        skillMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append(it)
            append("\n\n")
        }

        // 2. Vai trò và hướng dẫn thực thi (AGENT)
        agentMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Vai trò và chỉ thị cốt lõi (AGENT)】\n")
            append(it)
            append("\n\n")
        }

        // 3. Nhân cách, tư duy và phong cách giao tiếp (SOUL)
        soulMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Ranh giới nhân cách và phong cách (SOUL)】\n")
            append(it)
            append("\n\n")
        }

        // 4. Quy ước sử dụng công cụ (TOOLS)
        toolsMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Quy chuẩn sử dụng công cụ (TOOLS)】\n")
            append(it)
            append("\n\n")
        }

        // 5. Giao kèo ngữ cảnh người dùng (USER)
        userMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Giao kèo tương tác người dùng (USER)】\n")
            append(it)
            append("\n\n")
        }

        // 6. Ghi nhớ dài hạn và lát cắt sự kiện ban đầu (MEMORY)
        memoryMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Ghi nhớ sự kiện khởi tạo (MEMORY)】\n")
            append(it)
            append("\n\n")
        }

        // 7. Gợi ý gắn kết thư mục tài nguyên vật lý của sandbox
        if (!resourceGuestPath.isNullOrBlank()) {
            append("### 【Tài nguyên Skill và gắn kết sandbox】\n")
            append("Đường dẫn vật lý tài nguyên: `$resourceGuestPath`\n")
            append("Nếu cần gọi script scripts/ hoặc tài sản references/ đi kèm Skill này, hãy thực thi trong thư mục này.\n")
        }
    }.trim()
}

/**
 * Mô hình gói skill sau khi phân rã đầy đủ.
 */
data class SkillPackage(
    val manifest: SkillManifest,
    val templates: PromptTemplateBundle,
    val scripts: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val rawFiles: Map<String, ByteArray> = emptyMap(),
)

/**
 * Cấp độ kiểm toán an toàn tĩnh.
 */
@Serializable
enum class AuditLevel(val severity: Int, val label: String) {
    SAFE(0, "An toàn"),
    INFO(1, "Gợi ý"),
    WARNING(2, "Cảnh báo"),
    DANGER(3, "Nguy hiểm cao"),
    BLOCKED(4, "Chặn nghiêm trọng");
}

/**
 * Mục phát hiện của kiểm toán an toàn.
 */
@Serializable
data class AuditFinding(
    val level: AuditLevel,
    val ruleId: String,
    val title: String,
    val detail: String,
    val targetFile: String? = null,
    val line: Int? = null,
    val snippet: String? = null,
)

/**
 * Báo cáo kiểm toán an toàn tĩnh đầy đủ.
 */
@Serializable
data class SecurityAuditReport(
    val level: AuditLevel,
    val findings: List<AuditFinding> = emptyList(),
    val declaredPermissions: Set<SkillPermission> = emptySet(),
    val detectedPermissions: Set<SkillPermission> = emptySet(),
    val undeclaredPermissions: Set<SkillPermission> = emptySet(),
) {
    val isBlocked: Boolean get() = level == AuditLevel.BLOCKED
    val isSafe: Boolean get() = level == AuditLevel.SAFE
    val hasWarnings: Boolean get() = level >= AuditLevel.WARNING
}

/**
 * Cấp độ đánh giá tương thích.
 */
@Serializable
enum class CompatibilityLevel(val label: String) {
    COMPATIBLE("Tương thích hoàn toàn"),
    PARTIALLY_COMPATIBLE("Tương thích một phần (thiếu phụ thuộc hoặc bị hạ cấp)"),
    INCOMPATIBLE("Không tương thích");
}

/**
 * Báo cáo đánh giá tương thích của skill.
 */
@Serializable
data class SkillCompatibilityResult(
    val level: CompatibilityLevel,
    val missingTools: List<String> = emptyList(),
    val unsupportedRuntimes: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
) {
    val isCompatible: Boolean get() = level != CompatibilityLevel.INCOMPATIBLE
}

/**
 * Bản tóm tắt mục trong chợ skill trực tuyến ClawHub.
 */
@Serializable
data class ClawHubMarketItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val icon: String = "Sparkles",
    val tags: List<String> = emptyList(),
    val category: String = "Phổ thông",
    val downloadUrl: String,
    val stars: Int = 0,
    val isInstalled: Boolean = false,
    val requiredTools: List<String> = emptyList(),
    val permissions: List<SkillPermission> = emptyList(),
)

/**
 * Chi tiết skill trên ClawHub.
 */
@Serializable
data class ClawHubMarketDetail(
    val item: ClawHubMarketItem,
    val readmeMarkdown: String? = null,
    val license: String? = "Apache-2.0",
    val templateSummary: List<String> = emptyList(),
    val sampleScripts: List<String> = emptyList(),
)
