package top.wkbin.tianxuan.core.model.workflow

/** Privilege floor required by a host workflow action. */
enum class HostWorkflowPrivilege {
    /** App Context / HostBridge only — works without Shizuku/Root. */
    NONE,
    /** Needs Shizuku or Root effective mode. */
    PRIVILEGED,
}

data class HostWorkflowField(
    val key: String,
    val label: String,
    val hint: String = "",
    val required: Boolean = false,
    val multiline: Boolean = false,
)

data class HostWorkflowActionDef(
    val id: String,
    val label: String,
    val category: String,
    val privilege: HostWorkflowPrivilege,
    val description: String = "",
    val fields: List<HostWorkflowField> = emptyList(),
)

/**
 * Catalog of first-class host actions exposed by workflow [HOST_ACTION] nodes.
 * Keep IDs stable — they are persisted in node config.
 */
object HostWorkflowActions {
    val all: List<HostWorkflowActionDef> = listOf(
        HostWorkflowActionDef(
            id = "status",
            label = "Trạng thái quyền",
            category = "Chẩn đoán",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Báo cáo chế độ PRoot / Shizuku / Root đang có hiệu lực và trạng thái cấp quyền",
        ),
        HostWorkflowActionDef(
            id = "health",
            label = "Kiểm tra hoạt động cầu nối máy chủ",
            category = "Chẩn đoán",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Dò trạng thái hoạt động của cầu nối tianxuan-host và ADB không dây",
        ),
        HostWorkflowActionDef(
            id = "device_status",
            label = "Ảnh chụp trạng thái thiết bị",
            category = "Chẩn đoán",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Tóm tắt pin / mạng / Activity foreground / lưu trữ",
        ),
        HostWorkflowActionDef(
            id = "logcat",
            label = "Thu thập Logcat",
            category = "Chẩn đoán",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Thu thập nhật ký hệ thống gần đây (có thể lọc theo tag)",
            fields = listOf(
                HostWorkflowField("tag", "Tag (tuỳ chọn)", "ví dụ TianXuan"),
                HostWorkflowField("tail_lines", "Số dòng", "Mặc định 200"),
            ),
        ),
        HostWorkflowActionDef(
            id = "install-apk",
            label = "Cài đặt APK",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Cài đặt APK qua cầu nối máy chủ (ADB không dây hoặc trình cài đặt hệ thống)",
            fields = listOf(
                HostWorkflowField("artifactFrom", "ID nút nguồn tạo ra sản phẩm", "Để trống thì dùng biến APK_PATH"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_launch",
            label = "Mở ứng dụng",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Khởi chạy ứng dụng theo tên gói; khi không có mục trên màn hình chính thì lùi về monkey",
            fields = listOf(HostWorkflowField("package", "Tên gói", "ví dụ com.android.settings", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_force_stop",
            label = "Buộc dừng ứng dụng",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "am force-stop tên gói chỉ định",
            fields = listOf(HostWorkflowField("package", "Tên gói", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_clear_data",
            label = "Xoá dữ liệu ứng dụng",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm clear (thao tác phá huỷ, nên kèm phê duyệt thủ công)",
            fields = listOf(HostWorkflowField("package", "Tên gói", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_freeze",
            label = "Đóng băng / vô hiệu hoá ứng dụng",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm disable-user",
            fields = listOf(
                HostWorkflowField("package", "Tên gói", required = true),
                HostWorkflowField("user", "ID người dùng", "Mặc định 0"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_unfreeze",
            label = "Bỏ đóng băng / kích hoạt ứng dụng",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm enable",
            fields = listOf(
                HostWorkflowField("package", "Tên gói", required = true),
                HostWorkflowField("user", "ID người dùng", "Mặc định 0"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_grant_permission",
            label = "Cấp quyền runtime",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm grant quyền chỉ định",
            fields = listOf(
                HostWorkflowField("package", "Tên gói", required = true),
                HostWorkflowField("permission", "Tên quyền", "ví dụ android.permission.CAMERA", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_revoke_permission",
            label = "Thu hồi quyền runtime",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm revoke quyền chỉ định",
            fields = listOf(
                HostWorkflowField("package", "Tên gói", required = true),
                HostWorkflowField("permission", "Tên quyền", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "wait_foreground",
            label = "Chờ ứng dụng foreground",
            category = "Ứng dụng",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Liên tục kiểm tra cho đến khi tên gói chỉ định vào foreground hoặc hết thời gian",
            fields = listOf(
                HostWorkflowField("package", "Tên gói", required = true),
                HostWorkflowField("timeoutSeconds", "Số giây timeout", "Mặc định 15"),
            ),
        ),
        HostWorkflowActionDef(
            id = "send_broadcast",
            label = "Gửi broadcast",
            category = "Intent",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Gửi broadcast tường minh/không tường minh; broadcast được bảo vệ sẽ tự động đi qua am broadcast đặc quyền",
            fields = listOf(
                HostWorkflowField("intentAction", "Action", "ví dụ android.intent.action.BOOT_COMPLETED", required = true),
                HostWorkflowField("package", "Tên gói đích (tuỳ chọn)"),
                HostWorkflowField("component", "Thành phần (tuỳ chọn)", "pkg/.Receiver"),
                HostWorkflowField("extras", "Extras (nhiều dòng key=value)", "flag:bool=true\ncount:int=1", multiline = true),
                HostWorkflowField("preferShell", "Buộc dùng shell đặc quyền", "true/false, mặc định tự động"),
            ),
        ),
        HostWorkflowActionDef(
            id = "start_activity",
            label = "Khởi chạy Activity",
            category = "Intent",
            privilege = HostWorkflowPrivilege.NONE,
            description = "am start / startActivity: thành phần, Action, Data URI, Extras",
            fields = listOf(
                HostWorkflowField("component", "Thành phần", "pkg/.Activity"),
                HostWorkflowField("intentAction", "Action", "ví dụ android.intent.action.VIEW"),
                HostWorkflowField("dataUri", "Data URI", "https://… hoặc content://…"),
                HostWorkflowField("mimeType", "MIME (tuỳ chọn)"),
                HostWorkflowField("extras", "Extras (nhiều dòng)", multiline = true),
                HostWorkflowField("preferShell", "Buộc dùng shell đặc quyền", "true/false"),
            ),
        ),
        HostWorkflowActionDef(
            id = "start_service",
            label = "Khởi chạy Service",
            category = "Intent",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "am startservice / start-foreground-service",
            fields = listOf(
                HostWorkflowField("component", "Thành phần", "pkg/.Service", required = true),
                HostWorkflowField("intentAction", "Action (tuỳ chọn)"),
                HostWorkflowField("extras", "Extras (nhiều dòng)", multiline = true),
                HostWorkflowField("foreground", "Dịch vụ foreground", "true/false, mặc định false"),
            ),
        ),
        HostWorkflowActionDef(
            id = "settings_get",
            label = "Đọc cài đặt hệ thống",
            category = "Hệ thống",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "settings get system/secure/global",
            fields = listOf(
                HostWorkflowField("namespace", "Namespace", "system / secure / global", required = true),
                HostWorkflowField("key", "Tên khoá", required = true),
                HostWorkflowField("outputVariable", "Tên biến ghi vào", "Mặc định SETTINGS_VALUE"),
            ),
        ),
        HostWorkflowActionDef(
            id = "settings_put",
            label = "Ghi cài đặt hệ thống",
            category = "Hệ thống",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "settings put; namespace system ưu tiên ContentResolver",
            fields = listOf(
                HostWorkflowField("namespace", "Namespace", "system / secure / global", required = true),
                HostWorkflowField("key", "Tên khoá", required = true),
                HostWorkflowField("value", "Giá trị", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "airplane_mode",
            label = "Chế độ máy bay",
            category = "Hệ thống",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Bật/tắt chế độ máy bay và phát broadcast thay đổi trạng thái",
            fields = listOf(HostWorkflowField("enabled", "Bật", "true / false", required = true)),
        ),
        HostWorkflowActionDef(
            id = "wifi_set",
            label = "Công tắc Wi‑Fi",
            category = "Hệ thống",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "cmd wifi set-wifi-enabled",
            fields = listOf(HostWorkflowField("enabled", "Bật", "true / false", required = true)),
        ),
        HostWorkflowActionDef(
            id = "volume_set",
            label = "Điều chỉnh âm lượng media",
            category = "Hệ thống",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Đặt âm lượng STREAM_MUSIC (thường 0–15)",
            fields = listOf(HostWorkflowField("value", "Âm lượng", "ví dụ 8", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_observe",
            label = "Cảm nhận màn hình",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "uiautomator dump: ứng dụng foreground + tóm tắt cây thành phần",
            fields = listOf(HostWorkflowField("onlyInteractive", "Chỉ nút tương tác", "true/false, mặc định true")),
        ),
        HostWorkflowActionDef(
            id = "screen_click",
            label = "Chạm theo toạ độ",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Hạ cấp: cử chỉ trợ năng → cmd input → bin input",
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_double_click",
            label = "Nhấn đúp theo toạ độ",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_long_press",
            label = "Nhấn giữ theo toạ độ",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
                HostWorkflowField("durationMs", "Thời lượng ms", "Mặc định 800"),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_swipe",
            label = "Vuốt màn hình",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(
                HostWorkflowField("x1", "Điểm đầu X", required = true),
                HostWorkflowField("y1", "Điểm đầu Y", required = true),
                HostWorkflowField("x2", "Điểm cuối X", required = true),
                HostWorkflowField("y2", "Điểm cuối Y", required = true),
                HostWorkflowField("durationMs", "Thời lượng ms", "Mặc định 300"),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_scroll",
            label = "Cuộn màn hình",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Cuộn theo hướng (up/down/left/right), bên trong ánh xạ thành thao tác vuốt",
            fields = listOf(
                HostWorkflowField("direction", "Hướng", "up / down / left / right", required = true),
                HostWorkflowField("distanceRatio", "Biên độ", "0.15–0.8, mặc định 0.45"),
                HostWorkflowField("durationMs", "Thời lượng ms", "Mặc định 350"),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_input_text",
            label = "Dán/nhập văn bản",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "CJK đi qua clipboard + dán; tự động hạ cấp Ctrl+V / ASCII input text",
            fields = listOf(HostWorkflowField("text", "Văn bản", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_key",
            label = "Gửi phím",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "back/home/recents ưu tiên hành động toàn cục trợ năng, rồi hạ cấp keyevent",
            fields = listOf(HostWorkflowField("key", "Phím", "back/home/recents/enter/delete/paste/power", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_capture",
            label = "Chụp màn hình",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(HostWorkflowField("path", "Đường dẫn lưu", "/sdcard/Download/tianxuan-screen.png", required = true)),
        ),
        HostWorkflowActionDef(
            id = "gui_pilot",
            label = "Vòng lặp chạy thử GUI của agent",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Vòng lặp cục bộ: cảm nhận màn hình → model xuất một bước JSON → chạm/nhập. Không đi qua Lane agent con của chat.",
            fields = listOf(
                HostWorkflowField("goal", "Mục tiêu", "Có thể trống, mặc định đọc biến GUI_GOAL"),
                HostWorkflowField("package", "Tên gói đích", "Có thể trống, mặc định đọc TARGET_PACKAGE"),
                HostWorkflowField("maxSteps", "Số bước tối đa", "Mặc định 18"),
            ),
        ),
        HostWorkflowActionDef(
            id = "toast",
            label = "Hiện Toast",
            category = "Tương tác",
            privilege = HostWorkflowPrivilege.NONE,
            fields = listOf(HostWorkflowField("text", "Nội dung", required = true)),
        ),
        HostWorkflowActionDef(
            id = "vibrate",
            label = "Rung",
            category = "Tương tác",
            privilege = HostWorkflowPrivilege.NONE,
            fields = listOf(HostWorkflowField("durationMs", "Thời lượng ms", "Mặc định 200")),
        ),
        HostWorkflowActionDef(
            id = "clipboard_set",
            label = "Ghi vào clipboard",
            category = "Tương tác",
            privilege = HostWorkflowPrivilege.NONE,
            fields = listOf(HostWorkflowField("text", "Văn bản", required = true)),
        ),
        HostWorkflowActionDef(
            id = "clipboard_get",
            label = "Đọc clipboard",
            category = "Tương tác",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Kết quả đọc được ghi vào biến CLIPBOARD",
        ),
        HostWorkflowActionDef(
            id = "notification",
            label = "Gửi thông báo",
            category = "Tương tác",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Đăng một thông báo workflow TianXuan lên thanh thông báo",
            fields = listOf(
                HostWorkflowField("title", "Tiêu đề", required = true),
                HostWorkflowField("text", "Nội dung chính", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "shell",
            label = "Shell đặc quyền",
            category = "Nâng cao",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Thực thi shell máy chủ tuỳ ý dưới Shizuku/Root (rủi ro cao)",
            fields = listOf(HostWorkflowField("command", "Lệnh", "ví dụ dumpsys window | head", required = true, multiline = true)),
        ),
        HostWorkflowActionDef(
            id = "cmd",
            label = "Gọi dịch vụ cmd",
            category = "Nâng cao",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Thực thi /system/bin/cmd <service> …",
            fields = listOf(
                HostWorkflowField("service", "Tên dịch vụ", "ví dụ wifi / activity / package", required = true),
                HostWorkflowField("args", "Tham số", "set-wifi-enabled enabled", multiline = true),
            ),
        ),
    )

    fun find(id: String): HostWorkflowActionDef? = all.firstOrNull { it.id == id }

    fun grouped(): Map<String, List<HostWorkflowActionDef>> = all.groupBy { it.category }
}
