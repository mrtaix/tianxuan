package top.wkbin.tianxuan.runtime.virtualdisplay

import android.content.Context
import android.provider.Settings
import com.ai.assistance.showerclient.ShowerBinderRegistry
import com.ai.assistance.showerclient.ShowerController
import com.ai.assistance.showerclient.ShowerEnvironment
import com.ai.assistance.showerclient.ShowerServerManager
import com.ai.assistance.showerclient.ShowerVideoRenderer
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.model.ExecutionMode
import top.wkbin.tianxuan.runtime.privilege.PrivilegeAvailability
import top.wkbin.tianxuan.runtime.privilege.PrivilegeManager
import java.util.concurrent.ConcurrentHashMap

/**
 * 虚拟屏能力门面（多会话版）。
 *
 * 职责：
 * 1. 构造时把 [TianxuanShowerShellRunner] 装配进 [ShowerEnvironment]——这是 showerclient
 *    "Không liên quan host"设计的唯一注入点，Koin 单例首次创建即完成装配；
 * 2. 按 [sessionId] 维护相互独立的虚拟屏会话（每会话一块虚拟屏，可多 Agent 并行）；
 * 3. 对上暴露「确保虚拟屏 → 启动应用 → 截图 / 输入 → 关闭」的完整闭环，
 *    GUI 原语执行见 [VirtualScreenToolkit]，Agent 工具入口见 harness ToolExecutor 的
 *    virtual_screen_* 分支。
 *
 * 前置条件：需要 Shizuku（ADB 级）或 Root 特权；PRoot 模式下所有 shell 命令
 * 会在 [PrivilegeManager] 处失败，本门面相应返回 null/false。
 */
class VirtualDisplayCoordinator(
    private val context: Context,
    private val privilegeManager: PrivilegeManager,
    private val logger: AppLogger,
) {

    private val sessions = ConcurrentHashMap<String, ShowerController>()

    /** 最近一次虚拟屏操作失败的精确原因（供工具调用回显，替代笼统的"Không đủ quyền"）。 */
    @Volatile
    var lastFailureReason: String? = null

    init {
        ShowerEnvironment.shellRunner = TianxuanShowerShellRunner(privilegeManager, logger)
        // 追加更可靠的 workDir 候选：第三方库默认只有 /data/local/tmp 与
        // /data/data/com.android.shell/files，部分 ROM（小米 Android 16 实测）两者都不可写，
        // 导致 shower-server 无法部署。/dev/shm 是 tmpfs，shell uid 天然可写，可靠兜底。
        ShowerEnvironment.workDirCandidates = ShowerEnvironment.workDirCandidates + listOf(
            "/dev/shm",
            "/sdcard/Android/data/com.android.shell/files",
        )
    }

    /** shower-server Binder 是否已就绪（收到 SHOWER_BINDER_READY 广播且未死亡） */
    val isServerReady: Boolean
        get() = ShowerBinderRegistry.hasAliveService()

    /** 当前活跃会话 ID 集合 */
    val activeSessionIds: Set<String>
        get() = sessions.keys.toSet()

    /** 取指定会话的 controller（不存在则创建，仅持有本地状态，不触发 server 交互） */
    fun controller(sessionId: String): ShowerController =
        sessions.getOrPut(sessionId) { ShowerController() }

    /** 指定会话当前虚拟屏 displayId（未创建为 null） */
    fun getDisplayId(sessionId: String): Int? = sessions[sessionId]?.getDisplayId()

    /** 指定会话的视频流尺寸（未创建或未收到流为 null） */
    fun getVideoSize(sessionId: String): Pair<Int, Int>? = sessions[sessionId]?.getVideoSize()

    /**
     * 确保 shower-server 已启动，并为指定会话创建/复用一块与主屏同尺寸同密度的虚拟屏。
     *
     * @param sessionId 会话 ID；不同会话持有不同虚拟屏
     * @param bitrateKbps H.264 视频流码率；null 使用 server 默认值
     * @return 虚拟屏 displayId；server 启动失败或建屏失败返回 null
     */
    suspend fun ensureVirtualDisplay(
        sessionId: String = DEFAULT_SESSION_ID,
        bitrateKbps: Int? = null,
    ): Int? {
        val controller = controller(sessionId)

        // 1. 先检查宿主特权通道状态：若 effectiveMode 不是 SHIZUKU/ROOT（启动时被降级到
        //    PRoot，或 Shizuku 授权后未重启刷新），所有 shell 命令都会失败，resolveWorkDir
        //    会误报"Các thư mục ứng viên đều không ghi được"。先给出真实原因，避免误导用户查目录权限。
        val priv = privilegeManager.state.value
        if (priv.availability != PrivilegeAvailability.ACTIVE ||
            priv.effectiveMode == ExecutionMode.PROOT
        ) {
            lastFailureReason = buildString {
                append("Kênh đặc quyền host chưa có hiệu lực: effectiveMode hiện tại=${priv.effectiveMode}, ")
                append("availability=${priv.availability}. Lý do: ${priv.reason}.")
                append("Hãy vào Cài đặt Thiên Huyền → Giữ sống hệ thống và chẩn đoán lập trình viên → Chế độ đặc quyền host và chuyển sang Shizuku, ")
                append("Hoặc khởi động lại Thiên Huyền App để trạng thái đặc quyền được kiểm tra lại khi khởi động nguội.")
                if (!priv.shizukuAvailable) {
                    append("(Dịch vụ Shizuku hiện không khả dụng: vui lòng mở Shizuku App và đảm bảo dịch vụ đã khởi động, quyền đã được cấp)")
                }
            }
            logger.w("Màn hình ảo ensureVirtualDisplay bị chặn: kênh đặc quyền chưa có hiệu lực ($priv)")
            return null
        }

        if (!ShowerServerManager.ensureServerStarted(context)) {
            // 主动诊断：特权通道已生效但 server 启动失败。用同一条 Shizuku/Root 通道直接
            // 探测真实目录可写性与 stderr，区分「通道执行失败」与「目录真的不可写」——
            // 用户实测 /data/local/tmp 可写却判定失败，说明大概率是通道执行的问题。
            val probe = privilegeManager.executeShellCommand(
                "D=/data/local/tmp; touch \$D/.tianxuan_probe 2>&1; E1=\$?; rm -f \$D/.tianxuan_probe 2>/dev/null; echo PROBE_EXIT=\$E1; touch \$D/.tianxuan_probe2 2>&1; echo PROBE2_EXIT=\$?; rm -f \$D/.tianxuan_probe2",
            )
            lastFailureReason = buildString {
                append("Khởi động shower-server thất bại: kênh đặc quyền đã có hiệu lực (${priv.effectiveMode}) nhưng dò tìm thư mục làm việc đều thất bại.")
                append("Đã thử: ${ShowerEnvironment.workDirCandidates}.")
                if (probe.success) {
                    append("Kết quả dò chủ động /data/local/tmp: ${probe.stdout.take(200)} (exitCode=${probe.exitCode}).")
                    append("Nếu PROBE_EXIT=0 nhưng server vẫn xác định không ghi được, nghĩa là lệnh dò của ShowerServerManager đã thất bại ở một bước nào đó, ")
                    append("Cần xem logcat (tag: ShowerServerManager).")
                } else {
                    append("Thực thi lệnh thăm dò qua kênh đặc quyền thất bại (exitCode=${probe.exitCode}): ${probe.stderr.take(300)}.")
                    append("Điều này chỉ ra kênh Shizuku UserService bất thường (binder gián đoạn/chưa kết nối), chứ không phải vấn đề quyền thư mục.")
                }
            }
            logger.w("Khởi động server màn hình ảo thất bại: dò tìm thư mục làm việc đều thất bại; dò chủ động exit=${probe.exitCode} stderr=${probe.stderr.take(200)}")
            return null
        }
        val metrics = context.resources.displayMetrics
        val ok = controller.ensureDisplay(
            context = context,
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            dpi = metrics.densityDpi,
            bitrateKbps = bitrateKbps,
        )
        if (!ok) {
            lastFailureReason = "Tạo màn hình ảo thất bại (shower-server đã khởi động nhưng ensureDisplay thất bại, session=$sessionId). Có thể do tham số phản chiếu màn hình không khớp hoặc giao tiếp Binder bị gián đoạn, vui lòng xem logcat."
            logger.w("Tạo màn hình ảo thất bại (session=$sessionId, server đã khởi động)")
            return null
        }
        lastFailureReason = null
        return controller.getDisplayId()
    }

    /** 在指定会话的虚拟屏上以 shell 身份启动第三方应用（需 Shizuku/Root；普通 App 无法做到） */
    suspend fun launchApp(sessionId: String = DEFAULT_SESSION_ID, packageName: String): Boolean =
        controller(sessionId).launchApp(packageName)

    /** 指定会话虚拟屏整屏截图（PNG 字节；server 内部为 scrcpy 式临时镜像抓帧） */
    suspend fun requestScreenshot(
        sessionId: String = DEFAULT_SESSION_ID,
        timeoutMs: Long = SCREENSHOT_TIMEOUT_MS,
    ): ByteArray? = controller(sessionId).requestScreenshot(timeoutMs)

    /**
     * 显示指定会话的虚拟屏可视化悬浮窗（视频流 + 触摸回传）。
     *
     * @return false 表示缺少「显示在其他应用上层」权限（Settings.canDrawOverlays），
     *         调用方应引导用户到系统设置开启后重试
     */
    fun showOverlay(sessionId: String = DEFAULT_SESSION_ID): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        if (getDisplayId(sessionId) == null) return false
        VirtualDisplayHud.show(context, sessionId, this)
        return true
    }

    /** 隐藏虚拟屏可视化悬浮窗（未显示时为幂等空操作）。 */
    fun hideOverlay() {
        VirtualDisplayHud.hide()
    }

    /**
     * 销毁指定会话的虚拟屏并释放本地状态；server 进程由其空闲看护（15s 无客户端）自行退出。
     */
    suspend fun closeSession(sessionId: String) {
        if (VirtualDisplayHud.showingSessionId == sessionId) {
            hideOverlay()
        }
        val controller = sessions.remove(sessionId) ?: return
        runCatching { controller.shutdown() }
            .onFailure { logger.w("Đóng phiên màn hình ảo thất bại (session=$sessionId): ${it.message}") }
    }

    /** 关闭全部会话（App 退出或工作流整体结束时使用） */
    suspend fun closeAllSessions() {
        for (sessionId in sessions.keys.toList()) {
            closeSession(sessionId)
        }
    }

    /**
     * 内存水位哨兵联动：丢弃所有会话视频链路的等待缓冲（可再生数据）。
     * 真正的缓冲在 showerclient 渲染器内，此处仅转发，避免 app 层直接依赖 showerclient。
     */
    fun trimVideoBuffers() {
        ShowerVideoRenderer.trimAllPending()
    }

    private companion object {
        const val SCREENSHOT_TIMEOUT_MS = 3000L
        const val DEFAULT_SESSION_ID = "default"
    }
}
