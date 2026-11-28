package top.wkbin.tianxuan.core.common.astronomy

/**
 * Tam viên — cấu trúc ba tầng của kho bộ nhớ CTHarness.
 *
 * Tử Vi viên, Thái Vi viên và Thiên Thị viên cùng nhị thập bát tú tạo thành
 * hệ thống tam viên nhị thập bát tú. Tử Vi là nơi ở của Thiên đế,
 * Thái Vi lo việc triều chính, Thiên Thị là chợ trên trời.
 *
 * Ba tầng giúp mô hình xác định nơi đọc và ghi, không lẫn công việc ngắn hạn
 * vào thông tin định danh hay để hội thoại cuốn trôi sở thích lâu dài.
 */
enum class Enclosure(
    /** Tên viên, ví dụ Tử Vi viên. */
    val label: String,
    /** Ý nghĩa cổ điển. */
    val gloss: String,
    /** Tên tầng bộ nhớ. */
    val memoryLayer: String,
    /** Chính sách vòng đời của tầng. */
    val retention: String,
    /** Chính sách ghi: nội dung thuộc tầng này. */
    val writePolicy: String,
    /** Chính sách đọc: thời điểm truy xuất tầng này. */
    val readPolicy: String,
) {
    /** Tử Vi viên: nơi ở của Thiên đế, trung tâm các sao — bộ nhớ cốt lõi. */
    ZI_WEI(
        label = "Tử Vi viên",
        gloss = "Nơi ở của Thiên đế, trung tâm được các sao chầu quanh",
        memoryLayer = "Bộ nhớ cốt lõi",
        retention = "Lưu giữ vĩnh viễn, không nén, không chuyển ra ngoài",
        writePolicy = "Thiết lập danh tính, sở thích lâu dài, hồ sơ người dùng, quy tắc bất biến",
        readPolicy = "Đọc mỗi lượt, luôn có trong prompt hệ thống",
    ),

    /** Thái Vi viên: triều chính, chủ thi hành — bộ nhớ làm việc. */
    TAI_WEI(
        label = "Thái Vi viên",
        gloss = "Nơi xử lý triều chính, chủ thi hành",
        memoryLayer = "Bộ nhớ làm việc",
        retention = "Có thể lưu trữ khi hết phiên; quá hạn chuyển sang Tử Vi viên hoặc Thiên Thị viên",
        writePolicy = "Phiên hiện tại, kế hoạch thực thi, điểm kiểm tra nhiệm vụ, sản phẩm trung gian",
        readPolicy = "Đọc khi cần trong lúc làm việc, lưu trữ khi chuyển phiên",
    ),

    /** Thiên Thị viên: chợ trên trời, chủ giao thương — bộ nhớ truy xuất. */
    TIAN_SHI(
        label = "Thiên Thị viên",
        gloss = "Chợ trên trời, chủ giao thương và lưu thông",
        memoryLayer = "Bộ nhớ truy xuất",
        retention = "Nạp và gỡ khi cần; chỉ giữ nội dung khớp truy vấn",
        writePolicy = "Tri thức bên ngoài, lịch sử lưu trữ, ngữ liệu có thể lưu bên ngoài",
        readPolicy = "Đưa vào sau khi truy xuất ngữ nghĩa, gỡ sau khi dùng",
    ),
    ;

    /** Chuỗi hiển thị tên viên · tầng bộ nhớ trên UI. */
    val display: String get() = "$label · $memoryLayer"

    companion object {
        /** Tra viên theo tên tầng bộ nhớ, dùng khi phân tích cấu hình và prompt. */
        fun ofMemoryLayer(memoryLayer: String): Enclosure? =
            entries.firstOrNull { it.memoryLayer == memoryLayer }
    }
}
