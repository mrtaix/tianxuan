package top.wkbin.tianxuan.ui.settings.search

import androidx.compose.ui.graphics.Color
import top.wkbin.tianxuan.ui.components.RuntimeIconName

/** Các nhóm cài đặt và chức năng toàn cục của CTHarness. */
enum class SettingsSearchCategory(val label: String, val themeColor: Color) {
    AGENT("Agent và mô hình AI", Color(0xFF6366F1)),
    LINUX_ENV("Môi trường Linux và bộ nhớ", Color(0xFF10B981)),
    APPEARANCE("Giao diện, cỡ chữ và terminal", Color(0xFF8B5CF6)),
    SYSTEM_DEV("Chạy nền và chẩn đoán nhà phát triển", Color(0xFFF59E0B)),
    WORKSHOP("Workspace và bản dựng ngoại tuyến", Color(0xFFEC4899)),
    CORE_FEATURE("Truy cập nhanh CTHarness", Color(0xFF3B82F6)),
    ABOUT("Giới thiệu và cộng đồng chính thức", Color(0xFF06B6D4)),
}

/** Đích điều hướng của kết quả tìm kiếm. */
enum class SettingsSearchTarget {
    // Agent
    MODEL_PROFILES,
    MODEL_EDITOR_NEW,
    LOCAL_LLM,
    QUICK_PHRASES,
    STATS,
    TOOL_CENTER,
    CC_SWITCH,
    AGENT_EXECUTION,
    AGENT_SUBAGENTS,
    AGENT_SKILLS,
    MCP_SETTINGS,

    // Môi trường Linux và bộ nhớ
    DISTRO_MANAGEMENT,
    STORAGE_USAGE,
    STORAGE_MOUNTS,
    ENV_VARS,
    SSH_SETTINGS,
    FTP_SETTINGS,
    WEB_CHAT,
    APP_MANAGEMENT,
    PRIVILEGE_MODE,

    // Giao diện và terminal
    APPEARANCE_SETTINGS,
    THEME_MODE,
    DYNAMIC_COLOR,
    LIQUID_GLASS,
    FONT_SCALE,
    TERMINAL_SETTINGS,
    LANGUAGE_SETTINGS,

    // Chạy nền và nhà phát triển
    BATTERY_OPTIMIZATION,
    PHANTOM_PROCESS,
    DEVELOPER_OPTIONS,
    ADB_LOGCAT,
    CUSTOM_ITERATION,
    PERMISSION_GUIDE,

    // Workspace và môi trường dựng ứng dụng
    WORKSHOP_SETTINGS,
    WORKSHOP_ENVIRONMENT,
    WORKSHOP_SIGNING,
    WORKFLOWS,

    // Truy cập nhanh chức năng chính
    NAV_HOME,
    NAV_AGENT_CHAT,
    NAV_WORKSPACE,
    NAV_TERMINAL,
    NAV_BROWSER,

    // Giới thiệu và cộng đồng
    ABOUT_COMMUNITY,
    ABOUT_UPDATE,
    ABOUT_SPONSOR,
}

/** Một mục chức năng trong chỉ mục tìm kiếm toàn cục. */
data class SettingsSearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: SettingsSearchCategory,
    val target: SettingsSearchTarget,
    val icon: RuntimeIconName,
    val keywords: List<String> = emptyList(),
    val badge: String? = null,
    val breadcrumb: String? = null,
)
