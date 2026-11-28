package top.wkbin.tianxuan.core.common.astronomy

/**
 * Bắc Đẩu thất tinh — bảy cửa chức năng của CTHarness.
 *
 * Theo Sử ký · Thiên quan thư, Đẩu là xe của đế vương, vận hành ở trung tâm,
 * phân âm dương, lập bốn mùa, điều hòa ngũ hành và định các chu kỳ.
 *
 * Bảy sao ứng với bảy năng lực chính mà người dùng nhìn thấy. Các thành phần
 * triển khai phía sau thuộc nhị thập bát tú (xem [Mansion]); hai tầng không trùng vai trò.
 * Bốn sao đầu (Thiên Khu → Thiên Quyền) là Khôi, phần gáo, chủ tiếp nhận;
 * ba sao sau (Ngọc Hành → Dao Quang) là Tiêu, phần cán, chủ thi hành.
 */
enum class BeiDou(
    /** Tên sao, ví dụ Thiên Khu. */
    val starName: String,
    /** Ký hiệu Bayer và tên phương Tây, ví dụ α Dubhe. */
    val bayer: String,
    /** Tên gọi trong Đạo giáo, từ Tham Lang đến Phá Quân. */
    val alias: String,
    /** Thứ tự tính từ miệng gáo, 1..7. */
    val order: Int,
    /** Bộ phận: Khôi hoặc Tiêu. */
    val part: DipperPart,
    /** Tên cửa chức năng, ví dụ Trí Khu hoặc Trí Phường. */
    val gateName: String,
    /** ID tiếng Anh ổn định dùng cho điều hướng và log tag. */
    val gateKey: String,
    /** Ý nghĩa liên hệ giữa sao và cửa chức năng. */
    val rationale: String,
) {
    /** Thiên Khu: đầu mối điều phối mọi hoạt động. */
    TIAN_SHU(
        starName = "Thiên Khu",
        bayer = "α Dubhe",
        alias = "Tham Lang",
        order = 1,
        part = DipperPart.KUI,
        gateName = "Trí Khu",
        gateKey = "agent",
        rationale = "Đầu mối của mọi hoạt động, nơi suy tính vận hành của các sao",
    ),

    /** Thiên Tuyền: nơi chế tác, mài ngọc thành khí. */
    TIAN_XUAN(
        starName = "Thiên Tuyền",
        bayer = "β Merak",
        alias = "Cự Môn",
        order = 2,
        part = DipperPart.KUI,
        gateName = "Trí Phường",
        gateKey = "workspace",
        rationale = "Mài ngọc thành khí, nơi kiến tạo và chế tác",
    ),

    /** Thiên Cơ: trực tiếp nắm giữ cơ chế vận hành. */
    TIAN_JI(
        starName = "Thiên Cơ",
        bayer = "γ Phecda",
        alias = "Lộc Tồn",
        order = 3,
        part = DipperPart.KUI,
        gateName = "Terminal",
        gateKey = "terminal",
        rationale = "Đối diện thiên cơ, trực tiếp nắm giữ đầu mối",
    ),

    /** Thiên Quyền: cân nhắc ưu tiên và phân công điều phối. */
    TIAN_QUAN(
        starName = "Thiên Quyền",
        bayer = "δ Megrez",
        alias = "Văn Khúc",
        order = 4,
        part = DipperPart.KUI,
        gateName = "Luồng công việc",
        gateKey = "workflow",
        rationale = "Cân nhắc việc gấp việc hoãn, phân công và điều phối",
    ),

    /** Ngọc Hành: sao sáng nhất Bắc Đẩu, cân bằng được mất. */
    YU_HENG(
        starName = "Ngọc Hành",
        bayer = "ε Alioth",
        alias = "Liêm Trinh",
        order = 5,
        part = DipperPart.BIAO,
        gateName = "Phiên bản",
        gateKey = "git",
        rationale = "Cân bằng được mất, đo lường chính xác từng phần",
    ),

    /** Khai Dương: mở cửa đón ánh sáng bốn phương. */
    KAI_YANG(
        starName = "Khai Dương",
        bayer = "ζ Mizar",
        alias = "Vũ Khúc",
        order = 6,
        part = DipperPart.BIAO,
        gateName = "Trình duyệt",
        gateKey = "browser",
        rationale = "Mở cửa thấy trời, đón ánh sáng từ bốn phương",
    ),

    /** Dao Quang: vận chuyển đầu mối, soi sáng toàn cục. */
    YAO_GUANG(
        starName = "Dao Quang",
        bayer = "η Alkaid",
        alias = "Phá Quân",
        order = 7,
        part = DipperPart.BIAO,
        gateName = "Cài đặt",
        gateKey = "settings",
        rationale = "Vận chuyển đầu mối, ánh sáng phủ khắp toàn cục",
    ),
    ;

    /** Tiêu đề chính trên UI: tên sao. */
    val title: String get() = starName

    /** Tiêu đề phụ trên UI: tên sao · tên cửa chức năng. */
    val subtitle: String get() = "$starName · $gateName"

    companion object {
        /** Tra sao theo thứ tự 1..7; ngoài phạm vi trả về null. */
        fun ofOrder(order: Int): BeiDou? = entries.firstOrNull { it.order == order }

        /** Tra sao theo ID cửa chức năng để đối chiếu điều hướng. */
        fun ofGateKey(gateKey: String): BeiDou? = entries.firstOrNull { it.gateKey == gateKey }

        /** Tra sao theo tên hiển thị để phân tích nội dung. */
        fun ofStarName(starName: String): BeiDou? = entries.firstOrNull { it.starName == starName }
    }
}

/** Các bộ phận của Bắc Đẩu: Khôi là bốn sao phần gáo, Tiêu là ba sao phần cán. */
enum class DipperPart(val label: String, val gloss: String) {
    /** Khôi: bốn sao phần gáo, chủ tiếp nhận. */
    KUI(label = "Khôi", gloss = "Bốn sao phần gáo, chủ tiếp nhận"),

    /** Tiêu: ba sao phần cán, chủ thi hành. */
    BIAO(label = "Tiêu", gloss = "Ba sao phần cán, chủ thi hành"),
}
