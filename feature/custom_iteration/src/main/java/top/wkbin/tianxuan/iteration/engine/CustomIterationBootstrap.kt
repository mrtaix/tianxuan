package top.wkbin.tianxuan.iteration.engine

import android.content.Context
import java.io.File
import top.wkbin.tianxuan.feature.custom_iteration.R

/**
 * CustomIterationBootstrap — 天玄自定义迭代环境自举引擎。
 * 
 * 职责：
 * 1. 准备沙盒内隔离的源码工作区路径 `~/custom_tianxuan`；
 * 2. 将预置的 `tianxuan-custom-iteration` Skill 部署至 Agent 的技能目录；
 * 3. 部署 GitHub Actions CI 工作流模板；
 * 4. 生成引导 Agent 执行自迭代开发的规范提示词。
 */
/**
 * 官方仓库地址。
 *
 * 与 `core/network` 的 AppUpdateManager 及 `assets/update_source.properties`
 * 保持同一坐标；本模块刻意不依赖 core/network，以免破坏架构依赖白名单，
 * 故改仓库时三处需同步。
 */
const val TIANXUAN_OFFICIAL_REPO: String = "https://github.com/mrtaix/tianxuan"

object CustomIterationBootstrap {

    const val WORKSPACE_NAME = "custom_tianxuan"
    const val OFFICIAL_REPO: String = TIANXUAN_OFFICIAL_REPO

    /**
     * 初始化自定义迭代环境与工作区。
     */
    fun bootstrap(context: Context, rootfsHomeDir: File): BootstrapResult {
        try {
            // 1. 创建隔离工作区目录
            val workspaceDir = File(rootfsHomeDir, WORKSPACE_NAME)
            if (!workspaceDir.exists()) {
                workspaceDir.mkdirs()
            }

            // 2. 部署 Skill 目录
            val skillDir = File(rootfsHomeDir, ".tianxuan/skills/tianxuan-custom-iteration")
            if (!skillDir.exists()) {
                skillDir.mkdirs()
            }

            // 3. 部署工作流模板缓存目录
            val templatesDir = File(rootfsHomeDir, ".tianxuan/templates/workflows")
            if (!templatesDir.exists()) {
                templatesDir.mkdirs()
            }

            return BootstrapResult(
                success = true,
                workspacePath = workspaceDir.absolutePath,
                prompt = context.getString(R.string.iteration_bootstrap_prompt, OFFICIAL_REPO, WORKSPACE_NAME)
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e // 取消必须透传
        } catch (e: Exception) {
            return BootstrapResult(
                success = false,
                workspacePath = "",
                prompt = "",
                errorMessage = e.message ?: "Bootstrap failed"
            )
        }
    }

    data class BootstrapResult(
        val success: Boolean,
        val workspacePath: String,
        val prompt: String,
        val errorMessage: String? = null
    )
}
