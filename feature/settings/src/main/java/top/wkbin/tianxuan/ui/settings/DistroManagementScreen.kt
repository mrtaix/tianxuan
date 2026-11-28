package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeRadioButton as RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.launch
import top.wkbin.tianxuan.core.model.InstalledDistro
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.runtime.DistributionCatalog
import top.wkbin.tianxuan.runtime.DistributionSpec
import top.wkbin.tianxuan.runtime.RegistryRoute
import top.wkbin.tianxuan.runtime.RuntimeInstallRequest
import top.wkbin.tianxuan.ui.components.NoticeBanner
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.StatusBadge
import top.wkbin.tianxuan.ui.components.distroIconFor

/**
 * 天玄 · Linux 发行版与沙箱多实例管理 (Distro Management Hub)
 */
@Composable
fun DistroManagementScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val runtimeState by viewModel.runtimeState.collectAsStateWithLifecycle()
    val restoringDistroId by viewModel.restoringDistroId.collectAsStateWithLifecycle()
    val deletingDistroId by viewModel.deletingDistroId.collectAsStateWithLifecycle()

    // 安装进度保存在 VM（StateFlow），旋转后进度界面可恢复，安装在后台继续进行
    val installState by viewModel.distroInstallState.collectAsStateWithLifecycle()
    var showInstallDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var distroToReset by remember { mutableStateOf<InstalledDistro?>(null) }
    var distroToUninstall by remember { mutableStateOf<InstalledDistro?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val scope = rememberCoroutineScope()

    // 安装成功（isInstalling 从 true -> false 且无错误）后自动关闭弹窗
    var wasInstalling by remember { mutableStateOf(false) }
    LaunchedEffect(installState.isInstalling) {
        if (wasInstalling && !installState.isInstalling && installState.errorMessage == null) {
            showInstallDialog = false
        }
        wasInstalling = installState.isInstalling
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            RuntimeTopBar(
                title = "Quản lý bản phân phối Linux",
                statusText = "Sử dụng đồng thời và chuyển đổi linh hoạt giữa nhiều sandbox",
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
                    text = "Trí Phường hỗ trợ nhiều hệ thống Linux hoạt động đồng thời. Tất cả hệ thống tự động gắn kết dự án mã nguồn tại /workspace; bộ nhớ ngoài /sdcard được gắn kết theo tùy chọn trong trang “Gắn kết và chia sẻ bộ nhớ”. Hệ sinh thái phần mềm và trình quản lý gói của mỗi bản phân phối hoàn toàn độc lập.",
                )
            }

            // 1. 已安装系统列表
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Sandbox đã cài đặt (${installedDistros.size})",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                    )

                    Button(
                        onClick = { showInstallDialog = true },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Plus,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Cài hệ thống mới", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            items(installedDistros, key = { it.id }) { distro ->
                DistroItemCard(
                    distro = distro,
                    isActive = distro.id.equals(activeDistroId, ignoreCase = true),
                    isRestoring = distro.id.equals(restoringDistroId, ignoreCase = true),
                    isDeleting = distro.id.equals(deletingDistroId, ignoreCase = true),
                    canOperateMulti = installedDistros.size > 1,
                    onSwitchActive = {
                        viewModel.switchActiveDistro(distro.id)
                    },
                    onReset = {
                        distroToReset = distro
                    },
                    onUninstall = {
                        distroToUninstall = distro
                    },
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    // Cài hệ thống mới对话框（旋转后只要安装仍在进行就会重新显示进度）
    if (showInstallDialog || installState.isInstalling) {
        InstallDistroDialog(
            installedIds = installedDistros.map { it.id }.toSet(),
            isInstalling = installState.isInstalling,
            progressText = installState.progressText,
            progressFraction = installState.progressFraction,
            errorMessage = installState.errorMessage,
            onDismiss = {
                if (!installState.isInstalling) {
                    showInstallDialog = false
                    viewModel.clearDistroInstallError()
                }
            },
            onConfirmInstall = { spec, route ->
                viewModel.installDistro(
                    request = RuntimeInstallRequest(
                        distributionId = spec.id,
                        registryRoute = route,
                    ),
                )
            },
        )
    }

    // Đặt lại sandbox确认对话框（带 3 秒倒计时锁）
    distroToReset?.let { target ->
        ResetDistroConfirmDialog(
            distro = target,
            onConfirm = {
                val toReset = target.id
                distroToReset = null
                viewModel.resetDistro(toReset) { success, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            },
            onDismiss = { distroToReset = null },
        )
    }

    // Xóa hệ thống确认对话框（带 3 秒倒计时锁）
    distroToUninstall?.let { target ->
        DeleteDistroConfirmDialog(
            distro = target,
            onConfirm = {
                val toDelete = target.id
                distroToUninstall = null
                viewModel.uninstallDistro(toDelete) { success, msg ->
                    scope.launch {
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            },
            onDismiss = { distroToUninstall = null },
        )
    }
}

@Composable
private fun ResetDistroConfirmDialog(
    distro: InstalledDistro,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countdown by remember { mutableStateOf(3) }

    LaunchedEffect(distro.id) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Đặt lại sandbox ${distro.displayName}?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "• Các gói tự cài đặt và thay đổi hệ thống sẽ bị xóa",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "• Không xóa mã nguồn dự án trong /workspace",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = countdown == 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = if (countdown > 0) "Xác nhận đặt lại (${countdown}s)" else "Xác nhận đặt lại",
                    color = if (countdown == 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onError.copy(alpha = 0.6f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Hủy")
            }
        },
    )
}

@Composable
private fun DeleteDistroConfirmDialog(
    distro: InstalledDistro,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    var countdown by remember { mutableStateOf(3) }

    LaunchedEffect(distro.id) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
    }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Xóa hệ thống ${distro.displayName}?",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "• Bản phân phối Linux này và phần mềm đã cài sẽ bị xóa hoàn toàn",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "• Không xóa mã nguồn dự án trong /workspace",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = countdown == 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                ),
            ) {
                Text(
                    text = if (countdown > 0) "Xác nhận xóa (${countdown}s)" else "Xác nhận xóa",
                    color = if (countdown == 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onError.copy(alpha = 0.6f),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Hủy")
            }
        },
    )
}

@Composable
private fun DistroItemCard(
    distro: InstalledDistro,
    isActive: Boolean,
    isRestoring: Boolean,
    isDeleting: Boolean,
    canOperateMulti: Boolean,
    onSwitchActive: () -> Unit,
    onReset: () -> Unit,
    onUninstall: () -> Unit,
) {
    RuntimeCard(
        borderColor = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isActive) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        RuntimeIcon(
                            name = distroIconFor(distro.id),
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = distro.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Mã định danh: ${distro.id} · Trình quản lý gói: ${distro.packageManager} · Dung lượng: ${formatSize(distro.sizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isActive) {
                    StatusBadge(text = "Hệ thống chính", color = successStatusColor())
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 按钮 1: Đặt làm hệ thống chính (当前为Hệ thống chính或仅有 1 个系统时置灰禁用)
                OutlinedButton(
                    onClick = onSwitchActive,
                    enabled = !isActive && canOperateMulti && !isRestoring && !isDeleting,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = "Đặt làm hệ thống chính",
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }

                // 按钮 2: Đặt lại sandbox (执行中显示Đang khôi phục...并锁死)
                OutlinedButton(
                    onClick = onReset,
                    enabled = !isRestoring && !isDeleting,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Đang khôi phục...", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    } else {
                        Text(text = "Đặt lại sandbox", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }

                // 按钮 3: Xóa hệ thống (仅有 1 个系统时置灰禁用，执行中显示Đang xóa...并锁死)
                OutlinedButton(
                    onClick = onUninstall,
                    enabled = canOperateMulti && !isRestoring && !isDeleting,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(
                        1.dp,
                        if (canOperateMulti && !isRestoring && !isDeleting) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                        disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Đang xóa...", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    } else {
                        Text(
                            text = "Xóa hệ thống",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }

            // 单系统时「Đặt làm hệ thống chính」按钮会置灰，补充解释避免用户困惑
            if (!isActive && !canOperateMulti) {
                Text(
                    text = "Hiện chỉ có một hệ thống, không cần đổi hệ thống chính",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InstallDistroDialog(
    installedIds: Set<String>,
    isInstalling: Boolean,
    progressText: String?,
    progressFraction: Float,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConfirmInstall: (DistributionSpec, RegistryRoute) -> Unit,
) {
    val availableDistros = remember {
        DistributionCatalog.supported.filter { it.id !in installedIds }
    }
    var selectedSpec by remember {
        mutableStateOf(availableDistros.firstOrNull() ?: DistributionCatalog.supported.first())
    }
    var selectedRoute by remember { mutableStateOf(RegistryRoute.AUTO) }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isInstalling) "Đang cài sandbox Linux..." else "Cài bản phân phối Linux mới",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isInstalling) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progressFraction.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        )
                        Text(
                            text = progressText ?: "Đang tải xuống và cấu hình RootFS...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    if (availableDistros.isEmpty()) {
                        Text(text = "Đã cài đặt tất cả bản phân phối Linux có sẵn!")
                    } else {
                        Text(
                            text = "Chọn hệ thống Linux để tải xuống và cài đặt:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        availableDistros.forEach { spec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedSpec = spec }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = selectedSpec.id == spec.id,
                                    onClick = { selectedSpec = spec },
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                RuntimeIcon(
                                    name = distroIconFor(spec.id),
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = spec.displayWithVersion,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    )
                                    Text(
                                        text = spec.imageReference,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Nguồn tải xuống và tăng tốc mirror:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { selectedRoute = RegistryRoute.AUTO },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = if (selectedRoute == RegistryRoute.AUTO) {
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text("Tự động tăng tốc", style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(
                                onClick = { selectedRoute = RegistryRoute.CHINA_ACCELERATED },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = if (selectedRoute == RegistryRoute.CHINA_ACCELERATED) {
                                    ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                                } else ButtonDefaults.outlinedButtonColors(),
                            ) {
                                Text("Mirror trong nước", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    errorMessage?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isInstalling && availableDistros.isNotEmpty()) {
                Button(
                    onClick = { onConfirmInstall(selectedSpec, selectedRoute) },
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(text = "Bắt đầu cài đặt")
                }
            }
        },
        dismissButton = {
            if (!isInstalling) {
                TextButton(onClick = onDismiss) {
                    Text(text = "Hủy")
                }
            }
        },
    )
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024 * 1024)
    return if (mb >= 1024) {
        String.format("%.2f GB", mb / 1024)
    } else {
        String.format("%.1f MB", mb)
    }
}
