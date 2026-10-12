package top.wkbin.tianxuan.core.common.astronomy

/**
 * 北斗七星 —— 天玄的七曜「门」。
 *
 * 《史记·天官书》："斗为帝车，运于中央，临制四乡。分阴阳，建四时，均五行，
 * 移节度，定诸纪，皆系于斗。"
 *
 * 七星对应应用的七条主干能力：这一层是**用户看得见的门**，
 * 门背后的实现构件由二十八宿承载（见 [Mansion]），两层不重复、不串位。
 *
 * 前四星（天枢→天权）为**魁**（斗勺），主承；后三星（玉衡→摇光）为**杓**（斗柄），主施。
 */
enum class BeiDou(
    /** 星名，如「天枢」。 */
    val starName: String,
    /** 拜耳编号与西名，如「α Dubhe」。 */
    val bayer: String,
    /** 道门别称（贪狼、巨门……破军）。 */
    val alias: String,
    /** 自斗口起算的序次，1..7。 */
    val order: Int,
    /** 所属部位：魁或杓。 */
    val part: DipperPart,
    /** 能力门名，如「智枢」「智坊」。 */
    val gateName: String,
    /** 门的英文标识，用于路由与日志 tag 的稳定 ID。 */
    val gateKey: String,
    /** 取义，一句话说明为何以此星配此门。 */
    val rationale: String,
) {
    /** 天枢：众机之枢，星纪由此推排。 */
    TIAN_SHU(
        starName = "Thiên Khu",
        bayer = "α Dubhe",
        alias = "Tham Lang",
        order = 1,
        part = DipperPart.KUI,
        gateName = "Tri Khu",
        gateKey = "agent",
        rationale = "Trung tâm của vạn cơ, lịch sao từ đây mà suy diễn",
    ),

    /** 天璇：攻玉成器，造作之所。 */
    TIAN_XUAN(
        starName = "Thiên Tuyền",
        bayer = "β Merak",
        alias = "Cự Môn",
        order = 2,
        part = DipperPart.KUI,
        gateName = "Trí Phường",
        gateKey = "workspace",
        rationale = "Mài ngọc thành khí, nơi chế tác",
    ),

    /** 天玑：直面天机，亲执枢要。 */
    TIAN_JI(
        starName = "Thiên Cơ",
        bayer = "γ Phecda",
        alias = "Lộc Tồn",
        order = 3,
        part = DipperPart.KUI,
        gateName = "Terminal",
        gateKey = "terminal",
        rationale = "Đối diện thiên cơ, tự nắm then chốt",
    ),

    /** 天权：权衡缓急，分派调度。 */
    TIAN_QUAN(
        starName = "Thiên Quyền",
        bayer = "δ Megrez",
        alias = "Văn Khúc",
        order = 4,
        part = DipperPart.KUI,
        gateName = "Workflow",
        gateKey = "workflow",
        rationale = "Cân nhắc gấp gáp, phân phái điều độ",
    ),

    /** 玉衡：北斗最亮之星，平准得失。 */
    YU_HENG(
        starName = "Ngọc Hành",
        bayer = "ε Alioth",
        alias = "Liêm Trinh",
        order = 5,
        part = DipperPart.BIAO,
        gateName = "Phiên bản",
        gateKey = "git",
        rationale = "Cân bằng được mất, cân đo chi li",
    ),

    /** 开阳：开牖见天，纳四境之光。 */
    KAI_YANG(
        starName = "Khai Dương",
        bayer = "ζ Mizar",
        alias = "Vũ Khúc",
        order = 6,
        part = DipperPart.BIAO,
        gateName = "Trình duyệt",
        gateKey = "browser",
        rationale = "Mở cửa sổ thấy trời, thu ánh sáng bốn phương",
    ),

    /** 摇光：摇动枢机，光被全域。 */
    YAO_GUANG(
        starName = "Dao Quang",
        bayer = "η Alkaid",
        alias = "Phá Quân",
        order = 7,
        part = DipperPart.BIAO,
        gateName = "Cài đặt",
        gateKey = "settings",
        rationale = "Lay động then chốt, ánh sáng phủ khắp toàn cõi",
    ),
    ;

    /** UI 主标题：星名，如「天枢」。 */
    val title: String get() = starName

    /** UI 副题：星名 · 门名，如「天枢 · 智枢」。 */
    val subtitle: String get() = "$starName · $gateName"

    companion object {
        /** 按序次取星（1..7），越界返回 null。 */
        fun ofOrder(order: Int): BeiDou? = entries.firstOrNull { it.order == order }

        /** 按门标识取星，用于路由反查。 */
        fun ofGateKey(gateKey: String): BeiDou? = entries.firstOrNull { it.gateKey == gateKey }

        /** 按星名取星，用于文案解析。 */
        fun ofStarName(starName: String): BeiDou? = entries.firstOrNull { it.starName == starName }
    }
}

/** 北斗的部位：魁为斗勺（前四星），杓为斗柄（后三星）。 */
enum class DipperPart(val label: String, val gloss: String) {
    /** 魁：斗勺四星，主承。 */
    KUI(label = "Khôi", gloss = "Đẩu Chước bốn sao, chủ thừa"),

    /** 杓：斗柄三星，主施。 */
    BIAO(label = "Tiêu", gloss = "Chuôi Đẩu ba sao, chủ thi hành"),
}
