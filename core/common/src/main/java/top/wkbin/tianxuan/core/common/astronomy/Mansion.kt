package top.wkbin.tianxuan.core.common.astronomy

/**
 * Nhị thập bát tú — các thành phần triển khai của CTHarness.
 *
 * Theo Thượng thư khảo linh diệu, hai mươi tám tú là nguyên khí của trời,
 * tinh hoa của vạn vật. Tú nghĩa là nơi trú của Mặt Trời, Mặt Trăng và ngũ tinh,
 * được người xưa dùng làm mốc tọa độ trên thiên cầu.
 *
 * Cự độ theo Hoài Nam Tử · Thiên văn huấn: phương Đông 75¼ (gồm ¼ của tú Cơ),
 * phương Bắc 98, phương Tây 80, phương Nam 112, tổng cộng 365¼ độ (xem [Mansion.degree]).
 *
 * Bắc Đẩu là cửa vào năng lực; nhị thập bát tú là các công cụ phía sau cửa.
 * Mỗi tú gắn với một thành phần và một chỉ số quan sát được;
 * nhật ký và bảng chẩn đoán tinh đồ được phân chia theo tú.
 */
enum class Mansion(
    /** Tên tú, ví dụ Giác. */
    val starName: String,
    /** Tứ tượng tương ứng. */
    val quadrant: Quadrant,
    /** Thứ tự trong hai mươi tám tú, 1..28. */
    val order: Int,
    /** Thất diệu tương ứng. */
    val luminary: Luminary,
    /** Linh thú tương ứng, ví dụ Giao. */
    val beast: String,
    /** Cự độ theo Hoài Nam Tử · Thiên văn huấn; tú Cơ là 11.25. */
    val degree: Float,
    /** Tên kỹ thuật của thành phần triển khai. */
    val component: String,
    /** Tên chỉ số quan sát được của tú. */
    val metric: String,
    /** Ý nghĩa liên hệ giữa tú và thành phần. */
    val gloss: String,
) {
    // Bảy tú Thanh Long phương Đông (75¼ độ, gồm phần lẻ của tú Cơ): tầng động cơ agent.

    /** Giác Mộc Giao: sừng rồng, đứng đầu nhị thập bát tú, xưa gọi Thiên Môn. */
    JIAO(
        starName = "Giác", quadrant = Quadrant.AZURE_DRAGON, order = 1,
        luminary = Luminary.JUPITER, beast = "Giao", degree = 12f,
        component = "HarnessLoop", metric = "Số lượt agent",
        gloss = "Khởi điểm của tinh kỷ, nơi bắt đầu tính các tú",
    ),

    /** Cang Kim Long: cổ rồng, nối phần trên với phần dưới. */
    KANG(
        starName = "Cang", quadrant = Quadrant.AZURE_DRAGON, order = 2,
        luminary = Luminary.VENUS, beast = "Long", degree = 9f,
        component = "ProviderClient", metric = "Số yêu cầu giao thức",
        gloss = "Cổ họng điều tiết lưu lượng, nối liền trên dưới",
    ),

    /** Đê Thổ Hạc: ngực rồng, gốc trời; nơi vạn vật quy tụ theo Sử ký · Luật thư. */
    DI(
        starName = "Đê", quadrant = Quadrant.AZURE_DRAGON, order = 3,
        luminary = Luminary.SATURN, beast = "Hạc", degree = 15f,
        component = "AgentContext", metric = "Số token ngữ cảnh",
        gloss = "Nơi vạn vật quy tụ, tạo thành nền móng",
    ),

    /** Phòng Nhật Thố: bụng rồng, xưa gọi Thiên Tứ, chủ tiêu hóa vạn vật. */
    FANG(
        starName = "Phòng", quadrant = Quadrant.AZURE_DRAGON, order = 4,
        luminary = Luminary.SUN, beast = "Thố", degree = 5f,
        component = "ToolExecutor", metric = "Số lần gọi công cụ",
        gloss = "Thiên Tứ mang tải, tiếp nhận và thực thi",
    ),

    /** Tâm Nguyệt Hồ: tim rồng, chủ xét đoán sáng suốt. */
    XIN(
        starName = "Tâm", quadrant = Quadrant.AZURE_DRAGON, order = 5,
        luminary = Luminary.MOON, beast = "Hồ", degree = 5f,
        component = "ApprovalPolicyEngine", metric = "Số quyết định phê duyệt",
        gloss = "Trái tim minh xét, kiểm tra rồi mới cho phép",
    ),

    /** Vĩ Hỏa Hổ: đuôi rồng, quét sạch bốn phương. */
    WEI(
        starName = "Vĩ", quadrant = Quadrant.AZURE_DRAGON, order = 6,
        luminary = Luminary.MARS, beast = "Hổ", degree = 18f,
        component = "SubagentOrchestrator", metric = "Số agent con chạy đồng thời",
        gloss = "Đuôi quét tám phương, hợp lực cùng hành động",
    ),

    /** Cơ Thủy Báo: cái nia, thần gió, gạn đục khơi trong; mang thêm ¼ độ để đủ chu thiên. */
    JI(
        starName = "Cơ", quadrant = Quadrant.AZURE_DRAGON, order = 7,
        luminary = Luminary.MERCURY, beast = "Báo", degree = 11.25f,
        component = "CompactionManager", metric = "Số lần nén",
        gloss = "Sàng lọc tinh hoa, loại bỏ phần dư thừa",
    ),

    // Bảy tú Huyền Vũ phương Bắc (98 độ): tầng nền thời gian chạy.

    /** Đẩu Mộc Giải: Nam Đẩu, cùng Bắc Đẩu quản sinh tử, còn gọi Thiên Miếu. */
    DOU(
        starName = "Đẩu", quadrant = Quadrant.BLACK_TORTOISE, order = 8,
        luminary = Luminary.JUPITER, beast = "Giải", degree = 26f,
        component = "ProcessRegistry", metric = "Số tiến trình",
        gloss = "Quản sinh tử, đo lường vòng đời của muôn vật",
    ),

    /** Ngưu Kim Ngưu: Khiên Ngưu, chủ mang vác và kéo vật nặng. */
    NIU(
        starName = "Ngưu", quadrant = Quadrant.BLACK_TORTOISE, order = 9,
        luminary = Luminary.VENUS, beast = "Ngưu", degree = 8f,
        component = "PRoot", metric = "Thời gian khởi động sandbox",
        gloss = "Mang nặng đi xa, gánh cả núi sông",
    ),

    /** Nữ Thổ Bức: Vụ Nữ, chủ dệt may vải lụa. */
    NV(
        starName = "Nữ", quadrant = Quadrant.BLACK_TORTOISE, order = 10,
        luminary = Luminary.SATURN, beast = "Bức", degree = 12f,
        component = "WorkspaceFileService", metric = "Số thao tác tệp",
        gloss = "Dệt ngang đan dọc, kết thành tấm vải có trật tự",
    ),

    /** Hư Nhật Thử: chủ mùa đông, tang lễ và miếu đường; trống để có thể tiếp nhận. */
    XU(
        starName = "Hư", quadrant = Quadrant.BLACK_TORTOISE, order = 11,
        luminary = Luminary.SUN, beast = "Thử", degree = 10f,
        component = "StorageManager", metric = "Dung lượng lưu trữ thu hồi",
        gloss = "Dọn phần tích tụ dư thừa, tạo chỗ để tiếp nhận",
    ),

    /** Nguy Nguyệt Yến: ở cao trên nóc nhà, chủ bất an và thận trọng. */
    WEI_NORTH(
        starName = "Nguy", quadrant = Quadrant.BLACK_TORTOISE, order = 12,
        luminary = Luminary.MOON, beast = "Yến", degree = 17f,
        component = "PrivilegeManager", metric = "Số lần gọi đặc quyền",
        gloss = "Ở cao biết nguy, thận trọng giữ gìn",
    ),

    /** Thất Hỏa Trư: Doanh Thất, Huyền Cung, chủ dựng nhà chống giá rét. */
    SHI(
        starName = "Thất", quadrant = Quadrant.BLACK_TORTOISE, order = 13,
        luminary = Luminary.MARS, beast = "Trư", degree = 16f,
        component = "RootFS", metric = "Thời gian thiết lập container",
        gloss = "Dựng nhà xây móng, ngăn xâm nhập từ bên ngoài",
    ),

    /** Bích Thủy Du: Đông Bích, kho sách bí mật của thiên hạ theo Tấn thư · Thiên văn chí. */
    BI(
        starName = "Bích", quadrant = Quadrant.BLACK_TORTOISE, order = 14,
        luminary = Luminary.MERCURY, beast = "Du", degree = 9f,
        component = "CheckpointStore", metric = "Số điểm kiểm tra",
        gloss = "Cất sách trong vách, có thể phục hồi và trở lại",
    ),

    // Bảy tú Bạch Hổ phương Tây (80 độ): tầng công cụ và tích hợp.

    /** Khuê Mộc Lang: kho vũ khí trên trời, chủ quản việc xuất nhập binh khí. */
    KUI(
        starName = "Khuê", quadrant = Quadrant.WHITE_TIGER, order = 15,
        luminary = Luminary.JUPITER, beast = "Lang", degree = 16f,
        component = "ToolRegistry", metric = "Số công cụ đã đăng ký",
        gloss = "Kho chứa binh khí, xuất nhập có quy tắc",
    ),

    /** Lâu Kim Cẩu: chủ tụ họp, chăn nuôi và vườn thú. */
    LOU(
        starName = "Lâu", quadrant = Quadrant.WHITE_TIGER, order = 16,
        luminary = Luminary.VENUS, beast = "Cẩu", degree = 12f,
        component = "McpManager", metric = "Số kết nối MCP",
        gloss = "Tập hợp và nuôi dưỡng, điều phối các bên ngoài",
    ),

    /** Vị Thổ Trĩ: kho lương, chủ tiếp nhận và lưu trữ. */
    WEI_WEST(
        starName = "Vị", quadrant = Quadrant.WHITE_TIGER, order = 17,
        luminary = Luminary.SATURN, beast = "Trĩ", degree = 14f,
        component = "InstallTransactionManager", metric = "Số giao dịch cài đặt",
        gloss = "Kho lương tiếp nhận, xuất nhập có chừng mực",
    ),

    /** Mão Nhật Kê: cụm sao Mão, chủ quan sát bốn phương — công cụ sau cửa Khai Dương. */
    MAO(
        starName = "Mão", quadrant = Quadrant.WHITE_TIGER, order = 18,
        luminary = Luminary.SUN, beast = "Kê", degree = 11f,
        component = "WebViewTabPool", metric = "Số lần tải trang",
        gloss = "Soi sáng bốn phương, mỗi nơi yên vị",
    ),

    /** Tất Nguyệt Ô: lưới Tất, thần mưa, chủ giăng lưới bắt giữ. */
    BI_WEST(
        starName = "Tất", quadrant = Quadrant.WHITE_TIGER, order = 19,
        luminary = Luminary.MOON, beast = "Ô", degree = 16f,
        component = "CdpFetchInterceptor", metric = "Số lượt chặn mạng",
        gloss = "Giăng lưới bắt gió, không để lọt mất",
    ),

    /** Chủy Hỏa Hầu: đầu và miệng hổ, ba sao sít nhau, chủ sự tinh xảo. */
    ZI(
        starName = "Chủy", quadrant = Quadrant.WHITE_TIGER, order = 20,
        luminary = Luminary.MARS, beast = "Hầu", degree = 2f,
        component = "GenericRecipeInstaller", metric = "Số lần chạy script",
        gloss = "Linh hoạt tinh xảo, từ điều nhỏ thấy việc lớn",
    ),

    /** Sâm Thủy Viên: thân Bạch Hổ, chủ chinh phạt, cân nhắc và biên sự. */
    SHEN(
        starName = "Sâm", quadrant = Quadrant.WHITE_TIGER, order = 21,
        luminary = Luminary.MERCURY, beast = "Viên", degree = 9f,
        component = "SandboxBoundary", metric = "Số lượt chặn vượt quyền",
        gloss = "Trấn giữ ranh giới, cân nhắc điều được phép",
    ),

    // Bảy tú Chu Tước phương Nam (112 độ): tầng hiển thị và trải nghiệm.

    /** Tỉnh Mộc Ngạn: đầu Chu Tước, Đông Tỉnh chủ nguồn nước. */
    JING(
        starName = "Tỉnh", quadrant = Quadrant.VERMILION_BIRD, order = 22,
        luminary = Luminary.JUPITER, beast = "Ngạn", degree = 33f,
        component = "Theme", metric = "Số lần đổi giao diện",
        gloss = "Nguồn suối tuôn ra, tưới mát toàn thể",
    ),

    /** Quỷ Kim Dương: mắt Chu Tước, còn gọi Dư Quỷ, chủ tích lũy. */
    GUI(
        starName = "Quỷ", quadrant = Quadrant.VERMILION_BIRD, order = 23,
        luminary = Luminary.VENUS, beast = "Dương", degree = 4f,
        component = "RuntimeComponents", metric = "Tỷ lệ tái sử dụng thành phần",
        gloss = "Tích lũy công cụ, sẵn sàng cho trăm nghề",
    ),

    /** Liễu Thổ Chương: miệng Chu Tước, chủ biểu đạt. */
    LIU(
        starName = "Liễu", quadrant = Quadrant.VERMILION_BIRD, order = 24,
        luminary = Luminary.SATURN, beast = "Chương", degree = 15f,
        component = "MarkdownText", metric = "Thời gian kết xuất khung hình",
        gloss = "Mở lời thành văn, bày tỏ ngôn từ",
    ),

    /** Tinh Nhật Mã: cổ Chu Tước, còn gọi Thất Tinh, chủ dẫn đường. */
    XING(
        starName = "Tinh", quadrant = Quadrant.VERMILION_BIRD, order = 25,
        luminary = Luminary.SUN, beast = "Mã", degree = 7f,
        component = "Navigation3", metric = "Số lượt chuyển trang",
        gloss = "Dẫn lối chỉ đường, xác định nơi đến",
    ),

    /** Trương Nguyệt Lộc: diều Chu Tước, chủ bếp trời và yến tiệc — công cụ sau cửa Dao Quang. */
    ZHANG(
        starName = "Trương", quadrant = Quadrant.VERMILION_BIRD, order = 26,
        luminary = Luminary.MOON, beast = "Lộc", degree = 18f,
        component = "SettingsStore", metric = "Số lượt đọc ghi tùy chọn",
        gloss = "Bày đủ trăm vị, mỗi người chọn thứ mình cần",
    ),

    /** Dực Hỏa Xà: cánh Chu Tước, hai mươi hai sao, chủ mở rộng và trải ra. */
    YI(
        starName = "Dực", quadrant = Quadrant.VERMILION_BIRD, order = 27,
        luminary = Luminary.MARS, beast = "Xà", degree = 18f,
        component = "WorkflowEditor", metric = "Số nút trên khung vẽ",
        gloss = "Dang rộng đôi cánh, vẽ nên toàn cảnh",
    ),

    /** Chẩn Thủy Dẫn: đuôi Chu Tước, còn gọi Thiên Xa, chủ chuyên chở — công cụ sau cửa Thiên Cơ. */
    ZHEN(
        starName = "Chẩn", quadrant = Quadrant.VERMILION_BIRD, order = 28,
        luminary = Luminary.MERCURY, beast = "Dẫn", degree = 17f,
        component = "PtySession", metric = "Số phiên còn hoạt động",
        gloss = "Chuyên chở tiến bước, thông suốt không trở ngại",
    ),
    ;

    /** Tên đầy đủ gồm tên tú, thất diệu và linh thú, ví dụ Giác Mộc Giao. */
    val fullName: String get() = "$starName ${luminary.label} $beast"

    /** Tên tú kèm thành phần, dùng trong nội dung trợ giúp. */
    val reference: String get() = "Tú $starName ($component)"

    companion object {
        /** Tra tú theo thứ tự 1..28; ngoài phạm vi trả về null. */
        fun ofOrder(order: Int): Mansion? = entries.firstOrNull { it.order == order }

        /** Tra tú theo tên hiển thị khi phân tích prompt và cấu hình. */
        fun ofName(name: String): Mansion? = entries.firstOrNull { it.starName == name }

        /** Tra tú theo tên thành phần để đối chiếu log tag. */
        fun ofComponent(component: String): Mansion? =
            entries.firstOrNull { it.component.equals(component, ignoreCase = true) }

        /** Tổng cự độ chu thiên: 365¼ độ. */
        val TOTAL_DEGREES: Float get() = entries.sumOf { it.degree.toDouble() }.toFloat()
    }
}

/**
 * Thất diệu — bảy diệu của nhị thập bát tú, tuần hoàn Mộc, Kim, Thổ, Nhật, Nguyệt, Hỏa, Thủy.
 * Mỗi tượng có bảy tú với cùng thứ tự thất diệu; biết một tượng có thể suy ra các tượng khác.
 */
enum class Luminary(
    /** Tên diệu, ví dụ Mộc. */
    val label: String,
    /** Tên thiên thể tương ứng. */
    val body: String,
) {
    /** Tuế Tinh. */
    JUPITER(label = "Mộc", body = "Tuế Tinh"),

    /** Thái Bạch. */
    VENUS(label = "Kim", body = "Thái Bạch"),

    /** Trấn Tinh. */
    SATURN(label = "Thổ", body = "Trấn Tinh"),

    /** Thái Dương. */
    SUN(label = "Nhật", body = "Thái Dương"),

    /** Thái Âm. */
    MOON(label = "Nguyệt", body = "Thái Âm"),

    /** Huỳnh Hoặc. */
    MARS(label = "Hỏa", body = "Huỳnh Hoặc"),

    /** Thần Tinh. */
    MERCURY(label = "Thủy", body = "Thần Tinh"),
}
