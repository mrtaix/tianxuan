package top.wkbin.tianxuan.core.model

/**
 * Các chế độ chạy hệ thống và đặc quyền mà TianXuan hỗ trợ.
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
        title = "Sandbox chế độ người dùng PRoot (mặc định)",
        summary = "Không cần quyền đặc biệt nào, dùng được ngay. Mô phỏng môi trường người dùng Linux qua PRoot.",
        requiredPrivilege = "Không (quyền ứng dụng thông thường)",
        capabilities = listOf(
            "Chạy Debian / Ubuntu đầy đủ mà không cần Root",
            "Hỗ trợ hệ sinh thái quản lý gói như APT / APK",
            "Hỗ trợ terminal tương tác PTY và toolchain của Agent",
        ),
    ),
    SHIZUKU(
        id = "SHIZUKU",
        shortLabel = "Shizuku",
        title = "Chế độ nâng quyền Shizuku / ADB",
        summary = "Nhận quyền ADB qua dịch vụ Shizuku, gỡ bỏ giới hạn của hệ thống Android và bật tự động hoá toàn máy.",
        requiredPrivilege = "Cấp quyền dịch vụ Shizuku (UID 2000 shell)",
        capabilities = listOf(
            "Gỡ bỏ giới hạn 32 tiến trình ma trên Android 12+ (Phantom Process Killer)",
            "Miễn trừ whitelist giữ sống nền chuyên sâu",
            "Điều khiển tự động hoá máy chủ cho Agent (input / chụp màn hình / logcat)",
            "Vượt qua giới hạn truy cập tệp /sdcard/Android/data",
        ),
    ),
    ROOT(
        id = "ROOT",
        shortLabel = "Root",
        title = "Chế độ hiệu năng native Root",
        summary = "Yêu cầu quyền SU, giải phóng 100% sức mạnh phần cứng, loại bỏ hoàn toàn tổn thất do PRoot chặn syscall.",
        requiredPrivilege = "Quyền Root (Magisk / KernelSU / APatch / UID 0)",
        capabilities = listOf(
            "Hiệu năng Linux native 100% (không tốn chi phí chặn syscall)",
            "Ngữ nghĩa ext4 native (giải quyết triệt để vấn đề hard link của perl/dpkg)",
            "Hỗ trợ chạy container và image Docker / Podman",
            "Truy cập trực tiếp node GPU/NPU (/dev/kgsl-3d0 v.v.) để tăng tốc phần cứng",
            "Hỗ trợ lắng nghe cổng đặc quyền 80/443 và iptables",
        ),
    );

    companion object {
        /** Tương thích giá trị lưu trữ lịch sử: các id không xác định như ADB đã ngừng hỗ trợ sẽ an toàn lùi về PROOT. */
        fun fromId(id: String): ExecutionMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PROOT
    }
}
