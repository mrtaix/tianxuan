package top.wkbin.tianxuan.core.common.astronomy

/**
 * 四象 —— 二十八宿所分的四个方位域。
 *
 * 《礼记·曲礼上》："Trước đi Chu Tước sau Huyền Vũ, trái Thanh Long phải Bạch Hổ."
 * 每一象领七宿，配一行、一时、一色，是天玄底层模块的分域依据。
 */
enum class Quadrant(
    /** 象名，如「东方青龙」。 */
    val label: String,
    /** 方位：东南西北。 */
    val direction: String,
    /** 五行所属。 */
    val element: String,
    /** 所主时令。 */
    val season: String,
    /** 所主之色。 */
    val color: String,
    /** 该象所对应的技术分层名。 */
    val layerLabel: String,
) {
    /** 东方青龙：属木，主春生发 —— 智能体引擎层。 */
    AZURE_DRAGON(
        label = "Thanh Long phương Đông",
        direction = "Đông",
        element = "Mộc",
        season = "Xuân",
        color = "Thanh",
        layerLabel = "Lớp engine Agent",
    ),

    /** 北方玄武：属水，主冬藏纳 —— 运行时底层。 */
    BLACK_TORTOISE(
        label = "Huyền Vũ phương Bắc",
        direction = "Bắc",
        element = "Thủy",
        season = "Đông",
        color = "Hắc",
        layerLabel = "Tầng nền của runtime",
    ),

    /** 西方白虎：属金，主秋收敛 —— 工具与集成层。 */
    WHITE_TIGER(
        label = "Bạch Hổ phương Tây",
        direction = "Tây",
        element = "Kim",
        season = "Thu",
        color = "Bạch",
        layerLabel = "Tầng công cụ và tích hợp",
    ),

    /** 南方朱雀：属火，主夏显扬 —— 表现与体验层。 */
    VERMILION_BIRD(
        label = "Nam Phương Chu Tước",
        direction = "Nam",
        element = "Hỏa",
        season = "Hạ",
        color = "Xích",
        layerLabel = "Tầng biểu hiện và trải nghiệm",
    ),
    ;

    /** 该象领七宿。 */
    val mansions: List<Mansion> get() = Mansion.entries.filter { it.quadrant == this }

    companion object {
        /** 按方位字取象，用于配置解析与日志反查。 */
        fun ofDirection(direction: String): Quadrant? =
            entries.firstOrNull { it.direction == direction }
    }
}
