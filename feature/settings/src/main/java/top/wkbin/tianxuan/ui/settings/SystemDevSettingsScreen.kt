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
        topBar = { RuntimeTopBar("保活与诊断", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "进程保活与唤醒",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Shield,
                        title = "Hướng dẫn chống diệt nền và quyền của nhà sản xuất",
                        subtitle = "Hướng dẫn cấu hình riêng của OEM như tự khởi động, không giới hạn pin, khóa đa nhiệm",
                        value = "Xem hướng dẫn",
                        onClick = onOpenPermissionGuide,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Battery,
                        title = "电池优化与后台保活",
                        subtitle = "豁免系统电池限制，防止 Agent 息屏被冻结",
                        value = if (batteryExempted) "已豁免" else "未豁免",
                        onClick = { showBatteryDialog = true },
                    )
                    if (isRestrictiveRom) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = RuntimeIconName.Cpu,
                            title = "Chuyển nhanh tự khởi động $romLabel",
                            subtitle = "Truy cập trực tiếp một chạm tới mục cài đặt quản lý tự khởi động của hệ thống hãng",
                            value = "Đi tới để bật",
                            onClick = {
                                runCatching { RomAutostartHelper.openAutostartSettings(context) }.onFailure {
                                    Toast.makeText(context, "Không thể nhảy tới cài đặt nhà sản xuất, hãy cấu hình thủ công trong quản lý ứng dụng của cài đặt hệ thống", Toast.LENGTH_LONG).show()
                                }
                            },
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Speed,
                        title = "Android 12 子进程限制",
                        subtitle = "解除 Phantom Process 最多 32 个的后台限制",
                        value = when {
                            phantomBusy && phantomStatus == null -> "检测中"
                            phantomStatus?.state == PhantomProcessLimitState.REMOVED -> "已解除"
                            phantomStatus?.state == PhantomProcessLimitState.ACTIVE -> "未解除"
                            phantomStatus?.state == PhantomProcessLimitState.UNSUPPORTED -> "无需处理"
                            else -> "待检测"
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
                    text = "Gỡ lỗi và log hệ thống Android",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Terminal,
                        title = "ADB không dây và bắt nhật ký",
                        subtitle = "Tự động ghép đôi khám phá mDNS, tự động kết nối lại miễn ghép đôi và bàn làm việc Logcat thời gian thực",
                        value = "Vào bàn làm việc",
                        onClick = onOpenAdbLogcat,
                    )
                }
            }

            item {
                Text(
                    text = "Tùy biến lặp và cùng xây dựng Thiên Huyền",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Code,
                        title = "Lặp tùy chỉnh (TianXuanDev)",
                        subtitle = "Gọi AI phát triển chính Thien Huyen ngay trong sandbox điện thoại và build APK trên đám mây",
                        onClick = onOpenCustomIteration,
                    )
                }
            }

            item {
                Text(
                    text = "开发者调试与控制台",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    ToggleRow(
                        icon = RuntimeIconName.Bug,
                        title = "开发者诊断模式",
                        subtitle = "开启底层健康监控与调试控制台",
                        checked = developer,
                        change = viewModel::setDeveloperMode,
                    )
                    if (developer) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsRow(
                            icon = RuntimeIconName.Terminal,
                            title = "开发者控制台",
                            subtitle = "实时查看 PRoot 进程与命令追踪",
                            onClick = onOpenDeveloper,
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleRow(
                        icon = RuntimeIconName.Cpu,
                        title = "Chế độ tương thích QEMU x86_64",
                        subtitle = if (qemuCompatibilityReady) {
                            "Cho phép phiên được yêu cầu rõ ràng dùng QEMU x86_64 user-mode; phiên ARM64 không bị ảnh hưởng"
                        } else {
                            "Chưa phát hiện môi trường tương thích QEMU x86_64, vui lòng cài plugin qemu-x86-64-compat trong trung tâm plugin trước"
                        },
                        checked = qemuCompatibilityEnabled && qemuCompatibilityReady,
                        enabled = qemuCompatibilityReady,
                        change = viewModel::setQemuCompatibilityEnabled,
                    )
                    Text(
                        text = qemuCompatibilityMessage ?: if (qemuCompatibilityEnabled) {
                            "Đã bật. Plugin tương thích chỉ cung cấp QEMU user-mode ARM64 và RootFS x86_64 tối thiểu."
                        } else {
                            "Mặc định tắt, sẽ không tải hoặc dùng công cụ x86_64. Sau khi bật chỉ có hiệu lực với dự án bên thứ ba chọn rõ môi trường tương thích, không thay đổi ABI mặc định arm64-v8a của APK."
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
                Text("电池优化与后台运行", fontWeight = FontWeight.Bold)
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
                            if (exempted) "已豁免电池优化" else "未豁免 · 后台可能被冻结",
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
                    "天玄在 Agent 执行期间会启动前台服务并持有 CPU 进程锁，但系统电池优化仍可能在息屏后" +
                        "冻结进程，表现为 Agent 推理或命令执行中途停住。建议开启以下两项：",
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
                            Toast.makeText(context, "Không thể mở cài đặt hệ thống, hãy tự vào cài đặt cấp miễn trừ tối ưu pin", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("申请豁免电池优化")
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
                            Toast.makeText(context, "Không thể mở chi tiết ứng dụng, vui lòng vào cài đặt hệ thống thủ công", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("打开应用详情（自启动/后台运行）")
                }
                Text(
                    "提示：小米/华为/OPPO 等厂商系统还需在应用详情中手动允许「自启动」与「后台运行」，" +
                        "否则厂商省电策略仍会终止进程。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
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
        PhantomProcessLimitState.REMOVED -> "已解除限制"
        PhantomProcessLimitState.ACTIVE -> "限制仍生效"
        PhantomProcessLimitState.UNSUPPORTED -> "当前系统无需处理"
        PhantomProcessLimitState.UNAVAILABLE -> "暂时无法检测"
        null -> if (busy) "正在检测" else "尚未检测"
    }
    val healthy = state == PhantomProcessLimitState.REMOVED || state == PhantomProcessLimitState.UNSUPPORTED
    val statusContainer = if (healthy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val statusContent = if (healthy) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Speed, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Android 12 子进程限制", fontWeight = FontWeight.Bold)
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
                    status?.details ?: "读取系统实际配置，确认幽灵进程限制是否仍在生效。",
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
                                "Số tiến trình ma tối đa: ${status.maxPhantomProcesses ?: "系统默认（通常为 32）"}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                            Text(
                                "Giám sát tiến trình ma: ${when (status.monitoringEnabled) { true -> "开启"; false -> "关闭"; null -> "系统默认（开启）" }}",
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }

                Text(
                    "Android 12+ 会监控应用派生的子进程，超过系统上限后可能终止 PRoot、编译器或 Agent 任务。这里解除的是子进程限制，不是 Java/Kotlin 线程数。",
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
                        Text("正在处理")
                    } else {
                        Text("使用 Shizuku / Root 一键解除")
                    }
                }

                Text(
                    "也可以在已连接手机的电脑终端执行：",
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
                                copyToClipboard(context, adbCommand, "Android 12 子进程限制命令", "命令已复制")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            RuntimeIcon(RuntimeIconName.Copy, Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("复制命令")
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
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
    )
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
