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
            label = "Kiểm tra sức khỏe cầu host",
            category = "Chẩn đoán",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Dò trạng thái sức khỏe cầu nối tianxuan-host và ADB không dây",
        ),
        HostWorkflowActionDef(
            id = "device_status",
            label = "Snapshot trạng thái thiết bị",
            category = "Chẩn đoán",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Pin / Mạng / Activity tiền cảnh / Tóm tắt lưu trữ",
        ),
        HostWorkflowActionDef(
            id = "logcat",
            label = "Lấy Logcat",
            category = "Chẩn đoán",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Bắt nhật ký hệ thống gần đây (có thể lọc theo tag)",
            fields = listOf(
                HostWorkflowField("tag", "Tag (tùy chọn)", "Ví dụ TianXuan"),
                HostWorkflowField("tail_lines", "Số dòng", "Mặc định 200"),
            ),
        ),
        HostWorkflowActionDef(
            id = "install-apk",
            label = "Cài APK",
            category = "应用",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Cài APK qua cầu máy chủ (ADB không dây hoặc trình cài đặt hệ thống)",
            fields = listOf(
                HostWorkflowField("artifactFrom", "ID nút nguồn sản phẩm", "Để trống thì dùng biến APK_PATH"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_launch",
            label = "Mở ứng dụng",
            category = "应用",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Khởi chạy ứng dụng theo tên gói; khi không có lối vào màn hình chính thì lùi về monkey",
            fields = listOf(HostWorkflowField("package", "Tên gói", "Ví dụ com.android.settings", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_force_stop",
            label = "Buộc dừng ứng dụng",
            category = "应用",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "am force-stop tên gói chỉ định",
            fields = listOf(HostWorkflowField("package", "Tên gói", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_clear_data",
            label = "Xóa dữ liệu ứng dụng",
            category = "应用",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm clear (thao tác phá hoại, khuyến nghị kèm phê duyệt thủ công)",
            fields = listOf(HostWorkflowField("package", "Tên gói", required = true)),
        ),
        HostWorkflowActionDef(
            id = "app_freeze",
            label = "Đóng băng / Vô hiệu hóa ứng dụng",
            category = "应用",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm disable-user",
            fields = listOf(
                HostWorkflowField("package", "Tên gói", required = true),
                HostWorkflowField("user", "ID người dùng", "Mặc định 0"),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_unfreeze",
            label = "Rã đông / Bật ứng dụng",
            category = "应用",
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
            category = "应用",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "pm grant quyền chỉ định",
            fields = listOf(
                HostWorkflowField("package", "Tên gói", required = true),
                HostWorkflowField("permission", "Tên quyền", "Ví dụ android.permission.CAMERA", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "app_revoke_permission",
            label = "Thu hồi quyền runtime",
            category = "应用",
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
            category = "应用",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Thăm dò cho đến khi tên gói chỉ định vào foreground hoặc hết thời gian",
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
            description = "Gửi broadcast tường minh/ngầm định; broadcast được bảo vệ sẽ tự động dùng am broadcast đặc quyền",
            fields = listOf(
                HostWorkflowField("intentAction", "Action", "Ví dụ android.intent.action.BOOT_COMPLETED", required = true),
                HostWorkflowField("package", "Tên gói đích (tùy chọn)"),
                HostWorkflowField("component", "Thành phần (tùy chọn)", "pkg/.Receiver"),
                HostWorkflowField("extras", "Extras (key=value nhiều dòng)", "flag:bool=true\ncount:int=1", multiline = true),
                HostWorkflowField("preferShell", "Buộc shell đặc quyền", "true/false, mặc định tự động"),
            ),
        ),
        HostWorkflowActionDef(
            id = "start_activity",
            label = "Khởi động Activity",
            category = "Intent",
            privilege = HostWorkflowPrivilege.NONE,
            description = "am start / startActivity: thành phần, Action, Data URI, Extras",
            fields = listOf(
                HostWorkflowField("component", "Thành phần", "pkg/.Activity"),
                HostWorkflowField("intentAction", "Action", "Ví dụ android.intent.action.VIEW"),
                HostWorkflowField("dataUri", "Data URI", "https://… hoặc content://…"),
                HostWorkflowField("mimeType", "MIME (tùy chọn)"),
                HostWorkflowField("extras", "Extras (nhiều dòng)", multiline = true),
                HostWorkflowField("preferShell", "Buộc shell đặc quyền", "true/false"),
            ),
        ),
        HostWorkflowActionDef(
            id = "start_service",
            label = "Khởi động Service",
            category = "Intent",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "am startservice / start-foreground-service",
            fields = listOf(
                HostWorkflowField("component", "Thành phần", "pkg/.Service", required = true),
                HostWorkflowField("intentAction", "Action (tùy chọn)"),
                HostWorkflowField("extras", "Extras (nhiều dòng)", multiline = true),
                HostWorkflowField("foreground", "Dịch vụ tiền cảnh", "true/false, mặc định false"),
            ),
        ),
        HostWorkflowActionDef(
            id = "settings_get",
            label = "Đọc cài đặt hệ thống",
            category = "系统",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "settings get system/secure/global",
            fields = listOf(
                HostWorkflowField("namespace", "Không gian tên", "system / secure / global", required = true),
                HostWorkflowField("key", "Tên khóa", required = true),
                HostWorkflowField("outputVariable", "Ghi tên biến", "SETTINGS_VALUE mặc định"),
            ),
        ),
        HostWorkflowActionDef(
            id = "settings_put",
            label = "Ghi cài đặt hệ thống",
            category = "系统",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "settings put; namespace system ưu tiên ContentResolver",
            fields = listOf(
                HostWorkflowField("namespace", "Không gian tên", "system / secure / global", required = true),
                HostWorkflowField("key", "Tên khóa", required = true),
                HostWorkflowField("value", "值", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "airplane_mode",
            label = "Chế độ máy bay",
            category = "系统",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Bật/tắt chế độ máy bay và phát sóng thay đổi trạng thái",
            fields = listOf(HostWorkflowField("enabled", "Bật", "true / false", required = true)),
        ),
        HostWorkflowActionDef(
            id = "wifi_set",
            label = "Công tắc Wi‑Fi",
            category = "系统",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "cmd wifi set-wifi-enabled",
            fields = listOf(HostWorkflowField("enabled", "Bật", "true / false", required = true)),
        ),
        HostWorkflowActionDef(
            id = "volume_set",
            label = "Điều chỉnh âm lượng media",
            category = "系统",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Đặt âm lượng STREAM_MUSIC (0–15 thường gặp)",
            fields = listOf(HostWorkflowField("value", "Âm lượng", "Ví dụ 8", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_observe",
            label = "Nhận biết màn hình",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "uiautomator dump: ứng dụng tiền cảnh + tóm tắt cây control",
            fields = listOf(HostWorkflowField("onlyInteractive", "Chỉ node tương tác", "true/false, mặc định true")),
        ),
        HostWorkflowActionDef(
            id = "screen_click",
            label = "Nhấn tọa độ",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Hạ cấp: thao tác trợ năng → cmd input → bin input",
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_double_click",
            label = "Nhấp đúp tọa độ",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            fields = listOf(
                HostWorkflowField("x", "X", required = true),
                HostWorkflowField("y", "Y", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_long_press",
            label = "Nhấn giữ tọa độ",
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
                HostWorkflowField("x1", "Điểm bắt đầu X", required = true),
                HostWorkflowField("y1", "Y điểm bắt đầu", required = true),
                HostWorkflowField("x2", "Điểm cuối X", required = true),
                HostWorkflowField("y2", "Y điểm cuối", required = true),
                HostWorkflowField("durationMs", "Thời lượng ms", "Mặc định 300"),
            ),
        ),
        HostWorkflowActionDef(
            id = "screen_scroll",
            label = "Cuộn màn hình",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Cuộn theo hướng (up/down/left/right), ánh xạ nội bộ thành vuốt",
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
            description = "CJK đi qua clipboard+dán; tự động hạ cấp Ctrl+V / ASCII input text",
            fields = listOf(HostWorkflowField("text", "Văn bản", required = true)),
        ),
        HostWorkflowActionDef(
            id = "screen_key",
            label = "Gửi phím",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "back/home/recents ưu tiên thao tác toàn cục trợ năng, rồi hạ cấp keyevent",
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
            label = "Vòng lặp bay thử GUI của Agent",
            category = "GUI",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Vòng lặp cục bộ: cảm nhận màn hình → model xuất một bước JSON → nhấp/nhập. Không đi qua Lane Agent con hội thoại.",
            fields = listOf(
                HostWorkflowField("goal", "Mục tiêu", "Có thể để trống, mặc định đọc biến GUI_GOAL"),
                HostWorkflowField("package", "Tên gói mục tiêu", "Có thể để trống, mặc định đọc TARGET_PACKAGE"),
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
            description = "Kết quả đọc ghi vào biến CLIPBOARD",
        ),
        HostWorkflowActionDef(
            id = "notification",
            label = "Gửi thông báo",
            category = "Tương tác",
            privilege = HostWorkflowPrivilege.NONE,
            description = "Phát một thông báo workflow Thiên Huyền trong thanh thông báo",
            fields = listOf(
                HostWorkflowField("title", "Tiêu đề", required = true),
                HostWorkflowField("text", "Nội dung chính", required = true),
            ),
        ),
        HostWorkflowActionDef(
            id = "shell",
            label = "Shell đặc quyền",
            category = "Cao cấp",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Thực thi shell host bất kỳ dưới Shizuku/Root (rủi ro cao)",
            fields = listOf(HostWorkflowField("command", "Lệnh", "Ví dụ dumpsys window | head", required = true, multiline = true)),
        ),
        HostWorkflowActionDef(
            id = "cmd",
            label = "Gọi dịch vụ cmd",
            category = "Cao cấp",
            privilege = HostWorkflowPrivilege.PRIVILEGED,
            description = "Thực thi /system/bin/cmd <service> …",
            fields = listOf(
                HostWorkflowField("service", "Tên dịch vụ", "Ví dụ wifi / activity / package", required = true),
                HostWorkflowField("args", "Tham số", "set-wifi-enabled enabled", multiline = true),
            ),
        ),
    )

    fun find(id: String): HostWorkflowActionDef? = all.firstOrNull { it.id == id }

    fun grouped(): Map<String, List<HostWorkflowActionDef>> = all.groupBy { it.category }
}
