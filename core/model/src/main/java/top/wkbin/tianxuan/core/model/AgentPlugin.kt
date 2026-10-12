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
            description = "Thăm dò theo thời gian thực hệ tệp ảo PRoot Linux, trạng thái tiến trình và bộ nhớ, tự động hỗ trợ sinh gợi ý chẩn đoán khi bất thường",
            version = "1.0.0",
            author = "TianXuan System",
            permissions = listOf("Thực thi dò trạng thái hệ thống", "Thu thập chỉ số chẩn đoán PRoot"),
            iconName = "Terminal",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "destructive_cmd_interceptor",
            name = "Bộ chặn rà soát lệnh nguy hiểm cao",
            description = "Thực hiện kiểm tra an toàn trước và sửa tham số cho các lệnh phá hoại như rm -rf /, mkfs, dd, tránh làm hỏng nhầm sandbox hoặc vùng làm việc",
            version = "1.1.0",
            author = "Security Lab",
            permissions = listOf("Kiểm tra an toàn lệnh", "Chặn thao tác rủi ro cao"),
            iconName = "Alert",
            isEnabled = true,
            isBuiltin = true,
        ),
        AgentPlugin(
            id = "web_search_augment",
            name = "Tăng cường truy xuất Web và chỉ mục tài liệu",
            description = "Bổ sung cho Agent chỉ mục kho tri thức lập trình viên bên ngoài, tài liệu gói phần mềm Debian và kho giải pháp lỗi thường gặp",
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
            description = "Tìm kiếm web đa engine không cần API Key và trích xuất nội dung trang (dịch vụ MCP không phụ thuộc tích hợp sẵn trong Thien Huyen): hỗ trợ tìm kiếm trực tiếp đa engine như Baidu, Bing, DuckDuckGo và trích xuất làm sạch nội dung, phản hồi mili giây, không cần Node.js/npx, dùng ngay.",
            version = "1.0.0",
            author = "TianXuan System",
            permissions = listOf(
                "Lắng nghe mạng loopback cục bộ (127.0.0.1)",
                "Cài đặt ứng dụng từ nguồn không xác định (REQUEST_INSTALL_PACKAGES)",
                "Ghép nối và kết nối gỡ lỗi không dây (Android 11+)",
            ),
            iconName = "Cable",
            isEnabled = true,
            isBuiltin = true,
        ),
    )
}
