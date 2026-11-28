package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar

/**
 * Trang con cấp 2: hệ sinh thái agent và mô hình AI.
 */
@Composable
fun AgentEcoSettingsScreen(
    onBack: () -> Unit,
    onOpenModelProfiles: () -> Unit,
    onOpenLocalLlm: () -> Unit,
    onOpenToolCenter: () -> Unit,
    onOpenAgentSettings: () -> Unit,
    onOpenSubagentSettings: () -> Unit,
    onOpenSkillSettings: () -> Unit,
    onOpenMcpSettings: () -> Unit,
    onOpenQuickPhrases: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenCcSwitch: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val models by viewModel.models.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val phrases by viewModel.quickPhrases.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { RuntimeTopBar("Agent và mô hình", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "Hồ sơ mô hình và nhà cung cấp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Model,
                        title = "Quản lý hồ sơ mô hình",
                        subtitle = "Cấu hình khóa và điểm cuối cho OpenAI / DeepSeek / Claude / mô hình cục bộ",
                        value = if (models.isEmpty()) "Chưa cấu hình" else "${models.size} mô hình",
                        onClick = onOpenModelProfiles,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Cpu,
                        title = "LLM cục bộ",
                        subtitle = "Nhập hoặc tải GGUF để suy luận ngoại tuyến bằng llama.cpp trên thiết bị ARM64",
                        onClick = onOpenLocalLlm,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Chat,
                        title = "Cụm từ nhanh và lệnh thường dùng",
                        subtitle = "Tùy chỉnh thẻ bắt đầu nhanh và mẫu prompt thường dùng trên trang trò chuyện trống",
                        value = "${phrases.count { it.isEnabled }} mục đang bật",
                        onClick = onOpenQuickPhrases,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Speed,
                        title = "Thống kê và phân tích mức sử dụng",
                        subtitle = "Lượng token tiêu thụ, bản đồ nhiệt hoạt động, xếp hạng mô hình và chủ đề",
                        onClick = onOpenStats,
                    )
                }
            }

            item {
                Text(
                    text = "Hệ sinh thái công cụ và tiện ích",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Wrench,
                        title = "Trung tâm tiện ích và công cụ nền tảng",
                        subtitle = "Cài đặt llama.cpp, QEMU và môi trường phát triển nền tảng",
                        onClick = onOpenToolCenter,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Sparkles,
                        title = "Trung tâm agent (CC-Switch)",
                        subtitle = "Quản lý phiên bản Claude Code, OpenClaw, Hermes và chuyển nóng giữa các nguồn mô hình",
                        onClick = onOpenCcSwitch,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Bot, title = "Thực thi agent và ngữ cảnh", subtitle = "Luồng suy nghĩ, nén ngữ cảnh, giới hạn gọi công cụ và prompt hệ thống", onClick = onOpenAgentSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Bot, title = "Vai trò agent con", subtitle = "Chính sách ủy nhiệm tự động và các vai trò agent con khả dụng", onClick = onOpenSubagentSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Sparkles, title = "Skill và tiện ích", subtitle = "Quản lý prompt Skill, gói script và tiện ích môi trường chạy", value = "${skills.count { it.isEnabled }} skill", onClick = onOpenSkillSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Network,
                        title = "Hệ sinh thái và dịch vụ MCP",
                        subtitle = "Quản lý các dịch vụ Model Context Protocol như SQLite, Git và Fetch",
                        onClick = onOpenMcpSettings,
                    )
                }
            }
        }
    }
}
