package top.wkbin.tianxuan.ui.workflow

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import top.wkbin.tianxuan.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.model.workflow.NodeRunStatus
import top.wkbin.tianxuan.core.database.WorkflowScheduleEntity
import top.wkbin.tianxuan.core.model.workflow.WorkflowApprovalRequest
import top.wkbin.tianxuan.core.model.workflow.WorkflowDefinition
import top.wkbin.tianxuan.core.model.workflow.WorkflowRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeState
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

@Composable
fun WorkflowScreen(
    projectName: String = "",
    initialWorkflowId: String? = null,
    initialVariables: Map<String, String> = emptyMap(),
    initialExecutionId: String? = null,
    onBack: () -> Unit,
    viewModel: WorkflowViewModel = koinViewModel(),
) {
    val definitions by viewModel.definitions.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val backgroundRuns by viewModel.backgroundActiveRuns.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); viewModel.clearError() } }
    var pendingRun by remember { mutableStateOf<WorkflowDefinition?>(null) }
    var scheduleEditor by remember { mutableStateOf<WorkflowScheduleEntity?>(null) }
    var scheduleEditorIsNew by remember { mutableStateOf(false) }
    val activeState by viewModel.activeState.collectAsStateWithLifecycle()
    val approval by viewModel.approvalRequest.collectAsStateWithLifecycle()
    val editorState by viewModel.editorState.collectAsStateWithLifecycle()
    var discardRequested by rememberSaveable { mutableStateOf(false) }
    // 通知深链：进入页面即定位到指定执行（活跃运行或历史回看）
    LaunchedEffect(initialExecutionId) {
        if (initialExecutionId != null) viewModel.viewRun(initialExecutionId)
    }
    var autoStarted by rememberSaveable(initialWorkflowId) { mutableStateOf(false) }
    LaunchedEffect(initialWorkflowId, definitions, autoStarted) {
        if (!autoStarted && initialWorkflowId != null) {
            definitions.firstOrNull { it.id == initialWorkflowId }?.let {
                autoStarted = true
                pendingRun = it
            }
        }
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            RuntimeTopBar(
                title = activeState?.definition?.name ?: editorState?.definition?.name ?: "Luồng công việc Thiên Huyền",
                statusText = projectName.takeIf(String::isNotBlank)?.let { "Vùng làm việc · $it" },
                onBack = {
                    when {
                        activeState != null -> viewModel.closeRun()
                        editorState?.isDirty == true -> discardRequested = true
                        editorState != null -> viewModel.closeEditor()
                        else -> onBack()
                    }
                },
            )
        },
    ) { padding ->
        when {
            editorState != null -> WorkflowEditorView(
                state = editorState!!,
                activeRunState = activeState,
                onRun = {
                    if (editorState!!.isDirty) {
                        viewModel.saveEditor()
                    }
                    pendingRun = editorState!!.definition
                },
                onCancelRun = viewModel::cancel,
                onSelectNode = viewModel::selectNode,
                onMoveNode = viewModel::moveNode,
                onBeginConnection = viewModel::beginConnection,
                onCancelConnection = viewModel::cancelConnection,
                onConnect = viewModel::connectTo,
                onAddNode = viewModel::addNode,
                onUpdateNode = viewModel::updateNode,
                onUpdateMetadata = viewModel::updateMetadata,
                onRemoveNode = viewModel::removeSelectedNode,
                onRemoveNodeById = viewModel::removeNode,
                onConnectNodes = viewModel::connectNodes,
                onDisconnectNodes = viewModel::disconnectNodes,
                onDisconnectAllForNode = viewModel::disconnectAllForNode,
                onUpdateEdge = viewModel::updateEdge,
                onRemoveEdge = viewModel::removeEdge,
                onUndo = viewModel::undoEdit,
                onRedo = viewModel::redoEdit,
                onSave = viewModel::saveEditor,
                onAutoLayout = viewModel::autoLayout,
                modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            )
            activeState == null -> WorkflowCatalog(
                definitions = definitions,
                onRun = { pendingRun = it },
                history = history,
                onHistory = viewModel::showHistory,
                onRerun = viewModel::rerun,
                onEdit = viewModel::edit,
                onDelete = viewModel::deleteWorkflow,
                onCreate = viewModel::createWorkflow,
                backgroundRuns = backgroundRuns,
                onViewBackgroundRun = viewModel::viewRun,
                schedules = schedules,
                onNewSchedule = {
                    scheduleEditor = null
                    scheduleEditorIsNew = true
                },
                onEditSchedule = { scheduleEditor = it },
                onToggleSchedule = viewModel::toggleSchedule,
                onDeleteSchedule = viewModel::deleteSchedule,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> WorkflowRunView(
                state = activeState!!,
                onCancel = viewModel::cancel,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
    approval?.let { request ->
        ApprovalDialog(request, onDecision = { approved, variables -> viewModel.decide(request.nodeId, approved, variables) })
    }
    pendingRun?.let { definition ->
        // APK 扫描是全盘 walkTopDown，必须在协程里异步加载，禁止在组合期间同步执行
        var apks by remember(definition.id, projectName) { mutableStateOf<List<DiscoveredApk>>(emptyList()) }
        val apkScope = rememberCoroutineScope()
        LaunchedEffect(definition.id, projectName) {
            apks = viewModel.scanWorkspaceApks(projectName)
        }
        WorkflowStartDialog(
            definition = definition,
            supplied = initialVariables,
            models = models,
            availableApks = apks,
            onRefreshApks = {
                apkScope.launch { apks = viewModel.scanWorkspaceApks(projectName) }
            },
            onDismiss = { pendingRun = null },
        ) { variables ->
            pendingRun = null
            viewModel.start(definition, projectName, variables)
        }
    }
    if (discardRequested) {
        RuntimeAlertDialog(
            onDismissRequest = { discardRequested = false },
            title = { Text("Bỏ các thay đổi chưa lưu?") },
            text = { Text("Workflow hiện tại còn chỉnh sửa chưa lưu.") },
            dismissButton = { RuntimeOutlinedButton(onClick = { discardRequested = false }) { Text("Tiếp tục chỉnh sửa") } },
            confirmButton = {
                RuntimeButton(onClick = { discardRequested = false; viewModel.closeEditor() }) { Text("Bỏ thay đổi") }
            },
        )
    }
    if (scheduleEditorIsNew || scheduleEditor != null) {
        WorkflowScheduleEditDialog(
            definitions = definitions,
            models = models,
            existing = scheduleEditor.takeIf { !scheduleEditorIsNew },
            onDismiss = {
                scheduleEditor = null
                scheduleEditorIsNew = false
            },
            onSave = { entity ->
                viewModel.saveSchedule(entity)
                scheduleEditor = null
                scheduleEditorIsNew = false
            },
        )
    }
}

@Composable
private fun WorkflowCatalog(
    definitions: List<WorkflowDefinition>,
    onRun: (WorkflowDefinition) -> Unit,
    onEdit: (WorkflowDefinition) -> Unit,
    onDelete: (WorkflowDefinition) -> Unit,
    onCreate: () -> Unit,
    history: List<WorkflowHistoryEntry>,
    onHistory: (WorkflowRuntimeState) -> Unit,
    onRerun: (WorkflowHistoryEntry) -> Unit,
    backgroundRuns: List<WorkflowRuntimeState>,
    onViewBackgroundRun: (String) -> Unit,
    schedules: List<WorkflowScheduleEntity> = emptyList(),
    onNewSchedule: () -> Unit = {},
    onEditSchedule: (WorkflowScheduleEntity) -> Unit = {},
    onToggleSchedule: (String, Boolean) -> Unit = { _, _ -> },
    onDeleteSchedule: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var deleteRequested by remember { mutableStateOf<WorkflowDefinition?>(null) }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Sắp xếp các thao tác build, chẩn đoán, dịch ngược và Git thành DAG có thể quan sát, có thể tạm dừng.",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                RuntimeButton(onClick = onCreate, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                    RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp))
                    Text("Tạo mới", maxLines = 1)
                }
            }
        }
        if (backgroundRuns.isNotEmpty()) {
            item(key = "background_banner") {
                RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuntimeIcon(RuntimeIconName.Play, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (backgroundRuns.size == 1) "1 workflow đang chạy nền" else "${backgroundRuns.size} workflow đang chạy nền",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                backgroundRuns.joinToString("、") { it.definition.name },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        RuntimeOutlinedButton(
                            onClick = { onViewBackgroundRun(backgroundRuns.first().executionId) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text("Xem", maxLines = 1)
                        }
                    }
                }
            }
        }
        items(definitions, key = { it.id }) { workflow ->
            RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuntimeIcon(RuntimeIconName.Hub, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(workflow.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${workflow.category} · ${workflow.nodes.size} nút", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(workflow.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!workflow.isBuiltin) {
                            RuntimeOutlinedButton(onClick = { deleteRequested = workflow }, contentPadding = PaddingValues(9.dp)) {
                                RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp))
                            }
                        }
                        RuntimeOutlinedButton(onClick = { onEdit(workflow) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                            RuntimeIcon(RuntimeIconName.Edit, Modifier.size(16.dp))
                            Text(if (workflow.isBuiltin) "Tạo bản sao" else "Chỉnh sửa", maxLines = 1)
                        }
                        RuntimeButton(onClick = { onRun(workflow) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                            RuntimeIcon(RuntimeIconName.Play, Modifier.size(16.dp))
                            Text("Chạy", maxLines = 1)
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Lịch định thời",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                )
                    RuntimeOutlinedButton(onClick = onNewSchedule, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp))
                        Text("Tạo kế hoạch mới", maxLines = 1)
                    }
                }
            }
            if (schedules.isEmpty()) {
                item {
                    Text(
                        "Chưa có lịch định thời. Đặt workflow chạy định thời mỗi ngày, tuần tra theo chu kỳ hoặc kích hoạt trễ một lần; đến giờ dù ứng dụng chưa chạy, hệ thống cũng sẽ gọi lên để thực thi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(schedules, key = { "schedule:${it.id}" }) { schedule ->
                val workflowName = definitions.firstOrNull { it.id == schedule.workflowId }?.name ?: schedule.workflowId
                RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(schedule.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "$workflowName · ${scheduleRepeatLabel(schedule)}" +
                                        schedule.nextRunAt?.let { next ->
                                            " · Lần sau ${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(next))}"
                                        }.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(checked = schedule.enabled, onCheckedChange = { onToggleSchedule(schedule.id, it) })
                        }
                        schedule.lastExecutionId?.let {
                            Text(
                                "Lần kích hoạt trước: ${schedule.lastRunAt?.let { last -> java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(last)) } ?: "—"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(Modifier.align(Alignment.End), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RuntimeOutlinedButton(onClick = { onDeleteSchedule(schedule.id) }, contentPadding = PaddingValues(9.dp)) {
                                RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp))
                            }
                            RuntimeOutlinedButton(onClick = { onEditSchedule(schedule) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                                RuntimeIcon(RuntimeIconName.Edit, Modifier.size(16.dp))
                                Text("Chỉnh sửa", maxLines = 1)
                            }
                        }
                    }
                }
            }
        if (history.isNotEmpty()) item { Text("Chạy gần đây (${history.size} mục gần nhất)", style = MaterialTheme.typography.titleMedium) }
        items(history, key = { "history:${it.state.executionId}" }) { entry ->
            val run = entry.state
            RuntimeCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "${run.definition.name} · ${runStatusLabel(run.status)} · ${
                                java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(run.startedAt ?: 0))
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                entry.triggerSource == "SCHEDULE" && entry.scheduleId != null -> "Kích hoạt kế hoạch định giờ"
                                else -> "Kích hoạt thủ công"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    RuntimeOutlinedButton(onClick = { onRerun(entry) }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        RuntimeIcon(RuntimeIconName.Play, Modifier.size(16.dp))
                        Text("Chạy lại", maxLines = 1)
                    }
                    RuntimeOutlinedButton(onClick = { onHistory(run) }, contentPadding = PaddingValues(9.dp)) {
                        RuntimeIcon(RuntimeIconName.List, Modifier.size(16.dp))
                    }
                }
            }
        }
    }
    deleteRequested?.let { workflow ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteRequested = null },
            title = { Text("Xóa workflow?") },
            text = { Text("“${workflow.name}” và lịch sử chạy của nó sẽ bị xóa vĩnh viễn, không thể hoàn tác.") },
            dismissButton = { RuntimeOutlinedButton(onClick = { deleteRequested = null }) { Text("取消") } },
            confirmButton = {
                RuntimeButton(
                    onClick = {
                        deleteRequested = null
                        onDelete(workflow)
                    },
                ) { Text("删除") }
            },
        )
    }
}

@Composable
private fun WorkflowRunView(
    state: WorkflowRuntimeState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        var selectedNode by remember(state.executionId) { mutableStateOf<String?>(null) }
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxSize()) {
                WorkflowCanvas2D(state.definition, state, Modifier.weight(1f).fillMaxSize(), selectedNodeId = selectedNode, onNodeSelected = { selectedNode = it })
                Column(Modifier.width(320.dp).verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RunSummary(state)
                    val node = state.definition.nodes.firstOrNull { it.id == selectedNode }
                    if (node == null) Text("Nhấn chọn nút để xem nhật ký, lỗi và sản phẩm")
                    else {
                        Text(node.title, style = MaterialTheme.typography.titleMedium)
                        WorkflowNodeDetails(state.nodeStates[node.id])
                    }
                }
            }
        } else {
            WorkflowTimelineView(state, Modifier.fillMaxSize())
        }
        if (state.status in setOf(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL)) {
            RuntimeOutlinedButton(
                onClick = onCancel,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) { Text("取消", maxLines = 1) }
        }
    }
}

@Composable
fun WorkflowTimelineView(state: WorkflowRuntimeState, modifier: Modifier = Modifier) {
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            RunSummary(state)
        }
        items(state.definition.nodes, key = { it.id }) { node ->
            val run = state.nodeStates[node.id]
            val status = run?.status ?: NodeRunStatus.IDLE
            val isRunning = status == NodeRunStatus.RUNNING || status == NodeRunStatus.STREAMING
            val isWaiting = status == NodeRunStatus.WAITING_APPROVAL
            val color = statusColor(status)

            val pulseAlpha = if (isRunning) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_${node.id}")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.40f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "pulseAlpha",
                )
                alpha
            } else if (isWaiting) {
                0.85f
            } else {
                0.55f
            }

            val cardContainerColor = if (isRunning) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f * pulseAlpha)
            } else if (isWaiting) {
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }

            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = cardContainerColor,
                borderColor = color.copy(alpha = pulseAlpha),
                contentPadding = PaddingValues(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RuntimeIcon(statusIcon(status), Modifier.size(18.dp), tint = color.copy(alpha = if (isRunning) pulseAlpha else 1f))
                        Spacer(Modifier.width(10.dp))
                        Text(node.title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val duration = run?.output?.durationMs
                        if (duration != null && duration > 0L) {
                            Text("${duration}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), modifier = Modifier.padding(end = 6.dp))
                        }
                        Text(statusLabel(status), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1, fontWeight = if (isRunning || isWaiting) FontWeight.Bold else FontWeight.Normal)
                    }
                    run?.progressMessage?.takeIf(String::isNotBlank)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    WorkflowNodeDetails(run)
                }
            }
        }
        item { Spacer(Modifier.height(64.dp)) }
    }
}

@Composable
private fun RunSummary(state: WorkflowRuntimeState) {
    val color = when (state.status) {
        WorkflowRunStatus.SUCCESS -> Color(0xFF2E7D32)
        WorkflowRunStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    RuntimeCard(modifier = Modifier.fillMaxWidth(), borderColor = color.copy(alpha = 0.45f), contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RuntimeIcon(statusIcon(state.nodeStates.values.lastOrNull()?.status ?: NodeRunStatus.PENDING), Modifier.size(20.dp), tint = color)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(runStatusLabel(state.status), fontWeight = FontWeight.SemiBold)
                WorkflowElapsed(state)
                state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

@Composable
private fun ApprovalDialog(request: WorkflowApprovalRequest, onDecision: (Boolean, Map<String, String>) -> Unit) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    LaunchedEffect(request.executionId, request.nodeId) {
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
    }
    val values = remember(request) { mutableStateMapOf<String, String>().apply { request.requestedVariables.forEach { put(it, "") } } }
    RuntimeAlertDialog(
        onDismissRequest = { onDecision(false, emptyMap()) },
        title = { Text(request.title) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (request.description.isNotBlank()) Text(request.description, style = MaterialTheme.typography.bodyMedium)
                request.requestedVariables.forEach { key ->
                    OutlinedTextField(value = values[key].orEmpty(), onValueChange = { values[key] = it }, label = { Text(key, maxLines = 1, overflow = TextOverflow.Ellipsis) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }
        },
        dismissButton = { RuntimeOutlinedButton(onClick = { onDecision(false, emptyMap()) }) { Text("Từ chối", maxLines = 1) } },
        confirmButton = { RuntimeButton(onClick = { onDecision(true, values.toMap()) }) { Text("Xác nhận thực thi", maxLines = 1) } },
    )
}

@Composable
internal fun statusColor(status: NodeRunStatus): Color = when (status) {
    NodeRunStatus.SUCCESS -> Color(0xFF2E7D32)
    NodeRunStatus.FAILED, NodeRunStatus.CANCELLED -> MaterialTheme.colorScheme.error
    NodeRunStatus.RUNNING, NodeRunStatus.STREAMING, NodeRunStatus.WAITING_APPROVAL -> MaterialTheme.colorScheme.primary
    NodeRunStatus.SKIPPED -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.outline
}

internal fun statusIcon(status: NodeRunStatus) = when (status) {
    NodeRunStatus.SUCCESS -> RuntimeIconName.Check
    NodeRunStatus.FAILED, NodeRunStatus.CANCELLED -> RuntimeIconName.Alert
    NodeRunStatus.RUNNING, NodeRunStatus.STREAMING -> RuntimeIconName.Play
    NodeRunStatus.WAITING_APPROVAL -> RuntimeIconName.Shield
    NodeRunStatus.SKIPPED -> RuntimeIconName.ArrowUp
    else -> RuntimeIconName.Hub
}

internal fun statusLabel(status: NodeRunStatus) = when (status) {
    NodeRunStatus.IDLE -> "Rảnh rỗi"
    NodeRunStatus.PENDING -> "Chờ đợi"
    NodeRunStatus.RUNNING -> "运行中"
    NodeRunStatus.STREAMING -> "Đang xuất"
    NodeRunStatus.WAITING_APPROVAL -> "Chờ xác nhận"
    NodeRunStatus.SUCCESS -> "Thành công"
    NodeRunStatus.FAILED -> "失败"
    NodeRunStatus.SKIPPED -> "Đã bỏ qua"
    NodeRunStatus.CANCELLED -> "Đã hủy"
}

private fun runStatusLabel(status: WorkflowRunStatus) = when (status) {
    WorkflowRunStatus.IDLE -> "Chuẩn bị thực thi"
    WorkflowRunStatus.RUNNING -> "Workflow đang chạy"
    WorkflowRunStatus.WAITING_APPROVAL -> "Chờ xác nhận thủ công"
    WorkflowRunStatus.SUCCESS -> "Workflow đã hoàn thành"
    WorkflowRunStatus.FAILED -> "Thực thi workflow thất bại"
    WorkflowRunStatus.CANCELLED -> "Workflow đã bị hủy"
}
