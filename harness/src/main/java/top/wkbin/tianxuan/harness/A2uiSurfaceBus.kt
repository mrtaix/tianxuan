package top.wkbin.tianxuan.harness

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * render_surface（A2UI PoC）工具的界面载荷总线。
 *
 * A2UI = Agent-to-UI：智能体不写代码，而是输出 JSON Lines 协议消息描述界面，
 * 客户端用 Compose 渲染器（feature:a2uipoc 模块）映射为原生组件。安全边界来自
 * Component Catalog：智能体只能使用目录里声明的组件，与工具白名单同构。
 *
 * PoC 简化说明：总线用 object 单例而非 Koin 注入，避免给 ToolExecutor/KoinModule
 * 两个棘轮基线文件增行；转正式实现时应改为接口 + 注入，并把总线收敛进会话生命周期。
 *
 * 深度校验走钩子注入（[installDeepValidator]）：官方 A2UI parser 只允许在
 * feature:a2uipoc 使用（architecture-policy），harness 保持零 a2ui 依赖，
 * 由 a2uipoc 启动时注册基于官方 parser 的真校验，失败原因回传给模型自我纠正。
 */
object A2uiSurfaceBus {

    /** 工具产出的一条界面载荷（一次 render_surface 调用 = 一个 surface 的协议消息集）。 */
    data class A2uiSurfacePayload(
        val surfaceId: String,
        val title: String,
        /** A2UI 协议消息数组的 JSON 字符串（已归一化，渲染器逐条 processInput）。 */
        val messagesJson: String,
        val messageCount: Int,
        /** 发起本次 render_surface 的会话 id；用户交互事件按它路由回原会话。 */
        val sessionId: String,
        val createdAt: Long,
    )

    /** 用户与 A2UI 界面交互产生的事件（由渲染器从官方 outboundEvents 转发而来）。 */
    data class A2uiUserEvent(
        val surfaceId: String,
        val surfaceTitle: String,
        val sessionId: String,
        val componentId: String,
        val eventName: String,
        val context: Map<String, Any?>,
        val timestamp: Long,
    )

    /** A2UI 引擎运行时错误（组件被目录校验拒绝、surface 状态异常等），同样回传给智能体。 */
    data class A2uiErrorEvent(
        val surfaceId: String,
        val surfaceTitle: String,
        val sessionId: String,
        val code: String,
        val message: String,
    )

    private val _surfaces = MutableStateFlow<List<A2uiSurfacePayload>>(emptyList())

    /** 最近发布的界面载荷（新的在前，最多 [MAX_CACHED_SURFACES] 条，同 surfaceId 覆盖旧条目）。 */
    val surfaces: StateFlow<List<A2uiSurfacePayload>> = _surfaces.asStateFlow()

    /** 引擎出站消息出口（用户交互/运行时错误）；由装配层（ChatViewModel）注册，路由回原会话。 */
    @Volatile
    var userEventSink: ((A2uiUserEvent) -> Unit)? = null

    @Volatile
    var errorEventSink: ((A2uiErrorEvent) -> Unit)? = null

    @Volatile
    private var deepValidator: ((String) -> String?)? = null

    /**
     * 注册深度校验器（a2uipoc 启动时调用）：入参为归一化后的 messages JSON，
     * 返回 null 表示受理通过，返回非 null 文案作为 render_surface 的失败原因回传给模型。
     */
    fun installDeepValidator(validator: ((String) -> String?)?) {
        deepValidator = validator
    }

    fun clear() {
        _surfaces.value = emptyList()
    }

    fun findSurface(surfaceId: String): A2uiSurfacePayload? =
        _surfaces.value.firstOrNull { it.surfaceId == surfaceId }

    /**
     * render_surface 工具执行入口：校验参数 → 登记载荷 → 返回给模型的结果文本。
     * 返回值约定与 ToolExecutor 一致（success to output）。
     */
    fun publishFromTool(args: JsonObject, sessionId: String = ""): Pair<Boolean, String> {
        val payload = parseToolArgs(args, sessionId).getOrElse { failure ->
            return false to "Kiểm tra tham số render_surface không qua: ${failure.message}. Vui lòng sửa tham số rồi gọi lại."
        }
        publish(payload)
        return true to buildString {
            append("Đã nộp giao diện A2UI «${payload.title}» (surfaceId=${payload.surfaceId}, ")
            append("${payload.messageCount} thông điệp giao thức). Giao diện đã được render thành component gốc trong luồng chat của người dùng;")
            append("Nếu cần cập nhật giao diện này, hãy gọi lại với cùng surfaceId và chỉ kèm tin nhắn updateComponents; ")
            append("Nếu cần gỡ giao diện, gửi tin nhắn deleteSurface. Tương tác của người dùng với giao diện (bấm nút v.v.) ")
            append("Sẽ được trả lại cho bạn dưới dạng tin nhắn [Sự kiện giao diện A2UI].")
        }
    }

    private fun publish(payload: A2uiSurfacePayload) {
        _surfaces.value = (listOf(payload) + _surfaces.value.filterNot { it.surfaceId == payload.surfaceId })
            .take(MAX_CACHED_SURFACES)
    }

    /** 渲染器转发引擎出站消息的入口（用户交互 → userEventSink，运行时错误 → errorEventSink）。 */
    fun publishUserEvent(event: A2uiUserEvent) {
        userEventSink?.invoke(event)
    }

    fun publishErrorEvent(event: A2uiErrorEvent) {
        errorEventSink?.invoke(event)
    }

    /** 把用户交互事件格式化为注入 agent 会话的消息文本。 */
    fun formatUserEvent(event: A2uiUserEvent): String = buildString {
        append("[Sự kiện giao diện A2UI] Người dùng tại giao diện \"${event.surfaceTitle}\" (surfaceId=${event.surfaceId})")
        append("đã kích hoạt tương tác: thành phần componentId=${event.componentId}, sự kiện eventName=${event.eventName}")
        if (event.context.isNotEmpty()) append("，mang theo context=${event.context}")
        append(". Đây là phản hồi tương tác người dùng của giao diện A2UI, hãy xử lý tiếp theo ngữ nghĩa sự kiện; ")
        append("Nếu cần cập nhật giao diện, gọi render_surface với cùng surfaceId và chỉ kèm thông điệp updateComponents.")
    }

    /** 把引擎运行时错误格式化为注入 agent 会话的消息文本（否则界面只会静默转圈）。 */
    fun formatErrorEvent(event: A2uiErrorEvent): String = buildString {
        append("[Lỗi giao diện A2UI] Giao diện «${event.surfaceTitle}» (surfaceId=${event.surfaceId})")
        append("Render thất bại: [${event.code}] ${event.message}.")
        append("Hãy sửa định nghĩa thành phần (tên thuộc tính/kiểu/tham chiếu thành phần con phải phù hợp Catalog),")
        append("Gọi lại render_surface với cùng surfaceId và kèm danh sách components đầy đủ (gồm component gốc id=root).")
    }

    internal fun parseToolArgs(args: JsonObject, sessionId: String = ""): Result<A2uiSurfacePayload> = runCatching {
        val surfaceId = args["surfaceId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(surfaceId.isNotEmpty()) { "Thiếu surfaceId" }
        require(surfaceId.length <= MAX_ID_CHARS && SURFACE_ID_REGEX.matches(surfaceId)) {
            "surfaceId chỉ cho phép chữ số và _ . : -, và không vượt quá $MAX_ID_CHARS ký tự"
        }
        val title = args["title"]?.jsonPrimitive?.contentOrNull?.trim().takeIf { !it.isNullOrBlank() } ?: "Giao diện A2UI"
        require(title.length <= MAX_TITLE_CHARS) { "title không vượt quá $MAX_TITLE_CHARS ký tự" }
        val rawMessages = args["messages"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        require(rawMessages.length in MIN_JSON_CHARS..MAX_JSON_CHARS) {
            "messages phải là chuỗi JSON của mảng tin nhắn giao thức A2UI (độ dài $MIN_JSON_CHARS..$MAX_JSON_CHARS)"
        }
        val messagesJson = parseMessagesArray(rawMessages).toString()
        deepValidator?.invoke(messagesJson)?.let { error -> error("Kiểm tra giao thức A2UI thất bại: $error") }
        A2uiSurfacePayload(
            surfaceId = surfaceId,
            title = title,
            messagesJson = messagesJson,
            messageCount = Json.parseToJsonElement(messagesJson).let { it as JsonArray }.size,
            sessionId = sessionId,
            createdAt = System.currentTimeMillis(),
        )
    }

    /**
     * 把模型提交的 messages 原文归一化为协议消息数组的紧凑 JSON；解析失败返回 null。
     * 工具路径（校验）与聊天卡片路径（渲染）共用，保证两条路律试到同一份归一化载荷。
     */
    fun normalizeMessagesJson(raw: String): String? = runCatching {
        parseMessagesArray(raw).toString()
    }.getOrNull()

    /**
     * 解析 messages 原文为协议消息数组，附带对模型常见双转义错误的自动修复：
     * 1) 正常路径：直接是 JSON 数组；
     * 2) 整体被包成 JSON 字符串（双重编码）→ 剥一层再解析；
     * 3) 引号带多余反斜杠转义（[{\"version\"…）→ 去掉多余转义再解析。
     */
    private fun parseMessagesArray(raw: String): JsonArray {
        val direct = runCatching { Json.parseToJsonElement(raw) }.getOrNull()
        if (direct is JsonArray) return validateArrayShape(direct)
        if (direct is JsonPrimitive && direct.isString) {
            val unwrapped = runCatching { Json.parseToJsonElement(direct.content) }.getOrNull()
            if (unwrapped is JsonArray) return validateArrayShape(unwrapped)
        }
        if (raw.contains("\\\"")) {
            val repaired = runCatching { Json.parseToJsonElement(raw.replace("\\\"", "\"")) }.getOrNull()
            if (repaired is JsonArray) return validateArrayShape(repaired)
        }
        throw IllegalArgumentException(
            "messages phải là chuỗi JSON của mảng tin nhắn giao thức A2UI (tin đầu tiên createSurface, " +
                "sau đó updateComponents/deleteSurface); đừng escape hai lần với tham số messages," +
                "Đưa ra nguyên văn mảng trực tiếp là được",
        )
    }

    private fun validateArrayShape(array: JsonArray): JsonArray {
        require(array.isNotEmpty() && array.all { it is JsonObject && it.containsKey("version") }) {
            "messages mỗi phần tử mảng phải là đối tượng tin nhắn giao thức A2UI có version"
        }
        array.forEach { message ->
            val version = (message as JsonObject)["version"]?.jsonPrimitive?.contentOrNull
            require(version in SUPPORTED_VERSIONS) {
                "Giao thức version chỉ hỗ trợ $SUPPORTED_VERSIONS (nhận được: $version)"
            }
        }
        return array
    }

    /** 供 feature:a2uipoc 演示屏使用的内置示例（与 [A2uiSurfaceContract.CATALOG_ID] 对齐）。 */
    fun sampleMessagesJson(surfaceId: String = "demo-1"): String = buildSampleMessages(surfaceId)

    internal const val MAX_ID_CHARS = 64
    internal const val MAX_TITLE_CHARS = 80
    internal const val MIN_JSON_CHARS = 8
    internal const val MAX_JSON_CHARS = 200_000
    internal const val MAX_CACHED_SURFACES = 8
    internal val SURFACE_ID_REGEX = Regex("[A-Za-z0-9_.:-]+")

    /** 与官方 A2uiJsonMessageParser.SUPPORTED_VERSIONS 对齐，库升级时需联动核对。 */
    internal val SUPPORTED_VERSIONS = listOf("v0.9", "v0.9.1")

    // 注意：含模板引用，不能声明为 const val（棘轮文件外的新文件，保持 private fun 即可）
    private fun buildSampleMessages(surfaceId: String): String =
        """[{"version":"v0.9","createSurface":{"surfaceId":"$surfaceId","catalogId":"${A2uiSurfaceContract.CATALOG_ID}"}},
{"version":"v0.9","updateComponents":{"surfaceId":"$surfaceId","components":[
{"id":"root","component":"Column","children":["title","card","hint"]},
{"id":"title","component":"Text","text":"Thiên Huyền A2UI PoC"},
{"id":"card","component":"Card","child":"cardText"},
{"id":"cardText","component":"Text","text":"Giao diện này do Agent tạo qua render_surface, render thành thành phần Compose native."},
{"id":"hint","component":"Text","text":"Phạm vi thành phần do Catalog khai báo, Agent không thể thực thi mã bất kỳ."}
]}}]"""
}
