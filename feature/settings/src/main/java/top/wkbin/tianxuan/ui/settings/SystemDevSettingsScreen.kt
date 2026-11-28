package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.runtime.privilege.PhantomProcessLimitState
import top.wkbin.tianxuan.runtime.privilege.PhantomProcessLimitStatus
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

/**
 * 二级子页 3：系统保活与开发者诊断
 */
@Composable
fun SystemDevSettingsScreen(
    onBack: () -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenAdbLogcat: () -> Unit = {},
    onOpenCustomIteration: () -> Unit = {},
    onOpenPermissionGuide: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val developer by viewModel.developerMode.collectAsStateWithLifecycle()
    val qemuCompatibilityEnabled by viewModel.qemuCompatibilityEnabled.collectAsStateWithLifecycle()
    val qemuCompatibilityReady by viewModel.qemuCompatibilityReady.collectAsStateWithLifecycle()
    val qemuCompatibilityMessage by viewModel.qemuCompatibilityMessage.collectAsStateWithLifecycle()
    val phantomStatus by viewModel.phantomProcessStatus.collectAsStateWithLifecycle()
    val phantomBusy by viewModel.phantomProcessBusy.collectAsStateWithLifecycle()
    val phantomMessage by viewModel.phantomProcessMessage.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showBatteryDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showPhantomProcessDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var batteryExempted by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    val isRestrictiveRom = remember { RomAutostartHelper.isKnownRestrictiveRom() }
    val romLabel = remember { RomAutostartHelper.romLabel() }

    LaunchedEffect(Unit) { viewModel.refreshPhantomProcessLimit() }

    if (showBatteryDialog) {
        BatteryOptimizationDialog(
            exempted = batteryExempted,
            onRefresh = { batteryExempted = isIgnoringBatteryOptimizations(context) },
            onDismiss = { showBatteryDialog = false },
        )
    }
    if (showPhantomProcessDialog) {
        PhantomProcessLimitDialog(
            status = phantomStatus,
            busy = phantomBusy,
            message = phantomMessage,
            adbCommand = viewModel.phantomProcessAdbCommand,
            onRefresh = viewModel::refreshPhantomProcessLimit,
            onRemove = viewModel::removePhantomProcessLimit,
            onDismiss = {
                viewModel.clearPhantomProcessMessage()
                showPhantomProcessDialog = false
            },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Duy trì hoạt động và chẩn đoán", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Duy trì và đánh thức tiến trình",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Shield,
                        title = "Hướng dẫn quyền và chống dừng ứng dụng nền",
                        subtitle = "Hướng dẫn cấu hình riêng của OEM: tự khởi động, pin không hạn chế, khóa trong đa nhiệm…",
                        value = "Xem hướng dẫn",
                        onClick = onOpenPermissionGuide,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Battery,
                        title = "Tối ưu hóa pin và duy trì chạy nền",
                        subtitle = "Miễn hạn chế pin của hệ thống để tránh agent bị đóng băng khi tắt màn hình",
                        value = if (batteryExempted) "Đã miễn" else "Chưa miễn",
                        onClick = { showBatteryDialog = true },
                    )
                    if (isRestrictiveRom) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = RuntimeIconName.Cpu,
                            title = "Mở nhanh cài đặt tự khởi động $romLabel",
                            subtitle = "Mở trực tiếp mục quản lý tự khởi động của nhà sản xuất",
                            value = "Mở để bật",
                            onClick = {
                                runCatching { RomAutostartHelper.openAutostartSettings(context) }.onFailure {
                                    Toast.makeText(context, "Không thể mở cài đặt của nhà sản xuất. Hãy cấu hình thủ công trong mục quản lý ứng dụng của hệ thống.", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Speed,
                        title = "Giới hạn tiến trình con Android 12",
                        subtitle = "Gỡ giới hạn tối đa 32 tiến trình nền Phantom Process",
                        value = when {
                            phantomBusy && phantomStatus == null -> "Đang kiểm tra"
                            phantomStatus?.state == PhantomProcessLimitState.REMOVED -> "Đã gỡ"
                            phantomStatus?.state == PhantomProcessLimitState.ACTIVE -> "Chưa gỡ"
                            phantomStatus?.state == PhantomProcessLimitState.UNSUPPORTED -> "Không cần xử lý"
                            else -> "Chờ kiểm tra"
                        },
                        onClick = {
                            showPhantomProcessDialog = true
                            viewModel.refreshPhantomProcessLimit()
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Gỡ lỗi và nhật ký hệ thống Android",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Terminal,
                        title = "ADB không dây và thu thập nhật ký",
                        subtitle = "Tự tìm ghép nối qua mDNS, tự kết nối lại không cần ghép nối và bảng Logcat thời gian thực",
                        value = "Mở bảng điều khiển",
                        onClick = onOpenAdbLogcat,
                    )
                }
            }

            item {
                Text(
                    text = "Tùy chỉnh và cùng phát triển CTHarness",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Code,
                        title = "Phát triển tùy chỉnh (TianXuanDev)",
                        subtitle = "Dùng AI trong sandbox trên điện thoại để phát triển chính CTHarness và build APK trên đám mây",
                        onClick = onOpenCustomIteration,
                    )
                }
            }

            item {
                Text(
                    text = "Gỡ lỗi và bảng điều khiển cho nhà phát triển",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    ToggleRow(
                        icon = RuntimeIconName.Bug,
                        title = "Chế độ chẩn đoán cho nhà phát triển",
                        subtitle = "Bật giám sát tình trạng hệ thống và bảng điều khiển gỡ lỗi",
                        checked = developer,
                        change = viewModel::setDeveloperMode,
                    )
                    if (developer) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = RuntimeIconName.Terminal,
                            title = "Bảng điều khiển nhà phát triển",
                            subtitle = "Xem tiến trình PRoot và dấu vết lệnh theo thời gian thực",
                            onClick = onOpenDeveloper,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleRow(
                        icon = RuntimeIconName.Cpu,
                        title = "Chế độ tương thích QEMU x86_64",
                        subtitle = if (qemuCompatibilityReady) {
                            "Cho phép phiên yêu cầu rõ ràng sử dụng QEMU x86_64 user-mode; phiên ARM64 không bị ảnh hưởng"
                        } else {
                            "Không tìm thấy môi trường tương thích QEMU x86_64. Hãy cài plugin qemu-x86-64-compat trong trung tâm plugin trước."
                        },
                        checked = qemuCompatibilityEnabled && qemuCompatibilityReady,
                        enabled = qemuCompatibilityReady,
                        change = viewModel::setQemuCompatibilityEnabled,
                    )
                    Text(
                        text = qemuCompatibilityMessage ?: if (qemuCompatibilityEnabled) {
                            "Đã bật. Plugin tương thích chỉ cung cấp ARM64 QEMU user-mode và RootFS x86_64 tối thiểu."
                        } else {
                            "Mặc định tắt, không tải hoặc dùng công cụ x86_64. Khi bật, chỉ áp dụng cho dự án bên thứ ba chọn rõ môi trường tương thích, không thay đổi ABI mặc định arm64-v8a của APK."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (qemuCompatibilityReady) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BatteryOptimizationDialog(
    exempted: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 从系统授权页返回时刷新豁免状态
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefresh() }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Tối ưu hóa pin và chạy nền", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (exempted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        },
                    ) {
                        Text(
                            if (exempted) "Đã miễn tối ưu hóa pin" else "Chưa miễn · Có thể bị đóng băng khi chạy nền",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (exempted) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onErrorContainer
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(
                    "CTHarness khởi chạy dịch vụ tiền cảnh và giữ khóa đánh thức CPU khi agent thực thi, nhưng tối ưu hóa pin của hệ thống vẫn có thể " +
                        "đóng băng tiến trình khi tắt màn hình, khiến suy luận của agent hoặc lệnh đang chạy bị dừng giữa chừng. Nên bật hai mục sau:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }.onFailure {
                            Toast.makeText(context, "Không thể mở cài đặt hệ thống. Hãy vào cài đặt để cấp quyền miễn tối ưu hóa pin theo cách thủ công.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Yêu cầu miễn tối ưu hóa pin")
                }
                OutlinedButton(
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }.onFailure {
                            Toast.makeText(context, "Không thể mở thông tin ứng dụng. Hãy vào cài đặt hệ thống theo cách thủ công.", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Mở thông tin ứng dụng (tự khởi động/chạy nền)")
                }
                Text(
                    "Lưu ý: Trên hệ thống Xiaomi, Huawei, OPPO…, cần cho phép Tự khởi động và Chạy nền thủ công trong thông tin ứng dụng, " +
                        "nếu không chính sách tiết kiệm pin của nhà sản xuất vẫn sẽ dừng tiến trình.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
    )
}

@Composable
private fun PhantomProcessLimitDialog(
    status: PhantomProcessLimitStatus?,
    busy: Boolean,
    message: String?,
    adbCommand: String,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onRefresh() }

    val state = status?.state
    val statusText = when (state) {
        PhantomProcessLimitState.REMOVED -> "Đã gỡ giới hạn"
        PhantomProcessLimitState.ACTIVE -> "Giới hạn vẫn có hiệu lực"
        PhantomProcessLimitState.UNSUPPORTED -> "Hệ thống hiện tại không cần xử lý"
        PhantomProcessLimitState.UNAVAILABLE -> "Tạm thời không thể kiểm tra"
        null -> if (busy) "Đang kiểm tra" else "Chưa kiểm tra"
    }
    val healthy = state == PhantomProcessLimitState.REMOVED || state == PhantomProcessLimitState.UNSUPPORTED
    val statusContainer = if (healthy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val statusContent = if (healthy) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Speed, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Giới hạn tiến trình con Android 12", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(shape = RoundedCornerShape(6.dp), color = statusContainer) {
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = statusContent,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    IconButton(onClick = onRefresh, enabled = !busy) {
                        RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(19.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                Text(
                    status?.details ?: "Đọc cấu hình thực tế của hệ thống để xác nhận giới hạn tiến trình ảo có còn hiệu lực hay không.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state == PhantomProcessLimitState.REMOVED || state == PhantomProcessLimitState.ACTIVE) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Số tiến trình ảo tối đa: ${status.maxPhantomProcesses ?: "Mặc định hệ thống (thường là 32)"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                "Giám sát tiến trình ảo: ${when (status.monitoringEnabled) { true -> "Bật"; false -> "Tắt"; null -> "Mặc định hệ thống (bật)" }}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Text(
                    "Android 12+ giám sát tiến trình con do ứng dụng tạo và có thể dừng PRoot, trình biên dịch hoặc tác vụ agent khi vượt giới hạn hệ thống. Mục này gỡ giới hạn tiến trình con, không phải số luồng Java/Kotlin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Button(
                    onClick = onRemove,
                    enabled = !busy && state != PhantomProcessLimitState.REMOVED && state != PhantomProcessLimitState.UNSUPPORTED,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Đang xử lý")
                    } else {
                        Text("Gỡ bằng Shizuku / Root với một lần nhấn")
                    }
                }

                Text(
                    "Bạn cũng có thể chạy lệnh sau trong terminal máy tính đã kết nối điện thoại:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            adbCommand,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        OutlinedButton(
                            onClick = {
                                copyToClipboard(context, adbCommand, "Lệnh giới hạn tiến trình con Android 12", "Đã sao chép lệnh")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Sao chép lệnh")
                        }
                    }
                }

                if (!message.isNullOrBlank()) {
                    Text(
                        message,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (state == PhantomProcessLimitState.REMOVED) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
