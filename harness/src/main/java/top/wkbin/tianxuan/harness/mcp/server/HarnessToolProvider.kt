package top.wkbin.tianxuan.harness.mcp.server

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import top.wkbin.tianxuan.harness.HarnessApiMapper
import top.wkbin.tianxuan.harness.HarnessTool
import top.wkbin.tianxuan.harness.ProviderClient
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolExecutor
import top.wkbin.tianxuan.runtime.browser.secret.SecretRedactingInterceptor
import java.util.UUID

/**
 * 被控端（外部 AI 客户端控制本 App）暴露的 Harness 工具集合，按能力分层：
 *
 * - **只读层（默认开启）**：`read`、`host`（仅只读 action）、`memory`（仅 query/list）。
 * - **写入与执行层（默认关闭）**：`write` / `edit` / `base` / `process` / `download`，
 *   以及 `host` 的其余 action。需用户在设置页显式开启 [allowWriteTools]。
 *
 * 会话绑定类工具（history_* / compress / plan / scratchpad / subagent / ask_user /
 * use_capability / load_skill / build_script）一律不暴露：它们要么依赖 App 内会话上下文，
 * 要么会递归回 MCP 造成环路。
 *
 * 执行复用 [ToolExecutor]；`sessionId` 恒为空串 —— 被控端没有 App 内会话可承载审批暂停，
 * ToolExecutor 对空 sessionId 天然跳过审批策略，因此写操作的安全性完全由 [allowWriteTools] 门控。
 */
class HarnessToolProvider(
    private val executor: ToolExecutor,
) : McpToolProvider {

    /** 写入与执行层开关；由 [AgentMcpBootstrap] 按用户偏好实时刷新。 */
    @Volatile var allowWriteTools: Boolean = false

    override fun listTools(): List<JsonObject> {
        val allowed = exposedNames()
        return ProviderClient.TOOLS
            .filter { it.function.name in allowed }
            .map { def ->
                buildJsonObject {
                    put("name", JsonPrimitive(def.function.name))
                    put("description", JsonPrimitive(def.function.description))
                    put("inputSchema", def.function.parameters)
                }
            }
    }

    override suspend fun callTool(name: String, args: JsonObject): JsonObject {
        if (name !in exposedNames()) {
            return textResult("Công cụ 「$name」chưa mở cho phía bị điều khiển MCP.", isError = true)
        }
        val tool = HarnessApiMapper.toolByName(name)
        denialReason(name, tool, args)?.let { return textResult(it, isError = true) }

        val call = ToolCall(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            tool = tool,
            args = args,
            rawToolName = name,
        )
        val result = try {
            executor.execute(
                toolCall = call,
                sessionId = "",
                workspace = "",
                allowApprovalRequest = false,
            )
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            return textResult("Thực thi công cụ thất bại: ${t.message ?: t::class.simpleName}", isError = true)
        }
        return textResult(result.output, isError = !result.success)
    }

    private fun exposedNames(): Set<String> =
        READ_ONLY_TOOL_NAMES + if (allowWriteTools) WRITE_TOOL_NAMES else emptySet()

    private fun denialReason(name: String, tool: HarnessTool, args: JsonObject): String? = when (tool) {
        HarnessTool.READ -> null
        HarnessTool.HOST -> {
            val action = args.stringArg("action").lowercase()
            when {
                action.isBlank() -> "Lời gọi host thiếu tham số action."
                action in READ_ONLY_HOST_ACTIONS -> null
                allowWriteTools -> null
                else -> writeDenied("host action「$action」")
            }
        }
        HarnessTool.MEMORY -> {
            val action = args.stringArg("action").ifBlank { "list" }.lowercase()
            when {
                action in READ_ONLY_MEMORY_ACTIONS -> null
                allowWriteTools -> null
                else -> writeDenied("memory action「$action」")
            }
        }
        HarnessTool.WRITE, HarnessTool.EDIT, HarnessTool.BASE, HarnessTool.PROCESS, HarnessTool.DOWNLOAD ->
            if (allowWriteTools) null else writeDenied("Công cụ 「$name」")
        else -> "Công cụ 「$name」chưa mở cho phía bị điều khiển MCP."
    }

    private fun writeDenied(what: String): String =
        "$what thuộc tầng ghi/thực thi, hiện chưa bật. Hãy bật 「Cho phép ghi và thực thi」trong 「Cài đặt → MCP → Phía máy chủ (bên bị điều khiển)」rồi thử lại."

    private fun JsonObject.stringArg(key: String): String =
        (this[key] as? JsonPrimitive)?.content?.trim().orEmpty()

    private fun textResult(text: String, isError: Boolean): JsonObject = buildJsonObject {
        put("content", JsonArray(listOf(buildJsonObject {
            put("type", JsonPrimitive("text"))
            // 统一脱敏出口：文件内容/命令输出可能夹带密钥，防止直接回灌外部客户端
            put("text", JsonPrimitive(SecretRedactingInterceptor.apply(text)))
        })))
        put("isError", JsonPrimitive(isError))
    }

    private companion object {
        /** 只读层：默认开放。 */
        val READ_ONLY_TOOL_NAMES = setOf("read", "host", "memory")

        /** 写入与执行层：需用户显式开启。 */
        val WRITE_TOOL_NAMES = setOf("write", "edit", "base", "process", "download")

        /**
         * host 中不改变系统状态的 action；与 [top.wkbin.tianxuan.harness.ApprovalPolicyEngine]
         * 的 `HOST_READ_ONLY_ACTIONS` 保持同一口径。
         * 刻意不含 `screen_capture`：它会把真实屏幕内容写入文件，属隐私敏感的写入动作。
         */
        val READ_ONLY_HOST_ACTIONS = setOf(
            "status", "device_status", "logcat", "app_list",
            "package_list", "screen_observe", "settings_get",
        )

        /** memory 中只读取记忆的 action（`search` 是 `query` 的别名）。 */
        val READ_ONLY_MEMORY_ACTIONS = setOf("query", "search", "list")
    }
}
