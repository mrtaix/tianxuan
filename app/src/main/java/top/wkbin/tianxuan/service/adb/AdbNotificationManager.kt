package top.wkbin.tianxuan.service.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.MainActivity
import top.wkbin.tianxuan.R
import top.wkbin.tianxuan.runtime.bridge.adb.EmbeddedAdbManager

/**
 * 无线 ADB 通知栏助手管理器。
 *
 * 解决在手机「开发者选项 ➔ 使用配对码配对设备」弹窗时，一旦切出应用弹窗自动关闭且配对码失效的问题。
 * 在通知栏常驻展示包含 RemoteInput 输入框与连接控制动作的通知，让用户直接下拉通知栏即可完成 6 位配对码输入与连接。
 */
class AdbNotificationManager(
    private val context: Context,
    private val embeddedAdbManager: EmbeddedAdbManager,
    private val preferences: top.wkbin.tianxuan.core.datastore.RuntimePreferences,
) {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var syncJob: Job? = null
    private var isManuallyDismissed = false

    init {
        createNotificationChannel()
        scope.launch {
            preferences.adbNotificationEnabled.collect { enabled ->
                if (enabled) {
                    startSync()
                } else {
                    stopSync()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        runCatching {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "ADB không dây và gỡ lỗi",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Trợ lý thông báo dùng để kéo xuống nhập mã ghép đôi và kết nối nhanh ADB không dây trực tiếp trong tùy chọn lập trình viên của điện thoại"
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }.onFailure { Log.w(TAG, "Tạo kênh thông báo ADB không dây thất bại", it) }
    }

    /** 开启与 EmbeddedAdbManager 状态的自动同步。进入无线 ADB 页面或需要配对时调用。 */
    fun startSync() {
        isManuallyDismissed = false
        embeddedAdbManager.startDiscovery(EmbeddedAdbManager.TAG_NOTIFICATION)
        if (syncJob?.isActive == true) return
        syncJob = scope.launch {
            combine(
                embeddedAdbManager.state,
                embeddedAdbManager.discovery,
            ) { state, discovery ->
                state to discovery
            }.collect { (state, discovery) ->
                if (isManuallyDismissed) return@collect
                when (state) {
                    is EmbeddedAdbManager.ConnectionState.Connected -> {
                        showConnected(state.host, state.port)
                    }
                    EmbeddedAdbManager.ConnectionState.Pairing -> {
                        showPairingInProgress()
                    }
                    EmbeddedAdbManager.ConnectionState.Connecting -> {
                        showConnectingInProgress()
                    }
                    is EmbeddedAdbManager.ConnectionState.Failed -> {
                        showFailed(state.message)
                    }
                    EmbeddedAdbManager.ConnectionState.Discovering,
                    EmbeddedAdbManager.ConnectionState.Disconnected -> {
                        showPairingPrompt(discovery.pairingEndpoints.size, discovery.connectEndpoints.size)
                    }
                }
            }
        }
    }

    /** 停止自动同步并关闭通知。 */
    fun stopSync() {
        syncJob?.cancel()
        syncJob = null
        embeddedAdbManager.stopDiscovery(EmbeddedAdbManager.TAG_NOTIFICATION)
        dismiss()
    }

    /** 显式刷新/显示当前配对就绪通知。 */
    fun showPairingPrompt(pairingEndpointsCount: Int = 0, connectEndpointsCount: Int = 0) {
        isManuallyDismissed = false
        val contentText = when {
            pairingEndpointsCount > 0 -> "Đã phát hiện cổng ghép nối · vui lòng nhập mã ghép nối 6 chữ số bên dưới"
            connectEndpointsCount > 0 -> "Đã phát hiện cổng kết nối · Có thể nhấp 「Kết nối」 trực tiếp hoặc nhập mã ghép đôi"
            else -> "Đã bật dò cổng mDNS · vui lòng nhấn 「Ghép nối thiết bị bằng mã ghép nối」 trong gỡ lỗi không dây hệ thống"
        }

        val notif = buildNotification(
            title = "Thien Huyen · Ghép đôi nhanh ADB không dây",
            contentText = contentText,
            isOngoing = true,
            actions = listOf(
                buildPairInputAction(),
                buildConnectAction(),
                buildDismissAction(),
            ),
        )
        safeNotify(notif)
    }

    /** 显示正在配对中的进度通知。 */
    fun showPairingInProgress() {
        isManuallyDismissed = false
        val notif = buildNotification(
            title = "Thien Huyen · Đang ghép nối ADB không dây…",
            contentText = "Đang bắt tay an toàn với cổng đã phát hiện (TLS + SPAKE2)…",
            isOngoing = true,
            showProgress = true,
            actions = listOf(buildDismissAction()),
        )
        safeNotify(notif)
    }

    /** 显示正在建立连接的进度通知。 */
    fun showConnectingInProgress() {
        isManuallyDismissed = false
        val notif = buildNotification(
            title = "Thiên Huyền · Đang thiết lập kết nối ADB…",
            contentText = "Đang hoàn tất xác thực khóa RSA…",
            isOngoing = true,
            showProgress = true,
            actions = listOf(buildDismissAction()),
        )
        safeNotify(notif)
    }

    /** 显示已连接成功通知。 */
    fun showConnected(host: String, port: Int) {
        isManuallyDismissed = false
        val notif = buildNotification(
            title = "Thiên Huyền · ADB không dây đã kết nối",
            contentText = "Đã kết nối tới $host:$port · Nhấp để trở về bàn làm việc",
            isOngoing = false,
            actions = listOf(
                buildOpenWorkbenchAction(),
                buildDisconnectAction(),
            ),
        )
        safeNotify(notif)
    }

    /** 显示失败与重试通知。 */
    fun showFailed(errorMessage: String) {
        isManuallyDismissed = false
        val notif = buildNotification(
            title = "Thien Huyen · Kết nối/ghép đôi ADB không dây bất thường",
            contentText = errorMessage,
            isOngoing = false,
            actions = listOf(
                buildPairInputAction(isRetry = true),
                buildConnectAction(),
                buildDismissAction(),
            ),
        )
        safeNotify(notif)
    }

    /** 关闭通知栏通知。 */
    fun dismiss() {
        isManuallyDismissed = true
        runCatching { notificationManager.cancel(NOTIFICATION_ID) }
    }

    // ── 内部构建器 ─────────────────────────────────────────────────────────────

    private fun buildNotification(
        title: String,
        contentText: String,
        isOngoing: Boolean,
        showProgress: Boolean = false,
        actions: List<NotificationCompat.Action> = emptyList(),
    ): Notification {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ADB_LOGCAT
            putExtra(EXTRA_NAVIGATE_TO, "adb_logcat")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_LOGCAT,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.tianxuan_notification)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(contentPendingIntent)
            .setOngoing(isOngoing)
            .setAutoCancel(!isOngoing)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (showProgress) {
            builder.setProgress(0, 0, true)
        }

        actions.forEach { builder.addAction(it) }
        return builder.build()
    }

    private fun buildPairInputAction(isRetry: Boolean = false): NotificationCompat.Action {
        val mutableFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pairIntent = Intent(context, AdbNotificationReceiver::class.java).apply {
            action = ACTION_PAIR_INPUT
        }
        val pairPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_PAIR_INPUT,
            pairIntent,
            mutableFlags,
        )

        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("Nhập mã ghép đôi 6 chữ số (ví dụ 123456)")
            .build()

        return NotificationCompat.Action.Builder(
            R.drawable.tianxuan_notification,
            if (isRetry) "Thử lại nhập mã ghép nối" else "Nhập mã ghép nối",
            pairPendingIntent,
        ).addRemoteInput(remoteInput).build()
    }

    private fun buildConnectAction(): NotificationCompat.Action {
        val connectIntent = Intent(context, AdbNotificationReceiver::class.java).apply {
            action = ACTION_CONNECT
        }
        val connectPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_CONNECT,
            connectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.tianxuan_notification,
            "Kết nối",
            connectPendingIntent,
        ).build()
    }

    private fun buildDisconnectAction(): NotificationCompat.Action {
        val disconnectIntent = Intent(context, AdbNotificationReceiver::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DISCONNECT,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.tianxuan_notification,
            "Ngắt kết nối",
            disconnectPendingIntent,
        ).build()
    }

    private fun buildDismissAction(): NotificationCompat.Action {
        val dismissIntent = Intent(context, AdbNotificationReceiver::class.java).apply {
            action = ACTION_DISMISS
        }
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DISMISS,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.tianxuan_notification,
            "Tắt thông báo",
            dismissPendingIntent,
        ).build()
    }

    private fun buildOpenWorkbenchAction(): NotificationCompat.Action {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_ADB_LOGCAT
            putExtra(EXTRA_NAVIGATE_TO, "adb_logcat")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_OPEN_LOGCAT,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            R.drawable.tianxuan_notification,
            "Mở bàn làm việc",
            openPendingIntent,
        ).build()
    }

    private fun safeNotify(notification: Notification) {
        runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }.onFailure { Log.w(TAG, "Phát thông báo ADB không dây thất bại", it) }
    }

    companion object {
        const val CHANNEL_ID = "tianxuan_wireless_adb"
        const val NOTIFICATION_ID = 2005

        const val ACTION_PAIR_INPUT = "top.wkbin.tianxuan.action.ADB_PAIR_INPUT"
        const val ACTION_CONNECT = "top.wkbin.tianxuan.action.ADB_CONNECT"
        const val ACTION_DISCONNECT = "top.wkbin.tianxuan.action.ADB_DISCONNECT"
        const val ACTION_DISMISS = "top.wkbin.tianxuan.action.ADB_DISMISS"
        const val ACTION_OPEN_ADB_LOGCAT = "top.wkbin.tianxuan.action.OPEN_ADB_LOGCAT"

        const val KEY_PAIRING_CODE = "adb_pairing_code"
        const val EXTRA_NAVIGATE_TO = "navigate_to"

        private const val REQUEST_CODE_OPEN_LOGCAT = 2051
        private const val REQUEST_CODE_PAIR_INPUT = 2052
        private const val REQUEST_CODE_CONNECT = 2053
        private const val REQUEST_CODE_DISCONNECT = 2054
        private const val REQUEST_CODE_DISMISS = 2055

        private const val TAG = "AdbNotifManager"
    }
}
