package top.wkbin.tianxuan.ui.preview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import top.wkbin.tianxuan.ui.theme.AppleTypography
import top.wkbin.tianxuan.ui.components.CapsuleShape
import top.wkbin.tianxuan.ui.components.InfoRow
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeBadge
import top.wkbin.tianxuan.ui.components.RuntimeButton
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeCheckbox
import top.wkbin.tianxuan.ui.components.RuntimeDropdownMenu
import top.wkbin.tianxuan.ui.components.RuntimeDropdownMenuItem
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconButton
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeLiquidButton
import top.wkbin.tianxuan.ui.components.RuntimeModalBottomSheet
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedTextField
import top.wkbin.tianxuan.ui.components.RuntimeRadioButton
import top.wkbin.tianxuan.ui.components.RuntimeSearchBar
import top.wkbin.tianxuan.ui.components.RuntimeSegmentedControl
import top.wkbin.tianxuan.ui.components.RuntimeSheetHeader
import top.wkbin.tianxuan.ui.components.RuntimeSlider
import top.wkbin.tianxuan.ui.components.RuntimeSwitch
import top.wkbin.tianxuan.ui.components.RuntimeTextButton
import top.wkbin.tianxuan.ui.components.SectionHeader
import top.wkbin.tianxuan.ui.components.StatusBadge
import top.wkbin.tianxuan.ui.components.TianXuanBrandBadge
import top.wkbin.tianxuan.ui.theme.ChengmingDarkColors
import top.wkbin.tianxuan.ui.theme.ChengmingLightColors
import top.wkbin.tianxuan.ui.theme.LiquidGlassTypography
import top.wkbin.tianxuan.ui.theme.LocalLiquidGlassSurfaceBackdrop
import top.wkbin.tianxuan.ui.theme.TianXuanShapes
import top.wkbin.tianxuan.ui.theme.ThemeStyle
import top.wkbin.tianxuan.ui.theme.XuantongDarkColors
import top.wkbin.tianxuan.ui.theme.XuantongLightColors

/**
 * 预览底衬壁纸类型定义。
 */
enum class CatalogWallpaper(val title: String) {
    AURORA("Cực quang chảy sáng"),
    LIQUID_BLOOM("Khúc xạ lỏng"),
    NEON_DARK("Đêm Tối Cyber"),
    MINIMAL_CLEAN("Trắng tối giản"),
}

/**
 * 天玄 · 澄明液态玻璃全景组件图鉴与工作台 (LiquidGlassCatalogScreen)。
 *
 * 聚合展示全套对齐 Apple HIG Materials 与 Kyant Backdrop 规范的液态玻璃控件：
 * 1. 字体排版标尺 (AppleTypography)；
 * 2. 交互式流光触感按钮 (RuntimeLiquidButton / RuntimeButton)；
 * 3. 药丸分段选择器 (RuntimeSegmentedControl)；
 * 4. 内嵌凹陷文本框与搜索栏 (RuntimeOutlinedTextField / RuntimeSearchBar)；
 * 5. 双重折射滑动条与开关 (RuntimeSlider / RuntimeSwitch)；
 * 6. 模态底部抽屉与多层嵌套玻璃 (RuntimeModalBottomSheet Glass-on-Glass)；
 * 7. 上下文浮动菜单与弹窗 (RuntimeDropdownMenu / RuntimeAlertDialog)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiquidGlassCatalogScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    var currentThemeStyle by remember { mutableStateOf(ThemeStyle.LIQUID_GLASS) }
    var isDarkTheme by remember { mutableStateOf(true) }
    var selectedWallpaper by remember { mutableStateOf(CatalogWallpaper.AURORA) }

    // 状态模拟
    var segmentedIndex by remember { mutableIntStateOf(0) }
    var sampleText by remember { mutableStateOf("Linux AIRuntime (Thien Huyen)") }
    var searchQuery by remember { mutableStateOf("") }
    var switchState by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableFloatStateOf(0.65f) }
    var checkboxState by remember { mutableStateOf(true) }
    var radioIndex by remember { mutableIntStateOf(0) }

    // 弹窗与抽屉控制
    val sheetScope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }
    var showAlertDialog by remember { mutableStateOf(false) }
    var showDropdownMenu by remember { mutableStateOf(false) }

    val colorScheme = when (currentThemeStyle) {
        ThemeStyle.LIQUID_GLASS -> if (isDarkTheme) ChengmingDarkColors else ChengmingLightColors
        ThemeStyle.MATERIAL_YOU -> if (isDarkTheme) XuantongDarkColors else XuantongLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LiquidGlassTypography,
        shapes = TianXuanShapes,
    ) {
        val rootBackdrop = rememberLayerBackdrop()

        Box(
            modifier = modifier
                .fillMaxSize()
                .background(if (isDarkTheme) Color(0xFF090B10) else Color(0xFFF2F2F7)),
        ) {
            // 1. 底层高保真流光/折射壁纸渲染（受 rootBackdrop 捕获供全屏玻璃控件折射）
            CatalogWallpaperCanvas(
                wallpaper = selectedWallpaper,
                isDark = isDarkTheme,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (currentThemeStyle == ThemeStyle.LIQUID_GLASS) {
                            Modifier.layerBackdrop(rootBackdrop)
                        } else Modifier,
                    ),
            )

            // 2. 顶层主内容，向内注入 LocalLiquidGlassSurfaceBackdrop
            CompositionLocalProvider(
                LocalLiquidGlassSurfaceBackdrop provides if (currentThemeStyle == ThemeStyle.LIQUID_GLASS) rootBackdrop else null,
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        CatalogTopBar(
                            themeStyle = currentThemeStyle,
                            isDark = isDarkTheme,
                            wallpaper = selectedWallpaper,
                            onToggleTheme = {
                                currentThemeStyle = if (currentThemeStyle == ThemeStyle.LIQUID_GLASS) {
                                    ThemeStyle.MATERIAL_YOU
                                } else {
                                    ThemeStyle.LIQUID_GLASS
                                }
                            },
                            onToggleDark = { isDarkTheme = !isDarkTheme },
                            onSelectWallpaper = { selectedWallpaper = it },
                            onBack = onBack,
                        )
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        // 顶部状态横幅
                        CatalogModeBanner(currentThemeStyle, isDarkTheme, selectedWallpaper)

                        // 展区 1：字体与排版层级 (Typography Scale)
                        CatalogTypographySection()

                        // 展区 2：按钮与触控物理 (Buttons & Interactive Physics)
                        CatalogButtonsSection()

                        // 展区 3：胶囊分段器 (Segmented Controls)
                        CatalogSegmentedSection(
                            selectedIndex = segmentedIndex,
                            onSelect = { segmentedIndex = it },
                        )

                        // 展区 4：文本输入框与搜索栏 (TextFields & Search)
                        CatalogInputSection(
                            sampleText = sampleText,
                            onTextChange = { sampleText = it },
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                        )

                        // 展区 5：开关与滑动条 (Toggles & Sliders)
                        CatalogControlsSection(
                            switchState = switchState,
                            onSwitchChange = { switchState = it },
                            sliderValue = sliderValue,
                            onSliderChange = { sliderValue = it },
                            checkboxState = checkboxState,
                            onCheckboxChange = { checkboxState = it },
                            radioIndex = radioIndex,
                            onRadioChange = { radioIndex = it },
                        )

                        // 展区 6：浮层、抽屉与多层嵌套 (Sheets & Dialogs Glass-on-Glass)
                        CatalogOverlaysSection(
                            onOpenSheet = { showBottomSheet = true },
                            onOpenDialog = { showAlertDialog = true },
                            onOpenDropdown = { showDropdownMenu = true },
                            dropdownExpanded = showDropdownMenu,
                            onDismissDropdown = { showDropdownMenu = false },
                        )

                        // 展区 7：内容卡片与状态角标 (Cards & Badges)
                        CatalogCardsSection()

                        Spacer(Modifier.height(48.dp))
                    }
                }

                // 模态底部抽屉 (Modal Bottom Sheet) 实装演示
                if (showBottomSheet) {
                    RuntimeModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = bottomSheetState,
                    ) {
                        RuntimeSheetHeader(
                            title = "Ngăn kéo kính lỏng (Sheet Presentation)",
                            onDismiss = { showBottomSheet = false },
                            onConfirm = { showBottomSheet = false },
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                text = "✨ Demo lồng nhau an toàn Glass-on-Glass: ngăn kéo này dùng chất liệu Thick, thẻ và nút bên trong ngăn kéo đã tự động khúc xạ ngăn kéo này qua CompositionLocalProvider, triệt tiêu hoàn toàn sự cố hợp thành đệ quy RenderThread.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // 抽屉内部嵌套卡片
                            RuntimeCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Thẻ lồng nhau (Second Glass Layer)",
                                        style = AppleTypography.Headline,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Chú ý quan sát khúc xạ thấu kính và điểm sáng ở viền thẻ này, nền khúc xạ chính xác ngăn kéo, tạo cảm giác bán trong suốt độ sâu mức vật lý.",
                                        style = AppleTypography.Subhead,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    RuntimeLiquidButton(
                                        onClick = { showBottomSheet = false },
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Xác nhận và đóng ngăn kéo", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // 对话框 (AlertDialog) 演示
                if (showAlertDialog) {
                    RuntimeAlertDialog(
                        onDismissRequest = { showAlertDialog = false },
                        title = { Text("Hộp thoại Liquid Glass Thien Huyen", style = AppleTypography.Headline) },
                        text = {
                            Text(
                                "Hoàn toàn dựa trên kiến trúc Apple HIG Materials và Kyant Backdrop, hỗ trợ mặt nạ chiều sâu và phản hồi xúc giác khi nhấn.",
                                style = AppleTypography.Body,
                            )
                        },
                        confirmButton = {
                            RuntimeLiquidButton(
                                onClick = { showAlertDialog = false },
                                tint = MaterialTheme.colorScheme.primary,
                            ) {
                                Text("知道了", color = Color.White)
                            }
                        },
                        dismissButton = {
                            RuntimeTextButton(onClick = { showAlertDialog = false }) {
                                Text("取消")
                            }
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 各展区精装子组件实现
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CatalogModeBanner(
    themeStyle: ThemeStyle,
    isDark: Boolean,
    wallpaper: CatalogWallpaper,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TianXuanBrandBadge(size = 42.dp)
                Column {
                    Text(
                        text = if (themeStyle == ThemeStyle.LIQUID_GLASS) "Trừng Minh · Chế độ kính lỏng" else "Huyền Đồng · Chế độ Material You",
                        style = AppleTypography.Headline,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Nền lót: ${wallpaper.title} · ${if (isDark) "深色" else "浅色"}Giao diện",
                        style = AppleTypography.Footnote,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            RuntimeBadge(containerColor = MaterialTheme.colorScheme.primary) {
                Text(if (themeStyle == ThemeStyle.LIQUID_GLASS) "iOS HIG" else "M3 Native")
            }
        }
    }
}

@Composable
private fun CatalogTypographySection() {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SectionHeader(
                title = "Thước đo font và bố cục (Apple HIG Scale)",
                subtitle = "Bậc độ đậm San Francisco (SF Pro) và Tight Tracking âm",
            )
            Text("LargeTitle (34sp Bold)", style = AppleTypography.LargeTitle)
            Text("Title1 (28sp Bold)", style = AppleTypography.Title1)
            Text("Title2 (22sp Bold)", style = AppleTypography.Title2)
            Text("Title3 (20sp SemiBold)", style = AppleTypography.Title3)
            Text("Headline (17sp SemiBold)", style = AppleTypography.Headline)
            Text("Body (17sp Regular) · Môi trường chạy sandbox Linux native trên thiết bị di động Thiên Huyền", style = AppleTypography.Body)
            Text("Callout (16sp Regular) · Lõi Tri Khu giao tiếp kết đôi PTY gốc với terminal", style = AppleTypography.Callout)
            Text("Footnote (13sp Regular) · Căn chỉnh phân cấp chất liệu quang học Apple HIG Materials", style = AppleTypography.Footnote)
            Text("Caption (12sp Regular) · Đường phản quang đỉnh trời gương 1px", style = AppleTypography.Caption1)
        }
    }
}

@Composable
private fun CatalogButtonsSection() {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "Nút và hiệu ứng chạm phát sáng (Liquid Buttons)",
                subtitle = "Đốm sáng khuếch tán chảy theo chạm AGSL + biến dạng kéo giãn giảm chấn hai trục tanh",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 原版 Kyant 胶囊流体按钮
                RuntimeLiquidButton(
                    onClick = {},
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Play, Modifier.size(18.dp), tint = Color.White)
                    Text("Nút lưu quang", color = Color.White, style = AppleTypography.Button)
                }

                // 成功/绿色浸润按钮
                RuntimeLiquidButton(
                    onClick = {},
                    tint = Color(0xFF34C759),
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Check, Modifier.size(18.dp), tint = Color.White)
                    Text("Thấm nhuần thành công", color = Color.White, style = AppleTypography.Button)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 标准 RuntimeButton
                RuntimeButton(
                    onClick = {},
                    tonal = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Lấp đầy Tonal")
                }

                // 边框按钮与图标按钮
                RuntimeOutlinedButton(
                    onClick = {},
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Nút viền")
                }

                RuntimeIconButton(
                    onClick = {},
                ) {
                    RuntimeIcon(RuntimeIconName.Refresh)
                }
                RuntimeIconButton(
                    onClick = {},
                ) {
                    RuntimeIcon(RuntimeIconName.Settings)
                }
            }
        }
    }
}

@Composable
private fun CatalogSegmentedSection(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(
                title = "Bộ chọn phân đoạn dạng viên nhộng (Segmented Control)",
                subtitle = "UltraThin ray nền + thanh trượt tán sắc Thin + cảm giác đàn hồi kéo giãn nhẹ theo tốc độ",
            )

            RuntimeSegmentedControl(
                options = listOf("Tổng quan toàn cục", "Trung khu terminal", "Trí Phường", "Dao Quang"),
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Mục đang kích hoạt: mục thứ ${selectedIndex + 1} · nhấp hoặc gạt trái/phải để cảm nhận động lực học LiquidSpringSpec và phản hồi rung theo chuẩn Apple.",
                style = AppleTypography.Footnote,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CatalogInputSection(
    sampleText: String,
    onTextChange: (String) -> Unit,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "Nhập và tìm kiếm (Inset Glass Fields)",
                subtitle = "UltraThin bóng đổ vi lõm + đường phản chiếu gương 1px + phát sáng ngoài dòng chảy khi focus",
            )

            // 全圆角搜索栏
            RuntimeSearchBar(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholder = "Tìm ứng dụng sandbox, lệnh hoặc workspace...",
                modifier = Modifier.fillMaxWidth(),
            )

            // 带标签与图标的内嵌输入框
            RuntimeOutlinedTextField(
                value = sampleText,
                onValueChange = onTextChange,
                label = { Text("Tên container sandbox") },
                placeholder = { Text("Hãy nhập tên container...") },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp)) },
                supportingText = { Text("Hỗ trợ tổ hợp tiếng Trung-Anh, số và dấu gạch ngang") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CatalogControlsSection(
    switchState: Boolean,
    onSwitchChange: (Boolean) -> Unit,
    sliderValue: Float,
    onSliderChange: (Float) -> Unit,
    checkboxState: Boolean,
    onCheckboxChange: (Boolean) -> Unit,
    radioIndex: Int,
    onRadioChange: (Int) -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "Công tắc và thanh trượt (Toggles & Sliders)",
                subtitle = "Khúc xạ kép ngón cái + biến dạng ép nén đàn hồi (Squash & Stretch)",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Công tắc kính lỏng (LiquidToggle)", style = AppleTypography.Headline)
                    Text("Thanh trượt tán sắc khúc xạ kép", style = AppleTypography.Footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RuntimeSwitch(
                    checked = switchState,
                    onCheckedChange = onSwitchChange,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Thanh trượt giảm chấn (LiquidSlider)", style = AppleTypography.Callout)
                    Text("${(sliderValue * 100).toInt()}%", style = AppleTypography.Callout, fontWeight = FontWeight.Bold)
                }
                RuntimeSlider(
                    value = sliderValue,
                    onValueChange = onSliderChange,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuntimeCheckbox(checked = checkboxState, onCheckedChange = onCheckboxChange)
                    Spacer(Modifier.width(8.dp))
                    Text("Theo màu sáng/tối hệ thống", style = AppleTypography.Subhead)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuntimeRadioButton(selected = radioIndex == 0, onClick = { onRadioChange(0) })
                    Text("Tùy chọn A", style = AppleTypography.Subhead)
                    Spacer(Modifier.width(8.dp))
                    RuntimeRadioButton(selected = radioIndex == 1, onClick = { onRadioChange(1) })
                    Text("Lựa chọn B", style = AppleTypography.Subhead)
                }
            }
        }
    }
}

@Composable
private fun CatalogOverlaysSection(
    onOpenSheet: () -> Unit,
    onOpenDialog: () -> Unit,
    onOpenDropdown: () -> Unit,
    dropdownExpanded: Boolean,
    onDismissDropdown: () -> Unit,
) {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "Ngăn kéo, popup và lớp nổi (Sheets & Overlays)",
                subtitle = "Thick/Regular vật liệu + bo góc lớn 28dp + lồng ghép an toàn Glass-on-Glass",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuntimeButton(
                    onClick = onOpenSheet,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Mở ngăn kéo dưới cùng")
                }

                RuntimeOutlinedButton(
                    onClick = onOpenDialog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Bật hộp thoại")
                }

                Box {
                    RuntimeIconButton(
                        onClick = onOpenDropdown,
                    ) {
                        RuntimeIcon(RuntimeIconName.More)
                    }
                    RuntimeDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = onDismissDropdown,
                    ) {
                        RuntimeDropdownMenuItem(
                            text = { Text("Cài đặt workspace") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Settings, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                        RuntimeDropdownMenuItem(
                            text = { Text("Nạp lại sandbox") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                        RuntimeDropdownMenuItem(
                            text = { Text("Xóa cache") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Trash, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogCardsSection() {
    RuntimeCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionHeader(
                title = "Container nội dung và huy hiệu trạng thái (Cards & Badges)",
                subtitle = "Panel nội dung chất liệu Thick + góc trạng thái dạng giọt nước tán sắc",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusBadge(text = "运行中", color = Color(0xFF34C759))
                StatusBadge(text = "已就绪", color = MaterialTheme.colorScheme.primary)
                StatusBadge(text = "异常", color = MaterialTheme.colorScheme.error)
                RuntimeBadge(containerColor = Color(0xFFFF9500)) {
                    Text("NEW")
                }
                RuntimeBadge(containerColor = Color(0xFFFF2D55)) {
                    Text("99+")
                }
            }

            InfoRow(
                label = "Sandbox kiến trúc ARM64",
                value = "Ubuntu 24.04 LTS (PRoot)",
            )
            InfoRow(
                label = "Lõi shader render",
                value = "Android AGSL Liquid Shaders",
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// 顶栏与动态壁纸画布
// ═══════════════════════════════════════════════════════════════════════════

@Composable
private fun CatalogTopBar(
    themeStyle: ThemeStyle,
    isDark: Boolean,
    wallpaper: CatalogWallpaper,
    onToggleTheme: () -> Unit,
    onToggleDark: () -> Unit,
    onSelectWallpaper: (CatalogWallpaper) -> Unit,
    onBack: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (onBack != null) {
                RuntimeIconButton(onClick = onBack) {
                    RuntimeIcon(RuntimeIconName.Back)
                }
            }
            Column {
                Text(
                    text = "Toàn cảnh đồ giám kính lỏng",
                    style = AppleTypography.Headline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "TianXuan Liquid Glass Catalog",
                    style = AppleTypography.Caption2,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 壁纸切换器
            Box(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                    .clickable {
                        val nextIdx = (wallpaper.ordinal + 1) % CatalogWallpaper.entries.size
                        onSelectWallpaper(CatalogWallpaper.entries[nextIdx])
                    }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = "Hình nền: ${wallpaper.title}",
                    style = AppleTypography.Caption1,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 主题切换（澄明 vs 玄同）
            Box(
                modifier = Modifier
                    .clip(CapsuleShape)
                    .background(
                        if (themeStyle == ThemeStyle.LIQUID_GLASS) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                    )
                    .clickable(onClick = onToggleTheme)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(
                    text = if (themeStyle == ThemeStyle.LIQUID_GLASS) "Trừng Minh" else "Huyền Đồng",
                    style = AppleTypography.Caption1,
                    fontWeight = FontWeight.Bold,
                    color = if (themeStyle == ThemeStyle.LIQUID_GLASS) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }

            // 深浅切换
            RuntimeIconButton(
                onClick = onToggleDark,
            ) {
                RuntimeIcon(if (isDark) RuntimeIconName.Palette else RuntimeIconName.Sparkles)
            }
        }
    }
}

/**
 * 动态流光壁纸画布：生成丰富渐变色块，测试玻璃的折射效果。
 */
@Composable
private fun CatalogWallpaperCanvas(
    wallpaper: CatalogWallpaper,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    // aurora 渐变球的圆心偏移动画（已被移除：原 animOffset 从未被 Canvas 读取，
    // 却以 60fps 持续驱动重组与重绘，浪费 CPU/电量）
    val auroraCenter = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)

    when (wallpaper) {
        CatalogWallpaper.AURORA -> {
            Canvas(modifier = modifier) {
                // 极光渐变球
                val w = size.width
                val h = size.height

                drawRect(if (isDark) Color(0xFF090D18) else Color(0xFFE8EEF8))

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF007AFF).copy(alpha = 0.45f) else Color(0xFF007AFF).copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.25f, h * 0.20f),
                        radius = w * 0.65f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFFAF52DE).copy(alpha = 0.40f) else Color(0xFFAF52DE).copy(alpha = 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.80f, h * 0.45f),
                        radius = w * 0.70f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            if (isDark) Color(0xFF34C759).copy(alpha = 0.30f) else Color(0xFF34C759).copy(alpha = 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.30f, h * 0.75f),
                        radius = w * 0.60f,
                    ),
                )
            }
        }
        CatalogWallpaper.LIQUID_BLOOM -> {
            Canvas(modifier = modifier) {
                val w = size.width
                val h = size.height

                drawRect(if (isDark) Color(0xFF100A18) else Color(0xFFFAF0F5))

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF2D55).copy(alpha = if (isDark) 0.40f else 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.70f, h * 0.25f),
                        radius = w * 0.60f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFFFF9500).copy(alpha = if (isDark) 0.35f else 0.25f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.20f, h * 0.55f),
                        radius = w * 0.65f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF5856D6).copy(alpha = if (isDark) 0.40f else 0.30f),
                            Color.Transparent,
                        ),
                        center = Offset(w * 0.60f, h * 0.85f),
                        radius = w * 0.70f,
                    ),
                )
            }
        }
        CatalogWallpaper.NEON_DARK -> {
            Canvas(modifier = modifier) {
                drawRect(Color(0xFF040608))
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF00E5FF).copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(w * 0.15f, h * 0.30f),
                        radius = w * 0.50f,
                    ),
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFF007F).copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(w * 0.85f, h * 0.70f),
                        radius = w * 0.55f,
                    ),
                )
            }
        }
        CatalogWallpaper.MINIMAL_CLEAN -> {
            Canvas(modifier = modifier) {
                drawRect(if (isDark) Color(0xFF141416) else Color(0xFFF7F7F8))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Compose @Preview 支持（Android Studio 实时可视化渲染）
// ═══════════════════════════════════════════════════════════════════════════

@Preview(name = "Trừng Minh · Kính lỏng (Tối)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassCatalogDark() {
    LiquidGlassCatalogScreen()
}

@Preview(name = "Trong trẻo · Liquid Glass (sáng)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassCatalogLight() {
    // 浅色预览
    MaterialTheme(colorScheme = ChengmingLightColors, typography = LiquidGlassTypography) {
        LiquidGlassCatalogScreen()
    }
}
