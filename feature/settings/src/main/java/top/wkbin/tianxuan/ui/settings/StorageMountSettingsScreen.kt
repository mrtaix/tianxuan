package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import androidx.compose.material3.minimumInteractiveComponentSize

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.tianxuan.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.SwitchDefaults
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.core.model.StorageMountBinding
import top.wkbin.tianxuan.ui.components.NoticeBanner
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

/**
 * 宿主与沙箱存储挂载管理 (Storage Mounts & Bindings)
 * 基于 PRoot -b 机制，实现 Android 宿主目录与 Linux 容器内的无缝双向访问
 */
@Composable
fun StorageMountSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val downloadEnabled by viewModel.mountDownloadEnabled.collectAsStateWithLifecycle()
    val documentsEnabled by viewModel.mountDocumentsEnabled.collectAsStateWithLifecycle()
    val sharedStorageEnabled by viewModel.mountSharedStorageEnabled.collectAsStateWithLifecycle()
    val customBindings by viewModel.customMountBindings.collectAsStateWithLifecycle()

    var showAddDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // 待Xác nhận xóa的挂载点 id（破坏性操作二次确认）
    var pendingDeleteBindingId by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val pendingDeleteBinding = pendingDeleteBindingId?.let { id -> customBindings.firstOrNull { it.id == id } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "Gắn kết và chia sẻ bộ nhớ",
                statusText = "Ánh xạ bộ nhớ máy chủ PRoot (-b)",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                NoticeBanner(
                    text = "Điểm gắn kết chỉ áp dụng cho các tiến trình trong sandbox Linux (terminal, Agent Trí Phường, tác vụ build và dịch vụ nền), không ảnh hưởng đến trình duyệt tệp — trình duyệt luôn truy cập trực tiếp bộ nhớ máy chủ. Điểm gắn kết được áp dụng khi khởi chạy phiên; thay đổi chỉ có hiệu lực với terminal/tác vụ build tạo mới. Để đọc ghi đầy đủ, hãy cấp quyền “Truy cập tất cả tệp” trong cài đặt hệ thống.",
                )
            }

            // 1. 系统预置快捷挂载
            item {
                Text(
                    text = "Điểm gắn kết nhanh có sẵn của hệ thống",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Column {
                        MountToggleRow(
                            icon = RuntimeIconName.FolderDownload,
                            title = "Thư mục tải xuống (Download)",
                            hostPath = "/storage/emulated/0/Download",
                            guestPath = "/sdcard/Download",
                            checked = downloadEnabled,
                            onCheckedChange = viewModel::setMountDownloadEnabled,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        MountToggleRow(
                            icon = RuntimeIconName.Document,
                            title = "Thư mục tài liệu (Documents)",
                            hostPath = "/storage/emulated/0/Documents",
                            guestPath = "/sdcard/Documents",
                            checked = documentsEnabled,
                            onCheckedChange = viewModel::setMountDocumentsEnabled,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        MountToggleRow(
                            icon = RuntimeIconName.SdCard,
                            title = "Chia sẻ toàn bộ bộ nhớ (/sdcard)",
                            hostPath = "/storage/emulated/0",
                            guestPath = "/sdcard",
                            checked = sharedStorageEnabled,
                            onCheckedChange = viewModel::setMountSharedStorageEnabled,
                        )
                    }
                }
            }

            // 2. Ánh xạ tùy chỉnh
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Ánh xạ tùy chỉnh",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(14.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Thêm điểm gắn kết", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (customBindings.isEmpty()) {
                item {
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        contentPadding = PaddingValues(24.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "Chưa có điểm gắn kết tùy chỉnh",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Nhấn “Thêm điểm gắn kết” ở trên để ánh xạ thư mục Android vào sandbox Linux (chỉ terminal / Trí Phường / build mới thấy)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                items(customBindings, key = { it.id }) { binding ->
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = binding.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "${binding.hostPath} ➔ ${binding.guestPath}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                    ),
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // 历史版本可能存入越界路径：运行时会忽略该绑定，这里明确告知用户原因
                                val bindingError = remember(binding) { StorageMountBinding.validationError(binding) }
                                if (bindingError != null) {
                                    Text(
                                        text = "Điểm gắn kết không hợp lệ: $bindingError (xóa rồi thêm lại)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }

                            Switch(
                                checked = binding.enabled,
                                onCheckedChange = { enabled ->
                                    viewModel.toggleCustomMountBinding(binding.id, enabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )

                            IconButton(
                                onClick = { pendingDeleteBindingId = binding.id },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(32.dp),
                                contentDescription = "Xóa điểm gắn kết ${binding.name}",
                            ) {
                                RuntimeIcon(
                                    name = RuntimeIconName.Trash,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeleteBinding?.let { binding ->
        RuntimeAlertDialog(
            onDismissRequest = { pendingDeleteBindingId = null },
            title = { Text("Xóa điểm gắn kết", fontWeight = FontWeight.Bold) },
            text = {
                Text("Bạn có chắc muốn xóa điểm gắn kết「${binding.name}」（${binding.hostPath} ➔ ${binding.guestPath}）không? Terminal/tác vụ build tạo mới sẽ không còn ánh xạ thư mục này.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeCustomMountBinding(binding.id)
                        pendingDeleteBindingId = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Xác nhận xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteBindingId = null }) { Text("Hủy") }
            },
        )
    }

    if (showAddDialog) {
        AddMountDialog(
            existingGuestPaths = customBindings.map { it.guestPath }.toSet(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name, host, guest ->
                viewModel.addCustomMountBinding(name, host, guest)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun MountToggleRow(
    icon: RuntimeIconName,
    title: String,
    hostPath: String,
    guestPath: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$hostPath ➔ $guestPath",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@Composable
private fun AddMountDialog(
    existingGuestPaths: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, hostPath: String, guestPath: String) -> Unit,
) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var hostPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("/storage/emulated/0/") }
    var guestPath by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("/mnt/") }

    val nameValid = name.isNotBlank()
    val trimmedHost = hostPath.trim()
    val hostCharInvalid = ':' in trimmedHost
    val hostRootAllowed = StorageMountBinding.isHostPathAllowed(trimmedHost)
    val hostPathValid = hostPath.isNotBlank() && !hostCharInvalid && hostRootAllowed

    // 与 SettingsViewModel.addCustomMountBinding 的入参归一保持一致：无前导 / 自动补
    val trimmedGuest = guestPath.trim().let { if (it.startsWith("/")) it else "/$it" }
    val guestDuplicate = trimmedGuest in existingGuestPaths
    val guestNormalized = StorageMountBinding.normalizeGuestPath(trimmedGuest)
    val guestCharInvalid = ':' in trimmedGuest
    val guestRootAllowed = guestNormalized != null && StorageMountBinding.isGuestPathAllowed(trimmedGuest)
    val guestPathValid = guestPath.isNotBlank() && guestNormalized != null &&
        guestRootAllowed && !guestCharInvalid && !guestDuplicate

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm điểm gắn kết bộ nhớ", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên điểm gắn kết") },
                    placeholder = { Text("Ví dụ: ảnh trong album, mã nguồn dự án") },
                    isError = !nameValid,
                    supportingText = { if (!nameValid) Text("Vui lòng nhập tên điểm gắn kết") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = hostPath,
                    onValueChange = { hostPath = it },
                    label = { Text("Đường dẫn máy chủ (Android)") },
                    placeholder = { Text("/storage/emulated/0/...") },
                    isError = hostPath.isNotBlank() && !hostPathValid,
                    supportingText = {
                        when {
                            hostPath.isNotBlank() && hostCharInvalid -> Text("Đường dẫn máy chủ không được chứa dấu hai chấm hoặc ký tự không hợp lệ khác")
                            hostPath.isNotBlank() && !hostRootAllowed ->
                                Text("Đường dẫn máy chủ phải nằm trong /storage/emulated/0")
                            else -> {}
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = guestPath,
                    onValueChange = { guestPath = it },
                    label = { Text("Đường dẫn gắn kết trong container (Linux)") },
                    placeholder = { Text("/mnt/my_folder") },
                    isError = guestPath.isNotBlank() && !guestPathValid,
                    supportingText = {
                        when {
                            guestPath.isNotBlank() && !guestPath.trim().startsWith("/") -> Text("Đường dẫn phải bắt đầu bằng /")
                            guestPath.isNotBlank() && guestNormalized == null -> Text("Đường dẫn container không được chứa ..")
                            guestPath.isNotBlank() && guestCharInvalid -> Text("Đường dẫn container không được chứa dấu hai chấm hoặc ký tự không hợp lệ khác")
                            guestPath.isNotBlank() && !guestRootAllowed ->
                                Text("Đường dẫn container phải nằm trong /mnt hoặc /sdcard")
                            guestDuplicate -> Text("Đường dẫn container này đang được điểm gắn kết khác sử dụng")
                            else -> {}
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, hostPath, guestPath) },
                enabled = nameValid && hostPathValid && guestPathValid,
            ) {
                Text("Thêm điểm gắn kết")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
    )
}
