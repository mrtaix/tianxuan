package top.wkbin.tianxuan.harness

import top.wkbin.tianxuan.core.common.result.AppResult
import top.wkbin.tianxuan.core.database.HarnessSessionRepository
import top.wkbin.tianxuan.harness.session.SessionTreeStore
import top.wkbin.tianxuan.harness.skill.SkillResourceReader
import top.wkbin.tianxuan.core.security.SecretRedactor
import top.wkbin.tianxuan.core.datastore.AgentPreferences
import top.wkbin.tianxuan.core.model.ApprovalMode
import top.wkbin.tianxuan.core.model.RunMode
import top.wkbin.tianxuan.core.network.DownloadEvent
import top.wkbin.tianxuan.core.network.DownloadRequest
import top.wkbin.tianxuan.core.network.FileDownloader
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.LinuxEnvironmentManager
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import top.wkbin.tianxuan.harness.checkpoint.CheckpointStore
import top.wkbin.tianxuan.harness.effects.OutputRetention
import top.wkbin.tianxuan.harness.effects.ToolOutputRetention
import top.wkbin.tianxuan.harness.effects.foldOverlongLines
import top.wkbin.tianxuan.harness.effects.keepHeadWholeLines
import top.wkbin.tianxuan.harness.effects.keepTailWholeLines
import top.wkbin.tianxuan.harness.compaction.CompressAnchorResult
import top.wkbin.tianxuan.runtime.shell.ProcessType
import top.wkbin.tianxuan.runtime.privilege.BinderOutcome
import top.wkbin.tianxuan.runtime.privilege.PrivilegeManager
import top.wkbin.tianxuan.runtime.privilege.ShizukuSystemApis
import top.wkbin.tianxuan.runtime.apps.AndroidAppManager
import top.wkbin.tianxuan.runtime.bridge.adb.EmbeddedAdbManager
import top.wkbin.tianxuan.runtime.gui.GuiKey
import top.wkbin.tianxuan.runtime.gui.GuiPrimitive
import top.wkbin.tianxuan.runtime.gui.ScrollDirection
import top.wkbin.tianxuan.runtime.virtualdisplay.VirtualDisplayCoordinator
import top.wkbin.tianxuan.runtime.virtualdisplay.VirtualScreenToolkit
import top.wkbin.tianxuan.core.database.AndroidAppRepository
import top.wkbin.tianxuan.core.model.ExecutionMode
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Harness 工具执行器：把 LLM 发出的 ToolCall 翻译成受控操作。
 *
 * - read / write / edit → [WorkspaceFileAccess]（工作区路径安全层）
 * - base → [LinuxRuntime.execute]（PRoot 沙箱内执行命令，带超时与输出截断）
 *
 * 任何工具失败都不会抛异常，而是以结构化的 [ToolResult] 返回给 HarnessLoop，
 * 由模型决定下一步（自我纠正）。
 */
class ToolExecutor(
    private val fileAccess: WorkspaceFileAccess,
    private val linuxRuntime: LinuxRuntime,
    private val pathResolver: HarnessPathResolver,
    private val approvalPolicyEngine: ApprovalPolicyEngine,
    private val secretRedactor: SecretRedactor,
    private val fileDownloader: FileDownloader,
    private val linuxEnvironmentManager: LinuxEnvironmentManager? = null,
    private val approvalRepository: top.wkbin.tianxuan.core.database.AgentApprovalRepository? = null,
    private val sessionDao: HarnessSessionRepository? = null,
    private val subagentOrchestrator: SubagentOrchestrator? = null,
    private val mcpManager: top.wkbin.tianxuan.harness.mcp.McpManager? = null,
    private val contextExecutor: AgentContextExecutor? = null,
    private val messageStore: SessionTreeStore? = null,
    private val eventBus: top.wkbin.tianxuan.harness.events.HarnessEventBus? = null,
    private val privilegeManager: PrivilegeManager? = null,
    private val androidAppManager: AndroidAppManager? = null,
    private val androidAppRepository: AndroidAppRepository? = null,
    private val shizukuApis: ShizukuSystemApis? = null,
    private val hostGuiController: top.wkbin.tianxuan.runtime.gui.HostGuiController? = null,
    private val virtualDisplayCoordinator: VirtualDisplayCoordinator? = null,
    private val virtualScreenToolkit: VirtualScreenToolkit? = null,
    private val buildScriptToolExecutor: BuildScriptToolExecutor? = null,
    private val promptRouter: top.wkbin.tianxuan.harness.prompt.PromptRouter? = null,
    private val checkpointStore: top.wkbin.tianxuan.harness.checkpoint.CheckpointStore? = null,
    private val dualAgentCoordinator: top.wkbin.tianxuan.harness.dual.DualAgentCoordinator? = null,
    private val embeddedAdbManager: EmbeddedAdbManager? = null,
    private val workflowSignals: top.wkbin.tianxuan.harness.workflow.WorkflowSignalBus? = null,
    private val sessionApprovalGrants: top.wkbin.tianxuan.harness.approval.SessionApprovalGrants? = null,
    private val compactionManager: top.wkbin.tianxuan.harness.compaction.CompactionManager? = null,
    private val providerClient: ProviderClient? = null,
    private val skillRepository: top.wkbin.tianxuan.core.database.AgentSkillRepository? = null,
    private val settingsDataStore: AgentPreferences? = null,
    private val knowledgeManager: top.wkbin.tianxuan.harness.knowledge.KnowledgeManager? = null,
) {
    suspend fun execute(
        toolCall: ToolCall,
        sessionId: String = "",
        workspace: String = "",
        bypassApproval: Boolean = false,
        allowApprovalRequest: Boolean = true,
        progressReporter: (suspend (String) -> Unit)? = null,
        operationId: String? = null,
    ): ToolResult {
        val now = System.currentTimeMillis()
        // ask_user 是向用户提问的动作本身，不走审批门控（策略引擎对它亦豁免）：
        // 校验参数 → 落一条 pending 请求（复用审批请求的暂停/恢复管道）→ 返回
        // awaitingApproval 让整轮暂停；UI 渲染问题卡，答案由 HarnessLoop.resolveQuestion
        // 直接作为工具结果落库并续跑，无需重执行。bypassApproval（批准路径误重放）时
        // 返回中性结果，避免重复建问题请求。
        if (toolCall.tool == HarnessTool.ASK_USER) {
            if (!allowApprovalRequest) {
                return ToolResult(
                    id = UUID.randomUUID().toString(),
                    createdAt = now,
                    toolCallId = toolCall.id,
                    success = false,
                    output = "Phát hiện điện thoại đang trong kết nối VPN, VPN hiện tại sẽ tiếp quản lưu lượng của ứng dụng này, máy tính trong cùng mạng LAN có thể bị timeout khi kết nối. Hãy tắt VPN trước, hoặc thêm ứng dụng này vào danh sách kết nối trực tiếp/loại trừ của VPN.",
                    approvalDeferred = true,
                )
            }
            return executeAskUser(toolCall, sessionId, workspace, operationId, now)
        }
        val toolMetadata = mutableMapOf<String, String>()
        val outcome = try {
            if (!bypassApproval && sessionId.isNotBlank()) {
                val repository = approvalRepository
                // 一次查询同时取审批模式与运行意图（两级：会话级优先，回落全局默认）。
                val session = sessionDao?.findById(sessionId)
                val sessionMode = session?.approvalMode?.let(ApprovalMode::fromId)
                // fail-closed：审批仓库未注入或会话无 mode 时默认 REQUEST（每写操作需审批），
                // 而非 FULL_ACCESS（写操作免审批）。防止未来装配遗漏导致写操作免审。
                val mode = sessionMode ?: repository?.currentMode() ?: ApprovalMode.REQUEST
                val runMode = session?.runMode?.let(RunMode::fromId) ?: repository?.currentRunMode() ?: RunMode.BUILD
                // PLAN 只读模式与审批模式正交：命中即硬拒绝，不进审批队列（用户没打算执行，
                // 弹审批卡只是噪音）。子智能体 Lane 复用父会话 id（见 SubagentLaneRunner），
                // 因此这里同样约束后台并行子智能体，无需在 Lane 内重复判定。
                if (runMode == RunMode.PLAN) {
                    approvalPolicyEngine.planBlock(toolCall.tool, toolCall.args, toolCall.rawToolName)?.let { blocked ->
                        val blockedName = toolCall.rawToolName ?: toolCall.tool.name.lowercase()
                        return ToolResult(
                            id = UUID.randomUUID().toString(),
                            createdAt = now,
                            toolCallId = toolCall.id,
                            success = false,
                            output = buildString {
                                append("⛔ Chế độ lập kế hoạch chỉ đọc (PLAN) đã chặn lần gọi này:").append(blockedName).append('\n')
                                append("Lý do:").append(blocked).append('\n')
                                append("Lần gọi này chưa thực thi, cũng chưa vào hàng đợi phê duyệt.\n")
                                append("Đừng thử lại cùng một lời gọi, cũng đừng tuyên bố thao tác đó đã hoàn thành:")
                                append("Ghi động tác đó vào việc cần làm trong kết luận kế hoạch, do người dùng chuyển sang chế độ BUILD (xây dựng) rồi mới thực thi.")
                            },
                        )
                    }
                }
                val decision = approvalPolicyEngine.decide(mode, toolCall.tool, toolCall.args, workspace, toolCall.rawToolName)
                // 「本会话内记住」授权表豁免：用户此前对该操作类别批准过并勾选了记住，
                // 同类后续操作免审批直接执行。表只存内存、随会话销毁，无永久授权；
                // 只豁免本条 required 判定，策略引擎的其余约束不受影响。
                val grantedBySession = decision.required &&
                    sessionApprovalGrants != null &&
                    sessionApprovalGrants.isGranted(
                        sessionId = sessionId,
                        toolName = toolCall.rawToolName ?: toolCall.tool.name.lowercase(),
                        argumentsJson = toolCall.args.toString(),
                        riskLevel = decision.riskLevel,
                    )
                if (decision.required && !grantedBySession) {
                    if (!allowApprovalRequest) {
                        // 后台 Lane 没有可暂停的审批 UI，只能结构化交接：标记 approvalDeferred，
                        // 由 Lane 收集成待办上交父智能体。若只回一句失败文字，模型下一轮会输出
                        // "Đã giao cho Agent chính"，而那句话曾被当成完成结论。
                        return ToolResult(
                            id = UUID.randomUUID().toString(),
                            createdAt = now,
                            toolCallId = toolCall.id,
                            success = false,
                            output = buildString {
                                append("Công cụ này cần người dùng phê duyệt (${decision.summary}), Lane nền của Agent con không hỗ trợ tạm dừng phê duyệt, lời gọi lần này chưa được thực thi.")
                                append("\nLý do:").append(decision.reason)
                                append("\nVui lòng đừng thử lại cùng lời gọi, cũng đừng tuyên bố đã hoàn thành: hãy ghi thao tác đó thành việc cần làm trong kết luận,")
                                append("Do Agent chính khởi tạo lại ở phiên chính và chờ người dùng phê duyệt.")
                            },
                            approvalDeferred = true,
                        )
                    }
                    checkNotNull(repository) { "Kho phê duyệt chưa khởi tạo" }
                    val request = approvalPolicyEngine.createRequest(sessionId, toolCall, workspace, decision, operationId)
                    repository.create(request)
                    eventBus?.emit(
                        top.wkbin.tianxuan.harness.events.HarnessEvent.ApprovalRequested(
                            sessionId = sessionId,
                            timestamp = now,
                            operationId = operationId,
                            approvalRequestId = request.id,
                            toolName = request.toolName,
                            riskLevel = request.riskLevel,
                        ),
                    )
                    return ToolResult(
                        id = UUID.randomUUID().toString(),
                        createdAt = now,
                        toolCallId = toolCall.id,
                        success = false,
                        output = "Chờ người dùng phê duyệt: ${decision.summary}\n${decision.reason}",
                        awaitingApproval = true,
                        approvalRequestId = request.id,
                    )
                }
            }
            executeTool(toolCall.tool, toolCall.args, toolCall.rawToolName, sessionId, workspace, progressReporter, operationId, toolMetadata)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            false to "Lỗi thực thi công cụ: ${throwable.message ?: throwable::class.simpleName}"
        }
        val (success, rawOutput) = outcome
        val finalOutput = if (!success && !rawOutput.contains("【天玄") && !rawOutput.contains("【已强制拦截")) {
            rawOutput + "\n\n【Gợi ý debug Harness Thien Huyen】: Lần gọi công cụ này chưa thành công. Hãy đọc kỹ thông tin lỗi phía trên, phân tích nguyên nhân cụ thể và điều chỉnh chiến lược ở bước sau, nghiêm cấm dùng tham số giống hệt để thử lại mù quáng."
        } else {
            rawOutput
        }
        linuxEnvironmentManager?.refreshIfNeeded()
        // 先对全量输出脱敏，再截断/落盘：落盘引流文件必须与结果正文同一脱敏口径
        val redactedOutput = secretRedactor.redact(
            value = finalOutput,
            secretValues = linuxEnvironmentManager?.values?.value?.values.orEmpty(),
            privacyMode = settingsDataStore?.let { prefs ->
                runCatching { prefs.environmentPrivacyMode.first() }.getOrDefault(true)
            } ?: true,
        )
        val imagePayload = toolMetadata.remove("image_payload")
        return ToolResult(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            toolCallId = toolCall.id,
            success = success,
            output = truncateOutput(
                redactedOutput,
                toolCall.rawToolName ?: HarnessApiMapper.apiName(toolCall.tool),
                if (workspace.isNotBlank()) fileAccess.withBase(workspace) else null,
            ),
            metadata = toolMetadata.toMap(),
            imageDataUrl = imagePayload,
        )
    }

    /**
     * 输出超限时带元数据截断（pi 的 truncate 设计）：保留头部，并明确告知模型
     * 完整输出的规模与截断事实。截断之上叠加落盘引流（对齐 opencode Truncate）：
     * 全量输出写入工作区 `.tianxuan-outputs/` 并在正文附路径，模型可用 read 分页回读，
     * 从「有损截断 + 引导重新取数」升级为「无损引流」。
     */
    private suspend fun truncateOutput(
        output: String,
        toolName: String?,
        fileAccess: WorkspaceFileAccess?,
    ): String {
        // 超长单行先折叠（混淆/压缩文件）：否则单行预算退化成保留 60k 字符的整行，
        // 一条 tool result 就可能超出单条消息的传输上限导致连接被重置。落盘仍用原始全量。
        val folded = foldOverlongLines(output)
        if (folded.length <= MAX_OUTPUT_LENGTH) return folded
        val spillPath = fileAccess?.let { ToolOutputSpillStore.spill(it, toolName, output) }
        // 差异化截断：命令/构建/日志保留尾部（报错在末尾），读取/搜索保留头部。
        val retention = ToolOutputRetention.forTool(toolName)
        val kept = when (retention) {
            OutputRetention.TAIL -> keepTailWholeLines(folded, TRUNCATE_KEEP_LENGTH)
            OutputRetention.HEAD -> keepHeadWholeLines(folded, TRUNCATE_KEEP_LENGTH)
        }
        val totalLines = output.count { it == '\n' } + 1
        val keptLines = kept.count { it == '\n' } + 1
        val recoverHint = if (spillPath != null) {
            "Đầu ra đầy đủ đã được lưu tại $spillPath, có thể dùng công cụ read (phân trang offset/limit) để xem phần còn lại."
        } else {
            "Nếu cần phần còn lại hãy dùng grep để lọc từ khóa, head/tail lấy đầu/cuối, hoặc sed -n 'N,Mp' lấy đoạn dòng chỉ định, đừng lặp lại y nguyên cùng một lệnh."
        }
        return buildString {
            when (retention) {
                OutputRetention.TAIL -> {
                    append("[Đầu ra đã bị cắt: tổng đầu ra đầy đủ là ")
                    append(totalLines)
                    append(" dòng / ")
                    append(output.length)
                    append(" ký tự, dưới đây chỉ hiển thị phần cuối ")
                    append(keptLines)
                    append(" dòng (lỗi lệnh/assertion thường nằm ở cuối).")
                    append(recoverHint)
                    append("]\n")
                    append(kept)
                }
                OutputRetention.HEAD -> {
                    append(kept)
                    append("\n\n[Đầu ra đã bị cắt: tổng đầu ra đầy đủ gồm ")
                    append(totalLines)
                    append(" dòng / ")
                    append(output.length)
                    append(" ký tự, trên đây chỉ hiển thị phần đầu ")
                    append(keptLines)
                    append(" dòng.")
                    append(recoverHint)
                    append("]")
                }
            }
        }
    }

    private suspend fun executeTool(
        tool: HarnessTool,
        rawArgs: JsonObject,
        rawToolName: String?,
        sessionId: String,
        workspace: String,
        progressReporter: (suspend (String) -> Unit)?,
        operationId: String?,
        metadata: MutableMap<String, String>,
    ): Pair<Boolean, String> {
        // MCP 工具的参数名由远端 schema 定义，跳过单键解包/扁平键还原与内置别名，
        // 否则名为 input 的单参数或含 __ / . 的合法参数名会被错误改写。
        val args = top.wkbin.tianxuan.harness.validation.ToolSchemaValidator.normalizeArgs(
            rawArgs, applyAliases = tool != HarnessTool.MCP, isMcpTool = tool == HarnessTool.MCP,
        )
        val activeFileAccess = if (workspace.isNotBlank()) fileAccess.withBase(workspace) else fileAccess
        return when (tool) {
            HarnessTool.READ -> {
                val path = requireString(args, "path")
                val imageMime = resolveImageMime(path)
                if (imageMime != null) {
                    // 沙箱图片多模态直通：不做 UTF-8 解码，转 Base64 data URL 交给
                    // ApiMessageProjector 在 visionEnabled 时注入图片消息。
                    when (val bytesResult = activeFileAccess.readRawBytes(path)) {
                        is AppResult.Success -> {
                            val base64 = java.util.Base64.getEncoder().encodeToString(bytesResult.data)
                            metadata["image_payload"] = ImagePayloadCompressor.downscaleDataUrl(
                                "data:$imageMime;base64,$base64",
                            )
                            true to "Đã đọc tệp ảnh $path (${bytesResult.data.size} byte, $imageMime)." +
                                "Ảnh đã được cung cấp dưới dạng đính kèm đa phương thức kèm kết quả công cụ lần này; nếu model hiện tại không hỗ trợ thị giác, hãy chuyển sang mô tả nội dung ảnh bằng văn bản/script."
                        }
                        is AppResult.Failure -> bytesResult.toToolOutput(actionName = "read")
                    }
                } else {
                    val offset = args["offset"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
                    val limit = args["limit"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
                    activeFileAccess.read(path, offset, limit).toToolOutput(actionName = "read")
                }
            }
            HarnessTool.WRITE -> {
                val path = requireString(args, "path")
                val content = requireString(args, "content")
                captureBeforeWrite(sessionId, activeFileAccess, path)
                val linesAdded = content.lines().size
                val output = activeFileAccess.write(path, content).toToolOutput("Đã ghi $path\nDIFF_STAT: +$linesAdded -0", actionName = "write")
                if (output.first) captureAfterWrite(sessionId, activeFileAccess, path, knownContent = content)
                output
            }
            HarnessTool.EDIT -> {
                val path = requireString(args, "path")
                val oldText = requireString(args, "oldText")
                val newText = requireString(args, "newText")
                captureBeforeWrite(sessionId, activeFileAccess, path)
                val linesAdded = newText.lines().size
                val linesDeleted = oldText.lines().size
                val outcome = activeFileAccess.editDetailed(path, oldText, newText)
                val output = outcome.toToolOutput(actionName = "edit")
                // 成功时：命中策略回给模型（简短文本），Unified Diff 只进 metadata 供前端渲染，
                // 绝不把 diff 正文注入模型上下文，避免重复占用 Token。
                val finalOutput = if (output.first && outcome is AppResult.Success) {
                    outcome.data.diff?.let { metadata["diff"] = it }
                    true to "Đã sửa $path (chiến lược khớp: ${outcome.data.strategy}, thay thế ${outcome.data.replacements} chỗ)\n" +
                        "DIFF_STAT: +$linesAdded -$linesDeleted"
                } else {
                    output
                }
                // edit 的结果内容不等于 newText：从盘上重读最终状态作改动后凭据
                if (finalOutput.first) captureAfterWrite(sessionId, activeFileAccess, path, knownContent = null)
                finalOutput
            }
            HarnessTool.BASE -> executeBase(args, workspace)
            HarnessTool.PROCESS -> executeProcess(args, workspace)
            HarnessTool.HOST -> executeHost(args, operationId, sessionId)
            HarnessTool.DOWNLOAD -> {
                val destinationPath = args.stringArg("destination")
                if (destinationPath != null) captureBeforeWrite(sessionId, activeFileAccess, destinationPath)
                executeDownload(args, activeFileAccess, progressReporter)
            }
            HarnessTool.MEMORY -> contextExecutor?.executeMemory(args, sessionId, workspace) ?: (false to "Chưa khởi tạo trình thực thi ký ức")
            HarnessTool.PLAN -> contextExecutor?.executePlan(args, sessionId) ?: (false to "Chưa khởi tạo bộ thực thi kế hoạch")
            HarnessTool.SCRATCHPAD -> contextExecutor?.executeScratchpad(args, sessionId) ?: (false to "Chưa khởi tạo bộ thực thi nháp")
            HarnessTool.HISTORY_SEARCH -> executeHistorySearch(args, sessionId)
            HarnessTool.HISTORY_READ -> executeHistoryRead(args, sessionId)
            HarnessTool.COMPRESS -> executeCompress(args, sessionId)
            // ask_user 在 execute() 入口特判（不走审批门控）；此处仅为 when 穷尽兜底
            HarnessTool.ASK_USER -> false to "ask_user nên xử lý ở entry thực thi, không nên tới bộ phân phối công cụ"
            HarnessTool.RENDER_SURFACE -> A2uiSurfaceBus.publishFromTool(args, sessionId)
            HarnessTool.KNOWLEDGE -> executeKnowledgeSearch(args)
            HarnessTool.KB_ADD -> executeKnowledgeAdd(args)
            HarnessTool.KB_LIST -> executeKnowledgeList()
            HarnessTool.BUILD_SCRIPT -> buildScriptToolExecutor?.execute(args, workspace) ?: (false to "Chưa khởi tạo trình quản lý script build")
            HarnessTool.SUBAGENT -> if (rawToolName.equals("invoke_dual_agent", ignoreCase = true)) {
                dualAgentCoordinator?.executeFromTool(args, sessionId, workspace) ?: (false to "Chưa khởi tạo bộ điều phối Agent kép")
            } else {
                subagentOrchestrator?.executeSubagents(args, sessionId) ?: (false to "Chưa khởi tạo trình điều phối Agent con")
            }
            HarnessTool.MCP -> if (rawToolName == "use_capability") {
                executeCapability(args, workspace)
            } else {
                // 兼容路径：对话历史/模型习惯中仍可能出现直接 mcp__ 调用（schema 已不再宣告）
                mcpManager?.executeTool(rawToolName ?: "mcp", args, workspace) ?: (false to "Chưa khởi tạo trình quản lý MCP")
            }
            HarnessTool.LOAD_SKILL -> {
                // 按需技能加载：目录（元数据）常驻系统提示，命中后由模型主动拉取
                // 完整指导规则，避免全部正文常驻撑爆上下文。
                val query = requireString(args, "name").trim().trimStart('/')
                val skills = skillRepository?.activeSkills?.first().orEmpty()
                if (skills.isEmpty()) {
                    false to "Hiện chưa có kỹ năng nào được bật. Vui lòng nhắc người dùng vào「Cài đặt → Agent」bật kỹ năng rồi thử lại."
                } else {
                    val queryLower = query.lowercase()
                    // 精确匹配（name / id / 去斜杠 triggerCommand）：大小写无关。
                    val exact = skills.filter { skill ->
                        val candidates = setOf(
                            skill.name.lowercase(),
                            skill.id.lowercase(),
                            skill.triggerCommand?.removePrefix("/")?.lowercase().orEmpty(),
                        )
                        queryLower in candidates
                    }
                    // 模糊匹配仅作兜底，且必须唯一命中——若命中多条还静默取第一条，
                    // 会出现「load_skill("Git") 却加载了 Git 敏捷工作流」这类选错技能的问题。
                    val fuzzy = if (exact.isEmpty()) {
                        skills.filter { skill ->
                            skill.name.lowercase().contains(queryLower) ||
                                (skill.triggerCommand?.removePrefix("/")?.lowercase()?.contains(queryLower) == true)
                        }
                    } else {
                        emptyList()
                    }
                    val pool = if (exact.isNotEmpty()) exact else fuzzy
                    when {
                        pool.isEmpty() -> false to "Không tìm thấy skill khớp: $query. Skill khả dụng:" +
                            skills.joinToString("、") { it.name }
                        pool.size > 1 -> false to "Tên skill $query khớp nhiều skill:" +
                            pool.joinToString("、") { it.name } + "。Vui lòng dùng tên skill đầy đủ hoặc id để thử lại."
                        else -> {
                            val matched = pool.first()
                            val subPath = args.stringArg("path")?.trim()
                            if (subPath.isNullOrEmpty()) {
                                true to "[Skill đã nạp: ${matched.name}] (category=${matched.category})\n" +
                                    SkillResourceReader.stripFrontmatter(matched.systemPrompt).trim()
                            } else {
                                val resourceDir = matched.resourcePath?.takeIf { it.isNotBlank() }
                                if (resourceDir == null) {
                                    false to "Kỹ năng 「${matched.name}」 không có thư mục tài nguyên đọc được (resourcePath rỗng), không thể đọc $subPath." +
                                        "Vui lòng chuyển sang nhập kỹ năng đó dưới dạng thư mục chứa SKILL.md rồi mới đọc tài nguyên con."
                                } else {
                                    val content = SkillResourceReader.readSubResource(File(resourceDir), subPath)
                                    if (content == null) {
                                        false to "Tài nguyên kỹ năng không tồn tại hoặc vượt đường dẫn: $subPath." +
                                            "Đường dẫn phải là đường dẫn tương đối trong thư mục kỹ năng (như references/foo.md / scripts/bar.sh)."
                                    } else {
                                        true to "【Tài nguyên kỹ năng: ${matched.name}/$subPath】\n$content"
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HarnessTool.LOAD_RULE -> {
                val rule = requireString(args, "rule")
                val content = promptRouter?.loadRule(rule)
                if (content != null) {
                    true to "【Khối quy tắc: $rule】\n$content"
                } else {
                    // 名单从 PromptRouter 动态生成：硬编码清单会随规则块增删漂移（曾漏 image-delivery/browser-reverse）。
                    val available = promptRouter?.availableRuleNames()
                        ?: "workflow / code-navigation / security / memory / environment-proot / tools"
                    false to "Khối quy tắc không xác định: $rule. Khả dụng: $available"
                }
            }
        }
    }

    /** 宿主 Android 特权通道；权限在每次执行前实时复核，不能仅依赖启动时快照。 */
    private suspend fun executeHost(args: JsonObject, operationId: String?, sessionId: String): Pair<Boolean, String> {
        val raw = executeHostUncapped(args, operationId, sessionId)
        return raw.first to capHostOutput(raw.second)
    }

    /**
     * host 侧输出的统一硬上限：dumpsys / uiautomator XML / logcat 等宿主命令动辄数 MB，
     * 无上限时单条结果会以 ToolResult 字符串 + Room 实体 + UI 投影多份拷贝驻留 256MB 的
     * Java 堆，直接触发 target footprint OOM。截断标记引导模型缩小范围重取。
     */
    @OptIn(InternalCoroutinesApi::class)
    private suspend fun executeHostUncapped(args: JsonObject, operationId: String?, sessionId: String): Pair<Boolean, String> {
        val action = requireString(args, "action").trim().lowercase()

        // Logcat 优先走内置无线 ADB，不依赖 Shizuku/Root；不可用时再回退原特权通道。
        if (action == "logcat" && embeddedAdbManager != null) {
            val explicitPort = args["port"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
            val adbResult = embeddedAdbManager.captureLogcat(
                EmbeddedAdbManager.LogcatRequest(
                    packageName = args["package"]?.jsonPrimitive?.content?.trim().orEmpty(),
                    tag = args["tag"]?.jsonPrimitive?.content?.trim().orEmpty(),
                    priority = args["priority"]?.jsonPrimitive?.content?.trim()?.uppercase()?.firstOrNull() ?: 'V',
                    keyword = args["keyword"]?.jsonPrimitive?.content?.trim().orEmpty(),
                    lines = optionalLong(args, "tail_lines", 200L, 1L, 2_000L).toInt(),
                ),
                explicitPort = explicitPort,
            )
            if (adbResult.success) {
                return true to "mode wireless-adb · exit ${adbResult.exitCode ?: 0}\n${adbResult.output.trim()}"
            }
            val fallbackManager = privilegeManager ?: return false to adbResult.output.ifBlank {
                "ADB không dây chưa kết nối; vui lòng bật gỡ lỗi không dây trong bảng điều khiển nhà phát triển và hoàn tất ghép nối một lần."
            }
            val info = fallbackManager.getPrivilegeInfo()
            if (info.mode == ExecutionMode.PROOT || !info.modeActive) {
                return false to adbResult.output.ifBlank {
                    "ADB không dây chưa kết nối; vui lòng bật gỡ lỗi không dây trong bảng điều khiển nhà phát triển và hoàn tất ghép nối một lần."
                }
            }
        }
        val manager = privilegeManager ?: return false to "Chưa khởi tạo bộ thực thi quyền host"

        // settings_put system 命名空间优先走 Android ContentResolver API（需 WRITE_SETTINGS），
        // 避免 Shizuku shell 在部分国产 ROM 上被 SettingsProvider 静默拒绝（exit 22）。
        // secure/global 命名空间需 WRITE_SECURE_SETTINGS（第三方应用不可得），仍走 shell。
        if (action == "settings_put") {
            val namespace = requireSettingsNamespace(args)
            val key = requireHostIdentifier(args, "key", SETTINGS_KEY)
            val value = requireString(args, "value")
            if (namespace == "system") {
                val apiOk = manager.writeSystemSetting(key, value)
                if (apiOk) {
                    android.util.Log.i(
                        "TianXuan-Host",
                        secretRedactor.redact("action=settings_put via API success: system.$key=$value"),
                    )
                    return true to "mode api · exit 0\n[Android API] settings put system $key = $value"
                }
                // API 写入失败（通常是未授权 WRITE_SETTINGS），发事件引导用户授权，然后回退 shell
                eventBus?.emit(
                    top.wkbin.tianxuan.harness.events.HarnessEvent.PermissionRequired(
                        sessionId = sessionId.ifBlank { "unknown" },
                        timestamp = System.currentTimeMillis(),
                        permission = "WRITE_SETTINGS",
                        reason = "Sửa đổi cài đặt hệ thống (như độ sáng) cần cấp quyền 「Sửa đổi cài đặt hệ thống」",
                    )
                )
            }
        }

        return when (action) {
            "status" -> {
                val info = manager.getPrivilegeInfo()
                true to buildString {
                    append("Chế độ đang có hiệu lực:").append(info.mode.title)
                    append("\nTrạng thái quyền:").append(if (info.modeActive) "Đã cấp quyền" else "Chưa ủy quyền")
                    append("\nShizuku：").append(if (info.shizukuAvailable) "Khả dụng (shell UID 2000)" else "Không khả dụng")
                    append("\nRoot：").append(if (info.rootAvailable) "Khả dụng (UID 0)" else "Không khả dụng hoặc chưa chọn")
                }
            }
            "app_list" -> executeCachedApps(args)
            "screen_observe" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val onlyInteractive = args["only_interactive"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: true
                val res = gui.observeScreen(onlyInteractive)
                res.fold(
                    onSuccess = { obs -> true to obs.toAgentSummary() },
                    onFailure = { err -> false to "Nhận biết màn hình thất bại: ${err.message}" }
                )
            }
            "screen_click" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val x = requireInt(args, "x")
                val y = requireInt(args, "y")
                val res = gui.click(x, y)
                res.fold(
                    onSuccess = { msg -> true to msg },
                    onFailure = { err -> false to err.message.orEmpty() }
                )
            }
            "screen_double_click" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                gui.doubleClick(requireInt(args, "x"), requireInt(args, "y")).fold(
                    onSuccess = { true to it },
                    onFailure = { false to it.message.orEmpty() },
                )
            }
            "screen_long_press" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val duration = optionalLong(args, "duration_ms", 800L, 200L, 5_000L)
                gui.longPress(requireInt(args, "x"), requireInt(args, "y"), duration).fold(
                    onSuccess = { true to it },
                    onFailure = { false to it.message.orEmpty() },
                )
            }
            "screen_swipe" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val x1 = requireInt(args, "x1")
                val y1 = requireInt(args, "y1")
                val x2 = requireInt(args, "x2")
                val y2 = requireInt(args, "y2")
                val durationMs = optionalLong(args, "duration_ms", 300L, 50L, 3000L)
                val res = gui.swipe(x1, y1, x2, y2, durationMs)
                res.fold(
                    onSuccess = { msg -> true to msg },
                    onFailure = { err -> false to err.message.orEmpty() }
                )
            }
            "screen_scroll" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val direction = when (requireString(args, "direction").lowercase()) {
                    "up" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.UP
                    "down" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.DOWN
                    "left" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.LEFT
                    "right" -> top.wkbin.tianxuan.runtime.gui.ScrollDirection.RIGHT
                    else -> return false to "direction chỉ hỗ trợ up/down/left/right"
                }
                val ratio = args["distance_ratio"]?.jsonPrimitive?.content?.toFloatOrNull()?.coerceIn(0.15f, 0.8f) ?: 0.45f
                val durationMs = optionalLong(args, "duration_ms", 350L, 50L, 5_000L)
                gui.scroll(direction, ratio, durationMs).fold(
                    onSuccess = { true to it },
                    onFailure = { false to it.message.orEmpty() },
                )
            }
            "screen_input_text", "paste_text" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val text = requireString(args, "text")
                val res = gui.inputText(text)
                res.fold(
                    onSuccess = { msg -> true to msg },
                    onFailure = { err -> false to err.message.orEmpty() }
                )
            }
            "screen_key" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val key = requireString(args, "key")
                val res = gui.sendKey(key)
                res.fold(
                    onSuccess = { msg -> true to msg },
                    onFailure = { err -> false to err.message.orEmpty() }
                )
            }
            "app_launch" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val packageName = requireHostIdentifier(args, "package", PACKAGE_NAME)
                val res = gui.launchApp(packageName)
                res.fold(
                    onSuccess = { msg -> true to msg },
                    onFailure = { err -> false to err.message.orEmpty() }
                )
            }
            "screen_capture" -> {
                val gui = hostGuiController ?: return false to "Chưa khởi tạo bộ điều khiển GUI"
                val targetPath = requireString(args, "path")
                val res = gui.captureScreenshot(targetPath)
                res.fold(
                    onSuccess = { msg -> true to msg },
                    onFailure = { err -> false to err.message.orEmpty() }
                )
            }
            "virtual_screen_ensure" -> {
                val coordinator = virtualDisplayCoordinator ?: return false to "Chưa khởi tạo bộ điều phối màn hình ảo"
                val session = optionalSession(args)
                val displayId = coordinator.ensureVirtualDisplay(session)
                if (displayId == null) {
                    val reason = coordinator.lastFailureReason
                        ?: "Nguyên nhân không rõ (ensureVirtualDisplay trả về null nhưng không có failureReason)"
                    false to "Tạo màn hình ảo thất bại (session=$session): $reason"
                } else {
                    true to "Màn hình ảo đã sẵn sàng: session=$session displayId=$displayId (kích thước giống màn hình chính);" +
                        "Tiếp theo dùng virtual_screen_launch để khởi chạy ứng dụng, virtual_screen_screenshot để chụp ảnh nhận diện"
                }
            }
            "virtual_screen_launch" -> {
                val coordinator = virtualDisplayCoordinator ?: return false to "Chưa khởi tạo bộ điều phối màn hình ảo"
                val session = optionalSession(args)
                val packageName = requireHostIdentifier(args, "package", PACKAGE_NAME)
                if (coordinator.getDisplayId(session) == null &&
                    coordinator.ensureVirtualDisplay(session) == null
                ) {
                    return false to "Tạo màn hình ảo thất bại (session=$session): cần chế độ Shizuku hoặc Root"
                }
                val res = coordinator.launchApp(session, packageName)
                if (res) {
                    true to "Đã khởi chạy ứng dụng trên màn hình ảo: $packageName (session=$session " +
                        "displayId=${coordinator.getDisplayId(session)}); hình ảnh ứng dụng sẽ không xuất hiện trên màn hình chính"
                } else {
                    false to "Khởi chạy ứng dụng màn hình ảo thất bại: $packageName (kiểm tra tên gói có phải ứng dụng đã cài không)"
                }
            }
            "virtual_screen_screenshot" -> {
                val coordinator = virtualDisplayCoordinator ?: return false to "Chưa khởi tạo bộ điều phối màn hình ảo"
                val session = optionalSession(args)
                if (coordinator.getDisplayId(session) == null) {
                    return false to "Màn hình ảo chưa được tạo (session=$session): hãy gọi virtual_screen_ensure trước"
                }
                val targetPath = requireString(args, "path")
                val png = coordinator.requestScreenshot(session)
                    ?: return false to "Chụp màn hình ảo thất bại (session=$session): server không phản hồi hoặc đã thoát"
                runCatching {
                    val file = File(targetPath)
                    file.parentFile?.mkdirs()
                    file.writeBytes(png)
                }.fold(
                    onSuccess = { true to "Ảnh chụp màn hình ảo đã lưu tại $targetPath (${png.size} byte), có thể dùng read để xem ảnh" },
                    onFailure = { err -> false to "Ghi ảnh chụp thất bại: ${err.message}" }
                )
            }
            "virtual_screen_click",
            "virtual_screen_double_click",
            "virtual_screen_long_press",
            "virtual_screen_swipe",
            "virtual_screen_scroll",
            "virtual_screen_key",
            -> {
                val toolkit = virtualScreenToolkit ?: return false to "Chưa khởi tạo công cụ màn hình ảo"
                val session = optionalSession(args)
                val primitive = when (action) {
                    "virtual_screen_click" ->
                        GuiPrimitive.Tap(requireInt(args, "x"), requireInt(args, "y"))
                    "virtual_screen_double_click" ->
                        GuiPrimitive.DoubleTap(requireInt(args, "x"), requireInt(args, "y"))
                    "virtual_screen_long_press" -> GuiPrimitive.LongPress(
                        x = requireInt(args, "x"),
                        y = requireInt(args, "y"),
                        durationMs = optionalLong(args, "duration_ms", 800L, 200L, 5_000L),
                    )
                    "virtual_screen_swipe" -> GuiPrimitive.Swipe(
                        x1 = requireInt(args, "x1"),
                        y1 = requireInt(args, "y1"),
                        x2 = requireInt(args, "x2"),
                        y2 = requireInt(args, "y2"),
                        durationMs = optionalLong(args, "duration_ms", 300L, 50L, 5_000L),
                    )
                    "virtual_screen_scroll" -> GuiPrimitive.Scroll(
                        direction = when (args["direction"]?.jsonPrimitive?.contentOrNull?.lowercase()) {
                            "up" -> ScrollDirection.UP
                            "down" -> ScrollDirection.DOWN
                            "left" -> ScrollDirection.LEFT
                            "right" -> ScrollDirection.RIGHT
                            else -> return false to "screen_scroll cần direction: up/down/left/right"
                        },
                        distanceRatio = args["distance_ratio"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0.45f,
                        durationMs = optionalLong(args, "duration_ms", 350L, 50L, 5_000L),
                    )
                    else -> {
                        val key = GuiKey.parse(requireString(args, "key"))
                            ?: return false to "Phím không rõ: hỗ trợ back/home/recents/enter/delete/paste/power"
                        GuiPrimitive.Key(key)
                    }
                }
                val result = toolkit.execute(session, primitive)
                result.success to result.message
            }
            "virtual_screen_close" -> {
                val coordinator = virtualDisplayCoordinator ?: return false to "Chưa khởi tạo bộ điều phối màn hình ảo"
                val session = optionalSession(args)
                coordinator.closeSession(session)
                true to "Đã đóng phiên màn hình ảo: $session"
            }
            "virtual_screen_show" -> {
                val coordinator = virtualDisplayCoordinator ?: return false to "Chưa khởi tạo bộ điều phối màn hình ảo"
                val session = optionalSession(args)
                if (coordinator.getDisplayId(session) == null &&
                    coordinator.ensureVirtualDisplay(session) == null
                ) {
                    return false to "Tạo màn hình ảo thất bại (session=$session): cần chế độ Shizuku hoặc Root"
                }
                if (coordinator.showOverlay(session)) {
                    true to "Đã hiển thị cửa sổ nổi trực tiếp của màn hình ảo (session=$session): người dùng có thể xem hình ảnh và trực tiếp chạm can thiệp"
                } else {
                    false to "Chưa cấp quyền cửa sổ nổi: hãy hướng dẫn người dùng cho phép「Hiển thị trên các ứng dụng khác」trong cài đặt hệ thống rồi thử lại"
                }
            }
            "virtual_screen_hide" -> {
                val coordinator = virtualDisplayCoordinator ?: return false to "Chưa khởi tạo bộ điều phối màn hình ảo"
                coordinator.hideOverlay()
                true to "Đã ẩn cửa sổ nổi màn hình ảo (phiên màn hình ảo không bị ảnh hưởng, vẫn có thể tiếp tục thao tác)"
            }
            else -> {
                val packageName = if (action in APP_DATABASE_GUARDED_ACTIONS || action == "app_grant_permission") {
                    requireHostIdentifier(args, "package", PACKAGE_NAME)
                } else ""
                if (action in APP_DATABASE_GUARDED_ACTIONS) {
                    (androidAppManager ?: return false to "Chưa khởi tạo trình quản lý ứng dụng").requireInitialized(packageName)
                }
                val info = manager.getPrivilegeInfo()
                require(info.mode != ExecutionMode.PROOT && info.modeActive) {
                    "Không đủ quyền: trước khi đóng băng, bật, gỡ cài hoặc cấp quyền ứng dụng, hãy cấp quyền trong cài đặt và chuyển sang chế độ Shizuku hoặc Root."
                }

                // Shizuku 生效时优先 Binder 直调：免 shell 转义、异常结构化。
                // 仅通道不可用时才回退 shell；远端明确拒绝则直接报告不重试。
                if (info.mode == ExecutionMode.SHIZUKU && shizukuApis != null) {
                    val userId = optionalLong(args, "user", 0L, 0L, 999L).toInt()
                    val binderOutcome = when (action) {
                        "app_grant_permission" -> {
                            val permission = requireHostIdentifier(args, "permission", ANDROID_PERMISSION)
                            shizukuApis.grantRuntimePermission(packageName, permission, userId)
                        }
                        "app_freeze", "package_disable" ->
                            shizukuApis.setApplicationEnabledSetting(packageName, enabled = false, userId = userId)
                        "app_unfreeze", "package_enable" ->
                            shizukuApis.setApplicationEnabledSetting(packageName, enabled = true, userId = userId)
                        else -> null
                    }
                    when (binderOutcome) {
                        is BinderOutcome.Success -> {
                            android.util.Log.i("TianXuan-Host", "action=$action via binder success pkg=$packageName")
                            if (action in APP_DATABASE_GUARDED_ACTIONS) androidAppManager?.synchronize()
                            return true to buildString {
                                append("mode shizuku-api · exit 0")
                                append("\n[Android Binder] $action $packageName thành công")
                                if (action == "app_grant_permission") {
                                    append(" quyền=").append(requireHostIdentifier(args, "permission", ANDROID_PERMISSION))
                                }
                            }
                        }
                        is BinderOutcome.Failed ->
                            return false to "Phía máy chủ từ chối thao tác này: ${binderOutcome.message} (chế độ=${info.mode.shortLabel}). Vui lòng kiểm tra tên gói/tên quyền rồi thử lại."
                        else -> Unit
                    }
                }

                val command = buildHostCommand(action, args)
                require(command.length <= MAX_COMMAND_LENGTH) { "Lệnh quá dài (${command.length} ký tự, giới hạn $MAX_COMMAND_LENGTH)" }
                val hostOperationId = operationId?.takeIf { it.isNotBlank() } ?: "host-${UUID.randomUUID()}"
                val cancelHandle = currentCoroutineContext()[Job]?.invokeOnCompletion(onCancelling = true) { cause ->
                    if (cause is CancellationException) manager.cancelShellCommand(hostOperationId)
                }
                val result = try {
                    manager.executeShellCommand(command, hostOperationId)
                } finally {
                    cancelHandle?.dispose()
                }
                android.util.Log.i(
                    "TianXuan-Host",
                    secretRedactor.redact(
                        "action=$action exit=${result.exitCode} success=${result.success}\n" +
                            "cmd=$command\nstdout=${result.stdout.take(500)}\nstderr=${result.stderr.take(300)}",
                    ),
                )
                val body = buildString {
                    append("mode ").append(info.mode.shortLabel).append(" · exit ").append(result.exitCode)
                    if (result.stdout.isNotBlank()) append("\n").append(result.stdout.trim())
                    if (result.stderr.isNotBlank()) append("\n").append(result.stderr.trim())
                }
                if (result.success && action in APP_DATABASE_GUARDED_ACTIONS) {
                    // Keep the agent's next app_list read coherent with the mutation it just made.
                    androidAppManager?.synchronize()
                }
                result.success to body
            }
        }
    }

    private suspend fun executeCachedApps(args: JsonObject): Pair<Boolean, String> {
        val repository = androidAppRepository ?: return false to "Chưa khởi tạo cơ sở dữ liệu ứng dụng"
        if (repository.count() == 0) return false to "Cơ sở dữ liệu ứng dụng chưa được khởi tạo; vui lòng vào Cài đặt → Quản lý ứng dụng để hoàn tất khởi tạo và đồng bộ trước."
        val query = args["query"]?.jsonPrimitive?.content?.trim().orEmpty()
        val limit = optionalLong(args, "limit", 50L, 1L, 200L).toInt()
        val includeSystem = args["include_system"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        val apps = repository.search(query, if (includeSystem) limit else 200)
            .asSequence()
            .filter { includeSystem || !it.isSystemApp }
            .take(limit)
            .toList()
        if (apps.isEmpty()) return true to "Không tìm thấy trong cơ sở dữ liệu ứng dụng: ${query.ifBlank { "全部应用" }}"
        return true to apps.joinToString("\n") { app ->
            buildString {
                append(app.label).append(" | ").append(app.packageName)
                append(" | ").append(if (app.isSystemApp) "系统" else "用户")
                append(" | ").append(if (app.isEnabled) "Bật" else "Vô hiệu hóa")
                if (app.isSuspended) append(" | Đóng băng")
                if (app.isNetworkRestricted) append(" | Nối mạng nền bị hạn chế")
            }
        }
    }

    private fun buildHostCommand(action: String, args: JsonObject): String = when (action) {
        "exec" -> requireString(args, "command")
        "settings_get" -> {
            val namespace = requireSettingsNamespace(args)
            val key = requireHostIdentifier(args, "key", SETTINGS_KEY)
            "/system/bin/settings get $namespace ${shellQuote(key)}"
        }
        "settings_put" -> {
            val namespace = requireSettingsNamespace(args)
            val key = requireHostIdentifier(args, "key", SETTINGS_KEY)
            val value = requireString(args, "value")
            // 屏幕亮度写入：自适应亮度开启时系统会忽略手动值，先切到手动模式；
            // 写入后回读验证，因为 Shizuku UserService 进程若 UID 非 shell，
            // WRITE_SETTINGS 会被 SettingsProvider 静默拒绝（exit 0 但值不变）。
            if (namespace == "system" && key == "screen_brightness") {
                val quoted = shellQuote(value)
                buildString {
                    append("/system/bin/settings put system screen_brightness_mode 0; ")
                    append("/system/bin/settings put system screen_brightness $quoted; ")
                    append("echo \"uid=$(id -u) mode=$(/system/bin/settings get system screen_brightness_mode) ")
                    append("requested=$quoted actual=$(/system/bin/settings get system screen_brightness)\"")
                }
            } else {
                "/system/bin/settings put $namespace ${shellQuote(key)} ${shellQuote(value)}"
            }
        }
        "package_list" -> {
            val filter = args["filter"]?.jsonPrimitive?.content?.trim().orEmpty()
            "/system/bin/pm list packages" + if (filter.isBlank()) "" else " | /system/bin/grep -F -- ${shellQuote(filter)}"
        }
        "package_disable", "package_enable", "package_uninstall_user", "app_freeze", "app_unfreeze", "app_grant_permission" -> {
            val packageName = requireHostIdentifier(args, "package", PACKAGE_NAME)
            val user = optionalLong(args, "user", 0L, 0L, 999L)
            when (action) {
                "package_disable", "app_freeze" -> "/system/bin/pm disable-user --user $user ${shellQuote(packageName)}"
                "package_enable", "app_unfreeze" -> "/system/bin/pm enable --user $user ${shellQuote(packageName)}"
                "app_grant_permission" -> {
                    val permission = requireHostIdentifier(args, "permission", ANDROID_PERMISSION)
                    "/system/bin/pm grant ${shellQuote(packageName)} ${shellQuote(permission)}"
                }
                else -> "/system/bin/pm uninstall --user $user ${shellQuote(packageName)}"
            }
        }
        "device_status" ->
            "echo '[battery]'; dumpsys battery | grep -E 'level|status|temperature'" +
                "; echo '[network]'; dumpsys connectivity | head -30" +
                "; echo '[foreground]'; dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -6" +
                "; echo '[storage]'; df -h /data | tail -2"
        "logcat" -> {
            val lines = optionalLong(args, "tail_lines", 200L, 1L, 2_000L)
            val tag = args["tag"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (tag.isBlank()) "/system/bin/logcat -d -t $lines"
            else {
                require(LOGCAT_TAG.matches(tag)) { "Định dạng logcat tag không hợp lệ" }
                "/system/bin/logcat -d -t $lines -s ${shellQuote("$tag:*")}"
            }
        }
        else -> throw IllegalArgumentException(
            "host action không được hỗ trợ: $action; khả dụng status/exec/settings_get/settings_put/package_list/package_disable/package_enable/package_uninstall_user/app_list/app_freeze/app_unfreeze/app_grant_permission/logcat",
        )
    }

    private fun requireSettingsNamespace(args: JsonObject): String {
        val namespace = requireString(args, "namespace").trim().lowercase()
        require(namespace in setOf("system", "secure", "global")) { "namespace chỉ hỗ trợ system/secure/global" }
        return namespace
    }

    private fun requireHostIdentifier(args: JsonObject, key: String, pattern: Regex): String {
        val value = requireString(args, key).trim()
        require(pattern.matches(value)) { "$key định dạng không hợp lệ" }
        return value
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private suspend fun executeHistorySearch(args: JsonObject, sessionId: String): Pair<Boolean, String> {
        val query = requireString(args, "query")
        val limit = optionalLong(args, "limit", 8L, 1L, 20L).toInt()
        val matches = messageStore?.searchIndexed(sessionId, query, limit).orEmpty()
        if (matches.isEmpty()) return true to "Không tìm thấy lịch sử khớp: $query"
        return true to matches.map { (message, index) ->
            "[index=$index] id=${message.id} time=${message.createdAt} ${historyLabel(message)}"
        }.joinToString("\n")
    }

    /**
     * compress 工具：用户明确要求压缩上下文时，把指定边界之前的历史
     * 折叠为结构化摘要。anchor 必须原样、唯一地摘自某条用户消息——多匹配/零匹配都拒绝，
     * 让模型换更长的摘录重试，而不是猜一个边界静默压错地方。走与自动压缩相同的
     * cache-replay 摘要路径；原文不丢，仍可 history_read 回读。
     */
    /**
     * ask_user：把结构化提问落成一条 pending 请求（toolName=ask_user，复用审批请求的
     * 存储/过期/认领生命周期），返回 awaitingApproval 让工具回合暂停。
     * 校验失败回写可纠正文案（不暂停、不建请求）；仓储不可用同理。
     */
    private suspend fun executeAskUser(
        toolCall: ToolCall,
        sessionId: String,
        workspace: String,
        operationId: String?,
        now: Long,
    ): ToolResult {
        fun failure(output: String) = ToolResult(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            toolCallId = toolCall.id,
            success = false,
            output = output,
        )
        if (sessionId.isBlank()) return failure("ask_user cần context phiên (sessionId rỗng)")
        val questions = runCatching { AskUserQuestions.parse(toolCall.args) }.getOrElse { error ->
            return failure("Kiểm tra tham số ask_user không đạt: ${error.message}. Vui lòng sửa tham số rồi gọi lại.")
        }
        val repository = approvalRepository ?: return failure("Kho phê duyệt chưa khởi tạo, không thể đặt câu hỏi cho người dùng")
        val request = approvalPolicyEngine.createRequest(
            sessionId = sessionId,
            toolCall = ToolCall(
                id = toolCall.id,
                createdAt = now,
                tool = HarnessTool.ASK_USER,
                args = toolCall.args,
                rawToolName = AskUserQuestions.TOOL_NAME,
            ),
            workspace = workspace,
            decision = ApprovalDecision(
                required = true,
                riskLevel = "none",
                reason = questions.first().question.take(200),
                summary = "Agent hỏi bạn (${questions.size} câu hỏi)",
            ),
            operationId = operationId,
        )
        // 问题等待的是用户的思考与决策，TTL 比操作类审批（10 分钟）宽裕
        runCatching { repository.create(request.copy(expiresAt = now + QUESTION_TTL_MS)) }
            .getOrElse { throwable ->
                return failure("Lưu yêu cầu hỏi vào CSDL thất bại: ${throwable.message ?: throwable::class.simpleName}")
            }
        return ToolResult(
            id = UUID.randomUUID().toString(),
            createdAt = now,
            toolCallId = toolCall.id,
            success = true,
            output = "Đã đặt ${questions.size} câu hỏi cho người dùng, đang chờ trả lời.",
            awaitingApproval = true,
            approvalRequestId = request.id,
        )
    }

    private suspend fun executeCompress(args: JsonObject, sessionId: String): Pair<Boolean, String> {
        // compress 只会在用户明确要求时由模型调用，属于显式会话操作。
        // 不受「命令输出压缩」或「自动上下文压缩」开关约束：前者仅控制 RTK 命令输出，
        // 后者仅控制 ApiContextAssembler 的自动折叠策略。
        val manager = compactionManager ?: return false to "Chưa khởi tạo trình quản lý nén"
        val mode = args.stringArg("mode").orEmpty().trim().lowercase()
        val anchor = args.stringArg("anchor").orEmpty()
        if (sessionId.isBlank()) return false to "Hiện không có phiên hoạt động, không thể nén"

        val context = manager.project(sessionId)
        val keepFromIndex = when (val resolved = top.wkbin.tianxuan.harness.compaction.CompactionManager.resolveCompressAnchor(
            context.messages,
            mode,
            anchor,
        )) {
            is CompressAnchorResult.Resolved -> resolved.keepFromIndex
            is CompressAnchorResult.Invalid -> return false to resolved.message
        }

        // 手动压缩也走 LLM 摘要：按会话绑定的模型解析；解析失败退回机械摘要路径
        val model = runCatching {
            sessionDao?.findById(sessionId)?.let { session ->
                providerClient?.resolveConfigured(session.modelId, session.modelVariant)
            }
        }.getOrNull()
        val summaryContext = model?.let {
            top.wkbin.tianxuan.harness.compaction.SummaryRequestContext(
                systemPrompt = "",
                summaryLayer = context.summaryLayer,
                toolCallMode = if (it.pureChatMode) ToolCallMode.DISABLED else it.toolCallMode,
                visionEnabled = it.visionEnabled,
                recallBlocks = context.recallBlocks,
                // 手动压缩没有装配期的截断快照：直接取投影前缀（截断差异只影响缓存命中起点，不影响正确性）
                replayPrefix = context.messages.take(keepFromIndex),
            )
        }
        val compacted = manager.compact(
            sessionId,
            context,
            keepFromIndex,
            model = model,
            summaryContext = summaryContext,
        )
        val foldedCount = context.messages.size - compacted.messages.size
        return true to buildString {
            append("Đã nhấn ")
            append(if (mode == "before") "before" else "after")
            append(" Chế độ nén: gộp $foldedCount tin nhắn thành tóm tắt có cấu trúc")
            append("(Sau khi giữ còn ${compacted.messages.size} mục).")
            append("Nguyên văn bị gộp vẫn còn trong bản ghi hội thoại, khi cần chi tiết có thể dùng history_read(message_id) để đọc lại theo nhu cầu.")
        }
    }


    /**
     * use_capability 统一代理的分发：
     * - list：列出已启用的服务与缓存工具数，**不启动任何服务器进程**；
     * - inspect：查看某服务的工具清单与参数（缓存为空时按需发现一次——模型必须拿到
     *   完整清单才能构造 call；失败时回 getLastError 给出可读原因）；
     * - call：按 (server, tool) 执行——未连接的服务在此按需启动并发现；
     * - decline：模型显式放弃某能力，确认即回。
     */
    private suspend fun executeCapability(args: JsonObject, workspace: String): Pair<Boolean, String> {
        val manager = mcpManager ?: return false to "Chưa khởi tạo trình quản lý MCP"
        val action = args.stringArg("action").orEmpty().trim().lowercase()
        return when (action) {
            "list" -> {
                val summaries = manager.enabledServerSummaries()
                if (summaries.isEmpty()) {
                    false to "Hiện chưa bật dịch vụ MCP nào. Có thể bật năng lực tích hợp hoặc thêm dịch vụ tùy chỉnh trong 「Cài đặt → Plugin MCP và hệ sinh thái giao thức」."
                } else {
                    true to buildString {
                        appendLine("Dịch vụ MCP đã bật (dịch vụ chưa kết nối sẽ tự khởi động và phát hiện công cụ ở lần call đầu tiên): ")
                        summaries.forEach { summary ->
                            appendLine(
                                "- ${summary.id} · ${summary.name} · đã cache ${summary.cachedToolCount} công cụ · " +
                                    if (summary.connected) "Đã kết nối" else "Chưa kết nối",
                            )
                        }
                        append("Dùng inspect để xem danh sách công cụ và tham số của một dịch vụ, dùng call để gọi.")
                    }
                }
            }
            "inspect" -> {
                val serverId = args.stringArg("server")?.trim().orEmpty()
                if (serverId.isBlank()) return false to "inspect cần tham số server (dùng list trước để xem id dịch vụ khả dụng)"
                // 缓存为空 = 服务尚未连接过：按需发现一次（唯一会拉起进程的 inspect 场景——
                // 模型无从得知未连接服务的工具名，必须给它完整清单才能构造 call）
                var tools = manager.cachedToolsOf(serverId)
                if (tools.isEmpty()) {
                    tools = manager.discoverServerTools(serverId)
                }
                if (tools.isEmpty()) {
                    val lastError = manager.getLastError(serverId)
                    return false to "MCP[$serverId] phát hiện công cụ thất bại hoặc dịch vụ không khả dụng${lastError?.let { "：$it" } ?: "（未启用或不存在）"}。" +
                        "Có thể thử inspect lại sau, hoặc kiểm tra cài đặt và môi trường sandbox của dịch vụ đó."
                }
                val rendered = tools.joinToString("\n\n") { tool ->
                    buildString {
                        appendLine("### ${tool.name}")
                        if (tool.description.isNotBlank()) appendLine(tool.description.trim().take(400))
                        if (tool.parametersJson.isNotBlank() && tool.parametersJson != "{}") {
                            appendLine("Tham số: ${tool.parametersJson.take(1200)}")
                        }
                    }
                }
                val body = if (rendered.length > MAX_INSPECT_CHARS) {
                    rendered.take(MAX_INSPECT_CHARS) + "\n…[Danh sách quá dài đã cắt, có thể call trực tiếp theo tên công cụ đã biết]"
                } else {
                    rendered
                }
                true to "MCP[$serverId] danh sách công cụ (${tools.size} công cụ):\n$body"
            }
            "call" -> {
                val serverId = args.stringArg("server")?.trim().orEmpty()
                val tool = args.stringArg("tool")?.trim().orEmpty()
                if (serverId.isBlank() || tool.isBlank()) {
                    return false to "call cần tham số server và tool (inspect trước để xem tên công cụ và tham số khả dụng)"
                }
                val callArgs = args["arguments"] as? JsonObject ?: JsonObject(emptyMap())
                manager.executeCapabilityTool(serverId, tool, callArgs, workspace)
            }
            "decline" -> {
                val serverId = args.stringArg("server").orEmpty().trim()
                val tool = args.stringArg("tool").orEmpty().trim()
                true to "Đã ghi nhận: không thử lại nữa ${if (serverId.isNotBlank()) "$serverId." else ""}$tool. Vui lòng dùng cách khác để hoàn thành nhiệm vụ hoặc nêu rõ trở ngại cho người dùng."
            }
            else -> false to "action phải là một trong list / inspect / call / decline"
        }
    }

    private suspend fun executeHistoryRead(args: JsonObject, sessionId: String): Pair<Boolean, String> {
        val messageId = args["message_id"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
        val index = args["index"]?.jsonPrimitive?.content?.trim()?.toIntOrNull()
        require(messageId != null || index != null) { "history.read cần message_id hoặc index" }
        val messages = messageStore?.readWithRelated(sessionId, messageId, index).orEmpty()
        if (messages.isEmpty()) return false to "Không tìm thấy tin nhắn lịch sử chỉ định"
        return true to messages.joinToString("\n\n") { message ->
            "id=${message.id}\n${historyLabel(message, full = true)}"
        }.take(MAX_HISTORY_READ_OUTPUT)
    }

    private fun historyLabel(message: HarnessMessage, full: Boolean = false): String = when (message) {
        is CapabilityEvent -> "Sự kiện năng lực ${message.name}: ${message.details}"
        is SkillSuggestion -> "Gợi ý skill ${message.skillName}: ${message.description}"
        is ModelSwitchEvent -> "Chuyển model ${message.fromLabel} → ${message.toLabel}"
        is UserMessage -> "Người dùng: ${message.text.take(if (full) MAX_HISTORY_READ_OUTPUT else 240)}"
        is AssistantText -> "Trợ lý: ${message.text.take(if (full) MAX_HISTORY_READ_OUTPUT else 240)}" +
            if (full && !message.reasoning.isNullOrBlank()) "\nreasoning:\n${message.reasoning.take(MAX_HISTORY_READ_OUTPUT)}" else ""
        is ToolCall -> "Gọi công cụ ${message.rawToolName ?: message.tool}: ${message.args}" +
            if (full) "\nreasoning:\n${message.reasoning.orEmpty().take(MAX_HISTORY_READ_OUTPUT)}" else ""
        is ToolResult -> "Kết quả công cụ: ${message.output.take(if (full) MAX_HISTORY_READ_OUTPUT else 240)}"
    }

    private suspend fun executeBase(args: JsonObject, workspace: String): Pair<Boolean, String> {
        val command = requireString(args, "command")
        require(command.length <= MAX_COMMAND_LENGTH) { "Lệnh quá dài (${command.length} ký tự, giới hạn $MAX_COMMAND_LENGTH)" }
        val cwd = pathResolver.resolveWorkingDirectory(args["cwd"]?.jsonPrimitive?.content, workspace)
        val commandOutputCompressionEnabled = settingsDataStore?.let { prefs ->
            runCatching { prefs.commandOutputCompressionEnabled.first() }.getOrDefault(true)
        } ?: true
        val preparedCommand = RtkCommandOptimizer.prepare(command, commandOutputCompressionEnabled)
        val configuredTimeoutSeconds = settingsDataStore?.let { prefs ->
            runCatching { prefs.baseCommandTimeoutSeconds.first() }
                .getOrDefault(AgentPreferences.DEFAULT_BASE_COMMAND_TIMEOUT_SECONDS)
        } ?: AgentPreferences.DEFAULT_BASE_COMMAND_TIMEOUT_SECONDS
        val timeoutSeconds = optionalLong(
            args = args,
            key = "timeout_seconds",
            default = configuredTimeoutSeconds.toLong(),
            min = MIN_BASE_TIMEOUT_SECONDS,
            max = MAX_BASE_TIMEOUT_SECONDS,
        )
        val result = linuxRuntime.execute(
            ShellCommand(
                commandLine = preparedCommand.commandLine,
                workingDirectory = cwd,
                environment = preparedCommand.environment,
                timeoutMs = timeoutSeconds * 1000L,
            ),
        )
        val stdout = result.stdout.trim()
        val stderr = result.stderr.trim()
        val isSuccess = result.isSuccess

        // 🌟 AI 场景感知：构建失败或 APK 产物生成时主动通知工作流总线
        val projectName = workspace.trim('/').substringAfterLast('/').ifBlank { "workspace" }
        if (!isSuccess && isLikelyBuildCommand(command)) {
            val buildError = (stderr.ifBlank { stdout }).take(4000)
            workflowSignals?.emit(top.wkbin.tianxuan.harness.workflow.WorkflowSignal.BuildFailed(projectName, cwd, buildError))
        }
        val combinedOutput = stdout + "\n" + stderr
        APK_PATH_REGEX.find(combinedOutput)?.let { match ->
            val apkPath = match.value
            workflowSignals?.emit(top.wkbin.tianxuan.harness.workflow.WorkflowSignal.ApkGenerated(projectName, cwd, apkPath))
        }

        val body = buildString {
            append("exit ${result.exitCode} · ${result.durationMs} ms")
            if (stdout.isNotEmpty()) append("\n$stdout")
            if (stderr.isNotEmpty()) append("\n$stderr")
            if (!isSuccess && result.exitCode != 0) {
                append("\n\n【Yêu cầu phản tỉnh và sửa lỗi khi thực thi thất bại】")
                append("\nLệnh trả về mã thoát khác 0 (${result.exitCode}). Vui lòng đọc kỹ thông tin lỗi phía trên:")
                append("\n1. Nghiêm cấm lặp lại nguyên văn lệnh đã thất bại;")
                append("\n2. Phân tích xem là lỗi chính tả tham số, đường dẫn không tồn tại, sandbox thiếu phụ thuộc hệ thống hay lỗi cú pháp;")
                append("\n3. Sửa lệnh, dùng trình quản lý gói để cài phụ thuộc hoặc đổi công cụ khác rồi tiếp tục.")
            }
        }
        return isSuccess to body
    }

    private suspend fun executeProcess(args: JsonObject, workspace: String): Pair<Boolean, String> {
        val action = requireString(args, "action").trim().lowercase()
        return when (action) {
            "start" -> {
                val externalId = requireProcessId(args)
                val internalId = processId(externalId)
                val command = requireString(args, "command")
                require(command.length <= MAX_COMMAND_LENGTH) { "Lệnh quá dài (${command.length} ký tự, giới hạn $MAX_COMMAND_LENGTH)" }
                val cwd = pathResolver.resolveWorkingDirectory(args["cwd"]?.jsonPrimitive?.content, workspace)
                val existing = linuxRuntime.listBackground().firstOrNull { it.id == internalId && it.session.isAlive }
                require(existing == null) { "Tiến trình nền $externalId đã đang chạy; vui lòng truy vấn trạng thái hoặc dừng nó trước" }
                val managed = linuxRuntime.startBackground(
                    id = internalId,
                    command = ShellCommand(
                        commandLine = command,
                        workingDirectory = cwd,
                        timeoutMs = Long.MAX_VALUE,
                    ),
                    type = ProcessType.COMMAND,
                )
                true to buildString {
                    append("Tiến trình nền đã khởi động:").append(externalId)
                    managed.pid?.let { append("\npid: ").append(it) }
                    append("\nThư mục làm việc: ").append(cwd)
                    append("\nHãy dùng process(status/logs/stop) để quản lý; lệnh nên chạy ở chế độ foreground, đừng bọc thêm nohup hay &.")
                }
            }
            "status" -> {
                val externalId = requireProcessId(args)
                val managed = linuxRuntime.listBackground().firstOrNull { it.id == processId(externalId) }
                    ?: return false to "Không tìm thấy tiến trình nền: $externalId"
                true to buildString {
                    append("Tiến trình nền:").append(externalId)
                    append("\nTrạng thái:").append(if (managed.session.isAlive) "运行中" else "Đã thoát")
                    managed.pid?.let { append("\npid: ").append(it) }
                    append("\nĐã chạy:").append((System.currentTimeMillis() - managed.startedAt).coerceAtLeast(0L)).append(" ms")
                }
            }
            "logs" -> {
                val externalId = requireProcessId(args)
                val tailLines = optionalLong(args, "tail_lines", DEFAULT_PROCESS_LOG_LINES, 1L, MAX_PROCESS_LOG_LINES).toInt()
                val logs = linuxRuntime.getBackgroundLogs(processId(externalId)).takeLast(tailLines)
                true to if (logs.isEmpty()) "Tiến trình nền $externalId tạm chưa có log" else logs.joinToString("\n")
            }
            "list" -> {
                val managed = linuxRuntime.listBackground().filter { it.id.startsWith(AGENT_PROCESS_PREFIX) }
                true to if (managed.isEmpty()) {
                    "Hiện không có tiến trình nền do Agent quản lý"
                } else {
                    managed.joinToString("\n") {
                        val externalId = it.id.removePrefix(AGENT_PROCESS_PREFIX)
                        "$externalId · ${if (it.session.isAlive) "运行中" else "已退出"} · ${(System.currentTimeMillis() - it.startedAt).coerceAtLeast(0L)} ms"
                    }
                }
            }
            "stop" -> {
                val externalId = requireProcessId(args)
                val stopped = linuxRuntime.stopBackground(processId(externalId))
                stopped to if (stopped) "Tiến trình nền đã dừng: $externalId" else "Không tìm thấy tiến trình nền: $externalId"
            }
            else -> false to "process action không được hỗ trợ: $action; khả dụng start/status/logs/list/stop"
        }
    }


    private fun requireProcessId(args: JsonObject): String {
        val id = requireString(args, "id").trim().lowercase()
        require(PROCESS_ID.matches(id)) { "id tiến trình chỉ cho phép chữ thường, số, dấu chấm, gạch dưới và gạch nối, độ dài 1-64" }
        return id
    }

    private fun processId(externalId: String): String = AGENT_PROCESS_PREFIX + externalId

    private suspend fun executeDownload(
        args: JsonObject,
        activeFileAccess: WorkspaceFileAccess,
        progressReporter: (suspend (String) -> Unit)?,
    ): Pair<Boolean, String> {
        val url = requireString(args, "url")
        require(url.startsWith("https://", ignoreCase = true)) { "Địa chỉ tải phải dùng HTTPS" }
        val destinationPath = requireString(args, "destination")
        val destination = activeFileAccess.resolveDownloadDestination(destinationPath)
        val sha256 = args["sha256"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val maxAttempts = optionalLong(args, "max_attempts", DEFAULT_DOWNLOAD_ATTEMPTS, 1L, MAX_DOWNLOAD_ATTEMPTS).toInt()
        val maxBytes = optionalLong(args, "max_bytes", DEFAULT_DOWNLOAD_MAX_BYTES, 1L, MAX_DOWNLOAD_MAX_BYTES)
        var latestProgress: DownloadEvent.Progress? = null
        var verified = false
        var completedFile: java.io.File? = null
        val startedAt = System.currentTimeMillis()
        var lastReportedAt = 0L
        fileDownloader.download(
            DownloadRequest(
                url = url,
                destination = destination,
                sha256 = sha256,
                maxAttempts = maxAttempts,
                maxBytes = maxBytes,
            ),
        ).collect { event ->
            when (event) {
                is DownloadEvent.Progress -> {
                    latestProgress = event
                    val now = System.currentTimeMillis()
                    if (progressReporter != null && (now - lastReportedAt >= PROGRESS_REPORT_INTERVAL_MS || event.totalBytes != null && event.downloadedBytes == event.totalBytes)) {
                        lastReportedAt = now
                        progressReporter(formatDownloadProgress(event, startedAt))
                    }
                }
                DownloadEvent.Verifying -> {
                    verified = true
                    progressReporter?.invoke("Đang kiểm tra SHA-256 của tệp tải…")
                }
                is DownloadEvent.Completed -> completedFile = event.file
                DownloadEvent.Started -> Unit
            }
        }
        val file = completedFile ?: destination
        val size = file.length()
        if (hasImageExtension(destination) && !isImageMagic(destination)) {
            return false to buildString {
                append("Tải thất bại: mục tiêu phải là ảnh, nhưng nội dung không phải định dạng ảnh hợp lệ (JPEG/PNG/GIF/WebP/BMP).")
                append("\nNguồn:").append(url)
                append("\nKích thước:").append(size).append(" bytes")
                append("\nNguyên nhân thường gặp: dịch vụ lưu ảnh chống crawl/chống hotlink trả về ảnh placeholder hoặc trang lỗi, hoặc liên kết đã hết hạn.")
                append("\nGợi ý: đổi nguồn ảnh (đổi trang/đổi tên miền) hoặc thử lại sau, đừng thử lại nguyên trạng với cùng URL.")
            }
        }
        val duplicateOf = findDuplicateDownload(destination)
        val body = buildString {
            append("Tải xong:").append(destinationPath)
            append("\nKích thước:").append(size).append(" bytes")
            latestProgress?.totalBytes?.let { append(" / ").append(it).append(" bytes") }
            append("\nĐặc tính: HTTPS, tiếp tục tải HTTP Range, tự động thử lại (tối đa ").append(maxAttempts).append(" lần)")
            if (verified) append("\nSHA-256: đã kiểm tra")
            append("\nGhi chú: trình tải hiện tại là tiếp tục tải một kết nối, không phải tải phân mảnh đa luồng.")
            if (duplicateOf != null) {
                append("\n\nCảnh báo: nội dung tải lần này với tệp đã có trong workspace ").append(duplicateOf)
                append(" hoàn toàn giống nhau (SHA-256 trùng khớp), nghi là ảnh placeholder chống crawl của image host. Hãy đổi nguồn ảnh ngay (đổi site/đổi domain),")
                append("Đừng tiếp tục tải với tần suất cao từ cùng một image host, cũng đừng tải lặp lại cùng một URL.")
            }
        }
        return true to body
    }

    private fun hasImageExtension(file: java.io.File): Boolean =
        file.extension.lowercase() in IMAGE_DOWNLOAD_EXTENSIONS

    private fun isImageMagic(file: java.io.File): Boolean {
        val head = ByteArray(16)
        val read = try {
            java.io.FileInputStream(file).use { it.read(head) }
        } catch (_: java.io.IOException) {
            return true
        }
        if (read < 3) return false
        val jpeg = head[0] == 0xFF.toByte() && head[1] == 0xD8.toByte() && head[2] == 0xFF.toByte()
        val png = read >= 8 && head[0] == 0x89.toByte() && head[1] == 0x50.toByte() && head[2] == 0x4E.toByte() && head[3] == 0x47.toByte()
        val gif = head[0] == 0x47.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte()
        val bmp = head[0] == 0x42.toByte() && head[1] == 0x4D.toByte()
        val webp = read >= 12 && head[0] == 0x52.toByte() && head[1] == 0x49.toByte() && head[2] == 0x46.toByte() && head[3] == 0x46.toByte() &&
            head[8] == 0x57.toByte() && head[9] == 0x45.toByte() && head[10] == 0x42.toByte() && head[11] == 0x50.toByte()
        return jpeg || png || gif || bmp || webp
    }

    /** 检测同目录下是否已有字节完全相同的文件（反爬占位图的典型特征：不同 URL 下载结果一模一样）。 */
    private fun findDuplicateDownload(file: java.io.File): String? {
        if (file.length() <= 0L || file.length() > 32L * 1024 * 1024) return null
        val parent = file.parentFile ?: return null
        val candidates = parent.listFiles { f -> f.isFile && f.name != file.name && f.length() == file.length() }
            ?: return null
        val digest = try {
            sha256Of(file)
        } catch (_: Exception) {
            return null
        } ?: return null
        for (candidate in candidates) {
            if (sha256Of(candidate) == digest) return candidate.name
        }
        return null
    }

    private fun sha256Of(file: java.io.File): String? = try {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        java.io.FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    } catch (_: Exception) {
        null
    }

    private fun formatDownloadProgress(event: DownloadEvent.Progress, startedAt: Long): String {
        val elapsedMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
        val speed = event.downloadedBytes * 1000L / elapsedMs
        val downloaded = formatBytes(event.downloadedBytes)
        val total = event.totalBytes?.let(::formatBytes)
        val percent = event.totalBytes?.takeIf { it > 0L }?.let { event.downloadedBytes * 100 / it }
        return buildString {
            append("Đang tải xuống:").append(downloaded)
            if (total != null) {
                append(" / ").append(total)
                percent?.let { append(" (").append(it.coerceIn(0L, 100L)).append("%)") }
            }
            append(" · ").append(formatBytes(speed)).append("/s")
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KiB", "MiB", "GiB", "TiB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index += 1
        }
        return if (value >= 100 || value % 1.0 == 0.0) "${value.toInt()} ${units[index]}" else "${"%.1f".format(java.util.Locale.US, value)} ${units[index]}"
    }


    /** 写工具触碰前，捕获该路径的轮初内容进 checkpoint（同轮同路径去重）。 */
    private suspend fun captureBeforeWrite(
        sessionId: String,
        activeFileAccess: WorkspaceFileAccess,
        path: String,
    ) {
        val normalized = path.trim().trimStart('/')
        // 超过快照上限的文件整体跳过：避免大文件整读入堆内存（OOM 风险），
        // 且 commit 恢复走 write（同样受 1MiB 上限约束），快照了也恢复不回去。
        // 注意不能返回 null 充当"Tệp không tồn tại"快照——那会让 rewind 误删该文件。
        val size = activeFileAccess.fileSizeOrNull(normalized)
        if (size != null && size > CheckpointStore.SNAPSHOT_MAX_BYTES) {
            eventBus?.emit(
                top.wkbin.tianxuan.harness.events.HarnessEvent.RecoveryApplied(
                    sessionId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    operationId = null,
                    outcome = "checkpoint_incomplete",
                    detail = "Tệp $normalized vượt quá ${CheckpointStore.SNAPSHOT_MAX_BYTES} bytes, không thể chụp đầy đủ ảnh chụp trước lượt; lượt rewind này không đảm bảo khôi phục được tệp đó.",
                ),
            )
            return
        }
        checkpointStore?.capture(sessionId, normalized, activeFileAccess.previewOrNull(normalized))
    }

    /**
     * 写工具成功后，把该路径的最终状态记录为改动后凭据（restore 冲突检测的比对基准）。
     * [knownContent] 非空时直接采用（write 的入参即最终内容）；edit 从盘上重读。
     * 超大文件跳过（与 pre-image 同一上限）；checkpointStore 未配置时静默跳过。
     */
    private suspend fun captureAfterWrite(
        sessionId: String,
        activeFileAccess: WorkspaceFileAccess,
        path: String,
        knownContent: String?,
    ) {
        if (sessionId.isBlank()) return
        val store = checkpointStore ?: return
        val normalized = path.trim().trimStart('/')
        val size = activeFileAccess.fileSizeOrNull(normalized)
        if (size == null || size > CheckpointStore.SNAPSHOT_MAX_BYTES) return
        val content = knownContent ?: activeFileAccess.previewOrNull(normalized) ?: return
        store.captureAfterImage(sessionId, normalized, content)
    }

    private fun AppResult<Any>.toToolOutput(successMessage: String = "", actionName: String = ""): Pair<Boolean, String> = when (this) {
        is AppResult.Success -> true to successMessage.ifBlank { data.toString() }
        is AppResult.Failure -> {
            val baseError = error.message
            val reflectionHint = when (actionName) {
                "edit" -> "\n\n【Yêu cầu phản tỉnh và sửa lỗi khi thay thế văn bản thất bại】\nKhông tìm thấy oldText khớp duy nhất trong tệp mục tiêu. Hãy lập tức gọi read để xem nội dung thật và số dòng mới nhất của tệp, lấy nội dung khớp chính xác rồi mới phát edit, nghiêm cấm đoán mò hoặc lặp lại nội dung giống hệt!"
                "read" -> "\n\n【Yêu cầu phản tư và sửa lỗi khi đọc tệp thất bại】\nKhông thể đọc tệp tại đường dẫn chỉ định. Hãy gọi base chạy ls hoặc find để xác định đường dẫn thực sự tồn tại của tệp, tuyệt đối đừng mù quáng lặp lại đường dẫn sai!"
                else -> ""
            }
            false to (baseError + reflectionHint)
        }
    }

    /** 图片后缀探针：仅光栅格式走多模态直通，SVG 仍按文本读取（更利于模型审阅源码）。 */
    private fun resolveImageMime(path: String): String? = when (path.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "bmp" -> "image/bmp"
        "ico" -> "image/x-icon"
        else -> null
    }

    private fun JsonObject.stringArg(key: String): String? =
        this[key]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }

    private fun requireString(args: JsonObject, key: String): String {
        val value = args[key]?.jsonPrimitive?.content
        require(!value.isNullOrBlank()) { "Thiếu tham số: $key" }
        require(value.length <= MAX_ARG_LENGTH) { "Tham số $key quá dài (${value.length} ký tự, giới hạn $MAX_ARG_LENGTH)" }
        return value
    }

    private fun requireInt(args: JsonObject, key: String): Int {
        val raw = args[key]?.jsonPrimitive?.content?.trim()
        require(!raw.isNullOrBlank()) { "Thiếu tham số: $key" }
        return raw.toIntOrNull() ?: throw IllegalArgumentException("Tham số $key phải là số nguyên")
    }

    private fun optionalLong(args: JsonObject, key: String, default: Long, min: Long, max: Long): Long {
        val raw = args[key]?.jsonPrimitive?.content?.trim() ?: return default
        val value = raw.toLongOrNull() ?: throw IllegalArgumentException("Tham số $key phải là số nguyên")
        require(value in min..max) { "Tham số $key phải nằm trong khoảng $min-$max" }
        return value
    }

    /** virtual_screen_* 的可选会话 ID（默认 default，不同会话对应独立虚拟屏）。 */
    private fun optionalSession(args: JsonObject): String =
        args["session"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: VIRTUAL_SCREEN_DEFAULT_SESSION

    private suspend fun executeKnowledgeSearch(args: JsonObject): Pair<Boolean, String> {
        val km = knowledgeManager ?: return false to "Kho tri thức chưa khởi tạo"
        val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
        if (query.isBlank()) return false to "kb_search cần tham số query"
        val topK = (args["top_k"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 3).coerceIn(1, 10)
        return try {
            val hits = km.search(query, topK)
            if (hits.isEmpty()) {
                true to "Không truy xuất được đoạn tài liệu liên quan đến 「$query」 trong kho tri thức. Có thể dùng kb_add để thêm tài liệu trước."
            } else {
                val text = hits.joinToString("\n\n") { "【${it.docName} · Độ tương đồng ${"%.2f".format(it.score)}】\n${it.text}" }
                true to text
            }
        } catch (e: Exception) {
            false to "Truy xuất kho tri thức thất bại: ${e.message ?: e::class.simpleName}"
        }
    }

    private suspend fun executeKnowledgeAdd(args: JsonObject): Pair<Boolean, String> {
        val km = knowledgeManager ?: return false to "Kho tri thức chưa khởi tạo"
        val name = args["name"]?.jsonPrimitive?.contentOrNull?.trim() ?: ""
        val content = args["content"]?.jsonPrimitive?.contentOrNull ?: ""
        if (name.isBlank()) return false to "kb_add cần tham số name"
        if (content.isBlank()) return false to "kb_add cần tham số content"
        return try {
            val docId = km.addDocument(name, content, source = "agent")
            val doc = km.documents.first().firstOrNull { it.id == docId }
            true to "Tài liệu kho tri thức 「$name」 đã được ghi (ID: $docId, phân khối ${doc?.chunkCount ?: 0}, trạng thái ${doc?.status ?: "embedding"}）。"
        } catch (e: Exception) {
            false to "Ghi kho tri thức thất bại: ${e.message ?: e::class.simpleName}"
        }
    }

    private suspend fun executeKnowledgeList(): Pair<Boolean, String> {
        val km = knowledgeManager ?: return false to "Kho tri thức chưa khởi tạo"
        return try {
            val docs = km.documents.first()
            if (docs.isEmpty()) {
                true to "Kho tri thức trống. Có thể thêm tài liệu qua kb_add."
            } else {
                val text = docs.joinToString("\n") { doc ->
                    "- ${doc.name} (nguồn ${doc.source} · phân khối ${doc.chunkCount} · trạng thái ${doc.status})"
                }
                true to "Kho tri thức có tổng ${docs.size} tài liệu:\n$text"
            }
        } catch (e: Exception) {
            false to "Đọc kho tri thức thất bại: ${e.message ?: e::class.simpleName}"
        }
    }

    companion object {
        const val MIN_BASE_TIMEOUT_SECONDS = 1L
        /** virtual_screen_* 的默认会话 ID。 */
        const val VIRTUAL_SCREEN_DEFAULT_SESSION = "default"
        // 前台单命令上限 15min：防模型把 timeout_seconds 拉到 1h 导致界面长时间"Như treo máy"；
        // 超过 15min 的全量编译/长构建应走后台 process（其 Long.MAX_VALUE 有 stop 管理，属合理设计）。
        const val MAX_BASE_TIMEOUT_SECONDS = 900L
        /** ask_user 问题请求的有效期：等待用户思考与决策，比操作类审批的 10 分钟宽裕。 */
        const val QUESTION_TTL_MS: Long = 30 * 60 * 1000L
        const val MAX_OUTPUT_LENGTH = 64 * 1024
        const val TRUNCATE_KEEP_LENGTH = 60 * 1024
        const val MAX_COMMAND_LENGTH = 32 * 1024
        const val MAX_ARG_LENGTH = 1024 * 1024

        /** use_capability inspect 清单的输出上限：超出截断并指引直接 call。 */
        const val MAX_INSPECT_CHARS = 16_000
        const val MAX_HISTORY_READ_OUTPUT = 48 * 1024

        /**
         * host 侧工具输出的字符硬上限（与 runtime EmbeddedAdbManager 的 ADB 路径保持同值）。
         * 200K 字符 ≈ 0.4-0.8MB 堆：足够容纳正常 logcat/dumpsys 片段，又能挡住数 MB 的
         * 节点树/全量 dump 把 Java 堆（256MB，未开 largeHeap 前）拖爆。
         */
        const val MAX_HOST_OUTPUT_CHARS = 200_000
        const val DEFAULT_CWD = "/root"
        const val DEFAULT_DOWNLOAD_ATTEMPTS = 3L
        const val MAX_DOWNLOAD_ATTEMPTS = 10L
        private val SETTINGS_KEY = Regex("^[A-Za-z0-9._-]{1,160}$")
        private val PACKAGE_NAME = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
        private val ANDROID_PERMISSION = Regex("^[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+$")
        private val APP_DATABASE_GUARDED_ACTIONS = setOf(
            "package_disable", "package_enable", "package_uninstall_user", "app_freeze", "app_unfreeze", "app_grant_permission",
        )
        private val LOGCAT_TAG = Regex("^[A-Za-z0-9_.-]{1,80}$")
        const val DEFAULT_DOWNLOAD_MAX_BYTES = 1024L * 1024L * 1024L
        const val MAX_DOWNLOAD_MAX_BYTES = 4L * 1024L * 1024L * 1024L

        private val IMAGE_DOWNLOAD_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp")
        const val PROGRESS_REPORT_INTERVAL_MS = 250L
        const val DEFAULT_PROCESS_LOG_LINES = 120L
        const val MAX_PROCESS_LOG_LINES = 500L
        const val AGENT_PROCESS_PREFIX = "agent-process:"
        val PROCESS_ID = Regex("[a-z0-9][a-z0-9._-]{0,63}")
        private val APK_PATH_REGEX = Regex("""(?:\/[\w.\-]+)+\.apk""")

        private fun isLikelyBuildCommand(cmd: String): Boolean {
            val lower = cmd.lowercase()
            return lower.contains("gradle") || lower.contains("assemble") || lower.contains("tianxuan-build") ||
                lower.contains("cargo build") || lower.contains("make") || lower.contains("cmake")
        }
    }
}

/**
 * host 侧输出截断（[ToolExecutor.MAX_HOST_OUTPUT_CHARS]）：超限时保留前缀并附重取指引。
 * [HostActionNodeExecutor] 等其他宿主输出出口共用，保证口径一致。
 */
internal fun capHostOutput(output: String): String =
    if (output.length <= ToolExecutor.MAX_HOST_OUTPUT_CHARS) {
        output
    } else {
        output.take(ToolExecutor.MAX_HOST_OUTPUT_CHARS) +
            "\n[Đầu ra host vượt giới hạn đã bị cắt: nguyên văn ${output.length} ký tự, chỉ giữ lại ${ToolExecutor.MAX_HOST_OUTPUT_CHARS} đầu tiên." +
            "Nếu cần nội dung đầy đủ hãy thu hẹp phạm vi và lấy lại: logcat dùng tag/keyword chính xác hơn và tail_lines ít hơn, " +
            "dumpsys chỉ định dịch vụ con (như dumpsys activity), tránh xuất toàn bộ]"
    }
