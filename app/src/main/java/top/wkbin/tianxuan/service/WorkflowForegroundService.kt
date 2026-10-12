package top.wkbin.tianxuan.service

import org.koin.android.ext.android.inject
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlin.math.absoluteValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.R
import top.wkbin.tianxuan.core.model.workflow.WorkflowRunStatus
import top.wkbin.tianxuan.harness.workflow.WorkflowRunManager
import top.wkbin.tianxuan.lifecycle.ProcessingPowerLease
import top.wkbin.tianxuan.lifecycle.RuntimeLifecycleSupervisor

/**
 * 工作流后台执行前台服务：任意工作流运行时保活（dataSync + WakeLock/WifiLock），
 * 逐运行展示进度通知；运行结束发终态通知后自行退出。
 * 由 TianXuanApplication 根据 WorkflowRunManager.running 联动启动。
 */
class WorkflowForegroundService : Service() {

    val runManager: WorkflowRunManager by inject()
    val lifeCycleSupervisor: RuntimeLifecycleSupervisor by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var collecting = false
    /** 当前向 RuntimeLifecycleSupervisor 持有的保活租约（工作流运行时持有）。 */
    private var powerLease: ProcessingPowerLease? = null
    /** 出现过运行中状态的 executionId：用于检测"Đang chạy → Trạng thái cuối"迁移，终态通知只发一次。 */
    private val seenRunning = mutableSetOf<String>()
    /** 进度通知节流：executionId → 上次发布时间。 */
    private val lastProgressPostAt = mutableMapOf<String, Long>()

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            // 进度渠道低优先级（静默常驻）；结果渠道高优先级（完成/失败可感知）
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Workflow chạy nền", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Hiển thị tiến độ thực thi nền của workflow"
                    enableVibration(false)
                    setSound(null, null)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
            manager.createNotificationChannel(
                NotificationChannel(RESULT_CHANNEL_ID, "Kết quả workflow", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Nhắc nhở khi workflow chạy xong, thất bại hoặc bị dừng"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                },
            )
        }.onFailure { Log.w(TAG, "Tạo kênh thông báo thất bại", it) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                val executionId = intent.getStringExtra(EXTRA_EXECUTION_ID)
                runCatching {
                    if (executionId != null) {
                        runManager.cancel(executionId)
                    } else {
                        // 无指定运行时停止全部仍在推进的运行
                        runManager.activeRuns.value.values
                            .filter { it.status !in WorkflowRunManager.TERMINAL }
                            .forEach { runManager.cancel(it.executionId) }
                    }
                }.onFailure { Log.w(TAG, "Hủy workflow thất bại", it) }
                return START_NOT_STICKY
            }
            else -> {
                safeStartForeground(NOTIFICATION_ID, runningNotification())
                acquireLease()
                if (!collecting) {
                    collecting = true
                    serviceScope.launch {
                        runManager.activeRuns.collectLatest { runs ->
                            val active = runs.values.filter { it.status !in WorkflowRunManager.TERMINAL }
                            runs.values.forEach { state ->
                                if (state.status in WorkflowRunManager.TERMINAL && seenRunning.remove(state.executionId)) {
                                    safeNotify(
                                        runNotificationId(state.executionId),
                                        terminalNotification(state),
                                    )
                                }
                            }
                            if (active.isEmpty()) {
                                releaseLease()
                                stopForegroundSafely(STOP_FOREGROUND_DETACH)
                                stopSelf()
                            } else {
                                acquireLease()
                                active.forEach { state ->
                                    seenRunning.add(state.executionId)
                                    // bash 节点的流式日志每 ~300ms 刷新一次 state，
                                    // 直接转发会让通知管理器刷屏；按间隔节流
                                    val now = System.currentTimeMillis()
                                    if (now - (lastProgressPostAt[state.executionId] ?: 0L) >= PROGRESS_INTERVAL_MS) {
                                        lastProgressPostAt[state.executionId] = now
                                        safeNotify(runNotificationId(state.executionId), progressNotification(state))
                                    }
                                }
                            }
                        }
                    }
                }
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(startId: Int) {
        // dataSync 有系统级硬超时（Android 15+）：必须立即退出前台；运行本身以
        // DETACH 模式继续推进，只是失去前台优先级与通知。
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelfResult(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        onTimeout(startId)
    }

    override fun onDestroy() {
        releaseLease()
        serviceScope.cancel()
        super.onDestroy()
    }

    /** 向 [RuntimeLifecycleSupervisor] 申请租约（幂等，已持有时跳过）。 */
    private fun acquireLease() {
        if (powerLease != null) return
        powerLease = lifeCycleSupervisor.acquireLease(LEASE_HOLDER_ID)
        Log.i(TAG, "Acquired power lease from RuntimeLifecycleSupervisor")
    }

    /** 释放租约；幂等，安全多次调用。 */
    private fun releaseLease() {
        powerLease?.close()
        powerLease = null
        Log.i(TAG, "Released power lease from RuntimeLifecycleSupervisor")
    }


    private fun openRunIntent(executionId: String?): PendingIntent {
        val intent = Intent(this, top.wkbin.tianxuan.MainActivity::class.java)
            .setAction(ACTION_OPEN_RUN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        executionId?.let { intent.putExtra(EXTRA_EXECUTION_ID, it) }
        return PendingIntent.getActivity(
            this,
            (executionId?.hashCode() ?: 0).absoluteValue,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun runningNotification(): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.tianxuan_notification)
        .setContentTitle("Workflow đang chạy")
        .setContentText("Giữ sống nền đang tiến hành")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setAutoCancel(false)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .build()

    private fun progressNotification(state: top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeState): Notification {
        val active = state.nodeStates.entries.firstOrNull { it.value.status in ACTIVE_NODE_STATUSES }
        val nodeTitle = active?.let { (id, _) ->
            state.definition.nodes.firstOrNull { it.id == id }?.title ?: id
        }.orEmpty()
        val detail = active?.value?.progressMessage?.takeIf { it.isNotBlank() } ?: "运行中"
        val stopPending = PendingIntent.getService(
            this,
            runNotificationId(state.executionId),
            Intent(this, WorkflowForegroundService::class.java)
                .setAction(ACTION_STOP)
                .putExtra(EXTRA_EXECUTION_ID, state.executionId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.tianxuan_notification)
            .setContentTitle("Thiên Huyền · ${state.definition.name}")
            .setContentText(if (nodeTitle.isNotBlank()) "$nodeTitle · $detail" else detail)
            .setContentIntent(openRunIntent(state.executionId))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.tianxuan_notification,
                    getString(R.string.tianxuan_notification_stop),
                    stopPending,
                ),
            )
            .build()
    }

    private fun terminalNotification(state: top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeState): Notification {
        val (title, text) = when (state.status) {
            WorkflowRunStatus.SUCCESS -> "Workflow hoàn thành" to "${state.definition.name} tất cả các node thực thi thành công"
            WorkflowRunStatus.FAILED -> "Luồng công việc thất bại" to "${state.definition.name}：${state.error ?: "执行失败"}"
            else -> "Workflow đã dừng" to "${state.definition.name}：${state.error ?: "已取消"}"
        }
        return NotificationCompat.Builder(this, RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.tianxuan_notification)
            .setContentTitle("Thiên Huyền · $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openRunIntent(state.executionId))
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun safeStartForeground(id: Int, notification: Notification) {
        runCatching { startForeground(id, notification) }
            .onFailure { Log.w(TAG, "startForeground thất bại", it) }
    }

    private fun stopForegroundSafely(flag: Int) {
        runCatching { stopForeground(flag) }
            .onFailure { Log.w(TAG, "stopForeground thất bại", it) }
    }

    private fun safeNotify(id: Int, notification: Notification) {
        runCatching { getSystemService(NotificationManager::class.java).notify(id, notification) }
            .onFailure { Log.w(TAG, "Phát thông báo thất bại", it) }
    }

    private fun runNotificationId(executionId: String): Int =
        NOTIFICATION_ID + (executionId.hashCode().absoluteValue % 9000) + 1

    companion object {
        const val ACTION_STOP = "top.wkbin.tianxuan.action.STOP_WORKFLOW_SERVICE"
        const val ACTION_OPEN_RUN = "top.wkbin.tianxuan.action.OPEN_WORKFLOW_RUN"
        const val EXTRA_EXECUTION_ID = "extra_workflow_execution_id"
        private const val CHANNEL_ID = "tianxuan-workflow-v5"
        private const val RESULT_CHANNEL_ID = "tianxuan-workflow-result"
        // 通知 id 段独立于 AgentForegroundService（2001 系），避免 hash 取模后互相覆盖
        private const val NOTIFICATION_ID = 30_001
        private const val PROGRESS_INTERVAL_MS = 2_000L
        private const val TAG = "WorkflowFgService"
        private const val LEASE_HOLDER_ID = "workflow"
        private val ACTIVE_NODE_STATUSES = setOf(
            top.wkbin.tianxuan.core.model.workflow.NodeRunStatus.RUNNING,
            top.wkbin.tianxuan.core.model.workflow.NodeRunStatus.STREAMING,
            top.wkbin.tianxuan.core.model.workflow.NodeRunStatus.WAITING_APPROVAL,
        )

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "Quyền thông báo chưa được cấp, bỏ qua dịch vụ giữ sống tiền cảnh của workflow (chạy vẫn tiếp tục)")
                return
            }
            runCatching {
                context.startForegroundService(Intent(context, WorkflowForegroundService::class.java))
            }.onFailure { Log.w(TAG, "Khởi động dịch vụ tiền cảnh workflow thất bại", it) }
        }
    }
}
