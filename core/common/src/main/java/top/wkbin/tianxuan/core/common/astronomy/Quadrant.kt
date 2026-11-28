package top.wkbin.tianxuan.core.common.astronomy

/**
 * Tứ tượng — bốn miền phương vị của nhị thập bát tú.
 *
 * Theo Lễ ký · Khúc lễ thượng: trước là Chu Điểu, sau là Huyền Vũ,
 * trái là Thanh Long, phải là Bạch Hổ. Mỗi tượng quản bảy tú, ứng với
 * một hành, một mùa và một màu, làm cơ sở phân tầng mô-đun của CTHarness.
 */
enum class Quadrant(
    /** Tên tượng, ví dụ Thanh Long phương Đông. */
    val label: String,
    /** Phương vị: Đông, Nam, Tây, Bắc. */
    val direction: String,
    /** Hành tương ứng trong ngũ hành. */
    val element: String,
    /** Mùa tương ứng. */
    val season: String,
    /** Màu tương ứng. */
    val color: String,
    /** Tên tầng kỹ thuật tương ứng. */
    val layerLabel: String,
) {
    /** Thanh Long phương Đông: hành Mộc, mùa Xuân sinh trưởng — tầng động cơ agent. */
    AZURE_DRAGON(
        label = "Thanh Long phương Đông",
        direction = "Đông",
        element = "Mộc",
        season = "Xuân",
        color = "Thanh",
        layerLabel = "Tầng động cơ agent",
    ),

    /** Huyền Vũ phương Bắc: hành Thủy, mùa Đông tích trữ — tầng nền thời gian chạy. */
    BLACK_TORTOISE(
        label = "Huyền Vũ phương Bắc",
        direction = "Bắc",
        element = "Thủy",
        season = "Đông",
        color = "Hắc",
        layerLabel = "Tầng nền thời gian chạy",
    ),

    /** Bạch Hổ phương Tây: hành Kim, mùa Thu thu liễm — tầng công cụ và tích hợp. */
    WHITE_TIGER(
        label = "Bạch Hổ phương Tây",
        direction = "Tây",
        element = "Kim",
        season = "Thu",
        color = "Bạch",
        layerLabel = "Tầng công cụ và tích hợp",
    ),

    /** Chu Tước phương Nam: hành Hỏa, mùa Hạ hiển dương — tầng hiển thị và trải nghiệm. */
    VERMILION_BIRD(
        label = "Chu Tước phương Nam",
        direction = "Nam",
        element = "Hỏa",
        season = "Hạ",
        color = "Xích",
        layerLabel = "Tầng hiển thị và trải nghiệm",
    ),
    ;

    /** Bảy tú thuộc tượng này. */
    val mansions: List<Mansion> get() = Mansion.entries.filter { it.quadrant == this }

    companion object {
        /** Tra tượng theo phương vị để phân tích cấu hình và đối chiếu nhật ký. */
        fun ofDirection(direction: String): Quadrant? =
            entries.firstOrNull { it.direction == direction }
    }
}
