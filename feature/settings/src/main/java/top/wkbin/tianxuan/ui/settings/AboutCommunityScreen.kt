package top.wkbin.tianxuan.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.core.model.AppUpdateInfo
import top.wkbin.tianxuan.core.model.Community
import top.wkbin.tianxuan.core.model.UpdateCheckState
import top.wkbin.tianxuan.ui.components.MarkdownText
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeLinearProgressIndicator as LinearProgressIndicator
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text

/**
 * Trang con cấp 2: giới thiệu, cập nhật phiên bản và cộng đồng chính thức.
 */
@Composable
fun AboutCommunityScreen(
    onBack: () -> Unit,
    onOpenSponsor: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsStateWithLifecycle()
    val updateCheckState by viewModel.updateCheckState.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }
    var showReleaseNotesDialog by rememberSaveable { mutableStateOf(false) }
    val currentReleaseNotes by viewModel.currentReleaseNotes.collectAsStateWithLifecycle()
    val isLoadingReleaseNotes by viewModel.isLoadingReleaseNotes.collectAsStateWithLifecycle()
    val currentVersion = rememberAppVersion()

    // Hộp thoại cập nhật phiên bản.
    when (val state = updateCheckState) {
        is UpdateCheckState.Success -> {
            if (state.info.hasUpdate) {
                UpdateInfoDialog(
                    info = state.info,
                    downloadProgress = downloadProgress,
                    isDownloading = isDownloading,
                    onDownload = { state.info.apkDownloadUrl?.let { viewModel.downloadAndInstall(it) } },
                    onOpenBrowser = { openBrowser(context, state.info.releaseUrl) },
                    onDismiss = { viewModel.clearUpdateState() },
                )
            } else {
                RuntimeAlertDialog(
                    onDismissRequest = { viewModel.clearUpdateState() },
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RuntimeIcon(RuntimeIconName.Check, Modifier.size(22.dp), tint = successStatusColor())
                            Text("Bạn đang dùng phiên bản mới nhất", fontWeight = FontWeight.Bold)
                        }
                    },
                    text = {
                        Text("CTHarness v${state.info.currentVersion} hiện là phiên bản ổn định mới nhất, không cần cập nhật.")
                    },
                    confirmButton = {
                        TextButton(onClick = { viewModel.clearUpdateState() }) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            viewModel.clearUpdateState()
                            viewModel.loadCurrentReleaseNotes(currentVersion)
                            showReleaseNotesDialog = true
                        }) {
                            Text("Nhật ký phiên bản này")
                        }
                    },
                )
            }
        }
        is UpdateCheckState.Error -> {
            RuntimeAlertDialog(
                onDismissRequest = { viewModel.clearUpdateState() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RuntimeIcon(RuntimeIconName.Alert, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                        Text("Không thể kiểm tra cập nhật")
                    }
                },
                text = { Text(state.message) },
                confirmButton = {
                    TextButton(onClick = { viewModel.clearUpdateState() }) {
                        Text("Đã hiểu")
                    }
                },
            )
        }
        else -> Unit
    }

    if (showAboutDialog) {
        AboutAppDialog(
            onDismiss = { showAboutDialog = false },
            onOpenReleaseNotes = {
                showAboutDialog = false
                viewModel.loadCurrentReleaseNotes(currentVersion)
                showReleaseNotesDialog = true
            },
        )
    }

    if (showReleaseNotesDialog) {
        VersionReleaseNotesDialog(
            version = currentVersion,
            releaseNotes = currentReleaseNotes,
            isLoading = isLoadingReleaseNotes,
            onDismiss = { showReleaseNotesDialog = false },
            onOpenHistory = { openBrowser(context, "$REPO_URL/releases") },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Giới thiệu và cộng đồng", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Phiên bản và cập nhật ứng dụng",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Update,
                        title = "Kiểm tra phiên bản mới",
                        subtitle = "Tự kiểm tra và cập nhật trực tuyến qua GitHub Releases",
                        value = if (updateCheckState is UpdateCheckState.Checking) "Đang kiểm tra…" else "v$currentVersion",
                        onClick = {
                            // Không kiểm tra lặp khi yêu cầu trước chưa hoàn tất.
                            if (updateCheckState !is UpdateCheckState.Checking) {
                                viewModel.checkForUpdates(currentVersion)
                            }
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Document,
                        title = "Nhật ký cập nhật",
                        subtitle = "Xem ghi chú cập nhật và tính năng nổi bật của phiên bản hiện tại (v$currentVersion)",
                        onClick = {
                            viewModel.loadCurrentReleaseNotes(currentVersion)
                            showReleaseNotesDialog = true
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ToggleRow(
                        icon = RuntimeIconName.Update,
                        title = "Tự kiểm tra cập nhật khi khởi động",
                        subtitle = "Âm thầm kiểm tra phiên bản mới trong nền khi mở ứng dụng",
                        checked = autoCheckUpdates,
                        change = viewModel::setAutoCheckUpdates,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Sparkles,
                        title = "Xem lại hướng dẫn tính năng",
                        subtitle = "Hiển thị lại hướng dẫn lần đầu cho trung tâm tiện ích, xưởng và terminal đa phiên",
                        onClick = {
                            viewModel.replayFirstUseGuides()
                            Toast.makeText(
                                context,
                                "Đã đặt lại hướng dẫn. Hướng dẫn sẽ xuất hiện khi bạn mở lại trang tương ứng.",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }

            item {
                Text(
                    text = "Cộng đồng và tin tức chính thức",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Github,
                        title = "Trang dự án trên GitHub",
                        // Kho mã nguồn riêng tư: chỉ giới thiệu tin phiên bản và lịch sử phát hành.
                        // Không mô tả là mã nguồn mở hay mời Star trái với quyền truy cập thực tế.
                        subtitle = "$REPO_URL · Bản phát hành và thông báo cập nhật",
                        onClick = { openBrowser(context, REPO_URL) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Qq,
                        title = "Nhóm QQ cộng đồng chính thức",
                        subtitle = "Nhóm QQ: ${Community.QQ_GROUP_ID}",
                        value = Community.QQ_GROUP_ID,
                        onClick = { joinQqGroup(context) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Sponsor,
                        title = "Tài trợ dự án",
                        subtitle = "Ủng hộ CTHarness · Tiếp sức phát triển lâu dài",
                        onClick = onOpenSponsor,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Info,
                        title = "Giới thiệu CTHarness",
                        subtitle = "Sandbox Linux PRoot và trung tâm cộng tác AI chạy gốc trên Android",
                        onClick = { showAboutDialog = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutAppDialog(
    onDismiss: () -> Unit,
    onOpenReleaseNotes: () -> Unit,
) {
    val context = LocalContext.current
    val appVersion = rememberAppVersion()
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(name = RuntimeIconName.Package, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("CTHarness", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sandbox Linux PRoot và trung tâm lập trình cùng AI chạy gốc trên Android", style = MaterialTheme.typography.bodyMedium)
                Text("Phiên bản: v$appVersion (Material 3 Expressive)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("Kiến trúc: aarch64 · Ảo hóa không gian người dùng không cần chroot", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Giấy phép: Apache-2.0", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                OutlinedButton(
                    onClick = onOpenReleaseNotes,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Document, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Nhật ký cập nhật phiên bản này")
                }
                OutlinedButton(
                    onClick = { joinQqGroup(context) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    RuntimeIcon(RuntimeIconName.Chat, Modifier.size(16.dp), MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Tham gia nhóm QQ")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun UpdateInfoDialog(
    info: AppUpdateInfo,
    downloadProgress: Float?,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onOpenBrowser: () -> Unit,
    onDismiss: () -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = { if (!isDownloading) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Có phiên bản mới v${info.latestVersion}", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Hiện tại: v${info.currentVersion}  ➔  Mới nhất: v${info.latestVersion}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }

                if (info.releaseNotes.isNotBlank()) {
                    Text(
                        text = "Nhật ký cập nhật:",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MarkdownText(
                            markdown = info.releaseNotes,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }

                if (isDownloading) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Đang tải gói cài đặt cập nhật…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (downloadProgress != null) {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (info.apkDownloadUrl != null) {
                Button(
                    onClick = onDownload,
                    enabled = !isDownloading,
                ) {
                    Text(if (isDownloading) "Đang tải…" else "Cập nhật ngay trong ứng dụng")
                }
            } else {
                Button(onClick = onOpenBrowser) {
                    Text("Tải từ GitHub")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isDownloading) {
                Text("Để sau")
            }
        },
    )
}

@Composable
private fun VersionReleaseNotesDialog(
    version: String,
    releaseNotes: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RuntimeIcon(RuntimeIconName.Document, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Ghi chú cập nhật CTHarness v$version", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (isLoading && releaseNotes.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                    }
                } else if (!releaseNotes.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        MarkdownText(
                            markdown = releaseNotes,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                } else {
                    Text("Chưa có nhật ký cập nhật cho phiên bản hiện tại", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onOpenHistory) { Text("Xem toàn bộ lịch sử") }
        },
    )
}
