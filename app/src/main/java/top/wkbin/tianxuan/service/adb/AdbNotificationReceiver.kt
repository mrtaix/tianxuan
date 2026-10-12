package top.wkbin.tianxuan.service.adb

import org.koin.core.component.inject
import org.koin.core.component.KoinComponent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.runtime.bridge.adb.EmbeddedAdbManager

/**
 * 接收来自系统通知栏的无线 ADB 操作：
 * 1. 提取 RemoteInput 输入的 6 位配对码并异步发起 TLS + SPAKE2 配对与连接；
 * 2. 处理直接连接、断开连接与关闭通知请求。
 */
class AdbNotificationReceiver : BroadcastReceiver(), KoinComponent {

    val embeddedAdbManager: EmbeddedAdbManager by inject()

    val adbNotificationManager: AdbNotificationManager by inject()

    val preferences: top.wkbin.tianxuan.core.datastore.RuntimePreferences by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            AdbNotificationManager.ACTION_PAIR_INPUT -> {
                val pairingCode = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(AdbNotificationManager.KEY_PAIRING_CODE)
                    ?.toString()
                    ?.trim()

                if (pairingCode.isNullOrBlank() || !pairingCode.matches(Regex("\\d{6}"))) {
                    adbNotificationManager.showFailed("Định dạng mã ghép nối không hợp lệ: phải là 6 chữ số thuần, vui lòng thử lại")
                    return
                }

                // 立即刷新为配对中状态（防止通知栏 RemoteInput 转圈死锁）
                adbNotificationManager.showPairingInProgress()
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        Log.i(TAG, "Đã nhận mã ghép đôi từ thanh thông báo, đang khởi tạo ghép đôi ADB không dây…")
                        val result = embeddedAdbManager.pair(pairingCode)
                        result.fold(
                            onSuccess = {
                                Log.i(TAG, "Ghép đôi ADB không dây trên thanh thông báo thành công")
                                // EmbeddedAdbManager 会自动切入 Connected 状态，并通过 StateFlow 触发 showConnected
                            },
                            onFailure = { error ->
                                Log.w(TAG, "Ghép đôi ADB không dây ở thanh thông báo thất bại", error)
                                adbNotificationManager.showFailed(error.message ?: "Ghép nối ADB không dây thất bại, hãy xác nhận mã ghép nối hệ thống và thử lại")
                            },
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AdbNotificationManager.ACTION_CONNECT -> {
                adbNotificationManager.showConnectingInProgress()
                val pendingResult = goAsync()
                scope.launch {
                    try {
                        Log.i(TAG, "Khởi tạo kết nối ADB không dây trực tiếp từ thanh thông báo…")
                        val result = embeddedAdbManager.connect()
                        result.fold(
                            onSuccess = {
                                Log.i(TAG, "Kết nối ADB không dây trên thanh thông báo thành công")
                            },
                            onFailure = { error ->
                                Log.w(TAG, "Kết nối ADB không dây từ thanh thông báo thất bại", error)
                                adbNotificationManager.showFailed(error.message ?: "Kết nối ADB không dây thất bại, hãy xác nhận cổng hoặc ghép đôi lại")
                            },
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            AdbNotificationManager.ACTION_DISCONNECT -> {
                Log.i(TAG, "Yêu cầu ngắt kết nối ADB không dây từ thanh thông báo")
                embeddedAdbManager.disconnect()
                adbNotificationManager.showPairingPrompt()
            }

            AdbNotificationManager.ACTION_DISMISS -> {
                adbNotificationManager.dismiss()
                scope.launch { preferences.setAdbNotificationEnabled(false) }
            }
        }
    }

    private companion object {
        const val TAG = "AdbNotifReceiver"
    }
}
