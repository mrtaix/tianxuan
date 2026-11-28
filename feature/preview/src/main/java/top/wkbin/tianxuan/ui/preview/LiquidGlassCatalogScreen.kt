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
 * Định nghĩa các loại hình nền phủ cho màn hình xem trước.
 */
enum class CatalogWallpaper(val title: String) {
    AURORA("Cực quang rực sáng"),
    LIQUID_BLOOM("Khúc xạ chất lỏng"),
    NEON_DARK("Đêm neon cyber"),
    MINIMAL_CLEAN("Trắng tối giản"),
}

/**
 * Bộ sưu tập và không gian thử nghiệm toàn cảnh các thành phần Liquid Glass của TianXuan (LiquidGlassCatalogScreen).
 *
 * Tập hợp toàn bộ thành phần Liquid Glass tuân theo tiêu chuẩn Apple HIG Materials và Kyant Backdrop:
 * 1. Thang kiểu chữ và bố cục (AppleTypography);
 * 2. Nút tương tác có hiệu ứng ánh sáng và phản hồi chạm (RuntimeLiquidButton / RuntimeButton);
 * 3. Bộ chọn phân đoạn dạng viên thuốc (RuntimeSegmentedControl);
 * 4. Trường văn bản lõm và thanh tìm kiếm tích hợp (RuntimeOutlinedTextField / RuntimeSearchBar);
 * 5. Thanh trượt khúc xạ kép và công tắc (RuntimeSlider / RuntimeSwitch);
 * 6. Trang tính phương thức ở cạnh dưới và các lớp kính lồng nhau (RuntimeModalBottomSheet Glass-on-Glass);
 * 7. Menu nổi theo ngữ cảnh và hộp thoại (RuntimeDropdownMenu / RuntimeAlertDialog).
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

    // Mô phỏng trạng thái
    var segmentedIndex by remember { mutableIntStateOf(0) }
    var sampleText by remember { mutableStateOf("Linux AIRuntime (TianXuan)") }
    var searchQuery by remember { mutableStateOf("") }
    var switchState by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableFloatStateOf(0.65f) }
    var checkboxState by remember { mutableStateOf(true) }
    var radioIndex by remember { mutableIntStateOf(0) }

    // Điều khiển hộp thoại và trang tính
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
            // 1. Kết xuất hình nền ánh sáng/khúc xạ chất lượng cao ở lớp dưới (rootBackdrop thu nhận để các thành phần kính toàn màn hình khúc xạ)
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

            // 2. Nội dung chính ở lớp trên, cung cấp LocalLiquidGlassSurfaceBackdrop cho các thành phần con
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
                        // Biểu ngữ trạng thái đầu trang
                        CatalogModeBanner(currentThemeStyle, isDarkTheme, selectedWallpaper)

                        // Khu vực 1: Cấp bậc kiểu chữ và bố cục (Typography Scale)
                        CatalogTypographySection()

                        // Khu vực 2: Nút và hiệu ứng tương tác chạm (Buttons & Interactive Physics)
                        CatalogButtonsSection()

                        // Khu vực 3: Bộ điều khiển phân đoạn dạng viên thuốc (Segmented Controls)
                        CatalogSegmentedSection(
                            selectedIndex = segmentedIndex,
                            onSelect = { segmentedIndex = it },
                        )

                        // Khu vực 4: Trường nhập văn bản và thanh tìm kiếm (TextFields & Search)
                        CatalogInputSection(
                            sampleText = sampleText,
                            onTextChange = { sampleText = it },
                            searchQuery = searchQuery,
                            onQueryChange = { searchQuery = it },
                        )

                        // Khu vực 5: Công tắc và thanh trượt (Toggles & Sliders)
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

                        // Khu vực 6: Lớp phủ, trang tính và các lớp kính lồng nhau (Sheets & Dialogs Glass-on-Glass)
                        CatalogOverlaysSection(
                            onOpenSheet = { showBottomSheet = true },
                            onOpenDialog = { showAlertDialog = true },
                            onOpenDropdown = { showDropdownMenu = true },
                            dropdownExpanded = showDropdownMenu,
                            onDismissDropdown = { showDropdownMenu = false },
                        )

                        // Khu vực 7: Thẻ nội dung và nhãn trạng thái (Cards & Badges)
                        CatalogCardsSection()

                        Spacer(Modifier.height(48.dp))
                    }
                }

                // Bản trình diễn trang tính phương thức ở cạnh dưới (Modal Bottom Sheet)
                if (showBottomSheet) {
                    RuntimeModalBottomSheet(
                        onDismissRequest = { showBottomSheet = false },
                        sheetState = bottomSheetState,
                    ) {
                        RuntimeSheetHeader(
                            title = "Trang tính Liquid Glass (Sheet Presentation)",
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
                                text = "✨ Bản trình diễn lồng lớp kính an toàn: trang tính này dùng chất liệu Thick; các thẻ và nút bên trong tự động khúc xạ trang tính thông qua CompositionLocalProvider, tránh hoàn toàn sự cố kết hợp đệ quy trên RenderThread.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )

                            // Thẻ lồng bên trong trang tính
                            RuntimeCard(
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(
                                        "Thẻ lồng nhau (Lớp kính thứ hai)",
                                        style = AppleTypography.Headline,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Hãy quan sát hiệu ứng khúc xạ thấu kính và điểm sáng ở viền thẻ. Nền khúc xạ chính xác trang tính phía sau, tạo cảm giác chiều sâu bán trong suốt chân thực.",
                                        style = AppleTypography.Subhead,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    RuntimeLiquidButton(
                                        onClick = { showBottomSheet = false },
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Xác nhận và đóng trang tính", color = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Bản trình diễn hộp thoại (AlertDialog)
                if (showAlertDialog) {
                    RuntimeAlertDialog(
                        onDismissRequest = { showAlertDialog = false },
                        title = { Text("Hộp thoại Liquid Glass của TianXuan", style = AppleTypography.Headline) },
                        text = {
                            Text(
                                "Được xây dựng hoàn toàn theo kiến trúc Apple HIG Materials và Kyant Backdrop, hỗ trợ mặt nạ chiều sâu và phản hồi xúc giác khi nhấn.",
                                style = AppleTypography.Body,
                            )
                        },
                        confirmButton = {
                            RuntimeLiquidButton(
                                onClick = { showAlertDialog = false },
                                tint = MaterialTheme.colorScheme.primary,
                            ) {
                                Text("Đã hiểu", color = Color.White)
                            }
                        },
                        dismissButton = {
                            RuntimeTextButton(onClick = { showAlertDialog = false }) {
                                Text("Hủy")
                            }
                        },
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Triển khai các thành phần con được hoàn thiện cho từng khu vực
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
                        text = if (themeStyle == ThemeStyle.LIQUID_GLASS) "Chengming · Chế độ Liquid Glass" else "Xuantong · Chế độ Material You",
                        style = AppleTypography.Headline,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Hình nền: ${wallpaper.title} · giao diện ${if (isDark) "tối" else "sáng"}",
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
                title = "Thang kiểu chữ và bố cục (Apple HIG Scale)",
                subtitle = "Các cấp độ độ đậm chữ San Francisco (SF Pro) và khoảng cách ký tự âm (Tight Tracking)",
            )
            Text("LargeTitle (34sp Bold)", style = AppleTypography.LargeTitle)
            Text("Title1 (28sp Bold)", style = AppleTypography.Title1)
            Text("Title2 (22sp Bold)", style = AppleTypography.Title2)
            Text("Title3 (20sp SemiBold)", style = AppleTypography.Title3)
            Text("Headline (17sp SemiBold)", style = AppleTypography.Headline)
            Text("Body (17sp Regular) · Môi trường chạy sandbox Linux gốc trên thiết bị di động TianXuan", style = AppleTypography.Body)
            Text("Callout (16sp Regular) · Giao tiếp ghép đôi giữa lõi Smart Hub và PTY gốc của terminal", style = AppleTypography.Callout)
            Text("Footnote (13sp Regular) · Phân cấp chất liệu quang học theo Apple HIG Materials", style = AppleTypography.Footnote)
            Text("Caption (12sp Regular) · Đường phản chiếu ánh sáng gương trên đỉnh, rộng 1px", style = AppleTypography.Caption1)
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
                title = "Nút và hiệu ứng ánh sáng khi chạm (Liquid Buttons)",
                subtitle = "Vệt sáng AGSL tỏa ra khi chạm + biến dạng kéo giãn hai trục có giảm chấn tanh",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Nút chất lỏng dạng viên thuốc nguyên bản của Kyant
                RuntimeLiquidButton(
                    onClick = {},
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Play, Modifier.size(18.dp), tint = Color.White)
                    Text("Nút ánh sáng", color = Color.White, style = AppleTypography.Button)
                }

                // Nút thành công với sắc xanh lá
                RuntimeLiquidButton(
                    onClick = {},
                    tint = Color(0xFF34C759),
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Check, Modifier.size(18.dp), tint = Color.White)
                    Text("Thành công", color = Color.White, style = AppleTypography.Button)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // RuntimeButton tiêu chuẩn
                RuntimeButton(
                    onClick = {},
                    tonal = true,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Tô màu Tonal")
                }

                // Nút viền và nút biểu tượng
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
                title = "Bộ chọn phân đoạn dạng viên thuốc (Segmented Control)",
                subtitle = "Rãnh nền UltraThin + nút trượt tán sắc Thin + cảm giác đàn hồi kéo giãn nhẹ theo tốc độ",
            )

            RuntimeSegmentedControl(
                options = listOf("Tổng quan", "Trung tâm terminal", "Xưởng thông minh", "Diêu Quang"),
                selectedIndex = selectedIndex,
                onSelect = onSelect,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "Mục đang chọn: ${selectedIndex + 1} · Chạm hoặc chuyển trái/phải để cảm nhận động lực học LiquidSpringSpec và phản hồi rung theo tiêu chuẩn Apple.",
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
                title = "Nhập liệu và tìm kiếm (Inset Glass Fields)",
                subtitle = "Lõm nhẹ UltraThin với bóng trong + đường phản chiếu gương 1px + quầng sáng khi lấy tiêu điểm",
            )

            // Thanh tìm kiếm bo tròn hoàn toàn
            RuntimeSearchBar(
                query = searchQuery,
                onQueryChange = onQueryChange,
                placeholder = "Tìm ứng dụng sandbox, lệnh hoặc không gian làm việc...",
                modifier = Modifier.fillMaxWidth(),
            )

            // Trường nhập liệu lõm, có nhãn và biểu tượng
            RuntimeOutlinedTextField(
                value = sampleText,
                onValueChange = onTextChange,
                label = { Text("Tên container sandbox") },
                placeholder = { Text("Nhập tên container...") },
                leadingIcon = { RuntimeIcon(RuntimeIconName.Terminal, Modifier.size(20.dp)) },
                supportingText = { Text("Hỗ trợ chữ Trung, chữ Anh, chữ số và dấu gạch ngang") },
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
                subtitle = "Núm trượt khúc xạ kép + hiệu ứng bóp và kéo giãn có giảm chấn (Squash & Stretch)",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Công tắc Liquid Glass (LiquidToggle)", style = AppleTypography.Headline)
                    Text("Núm trượt tán sắc khúc xạ kép", style = AppleTypography.Footnote, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("Thanh trượt có giảm chấn (LiquidSlider)", style = AppleTypography.Callout)
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
                    Text("Theo giao diện sáng/tối của hệ thống", style = AppleTypography.Subhead)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuntimeRadioButton(selected = radioIndex == 0, onClick = { onRadioChange(0) })
                    Text("Tùy chọn A", style = AppleTypography.Subhead)
                    Spacer(Modifier.width(8.dp))
                    RuntimeRadioButton(selected = radioIndex == 1, onClick = { onRadioChange(1) })
                    Text("Tùy chọn B", style = AppleTypography.Subhead)
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
                title = "Trang tính, hộp thoại và lớp phủ (Sheets & Overlays)",
                subtitle = "Chất liệu Thick/Regular + góc bo lớn 28dp + lồng lớp kính an toàn (Glass-on-Glass)",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                RuntimeButton(
                    onClick = onOpenSheet,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Mở trang tính bên dưới")
                }

                RuntimeOutlinedButton(
                    onClick = onOpenDialog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Mở hộp thoại")
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
                            text = { Text("Cài đặt không gian làm việc") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Settings, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                        RuntimeDropdownMenuItem(
                            text = { Text("Tải lại sandbox") },
                            leadingIcon = { RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(18.dp)) },
                            onClick = onDismissDropdown,
                        )
                        RuntimeDropdownMenuItem(
                            text = { Text("Xóa bộ nhớ đệm") },
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
                title = "Thẻ nội dung và nhãn trạng thái (Cards & Badges)",
                subtitle = "Bảng nội dung chất liệu Thick + nhãn trạng thái dạng giọt tán sắc",
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatusBadge(text = "Đang chạy", color = Color(0xFF34C759))
                StatusBadge(text = "Sẵn sàng", color = MaterialTheme.colorScheme.primary)
                StatusBadge(text = "Lỗi", color = MaterialTheme.colorScheme.error)
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
                label = "Lõi shader kết xuất",
                value = "Android AGSL Liquid Shaders",
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// Thanh trên cùng và canvas hình nền động
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
                    text = "Bộ sưu tập Liquid Glass toàn cảnh",
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
            // Bộ chuyển đổi hình nền
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

            // Chuyển chủ đề (Chengming và Xuantong)
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
                    text = if (themeStyle == ThemeStyle.LIQUID_GLASS) "Chengming" else "Xuantong",
                    style = AppleTypography.Caption1,
                    fontWeight = FontWeight.Bold,
                    color = if (themeStyle == ThemeStyle.LIQUID_GLASS) Color.White else MaterialTheme.colorScheme.onSurface,
                )
            }

            // Chuyển chế độ sáng/tối
            RuntimeIconButton(
                onClick = onToggleDark,
            ) {
                RuntimeIcon(if (isDark) RuntimeIconName.Palette else RuntimeIconName.Sparkles)
            }
        }
    }
}

/**
 * Canvas hình nền ánh sáng động: tạo các mảng màu chuyển sắc phong phú để kiểm tra hiệu ứng khúc xạ của kính.
 */
@Composable
private fun CatalogWallpaperCanvas(
    wallpaper: CatalogWallpaper,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    // Hoạt ảnh dịch chuyển tâm của quả cầu chuyển sắc aurora (đã xóa: animOffset trước đây không được Canvas đọc,
    // nhưng vẫn kích hoạt tái tổ hợp và vẽ lại ở 60fps, gây lãng phí CPU/pin)
    val auroraCenter = androidx.compose.ui.geometry.Offset(0.5f, 0.5f)

    when (wallpaper) {
        CatalogWallpaper.AURORA -> {
            Canvas(modifier = modifier) {
                // Quả cầu chuyển sắc cực quang
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
// Hỗ trợ Compose @Preview (kết xuất trực quan trực tiếp trong Android Studio)
// ═══════════════════════════════════════════════════════════════════════════

@Preview(name = "Chengming · Liquid Glass (tối)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassCatalogDark() {
    LiquidGlassCatalogScreen()
}

@Preview(name = "Chengming · Liquid Glass (sáng)", showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun PreviewLiquidGlassCatalogLight() {
    // Bản xem trước giao diện sáng
    MaterialTheme(colorScheme = ChengmingLightColors, typography = LiquidGlassTypography) {
        LiquidGlassCatalogScreen()
    }
}
