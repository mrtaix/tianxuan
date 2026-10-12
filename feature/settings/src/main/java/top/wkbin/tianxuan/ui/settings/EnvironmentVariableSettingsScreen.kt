package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeSwitch as Switch
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.SectionHeader

@Composable
fun EnvironmentVariableSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val entries by viewModel.environmentVariables.collectAsStateWithLifecycle()
    val values by viewModel.environmentValues.collectAsStateWithLifecycle()
    val effectiveEntries by viewModel.effectiveEnvironment.collectAsStateWithLifecycle()
    val privacyMode by viewModel.environmentPrivacyMode.collectAsStateWithLifecycle()
    val loading by viewModel.environmentLoading.collectAsStateWithLifecycle()
    val error by viewModel.environmentError.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    // 对象状态仅保存可 Bundle 化的字段，旋转后按 key/entry 恢复
    var showEditor by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var editingKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    var editorInitialKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var editorInitialValue by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var deleteKey by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val editing = editingKey?.let { key -> entries.firstOrNull { it.id == key } }
    val showDelete = deleteKey?.let { key -> entries.firstOrNull { it.id == key } }

    fun openEnvironmentEditor(
        entry: top.wkbin.tianxuan.core.model.EnvironmentVariable?,
        key: String,
        value: String,
    ) {
        viewModel.clearEnvironmentError()
        editingKey = entry?.id
        editorInitialKey = key
        editorInitialValue = value
        showEditor = true
    }

    if (showEditor) {
        EnvironmentVariableEditor(
            entry = editing,
            initialKey = editorInitialKey,
            currentValue = editorInitialValue,
            error = error,
            onDismiss = {
                viewModel.clearEnvironmentError()
                showEditor = false
                editingKey = null
            },
            onSave = { key, value, note ->
                if (editing == null) viewModel.addEnvironmentVariable(key, value, note) { if (it) { showEditor = false } }
                else viewModel.updateEnvironmentVariable(editing.id, key, value, note) { if (it) { showEditor = false; editingKey = null } }
            },
        )
    }
    showDelete?.let { entry ->
        RuntimeAlertDialog(
            onDismissRequest = { deleteKey = null },
            title = { Text("删除环境变量") },
            text = { Text("Chắc chắn xóa ${entry.key}?") },
            confirmButton = { TextButton(onClick = { viewModel.deleteEnvironmentVariable(entry.id); deleteKey = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { deleteKey = null }) { Text("取消") } },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                "环境变量",
                onBack,
                actions = {
                    IconButton(onClick = { viewModel.refreshEnvironmentVariables() }, enabled = !loading && runtimeState is top.wkbin.tianxuan.core.model.RuntimeState.Ready) {
                        RuntimeIcon(RuntimeIconName.Refresh)
                    }
                    IconButton(onClick = { openEnvironmentEditor(null, "", "") }, enabled = !loading && runtimeState is top.wkbin.tianxuan.core.model.RuntimeState.Ready) {
                        RuntimeIcon(RuntimeIconName.Plus)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Shield,
                        title = "Che giấu riêng tư Agent",
                        subtitle = "Chỉ che giá trị biến gửi cho Agent và ghi vào hội thoại; trang này vẫn hiển thị văn bản thuần",
                        trailing = { Switch(checked = privacyMode, onCheckedChange = viewModel::setEnvironmentPrivacyMode) },
                    )
                }
            }
            item {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.72f),
                    borderColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f),
                    contentPadding = PaddingValues(14.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        RuntimeIcon(RuntimeIconName.Alert, tint = MaterialTheme.colorScheme.tertiary)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "Cảnh báo · Sửa đổi biến môi trường có thể gây lỗi chạy",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                            Text(
                                "Ghi đè sai các biến như JAVA_HOME, GRADLE_HOME, LANG có thể khiến terminal, công cụ xây dựng hoặc plugin không khởi động được. Vui lòng chỉ sửa các biến mà bạn hiểu rõ mục đích; các biến quan trọng của runtime Thiên Huyền sẽ được bảo vệ cưỡng chế.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "Biến người dùng được lưu trong /etc/profile.d của Linux thuộc $activeDistroId, và có hiệu lực ở lần chạy lệnh hoặc phiên terminal kế tiếp. Giá trị được lưu với quyền tệp hạn chế.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { message ->
                item {
                    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                SectionHeader(
                    title = "Biến người dùng",
                    subtitle = "Cấu hình người dùng TianXuan có thể chỉnh sửa",
                    trailing = { Text(entries.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            if (loading && entries.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (entries.isEmpty()) {
                item { RuntimeCard(modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Chưa có biến người dùng", style = MaterialTheme.typography.titleMedium); Text("点击右上角 + 添加", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
            } else {
                items(entries, key = { it.id }) { entry ->
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { openEnvironmentEditor(entry, entry.key, values[entry.key].orEmpty()) },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    entry.key,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (entry.note.isNotBlank()) {
                                    Text(
                                        entry.note,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    values[entry.key].orEmpty().ifEmpty { "(Giá trị rỗng)" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(onClick = { openEnvironmentEditor(entry, entry.key, values[entry.key].orEmpty()) }) { RuntimeIcon(RuntimeIconName.Edit) }
                            IconButton(onClick = { deleteKey = entry.id }) { RuntimeIcon(RuntimeIconName.Trash, tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    title = "Môi trường hiệu lực hiện tại",
                    subtitle = "Biến và giá trị thực tế mà lệnh mới nhìn thấy",
                    trailing = { Text(effectiveEntries.size.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            if (loading && effectiveEntries.isEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            } else if (effectiveEntries.isEmpty()) {
                item {
                    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
                        Text("Chưa có môi trường runtime nào có thể đọc", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    SettingsGroup {
                        effectiveEntries.forEachIndexed { index, entry ->
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            val managedEntry = entries.firstOrNull { it.key == entry.key }
                            SettingsRow(
                                icon = RuntimeIconName.Key,
                                title = entry.key,
                                subtitle = entry.value.ifEmpty { "(Giá trị rỗng)" },
                                onClick = {
                                    if (managedEntry == null) {
                                        openEnvironmentEditor(null, entry.key, entry.value)
                                    } else {
                                        openEnvironmentEditor(managedEntry, managedEntry.key, values[managedEntry.key].orEmpty())
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnvironmentVariableEditor(
    entry: top.wkbin.tianxuan.core.model.EnvironmentVariable?,
    initialKey: String,
    currentValue: String,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var key by remember(entry?.id, initialKey) { mutableStateOf(initialKey) }
    var value by remember(entry?.id, initialKey) { mutableStateOf(currentValue) }
    var note by remember(entry) { mutableStateOf(entry?.note.orEmpty()) }
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (entry != null) "编辑环境变量" else if (initialKey.isNotBlank()) "配置环境变量" else "添加环境变量") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    RuntimeIcon(RuntimeIconName.Alert, Modifier.size(18.dp), MaterialTheme.colorScheme.tertiary)
                    Text(
                        if (entry == null && initialKey.isNotBlank()) {
                            "Điều này sẽ ghi đè giá trị Linux hiện tại trong cấu hình người dùng. Cấu hình sai có thể khiến các lệnh liên quan không chạy được."
                        } else {
                            "Sau khi sửa sẽ ảnh hưởng tới lệnh và terminal mới khởi động, hãy xác nhận tên biến và giá trị đúng."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                OutlinedTextField(value = key, onValueChange = { key = it.uppercase() }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("值") }, singleLine = true)
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注（可选）") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(key, value, note) }, enabled = key.isNotBlank() && (entry != null || value.isNotEmpty())) { Text("保存") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
