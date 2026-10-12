package top.wkbin.tianxuan.core.tools.skill

import top.wkbin.tianxuan.core.model.skill.CompatibilityLevel
import top.wkbin.tianxuan.core.model.skill.SkillCompatibilityResult
import top.wkbin.tianxuan.core.model.skill.SkillPackage
import top.wkbin.tianxuan.core.tools.ToolRegistry

/**
 * 技能兼容性与依赖评估器（静态评估技能在当前移动端 PRoot 沙箱与天玄环境下的适配度）。
 */
class SkillCompatibilityEvaluator(
    private val toolRegistry: ToolRegistry? = null,
) {
    /**
     * 评估技能包对当前 TianXuan 运行时环境的兼容性。
     */
    fun evaluate(pkg: SkillPackage): SkillCompatibilityResult {
        val notes = mutableListOf<String>()
        val missingTools = mutableListOf<String>()
        val unsupportedRuntimes = mutableListOf<String>()

        // 1. 运行时沙箱兼容性检查
        val declaredRuntimes = pkg.manifest.compatibleRuntimes
        if (declaredRuntimes.isNotEmpty()) {
            val supportedInTianXuan = setOf("proot", "debian", "ubuntu", "alpine", "linux", "android", "arm64", "aarch64")
            val hasOverlap = declaredRuntimes.any { it.lowercase() in supportedInTianXuan }
            if (!hasOverlap) {
                unsupportedRuntimes.addAll(declaredRuntimes)
                notes.add("Môi trường chạy mục tiêu khai báo của kỹ năng này (${declaredRuntimes.joinToString()}) không khớp với Thiên Huyền Android PRoot (arm64-v8a)")
            }
        }

        // 2. 依赖外部命令/工具检查
        val requiredTools = pkg.manifest.requiredTools
        if (requiredTools.isNotEmpty()) {
            val installedToolIds = toolRegistry?.load()?.map { it.id.lowercase() }?.toSet().orEmpty()
            requiredTools.forEach { tool ->
                val normalized = tool.trim().lowercase()
                val isBuiltinLinuxCommand = normalized in BUILTIN_SANDBOX_COMMANDS
                val isRegisteredInTianXuan = normalized in installedToolIds

                if (!isBuiltinLinuxCommand && !isRegisteredInTianXuan) {
                    missingTools.add(tool)
                    notes.add("Thiếu phụ thuộc công cụ tiền đề: `$tool` (có thể cài qua apt trong sandbox hoặc cấu hình Recipe tương ứng trong trung tâm công cụ)")
                }
            }
        }

        // 3. 评定综合兼容度
        val level = when {
            unsupportedRuntimes.isNotEmpty() -> CompatibilityLevel.INCOMPATIBLE
            missingTools.isNotEmpty() -> CompatibilityLevel.PARTIALLY_COMPATIBLE
            else -> CompatibilityLevel.COMPATIBLE
        }

        if (level == CompatibilityLevel.COMPATIBLE) {
            notes.add("Hoàn toàn tương thích với sandbox Thien Huyen hiện tại (Debian arm64-v8a), tất cả phụ thuộc tiền đề đều đã sẵn sàng.")
        }

        return SkillCompatibilityResult(
            level = level,
            missingTools = missingTools,
            unsupportedRuntimes = unsupportedRuntimes,
            notes = notes,
        )
    }

    companion object {
        /** 天玄 Debian 沙箱预置或基础 Linux 核心命令列表。 */
        private val BUILTIN_SANDBOX_COMMANDS = setOf(
            "sh", "bash", "cat", "echo", "grep", "find", "sed", "awk",
            "ls", "cp", "mv", "rm", "mkdir", "chmod", "ps", "tar", "gzip",
            "tianxuan-build", "tianxuan",
        )
    }
}
