package top.wkbin.tianxuan.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

/**
 * Màn hình "Tính năng mới" kèm logic xuất/nhập bản sao lưu qua SAF.
 * Tách khỏi NavHost để giữ sơ đồ điều hướng gọn và không vượt giới hạn số dòng.
 */
@Composable
fun NewFeaturesRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val payload = runCatching { viewModel.exportBackup() }
                status = payload.fold(
                    onSuccess = { json ->
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                                ?: error("không mở được tệp đích")
                        }.fold({ "Đã xuất bản sao lưu." }, { "Xuất thất bại: ${it.message}" })
                    },
                    onFailure = { "Xuất thất bại: ${it.message}" },
                )
                busy = false
            }
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                val raw = runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
                status = if (raw == null) {
                    "Không đọc được tệp đã chọn"
                } else {
                    viewModel.importBackup(raw).fold(
                        { r -> "Đã nhập ${r.sessions} phiên, ${r.entries} tin nhắn." },
                        { "Nhập thất bại: ${it.message}" },
                    )
                }
                busy = false
            }
        }
    }

    NewFeaturesScreen(
        onBack = onBack,
        backupStatus = status,
        backupBusy = busy,
        onExportBackup = { exporter.launch("ctharness-backup.json") },
        onImportBackup = { importer.launch(arrayOf("application/json", "*/*")) },
        viewModel = viewModel,
    )
}
