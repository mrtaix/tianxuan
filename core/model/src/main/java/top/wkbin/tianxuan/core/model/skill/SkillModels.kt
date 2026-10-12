package top.wkbin.tianxuan.core.model.skill

import kotlinx.serialization.Serializable

/**
 * 技能所申请的系统权限边界。
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
        description = "Cho phép thực thi lệnh Shell hoặc gọi script đi kèm trong sandbox PRoot Linux",
        isHighRisk = true,
    ),
    NETWORK(
        id = "network",
        label = "Truy cập mạng",
        description = "Cho phép phát yêu cầu mạng tới máy chủ công cộng hoặc mạng cục bộ bên ngoài",
        isHighRisk = true,
    ),
    FILE_WRITE(
        id = "file_write",
        label = "Ghi hệ thống tệp",
        description = "Cho phép tạo, sửa hoặc xóa tệp trong workspace hoặc nội bộ sandbox",
        isHighRisk = false,
    ),
    BROWSER_AUTOMATION(
        id = "browser_automation",
        label = "Điều khiển trình duyệt",
        description = "Cho phép điều khiển trình duyệt không giao diện tích hợp để render trang, chụp snapshot hoặc phân tích DOM",
        isHighRisk = true,
    ),
    BACKGROUND_SERVICE(
        id = "background_service",
        label = "Thường trú nền",
        description = "Cho phép đăng ký và khởi động dịch vụ tiến trình nền thường trú dài hạn trong sandbox",
        isHighRisk = false,
    ),
    SYSTEM_PROBE(
        id = "system_probe",
        label = "Dò hệ thống",
        description = "Cho phép dò CPU sandbox, mức sử dụng bộ nhớ và danh sách tiến trình đang chạy",
        isHighRisk = false,
    );

    companion object {
        fun fromIdOrNull(id: String): SkillPermission? =
            entries.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }
    }
}

/**
 * 技能包标准元数据声明（对应 SKILL.md Frontmatter 或 manifest.json）。
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
    val category: String = "Chung",
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
 * 声明式提示词模板解构集合（对标天玄标准）。
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
     * 智能组装生成系统提示词 (System Prompt)。
     */
    fun composeSystemPrompt(resourceGuestPath: String? = null): String = buildString {
        // 1. 技能入口与总纲
        skillMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append(it)
            append("\n\n")
        }

        // 2. 角色与执行指引 (AGENT)
        agentMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Vai trò cốt lõi và chỉ thị (AGENT)】\n")
            append(it)
            append("\n\n")
        }

        // 3. 人格心智与沟通风格 (SOUL)
        soulMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Ranh giới nhân cách và phong cách (SOUL)】\n")
            append(it)
            append("\n\n")
        }

        // 4. 工具使用约定 (TOOLS)
        toolsMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Quy chuẩn sử dụng công cụ (TOOLS)】\n")
            append(it)
            append("\n\n")
        }

        // 5. 用户上下文契约 (USER)
        userMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Hợp đồng tương tác người dùng (USER)】\n")
            append(it)
            append("\n\n")
        }

        // 6. 长期记忆与初始事实切片 (MEMORY)
        memoryMd?.trim()?.takeIf { it.isNotEmpty() }?.let {
            append("### 【Khởi tạo ký ức sự kiện (MEMORY)】\n")
            append(it)
            append("\n\n")
        }

        // 7. 沙箱物理资源目录挂载提示
        if (!resourceGuestPath.isNullOrBlank()) {
            append("### 【Tài nguyên Skill và mount sandbox】\n")
            append("Đường dẫn vật lý tài nguyên: `$resourceGuestPath`\n")
            append("Nếu cần gọi script scripts/ hoặc tài nguyên references/ đi kèm Skill này, hãy thực thi trong thư mục này.\n")
        }
    }.trim()
}

/**
 * 完整解构后的技能包模型。
 */
data class SkillPackage(
    val manifest: SkillManifest,
    val templates: PromptTemplateBundle,
    val scripts: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val rawFiles: Map<String, ByteArray> = emptyMap(),
)

/**
 * 静态安全审查等级。
 */
@Serializable
enum class AuditLevel(val severity: Int, val label: String) {
    SAFE(0, "An toàn"),
    INFO(1, "Nhắc"),
    WARNING(2, "Cảnh báo"),
    DANGER(3, "Nguy hiểm cao"),
    BLOCKED(4, "Chặn nghiêm trọng");
}

/**
 * 安全审查发现项。
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
 * 完整的静态安全审查报告。
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
 * 兼容性评估等级。
 */
@Serializable
enum class CompatibilityLevel(val label: String) {
    COMPATIBLE("Tương thích hoàn toàn"),
    PARTIALLY_COMPATIBLE("Tương thích một phần (có phụ thuộc thiếu hoặc hạ cấp)"),
    INCOMPATIBLE("Không tương thích");
}

/**
 * 技能兼容性评估报告。
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
 * ClawHub 在线技能市场条目简报。
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
    val category: String = "Chung",
    val downloadUrl: String,
    val stars: Int = 0,
    val isInstalled: Boolean = false,
    val requiredTools: List<String> = emptyList(),
    val permissions: List<SkillPermission> = emptyList(),
)

/**
 * ClawHub 技能详情。
 */
@Serializable
data class ClawHubMarketDetail(
    val item: ClawHubMarketItem,
    val readmeMarkdown: String? = null,
    val license: String? = "Apache-2.0",
    val templateSummary: List<String> = emptyList(),
    val sampleScripts: List<String> = emptyList(),
)
