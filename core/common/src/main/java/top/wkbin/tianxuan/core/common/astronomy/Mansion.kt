package top.wkbin.tianxuan.core.common.astronomy

/**
 * 二十八宿 —— 天玄的宿位「器」。
 *
 * 《尚书考灵曜》："Nhị thập bát tú, khí Thiên Nguyên, tinh túy của vạn vật."
 * 宿（xiù）即「舍」，是日月五星运行途中的馆舍，古人用它们作天球的坐标刻度。
 *
 * 距度依《淮南子·天文训》「星分度」：东方 75¼（含箕宿所带四分一）、
 * 北方 98、西方 80、南方 112，四象合计 365¼ 度，合周天（见 [Mansion.degree]）。
 *
 * 与北斗七星的关系：北斗是**门**（能力入口），二十八宿是门后的**器**（实现构件）。
 * 每宿绑定一个构件与一项可观测指标，日志与诊断面板（星图）按宿分区。
 */
enum class Mansion(
    /** 宿名，如「角」。 */
    val starName: String,
    /** 所属四象。 */
    val quadrant: Quadrant,
    /** 全二十八宿中的序次，1..28。 */
    val order: Int,
    /** 七曜配属。 */
    val luminary: Luminary,
    /** 所配之禽，如「蛟」。 */
    val beast: String,
    /** 距度（《淮南子·天文训》），箕宿为 11.25。 */
    val degree: Float,
    /** 所绑定的实现构件名。 */
    val component: String,
    /** 该宿承载的可观测指标名。 */
    val metric: String,
    /** 取义：为何以此宿配此构件。 */
    val gloss: String,
) {
    // ── 东方青龙七宿（七十五度又四分一，含箕宿所带）：智能体引擎层 ──────────

    /** 角木蛟：龙角，二十八宿之首，古称「天门」。 */
    JIAO(
        starName = "Giác", quadrant = Quadrant.AZURE_DRAGON, order = 1,
        luminary = Luminary.JUPITER, beast = "Giao", degree = 12f,
        component = "HarnessLoop", metric = "Số lượt Agent",
        gloss = "Tinh kỷ chi nguyên, chúng tú do thử khởi toán",
    ),

    /** 亢金龙：龙颈，承上启下之要害。 */
    KANG(
        starName = "Cang", quadrant = Quadrant.AZURE_DRAGON, order = 2,
        luminary = Luminary.VENUS, beast = "Long", degree = 9f,
        component = "ProviderClient", metric = "Số yêu cầu giao thức",
        gloss = "Cổ họng thôn thổ, nối trên tiếp dưới",
    ),

    /** 氐土貉：龙胸、天根，《史记·律书》"Đê giả, nói vạn vật đều đến vậy"。 */
    DI(
        starName = "Đê", quadrant = Quadrant.AZURE_DRAGON, order = 3,
        luminary = Luminary.SATURN, beast = "Hạc", degree = 15f,
        component = "AgentContext", metric = "Số token ngữ cảnh",
        gloss = "Vạn vật đều đến, ấy là gốc rễ",
    ),

    /** 房日兔：龙腹，古称「天驷」，主消化万物。 */
    FANG(
        starName = "Phòng", quadrant = Quadrant.AZURE_DRAGON, order = 4,
        luminary = Luminary.SUN, beast = "Thố", degree = 5f,
        component = "ToolExecutor", metric = "Số lần gọi công cụ",
        gloss = "Thiên tứ phụ tải, tiêu hóa thi hành",
    ),

    /** 心月狐：龙心，明察之主。 */
    XIN(
        starName = "Tâm", quadrant = Quadrant.AZURE_DRAGON, order = 5,
        luminary = Luminary.MOON, beast = "Hồ", degree = 5f,
        component = "ApprovalPolicyEngine", metric = "Số quyết định phê duyệt",
        gloss = "Lòng phán quyết sáng suốt, xét rồi mới cho",
    ),

    /** 尾火虎：龙尾，扫荡四方。 */
    WEI(
        starName = "Vĩ", quadrant = Quadrant.AZURE_DRAGON, order = 6,
        luminary = Luminary.MARS, beast = "Hổ", degree = 18f,
        component = "SubagentOrchestrator", metric = "Số Agent con chạy đồng thời",
        gloss = "Đuôi quét tám cõi, hợp lực chung sức",
    ),

    /** 箕水豹：簸箕，风伯，扬清激浊。箕宿带四分一，以合周天 365¼ 度。 */
    JI(
        starName = "Cơ", quadrant = Quadrant.AZURE_DRAGON, order = 7,
        luminary = Luminary.MERCURY, beast = "Báo", degree = 11.25f,
        component = "CompactionManager", metric = "Số lần nén",
        gloss = "Bá dương sàng thải, lưu tinh khứ nhũng",
    ),

    // ── 北方玄武七宿（九十八度）：运行时底层 ──────────────────────────────

    /** 斗木獬：南斗，与北斗共掌生死，又称「天庙」。 */
    DOU(
        starName = "Đẩu", quadrant = Quadrant.BLACK_TORTOISE, order = 8,
        luminary = Luminary.JUPITER, beast = "Giải", degree = 26f,
        component = "ProcessRegistry", metric = "Số tiến trình",
        gloss = "Nắm giữ sinh tử, đo lường vạn mệnh",
    ),

    /** 牛金牛：牵牛，负载引重。 */
    NIU(
        starName = "Ngưu", quadrant = Quadrant.BLACK_TORTOISE, order = 9,
        luminary = Luminary.VENUS, beast = "Ngưu", degree = 8f,
        component = "PRoot", metric = "Thời gian khởi động sandbox",
        gloss = "Gánh nặng đi xa, chở núi sông",
    ),

    /** 女土蝠：婺女，主织纫布帛。 */
    NV(
        starName = "Nữ", quadrant = Quadrant.BLACK_TORTOISE, order = 10,
        luminary = Luminary.SATURN, beast = "Phúc", degree = 12f,
        component = "WorkspaceFileService", metric = "Số thao tác tệp",
        gloss = "Dệt dọc ngang, thành chương thành bức",
    ),

    /** 虚日鼠：虚宿主冬，《史记·天官书》"Hư, việc khóc than"，主死丧、庙堂，虚而能受。 */
    XU(
        starName = "Hư", quadrant = Quadrant.BLACK_TORTOISE, order = 11,
        luminary = Luminary.SUN, beast = "Thử", degree = 10f,
        component = "StorageManager", metric = "Lượng lưu trữ thu hồi",
        gloss = "Hư mà dung chứa, dọn sạch dư thừa",
    ),

    /** 危月燕：高危，屋脊之上，主不安与戒惧。 */
    WEI_NORTH(
        starName = "Nguy", quadrant = Quadrant.BLACK_TORTOISE, order = 12,
        luminary = Luminary.MOON, beast = "Yên", degree = 17f,
        component = "PrivilegeManager", metric = "Số lần gọi đặc quyền",
        gloss = "Ở cao gặp nguy, cẩn trọng gìn giữ",
    ),

    /** 室火猪：营室、玄宫，主营造屋室以御严冬。 */
    SHI(
        starName = "Thất", quadrant = Quadrant.BLACK_TORTOISE, order = 13,
        luminary = Luminary.MARS, beast = "Trư", degree = 16f,
        component = "RootFS", metric = "Thời gian lắp ráp container",
        gloss = "Dinh thất xây nền, để chống ngoại xâm",
    ),

    /** 壁水貐：东壁，《晋书·天文志》"Bài viết chính, mật phủ sách vở thiên hạ"。 */
    BI(
        starName = "Bích", quadrant = Quadrant.BLACK_TORTOISE, order = 14,
        luminary = Luminary.MERCURY, beast = "Dữu", degree = 9f,
        component = "CheckpointStore", metric = "Số điểm kiểm tra",
        gloss = "Cất giấu sách vở trong tường, có thể lấy có thể trả",
    ),

    // ── 西方白虎七宿（八十度）：工具与集成层 ──────────────────────────────

    /** 奎木狼：天之武库（《史记》正义"Khuê, phủ khố của trời"），主兵禁。 */
    KUI(
        starName = "Khuê", quadrant = Quadrant.WHITE_TIGER, order = 15,
        luminary = Luminary.JUPITER, beast = "Lang", degree = 16f,
        component = "ToolRegistry", metric = "Số công cụ đã đăng ký",
        gloss = "Vũ khố tàng binh, xuất nạp hữu cấm",
    ),

    /** 娄金狗：主聚众、牧养、苑囿。 */
    LOU(
        starName = "Lâu", quadrant = Quadrant.WHITE_TIGER, order = 16,
        luminary = Luminary.VENUS, beast = "Cẩu", degree = 12f,
        component = "McpManager", metric = "Số kết nối MCP",
        gloss = "Tụ chúng chăn nuôi, thống ngự ngoại bang",
    ),

    /** 胃土雉：仓廪，主受纳储藏。 */
    WEI_WEST(
        starName = "Vị", quadrant = Quadrant.WHITE_TIGER, order = 17,
        luminary = Luminary.SATURN, beast = "Trĩ", degree = 14f,
        component = "InstallTransactionManager", metric = "Số giao dịch cài đặt",
        gloss = "Kho lẫm thu nhận, ra vào có độ",
    ),

    /** 昴日鸡：昴星团（髦头），主明察四方 —— 开阳门后之器。 */
    MAO(
        starName = "Mão", quadrant = Quadrant.WHITE_TIGER, order = 18,
        luminary = Luminary.SUN, beast = "Kê", degree = 11f,
        component = "WebViewTabPool", metric = "Số lần tải trang",
        gloss = "Soi sáng bốn cõi, ai nấy an vị",
    ),

    /** 毕月乌：毕网，雨师，主网罗捕获。 */
    BI_WEST(
        starName = "Tất", quadrant = Quadrant.WHITE_TIGER, order = 19,
        luminary = Luminary.MOON, beast = "Ô", degree = 16f,
        component = "CdpFetchInterceptor", metric = "Số lần chặn mạng",
        gloss = "Giăng lưới bắt gió, không gì lọt thoát",
    ),

    /** 觜火猴：虎首虎口，三星紧密，主细微机巧。 */
    ZI(
        starName = "Chủy", quadrant = Quadrant.WHITE_TIGER, order = 20,
        luminary = Luminary.MARS, beast = "Hầu", degree = 2f,
        component = "GenericRecipeInstaller", metric = "Số lần thực thi script",
        gloss = "Tinh xảo biến hóa, thấy nhỏ biết lớn",
    ),

    /** 参水猿：白虎之身，主杀伐、权衡、边事。 */
    SHEN(
        starName = "Sâm", quadrant = Quadrant.WHITE_TIGER, order = 21,
        luminary = Luminary.MERCURY, beast = "Viên", degree = 9f,
        component = "SandboxBoundary", metric = "Số lần chặn vượt quyền",
        gloss = "Trấn giữ ranh giới, cân nhắc được không",
    ),

    // ── 南方朱雀七宿（一百一十二度）：表现与体验层 ────────────────────────

    /** 井木犴：朱雀之首，东井主水衡、泉源。 */
    JING(
        starName = "Tỉnh", quadrant = Quadrant.VERMILION_BIRD, order = 22,
        luminary = Luminary.JUPITER, beast = "Hãn", degree = 33f,
        component = "Theme", metric = "Số lần đổi chủ đề",
        gloss = "Nguồn suối chảy ra, thấm nhuần toàn thể",
    ),

    /** 鬼金羊：朱雀之目（舆鬼），主积聚。 */
    GUI(
        starName = "Quỷ", quadrant = Quadrant.VERMILION_BIRD, order = 23,
        luminary = Luminary.VENUS, beast = "Dương", degree = 4f,
        component = "RuntimeComponents", metric = "Tỷ lệ tái sử dụng thành phần",
        gloss = "Thiết bị tích tụ dùng, để dự phòng bách công",
    ),

    /** 柳土獐：朱雀之口，主吐露。 */
    LIU(
        starName = "Liễu", quadrant = Quadrant.VERMILION_BIRD, order = 24,
        luminary = Luminary.SATURN, beast = "Chương", degree = 15f,
        component = "MarkdownText", metric = "Thời gian render một khung",
        gloss = "Xuất khẩu thành chương, thổ lộ văn từ",
    ),

    /** 星日马：朱雀之颈（七星），主引领方向。 */
    XING(
        starName = "Tinh", quadrant = Quadrant.VERMILION_BIRD, order = 25,
        luminary = Luminary.SUN, beast = "Mã", degree = 7f,
        component = "Navigation3", metric = "Số lần nhảy chuyển route",
        gloss = "Dẫn dắt phương hướng, chỉ lối nguồn cơn",
    ),

    /** 张月鹿：朱雀之嗉，主天厨、陈列宴飨 —— 摇光门后之器。 */
    ZHANG(
        starName = "Trương", quadrant = Quadrant.VERMILION_BIRD, order = 26,
        luminary = Luminary.MOON, beast = "Lộc", degree = 18f,
        component = "SettingsStore", metric = "Số ưu tiên đọc ghi",
        gloss = "Trần liệt bách vị, các thủ sở nhu",
    ),

    /** 翼火蛇：朱雀之翼，二十二星，主铺张舒展。 */
    YI(
        starName = "Dực", quadrant = Quadrant.VERMILION_BIRD, order = 27,
        luminary = Luminary.MARS, beast = "Xà", degree = 18f,
        component = "WorkflowEditor", metric = "Số nút canvas",
        gloss = "Giương cánh trải bày, vẽ họa toàn cục",
    ),

    /** 轸水蚓：朱雀之尾，又名天车，主车驾承载 —— 天玑门后之器。 */
    ZHEN(
        starName = "Chẩn", quadrant = Quadrant.VERMILION_BIRD, order = 28,
        luminary = Luminary.MERCURY, beast = "Dẫn", degree = 17f,
        component = "PtySession", metric = "Số phiên còn sống",
        gloss = "Chở xe mà đi, thông đạt vô ngại",
    ),
    ;

    /** 「七曜·禽」全称，如「角木蛟」。 */
    val fullName: String get() = "$starName${luminary.label}$beast"

    /** 「宿名（构件）」的指代式，用于帮助文案，如「奎宿（工具注册表）」。 */
    val reference: String get() = "Sao ${starName} ($component)"

    companion object {
        /** 按序次取宿（1..28），越界返回 null。 */
        fun ofOrder(order: Int): Mansion? = entries.firstOrNull { it.order == order }

        /** 按宿名取宿，用于提示词与配置解析。 */
        fun ofName(name: String): Mansion? = entries.firstOrNull { it.starName == name }

        /** 按构件名取宿，用于日志 tag 反查。 */
        fun ofComponent(component: String): Mansion? =
            entries.firstOrNull { it.component.equals(component, ignoreCase = true) }

        /** 周天距度总和：365¼ 度。 */
        val TOTAL_DEGREES: Float get() = entries.sumOf { it.degree.toDouble() }.toFloat()
    }
}

/**
 * 七曜 —— 二十八宿的七曜配属，木金土日月火水循环往复。
 *
 * 四象各七宿，每象之内七曜次序一致，故只需记一象便知其余。
 */
enum class Luminary(
    /** 曜名，如「木」。 */
    val label: String,
    /** 对应天体名。 */
    val body: String,
) {
    /** 岁星。 */
    JUPITER(label = "Mộc", body = "Tuế Tinh"),

    /** 太白。 */
    VENUS(label = "Kim", body = "Thái Bạch"),

    /** 镇星（填星）。 */
    SATURN(label = "Thổ", body = "Trấn Tinh"),

    /** 日。 */
    SUN(label = "Nhật", body = "Mặt Trời"),

    /** 月。 */
    MOON(label = "Nguyệt", body = "Thái Âm"),

    /** 荧惑。 */
    MARS(label = "Hỏa", body = "Huỳnh Hoặc"),

    /** 辰星。 */
    MERCURY(label = "Thủy", body = "Thần Tinh"),
}
