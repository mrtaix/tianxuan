package top.wkbin.tianxuan.ui.settings.permission

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import top.wkbin.tianxuan.ui.components.RuntimeIconName

/**
 * 厂商保活引导步骤模型
 */
data class PermissionStep(
    val title: String,
    val description: String,
    val tip: String? = null,
)

/**
 * 品牌定义与识别模型
 */
enum class OemBrand(val id: String, val label: String, val osName: String) {
    XIAOMI("xiaomi", "Xiaomi / Redmi", "HyperOS / MIUI"),
    HUAWEI("huawei", "Huawei / Honor", "HarmonyOS / MagicOS"),
    VIVO("vivo", "vivo / iQOO", "OriginOS / Funtouch"),
    OPPO("oppo", "OPPO / OnePlus / realme", "ColorOS / RealmeUI"),
    MEIZU("meizu", "Meizu", "Flyme"),
    SAMSUNG("samsung", "Samsung", "One UI"),
    GENERIC("generic", "Android gốc / Hãng khác", "AOSP / Android");

    companion object {
        fun detect(): OemBrand {
            val manufacturer = Build.MANUFACTURER?.lowercase().orEmpty()
            val brand = Build.BRAND?.lowercase().orEmpty()
            val fingerprint = Build.FINGERPRINT?.lowercase().orEmpty()
            val hardware = Build.HARDWARE?.lowercase().orEmpty()
            val combined = "$manufacturer $brand $fingerprint $hardware"

            return when {
                combined.contains("xiaomi") || combined.contains("redmi") ||
                    combined.contains("poco") || combined.contains("miui") -> XIAOMI
                combined.contains("huawei") || combined.contains("honor") ||
                    combined.contains("emui") || combined.contains("magic") -> HUAWEI
                combined.contains("vivo") || combined.contains("iqoo") ||
                    combined.contains("funtouch") || combined.contains("origin") -> VIVO
                combined.contains("oppo") || combined.contains("realme") ||
                    combined.contains("oneplus") || combined.contains("coloros") -> OPPO
                combined.contains("meizu") || combined.contains("flyme") -> MEIZU
                combined.contains("samsung") -> SAMSUNG
                else -> GENERIC
            }
        }
    }
}

/**
 * 保活与权限专题枚举
 */
enum class KeepaliveTopic(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: RuntimeIconName,
) {
    AUTOSTART(
        id = "autostart",
        title = "Tự khởi động và đánh thức chạy nền",
        subtitle = "Cho phép hệ thống tự khởi chạy dịch vụ sandbox và agent khi bật máy hoặc khi cần chạy nền",
        icon = RuntimeIconName.Cpu,
    ),
    BATTERY_UNRESTRICTED(
        id = "battery_unrestricted",
        title = "Không hạn chế sử dụng pin",
        subtitle = "Miễn chính sách tiết kiệm pin và chế độ Doze để tiến trình PRoot không bị đóng băng khi tắt màn hình",
        icon = RuntimeIconName.Battery,
    ),
    LOCK_RECENTS(
        id = "lock_recents",
        title = "Khóa thẻ ứng dụng gần đây",
        subtitle = "Khóa CTHarness trong danh sách ứng dụng gần đây để thao tác dọn tất cả không dừng dịch vụ tiền cảnh",
        icon = RuntimeIconName.Shield,
    ),
    FLOATING_OVERLAY(
        id = "floating_overlay",
        title = "Cửa sổ nổi và hiển thị từ nền",
        subtitle = "Cho phép hiện thông báo và cửa sổ khi dựng xong ứng dụng hoặc khi agent cần tương tác trong lúc chạy nền",
        icon = RuntimeIconName.Terminal,
    ),
    NOTIFICATION(
        id = "notification",
        title = "Thông báo và dịch vụ tiền cảnh thường trực",
        subtitle = "Duy trì thông báo dịch vụ tiền cảnh để môi trường Linux tiếp tục chạy với độ ưu tiên cao nhất",
        icon = RuntimeIconName.Chat,
    ),
}

/**
 * 厂商保活与权限引导知识库仓储
 */
object PermissionGuideRepository {

    fun getSteps(brand: OemBrand, topic: KeepaliveTopic): List<PermissionStep> {
        return when (topic) {
            KeepaliveTopic.AUTOSTART -> when (brand) {
                OemBrand.XIAOMI -> listOf(
                    PermissionStep("Mở cài đặt ứng dụng", "Mở Cài đặt ➔ Cài đặt ứng dụng ➔ Quản lý quyền ➔ Quản lý tự khởi động."),
                    PermissionStep("Bật tự khởi động cho CTHarness", "Tìm CTHarness trong danh sách ứng dụng và bật công tắc tự khởi động."),
                    PermissionStep("Cho phép ứng dụng đánh thức lẫn nhau", "Mở thông tin CTHarness và bật Cho phép khởi động liên kết / Được ứng dụng khác đánh thức (khuyến nghị trên HyperOS)."),
                )
                OemBrand.HUAWEI -> listOf(
                    PermissionStep("Mở quản lý khởi chạy ứng dụng", "Vào Trình quản lý điện thoại ➔ Quản lý khởi chạy ứng dụng."),
                    PermissionStep("Chuyển sang quản lý thủ công", "Tìm CTHarness, tắt Quản lý tự động rồi bật cả ba tùy chọn trong hộp thoại: Cho phép tự khởi động, Cho phép khởi động liên kết và Cho phép hoạt động nền."),
                )
                OemBrand.VIVO -> listOf(
                    PermissionStep("Mở quyền tự khởi động", "Vào Cài đặt ➔ Ứng dụng và quyền ➔ Quản lý quyền ➔ thẻ Quyền ➔ Tự khởi động."),
                    PermissionStep("Bật tự khởi động CTHarness", "Tìm CTHarness và cho phép tự khởi động."),
                    PermissionStep("Cho phép chạy nền tiêu thụ nhiều pin", "Trong Cài đặt ➔ Pin ➔ Tiêu thụ pin cao khi chạy nền, cho phép CTHarness tiếp tục chạy nền."),
                )
                OemBrand.OPPO -> listOf(
                    PermissionStep("Mở quản lý tự khởi động", "Vào Cài đặt ➔ Ứng dụng ➔ Quản lý tự khởi động."),
                    PermissionStep("Cho phép tự khởi động và khởi động liên kết", "Tìm CTHarness, bật Cho phép tự khởi động và Cho phép khởi động liên kết."),
                )
                OemBrand.MEIZU -> listOf(
                    PermissionStep("Mở quản lý chạy nền", "Vào Trình quản lý điện thoại ➔ Quản lý quyền ➔ Quản lý chạy nền."),
                    PermissionStep("Duy trì chạy nền", "Đặt CTHarness thành Cho phép chạy nền hoặc Duy trì hoạt động nền."),
                )
                OemBrand.SAMSUNG -> listOf(
                    PermissionStep("Kiểm tra ứng dụng chạy tự động", "Vào Cài đặt ➔ Pin ➔ Giới hạn sử dụng nền."),
                    PermissionStep("Đưa ứng dụng ra khỏi danh sách ngủ", "Đảm bảo CTHarness không nằm trong Ứng dụng ngủ sâu và thêm vào danh sách Ứng dụng không bao giờ ngủ."),
                )
                OemBrand.GENERIC -> listOf(
                    PermissionStep("Kiểm tra quyền khởi động", "Trong thông tin ứng dụng của cài đặt hệ thống, tìm quyền Tự khởi động hoặc Khởi động cùng thiết bị và cho phép nếu có."),
                )
            }
            KeepaliveTopic.BATTERY_UNRESTRICTED -> when (brand) {
                OemBrand.XIAOMI -> listOf(
                    PermissionStep("Mở chính sách tiết kiệm pin của ứng dụng", "Nhấn giữ biểu tượng CTHarness ➔ Thông tin ứng dụng ➔ kéo xuống Chính sách tiết kiệm pin."),
                    PermissionStep("Chọn Không hạn chế", "Chọn Không hạn chế để không giới hạn hoạt động nền, phù hợp với biên dịch PRoot và tác vụ suy luận dài của agent."),
                )
                OemBrand.HUAWEI -> listOf(
                    PermissionStep("Bỏ qua tối ưu pin", "Vào Cài đặt ➔ Ứng dụng ➔ Quản lý quyền ➔ menu góc trên bên phải Quyền truy cập đặc biệt ➔ Tối ưu pin."),
                    PermissionStep("Không cho phép tối ưu", "Chọn bộ lọc Tất cả ứng dụng, tìm CTHarness và đặt thành Không cho phép tối ưu."),
                )
                OemBrand.VIVO -> listOf(
                    PermissionStep("Cho phép tiêu thụ pin nền", "Vào Cài đặt ➔ Pin ➔ Quản lý tiêu thụ pin nền."),
                    PermissionStep("Cho phép chạy nền tiêu thụ nhiều pin", "Tìm CTHarness và chọn Cho phép chạy nền tiêu thụ nhiều pin."),
                )
                OemBrand.OPPO -> listOf(
                    PermissionStep("Quản lý tiêu thụ pin", "Nhấn giữ biểu tượng CTHarness ➔ Thông tin ứng dụng ➔ Quản lý tiêu thụ pin."),
                    PermissionStep("Bật các tùy chọn hoạt động nền", "Bật Cho phép mọi hoạt động nền, Cho phép đánh thức tiền cảnh và tắt Tối ưu tiết kiệm pin sâu."),
                )
                OemBrand.SAMSUNG -> listOf(
                    PermissionStep("Chế độ sử dụng pin", "Vào Cài đặt ➔ Ứng dụng ➔ CTHarness ➔ Pin."),
                    PermissionStep("Chọn Không hạn chế", "Chuyển từ chế độ mặc định Đã tối ưu sang Không hạn chế (Unrestricted)."),
                )
                else -> listOf(
                    PermissionStep("Bỏ qua tối ưu pin", "Vào Thông tin ứng dụng ➔ Pin ➔ chọn Không hạn chế hoặc yêu cầu bỏ qua tối ưu pin của hệ thống."),
                )
            }
            KeepaliveTopic.LOCK_RECENTS -> listOf(
                PermissionStep("Mở ứng dụng gần đây", "Vuốt lên từ đáy màn hình rồi giữ (hoặc nhấn phím đa nhiệm) để xem các thẻ ứng dụng gần đây."),
                PermissionStep("Khóa thẻ CTHarness", "Nhấn giữ thẻ CTHarness (hoặc kéo thẻ xuống), rồi nhấn biểu tượng khóa 🔒 để hệ thống không dừng ứng dụng khi dọn tất cả tác vụ nền."),
            )
            KeepaliveTopic.FLOATING_OVERLAY -> listOf(
                PermissionStep("Cho phép cửa sổ nổi / Hiển thị trên ứng dụng khác", "Vào Thông tin ứng dụng ➔ Nâng cao ➔ Hiển thị trên ứng dụng khác / Cửa sổ nổi, rồi chọn Cho phép."),
                PermissionStep("Cho phép mở giao diện từ nền", "Trên Xiaomi, vivo và các hệ thống tương tự, vào Quản lý quyền để bật Mở giao diện từ nền hoặc Lối tắt màn hình chính."),
            )
            KeepaliveTopic.NOTIFICATION -> listOf(
                PermissionStep("Cho phép thông báo và dịch vụ tiền cảnh", "Đảm bảo CTHarness có quyền thông báo. Khi khởi động sandbox PRoot và vòng lặp agent, CTHarness chạy dịch vụ tiền cảnh thường trực để duy trì độ ưu tiên tiến trình."),
            )
        }
    }

    /**
     * 检查某项权限在当前系统中的状态
     */
    fun checkStatus(context: Context, topic: KeepaliveTopic): Boolean {
        return when (topic) {
            KeepaliveTopic.BATTERY_UNRESTRICTED -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            }
            KeepaliveTopic.NOTIFICATION -> {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
            KeepaliveTopic.FLOATING_OVERLAY -> {
                Settings.canDrawOverlays(context)
            }
            KeepaliveTopic.AUTOSTART,
            KeepaliveTopic.LOCK_RECENTS -> {
                // Android 无公开 API 直接读取 OEM 私有自启动/最近任务加锁状态
                false
            }
        }
    }

    /**
     * 构建跳转至该专题设置页的 Intent 列表（按优先级降级尝试）
     */
    fun buildLaunchIntents(context: Context, brand: OemBrand, topic: KeepaliveTopic): List<Intent> {
        val pkg = context.packageName
        return when (topic) {
            KeepaliveTopic.AUTOSTART -> buildAutostartIntents(pkg, brand)
            KeepaliveTopic.BATTERY_UNRESTRICTED -> listOf(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                appDetailsIntent(pkg),
            )
            KeepaliveTopic.FLOATING_OVERLAY -> listOf(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg")),
                appDetailsIntent(pkg),
            )
            KeepaliveTopic.NOTIFICATION -> listOf(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                },
                appDetailsIntent(pkg),
            )
            KeepaliveTopic.LOCK_RECENTS -> listOf(
                // 最近任务加锁不需要跳转，用户直接呼出多任务即可
                appDetailsIntent(pkg),
            )
        }
    }

    private fun buildAutostartIntents(pkg: String, brand: OemBrand): List<Intent> {
        return when (brand) {
            OemBrand.XIAOMI -> listOf(
                intentFor("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                intentFor("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity"),
                intentFor("com.miui.securitycenter", "com.miui.powercenter.PowerSettings"),
                appDetailsIntent(pkg),
            )
            OemBrand.HUAWEI -> listOf(
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"),
                intentFor("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                appDetailsIntent(pkg),
            )
            OemBrand.VIVO -> listOf(
                intentFor("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                intentFor("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                intentFor("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                appDetailsIntent(pkg),
            )
            OemBrand.OPPO -> listOf(
                intentFor("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                intentFor("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                intentFor("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                appDetailsIntent(pkg),
            )
            OemBrand.MEIZU -> listOf(
                intentFor("com.meizu.safe", "com.meizu.safe.permission.SmartBGActivity"),
                intentFor("com.meizu.safe", "com.meizu.safe.security.AppSecActivity"),
                appDetailsIntent(pkg),
            )
            OemBrand.SAMSUNG -> listOf(
                appDetailsIntent(pkg),
            )
            OemBrand.GENERIC -> listOf(
                appDetailsIntent(pkg),
            )
        }
    }

    private fun intentFor(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun appDetailsIntent(pkg: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
