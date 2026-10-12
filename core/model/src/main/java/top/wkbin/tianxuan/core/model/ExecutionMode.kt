package top.wkbin.tianxuan.core.model

/**
 * 天玄支持的系统运行与特权模式。
 */
enum class ExecutionMode(
    val id: String,
    val shortLabel: String,
    val title: String,
    val summary: String,
    val requiredPrivilege: String,
    val capabilities: List<String>,
) {
    PROOT(
        id = "PROOT",
        shortLabel = "PRoot",
        title = "Sandbox user-space PRoot (mặc định)",
        summary = "Không cần bất kỳ quyền đặc biệt nào, dùng ngay. Mô phỏng môi trường user-space Linux qua PRoot.",
        requiredPrivilege = "Không có (quyền ứng dụng thông thường)",
        capabilities = listOf(
            "Chạy 100% miễn Root bản Debian / Ubuntu đầy đủ",
            "Hỗ trợ hệ sinh thái quản lý gói APT / APK v.v.",
            "Hỗ trợ terminal tương tác PTY và chuỗi công cụ Agent",
        ),
    ),
    SHIZUKU(
        id = "SHIZUKU",
        shortLabel = "Shizuku",
        title = "Chế độ nâng quyền Shizuku / ADB",
        summary = "Thông qua dịch vụ Shizuku để lấy quyền ADB, gỡ bỏ giới hạn hệ thống Android và bật tự động hoá toàn máy.",
        requiredPrivilege = "Ủy quyền dịch vụ Shizuku (UID 2000 shell)",
        capabilities = listOf(
            "Gỡ giới hạn 32 tiến trình ma của Android 12+ (Phantom Process Killer)",
            "Miễn trừ whitelist giữ sống nền sâu",
            "Điều khiển tự động hóa host Agent (input / chụp màn hình / logcat)",
            "Vượt qua giới hạn truy cập tệp /sdcard/Android/data",
        ),
    ),
    ROOT(
        id = "ROOT",
        shortLabel = "Root",
        title = "Chế độ hiệu năng native Root",
        summary = "Yêu cầu quyền SU, giải phóng 100% sức mạnh phần cứng, loại bỏ triệt để tổn hao chặn lời gọi hệ thống của PRoot.",
        requiredPrivilege = "Quyền Root (Magisk / KernelSU / APatch / UID 0)",
        capabilities = listOf(
            "Hiệu năng Linux gốc 100% (không có tổn hao chặn syscall)",
            "Ngữ nghĩa ext4 native (giải quyết triệt để vấn đề hard link perl/dpkg)",
            "Hỗ trợ chạy container và image Docker / Podman",
            "Truy cập trực tiếp nút GPU/NPU (/dev/kgsl-3d0 v.v.) tăng tốc phần cứng",
            "Hỗ trợ lắng nghe cổng đặc quyền 80/443 và iptables",
        ),
    );

    companion object {
        /** 兼容历史持久化值：已下线的 ADB 等未知 id 会安全回落到 PROOT。 */
        fun fromId(id: String): ExecutionMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PROOT
    }
}
