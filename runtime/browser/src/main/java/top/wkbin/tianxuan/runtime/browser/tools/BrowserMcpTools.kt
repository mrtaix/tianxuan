package top.wkbin.tianxuan.runtime.browser.tools

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import top.wkbin.tianxuan.core.browser.BrowserCapability
import top.wkbin.tianxuan.core.browser.BrowserPreferences
import top.wkbin.tianxuan.core.browser.BrowserRisk
import top.wkbin.tianxuan.core.model.ToolImageRef
import top.wkbin.tianxuan.runtime.browser.BrowserEngine
import top.wkbin.tianxuan.runtime.browser.BrowserSessionToken
import top.wkbin.tianxuan.runtime.browser.cdp.DebugStep
import top.wkbin.tianxuan.runtime.browser.hook.HookAction
import top.wkbin.tianxuan.runtime.browser.hook.HookRule
import top.wkbin.tianxuan.runtime.browser.hook.HookType
import top.wkbin.tianxuan.runtime.browser.hook.validate

/**
 * mcp__browser__* 工具调度器。
 *
 * `tools/list` 的描述由 [list] 提供；`tools/call` 的入参由 [invoke] 翻译成 [BrowserEngine] 方法。
 *
 * 注：所有调用都是 `suspend`，由调用方在自己的协程里 await；浏览器能力列表由 [BrowserEngine.descriptor.capabilities] 预先判定。
 */
class BrowserMcpTools(
    private val engines: List<BrowserEngine>,
    private val engineSelector: (BrowserSessionToken) -> BrowserEngine?,
    private val prefs: BrowserPreferences,
) {
    /** hook 动作数组的解码器：sealed 多态，判别字段 "type"（与 HookRuleStore 的 Json 配置一致）。 */
    private val hookJson = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    /**
     * provider 可见工具清单。与 invoke 侧门禁（allowEvalJs / allowHooks / allowCdp）同源：
     * 被关闭的能力对应工具不再注入模型——模型看不到就不会调用，省掉每轮请求重复携带的
     * schema tokens；若模型凭历史轮次调用已隐藏工具，invoke 门禁仍会拦截并给出开启指引。
     * 门禁组合：hook_* 需 allowHooks 或 allowCdp（CDP Fetch 拦截同样要建规则）；
     * inject_script 仅 allowHooks；debug_* 仅 allowCdp；evaluate 仅 allowEvalJs；
     * network_detail 无门禁（hooks 关闭时 body 天然不存在，仅元数据可见）。
     */
    fun list(): List<ToolSpec> = TOOLS.filter { spec ->
        when {
            spec.name.startsWith("browser.debug_") -> prefs.allowCdp
            spec.name.startsWith("browser.hook_") -> prefs.allowHooks || prefs.allowCdp
            spec.name == "browser.inject_script" -> prefs.allowHooks
            spec.name == "browser.evaluate" -> prefs.allowEvalJs
            else -> true
        }
    }

    suspend fun invoke(toolName: String, args: JsonObject): InvokeResult {
        val spec = TOOLS.firstOrNull { it.name == toolName }
            ?: return InvokeResult.error("Công cụ không xác định：$toolName")
        val engine = pickEngine(explicitTokenOf(args)) ?: return InvokeResult.error("Không có engine trình duyệt")
        return when (toolName) {
            "browser.open" -> {
                val url = sanitizeUrl(args["url"]?.asString()).ifEmpty { prefs.homeUrl }
                val token = engine.openTab(url)
                InvokeResult.okMessage("opened tab=${token.tabId} url=${url}")
            }
            "browser.navigate" -> {
                val url = sanitizeUrl(args["url"]?.asString())
                if (url.isBlank()) InvokeResult.error("Thiếu tham số url") else {
                engine.navigate(tokenOf(args, engine), url); InvokeResult.okMessage("navigated=$url")
                }
            }
            "browser.back" -> InvokeResult.okMessage(if (engine.back(tokenOf(args, engine))) "ok" else "noop")
            "browser.forward" -> InvokeResult.okMessage(if (engine.forward(tokenOf(args, engine))) "ok" else "noop")
            "browser.refresh" -> { engine.refresh(tokenOf(args, engine)); InvokeResult.okMessage("refreshed") }
            "browser.list_tabs" -> InvokeResult.okMessage(
                engine.listTabs().joinToString("\n") { "${it.tabId}\t${it.url}\t${it.title}" }
            )
            "browser.close_tab" -> { engine.closeTab(tokenOf(args, engine)); InvokeResult.okMessage("closed") }
            "browser.snapshot" -> handleSnapshot(engine, args)
            "browser.click" -> handleWithRef(engine, args, ::click)
            "browser.type" -> handleType(engine, args)
            "browser.press" -> handlePress(engine, args)
            "browser.scroll" -> {
                val deltaY = args["deltaY"]?.asInt() ?: 300
                InvokeResult.okMessage(if (engine.scroll(tokenOf(args, engine), deltaY)) "scrolled=$deltaY" else "noop")
            }
            "browser.screenshot" -> InvokeResult.okImage("", engine.screenshot(tokenOf(args, engine), prefs))
            "browser.current_url" -> {
            val t = tokenOf(args, engine)
            InvokeResult.okMessage(engine.eventBus.urlOf(t.tabId) ?: t.url)
        }
            "browser.title" -> {
            val t = tokenOf(args, engine)
            InvokeResult.okMessage(engine.eventBus.titleOf(t.tabId) ?: t.title)
        }
            "browser.page_source" -> {
                val max = (args["maxBytes"]?.asInt() ?: 60_000).coerceIn(1_000, 256_000)
                InvokeResult.okMessage(engine.pageSource(tokenOf(args, engine), max))
            }
            "browser.evaluate" -> {
                val exp = args["expression"]?.asString().orEmpty()
                when {
                    // 安全门禁：allowEvalJs 默认 false，未显式开启时不执行任何脚本
                    !prefs.allowEvalJs -> InvokeResult.error("evaluate bị vô hiệu (allowEvalJs=false), hãy bật trong cài đặt trình duyệt")
                    exp.isEmpty() -> InvokeResult.error("Thiếu expression")
                    else -> engine.evaluate(tokenOf(args, engine), exp)?.let { InvokeResult.okMessage(it) }
                        // 引擎侧 evaluate 超时/失败返回 null：不能再伪装成成功（空串）
                        ?: InvokeResult.error("evaluation failed or timed out")
                }
            }
            "browser.console_list" -> InvokeResult.okMessage(
                engine.eventBus.console.value.joinToString("\n") { "[${it.level}] ${it.message}" }
            )
            "browser.console_clear" -> { engine.eventBus.clearConsole(); InvokeResult.okMessage("ok") }
            "browser.network_list" -> InvokeResult.okMessage(
                engine.eventBus.network.value.joinToString("\n") { r ->
                    buildString {
                        append("[${r.source}] [${r.method}] ${r.url} (${r.statusCode}, ${r.durationMs}ms")
                        if (r.requestSize > 0 || r.responseSize > 0) {
                            append(", req ${fmtBytes(r.requestSize)} res ${fmtBytes(r.responseSize)}")
                        }
                        if (r.ruleId.isNotBlank()) append(", rule=${r.ruleId}, action=${r.actionTaken}")
                        append(") id=${r.id}")
                    }
                }.ifEmpty { "no captured requests" }
            )
            "browser.cookies_get" -> {
            val t = tokenOf(args, engine)
            InvokeResult.okMessage(engine.cookiesGet(t, args["url"]?.asString()))
        }
            "browser.cookies_set" -> {
            val t = tokenOf(args, engine)
            engine.cookiesSet(t, args["url"]?.asString().orEmpty(), args["header"]?.asString().orEmpty())
            InvokeResult.okMessage("ok")
        }
            "browser.cookies_delete" -> {
            val t = tokenOf(args, engine)
            engine.cookiesDelete(t, args["url"]?.asString().orEmpty(), args["name"]?.asString().orEmpty())
            InvokeResult.okMessage("ok")
        }
            "browser.local_get", "browser.session_get" -> handleKvGet(engine, args, toolName.startsWith("browser.session"))
            "browser.local_set", "browser.session_set" -> handleKvSet(engine, args, toolName.startsWith("browser.session"))
            "browser.local_delete", "browser.session_delete" -> handleKvDelete(engine, args, toolName.startsWith("browser.session"))
            "browser.local_keys", "browser.session_keys" -> handleKvKeys(engine, args, toolName.startsWith("browser.session"))
            // ===== 注入式 Hook 引擎（阶段 1；allowHooks||allowCdp 门禁，network_detail 除外） =====
            "browser.hook_create" -> handleHookCreate(engine, args)
            "browser.hook_list" -> handleHookList(engine, args)
            "browser.hook_remove" -> handleHookRemove(engine, args)
            "browser.hook_reset" -> handleHookReset(engine, args)
            "browser.hook_hits" -> handleHookHits(engine, args)
            "browser.inject_script" -> handleInjectScript(engine, args)
            "browser.network_detail" -> handleNetworkDetail(engine, args)
            // ===== CDP 调试引擎（阶段 2；allowCdp 门禁） =====
            "browser.debug_status" -> handleDebugStatus(engine)
            "browser.debug_attach" -> handleDebugAttach(engine, args)
            "browser.debug_detach" -> handleDebugDetach(engine, args)
            "browser.debug_set_breakpoint" -> handleDebugSetBreakpoint(engine, args)
            "browser.debug_remove_breakpoint" -> handleDebugRemoveBreakpoint(engine, args)
            "browser.debug_list_breakpoints" -> handleDebugListBreakpoints(engine, args)
            "browser.debug_resume" -> handleDebugResume(engine, args)
            "browser.debug_step" -> handleDebugStep(engine, args)
            "browser.debug_state" -> handleDebugState(engine, args)
            "browser.debug_eval" -> handleDebugEval(engine, args)
            "browser.debug_scope" -> handleDebugScope(engine, args)
            else -> InvokeResult.error("Công cụ chưa triển khai: $toolName")
        }
    }

    private fun explicitTokenOf(args: JsonObject): BrowserSessionToken {
        val tabId = args["tab"]?.asString() ?: BrowserSessionToken.DEFAULT_TAB_ID
        return BrowserSessionToken(tabId = tabId, family = prefs.resolvedFamily)
    }

    /**
     * 解析工具调用的目标 tab token。
     * 归一化容错：模型常把 list_tabs 输出的 `t:xxx` 传成 `xxx`（丢前缀），
     * 先精确匹配再后缀匹配已存在的 tab，均未命中时保留原值让引擎报 not found。
     */
    private suspend fun tokenOf(args: JsonObject, engine: BrowserEngine): BrowserSessionToken {
        val raw = args["tab"]?.asString()
        if (raw.isNullOrBlank()) {
            return engine.activeTab() ?: BrowserSessionToken.defaultTab(engine.family)
        }
        if (raw == BrowserSessionToken.DEFAULT_TAB_ID) {
            return BrowserSessionToken(raw, engine.family)
        }
        val tabs = engine.listTabs()
        val normalized = tabs.firstOrNull { it.tabId == raw }?.tabId
            ?: tabs.firstOrNull { it.tabId.endsWith(raw) }?.tabId
            ?: raw
        return BrowserSessionToken(tabId = normalized, family = engine.family)
    }

    /** URL 清洗：剥离首尾空白与反引号，并拒绝 javascript:/data: 等可执行 scheme。 */
    private fun sanitizeUrl(raw: String?): String {
        val trimmed = raw?.trim()?.trim('`')?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""
        val lower = trimmed.lowercase()
        // 仅允许 http/https/about/blob，拒绝 javascript:/data:/file: 等可执行 scheme
        val allowedSchemes = listOf("http://", "https://", "about:", "blob:", "http%3a", "https%3a")
        return if (allowedSchemes.any { lower.startsWith(it) } || (!lower.contains("://") && !lower.startsWith("about:"))) {
            trimmed
        } else {
            ""
        }
    }

    private fun pickEngine(token: BrowserSessionToken): BrowserEngine? {
        return engineSelector(token) ?: engines.firstOrNull()
    }

    private suspend fun handleWithRef(
        engine: BrowserEngine,
        args: JsonObject,
        action: suspend (BrowserEngine, BrowserSessionToken, String) -> Boolean,
    ): InvokeResult {
        val ref = args["ref"]?.asString().orEmpty()
        if (ref.isBlank()) return InvokeResult.error("Thiếu ref")
        val ok = action(engine, tokenOf(args, engine), ref)
        return if (ok) InvokeResult.okMessage("ok=$ref") else InvokeResult.error("failed=$ref")
    }

    private suspend fun click(engine: BrowserEngine, tab: BrowserSessionToken, ref: String): Boolean =
        engine.click(tab, ref) { _, r -> "[data-tianxuan-ref='$r']" }

    private suspend fun handleType(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val ref = args["ref"]?.asString().orEmpty()
        val text = args["text"]?.asString().orEmpty()
        if (ref.isBlank() || text.isEmpty()) return InvokeResult.error("Thiếu ref hoặc text")
        val ok = engine.typeInto(tokenOf(args, engine), ref, text) { _, r -> "[data-tianxuan-ref='$r']" }
        return if (ok) InvokeResult.okMessage("typed=${text.length}") else InvokeResult.error("type-failed=$ref")
    }

    private suspend fun handlePress(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val ref = args["ref"]?.asString()
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("Thiếu key")
        val ok = engine.press(tokenOf(args, engine), ref, key) { _, r -> ref?.let { "[data-tianxuan-ref='$r']" } ?: "" }
        return if (ok) InvokeResult.okMessage("pressed=$key") else InvokeResult.error("press-failed=$key")
    }

    private suspend fun handleSnapshot(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val max = (args["maxElements"]?.asInt() ?: 200).coerceIn(10, 300)
        val snap = engine.snapshot(tokenOf(args, engine), max)
        val refsArray = JsonArray(snap.refs.entries.take(max).map { (ref, r) ->
            buildJsonObject {
                put("ref", JsonPrimitive(ref))
                put("tag", JsonPrimitive(r.tag))
                r.type?.take(50)?.let { put("type", JsonPrimitive(it)) }
                r.role?.take(50)?.let { put("role", JsonPrimitive(it)) }
                r.name?.take(200)?.let { put("name", JsonPrimitive(it)) }
                r.text?.take(500)?.let { put("text", JsonPrimitive(it)) }
                r.placeholder?.take(200)?.let { put("placeholder", JsonPrimitive(it)) }
                r.ariaLabel?.take(200)?.let { put("ariaLabel", JsonPrimitive(it)) }
            }
        })
        return InvokeResult.okMessage(buildJsonObject {
            put("ok", JsonPrimitive(true))
            put("tab", JsonPrimitive(snap.tabId))
            put("url", JsonPrimitive(snap.url.take(1024)))
            put("title", JsonPrimitive(snap.title.take(500)))
            put("domFingerprint", JsonPrimitive(snap.domFingerprint))
            put("interactiveCount", JsonPrimitive(snap.interactiveRefs.size))
            put("refs", refsArray)
        }.toString())
    }

    private suspend fun handleKvGet(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult {
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("Thiếu key")
        val tab = tokenOf(args, engine)
        val v = if (session) engine.sessionGet(tab, key) else engine.localGet(tab, key)
        return InvokeResult.okMessage(v ?: "")
    }

    private suspend fun handleKvSet(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult {
        val key = args["key"]?.asString().orEmpty()
        val v = args["value"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("Thiếu key")
        val tab = tokenOf(args, engine)
        if (session) engine.sessionSet(tab, key, v) else engine.localSet(tab, key, v)
        return InvokeResult.okMessage("ok")
    }

    private suspend fun handleKvDelete(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult {
        val key = args["key"]?.asString().orEmpty()
        if (key.isEmpty()) return InvokeResult.error("Thiếu key")
        val tab = tokenOf(args, engine)
        if (session) engine.sessionDelete(tab, key) else engine.localDelete(tab, key)
        return InvokeResult.okMessage("ok")
    }

    private suspend fun handleKvKeys(engine: BrowserEngine, args: JsonObject, session: Boolean): InvokeResult =
        InvokeResult.okMessage(
            (if (session) engine.sessionKeys(tokenOf(args, engine)) else engine.localKeys(tokenOf(args, engine))).joinToString("\n")
        )

    // ===== hook 引擎工具实现 =====

    /**
     * 安全门禁：hook 规则工具在 allowHooks 或 allowCdp 任一开启时可用
     * （只开 allowCdp 时 CDP Fetch 拦截同样需要建规则）；默认均 false 时拒绝。
     */
    private fun hookGate(): InvokeResult? =
        if (!prefs.allowHooks && !prefs.allowCdp) {
            InvokeResult.error("Công cụ quy tắc hook bị vô hiệu hóa (allowHooks và allowCdp đều là false), vui lòng bật trong cài đặt trình duyệt rồi khởi động lại")
        } else null

    /** CDP debug 工具门禁：独立 allowCdp 开关（与 allowHooks 解耦）。 */
    private fun debugGate(): InvokeResult? =
        if (!prefs.allowCdp) {
            InvokeResult.error("Công cụ debug bị vô hiệu hóa (allowCdp=false), vui lòng bật trong cài đặt trình duyệt rồi khởi động lại")
        } else null

    /** 引擎侧 hooks/cdp 未启用（如 in-app 池开关为 false）时抛 UnsupportedOperationException → 转友好错误。 */
    private suspend fun runGatedTool(block: suspend () -> InvokeResult): InvokeResult =
        runCatching { block() }.getOrElse { e ->
            InvokeResult.error(e.message ?: "Thao tác thất bại")
        }

    private suspend fun handleHookCreate(engine: BrowserEngine, args: JsonObject): InvokeResult {
        hookGate()?.let { return it }
        val typeRaw = args["type"]?.asString()
            ?: return InvokeResult.error("Thiếu type (fetch/xhr/websocket/function/method/property/storage/cookie/console/timer/crypto)")
        val type = HookType.entries.firstOrNull { it.name.equals(typeRaw, ignoreCase = true) }
            ?: return InvokeResult.error("hook type không xác định: $typeRaw")
        val target = args["target"]?.asString() ?: return InvokeResult.error("Thiếu target")
        val actions: List<HookAction> = when (val actionsEl = args["actions"]) {
            null -> listOf(HookAction.Log())
            is JsonArray -> runCatching {
                hookJson.decodeFromJsonElement(ListSerializer(HookAction.serializer()), actionsEl)
            }.getOrElse { return InvokeResult.error("Phân giải actions thất bại: ${it.message}") }
            else -> return InvokeResult.error("actions phải là mảng, ví dụ [{\"type\":\"log\"}]")
        }
        if (actions.isEmpty()) return InvokeResult.error("actions không được rỗng")
        val rule = HookRule(
            id = "hr_" + java.util.UUID.randomUUID().toString().substring(0, 8),
            type = type,
            target = target,
            name = args["name"]?.asString() ?: "",
            scopeTabId = args["tab"]?.asString(),
            method = args["method"]?.asString() ?: "*",
            actions = actions,
            enabled = args["enabled"]?.asBool() ?: true,
            captureStack = args["captureStack"]?.asBool() ?: false,
            captureBody = args["captureBody"]?.asBool() ?: false,
        )
        rule.validate()?.let { return InvokeResult.error(it) }
        return runGatedTool {
            engine.hookInstall(rule)
            InvokeResult.okMessage(
                "hook installed: ${rule.id} type=${rule.type} target=${rule.target} actions=${actions.joinToString(",") { it::class.simpleName ?: "?" }}"
            )
        }
    }

    private suspend fun handleHookList(engine: BrowserEngine, args: JsonObject): InvokeResult {
        hookGate()?.let { return it }
        return runGatedTool {
            val rules = engine.hookList(args["tab"]?.asString())
            InvokeResult.okMessage(
                if (rules.isEmpty()) "no hooks"
                else rules.joinToString("\n") {
                    "${it.rule.id}\t${it.rule.type}\t${it.rule.target}\tenabled=${it.rule.enabled}\thits=${it.hitCount}"
                }
            )
        }
    }

    private suspend fun handleHookRemove(engine: BrowserEngine, args: JsonObject): InvokeResult {
        hookGate()?.let { return it }
        val id = args["id"]?.asString() ?: return InvokeResult.error("Thiếu id")
        return runGatedTool {
            if (engine.hookRemove(id)) InvokeResult.okMessage("hook removed: $id")
            else InvokeResult.error("hook not found: $id")
        }
    }

    private suspend fun handleHookReset(engine: BrowserEngine, args: JsonObject): InvokeResult {
        hookGate()?.let { return it }
        val tabId = args["tab"]?.asString()
        return runGatedTool {
            engine.hookReset(tabId)
            InvokeResult.okMessage("hooks reset" + (tabId?.let { " (tab=$it)" } ?: " (all)"))
        }
    }

    private suspend fun handleHookHits(engine: BrowserEngine, args: JsonObject): InvokeResult {
        hookGate()?.let { return it }
        val tabId = args["tab"]?.asString()
        val limit = (args["limit"]?.asInt() ?: 50).coerceIn(1, 200)
        val hits = engine.eventBus.hookHits.value
            .filter { tabId == null || it.tabId == tabId }
            .takeLast(limit)
        return InvokeResult.okMessage(
            if (hits.isEmpty()) "no hook hits"
            else hits.joinToString("\n") {
                "[${it.at}] ${it.tabId} ${it.hookId} ${it.type} ${it.target} ${it.phase} — ${it.summary}"
            }
        )
    }

    private suspend fun handleInjectScript(engine: BrowserEngine, args: JsonObject): InvokeResult {
        // inject_script 是页内注入，维持 allowHooks-only（CDP 开关不包含注入能力）
        if (!prefs.allowHooks) {
            return InvokeResult.error("inject_script bị vô hiệu (allowHooks=false), hãy bật trong cài đặt trình duyệt rồi khởi động lại")
        }
        val code = args["code"]?.asString() ?: return InvokeResult.error("Thiếu code")
        if (code.isBlank()) return InvokeResult.error("code không được rỗng")
        val persistent = args["persistent"]?.asBool() ?: false
        val name = args["name"]?.asString() ?: ""
        return runGatedTool {
            InvokeResult.okMessage(engine.injectScript(tokenOf(args, engine), code, persistent, name))
        }
    }

    /** 不受 allowHooks 门禁：hooks 关闭时 body 天然不存在，仅元数据可见。 */
    private suspend fun handleNetworkDetail(engine: BrowserEngine, args: JsonObject): InvokeResult {
        val id = args["id"]?.asString() ?: return InvokeResult.error("Thiếu id")
        return runGatedTool {
            engine.networkDetail(id)?.let { InvokeResult.okMessage(it) }
                ?: InvokeResult.error("network request not found: $id (id lấy từ cuối đầu ra browser.network_list)")
        }
    }

    // ===== CDP debug 工具实现 =====

    private suspend fun handleDebugStatus(engine: BrowserEngine): InvokeResult {
        debugGate()?.let { return it }
        return runGatedTool { InvokeResult.okMessage(engine.debugStatus()) }
    }

    private suspend fun handleDebugAttach(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        return runGatedTool { InvokeResult.okMessage(engine.debugAttach(tokenOf(args, engine))) }
    }

    private suspend fun handleDebugDetach(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        val tab = args["tab"]?.asString()?.let { BrowserSessionToken(tabId = it, family = engine.family) }
        return runGatedTool {
            val n = engine.debugDetach(tab)
            InvokeResult.okMessage("detached $n tab(s)" + (tab?.let { " (tab=${it.tabId})" } ?: " (all)"))
        }
    }

    private suspend fun handleDebugSetBreakpoint(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        val url = sanitizeUrl(args["url"]?.asString())
        if (url.isEmpty()) return InvokeResult.error("Thiếu url (URL script, như https://x.test/app.js)")
        val line = args["line"]?.asInt() ?: return InvokeResult.error("Thiếu line (số dòng bắt đầu từ 0)")
        val column = args["column"]?.asInt() ?: 0
        val condition = args["condition"]?.asString()
        return runGatedTool {
            val bp = engine.debugSetBreakpoint(tokenOf(args, engine), url, line, column, condition)
            InvokeResult.okMessage(
                "breakpoint ${bp.id}: ${bp.url}:${bp.lineNumber}:${bp.columnNumber}" +
                    (if (bp.condition.isNotEmpty()) " if (${bp.condition})" else "")
            )
        }
    }

    private suspend fun handleDebugRemoveBreakpoint(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        val id = args["id"]?.asString() ?: return InvokeResult.error("Thiếu id (từ debug_list_breakpoints)")
        return runGatedTool {
            if (engine.debugRemoveBreakpoint(tokenOf(args, engine), id)) InvokeResult.okMessage("breakpoint removed: $id")
            else InvokeResult.error("breakpoint not found: $id")
        }
    }

    private suspend fun handleDebugListBreakpoints(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        return runGatedTool {
            val bps = engine.debugListBreakpoints(tokenOf(args, engine))
            InvokeResult.okMessage(
                if (bps.isEmpty()) "no breakpoints"
                else bps.joinToString("\n") {
                    "${it.id}\t${it.url}:${it.lineNumber}:${it.columnNumber}" +
                        (if (it.condition.isNotEmpty()) "\tif(${it.condition})" else "")
                }
            )
        }
    }

    private suspend fun handleDebugResume(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        val tab = args["tab"]?.asString()?.let { BrowserSessionToken(tabId = it, family = engine.family) }
        return runGatedTool {
            val n = engine.debugResume(tab)
            InvokeResult.okMessage(
                if (n > 0) "resumed $n tab(s)"
                else "no paused tabs (khi lược bỏ tham số tab sẽ khôi phục tất cả tab đang paused)"
            )
        }
    }

    private suspend fun handleDebugStep(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        val stepRaw = args["step"]?.asString() ?: return InvokeResult.error("Thiếu step (over/into/out)")
        val step = when (stepRaw.lowercase()) {
            "over" -> DebugStep.OVER
            "into" -> DebugStep.INTO
            "out" -> DebugStep.OUT
            else -> return InvokeResult.error("step không xác định: $stepRaw (tùy chọn over/into/out)")
        }
        return runGatedTool {
            engine.debugStep(tokenOf(args, engine), step)
            InvokeResult.okMessage("stepped $stepRaw (điểm tạm dừng kế tiếp xem browser.debug_state)")
        }
    }

    private suspend fun handleDebugState(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        return runGatedTool { InvokeResult.okMessage(engine.debugState(tokenOf(args, engine))) }
    }

    private suspend fun handleDebugEval(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        val expression = args["expression"]?.asString() ?: return InvokeResult.error("Thiếu expression")
        if (expression.isBlank()) return InvokeResult.error("expression không được rỗng")
        val frame = args["frame"]?.asInt() ?: 0
        return runGatedTool {
            InvokeResult.okMessage(engine.debugEval(tokenOf(args, engine), frame, expression))
        }
    }

    private suspend fun handleDebugScope(engine: BrowserEngine, args: JsonObject): InvokeResult {
        debugGate()?.let { return it }
        val frame = args["frame"]?.asInt() ?: 0
        val scope = args["scope"]?.asInt()
        return runGatedTool {
            InvokeResult.okMessage(engine.debugScope(tokenOf(args, engine), frame, scope))
        }
    }

    private fun fmtBytes(n: Long): String = when {
        n >= 1024 * 1024 -> "${n / (1024 * 1024)}MB"
        n >= 1024 -> "${n / 1024}KB"
        else -> "${n}B"
    }

    private fun JsonElement?.asString(): String? =
        (this as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

    private fun JsonElement?.asInt(): Int? =
        (this as? JsonPrimitive)?.content?.toIntOrNull()

    private fun JsonElement?.asBool(): Boolean? =
        (this as? JsonPrimitive)?.content?.toBooleanStrictOrNull()


    data class ToolSpec(
        val name: String,
        val risk: BrowserRisk,
        val capability: BrowserCapability,
        val description: String,
        val inputSchema: JsonObject,
    )

    data class InvokeResult(
        val success: Boolean,
        val output: String,
        val imageAttachments: List<ToolImageRef> = emptyList(),
    ) {
        companion object {
            fun okMessage(message: String) = InvokeResult(success = true, output = message)
            fun okImage(message: String, image: ToolImageRef?) = InvokeResult(
                success = true,
                output = message,
                imageAttachments = listOfNotNull(image)
            )
            fun error(message: String) = InvokeResult(success = false, output = message)
        }
    }

    companion object {
        private fun schema(
            properties: Map<String, String> = emptyMap(),
            required: List<String> = emptyList(),
        ): JsonObject = schemaEl(properties.entries.associate { (name, type) ->
            name to buildJsonObject { put("type", JsonPrimitive(type)) }
        }, required)

        /** 嵌套结构版 schema：属性值直接给 JsonElement（数组/对象等）。 */
        private fun schemaEl(
            properties: Map<String, JsonElement> = emptyMap(),
            required: List<String> = emptyList(),
        ): JsonObject = buildJsonObject {
            put("type", JsonPrimitive("object"))
            put("properties", buildJsonObject {
                properties.forEach { (name, el) -> put(name, el) }
            })
            if (required.isNotEmpty()) {
                put("required", JsonArray(required.map(::JsonPrimitive)))
            }
            put("additionalProperties", JsonPrimitive(false))
        }

        private val NO_ARGS = schema(mapOf("tab" to "string"))
        private val URL = schema(mapOf("url" to "string", "tab" to "string"), listOf("url"))
        private val REF = schema(mapOf("ref" to "string", "tab" to "string"), listOf("ref"))

        // ---- hook 工具的嵌套 schema 片段 ----
        private val strT = buildJsonObject { put("type", JsonPrimitive("string")) }
        private val intT = buildJsonObject { put("type", JsonPrimitive("integer")) }
        private val boolT = buildJsonObject { put("type", JsonPrimitive("boolean")) }
        private val objT = buildJsonObject { put("type", JsonPrimitive("object")) }

        /** actions 数组：item 的 type 取值 log/block/redirect/mock/modify_headers/replace/fake_value。 */
        private val ACTIONS_SCHEMA: JsonElement = buildJsonObject {
            put("type", JsonPrimitive("array"))
            put("items", buildJsonObject {
                put("type", JsonPrimitive("object"))
                put("properties", buildJsonObject {
                    put("type", strT)
                    put("captureBody", boolT)
                    put("url", strT)
                    put("status", intT)
                    put("headers", objT)
                    put("body", strT)
                    put("request", objT)
                    put("response", objT)
                    put("code", strT)
                    put("value", strT)
                })
                put("required", JsonArray(listOf(JsonPrimitive("type"))))
            })
        }

        private val HOOK_CREATE_SCHEMA: JsonObject = schemaEl(
            mapOf(
                "type" to strT,
                "target" to strT,
                "name" to strT,
                "method" to strT,
                "tab" to strT,
                "actions" to ACTIONS_SCHEMA,
                "captureStack" to boolT,
                "captureBody" to boolT,
                "enabled" to boolT,
            ),
            listOf("type", "target"),
        )
        private val INJECT_SCHEMA: JsonObject = schemaEl(
            mapOf(
                "code" to strT,
                "tab" to strT,
                "persistent" to boolT,
                "name" to strT,
            ),
            listOf("code"),
        )

        // ---- debug 工具 schema ----
        private val DEBUG_SET_BP_SCHEMA: JsonObject = schemaEl(
            mapOf(
                "url" to strT,
                "line" to intT,
                "column" to intT,
                "condition" to strT,
                "tab" to strT,
            ),
            listOf("url", "line"),
        )
        private val DEBUG_STEP_SCHEMA: JsonObject = schemaEl(
            mapOf("step" to strT, "tab" to strT),
            listOf("step"),
        )
        private val DEBUG_EVAL_SCHEMA: JsonObject = schemaEl(
            mapOf("expression" to strT, "frame" to intT, "tab" to strT),
            listOf("expression"),
        )
        private val DEBUG_SCOPE_SCHEMA: JsonObject = schemaEl(
            mapOf("frame" to intT, "scope" to intT, "tab" to strT),
        )
        private val TOOLS: List<ToolSpec> = listOf(
            ToolSpec("browser.open", BrowserRisk.MEDIUM, BrowserCapability.OPEN, "Mở tab mới; trả về tab ID", URL),
            ToolSpec("browser.navigate", BrowserRisk.MEDIUM, BrowserCapability.NAVIGATE, "Tab hiện tại hoặc chỉ định chuyển tới URL", URL),
            ToolSpec("browser.back", BrowserRisk.LOW, BrowserCapability.NAVIGATE, "Quay lại", NO_ARGS),
            ToolSpec("browser.forward", BrowserRisk.LOW, BrowserCapability.NAVIGATE, "Tiến tới", NO_ARGS),
            ToolSpec("browser.refresh", BrowserRisk.LOW, BrowserCapability.NAVIGATE, "刷新", NO_ARGS),
            ToolSpec("browser.list_tabs", BrowserRisk.LOW, BrowserCapability.LIST_TABS, "Liệt kê tab", schema()),
            ToolSpec("browser.close_tab", BrowserRisk.LOW, BrowserCapability.CLOSE_TAB, "Đóng tab hiện tại hoặc chỉ định", NO_ARGS),
            ToolSpec("browser.snapshot", BrowserRisk.LOW, BrowserCapability.SNAPSHOT, "Trích xuất ref + thông tin meta trang", schema(mapOf("tab" to "string", "maxElements" to "integer"))),
            ToolSpec("browser.click", BrowserRisk.HIGH, BrowserCapability.CLICK, "Nhấp phần tử theo snapshot ref", REF),
            ToolSpec("browser.type", BrowserRisk.HIGH, BrowserCapability.TYPE, "Gõ văn bản theo snapshot ref", schema(mapOf("ref" to "string", "text" to "string", "tab" to "string"), listOf("ref", "text"))),
            ToolSpec("browser.press", BrowserRisk.HIGH, BrowserCapability.PRESS, "Gửi phím tới focus hiện tại hoặc ref chỉ định", schema(mapOf("ref" to "string", "key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.scroll", BrowserRisk.LOW, BrowserCapability.SCROLL, "Cuộn trang hiện tại", schema(mapOf("deltaY" to "integer", "tab" to "string"))),
            ToolSpec("browser.screenshot", BrowserRisk.LOW, BrowserCapability.SCREENSHOT, "Chụp trang hiện tại", NO_ARGS),
            ToolSpec("browser.current_url", BrowserRisk.LOW, BrowserCapability.PAGE_SOURCE, "Lấy URL hiện tại", NO_ARGS),
            ToolSpec("browser.title", BrowserRisk.LOW, BrowserCapability.PAGE_SOURCE, "Lấy title hiện tại", NO_ARGS),
            ToolSpec("browser.page_source", BrowserRisk.MEDIUM, BrowserCapability.PAGE_SOURCE, "HTML trang", schema(mapOf("maxBytes" to "integer", "tab" to "string"))),
            ToolSpec("browser.evaluate", BrowserRisk.CRITICAL, BrowserCapability.EVALUATE_JS, "Thực thi JS", schema(mapOf("expression" to "string", "tab" to "string"), listOf("expression"))),
            ToolSpec("browser.console_list", BrowserRisk.LOW, BrowserCapability.CONSOLE_READ, "Log console gần nhất", NO_ARGS),
            ToolSpec("browser.console_clear", BrowserRisk.MEDIUM, BrowserCapability.CONSOLE_READ, "Xóa console", NO_ARGS),
            ToolSpec("browser.network_list", BrowserRisk.LOW, BrowserCapability.NETWORK_INTERCEPT, "Đã bắt yêu cầu mạng", NO_ARGS),
            ToolSpec("browser.cookies_get", BrowserRisk.CRITICAL, BrowserCapability.COOKIES_RW, "Đọc Cookie", schema(mapOf("url" to "string", "tab" to "string"))),
            ToolSpec("browser.cookies_set", BrowserRisk.HIGH, BrowserCapability.COOKIES_RW, "Ghi Cookie", schema(mapOf("url" to "string", "header" to "string", "tab" to "string"), listOf("url", "header"))),
            ToolSpec("browser.cookies_delete", BrowserRisk.HIGH, BrowserCapability.COOKIES_RW, "Xóa Cookie", schema(mapOf("url" to "string", "name" to "string", "tab" to "string"), listOf("url", "name"))),
            ToolSpec("browser.local_get", BrowserRisk.HIGH, BrowserCapability.LOCAL_RW, "Đọc localStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.local_set", BrowserRisk.HIGH, BrowserCapability.LOCAL_RW, "Ghi localStorage", schema(mapOf("key" to "string", "value" to "string", "tab" to "string"), listOf("key", "value"))),
            ToolSpec("browser.local_delete", BrowserRisk.HIGH, BrowserCapability.LOCAL_RW, "Xóa localStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.local_keys", BrowserRisk.MEDIUM, BrowserCapability.LOCAL_RW, "Liệt kê localStorage keys", NO_ARGS),
            ToolSpec("browser.session_get", BrowserRisk.HIGH, BrowserCapability.SESSION_RW, "Đọc sessionStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.session_set", BrowserRisk.HIGH, BrowserCapability.SESSION_RW, "Ghi sessionStorage", schema(mapOf("key" to "string", "value" to "string", "tab" to "string"), listOf("key", "value"))),
            ToolSpec("browser.session_delete", BrowserRisk.HIGH, BrowserCapability.SESSION_RW, "Xóa sessionStorage", schema(mapOf("key" to "string", "tab" to "string"), listOf("key"))),
            ToolSpec("browser.session_keys", BrowserRisk.MEDIUM, BrowserCapability.SESSION_RW, "Liệt kê sessionStorage keys", NO_ARGS),
            // ===== 注入式 Hook 引擎（阶段 1）=====
            ToolSpec(
                "browser.hook_create", BrowserRisk.CRITICAL, BrowserCapability.INSTALL_HOOK,
                "Cài đặt quy tắc hook (công cụ cốt lõi reverse web). target tùy theo type: fetch/xhr/websocket→URL glob (* ? ký tự đại diện); function/method→đường dẫn đối tượng; property→đường dẫn thuộc tính; storage→local/session; cookie→tên cookie hoặc *; console→level phân tách bằng dấu phẩy; timer→setInterval/setTimeout; crypto→random/uuid. actions là mảng động tác, item.type có thể chọn log/block/redirect(chỉ fetch/xhr)/mock(chỉ fetch/xhr)/modify_headers(chỉ fetch/xhr)/replace(chỉ function/method)/fake_value(chỉ property), các trường khả dụng của mỗi động tác xem schema; nhiều động tác quyết định theo thứ tự ưu tiên block>mock>redirect>modify_headers>log. Khuyến nghị log quan sát trước rồi mới chỉnh sửa",
                HOOK_CREATE_SCHEMA,
            ),
            ToolSpec("browser.hook_list", BrowserRisk.LOW, BrowserCapability.INSTALL_HOOK, "Liệt kê quy tắc hook đã cài và số lần khớp", schema(mapOf("tab" to "string"))),
            ToolSpec("browser.hook_remove", BrowserRisk.MEDIUM, BrowserCapability.INSTALL_HOOK, "Xóa quy tắc hook theo id", schema(mapOf("id" to "string"), listOf("id"))),
            ToolSpec("browser.hook_reset", BrowserRisk.HIGH, BrowserCapability.INSTALL_HOOK, "Xóa quy tắc hook/script thường trú/bản ghi trúng/body (khi bỏ qua tab thì toàn cục)", schema(mapOf("tab" to "string"))),
            ToolSpec("browser.hook_hits", BrowserRisk.MEDIUM, BrowserCapability.INSTALL_HOOK, "Bản ghi trúng hook gần đây (tóm tắt tham số hàm/giá trị thuộc tính đọc được v.v.)", schema(mapOf("tab" to "string", "limit" to "integer"))),
            ToolSpec("browser.inject_script", BrowserRisk.CRITICAL, BrowserCapability.INSTALL_HOOK, "Tiêm JS vào trang. persistent=false thực thi ngay một lần; persistent=true đăng ký thành script bền vững, tự động phát lại mỗi lần điều hướng (phù hợp để định nghĩa lại hàm/sửa prototype). Cần allowEvalJs, tư duy giống evaluate: mã tác động trực tiếp lên trang thật, xin thận trọng", INJECT_SCHEMA),
            ToolSpec("browser.network_detail", BrowserRisk.MEDIUM, BrowserCapability.NETWORK_INTERCEPT, "Truy vấn metadata và body yêu cầu/phản hồi của một yêu cầu mạng đơn (id lấy từ network_list; body cần quy tắc captureBody=true để bắt)", schema(mapOf("id" to "string"), listOf("id"))),
            // ===== CDP 调试引擎（阶段 2）=====
            ToolSpec("browser.debug_status", BrowserRisk.LOW, BrowserCapability.CDP_DEBUG, "Tổng quan phiên CDP attach: target/paused/số breakpoint/số worker của từng tab", schema()),
            ToolSpec("browser.debug_attach", BrowserRisk.MEDIUM, BrowserCapability.CDP_DEBUG, "attach tab để bật gỡ lỗi CDP (breakpoint thật + chặn Fetch cấp Worker). attach lặp lại là idempotent; breakpoint sẽ tự phát lại khi re-attach sau detach", NO_ARGS),
            ToolSpec("browser.debug_detach", BrowserRisk.LOW, BrowserCapability.CDP_DEBUG, "Tách kết nối CDP (lược bỏ tab=tất cả). Tự động resume trước detach để chống đóng băng trang", NO_ARGS),
            ToolSpec("browser.debug_set_breakpoint", BrowserRisk.MEDIUM, BrowserCapability.CDP_DEBUG, "Đặt breakpoint tại dòng chỉ định của URL script (line/column đều bắt đầu từ 0). Khi kích hoạt trang sẽ tạm dừng, dùng debug_state xem call stack, debug_eval xem biến; **gỡ lỗi xong bắt buộc debug_resume**. condition là biểu thức điều kiện tùy chọn", DEBUG_SET_BP_SCHEMA),
            ToolSpec("browser.debug_remove_breakpoint", BrowserRisk.LOW, BrowserCapability.CDP_DEBUG, "Gỡ breakpoint theo id (id lấy từ debug_list_breakpoints)", schema(mapOf("id" to "string", "tab" to "string"), listOf("id"))),
            ToolSpec("browser.debug_list_breakpoints", BrowserRisk.LOW, BrowserCapability.CDP_DEBUG, "Liệt kê breakpoint của tab hiện tại", schema(mapOf("tab" to "string"))),
            ToolSpec("browser.debug_resume", BrowserRisk.MEDIUM, BrowserCapability.CDP_DEBUG, "Tiếp tục thực thi (bỏ qua tab = khôi phục tất cả tab đang paused). Trong thời gian tạm dừng, các công cụ trong trang (evaluate/snapshot/click v.v.) sẽ bị đóng băng và từ chối", NO_ARGS),
            ToolSpec("browser.debug_step", BrowserRisk.MEDIUM, BrowserCapability.CDP_DEBUG, "Thực thi từng bước: over (bỏ qua hàm)/into (vào hàm)/out (ra khỏi hàm); khi nhiều tab cùng paused phải chỉ định tab", DEBUG_STEP_SCHEMA),
            ToolSpec("browser.debug_state", BrowserRisk.LOW, BrowserCapability.CDP_DEBUG, "Xem trạng thái tạm dừng: ngăn xếp gọi (frame/tên hàm/vị trí/loại scope) hoặc running", schema(mapOf("tab" to "string"))),
            ToolSpec("browser.debug_eval", BrowserRisk.HIGH, BrowserCapability.CDP_DEBUG, "Tính giá trị biểu thức trên frame tạm dừng (có thể đọc ghi biến cục bộ; returnByValue). frame mặc định 0 (đỉnh stack); kết quả quá dài sẽ cắt còn 64KB", DEBUG_EVAL_SCHEMA),
            ToolSpec("browser.debug_scope", BrowserRisk.MEDIUM, BrowserCapability.CDP_DEBUG, "Đọc biến phạm vi của khung tạm dừng: khi lược bỏ scope thì xuất tóm tắt toàn bộ phạm vi (kiểu + danh sách tên biến), khi chỉ định thì xuất chi tiết tên biến=giá trị của phạm vi đó", DEBUG_SCOPE_SCHEMA),
        )
    }
}



