package top.wkbin.tianxuan.ui.settings

import org.koin.compose.viewmodel.koinViewModel
import top.wkbin.tianxuan.ui.components.RuntimeAlertDialog
import top.wkbin.tianxuan.ui.components.RuntimeCircularProgressIndicator as CircularProgressIndicator

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import top.wkbin.tianxuan.ui.components.RuntimeButton as Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.HorizontalDivider
import top.wkbin.tianxuan.ui.components.RuntimeIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import top.wkbin.tianxuan.ui.components.RuntimeOutlinedButton as OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import top.wkbin.tianxuan.ui.components.RuntimeSlider as Slider
import androidx.compose.material3.Surface
import top.wkbin.tianxuan.ui.components.RuntimeSwitch as Switch
import androidx.compose.material3.SwitchDefaults
import top.wkbin.tianxuan.ui.settings.LocalizedText as Text
import top.wkbin.tianxuan.ui.components.RuntimeTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import top.wkbin.tianxuan.core.model.AgentPlugin
import top.wkbin.tianxuan.harness.ContextWindowPolicy
import top.wkbin.tianxuan.core.database.AiModelEntity
import top.wkbin.tianxuan.core.model.AgentSkill
import top.wkbin.tianxuan.core.model.AgentSubagent
import top.wkbin.tianxuan.core.model.AgentDepartments
import top.wkbin.tianxuan.core.model.ApprovalMode
import top.wkbin.tianxuan.core.model.RunMode
import top.wkbin.tianxuan.ui.components.RuntimeCard
import top.wkbin.tianxuan.ui.components.RuntimeIcon
import top.wkbin.tianxuan.ui.components.RuntimeIconName
import top.wkbin.tianxuan.ui.components.RuntimeTopBar
import top.wkbin.tianxuan.ui.components.SectionHeader

enum class AgentSettingsCategory { EXECUTION, SUBAGENTS, SKILLS }

@Composable
fun AgentSettingsScreen(
    onBack: () -> Unit,
    category: AgentSettingsCategory = AgentSettingsCategory.EXECUTION,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val thinkingExpanded by viewModel.thinkingExpanded.collectAsStateWithLifecycle()
    val translationModelStatus by viewModel.translationModelStatus.collectAsStateWithLifecycle()
    val thinkingAutoTranslate by viewModel.thinkingAutoTranslate.collectAsStateWithLifecycle()
    val chatRoundCollapse by viewModel.chatRoundCollapse.collectAsStateWithLifecycle()
    val queueSendAfterToolBatch by viewModel.queueSendAfterToolBatch.collectAsStateWithLifecycle()
    val customSystemPromptEnabled by viewModel.customSystemPromptEnabled.collectAsStateWithLifecycle()
    val customSystemPrompt by viewModel.customSystemPrompt.collectAsStateWithLifecycle()
    val agentCharName by viewModel.agentCharName.collectAsStateWithLifecycle()
    val agentUserName by viewModel.agentUserName.collectAsStateWithLifecycle()
    val compactionEnabled by viewModel.contextCompactionEnabled.collectAsStateWithLifecycle()
    val maxToolRounds by viewModel.maxToolRounds.collectAsStateWithLifecycle()
    val roundLimitAutoContinuations by viewModel.roundLimitAutoContinuations.collectAsStateWithLifecycle()
    val autoWorkspaceCwd by viewModel.autoWorkspaceCwd.collectAsStateWithLifecycle()
    val commandOutputCompressionEnabled by viewModel.commandOutputCompressionEnabled.collectAsStateWithLifecycle()
    val baseCommandTimeoutSeconds by viewModel.baseCommandTimeoutSeconds.collectAsStateWithLifecycle()
    val approvalMode by viewModel.approvalMode.collectAsStateWithLifecycle()
    val runMode by viewModel.runMode.collectAsStateWithLifecycle()
    val maxToolsPerRound by viewModel.maxToolsPerRound.collectAsStateWithLifecycle()
    val maxConsecutiveFailures by viewModel.maxConsecutiveFailures.collectAsStateWithLifecycle()
    val contextBudgetTokens by viewModel.contextBudgetTokens.collectAsStateWithLifecycle()
    val effectiveContextBudget by viewModel.effectiveContextBudget.collectAsStateWithLifecycle()
    val activeModelDeclaredTokens by viewModel.activeModelDeclaredTokens.collectAsStateWithLifecycle()
    val contextFoldingRatioPercent by viewModel.contextFoldingRatioPercent.collectAsStateWithLifecycle()
    val skills by viewModel.allSkills.collectAsStateWithLifecycle()
    val subagents by viewModel.allSubagents.collectAsStateWithLifecycle()
    val autoSubagentDelegation by viewModel.autoSubagentDelegationEnabled.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val plugins by viewModel.allPlugins.collectAsStateWithLifecycle()
    val skillArchiveMessage by viewModel.skillArchiveMessage.collectAsStateWithLifecycle()
    val skillArchiveMessageIsError by viewModel.skillArchiveMessageIsError.collectAsStateWithLifecycle()
    val skillEvolutionSuggestions by viewModel.skillEvolutionSuggestions.collectAsStateWithLifecycle()
    val marketSkills by viewModel.clawHubMarketSkills.collectAsStateWithLifecycle()
    val isMarketLoading by viewModel.isMarketLoading.collectAsStateWithLifecycle()
    val isMarketOfflinePreset by viewModel.isMarketOfflinePreset.collectAsStateWithLifecycle()
    val pendingSkillInspection by viewModel.pendingSkillInspection.collectAsStateWithLifecycle()
    val preparingSkillId by viewModel.preparingSkillId.collectAsStateWithLifecycle()
    val isCommittingInstallation by viewModel.isCommittingInstallation.collectAsStateWithLifecycle()

    var showAddSkillDialog by remember { mutableStateOf(false) }
    var viewingSkillPrompt by remember { mutableStateOf<AgentSkill?>(null) }
    var editingSubagent by remember { mutableStateOf<AgentSubagent?>(null) }
    var showSubagentDialog by remember { mutableStateOf(false) }
    var deletingSubagent by remember { mutableStateOf<AgentSubagent?>(null) }
    var deletingSkill by remember { mutableStateOf<AgentSkill?>(null) }
    var subagentQuery by rememberSaveable { mutableStateOf("") }
    var skillQuery by rememberSaveable { mutableStateOf("") }
    var expandedDepartmentIds by rememberSaveable { mutableStateOf(listOf<String>()) }
    val visibleSubagentGroups = remember(subagents, subagentQuery) {
        val query = subagentQuery.trim().lowercase()
        subagents.asSequence()
            .filter { profile ->
                query.isBlank() || profile.name.lowercase().contains(query) ||
                    profile.id.lowercase().contains(query) || profile.description.lowercase().contains(query)
            }
            .groupBy { it.departmentId }
            .entries
            .sortedBy { AgentDepartments.find(it.key).sortOrder }
    }
    val visibleSkills = remember(skills, skillQuery) {
        val query = skillQuery.trim().lowercase()
        if (query.isBlank()) {
            skills
        } else {
            skills.filter { skill ->
                skill.name.lowercase().contains(query) || skill.id.lowercase().contains(query) ||
                    skill.description.lowercase().contains(query) || skill.category.lowercase().contains(query)
            }
        }
    }
    val skillArchivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.size == 1) {
            viewModel.inspectLocalSkillZip(uris.first())
        } else if (uris.isNotEmpty()) {
            viewModel.importSkillArchives(uris)
        }
    }
    val skillDirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> if (uri != null) viewModel.importSkillsFromTree(uri) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RuntimeTopBar(
                title = when (category) {
                    AgentSettingsCategory.EXECUTION -> "Thực thi và ngữ cảnh agent"
                    AgentSettingsCategory.SUBAGENTS -> "Vai trò agent con"
                    AgentSettingsCategory.SKILLS -> "Skills và plugin"
                },
                statusText = "Quản lý năng lực agent theo từng lĩnh vực",
                onBack = onBack,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (category == AgentSettingsCategory.EXECUTION) {
                        item {
                SectionHeader(
                    title = "Luồng suy nghĩ và thực thi",
                    subtitle = "Điều chỉnh cách hiển thị suy nghĩ của mô hình suy luận như DeepSeek và giới hạn thực thi công cụ",
                )
            }
            item {
                AgentBlockTitle("Hiển thị và dịch suy nghĩ")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Brain,
                        title = "Mặc định mở rộng suy nghĩ của mô hình",
                        subtitle = if (thinkingExpanded) "Suy nghĩ mới trong cuộc trò chuyện sẽ mặc định được mở rộng" else "Suy nghĩ (kể cả nội dung đang tạo) mặc định được thu gọn; nhấn để xem",
                        checked = thinkingExpanded,
                        onCheckedChange = viewModel::setThinkingExpanded,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentToggleRow(
                        icon = RuntimeIconName.Globe,
                        title = "Tự động dịch suy nghĩ tiếng Anh sang tiếng Trung khi mở rộng",
                        subtitle = if (thinkingAutoTranslate) "Tự dịch suy nghĩ tiếng Anh bằng mô hình ngoại tuyến cục bộ" else "Chỉ dịch khi nhấn nút dịch trên khối suy nghĩ",
                        checked = thinkingAutoTranslate,
                        onCheckedChange = viewModel::setThinkingAutoTranslate,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentToggleRow(
                        icon = RuntimeIconName.Package,
                        title = "Gửi tin nhắn đang chờ ngay khi lệnh thực thi xong",
                        subtitle = if (queueSendAfterToolBatch) "Tin nhắn xếp hàng được nạp ngay sau mỗi loạt lệnh, không phải đợi toàn bộ tác vụ kết thúc" else "Mặc định: chỉ nạp tin nhắn chờ khi agent đã trả lời xong toàn bộ lượt",
                        checked = queueSendAfterToolBatch,
                        onCheckedChange = viewModel::setQueueSendAfterToolBatch,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentToggleRow(
                        icon = RuntimeIconName.Compress,
                        title = "Tự động thu gọn suy nghĩ và công cụ ở các lượt trước",
                        subtitle = if (chatRoundCollapse) "Lượt đã kết thúc: gộp suy nghĩ và lệnh quá 2 bước thành tóm tắt; lượt đang chạy luôn hiện đầy đủ" else "Khi tắt, hiện lần lượt toàn bộ suy nghĩ và lời gọi công cụ",
                        checked = chatRoundCollapse,
                        onCheckedChange = viewModel::setChatRoundCollapse,
                    )
                }
            }
            item {
                AgentBlockTitle("Mô hình ngôn ngữ dịch suy nghĩ ngoại tuyến")
            }
            item {
                AgentSettingsGroup {
                    TranslationModelCard(
                        status = translationModelStatus,
                        onDownload = { viewModel.downloadTranslationModel() },
                        onDelete = { viewModel.deleteTranslationModel() },
                        onRetry = { viewModel.downloadTranslationModel() },
                    )
                }
            }
            item {
                AgentBlockTitle("Ngữ cảnh thực thi")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.FolderOpen,
                        title = "Tự động chèn đường dẫn workspace liên kết",
                        subtitle = "Khi cuộc trò chuyện liên kết với workspace, lệnh base mặc định dùng thư mục đó làm thư mục làm việc (cwd)",
                        checked = autoWorkspaceCwd,
                        onCheckedChange = viewModel::setAutoWorkspaceCwd,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AgentToggleRow(
                        icon = RuntimeIconName.Compress,
                        title = "Nén thông minh đầu ra lệnh của agent",
                        subtitle = "Giảm ngữ cảnh tiêu thụ khi agent đọc git, kết quả tìm kiếm, kiểm thử và nhật ký build; không ảnh hưởng terminal và tệp gốc",
                        checked = commandOutputCompressionEnabled,
                        onCheckedChange = viewModel::setCommandOutputCompressionEnabled,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ApprovalModeSelectorRow(
                        mode = approvalMode,
                        onModeChange = viewModel::setApprovalMode,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RunModeSelectorRow(
                        mode = runMode,
                        onModeChange = viewModel::setRunMode,
                    )
                }
            }
            item {
                AgentBlockTitle("Giới hạn gọi công cụ")
            }
            item {
                AgentSettingsGroup {
                    BaseCommandTimeoutSliderRow(
                        currentValue = baseCommandTimeoutSeconds,
                        onValueChange = viewModel::setBaseCommandTimeoutSeconds,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    RoundsSliderRow(
                        currentValue = maxToolRounds,
                        onValueChange = viewModel::setMaxToolRounds,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    AutoContinuationSliderRow(
                        currentValue = roundLimitAutoContinuations,
                        roundsPerSegment = maxToolRounds,
                        onValueChange = viewModel::setRoundLimitAutoContinuations,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ToolsPerRoundSliderRow(
                        currentValue = maxToolsPerRound,
                        onValueChange = viewModel::setMaxToolsPerRound,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    ConsecutiveFailuresSliderRow(
                        currentValue = maxConsecutiveFailures,
                        onValueChange = viewModel::setMaxConsecutiveFailures,
                    )
                }
            }

                        item {
                SectionHeader(
                    title = "Tùy chỉnh prompt hệ thống và nhân vật",
                    subtitle = "Tùy chỉnh nhân vật ban đầu của agent, mô tả môi trường và chèn biến macro động",
                )
            }
            item {
                AgentBlockTitle("Chỉnh sửa prompt")
            }
            item {
                SystemPromptCustomCard(
                    enabled = customSystemPromptEnabled,
                    onEnabledChange = viewModel::setCustomSystemPromptEnabled,
                    prompt = customSystemPrompt,
                    onPromptChange = viewModel::setCustomSystemPrompt,
                    charName = agentCharName,
                    onCharNameChange = viewModel::setAgentCharName,
                    userName = agentUserName,
                    onUserNameChange = viewModel::setAgentUserName,
                )
            }

                        item {
                SectionHeader(
                    title = "Bộ nhớ ngữ cảnh và nén thông minh",
                    subtitle = "Tối ưu token cho tác vụ dài, tránh vượt giới hạn cửa sổ ngữ cảnh của mô hình",
                )
            }
            item {
                AgentBlockTitle("Chiến lược nén")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Compress,
                        title = "Bật nén ngữ cảnh thông minh (Context Compaction)",
                        subtitle = "Tự động tạo tóm tắt có cấu trúc khi lịch sử vượt ngưỡng token; giữ lại nội dung gốc gần đây, trạng thái tác vụ và dấu vết tệp quan trọng",
                        checked = compactionEnabled,
                        onCheckedChange = viewModel::setContextCompactionEnabled,
                    )
                    if (compactionEnabled) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ContextBudgetSliderRow(
                            currentValue = contextBudgetTokens,
                            declaredTokens = activeModelDeclaredTokens,
                            onValueChange = viewModel::setContextBudgetTokens,
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        ContextFoldingRatioSliderRow(
                            currentValue = contextFoldingRatioPercent,
                            budget = effectiveContextBudget,
                            declaredTokens = activeModelDeclaredTokens,
                            onValueChange = viewModel::setContextFoldingRatioPercent,
                        )
                    }
                }
            }

            }

            if (category == AgentSettingsCategory.SUBAGENTS) {
                        item {
                SectionHeader(
                    title = "Agent phát triển phần mềm (${subagents.count { it.isEnabled }}/${subagents.size} đã bật)",
                    subtitle = "Agency Agents · 9 bộ phận R&D · Giấy phép MIT · Tích hợp đầy đủ prompt ngoại tuyến",
                )
            }
            item {
                AgentBlockTitle("Chính sách ủy phái tự động")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Bot,
                        title = "Tự động đánh giá và chia nhỏ nhiệm vụ",
                        subtitle = if (autoSubagentDelegation) {
                            "Agent chính tự quyết định việc ủy phái với nhiệm vụ phức tạp có thể chạy song song"
                        } else {
                            "Chỉ ủy phái khi người dùng yêu cầu rõ ràng việc chạy song song hoặc phối hợp agent con"
                        },
                        checked = autoSubagentDelegation,
                        onCheckedChange = viewModel::setAutoSubagentDelegationEnabled,
                    )
                }
            }
            item {
                AgentBlockTitle("Danh sách vai trò")
            }
            item {
                OutlinedTextField(
                    value = subagentQuery,
                    onValueChange = { subagentQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tìm Agent") },
                    placeholder = { Text("Tên, mã vai trò hoặc nhiệm vụ") },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Search, Modifier.size(18.dp)) },
                    singleLine = true,
                )
            }
            item {
                Button(
                    onClick = {
                        editingSubagent = null
                        showSubagentDialog = true
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.Plus, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Thêm vai trò agent con", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }
            visibleSubagentGroups.forEach { (departmentId, profiles) ->
                val expanded = subagentQuery.isNotBlank() || departmentId in expandedDepartmentIds
                item(key = "department:$departmentId") {
                    AgentDepartmentCard(
                        departmentId = departmentId,
                        enabledCount = profiles.count { it.isEnabled },
                        agentCount = profiles.size,
                        expanded = expanded,
                        onClick = {
                            if (subagentQuery.isBlank()) {
                                expandedDepartmentIds = if (expanded) {
                                    expandedDepartmentIds - departmentId
                                } else {
                                    expandedDepartmentIds + departmentId
                                }
                            }
                        },
                    )
                }
                if (expanded) {
                    items(profiles, key = { "subagent:${it.id}" }) { profile ->
                        SubagentProfileCard(
                            profile = profile,
                            modelLabel = profile.defaultModelId?.let { modelId ->
                                models.firstOrNull { it.id == modelId }?.let { model ->
                                    "${model.name} · ${profile.defaultModelVariant ?: model.model.substringBefore(',').trim()}"
                                } ?: "Hồ sơ mô hình đã bị xóa"
                            } ?: "Theo agent chính",
                            onToggle = { enabled -> viewModel.toggleSubagent(profile.id, enabled) },
                            onEdit = {
                                editingSubagent = profile
                                showSubagentDialog = true
                            },
                            onDelete = { deletingSubagent = profile },
                        )
                    }
                }
            }

            }

            if (category == AgentSettingsCategory.SKILLS) {
                        item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SectionHeader(
                        title = "Thư viện Skill chuyên sâu (${skills.count { it.isEnabled }}/${skills.size} đã bật)",
                        subtitle = "Chèn quy chuẩn chuyên môn và hướng dẫn thao tác vào prompt hệ thống của Agent",
                    )
                }
            }
            item {
                AgentBlockTitle("Cải tiến kỹ năng")
            }
            item {
                AgentSettingsGroup {
                    AgentToggleRow(
                        icon = RuntimeIconName.Sparkles,
                        title = "Gợi ý lưu thành kỹ năng hoặc sửa kỹ năng sau cuộc trò chuyện",
                        subtitle = if (skillEvolutionSuggestions) {
                            "Sau khi hoàn thành một lượt có gọi công cụ, có thể gợi ý tạo kỹ năng mới hoặc sửa kỹ năng tùy chỉnh hiện có"
                        } else {
                            "Đã tắt: không hiển thị thẻ gợi ý cải tiến kỹ năng khi cuộc trò chuyện kết thúc"
                        },
                        checked = skillEvolutionSuggestions,
                        onCheckedChange = viewModel::setSkillEvolutionSuggestions,
                    )
                }
            }
            item {
                AgentBlockTitle("Quản lý kỹ năng")
            }
            item {
                OutlinedButton(onClick = { skillArchivePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.FolderDownload, Modifier.size(16.dp))
                        Text("Nhập Skill từ ZIP (hỗ trợ chọn nhiều tệp)")
                    }
                }
            }
            item {
                OutlinedButton(onClick = viewModel::scanSkillDirectories, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.FolderOpen, Modifier.size(16.dp))
                        Text("Quét thư mục Skill để nhập hàng loạt")
                    }
                }
                Text(
                    text = "Có thể sao chép toàn bộ thư mục skills của công cụ bên ngoài vào attachments/skills hoặc thư mục skills trong workspace; khởi động lại ứng dụng để tự động nhập hàng loạt. Nếu sao chép khi ứng dụng đang chạy, nhấn nút phía trên để quét ngay; kỹ năng đã nhập sẽ không bị đăng ký trùng",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            item {
                OutlinedButton(onClick = { skillDirPicker.launch(null) }, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.Search, Modifier.size(16.dp))
                        Text("Nhập từ thư mục tự chọn (vị trí bất kỳ)")
                    }
                }
                Text(
                    text = "Chọn một thư mục bất kỳ trong trình quản lý tệp (hỗ trợ lồng nhau ở mọi độ sâu, kể cả thư mục Skill con nằm trong thư mục Skill); tự động tìm và nhập tất cả thư mục kỹ năng có chứa SKILL.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                )
            }
            item {
                Button(
                    onClick = { showAddSkillDialog = true },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RuntimeIcon(RuntimeIconName.Sparkles, Modifier.size(16.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Thêm Skill tùy chỉnh", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = skillQuery,
                    onValueChange = { skillQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Tìm Skill") },
                    placeholder = { Text("Tên, mã định danh, danh mục hoặc mô tả") },
                    leadingIcon = { RuntimeIcon(RuntimeIconName.Search, Modifier.size(18.dp)) },
                    singleLine = true,
                )
            }

            items(visibleSkills, key = { it.id }) { skill ->
                SkillCard(
                    skill = skill,
                    onToggle = { enabled -> viewModel.toggleSkill(skill.id, enabled) },
                    onViewPrompt = { viewingSkillPrompt = skill },
                    onDelete = if (!skill.isBuiltin) { { deletingSkill = skill } } else null,
                )
            }
            if (visibleSkills.isEmpty()) {
                item {
                    Text(
                        text = "Không có kỹ năng khớp với \"${skillQuery.trim()}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // ---- Mục 4.5: chợ hệ sinh thái kỹ năng ClawHub ----
            item {
                SectionHeader(
                    title = "Chợ kỹ năng ClawHub",
                    subtitle = if (isMarketOfflinePreset) {
                        "Chế độ tuyển chọn ngoại tuyến: dưới đây là các gói tích hợp được tuyển chọn (chưa kết nối chợ từ xa); bộ máy cục bộ vẫn kiểm tra tĩnh về bảo mật và tương thích trước khi cài đặt"
                    } else {
                        "Các gói kỹ năng chuẩn cộng đồng được tuyển chọn; bộ máy cục bộ kiểm tra tĩnh về bảo mật và tương thích trước khi tải"
                    },
                )
            }

            if (isMarketLoading && marketSkills.isEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    }
                }
            } else {
                items(marketSkills, key = { "clawhub_" + it.id }) { marketItem ->
                    ClawHubMarketSkillCard(
                        item = marketItem,
                        isPreparing = preparingSkillId == marketItem.id,
                        onInstall = { viewModel.prepareInstallMarketSkill(marketItem.id) },
                    )
                }
            }

            // ---- Mục 5: quản lý hệ sinh thái plugin ----
            item {
                SectionHeader(
                    title = "Quản lý hệ sinh thái plugin (${plugins.count { it.isEnabled }}/${plugins.size} đang chạy)",
                    subtitle = "Phát hiện môi trường sandbox, chặn thao tác nguy hiểm và mở rộng năng lực",
                )
            }

            item {
                AgentBlockTitle("Plugin đã cài đặt")
            }
            items(plugins, key = { it.id }) { plugin ->
                PluginCard(
                    plugin = plugin,
                    onToggle = { enabled -> viewModel.togglePlugin(plugin.id, enabled) },
                )
            }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    pendingSkillInspection?.let { inspection ->
        top.wkbin.tianxuan.ui.settings.skill.SkillSecurityAuditDialog(
            inspection = inspection,
            isCommitting = isCommittingInstallation,
            onConfirmInstall = viewModel::confirmSkillInstallation,
            onDismiss = viewModel::dismissSkillInspection,
        )
    }

    skillArchiveMessage?.let { message ->
        RuntimeAlertDialog(
            onDismissRequest = viewModel::clearSkillArchiveMessage,
            title = { Text(if (!skillArchiveMessageIsError) "Đã nhập Skill" else "Nhập Skill thất bại", fontWeight = FontWeight.Bold) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearSkillArchiveMessage) { Text("Đã hiểu") } },
        )
    }

    // Hộp thoại xem toàn văn prompt của Skill
    viewingSkillPrompt?.let { skill ->
        RuntimeAlertDialog(
            onDismissRequest = { viewingSkillPrompt = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(skill.name, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Prompt hướng dẫn chèn vào hệ thống (System Prompt):", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            skill.systemPrompt,
                            modifier = Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingSkillPrompt = null }) {
                    Text("Đóng")
                }
            },
        )
    }

    // Hộp thoại thêm Skill tùy chỉnh
    if (showAddSkillDialog) {
        AddSkillDialog(
            onDismiss = { showAddSkillDialog = false },
            onConfirm = { name, desc, prompt, cmd ->
                viewModel.addCustomSkill(name, desc, prompt, cmd)
                showAddSkillDialog = false
            },
        )
    }

    if (showSubagentDialog) {
        SubagentEditorDialog(
            profile = editingSubagent,
            models = models,
            existingIds = subagents.mapTo(mutableSetOf()) { it.id },
            onDismiss = {
                showSubagentDialog = false
                editingSubagent = null
            },
            onConfirm = { roleId, name, description, prompt, defaultModelId, defaultModelVariant ->
                viewModel.saveSubagent(
                    editingSubagent,
                    roleId,
                    name,
                    description,
                    prompt,
                    defaultModelId,
                    defaultModelVariant,
                )
                showSubagentDialog = false
                editingSubagent = null
            },
        )
    }

    deletingSkill?.let { skill ->
        RuntimeAlertDialog(
            onDismissRequest = { deletingSkill = null },
            title = { Text("Xóa Skill", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa “${skill.name}”? Thư mục tài nguyên script cũng sẽ bị xóa và không thể khôi phục.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteCustomSkill(skill.id)
                        deletingSkill = null
                    },
                ) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSkill = null }) { Text("Hủy") }
            },
        )
    }

    deletingSubagent?.let { profile ->
        RuntimeAlertDialog(
            onDismissRequest = { deletingSubagent = null },
            title = { Text("Xóa agent con", fontWeight = FontWeight.Bold) },
            text = { Text("Bạn có chắc muốn xóa “${profile.name}”? Sau khi xóa, vai trò này sẽ không còn được ủy phái nhiệm vụ.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSubagent(profile.id)
                        deletingSubagent = null
                    },
                ) {
                    Text("Xóa", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSubagent = null }) { Text("Hủy") }
            },
        )
    }
}

@Composable
private fun BaseCommandTimeoutSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderMinutes by remember(currentValue) {
        mutableFloatStateOf((currentValue / 60f).coerceIn(1f, 60f))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Thời gian chờ mặc định của lệnh base", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderMinutes.toInt()} phút",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Áp dụng cho lệnh foreground thông thường; mô hình có thể đặt 1–60 phút cho từng lệnh, tác vụ thường trú nên dùng công cụ process",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderMinutes,
            onValueChange = { sliderMinutes = it },
            onValueChangeFinished = { onValueChange(sliderMinutes.toInt() * 60) },
            valueRange = 1f..60f,
            steps = 58,
        )
    }
}

@Composable
private fun AgentSettingsGroup(content: @Composable () -> Unit) {
    RuntimeCard(
        Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
        contentPadding = PaddingValues(0.dp),
    ) {
        Column { content() }
    }
}

@Composable
private fun AgentBlockTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun AgentToggleRow(
    icon: RuntimeIconName,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            RuntimeIcon(icon, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
            ),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ApprovalModeSelectorRow(
    mode: ApprovalMode,
    onModeChange: (ApprovalMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(RuntimeIconName.Shield, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Quyền công cụ toàn cục", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "Thay đổi được áp dụng ngay cho mọi cuộc trò chuyện; lựa chọn riêng ở đầu cuộc trò chuyện sẽ bị ghi đè khi thay đổi toàn cục lần tiếp theo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                ApprovalMode.REQUEST to "Yêu cầu phê duyệt",
                ApprovalMode.ASSISTED to "Phê duyệt giúp tôi",
                ApprovalMode.FULL_ACCESS to "Toàn quyền truy cập",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { onModeChange(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RunModeSelectorRow(
    mode: RunMode,
    onModeChange: (RunMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(RuntimeIconName.Code, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Chế độ chạy mặc định cho cuộc trò chuyện mới", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Text(
                    "Chỉ là giá trị ban đầu cho cuộc trò chuyện mới; có thể đổi riêng cho cuộc trò chuyện hiện có ở phần đầu màn hình chat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                RunMode.BUILD to "Xây dựng",
                RunMode.PLAN to "Lập kế hoạch",
            ).forEach { (value, label) ->
                FilterChip(
                    selected = mode == value,
                    onClick = { onModeChange(value) },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun AgentDepartmentCard(
    departmentId: String,
    enabledCount: Int,
    agentCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val department = AgentDepartments.find(departmentId)
    val departmentColor = Color(department.colorArgb)
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = departmentColor.copy(alpha = 0.4f),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(departmentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                RuntimeIcon(RuntimeIconName.Bot, Modifier.size(20.dp), departmentColor)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${department.localizedName} · ${department.name}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                )
                Text(
                    "$enabledCount/$agentCount đã bật",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            RuntimeIcon(
                if (expanded) RuntimeIconName.ChevronUp else RuntimeIconName.ChevronDown,
                Modifier.size(20.dp),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubagentProfileCard(
    profile: AgentSubagent,
    modelLabel: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (profile.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    RuntimeIcon(RuntimeIconName.Bot, Modifier.size(18.dp), MaterialTheme.colorScheme.primary)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium)
                    Text(profile.id, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
                }
                if (profile.isBuiltin) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "Agency",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Text(profile.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Mô hình mặc định: $modelLabel",
                style = MaterialTheme.typography.labelSmall,
                color = if (profile.defaultModelId == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp),
                    contentDescription = "Sửa vai trò",
                ) {
                    RuntimeIcon(RuntimeIconName.Edit, Modifier.size(17.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp),
                    contentDescription = "Xóa vai trò",
                ) {
                    RuntimeIcon(RuntimeIconName.Trash, Modifier.size(17.dp), MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.width(6.dp))
                Switch(
                    checked = profile.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
    }
}



@Composable
private fun RoundsSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Số vòng công cụ tối đa mỗi lượt", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} vòng",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Tránh mô hình rơi vào vòng lặp vô hạn khi xử lý nhiệm vụ phức tạp; khi hết số vòng sẽ tới điểm kiểm tra tự động tiếp tục bên dưới",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 10f..300f,
            steps = 28, // 10, 20, 30 ... 300
        )
    }
}

@Composable
private fun AutoContinuationSliderRow(
    currentValue: Int,
    roundsPerSegment: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    val continuations = sliderVal.toInt()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tự động tiếp tục khi hết số vòng", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                if (continuations == 0) "Tắt" else "$continuations lần",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (continuations == 0) {
                "Dừng và chờ người dùng xác nhận khi hết số vòng; phù hợp khi cần kiểm soát theo từng giai đoạn"
            } else {
                "Khi hết số vòng, mô hình sẽ tổng kết và ghi lại tiến độ rồi tự động tiếp tục, không cần người dùng nhấn tiếp tục; " +
                    "tổng giới hạn ${roundsPerSegment * (continuations + 1)} vòng"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 0f..10f,
            steps = 9, // 0, 1, 2 ... 10
        )
    }
}

@Composable
private fun ContextBudgetSliderRow(
    currentValue: Int,
    declaredTokens: Int?,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Giới hạn ngân sách token ngữ cảnh", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} tok",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            if (declaredTokens != null) {
                "Mô hình đang dùng đã có giới hạn ngữ cảnh ${declaredTokens / 1000}K (cấu hình rõ ràng hoặc tự nhận diện), thanh trượt này hiện không có hiệu lực; " +
                    "giá trị này chỉ dùng làm ngân sách dự phòng khi không nhận diện được mô hình và chưa cấu hình contextTokens."
            } else {
                "Chưa nhận diện được giới hạn ngữ cảnh của mô hình đang dùng; giá trị này là ngân sách thực tế; " +
                    "lịch sử trò chuyện dài vượt ngân sách sẽ tự động thu gọn nội dung cũ."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 8000f..2000000f,
            steps = 49, // 步长约 4 万 tok
        )
    }
}

/**
 * Thanh trượt tỉ lệ ngưỡng gấp lịch sử.
 *
 * Cho phép gấp lịch sử sớm hơn ngưỡng cứng, giảm input token và độ trễ chữ đầu ở phiên dài.
 */
@Composable
private fun ContextFoldingRatioSliderRow(
    currentValue: Int,
    budget: Int,
    declaredTokens: Int?,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    // Xem trước ngưỡng gấp theo tỉ lệ hiện tại; budget phải là ngân sách thực tế và trừ đúng khoản dự phòng như engine.
    val previewSystemTokens = ContextWindowPolicy.estimateReservedPromptTokens(
        pureChat = false,
        toolDisabled = false,
    )
    val previewLimit = remember(sliderVal, budget, previewSystemTokens) {
        ContextWindowPolicy.foldingLimitFor(
            budget = budget,
            ratioPercent = sliderVal.toInt(),
            systemTokens = previewSystemTokens,
        ).coerceAtLeast(0)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tỷ lệ ngưỡng thu gọn lịch sử", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()}%",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Lịch sử bắt đầu thu gọn tại ‘ngân sách × tỷ lệ’; mặc định 100%, khớp với các harness phổ biến: " +
                " contextWindow - reserveTokens - toolSchemaReserve - systemTokens." +
                " Giảm tỷ lệ để tóm tắt lịch sử sớm hơn và tiết kiệm token đầu vào; mô hình cửa sổ lớn không còn bị giới hạn ở 96K." +
                " Khi dùng Nginx/trung chuyển, hãy đặt client_max_body_size trên 20m để tránh lỗi HTTP 413.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Theo cài đặt hiện tại, ngưỡng thu gọn khoảng ${previewLimit / 1000}K tok",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.primary,
        )
        // Ghi rõ con số xem trước tính theo ngân sách nào để không nhầm với thanh trượt ngân sách ngữ cảnh.
        Text(
            if (declaredTokens != null) {
                "Tính theo cửa sổ mô hình đang có hiệu lực ${budget / 1000}K " +
                    "(cấu hình rõ ràng hoặc tự thích ứng)" +
                    (if (declaredTokens > ContextWindowPolicy.MAX_CONTEXT_BUDGET) {
                        ", bộ máy giới hạn ở ${ContextWindowPolicy.MAX_CONTEXT_BUDGET / 1000}K"
                    } else {
                        ""
                    }) +
                    ". Bản xem trước đã trừ phần dự phòng cho prompt hệ thống/đầu ra/schema công cụ."
            } else {
                "Tính theo thanh trượt ‘Giới hạn ngân sách ngữ cảnh’ phía trên (chưa nhận diện được giới hạn ngữ cảnh mô hình). " +
                    "Bản xem trước đã trừ phần dự phòng cho prompt hệ thống/đầu ra/schema công cụ."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            // khớp coerceIn(10, 100) của SettingsDataStore.setContextFoldingRatioPercent
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 10f..100f,
            steps = 8, // 步长 10%
        )
    }
}

@Composable
private fun ToolsPerRoundSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Số lời gọi công cụ tối đa mỗi vòng", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} lời gọi",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Ngăn mô hình gọi quá nhiều công cụ cùng lúc làm cạn ngữ cảnh hoặc rơi vào vòng lặp mất kiểm soát",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 1f..30f,
            steps = 29,
        )
    }
}

@Composable
private fun ConsecutiveFailuresSliderRow(
    currentValue: Int,
    onValueChange: (Int) -> Unit,
) {
    var sliderVal by remember(currentValue) { mutableFloatStateOf(currentValue.toFloat()) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Ngưỡng ngắt khi thất bại liên tiếp", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "${sliderVal.toInt()} vòng",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            "Chủ động dừng khi mọi lời gọi công cụ đều thất bại trong nhiều vòng liên tiếp, tránh chạy vô ích trong vòng lặp vô hạn",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = sliderVal,
            onValueChange = { sliderVal = it },
            onValueChangeFinished = { onValueChange(sliderVal.toInt()) },
            valueRange = 2f..30f,
            steps = 28,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SkillCard(
    skill: AgentSkill,
    onToggle: (Boolean) -> Unit,
    onViewPrompt: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (skill.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    skill.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        skill.category,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val cmd = skill.triggerCommand
                if (cmd != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            cmd,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Text(skill.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onViewPrompt, contentPadding = PaddingValues(0.dp)) {
                    Text("Xem hướng dẫn (Prompt)", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.weight(1f))
                if (skill.isImmutable) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                    ) {
                        Text(
                            "Lõi hệ thống luôn bật",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                } else {
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp),
                            contentDescription = "Xóa Skill",
                        ) {
                            RuntimeIcon(RuntimeIconName.Trash, Modifier.size(16.dp), MaterialTheme.colorScheme.error)
                        }
                    }
                    Switch(
                        checked = skill.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClawHubMarketSkillCard(
    item: top.wkbin.tianxuan.core.model.skill.ClawHubMarketItem,
    isPreparing: Boolean,
    onInstall: () -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        item.category,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(
                        "v${item.version}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Tác giả: ${item.author} · ★ ${item.stars}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                when {
                    isPreparing -> {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                Text("Đang kiểm tra…", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    item.isInstalled -> {
                        OutlinedButton(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Text("Đã cài đặt", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    else -> {
                        Button(
                            onClick = onInstall,
                            enabled = !isPreparing,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                RuntimeIcon(name = RuntimeIconName.Shield, modifier = Modifier.size(14.dp))
                                Text("Kiểm tra và cài đặt", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginCard(
    plugin: AgentPlugin,
    onToggle: (Boolean) -> Unit,
) {
    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        borderColor = if (plugin.isEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            plugin.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Text("v${plugin.version}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "Tác giả: ${plugin.author}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = plugin.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }

            Text(plugin.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (plugin.permissions.isNotEmpty()) {
                val visiblePermissions = plugin.permissions.take(4)
                val hiddenCount = plugin.permissions.size - visiblePermissions.size
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        "Quyền:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    visiblePermissions.forEach { perm ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                perm,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 140.dp).padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (hiddenCount > 0) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = RoundedCornerShape(4.dp),
                        ) {
                            Text(
                                "tổng cộng ${plugin.permissions.size} quyền",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSkillDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, systemPrompt: String, command: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var cmd by remember { mutableStateOf("") }

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thêm Skill tùy chỉnh", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên kỹ năng (ví dụ: Chuyên gia biên dịch Rust)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Mô tả ngắn") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = cmd,
                    onValueChange = { cmd = it },
                    label = { Text("Lệnh kích hoạt (tùy chọn, ví dụ /rust)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Quy tắc prompt hệ thống (System Prompt)") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, desc, prompt, cmd) },
                enabled = name.isNotBlank() && prompt.isNotBlank(),
            ) {
                Text("Thêm và bật")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        },
    )
}

@Composable
private fun SubagentEditorDialog(
    profile: AgentSubagent?,
    models: List<AiModelEntity>,
    existingIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (
        roleId: String,
        name: String,
        description: String,
        systemPrompt: String,
        defaultModelId: String?,
        defaultModelVariant: String?,
    ) -> Unit,
) {
    var roleId by remember(profile?.id) { mutableStateOf(profile?.id.orEmpty()) }
    var name by remember(profile?.id) { mutableStateOf(profile?.name.orEmpty()) }
    var description by remember(profile?.id) { mutableStateOf(profile?.description.orEmpty()) }
    var prompt by remember(profile?.id) { mutableStateOf(profile?.systemPrompt.orEmpty()) }
    var defaultModelId by remember(profile?.id) { mutableStateOf(profile?.defaultModelId) }
    var defaultModelVariant by remember(profile?.id) { mutableStateOf(profile?.defaultModelVariant) }
    var modelMenuExpanded by remember { mutableStateOf(false) }
    val normalizedId = roleId.trim().lowercase()
        .replace(Regex("[^a-z0-9_-]+"), "_")
        .trim('_')
    val duplicateId = normalizedId.isNotBlank() && normalizedId != profile?.id && normalizedId in existingIds
    val canSave = normalizedId.isNotBlank() && name.isNotBlank() && prompt.isNotBlank() && !duplicateId

    RuntimeAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (profile == null) "Thêm vai trò agent con" else "Sửa vai trò agent con", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = roleId,
                    onValueChange = { roleId = it },
                    label = { Text("Mã vai trò") },
                    supportingText = {
                        Text(if (duplicateId) "Mã vai trò này đã tồn tại" else "Chỉ hỗ trợ chữ cái tiếng Anh, chữ số, dấu gạch dưới và dấu gạch nối")
                    },
                    isError = duplicateId,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tên hiển thị") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Mô tả trách nhiệm") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt hướng dẫn vai trò") },
                    minLines = 5,
                    maxLines = 9,
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Mô hình mặc định", style = MaterialTheme.typography.labelMedium)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { modelMenuExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val selected = models.firstOrNull { it.id == defaultModelId }
                            Text(
                                selected?.let { "${it.name} · ${defaultModelVariant ?: it.model.substringBefore(',').trim()}" }
                                    ?: if (defaultModelId == null) "Theo agent chính" else "Hồ sơ mô hình đã bị xóa",
                                modifier = Modifier.weight(1f),
                            )
                            RuntimeIcon(RuntimeIconName.ChevronDown, Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Theo agent chính") },
                                onClick = {
                                    defaultModelId = null
                                    defaultModelVariant = null
                                    modelMenuExpanded = false
                                },
                            )
                            models.forEach { model ->
                                model.model.split(',').map { it.trim() }.filter { it.isNotBlank() }.distinct().forEach { variant ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.name)
                                                Text(
                                                    "${model.provider} · $variant",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            defaultModelId = model.id
                                            defaultModelVariant = variant
                                            modelMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "Mô hình được chỉ định rõ khi ủy phái nhiệm vụ vẫn được ưu tiên hơn cài đặt này",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(normalizedId, name, description, prompt, defaultModelId, defaultModelVariant)
                },
                enabled = canSave,
            ) {
                Text(if (profile == null) "Thêm và bật" else "Lưu")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Hủy") }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SystemPromptCustomCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    prompt: String,
    onPromptChange: (String) -> Unit,
    charName: String,
    onCharNameChange: (String) -> Unit,
    userName: String,
    onUserNameChange: (String) -> Unit,
) {
    // Chỉ nạp giá trị đã lưu ở lần đầu vào màn hình; nhập liệu ghi sau 400ms để không ghi mỗi phím.
    var textBuffer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(prompt) }
    androidx.compose.runtime.LaunchedEffect(textBuffer) {
        if (textBuffer != prompt) {
            kotlinx.coroutines.delay(400)
            onPromptChange(textBuffer)
        }
    }

    // Tên gọi cũng ghi sau 400ms; để trống thì quay về mặc định
    var charNameBuffer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(charName) }
    androidx.compose.runtime.LaunchedEffect(charNameBuffer) {
        if (charNameBuffer != charName) {
            kotlinx.coroutines.delay(400)
            onCharNameChange(charNameBuffer)
        }
    }
    var userNameBuffer by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(userName) }
    androidx.compose.runtime.LaunchedEffect(userNameBuffer) {
        if (userNameBuffer != userName) {
            kotlinx.coroutines.delay(400)
            onUserNameChange(userNameBuffer)
        }
    }

    var showDefaultPromptDialog by remember { mutableStateOf(false) }
    var defaultPromptAsset by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val androidContext = androidx.compose.ui.platform.LocalContext.current
    if (showDefaultPromptDialog) {
        androidx.compose.runtime.LaunchedEffect(showDefaultPromptDialog) {
            if (defaultPromptAsset.isBlank()) {
                defaultPromptAsset = runCatching {
                    androidContext.assets.open("prompts/system/core.md").bufferedReader().use { it.readText() }
                }.getOrDefault("(Không tải được prompt hệ thống mặc định)")
            }
        }
        RuntimeAlertDialog(
            onDismissRequest = { showDefaultPromptDialog = false },
            title = { Text("Prompt hệ thống mặc định (core.md)", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        defaultPromptAsset,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDefaultPromptDialog = false }) { Text("Đã hiểu") }
            },
        )
    }

    val defaultPromptTemplate = """
You are a helpful and expert AI assistant called {{char}}, based on model {{model_name}}.

## Persona
- You refer to yourself as {{char}} in conversations.
- You address the user as {{user}}.

## System & Device Context
- Time: {{cur_datetime}}
- Locale: {{locale}}
- Timezone: {{timezone}}
- Device: {{device_info}}
- System: {{system_version}}
- Battery: {{battery_level}}

## Instructions
- Always think carefully before answering.
- Follow user instructions precisely and write clean code.
    """.trimIndent()

    val availableVariables = listOf(
        "{{cur_date}}" to "Ngày",
        "{{cur_time}}" to "Giờ",
        "{{cur_datetime}}" to "Ngày giờ",
        "{{model_name}}" to "Tên mô hình",
        "{{model_id}}" to "ID mô hình",
        "{{locale}}" to "Ngôn ngữ và khu vực",
        "{{timezone}}" to "Múi giờ",
        "{{device_info}}" to "Thông tin thiết bị",
        "{{system_version}}" to "Phiên bản hệ thống",
        "{{battery_level}}" to "Mức pin",
        "{{char}}" to "Tên trợ lý",
        "{{user}}" to "Tên người dùng",
    )

    RuntimeCard(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    RuntimeIcon(RuntimeIconName.Prompt, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Prompt hệ thống tùy chỉnh (System Prompt)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Ghi đè System Prompt mặc định toàn cục, hỗ trợ chèn biến macro động", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }

            // Cấu hình tên gọi độc lập với công tắc prompt tùy chỉnh: prompt mặc định vẫn áp dụng
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Cách xưng hô và nhân cách", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                TextButton(
                    onClick = { showDefaultPromptDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RuntimeIcon(RuntimeIconName.Visibility, Modifier.size(12.dp))
                        Text("Xem prompt hệ thống mặc định", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = charNameBuffer,
                    onValueChange = { charNameBuffer = it },
                    label = { Text("Tên mô hình tự xưng") },
                    placeholder = { Text("CTHarness") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = userNameBuffer,
                    onValueChange = { userNameBuffer = it },
                    label = { Text("Cách gọi người dùng") },
                    placeholder = { Text("Người dùng") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                "Thay đổi có hiệu lực ngay với mọi cuộc trò chuyện (để trống để khôi phục mặc định); biến macro {{char}} / {{user}} trong prompt tùy chỉnh cũng sử dụng hai giá trị này.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (enabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Nội dung mẫu prompt:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    TextButton(
                        onClick = {
                            textBuffer = defaultPromptTemplate
                            onPromptChange(defaultPromptTemplate)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            RuntimeIcon(RuntimeIconName.Refresh, Modifier.size(12.dp))
                            Text("Đặt lại mẫu", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                OutlinedTextField(
                    value = textBuffer,
                    onValueChange = { textBuffer = it },
                    placeholder = { Text("Nhập prompt hệ thống tùy chỉnh; hỗ trợ biến macro như {{cur_datetime}}...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                )

                Text("Nhấn để chèn nhanh biến macro động:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    availableVariables.forEach { (variable, label) ->
                        SuggestionChip(
                            onClick = {
                                val updated = if (textBuffer.isBlank()) variable else "$textBuffer $variable"
                                textBuffer = updated
                                onPromptChange(updated)
                            },
                            label = {
                                Text("$label: $variable", style = MaterialTheme.typography.labelSmall)
                            },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            ),
                        )
                    }
                }
            }
        }
    }
}
