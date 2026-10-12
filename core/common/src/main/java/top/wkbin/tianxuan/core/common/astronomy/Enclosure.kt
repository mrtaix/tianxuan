package top.wkbin.tianxuan.core.common.astronomy

/**
 * 三垣 —— 天玄记忆库的三层结构。
 *
 * 三垣（紫微垣、太微垣、天市垣）与二十八宿合称「三垣二十八宿」。
 * 紫微为天帝居所，太微为朝廷政务，天市为天上街市。
 *
 * 以此构建记忆三层，让模型明确「该往哪一层写、从哪一层读」，
 * 避免把短期政务写进身份、也避免长期偏好被对话冲掉。
 */
enum class Enclosure(
    /** 垣名，如「紫微垣」。 */
    val label: String,
    /** 古义。 */
    val gloss: String,
    /** 记忆层名。 */
    val memoryLayer: String,
    /** 该层的生命周期策略。 */
    val retention: String,
    /** 写入策略：何种内容应落此层。 */
    val writePolicy: String,
    /** 读取策略：何时召回此层。 */
    val readPolicy: String,
) {
    /** 紫微垣：天帝居所，众星环拱之中枢 —— 核心记忆。 */
    ZI_WEI(
        label = "Tử Vi Viên",
        gloss = "Nơi ở của Thiên Đế, trung tâm được các vì sao vây quanh",
        memoryLayer = "Ký ức cốt lõi",
        retention = "Thường trú vĩnh viễn, không nén, không đưa ra ngoài",
        writePolicy = "Thiết lập danh tính, sở thích dài hạn, chân dung người dùng, quy tắc bất di bất dịch",
        readPolicy = "Phải đọc mỗi lượt, prompt hệ thống thường trú",
    ),

    /** 太微垣：朝廷政务，主施行 —— 工作记忆。 */
    TAI_WEI(
        label = "Thái Vi Viên",
        gloss = "Chính vụ triều đình, chủ thi hành",
        memoryLayer = "Bộ nhớ công việc",
        retention = "Phiên kết thúc có thể lưu trữ, quá hạn chuyển vào Tử Vi Viên hoặc Thiên Thị Viên",
        writePolicy = "Phiên hiện tại, kế hoạch thực thi, checkpoint nhiệm vụ, sản phẩm trung gian",
        readPolicy = "Đọc theo nhu cầu khi nhiệm vụ đang chạy, lưu trữ khi chuyển hội thoại",
    ),

    /** 天市垣：天上街市，主交易流通 —— 检索记忆。 */
    TIAN_SHI(
        label = "Thiên Thị Viên",
        gloss = "Thiên thượng nhai thị, chủ lưu thông giao dịch",
        memoryLayer = "Truy xuất bộ nhớ",
        retention = "Vào ra theo nhu cầu, trúng thì dùng, không trúng thì không thường trú",
        writePolicy = "Tri thức ngoài, lưu trữ lịch sử, ngữ liệu có thể để ngoài",
        readPolicy = "Tiêm vào sau khi truy xuất ngữ nghĩa, dùng xong thì thoát",
    ),
    ;

    /** UI 上「垣名 · 记忆层」的显示串。 */
    val display: String get() = "$label · $memoryLayer"

    companion object {
        /** 按记忆层名取垣，用于配置与提示词解析。 */
        fun ofMemoryLayer(memoryLayer: String): Enclosure? =
            entries.firstOrNull { it.memoryLayer == memoryLayer }
    }
}
