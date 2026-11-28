package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

/**
 * MCP (Model Context Protocol) 传输协议类型
 */
@Serializable
enum class McpTransportType {
    STDIO,  // 在 Linux 沙箱内通过标准输入输出与进程交互
    /**
     * 远程 HTTP 服务。客户端按 URL 自动协商：URL 不以 /sse 结尾时先 Streamable HTTP
     * （POST /mcp 请求-响应），失败再回落 legacy HTTP+SSE；服务端只需实现其一。
     */
    SSE,
}
/**
 * MCP 服务配置
 */
@Serializable
data class McpServerConfig(
    val id: String,
    val name: String,
    val description: String = "",
    val transportType: McpTransportType = McpTransportType.STDIO,
    /** 沙箱内执行命令（例如 "npx" 或 "python3"） */
    val command: String = "",
    /** 命令行参数列表（例如 ["-y", "@modelcontextprotocol/server-sqlite", "--db-path", "/workspace/app.db"]） */
    val args: List<String> = emptyList(),
    /** 环境变量 */
    val env: Map<String, String> = emptyMap(),
    /** 远程 MCP 服务的 HTTP 端点 URL（例如 http://127.0.0.1:8000/mcp 或 .../sse） */
    val serverUrl: String = "",
    /** Authentication mode for remote HTTP MCP services. */
    val authMode: McpAuthMode = McpAuthMode.NONE,
    /** Public OAuth client configuration; secrets are never stored here. */
    val oauthClientId: String = "",
    val oauthAuthorizationEndpoint: String = "",
    val oauthTokenEndpoint: String = "",
    val oauthRedirectUri: String = "tianxuan://oauth/mcp",
    val oauthScope: String = "",
    val oauthResource: String = "",
    /**
     * 远程 MCP 服务的 Bearer token（发送 `Authorization: Bearer <token>`）。
     * 运行时注入用（例如内置 browser server 的自环凭据）：@Transient 保证不参与序列化，
     * 既不落 Room 也不随 [toExportJsonConfig] 导出。
     */
    @kotlinx.serialization.Transient
    val authToken: String = "",
    /** 是否启用 */
    val isEnabled: Boolean = true,
    /** 是否为内置预设服务 */
    val isBuiltin: Boolean = false,
    /** 内置 server 默认风险等级（如 "low" / "medium" / "high" / "critical"），MCP bootstrap 据此判定审批粒度。 */
    val builtinRisk: String = "low",
    /** 内置 server bootstrap 优先级。< 0 表示排在所有用户 MCP server 之前；>= 0 表示标准用户配置。 */
    val bootstrapOrder: Int = if (isBuiltin) -1 else 0,

) {
    /** 导出为 mcpServers JSON 配置片段（键为服务 id）；配置格式约定归模型层，View 只读展示 */
    fun toExportJsonConfig(): String = buildString {
        appendLine("{")
        appendLine("  \"$id\": {")
        if (transportType == McpTransportType.STDIO) {
            appendLine("    \"command\": \"$command\",")
            appendLine("    \"args\": [${args.joinToString(", ") { "\"$it\"" }}]")
            if (env.isNotEmpty()) {
                appendLine("    \"env\": {")
                env.entries.forEachIndexed { i, (k, v) ->
                    val comma = if (i == env.size - 1) "" else ","
                    appendLine("      \"$k\": \"$v\"$comma")
                }
                appendLine("    }")
            }
        } else {
            appendLine("    \"url\": \"$serverUrl\"")
        }
        appendLine("  }")
        append("}")
    }
}

/**
 * MCP 服务连通性检测状态（运行时状态，不持久化）
 */
enum class McpConnectionState {
    /** 尚未检测（默认态 / 服务未启用） */
    UNKNOWN,
    /** 正在检测连通性 */
    CHECKING,
    /** 已连通，服务在线可用 */
    ONLINE,
    /** 检测失败 / 服务离线不可达 */
    OFFLINE,
}

/**
 * 动态从 MCP Server 发现并注册的工具定义
 */
@Serializable
data class McpToolInfo(
    val serverId: String,
    val serverName: String,
    val name: String,
    val description: String,
    val parametersJson: String = "{}",
)

/**
 * 子智能体任务规格参数
 */
@Serializable
data class SubagentTaskSpec(
    val taskName: String,
    /** Optional exact profile id/name override retained for persisted and legacy calls. */
    val role: String = "",
    val prompt: String,
    /** Built-in department id used to constrain local catalog matching. */
    val department: String = "",
    /** Short English professional keywords matched against id/name/description locally. */
    val agentQuery: String = "",
    /** 子任务声明的写目标文件/目录。空列表 = 只读并行；["*"] = 整工作区独占写租约。 */
    val writePaths: List<String> = emptyList(),
    /** 可选：已保存模型档案的 ID/名称，或该档案中已配置的具体模型名；null/"inherit" 继承父会话。 */
    val model: String? = null,
    /**
     * 可选：续跑目标（对齐 opencode task 工具的 task_id resume）。
     * 填入上次 invoke_subagent 汇总中的 task_id（即子任务 Lane 名）时，
     * 本次在该 Lane 已有对话上继续执行（prompt 为续跑指令），而不是创建全新 Lane。
     */
    val taskId: String? = null,
)

/**
 * 内置 MCP 预设服务
 */
object BuiltinMcpPresets {
    /** 内置 in-app browser MCP server 的 id：自环 Bearer 认证与浏览器工具审批白名单都以它为准。 */
    const val BROWSER_BUILTIN_ID = "tianxuan-browser-builtin"

    val presets: List<McpServerConfig> = listOf(
        McpServerConfig(
            id = "mcp_sqlite",
            name = "Trình khám phá cơ sở dữ liệu SQLite",
            description = "Truy vấn, phân tích và thao tác với tệp cơ sở dữ liệu SQLite trong sandbox hoặc workspace",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/tianxuan/scripts/sqlite_mcp_server.py", "--db-path", "/root/tianxuan.db"),
            isEnabled = true,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_git",
            name = "Trung tâm cộng tác kho Git",
            description = "Phân tích sâu lịch sử commit Git, cấu trúc nhánh, khác biệt Diff và trạng thái workspace",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/tianxuan/scripts/git_mcp_server.py", "--repository", "/workspace"),
            isEnabled = true,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_apktool",
            name = "Phân tích ngược Android và kiểm toán APK",
            description = "Tự động dịch ngược APK, phân tích quyền trong manifest, trích xuất thông tin xác thực hardcode, tìm mã nhạy cảm Smali và đóng gói/ký lại (dịch vụ MCP tích hợp TianXuan, phụ thuộc toolchain apktool/jadx/aapt của android-suite)",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/tianxuan/scripts/apktool_mcp_server.py"),
            isEnabled = true,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_codegraph",
            name = "Đồ thị tri thức mã CodeGraph",
            description = "Lập chỉ mục nhanh định nghĩa ký hiệu, chuỗi gọi và phạm vi ảnh hưởng trong workspace (hỗ trợ Python/Java/Kotlin/C/C++/JS/TS/Smali), giảm đáng kể Token và số lượt gọi công cụ khi khảo sát mã",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/tianxuan/scripts/codegraph_mcp_server.py", "--repository", "/workspace"),
            isEnabled = true,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = "mcp_websearch",
            name = "Tìm kiếm web (Open-WebSearch)",
            description = "Tìm kiếm web đa công cụ và trích xuất nội dung trang không cần API Key (dịch vụ MCP tích hợp TianXuan, không phụ thuộc): hỗ trợ tìm kiếm trực tiếp Baidu, Bing, DuckDuckGo và làm sạch nội dung, phản hồi nhanh, không cần Node.js/npx, dùng ngay",
            transportType = McpTransportType.STDIO,
            command = "python3",
            args = listOf("-u", "/opt/tianxuan/scripts/websearch_mcp_server.py"),
            env = mapOf(
                "DEFAULT_SEARCH_ENGINE" to "baidu",
            ),
            isEnabled = true,
            isBuiltin = true,
        ),
        McpServerConfig(
            id = BROWSER_BUILTIN_ID,
            name = "TianXuan Browser (Built-in)",
            description = "TianXuan 内置 in-app WebView 的 MCP server（in-process loopback 127.0.0.1:8787，Streamable HTTP POST /mcp 请求-响应，Bearer 认证）；由 harness 层 BrowserMcpBootstrap 启动，暴露 mcp__browser__* 工具给 Agent 与外接 IDE。",
            transportType = McpTransportType.SSE,
            serverUrl = "http://127.0.0.1:8787/mcp",
            isEnabled = true,
            isBuiltin = true,
            builtinRisk = "medium",
            bootstrapOrder = -100,
        ),
    )
}
