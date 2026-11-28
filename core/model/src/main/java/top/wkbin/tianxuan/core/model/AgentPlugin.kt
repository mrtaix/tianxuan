package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentPlugin(
    val id: String,
    val name: String,
    val description: String,
    val version: String = "1.0.0",
    val author: String = "TianXuan Core Team",
    val permissions: List<String> = emptyList(),
    val iconName: String = "Package",
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true,
)

object BuiltinPlugins {
    val presets: List<AgentPlugin> = listOf(
        AgentPlugin(
            id = "proot_health_probe",
            name = "Đầu dò chẩn đoán sandbox PRoot",
            description = "Dò theo thời gian thực hệ thống tệp ảo, tiến trình và bộ nhớ của Linux PRoot, tự động hỗ trợ sinh gợi ý chẩn đoán khi có bất thường",
            version = "1.0.0",
            author = "TianXuan System",
            permissions = listOf("Thực thi đầu dò trạng thái hệ thống", "Thu thập chỉ số chẩn đoán PRoot"),
            iconName = "Terminal",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "destructive_cmd_interceptor",
            name = "Bộ chặn rà soát lệnh nguy hiểm cao",
            description = "Kiểm tra an toàn trước và chỉnh tham số với các lệnh phá huỷ như rm -rf /, mkfs, dd, tránh làm hỏng sandbox hoặc workspace",
            version = "1.1.0",
            author = "Security Lab",
            permissions = listOf("Rà soát an toàn lệnh", "Chặn thao tác nguy hiểm cao"),
            iconName = "Alert",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "web_search_augment",
            name = "Tăng cường tra cứu Web và chỉ mục tài liệu",
            description = "Bổ sung cho Agent chỉ mục kho tri thức nhà phát triển bên ngoài, tài liệu gói Debian và thư viện cách xử lý lỗi thường gặp",
            version = "0.9.0",
            author = "Community",
            permissions = listOf("Yêu cầu mạng", "Chỉ mục kho tri thức"),
            iconName = "Globe",
            isEnabled = false,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "host_bridge",
            name = "Cầu nối máy chủ · ADB tích hợp",
            description = "Trong sandbox truy cập năng lực máy chủ qua 127.0.0.1:7980: cài APK im lặng, thực thi Shell đặc quyền. Ưu tiên dùng ADB gỡ lỗi không dây tích hợp (không cần Root và Shizuku bên ngoài), cũng có thể lùi về Shizuku/Root.",
            version = "1.0.0",
            author = "TianXuan System",
            permissions = listOf(
                "Lắng nghe mạng loopback cục bộ (127.0.0.1)",
                "Cài ứng dụng từ nguồn không xác định (REQUEST_INSTALL_PACKAGES)",
                "Ghép đôi và kết nối gỡ lỗi không dây (Android 11+)",
            ),
            iconName = "Cable",
            isEnabled = true,
            isBuiltin = true,
        ),
    )
}
