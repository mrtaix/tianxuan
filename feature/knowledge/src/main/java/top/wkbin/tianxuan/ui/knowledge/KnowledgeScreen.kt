package top.wkbin.tianxuan.ui.knowledge

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.core.database.KbDocumentEntity
import top.wkbin.tianxuan.harness.knowledge.KnowledgeHit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeScreen(
    onBack: () -> Unit,
    viewModel: KnowledgeViewModel = koinViewModel(),
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val adding by viewModel.adding.collectAsStateWithLifecycle()
    val reEmbedding by viewModel.reEmbedding.collectAsStateWithLifecycle()
    val embeddingModel by viewModel.embeddingModel.collectAsStateWithLifecycle()
    val embeddingEndpointSuffix by viewModel.embeddingEndpointSuffix.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<KbDocumentEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kho tri thức") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Quay lại")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Thêm tài liệu")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SearchSection(
                query = searchQuery,
                onQueryChange = viewModel::search,
                results = searchResults,
                searching = searching,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            EmbeddingSettingsCard(
                embeddingModel = embeddingModel,
                embeddingEndpointSuffix = embeddingEndpointSuffix,
                onModelChange = viewModel::setEmbeddingModel,
                onSuffixChange = viewModel::setEmbeddingEndpointSuffix,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Text(
                "Danh sách tài liệu (${documents.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            if (documents.isEmpty()) {
                EmptyState(modifier = Modifier.padding(horizontal = 16.dp))
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp),
                ) {
                    documents.forEach { doc ->
                        DocumentCard(
                            doc = doc,
                            onDelete = { showDeleteConfirm = doc },
                            onReEmbed = { viewModel.reEmbed(doc.id) },
                            isReEmbedding = reEmbedding == doc.id,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showAddDialog) {
        AddDocumentDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, content ->
                viewModel.addDocument(name, content)
                showAddDialog = false
            },
            isLoading = adding,
        )
    }

    showDeleteConfirm?.let { doc ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Xóa tài liệu") },
            text = { Text("Bạn có chắc muốn xóa「${doc.name}」không? ${doc.chunkCount} đoạn sẽ bị xóa cùng lúc.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeDocument(doc.id)
                        showDeleteConfirm = null
                    },
                ) { Text("Xóa") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) { Text("Hủy") }
            },
        )
    }

    error?.let { msg ->
        Snackbar(
            action = {
                TextButton(onClick = { viewModel.clearError() }) { Text("Đóng") }
            },
        ) { Text(msg) }
    }
}

@Composable
private fun SearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<KnowledgeHit>,
    searching: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Kiểm tra tìm kiếm", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Nhập nội dung để tìm các đoạn tài liệu liên quan…") },
                trailingIcon = {
                    if (searching) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Filled.Search, contentDescription = "Tìm kiếm")
                    }
                },
                singleLine = true,
            )
            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                results.forEach { hit ->
                    HitCard(hit)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun HitCard(hit: KnowledgeHit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    hit.docName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Badge { Text("%.2f".format(hit.score)) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                hit.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DocumentCard(
    doc: KbDocumentEntity,
    onDelete: () -> Unit,
    onReEmbed: () -> Unit,
    isReEmbedding: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    doc.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusBadge(doc.status)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Nguồn: ${doc.source}  |  Số đoạn: ${doc.chunkCount}  |  Thêm lúc ${formatTime(doc.createdAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!doc.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    doc.errorMessage!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                IconButton(onClick = onReEmbed) {
                    if (isReEmbedding) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = "Tạo lại embedding")
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Xóa",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val (text, color) = when (status) {
        "embedded" -> "Đã tạo embedding" to MaterialTheme.colorScheme.primary
        "embedding" -> "Đang tạo embedding…" to MaterialTheme.colorScheme.tertiary
        "partial" -> "Tạo embedding một phần" to MaterialTheme.colorScheme.secondary
        "error" -> "Lỗi" to MaterialTheme.colorScheme.error
        else -> "Đang chờ xử lý" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Badge(containerColor = color) { Text(text) }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Chưa có tài liệu nào trong kho tri thức",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Nhấn dấu + ở góc trên bên phải để thêm tài liệu. Hệ thống sẽ tự động gọi API embedding để tạo vector. Khi tìm kiếm, nội dung liên quan sẽ được tìm theo độ tương đồng ngữ nghĩa và đưa vào ngữ cảnh hội thoại.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddDocumentDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, content: String) -> Unit,
    isLoading: Boolean,
) = AddDocumentDialogImpl(onDismiss, onAdd, isLoading)

private fun formatTime(timestamp: Long): String {
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
