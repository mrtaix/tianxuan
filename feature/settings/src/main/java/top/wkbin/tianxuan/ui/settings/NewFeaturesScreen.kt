package top.wkbin.tianxuan.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName

/**
 * Màn hình "Tính năng mới": gồm các tính năng đã dùng được (có công tắc/lối vào riêng)
 * và danh sách tính năng dự kiến bổ sung. Không dùng dialog để chứa được nhiều mục
 * với nhiều dạng điều khiển khác nhau về sau.
 */
private data class UpcomingFeature(
    val icon: RuntimeIconName,
    val title: String,
    val description: String,
)

private val upcomingFeatures = listOf(
    UpcomingFeature(
        RuntimeIconName.Github,
        "Sửa tính năng tạo nhánh (fork)",
        "Khắc phục lỗi tạo nhánh để thao tác fork repository hoạt động bình thường.",
    ),
    UpcomingFeature(
        RuntimeIconName.Key,
        "Đăng nhập GitHub, ChatGPT, Antigravity, Claude, OpenCode",
        "Bổ sung luồng đăng nhập/ủy quyền cho các nhà cung cấp và công cụ AI phổ biến ngay trong ứng dụng.",
    ),
    UpcomingFeature(
        RuntimeIconName.Extension,
        "Tìm kiếm plugin từ GitHub/GitLab",
        "Tìm plugin từ GitHub và GitLab, tự động phát hiện bản cập nhật, kèm nút bật/tắt plugin.",
    ),
    UpcomingFeature(
        RuntimeIconName.Info,
        "Tính năng /btw (câu hỏi phụ)",
        "Đặt câu hỏi phụ ngay khi agent đang chạy: chạy độc lập, không làm gián đoạn, không ghi vào lịch sử hội thoại và chỉ trả lời từ ngữ cảnh đã có.",
    ),
    UpcomingFeature(
        RuntimeIconName.Bot,
        "Rà soát lại tính năng subagent",
        "Kiểm tra và sửa lỗi được báo cáo ở cơ chế ủy quyền cho agent con.",
    ),
    UpcomingFeature(
        RuntimeIconName.Search,
        "Sửa và nâng cấp tính năng RAG",
        "Cải thiện độ chính xác truy xuất và khả năng bổ sung ngữ cảnh của kho tri thức RAG.",
    ),
    UpcomingFeature(
        RuntimeIconName.FolderDownload,
        "Sao lưu trò chuyện và cài đặt",
        "Xuất và nhập dữ liệu để sao lưu lịch sử hội thoại cùng cấu hình ứng dụng.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewFeaturesScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val queueSendAfterToolBatch by viewModel.queueSendAfterToolBatch.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tính năng mới") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        RuntimeIcon(RuntimeIconName.Back, Modifier.size(22.dp))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Đã dùng được",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        RuntimeIcon(
                            RuntimeIconName.Chat,
                            Modifier.size(22.dp),
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
                                    "Đang bật: tin nhắn bạn gửi khi agent đang chạy được nạp ngay sau mỗi loạt lệnh."
                                } else {
                                    "Đang tắt: tin nhắn chỉ được nạp khi agent trả lời xong toàn bộ lượt."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = queueSendAfterToolBatch,
                            onCheckedChange = viewModel::setQueueSendAfterToolBatch,
                        )
                    }
                }
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            item {
                Text(
                    "Sẽ bổ sung sau",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(upcomingFeatures) { feature ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RuntimeIcon(
                        feature.icon,
                        Modifier.size(20.dp).padding(top = 2.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            feature.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            feature.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
