package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeFilledTonalButton as FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import top.wkbin.tianxuan.ui.components.RuntimeRadioButton as RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.tianxuan.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.SecondaryTabRow
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.material3.Text as MaterialText
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.tianxuan.core.model.McpConnectionState
import top.wkbin.tianxuan.core.model.McpAuthMode
import top.wkbin.tianxuan.core.model.McpAuthState
import top.wkbin.tianxuan.core.model.McpServerConfig
import top.wkbin.tianxuan.core.model.McpToolInfo
import top.wkbin.tianxuan.core.model.McpTransportType
import top.wkbin.tianxuan.harness.mcp.server.AgentMcpAccess
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

@Composable
fun McpSettingsScreen(
    onBack: () -> Unit,
    onOpenMonitor: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val servers by viewModel.mcpServers.collectAsStateWithLifecycle()
    val toggleOverrides by viewModel.mcpToggleOverrides.collectAsStateWithLifecycle()
    val connectionStates by viewModel.mcpConnectionStates.collectAsStateWithLifecycle()
    val mcpAuthStates by viewModel.mcpAuthStates.collectAsStateWithLifecycle()
    val browserGates by viewModel.browserGates.collectAsStateWithLifecycle()
    val screenScope = rememberCoroutineScope()
    val screenContext = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var showLocalDiscoveryDialog by remember { mutableStateOf(false) }
    var viewingDetailServer by remember { mutableStateOf<McpServerConfig?>(null) }
    // 待确认删除的服务 id（破坏性操作二次确认）；McpServerConfig 非 Parcelable，仅保存 id
    var pendingDeleteServerId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteServer = pendingDeleteServerId?.let { id -> servers.firstOrNull { it.id == id } }

    // 进入设置页时自动探测一次所有已启用 MCP 服务的连通性
    LaunchedEffect(Unit) { viewModel.refreshMcpConnections() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "MCP 插件与协议生态",
                statusText = "已就绪",
                onBack = onBack,
                actions = {
                    androidx.compose.material3.TextButton(onClick = onOpenMonitor) {
                        Text("Giám sát dịch vụ")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                // Banner Card
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                RuntimeIcon(RuntimeIconName.Network, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                            }
                            Text(
                                "Model Context Protocol (MCP)",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            "MCP 是开放的标准模型上下文协议。开启后，天玄将在 PRoot 沙箱内启动对应的 Stdio 服务或连接本地 SSE 端点，并动态向智枢 Agent 注入专业工具能力。",
                            style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Dịch vụ plugin MCP khả dụng (${servers.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { viewModel.refreshMcpConnections() },
                            modifier = Modifier.size(30.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(16.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(
                            onClick = {
                                showLocalDiscoveryDialog = true
                                viewModel.discoverLocalMcpServers()
                            },
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(RuntimeIconName.Search, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                                Text("Dò cục bộ của máy", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        TextButton(onClick = { showAddDialog = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                                Text("添加服务", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            val builtinServers = servers.filter { it.isBuiltin }
            val customServers = servers.filterNot { it.isBuiltin }

            if (builtinServers.isNotEmpty()) {
                item(key = "mcp_section_builtin") {
                    McpSectionHeader(
                        title = "MCP lõi hệ thống",
                        subtitle = "Năng lực cấp hệ thống tích hợp của Thiên Huyền, mặc định tắt; sau khi bật sẽ thường trú trong phiên này, harness có thể gọi bất cứ lúc nào (không tham gia đánh thức @)",
                        count = builtinServers.size,
                    )
                }
                items(builtinServers, key = { it.id }) { server ->
                    McpServerItemCard(
                        server = server,
                        connectionState = connectionStates[server.id] ?: McpConnectionState.UNKNOWN,
                        togglePending = server.id in toggleOverrides,
                        onToggle = { enabled -> viewModel.toggleMcpServer(server.id, enabled) },
                        onClickDetail = { viewingDetailServer = server },
                    )
                }
            }

            if (customServers.isNotEmpty()) {
                item(key = "mcp_section_custom") {
                    McpSectionHeader(
                        title = "MCP tùy chỉnh và bên thứ ba",
                        subtitle = "Dịch vụ MCP bên ngoài nhập qua「Thêm dịch vụ」, có thể @ để đánh thức",
                        count = customServers.size,
                    )
                }
                items(customServers, key = { it.id }) { server ->
                    McpServerItemCard(
                        server = server,
                        connectionState = connectionStates[server.id] ?: McpConnectionState.UNKNOWN,
                        togglePending = server.id in toggleOverrides,
                        onToggle = { enabled -> viewModel.toggleMcpServer(server.id, enabled) },
                        onClickDetail = { viewingDetailServer = server },
                    )
                }
            }

            item(key = "mcp_section_browser_gates") {
                BrowserGatesCard(gates = browserGates, viewModel = viewModel)
            }

            item(key = "mcp_section_agent_server") {
                AgentServerCard(
                    state = viewModel.agentServerState.collectAsStateWithLifecycle().value,
                    viewModel = viewModel,
                )
            }

            item {
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAddDialog) {
        AddMcpServerDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { newServer ->
                viewModel.saveMcpServer(newServer)
                showAddDialog = false
            },
            onAddBatch = { serversList ->
                serversList.forEach { viewModel.saveMcpServer(it) }
                showAddDialog = false
            },
        )
    }

    if (showLocalDiscoveryDialog) {
        LocalMcpDiscoveryDialog(
            state = viewModel.localMcpDiscovery.collectAsStateWithLifecycle().value,
            onDismiss = { showLocalDiscoveryDialog = false },
            onRetry = viewModel::discoverLocalMcpServers,
            onAdd = viewModel::saveMcpServer,
        )
    }

    pendingDeleteServer?.let { server ->
        RuntimeAlertDialog(
            onDismissRequest = { pendingDeleteServerId = null },
            title = { Text("Xóa dịch vụ MCP", fontWeight = FontWeight.Bold) },
            text = {
                Text("Bạn có chắc muốn xóa «${server.name}» không? Sau khi xóa sẽ dừng tiêm năng lực công cụ của dịch vụ này vào Agent Trí Khu; mở lại MCP lõi hệ thống là có thể khôi phục.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMcpServer(server.id)
                        pendingDeleteServerId = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Xác nhận xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteServerId = null }) { Text("取消") }
            },
        )
    }

    viewingDetailServer?.let { server ->
        McpServerDetailDialog(
            server = server,
            onDismiss = { viewingDetailServer = null },
            onDelete = {
                pendingDeleteServerId = server.id
                viewingDetailServer = null
            },
            onTest = {
                val res = viewModel.testMcpServer(server)
                viewModel.refreshMcpConnections()
                res
            },
            authState = mcpAuthStates[server.id] ?: McpAuthState.Unsupported,
            onAuthorize = {
                screenScope.launch {
                    runCatching { viewModel.beginMcpAuthorization(server) }
                        .onSuccess {
                            runCatching { screenContext.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))) }
                                .onFailure { error -> Toast.makeText(screenContext, "Khởi động phân quyền thất bại: ${error.message}", Toast.LENGTH_SHORT).show() }
                        }
                        .onFailure { Toast.makeText(screenContext, "Khởi động cấp quyền thất bại: ${it.message}", Toast.LENGTH_SHORT).show() }
                }
            },
            onLogout = { viewModel.logoutMcpAuthorization(server.id) },
        )
    }
}

/** 仅展示已通过 MCP 协议握手的 127.0.0.1 服务；用户确认后才写入配置。 */
@Composable
private fun LocalMcpDiscoveryDialog(
    state: LocalMcpDiscoveryState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onAdd: (McpServerConfig) -> Unit,
) {
    var addedIds by remember { mutableStateOf(emptySet<String>()) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dò MCP cục bộ của máy", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Đang kiểm tra cổng lắng nghe của 127.0.0.1 và xác minh bằng giao thức MCP. Chỉ hiển thị dịch vụ thực sự khả dụng, không tự động thêm.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when (state) {
                    LocalMcpDiscoveryState.Idle, LocalMcpDiscoveryState.Scanning -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("Đang dò dịch vụ cục bộ của máy…", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    is LocalMcpDiscoveryState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    is LocalMcpDiscoveryState.Results -> {
                        if (state.servers.isEmpty()) {
                            Text(
                                "Không phát hiện dịch vụ MCP cục bộ khả dụng. Hãy xác nhận dịch vụ đã khởi động và lắng nghe trên 127.0.0.1.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.servers.forEach { detected ->
                                RuntimeCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                    contentPadding = PaddingValues(10.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                detected.serverUrl,
                                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                "Đã xác minh · ${detected.toolCount} công cụ",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        if (detected.server.id in addedIds) {
                                            Text("Đã thêm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            OutlinedButton(
                                                onClick = {
                                                    onAdd(detected.server)
                                                    addedIds = addedIds + detected.server.id
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            ) { Text("添加", style = MaterialTheme.typography.labelSmall) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (state) {
                LocalMcpDiscoveryState.Scanning -> Unit
                else -> Button(onClick = onRetry) { Text("Dò lại") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/**
 * 浏览器 MCP 安全门禁卡片：内置浏览器服务的危险能力开关（最小权限，默认全关）。
 * 门禁为引擎池级/工具快照级配置，修改后需重启应用生效。
 */
@Composable
private fun BrowserGatesCard(gates: BrowserGateState, viewModel: SettingsViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Cổng kiểm soát an toàn MCP trình duyệt",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Công tắc năng lực nguy hiểm của dịch vụ trình duyệt tích hợp, mặc định tắt hết; sau khi sửa cần khởi động lại ứng dụng mới có hiệu lực",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BrowserGateRow(
                title = "Kết nối từ xa",
                description = "Cho phép thiết bị LAN kết nối dịch vụ MCP trình duyệt (bind 0.0.0.0); khi tắt chỉ giới hạn loopback cục bộ, vẫn bắt buộc xác thực Bearer",
                checked = gates.allowRemoteConnect,
                onCheckedChange = viewModel::setBrowserAllowRemoteConnect,
            )
            BrowserGateRow(
                title = "Thực thi JS trang",
                description = "Cho phép Agent thực thi JavaScript tùy ý trong ngữ cảnh trang (browser.evaluate)",
                checked = gates.allowEvalJs,
                onCheckedChange = viewModel::setBrowserAllowEvalJs,
            )
            BrowserGateRow(
                title = "Hook kiểu tiêm",
                description = "Cho phép tiêm script chặn/ghi lại yêu cầu mạng của trang và hàm JS (log/block/redirect/mock)",
                checked = gates.allowHooks,
                onCheckedChange = viewModel::setBrowserAllowHooks,
            )
            BrowserGateRow(
                title = "Gỡ lỗi CDP (breakpoint)",
                description = "Cho phép attach trang qua giao thức DevTools: breakpoint thật, đọc ghi biến khi tạm dừng, chặn yêu cầu cấp Worker; bật sẽ phơi bày DevTools socket của tiến trình này (các ứng dụng khác trên cùng thiết bị về lý thuyết có thể kết nối), chỉ dùng trong môi trường được kiểm soát",
                checked = gates.allowCdp,
                onCheckedChange = viewModel::setBrowserAllowCdp,
            )
            BrowserGateRow(
                title = "Bảng debug trang (vConsole)",
                description = "Tiêm bảng nổi vConsole (console / mạng / lưu trữ) vào mỗi trang để tra lỗi khi duyệt thủ công; không liên quan tới cổng kiểm soát công cụ Agent, có hiệu lực với tab mới mở sau khi khởi động lại",
                checked = gates.allowVConsole,
                onCheckedChange = viewModel::setBrowserAllowVConsole,
            )
        }
    }
}

@Composable
private fun BrowserGateRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * MCP 被控端（服务端）卡片：让外部 AI 客户端（Claude Desktop / Cursor 等）通过 MCP 控制本 App。
 *
 * 与上面的浏览器门禁不同，被控端是**独立端点**（默认 8890）且使用**持久化令牌**，
 * 配置变更由 AgentMcpBootstrap 监听偏好后即时重启，无需重启应用。
 */
@Composable
private fun AgentServerCard(state: AgentServerState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val lanIp = remember { detectLanIp() }
    // 配置变更后 Bootstrap 异步重启，延迟一拍再读实际监听状态，避免刚切换时显示过期状态
    var runtimeTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.enabled, state.port, state.allowRemote, state.token) {
        runtimeTick++
        kotlinx.coroutines.delay(700)
        runtimeTick++
    }
    val running = remember(runtimeTick) { AgentMcpAccess.running }
    val boundPort = remember(runtimeTick) { AgentMcpAccess.port ?: state.port }

    var portText by remember(state.port) { mutableStateOf(state.port.toString()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "Phía server (bên bị điều khiển)",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "Cho phép client AI bên ngoài (Claude Desktop / Cursor v.v.) điều khiển máy này qua MCP: đọc ghi tệp workspace, thực thi lệnh sandbox, thao tác ứng dụng máy chủ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrowserGateRow(
                title = "Bật phía bị điều khiển",
                description = "Khởi động endpoint dịch vụ MCP độc lập trên máy; sau khi tắt sẽ dừng lắng nghe ngay lập tức",
                checked = state.enabled,
                onCheckedChange = viewModel::setAgentServerEnabled,
            )

            AnimatedVisibility(visible = state.enabled) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BrowserGateRow(
                        title = "Cho phép kết nối mạng LAN",
                        description = "Gắn 0.0.0.0, thiết bị cùng phân mạng có thể kết nối; khi tắt chỉ giới hạn loopback cục bộ, cả hai chế độ đều cưỡng chế xác thực Bearer",
                        checked = state.allowRemote,
                        onCheckedChange = viewModel::setAgentServerAllowRemote,
                    )
                    BrowserGateRow(
                        title = "Cho phép ghi và thực thi",
                        description = "Mở các thao tác ghi của write/edit/base/process/download và host; khi tắt chỉ cung cấp read và các động tác host/memory chỉ đọc",
                        checked = state.allowWriteTools,
                        onCheckedChange = viewModel::setAgentServerAllowWriteTools,
                    )

                    // 端口
                    OutlinedTextField(
                        value = portText,
                        onValueChange = { input ->
                            portText = input.filter { it.isDigit() }.take(5)
                            portText.toIntOrNull()?.let(viewModel::setAgentServerPort)
                        },
                        label = { Text("Cổng lắng nghe") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        supportingText = { Text("Mặc định 8890; khi bị chiếm dụng tự động dời sang 10 cổng lân cận", style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // 运行状态与连接地址
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (running) successStatusColor() else MaterialTheme.colorScheme.outline),
                            )
                            Text(
                                if (running) "Đang chạy · lắng nghe ${boundPort}" else "已停止",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        val loopbackUrl = "http://127.0.0.1:$boundPort/mcp"
                        val lanUrl = lanIp?.let { "http://$it:$boundPort/mcp" }
                        CopyableLine(label = "Địa chỉ máy này", value = loopbackUrl, context = context)
                        if (state.allowRemote && lanUrl != null) {
                            CopyableLine(label = "Địa chỉ LAN", value = lanUrl, context = context)
                        }
                    }

                    // 持久化令牌
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Access token (Bearer)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = state.token.ifBlank { "Tự động tạo sau khi bật" },
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(
                                    onClick = {
                                        copyToClipboard(context, state.token, "mcp_agent_token", "Token đã sao chép")
                                    },
                                    enabled = state.token.isNotBlank(),
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    RuntimeIcon(RuntimeIconName.Copy, Modifier.size(14.dp), MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "Token lưu bền vững, client ngoài có thể tái dùng lâu dài; sau khi đặt lại cần đồng bộ cập nhật cấu hình client",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = viewModel::resetAgentServerToken) {
                                Text("Reset token", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    Text(
                        "Ví dụ cấu hình client ngoài: điền địa chỉ trên vào cấu hình MCP, và kèm Authorization=[REDACTED] <token> trong header yêu cầu.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Text(
                        "Sau khi bật sẽ hiển thị một thông báo thường trú để giữ sống nền, tránh kết nối bị hệ thống ngắt khi App vào nền.",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 一行「标签 + 等宽值 + 复制按钮」，用于展示连接地址。 */
@Composable
private fun CopyableLine(label: String, value: String, context: Context) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(
            onClick = {
                copyToClipboard(context, value, "mcp_agent_url", "Đã sao chép địa chỉ")
            },
            modifier = Modifier.size(24.dp),
        ) {
            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(14.dp), MaterialTheme.colorScheme.outline)
        }
    }
}

/** 探测本机局域网 IPv4（用于展示可被外部设备访问的连接地址）；失败返回 null。 */
private fun detectLanIp(): String? = try {
    java.net.NetworkInterface.getNetworkInterfaces()
        ?.asSequence()
        ?.filter { !it.isLoopback && it.isUp }
        ?.flatMap { it.inetAddresses.asSequence() }
        ?.firstOrNull { it is java.net.Inet4Address && !it.isLoopbackAddress }
        ?.hostAddress
        ?.takeIf { it.isNotBlank() && it != "127.0.0.1" }
} catch (_: Exception) {
    null
}

/**
 * MCP 分栏标题：分组展示「系统核心 MCP（内置）」与「自定义与三方 MCP」。
 */
@Composable
private fun McpSectionHeader(title: String, subtitle: String, count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "$title ($count)",
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 紧凑轻量的 MCP 服务卡片（列表展示）
 */
@Composable
private fun McpServerItemCard(
    server: McpServerConfig,
    connectionState: McpConnectionState,
    togglePending: Boolean,
    onToggle: (Boolean) -> Unit,
    onClickDetail: () -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClickDetail,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        server.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    McpConnectionDot(state = connectionState, enabled = server.isEnabled)
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        MaterialText(
                            server.transportType.name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Visible,
                            softWrap = false,
                        )
                    }
                }
                if (server.description.isNotBlank()) {
                    Text(
                        server.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Switch(
                checked = server.isEnabled,
                onCheckedChange = onToggle,
                enabled = !togglePending,
            )
        }
    }
}

/**
 * MCP 服务连通性状态小圆点：绿=在线 / 红=离线 / 转圈=检测中 / 灰=未知或未启用。
 */
@Composable
private fun McpConnectionDot(state: McpConnectionState, enabled: Boolean) {
    val (color, label) = when {
        !enabled -> MaterialTheme.colorScheme.outlineVariant to "未启用"
        state == McpConnectionState.CHECKING -> MaterialTheme.colorScheme.tertiary to "检测中"
        state == McpConnectionState.ONLINE -> successStatusColor() to "已连通"
        state == McpConnectionState.OFFLINE -> MaterialTheme.colorScheme.error to "离线"
        else -> MaterialTheme.colorScheme.outline to "未检测"
    }
    Box(
        modifier = Modifier
            .size(10.dp)
            .semantics { contentDescription = label }
            .clip(CircleShape)
            .background(color),
    )
}

/**
 * MCP 服务详情、命令预览、JSON 配置与工具探测弹窗
 */
@Composable
private fun McpServerDetailDialog(
    server: McpServerConfig,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onTest: suspend () -> Result<List<McpToolInfo>>,
    authState: McpAuthState = McpAuthState.Unsupported,
    onAuthorize: () -> Unit = {},
    onLogout: () -> Unit = {},
) {
    var testing by remember { mutableStateOf(false) }
    var discoveredTools by remember { mutableStateOf<List<McpToolInfo>?>(null) }
    var testError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val jsonConfig = remember(server) { server.toExportJsonConfig() }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    server.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    MaterialText(
                        server.transportType.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (server.description.isNotBlank()) {
                    Text(
                        server.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // 启动命令 / URL 预览
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (server.transportType == McpTransportType.STDIO) "启动命令" else "服务端点 URL",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val cmdText = if (server.transportType == McpTransportType.STDIO) {
                                "${server.command} ${server.args.joinToString(" ")}"
                            } else {
                                server.serverUrl
                            }
                            Text(
                                text = cmdText,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    copyToClipboard(context, cmdText, "mcp_cmd", "命令已复制")
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Copy, Modifier.size(14.dp), MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // JSON 配置代码块
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "MCP JSON 配置 (Cursor / Claude 格式)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(
                            onClick = {
                                copyToClipboard(context, jsonConfig, "mcp_json", "JSON 配置已复制")
                            },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text("复制 JSON", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E1E1E),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = jsonConfig,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                            ),
                            color = Color(0xFFD4D4D4),
                            modifier = Modifier.padding(10.dp),
                        )
                    }
                }

                // OAuth 身份与授权
                if (server.authMode == McpAuthMode.OAUTH) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Danh tính và cấp quyền",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            val label = when (authState) {
                                McpAuthState.Unauthenticated -> "Chưa ủy quyền"
                                McpAuthState.Authorizing -> "Đang cấp quyền…"
                                is McpAuthState.Authorized -> "Đã cấp quyền"
                                is McpAuthState.Error -> "授权失败"
                                McpAuthState.Unsupported -> "Không hỗ trợ"
                            }
                            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            when (authState) {
                                McpAuthState.Unauthenticated, is McpAuthState.Error ->
                                    Button(onClick = onAuthorize, enabled = server.oauthClientId.isNotBlank()) { Text("Phân quyền") }
                                is McpAuthState.Authorized ->
                                    OutlinedButton(onClick = onLogout) { Text("Thoát cấp quyền") }
                                McpAuthState.Authorizing ->
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                                McpAuthState.Unsupported -> Unit
                            }
                        }
                    }
                }

                // 测试与工具探测区域
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "沙箱连通性与工具探测",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        FilledTonalButton(
                            onClick = {
                                if (!testing) {
                                    testing = true
                                    testError = null
                                    scope.launch {
                                        val res = onTest()
                                        testing = false
                                        res.onSuccess { tools ->
                                            discoveredTools = tools
                                            Toast.makeText(context, "Dò thành công ${tools.size} công cụ", Toast.LENGTH_SHORT).show()
                                        }.onFailure { err ->
                                            testError = err.message ?: "连接失败"
                                            Toast.makeText(context, "Kết nối thất bại: ${err.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            if (testing) {
                                CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text("探测工具", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    testError?.let {
                        Text(
                            "Dò thất bại: $it",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    discoveredTools?.let { tools ->
                        if (tools.isEmpty()) {
                            Text(
                                "该服务已连通，但未返回任何工具定义",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    "Công cụ đã đăng ký (${tools.size}):",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                tools.forEach { tool ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                tool.name,
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                                                color = MaterialTheme.colorScheme.onSurface,
                                            )
                                            Text(
                                                tool.description,
                                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!server.isBuiltin) {
                    TextButton(onClick = onDelete) {
                        Text("删除服务", color = MaterialTheme.colorScheme.error)
                    }
                }
                Button(onClick = onDismiss) {
                    Text("完成")
                }
            }
        },
    )
}

@Composable
private fun AddMcpServerDialog(
    onDismiss: () -> Unit,
    onAdd: (McpServerConfig) -> Unit,
    onAddBatch: (List<McpServerConfig>) -> Unit = { list -> list.forEach(onAdd) },
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    // 表单状态
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf(McpTransportType.STDIO) }
    var command by remember { mutableStateOf("npx") }
    var argsStr by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("http://127.0.0.1:8000/sse") }
    var oauthEnabled by remember { mutableStateOf(false) }
    var oauthClientId by remember { mutableStateOf("") }
    var oauthAuthorizationEndpoint by remember { mutableStateOf("") }
    var oauthTokenEndpoint by remember { mutableStateOf("") }
    var oauthScope by remember { mutableStateOf("") }
    var oauthResource by remember { mutableStateOf("") }

    // JSON 模式状态
    var jsonText by remember {
        mutableStateOf(
            """
            {
              "mcpServers": {
                "sqlite": {
                  "command": "uvx",
                  "args": ["mcp-server-sqlite", "--db-path", "/opt/tianxuan/data/sqlite.db"]
                }
              }
            }
            """.trimIndent()
        )
    }

    val parsedServers = remember(jsonText) { parseMcpJson(jsonText) }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("添加 MCP 服务", fontWeight = FontWeight.Bold)
                SecondaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {},
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("📝 表单模式", style = MaterialTheme.typography.labelMedium) },
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("📋 JSON 导入", style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (selectedTab == 0) {
                    // 📝 表单模式
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("服务名称") },
                        placeholder = { Text("如: sqlite / github / fetch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述（可选）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "传输协议类型",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            val isStdio = transport == McpTransportType.STDIO
                            val isSse = transport == McpTransportType.SSE

                            // Stdio 选项卡
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { transport = McpTransportType.STDIO },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isStdio) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "Stdio",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isStdio) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isStdio) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "沙箱进程",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (isStdio) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }

                            // SSE 选项卡
                            Surface(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { transport = McpTransportType.SSE },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSse) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "SSE",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSse) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSse) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "远程 HTTP",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (isSse) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f) else MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }

                    if (transport == McpTransportType.STDIO) {
                        OutlinedTextField(
                            value = command,
                            onValueChange = { command = it },
                            label = { Text("执行命令（如 npx / uvx / python3）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = argsStr,
                            onValueChange = { argsStr = it },
                            label = { Text("参数（空格分隔）") },
                            placeholder = { Text("-y @modelcontextprotocol/server-sqlite") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("URL điểm cuối HTTP / SSE") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("OAuth 2.0 + PKCE", style = MaterialTheme.typography.labelMedium)
                                Text("Chế độ mã ủy quyền; token được mã hóa lưu trữ và sẽ không xuất ra", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = oauthEnabled, onCheckedChange = { oauthEnabled = it })
                        }
                        if (oauthEnabled) {
                            OutlinedTextField(oauthClientId, { oauthClientId = it }, label = { Text("OAuth client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(oauthAuthorizationEndpoint, { oauthAuthorizationEndpoint = it }, label = { Text("Authorization endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(oauthTokenEndpoint, { oauthTokenEndpoint = it }, label = { Text("Token endpoint") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(oauthScope, { oauthScope = it }, label = { Text("Scope (tùy chọn)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(oauthResource, { oauthResource = it }, label = { Text("Resource (tùy chọn)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else {
                    // 📋 JSON 模式
                    Text(
                        "直接粘贴 Claude Desktop / Cursor 标准配置：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        label = { Text("MCP JSON 配置") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        ),
                    )

                    if (parsedServers.isSuccess) {
                        val list = parsedServers.getOrThrow()
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "✅ Phân giải thành công ${list.size} dịch vụ: " + list.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    } else {
                        val errMsg = parsedServers.exceptionOrNull()?.message ?: "JSON 语法解析错误"
                        Text(
                            "❌ $errMsg",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (selectedTab == 0) {
                Button(
                    onClick = {
                        val id = "custom_" + System.currentTimeMillis()
                        val argsList = argsStr.trim().split(" ").filter { it.isNotBlank() }
                        onAdd(
                            McpServerConfig(
                                id = id,
                                name = name.trim(),
                                description = description.trim(),
                                transportType = transport,
                                command = command.trim(),
                                args = argsList,
                                serverUrl = serverUrl.trim(),
                                authMode = if (oauthEnabled) McpAuthMode.OAUTH else McpAuthMode.NONE,
                                oauthClientId = oauthClientId.trim(),
                                oauthAuthorizationEndpoint = oauthAuthorizationEndpoint.trim(),
                                oauthTokenEndpoint = oauthTokenEndpoint.trim(),
                                oauthScope = oauthScope.trim(),
                                oauthResource = oauthResource.trim(),
                                isEnabled = true,
                                isBuiltin = false,
                            )
                        )
                    },
                    enabled = name.isNotBlank() && (transport != McpTransportType.STDIO || command.isNotBlank()) &&
                        (!oauthEnabled || (oauthClientId.isNotBlank() && oauthAuthorizationEndpoint.isNotBlank() && oauthTokenEndpoint.isNotBlank())),
                ) {
                    Text("添加")
                }
            } else {
                Button(
                    onClick = {
                        val list = parsedServers.getOrNull()
                        if (!list.isNullOrEmpty()) {
                            onAddBatch(list)
                        }
                    },
                    enabled = parsedServers.isSuccess && parsedServers.getOrNull()?.isNotEmpty() == true,
                ) {
                    Text("Nhập (${parsedServers.getOrNull()?.size ?: 0} dịch vụ)")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 解析用户粘贴的各类 MCP JSON 格式 */
private fun parseMcpJson(rawJson: String): Result<List<McpServerConfig>> = runCatching {
    val trimmed = rawJson.trim()
    if (trimmed.isBlank()) error("请输入 JSON 内容")
    val element = Json.parseToJsonElement(trimmed)
    val list = mutableListOf<McpServerConfig>()
    val rootObj = element.jsonObject

    if (rootObj.containsKey("mcpServers")) {
        val serversObj = rootObj["mcpServers"]?.jsonObject ?: error("mcpServers 不是有效的 JSON 对象")
        for ((serverName, serverVal) in serversObj) {
            val sObj = serverVal.jsonObject
            val url = sObj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val command = sObj["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val args = sObj["args"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
            val env = sObj["env"]?.jsonObject?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
            val desc = sObj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val authMode = if (sObj["authMode"]?.jsonPrimitive?.contentOrNull.equals("OAUTH", ignoreCase = true)) McpAuthMode.OAUTH else McpAuthMode.NONE
            val oauthClientId = sObj["oauthClientId"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val oauthAuthorizationEndpoint = sObj["oauthAuthorizationEndpoint"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val oauthTokenEndpoint = sObj["oauthTokenEndpoint"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val oauthScope = sObj["oauthScope"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val oauthResource = sObj["oauthResource"]?.jsonPrimitive?.contentOrNull.orEmpty()

            val isSse = url.isNotBlank() || (command.isBlank() && sObj.containsKey("url"))
            list.add(
                McpServerConfig(
                    id = "custom_${serverName.lowercase().replace(Regex("[^a-z0-9_]"), "_")}_${System.currentTimeMillis()}",
                    name = serverName,
                    description = desc.ifBlank { if (isSse) "Dịch vụ HTTP từ xa: $url" else "Stdio cục bộ: $command ${args.joinToString(" ")}" },
                    transportType = if (isSse) McpTransportType.SSE else McpTransportType.STDIO,
                    command = command,
                    args = args,
                    env = env,
                    serverUrl = url,
                    authMode = authMode,
                    oauthClientId = oauthClientId,
                    oauthAuthorizationEndpoint = oauthAuthorizationEndpoint,
                    oauthTokenEndpoint = oauthTokenEndpoint,
                    oauthScope = oauthScope,
                    oauthResource = oauthResource,
                    isEnabled = true,
                    isBuiltin = false,
                )
            )
        }
    } else {
        // 单个对象
        val name = rootObj["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "custom_mcp" }
        val url = rootObj["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val command = rootObj["command"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val args = rootObj["args"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        val env = rootObj["env"]?.jsonObject?.mapValues { it.value.jsonPrimitive.contentOrNull.orEmpty() }.orEmpty()
        val desc = rootObj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()

        val isSse = url.isNotBlank() || (command.isBlank() && rootObj.containsKey("url"))
        if (command.isBlank() && url.isBlank()) error("配置中必须包含 command 或 url 字段")
        list.add(
            McpServerConfig(
                id = "custom_${name.lowercase().replace(Regex("[^a-z0-9_]"), "_")}_${System.currentTimeMillis()}",
                name = name,
                description = desc.ifBlank { if (isSse) "Dịch vụ HTTP từ xa: $url" else "Stdio cục bộ: $command ${args.joinToString(" ")}" },
                transportType = if (isSse) McpTransportType.SSE else McpTransportType.STDIO,
                command = command,
                args = args,
                env = env,
                serverUrl = url,
                isEnabled = true,
                isBuiltin = false,
            )
        )
    }
    if (list.isEmpty()) error("未找到有效的 MCP 服务配置")
    list
}
