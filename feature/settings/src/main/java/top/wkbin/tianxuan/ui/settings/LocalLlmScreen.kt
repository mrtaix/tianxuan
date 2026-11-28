package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.runtime.LocalGgufModel
import top.wkbin.tianxuan.runtime.LocalLlmServiceState
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeTextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

@Composable
fun LocalLlmScreen(
    onBack: () -> Unit,
    onOpenEngine: () -> Unit,
    viewModel: LocalLlmViewModel = koinViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val serviceState by viewModel.serviceState.collectAsStateWithLifecycle()
    val transfer by viewModel.transfer.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val engineInstalled by viewModel.engineInstalled.collectAsStateWithLifecycle()
    val deviceRamBytes = viewModel.deviceRamBytes
    val mobileModelPresets = viewModel.mobileModelPresets
    val topBarStatus = when (val state = serviceState) {
        LocalLlmServiceState.Stopped -> "Dịch vụ chưa khởi động"
        is LocalLlmServiceState.Starting -> "Đang khởi động · ${state.fileName}"
        is LocalLlmServiceState.Running -> "Đang chạy · ${state.fileName}"
        is LocalLlmServiceState.Failed -> "Khởi động thất bại"
    }

    var showDownloadDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showPathDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // Tên tệp mô hình chờ xác nhận xóa; thao tác phá hủy cần xác nhận lại.
    var pendingDeleteModel by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importUri)
    }

    message?.let { currentMessage ->
        RuntimeAlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            title = { Text(if (messageIsError) "Thao tác chưa hoàn tất" else "LLM cục bộ") },
            text = { Text(currentMessage) },
            confirmButton = {
                RuntimeTextButton(onClick = viewModel::consumeMessage) { Text("Đã hiểu") }
            },
        )
    }

    if (showDownloadDialog) {
        DownloadModelDialog(
            onDismiss = { showDownloadDialog = false },
            onDownload = { url, checksum ->
                showDownloadDialog = false
                viewModel.download(url, checksum)
            },
        )
    }

    if (showPathDialog) {
        ImportPathDialog(
            onDismiss = { showPathDialog = false },
            onImport = { path ->
                showPathDialog = false
                viewModel.importPath(path)
            },
        )
    }

    pendingDeleteModel?.let { fileName ->
        DeleteModelConfirmDialog(
            fileName = fileName,
            onConfirm = {
                pendingDeleteModel = null
                viewModel.delete(fileName)
            },
            onDismiss = { pendingDeleteModel = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "LLM cục bộ",
                onBack = onBack,
                statusText = topBarStatus,
                actions = {
                    if (serviceState is LocalLlmServiceState.Running) {
                        RuntimeOutlinedButton(
                            onClick = viewModel::stop,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Stop, Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text("Dừng")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RuntimeOutlinedButton(
                        onClick = { showDownloadDialog = true },
                        enabled = !transfer.running,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Download, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Tải từ mạng", maxLines = 1)
                    }
                    RuntimeOutlinedButton(
                        onClick = { showPathDialog = true },
                        enabled = !transfer.running,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.Folder, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Nhập từ đường dẫn", maxLines = 1)
                    }
                    RuntimeOutlinedButton(
                        onClick = { filePicker.launch(arrayOf("application/octet-stream", "application/*", "*/*")) },
                        enabled = !transfer.running,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 11.dp),
                    ) {
                        RuntimeIcon(RuntimeIconName.File, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Chọn tệp", maxLines = 1)
                    }
                }
            }

            if (transfer.running) {
                item {
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    transfer.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    transfer.totalBytes?.let { "${formatBytes(transfer.copiedBytes)} / ${formatBytes(it)}" }
                                        ?: formatBytes(transfer.copiedBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            RuntimeLinearProgressIndicator(
                                progress = transfer.fraction?.let { fraction -> { fraction } },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            RuntimeTextButton(onClick = viewModel::cancelTransfer, modifier = Modifier.align(Alignment.End)) {
                                Text("Hủy")
                            }
                        }
                    }
                }
            }

            item {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Info, Modifier.size(23.dp), MaterialTheme.colorScheme.primary)
                            Text("Suy luận bằng LLM cục bộ", style = MaterialTheme.typography.titleMedium)
                        }
                        Text(
                            "Chạy mô hình GGUF bằng llama.cpp trong sandbox Linux; dữ liệu suy luận không rời khỏi thiết bị. Hỗ trợ tải tiếp từ liên kết trực tiếp HTTPS / Hugging Face hoặc nhập qua trình chọn tệp của điện thoại.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (!engineInstalled) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Cần cài bộ máy suy luận llama.cpp trước", fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        RuntimeButton(onClick = viewModel::installEngine) { Text("Cài ngay") }
                                        RuntimeOutlinedButton(onClick = onOpenEngine) { Text("Xem chi tiết") }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mô hình nhỏ đề xuất cho điện thoại", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "RAM thiết bị ${formatBytes(deviceRamBytes)} · Đề xuất Q4_K_M để cân bằng dung lượng, tốc độ và chất lượng",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(mobileModelPresets, key = MobileModelPreset::id) { preset ->
                val suitable = viewModel.isDeviceSuitable(preset)
                val alreadyDownloaded = models.any { model ->
                    preset.url.substringBefore('?').substringAfterLast('/').equals(model.fileName, ignoreCase = true)
                }
                MobileModelPresetCard(
                    preset = preset,
                    suitable = suitable,
                    alreadyDownloaded = alreadyDownloaded,
                    transferRunning = transfer.running,
                    download = { viewModel.downloadPreset(preset) },
                )
            }

            item {
                ServiceStatusCard(serviceState = serviceState, stop = viewModel::stop)
            }

            item {
                Text("Mô hình đã nhập", style = MaterialTheme.typography.titleMedium)
            }

            if (models.isEmpty()) {
                item {
                    RuntimeCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        borderColor = Color.Transparent,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Model, Modifier.size(44.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                            Text("Chưa có mô hình", style = MaterialTheme.typography.titleSmall)
                            Text("Dùng các nút phía trên để tải hoặc nhập mô hình GGUF", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(models, key = LocalGgufModel::fileName) { model ->
                    ModelCard(
                        model = model,
                        serviceState = serviceState,
                        engineInstalled = engineInstalled,
                        start = { viewModel.start(model.fileName) },
                        stop = viewModel::stop,
                        delete = { pendingDeleteModel = model.fileName },
                    )
                }
            }
        }
    }
}

@Composable
private fun MobileModelPresetCard(
    preset: MobileModelPreset,
    suitable: Boolean,
    alreadyDownloaded: Boolean,
    transferRunning: Boolean,
    download: () -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (suitable) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RuntimeIcon(RuntimeIconName.Cpu, Modifier.size(22.dp), MaterialTheme.colorScheme.primary)
                Text(
                    preset.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (suitable) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    },
                ) {
                    Text(
                        if (suitable) "Phù hợp thiết bị" else "Không đủ RAM",
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (suitable) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onErrorContainer
                        },
                    )
                }
            }
            Text(preset.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ModelSpecChip(preset.parameterCount)
                        ModelSpecChip(preset.quantization)
                        ModelSpecChip(formatBytes(preset.downloadBytes))
                    }
                    Text(
                        "Khuyến nghị thiết bị có ít nhất ${formatRam(preset.minimumDeviceRamBytes)} RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RuntimeButton(
                    onClick = download,
                    enabled = suitable && !alreadyDownloaded && !transferRunning,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    RuntimeIcon(
                        if (alreadyDownloaded) RuntimeIconName.Check else RuntimeIconName.Download,
                        Modifier.size(16.dp),
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(if (alreadyDownloaded) "Đã tải" else "Tải xuống")
                }
            }
        }
    }
}

@Composable
private fun ModelSpecChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServiceStatusCard(
    serviceState: LocalLlmServiceState,
    stop: () -> Unit,
) {
    val running = serviceState as? LocalLlmServiceState.Running
    val color = when (serviceState) {
        LocalLlmServiceState.Stopped -> MaterialTheme.colorScheme.onSurfaceVariant
        is LocalLlmServiceState.Starting -> MaterialTheme.colorScheme.tertiary
        is LocalLlmServiceState.Running -> successStatusColor()
        is LocalLlmServiceState.Failed -> MaterialTheme.colorScheme.error
    }
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = color.copy(alpha = 0.25f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(modifier = Modifier.size(10.dp), shape = RoundedCornerShape(50), color = color) {}
                Text(
                    when (serviceState) {
                        LocalLlmServiceState.Stopped -> "Chưa chạy"
                        is LocalLlmServiceState.Starting -> "Đang khởi động · ${serviceState.fileName}"
                        is LocalLlmServiceState.Running -> "Đang chạy · ${serviceState.fileName}"
                        is LocalLlmServiceState.Failed -> "Khởi động thất bại"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.weight(1f))
                if (running != null) {
                    RuntimeOutlinedButton(onClick = stop, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)) {
                        RuntimeIcon(RuntimeIconName.Stop, Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text("Dừng")
                    }
                }
            }
            Text(
                running?.endpoint ?: when (serviceState) {
                    is LocalLlmServiceState.Failed -> serviceState.message
                    else -> "Chọn mô hình bên dưới để khởi động dịch vụ. Sau khi khởi động thành công, mô hình sẽ được chọn cho hội thoại hiện tại."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: LocalGgufModel,
    serviceState: LocalLlmServiceState,
    engineInstalled: Boolean,
    start: () -> Unit,
    stop: () -> Unit,
    delete: () -> Unit,
) {
    val isRunning = (serviceState as? LocalLlmServiceState.Running)?.fileName == model.fileName
    val isStarting = (serviceState as? LocalLlmServiceState.Starting)?.fileName == model.fileName
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (isRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RuntimeIcon(RuntimeIconName.Model, Modifier.size(28.dp), MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    model.fileName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatBytes(model.sizeBytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isRunning) {
                RuntimeOutlinedButton(onClick = stop, contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp)) {
                    RuntimeIcon(RuntimeIconName.Stop, Modifier.size(17.dp))
                }
            } else {
                RuntimeButton(
                    onClick = start,
                    enabled = engineInstalled && (
                        serviceState is LocalLlmServiceState.Stopped ||
                            serviceState is LocalLlmServiceState.Failed
                        ),
                    contentPadding = PaddingValues(horizontal = 11.dp, vertical = 8.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Play, Modifier.size(17.dp))
                    if (isStarting) {
                        Spacer(Modifier.size(4.dp))
                        Text("Đang khởi động")
                    }
                }
            }
            RuntimeIconButton(onClick = delete, enabled = !isRunning && !isStarting) {
                RuntimeIcon(RuntimeIconName.Trash, Modifier.size(19.dp), MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DownloadModelDialog(
    onDismiss: () -> Unit,
    onDownload: (String, String?) -> Unit,
) {
    var url by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var sha256 by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    // Kiểm tra URL: liên kết trực tiếp http(s) tới tệp .gguf; chấp nhận tham số như ?download=true.
    val urlValid = (url.startsWith("http://") || url.startsWith("https://")) &&
        url.substringBefore('?').lowercase().endsWith(".gguf")
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tải GGUF từ mạng") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Dán liên kết HTTPS trực tiếp kết thúc bằng .gguf. Trên trang tệp Hugging Face, hãy sao chép liên kết Download.")
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Địa chỉ tải GGUF") },
                    placeholder = { Text("https://.../model.gguf") },
                    isError = url.isNotBlank() && !urlValid,
                    supportingText = {
                        if (url.isNotBlank() && !urlValid) {
                            Text("Nhập liên kết trực tiếp bắt đầu bằng http(s):// và trỏ tới tệp .gguf")
                        }
                    },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sha256,
                    onValueChange = { sha256 = it },
                    label = { Text("SHA-256 (không bắt buộc)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            RuntimeButton(onClick = { onDownload(url, sha256.ifBlank { null }) }, enabled = urlValid) { Text("Bắt đầu tải") }
        },
        dismissButton = { RuntimeTextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

@Composable
private fun ImportPathDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var path by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val pathValid = path.substringBefore('?').lowercase().endsWith(".gguf")
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nhập từ đường dẫn") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Nhập đường dẫn tuyệt đối mà ứng dụng có thể đọc, ví dụ /storage/emulated/0/Download/model.gguf. Nếu bị giới hạn bởi quyền lưu trữ Android, hãy dùng nút Chọn tệp.")
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Đường dẫn tệp GGUF") },
                    isError = path.isNotBlank() && !pathValid,
                    supportingText = {
                        if (path.isNotBlank() && !pathValid) {
                            Text("Đường dẫn phải trỏ tới tệp mô hình .gguf")
                        }
                    },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            RuntimeButton(onClick = { onImport(path) }, enabled = pathValid) { Text("Nhập") }
        },
        dismissButton = { RuntimeTextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

/** Hộp thoại xác nhận xóa GGUF, dùng khóa đếm ngược giống DistroManagementScreen. */
@Composable
private fun DeleteModelConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countdown by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(3) }

    LaunchedEffect(fileName) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000)
            countdown--
        }
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Xóa mô hình $fileName?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "• Tệp mô hình GGUF sẽ bị xóa khỏi sandbox để giải phóng dung lượng tương ứng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "• Sau khi xóa, cần tải hoặc nhập lại mô hình để sử dụng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = {
            RuntimeButton(
                onClick = onConfirm,
                enabled = countdown == 0,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = if (countdown > 0) "Xác nhận xóa ($countdown giây)" else "Xác nhận xóa",
                    color = if (countdown == 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onError.copy(alpha = 0.6f),
                )
            }
        },
        dismissButton = { RuntimeTextButton(onClick = onDismiss) { Text("Hủy") } },
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatRam(bytes: Long): String = "${kotlin.math.ceil(bytes / (1024.0 * 1024.0 * 1024.0)).toInt()} GB"
