package top.wkbin.tianxuan.ui.developer

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import androidx.compose.material3.HorizontalDivider
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.tianxuan.ui.components.RuntimeSwitch as Switch
import top.wkbin.tianxuan.ui.developer.LocalizedText as Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.core.model.InstalledRuntime
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.ui.components.EmptyPanel
import top.wkbin.tianxuan.ui.components.IconTile
import top.wkbin.tianxuan.ui.components.InfoRow
import top.wkbin.tianxuan.ui.components.NoticeBanner
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.SectionHeader
import top.wkbin.tianxuan.ui.components.StatusBadge
import top.wkbin.tianxuan.runtime.bridge.adb.EmbeddedAdbManager

@Composable
fun DeveloperScreen(
    onBack: () -> Unit,
    onOpenCatalog: () -> Unit = {},
    viewModel: DeveloperViewModel = koinViewModel(),
) {
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val commandInput by viewModel.commandInput.collectAsStateWithLifecycle()
    val health by viewModel.health.collectAsStateWithLifecycle()
    val commandResult by viewModel.commandResult.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val messageIsError by viewModel.messageIsError.collectAsStateWithLifecycle()
    val registryStatusIsError by viewModel.registryStatusIsError.collectAsStateWithLifecycle()
    val unusedRuntimes by viewModel.unusedRuntimes.collectAsStateWithLifecycle()
    val processes by viewModel.processes.collectAsStateWithLifecycle()
    val rootfsVersion by viewModel.rootfsVersion.collectAsStateWithLifecycle()
    val rootfsUpdate by viewModel.rootfsUpdate.collectAsStateWithLifecycle()
    val savedManifestUrl by viewModel.registryManifestUrl.collectAsStateWithLifecycle()
    val savedSignatureUrl by viewModel.registrySignatureUrl.collectAsStateWithLifecycle()
    val savedPublicKey by viewModel.registryPublicKey.collectAsStateWithLifecycle()
    val registryStatus by viewModel.registryStatus.collectAsStateWithLifecycle()
    val agentLoggingEnabled by viewModel.agentLoggingEnabled.collectAsStateWithLifecycle()
    val agentLogSize by viewModel.agentLogSize.collectAsStateWithLifecycle()
    val agentLogLocation by viewModel.agentLogLocation.collectAsStateWithLifecycle()
    val adbState by viewModel.adbState.collectAsStateWithLifecycle()
    val adbDiscovery by viewModel.adbDiscovery.collectAsStateWithLifecycle()
    val adbBusy by viewModel.adbBusy.collectAsStateWithLifecycle()
    val adbMessage by viewModel.adbMessage.collectAsStateWithLifecycle()
    val logcatOutput by viewModel.logcatOutput.collectAsStateWithLifecycle()
    var manifestUrl by remember(savedManifestUrl) { mutableStateOf(savedManifestUrl) }
    var signatureUrl by remember(savedSignatureUrl) { mutableStateOf(savedSignatureUrl) }
    var publicKey by remember(savedPublicKey) { mutableStateOf(savedPublicKey) }
    var cleanupTarget by remember { mutableStateOf<InstalledRuntime?>(null) }
    var showRootfsUpdateConfirmation by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var showAgentLogDialog by remember { mutableStateOf(false) }
    var agentLogText by remember { mutableStateOf("") }
    var pairingCode by remember { mutableStateOf("") }
    var logcatPackage by remember { mutableStateOf("") }
    var logcatTag by remember { mutableStateOf("") }
    var logcatKeyword by remember { mutableStateOf("") }
    var logcatLines by remember { mutableStateOf("200") }
    var logcatPriority by remember { mutableStateOf('V') }
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(adbState) {
        if (adbState is EmbeddedAdbManager.ConnectionState.Connected) pairingCode = ""
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Bảng điều khiển nhà phát triển", onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            RuntimeCard(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        IconTile(RuntimeIconName.Sparkles)
                        Column {
                            Text("Thư viện tổng quan thành phần Liquid Glass", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "So sánh tiêu chuẩn Apple HIG / Kyant Backdrop và trưng bày toàn bộ thành phần",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(onClick = onOpenCatalog) {
                        Text("Mở phòng trưng bày")
                    }
                }
            }

            RuntimeControlCard(
                state = runtimeState,
                rootfsVersion = rootfsVersion,
                rootfsUpdate = rootfsUpdate,
                busy = busy,
                onInitialize = viewModel::initialize,
                onCancel = viewModel::cancelInitialization,
                onUpdate = { showRootfsUpdateConfirmation = true },
                onCheckUpdate = viewModel::checkRootfsUpdate,
                onHealthCheck = viewModel::runHealthCheck,
                onReset = { showResetConfirmation = true },
            )

            message?.let { NoticeBanner(it, isError = messageIsError) }

            health?.let { result ->
                RuntimeCard(
                    Modifier.fillMaxWidth(),
                    containerColor = if (result.isHealthy) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    else MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconTile(
                            if (result.isHealthy) RuntimeIconName.Shield else RuntimeIconName.Alert,
                            color = if (result.isHealthy) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.error,
                        )
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (result.isHealthy) "Kiểm tra tình trạng đạt" else "Kiểm tra tình trạng phát hiện lỗi", style = MaterialTheme.typography.titleLarge)
                            Text(result.detail ?: "Đã kiểm tra các chức năng cốt lõi của Runtime", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(15.dp))
                    InfoRow("Hệ điều hành", result.osRelease ?: "-")
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Kiến trúc", result.architecture ?: "-")
                    Spacer(Modifier.height(8.dp))
                    InfoRow("Không gian làm việc có thể ghi", if (result.workspaceWritable) "Có" else "Không")
                }
            }

            SectionHeader("ADB không dây · Logcat", "mDNS tự động tìm cổng động; tự kết nối lại sau khi ghép đôi thành công lần đầu")
            RuntimeCard(Modifier.fillMaxWidth()) {
                val connected = adbState as? EmbeddedAdbManager.ConnectionState.Connected
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconTile(RuntimeIconName.Terminal)
                    Column(Modifier.weight(1f)) {
                        Text("ADB không dây tích hợp sẵn", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (val current = adbState) {
                                EmbeddedAdbManager.ConnectionState.Disconnected -> "Chưa kết nối"
                                EmbeddedAdbManager.ConnectionState.Discovering -> "Đang tìm cổng qua mDNS"
                                EmbeddedAdbManager.ConnectionState.Pairing -> "Đang ghép đôi an toàn"
                                EmbeddedAdbManager.ConnectionState.Connecting -> "Đang kết nối"
                                is EmbeddedAdbManager.ConnectionState.Connected -> "${current.host}:${current.port}"
                                is EmbeddedAdbManager.ConnectionState.Failed -> current.message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    StatusBadge(if (connected != null) "Đã kết nối" else "Chưa sẵn sàng", if (connected != null) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Đã tìm thấy: ${adbDiscovery.pairingEndpoints.size} cổng ghép đôi · ${adbDiscovery.connectEndpoints.size} cổng kết nối",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = { value -> pairingCode = value.filter(Char::isDigit).take(6) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Mã ghép đôi dùng một lần gồm 6 chữ số") },
                    supportingText = { Text("Trước tiên, bật “Ghép đôi thiết bị bằng mã ghép đôi” trong mục “Gỡ lỗi không dây” của hệ thống; không cần nhập cổng") },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.pairWirelessAdb(pairingCode) },
                        enabled = !adbBusy && pairingCode.length == 6 && adbDiscovery.pairingEndpoints.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Ghép đôi và kết nối") }
                    OutlinedButton(
                        onClick = viewModel::connectWirelessAdb,
                        enabled = !adbBusy && adbDiscovery.connectEndpoints.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                    ) { Text("Kết nối lại") }
                }
                adbMessage?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(18.dp))
                Text("Thu thập nhật ký ứng dụng", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = logcatPackage,
                    onValueChange = { logcatPackage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên gói (để trống để thu thập tất cả)") },
                    singleLine = true,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = logcatTag,
                        onValueChange = { logcatTag = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Tag (không bắt buộc)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = logcatLines,
                        onValueChange = { logcatLines = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(0.55f),
                        label = { Text("Số dòng") },
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = logcatKeyword,
                    onValueChange = { logcatKeyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Từ khóa (không bắt buộc, không phân biệt chữ hoa/thường)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf('V', 'D', 'I', 'W', 'E', 'F').forEach { priority ->
                        if (priority == logcatPriority) {
                            Button(onClick = { logcatPriority = priority }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) { Text(priority.toString()) }
                        } else {
                            OutlinedButton(onClick = { logcatPriority = priority }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp)) { Text(priority.toString()) }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.captureLogcat(logcatPackage, logcatTag, logcatPriority, logcatKeyword, logcatLines.toIntOrNull() ?: 200) },
                        enabled = connected != null && !adbBusy,
                        modifier = Modifier.weight(1f),
                    ) { Text("Thu thập nhật ký") }
                    OutlinedButton(onClick = viewModel::clearDeviceLogcat, enabled = connected != null && !adbBusy) { Text("Xóa nhật ký") }
                }
                if (logcatOutput.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            logcatOutput,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Logcat", logcatOutput))
                            android.widget.Toast.makeText(context, "Đã sao chép nhật ký", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Sao chép nhật ký") }
                }
            }

            SectionHeader("Nguồn công cụ", "Cập nhật danh mục từ xa: xác minh chữ ký HTTPS + Ed25519 (khóa công khai tùy chỉnh chỉ phát hiện lỗi truyền tải, không phải điểm tin cậy chống giả mạo)")
            RuntimeCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Kiểm tra trước khi lưu: cả hai địa chỉ phải dùng HTTPS và khóa công khai không được để trống
                    val manifestUrlValid = manifestUrl.trim().startsWith("https://")
                    val signatureUrlValid = signatureUrl.trim().startsWith("https://")
                    val publicKeyValid = publicKey.isNotBlank()
                    OutlinedTextField(
                        value = manifestUrl,
                        onValueChange = { manifestUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Địa chỉ HTTPS của danh mục") },
                        isError = manifestUrl.isNotBlank() && !manifestUrlValid,
                        supportingText = {
                            if (manifestUrl.isNotBlank() && !manifestUrlValid) Text("Địa chỉ phải bắt đầu bằng https://")
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = signatureUrl,
                        onValueChange = { signatureUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Địa chỉ HTTPS của chữ ký") },
                        isError = signatureUrl.isNotBlank() && !signatureUrlValid,
                        supportingText = {
                            if (signatureUrl.isNotBlank() && !signatureUrlValid) Text("Địa chỉ phải bắt đầu bằng https://")
                        },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = publicKey,
                        onValueChange = { publicKey = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Khóa công khai Ed25519 (Base64)") },
                        isError = !publicKeyValid,
                        supportingText = {
                            if (!publicKeyValid) Text("Khóa công khai không được để trống")
                        },
                        minLines = 2,
                    )
                    Text(
                        "Lưu ý: người kiểm soát địa chỉ danh mục cũng có thể cung cấp khóa công khai. Khóa tự nhập chỉ giúp phát hiện lỗi truyền tải/kiểm tra tính nhất quán của nội dung, không thể chống giả mạo đầu cuối. Muốn có điểm tin cậy thực sự, hãy tích hợp sẵn khóa công khai cố định trong APK.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.saveRegistryConfig(manifestUrl.trim(), signatureUrl.trim(), publicKey.trim()) },
                            enabled = manifestUrlValid && signatureUrlValid && publicKeyValid,
                        ) { Text("Lưu cấu hình") }
                        OutlinedButton(
                            onClick = viewModel::updateRegistry,
                            enabled = manifestUrlValid && signatureUrlValid && publicKeyValid,
                        ) { Text("Kiểm tra cập nhật") }
                    }
                    registryStatus?.let { NoticeBanner(it, isError = registryStatusIsError) }
                }
            }

            SectionHeader("Quản lý tài nguyên", "Tiến trình Linux chạy nền và các phần phụ thuộc dùng chung")

            RuntimeCard(Modifier.fillMaxWidth()) {
                ResourceHeader(RuntimeIconName.Cpu, "Tiến trình nền", "${processes.size} tiến trình đang hoạt động", viewModel::refreshProcesses)
                Spacer(Modifier.height(14.dp))
                if (processes.isEmpty()) {
                    Text("Hiện không có tiến trình nền nào được đăng ký.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    processes.forEachIndexed { index, process ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(process.toolId ?: process.id, style = MaterialTheme.typography.titleMedium)
                                Text("${process.type} · PID ${process.pid ?: "không rõ"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { viewModel.stopProcess(process.id) }, enabled = !busy) { Text("Dừng") }
                        }
                    }
                }
            }

            RuntimeCard(Modifier.fillMaxWidth()) {
                ResourceHeader(RuntimeIconName.Package, "Runtime có thể dọn dẹp", "Số tham chiếu bằng 0", viewModel::refreshUnusedRuntimes)
                Spacer(Modifier.height(8.dp))
                Text("Chỉ liệt kê các phần phụ thuộc dùng chung không được công cụ nào tham chiếu; thao tác dọn dẹp sẽ chạy trình quản lý gói trong hộp cát Linux.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                if (unusedRuntimes.isEmpty()) {
                    Text("Hiện không có Runtime dùng chung nào có thể dọn dẹp an toàn.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    unusedRuntimes.forEachIndexed { index, runtime ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(runtime.name.name, style = MaterialTheme.typography.titleMedium)
                                Text(runtime.version ?: "Không rõ phiên bản", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { cleanupTarget = runtime }, enabled = !busy) { Text("Dọn dẹp") }
                        }
                    }
                }
            }

            SectionHeader("Chẩn đoán tác tử", "Lưu cục bộ luồng thực thi, lệnh gọi công cụ và chi tiết lỗi của tác tử")
            RuntimeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(RuntimeIconName.Shield)
                    Column(Modifier.weight(1f)) {
                        Text("Nhật ký gỡ lỗi cục bộ của tác tử", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (agentLoggingEnabled) "Đã bật (dung lượng tệp: ${agentLogSize.toReadableSize()})" else "Đã tắt (không ghi vào tệp nhật ký cục bộ)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (agentLogLocation.isNotBlank()) {
                            Text(
                                "Tệp nhật ký: $agentLogLocation",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Switch(
                        checked = agentLoggingEnabled,
                        onCheckedChange = viewModel::setAgentLoggingEnabled,
                    )
                }
                if (agentLoggingEnabled || agentLogSize > 0L) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = {
                                agentLogText = viewModel.readAgentLogs()
                                showAgentLogDialog = true
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Xem nhật ký")
                        }
                        OutlinedButton(
                            onClick = {
                                val logs = viewModel.readAgentLogs()
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TianXuan Agent Logs", logs))
                                android.widget.Toast.makeText(context, "Đã sao chép nhật ký vào khay nhớ tạm", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Sao chép tất cả")
                        }
                        TextButton(
                            onClick = viewModel::clearAgentLogs,
                        ) {
                            Text("Xóa nhật ký", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            SectionHeader("Chẩn đoán lệnh", "Chạy lệnh Shell một lần trong môi trường Linux biệt lập")
            RuntimeCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconTile(RuntimeIconName.Terminal)
                    Column(Modifier.weight(1f)) {
                        Text("Shell Runner", style = MaterialTheme.typography.titleMedium)
                        Text("Phù hợp để chẩn đoán nhanh, không tạo phiên terminal duy trì", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = commandInput,
                    onValueChange = viewModel::onCommandInputChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Lệnh Shell") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = viewModel::runCommand, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    RuntimeIcon(RuntimeIconName.Play, Modifier.size(18.dp)); Spacer(Modifier.size(7.dp)); Text("Chạy lệnh")
                }
                commandResult?.let { result ->
                    Spacer(Modifier.height(14.dp))
                    Column(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color(0xFF080D16)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("exit ${result.exitCode}  •  ${result.durationMs} ms", style = MaterialTheme.typography.labelMedium, color = if (result.exitCode == 0) Color(0xFF83EDC9) else Color(0xFFFFA2AE))
                        Text(result.stdout.ifBlank { "(no stdout)" }, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color(0xFFD9E2F1))
                        if (result.stderr.isNotBlank()) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                            Text(result.stderr, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = Color(0xFFFFA2AE))
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    cleanupTarget?.let { runtime ->
        RuntimeAlertDialog(
            onDismissRequest = { cleanupTarget = null },
            title = { Text("Dọn dẹp ${runtime.name.name}?") },
            text = { Text("Runtime dùng chung không được công cụ tham chiếu sẽ bị gỡ bỏ. Không thể tự động khôi phục thao tác này.") },
            confirmButton = { TextButton(onClick = { cleanupTarget = null; viewModel.cleanupRuntime(runtime.id) }) { Text("Xác nhận dọn dẹp", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { cleanupTarget = null }) { Text("Hủy") } },
        )
    }
    if (showResetConfirmation) {
        var countdown by remember { mutableStateOf(3) }
        LaunchedEffect(Unit) {
            while (countdown > 0) {
                delay(1000)
                countdown--
            }
        }
        RuntimeAlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Đặt lại hộp cát hiện tại?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("• Các gói tự cài đặt và thay đổi hệ thống sẽ bị xóa", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("• Mã dự án trong /workspace sẽ không bị xóa", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showResetConfirmation = false; viewModel.resetLinuxEnvironment() },
                    enabled = countdown == 0,
                ) {
                    Text(
                        if (countdown > 0) "Xác nhận đặt lại (${countdown}s)" else "Xác nhận đặt lại",
                        color = if (countdown == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.error.copy(alpha = 0.4f),
                    )
                }
            },
            dismissButton = { TextButton(onClick = { showResetConfirmation = false }) { Text("Hủy") } },
        )
    }
    if (showRootfsUpdateConfirmation) {
        RuntimeAlertDialog(
            onDismissRequest = { showRootfsUpdateConfirmation = false },
            title = { Text("Cập nhật RootFS Linux?") },
            text = { Text("Phiên bản mới sẽ được tải xuống và xác minh trực tuyến; /root và /opt/tianxuan được giữ lại. Các tiến trình Linux nền sẽ dừng trong khi cập nhật; nếu thất bại, phiên bản cũ sẽ được khôi phục tự động.") },
            confirmButton = { TextButton(onClick = { showRootfsUpdateConfirmation = false; viewModel.updateRootfs() }) { Text("Xác nhận cập nhật") } },
            dismissButton = { TextButton(onClick = { showRootfsUpdateConfirmation = false }) { Text("Hủy") } },
        )
    }
    if (showAgentLogDialog) {
        RuntimeAlertDialog(
            onDismissRequest = { showAgentLogDialog = false },
            title = { Text("Nhật ký gỡ lỗi cục bộ của tác tử") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        agentLogText.ifBlank { "Chưa có nhật ký" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAgentLogDialog = false }) { Text("Đóng") }
            },
            dismissButton = {
                TextButton(onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("TianXuan Agent Logs", agentLogText))
                    android.widget.Toast.makeText(context, "Đã sao chép nhật ký", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Sao chép") }
            },
        )
    }
}

@Composable
private fun RuntimeControlCard(
    state: RuntimeState,
    rootfsVersion: String?,
    rootfsUpdate: top.wkbin.tianxuan.runtime.RootfsUpdateInfo?,
    busy: Boolean,
    onInitialize: () -> Unit,
    onCancel: () -> Unit,
    onUpdate: () -> Unit,
    onCheckUpdate: () -> Unit,
    onHealthCheck: () -> Unit,
    onReset: () -> Unit,
) {
    val color = when (state) {
        RuntimeState.Ready -> MaterialTheme.colorScheme.secondary
        is RuntimeState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    RuntimeCard(Modifier.fillMaxWidth(), containerColor = color.copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(RuntimeIconName.Terminal, color = color)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Linux Runtime", style = MaterialTheme.typography.titleLarge)
                Text("RootFS ${rootfsVersion ?: "Chưa cài đặt"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rootfsUpdate?.let { info ->
                    Text(
                        if (info.hasUpdate) "Có bản cập nhật mới" else "Đã là phiên bản mới nhất",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (info.hasUpdate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            StatusBadge(describeState(state), color)
        }
        val initializing = state as? RuntimeState.Initializing
        if (initializing != null) {
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(progress = { initializing.progress }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(displayStep(initializing.step), style = MaterialTheme.typography.bodySmall)
                Text("${(initializing.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
            }
            initializing.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Hủy tải xuống") }
        } else {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onInitialize, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (state is RuntimeState.Error) "Thử khởi tạo lại" else if (state is RuntimeState.Ready) "Kiểm tra khởi tạo lại" else "Tải xuống và khởi tạo")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onHealthCheck,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text("Kiểm tra tình trạng", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onCheckUpdate,
                    enabled = !busy && state is RuntimeState.Ready,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text("Kiểm tra cập nhật", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                OutlinedButton(
                    onClick = onUpdate,
                    enabled = !busy && state is RuntimeState.Ready,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text("Cập nhật RootFS", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
            Spacer(Modifier.height(9.dp))
            OutlinedButton(
                onClick = onReset,
                enabled = !busy && state !is RuntimeState.Initializing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Khôi phục trạng thái ban đầu của Linux", color = MaterialTheme.colorScheme.error) }
        }
        (state as? RuntimeState.Error)?.let {
            Spacer(Modifier.height(12.dp))
            // Tách thông báo hiển thị cho người dùng khỏi ngoại lệ gốc: ghi ngoại lệ vào nhật ký để tránh hiển thị trực tiếp nội dung kỹ thuật
            androidx.compose.runtime.LaunchedEffect(it.throwable) {
                android.util.Log.w("DeveloperScreen", "Runtime error: ${it.throwable.message}")
            }
            NoticeBanner("Khởi tạo Runtime thất bại. Hãy kiểm tra kết nối mạng và dung lượng lưu trữ, sau đó nhấn “Thử khởi tạo lại”.", isError = true)
        }
    }
}

@Composable
private fun ResourceHeader(icon: RuntimeIconName, title: String, detail: String, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconTile(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onRefresh) { Text("Làm mới") }
    }
}

private fun Long.toReadableSize(): String {
    if (this < 1024L) return "$this B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = this.toDouble()
    var index = -1
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index += 1 }
    return "%.1f %s".format(value, units[index])
}

private fun describeState(state: RuntimeState): String = when (state) {
    RuntimeState.NotInitialized -> "Chưa khởi tạo"
    is RuntimeState.Initializing -> "Đang khởi tạo"
    RuntimeState.Ready -> "Sẵn sàng"
    is RuntimeState.Error -> "Lỗi"
}

private fun displayStep(step: String): String = when (step) {
    "detectArchitecture" -> "Đang phát hiện kiến trúc thiết bị"
    "checkStorage" -> "Kiểm tra dung lượng trống"
    "createDirectories" -> "Tạo thư mục chạy"
    "Chuẩn bị PRoot" -> "Chuẩn bị thành phần khởi chạy PRoot"
    "configureRootfs" -> "Cấu hình hệ thống Linux"
    "configureDns" -> "Cấu hình DNS"
    "configureEnvironment" -> "Cấu hình biến môi trường"
    "createWorkspace" -> "Tạo không gian làm việc"
    "runHealthCheck" -> "Chạy kiểm tra tình trạng"
    else -> step
}
