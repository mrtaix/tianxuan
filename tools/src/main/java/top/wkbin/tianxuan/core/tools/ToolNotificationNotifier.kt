package top.wkbin.tianxuan.core.tools

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.content.FileProvider
import java.io.File
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import top.wkbin.tianxuan.tools.R

/**
 * 插件与工具安装系统通知栏控制器
 * 实时同步并发安装进度、完成与失败状态到 Android 系统通知栏。
 */
@SuppressLint("MissingPermission")
class ToolNotificationNotifier(
    private val context: Context,
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val channelId = "tianxuan_tool_install"
    private val appLogo: Bitmap? by lazy {
        context.applicationInfo.icon.takeIf { it != 0 }?.let { iconRes ->
            runCatching { BitmapFactory.decodeResource(context.resources, iconRes) }.getOrNull()
        }
    }

    init {
        createChannel()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            channelId,
            "Tiến độ cài đặt plugin và công cụ",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Hiển thị tiến độ cài đặt và cập nhật các công cụ AI và plugin trong sandbox PRoot Thien Huyen"
            setShowBadge(false)
        }
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        systemManager?.createNotificationChannel(channel)
    }

    fun showProgress(toolId: String, toolName: String, message: String, progress: Float?) {
        val notificationId = toolNotificationId(toolId)
        val pendingIntent = getLaunchIntent()

        val progressPercent = ((progress ?: 0f) * 100).toInt().coerceIn(0, 100)
        val isIndeterminate = progress == null

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("Đang cài đặt $toolName")
            .setContentText(message)
            .setLargeIcon(appLogo)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progressPercent, isIndeterminate)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // Android 13+ 通知权限未授权时忽略
        }
    }

    fun showSuccess(toolId: String, toolName: String, version: String?) {
        val notificationId = toolNotificationId(toolId)
        val pendingIntent = getLaunchIntent()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("$toolName cài đặt hoàn tất")
            .setContentText("Phiên bản ${version ?: "已就绪"}，có thể dùng trực tiếp trong bảng điều khiển hoặc trung tâm công cụ")
            .setLargeIcon(appLogo)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showFailed(toolId: String, toolName: String, error: String) {
        val notificationId = toolNotificationId(toolId)
        val pendingIntent = getLaunchIntent()

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("$toolName cài đặt thất bại")
            .setContentText(error.take(120))
            .setLargeIcon(appLogo)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancel(toolId: String) {
        try {
            notificationManager.cancel(toolNotificationId(toolId))
        } catch (_: Exception) {
        }
    }

    // ==================== 智坊工程构建通知 ====================
    private val buildChannelId = "tianxuan_build_result"

    init {
        createBuildChannel()
    }

    private fun createBuildChannel() {
        val channel = NotificationChannel(
            buildChannelId,
            "Kết quả build dự án Tri Phuong",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Hiển thị kết quả biên dịch hoàn tất hoặc thất bại của dự án Android / Flutter trong Thiên Huyền Tri Phường"
            setShowBadge(true)
        }
        val systemManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        systemManager?.createNotificationChannel(channel)
    }

    fun showBuildProgress(projectName: String, step: String) {
        val notificationId = buildNotificationId(projectName)
        val pendingIntent = getLaunchIntent()
        val builder = NotificationCompat.Builder(context, buildChannelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("🔨 Đang biên dịch $projectName")
            .setContentText(step)
            .setLargeIcon(appLogo)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, 0, true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        try {
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showBuildSuccess(projectName: String, apkPath: String?) {
        val notificationId = buildNotificationId(projectName)
        val pendingIntent = apkPath?.let { getApkInstallIntent(File(it)) } ?: getLaunchIntent()
        val builder = NotificationCompat.Builder(context, buildChannelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("✅ $projectName biên dịch thành công")
            .setContentText(if (apkPath != null) "APK đã được tạo, nhấp để cài đặt vào điện thoại" else "Xây dựng đã hoàn tất")
            .setLargeIcon(appLogo)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
        try {
            // Some Android/OEM notification managers keep the previous ongoing
            // notification row when it is changed in-place to a non-ongoing one.
            // Remove the progress row first so a completed build cannot remain
            // stuck on the last "copying to Download" message.
            notificationManager.cancel(notificationId)
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun showBuildFailed(projectName: String, error: String) {
        val notificationId = buildNotificationId(projectName)
        val pendingIntent = getLaunchIntent()
        val builder = NotificationCompat.Builder(context, buildChannelId)
            .setSmallIcon(context.applicationInfo.icon)
            .setContentTitle("❌ $projectName biên dịch thất bại")
            .setContentText(error.take(120))
            .setLargeIcon(appLogo)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
        try {
            notificationManager.cancel(notificationId)
            notificationManager.notify(notificationId, builder.build())
        } catch (_: SecurityException) {
        }
    }

    fun cancelBuildNotification(projectName: String) {
        try {
            notificationManager.cancel(buildNotificationId(projectName))
        } catch (_: Exception) {
        }
    }

    private fun buildNotificationId(projectName: String): Int {
        return 30000 + (projectName.hashCode() and 0x7FFF)
    }

    private fun toolNotificationId(toolId: String): Int {
        return 20000 + (toolId.hashCode() and 0x7FFF)
    }

    private fun getLaunchIntent(): PendingIntent? {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun getApkInstallIntent(apk: File): PendingIntent? {
        if (!apk.isFile || apk.length() <= 0L) return null
        val stagedApk = runCatching { stageApkForInstall(apk) }.getOrNull() ?: return getLaunchIntent()
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", stagedApk)
        }.getOrNull() ?: return getLaunchIntent()
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("APK", uri)
        }
        return PendingIntent.getActivity(
            context,
            (stagedApk.absolutePath.hashCode() and 0x7FFF) + 40000,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun stageApkForInstall(apk: File): File {
        val dir = File(context.cacheDir, "notification-apk-installs").apply { mkdirs() }
        val now = System.currentTimeMillis()
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile && now - it.lastModified() > 24 * 60 * 60 * 1000L }
            .forEach { it.delete() }
        val staged = File(dir, "${apk.nameWithoutExtension}-$now-${apk.length()}.apk")
        apk.inputStream().use { input -> staged.outputStream().use { output -> input.copyTo(output) } }
        check(staged.isFile && staged.length() == apk.length()) { "Bản sao tạm APK không đầy đủ" }
        return staged
    }
}
