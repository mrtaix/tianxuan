package top.wkbin.tianxuan.ui.developer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.datastore.AgentPreferences
import top.wkbin.tianxuan.core.datastore.OnboardingPreferences
import top.wkbin.tianxuan.core.datastore.RegistryPreferences
import top.wkbin.tianxuan.core.datastore.RuntimePreferences
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.core.model.InstalledRuntime
import top.wkbin.tianxuan.core.tools.RuntimeManager
import top.wkbin.tianxuan.core.tools.SignedRegistryRequest
import top.wkbin.tianxuan.core.tools.ToolManager
import top.wkbin.tianxuan.core.tools.ToolRegistry
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.RuntimeHealth
import top.wkbin.tianxuan.runtime.RootfsUpdateInfo
import top.wkbin.tianxuan.runtime.shell.CommandResult
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import top.wkbin.tianxuan.runtime.shell.ManagedProcess
import top.wkbin.tianxuan.runtime.bridge.adb.EmbeddedAdbManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DeveloperViewModel(
    private val linuxRuntime: LinuxRuntime,
    private val runtimeManager: RuntimeManager,
    private val runtimePreferences: RuntimePreferences,
    private val agentPreferences: AgentPreferences,
    private val registryPreferences: RegistryPreferences,
    private val onboardingPreferences: top.wkbin.tianxuan.core.datastore.OnboardingPreferences,
    private val toolRegistry: ToolRegistry,
    private val toolManager: ToolManager,
    private val logger: AppLogger,
    private val embeddedAdbManager: EmbeddedAdbManager,
) : ViewModel() {

    init {
        embeddedAdbManager.startDiscovery(EmbeddedAdbManager.TAG_DEVELOPER_UI)
    }

    override fun onCleared() {
        super.onCleared()
        embeddedAdbManager.stopDiscovery(EmbeddedAdbManager.TAG_DEVELOPER_UI)
    }

    val runtimeState: StateFlow<RuntimeState> = linuxRuntime.state

    private val _commandInput = MutableStateFlow("cat /etc/os-release")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _health = MutableStateFlow<RuntimeHealth?>(null)
    val health: StateFlow<RuntimeHealth?> = _health.asStateFlow()

    private val _commandResult = MutableStateFlow<CommandResult?>(null)
    val commandResult: StateFlow<CommandResult?> = _commandResult.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 当前 message 是否为失败结果（类型化标记，避免 UI 用字符串匹配判断样式）。 */
    private val _messageIsError = MutableStateFlow(false)
    val messageIsError: StateFlow<Boolean> = _messageIsError.asStateFlow()

    private fun setMessage(text: String, isError: Boolean = false) {
        _message.value = text
        _messageIsError.value = isError
    }
    private val _unusedRuntimes = MutableStateFlow<List<InstalledRuntime>>(emptyList())
    val unusedRuntimes: StateFlow<List<InstalledRuntime>> = _unusedRuntimes.asStateFlow()
    private val _processes = MutableStateFlow<List<ManagedProcess>>(emptyList())
    val processes: StateFlow<List<ManagedProcess>> = _processes.asStateFlow()
    private val _rootfsVersion = MutableStateFlow<String?>(null)
    val rootfsVersion: StateFlow<String?> = _rootfsVersion.asStateFlow()
    private val _rootfsUpdate = MutableStateFlow<RootfsUpdateInfo?>(null)
    val rootfsUpdate: StateFlow<RootfsUpdateInfo?> = _rootfsUpdate.asStateFlow()
    private var initializationJob: Job? = null

    val adbState: StateFlow<EmbeddedAdbManager.ConnectionState> = embeddedAdbManager.state
    val adbDiscovery: StateFlow<EmbeddedAdbManager.DiscoveryState> = embeddedAdbManager.discovery
    private val _adbBusy = MutableStateFlow(false)
    val adbBusy: StateFlow<Boolean> = _adbBusy.asStateFlow()
    private val _adbMessage = MutableStateFlow<String?>(null)
    val adbMessage: StateFlow<String?> = _adbMessage.asStateFlow()
    private val _logcatOutput = MutableStateFlow("")
    val logcatOutput: StateFlow<String> = _logcatOutput.asStateFlow()

    val adbNotificationEnabled: StateFlow<Boolean> = runtimePreferences.adbNotificationEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setAdbNotificationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            runtimePreferences.setAdbNotificationEnabled(enabled)
        }
    }

    fun pairWirelessAdb(code: String, pairingPort: Int? = null) {
        if (_adbBusy.value) return
        viewModelScope.launch {
            _adbBusy.value = true
            _adbMessage.value = if (pairingPort != null) "Đang dùng cổng ghép đôi chỉ định $pairingPort để ghép đôi an toàn…" else "Đang ghép nối an toàn bằng cổng ghép nối tự phát hiện…"
            val result = embeddedAdbManager.pair(pairingPort, code.trim())
            _adbMessage.value = result.fold(
                onSuccess = { "Ghép đôi và kết nối thành công; khóa đã được lưu an toàn, sau này sẽ tự động phát hiện cổng và kết nối lại." },
                onFailure = { it.message ?: "Ghép đôi ADB không dây thất bại" },
            )
            _adbBusy.value = false
        }
    }

    fun connectWirelessAdb(explicitPort: Int? = null) {
        if (_adbBusy.value) return
        viewModelScope.launch {
            _adbBusy.value = true
            _adbMessage.value = if (explicitPort != null) "Đang kết nối cổng chỉ định $explicitPort…" else "Đang kết nối cổng gỡ lỗi không dây tự phát hiện…"
            val result = embeddedAdbManager.connect(explicitPort)
            _adbMessage.value = result.fold(
                onSuccess = { "ADB không dây đã kết nối." },
                onFailure = { it.message ?: "Kết nối ADB không dây thất bại" },
            )
            _adbBusy.value = false
        }
    }

    fun restartAdbDiscovery() {
        embeddedAdbManager.restartDiscovery()
        _adbMessage.value = "Đã khởi động lại dò cổng mDNS."
    }

    fun captureLogcat(packageName: String, tag: String, priority: Char, keyword: String, lines: Int) {
        if (_adbBusy.value) return
        viewModelScope.launch {
            _adbBusy.value = true
            _adbMessage.value = "Đang lấy log…"
            runCatching {
                embeddedAdbManager.captureLogcat(
                    EmbeddedAdbManager.LogcatRequest(packageName.trim(), tag.trim(), priority, keyword, lines),
                )
            }.onSuccess { result ->
                _logcatOutput.value = result.output
                _adbMessage.value = if (result.success) "Thu thập log hoàn tất." else result.output
            }.onFailure { error ->
                _adbMessage.value = error.message ?: "Lấy log thất bại"
            }
            _adbBusy.value = false
        }
    }

    fun clearDeviceLogcat() {
        if (_adbBusy.value) return
        viewModelScope.launch {
            _adbBusy.value = true
            val result = embeddedAdbManager.clearLogcat()
            if (result.success) _logcatOutput.value = ""
            _adbMessage.value = if (result.success) "Bộ đệm Logcat của thiết bị đã được xóa." else result.output
            _adbBusy.value = false
        }
    }

    val registryManifestUrl: StateFlow<String> = registryPreferences.manifestUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val registrySignatureUrl: StateFlow<String> = registryPreferences.signatureUrl
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val registryPublicKey: StateFlow<String> = registryPreferences.publicKey
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private val _registryStatus = MutableStateFlow<String?>(null)
    val registryStatus: StateFlow<String?> = _registryStatus.asStateFlow()

    /** 工具清单更新是否失败（类型化标记）。 */
    private val _registryStatusIsError = MutableStateFlow(false)
    val registryStatusIsError: StateFlow<Boolean> = _registryStatusIsError.asStateFlow()

    private fun setRegistryStatus(text: String, isError: Boolean = false) {
        _registryStatus.value = text
        _registryStatusIsError.value = isError
    }

    val agentLoggingEnabled: StateFlow<Boolean> = agentPreferences.agentLoggingEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _agentLogSize = MutableStateFlow(0L)
    val agentLogSize: StateFlow<Long> = _agentLogSize.asStateFlow()
    private val _agentLogLocation = MutableStateFlow("")
    val agentLogLocation: StateFlow<String> = _agentLogLocation.asStateFlow()

    fun setAgentLoggingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            agentPreferences.setAgentLoggingEnabled(enabled)
            refreshAgentLogSize()
        }
    }

    fun refreshAgentLogSize() {
        _agentLogSize.value = logger.getAgentLogSizeBytes()
        _agentLogLocation.value = logger.getAgentLogLocation()
    }

    fun readAgentLogs(): String = logger.readAgentLogs()

    fun clearAgentLogs() {
        logger.clearAgentLogs()
        refreshAgentLogSize()
        setMessage("智能体日志已清空。")
    }

    fun saveRegistryConfig(manifestUrl: String, signatureUrl: String, publicKey: String) {
        viewModelScope.launch {
            registryPreferences.setRegistryConfig(manifestUrl, signatureUrl, publicKey)
            setRegistryStatus("工具清单配置已保存。")
        }
    }

    fun updateRegistry() {
        viewModelScope.launch {
            setRegistryStatus("正在下载并验证工具清单…")
            val request = SignedRegistryRequest(
                manifestUrl = registryManifestUrl.value,
                signatureUrl = registrySignatureUrl.value,
                publicKeyBase64 = registryPublicKey.value,
            )
            val result = toolRegistry.updateSigned(request)
            if (result.isSuccess) {
                toolManager.syncRegistry()
                setRegistryStatus("Danh sách công cụ đã cập nhật: ${result.getOrNull()} công cụ.")
            } else {
                logger.e("Registry update failed: ${result.errorOrNull()?.message}")
                setRegistryStatus("Cập nhật danh sách công cụ thất bại, hãy kiểm tra địa chỉ danh sách, chữ ký và cấu hình public key", isError = true)
            }
        }
    }

    init {
        refreshUnusedRuntimes()
        refreshProcesses()
        refreshRootfsVersion()
        refreshAgentLogSize()
    }

    fun onCommandInputChanged(value: String) {
        _commandInput.value = value
    }

    fun initialize() {
        if (_busy.value) return
        _message.value = null
        initializationJob = viewModelScope.launch {
            _busy.value = true
            try {
                when (val result = linuxRuntime.initialize()) {
                    is AppResult.Success -> setMessage("初始化完成。")
                    is AppResult.Failure -> {
                        logger.e("Runtime initialize failed: ${result.error.message}")
                        setMessage("Khởi tạo thất bại, hãy kiểm tra mạng và dung lượng lưu trữ rồi thử lại", isError = true)
                    }
                }
            } finally {
                _busy.value = false
                initializationJob = null
            }
        }
    }

    fun cancelInitialization() {
        initializationJob?.cancel()
    }

    fun updateRootfs() {
        if (_busy.value || linuxRuntime.state.value !is RuntimeState.Ready) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            _messageIsError.value = false
            runCatching { linuxRuntime.updateRootfs() }
                .onSuccess { result ->
                    if (result.isSuccess) {
                        setMessage("RootFS 更新完成，用户数据已保留。")
                    } else {
                        logger.e("RootFS update failed: ${result.errorOrNull()?.message}")
                        setMessage("Cập nhật RootFS thất bại, đã tự động khôi phục phiên bản cũ; hãy thử lại sau", isError = true)
                    }
                    refreshRootfsVersion()
                }
                .onFailure { throwable ->
                    logger.e("RootFS update failed", throwable)
                    setMessage("Cập nhật RootFS thất bại, vui lòng kiểm tra mạng rồi thử lại", isError = true)
                }
            _busy.value = false
        }
    }

    fun checkRootfsUpdate() {
        if (_busy.value || linuxRuntime.state.value !is RuntimeState.Ready) return
        viewModelScope.launch {
            _busy.value = true
            setMessage("正在检查 RootFS 的 OCI manifest…")
            runCatching { linuxRuntime.checkRootfsUpdate() }
                .onSuccess { result ->
                    if (result.isSuccess) {
                        val info = result.getOrNull()!!
                        _rootfsUpdate.value = info
                        if (info.hasUpdate) {
                            setMessage("检测到 RootFS 新版本，可以更新。")
                        } else {
                            setMessage("RootFS 已是最新版本。")
                        }
                    } else {
                        logger.e("RootFS update check failed: ${result.errorOrNull()?.message}")
                        setMessage("Kiểm tra cập nhật RootFS thất bại, vui lòng kiểm tra mạng rồi thử lại", isError = true)
                    }
                }
                .onFailure { throwable ->
                    logger.e("RootFS update check failed", throwable)
                    setMessage("Kiểm tra cập nhật RootFS thất bại, vui lòng kiểm tra mạng rồi thử lại", isError = true)
                }
            _busy.value = false
        }
    }

    fun runHealthCheck() {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { linuxRuntime.healthCheck() }
                .onSuccess { _health.value = it }
                .onFailure {
                    logger.e("Health check failed", it)
                    setMessage("Kiểm tra sức khỏe thất bại, hãy xác nhận runtime đã sẵn sàng rồi thử lại", isError = true)
                }
            _busy.value = false
        }
    }

    fun refreshUnusedRuntimes() {
        viewModelScope.launch {
            runCatching { runtimeManager.unusedRuntimes() }
                .onSuccess { _unusedRuntimes.value = it }
                .onFailure { logger.e("读取可清理 Runtime 失败", it) }
        }
    }

    fun cleanupRuntime(runtimeId: String) {
        if (_busy.value) return
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching { runtimeManager.cleanup(runtimeId) }
                .onSuccess { result ->
                    if (result.isSuccess) setMessage("共享 Runtime 已清理。") else setMessage("Dọn thất bại, hãy thử lại sau", isError = true)
                    refreshUnusedRuntimes()
                }
                .onFailure { logger.e("Runtime cleanup failed", it); setMessage("Dọn thất bại, hãy thử lại sau", isError = true) }
            _busy.value = false
        }
    }

    fun refreshProcesses() {
        viewModelScope.launch {
            runCatching {
                linuxRuntime.cleanupDeadBackground()
                linuxRuntime.listBackground()
            }.onSuccess { _processes.value = it }
                .onFailure { logger.e("读取后台进程失败", it) }
        }
    }

    fun stopProcess(processId: String) {
        if (_busy.value) return
        viewModelScope.launch {
            runCatching { linuxRuntime.stopBackground(processId) }
                .onSuccess { refreshProcesses() }
                .onFailure { logger.e("Stop process failed", it); setMessage("Dừng tiến trình thất bại, hãy thử lại sau", isError = true) }
        }
    }

    fun refreshRootfsVersion() {
        _rootfsVersion.value = linuxRuntime.rootfsVersion()
    }

    fun resetLinuxEnvironment() {
        if (_busy.value || linuxRuntime.state.value is RuntimeState.Initializing) return
        viewModelScope.launch {
            _busy.value = true
            val distroId = linuxRuntime.activeDistroId.value
            runCatching {
                val result = linuxRuntime.resetSandbox(distroId)
                check(result.isSuccess) { result.errorOrNull()?.message ?: "Linux 环境重置失败" }
                toolManager.resetDistroState(distroId)
                // A factory-style runtime reset intentionally restarts the
                // complete first-run flow: environment download, then model
                // selection/configuration.
                onboardingPreferences.setOnboardingCompleted(false)
                result
            }
                .onSuccess { result ->
                    if (result.isSuccess) {
                        setMessage("Linux 环境已恢复初始状态，工作区工程未删除。")
                    } else {
                        logger.e("Linux reset failed: ${result.errorOrNull()?.message}")
                        setMessage("Đặt lại thất bại, vui lòng thử lại sau", isError = true)
                    }
                }
                .onFailure { logger.e("Linux reset failed", it); setMessage("Đặt lại thất bại, vui lòng thử lại sau", isError = true) }
            _busy.value = false
        }
    }

    fun runCommand() {
        if (_busy.value) return
        val command = _commandInput.value.trim()
        if (command.isEmpty()) {
            setMessage("命令不能为空。", isError = true)
            return
        }
        viewModelScope.launch {
            _busy.value = true
            _message.value = null
            runCatching {
                linuxRuntime.execute(ShellCommand(commandLine = command))
            }.onSuccess {
                _commandResult.value = it
            }.onFailure {
                logger.e("Command execution failed", it)
                _commandResult.value = null
                setMessage("Thực thi lệnh thất bại, hãy kiểm tra cú pháp lệnh và trạng thái runtime", isError = true)
            }
            _busy.value = false
        }
    }
}
