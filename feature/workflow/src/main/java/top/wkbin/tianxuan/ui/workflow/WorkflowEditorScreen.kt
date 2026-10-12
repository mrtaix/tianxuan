package top.wkbin.tianxuan.ui.workflow

import android.content.ClipData
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.wkbin.tianxuan.core.model.workflow.FailurePolicy
import top.wkbin.tianxuan.core.model.workflow.HostWorkflowActions
import top.wkbin.tianxuan.core.model.workflow.HostWorkflowPrivilege
import top.wkbin.tianxuan.core.model.workflow.NodeRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowEdge
import top.wkbin.tianxuan.core.model.workflow.WorkflowNode
import top.wkbin.tianxuan.core.model.workflow.WorkflowNodeType
import top.wkbin.tianxuan.core.model.workflow.WorkflowRunStatus
import top.wkbin.tianxuan.core.model.workflow.WorkflowRuntimeState
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton

/** 共享的 pretty Json：构建实例开销大，不在 remember/重组中反复创建。 */
private val PrettyJson = Json { prettyPrint = true }

@Composable
fun WorkflowEditorView(
    state: WorkflowEditorUiState,
    activeRunState: WorkflowRuntimeState? = null,
    onRun: () -> Unit = {},
    onCancelRun: () -> Unit = {},
    onSelectNode: (String?) -> Unit,
    onMoveNode: (String, Float, Float) -> Unit,
    onBeginConnection: (String) -> Unit,
    onCancelConnection: () -> Unit,
    onConnect: (String) -> Unit,
    onAddNode: (WorkflowNodeType) -> Unit,
    onUpdateNode: (WorkflowNode) -> Unit,
    onUpdateMetadata: (String, String, String) -> Unit,
    onRemoveNode: () -> Unit,
    onRemoveNodeById: (String) -> Unit = {},
    onConnectNodes: (String, String, String, String?) -> Unit = { _, _, _, _ -> },
    onDisconnectNodes: (String, String) -> Unit = { _, _ -> },
    onDisconnectAllForNode: (String) -> Unit = {},
    onUpdateEdge: (WorkflowEdge) -> Unit,
    onRemoveEdge: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onAutoLayout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRunning = activeRunState?.status in setOf(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL)
    var sheetExpanded by remember { mutableStateOf(false) }
    var showRunConsole by remember { mutableStateOf(false) }
    var nodePendingDelete by remember { mutableStateOf<WorkflowNode?>(null) }

    BoxWithConstraints(modifier) {
        val editorHeight = maxHeight
        val isWideScreen = maxWidth >= 820.dp
        val selectedNode = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
        val isInspectorOpen = !isWideScreen && sheetExpanded && selectedNode != null

        Column(Modifier.fillMaxSize()) {
            state.message?.let { message ->
                Surface(
                    color = if (message == "已保存" || message == "请选择目标节点") {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message == "已保存" || message == "请选择目标节点") {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                val canvas: @Composable (Modifier) -> Unit = { canvasModifier ->
                    WorkflowCanvas2D(
                        definition = state.definition,
                        state = activeRunState,
                        editable = !isRunning,
                        selectedNodeId = state.selectedNodeId,
                        connectionSourceId = state.connectionSourceId,
                        onNodeSelected = onSelectNode,
                        onNodeClicked = { nodeId ->
                            onSelectNode(nodeId)
                            sheetExpanded = true
                        },
                        onCanvasTapped = {
                            sheetExpanded = false
                            onSelectNode(null)
                        },
                        onNodeMoved = onMoveNode,
                        onConnectionRequested = { _, target -> onConnect(target) },
                        onBeginConnection = onBeginConnection,
                        onRemoveNode = { nodeId ->
                            val target = state.definition.nodes.firstOrNull { it.id == nodeId }
                            if (target != null) nodePendingDelete = target
                        },
                        onConfigureNode = { nodeId ->
                            onSelectNode(nodeId)
                            sheetExpanded = true
                        },
                        modifier = canvasModifier,
                    )
                }
                val inspector: @Composable (Modifier) -> Unit = { inspectorModifier ->
                    EditorInspector(
                        state = state,
                        activeRunState = activeRunState,
                        onUpdateNode = onUpdateNode,
                        onUpdateMetadata = onUpdateMetadata,
                        onBeginConnection = onBeginConnection,
                        onCancelConnection = onCancelConnection,
                        onRemoveNode = {
                            val selected = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
                            if (selected != null) nodePendingDelete = selected
                        },
                        onConnectNodes = onConnectNodes,
                        onDisconnectAllForNode = onDisconnectAllForNode,
                        onUpdateEdge = onUpdateEdge,
                        onRemoveEdge = onRemoveEdge,
                        onOpenConsole = { showRunConsole = true },
                        modifier = inspectorModifier,
                    )
                }

                if (isWideScreen) {
                    Row(Modifier.fillMaxSize()) {
                        canvas(Modifier.weight(1f).fillMaxSize())
                        if (sheetExpanded && selectedNode != null) {
                            VerticalDivider()
                            Box(Modifier.width(420.dp).fillMaxSize()) {
                                Column(Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        val theme = selectedNode.type.visualTheme()
                                        Surface(
                                            color = theme.accentColor.copy(alpha = 0.16f),
                                            shape = RoundedCornerShape(6.dp),
                                        ) {
                                            Text(
                                                text = theme.tag,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = theme.accentColor,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            )
                                        }
                                        Text(
                                            text = selectedNode.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )
                                        RuntimeIconButton(
                                            onClick = {
                                                sheetExpanded = false
                                                onSelectNode(null)
                                            },
                                            modifier = Modifier.size(32.dp),
                                            contentDescription = "Đóng bảng điều khiển",
                                        ) {
                                            RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp))
                                        }
                                    }
                                    HorizontalDivider()
                                    inspector(Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                } else {
                    EditorBottomSheetLayout(
                        maxHeight = editorHeight,
                        selectedNode = selectedNode,
                        sheetExpanded = sheetExpanded && selectedNode != null,
                        onSheetExpandedChange = { expanded ->
                            sheetExpanded = expanded
                            if (!expanded) onSelectNode(null)
                        },
                        isRunning = isRunning,
                        canvas = canvas,
                        inspector = inspector,
                    )
                }
            }

            AnimatedVisibility(
                visible = !isInspectorOpen,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    expandVertically(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                    fadeIn(tween(160)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    shrinkVertically(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                    fadeOut(tween(140)),
            ) {
                EditorBottomDock(
                    state = state,
                    activeRunState = activeRunState,
                    onAddNode = onAddNode,
                    onDeleteSelectedNode = {
                        val selected = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
                        if (selected != null) nodePendingDelete = selected
                    },
                    onToggleConsole = { showRunConsole = true },
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onSave = onSave,
                    onAutoLayout = onAutoLayout,
                    onRun = {
                        onRun()
                        showRunConsole = true
                    },
                    onCancelRun = onCancelRun,
                )
            }
        }
    }

    // 节点删除确认弹窗
    nodePendingDelete?.let { target ->
        RuntimeAlertDialog(
            onDismissRequest = { nodePendingDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Xác nhận xóa node", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Có chắc xóa 「${target.title}」 không?\nTất cả đường nối đầu vào và đầu ra liên kết với node đó cũng sẽ bị ngắt và gỡ bỏ cùng lúc.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                RuntimeButton(
                    onClick = {
                        val id = target.id
                        nodePendingDelete = null
                        if (state.selectedNodeId == id) onRemoveNode() else onRemoveNodeById(id)
                    },
                ) {
                    Text("Xác nhận xóa")
                }
            },
            dismissButton = {
                RuntimeOutlinedButton(onClick = { nodePendingDelete = null }) {
                    Text("取消")
                }
            },
        )
    }

    // 运行日志与控制台弹窗
    if (showRunConsole && activeRunState != null) {
        RunConsoleModal(
            activeRunState = activeRunState,
            onDismiss = { showRunConsole = false },
            onCancelRun = onCancelRun,
            onRerun = onRun,
        )
    }
}

@Composable
private fun EditorBottomSheetLayout(
    maxHeight: Dp,
    selectedNode: WorkflowNode?,
    sheetExpanded: Boolean,
    onSheetExpandedChange: (Boolean) -> Unit,
    isRunning: Boolean,
    canvas: @Composable (Modifier) -> Unit,
    inspector: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        // 画布全屏铺满，彻底消除常驻 56dp 底栏占用
        canvas(Modifier.fillMaxSize())

        // 仅在单击节点并且展开时才向上弹出，非持久化
        AnimatedVisibility(
            visible = sheetExpanded && selectedNode != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeIn(tween(200)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(220, easing = FastOutSlowInEasing)) + fadeOut(tween(180)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            if (selectedNode != null) {
                val theme = selectedNode.type.visualTheme()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(maxHeight * 0.62f)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
                    tonalElevation = 4.dp,
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                    ) {
                        // 顶部居中拖拽把手指示条
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 36.dp, height = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                        CircleShape,
                                    ),
                            )
                        }

                        // 弹窗顶部栏：类型彩色药丸、节点标题、关闭按钮
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Surface(
                                color = theme.accentColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp),
                            ) {
                                Text(
                                    text = theme.tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.accentColor,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                )
                            }
                            Text(
                                text = selectedNode.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            RuntimeIconButton(
                                onClick = { onSheetExpandedChange(false) },
                                modifier = Modifier.size(32.dp),
                                contentDescription = "Thu bảng điều khiển",
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()

                        // 属性配置与连线检查器
                        inspector(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorBottomDock(
    state: WorkflowEditorUiState,
    activeRunState: WorkflowRuntimeState?,
    onAddNode: (WorkflowNodeType) -> Unit,
    onDeleteSelectedNode: () -> Unit,
    onToggleConsole: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSave: () -> Unit,
    onAutoLayout: () -> Unit,
    onRun: () -> Unit,
    onCancelRun: () -> Unit,
) {
    var addDialogVisible by remember { mutableStateOf(false) }
    val isRunning = activeRunState?.status in setOf(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp), // 去除圆角，平铺沉底贴边
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                thickness = 1.dp,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
            // 添加节点
            DockActionButton(
                icon = RuntimeIconName.Plus,
                label = "添加",
                onClick = { addDialogVisible = true },
                tint = MaterialTheme.colorScheme.primary,
            )

            // 运行 / 停止调试
            if (isRunning) {
                DockActionButton(
                    icon = RuntimeIconName.Stop,
                    label = "停止",
                    onClick = onCancelRun,
                    tint = MaterialTheme.colorScheme.error,
                )
            } else {
                DockActionButton(
                    icon = RuntimeIconName.Play,
                    label = "Chạy",
                    onClick = onRun,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }

            // 控制台 / 日志
            if (activeRunState != null) {
                DockActionButton(
                    icon = RuntimeIconName.Terminal,
                    label = if (isRunning) "运行中" else "日志",
                    onClick = onToggleConsole,
                    tint = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    badge = if (isRunning) {
                        {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .align(Alignment.TopEnd)
                            )
                        }
                    } else null,
                )
            }

            // 删除节点（当选中节点且未在运行时呈现）
            if (state.selectedNodeId != null && !isRunning) {
                DockActionButton(
                    icon = RuntimeIconName.Trash,
                    label = "删除",
                    onClick = onDeleteSelectedNode,
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            // 自动排版
            DockActionButton(
                icon = RuntimeIconName.Tune,
                label = "Dàn trang",
                onClick = onAutoLayout,
            )

            // 撤销
            DockActionButton(
                icon = RuntimeIconName.Back,
                label = "Hoàn tác",
                enabled = state.canUndo && !isRunning,
                onClick = onUndo,
            )

            // 重做
            DockActionButton(
                icon = RuntimeIconName.Reverse,
                label = "Làm lại",
                enabled = state.canRedo && !isRunning,
                onClick = onRedo,
            )

            // 保存
            DockActionButton(
                icon = RuntimeIconName.Save,
                label = when {
                    state.isSaving -> "Đang lưu"
                    state.isDirty -> "Lưu*"
                    else -> "已保存"
                },
                enabled = !state.isSaving && state.isDirty && !isRunning,
                tint = if (state.isDirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onSave,
            )
        }
    }
}

    if (addDialogVisible) {
        NodePickerModal(
            onDismiss = { addDialogVisible = false },
            onSelect = { type ->
                addDialogVisible = false
                onAddNode(type)
            },
        )
    }
}

@Composable
private fun DockActionButton(
    icon: RuntimeIconName,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(0.dp), // 去除圆角
        color = Color.Transparent,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.38f)
            .padding(horizontal = 2.dp),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                RuntimeIcon(icon, Modifier.size(19.dp), tint = tint)
                badge?.invoke(this)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = tint,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RunConsoleModal(
    activeRunState: WorkflowRuntimeState,
    onDismiss: () -> Unit,
    onCancelRun: () -> Unit,
    onRerun: () -> Unit,
) {
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val isRunning = activeRunState.status in setOf(WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL)
    val startedAt = activeRunState.startedAt ?: 0L
    val finishedAt = activeRunState.finishedAt ?: 0L
    val durationMs = if (finishedAt > 0L && startedAt > 0L) {
        finishedAt - startedAt
    } else if (startedAt > 0L) {
        (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
    } else {
        0L
    }

    val consoleOutput = remember(activeRunState) {
        buildString {
            appendLine("=== Đầu ra console workflow Thien Huyen「${activeRunState.definition.name}」===")
            appendLine("Trạng thái toàn cục: ${runStatusLabel(activeRunState.status)}  |  Thời gian thực thi: ${durationMs}ms")
            activeRunState.error?.let {
                appendLine("❌ Thông tin ngoại lệ: $it")
            }
            appendLine()
            activeRunState.definition.nodes.forEach { node ->
                val run = activeRunState.nodeStates[node.id]
                val status = run?.status ?: NodeRunStatus.PENDING
                appendLine("--------------------------------------------------")
                appendLine("[${statusLabel(status)}] Node: ${node.title} (${node.id})")
                val progress = run?.progressMessage
                val textOutput = run?.output?.textOutput
                val errorOutput = run?.output?.error
                if (!progress.isNullOrBlank()) {
                    appendLine("Tiến độ: $progress")
                }
                if (!textOutput.isNullOrBlank()) {
                    appendLine(textOutput)
                }
                if (!errorOutput.isNullOrBlank()) {
                    appendLine("stderr: $errorOutput")
                }
            }
            appendLine("--------------------------------------------------")
            appendLine("=== Kết thúc đầu ra thực thi ===")
        }
    }

    val scrollState = rememberScrollState()
    LaunchedEffect(consoleOutput) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Chạy console debug", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(
                    color = runStatusColor(activeRunState.status).copy(alpha = 0.16f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        text = runStatusLabel(activeRunState.status),
                        style = MaterialTheme.typography.labelSmall,
                        color = runStatusColor(activeRunState.status),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    activeRunState.definition.nodes.forEach { node ->
                        val nodeRun = activeRunState.nodeStates[node.id]
                        val st = nodeRun?.status ?: NodeRunStatus.PENDING
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = statusColor(st).copy(alpha = 0.14f),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(
                                    text = statusIconText(st),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor(st),
                                )
                                Text(
                                    text = node.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor(st),
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }

                Surface(
                    color = Color(0xFF0F172A),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                ) {
                    Box(Modifier.fillMaxSize().padding(10.dp)) {
                        Text(
                            text = consoleOutput,
                            color = Color(0xFFE2E8F0),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                            modifier = Modifier.verticalScroll(scrollState),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeOutlinedButton(onClick = {
                    clipboardScope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("workflow_console", consoleOutput)))
                    }
                }) {
                    Text("Sao chép log")
                }
                if (isRunning) {
                    RuntimeOutlinedButton(onClick = onCancelRun) {
                        Text("Dừng chạy", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    RuntimeButton(onClick = onRerun) {
                        Text("Chạy lại")
                    }
                }
            }
        },
        dismissButton = {
            RuntimeOutlinedButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun statusIconText(status: NodeRunStatus) = when (status) {
    NodeRunStatus.SUCCESS -> "✔"
    NodeRunStatus.FAILED -> "✘"
    NodeRunStatus.RUNNING, NodeRunStatus.STREAMING -> "●"
    NodeRunStatus.SKIPPED -> "↷"
    NodeRunStatus.CANCELLED -> "⊘"
    NodeRunStatus.WAITING_APPROVAL -> "🛡"
    NodeRunStatus.IDLE, NodeRunStatus.PENDING -> "⌛"
}

@Composable
private fun runStatusColor(status: WorkflowRunStatus): Color = when (status) {
    WorkflowRunStatus.SUCCESS -> Color(0xFF2E7D32)
    WorkflowRunStatus.FAILED, WorkflowRunStatus.CANCELLED -> MaterialTheme.colorScheme.error
    WorkflowRunStatus.RUNNING, WorkflowRunStatus.WAITING_APPROVAL -> MaterialTheme.colorScheme.primary
    WorkflowRunStatus.IDLE -> MaterialTheme.colorScheme.outline
}

private fun runStatusLabel(status: WorkflowRunStatus): String = when (status) {
    WorkflowRunStatus.IDLE -> "未运行"
    WorkflowRunStatus.RUNNING -> "运行中"
    WorkflowRunStatus.WAITING_APPROVAL -> "Chờ phê duyệt"
    WorkflowRunStatus.SUCCESS -> "Thực thi thành công"
    WorkflowRunStatus.FAILED -> "Thực thi thất bại"
    WorkflowRunStatus.CANCELLED -> "Đã hủy"
}


@Composable
private fun NodePickerModal(
    onDismiss: () -> Unit,
    onSelect: (WorkflowNodeType) -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Thêm node workflow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Chọn loại nút muốn thêm vào canvas, sau khi tạo có thể cấu hình tham số trong bảng kiểm tra:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                WorkflowNodeType.entries.forEach { type ->
                    val meta = type.metadata()
                    Surface(
                        onClick = { onSelect(type) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    RuntimeIcon(meta.icon, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(meta.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    meta.summary,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            RuntimeOutlinedButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EditorInspector(
    state: WorkflowEditorUiState,
    activeRunState: WorkflowRuntimeState?,
    onUpdateNode: (WorkflowNode) -> Unit,
    onUpdateMetadata: (String, String, String) -> Unit,
    onBeginConnection: (String) -> Unit,
    onCancelConnection: () -> Unit,
    onRemoveNode: () -> Unit,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onDisconnectAllForNode: (String) -> Unit,
    onUpdateEdge: (WorkflowEdge) -> Unit,
    onRemoveEdge: (String) -> Unit,
    onOpenConsole: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected = state.definition.nodes.firstOrNull { it.id == state.selectedNodeId }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WorkflowMetadataCard(state, onUpdateMetadata)

        if (selected == null) {
            RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
                Text("Vui lòng nhấn node trong canvas để cấu hình tham số, quản lý đường nối hoặc xem log thực thi", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            val nodeRun = activeRunState?.nodeStates?.get(selected.id)
            if (nodeRun != null) {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = statusColor(nodeRun.status).copy(alpha = 0.5f),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Trạng thái chạy gỡ lỗi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Surface(
                                color = statusColor(nodeRun.status).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Text(
                                    text = statusLabel(nodeRun.status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor(nodeRun.status),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        WorkflowNodeDetails(nodeRun)
                        RuntimeOutlinedButton(onClick = onOpenConsole, modifier = Modifier.fillMaxWidth()) {
                            RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(16.dp))
                            Text("Mở log console terminal đầy đủ", maxLines = 1)
                        }
                    }
                }
            }

            NodeInspectorCard(
                state = state,
                node = selected,
                isConnecting = state.connectionSourceId == selected.id,
                onApply = onUpdateNode,
                onBeginConnection = { onBeginConnection(selected.id) },
                onCancelConnection = onCancelConnection,
                onRemove = onRemoveNode,
                onConnectNodes = onConnectNodes,
                onRemoveEdge = onRemoveEdge,
            )

            NodeConnectionsCard(
                state = state,
                node = selected,
                onConnectNodes = onConnectNodes,
                onDisconnectAll = onDisconnectAllForNode,
                onUpdateEdge = onUpdateEdge,
                onRemoveEdge = onRemoveEdge,
            )
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun WorkflowMetadataCard(state: WorkflowEditorUiState, onApply: (String, String, String) -> Unit) {
    var name by remember(state.definition.id, state.definition.name) { mutableStateOf(state.definition.name) }
    var description by remember(state.definition.id, state.definition.description) { mutableStateOf(state.definition.description) }
    var category by remember(state.definition.id, state.definition.category) { mutableStateOf(state.definition.category) }
    var expanded by remember { mutableStateOf(false) }

    RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Thông tin workflow", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    text = if (expanded) "Thu gọn" else "Sửa đổi",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                OutlinedTextField(name, { name = it }, label = { Text("Tên luồng công việc") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it }, label = { Text("Phân loại") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("Thuyết minh") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                RuntimeOutlinedButton(onClick = { onApply(name, description, category) }, modifier = Modifier.align(Alignment.End)) { Text("Thông tin ứng dụng") }
            } else {
                Text(
                    text = "${name} · [${category}] - ${description.ifBlank { "暂无说明" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NodeInspectorCard(
    state: WorkflowEditorUiState,
    node: WorkflowNode,
    isConnecting: Boolean,
    onApply: (WorkflowNode) -> Unit,
    onBeginConnection: () -> Unit,
    onCancelConnection: () -> Unit,
    onRemove: () -> Unit,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onRemoveEdge: (String) -> Unit,
) {
    val meta = node.type.metadata()
    var title by remember(node.id, node.title) { mutableStateOf(node.title) }
    var description by remember(node.id, node.description) { mutableStateOf(node.description) }
    var timeout by remember(node.id, node.timeoutSeconds) { mutableStateOf(node.timeoutSeconds.toString()) }
    var policy by remember(node.id, node.failurePolicy) { mutableStateOf(node.failurePolicy) }
    var policyExpanded by remember { mutableStateOf(false) }

    val configMap = remember(node.id, node.config) {
        mutableStateMapOf<String, String>().apply { putAll(node.config) }
    }
    var showAdvancedJson by remember { mutableStateOf(false) }
    var rawJsonText by remember(node.id, node.config) {
        mutableStateOf(PrettyJson.encodeToString(node.config))
    }
    val parsedConfig = remember(rawJsonText) {
        runCatching { PrettyJson.decodeFromString<Map<String, String>>(rawJsonText) }.getOrNull()
    }

    RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RuntimeIcon(meta.icon, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(meta.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = node.id,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RuntimeOutlinedButton(
                    onClick = onRemove,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Text("删除", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "💡 Tác dụng nút: ${meta.summary}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = meta.guide,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Tiêu đề nút") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Ghi chú / mô tả nút") },
                minLines = 1,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Cấu hình tham số lõi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            when (node.type) {
                WorkflowNodeType.BASH_COMMAND -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(16.dp), tint = Color(0xFF06B6D4))
                                Text("Dòng lệnh Shell Terminal (thực thi trong môi trường sandbox)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF06B6D4))
                            }
                            OutlinedTextField(
                                value = configMap["command"].orEmpty(),
                                onValueChange = { configMap["command"] = it },
                                label = { Text("Lệnh Bash (nhập lệnh thực thi ở đây)") },
                                placeholder = { Text("Ví dụ: git status --short && git diff") },
                                supportingText = { Text("Hỗ trợ tham chiếu đầu ra bước trước: \${previous.output}, workspace: \${WORKSPACE_PATH}") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Preset lệnh thường dùng (nhấp để điền trực tiếp)：", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "Kernel hệ thống" to "echo '=== Nhân hệ thống ===' && uname -a && cat /etc/os-release | head -n 8",
                                    "Bộ nhớ và đĩa" to "echo '=== Mức dùng tài nguyên ===' && free -h && df -h /",
                                    "Trạng thái Git" to "git status --short && git diff --stat",
                                    "Kiểm tra mạng" to "curl -I -s -m 5 https://www.baidu.com | head -n 4",
                                    "Khắc phục sự cố công cụ" to "for cmd in git python3 curl make; do which \$cmd && echo \"✔ \$cmd\" || echo \"✘ \$cmd\"; done",
                                ).forEach { (name, cmd) ->
                                    Surface(
                                        onClick = { configMap["command"] = cmd },
                                        shape = RoundedCornerShape(50),
                                        color = Color(0xFF06B6D4).copy(alpha = 0.14f),
                                    ) {
                                        Text(
                                            text = "+ $name",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF06B6D4),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        )
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = configMap["workingDirectory"].orEmpty(),
                                onValueChange = { configMap["workingDirectory"] = it },
                                label = { Text("Thư mục làm việc (tùy chọn)") },
                                placeholder = { Text("Để trống mặc định là thư mục gốc dự án hiện tại") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                WorkflowNodeType.CONDITION_BRANCH -> {
                    OutlinedTextField(
                        value = configMap["expression"] ?: configMap["condition"].orEmpty(),
                        onValueChange = {
                            configMap["expression"] = it
                            configMap.remove("condition")
                        },
                        label = { Text("Biểu thức điều kiện") },
                        placeholder = { Text("exitCode == 0  hoặc  \${HOST_PRIVILEGED} == true") },
                        supportingText = {
                            Text("Hỗ trợ so sánh exitCode / output contains / \${VAR}, cũng như && ||. Thỏa mãn→exit 0, nếu không exit 1.")
                        },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BranchRouterCard(
                        state = state,
                        node = node,
                        onConnectNodes = onConnectNodes,
                        onRemoveEdge = onRemoveEdge,
                    )
                }

                WorkflowNodeType.PROCESS_SERVICE -> {
                    OutlinedTextField(
                        value = configMap["command"].orEmpty(),
                        onValueChange = { configMap["command"] = it },
                        label = { Text("Lệnh khởi động dịch vụ nền (bắt buộc)") },
                        placeholder = { Text("Ví dụ: python3 -m http.server 8080") },
                        supportingText = { Text("Sẽ chạy thường trú ở nền sandbox, ID tiến trình lưu tại \${PROCESS_ID}") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["workingDirectory"].orEmpty(),
                        onValueChange = { configMap["workingDirectory"] = it },
                        label = { Text("Thư mục làm việc (tùy chọn)") },
                        placeholder = { Text("Để trống thì mặc định là thư mục gốc dự án") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.AGENT_INFERENCE -> {
                    OutlinedTextField(
                        value = configMap["prompt"].orEmpty(),
                        onValueChange = { configMap["prompt"] = it },
                        label = { Text("Yêu cầu nhiệm vụ / prompt (bắt buộc)") },
                        placeholder = { Text("Ví dụ: rà soát khác biệt của node trước và đề xuất tối ưu") },
                        supportingText = { Text("Hỗ trợ dùng \${previous.output} để tham chiếu đầu ra node thượng nguồn") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["role"].orEmpty(),
                        onValueChange = { configMap["role"] = it },
                        label = { Text("Vai trò Agent (tùy chọn)") },
                        placeholder = { Text("Ví dụ: Kiến trúc sư Android kỳ cựu") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["modelId"].orEmpty(),
                        onValueChange = { configMap["modelId"] = it },
                        label = { Text("ID cấu hình model cố định (tùy chọn)") },
                        placeholder = { Text("Để trống thì dùng WORKFLOW_MODEL_ID được chọn lúc chạy") },
                        supportingText = { Text("Ưu tiên hơn model được chọn trong hộp thoại chạy; thường để trống là được") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.SUBAGENT_DELEGATE -> {
                    OutlinedTextField(
                        value = configMap["prompt"].orEmpty(),
                        onValueChange = { configMap["prompt"] = it },
                        label = { Text("Nhiệm vụ con cần (bắt buộc)") },
                        placeholder = { Text("Ví dụ: chạy unit test và thử sửa các case thất bại") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["role"].orEmpty(),
                        onValueChange = { configMap["role"] = it },
                        label = { Text("Vai trò Agent con (tùy chọn)") },
                        placeholder = { Text("Ví dụ: Trợ lý sửa lỗi test") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.TIANXUAN_BUILD -> {
                    var modeExpanded by remember { mutableStateOf(false) }
                    var typeExpanded by remember { mutableStateOf(false) }
                    val currentMode = configMap["mode"] ?: "build"
                    val currentType = configMap["projectType"] ?: "android"

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            RuntimeOutlinedButton(onClick = { modeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Chế độ: $currentMode", maxLines = 1)
                            }
                            DropdownMenu(expanded = modeExpanded, onDismissRequest = { modeExpanded = false }) {
                                listOf("build" to "Build tiêu chuẩn", "doctor" to "Khám sức khỏe môi trường", "analyze" to "Phân tích tĩnh").forEach { (m, l) ->
                                    DropdownMenuItem(text = { Text("$l ($m)") }, onClick = { configMap["mode"] = m; modeExpanded = false })
                                }
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            RuntimeOutlinedButton(onClick = { typeExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Loại: $currentType", maxLines = 1)
                            }
                            DropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                                listOf("android", "cmake", "cargo", "gradle").forEach { t ->
                                    DropdownMenuItem(text = { Text(t) }, onClick = { configMap["projectType"] = t; typeExpanded = false })
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = configMap["task"] ?: "assembleDebug",
                        onValueChange = { configMap["task"] = it },
                        label = { Text("Build Task") },
                        placeholder = { Text("Ví dụ: assembleDebug hoặc build") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.HOST_ACTION -> {
                    HostActionConfigFields(configMap = configMap)
                }

                WorkflowNodeType.DELAY -> {
                    OutlinedTextField(
                        value = configMap["seconds"] ?: "1",
                        onValueChange = { configMap["seconds"] = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                        label = { Text("Số giây chờ") },
                        supportingText = { Text("Hỗ trợ 0–600 giây, có thể ghi số thập phân") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.SET_VARIABLE -> {
                    OutlinedTextField(
                        value = configMap["variables"].orEmpty(),
                        onValueChange = { configMap["variables"] = it },
                        label = { Text("Gán biến (mỗi dòng KEY=value)") },
                        placeholder = { Text("FOO=bar\nPATH=\${WORKSPACE_PATH}") },
                        supportingText = { Text("Hỗ trợ \${VAR} / \${previous.output} / \${nodeId.output}") },
                        minLines = 3,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.HUMAN_APPROVAL -> {
                    OutlinedTextField(
                        value = configMap["prompt"].orEmpty(),
                        onValueChange = { configMap["prompt"] = it },
                        label = { Text("Giải thích xác nhận phê duyệt (bắt buộc)") },
                        placeholder = { Text("Ví dụ: có xác nhận push bản sửa lên repo từ xa không?") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.TRIGGER -> {
                    Text(
                        text = "Điểm vào khởi động workflow. Nhấn “Chạy gỡ lỗi” ở trên cùng để kích hoạt toàn bộ luồng DAG từ đây.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                WorkflowNodeType.TERMINAL_OUTPUT -> {
                    Text(
                        text = "Nút kết thúc workflow. Sẽ tự động thu thập và lưu trữ đầu ra chuẩn, log lỗi và tệp sản phẩm của tất cả bước thượng nguồn.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedJson = !showAdvancedJson }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (showAdvancedJson) "Thu gọn cấu hình nâng cao (JSON)" else "Mở rộng cấu hình nâng cao (JSON)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                RuntimeIcon(
                    name = if (showAdvancedJson) RuntimeIconName.ChevronUp else RuntimeIconName.ChevronDown,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            if (showAdvancedJson) {
                OutlinedTextField(
                    value = rawJsonText,
                    onValueChange = {
                        rawJsonText = it
                        parsedConfig?.let { parsed ->
                            configMap.clear()
                            configMap.putAll(parsed)
                        }
                    },
                    label = { Text("Cấu hình JSON gốc") },
                    isError = parsedConfig == null,
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = timeout,
                    onValueChange = { timeout = it.filter(Char::isDigit).take(4) },
                    label = { Text("Timeout (giây)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Column(Modifier.weight(1.2f)) {
                    RuntimeOutlinedButton(onClick = { policyExpanded = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Chiến lược: ${policy.editorLabel()}", maxLines = 1)
                    }
                    DropdownMenu(expanded = policyExpanded, onDismissRequest = { policyExpanded = false }) {
                        FailurePolicy.entries.forEach { item ->
                            DropdownMenuItem(text = { Text(item.editorLabel()) }, onClick = { policy = item; policyExpanded = false })
                        }
                    }
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeOutlinedButton(
                    onClick = if (isConnecting) onCancelConnection else onBeginConnection,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isConnecting) "Hủy kết nối" else "Nối dây trong canvas…", maxLines = 1)
                }
                RuntimeButton(
                    onClick = {
                        val finalConfig = if (showAdvancedJson && parsedConfig != null) parsedConfig else configMap.toMap()
                        onApply(
                            node.copy(
                                title = title.trim().ifBlank { node.title },
                                description = description.trim(),
                                timeoutSeconds = timeout.toIntOrNull()?.coerceIn(1, 7200) ?: node.timeoutSeconds,
                                config = finalConfig,
                                failurePolicy = policy,
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Thay đổi tham số ứng dụng", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun HostActionConfigFields(configMap: MutableMap<String, String>) {
    var categoryExpanded by remember { mutableStateOf(false) }
    var actionExpanded by remember { mutableStateOf(false) }
    val grouped = remember { HostWorkflowActions.grouped() }
    val currentActionId = configMap["action"] ?: "status"
    val currentDef = HostWorkflowActions.find(currentActionId)
    val currentCategory = currentDef?.category ?: grouped.keys.firstOrNull().orEmpty()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                RuntimeOutlinedButton(onClick = { categoryExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Phân loại: $currentCategory", maxLines = 1)
                }
                DropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    grouped.keys.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category) },
                            onClick = {
                                categoryExpanded = false
                                val first = grouped[category]?.firstOrNull() ?: return@DropdownMenuItem
                                configMap["action"] = first.id
                            },
                        )
                    }
                }
            }
            Column(modifier = Modifier.weight(1.4f)) {
                RuntimeOutlinedButton(onClick = { actionExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(currentDef?.label ?: currentActionId, maxLines = 1)
                }
                DropdownMenu(expanded = actionExpanded, onDismissRequest = { actionExpanded = false }) {
                    grouped[currentCategory].orEmpty().forEach { action ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    buildString {
                                        append(action.label)
                                        if (action.privilege == HostWorkflowPrivilege.PRIVILEGED) append(" · Cần đặc quyền")
                                    },
                                )
                            },
                            onClick = {
                                configMap["action"] = action.id
                                actionExpanded = false
                            },
                        )
                    }
                }
            }
        }

        currentDef?.let { def ->
            Text(
                text = def.description + if (def.privilege == HostWorkflowPrivilege.PRIVILEGED) {
                    "\n⚠ Cần Shizuku hoặc Root để có hiệu lực."
                } else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            def.fields.forEach { field ->
                OutlinedTextField(
                    value = configMap[field.key].orEmpty(),
                    onValueChange = { configMap[field.key] = it },
                    label = { Text(if (field.required) "${field.label} *" else field.label) },
                    placeholder = { Text(field.hint) },
                    singleLine = !field.multiline,
                    minLines = if (field.multiline) 3 else 1,
                    maxLines = if (field.multiline) 6 else 1,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } ?: OutlinedTextField(
            value = configMap["command"].orEmpty(),
            onValueChange = { configMap["command"] = it },
            label = { Text("Lệnh máy chủ tùy chỉnh (khoang thoát)") },
            supportingText = { Text("Khi action không rõ sẽ thực thi lệnh này theo shell đặc quyền") },
            minLines = 2,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BranchRouterCard(
    state: WorkflowEditorUiState,
    node: WorkflowNode,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onRemoveEdge: (String) -> Unit,
) {
    val branchEdges = state.definition.edges.filter { it.fromNodeId == node.id }
    val otherNodes = state.definition.nodes.filter { it.id != node.id }

    val successEdge = branchEdges.firstOrNull { it.conditionExpression?.contains("exitCode == 0") == true || it.fromPort == "success" }
    val failureEdge = branchEdges.firstOrNull { it.conditionExpression?.contains("exitCode !=") == true || it.fromPort == "failure" }

    var successTargetId by remember(otherNodes) { mutableStateOf(otherNodes.firstOrNull()?.id.orEmpty()) }
    var failureTargetId by remember(otherNodes) { mutableStateOf(otherNodes.getOrNull(1)?.id ?: otherNodes.firstOrNull()?.id.orEmpty()) }
    var successExpanded by remember { mutableStateOf(false) }
    var failureExpanded by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFFEAB308).copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RuntimeIcon(RuntimeIconName.Link, Modifier.size(16.dp), tint = Color(0xFFEAB308))
                Text("Router nhánh điều kiện trực quan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFEAB308))
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "💡 Node sẽ tính giá trị biểu thức phía trên: đúng exitCode=0, sai exitCode=1 (bản thân node vẫn thành công, tiện cho phân luồng).\n" +
                        "Dưới đây có thể nhanh chóng nối nhánh thành công/thất bại tới node mục tiêu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(50), color = Color(0xFF10B981).copy(alpha = 0.18f)) {
                    Text("✔ Nhánh thành công (exitCode == 0)", style = MaterialTheme.typography.labelSmall, color = Color(0xFF10B981), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (successEdge != null) {
                    val target = state.definition.nodes.firstOrNull { it.id == successEdge.toNodeId }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Đã kết nối tới: ${target?.title ?: successEdge.toNodeId}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        RuntimeOutlinedButton(
                            onClick = { onRemoveEdge(successEdge.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Ngắt kết nối", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (otherNodes.isNotEmpty()) {
                    val currentSuccessNode = otherNodes.firstOrNull { it.id == successTargetId } ?: otherNodes.first()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            RuntimeOutlinedButton(onClick = { successExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Mục tiêu: ${currentSuccessNode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = successExpanded, onDismissRequest = { successExpanded = false }) {
                                otherNodes.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.title) },
                                        onClick = { successTargetId = target.id; successExpanded = false },
                                    )
                                }
                            }
                        }
                        RuntimeButton(
                            onClick = { onConnectNodes(node.id, currentSuccessNode.id, "output", "exitCode == 0") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Kết nối", maxLines = 1)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Surface(shape = RoundedCornerShape(50), color = Color(0xFFF43F5E).copy(alpha = 0.18f)) {
                    Text("✘ Nhánh thất bại (exitCode != 0)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFF43F5E), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                if (failureEdge != null) {
                    val target = state.definition.nodes.firstOrNull { it.id == failureEdge.toNodeId }
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Đã kết nối tới: ${target?.title ?: failureEdge.toNodeId}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        RuntimeOutlinedButton(
                            onClick = { onRemoveEdge(failureEdge.id) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text("Ngắt kết nối", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else if (otherNodes.isNotEmpty()) {
                    val currentFailureNode = otherNodes.firstOrNull { it.id == failureTargetId } ?: otherNodes.first()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.weight(1f)) {
                            RuntimeOutlinedButton(onClick = { failureExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Mục tiêu: ${currentFailureNode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            DropdownMenu(expanded = failureExpanded, onDismissRequest = { failureExpanded = false }) {
                                otherNodes.forEach { target ->
                                    DropdownMenuItem(
                                        text = { Text(target.title) },
                                        onClick = { failureTargetId = target.id; failureExpanded = false },
                                    )
                                }
                            }
                        }
                        RuntimeButton(
                            onClick = { onConnectNodes(node.id, currentFailureNode.id, "output", "exitCode != 0") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Kết nối", maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NodeConnectionsCard(
    state: WorkflowEditorUiState,
    node: WorkflowNode,
    onConnectNodes: (String, String, String, String?) -> Unit,
    onDisconnectAll: (String) -> Unit,
    onUpdateEdge: (WorkflowEdge) -> Unit,
    onRemoveEdge: (String) -> Unit,
) {
    val outgoingEdges = state.definition.edges.filter { it.fromNodeId == node.id }
    val incomingEdges = state.definition.edges.filter { it.toNodeId == node.id }
    val allConnected = outgoingEdges + incomingEdges

    val otherNodes = remember(state.definition.nodes, node.id) {
        state.definition.nodes.filter { it.id != node.id }
    }

    var selectedTargetId by remember(otherNodes) { mutableStateOf(otherNodes.firstOrNull()?.id.orEmpty()) }
    var selectedPort by remember { mutableStateOf("output") }
    var conditionText by remember { mutableStateOf("") }
    var targetExpanded by remember { mutableStateOf(false) }
    var portExpanded by remember { mutableStateOf(false) }

    RuntimeCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RuntimeIcon(RuntimeIconName.Link, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Nối node và topology (${allConnected.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (allConnected.isNotEmpty()) {
                    Surface(
                        onClick = { onDisconnectAll(node.id) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "Ngắt kết nối tất cả bằng một chạm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text("Đường nối ra (nối tới node kế tiếp):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (outgoingEdges.isEmpty()) {
                Text("Chưa có đường nối ra", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                outgoingEdges.forEach { edge ->
                    val targetNode = state.definition.nodes.firstOrNull { it.id == edge.toNodeId }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(50),
                                    ) {
                                        Text(
                                            text = edge.fromPort.portLabel(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                    Text("──►", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        text = targetNode?.title ?: edge.toNodeId,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (!edge.conditionExpression.isNullOrBlank()) {
                                    Text(
                                        text = "Điều kiện: ${edge.conditionExpression}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            RuntimeOutlinedButton(
                                onClick = { onRemoveEdge(edge.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                                Text("Ngắt kết nối", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Text("Đường nối đến (từ nút trước):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (incomingEdges.isEmpty()) {
                Text("Hiện chưa có đường nối đến", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                incomingEdges.forEach { edge ->
                    val sourceNode = state.definition.nodes.firstOrNull { it.id == edge.fromNodeId }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = sourceNode?.title ?: edge.fromNodeId,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text("──►", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Node này", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                if (!edge.conditionExpression.isNullOrBlank()) {
                                    Text(
                                        text = "Điều kiện: ${edge.conditionExpression}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.tertiary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            RuntimeOutlinedButton(
                                onClick = { onRemoveEdge(edge.id) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.error)
                                Text("Ngắt kết nối", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("➕ Thêm đường nối tới nút khác:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (otherNodes.isEmpty()) {
                Text("Trong canvas chưa có nút khả dụng nào khác", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val currentTargetNode = otherNodes.firstOrNull { it.id == selectedTargetId } ?: otherNodes.first()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        RuntimeOutlinedButton(
                            onClick = { targetExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Mục tiêu: ${currentTargetNode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = targetExpanded, onDismissRequest = { targetExpanded = false }) {
                            otherNodes.forEach { target ->
                                DropdownMenuItem(
                                    text = { Text("${target.title} (${target.type.editorLabel()})") },
                                    onClick = {
                                        selectedTargetId = target.id
                                        targetExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.weight(1f)) {
                            RuntimeOutlinedButton(
                                onClick = { portExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Cổng: ${selectedPort.portLabel()}", maxLines = 1)
                            }
                            DropdownMenu(expanded = portExpanded, onDismissRequest = { portExpanded = false }) {
                                EdgePorts.forEach { port ->
                                    DropdownMenuItem(
                                        text = { Text(port.portLabel()) },
                                        onClick = {
                                            selectedPort = port
                                            portExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = conditionText,
                            onValueChange = { conditionText = it },
                            label = { Text("Điều kiện (tùy chọn)") },
                            placeholder = { Text("Như exitCode == 0") },
                            singleLine = true,
                            modifier = Modifier.weight(1.5f),
                        )
                    }

                    RuntimeButton(
                        onClick = {
                            onConnectNodes(node.id, currentTargetNode.id, selectedPort, conditionText.trim().ifBlank { null })
                            conditionText = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp))
                        Text("Lập tức tạo đường nối", maxLines = 1)
                    }
                }
            }
        }
    }
}

data class NodeTypeMeta(
    val type: WorkflowNodeType,
    val label: String,
    val icon: RuntimeIconName,
    val summary: String,
    val guide: String,
)

fun WorkflowNodeType.metadata(): NodeTypeMeta = when (this) {
    WorkflowNodeType.TRIGGER -> NodeTypeMeta(
        type = this,
        label = "Trigger",
        icon = RuntimeIconName.Play,
        summary = "Điểm vào khởi động luồng, hỗ trợ nhấn thủ công hoặc kích hoạt bằng sự kiện bên ngoài",
        guide = "Làm nút khởi đầu của đồ thị DAG. Khi bạn nhấp «Chạy gỡ lỗi» ở trên cùng hoặc nhận tín hiệu kích hoạt, luồng công việc bắt đầu thực thi từ đây xuống dưới.",
    )
    WorkflowNodeType.BASH_COMMAND -> NodeTypeMeta(
        type = this,
        label = "Dòng lệnh Shell",
        icon = RuntimeIconName.Terminal,
        summary = "Thực thi một lệnh hoặc script Shell trong sandbox PRoot Linux",
        guide = "Có thể thực thi bất kỳ lệnh đã cài trong sandbox (như git, make, python, curl v.v.). Hỗ trợ tham chiếu thư mục gốc dự án qua \${WORKSPACE_PATH}, hoặc dùng \${previous.output} để lấy đầu ra của bước trước.",
    )
    WorkflowNodeType.PROCESS_SERVICE -> NodeTypeMeta(
        type = this,
        label = "Dịch vụ thường trú nền",
        icon = RuntimeIconName.Settings,
        summary = "Khởi động tiến trình daemon thường trú nền của sandbox, không chặn luồng tiếp theo tiếp tục thực thi",
        guide = "Phù hợp để khởi động http-server, dịch vụ Web hoặc tiến trình listener. ID tiến trình sẽ tự động lưu trong biến \${PROCESS_ID} để quy trình sau tham chiếu.",
    )
    WorkflowNodeType.AGENT_INFERENCE -> NodeTypeMeta(
        type = this,
        label = "Suy luận Agent AI",
        icon = RuntimeIconName.Sparkles,
        summary = "Điều phối Agent AI kết hợp chỉ thị nhiệm vụ và đầu ra thượng nguồn để phân tích và tạo kết quả",
        guide = "Giao chỉ thị nhiệm vụ (Prompt) và sản phẩm thượng nguồn cho Agent AI xử lý. Hỗ trợ kết hợp \${previous.output} để rà soát mã, khắc phục sự cố hoặc tạo bản vá sửa lỗi.",
    )
    WorkflowNodeType.SUBAGENT_DELEGATE -> NodeTypeMeta(
        type = this,
        label = "Phái Agent con",
        icon = RuntimeIconName.Hub,
        summary = "Ủy thác Agent con chuyên dụng độc lập để thực thi nhiệm vụ chi tiết hơn",
        guide = "Phái Agent con của vai trò cụ thể (như trình tạo unit test, nhân viên phân tích lỗi build) xử lý nhiệm vụ chuyên biệt, cách ly ngữ cảnh Agent chính.",
    )
    WorkflowNodeType.TIANXUAN_BUILD -> NodeTypeMeta(
        type = this,
        label = "Build ngoại tuyến Thiên Huyền",
        icon = RuntimeIconName.Package,
        summary = "Gọi engine xây dựng ngoại tuyến trong sandbox để biên dịch đóng gói Android/C++/Rust",
        guide = "Gọi engine biên dịch tianxuan-build tích hợp trong sandbox. Hỗ trợ các loại như android, cmake, cargo, có thể trực tiếp tạo ra gói cài APK hoặc tệp nhị phân thực thi.",
    )
    WorkflowNodeType.CONDITION_BRANCH -> NodeTypeMeta(
        type = this,
        label = "Router nhánh điều kiện",
        icon = RuntimeIconName.Link,
        summary = "Đánh giá biểu thức và phân luồng theo đúng/không đúng",
        guide = "Hỗ trợ exitCode, output contains, so sánh \${biến}, cùng && / ||. Khi thành lập exitCode=0, nếu không thì bằng 1; hãy dùng điều kiện bên cạnh hoặc cổng success/failure để hứng nhánh.",
    )
    WorkflowNodeType.HUMAN_APPROVAL -> NodeTypeMeta(
        type = this,
        label = "Kiểm soát phê duyệt thủ công",
        icon = RuntimeIconName.Alert,
        summary = "Tạm dừng quy trình và bật hộp thoại xác nhận, chờ xác nhận thủ công hoặc nhập tham số rồi tiếp tục",
        guide = "Điểm kiểm soát an toàn trước các bước quan trọng (như phát hành, dọn dẹp phá hủy hoặc lên live). Khi luồng thực thi tới đây sẽ tự động tạm dừng và bật hộp thoại, chờ bạn xác nhận thủ công hoặc điền tham số cần thiết.",
    )
    WorkflowNodeType.HOST_ACTION -> NodeTypeMeta(
        type = this,
        label = "Thao tác hệ thống máy chủ",
        icon = RuntimeIconName.Android,
        summary = "Năng lực máy chủ Android như mở ứng dụng, gửi broadcast, sửa cài đặt, điều khiển màn hình",
        guide = "Xuyên sandbox gọi Android: quản lý ứng dụng, Intent/broadcast, cài đặt hệ thống, tự động hóa GUI, thông báo và shell đặc quyền. Các thao tác gắn nhãn「cần đặc quyền」yêu cầu Shizuku hoặc Root.",
    )
    WorkflowNodeType.DELAY -> NodeTypeMeta(
        type = this,
        label = "Chờ trễ",
        icon = RuntimeIconName.Speed,
        summary = "Tạm dừng vài giây rồi tiếp tục các node sau",
        guide = "Dùng để chờ chuyển giao diện, broadcast đến nơi hoặc dành thời gian cho người quan sát thủ công.",
    )
    WorkflowNodeType.SET_VARIABLE -> NodeTypeMeta(
        type = this,
        label = "Đặt biến",
        icon = RuntimeIconName.Tune,
        summary = "Tiêm biến KEY=value vào các nút tiếp theo",
        guide = "Mỗi dòng một phép gán, bên phải hỗ trợ nội suy \${VAR} . Biến được ghi sẽ vào ngữ cảnh toàn cục, có thể dùng cho điều kiện và động tác máy chủ tham chiếu.",
    )
    WorkflowNodeType.TERMINAL_OUTPUT -> NodeTypeMeta(
        type = this,
        label = "Đầu ra lưu trữ kết quả",
        icon = RuntimeIconName.Check,
        summary = "Điểm cuối workflow, tự động tổng hợp mọi nhật ký đầu ra, thời gian và tệp sản phẩm",
        guide = "Node kết thúc cuối cùng của luồng DAG. Sẽ tự động thu thập và hiển thị stdout terminal, exit code và danh sách tệp sản phẩm của tất cả giai đoạn thượng nguồn.",
    )
}

private fun WorkflowNodeType.editorLabel() = metadata().label

private fun FailurePolicy.editorLabel() = when (this) {
    FailurePolicy.ABORT -> "Chấm dứt quy trình"
    FailurePolicy.CONTINUE -> "Tiếp tục thực thi"
    FailurePolicy.RETRY_ONCE -> "Thử lại một lần"
    FailurePolicy.ASK_USER -> "Hỏi người dùng"
}

private fun String.portLabel() = when (lowercase()) {
    "success" -> "Thành công (success)"
    "failure" -> "Thất bại (failure)"
    else -> "Đầu ra bất kỳ (output)"
}

private val EdgePorts = listOf("output", "success", "failure")
