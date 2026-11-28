package top.wkbin.tianxuan.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName

/**
 * Hộp thoại riêng cho câu hỏi phụ "/btw".
 *
 * Câu trả lời **không** đi vào lịch sử hội thoại: chỉ hiện trong hộp thoại này cho tới khi
 * người dùng đóng, nên nó không làm nhiễu ngữ cảnh của lượt đang chạy.
 */
@Composable
fun BtwAnswerDialog(
    answer: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuntimeIcon(
                    RuntimeIconName.Info,
                    Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "Câu hỏi phụ /btw",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    if (isLoading) "Đang trả lời…" else answer,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Câu trả lời này không được ghi vào lịch sử hội thoại và không làm gián đoạn agent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(end = 4.dp)) {
                if (!isLoading) {
                    TextButton(onClick = { clipboard.setText(AnnotatedString(answer)) }) { Text("Sao chép") }
                }
                TextButton(onClick = onDismiss) { Text("Đóng") }
            }
        },
    )
}
