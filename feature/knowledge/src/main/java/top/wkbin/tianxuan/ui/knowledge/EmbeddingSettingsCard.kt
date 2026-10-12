package top.wkbin.tianxuan.ui.knowledge

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 嵌入配置卡片：模型名与端点后缀（适配不支持默认 text-embedding-3-small 的提供商）。 */
@Composable
fun EmbeddingSettingsCard(
    embeddingModel: String,
    embeddingEndpointSuffix: String,
    onModelChange: (String) -> Unit,
    onSuffixChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Cấu hình nhúng", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = embeddingModel,
                onValueChange = onModelChange,
                label = { Text("Tên model nhúng") },
                placeholder = { Text("text-embedding-3-small") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = embeddingEndpointSuffix,
                onValueChange = onSuffixChange,
                label = { Text("Hậu tố endpoint nhúng") },
                placeholder = { Text("/embeddings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Yêu cầu nhúng đi qua Base URL + Key của hồ sơ model đã kích hoạt cùng tên model này và hậu tố endpoint." +
                    "Nếu 404/NOT_FOUND, nghĩa là nhà cung cấp của bạn không cung cấp endpoint này hoặc tên model sai, hãy đổi sang tên thực sự được hỗ trợ." +
                    "Khi nhúng thất bại tự động hạ cấp xuống tìm kiếm từ khóa (BM25), kho tri thức vẫn khả dụng.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
