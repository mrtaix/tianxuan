package top.wkbin.tianxuan.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CTHarness · Thanh điều hướng thường trực cho màn hình rộng.
 *
 * Điện thoại dùng thanh dưới cùng (xem [RuntimeBottomBar]); máy tính bảng ngang dùng thanh bên:
 * thanh dưới khó với tới, bị bàn phím đẩy lên và khó giữ dấu vị trí hiện tại.
 *
 * Lấy chiều rộng từ [LocalConfiguration], không dùng windowMetrics của Activity:
 * configuration của Compose đã trừ thanh hệ thống và tự tái tổ hợp khi gập máy hoặc chia màn hình.
 * Không cần đăng ký bộ lắng nghe; dùng remember để tránh tính lại ở mỗi lần tái tổ hợp.
 */
@Composable
fun rememberWidthClass(): TianXuanWidthClass {
    val configuration = LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    // Chiều rộng chỉ đổi khi cấu hình đổi; dùng làm key để tránh tính lại khi tái tổ hợp không liên quan.
    return remember(widthDp) { widthClassOf(widthDp) }
}

/**
 * Thanh điều hướng bên thường trực.
 *
 * @param selected cửa chức năng đang chọn
 * @param onNavigate xử lý thao tác điều hướng
 * @param widthClass nhóm chiều rộng do bên gọi quyết định, có thể cố định trong kiểm thử
 * @param modifier bộ điều chỉnh bố cục
 */
@Composable
fun RuntimeNavRail(
    selected: MainDestination,
    onNavigate: (MainDestination) -> Unit,
    modifier: Modifier = Modifier,
    widthClass: TianXuanWidthClass = rememberWidthClass(),
) {
    val railWidth: Dp = if (widthClass.showsNavLabels) {
        TianXuanBreakpoints.RAIL_WIDTH_DP.dp
    } else {
        TianXuanBreakpoints.COMPACT_RAIL_WIDTH_DP.dp
    }

    Column(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MainDestination.entries.forEach { destination ->
            NavRailItem(
                destination = destination,
                selected = selected == destination,
                showLabel = widthClass.showsNavLabels,
                onClick = { onNavigate(destination) },
            )
        }
    }
}

/**
 * Một mục điều hướng.
 *
 * Trạng thái chọn dùng cả màu nền và chữ đậm, không chỉ đổi màu biểu tượng:
 * mục điều hướng trên máy tính bảng xa ngón tay hơn nên cần dấu hiệu rõ ràng.
 */
@Composable
private fun NavRailItem(
    destination: MainDestination,
    selected: Boolean,
    showLabel: Boolean,
    onClick: () -> Unit,
) {
    val label = stringResource(destination.labelRes)
    val container = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val content = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // testTag giúp kiểm thử kết xuất tìm mục trong bố cục thích ứng.
            // Không dùng nội dung bản dịch để khẳng định: đổi tên tinh tượng không nên làm hỏng kiểm thử.
            .testTag(NAV_ITEM_TEST_TAG)
            // Màu nền thể hiện trạng thái chọn: trên máy tính bảng, mục điều hướng xa ngón tay;
            // chỉ đổi màu biểu tượng chưa đủ để nhận ra ngay cửa chức năng hiện tại.
            .background(container, MaterialTheme.shapes.large)
            .padding(vertical = 10.dp, horizontal = if (showLabel) 12.dp else 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RuntimeIconButton(
            onClick = onClick,
            contentDescription = label,
        ) {
            RuntimeIcon(name = destination.icon, tint = content)
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = content,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Dấu ngữ nghĩa của mục điều hướng để kiểm thử kết xuất tìm nút trong bố cục thích ứng. */
const val NAV_ITEM_TEST_TAG: String = "adaptive-nav-item"
