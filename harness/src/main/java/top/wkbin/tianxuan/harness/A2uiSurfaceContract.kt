package top.wkbin.tianxuan.harness

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * render_surface 工具对 LLM 暴露的契约（名称 / 描述 / 参数 Schema / ApiToolDefinition）。
 *
 * 单独成文件的原因：ProviderClient 的 TOOLS 列表在尺寸棘轮基线内（只许缩减），
 * 这里把定义收敛为一个常量，ProviderClient 只需追加一行引用。
 */
object A2uiSurfaceContract {

    const val TOOL_NAME = "render_surface"

    /**
     * M3 Basic Catalog 的目录 id：createSurface 消息里的 catalogId 必须与之一致。
     * 值与 androidx.a2ui.compose.ui.catalog.A2uiBasicCatalogV1.CatalogId 对齐
     * （harness 不依赖该库，故此处以常量同步；库升级时需联动核对）。
     */
    const val CATALOG_ID = "https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json"

    private const val TOOL_DESCRIPTION =
        "Render dữ liệu có cấu trúc thành giao diện tương tác native trên thiết bị (A2UI PoC). Nội dung phù hợp trực quan hóa (so sánh/xếp hạng/" +
            "biểu mẫu/bảng kanban) thì dùng công cụ này để gửi, sau đó tóm tắt một hai câu, không lặp lại dữ liệu trong phần nội dung chính." +
            "messages là chuỗi JSON của mảng tin nhắn A2UI: tin đầu tiên createSurface (giao diện mới dùng surfaceId hoàn toàn mới), " +
            "sau đó updateComponents (components phẳng, container dùng children/child tham chiếu id thành phần con, đúng một root), " +
            "deleteSurface để xóa giao diện. version cố định v0.9; cấm escape hai lần với messages; " +
            "Văn bản viết hằng rõ ràng không dùng biểu thức, không được bịa loại thành phần (không có thành phần bảng, bảng dùng List hoặc tổ hợp Row/Column)." +
            "Tương tác như người dùng nhấp giao diện sẽ được trả về dưới dạng tin nhắn [Sự kiện giao diện A2UI], dựa vào đó xử lý hoặc dùng cùng surfaceId để cập nhật giao diện."

    private const val TOOL_SCHEMA_JSON =
        """{"type":"object","properties":{"surfaceId":{"type":"string","description":"界面唯一 id（字母数字与 _ . : -，≤64 字符）；更新已有界面时必须复用原 id"},"title":{"type":"string","description":"界面短标题（≤80 字符）"},"messages":{"type":"string","description":"A2UI 协议消息数组的 JSON 字符串；不要二次转义，version 固定为 v0.9，catalogId 固定为 $CATALOG_ID。示例：[{\"version\":\"v0.9\",\"createSurface\":{\"surfaceId\":\"s1\",\"catalogId\":\"$CATALOG_ID\"}},{\"version\":\"v0.9\",\"updateComponents\":{\"surfaceId\":\"s1\",\"components\":[{\"id\":\"root\",\"component\":\"Column\",\"children\":[\"t\"]},{\"id\":\"t\",\"component\":\"Text\",\"text\":\"Xin chào\"}]}}]"}},"required":["surfaceId","messages"]}"""

    /** ProviderClient.TOOLS 直接引用，避免在棘轮基线文件里展开多行定义。 */
    val TOOL_DEFINITION: ApiToolDefinition = ApiToolDefinition(
        function = ApiFunctionDefinition(
            name = TOOL_NAME,
            description = TOOL_DESCRIPTION,
            parameters = Json.parseToJsonElement(TOOL_SCHEMA_JSON) as JsonObject,
        ),
    )
}
