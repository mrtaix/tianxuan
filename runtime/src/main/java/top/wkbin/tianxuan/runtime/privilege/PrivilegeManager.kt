package top.wkbin.tianxuan.runtime.privilege

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.common.result.AppError
import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.common.result.ErrorCode
import top.wkbin.tianxuan.core.datastore.RuntimePreferences
import top.wkbin.tianxuan.core.model.ExecutionMode
import top.wkbin.tianxuan.core.model.PrivilegeCheckResult
import java.util.concurrent.TimeUnit
import java.util.UUID

enum class PrivilegeAvailability { CHECKING, ACTIVE, DEGRADED, UNAVAILABLE }

/** 全应用共享的权限权威状态；首选模式与当前实际生效模式明确分离。 */
data class PrivilegeState(
    val preferredMode: ExecutionMode = ExecutionMode.PROOT,
    val effectiveMode: ExecutionMode = ExecutionMode.PROOT,
    val availability: PrivilegeAvailability = PrivilegeAvailability.CHECKING,
    val reason: String = "Đang kiểm tra quyền chạy",
    val shizukuAvailable: Boolean = false,
    val rootAvailable: Boolean = false,
) {
    val active: Boolean get() = availability == PrivilegeAvailability.ACTIVE
    val degraded: Boolean get() = availability == PrivilegeAvailability.DEGRADED
}

class PrivilegeManager(
    private val context: Context,
    private val settingsDataStore: RuntimePreferences,
    private val logger: AppLogger,
    private val shizukuHostServiceClient: ShizukuHostServiceClient,
) {
    private val _state = MutableStateFlow(PrivilegeState())
    val state: StateFlow<PrivilegeState> = _state.asStateFlow()
    private val rootRunner = HostProcessRunner { command -> ProcessBuilder("su", "-c", command).start() }

    /**
     * 应用进程启动时恢复并校验上次选择的模式。
     *
     * 这里只做实际权限探测，不主动弹出 Shizuku 授权框；授权已失效、服务未运行或
     * Root 不可用时，立即把生效模式持久化降级为 PRoot，避免 UI 与 Harness 继续
     * 把一个已经失效的高权限模式当作可用能力。
     */
    suspend fun reconcilePersistedMode(): ExecutionMode = withContext(Dispatchers.IO) {
        val preferred = preferredMode()
        // 冷启动校验完成前，执行面先锁定为最低权限，杜绝 Harness 抢跑使用旧高权限值。
        settingsDataStore.setEffectiveExecutionMode(ExecutionMode.PROOT)
        _state.value = PrivilegeState(
            preferredMode = preferred,
            effectiveMode = ExecutionMode.PROOT,
            availability = PrivilegeAvailability.CHECKING,
            reason = "Đang khôi phục quyền ${preferred.shortLabel}",
        )
        if (preferred == ExecutionMode.PROOT) {
            settingsDataStore.setExecutionModes(ExecutionMode.PROOT, ExecutionMode.PROOT)
            _state.value = PrivilegeState(
                preferredMode = ExecutionMode.PROOT,
                effectiveMode = ExecutionMode.PROOT,
                availability = PrivilegeAvailability.ACTIVE,
                reason = "Chế độ user-mode PRoot không cần cấp quyền thêm",
            )
            return@withContext ExecutionMode.PROOT
        }

        // ShizukuProvider 与 Application.onCreate 存在很短的 Binder 交付窗口；等待 sticky
        // 回调后再判断，避免“服务其实可用但启动瞬间尚未收到 Binder”导致误降级。
        if (preferred == ExecutionMode.SHIZUKU) awaitShizukuBinder()

        val check = passiveCheck(preferred)
        if (check is PrivilegeCheckResult.Authorized) {
            settingsDataStore.setExecutionModes(preferred, preferred)
            applyPrivilegeOptimizations(preferred)
            refreshState(preferred, preferred, PrivilegeAvailability.ACTIVE, check.details)
            logger.i("Kiểm tra quyền khởi động đã qua, khôi phục chế độ chạy: ${preferred.name}")
            preferred
        } else {
            val reason = when (check) {
                is PrivilegeCheckResult.Unauthorized -> check.reason
                is PrivilegeCheckResult.ServiceNotRunning -> check.guidance
                is PrivilegeCheckResult.Authorized -> check.details
            }
            // 只降级实际模式，保留用户首选；下次冷启动会自动重试。
            settingsDataStore.setExecutionModes(preferred, ExecutionMode.PROOT)
            refreshState(preferred, ExecutionMode.PROOT, PrivilegeAvailability.DEGRADED, reason)
            logger.w("Kiểm tra quyền khởi động thất bại, ${preferred.name} đã tạm hạ cấp xuống PROOT: $reason")
            ExecutionMode.PROOT
        }
    }

    private fun passiveCheck(mode: ExecutionMode): PrivilegeCheckResult = when (mode) {
        ExecutionMode.PROOT -> PrivilegeCheckResult.Authorized(mode, "Chế độ user-mode PRoot không cần cấp quyền thêm")
        ExecutionMode.ROOT -> checkRootPrivilege()
        ExecutionMode.SHIZUKU -> when {
            !runCatching { Shizuku.pingBinder() }.getOrDefault(false) ->
                PrivilegeCheckResult.ServiceNotRunning(mode, "Dịch vụ Shizuku chưa chạy")
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED ->
                PrivilegeCheckResult.Unauthorized(mode, "Quyền Shizuku chưa được cấp hoặc đã bị thu hồi")
            else -> PrivilegeCheckResult.Authorized(mode, "Quyền Shizuku đã khôi phục")
        }
    }

    private suspend fun refreshState(
        preferred: ExecutionMode,
        effective: ExecutionMode,
        availability: PrivilegeAvailability,
        reason: String,
    ) {
        val shizuku = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        _state.value = PrivilegeState(
            preferredMode = preferred,
            effectiveMode = effective,
            availability = availability,
            reason = reason,
            shizukuAvailable = shizuku,
            rootAvailable = effective == ExecutionMode.ROOT && availability == PrivilegeAvailability.ACTIVE,
        )
    }

    private suspend fun awaitShizukuBinder(): Boolean {
        if (runCatching { Shizuku.pingBinder() }.getOrDefault(false)) return true
        val received = CompletableDeferred<Unit>()
        val listener = Shizuku.OnBinderReceivedListener { received.complete(Unit) }
        Shizuku.addBinderReceivedListenerSticky(listener)
        return try {
            withTimeoutOrNull(SHIZUKU_STARTUP_WAIT_MS) {
                received.await()
                true
            } ?: false
        } finally {
            Shizuku.removeBinderReceivedListener(listener)
        }
    }

    /**
     * 探测并尝试获取目标运行模式的特权授权。
     */
    suspend fun checkAndAuthorize(mode: ExecutionMode): PrivilegeCheckResult = withContext(Dispatchers.IO) {
        when (mode) {
            ExecutionMode.PROOT -> {
                PrivilegeCheckResult.Authorized(
                    mode = ExecutionMode.PROOT,
                    details = "Sandbox user-space PRoot đã sẵn sàng, không cần đặc quyền hệ thống bổ sung.",
                )
            }

            ExecutionMode.ROOT -> {
                checkRootPrivilege()
            }

            ExecutionMode.SHIZUKU -> {
                checkShizukuPrivilege()
            }
        }
    }

    /**
     * 申请并切换到指定的运行模式。如果授权成功，自动持久化并释放特权能力。
     */
    suspend fun switchMode(mode: ExecutionMode): AppResult<PrivilegeCheckResult.Authorized> = withContext(Dispatchers.IO) {
        _state.value = _state.value.copy(
            preferredMode = mode,
            availability = PrivilegeAvailability.CHECKING,
            reason = "Đang xin quyền ${mode.shortLabel}",
        )
        val check = checkAndAuthorize(mode)
        when (check) {
            is PrivilegeCheckResult.Authorized -> {
                settingsDataStore.setExecutionModes(mode, mode)
                applyPrivilegeOptimizations(mode)
                refreshState(mode, mode, PrivilegeAvailability.ACTIVE, check.details)
                logger.i("Đã chuyển thành công sang chế độ chạy: ${mode.name} (${check.details})")
                AppResult.Success(check)
            }

            is PrivilegeCheckResult.Unauthorized -> {
                val previousPreferred = settingsDataStore.preferredExecutionMode.first()
                val previousEffective = settingsDataStore.effectiveExecutionMode.first()
                refreshState(previousPreferred, previousEffective, PrivilegeAvailability.ACTIVE, "Chuyển đổi thất bại: ${check.reason}")
                logger.w("Chuyển sang ${mode.name} thất bại: ${check.reason}")
                AppResult.Failure(
                    AppError(
                        code = ErrorCode.SECURITY,
                        message = check.reason,
                    ),
                )
            }

            is PrivilegeCheckResult.ServiceNotRunning -> {
                val previousPreferred = settingsDataStore.preferredExecutionMode.first()
                val previousEffective = settingsDataStore.effectiveExecutionMode.first()
                refreshState(previousPreferred, previousEffective, PrivilegeAvailability.ACTIVE, "Chuyển thất bại: ${check.guidance}")
                logger.w("Chuyển sang ${mode.name} thất bại: ${check.guidance}")
                AppResult.Failure(
                    AppError(
                        code = ErrorCode.UNKNOWN,
                        message = check.guidance,
                    ),
                )
            }
        }
    }

    /**
     * 探测 Root 权限（通过 su 执行流测试 UID 0）
     */
    private fun checkRootPrivilege(): PrivilegeCheckResult {
        return try {
            val process = ProcessBuilder("su", "-c", "id").start()
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return PrivilegeCheckResult.Unauthorized(
                    ExecutionMode.ROOT,
                    "Yêu cầu ủy quyền Root hết thời gian, hãy cho phép ủy quyền trong popup Magisk / KernelSU / APatch.",
                )
            }

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.exitValue()

            if (exitCode == 0 && output.contains("uid=0")) {
                PrivilegeCheckResult.Authorized(
                    ExecutionMode.ROOT,
                    "Đã có quyền Root (UID 0: root), đã giải phóng năng lực tăng tốc phần cứng Linux native và kernel!",
                )
            } else {
                PrivilegeCheckResult.Unauthorized(
                    ExecutionMode.ROOT,
                    "Cấp quyền Root không qua (exit $exitCode): $output",
                )
            }
        } catch (e: Exception) {
            logger.e("Kiểm tra quyền Root phát sinh ngoại lệ", e)
            PrivilegeCheckResult.ServiceNotRunning(
                ExecutionMode.ROOT,
                "Không phát hiện chương trình su khả dụng trên thiết bị. Nếu đã Root, hãy kiểm tra xem đã bật cấp quyền cho Thien Huyen trong trình quản lý cấp quyền chưa.",
            )
        }
    }

    /**
     * 使用官方 Shizuku-API 进行 Binder 服务探测与权限检查
     */
    private suspend fun checkShizukuPrivilege(): PrivilegeCheckResult {
        return try {
            // 1. 探测 Shizuku Binder 服务是否处于运行激活状态
            val isBinderAlive = Shizuku.pingBinder()
            if (!isBinderAlive) {
                return PrivilegeCheckResult.ServiceNotRunning(
                    ExecutionMode.SHIZUKU,
                    "Dịch vụ Shizuku chưa chạy. Hãy mở Shizuku App để đảm bảo trạng thái dịch vụ là “Đang chạy” (có thể khởi động qua gỡ lỗi không dây hoặc Root).",
                )
            }

            // 2. 检查应用是否已获得 Shizuku 权限
            val isGranted = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

            if (isGranted) {
                PrivilegeCheckResult.Authorized(
                    ExecutionMode.SHIZUKU,
                    "Shizuku (v${Shizuku.getVersion()}) ủy quyền thành công, đã mở khóa đặc quyền cấp ADB và năng lực miễn trừ giới hạn tiến trình Android 12+!",
                )
            } else {
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    PrivilegeCheckResult.Unauthorized(
                        ExecutionMode.SHIZUKU,
                        "Vui lòng cho phép Thien Huyen truy cập dịch vụ đặc quyền ADB trong hộp thoại Shizuku.",
                    )
                } else {
                    val granted = requestShizukuPermission()
                    if (granted) {
                        PrivilegeCheckResult.Authorized(
                            ExecutionMode.SHIZUKU,
                            "Shizuku (v${Shizuku.getVersion()}) cấp quyền thành công, đã tự động chuyển sang chế độ đặc quyền cấp ADB.",
                        )
                    } else {
                        PrivilegeCheckResult.Unauthorized(
                            ExecutionMode.SHIZUKU,
                            "Shizuku bị từ chối cấp quyền hoặc chờ quá thời gian, hãy kiểm tra trạng thái cấp quyền của Thien Huyen trong Shizuku App.",
                        )
                    }
                }
            }
        } catch (e: Exception) {
            logger.e("Kiểm tra Shizuku xảy ra bất thường", e)
            PrivilegeCheckResult.ServiceNotRunning(
                ExecutionMode.SHIZUKU,
                "Không thể kết nối đến dịch vụ Shizuku (${e.message}). Vui lòng kiểm tra Shizuku có đang chạy bình thường không.",
            )
        }
    }

    private suspend fun requestShizukuPermission(): Boolean {
        val result = CompletableDeferred<Boolean>()
        val listener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                result.complete(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        return try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
            withTimeoutOrNull(SHIZUKU_PERMISSION_WAIT_MS) { result.await() } ?: false
        } finally {
            Shizuku.removeRequestPermissionResultListener(listener)
        }
    }

    /**
     * 在授权成功后应用系统级特权优化（如解除 Android 12+ 幽灵进程 32 限制等）
     */
    private suspend fun applyPrivilegeOptimizations(mode: ExecutionMode) {
        when (mode) {
            ExecutionMode.ROOT -> {
                runCatching { executeViaRoot(PHANTOM_PROCESS_REMOVE_COMMAND, "privilege-opt-root") }
                    .onSuccess { result ->
                        if (!result.success) logger.w("Giải phóng giới hạn tiến trình ma qua Root thất bại: ${result.stderr}")
                    }
                    .onFailure {
                        logger.w("Gỡ giới hạn tiến trình ma qua Root thất bại", it)
                    }
                selfGrantWriteSettings(mode)
            }
            ExecutionMode.SHIZUKU -> {
                runCatching { executeViaShizuku(PHANTOM_PROCESS_REMOVE_COMMAND, "privilege-opt-shizuku") }
                    .onSuccess { result ->
                        if (!result.success) logger.w("Giải phóng giới hạn tiến trình ma qua Shizuku thất bại: ${result.stderr}")
                    }
                    .onFailure {
                        logger.w("Gỡ giới hạn tiến trình ẩn qua Shizuku thất bại", it)
                    }
                selfGrantWriteSettings(mode)
            }
            ExecutionMode.PROOT -> Unit
        }
    }

    /**
     * 以 shell/root 权限通过 appops 给自己授予 WRITE_SETTINGS，避免用户手动去系统设置授权。
     * 授权后 writeSystemSetting() 即可直接用 app 自身的 ContentResolver 写入系统设置，
     * 绕过部分国产 ROM（如 vivo）对 `settings put` shell 命令的静默拒绝。
     */
    private suspend fun selfGrantWriteSettings(mode: ExecutionMode) {
        if (Settings.System.canWrite(context)) {
            logger.i("WRITE_SETTINGS đã được cấp, bỏ qua tự cấp quyền")
            return
        }
        val pkg = context.packageName
        // appops op code 23 = android:write_settings；用名称更兼容
        val command = "appops set $pkg android:write_settings allow"
        val result = when (mode) {
            ExecutionMode.SHIZUKU -> runCatching { executeViaShizuku(command, "self-grant-write-settings") }
            ExecutionMode.ROOT -> runCatching { executeViaRoot(command, "self-grant-write-settings") }
            else -> return
        }
        result.onSuccess { r ->
            if (r.success && Settings.System.canWrite(context)) {
                logger.i("Tự cấp quyền WRITE_SETTINGS thành công (via ${mode.shortLabel})")
            } else {
                logger.w("Tự cấp quyền WRITE_SETTINGS thất bại: exit=${r.exitCode} stderr=${r.stderr} canWrite=${Settings.System.canWrite(context)}")
            }
        }.onFailure {
            logger.w("Tự cấp quyền WRITE_SETTINGS gặp lỗi", it)
        }
    }

    /**
     * 读取 Android 幽灵进程监控的实际系统值，而不是依赖应用内的“已执行”标记。
     * Android 12 引入该限制；不同系统版本/厂商可能采用数量上限或监控开关中的任一项。
     */
    suspend fun checkPhantomProcessLimit(): PhantomProcessLimitStatus = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return@withContext PhantomProcessLimitStatus(
                state = PhantomProcessLimitState.UNSUPPORTED,
                details = "Hệ thống dưới Android 12 không có giới hạn 32 tiến trình ma.",
            )
        }

        // 先走应用进程可读的系统 API。这样即使用户通过电脑 ADB 执行命令、当前使用 PRoot，
        // 只要 ROM 允许读取对应配置，页面仍能自行识别已经解除的状态。
        val directMonitoring = runCatching {
            Settings.Global.getString(context.contentResolver, "settings_enable_monitor_phantom_procs")
        }.getOrNull()
        val directStatus = if (directMonitoring != null) {
            parsePhantomProcessLimit("max=\nmonitor=$directMonitoring\n")
        } else {
            null
        }
        if (directStatus?.state == PhantomProcessLimitState.REMOVED) {
            return@withContext directStatus
        }

        val result = executeShellCommand(PHANTOM_PROCESS_QUERY_COMMAND)
        if (!result.success) {
            if (directStatus != null) return@withContext directStatus
            return@withContext PhantomProcessLimitStatus(
                state = PhantomProcessLimitState.UNAVAILABLE,
                details = result.stderr.ifBlank { "Cần bật và cấp quyền Shizuku hoặc Root trước mới có thể đọc trạng thái hệ thống." },
            )
        }

        parsePhantomProcessLimit(result.stdout)
    }

    /** 使用当前 Shizuku/Root 宿主权限解除 Android 12+ 幽灵进程限制。 */
    suspend fun removePhantomProcessLimit(): ShellExecResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return@withContext ShellExecResult(true, 0, "", "")
        }
        executeShellCommand(PHANTOM_PROCESS_REMOVE_COMMAND)
    }

    // ============================ HostBridge / 对外接口 ============================

    /** 响应式实际生效模式；权限失效时为 PRoot。 */
    val activeMode: Flow<ExecutionMode> = settingsDataStore.effectiveExecutionMode
    val preferredModeFlow: Flow<ExecutionMode> = settingsDataStore.preferredExecutionMode

    /** 读取当前实际生效模式；读取失败时回退 PRoot。 */
    private suspend fun currentMode(): ExecutionMode = runCatching { settingsDataStore.effectiveExecutionMode.first() }
        .getOrDefault(ExecutionMode.PROOT)

    private suspend fun preferredMode(): ExecutionMode = runCatching { settingsDataStore.preferredExecutionMode.first() }
        .getOrDefault(ExecutionMode.PROOT)

    /**
     * 在宿主侧以当前特权模式执行 Shell 命令。
     * - SHIZUKU 模式：通过 Shizuku Binder 以 ADB 级别 (shell uid) 执行
     * - ROOT 模式：通过 su 以 root uid 执行
     * - PROOT 模式：不支持，返回错误
     *
     * 这是打破"Phụ thuộc quyền vòng lặp"的关键能力：
     * 沙箱内无法直接执行需要 shell/root 权限的 Android 命令（如 settings put、pm grant、appops set），
     * 但通过 HostBridge → PrivilegeManager.executeShellCommand 可以绕过沙箱限制，
     * 在宿主侧以特权身份执行。
     */
    suspend fun executeShellCommand(
        command: String,
        operationId: String = UUID.randomUUID().toString(),
    ): ShellExecResult = withContext(Dispatchers.IO) {
        val snapshot = state.value
        if (snapshot.availability != PrivilegeAvailability.ACTIVE || snapshot.effectiveMode == ExecutionMode.PROOT) {
            return@withContext ShellExecResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = if (snapshot.availability == PrivilegeAvailability.CHECKING) {
                    "Quyền máy chủ vẫn đang kiểm tra khởi động, vui lòng thử lại sau."
                } else {
                    "Chế độ đang thực sự có hiệu lực hiện tại là PRoot; ${snapshot.preferredMode.shortLabel} ưu tiên tạm thời không khả dụng: ${snapshot.reason}"
                },
            )
        }
        val mode = snapshot.effectiveMode

        when (mode) {
            ExecutionMode.SHIZUKU -> executeViaShizuku(command, operationId)
            ExecutionMode.ROOT -> executeViaRoot(command, operationId)
            ExecutionMode.PROOT -> ShellExecResult(
                success = false,
                exitCode = -1,
                stdout = "",
                stderr = "Chế độ chạy hiện tại (PRoot) không hỗ trợ thực thi Shell của máy chủ. Hãy chuyển sang chế độ Shizuku hoặc Root trong cài đặt.",
            )
        }
    }

    /** Harness 取消任务时同步终止对应的 Shizuku/Root 宿主子进程。 */
    fun cancelShellCommand(operationId: String): Boolean =
        rootRunner.cancel(operationId) or shizukuHostServiceClient.cancel(operationId)

    /**
     * 直接通过 Android ContentResolver 写入 system 命名空间设置，绕过 shell 命令。
     * 仅适用于 system namespace（需要 WRITE_SETTINGS 权限）；secure/global 仍需 shell。
     * 返回 true 表示写入成功，false 表示无权限或写入异常（调用方应回退到 shell 命令）。
     */
    fun writeSystemSetting(key: String, value: String): Boolean = try {
        if (!Settings.System.canWrite(context)) {
            logger.w("writeSystemSetting: app lacks WRITE_SETTINGS permission, key=$key")
            false
        } else {
            val resolver = context.contentResolver
            // 优先按整数写入（亮度、超时等数值设置），失败则按字符串写入
            val asInt = value.toIntOrNull()
            if (asInt != null) {
                Settings.System.putInt(resolver, key, asInt)
            } else {
                value.toFloatOrNull()?.let { Settings.System.putFloat(resolver, key, it) }
                    ?: Settings.System.putString(resolver, key, value)
            }
            logger.i("writeSystemSetting: wrote $key=$value via ContentResolver")
            true
        }
    } catch (e: Exception) {
        logger.e("writeSystemSetting failed: key=$key value=$value", e)
        false
    }

    /**
     * 获取当前特权状态快照：当前激活模式 + 该模式的特权是否实际生效 + 各授权通道可用性。
     * 这是首页 UI 与沙箱内 Agent（经 HostBridge /api/health）共用的权威状态来源。
     * PRoot 模式无需探测即视为生效；Shizuku/Root 则以实时探测结果为准。
     */
    suspend fun getPrivilegeInfo(): PrivilegeInfo = withContext(Dispatchers.IO) {
        if (state.value.availability == PrivilegeAvailability.CHECKING) {
            return@withContext PrivilegeInfo(
                mode = ExecutionMode.PROOT,
                modeActive = true,
                shizukuAvailable = false,
                rootAvailable = false,
            )
        }
        val mode = currentMode()

        val shizukuAvailable = runCatching {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)

        // PRoot 无需任何授权，跳过耗时的 su 探测
        val rootAvailable = if (mode == ExecutionMode.ROOT) {
            runCatching {
                val process = ProcessBuilder("su", "-c", "echo ok").start()
                val completed = process.waitFor(3, TimeUnit.SECONDS)
                if (!completed) {
                    process.destroyForcibly()
                    false
                } else {
                    process.exitValue() == 0
                }
            }.getOrDefault(false)
        } else {
            false
        }

        val modeActive = when (mode) {
            ExecutionMode.PROOT -> true
            ExecutionMode.SHIZUKU -> shizukuAvailable
            ExecutionMode.ROOT -> rootAvailable
        }

        val effectiveMode = if (mode != ExecutionMode.PROOT && !modeActive) {
            val preferred = preferredMode()
            settingsDataStore.setEffectiveExecutionMode(ExecutionMode.PROOT)
            refreshState(
                preferred = preferred,
                effective = ExecutionMode.PROOT,
                availability = PrivilegeAvailability.DEGRADED,
                reason = "Quyền ${mode.shortLabel} đã hết hiệu lực, hiện đã hạ cấp an toàn xuống PRoot",
            )
            ExecutionMode.PROOT
        } else {
            mode
        }

        PrivilegeInfo(
            mode = effectiveMode,
            modeActive = effectiveMode == ExecutionMode.PROOT || modeActive,
            shizukuAvailable = shizukuAvailable,
            rootAvailable = rootAvailable,
        )
    }

    /**
     * 通过 Shizuku 以 ADB 级别 (shell uid, UID 2000) 执行命令。
     */
    private suspend fun executeViaShizuku(command: String, operationId: String): ShellExecResult {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            return ShellExecResult(false, -1, "", "Dịch vụ Shizuku chưa chạy. Hãy mở Shizuku App và đảm bảo dịch vụ đã khởi động.")
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            return ShellExecResult(false, -1, "", "Shizuku chưa cấp quyền. Vui lòng cấp quyền truy cập cho Thiên Huyền trong Shizuku App.")
        }

        return try {
            shizukuHostServiceClient.execute(operationId, command)
        } catch (e: Exception) {
            logger.e("Shizuku UserService execution failed", e)
            ShellExecResult(false, -1, "", "Shizuku UserService thực thi thất bại: ${e.message}")
        }
    }

    /**
     * 通过 su 以 root uid (UID 0) 执行命令。
     */
    private fun executeViaRoot(command: String, operationId: String): ShellExecResult =
        rootRunner.execute(operationId, command)

    companion object {
        private const val SHIZUKU_STARTUP_WAIT_MS = 3_000L
        private const val SHIZUKU_PERMISSION_WAIT_MS = 60_000L
        private const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        /** 可在电脑终端直接执行，适用于未使用 Shizuku/Root 的设备。 */
        const val PHANTOM_PROCESS_ADB_COMMAND =
            "adb shell device_config put activity_manager max_phantom_processes 2147483647\n" +
                "adb shell settings put global settings_enable_monitor_phantom_procs false"

        private const val PHANTOM_PROCESS_REMOVE_COMMAND =
            "/system/bin/device_config put activity_manager max_phantom_processes 2147483647; MAX_EXIT=\$?; " +
                "/system/bin/settings put global settings_enable_monitor_phantom_procs false; MONITOR_EXIT=\$?; " +
                "if [ \"\$MAX_EXIT\" -eq 0 ] || [ \"\$MONITOR_EXIT\" -eq 0 ]; then exit 0; else exit 1; fi"

        private const val PHANTOM_PROCESS_QUERY_COMMAND =
            "MAX=\$(/system/bin/device_config get activity_manager max_phantom_processes 2>/dev/null); " +
                "MONITOR=\$(/system/bin/settings get global settings_enable_monitor_phantom_procs 2>/dev/null); " +
                "printf 'max=%s\\nmonitor=%s\\n' \"\$MAX\" \"\$MONITOR\""
    }
}

enum class PhantomProcessLimitState {
    REMOVED,
    ACTIVE,
    UNAVAILABLE,
    UNSUPPORTED,
}

data class PhantomProcessLimitStatus(
    val state: PhantomProcessLimitState,
    val maxPhantomProcesses: Long? = null,
    val monitoringEnabled: Boolean? = null,
    val details: String,
)

internal fun parsePhantomProcessLimit(output: String): PhantomProcessLimitStatus {
    val values = output.lineSequence()
        .mapNotNull { line ->
            val separator = line.indexOf('=')
            if (separator <= 0) null else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        .toMap()
    val max = values["max"]?.takeUnless { it.isBlank() || it.equals("null", true) }?.toLongOrNull()
    val monitoring = values["monitor"]
        ?.takeUnless { it.isBlank() || it.equals("null", true) }
        ?.let { raw ->
            when (raw.lowercase()) {
                "1", "true" -> true
                "0", "false" -> false
                else -> null
            }
        }
    val removed = max == Long.MAX_VALUE || (max != null && max >= Int.MAX_VALUE) || monitoring == false

    return if (removed) {
        PhantomProcessLimitStatus(
            state = PhantomProcessLimitState.REMOVED,
            maxPhantomProcesses = max,
            monitoringEnabled = monitoring,
            details = "Đã gỡ bỏ giới hạn tiến trình ma của Android.",
        )
    } else {
        PhantomProcessLimitStatus(
            state = PhantomProcessLimitState.ACTIVE,
            maxPhantomProcesses = max,
            monitoringEnabled = monitoring,
            details = if (max == null && monitoring == null) {
                "Vẫn dùng giới hạn mặc định của hệ thống (thường tối đa 32 tiến trình ma)."
            } else {
                "Hệ thống vẫn đang hạn chế tiến trình ma."
            },
        )
    }
}

/** Shell 命令执行结果。 */
data class ShellExecResult(
    val success: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
)

/** 特权状态快照：首页 UI 与 HostBridge /api/health 共用的权威描述。 */
data class PrivilegeInfo(
    /** 当前激活（已持久化）的运行模式。 */
    val mode: ExecutionMode,
    /** 当前激活模式的特权是否实际生效（PRoot 恒 true；Shizuku/Root 以实时探测为准）。 */
    val modeActive: Boolean,
    val shizukuAvailable: Boolean,
    val rootAvailable: Boolean,
)
