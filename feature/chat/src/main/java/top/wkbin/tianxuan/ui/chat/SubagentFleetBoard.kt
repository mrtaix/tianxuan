package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import top.wkbin.tianxuan.feature.chat.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import top.wkbin.tianxuan.harness.session.ConversationBranch

/**
 * Bảng agent con chạy song song: hiển thị trạng thái thời gian thực của các lane trong phiên.
 * Cho biết agent nào đang chạy, gặp lỗi và số bước; chạm một dòng để mở kết quả của lane.
 *
 * Dữ liệu từ [ConversationBranch] (kind = SUBAGENT): isBusy/isFaulted/toolCallCount
 * đã được LaneManager tổng hợp, không cần thêm trường ở backend.
 */
@Composable
internal fun SubagentFleetBoard(
    subagents: List<ConversationBranch>,
    onOpen: (ConversationBranch) -> Unit,
) {
    val running = subagents.count { it.isBusy }
    val faulted = subagents.count { it.faulted && !it.isBusy }
    val idle = subagents.size - running - faulted

    Column(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Text(stringResource(R.string.chat_fleet_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                FleetStat(stringResource(R.string.chat_fleet_running_count, running), MaterialTheme.colorScheme.primary)
                FleetStat(stringResource(R.string.chat_fleet_faulted_count, faulted), MaterialTheme.colorScheme.error)
                FleetStat(stringResource(R.string.chat_fleet_other_count, idle), MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        subagents.forEach { branch ->
            val (label, color) = when {
                branch.isBusy -> stringResource(R.string.chat_fleet_running) to MaterialTheme.colorScheme.primary
                branch.faulted -> stringResource(R.string.chat_fleet_faulted) to MaterialTheme.colorScheme.error
                else -> stringResource(R.string.chat_fleet_idle) to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    // Chỉ mở transcript chỉ đọc khi định vị được durable lane.
                    .clickable(enabled = branch.laneName != null) { onOpen(branch) }
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                Spacer(Modifier.width(8.dp))
                Text(
                    branch.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (branch.toolCallCount > 0) {
                    Text(
                        stringResource(R.string.chat_fleet_steps, branch.toolCallCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = color)
            }
        }
    }
}

@Composable
private fun FleetStat(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier.padding(start = 6.dp),
    )
}
