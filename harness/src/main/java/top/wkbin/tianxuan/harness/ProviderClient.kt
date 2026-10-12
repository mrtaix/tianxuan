package top.wkbin.tianxuan.harness

import top.wkbin.tianxuan.core.database.AiModelRepository
import top.wkbin.tianxuan.core.datastore.AgentPreferences
import top.wkbin.tianxuan.core.tools.ProviderRepository
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import top.wkbin.tianxuan.harness.mcp.McpToolApiName
import top.wkbin.tianxuan.harness.prompt.PromptRouter

/** HTTP 429 的结构化错误，供 Harness 区分临时限流与账户额度耗尽。 */
class LlmRateLimitException(
    message: String,
    val retryAfterSeconds: Long? = null,
    val quotaExhausted: Boolean = false,
) : IOException(message)

/** 上游临时故障（5xx，如 Cloudflare 524 origin timeout）：可退避重试，由网络重试路径统一处理。 */
class TransientHttpException(
    message: String,
    val httpCode: Int,
    val retryAfterSeconds: Long? = null,
) : IOException(message)

/**
 * 上下文超限（HTTP 400 家族与 413 Payload Too Large）：与其他 4xx 的本质区别是可自愈——
 * 引擎捕获后执行紧急机械压缩并重试同一请求（对齐 opencode 的 overflow → compact → replay 闭环），
 * 而不是直接把失败抛给用户。HTTP 413 常见于 Nginx/中转拒绝过大 JSON，body 往往是 HTML。
 */
class LlmContextOverflowException(message: String) : IOException(message)

/** Provider rejected the requested output-token parameter, not the input context. */
class LlmInvalidOutputTokensException(message: String) : IOException(message)

/**
 * 上游以 HTTP 200 返回了"Không có gì cả"的一轮（无正文、无推理、无工具调用）。
 *
 * 这类响应若不显式识别，会被上层当成"Model trả lời xong rồi"正常收尾，前台表现为
 * 「消息发出去没有任何回复」——既不报错也没有内容，正是最无从定位的一种形态。
 */
class LlmEmptyResponseException(message: String) : IOException(message)

/** 可独立测试的 HTTP 层：OpenAI 兼容 chat/completions 请求与响应解析。 */
internal class ChatApi(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    @OptIn(InternalCoroutinesApi::class)
    suspend fun chat(model: ModelConfig, messages: List<ApiMessage>): ChatResult =
        withContext(Dispatchers.IO) {
            val call = okHttpClient.newCall(buildRequest(model, messages, stream = false))
            // 与流式路径一致：取消时立即关闭 socket，否则阻塞的 execute()/body.string()
            // 不感知协程取消，用户点"停止"后最长要等满 callTimeout。
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
            try {
                call.execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) {
                        if (response.code == 429) {
                            throw ProviderClient.rateLimitException(response.code, body, response.header("Retry-After"))
                        }
                        if (response.code in 500..599) {
                            throw ProviderClient.transientHttpException(response.code, body, response.header("Retry-After"))
                        }
                        throw ProviderClient.contextOverflowException(response.code, body)
                    }
                    if (!ProviderClient.looksLikeJsonResponse(body)) {
                        throw IllegalStateException(
                            ProviderClient.formatHttpErrorMessage(response.code, body),
                        )
                    }
                    val parsed = json.decodeFromString(ChatCompletionResponse.serializer(), body)
                    val message = parsed.choices.firstOrNull()?.message ?: ChatResponseMessage()
                    message.toChatResult(parsed.usage?.toChatUsage() ?: ChatUsage())
                }
            } finally {
                cancelHandle?.dispose()
            }
        }

    /**
     * 流式调用：逐行读取 SSE（data: ...），每个内容增量立即通过 [onDelta] 回调
     * 交给 UI；工具调用参数按 index 分片累积。推理增量通过 [onReasoning] 回调。
     *
     * 默认携带 stream_options.include_usage 请求最终 usage 块；个别严格校验的
     * Provider 会因此 400，此时自动降级为不带该参数重试一次（请求在流开始前
     * 即失败，不会有增量重复发送的风险）。
     */
    @OptIn(InternalCoroutinesApi::class)
    suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit = {},
        onToolProgress: (ToolCallStreamProgress) -> Unit = {},
        onDelta: (String) -> Unit,
    ): ChatResult = try {
        executeStream(model, messages, onReasoning, onToolProgress, onDelta, includeUsage = true)
    } catch (rejected: IllegalStateException) {
        if (rejected.message?.contains("stream_options", ignoreCase = true) == true) {
            executeStream(model, messages, onReasoning, onToolProgress, onDelta, includeUsage = false)
        } else {
            throw rejected
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    private suspend fun executeStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit,
        onToolProgress: (ToolCallStreamProgress) -> Unit,
        onDelta: (String) -> Unit,
        includeUsage: Boolean,
    ): ChatResult = withContext(Dispatchers.IO) {
        val call = okHttpClient.newCall(buildRequest(model, messages, stream = true, includeUsage = includeUsage))
        // 关键：阻塞式 readUtf8Line() 不感知协程取消。用户点"停止"时必须主动 call.cancel()
        // 关闭底层 socket，阻塞读才会立刻抛出 IOException 退出——否则要等读超时，
        // 表现为"Nút dừng không phản hồi"。
        val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true) { call.cancel() }
        // source.timeout() 只有收到 HTTP 响应头后才生效。看门狗覆盖 DNS、连接、请求体上传、
        // 等待响应头以及首个 SSE 事件的完整阶段，避免大请求仍静默等满 callTimeout。
        val firstEventTimeoutMs = ProviderClient.resolveFirstEventTimeoutMs(
            ProviderClient.estimateApiMessageTokens(messages),
        )
        val firstEventState = AtomicInteger(ProviderClient.FIRST_EVENT_WAITING)
        val firstEventWatchdog = launch {
            delay(firstEventTimeoutMs)
            if (firstEventState.compareAndSet(ProviderClient.FIRST_EVENT_WAITING, ProviderClient.FIRST_EVENT_TIMED_OUT)) {
                call.cancel()
            }
        }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val rawBody = response.body.string().take(512)
                    if (response.code == 429) {
                        throw ProviderClient.rateLimitException(response.code, rawBody, response.header("Retry-After"))
                    }
                    if (response.code in 500..599) {
                        throw ProviderClient.transientHttpException(response.code, rawBody, response.header("Retry-After"))
                    }
                    throw ProviderClient.contextOverflowException(response.code, rawBody)
                }
                val source = response.body.source()
                val demuxer = ThinkTagStreamDemuxer(onReasoning, onDelta)
                val toolCalls = mutableMapOf<Int, ToolCallAccumulator>()
                // 网关不分发 index 时的流式调用归并状态：id → 槽位、下一个可用的合成槽、
                // 最近活跃槽（承接无 id 的 arguments 延续分片）
                val slotByCallId = mutableMapOf<String, Int>()
                var nextSyntheticSlot = 0
                var lastActiveSlot: Int? = null
                var usage = ChatUsage()
                // 空响应诊断首部：只缓冲到出现首个 data: 行为止（或到上限），
                // 既不把整段流式正文留在内存，又能在"Không nhận được gì cả"时把上游真实返回写进错误。
                val rawHead = StringBuilder()
                var sawSseData = false
                // 非 SSE 响应体：部分 OpenAI 兼容网关忽略 stream:true，直接返回整段 JSON 补全。
                // 此前这种 body 因"Không có dòng data:"被逐行丢弃，最终表现为空回复。
                var nonSseBody: String? = null
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!sawSseData && rawHead.length < ProviderClient.RESPONSE_HEAD_CAPTURE_CHARS) {
                        rawHead.append(line).append('\n')
                    }
                    if (!line.startsWith("data:")) {
                        val trimmed = line.trimStart()
                        val sseField = trimmed.isEmpty() ||
                            trimmed.startsWith("event:") || trimmed.startsWith("id:") ||
                            trimmed.startsWith("retry:") || trimmed.startsWith(":")
                        if (!sawSseData && !sseField) {
                            nonSseBody = line + "\n" + source.readUtf8()
                            break
                        }
                        continue
                    }
                    sawSseData = true
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val root = runCatching { json.parseToJsonElement(data) as? JsonObject }.getOrNull()
                    if (root != null && firstEventState.compareAndSet(
                            ProviderClient.FIRST_EVENT_WAITING,
                            ProviderClient.FIRST_EVENT_RECEIVED,
                        )
                    ) {
                        firstEventWatchdog.cancel()
                    }
                    // usage 块位于 chunk 顶层（stream_options.include_usage 时由最后一个 chunk 携带；
                    // DeepSeek/OpenRouter 等默认就会发）。后面的块覆盖前面的，保留最终值。
                    (root?.get("usage") as? JsonObject)?.let { block ->
                        parseUsageBlock(block)?.let { parsed -> usage = parsed }
                    }
                    val choice = root
                        ?.get("choices")?.let { it as? JsonArray }?.firstOrNull() as? JsonObject
                        ?: continue
                    val delta = choice["delta"] as? JsonObject
                    delta?.get("content")?.let { it as? JsonPrimitive }?.contentOrNull?.let { chunk ->
                        if (chunk.isNotEmpty()) {
                            demuxer.onContentChunk(chunk)
                        }
                    }
                    // 推理增量：兼容 reasoning_content（DeepSeek/GLM 等）与 reasoning（OpenRouter 等网关）两种字段名
                    val reasoningChunk = (delta?.get("reasoning_content") ?: delta?.get("reasoning"))
                        ?.let { it as? JsonPrimitive }?.contentOrNull
                    reasoningChunk?.let { chunk ->
                        if (chunk.isNotEmpty()) {
                            demuxer.onExplicitReasoningChunk(chunk)
                        }
                    }
                    // 调用边界判据：显式 index 优先；网关不分发 index 时以「id 变化」识别新调用
                    // ——tool call 首个分片必带非空 id，后续 arguments 分片不带 id。若按元素
                    // 迭代序号 fallback（每个 chunk 恒为 0），跨 chunk 的多个并行调用会全部
                    // 落进同一槽位：id 相互覆盖、arguments 拼成非法 JSON、其中一个调用静默丢失。
                    delta?.get("tool_calls")?.let { it as? JsonArray }?.forEachIndexed { position, call2 ->
                        val callObj = call2 as? JsonObject ?: return@forEachIndexed
                        val explicitIndex = callObj["index"]?.let { it as? JsonPrimitive }?.contentOrNull?.toIntOrNull()
                        val callId = callObj["id"]?.let { it as? JsonPrimitive }?.contentOrNull
                            ?.takeIf { it.isNotEmpty() }
                        val slot: Int = when {
                            explicitIndex != null -> explicitIndex
                            callId != null -> slotByCallId[callId] ?: run {
                                var candidate = nextSyntheticSlot++
                                while (candidate in toolCalls) candidate = nextSyntheticSlot++
                                slotByCallId[callId] = candidate
                                candidate
                            }
                            // 无 index 无 id 的分片：延续最近活跃调用（arguments 增量）。
                            // 用 elvis 而非 != null 分支：lastActiveSlot 是被 lambda 捕获的
                            // var，Kotlin 无法 smart cast，在分支里直接取值只能写 !!。
                            else -> lastActiveSlot ?: run {
                                var candidate = nextSyntheticSlot++
                                while (candidate in toolCalls) candidate = nextSyntheticSlot++
                                candidate
                            }
                        }
                        lastActiveSlot = slot
                        val accum = toolCalls.getOrPut(slot) { ToolCallAccumulator() }
                        callId?.let {
                            accum.id = it
                            slotByCallId.putIfAbsent(callId, slot)
                        }
                        val function = callObj["function"] as? JsonObject
                        function?.get("name")?.let { it as? JsonPrimitive }?.contentOrNull
                            ?.takeIf { it.isNotEmpty() }?.let { accum.name = it }
                        function?.get("arguments")?.let { it as? JsonPrimitive }?.contentOrNull
                            ?.let { accum.arguments.append(it) }
                        accum.publishProgress(onToolProgress)
                    }
                }
                demuxer.flush()
                toolCalls.values.forEach { it.publishProgress(onToolProgress, force = true) }
                // 部分 OpenAI 兼容端对无参数函数不下发 arguments 分片，空串须兜底为 "{}"
                val calls = toolCalls.values.map {
                    ApiToolCallSpec(
                        it.id.ifBlank { ToolCallIdNormalizer.normalize(null) },
                        it.name,
                        it.arguments.toString().ifBlank { "{}" },
                    )
                }
                ChatResult(
                    content = demuxer.fullText.toString().ifEmpty { null },
                    toolCalls = calls,
                    reasoningContent = demuxer.fullReasoning.toString().ifEmpty { null },
                    usage = usage,
                ).let { streamed ->
                    // HTTP 200 却什么都没产出：先按非流式 JSON 兜底解析，仍拿不到内容就明确报错。
                    // 绝不允许空响应继续往下走——它会一路"Không gọi công cụ → thu lại → Outcome=completed"，
                    // 前台看不到任何内容也没有任何错误，即用户反馈的「发消息没有回复」。
                    if (streamed.isBlankResponse) {
                        resolveBlankStreamResult(nonSseBody, rawHead.toString())
                    } else {
                        streamed
                    }
                }
            }
        } catch (io: IOException) {
            if (firstEventState.get() == ProviderClient.FIRST_EVENT_TIMED_OUT) {
                throw SocketTimeoutException(
                    "Chờ phản hồi đầu tiên của model quá ${firstEventTimeoutMs / 1000}s",
                ).apply { initCause(io) }
            }
            throw io
        } finally {
            firstEventWatchdog.cancel()
            cancelHandle?.dispose()
        }
    }

    /** 非流式补全消息 → 统一 [ChatResult]（与 [chat] 同一口径，供兜底解析复用）。 */
    private fun ChatResponseMessage.toChatResult(usage: ChatUsage): ChatResult {
        val calls = tool_calls.orEmpty().mapNotNull { call ->
            call.function.let { fn ->
                if (fn.name.isBlank()) null else ApiToolCallSpec(
                    call.id.ifBlank { ToolCallIdNormalizer.normalize(null) },
                    fn.name,
                    fn.arguments.ifBlank { "{}" },
                )
            }
        }
        val (extractedContent, extractedReasoning) =
            ProviderClient.extractThinkTags(content, reasoning_content)
        return ChatResult(
            content = extractedContent,
            toolCalls = calls,
            reasoningContent = extractedReasoning,
            usage = usage,
        )
    }

    /**
     * 流式请求拿到了完全空的一轮（HTTP 200，但无正文/推理/工具调用）。
     *
     * 先按非流式 JSON 补全兜底解析：[nonSseBody] 非空说明响应体根本不是 SSE
     * （网关忽略了 `stream: true`），此前这种 body 会被逐行丢弃、最终表现为"Phản hồi rỗng"。
     * 兜底仍无内容时抛 [LlmEmptyResponseException]，并附上原始响应首部——
     * 把一次"Không có gì cả"变成可定位、可重试的失败。
     */
    private fun resolveBlankStreamResult(nonSseBody: String?, rawHead: String): ChatResult {
        val body = nonSseBody?.trim().orEmpty()
        if (body.isNotEmpty() && ProviderClient.looksLikeJsonResponse(body)) {
            val parsed = runCatching {
                json.decodeFromString(ChatCompletionResponse.serializer(), body)
            }.getOrNull()
            if (parsed != null) {
                val recovered = (parsed.choices.firstOrNull()?.message ?: ChatResponseMessage())
                    .toChatResult(parsed.usage?.toChatUsage() ?: ChatUsage())
                if (!recovered.isBlankResponse) return recovered
            }
        }
        throw LlmEmptyResponseException(
            ProviderClient.EMPTY_RESPONSE_MESSAGE +
                ProviderClient.describeResponseHead(body.ifEmpty { rawHead }),
        )
    }

    /** 手工解析流式 chunk 顶层 usage（各 Provider 字段不统一，DTO 反而脆）。 */
    private fun parseUsageBlock(block: JsonObject): ChatUsage? {
        val input = block["prompt_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: return null
        return ChatUsage(
            inputTokens = input,
            outputTokens = block["completion_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
            reasoningTokens = (block["completion_tokens_details"] as? JsonObject)
                ?.get("reasoning_tokens")?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
            cacheReadTokens = (block["prompt_tokens_details"] as? JsonObject)
                ?.get("cached_tokens")?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull()
                ?: block["prompt_cache_hit_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
            cacheWriteTokens = block["prompt_cache_miss_tokens"]?.let { it as? JsonPrimitive }?.contentOrNull?.toLongOrNull() ?: 0,
        )
    }

    private fun buildRequest(model: ModelConfig, messages: List<ApiMessage>, stream: Boolean, includeUsage: Boolean = true): Request {
            val tools = if (model.pureChatMode) emptyList() else ProviderClient.buildDynamicTools()
        // JSON_TEXT 模式：把工具 JSON 描述追加到首条 system 消息末尾，让模型在纯文本中输出工具调用。
        // 只注入首条：压缩摘要层也是 system 消息，全量注入会把数千 token 的工具 schema 复制多份，
        // 还把 JSON 定义拼在「早期历史摘要」末尾污染摘要语义（Anthropic/Responses 路径本就只注入一次）。
        val effectiveMessages = if (!model.pureChatMode && model.toolCallMode == ToolCallMode.JSON_TEXT && tools.isNotEmpty()) {
            val desc = ProviderClient.buildToolsTextDescription(tools)
            var injected = false
            messages.map { msg ->
                if (!injected && msg.role == "system" && !msg.content.isNullOrBlank()) {
                    injected = true
                    msg.copy(content = msg.content + "\n\n## Định nghĩa JSON công cụ khả dụng (phải xuất đúng name và tham số này)\n" + desc)
                } else {
                    msg
                }
            }
        } else {
            messages
        }
        // NATIVE 模式下 tools 数组独立于 messages，输出预算必须显式扣掉 schema；
        // JSON_TEXT 模式下 schema 已内联进 effectiveMessages，由 messages 口径覆盖，故传 0。
        val toolSchemaTokens =
            if (!model.pureChatMode && model.toolCallMode == ToolCallMode.NATIVE) {
                ContextWindowPolicy.estimateToolDefinitionTokens(tools)
            } else {
                0
            }
        val requestJson = kotlinx.serialization.json.buildJsonObject {
            put("model", kotlinx.serialization.json.JsonPrimitive(model.model))
            put("stream", kotlinx.serialization.json.JsonPrimitive(stream))
            if (stream && includeUsage) {
                // 请求最终 usage 块（OpenAI 官方规范字段；DeepSeek/DashScope/GLM/OpenRouter 均支持）
                put("stream_options", kotlinx.serialization.json.buildJsonObject {
                    put("include_usage", kotlinx.serialization.json.JsonPrimitive(true))
                })
            }
            model.temperature?.let { put("temperature", kotlinx.serialization.json.JsonPrimitive(it)) }
            put("max_tokens", kotlinx.serialization.json.JsonPrimitive(
                ContextWindowPolicy.outputBudget(
                    model.maxTokens,
                    8_192,
                    effectiveMessages,
                    model.contextTokens,
                    model.model,
                    model.provider,
                    toolSchemaTokens,
                ),
            ))
            model.topP?.let { put("top_p", kotlinx.serialization.json.JsonPrimitive(it)) }
            // 推理开关/强度：按厂商能力翻译（reasoning_effort / thinking_config / thinking / reasoning）
            ReasoningAdapter.openAiFields(model).forEach { (key, value) -> put(key, value) }
            // 工具调用：纯净模式与 DISABLED 模式完全不注入工具相关参数；仅 NATIVE 模式注入标准 tools
            if (!model.pureChatMode && model.toolCallMode == ToolCallMode.NATIVE && tools.isNotEmpty()) {
                put("tools", json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(ApiToolDefinition.serializer()), tools))
            }
            put("messages", kotlinx.serialization.json.buildJsonArray {
                effectiveMessages.forEach { msg ->
                    add(kotlinx.serialization.json.buildJsonObject {
                        put("role", kotlinx.serialization.json.JsonPrimitive(msg.role))
                        if (msg.imageUrls.isNotEmpty()) {
                            put("content", kotlinx.serialization.json.buildJsonArray {
                                if (!msg.content.isNullOrBlank()) {
                                    add(kotlinx.serialization.json.buildJsonObject {
                                        put("type", kotlinx.serialization.json.JsonPrimitive("text"))
                                        put("text", kotlinx.serialization.json.JsonPrimitive(msg.content))
                                    })
                                }
                                msg.imageUrls.forEach { url ->
                                    add(kotlinx.serialization.json.buildJsonObject {
                                        put("type", kotlinx.serialization.json.JsonPrimitive("image_url"))
                                        put("image_url", kotlinx.serialization.json.buildJsonObject {
                                            put("url", kotlinx.serialization.json.JsonPrimitive(url))
                                        })
                                    })
                                }
                            })
                        } else {
                            // Strict OpenAI-compatible gateways deserialize content as a required
                            // String; never omit it. Assistant tool-call turns legitimately have no
                            // text, so send an empty string instead.
                            put("content", kotlinx.serialization.json.JsonPrimitive(msg.content ?: ""))
                        }
                        msg.reasoning_content?.let { put("reasoning_content", kotlinx.serialization.json.JsonPrimitive(it)) }
                        msg.tool_call_id?.let { put("tool_call_id", kotlinx.serialization.json.JsonPrimitive(it)) }
                        msg.tool_calls?.let { calls ->
                            put("tool_calls", json.encodeToJsonElement(kotlinx.serialization.builtins.ListSerializer(ApiToolCall.serializer()), calls))
                        }
                    })
                }
            })
        }
        return Request.Builder()
            .url("${model.baseUrl.trimEnd('/')}/chat/completions")
            .header("Content-Type", "application/json")
            .apply {
                model.apiKey?.let { header("Authorization", "Bearer $it") }
                ProviderClient.parseCustomHeaders(model.customHeaders).forEach { (name, value) ->
                    header(name, value)
                }
            }
            // 直接序列化为 ByteArray，省去 JsonObject→String→ByteArray 中间的 String 副本，
            // 高峰时减少一份完整请求体大小的临时堆驻留。
            .post(requestJson.toString().encodeToByteArray().toRequestBody(ProviderClient.JSON_MEDIA_TYPE))
            .build()
    }
}

/** 分片累积一次工具调用的 id/name/arguments（OpenAI 与 Anthropic 流式均复用）。 */
internal data class ToolCallAccumulator(
    var id: String = "",
    var name: String = "",
    val arguments: StringBuilder = StringBuilder(),
) {
    private var lastProgressAtNanos: Long = 0L
    private var lastProgress: Pair<Int, Int>? = null

    /**
     * 工具参数本身也是 SSE 分片。WRITE/EDIT 在完整 JSON 到达前不能执行，但可以从
     * 已收到的 JSON 字符串片段估算增删行数，避免大文件生成期间 UI 长时间停在“思考中”。
     */
    fun publishProgress(onProgress: (ToolCallStreamProgress) -> Unit, force: Boolean = false) {
        val normalizedName = name.trim().lowercase()
        if (normalizedName != "write" && normalizedName != "edit") return
        val now = System.nanoTime()
        if (!force && lastProgressAtNanos != 0L && now - lastProgressAtNanos < TOOL_PROGRESS_INTERVAL_NANOS) return

        val progress = when (normalizedName) {
            "write" -> ToolCallStreamProgress(
                name = normalizedName,
                addedLines = countPartialJsonStringLines(arguments, "content") ?: 0,
                deletedLines = 0,
            )
            else -> ToolCallStreamProgress(
                name = normalizedName,
                addedLines = countPartialJsonStringLines(arguments, "newText") ?: 0,
                deletedLines = countPartialJsonStringLines(arguments, "oldText") ?: 0,
            )
        }
        val counts = progress.addedLines to progress.deletedLines
        if (force || counts != lastProgress) {
            lastProgress = counts
            lastProgressAtNanos = now
            onProgress(progress)
        }
    }

    private companion object {
        const val TOOL_PROGRESS_INTERVAL_NANOS = 200_000_000L
    }
}

/** WRITE/EDIT 工具参数流的轻量进度；只用于 UI，不参与最终文件写入。 */
data class ToolCallStreamProgress(
    val name: String,
    val addedLines: Int,
    val deletedLines: Int,
)

/**
 * 从尚未闭合的 JSON 参数中读取指定字符串字段的当前行数。
 * JSON 中换行通常是 `\n`；转义反斜杠 `\\n` 不应误判为新行。
 */
internal fun countPartialJsonStringLines(arguments: CharSequence, fieldName: String): Int? {
    val key = "\"$fieldName\""
    var searchFrom = 0
    while (searchFrom < arguments.length) {
        val keyStart = arguments.indexOf(key, searchFrom)
        if (keyStart < 0) return null
        var cursor = keyStart + key.length
        while (cursor < arguments.length && arguments[cursor].isWhitespace()) cursor++
        if (cursor >= arguments.length) return null
        if (arguments[cursor] != ':') {
            searchFrom = keyStart + key.length
            continue
        }
        cursor++
        while (cursor < arguments.length && arguments[cursor].isWhitespace()) cursor++
        if (cursor >= arguments.length) return null
        if (arguments[cursor] != '"') {
            searchFrom = keyStart + key.length
            continue
        }
        cursor++
        var lines = 1
        var escaped = false
        while (cursor < arguments.length) {
            val char = arguments[cursor++]
            if (escaped) {
                if (char == 'n') lines++
                escaped = false
            } else {
                when (char) {
                    '\\' -> escaped = true
                    '\n' -> lines++
                    '"' -> return lines
                }
            }
        }
        return lines
    }
    return null
}

/** 解析后的模型运行配置。 */
data class ModelConfig(
    val name: String,
    val provider: String,
    val model: String,
    val baseUrl: String,
    val apiKey: String?,
    /** 同一接口地址下参与轮询的 Key 池；为空时兼容使用 [apiKey]。 */
    val apiKeys: List<String> = emptyList(),
    /** 单 Key 每分钟请求上限；0 表示不限。 */
    val requestsPerMinutePerKey: Int = 0,
    /** 接入协议：OPENAI 兼容或 Anthropic Messages API。 */
    val protocol: ApiProtocol = ApiProtocol.OPENAI,
    /** 推理参数（null = 服务端默认）。 */
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val topP: Float? = null,
    /** 推理开关：AUTO = 跟随模型服务端默认。 */
    val reasoningMode: ReasoningMode = ReasoningMode.AUTO,
    /** 推理强度：null = 服务端默认。 */
    val reasoningEffort: ReasoningEffort? = null,
    /**
     * 工具调用模式：NATIVE = OpenAI 标准 function calling（注入 tools）；
     * JSON_TEXT = 工具列表写入系统提示词，模型用文本输出工具调用；
     * DISABLED = 禁用工具（纯聊天）。
     */
    val toolCallMode: ToolCallMode = ToolCallMode.NATIVE,
    val dynamicMcpTools: List<top.wkbin.tianxuan.core.model.McpToolInfo> = emptyList(),
    /** 上下文 Token 容量上限（如 128000，超出时滑动窗口压缩）。 */
    val contextTokens: Int? = null,
    /**
     * 每模型压缩预算覆盖（对齐 pi compaction.modelOverrides）：
     * 压缩触发时保留的最近 token 上限（null = 不启用该收紧）。
     */
    val compactionKeepRecentTokens: Int? = null,
    /** 为 LLM 响应预留的 token（null = 使用内置默认 8192）。 */
    val compactionReserveTokens: Int? = null,
    /** 自定义请求头（多行 Key: Value 格式）。 */
    val customHeaders: String = "",
    /** 纯净排查模式：不注入系统提示词与工具。 */
    val pureChatMode: Boolean = false,
    /** 是否支持视觉多模态直接发送图片。 */
    val visionEnabled: Boolean = true,
    /** 是否使用 OpenAI Responses API（true = POST /responses；false = /chat/completions）。 */
    val responseApiEnabled: Boolean = false,
    /**
     * Anthropic Prompt Caching：请求时注入 cache_control 断点
     * （System 末尾 / Tools 末尾 / 倒数第二轮真实 User 消息）。仅对原生 Anthropic
     * Messages API 生效；由 toModelConfig 按协议自动启用，避免给不支持的代理注入字段。
     */
    val promptCachingEnabled: Boolean = false,
    /** 是否使用 1 小时缓存 TTL（cache_control.ttl=1h + 扩展 beta 头）；默认 5 分钟。 */
    val promptCacheTtl1h: Boolean = false,
)

internal data class RequestedModelTarget(
    val profileId: String,
    val variant: String? = null,
)

internal fun selectRequestedModelTarget(
    profiles: List<top.wkbin.tianxuan.core.database.AiModelEntity>,
    selection: String,
): RequestedModelTarget? {
    val requested = selection.trim()
    if (requested.isBlank()) return null
    profiles.firstOrNull {
        it.id.equals(requested, ignoreCase = true) || it.name.equals(requested, ignoreCase = true)
    }?.let { return RequestedModelTarget(profileId = it.id) }
    profiles.forEach { entity ->
        val canonicalVariant = entity.model.split(',')
            .map { it.trim() }
            .firstOrNull { it.equals(requested, ignoreCase = true) }
        if (canonicalVariant != null) {
            return RequestedModelTarget(profileId = entity.id, variant = canonicalVariant)
        }
    }
    return null
}

/** LLM 接入协议：绝大多数厂商提供 OpenAI 兼容端点；Anthropic Claude 需要专用适配。 */
enum class ApiProtocol { OPENAI, ANTHROPIC }

/** 工具调用模式：NATIVE = 标准函数调用；JSON_TEXT = 文本 JSON 标记；DISABLED = 禁用。 */
enum class ToolCallMode { NATIVE, JSON_TEXT, DISABLED }

/**
 * 残渣级思考的长度上限（字符，trim 后）：正文与工具调用全空时，思考字段只剩低于该阈值的
 * 截断残渣（空壳 think、换行、半截标签）不算"Có nội dung"。真实推理是成段文字，起点远高于此；
 * 而放行这类残渣会落进"Không gọi công cụ → Complete"分支，把中转截断产生的空壳响应记成任务完成。
 */
internal const val BLANK_REASONING_RESIDUAL_CHARS = 16

/** null/空白，或 trim 后低于 [BLANK_REASONING_RESIDUAL_CHARS] 的残渣级思考，均视为"Không có nội dung suy nghĩ"。 */
internal fun String?.isNullOrResidualReasoning(): Boolean =
    isNullOrBlank() || trim().length < BLANK_REASONING_RESIDUAL_CHARS

/** LLM 返回的一轮结果：纯文本 或 一个/多个工具调用。 */
data class ChatResult(
    val content: String?,
    val toolCalls: List<ApiToolCallSpec>,
    /** 推理模型输出的思考内容（DeepSeek 等），多轮对话需原样传回 API。 */
    val reasoningContent: String? = null,
    /** Provider 报告的本轮 token 用量；未报告时全部为 0。 */
    val usage: ChatUsage = ChatUsage(),
    /**
     * 思考流确切耗时（毫秒）：首个 reasoning 增量到 reasoning 结束之间。
     * 由 ProviderClient.chatStream 统一测量，非推理模型或未观测到 reasoning 时为 null。
     */
    val reasoningMs: Long? = null,
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()

    /**
     * 空的一轮：没有正文、没有（有效）推理、也没有任何工具调用。
     * 判定覆盖两类形态：三者全空的"Phản hồi rỗng sạch sẽ"，以及正文/工具全空、思考只剩
     * 残渣级字符（[BLANK_REASONING_RESIDUAL_CHARS] 以下）的"Phản hồi gần rỗng"——后者是
     * 中转/网关在长上下文或不稳定时静默截断的典型产物，必须同样显式报错可重试，
     * 而不是被上层"Không gọi công cụ → Complete"记成任务完成。
     * 真正的推理-only 轮次（成段思考 + 无正文 + 无工具）仍不算空：那是模型自主收束的合法形态。
     */
    val isBlankResponse: Boolean
        get() = content.isNullOrBlank() &&
            reasoningContent.isNullOrResidualReasoning() &&
            toolCalls.isEmpty()
}

/**
 * 一次补全的 token 用量（OpenAI usage 与 Anthropic usage 的统一投影）。
 * OpenAI: prompt/completion_tokens + details(cached/reasoning)；
 * Anthropic: input/output_tokens + cache_read/cache_creation_input_tokens；
 * DeepSeek: prompt_cache_hit/miss_tokens 映射为 cacheRead/cacheWrite。
 */
data class ChatUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val reasoningTokens: Long = 0,
    val cacheReadTokens: Long = 0,
    val cacheWriteTokens: Long = 0,
) {
    val hasData: Boolean
        get() = inputTokens > 0 || outputTokens > 0 || reasoningTokens > 0 ||
            cacheReadTokens > 0 || cacheWriteTokens > 0
}

data class ApiToolCallSpec(
    val id: String,
    val name: String,
    val argumentsJson: String,
)

// ---------- OpenAI 兼容 chat/completions DTO ----------

@Serializable
data class ApiMessage(
    val role: String,
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ApiToolCall>? = null,
    val tool_call_id: String? = null,
    val imageUrls: List<String> = emptyList(),
)

@Serializable
data class ApiToolCall(
    val id: String,
    val type: String = "function",
    val function: ApiFunctionCall,
)

/**
 * Provider 出口的消息序列修复（最后一道防线）。
 *
 * OpenAI / DeepSeek / Anthropic 协议均要求：`role=tool` 的消息必须紧跟在
 * 包含对应 tool_call_id 的 `assistant(tool_calls)` 消息之后。中断恢复、
 * 结果跨用户边界、超长会话头部截断（branchTail）等异常历史可能破坏该约束，
 * 直接发送会被服务端 400 拒绝（DeepSeek 报
 * "Messages with role 'tool' must be a response to a preceding message with 'tool_calls'"）。
 *
 * 修复规则：
 * - 错位/孤立的 tool 结果转写为 user 文本（信息保留，协议合法）；
 * - assistant.tool_calls 后缺失的结果补占位 tool 消息，避免整轮请求被拒。
 */
internal fun sanitizeApiTranscript(messages: List<ApiMessage>): List<ApiMessage> {
    var awaitingResultIds = LinkedHashSet<String>()
    val out = mutableListOf<ApiMessage>()

    fun flushMissingResults() {
        awaitingResultIds.forEach { id ->
            out.add(
                ApiMessage(
                    role = "tool",
                    content = "(Thiếu kết quả công cụ: lịch sử gián đoạn hoặc bị cắt, nếu vẫn cần hãy phát lại lần gọi công cụ đó)",
                    tool_call_id = id,
                ),
            )
        }
        awaitingResultIds = LinkedHashSet()
    }

    for (message in messages) {
        when {
            message.role == "assistant" && !message.tool_calls.isNullOrEmpty() -> {
                flushMissingResults()
                out.add(message)
                awaitingResultIds = message.tool_calls.mapTo(LinkedHashSet()) { it.id }
            }
            message.role == "tool" -> {
                val id = message.tool_call_id.orEmpty()
                if (id.isNotEmpty() && id in awaitingResultIds) {
                    out.add(message)
                    awaitingResultIds.remove(id)
                } else {
                    // 紧邻的前一条不是包含该 tool_call_id 的 assistant(tool_calls)：转写为 user 文本
                    out.add(
                        ApiMessage(
                            role = "user",
                            content = "【Kết quả thực thi công cụ (thứ tự lịch sử bất thường, đã chuyển thành văn bản)】\n${message.content.orEmpty()}",
                        ),
                    )
                }
            }
            else -> {
                if (awaitingResultIds.isNotEmpty()) flushMissingResults()
                out.add(message)
            }
        }
    }
    flushMissingResults()
    return out
}

@Serializable
data class ApiFunctionCall(
    val name: String,
    val arguments: String,
)

@Serializable
data class ApiToolDefinition(
    val type: String = "function",
    val function: ApiFunctionDefinition,
)

@Serializable
data class ApiFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: kotlinx.serialization.json.JsonObject,
)

@Serializable
data class ChatCompletionResponse(
    val choices: List<ChatChoice> = emptyList(),
    val usage: ChatUsageResponse? = null,
)

/** OpenAI 兼容 usage 块（含 DeepSeek 缓存字段与 reasoning details）。 */
@Serializable
data class ChatUsageResponse(
    val prompt_tokens: Long? = null,
    val completion_tokens: Long? = null,
    val prompt_tokens_details: PromptTokensDetails? = null,
    val completion_tokens_details: CompletionTokensDetails? = null,
    val prompt_cache_hit_tokens: Long? = null,
    val prompt_cache_miss_tokens: Long? = null,
) {
    @Serializable
    data class PromptTokensDetails(val cached_tokens: Long? = null)

    @Serializable
    data class CompletionTokensDetails(val reasoning_tokens: Long? = null)

    fun toChatUsage(): ChatUsage = ChatUsage(
        inputTokens = prompt_tokens ?: 0,
        outputTokens = completion_tokens ?: 0,
        reasoningTokens = completion_tokens_details?.reasoning_tokens ?: 0,
        cacheReadTokens = prompt_tokens_details?.cached_tokens ?: prompt_cache_hit_tokens ?: 0,
        cacheWriteTokens = prompt_cache_miss_tokens ?: 0,
    )
}

@Serializable
data class ChatChoice(
    val message: ChatResponseMessage = ChatResponseMessage(),
)

@Serializable
data class ChatResponseMessage(
    val content: String? = null,
    val reasoning_content: String? = null,
    val tool_calls: List<ApiToolCall>? = null,
)

/**
 * 调用 LLM（OpenAI 兼容 chat/completions，支持 tools/tool_calls）。
 *
 * 模型配置优先取 [AiModelRepository] 中激活的 [top.wkbin.tianxuan.core.database.AiModelEntity]，
 * 未配置时回退到 [ProviderRepository]；API Key 始终从加密存储读取，绝不落库/落日志。
 */
class ProviderClient(
    private val okHttpClient: OkHttpClient,
    private val providerRepository: ProviderRepository,
    private val modelDao: AiModelRepository,
    private val mcpManager: top.wkbin.tianxuan.harness.mcp.McpManager,
    private val settingsDataStore: AgentPreferences,
    private val json: Json,
) {
    private val apiKeyScheduler = ApiKeyScheduler()
    // 非流式专用：保留 callTimeout 总超时（含响应体读取），防止慢端点永久挂起。
    private val httpClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    // 流式专用：callTimeout 计时覆盖整个 SSE 响应体读取，长生成（>5min）会被硬掐断、
    // 已流式内容全部丢弃。这里取消 callTimeout（0 = 不限制），长连接依靠
    // readTimeout（逐次 read 间隔超时，每个 SSE 事件都会重置）+ 首字看门狗兜底。
    // 连接池与拦截器通过 newBuilder() 与非流式 client 共享，无额外开销。
    private val streamHttpClient: OkHttpClient = okHttpClient.newBuilder()
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .readTimeout(READ_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    suspend fun resolveModel(): ModelConfig = withContext(Dispatchers.IO) {
        val active = modelDao.activeModel()
        val baseConfig = active?.toModelConfig(providerRepository)
            ?: ModelConfig(
                name = "Mặc định",
                provider = providerRepository.provider.first(),
                model = providerRepository.model.first().ifBlank { DEFAULT_MODEL },
                baseUrl = providerRepository.baseUrl.first().ifBlank { DEFAULT_BASE_URL },
                apiKey = providerRepository.readApiKey(),
            )
        // 主流模型自动适配：provider 的 /models 通常不返回 context window，
        // 这里在最终模型名（含 variant）确定后统一补全；显式 contextTokens 仍优先。
        baseConfig.withResolvedContextWindow().applyGlobalReasoningDepth()
    }

    /**
     * 同 [resolveModel]，但额外做最小配置校验。[modelId] 非空且存在时优先使用该会话绑定档案，
     * [modelVariant] 用于覆盖档案里的默认模型名，实现同一供应商档案下的会话级模型隔离。
     * 否则回退到当前激活模型。无可用模型且未设置 API Key 时直接抛出明确异常。
     */
    suspend fun resolveConfigured(modelId: String? = null, modelVariant: String? = null): ModelConfig = withContext(Dispatchers.IO) {
        val requested = modelId?.takeIf { it.isNotBlank() }?.let { modelDao.findById(it) }
        val active = requested ?: modelDao.activeModel()
        val providerKey = providerRepository.readApiKey().orEmpty()
        if (active == null && providerKey.isBlank()) {
            throw IllegalStateException("Chưa cấu hình model hoặc API Key, hãy thêm và kích hoạt một model trong 「Cài đặt → Model」 trước")
        }
        val baseConfig = if (active != null) {
            active.toModelConfig(providerRepository)
        } else {
            val provider = providerRepository.provider.first()
            val baseUrl = providerRepository.baseUrl.first().ifBlank { DEFAULT_BASE_URL }
            ModelConfig(
                name = "Mặc định",
                provider = provider,
                model = providerRepository.model.first().ifBlank { DEFAULT_MODEL },
                baseUrl = baseUrl,
                apiKey = providerKey.ifBlank { null },
                protocol = inferProtocol(baseUrl, provider),
            )
        }
        val sessionConfig = if (requested != null && !modelVariant.isNullOrBlank()) {
            baseConfig.copy(model = modelVariant.trim())
        } else {
            baseConfig
        }
        // 同 resolveModel：请求路径零 MCP 发现；按最终 variant 自动适配 context window。
        sessionConfig.withResolvedContextWindow().applyGlobalReasoningDepth()
    }

    /**
     * Resolve an explicit agent model selection without silently falling back to the active model.
     * The selection may be a saved profile id/name or one concrete model configured in a profile.
     */
    suspend fun resolveRequestedModel(
        selection: String?,
        inheritedProfileId: String? = null,
        inheritedVariant: String? = null,
    ): ModelConfig = withContext(Dispatchers.IO) {
        val requested = selection?.trim()?.takeIf { it.isNotBlank() && !it.equals("inherit", ignoreCase = true) }
            ?: return@withContext resolveConfigured(inheritedProfileId, inheritedVariant)
        val profiles = modelDao.observeAll().first()
        val target = selectRequestedModelTarget(profiles, requested) ?: throw IllegalArgumentException(
            "Không tìm thấy lựa chọn model “$requested”. Hãy truyền ID/tên hồ sơ model đã lưu, hoặc tên model cụ thể đã cấu hình trong hồ sơ.",
        )
        resolveConfigured(target.profileId, target.variant)
    }

    /** Resolve a persisted role binding strictly, including its concrete model variant. */
    suspend fun resolveSavedModelProfile(profileId: String, variant: String?): ModelConfig = withContext(Dispatchers.IO) {
        val profile = modelDao.findById(profileId) ?: throw IllegalArgumentException(
            "Hồ sơ model “$profileId” mà Agent con gắn kết đã không còn tồn tại, hãy chọn lại trong cài đặt vai trò Agent con.",
        )
        val requestedVariant = variant?.trim()?.takeIf { it.isNotBlank() }
        val canonicalVariant = requestedVariant?.let { requested ->
            profile.model.split(',').map { it.trim() }.firstOrNull { it.equals(requested, ignoreCase = true) }
                ?: throw IllegalArgumentException(
                    "Hồ sơ model \"${profile.name}\" không còn tồn tại model \"$requested\", hãy chọn lại trong cài đặt vai trò Agent con.",
                )
        }
        resolveConfigured(profile.id, canonicalVariant)
    }

    /**
     * 把「全局推理深度」设置应用到未显式配置的模型上。**只对该厂商实际支持的能力生效**：
     * - 先探测厂商能力（能否关闭 / 能否调强度），不支持的选项直接忽略，避免设置"Đã sửa nhưng không phản hồi"；
     * - 模型已显式关闭推理 -> 保持不动（用户意图优先）；
     * - 全局 disabled：仅当厂商 [ReasoningCapabilities.supportsDisable] 且模型 AUTO 时关闭推理；
     * - 全局 low/medium/high：仅当厂商 [ReasoningCapabilities.supportsEffort] 时按深度设置强度
     *   （模型 AUTO 则同时开启推理）；厂商不支持强度（如豆包）则保持 AUTO 跟随服务端默认；
     * - 全局 auto 或未知值 -> 不动。
     */
    private suspend fun ModelConfig.applyGlobalReasoningDepth(): ModelConfig {
        if (reasoningMode == ReasoningMode.DISABLED) return this
        val depth = settingsDataStore.defaultReasoningDepth.first()
        val caps = ReasoningAdapter.capabilities(this)
        return when (depth) {
            "disabled" ->
                if (reasoningMode == ReasoningMode.AUTO && caps.supportsDisable) {
                    copy(reasoningMode = ReasoningMode.DISABLED)
                } else {
                    this
                }
            "low", "medium", "high", "extreme", "max" -> {
                if (!caps.supportsEffort) return this // 不支持强度 -> 跟随服务端默认
                val effort = when (depth) {
                    "low" -> ReasoningEffort.LOW
                    "medium" -> ReasoningEffort.MEDIUM
                    "high" -> ReasoningEffort.HIGH
                    else -> ReasoningEffort.MAX
                }
                if (reasoningMode == ReasoningMode.AUTO) {
                    copy(reasoningMode = ReasoningMode.ENABLED, reasoningEffort = effort)
                } else {
                    copy(reasoningEffort = reasoningEffort ?: effort)
                }
            }
            else -> this
        }
    }

    suspend fun chat(model: ModelConfig, messages: List<ApiMessage>): ChatResult =
        executeWithRotatedApiKey(model, apiKeyScheduler) { selected ->
            val sanitized = sanitizeApiTranscript(messages)
            when {
                // 用户显式开启 Responses API 时优先走该协议（仅对 OpenAI 兼容端点有意义）
                selected.responseApiEnabled -> ResponsesApi(httpClient, json).chat(selected, sanitized)
                selected.protocol == ApiProtocol.ANTHROPIC -> AnthropicApi(httpClient, json).chat(selected, sanitized)
                else -> ChatApi(httpClient, json).chat(selected, sanitized)
            }
        }.also { result ->
            if (result.isBlankResponse) throw LlmEmptyResponseException(EMPTY_RESPONSE_MESSAGE)
        }

    /**
     * 流式调用：内容增量通过 [onDelta] 实时回调，推理增量通过 [onReasoning] 实时回调。
     *
     * 思考耗时在此统一测量（三种协议同一口径）：首个 reasoning 增量开始计时，
     * 首个正文增量（或流正常结束）停止计时，结果写入 [ChatResult.reasoningMs]。
     */
    suspend fun chatStream(
        model: ModelConfig,
        messages: List<ApiMessage>,
        onReasoning: (String) -> Unit = {},
        onToolProgress: (ToolCallStreamProgress) -> Unit = {},
        onDelta: (String) -> Unit,
    ): ChatResult {
        val timing = ReasoningTimingTracker()
        val timedReasoning: (String) -> Unit = { chunk ->
            timing.onReasoningChunk()
            onReasoning(chunk)
        }
        val timedDelta: (String) -> Unit = { chunk ->
            timing.onContentChunk()
            onDelta(chunk)
        }
        val result = executeWithRotatedApiKey(model, apiKeyScheduler) { selected ->
            val sanitized = sanitizeApiTranscript(messages)
            when {
                selected.responseApiEnabled -> ResponsesApi(streamHttpClient, json).chatStream(
                    selected,
                    sanitized,
                    timedReasoning,
                    onToolProgress,
                    timedDelta,
                )
                selected.protocol == ApiProtocol.ANTHROPIC -> AnthropicApi(streamHttpClient, json).chatStream(
                    selected,
                    sanitized,
                    timedReasoning,
                    onToolProgress,
                    timedDelta,
                )
                else -> ChatApi(streamHttpClient, json).chatStream(
                    selected,
                    sanitized,
                    timedReasoning,
                    onToolProgress,
                    timedDelta,
                )
            }
        }
        if (result.isBlankResponse) throw LlmEmptyResponseException(EMPTY_RESPONSE_MESSAGE)
        val reasoningMs = timing.finish()
        return if (reasoningMs != null) result.copy(reasoningMs = reasoningMs) else result
    }

    /**
     * Fallback metadata for mainstream models whose provider /models response does not
     * expose the context window. Explicit profile values are never overwritten.
     */
    private fun ModelConfig.withResolvedContextWindow(): ModelConfig {
        if (contextTokens != null) return this
        val inferred = ModelContextWindows.resolve(model, provider) ?: return this
        return copy(contextTokens = inferred)
    }


    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
        // 非流式 chat() 的总超时（含响应体读取）；须覆盖最大首字看门狗（240s），
        // 否则超大上下文 Prefill 会先被 callTimeout 掐断。
        // 流式路径已改用无 callTimeout 的 streamHttpClient（见类成员注释）。
        private const val CALL_TIMEOUT_MS = 5 * 60 * 1000L

        /** 大请求若迟迟没有任何合法 SSE 事件，应尽早失败并向 UI 暴露重试，而不是静默等满 callTimeout。 */
        internal const val FIRST_STREAM_EVENT_TIMEOUT_MS = 90_000L
        internal const val FIRST_EVENT_WAITING = 0
        internal const val FIRST_EVENT_RECEIVED = 1
        internal const val FIRST_EVENT_TIMED_OUT = 2

        /** 按预估输入规模放宽首字看门狗：超大上下文 Prefill 常超过默认 90s。 */
        internal fun resolveFirstEventTimeoutMs(estimatedTokens: Int): Long = when {
            estimatedTokens > 80_000 -> 240_000L
            estimatedTokens > 40_000 -> 150_000L
            else -> FIRST_STREAM_EVENT_TIMEOUT_MS
        }

        internal fun estimateApiMessageTokens(messages: List<ApiMessage>): Int =
            ContextWindowPolicy.estimateApiMessages(messages)

        /**
         * 单回合推理内容的累积上限（字符）。推理是执行过程草稿，不是长期上下文；
         * 超限后停止累积，防止异常模型的无界推理把会话内存与历史存储拖垮。
         */
        const val MAX_STREAM_REASONING_CHARS = 128 * 1024

        /** 流式增量上屏的发布间隔：SSE chunk 频率远高于帧率，逐 chunk 全量发布是 O(n²) 分配。 */
        const val STREAM_PUBLISH_INTERVAL_MS = 100L

        /**
         * 空响应的用户可见文案（唯一来源：流式层报错与 turn 层兜底共用同一句）。
         * 覆盖两类形态：三者全空，以及正文/工具全空、思考只剩截断残渣。
         * 只描述可观察现象与可执行动作，不猜测具体厂商原因。
         */
        internal const val EMPTY_RESPONSE_MESSAGE =
            "Model trả về phản hồi rỗng (HTTP 200, phần thân và lời gọi công cụ đều rỗng, hoặc trường tư duy chỉ còn phần cặn bị cắt cụt vô nghĩa)." +
                "Nguyên nhân thường gặp là tên model đó không khả dụng ở phía server, bị gateway/kiểm soát rủi ro chặn, ngữ cảnh dài bị trung chuyển cắt cụt, hoặc thượng nguồn tạm thời bất thường." +
                "Hãy kiểm tra cấu hình model, khi cần nén context hoặc chuyển model khác rồi thử lại."

        /** 空响应诊断时缓冲的原始响应首部上限（字符）：只用于报错与兜底判定，不参与正文累积。 */
        internal const val RESPONSE_HEAD_CAPTURE_CHARS = 2_000

        /** 错误文案里展示的响应首部长度。 */
        private const val RESPONSE_HEAD_DISPLAY_CHARS = 160

        /**
         * 把原始响应首部压成单行诊断片段：转义换行、截断。
         * 目的是让"Thượng nguồn rốt cuộc trả về gì"能从错误文案里直接读到，而不是只有一句"Phản hồi rỗng"。
         */
        internal fun describeResponseHead(raw: String): String {
            val compact = raw.replace('\n', ' ').replace('\r', ' ').trim()
            if (compact.isEmpty()) return ""
            val clipped = if (compact.length > RESPONSE_HEAD_DISPLAY_CHARS) {
                compact.take(RESPONSE_HEAD_DISPLAY_CHARS) + "…"
            } else {
                compact
            }
            return " Phần đầu phản hồi gốc: $clipped"
        }

        /** Room 实体 → 运行配置：推理参数原样透传，协议按 Base URL / 厂商名自动推断。 */
        private suspend fun top.wkbin.tianxuan.core.database.AiModelEntity.toModelConfig(
            providerRepository: top.wkbin.tianxuan.core.tools.ProviderRepository,
        ): ModelConfig {
            val baseUrl = this.baseUrl.ifBlank { DEFAULT_BASE_URL }
            val resolvedProtocol = inferProtocol(baseUrl, provider)
            val modelKeys = providerRepository.readModelApiKeys(secretRef)
            val fallbackKey = providerRepository.readApiKey().orEmpty().ifBlank { null }
            val effectiveKeys = modelKeys.ifEmpty { listOfNotNull(fallbackKey) }
            return ModelConfig(
                name = name,
                provider = provider,
                model = model.split(",").firstOrNull()?.trim().takeUnless { it.isNullOrBlank() } ?: model.trim(),
                baseUrl = baseUrl,
                apiKey = effectiveKeys.firstOrNull(),
                apiKeys = effectiveKeys,
                requestsPerMinutePerKey = requestsPerMinutePerKey.coerceAtLeast(0),
                protocol = resolvedProtocol,
                temperature = temperature,
                maxTokens = maxTokens,
                topP = topP,
                reasoningMode = when (reasoningMode?.lowercase()) {
                    "disabled" -> ReasoningMode.DISABLED
                    "enabled" -> ReasoningMode.ENABLED
                    else -> ReasoningMode.AUTO
                },
                reasoningEffort = when (reasoningEffort?.lowercase()) {
                    "low" -> ReasoningEffort.LOW
                    "medium" -> ReasoningEffort.MEDIUM
                    "high" -> ReasoningEffort.HIGH
                    "extreme", "max" -> ReasoningEffort.MAX
                    else -> null
                },
                toolCallMode = when (toolCallMode?.lowercase()) {
                    "json" -> ToolCallMode.JSON_TEXT
                    "disabled" -> ToolCallMode.DISABLED
                    else -> ToolCallMode.NATIVE
                },
                contextTokens = contextTokens,
                compactionKeepRecentTokens = compactionKeepRecentTokens,
                compactionReserveTokens = compactionReserveTokens,
                customHeaders = customHeaders,
                pureChatMode = pureChatMode,
                visionEnabled = visionEnabled,
                responseApiEnabled = responseApiEnabled,
                promptCachingEnabled = promptCachingEnabled && resolvedProtocol == ApiProtocol.ANTHROPIC,
                promptCacheTtl1h = promptCacheTtl1h,
            )
        }

        /** 解析多行自定义请求头（格式为 Key: Value，支持忽略空行与 # 注释） */
        fun parseCustomHeaders(raw: String): List<Pair<String, String>> {
            if (raw.isBlank()) return emptyList()
            return raw.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { line ->
                    val colonIndex = line.indexOf(':')
                    if (colonIndex > 0) {
                        val name = line.substring(0, colonIndex).trim()
                        val value = line.substring(colonIndex + 1).trim()
                        if (name.isNotEmpty() && value.isNotEmpty()) name to value else null
                    } else null
                }
                .toList()
        }

        /** Anthropic 协议自动识别：官方域名或厂商名含 anthropic/claude。 */
        fun inferProtocol(baseUrl: String, provider: String): ApiProtocol {
            val host = runCatching { java.net.URI(baseUrl.trim()).host?.lowercase() }.getOrNull().orEmpty()
            val providerLower = provider.lowercase()
            return if (host == "api.anthropic.com" ||
                providerLower.contains("anthropic") ||
                providerLower.contains("claude")
            ) {
                ApiProtocol.ANTHROPIC
            } else {
                ApiProtocol.OPENAI
            }
        }
        /**
         * 检测响应体是否不像 JSON —— 用于在 [formatHttpErrorMessage] /
         * ChatApi / AnthropicApi 的解析前短路，避免把 HTML / 纯文本 /
         * BOM 头部的内容丢给 kotlinx.serialization 抛出一串反混淆栈。
         *
         * - UTF-8 BOM (U+FEFF) 不被 Kotlin 的 [String.trimStart] 当作空白，
         *   但确实会让 JSON 解析器从 offset 1 开始而误判；这里先剔除。
         * - "<!doctype html"、"<html"、纯文本错误页（Cloudflare / Nginx /
         *   代理登录页）是最常见的"Giả JSON"响应。
         */
        internal fun looksLikeJsonResponse(body: String): Boolean {
            val stripped = if (body.isNotEmpty() && body[0] == '\uFEFF') body.substring(1) else body
            val prefix = stripped.trimStart().take(64).lowercase()
            if (prefix.isEmpty()) return false
            return prefix.startsWith("{") || prefix.startsWith("[")
        }

        fun formatHttpErrorMessage(code: Int, rawBody: String): String {
            val trimmedBody = if (rawBody.isNotEmpty() && rawBody[0] == '\uFEFF') rawBody.substring(1) else rawBody
            // 远端把错误页（HTML/纯文本）当成 body 返回时，给出可读的固定文案，
            // 避免直接把 <html>... 拼到错误提示里刷屏。
            if (!looksLikeJsonResponse(trimmedBody)) {
                if (code == 413) {
                    return "Thân yêu cầu quá lớn (HTTP 413): reverse proxy hoặc gateway đã từ chối yêu cầu này (thường gặp với Nginx client_max_body_size). Engine sẽ thử nén lịch sử rồi thử lại."
                }
                val kind = when {
                    trimmedBody.trimStart().startsWith("<", ignoreCase = true) -> "Trang web"
                    else -> "Văn bản không phải JSON"
                }
                return "Yêu cầu LLM thất bại (HTTP $code): đầu xa trả về $kind, có thể là trang đăng nhập reverse proxy, chặn CDN hoặc lỗi định tuyến Base URL. Hãy kiểm tra Base URL và mạng."
            }
            val errorMsg = runCatching {
                val obj = Json.parseToJsonElement(trimmedBody) as? JsonObject
                val err = obj?.get("error") as? JsonObject
                err?.get("message")?.let { it as? JsonPrimitive }?.contentOrNull
            }.getOrNull()?.trim() ?: trimmedBody.take(300).trim()

            val lowerMsg = errorMsg.lowercase()
            return when {
                code == 413 ->
                    "Thân yêu cầu quá lớn (HTTP 413): reverse proxy hoặc gateway đã từ chối yêu cầu này (thường gặp với Nginx client_max_body_size). Engine sẽ thử nén lịch sử rồi thử lại."
                code == 403 && (lowerMsg.contains("free quota") || lowerMsg.contains("quota exhausted") || lowerMsg.contains("free tier")) ->
                    "Hạn mức miễn phí API đã cạn (HTTP 403): vui lòng vào bảng điều khiển nhà cung cấp dịch vụ model để nạp tiền, tắt giới hạn tầng miễn phí, hoặc chuyển sang model khả dụng khác trong Thiên Huyền."
                code == 401 || lowerMsg.contains("invalid api key") || lowerMsg.contains("unauthorized") ->
                    "API Key không hợp lệ hoặc chưa được cấp quyền (HTTP 401): hãy kiểm tra và cập nhật API Key của nhà cung cấp đó trong cài đặt model."
                // 402 与直述"Số dư/hạn mức"的文案：不少厂商（DeepSeek、多数中转站）用 402 而非 429
                // 回报余额耗尽。原实现会把它并入泛化 4xx（IllegalStateException），既不重试也不给
                // 充值引导，用户侧表现为"Mỗi lần gửi đều thất bại tức thì mà không rõ nguyên nhân"。
                code == 402 || lowerMsg.contains("insufficient balance") ||
                    lowerMsg.contains("余额") || lowerMsg.contains("额度已用尽") ->
                    "Số dư/hạn mức API đã cạn (HTTP $code): hãy vào bảng điều khiển nhà cung cấp model để nạp thêm, hoặc chuyển sang model khả dụng khác trong Thiên Huyền. $errorMsg"
                code == 429 || lowerMsg.contains("rate limit") || lowerMsg.contains("insufficient_quota") || lowerMsg.contains("quota") ->
                    "Hạn mức API đã cạn hoặc tần suất yêu cầu vượt giới hạn (HTTP $code): $errorMsg"
                code == 404 ->
                    "Tên model hoặc địa chỉ API không tồn tại (HTTP 404): vui lòng kiểm tra tên model có viết đúng chính tả không."
                errorMsg.isNotBlank() ->
                    "Yêu cầu LLM thất bại (HTTP $code): $errorMsg"
                else ->
                    "Yêu cầu LLM thất bại (HTTP $code)"
            }
        }

        internal fun rateLimitException(code: Int, rawBody: String, retryAfter: String?): LlmRateLimitException {
            val message = formatHttpErrorMessage(code, rawBody)
            val lower = message.lowercase()
            val quotaExhausted = listOf(
                "insufficient_quota",
                "quota exceeded",
                "allocated quota",
                "quota exhausted",
                "resource_exhausted",
                "余额",
                "额度已用尽",
            ).any { it in lower }
            val retrySeconds = retryAfter?.trim()?.toLongOrNull()?.coerceIn(1L, 300L)
                ?: runCatching {
                    ZonedDateTime.parse(retryAfter?.trim().orEmpty(), DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toEpochSecond() - System.currentTimeMillis() / 1000L
                }.getOrNull()?.takeIf { it > 0 }?.coerceAtMost(300L)
            return LlmRateLimitException(message, retrySeconds, quotaExhausted)
        }

        /** 5xx 上游临时故障（如 Cloudflare 524 origin timeout）：包装为可退避重试的 IOException。 */
        internal fun transientHttpException(code: Int, rawBody: String, retryAfter: String?): TransientHttpException {
            val message = formatHttpErrorMessage(code, rawBody)
            val retrySeconds = retryAfter?.trim()?.toLongOrNull()?.coerceIn(1L, 300L)
            return TransientHttpException(message, code, retrySeconds)
        }

        /**
         * 上下文超限识别（对齐 opencode session/retry 的 RETRYABLE 思路，但走压缩恢复而非重试）：
         * HTTP 413 一律视为可恢复的荷载超限（Nginx/中转 HTML 页往往不含 token 文案）；
         * 其余 4xx 命中 [CONTEXT_OVERFLOW_PATTERNS] 时返回 [LlmContextOverflowException]，
         * 否则仍是 [IllegalStateException]。
         * 匹配对 rawBody 原文做小写包含——各家 400 文案不同，宁滥勿缺：误报的代价只是
         * 多一次机械压缩尝试（有界），漏报则用户直接看到失败。
         */
        internal fun contextOverflowException(code: Int, rawBody: String): Exception =
            when {
                code == 413 -> LlmContextOverflowException(formatHttpErrorMessage(code, rawBody))
                isContextOverflowMessage(rawBody) -> LlmContextOverflowException(formatHttpErrorMessage(code, rawBody))
                isInvalidOutputTokensMessage(rawBody) -> LlmInvalidOutputTokensException(formatHttpErrorMessage(code, rawBody))
                else -> IllegalStateException(formatHttpErrorMessage(code, rawBody))
            }

        internal fun isInvalidOutputTokensMessage(rawBody: String): Boolean {
            val lower = rawBody.lowercase()
            val names = listOf("max_tokens", "max_output_tokens", "output tokens", "output_token")
            val invalid = listOf("invalid", "must be", "cannot", "should be", "greater than", "positive", "at least", "too large")
            return names.any { it in lower } && invalid.any { it in lower } &&
                !isContextOverflowMessage(rawBody)
        }

        internal fun isContextOverflowMessage(rawBody: String): Boolean {
            val lower = rawBody.lowercase()
            return CONTEXT_OVERFLOW_PATTERNS.any { it in lower }
        }

        private val CONTEXT_OVERFLOW_PATTERNS = listOf(
            // OpenAI 家族："This model's maximum context length is ... tokens"
            "maximum context length",
            "context length",
            "context_length",
            // Anthropic："prompt is too long: N tokens > M maximum"
            "prompt is too long",
            "exceed context limit",
            "context window",
            // Gemini："input token count ... exceeds the maximum number of tokens allowed"
            "exceeds the maximum number of tokens",
            "input token count",
            "too many input tokens",
            // 通用
            "reduce the length",
            "too many tokens",
            "input is too long",
            "context token limit",
            "request entity too large",
            "payload too large",
            "Context vượt giới hạn",
            "Vượt quá context",
            "Độ dài ngữ cảnh",
        )

        internal const val READ_TIMEOUT_MS = 5 * 60 * 1000L
        internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        /** 工具 JSON Schema，与 ToolExecutor 的参数契约一一对应。 */
        val TOOLS: List<ApiToolDefinition> = listOf(
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "kb_search",
                    description = "Truy xuất đoạn tài liệu liên quan tới truy vấn trong kho tri thức RAG (truy xuất vector ngữ nghĩa, khi nhúng thất bại thì lùi về khớp từ khóa). query là câu hỏi ngôn ngữ tự nhiên hoặc từ khóa; topK mặc định 3 tối đa 10. Chỉ đọc.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"query":{"type":"string","description":"检索问题或关键词"},"top_k":{"type":"integer","minimum":1,"maximum":10,"description":"最多返回片段数，默认 3"}},"required":["query"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "kb_add",
                    description = "Ghi một đoạn văn bản tri thức vào kho tri thức RAG (tự động phân khối và nhúng vector, khi nhúng thất bại giữ lại dạng truy xuất từ khóa). name là tên tài liệu (dùng để tham chiếu thủ công @kb:name); content là văn bản đầy đủ. Sau khi ghi không thể xóa trực tiếp (dùng kb_list để xác nhận tồn tại).",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"name":{"type":"string","description":"文档名（用于手动 @kb:name 引用）"},"content":{"type":"string","description":"完整知识文本内容"}},"required":["name","content"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "kb_list",
                    description = "Liệt kê tất cả tài liệu trong kho tri thức RAG (tên, nguồn, số phân khối, trạng thái nhúng). Dùng để xác nhận tài liệu đã vào kho chưa. Chỉ đọc.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{}}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "build_script",
                    description = "Quản lý script build Tri Phuong và gắn vào dự án. Khi phụ thuộc cũ/mới không tương thích, trước tiên kiểm tra cấu hình Gradle/Flutter của dự án, rồi create script và bind dự án hiện tại. Giao diện script: tham số thứ 1 là thư mục dự án; tham số thứ 2 của Android là Gradle task; tham số thứ 2 của Flutter là tham số build đầy đủ. Hỗ trợ list/get/create/update/delete/bind/unbind.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"action":{"type":"string","enum":["list","get","create","update","delete","bind","unbind"]},"id":{"type":"string","description":"脚本 ID；get/update/delete/bind 必需"},"name":{"type":"string","description":"脚本名称"},"description":{"type":"string","description":"适用依赖版本与用途"},"project_type":{"type":"string","enum":["android","flutter"]},"content":{"type":"string","description":"完整 POSIX shell 脚本，最大 200 KB"},"project":{"type":"string","description":"项目名称；省略时使用当前工作区"}},"required":["action"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "history_search",
                    description = "Tìm kiếm tin nhắn cũ theo từ khóa trong toàn bộ lịch sử của phiên hiện tại. index trong kết quả là chỉ mục tin nhắn bắt đầu từ 0 của nhánh đang hoạt động, bao gồm cả tin nhắn công cụ, có thể truyền trực tiếp cho history_read; message_id ổn định hơn. Chỉ đọc, không sửa lịch sử.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"query":{"type":"string","description":"要检索的关键词、文件名、错误信息或约束"},"limit":{"type":"integer","minimum":1,"maximum":20,"description":"最多返回命中条数，默认 8"}},"required":["query"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "history_read",
                    description = "Đọc nguyên văn một tin nhắn lịch sử của phiên hiện tại. Phải cung cấp message_id do history_search trả về hoặc index bắt đầu từ 0 của nhánh đang hoạt động (bao gồm cả tin nhắn công cụ), ít nhất một trong hai; khi cung cấp cả hai thì ưu tiên dùng message_id. Mỗi lần trả về có giới hạn kích thước. Chỉ đọc.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"message_id":{"type":"string","description":"history_search 返回的消息 ID；与 index 至少提供一个"},"index":{"type":"integer","minimum":0,"description":"历史消息的 0 起始索引；与 message_id 至少提供一个"}}}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "read",
                    description = "Đọc nội dung tệp (UTF-8, giới hạn 1MB mỗi tệp). Đường dẫn có thể là tương đối hoặc bắt đầu bằng /workspace/. Ưu tiên dùng nó để kiểm tra nội dung tệp thay vì cat/sed. Tệp lớn thì dùng offset (số dòng bắt đầu từ 1) và limit (số dòng) để đọc phân trang, phần đầu trả về sẽ ghi rõ tổng số dòng và cửa sổ hiện tại. Nếu tệp không tồn tại hoặc đọc thất bại, dùng ls/find của base để định vị.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"path":{"type":"string","description":"文件路径"},"offset":{"type":"integer","description":"起始行号（1 起始），可选"},"limit":{"type":"integer","description":"读取的最大行数，可选"}},"required":["path"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "write",
                    description = "Tạo hoặc ghi đè hoàn toàn nội dung tệp, tự động tạo thư mục cha. Chỉ dùng cho tệp mới hoặc ghi lại toàn bộ; nếu chỉ muốn sửa cục bộ, hãy chuyển dùng edit.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"}},"required":["path","content"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "edit",
                    description = "Thực hiện thay thế văn bản chính xác trong tệp. oldText phải khớp từng chữ với bản gốc và duy nhất, một lần có thể truyền nhiều thay thế, nhưng mỗi cái không được chồng chéo hoặc lồng nhau. oldText trùng lặp hoặc khớp nhiều chỗ sẽ thất bại—hãy read xác nhận nội dung trước khi sửa.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"path":{"type":"string"},"oldText":{"type":"string"},"newText":{"type":"string"}},"required":["path","oldText","newText"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "base",
                    description = "Thực thi lệnh shell foreground trong sandbox Debian Linux, trả về exit code/stdout/stderr. Dùng để cài phần mềm, chạy script, kiểm tra trạng thái và thực thi build. Timeout mặc định do người dùng cấu hình trong cài đặt Agent; có thể dùng timeout_seconds để chỉ định 1-900 giây cho một lần gọi. Dịch vụ thường trú không dùng nohup hoặc &, hãy chuyển sang dùng công cụ process.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"command":{"type":"string","description":"要执行的 shell 命令"},"cwd":{"type":"string","description":"工作目录；关联工作区时默认使用工作区，否则为 /root"},"timeout_seconds":{"type":"integer","minimum":1,"maximum":900,"description":"可选的单次超时秒数；省略时使用用户设置的默认值"}},"required":["command"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "process",
                    description = "Dùng trình tải HTTPS tích hợp để lưu tệp từ xa vào workspace. Hỗ trợ tiếp tục tải HTTP Range, tự động thử lại, giới hạn kích thước tệp tối đa và kiểm tra SHA-256 tuỳ chọn; hiện tại là tiếp tục tải một kết nối, không phải phân mảnh đa luồng. destination phải nằm trong workspace hiện tại, không điền đường dẫn tuyệt đối của máy chủ.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"action":{"type":"string","enum":["start","status","logs","list","stop"]},"id":{"type":"string","pattern":"^[a-z0-9][a-z0-9._-]{0,63}$","description":"稳定的进程标识；list 不需要"},"command":{"type":"string","description":"start 时必需，需以前台模式持续运行"},"cwd":{"type":"string","description":"start 的工作目录"},"tail_lines":{"type":"integer","minimum":1,"maximum":500,"description":"logs 返回的末尾行数，默认 120"}},"required":["action"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "host",
                    description = "Thực thi cài đặt hệ thống, quản lý ứng dụng, Logcat hoặc tự động hóa GUI màn hình ở phía máy chủ Android. Thu thập log (logcat) ưu tiên dùng ADB không dây tích hợp (không cần cấp quyền Shizuku/Root, hỗ trợ tùy chọn chỉ định port), các thao tác đặc quyền còn lại cần Shizuku hoặc Root. Nguyên thủy GUI (screen_click/double_click/long_press/swipe/scroll/input_text/key) đi qua HostGuiToolkit: thao tác toàn cục trợ năng → cmd input → bin input tự động hạ cấp; nhập tiếng Trung đi qua dán clipboard. Dòng màn hình ảo (virtual_screen_*) khởi chạy cách ly và điều khiển ứng dụng bên thứ ba trên một màn hình ảo Shower độc lập (cần Shizuku hoặc Root), hoàn toàn không ảnh hưởng màn hình chính: trước tiên virtual_screen_ensure tạo màn hình, rồi virtual_screen_launch khởi chạy ứng dụng mục tiêu, dùng virtual_screen_screenshot chụp ảnh nhận diện rồi thao tác theo hệ tọa độ màn hình ảo, virtual_screen_show/hide có thể hiển thị/ẩn cửa sổ nổi video trực tiếp của màn hình ảo đó cho người dùng (có thể can thiệp chạm thủ công), sau khi xong virtual_screen_close giải phóng.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"action":{"type":"string","enum":["status","exec","settings_get","settings_put","package_list","package_disable","package_enable","package_uninstall_user","app_list","app_freeze","app_unfreeze","app_grant_permission","logcat","device_status","screen_observe","screen_click","screen_double_click","screen_long_press","screen_swipe","screen_scroll","screen_input_text","paste_text","screen_key","app_launch","screen_capture","virtual_screen_ensure","virtual_screen_launch","virtual_screen_screenshot","virtual_screen_click","virtual_screen_double_click","virtual_screen_long_press","virtual_screen_swipe","virtual_screen_scroll","virtual_screen_key","virtual_screen_close","virtual_screen_show","virtual_screen_hide"]},"command":{"type":"string","description":"仅 exec 使用的原始宿主命令"},"namespace":{"type":"string","enum":["system","secure","global"],"description":"settings_get/settings_put 的设置命名空间"},"key":{"type":"string","description":"系统设置键名，或 screen_key 的按键名(back/home/recents/enter/delete/paste/power)"},"value":{"type":"string","description":"settings_put 的值"},"text":{"type":"string","description":"screen_input_text/paste_text 要粘贴的文本"},"x":{"type":"integer","description":"screen_click/double_click/long_press 的点击 X 坐标"},"y":{"type":"integer","description":"screen_click/double_click/long_press 的点击 Y 坐标"},"x1":{"type":"integer","description":"screen_swipe 起点 X 坐标"},"y1":{"type":"integer","description":"screen_swipe 起点 Y 坐标"},"x2":{"type":"integer","description":"screen_swipe 终点 X 坐标"},"y2":{"type":"integer","description":"screen_swipe 终点 Y 坐标"},"duration_ms":{"type":"integer","description":"swipe/long_press/scroll 持续时间毫秒"},"direction":{"type":"string","enum":["up","down","left","right"],"description":"screen_scroll 方向"},"distance_ratio":{"type":"number","description":"screen_scroll 幅度 0.15-0.8"},"package":{"type":"string","description":"应用操作或 logcat PID 过滤的 Android 包名（如 com.tencent.mm）"},"path":{"type":"string","description":"screen_capture 保存截图的目标绝对路径"},"permission":{"type":"string","description":"app_grant_permission 的 Android 权限名"},"query":{"type":"string","description":"app_list 的包名或应用名搜索词"},"include_system":{"type":"boolean","description":"app_list 是否显示系统应用，默认 false"},"limit":{"type":"integer","minimum":1,"maximum":200,"description":"app_list 返回数量，默认 50"},"user":{"type":"integer","minimum":0,"maximum":999,"description":"Android 用户 ID，默认 0"},"filter":{"type":"string","description":"package_list 的可选字面量过滤词"},"tail_lines":{"type":"integer","minimum":1,"maximum":2000,"description":"logcat 返回行数，默认 200"},"tag":{"type":"string","description":"logcat 的可选 tag"},"priority":{"type":"string","enum":["V","D","I","W","E","F"],"description":"logcat 最低优先级，默认 V"},"keyword":{"type":"string","description":"logcat 可选关键词（忽略大小写）"},"port":{"type":"integer","minimum":1,"maximum":65535,"description":"无线 ADB 端口（如 12345），logcat 时可选显式指定"},"session":{"type":"string","description":"virtual_screen_* 使用的会话 ID，默认 default；不同会话对应相互独立的虚拟屏，可并行操控多个应用"}},"required":["action"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "download",
                    description = "Dùng trình tải HTTPS tích hợp để lưu tệp từ xa vào workspace. Hỗ trợ tiếp tục tải HTTP Range, tự động thử lại, giới hạn kích thước tệp tối đa và kiểm tra SHA-256 tuỳ chọn; hiện tại là tiếp tục tải một kết nối, không phải phân mảnh đa luồng. destination phải nằm trong workspace hiện tại, không điền đường dẫn tuyệt đối của máy chủ.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"url":{"type":"string","description":"HTTPS 下载地址"},"destination":{"type":"string","description":"工作区内目标路径，例如 dist/tool.tar.gz"},"sha256":{"type":"string","description":"可选 SHA-256 十六进制摘要"},"max_attempts":{"type":"integer","description":"可选最大尝试次数，1-10，默认 3"},"max_bytes":{"type":"integer","description":"可选最大文件大小（字节），默认 1 GiB，最大 4 GiB"}},"required":["url","destination"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "memory",
                    description = "Quản lý bộ nhớ ngữ nghĩa và sự kiện dài hạn: ghi bền vững sở thích lâu dài, quy chuẩn kiến trúc dự án, sự kiện ổn định của người dùng. Hỗ trợ action: save, query, list, delete. scope: global, project, session. kind: preference, rule, fact, project_info.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"action":{"type":"string","enum":["save","query","list","delete"],"description":"操作动作"},"key":{"type":"string","description":"记忆键名"},"value":{"type":"string","description":"记忆内容（save 必需）"},"kind":{"type":"string","enum":["preference","rule","fact","project_info"],"description":"记忆类型"},"scope":{"type":"string","enum":["global","project","session"],"description":"记忆作用域"}},"required":["action"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "plan",
                    description = "Quản lý lập kế hoạch nhiệm vụ đa bước có cấu trúc: tách nhỏ các bước con của nhiệm vụ dài và liên tục theo dõi tiến độ. Khi nhiệm vụ dự kiến cần hơn 3 lần gọi công cụ, tồn tại nhiều giai đoạn thực thi phụ thuộc lẫn nhau, sau thất bại cần phân nhánh điều tra, hoặc sẽ sửa nhiều tệp/trạng thái hệ thống, thì lần gọi công cụ đầu tiên hãy replace_active để lập kế hoạch, mỗi bước hoàn thành thì advance; nhiệm vụ đơn bước hoặc hai bước đơn giản thì đừng tạo plan. Quy tắc chi tiết xem khối quy tắc workflow (khi chưa được chèn có thể dùng load_rule để lấy). Hỗ trợ action: replace_active, get_active, advance, clear_active.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"action":{"type":"string","enum":["replace_active","get_active","advance","clear_active"],"description":"规划操作动作"},"goal":{"type":"string","description":"任务总体目标"},"steps":{"type":"array","description":"规划步骤列表（每个步骤包含 id, title, status: pending|in_progress|completed|failed）","items":{"type":"object","properties":{"id":{"type":"string"},"title":{"type":"string"},"status":{"type":"string"}},"required":["id","title","status"]}},"status":{"type":"string","enum":["active","completed","cancelled"],"description":"可选：计划整体生命周期状态（仅限 active/completed/cancelled，步骤进度请写在 steps[].status）"}},"required":["action"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "scratchpad",
                    description = "Ghi chú nháp công việc cục bộ của nhiệm vụ/phiên: ghi tạm giả thuyết điều tra, bản nháp phân tích, mục tiêu con hiện tại và điểm chặn (Blockers). Hỗ trợ action: save, get, list, delete, clear.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"action":{"type":"string","enum":["save","get","list","delete","clear"],"description":"草稿操作动作"},"key":{"type":"string","description":"草稿键名"},"value":{"type":"string","description":"草稿内容（save 必需）"}},"required":["action"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "invoke_subagent",
                    description = "Phân giải vai trò từ chỉ mục cục bộ theo bộ phận R&D và từ khóa chuyên môn ngắn gọn, rồi phái đi Agent con cách ly song song. Khi người dùng liệt kê nhiều nhiệm vụ con độc lập, phải gửi đầy đủ trong mảng subagents của cùng một lần gọi, cấm phái từng cái một; kết quả giữ nguyên thứ tự mảng. writePaths=[] nghĩa là chỉ đọc song song, đường dẫn chính xác nghĩa là write lease cục bộ, [\"*\"] nghĩa là ghi độc quyền toàn workspace. Write lease là cổng cưỡng chế đối với write/edit/download: nhiệm vụ con có writePaths=[] gọi các công cụ này sẽ bị chặn trực tiếp, đường dẫn vượt phạm vi cũng bị chặn, cần ghi đĩa phải khai báo đường dẫn cụ thể trước; shell của base không bị ràng buộc bởi cổng, write lease cũng chỉ phối hợp trong cùng một lần gọi. Khi nhiệm vụ con cần thao tác loại phê duyệt (Lane nền không thể tạm dừng phê duyệt) sẽ được nộp lên dưới dạng việc cần làm, phải do bạn khởi tạo lại ở phiên chính. Thư mục ứng viên sẽ không vào hội thoại chính. Mỗi nhiệm vụ con trong summary đều có task_id, gửi lại task_id của nhiệm vụ con chưa hoàn tất kèm chỉ dẫn mới (trường task_id) có thể chạy tiếp trên cùng phiên con, thay vì bắt đầu lại từ đầu.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"subagents":{"type":"array","description":"一次性提交的完整独立子任务列表；用户枚举 N 项时必须包含全部 N 项","minItems":1,"maxItems":6,"items":{"type":"object","properties":{"taskName":{"type":"string","description":"简短子任务名称"},"department":{"type":"string","enum":["engineering","design","product","project-management","testing","security","game-development","spatial-computing","specialized"],"description":"先选研发部门，匹配严格限制在该部门"},"agentQuery":{"type":"string","minLength":2,"maxLength":80,"description":"2-5 个简短英文专业关键词，如 frontend react、mobile android、test automation；不要复制完整任务"},"role":{"type":"string","description":"可选：仅兼容已知 profile id/name 的精确覆盖；存在时优先于索引匹配"},"prompt":{"type":"string","description":"详细任务指令与交付要求；填写 task_id 时为本轮续跑指令"},"writePaths":{"type":"array","description":"必须声明。纯调研/分析填空数组 []；修改文件时列出精确相对路径；只有整工作区独占写入才填 [\"*\"]","items":{"type":"string"}},"model":{"type":"string","description":"Tùy chọn: ID/tên hồ sơ model đã lưu, hoặc tên model cụ thể đã cấu hình trong hồ sơ; ưu tiên hơn model mặc định của vai trò. Truyền inherit để buộc kế thừa model của phiên cha; không điền thì dùng model mặc định của vai trò, vai trò chưa cấu hình thì kế thừa phiên cha"},"task_id":{"type":"string","description":"Tùy chọn: khi tiếp chạy nhiệm vụ con đã có thì điền task_id trong bản tổng hợp lần trước (sao chép nguyên văn), lần này tiếp tục trên ngữ cảnh đã có của phiên con đó; khi đó role/department/agentQuery sẽ bị bỏ qua"}},"required":["taskName","department","agentQuery","prompt","writePaths"]}}},"required":["subagents"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "invoke_dual_agent",
                    description = "Dùng cặp Agent Planner/Executor cách ly vật lý để thực thi nhiệm vụ phức tạp nhiều bước. Planner chỉ lập kế hoạch và nghiệm thu, Executor dùng công cụ song song theo phụ thuộc DAG; phù hợp với nhiệm vụ cần triển khai và xác minh nhiều giai đoạn.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"prompt":{"type":"string","description":"需要双智能体完成的完整任务与验收标准"},"planner_model":{"type":"string","description":"可选：Planner 使用的已保存模型档案 ID/名称，或档案中已配置的具体模型名；省略则继承当前会话"},"executor_model":{"type":"string","description":"可选：Executor 使用的已保存模型档案 ID/名称，或档案中已配置的具体模型名；省略则继承当前会话"},"max_steps":{"type":"integer","minimum":1,"maximum":30,"description":"最大规划轮数，默认 10"}},"required":["prompt"]}""",
                    ).jsonObject,
                ),
            ),
            A2uiSurfaceContract.TOOL_DEFINITION,
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "load_skill",
                    description = "Nạp theo nhu cầu hướng dẫn đầy đủ hoặc tài nguyên con của Skill. Danh mục «Skill khả dụng» ở cuối system prompt chỉ liệt kê tên và tình huống áp dụng; " +
                        "Khi yêu cầu người dùng khớp mô tả của một kỹ năng nào đó, hãy dùng công cụ này tải quy tắc chỉ dẫn đầy đủ và đường dẫn tài nguyên của nó trước, rồi thực thi theo hướng dẫn." +
                        "Khi truyền path thì đọc tệp phụ theo đường dẫn tương đối trong thư mục skill (như references/spec.md, scripts/run.sh); " +
                        "Khi lược bỏ path thì đọc phần thân SKILL.md gốc (đã loại bỏ YAML frontmatter). Skill mà người dùng đã @nhắc sẽ tự động có hiệu lực, không cần nạp lại. Chỉ đọc.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"name":{"type":"string","description":"技能名称或触发命令（不含 / 前缀），须与目录中列出的一致"},"path":{"type":"string","description":"可选：技能目录内相对资源路径（如 references/foo.md 或 scripts/bar.sh）；省略时读取根 SKILL.md 正文"}},"required":["name"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "load_rule",
                    description = PromptRouter.LOAD_RULE_DESCRIPTION,
                    parameters = Json.parseToJsonElement(PromptRouter.LOAD_RULE_SCHEMA).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "ask_user",
                    description = "Lệnh base không phải truy xuất chỉ đọc: chế độ quy hoạch chỉ đọc chỉ cho phép các lệnh xem như ls / cat / rg / grep / find / git status, không cho phép cài đặt, build, test hay ghi tệp.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"questions":{"type":"array","minItems":1,"maxItems":4,"items":{"type":"object","properties":{"question":{"type":"string","description":"问题正文，具体、可直接回答"},"header":{"type":"string","description":"短标签（≤12 字），用于卡片分组"},"options":{"type":"array","minItems":0,"maxItems":6,"items":{"type":"object","properties":{"label":{"type":"string","description":"选项短文本"},"description":{"type":"string","description":"选项补充说明，可省略"}},"required":["label"]},"description":"候选项；省略或为空表示自由文本问题"},"allow_custom":{"type":"boolean","description":"除候选项外是否允许用户自定义输入；有 options 时默认 true"}},"required":["question"]}}},"required":["questions"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "compress",
                    description = "Đặt câu hỏi có cấu trúc cho người dùng tại các điểm quyết định then chốt (câu hỏi lựa chọn hoặc nhập tự do). Dùng khi nhiều phương án hợp lý khó tự quyết, hoặc thiếu thông tin then chốt ảnh hưởng đáng kể đến kết quả; đừng dùng nó để xác nhận các bước hiển nhiên, cũng đừng gọi thường xuyên làm gián đoạn người dùng.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"mode":{"type":"string","enum":["before","after"],"description":"before=压缩锚点轮之前的全部历史，保留锚点轮及之后；after=压缩除当前进行中轮次外的全部已完成历史"},"anchor":{"type":"string","description":"某条用户消息中一段原样、唯一的文字摘录（至少 8 字符），用于定位压缩边界；匹配到多条时会被拒绝"}},"required":["mode","anchor"]}""",
                    ).jsonObject,
                ),
            ),
            ApiToolDefinition(
                function = ApiFunctionDefinition(
                    name = "use_capability",
                    description = "Cổng proxy thống nhất cho năng lực MCP: phát hiện và gọi công cụ của dịch vụ MCP đã bật (trình duyệt/tìm kiếm tích hợp v.v. + dịch vụ do người dùng thêm). action=list liệt kê dịch vụ (không khởi động tiến trình nào); action=inspect + server xem danh sách công cụ và tham số; action=call + server + tool + arguments thực thi (dịch vụ chưa kết nối sẽ tự khởi động); action=decline + server + tool nghĩa là từ bỏ năng lực đó.",
                    parameters = Json.parseToJsonElement(
                        """{"type":"object","properties":{"action":{"type":"string","enum":["list","inspect","call","decline"],"description":"list=列出服务；inspect=查看某服务的工具清单；call=调用工具；decline=放弃某能力"},"server":{"type":"string","description":"MCP 服务 id（list 时省略；inspect/call/decline 必填）"},"tool":{"type":"string","description":"目标工具名（call/decline 必填；inspect 省略则列出该服务全部工具）"},"arguments":{"type":"object","description":"call 时传给目标工具的参数对象，结构与 inspect 输出的参数 schema 一致"}},"required":["action"]}""",
                    ).jsonObject,
                ),
            ),
        )

        /**
         * 组装静态基础工具（含 use_capability 统一代理）。
         * MCP 工具 schema 不再进入 provider 可见面：模型经 use_capability 的
         * list/inspect 发现能力、call 调用；服务器清单变化不再击穿前缀缓存。
         */
        fun buildDynamicTools(): List<ApiToolDefinition> = TOOLS

        /**
         * 把工具定义转成给模型看的 JSON 文本描述（JSON_TEXT 工具调用模式使用）。
         * 每个工具一行 JSON，格式与 OpenAI function calling 一致，模型按 name/parameters 输出调用。
         */
        fun buildToolsTextDescription(tools: List<ApiToolDefinition>): String {
            if (tools.isEmpty()) return "(Không có công cụ khả dụng)"
            return tools.joinToString("\n") { tool ->
                val fn = tool.function
                buildString {
                    append("- ").append(fn.name).append(": ").append(fn.description)
                    append("\n  JSON Schema tham số: ").append(fn.parameters.toString())
                }
            }
        }

        /**
         * 从模型回复内容中提取 <think>...</think> 标签内容并与正文分离。
         */
        fun extractThinkTags(content: String?, explicitReasoning: String?): Pair<String?, String?> {
            if (content.isNullOrBlank()) return Pair(content, explicitReasoning)
            val thinkPattern = Regex("""(?s)<think>(.*?)</think>""")
            val match = thinkPattern.find(content)
            return if (match != null) {
                val extractedReasoning = match.groupValues[1].trim()
                val strippedContent = thinkPattern.replace(content, "").trim().ifEmpty { null }
                val finalReasoning = explicitReasoning?.takeIf { it.isNotBlank() } ?: extractedReasoning.ifEmpty { null }
                Pair(strippedContent, finalReasoning)
            } else {
                val unclosedPattern = Regex("""(?s)<think>(.*)""")
                val unclosedMatch = unclosedPattern.find(content)
                if (unclosedMatch != null) {
                    val extractedReasoning = unclosedMatch.groupValues[1].trim()
                    val strippedContent = unclosedPattern.replace(content, "").trim().ifEmpty { null }
                    val finalReasoning = explicitReasoning?.takeIf { it.isNotBlank() } ?: extractedReasoning.ifEmpty { null }
                    Pair(strippedContent, finalReasoning)
                } else {
                    Pair(content, explicitReasoning)
                }
            }
        }

        /**
         * 剥离文本中的 <think>...</think> 标签残余。
         */
        fun stripThinkTags(content: String?): String? {
            if (content == null) return null
            val thinkPattern = Regex("""(?s)<think>(.*?)</think>""")
            val unclosedPattern = Regex("""(?s)<think>(.*)""")
            val stripped = unclosedPattern.replace(thinkPattern.replace(content, ""), "").trim()
            return stripped.ifEmpty { null }
        }
    }
}

/**
 * 流式 chunk 的 <think> 标签与思考/正文智能分流器。
 * 兼容：原生 reasoning_chunk、在 content 中输出 <think>...</think> 标签以及跨 chunk 标签碎片。
 * 内置思维链死循环/重复自旋检测与保护机制。
 */
internal class ThinkTagStreamDemuxer(
    private val onReasoning: (String) -> Unit,
    private val onDelta: (String) -> Unit,
    private val maxReasoningChars: Int = ProviderClient.MAX_STREAM_REASONING_CHARS,
) {
    val fullText = StringBuilder()
    val fullReasoning = StringBuilder()

    private var inThinkTag = false
    private val pendingBuffer = StringBuilder()
    private var reasoningMutedDueToLoop = false

    fun onExplicitReasoningChunk(chunk: String) {
        if (chunk.isEmpty()) return
        appendReasoning(chunk)
    }

    fun onContentChunk(chunk: String) {
        if (chunk.isEmpty()) return
        pendingBuffer.append(chunk)
        processPending()
    }

    fun flush() {
        if (pendingBuffer.isNotEmpty()) {
            val leftover = pendingBuffer.toString()
            pendingBuffer.clear()
            if (inThinkTag) {
                appendReasoning(leftover)
            } else {
                appendText(leftover)
            }
        }
    }

    private fun processPending() {
        while (pendingBuffer.isNotEmpty()) {
            if (!inThinkTag) {
                val thinkStartIdx = pendingBuffer.indexOf("<think>")
                if (thinkStartIdx != -1) {
                    val before = pendingBuffer.substring(0, thinkStartIdx)
                    if (before.isNotEmpty()) {
                        appendText(before)
                    }
                    pendingBuffer.delete(0, thinkStartIdx + "<think>".length)
                    inThinkTag = true
                } else {
                    val possiblePrefixLen = matchTrailingPrefix(pendingBuffer, "<think>")
                    if (possiblePrefixLen > 0) {
                        val emitLen = pendingBuffer.length - possiblePrefixLen
                        if (emitLen > 0) {
                            val toEmit = pendingBuffer.substring(0, emitLen)
                            appendText(toEmit)
                            pendingBuffer.delete(0, emitLen)
                        }
                        break
                    } else {
                        val textToEmit = pendingBuffer.toString()
                        pendingBuffer.clear()
                        appendText(textToEmit)
                    }
                }
            } else {
                val thinkEndIdx = pendingBuffer.indexOf("</think>")
                if (thinkEndIdx != -1) {
                    val reasoningContent = pendingBuffer.substring(0, thinkEndIdx)
                    if (reasoningContent.isNotEmpty()) {
                        appendReasoning(reasoningContent)
                    }
                    pendingBuffer.delete(0, thinkEndIdx + "</think>".length)
                    inThinkTag = false
                } else {
                    val possiblePrefixLen = matchTrailingPrefix(pendingBuffer, "</think>")
                    if (possiblePrefixLen > 0) {
                        val emitLen = pendingBuffer.length - possiblePrefixLen
                        if (emitLen > 0) {
                            val toEmit = pendingBuffer.substring(0, emitLen)
                            appendReasoning(toEmit)
                            pendingBuffer.delete(0, emitLen)
                        }
                        break
                    } else {
                        val reasoningToEmit = pendingBuffer.toString()
                        pendingBuffer.clear()
                        appendReasoning(reasoningToEmit)
                    }
                }
            }
        }
    }

    private fun appendText(str: String) {
        if (str.isEmpty()) return
        fullText.append(str)
        onDelta(str)
    }

    private fun appendReasoning(str: String) {
        if (str.isEmpty()) return
        if (reasoningMutedDueToLoop) return

        if (detectRepetitionLoop(fullReasoning, str)) {
            reasoningMutedDueToLoop = true
            val notice = "\n[Thiên Huyền nhắc: Phát hiện vòng lặp chết tự xoay lặp lại của chuỗi suy nghĩ, đã tự động cắt nội dung suy nghĩ dư thừa và tiếp tục thực thi]\n"
            if (fullReasoning.length + notice.length <= maxReasoningChars) {
                fullReasoning.append(notice)
            }
            onReasoning(notice)
            return
        }

        if (fullReasoning.length < maxReasoningChars) {
            val takeCount = (maxReasoningChars - fullReasoning.length).coerceAtMost(str.length)
            fullReasoning.append(str.take(takeCount))
        }
        onReasoning(str)
    }

    private fun matchTrailingPrefix(sb: CharSequence, target: String): Int {
        for (len in target.length - 1 downTo 1) {
            if (sb.length >= len) {
                val sub = sb.subSequence(sb.length - len, sb.length)
                if (target.startsWith(sub)) {
                    return len
                }
            }
        }
        return 0
    }

    private fun detectRepetitionLoop(history: StringBuilder, newChunk: String): Boolean {
        if (history.length < 200) return false
        val recent = history.takeLast(300).toString() + newChunk
        for (patternLen in 12..40) {
            if (recent.length >= patternLen * 4) {
                val p = recent.takeLast(patternLen)
                val p2 = recent.substring(recent.length - patternLen * 2, recent.length - patternLen)
                val p3 = recent.substring(recent.length - patternLen * 3, recent.length - patternLen * 2)
                val p4 = recent.substring(recent.length - patternLen * 4, recent.length - patternLen * 3)
                if (p == p2 && p2 == p3 && p3 == p4) {
                    return true
                }
            }
        }
        return false
    }
}
