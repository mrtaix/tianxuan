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
    GENERIC("generic", "Gốc / thương hiệu khác", "AOSP / Android");

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
        title = "Tự khởi động và đánh thức nền",
        subtitle = "Cho phép hệ thống tự động kéo lên sandbox và dịch vụ Agent khi khởi động hoặc khi nền cần",
        icon = RuntimeIconName.Cpu,
    ),
    BATTERY_UNRESTRICTED(
        id = "battery_unrestricted",
        title = "Tối ưu pin không giới hạn",
        subtitle = "Miễn trừ chính sách tiết kiệm pin hệ thống và chế độ Doze, ngăn tiến trình PRoot bị đóng băng sau khi tắt màn hình",
        icon = RuntimeIconName.Battery,
    ),
    LOCK_RECENTS(
        id = "lock_recents",
        title = "Khóa thẻ đa nhiệm",
        subtitle = "Khóa Thiên Huyền trong danh sách tác vụ gần đây, tránh bị kill dịch vụ tiền cảnh khi dọn một chạm",
        icon = RuntimeIconName.Shield,
    ),
    FLOATING_OVERLAY(
        id = "floating_overlay",
        title = "Cửa sổ nổi và bật lên nền",
        subtitle = "Cho phép bật thông báo và cửa sổ build hoàn tất hoặc tương tác Agent khi chạy nền",
        icon = RuntimeIconName.Terminal,
    ),
    NOTIFICATION(
        id = "notification",
        title = "Thông báo và thường trú foreground",
        subtitle = "Giữ hiển thị thông báo dịch vụ giữ sống tiền cảnh, đảm bảo runtime Linux sống với độ ưu tiên cao nhất",
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
                    PermissionStep("Vào「Cài đặt ứng dụng」", "Mở hệ thống [Cài đặt] ➔ [Cài đặt ứng dụng] ➔ [Quản lý cấp quyền] ➔ [Quản lý tự khởi động]."),
                    PermissionStep("Bật tự khởi động 「Thien Huyen」", "Trong danh sách ứng dụng tìm 【Thiên Huyền (TianXuan)】, chuyển công tắc tự khởi động sang 【Bật】."),
                    PermissionStep("Cho phép các ứng dụng đánh thức lẫn nhau", "Bấm vào chi tiết Thien Huyen, bật【Cho phép khởi chạy liên kết / Được ứng dụng khác đánh thức】(khuyến nghị cho HyperOS)."),
                )
                OemBrand.HUAWEI -> listOf(
                    PermissionStep("Mở「Quản lý khởi động ứng dụng」", "Vào 【Trình quản lý điện thoại】 ➔ 【Quản lý khởi động ứng dụng】."),
                    PermissionStep("Chuyển sang quản lý thủ công", "Tìm 【Thiên Huyền】, tắt công tắc 「Tự động quản lý」, trong cửa sổ bật lên mở toàn bộ ba mục: 【Cho phép tự khởi động】, 【Cho phép khởi động liên kết】 và 【Cho phép hoạt động nền】."),
                )
                OemBrand.VIVO -> listOf(
                    PermissionStep("Mở 「Quyền tự khởi động」", "Vào 【Cài đặt】 hệ thống ➔ 【Ứng dụng và quyền】 ➔ 【Quản lý quyền】 ➔ tab 【Quyền】 ➔ 【Tự khởi động】."),
                    PermissionStep("Bật tự khởi động Thiên Huyền", "Tìm 【Thien Huyen】 và bật cho phép tự khởi động."),
                    PermissionStep("Bật chạy nền tốn pin cao", "Trong [Cài đặt] ➔ [Pin] ➔ [Hao pin nền cao], tích chọn cho phép Thien Huyen tiếp tục chạy nền."),
                )
                OemBrand.OPPO -> listOf(
                    PermissionStep("Vào「Quản lý tự khởi động」", "Vào【Cài đặt】➔【Ứng dụng】➔【Quản lý tự khởi động】của hệ thống."),
                    PermissionStep("Cho phép tự khởi động và khởi động liên kết", "Tìm【Thien Huyen】, bật công tắc【Cho phép tự khởi động】và【Cho phép khởi chạy liên kết】."),
                )
                OemBrand.MEIZU -> listOf(
                    PermissionStep("Mở「Quản lý nền」", "Vào 【Trình quản lý điện thoại】 ➔ 【Quản lý quyền】 ➔ 【Quản lý nền】."),
                    PermissionStep("Giữ chạy nền", "Đặt 【Thiên Huyền】 thành 【Cho phép chạy nền】 hoặc 【Giữ đánh thức nền】."),
                )
                OemBrand.SAMSUNG -> listOf(
                    PermissionStep("Kiểm tra 「Tự động chạy ứng dụng」", "Vào 【Cài đặt】 ➔ 【Pin】 ➔ 【Giới hạn sử dụng nền】."),
                    PermissionStep("Chuyển ra khỏi danh sách ngủ", "Đảm bảo Thien Huyen không nằm trong 「Ứng dụng ngủ sâu」, và thêm vào danh sách 【Ứng dụng không bao giờ ngủ】."),
                )
                OemBrand.GENERIC -> listOf(
                    PermissionStep("Kiểm tra mục khởi động hệ thống", "Trong chi tiết ứng dụng của cài đặt hệ thống, kiểm tra xem có quyền 「Tự khởi động」hoặc 「Khởi động cùng máy」không và cho phép."),
                )
            }
            KeepaliveTopic.BATTERY_UNRESTRICTED -> when (brand) {
                OemBrand.XIAOMI -> listOf(
                    PermissionStep("Chiến lược tiết kiệm pin ở chi tiết ứng dụng", "Nhấn giữ icon Thien Huyen trên desktop ➔ 【Thông tin ứng dụng】 ➔ vuốt xuống tìm 【Chiến lược tiết kiệm pin】."),
                    PermissionStep("Đặt thành「Không giới hạn」", "Chọn 【Không giới hạn】 (không hạn chế hoạt động nền, có lợi nhất cho biên dịch PRoot và tư duy dài của Agent)."),
                )
                OemBrand.HUAWEI -> listOf(
                    PermissionStep("Bỏ qua tối ưu pin", "Vào 【Cài đặt】 ➔ 【Ứng dụng】 ➔ 【Quản lý quyền】 ➔ menu góc trên phải 【Quyền truy cập đặc biệt】 ➔ 【Tối ưu pin】."),
                    PermissionStep("Đặt thành 「Không cho phép tối ưu」", "Lọc xuống 「Tất cả ứng dụng」, tìm 【Thiên Huyền】, đặt thành 【Không cho phép】."),
                )
                OemBrand.VIVO -> listOf(
                    PermissionStep("Cho phép hao pin cao nền", "Vào 【Cài đặt】 ➔ 【Pin】 ➔ 【Quản lý hao pin nền】."),
                    PermissionStep("Bật chạy hao pin cao", "Tìm【Thien Huyen】, chọn【Cho phép chạy nền tốn pin cao】."),
                )
                OemBrand.OPPO -> listOf(
                    PermissionStep("Quản lý hao pin", "Nhấn giữ icon Thien Huyen ➔ 【Chi tiết ứng dụng】 ➔ 【Quản lý hao pin】."),
                    PermissionStep("Bật tất cả tùy chọn nền", "Tích chọn【Cho phép hành vi nền hoàn toàn】,【Cho phép đánh thức tiền cảnh】và tắt「Tối ưu tiết kiệm pin sâu」."),
                )
                OemBrand.SAMSUNG -> listOf(
                    PermissionStep("Chế độ dùng pin", "Vào【Cài đặt】➔【Ứng dụng】➔【Thien Huyen】➔【Pin】của hệ thống."),
                    PermissionStep("Đặt thành 「Không bị giới hạn」", "Chuyển từ 「Đã tối ưu」mặc định sang 【Không bị hạn chế】 (Unrestricted)."),
                )
                else -> listOf(
                    PermissionStep("Bỏ qua tối ưu pin", "Vào Thông tin ứng dụng ➔ 【Pin】 ➔ đặt thành 【Không giới hạn】 hoặc xin bỏ qua tối ưu pin hệ thống."),
                )
            }
            KeepaliveTopic.LOCK_RECENTS -> listOf(
                PermissionStep("Vào nền đa nhiệm", "Vuốt lên từ đáy màn hình và giữ (hoặc nhấp phím điều hướng đa nhiệm), vào xem thẻ đa nhiệm."),
                PermissionStep("Khóa thẻ Thiên Huyền", "Nhấn giữ thẻ nhiệm vụ Thien Huyen (hoặc kéo thẻ xuống), bấm biểu tượng khóa nhỏ 🔒 hiện ra để khóa, tránh bị hệ thống kill khi dọn nền một chạm."),
            )
            KeepaliveTopic.FLOATING_OVERLAY -> listOf(
                PermissionStep("Bật cửa sổ nổi / hiển thị trên ứng dụng khác", "Vào 【Thông tin ứng dụng】 ➔ 【Nâng cao】 ➔ 【Hiển thị trên các ứng dụng khác / Cửa sổ nổi】, chuyển sang 【Cho phép】."),
                PermissionStep("Quyền hiện cửa sổ nền trước", "Với hệ thống Xiaomi/vivo v.v., trong【Quản lý quyền】hãy bật quyền【Hiện cửa sổ nền trước】hoặc【Lối tắt màn hình chính】."),
            )
            KeepaliveTopic.NOTIFICATION -> listOf(
                PermissionStep("Cho phép thông báo và dịch vụ foreground thường trú", "Đảm bảo Thien Huyen có quyền thông báo, Thien Huyen sẽ khởi động dịch vụ thường trú tiền cảnh khi khởi động sandbox PRoot và vòng lặp Agent, bảo đảm độ ưu tiên tiến trình hệ thống."),
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
