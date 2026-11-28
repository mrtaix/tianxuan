package top.wkbin.tianxuan.runtime.service

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class RuntimeServiceController(
    private val context: Context,
) {
    fun start(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Chưa cấp quyền thông báo; bỏ qua dịch vụ giữ Runtime chạy nền")
            return false
        }
        return runCatching {
            ContextCompat.startForegroundService(
                context,
                Intent(context, RuntimeForegroundService::class.java),
            )
            true
        }.onFailure { Log.w(TAG, "Khởi động dịch vụ nền Runtime thất bại", it) }
            .getOrDefault(false)
    }

    fun stop() {
        val intent = Intent(context, RuntimeForegroundService::class.java)
            .setAction(RuntimeForegroundService.ACTION_STOP)
        runCatching { context.startService(intent) }
            .onFailure {
                // 后台态禁止 startService（Android 8+ 起会抛 IllegalStateException）。退化为
                // stopService，至少不让调用方崩溃；能被正常解除前台/清理的路径仍走 ACTION_STOP。
                Log.w(TAG, "Gửi ACTION_STOP thất bại, chuyển sang stopService", it)
                runCatching { context.stopService(Intent(context, RuntimeForegroundService::class.java)) }
            }
    }

    private companion object {
        const val TAG = "RuntimeServiceController"
    }
}
