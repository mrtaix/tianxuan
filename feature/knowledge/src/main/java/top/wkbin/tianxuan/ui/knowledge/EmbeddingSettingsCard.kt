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

/** Thẻ cấu hình embedding: tên mô hình và hậu tố endpoint (dành cho nhà cung cấp không hỗ trợ mặc định text-embedding-3-small). */
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
            Text("Cấu hình embedding", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = embeddingModel,
                onValueChange = onModelChange,
                label = { Text("Tên mô hình embedding") },
                placeholder = { Text("text-embedding-3-small") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = embeddingEndpointSuffix,
                onValueChange = onSuffixChange,
                label = { Text("Hậu tố endpoint embedding") },
                placeholder = { Text("/embeddings") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Yêu cầu embedding dùng Base URL + Key của hồ sơ mô hình đang kích hoạt, cùng tên mô hình và hậu tố endpoint này. " +
                    "Nếu gặp 404/NOT_FOUND, nhà cung cấp có thể không hỗ trợ endpoint này hoặc tên mô hình chưa đúng; hãy đổi sang tên được hỗ trợ. " +
                    "Nếu embedding thất bại, hệ thống tự chuyển sang tìm kiếm từ khóa (BM25); kho tri thức vẫn hoạt động.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
