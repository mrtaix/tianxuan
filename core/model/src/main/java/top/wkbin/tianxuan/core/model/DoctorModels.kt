package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

enum class DoctorStatus {
    HEALTHY,
    WARNING,
    ERROR,
    /**
     * Không dò được sandbox (timeout/lỗi/sandbox đang bận).
     * Không có kết quả dò ≠ lỗi cấu hình: hiển thị thẻ xám "Tạm thời chưa xác nhận được", không tính vào mục cần sửa;
     * tránh báo nhầm timeout do sandbox bận thành WARNING/ERROR.
     */
    UNKNOWN,
    CHECKING,
}

enum class DoctorCategory(val displayName: String) {
    SANDBOX("Sandbox và bộ nhớ"),
    NETWORK_SSL("Mạng và bảo mật"),
    PACKAGE_MANAGER("Quản lý gói và nguồn"),
    DEV_RUNTIMES("Môi trường phát triển cốt lõi"),
}

@Serializable
data class DoctorItem(
    val id: String,
    val category: DoctorCategory,
    val title: String,
    val status: DoctorStatus,
    val summary: String,
    val detail: String? = null,
    val fixable: Boolean = true,
)

@Serializable
data class DoctorReport(
    val items: List<DoctorItem> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val overallStatus: DoctorStatus = DoctorStatus.HEALTHY,
    val healthyCount: Int = 0,
    val warningCount: Int = 0,
    val errorCount: Int = 0,
    /** Số mục không dò được (sandbox bận); không tính vào kết luận needsFix. */
    val unknownCount: Int = 0,
) {
    val isAllHealthy: Boolean get() = overallStatus == DoctorStatus.HEALTHY && items.isNotEmpty()
    val needsFix: Boolean get() = warningCount > 0 || errorCount > 0
}

@Serializable
data class RepairProgress(
    val stepTitle: String,
    val stepIndex: Int,
    val totalSteps: Int = 5,
    val progress: Float = 0.0f,
    val logs: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null,
)
