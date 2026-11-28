package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.ui.components.IconTile
import top.wkbin.tianxuan.ui.components.MainDestination
import top.wkbin.tianxuan.ui.components.RuntimeBottomBar
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.liquidGlassContent
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassBackdrop

/**
 * CTHarness - Cài đặt và cấu hình mô hình.
 */
@Composable
fun SettingsScreen(
    onNavigate: (MainDestination) -> Unit,
    onOpenAgentEco: () -> Unit,
    onOpenLinuxEnv: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenSystemDev: () -> Unit,
    onOpenAboutCommunity: () -> Unit,
    onOpenSearch: () -> Unit = {},
    onOpenA2uiPoc: () -> Unit = {},
    onOpenKnowledge: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val developer by viewModel.developerMode.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val effectiveExecutionMode by viewModel.effectiveExecutionMode.collectAsStateWithLifecycle()
    val installedDistros by viewModel.installedDistros.collectAsStateWithLifecycle()
    val activeDistroId by viewModel.activeDistroId.collectAsStateWithLifecycle()
    val terminalFontSize by viewModel.terminalFontSize.collectAsStateWithLifecycle()

    val themeLabel = when (themeMode) {
        "light" -> "Sáng"
        "dark" -> "Tối"
        else -> "Theo hệ thống"
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val appVersionName = rememberAppVersion()
    var showRoadmap by remember { mutableStateOf(false) }
    val queueSendAfterToolBatch by viewModel.queueSendAfterToolBatch.collectAsStateWithLifecycle()

    val glassBackdrop = LocalLiquidGlassBackdrop.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = "CTHarness · Cài đặt",
                statusText = "Cấu hình và điều khiển hệ thống",
            )
        },
        bottomBar = {
            if (glassBackdrop == null) {
                RuntimeBottomBar(MainDestination.Settings, onNavigate)
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .liquidGlassContent()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Lối tắt tìm kiếm chức năng và tùy chọn
            item {
                RuntimeCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onOpenSearch,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(
                            name = RuntimeIconName.Search,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Tìm chức năng và tùy chọn cài đặt...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.weight(1f),
                        )
                        RuntimeIcon(
                            name = RuntimeIconName.ChevronRight,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Danh mục cài đặt",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
                )
            }

            // 1. Hệ sinh thái agent và mô hình AI
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Brain,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "Agent và mô hình AI",
                    subtitle = "Hồ sơ mô hình · Công cụ và plugin · Kỹ năng và MCP",
                    badge = if (models.isEmpty()) "Chưa cấu hình mô hình" else "${models.size} mô hình · ${skills.count { it.isEnabled }} kỹ năng",
                    onClick = onOpenAgentEco,
                )
            }

            // 2. Sandbox Linux và bộ nhớ
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Server,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "Môi trường Linux và lưu trữ",
                    subtitle = "Quản lý bản phân phối · Gắn bộ nhớ thiết bị · Quyền thực thi",
                    badge = "${installedDistros.size} hệ thống · ${effectiveExecutionMode.shortLabel}",
                    onClick = onOpenLinuxEnv,
                )
            }

            // 3. Giao diện, cỡ chữ và tùy biến terminal
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Palette,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "Giao diện, cỡ chữ và terminal",
                    subtitle = "Chủ đề sáng/tối · Cỡ chữ ứng dụng · Màu và phông terminal",
                    badge = "$themeLabel · ${terminalFontSize}sp",
                    onClick = onOpenAppearance,
                )
            }

            // 4. Giữ hệ thống chạy nền và chẩn đoán nhà phát triển
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Admin,
                    accent = MaterialTheme.colorScheme.primary,
                    title = "Chạy nền và chẩn đoán",
                    subtitle = "Miễn tối ưu pin · Giám sát gỡ lỗi · Bảng điều khiển PRoot",
                    badge = if (developer) "Đã bật chế độ chẩn đoán" else "Hoạt động ổn định",
                    onClick = onOpenSystemDev,
                )
            }

            // 4.5 Tính năng thử nghiệm: PoC trình render A2UI (chỉ hiện ở chế độ chẩn đoán)
            if (developer) {
                item {
                    SettingsCategoryCard(
                        icon = RuntimeIconName.Sparkles,
                        accent = MaterialTheme.colorScheme.tertiary,
                        title = "Trình kết xuất A2UI thử nghiệm",
                        subtitle = "Agent trình bày câu trả lời bằng thẻ giao diện native · Nhấn để xem ví dụ",
                        badge = "Tính năng thử nghiệm",
                        onClick = onOpenA2uiPoc,
                    )
                }
            }

            // 4.6 Kho tri thức RAG: hiển thị cho mọi người dùng (không phải tính năng chẩn đoán)
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Document,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "Kho tri thức RAG",
                    subtitle = "Truy xuất vector · Tự bổ sung ngữ cảnh hội thoại · Tham chiếu bằng @kb:tên_tài_liệu",
                    badge = "Bổ trợ AI",
                    onClick = onOpenKnowledge,
                )
            }

            // 6. Các tính năng sẽ thêm (roadmap)
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Update,
                    accent = MaterialTheme.colorScheme.tertiary,
                    title = "Tính năng mới",
                    subtitle = "Chế độ gửi hàng đợi đã dùng được · kèm danh sách tính năng sẽ bổ sung sau",
                    badge = "Mới",
                    onClick = { showRoadmap = true },
                )
            }

            // 7. Giới thiệu, cập nhật và cộng đồng
            item {
                SettingsCategoryCard(
                    icon = RuntimeIconName.Community,
                    accent = MaterialTheme.colorScheme.secondary,
                    title = "Giới thiệu, cập nhật và cộng đồng",
                    subtitle = "Kiểm tra cập nhật · Mã nguồn GitHub · Nhóm cộng đồng QQ",
                    badge = if (appVersionName == "unknown") "Chưa rõ phiên bản" else "v$appVersionName",
                    onClick = onOpenAboutCommunity,
                )
            }
        }
    }
    if (showRoadmap) {
        RoadmapSettingsDialog(
            queueSendAfterToolBatch = queueSendAfterToolBatch,
            onQueueSendAfterToolBatchChange = viewModel::setQueueSendAfterToolBatch,
            onDismiss = { showRoadmap = false },
        )
    }
}

/**
 * Thẻ điều hướng nhóm cài đặt hiện đại, gọn và tinh tế.
 */
@Composable
private fun SettingsCategoryCard(
    icon: RuntimeIconName,
    accent: Color,
    title: String,
    subtitle: String,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    RuntimeCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconTile(
                icon = icon,
                size = 36.dp,
                color = accent,
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                    )
                }
            }

            RuntimeIcon(
                name = RuntimeIconName.ChevronRight,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
