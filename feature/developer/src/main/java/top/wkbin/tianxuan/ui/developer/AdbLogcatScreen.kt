package top.wkbin.tianxuan.ui.developer

import org.koin.compose.viewmodel.koinViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.runtime.bridge.adb.EmbeddedAdbManager
import top.wkbin.tianxuan.ui.components.IconTile
import top.wkbin.tianxuan.ui.components.NoticeBanner
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeFilledTonalButton as FilledTonalButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.SectionHeader
import top.wkbin.tianxuan.ui.components.StatusBadge
import top.wkbin.tianxuan.ui.developer.LocalizedText as Text

/**
 * Trang tiện ích riêng để kết nối ADB không dây và thu thập nhật ký.
 *
 * Cung cấp:
 * 1. Hiển thị các điểm cuối tự động phát hiện qua mDNS và trạng thái tự kết nối lại;
 * 2. Ghép đôi an toàn chỉ với một thao tác qua TLS + SPAKE2 (chỉ cần nhập mã ghép đôi một lần trong phần gỡ lỗi không dây của hệ thống);
 * 3. Lọc Logcat theo nhiều tiêu chí (tên gói/Tag/mức ưu tiên/từ khóa/số dòng) và xem trước nhật ký trực tiếp;
 * 4. Sao chép nhật ký và xóa bộ đệm trên thiết bị.
 */
@Composable
fun AdbLogcatScreen(
    onBack: () -> Unit,
    viewModel: DeveloperViewModel = koinViewModel(),
) {
    val adbState by viewModel.adbState.collectAsStateWithLifecycle()
    val adbDiscovery by viewModel.adbDiscovery.collectAsStateWithLifecycle()
    val adbBusy by viewModel.adbBusy.collectAsStateWithLifecycle()
    val adbMessage by viewModel.adbMessage.collectAsStateWithLifecycle()
    val logcatOutput by viewModel.logcatOutput.collectAsStateWithLifecycle()
    val adbNotificationEnabled by viewModel.adbNotificationEnabled.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var pairingCode by rememberSaveable { mutableStateOf("") }
    var explicitPort by rememberSaveable { mutableStateOf("") }
    var logcatPackage by rememberSaveable { mutableStateOf(context.packageName) }
    var logcatTag by rememberSaveable { mutableStateOf("") }
    var logcatKeyword by rememberSaveable { mutableStateOf("") }
    var logcatLines by rememberSaveable { mutableStateOf("200") }
    var logcatPriority by rememberSaveable { mutableStateOf('V') }

    val connected = adbState as? EmbeddedAdbManager.ConnectionState.Connected

    LaunchedEffect(adbState) {
        if (adbState is EmbeddedAdbManager.ConnectionState.Connected) {
            pairingCode = ""
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("ADB không dây và thu thập nhật ký", onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 1. Trạng thái kết nối và tổng quan ──────────────────────────────────────────
            RuntimeCard(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconTile(
                        icon = RuntimeIconName.Terminal,
                        color = if (connected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Trình khách ADB không dây tích hợp sẵn", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when (val current = adbState) {
                                EmbeddedAdbManager.ConnectionState.Disconnected -> "Chưa kết nối"
                                EmbeddedAdbManager.ConnectionState.Discovering -> "Đang tự động tìm cổng gỡ lỗi qua mDNS…"
                                EmbeddedAdbManager.ConnectionState.Pairing -> "Đang ghép đôi an toàn…"
                                EmbeddedAdbManager.ConnectionState.Connecting -> "Đang thiết lập kết nối ADB…"
                                is EmbeddedAdbManager.ConnectionState.Connected -> "Đã kết nối: ${current.host}:${current.port}"
                                is EmbeddedAdbManager.ConnectionState.Failed -> current.message
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (adbState is EmbeddedAdbManager.ConnectionState.Failed) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    StatusBadge(
                        text = when (adbState) {
                            is EmbeddedAdbManager.ConnectionState.Connected -> "Đã kết nối"
                            EmbeddedAdbManager.ConnectionState.Discovering -> "Đang dò tìm"
                            EmbeddedAdbManager.ConnectionState.Pairing -> "Đang ghép đôi"
                            EmbeddedAdbManager.ConnectionState.Connecting -> "Đang kết nối"
                            is EmbeddedAdbManager.ConnectionState.Failed -> "Lỗi"
                            EmbeddedAdbManager.ConnectionState.Disconnected -> "Chưa sẵn sàng"
                        },
                        color = when (adbState) {
                            is EmbeddedAdbManager.ConnectionState.Connected -> MaterialTheme.colorScheme.secondary
                            is EmbeddedAdbManager.ConnectionState.Failed -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.primary
                        },
                    )
                }

                if (adbBusy || adbState == EmbeddedAdbManager.ConnectionState.Discovering || adbState == EmbeddedAdbManager.ConnectionState.Connecting) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Dò tìm mDNS tự động: ${adbDiscovery.pairingEndpoints.size} điểm cuối ghép đôi · ${adbDiscovery.connectEndpoints.size} điểm cuối kết nối",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Trợ lý ghép đôi trên thanh thông báo",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                        )
                        Text(
                            "Hiển thị cố định ô nhập mã ghép đôi trong thanh thông báo hệ thống để nhập nhanh mà không cần chuyển màn hình",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    androidx.compose.material3.Switch(
                        checked = adbNotificationEnabled,
                        onCheckedChange = viewModel::setAdbNotificationEnabled,
                    )
                }
            }

            // ── 2. Bảng điều khiển ghép đôi và kết nối lại ──────────────────────────────────────────
            SectionHeader("Ghép đôi và kết nối an toàn", "Khóa được lưu bền vững trong thư mục riêng của ứng dụng; sau khi ghép đôi một lần, không cần tìm cổng lại")
            RuntimeCard(Modifier.fillMaxWidth()) {
                NoticeBanner(
                    text = "💡 Khuyên dùng ghép đôi nhanh từ thanh thông báo: Khi hộp thoại “Ghép đôi thiết bị bằng mã ghép đôi” của Android đang mở, rời khỏi Cài đặt sẽ đóng hộp thoại và làm mã hết hiệu lực. Bật “Trợ lý ghép đôi trên thanh thông báo” ở trên để kéo thanh thông báo xuống ngay trong hộp thoại Tùy chọn nhà phát triển, nhập mã ghép đôi 6 chữ số rồi gửi mà không cần chuyển màn hình!",
                    isError = false,
                )
                Spacer(Modifier.height(10.dp))

                OutlinedButton(
                    onClick = {
                        runCatching {
                            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        }.onFailure {
                            Toast.makeText(context, "Không thể mở trực tiếp Tùy chọn nhà phát triển. Hãy bật thủ công trong Cài đặt hệ thống.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Mở Tùy chọn nhà phát triển hệ thống (bật gỡ lỗi không dây)")
                }

                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { value -> pairingCode = value.filter(Char::isDigit).take(6) },
                        modifier = Modifier.weight(1.1f),
                        label = { Text("Mã ghép đôi gồm 6 chữ số") },
                        placeholder = { Text("Ví dụ: 123456") },
                        supportingText = { Text("Nhập khi ghép đôi lần đầu") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = explicitPort,
                        onValueChange = { value -> explicitPort = value.filter(Char::isDigit).take(5) },
                        modifier = Modifier.weight(0.9f),
                        label = { Text("Cổng kết nối (không bắt buộc)") },
                        placeholder = { Text("Ví dụ: 41235") },
                        supportingText = { Text("Để trống để mDNS dò tìm") },
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.pairWirelessAdb(pairingCode, explicitPort.toIntOrNull()) },
                        enabled = !adbBusy && pairingCode.length == 6 && (adbDiscovery.pairingEndpoints.isNotEmpty() || explicitPort.isNotBlank()),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Hoàn tất ghép đôi và kết nối")
                    }
                    OutlinedButton(
                        onClick = { viewModel.connectWirelessAdb(explicitPort.toIntOrNull()) },
                        enabled = !adbBusy && (adbDiscovery.connectEndpoints.isNotEmpty() || explicitPort.isNotBlank() || adbState is EmbeddedAdbManager.ConnectionState.Connected),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (explicitPort.isNotBlank()) "Kết nối qua cổng đã chỉ định" else "Kết nối lại")
                    }
                }

                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    FilledTonalButton(
                        onClick = viewModel::restartAdbDiscovery,
                        enabled = !adbBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text("Dò tìm cổng lại", style = MaterialTheme.typography.labelSmall)
                    }
                }

                adbMessage?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ── 3. Bàn làm việc thu thập nhật ký ───────────────────────────────────────────
            SectionHeader("Thu thập nhật ký Logcat", "Chỉ định tên gói ứng dụng, Tag và điều kiện lọc để thu thập, phân tích nhật ký chạy ngay tại đây")
            RuntimeCard(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = logcatPackage,
                    onValueChange = { logcatPackage = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tên gói ứng dụng đích (để trống để thu thập nhật ký toàn hệ thống)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalButton(
                        onClick = { logcatPackage = context.packageName },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Điền TianXuan", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { logcatPackage = "" },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text("Nhật ký toàn hệ thống", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = logcatTag,
                        onValueChange = { logcatTag = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Lọc theo Tag (không bắt buộc)") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = logcatLines,
                        onValueChange = { logcatLines = it.filter(Char::isDigit).take(4) },
                        modifier = Modifier.weight(0.6f),
                        label = { Text("Số dòng") },
                        singleLine = true,
                    )
                }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = logcatKeyword,
                    onValueChange = { logcatKeyword = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Lọc theo từ khóa (không bắt buộc, không phân biệt chữ hoa/thường)") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))

                Text("Mức nhật ký", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf('V' to "Verbose", 'D' to "Debug", 'I' to "Info", 'W' to "Warn", 'E' to "Error", 'F' to "Fatal").forEach { (priority, _) ->
                        if (priority == logcatPriority) {
                            Button(
                                onClick = { logcatPriority = priority },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                            ) {
                                Text(priority.toString())
                            }
                        } else {
                            OutlinedButton(
                                onClick = { logcatPriority = priority },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
                            ) {
                                Text(priority.toString())
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            viewModel.captureLogcat(
                                logcatPackage,
                                logcatTag,
                                logcatPriority,
                                logcatKeyword,
                                logcatLines.toIntOrNull() ?: 200,
                            )
                        },
                        enabled = connected != null && !adbBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Thu thập nhật ký")
                    }
                    OutlinedButton(
                        onClick = viewModel::clearDeviceLogcat,
                        enabled = connected != null && !adbBusy,
                    ) {
                        Text("Xóa bộ đệm")
                    }
                }

                if (logcatOutput.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text("Kết quả thu thập", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(
                                    logcatOutput,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Logcat", logcatOutput))
                            Toast.makeText(context, "Đã sao chép nhật ký vào khay nhớ tạm", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Sao chép toàn bộ nhật ký")
                    }
                }
            }

            // ── 4. Hướng dẫn phối hợp giữa CLI trong hộp cát và Agent ──────────────────────────────
            SectionHeader("Kết hợp terminal trong hộp cát với Agent", "Gọi trực tiếp từ dòng lệnh Linux hoặc cuộc trò chuyện AI")
            RuntimeCard(Modifier.fillMaxWidth()) {
                Text(
                    "• Lệnh terminal: đã có sẵn logcat-grabber <package name>, logcat-tail <package name> và logcat-export <package name> <path>\n" +
                    "• Giao diện máy chủ: gọi trực tiếp xuyên hộp cát bằng tianxuan-host logcat\n" +
                    "• Phối hợp AI: yêu cầu Agent “thu thập nhật ký sự cố giúp tôi” trong cuộc trò chuyện Zhishu; Agent sẽ tự gọi công cụ host.logcat để phân tích và xử lý",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
