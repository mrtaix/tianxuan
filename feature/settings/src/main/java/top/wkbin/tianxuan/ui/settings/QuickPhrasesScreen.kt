package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.core.model.QuickPhrase
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeFilterChip
import top.wkbin.tianxuan.ui.components.RuntimeFloatingActionButton
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeSwitch as Switch
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

@Composable
fun QuickPhrasesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val phrases by viewModel.quickPhrases.collectAsStateWithLifecycle()
    // QuickPhrase 非 Parcelable：仅Lưu id，旋转后从列表按 id 恢复Chỉnh sửa对象
    var editingPhraseId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val editingPhrase = editingPhraseId?.let { id -> phrases.firstOrNull { it.id == id } }
    var isCreating by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showResetDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var selectedFilter by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>("ALL") }
    // 待Xác nhận xóa的短语 id（破坏性操作二次确认）
    var pendingDeletePhraseId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeletePhrase = pendingDeletePhraseId?.let { id -> phrases.firstOrNull { it.id == id } }

    val filterOptions = listOf(
        "ALL" to "Tất cả (${phrases.size})",
        "GENERAL" to "Chung",
        "ANDROID" to "Android",
        "FLUTTER" to "Flutter",
        "REVERSE" to "Phân tích ngược",
    )

    val filteredPhrases = remember(phrases, selectedFilter) {
        when (selectedFilter) {
            "ALL" -> phrases
            "GENERAL" -> phrases.filter { it.targetProjectType == null }
            "ANDROID" -> phrases.filter { it.targetProjectType == "ANDROID" }
            "FLUTTER" -> phrases.filter { it.targetProjectType == "FLUTTER" }
            "REVERSE" -> phrases.filter { it.targetProjectType == "REVERSE" }
            else -> phrases
        }
    }

    if (showResetDialog) {
        RuntimeAlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Đặt lại câu lệnh nhanh mặc định", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn khôi phục câu lệnh nhanh và lệnh về mặc định ban đầu không? Các câu lệnh tùy chỉnh sẽ bị xóa.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetQuickPhrasesToDefault()
                        showResetDialog = false
                    },
                ) {
                    Text("Xác nhận đặt lại", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Hủy")
                }
            },
        )
    }

    if (isCreating || editingPhrase != null) {
        QuickPhraseEditorDialog(
            phrase = editingPhrase,
            onDismiss = {
                isCreating = false
                editingPhraseId = null
            },
            onSave = { id, title, content, description, iconName, targetProjectType, isEnabled ->
                viewModel.saveQuickPhrase(
                    id = id,
                    title = title,
                    content = content,
                    description = description,
                    iconName = iconName,
                    targetProjectType = targetProjectType,
                    isEnabled = isEnabled,
                )
                isCreating = false
                editingPhraseId = null
            },
        )
    }

    pendingDeletePhrase?.let { phrase ->
        RuntimeAlertDialog(
            onDismissRequest = { pendingDeletePhraseId = null },
            title = { Text("Xóa câu lệnh nhanh", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa「${phrase.title}」không? Không thể khôi phục sau khi xóa.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteQuickPhrase(phrase.id)
                        pendingDeletePhraseId = null
                    },
                ) {
                    Text("Xác nhận xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePhraseId = null }) {
                    Text("Hủy")
                }
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "Câu lệnh nhanh và lệnh thường dùng",
                onBack = onBack,
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        contentDescription = "Đặt lại câu lệnh nhanh mặc định",
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Refresh,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = { isCreating = true },
                        contentDescription = "Thêm câu lệnh nhanh",
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Plus,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            RuntimeFloatingActionButton(
                onClick = { isCreating = true },
            ) {
                RuntimeIcon(RuntimeIconName.Plus, Modifier.size(24.dp))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // 分类筛选胶囊
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                filterOptions.forEach { (key, label) ->
                    val isSelected = selectedFilter == key
                    RuntimeFilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = key },
                        label = {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                        },
                    )
                }
            }

            if (filteredPhrases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Chat,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            text = "Không có câu lệnh nhanh phù hợp",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { isCreating = true }) {
                            Text("Thêm câu lệnh nhanh đầu tiên")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(filteredPhrases, key = { it.id }) { phrase ->
                        QuickPhraseCard(
                            phrase = phrase,
                            onToggle = { enabled -> viewModel.toggleQuickPhrase(phrase.id, enabled) },
                            onEdit = { editingPhraseId = phrase.id },
                            onDelete = { pendingDeletePhraseId = phrase.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickPhraseCard(
    phrase: QuickPhrase,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    RuntimeCard(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(14.dp),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 图标胶囊
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(
                        name = parseIconName(phrase.iconName),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = phrase.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // 标签：项目类型
                        val typeLabel = when (phrase.targetProjectType) {
                            "ANDROID" -> "Android"
                            "FLUTTER" -> "Flutter"
                            "REVERSE" -> "Phân tích ngược"
                            else -> "Chung"
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Text(
                                text = typeLabel,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    if (phrase.description.isNotBlank()) {
                        Text(
                            text = phrase.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // 提示词/指令内容预览
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = phrase.content,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            // 底部三等分等宽操作栏 (状态切换 + Chỉnh sửa + Xóa)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 1. 状态切换开关 (1/3 宽，与 MCP/插件/Agent 设置页一致使用 Switch)
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Switch(
                        checked = phrase.isEnabled,
                        onCheckedChange = onToggle,
                    )
                }

                // 2. Chỉnh sửa按钮 (1/3 宽)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onEdit() },
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Chỉnh sửa",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                // 3. Xóa按钮 (1/3 宽)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .clickable { onDelete() },
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Xóa",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickPhraseEditorDialog(
    phrase: QuickPhrase?,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        title: String,
        content: String,
        description: String,
        iconName: String,
        targetProjectType: String?,
        isEnabled: Boolean,
    ) -> Unit,
) {
    var title by remember { mutableStateOf(phrase?.title.orEmpty()) }
    var description by remember { mutableStateOf(phrase?.description.orEmpty()) }
    var content by remember { mutableStateOf(phrase?.content.orEmpty()) }
    var iconName by remember { mutableStateOf(phrase?.iconName ?: "Play") }
    var targetProjectType by remember { mutableStateOf(phrase?.targetProjectType) }
    var isEnabled by remember { mutableStateOf(phrase?.isEnabled ?: true) }
    var projectTypeDropdownExpanded by remember { mutableStateOf(false) }

    val iconOptions = listOf(
        "Play" to RuntimeIconName.Play,
        "Check" to RuntimeIconName.Check,
        "Alert" to RuntimeIconName.Alert,
        "Code" to RuntimeIconName.Code,
        "Plus" to RuntimeIconName.Plus,
        "Package" to RuntimeIconName.Package,
        "Search" to RuntimeIconName.Search,
        "Brain" to RuntimeIconName.Brain,
        "Bot" to RuntimeIconName.Bot,
        "Chat" to RuntimeIconName.Chat,
        "Refresh" to RuntimeIconName.Refresh,
        "Terminal" to RuntimeIconName.Terminal,
        "Tool" to RuntimeIconName.Wrench,
    )

    val projectTypeOptions = listOf(
        null to "Chung (hiển thị trong mọi phiên)",
        "ANDROID" to "Chỉ dành cho dự án Android",
        "FLUTTER" to "Chỉ dành cho dự án Flutter",
        "REVERSE" to "Chỉ dành cho phân tích ngược",
    )

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (phrase == null) "Thêm câu lệnh nhanh" else "Chỉnh sửa câu lệnh nhanh",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Tiêu đề câu lệnh") },
                    placeholder = { Text("Ví dụ: chạy mã / rà soát mã") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả ngắn (tùy chọn)") },
                    placeholder = { Text("Ví dụ: chạy kiểm thử đơn vị của workspace hiện tại") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Mẫu prompt / lệnh") },
                    placeholder = { Text("Nhập lệnh/Prompt cần tự động điền hoặc kích hoạt...") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )

                // 目标项目类型下拉选择
                ExposedDropdownMenuBox(
                    expanded = projectTypeDropdownExpanded,
                    onExpandedChange = { projectTypeDropdownExpanded = !projectTypeDropdownExpanded },
                ) {
                    OutlinedTextField(
                        value = projectTypeOptions.firstOrNull { it.first == targetProjectType }?.second ?: "Chung",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Loại dự án áp dụng") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(projectTypeDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = projectTypeDropdownExpanded,
                        onDismissRequest = { projectTypeDropdownExpanded = false },
                    ) {
                        projectTypeOptions.forEach { (typeKey, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    targetProjectType = typeKey
                                    projectTypeDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                // 图标选择器
                Text("Chọn biểu tượng:", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    iconOptions.forEach { (name, icon) ->
                        val isSelected = iconName == name
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .minimumInteractiveComponentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                                )
                                .clickable { iconName = name },
                            contentAlignment = Alignment.Center,
                        ) {
                            RuntimeIcon(
                                name = icon,
                                modifier = Modifier.size(20.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && content.isNotBlank()) {
                        onSave(
                            phrase?.id,
                            title,
                            content,
                            description,
                            iconName,
                            targetProjectType,
                            isEnabled,
                        )
                    }
                },
                enabled = title.isNotBlank() && content.isNotBlank(),
            ) {
                Text("Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
}

private fun parseIconName(name: String): RuntimeIconName {
    return when (name.lowercase()) {
        "play" -> RuntimeIconName.Play
        "check" -> RuntimeIconName.Check
        "alert" -> RuntimeIconName.Alert
        "code" -> RuntimeIconName.Code
        "plus" -> RuntimeIconName.Plus
        "package" -> RuntimeIconName.Package
        "search" -> RuntimeIconName.Search
        "brain" -> RuntimeIconName.Brain
        "bot" -> RuntimeIconName.Bot
        "chat" -> RuntimeIconName.Chat
        "refresh" -> RuntimeIconName.Refresh
        "terminal" -> RuntimeIconName.Terminal
        "tool", "wrench" -> RuntimeIconName.Wrench
        else -> RuntimeIconName.Play
    }
}
