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
 * 二级子页 1：智能体与 AI 模型生态
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
        topBar = { RuntimeTopBar("智能体与模型", onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = "模型档案与提供商",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Model,
                        title = "模型档案管理",
                        subtitle = "配置 OpenAI / DeepSeek / Claude / 本地大模型密钥与端点",
                        value = if (models.isEmpty()) "未配置" else "${models.size} model",
                        onClick = onOpenModelProfiles,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Cpu,
                        title = "本地 LLM",
                        subtitle = "导入或下载 GGUF，在 ARM64 设备端通过 llama.cpp 离线推理",
                        onClick = onOpenLocalLlm,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Chat,
                        title = "Cụm từ nhanh và lệnh thường dùng",
                        subtitle = "Thẻ bắt đầu nhanh trang trắng Tri Khu tùy chỉnh và mẫu prompt tần suất cao",
                        value = "${phrases.count { it.isEnabled }} mục đã bật",
                        onClick = onOpenQuickPhrases,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Speed,
                        title = "Thống kê dữ liệu và phân tích mức dùng",
                        subtitle = "Tiêu thụ Token, heatmap mức độ hoạt động, bảng xếp hạng model và chủ đề",
                        onClick = onOpenStats,
                    )
                }
            }

            item {
                Text(
                    text = "工具与插件生态",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
                SettingsGroup {
                    SettingsRow(
                        icon = RuntimeIconName.Wrench,
                        title = "Trung tâm plugin và công cụ tầng nền",
                        subtitle = "Cài đặt llama.cpp, QEMU và môi trường phát triển tầng thấp",
                        onClick = onOpenToolCenter,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Sparkles,
                        title = "Trung tâm Agent (CC-Switch)",
                        subtitle = "Quản lý thống nhất phiên bản Claude Code, OpenClaw, Hermes và chuyển nguồn model nóng đa nguồn",
                        onClick = onOpenCcSwitch,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Bot, title = "Thực thi Agent và ngữ cảnh", subtitle = "Luồng suy nghĩ, nén context, giới hạn gọi công cụ và prompt hệ thống", onClick = onOpenAgentSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Bot, title = "Vai trò Agent con", subtitle = "Chính sách ủy thác tự động và các vai trò Agent con khả dụng", onClick = onOpenSubagentSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(icon = RuntimeIconName.Sparkles, title = "Skills và plugin", subtitle = "Quản lý prompt Skill, gói script và plugin runtime", value = "${skills.count { it.isEnabled }} skill", onClick = onOpenSkillSettings)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsRow(
                        icon = RuntimeIconName.Network,
                        title = "MCP 协议生态与服务",
                        subtitle = "管理 SQLite、Git、Fetch 等 Model Context Protocol 协议服务",
                        onClick = onOpenMcpSettings,
                    )
                }
            }
        }
    }
}
