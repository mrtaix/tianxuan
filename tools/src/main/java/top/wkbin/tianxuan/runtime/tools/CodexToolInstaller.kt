package top.wkbin.tianxuan.runtime.tools

import top.wkbin.tianxuan.core.model.RuntimeName
import top.wkbin.tianxuan.core.model.RuntimeRequirement
import top.wkbin.tianxuan.core.tools.DependencyManager
import top.wkbin.tianxuan.core.tools.ProviderManager
import top.wkbin.tianxuan.core.tools.ToolActionResult
import top.wkbin.tianxuan.core.tools.ToolRuntimeAdapter
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.shell.CommandResult
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import top.wkbin.tianxuan.runtime.shell.SessionConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class CodexToolInstaller(
    private val linuxRuntime: LinuxRuntime,
    private val dependencyManager: DependencyManager,
    private val providerManager: ProviderManager,
    private val remoteScriptRunner: RemoteScriptRunner,
    private val toolCommandLinker: ToolCommandLinker,
) : ToolRuntimeAdapter {
    override val toolId: String = "codex"

    override fun install(): Flow<InstallEvent> = flow {
        emit(InstallEvent.Started(toolId))
        try {
            checkRuntimeReady()
            emit(InstallEvent.Progress(toolId, "Chuẩn bị curl và chứng chỉ CA", 0.15f, InstallEvent.Phase.INSTALLING_DEPENDENCY))
            ensureDependency(RuntimeName.CURL, toolId)
            ensureDependency(RuntimeName.CA_CERTIFICATES, toolId)
            emit(InstallEvent.Progress(toolId, "Chạy script cài đặt OpenAI Codex", 0.45f, InstallEvent.Phase.RUNNING_INSTALLER))
            val installEnvironment = providerManager.environment() + mapOf(
                "HOME" to ToolLayout.toolDirectory(toolId),
                "CODEX_HOME" to ToolLayout.toolDataDirectory(toolId),
            )
            val install = runCatching {
                executeAndReport(
                    remoteScriptRunner.run(
                        RemoteScriptSpec(
                            name = "codex",
                            url = "https://chatgpt.com/codex/install.sh",
                            retries = 0,
                        ),
                        installEnvironment,
                    ),
                )
            }.getOrElse { CommandResult(exitCode = 1, stdout = "", stderr = it.message ?: "Mạng quá thời gian", durationMs = 0L) }

            if (!install.isSuccess) {
                // 失败必须如实上抛。此前这里会写入一个只会打印 "codex 0.1.0" 的 stub
                // 脚本充当伪 CLI，导致下方 verify 必过、安装被记为 Completed/INSTALLED——
                // 真实 CLI 从未落盘却被掩盖，网络恢复后也不会触发重装。
                val reason = install.stderr.ifBlank { install.stdout }.trim()
                error(
                    "Codex thực thi script cài đặt từ xa thất bại (exit=${install.exitCode}): " +
                        reason.lineSequence().firstOrNull().orEmpty().ifBlank { "Mạng bị giới hạn hoặc tải script thất bại" },
                )
            }
            val link = toolCommandLinker.link(
                command = "codex",
                target = "${ToolLayout.toolDirectory(toolId)}/.local/bin/codex",
                environment = providerManager.environment(),
            )
            if (!link.isSuccess) error(link.stderr.ifBlank { "Không thể tạo điểm vào lệnh codex" })
            emit(InstallEvent.Progress(toolId, "Xác minh lệnh codex", 0.85f, InstallEvent.Phase.VERIFYING_INSTALLATION))
            val version = executeAndReport("codex --version")
            if (!version.isSuccess) error(version.stderr.ifBlank { "Không tìm thấy lệnh codex" })
            emit(InstallEvent.Completed(toolId, version.stdout.trim().lineSequence().firstOrNull()))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            emit(InstallEvent.RolledBack(toolId))
            emit(InstallEvent.Failed(toolId, throwable.message ?: "Cài Codex thất bại"))
        }
    }

    override suspend fun launch(): CommandResult = execute("codex")

    override suspend fun verify(): CommandResult = execute("codex --version")

    override suspend fun interactiveSessionConfig(): SessionConfig = SessionConfig(
        commandLine = "exec codex",
        environment = providerManager.environment(),
        allowSttyResize = false,
    )

    override suspend fun uninstall(deleteData: Boolean): ToolActionResult {
        val dataCleanup = if (deleteData) " && rm -rf ${ToolLayout.toolDataDirectory(toolId)}" else ""
        val link = toolCommandLinker.remove("codex", providerManager.environment())
        val directory = execute("rm -rf ${ToolLayout.toolDirectory(toolId)}$dataCleanup")
        return ToolActionResult(
            success = link.isSuccess && directory.isSuccess,
            message = listOf(link, directory).firstOrNull { !it.isSuccess }
                ?.let { it.stderr.ifBlank { it.stdout } }
                ?.ifBlank { "Gỡ cài thất bại" }
                ?: "Gỡ cài đặt hoàn tất",
        )
    }

    private suspend fun ensureDependency(name: RuntimeName, toolId: String) {
        val result = dependencyManager.acquire(RuntimeRequirement(name), toolId)
        if (result.isFailure) error(result.errorOrNull()?.message ?: "Cài đặt phụ thuộc thất bại：$name")
    }

    private suspend fun execute(command: String) = linuxRuntime.execute(
        ShellCommand(command, environment = providerManager.environment()),
    )

    private suspend fun kotlinx.coroutines.flow.FlowCollector<InstallEvent>.executeAndReport(
        result: CommandResult,
    ): CommandResult {
        result.stdout.lineSequence().filter { it.isNotBlank() }.forEach { emit(InstallEvent.Output("codex", it)) }
        result.stderr.lineSequence().filter { it.isNotBlank() }.forEach { emit(InstallEvent.Output("codex", it)) }
        return result
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<InstallEvent>.executeAndReport(
        command: String,
    ): CommandResult = executeAndReport(execute(command))

    private fun checkRuntimeReady() {
        check(linuxRuntime.state.value is top.wkbin.tianxuan.core.model.RuntimeState.Ready) {
            "Linux Runtime chưa sẵn sàng, hãy khởi tạo Linux trước"
        }
    }

    private fun CommandResult.toActionResult() = ToolActionResult(
        success = isSuccess,
        message = stderr.ifBlank { stdout }.trim().ifBlank { if (isSuccess) "Gỡ cài đặt hoàn tất" else "Mã thoát lệnh $exitCode" },
    )
}
