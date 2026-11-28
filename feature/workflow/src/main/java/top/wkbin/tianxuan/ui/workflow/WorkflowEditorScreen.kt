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

/** Json định dạng đẹp dùng chung: tạo instance tốn chi phí, không tạo lặp lại trong remember/tái kết hợp. */
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
                    color = if (message == "Đã lưu" || message == "Hãy chọn nút đích") {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message == "Đã lưu" || message == "Hãy chọn nút đích") {
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
                                            contentDescription = "Đóng bảng",
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

    // Hộp thoại xác nhận xóa nút
    nodePendingDelete?.let { target ->
        RuntimeAlertDialog(
            onDismissRequest = { nodePendingDelete = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                    Text("Xác nhận xóa nút", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Bạn có chắc muốn xóa “${target.title}”?\nTất cả liên kết đầu vào và đầu ra của nút này cũng sẽ bị xóa.",
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
                    Text("Hủy")
                }
            },
        )
    }

    // Hộp thoại nhật ký chạy và bảng điều khiển
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
        // Canvas phủ kín màn hình, loại bỏ hoàn toàn thanh dưới cố định 56dp
        canvas(Modifier.fillMaxSize())

        // Chỉ bật trang thông tin bằng cách trượt lên khi nhấn một lần vào nút và mở; không giữ trạng thái
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
                        // Thanh chỉ báo tay nắm kéo ở giữa phía trên
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

                        // Thanh tiêu đề hộp thoại: nhãn loại dạng viên màu, tiêu đề nút và nút đóng
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
                                contentDescription = "Thu gọn bảng",
                            ) {
                                RuntimeIcon(RuntimeIconName.Close, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider()

                        // Trình kiểm tra cấu hình thuộc tính và kết nối
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
        shape = RoundedCornerShape(0.dp), // Loại bỏ góc bo, áp sát mép dưới
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
            // Thêm nút
            DockActionButton(
                icon = RuntimeIconName.Plus,
                label = "Thêm",
                onClick = { addDialogVisible = true },
                tint = MaterialTheme.colorScheme.primary,
            )

            // Chạy / dừng gỡ lỗi
            if (isRunning) {
                DockActionButton(
                    icon = RuntimeIconName.Stop,
                    label = "Dừng",
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

            // Bảng điều khiển / nhật ký
            if (activeRunState != null) {
                DockActionButton(
                    icon = RuntimeIconName.Terminal,
                    label = if (isRunning) "Đang chạy" else "Nhật ký",
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

            // Xóa nút (hiển thị khi đã chọn nút và không chạy)
            if (state.selectedNodeId != null && !isRunning) {
                DockActionButton(
                    icon = RuntimeIconName.Trash,
                    label = "Xóa",
                    onClick = onDeleteSelectedNode,
                    tint = MaterialTheme.colorScheme.error,
                )
            }

            // Tự động sắp xếp
            DockActionButton(
                icon = RuntimeIconName.Tune,
                label = "Sắp xếp",
                onClick = onAutoLayout,
            )

            // Hoàn tác
            DockActionButton(
                icon = RuntimeIconName.Back,
                label = "Hoàn tác",
                enabled = state.canUndo && !isRunning,
                onClick = onUndo,
            )

            // Làm lại
            DockActionButton(
                icon = RuntimeIconName.Reverse,
                label = "Làm lại",
                enabled = state.canRedo && !isRunning,
                onClick = onRedo,
            )

            // Lưu
            DockActionButton(
                icon = RuntimeIconName.Save,
                label = when {
                    state.isSaving -> "Đang lưu"
                                    state.isDirty -> "Lưu*"
                                    else -> "Đã lưu"
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
        shape = RoundedCornerShape(0.dp), // Loại bỏ góc bo
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
            appendLine("=== Nhật ký bảng điều khiển workflow Thiên Huyền “${activeRunState.definition.name}” ===")
            appendLine("Trạng thái chung: ${runStatusLabel(activeRunState.status)}  |  Thời gian thực thi: ${durationMs}ms")
            activeRunState.error?.let {
                appendLine("❌ Thông tin lỗi: $it")
            }
            appendLine()
            activeRunState.definition.nodes.forEach { node ->
                val run = activeRunState.nodeStates[node.id]
                val status = run?.status ?: NodeRunStatus.PENDING
                appendLine("--------------------------------------------------")
                appendLine("[${statusLabel(status)}] Nút: ${node.title} (${node.id})")
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
                Text("Bảng điều khiển chạy và gỡ lỗi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                    Text("Sao chép nhật ký")
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
            RuntimeOutlinedButton(onClick = onDismiss) { Text("Đóng") }
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
    WorkflowRunStatus.IDLE -> "Chưa chạy"
    WorkflowRunStatus.RUNNING -> "Đang chạy"
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
            Text("Thêm nút workflow", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Chọn loại nút để thêm vào canvas. Sau khi tạo, bạn có thể cấu hình tham số trong bảng kiểm tra:",
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
            RuntimeOutlinedButton(onClick = onDismiss) { Text("Hủy") }
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
                Text("Chọn một nút trên canvas để cấu hình tham số, quản lý liên kết hoặc xem nhật ký thực thi", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            Text("Trạng thái chạy và gỡ lỗi", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                            Text("Mở toàn bộ nhật ký terminal", maxLines = 1)
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
                    text = if (expanded) "Thu gọn" else "Chỉnh sửa",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (expanded) {
                OutlinedTextField(name, { name = it }, label = { Text("Tên workflow") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(category, { category = it }, label = { Text("Danh mục") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(description, { description = it }, label = { Text("Mô tả") }, minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                            RuntimeOutlinedButton(onClick = { onApply(name, description, category) }, modifier = Modifier.align(Alignment.End)) { Text("Áp dụng thông tin") }
            } else {
                Text(
                    text = "${name} · [${category}] - ${description.ifBlank { "Chưa có mô tả" }}",
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
                    Text("Xóa", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "💡 Chức năng của nút: ${meta.summary}",
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

            Text("Cấu hình tham số chính", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

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
                                Text("Dòng lệnh shell trong terminal (thực thi trong sandbox)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFF06B6D4))
                            }
                            OutlinedTextField(
                                value = configMap["command"].orEmpty(),
                                onValueChange = { configMap["command"] = it },
                                label = { Text("Lệnh Bash (nhập lệnh cần thực thi)") },
                                                    placeholder = { Text("Ví dụ: git status --short && git diff") },
                                                    supportingText = { Text("Tham chiếu đầu ra nút trước: \${previous.output}; workspace: \${WORKSPACE_PATH}") },
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text("Lệnh có sẵn thường dùng (chạm để điền):", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "Nhân hệ điều hành" to "echo '=== Nhân hệ điều hành ===' && uname -a && cat /etc/os-release | head -n 8",
                                    "Bộ nhớ và ổ đĩa" to "echo '=== Mức sử dụng tài nguyên ===' && free -h && df -h /",
                                    "Trạng thái Git" to "git status --short && git diff --stat",
                                    "Kiểm tra mạng" to "curl -I -s -m 5 https://www.baidu.com | head -n 4",
                                    "Kiểm tra công cụ" to "for cmd in git python3 curl make; do which \$cmd && echo \"✔ \$cmd\" || echo \"✘ \$cmd\"; done",
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
                                label = { Text("Thư mục làm việc (không bắt buộc)") },
                                placeholder = { Text("Để trống để dùng thư mục gốc của dự án hiện tại") },
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
                            Text("Hỗ trợ so sánh exitCode / output contains / \${VAR} và && ||. Đúng → exit 0, nếu không → exit 1.")
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
                        label = { Text("Lệnh khởi chạy dịch vụ nền (bắt buộc)") },
                                            placeholder = { Text("Ví dụ: python3 -m http.server 8080") },
                                            supportingText = { Text("Chạy thường trú trong sandbox; ID tiến trình được lưu vào \${PROCESS_ID}") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["workingDirectory"].orEmpty(),
                        onValueChange = { configMap["workingDirectory"] = it },
                        label = { Text("Thư mục làm việc (không bắt buộc)") },
                        placeholder = { Text("Để trống để dùng thư mục gốc của dự án") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.AGENT_INFERENCE -> {
                    OutlinedTextField(
                        value = configMap["prompt"].orEmpty(),
                        onValueChange = { configMap["prompt"] = it },
                        label = { Text("Yêu cầu nhiệm vụ / prompt (bắt buộc)") },
                                            placeholder = { Text("Ví dụ: Rà soát diff của nút trước và đề xuất cải tiến") },
                                            supportingText = { Text("Dùng \${previous.output} để tham chiếu đầu ra của nút trước") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["role"].orEmpty(),
                        onValueChange = { configMap["role"] = it },
                        label = { Text("Vai trò agent (tùy chọn)") },
                                            placeholder = { Text("Ví dụ: Kiến trúc sư Android cấp cao") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["modelId"].orEmpty(),
                        onValueChange = { configMap["modelId"] = it },
                        label = { Text("ID cấu hình mô hình cố định (tùy chọn)") },
                                            placeholder = { Text("Để trống để dùng WORKFLOW_MODEL_ID đã chọn khi chạy") },
                                            supportingText = { Text("Ưu tiên hơn mô hình trong hộp thoại chạy; thường có thể để trống") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.SUBAGENT_DELEGATE -> {
                    OutlinedTextField(
                        value = configMap["prompt"].orEmpty(),
                        onValueChange = { configMap["prompt"] = it },
                        label = { Text("Yêu cầu nhiệm vụ con (bắt buộc)") },
                                            placeholder = { Text("Ví dụ: Chạy kiểm thử đơn vị và thử sửa các ca thất bại") },
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = configMap["role"].orEmpty(),
                        onValueChange = { configMap["role"] = it },
                        label = { Text("Vai trò agent con (tùy chọn)") },
                                            placeholder = { Text("Ví dụ: Trợ lý sửa lỗi kiểm thử") },
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
                                listOf("build" to "Build tiêu chuẩn", "doctor" to "Kiểm tra môi trường", "analyze" to "Phân tích tĩnh").forEach { (m, l) ->
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
                        label = { Text("Tác vụ build") },
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
                                            supportingText = { Text("Hỗ trợ 0–600 giây, có thể dùng số thập phân") },
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
                        label = { Text("Nội dung xác nhận phê duyệt (bắt buộc)") },
                                            placeholder = { Text("Ví dụ: Bạn có xác nhận đẩy bản sửa lên repo từ xa không?") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                WorkflowNodeType.TRIGGER -> {
                    Text(
                        text = "Điểm bắt đầu workflow. Chọn chạy và gỡ lỗi ở phía trên để kích hoạt toàn bộ DAG từ đây.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                WorkflowNodeType.TERMINAL_OUTPUT -> {
                    Text(
                        text = "Nút kết thúc workflow. Tự động thu thập và lưu trữ đầu ra chuẩn, nhật ký lỗi và tệp được tạo từ mọi bước trước.",
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
                    text = if (showAdvancedJson) "Thu gọn cấu hình nâng cao (JSON)" else "Mở cấu hình nâng cao (JSON)",
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
                    label = { Text("Thời gian chờ tối đa (giây)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Column(Modifier.weight(1.2f)) {
                    RuntimeOutlinedButton(onClick = { policyExpanded = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("Chính sách: ${policy.editorLabel()}", maxLines = 1)
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
                    Text(if (isConnecting) "Hủy kết nối" else "Kết nối trên canvas…", maxLines = 1)
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
                    Text("Áp dụng thay đổi tham số", maxLines = 1)
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
                    Text("Danh mục: $currentCategory", maxLines = 1)
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
            label = { Text("Lệnh máy chủ tùy chỉnh (vùng đặc quyền)") },
            supportingText = { Text("Khi action không xác định, lệnh này sẽ được thực thi bằng shell có đặc quyền") },
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
                Text("Bộ định tuyến nhánh điều kiện trực quan", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color(0xFFEAB308))
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "💡 Nút sẽ đánh giá biểu thức phía trên: đúng thì exitCode=0, sai thì exitCode=1 (bản thân nút vẫn thành công để thuận tiện phân nhánh).\n" +
                        "Có thể nhanh chóng nối nhánh thành công/thất bại bên dưới tới nút đích.",
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
                                Text("Đích: ${currentSuccessNode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                Text("Đích: ${currentFailureNode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text("Các kết nối và cấu trúc liên kết của nút (${allConnected.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (allConnected.isNotEmpty()) {
                    Surface(
                        onClick = { onDisconnectAll(node.id) },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = "Ngắt kết nối tất cả bằng một lần chạm",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            Text("Kết nối đi (tới các nút tiếp theo):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (outgoingEdges.isEmpty()) {
                Text("Chưa có kết nối đi", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            Text("Kết nối đi vào (từ các nút trước đó):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (incomingEdges.isEmpty()) {
                Text("Chưa có kết nối đi vào", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    Text("Nút hiện tại", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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

            Text("➕ Thêm kết nối tới nút khác:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            if (otherNodes.isEmpty()) {
                Text("Chưa có nút nào khác trên canvas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val currentTargetNode = otherNodes.firstOrNull { it.id == selectedTargetId } ?: otherNodes.first()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        RuntimeOutlinedButton(
                            onClick = { targetExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Đích: ${currentTargetNode.title}", maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                            label = { Text("Điều kiện (không bắt buộc)") },
                            placeholder = { Text("Ví dụ: exitCode == 0") },
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
                        Text("Tạo kết nối ngay", maxLines = 1)
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
        label = "Kích hoạt",
        icon = RuntimeIconName.Play,
        summary = "Điểm bắt đầu, hỗ trợ kích hoạt thủ công hoặc bằng sự kiện bên ngoài",
        guide = "Nút bắt đầu của DAG. Workflow bắt đầu thực thi từ đây khi bạn chọn chạy và gỡ lỗi ở phía trên hoặc khi nhận tín hiệu kích hoạt.",
    )
    WorkflowNodeType.BASH_COMMAND -> NodeTypeMeta(
        type = this,
        label = "Dòng lệnh shell",
        icon = RuntimeIconName.Terminal,
        summary = "Thực thi một lệnh hoặc script shell trong sandbox Linux PRoot",
        guide = "Có thể chạy mọi lệnh đã cài trong sandbox (như git, make, python, curl). Dùng \${WORKSPACE_PATH} để tham chiếu thư mục gốc dự án hoặc \${previous.output} để lấy đầu ra của nút trước.",
    )
    WorkflowNodeType.PROCESS_SERVICE -> NodeTypeMeta(
        type = this,
        label = "Dịch vụ chạy nền",
        icon = RuntimeIconName.Settings,
        summary = "Khởi chạy tiến trình thường trú trong sandbox mà không chặn các bước sau",
        guide = "Phù hợp để khởi chạy http-server, dịch vụ web hoặc tiến trình lắng nghe. ID tiến trình tự động được lưu vào biến \${PROCESS_ID} để các bước sau tham chiếu.",
    )
    WorkflowNodeType.AGENT_INFERENCE -> NodeTypeMeta(
        type = this,
        label = "Suy luận agent AI",
        icon = RuntimeIconName.Sparkles,
        summary = "Điều phối agent AI phân tích yêu cầu và đầu ra của các bước trước để tạo kết quả",
        guide = "Giao prompt và đầu ra của các bước trước cho agent AI xử lý. Có thể dùng \${previous.output} để rà soát mã, chẩn đoán lỗi hoặc tạo bản vá.",
    )
    WorkflowNodeType.SUBAGENT_DELEGATE -> NodeTypeMeta(
        type = this,
        label = "Ủy phái agent con",
        icon = RuntimeIconName.Hub,
        summary = "Giao nhiệm vụ chi tiết cho agent con chuyên trách và độc lập",
        guide = "Giao việc cho agent con có vai trò cụ thể (như tạo kiểm thử đơn vị hoặc phân tích lỗi build), tách biệt khỏi ngữ cảnh của agent chính.",
    )
    WorkflowNodeType.TIANXUAN_BUILD -> NodeTypeMeta(
        type = this,
        label = "Build ngoại tuyến CTHarness",
        icon = RuntimeIconName.Package,
        summary = "Dùng bộ máy build ngoại tuyến trong sandbox để biên dịch, đóng gói Android/C++/Rust",
        guide = "Gọi bộ máy biên dịch tianxuan-build tích hợp trong sandbox. Hỗ trợ android, cmake, cargo và có thể tạo trực tiếp APK hoặc tệp nhị phân thực thi.",
    )
    WorkflowNodeType.CONDITION_BRANCH -> NodeTypeMeta(
        type = this,
        label = "Định tuyến nhánh điều kiện",
        icon = RuntimeIconName.Link,
        summary = "Đánh giá biểu thức và chia nhánh theo kết quả đúng/sai",
        guide = "Hỗ trợ exitCode, output contains, so sánh biến theo dạng \${variable} và && / ||. Khi điều kiện đúng, exitCode=0; nếu không, bằng 1. Dùng điều kiện trên kết nối hoặc cổng thành công/thất bại để nối nhánh.",
    )
    WorkflowNodeType.HUMAN_APPROVAL -> NodeTypeMeta(
        type = this,
        label = "Người dùng phê duyệt",
        icon = RuntimeIconName.Alert,
        summary = "Tạm dừng và mở hộp thoại, chờ người dùng xác nhận hoặc nhập tham số để tiếp tục",
        guide = "Điểm kiểm soát an toàn trước các bước quan trọng như phát hành, xóa dữ liệu hoặc triển khai. Workflow tự động tạm dừng tại đây và mở hộp thoại chờ bạn xác nhận hoặc nhập tham số cần thiết.",
    )
    WorkflowNodeType.HOST_ACTION -> NodeTypeMeta(
        type = this,
        label = "Thao tác hệ thống máy chủ",
        icon = RuntimeIconName.Android,
        summary = "Khả năng của máy chủ Android: mở ứng dụng, gửi broadcast, đổi cài đặt và điều khiển màn hình",
        guide = "Gọi Android từ sandbox: quản lý ứng dụng, Intent/broadcast, cài đặt hệ thống, tự động hóa GUI, thông báo và shell đặc quyền. Thao tác có nhãn “Cần đặc quyền” yêu cầu Shizuku hoặc Root.",
    )
    WorkflowNodeType.DELAY -> NodeTypeMeta(
        type = this,
        label = "Chờ theo độ trễ",
        icon = RuntimeIconName.Speed,
        summary = "Tạm dừng vài giây rồi tiếp tục với các nút sau",
        guide = "Dùng để chờ chuyển giao diện, xử lý broadcast hoặc dành thời gian cho người dùng quan sát.",
    )
    WorkflowNodeType.SET_VARIABLE -> NodeTypeMeta(
        type = this,
        label = "Đặt biến",
        icon = RuntimeIconName.Tune,
        summary = "Truyền biến KEY=value cho các nút tiếp theo",
        guide = "Mỗi dòng là một phép gán, vế phải hỗ trợ nội suy \${VAR}. Biến được ghi vào ngữ cảnh toàn cục để điều kiện và thao tác máy chủ tham chiếu.",
    )
    WorkflowNodeType.TERMINAL_OUTPUT -> NodeTypeMeta(
        type = this,
        label = "Lưu trữ kết quả đầu ra",
        icon = RuntimeIconName.Check,
        summary = "Điểm kết thúc workflow, tự động tổng hợp nhật ký đầu ra, thời gian chạy và tệp đã tạo",
        guide = "Nút kết thúc của DAG. Tự động thu thập và hiển thị đầu ra chuẩn của terminal, mã thoát và danh sách tệp được tạo ở mọi bước trước.",
    )
}

private fun WorkflowNodeType.editorLabel() = metadata().label

private fun FailurePolicy.editorLabel() = when (this) {
    FailurePolicy.ABORT -> "Dừng workflow"
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
