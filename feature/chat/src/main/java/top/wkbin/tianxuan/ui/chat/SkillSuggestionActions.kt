package top.wkbin.tianxuan.ui.chat

import java.util.UUID
import top.wkbin.tianxuan.core.model.AgentSkill
import top.wkbin.tianxuan.harness.SkillSuggestion

/**
 * Chuyển gợi ý cải tiến thành [AgentSkill], nhất quán với thao tác thêm kỹ năng trong cài đặt:
 * cập nhật dùng id kỹ năng tùy chỉnh hiện có (không ghi đè kỹ năng tích hợp), tạo mới dùng tiền tố `custom_`.
 */
internal object SkillSuggestionActions {
    fun resolveExisting(
        suggestion: SkillSuggestion,
        createNew: Boolean,
        skills: List<AgentSkill>,
    ): AgentSkill? {
        if (createNew) return null
        val targetId = suggestion.targetSkillId?.takeIf { it.isNotBlank() } ?: return null
        return skills.firstOrNull { it.id == targetId }?.takeUnless { it.isBuiltin }
    }

    fun toCustomSkill(
        suggestion: SkillSuggestion,
        existing: AgentSkill?,
        newId: () -> String = { "custom_" + UUID.randomUUID().toString().take(8) },
    ): AgentSkill {
        val trimmedName = suggestion.skillName.trim()
        val trimmedPrompt = suggestion.systemPrompt.trim()
        val trigger = suggestion.triggerCommand?.trim()?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("/")) it else "/$it" }
        return if (existing != null && !existing.isBuiltin) {
            existing.copy(
                name = trimmedName,
                description = suggestion.description.trim().ifBlank { existing.description },
                systemPrompt = trimmedPrompt,
                triggerCommand = trigger ?: existing.triggerCommand,
            )
        } else {
            AgentSkill(
                id = newId(),
                name = trimmedName,
                description = suggestion.description.trim().ifBlank { "Kỹ năng tùy chỉnh" },
                systemPrompt = trimmedPrompt,
                triggerCommand = trigger,
                iconName = "Code",
                isEnabled = true,
                isBuiltin = false,
                // Giữ khóa danh mục lưu trữ để khớp bộ lọc và ánh xạ nhãn hiện có.
        category = "自定义",
            )
        }
    }

    /**
     * 计算当前会话应隐藏的技能建议 id：
     * 1. 包含 DataStore 持久化的已忽略/已应用 id 集合（跨进程重启不丢失）；
     * 2. 包含本地即时操作的 id 集合（保证点击瞬间响应，防界面卡顿闪烁）；
     * 3. 自动治愈：对于建议沉淀的技能（action=create）或修复的技能（action=update），
     *    若技能库中已实际存在该名称的技能或正文已对齐，则自动识别为已沉淀/已修复，不再重复向用户弹卡。
     */
    fun computeHiddenSuggestionIds(
        persisted: Set<String>,
        local: Set<String>,
        skills: List<AgentSkill>,
        messages: List<top.wkbin.tianxuan.harness.HarnessMessage>,
    ): Set<String> {
        val autoDismissedFromExistingSkills = messages.asSequence()
            .filterIsInstance<SkillSuggestion>()
            .filter { suggestion ->
                val name = suggestion.skillName.trim()
                if (name.isBlank()) return@filter true
                when (suggestion.action) {
                    "create" -> skills.any { it.name.equals(name, ignoreCase = true) }
                    "update" -> {
                        val target = skills.firstOrNull { it.id == suggestion.targetSkillId }
                            ?: skills.firstOrNull { it.name.equals(name, ignoreCase = true) }
                        target != null && target.systemPrompt.trim() == suggestion.systemPrompt.trim()
                    }
                    else -> false
                }
            }
            .map { it.id }
        return persisted + local + autoDismissedFromExistingSkills
    }
}
