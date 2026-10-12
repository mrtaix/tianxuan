package top.wkbin.tianxuan.harness.workflow

import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.tianxuan.core.model.ExecutionMode
import top.wkbin.tianxuan.core.model.workflow.NodeExecutionOutput
import top.wkbin.tianxuan.core.model.workflow.NodeRunStatus
import top.wkbin.tianxuan.harness.ApiMessage
import top.wkbin.tianxuan.harness.ProviderClient
import top.wkbin.tianxuan.runtime.gui.HostGuiController
import top.wkbin.tianxuan.runtime.gui.ScreenObservation
import top.wkbin.tianxuan.runtime.gui.ScrollDirection
import top.wkbin.tianxuan.runtime.gui.WorkflowGuiHudBridge
import top.wkbin.tianxuan.runtime.privilege.PrivilegeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/**
 * Workflow-native GUI loop: observe → LLM returns one JSON action → execute.
 * Avoids SubagentLaneRunner (MCP discovery + approval dead-ends) so progress shows in the workflow UI.
 */
class WorkflowGuiPilot(
    private val gui: HostGuiController,
    private val privilegeManager: PrivilegeManager,
    private val providerClient: ProviderClient,
    private val hud: WorkflowGuiHudBridge,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun run(
        goal: String,
        targetPackage: String?,
        modelId: String?,
        modelVariant: String?,
        maxSteps: Int = 18,
        onProgress: suspend (String) -> Unit,
    ): NodeExecutionOutput {
        val info = privilegeManager.getPrivilegeInfo()
        if (info.mode == ExecutionMode.PROOT || !info.modeActive) {
            return NodeExecutionOutput(
                status = NodeRunStatus.FAILED,
                exitCode = 13,
                error = "GUI thử nghiệm cần Shizuku hoặc Root đã được cấp quyền",
            )
        }
        val trimmedGoal = goal.trim()
        if (trimmedGoal.isBlank()) {
            return NodeExecutionOutput(NodeRunStatus.FAILED, exitCode = 2, error = "GUI_GOAL rỗng")
        }

        val log = StringBuilder()
        fun appendLog(line: String) {
            log.appendLine(line)
            Log.i(TAG, line)
        }

        appendLog("Mục tiêu：$trimmedGoal")
        if (!targetPackage.isNullOrBlank()) {
            onProgress("Mở $targetPackage")
            gui.launchApp(targetPackage).onFailure {
                appendLog("Khởi động thất bại: ${it.message}")
            }.onSuccess {
                appendLog(it)
            }
            gui.waitForForeground(targetPackage, 15_000).onSuccess { appendLog(it) }
            delay(1_200)
        }

        val model = providerClient.resolveConfigured(modelId, modelVariant)
            .copy(pureChatMode = true, dynamicMcpTools = emptyList())
        appendLog("Model: ${model.name} / ${model.model} (JSON thuần quyết định, không đi theo giao thức công cụ)")

        var doneReason: String? = null
        var failedReason: String? = null
        var steps = 0

        for (step in 1..maxSteps.coerceIn(1, 40)) {
            coroutineContext.ensureActive()
            if (hud.isStopRequested()) {
                throw CancellationException("Người dùng dừng chạy thử GUI")
            }
            steps = step
            onProgress("Bước $step: cảm nhận màn hình…")
            hud.thinking("Bước $step: cảm nhận màn hình…")
            val observation = gui.observeScreen(onlyInteractive = true).getOrElse { err ->
                appendLog("Cảm nhận thất bại: ${err.message}")
                failedReason = "screen_observe thất bại: ${err.message}"
                break
            }
            appendLog("Tiền cảnh=${observation.packageName} nút=${observation.nodes.size}")
            onProgress("Bước $step: để model quyết định…")
            hud.thinking("Bước $step：model đang ra quyết định…", "${observation.nodes.size} nút")

            val decisionText = runCatching {
                providerClient.chat(
                    model,
                    listOf(
                        ApiMessage(role = "system", content = SYSTEM_PROMPT),
                        ApiMessage(
                            role = "user",
                            content = buildUserPrompt(trimmedGoal, targetPackage, observation, step, maxSteps),
                        ),
                    ),
                ).content.orEmpty()
            }.getOrElse { err ->
                appendLog("Gọi model thất bại: ${err.message}")
                failedReason = "Gọi model thất bại: ${err.message}"
                break
            }

            if (hud.isStopRequested()) {
                throw CancellationException("Người dùng dừng chạy thử GUI")
            }

            val action = parseAction(decisionText)
            if (action == null) {
                appendLog("Không thể phân giải quyết định: ${decisionText.take(400)}")
                onProgress("Bước $step: phân giải quyết định thất bại, thử lại")
                hud.thinking("Bước $step: phân giải quyết định thất bại, thử lại")
                delay(600)
                continue
            }

            val kind = action.string("action")?.lowercase().orEmpty()
            val reason = action.string("reason").orEmpty()
            appendLog("Quyết định=$kind reason=$reason")
            onProgress("Bước $step: thực thi $kind")
            hud.thinking("Bước $step: thực thi $kind", reason)

            when (kind) {
                "done" -> {
                    doneReason = reason.ifBlank { "Model tuyên bố hoàn thành" }
                    break
                }
                "fail" -> {
                    failedReason = reason.ifBlank { "Model tuyên bố thất bại" }
                    break
                }
                "click_text" -> {
                    val needle = action.string("text").orEmpty()
                    if (needle.isBlank()) {
                        appendLog("click_text thiếu text")
                        continue
                    }
                    // 禁止把「待发送文案」当成屏幕按钮去点
                    if (needle.length >= 4 && trimmedGoal.contains(needle) &&
                        observation.nodes.none { it.text.contains(needle) || it.contentDesc.contains(needle) }
                    ) {
                        appendLog("«$needle» không giống control màn hình (giống văn bản chờ gửi hơn), hãy chuyển dùng screen_input_text")
                        continue
                    }
                    val hit = observation.nodes
                        .filter { node ->
                            listOf(node.text, node.contentDesc, node.resourceId)
                                .any { it.contains(needle, ignoreCase = true) }
                        }
                        .sortedWith(
                            compareByDescending<top.wkbin.tianxuan.runtime.gui.GuiNode> { it.text.equals(needle, true) }
                                .thenByDescending { it.clickable }
                                .thenByDescending { it.text.length.coerceAtMost(needle.length * 2) },
                        )
                        .firstOrNull()
                    if (hit == null) {
                        appendLog("Không tìm thấy văn án 「$needle」")
                        continue
                    }
                    // 聊天场景：标题栏「返回」容易把流程抖回去，除非目标就是返回
                    if ((hit.text == "返回" || hit.contentDesc.contains("返回")) &&
                        !trimmedGoal.contains("返回") &&
                        observation.nodes.any { it.editable || it.className.contains("EditText", true) }
                    ) {
                        appendLog("Bỏ qua 「Quay lại」(hiện đã có ô nhập, nên ở lại trang chat)")
                        continue
                    }
                    gui.click(hit.centerX, hit.centerY).fold(
                        onSuccess = { appendLog("$it ← 「$needle」") },
                        onFailure = { appendLog("Nhấp thất bại: ${it.message}") },
                    )
                }
                "screen_click" -> {
                    val x = action.int("x")
                    val y = action.int("y")
                    if (x == null || y == null) {
                        appendLog("screen_click thiếu tọa độ")
                        continue
                    }
                    gui.click(x, y).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Nhấp thất bại: ${it.message}") },
                    )
                }
                "screen_swipe" -> {
                    val x1 = action.int("x1")
                    val y1 = action.int("y1")
                    val x2 = action.int("x2")
                    val y2 = action.int("y2")
                    if (listOf(x1, y1, x2, y2).any { it == null }) {
                        appendLog("screen_swipe thiếu tọa độ")
                        continue
                    }
                    gui.swipe(x1!!, y1!!, x2!!, y2!!, action.int("duration_ms")?.toLong() ?: 300L).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Vuốt thất bại: ${it.message}") },
                    )
                }
                "screen_input_text" -> {
                    val text = action.string("text").orEmpty()
                    if (text.isBlank()) {
                        appendLog("screen_input_text thiếu text")
                        continue
                    }
                    // 先点到可编辑框，再剪贴板粘贴（中文不能走 input text）
                    val editable = observation.nodes.firstOrNull { it.editable }
                        ?: observation.nodes.firstOrNull {
                            it.className.contains("EditText", ignoreCase = true) ||
                                it.contentDesc.contains("输入", ignoreCase = true) ||
                                it.text.contains("输入", ignoreCase = true)
                        }
                    if (editable != null) {
                        gui.click(editable.centerX, editable.centerY).onSuccess {
                            appendLog("Trước tiên lấy nét khung nhập center=(${editable.centerX},${editable.centerY})")
                        }
                        delay(250)
                    }
                    gui.inputText(text).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Nhập thất bại: ${it.message}") },
                    )
                }
                "screen_scroll" -> {
                    val dir = when (action.string("direction")?.lowercase()) {
                        "up" -> ScrollDirection.UP
                        "down" -> ScrollDirection.DOWN
                        "left" -> ScrollDirection.LEFT
                        "right" -> ScrollDirection.RIGHT
                        else -> {
                            appendLog("screen_scroll thiếu direction hợp lệ")
                            continue
                        }
                    }
                    gui.scroll(dir).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Cuộn thất bại: ${it.message}") },
                    )
                }
                "screen_double_click" -> {
                    val x = action.int("x")
                    val y = action.int("y")
                    if (x == null || y == null) {
                        appendLog("screen_double_click thiếu tọa độ")
                        continue
                    }
                    gui.doubleClick(x, y).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Nhấn đúp thất bại: ${it.message}") },
                    )
                }
                "screen_long_press" -> {
                    val x = action.int("x")
                    val y = action.int("y")
                    if (x == null || y == null) {
                        appendLog("screen_long_press thiếu tọa độ")
                        continue
                    }
                    gui.longPress(x, y, action.int("duration_ms")?.toLong() ?: 800L).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Nhấn giữ thất bại: ${it.message}") },
                    )
                }
                "screen_key" -> {
                    val key = action.string("key").orEmpty()
                    if (key.isBlank()) {
                        appendLog("screen_key thiếu key")
                        continue
                    }
                    gui.sendKey(key).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Nhấn phím thất bại: ${it.message}") },
                    )
                }
                "app_launch" -> {
                    val pkg = action.string("package").orEmpty().ifBlank { targetPackage.orEmpty() }
                    if (pkg.isBlank()) {
                        appendLog("app_launch thiếu package")
                        continue
                    }
                    gui.launchApp(pkg).fold(
                        onSuccess = { appendLog(it) },
                        onFailure = { appendLog("Khởi động thất bại: ${it.message}") },
                    )
                }
                else -> appendLog("Hành động không xác định: $kind")
            }
            // 不再固定等待：无障碍服务可用时按窗口变化事件判断界面是否已稳定，无服务则回退 900ms
            gui.awaitUiSettled()
        }

        // 步数耗尽且模型未宣布 done：目标未达成，必须判 FAILED（exitCode 非零），
        // 不得以 SUCCESS/exitCode 0 返回误导调用方（步数上限与循环一致取 1..40 收敛值）
        val stepBudget = maxSteps.coerceIn(1, 40)
        val exhausted = failedReason == null && doneReason == null && steps >= stepBudget
        val effectiveFailure = failedReason
            ?: if (exhausted) "Cạn số bước mà chưa đạt mục tiêu (model chưa tuyên bố hoàn thành trong ${stepBudget} bước)" else null
        val success = effectiveFailure == null
        val summary = buildString {
            appendLine(if (doneReason != null) "✔ $doneReason" else if (effectiveFailure != null) "✘ $effectiveFailure" else "Đạt số bước tối đa $maxSteps")
            appendLine()
            append(log.toString().trimEnd())
        }
        onProgress(if (success) "Kết thúc chạy thử GUI" else "Chạy thử GUI chưa hoàn thành")
        return NodeExecutionOutput(
            status = if (success) NodeRunStatus.SUCCESS else NodeRunStatus.FAILED,
            exitCode = if (success) 0 else 1,
            textOutput = summary,
            error = effectiveFailure,
            variables = mapOf(
                "GUI_PILOT_STEPS" to steps.toString(),
                "GUI_PILOT_DONE" to (doneReason != null).toString(),
            ),
        )
    }

    private fun parseAction(raw: String): JsonObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching {
            json.parseToJsonElement(raw.substring(start, end + 1)).jsonObject
        }.getOrNull()
    }

    private fun JsonObject.string(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.int(key: String): Int? =
        this[key]?.jsonPrimitive?.intOrNull
            ?: this[key]?.jsonPrimitive?.contentOrNull?.toIntOrNull()

    private fun buildUserPrompt(
        goal: String,
        targetPackage: String?,
        observation: ScreenObservation,
        step: Int,
        maxSteps: Int,
    ): String = buildString {
        appendLine("## Mục tiêu")
        appendLine(goal)
        if (!targetPackage.isNullOrBlank()) appendLine("Tên gói mục tiêu: $targetPackage")
        appendLine("Tiến độ: bước $step / $maxSteps")
        appendLine()
        appendLine("## Màn hình hiện tại")
        append(observation.toAgentSummary(maxNodes = 60))
        appendLine()
        appendLine("Chỉ xuất ra một đối tượng JSON. Gửi tin nhắn tiếng Trung: trước tiên lấy nét khung nhập, rồi screen_input_text (hệ thống sẽ tự động dán clipboard); sau đó click_text 「Gửi」. Đừng nhấn 「Quay lại」 qua lại lung tung.")
    }

    private companion object {
        const val TAG = "TianXuan-GuiPilot"
        val SYSTEM_PROMPT = """
你是 Android GUI 驾驶员。根据屏幕控件树推进用户目标。
只输出一个 JSON 对象，字段：
- action: click_text | screen_click | screen_double_click | screen_long_press | screen_swipe | screen_scroll | screen_input_text | screen_key | app_launch | done | fail
- text: click_text / screen_input_text 用
- x,y / x1,y1,x2,y2,duration_ms: 坐标动作
- direction: up|down|left|right（screen_scroll）
- key: back|home|recents|enter|delete|paste
- package: app_launch
- reason: 简短中文说明

规则：
1. 优先 click_text，text 必须来自当前屏幕已有节点（群名/发送/搜索等），绝不要用待发送正文当 click_text。
2. 输入任意中文或长文本：用 screen_input_text（底层剪贴板粘贴）。列表不够时用 screen_scroll。
3. 进入聊天且已有输入框后：禁止点「返回」；应 screen_input_text → click_text「发送」。
4. 禁止付款/红包/转账；若出现此类界面输出 {"action":"fail","reason":"..."}。
5. 目标完成后 {"action":"done","reason":"..."}。
6. 不要输出 Markdown 或其它文字。
        """.trimIndent()
    }
}
