package top.wkbin.tianxuan.iteration.engine

import android.content.Context
import java.io.File

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
const val TIANXUAN_OFFICIAL_REPO: String = "https://github.com/xiaozhe7772222/tianxuan"

object CustomIterationBootstrap {

    const val WORKSPACE_NAME = "custom_tianxuan"
    const val OFFICIAL_REPO: String = TIANXUAN_OFFICIAL_REPO

    /**
     * 双包共存身份：自定义迭代（TIANXUAN_DEV_BUILD=1）产出的预览包标识。
     *
     * 这组常量是「唯一真源」里对人类可读的那一份镜像——真正决定构建产物的是
     * `app/build.gradle.kts` 的 `tianXuanDevBuild` 分支。两边必须保持一致：
     * 若日后改了 build 脚本却忘了这里，单元测试 CustomIterationIdentityTest 会红，
     * 以此逼出「只改一处」的回退。
     */
    const val DEV_APPLICATION_ID: String = "com.mvibe.mobi"
    const val DEV_APP_LABEL: String = "Mvibe"

    /** 正式版包名——预览包必须与之不同，才能共存安装、互不覆盖。 */
    const val OFFICIAL_APPLICATION_ID: String = "top.wkbin.tianxuan"

    /**
     * 校验预览包标识是否满足共存前提：
     * 1. applicationId 为合法 reverse-DNS（至少两段、仅含 [a-z0-9_] 且不以数字开头的段）；
     * 2. 与正式版包名不相等（否则会覆盖安装、抢占无障碍/输入法权限）；
     * 3. 应用名非空。
     *
     * 返回 null 表示通过；否则返回人类可读的失败原因（供日志/UI 使用）。
     */
    fun validateDevIdentity(
        applicationId: String = DEV_APPLICATION_ID,
        appLabel: String = DEV_APP_LABEL,
    ): String? {
        if (appLabel.isBlank()) return "应用名不能为空"
        if (applicationId == OFFICIAL_APPLICATION_ID) {
            return "预览包包名不能与正式版相同（$OFFICIAL_APPLICATION_ID），否则无法共存"
        }
        val segments = applicationId.split('.')
        if (segments.size < 2) return "包名至少需要两段（reverse-DNS），实际为 \"$applicationId\""
        val segmentPattern = Regex("^[a-z][a-z0-9_]*$")
        for (seg in segments) {
            if (!segmentPattern.matches(seg)) {
                return "包名段 \"$seg\" 非法：每段须以小写字母开头，仅含 [a-z0-9_]"
            }
        }
        return null
    }

    const val BOOTSTRAP_PROMPT = """我准备在天玄（TianXuan）的手机 Linux 虚拟沙盒中进行 TianXuan 自定义迭代。

请按以下步骤引导我：
1. 检查本地开发环境：
   - 确认当前命令在 Linux PRoot 沙盒中执行；
   - 确认独立工作区为 ~/custom_tianxuan；
   - 优先使用 GitHub Actions 构建 TianXuanDev APK，无需在手机本地安装庞大的 Android SDK/NDK。

2. 检查并配置 GitHub 认证：
   - 检查 gh CLI 与 Git 配置；
   - 引导我使用 gh auth login（设备码流程）或 SSH Key 完成登录验证；
   - 不要把 Token、私钥写入命令历史或日志中。

3. 验证与克隆仓库：
   - 为官方仓库 $OFFICIAL_REPO 点星；
   - Fork main 分支到我自己的 GitHub 账户；
   - 将 Fork 后的仓库克隆到 ~/custom_tianxuan。

4. 遵循 tianxuan-custom-iteration Skill 开发规范：
   - 按照天玄的 Jetpack Compose、Koin 和多模块规范进行修改；
   - 编写或调整功能后运行单元测试验证；
   - 提交修改并推送到 Fork 仓库的特性分支。

5. 通过 GitHub Actions 构建独立的 TianXuanDev APK：
   - 触发 .github/workflows/tianxuandev-build.yml 编译；
   - 实时监控构建进度并在成功后下载 APK 至手机；
   - 校验包名 top.wkbin.tianxuan.dev 和应用名 TianXuanDev，与正式版独立共存。

6. 若体验满意，协助我生成标准 PR 提交到 $OFFICIAL_REPO。"""

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
                prompt = BOOTSTRAP_PROMPT
            )
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
