package top.wkbin.tianxuan.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName

/**
 * Mục "Tính năng mới": gồm các công tắc của tính năng đã dùng được ngay,
 * và danh sách tính năng sẽ bổ sung sau này.
 */
private data class RoadmapItem(
    val icon: RuntimeIconName,
    val title: String,
    val description: String,
)

private val upcomingFeatures = listOf(
    RoadmapItem(
        icon = RuntimeIconName.Github,
        title = "Sửa tính năng tạo nhánh (fork)",
        description = "Khắc phục lỗi tạo nhánh hiện tại để thao tác fork repository hoạt động bình thường.",
    ),
    RoadmapItem(
        icon = RuntimeIconName.Key,
        title = "Đăng nhập xác thực GitHub, ChatGPT, Antigravity, Claude, OpenCode",
        description = "Bổ sung luồng đăng nhập/ủy quyền cho các nhà cung cấp và công cụ AI phổ biến ngay trong ứng dụng.",
    ),
    RoadmapItem(
        icon = RuntimeIconName.Extension,
        title = "Tìm kiếm plugin từ GitHub/GitLab để cài đặt",
        description = "Tìm plugin từ GitHub và GitLab, tự động phát hiện bản cập nhật, kèm nút bật/tắt plugin.",
    ),
    RoadmapItem(
        icon = RuntimeIconName.Info,
        title = "Tính năng /btw (câu hỏi phụ)",
        description = "Đặt câu hỏi phụ về công việc hiện tại mà không ghi vào lịch sử hội thoại; chạy độc lập, không làm gián đoạn agent đang xử lý và chỉ trả lời từ ngữ cảnh đã có (không gọi công cụ).",
    ),
    RoadmapItem(
        icon = RuntimeIconName.Bot,
        title = "Rà soát lại tính năng subagent",
        description = "Kiểm tra và sửa lỗi được báo cáo ở cơ chế ủy quyền cho agent con.",
    ),
    RoadmapItem(
        icon = RuntimeIconName.Search,
        title = "Sửa và nâng cấp tính năng RAG",
        description = "Cải thiện độ chính xác truy xuất và khả năng bổ sung ngữ cảnh của kho tri thức RAG.",
    ),
    RoadmapItem(
        icon = RuntimeIconName.FolderDownload,
        title = "Sao lưu trò chuyện và cài đặt",
        description = "Xuất và nhập dữ liệu để sao lưu lịch sử hội thoại cùng cấu hình ứng dụng.",
    ),
)

@Composable
fun RoadmapSettingsDialog(
    queueSendAfterToolBatch: Boolean,
    onQueueSendAfterToolBatchChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(
                    RuntimeIconName.Update,
                    Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text("Tính năng mới", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Đã dùng được ngay",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RuntimeIcon(
                        RuntimeIconName.Chat,
                        Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Gửi tin nhắn hàng đợi ngay khi lệnh xong",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            if (queueSendAfterToolBatch) {
                                "Đang bật: tin nhắn xếp hàng được nạp ngay sau mỗi loạt lệnh, không phải đợi toàn bộ tác vụ kết thúc."
                            } else {
                                "Đang tắt: chỉ nạp tin nhắn chờ khi agent trả lời xong toàn bộ lượt (chế độ mặc định)."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = queueSendAfterToolBatch,
                        onCheckedChange = onQueueSendAfterToolBatchChange,
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "Sẽ bổ sung sau",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                upcomingFeatures.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RuntimeIcon(
                            item.icon,
                            Modifier.size(20.dp).padding(top = 2.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${index + 1}. ${item.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                item.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Đóng") }
        },
    )
}
