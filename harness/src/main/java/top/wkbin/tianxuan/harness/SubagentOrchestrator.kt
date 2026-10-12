package top.wkbin.tianxuan.harness

import java.util.concurrent.ConcurrentHashMap
import top.wkbin.tianxuan.core.database.AgentContextRepository
import top.wkbin.tianxuan.core.database.HarnessSessionRepository
import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.model.AgentSubagent
import top.wkbin.tianxuan.core.model.AgentSubagentIndexEntry
import top.wkbin.tianxuan.core.model.SubagentTaskSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.supervisorScope
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive
import top.wkbin.tianxuan.harness.session.LaneManager
import top.wkbin.tianxuan.harness.subagent.SubagentApprovalHandoff
import top.wkbin.tianxuan.harness.subagent.SubagentClaimAdjudication
import top.wkbin.tianxuan.harness.subagent.SubagentLaneRunner
import top.wkbin.tianxuan.harness.subagent.SubagentTermination
import top.wkbin.tianxuan.harness.subagent.adjudicateSubagentClaim
import top.wkbin.tianxuan.harness.subagent.buildSubagentTimeoutSummary
import top.wkbin.tianxuan.harness.subagent.declaresWriteIntent
import top.wkbin.tianxuan.harness.subagent.detectSuspectedShellWrites
import top.wkbin.tianxuan.harness.subagent.extractSubagentReceipts
import top.wkbin.tianxuan.harness.subagent.parseSubagentClaim
import top.wkbin.tianxuan.harness.subagent.renderSubagentClaimAdjudication
import top.wkbin.tianxuan.harness.subagent.stripSubagentClaimBlock
import top.wkbin.tianxuan.harness.prompt.PromptAssetLoader
import top.wkbin.tianxuan.harness.WorkspaceFileAccess

internal class SubagentConcurrencyGate(
    maxParallelism: Int = DEFAULT_MAX_CONCURRENT_SUBAGENTS,
) {
    private val permits = Semaphore(maxParallelism.coerceAtLeast(1))

    suspend fun <T> withPermit(block: suspend () -> T): T = permits.withPermit { block() }
}

internal const val DEFAULT_MAX_CONCURRENT_SUBAGENTS = 3

/**
 * Subagent 子智能体任务编排器：
 * 负责解析主智能体的 invoke_subagent 请求，动态创建隔离的子会话，
 * 并发调度子智能体执行研究、编写或测试任务，最终汇聚输出结构化 Markdown。
 */
class SubagentOrchestrator(
    private val sessionDao: HarnessSessionRepository,
    private val laneManager: LaneManager,
    private val laneRunner: SubagentLaneRunner,
    private val subagentRepository: top.wkbin.tianxuan.core.database.AgentSubagentRepository,
    private val promptAssets: PromptAssetLoader,
    private val agentContextRepo: AgentContextRepository,
    private val fileAccess: WorkspaceFileAccess,
    private val providerClient: ProviderClient,
    private val logger: AppLogger,
) {
    /**
     * Application-wide budget: a three-agent fan-out should actually run three lanes at once,
     * while larger batches still queue to protect the shared API/PRoot/Room resources.
     */
    private val globalParallelism = SubagentConcurrencyGate()
    private val resumeLaneLocks = ConcurrentHashMap<String, Mutex>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun executeSubagents(
        args: JsonObject,
        parentSessionId: String,
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val specs = SubagentArgsParser.parse(args)
        if (specs.isEmpty()) {
            return@withContext false to "Không phân giải được danh sách nhiệm vụ subagents hợp lệ, vui lòng kiểm tra tham số"
        }
        val background = args["background"]?.jsonPrimitive?.booleanOrNull == true
        if (background) {
            return@withContext false to
                "background=true tạm thời không khả dụng: nhiệm vụ con nền trên di động chưa tích hợp khôi phục lô bền vững và giữ sống dịch vụ tiền cảnh." +
                "Để tránh nhiệm vụ bị mất âm thầm sau khi ứng dụng vào nền, lần này không khởi động nhiệm vụ con nào; hãy bỏ background và thử lại ở chế độ tiền cảnh."
        }
        runBatch(specs, parentSessionId)
    }

    private suspend fun runBatch(
        specs: List<SubagentTaskSpec>,
        parentSessionId: String,
    ): Pair<Boolean, String> {
        return supervisorScope {
        val parentSession = sessionDao.findById(parentSessionId)
        val workspace = parentSession?.workspace.orEmpty()
        val modelId = parentSession?.modelId
        val modelVariant = parentSession?.modelVariant
        val profileIndex = subagentRepository.enabledIndex()
        if (profileIndex.isEmpty()) {
            return@supervisorScope false to "Hiện chưa có vai trò Agent con nào được bật, vui lòng thêm hoặc bật vai trò trong Cài đặt Agent trước"
        }
        val parentLeaf = laneManager.get(parentSessionId, "main")?.leafId

        // 写租约协调：把任务按 write_paths 冲突切成若干"Ghi không xung đột"的波。
        // 同一波内的写路径互不相交 → 可并行（受 globalParallelism 约束）；跨波顺序执行。
        // 未声明 writePaths 的任务为只读任务，可彼此并行；["*"] 才表示整工作区独占写租约。
        val results = mutableListOf<SubagentExecutionOutcome>()
        val waves = buildWriteCleanWaves(specs)
        logger.logAgent(
            parentSessionId,
            "SubagentSchedule",
            "tasks=${specs.size}, waves=${waves.size}, maxParallelism=$DEFAULT_MAX_CONCURRENT_SUBAGENTS, " +
                "waveSizes=${waves.joinToString(prefix = "[", postfix = "]") { it.size.toString() }}",
        )
        for ((waveIndex, wave) in waves.withIndex()) {
            val waveStartedAt = System.nanoTime()
            logger.logAgent(
                parentSessionId,
                "SubagentWave",
                "start=${waveIndex + 1}/${waves.size}, tasks=${wave.joinToString { it.taskName }}; " +
                    "lease wave=${waveLeaseDescription(wave)}; tasks in this wave may run concurrently, later waves wait",
            )
            val waveResults = wave.map { spec ->
                async {
                    globalParallelism.withPermit {
                        runSubagentSafely(spec, parentSessionId, parentLeaf, workspace, modelId, modelVariant, profileIndex)
                    }
                }
            }.awaitAll()
            results += waveResults
            logger.logAgent(
                parentSessionId,
                "SubagentWave",
                "complete=${waveIndex + 1}/${waves.size}, elapsedMs=${(System.nanoTime() - waveStartedAt) / 1_000_000}, " +
                    "results=${waveResults.joinToString { "${it.spec.taskName}:${it.termination}" }}",
            )
        }

        // Completion order and lease waves must not change the user-requested presentation order.
        val orderedResults = results.sortedBy { outcome -> specs.indexOf(outcome.spec) }
        val summaryMarkdown = paginateSummary(orderedResults, workspace)
        logger.logAgent(
            parentSessionId,
            "SubagentBatch",
            orderedResults.joinToString("; ") { outcome ->
                "task=${outcome.spec.taskName}, success=${outcome.isSuccess}, termination=${outcome.termination}, " +
                    "toolCalls=${outcome.toolCallCount}, pendingApprovals=${outcome.pendingApprovals.size}, " +
                    "blockedWrites=${outcome.blockedWrites.size}, " +
                    "readOnlyWriteIntent=${outcome.readOnlyWriteIntent}, " +
                    "suspectedShellWrites=${outcome.suspectedShellWrites.joinToString("|")}"
            },
        )
        // 失败原因必须与上面的批次日志同等、无门控地落盘：子智能体的终止原因原先只经
        // AgentEventLogger（受 agentLoggingEnabled 门控，默认关闭），批次行里也只有一句
        // termination=FAILED，于是"Agent con được phái luôn thất bại toàn bộ"既拿不到具体异常，也无法
        // 区分不可重试 4xx / 限流耗尽 / 上下文超限 / 超时 / 轮数耗尽。摘要是唯一的线索来源。
        orderedResults.filterNot { it.isSuccess }.forEach { outcome ->
            logger.logAgent(
                parentSessionId,
                "SubagentFailure",
                "task=${outcome.spec.taskName}, termination=${outcome.termination}, " +
                    "toolCalls=${outcome.toolCallCount}, reason=" +
                    outcome.summary.lineSequence().firstOrNull()?.take(300).orEmpty(),
            )
        }
        // 整批工具结果只有在每个子任务都确认完成时才算成功。用 anySuccess 会让"1 thành công 5 thất bại"
        // 在父会话里显示为成功工具调用，模型据此继续往下走，正文里的部分失败说明形同虚设。
        val allSucceeded = orderedResults.all { it.isSuccess }
        allSucceeded to summaryMarkdown
        }
    }

    private suspend fun runSubagentSafely(
        spec: SubagentTaskSpec,
        parentSessionId: String,
        parentLeaf: String?,
        workspace: String,
        modelId: String?,
        modelVariant: String?,
        profileIndex: List<AgentSubagentIndexEntry>,
    ): SubagentExecutionOutcome = try {
        runSubagent(spec, parentSessionId, parentLeaf, workspace, modelId, modelVariant, profileIndex)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        SubagentExecutionOutcome(
            spec = spec,
            subSessionId = spec.taskId.orEmpty(),
            isSuccess = false,
            summary = "Nhiệm vụ con thực thi bất thường (${failure::class.simpleName ?: "Throwable"}）：" +
                (failure.message ?: "Không có thông báo lỗi"),
            toolCallCount = 0,
            termination = SubagentTermination.FAILED,
        )
    }

    private suspend fun runSubagent(
        spec: SubagentTaskSpec,
        parentSessionId: String,
        parentLeaf: String?,
        workspace: String,
        modelId: String?,
        modelVariant: String?,
        profileIndex: List<AgentSubagentIndexEntry>,
    ): SubagentExecutionOutcome {
        // task_id 续跑（对齐 opencode task 工具）：跳过 create，直接在同名 Lane 已有
        // 对话上追加本轮指令；角色取自 lane 名内嵌的 profileId，忽略本次派发参数。
        val resumeLaneName = spec.taskId?.trim()?.takeIf { it.isNotBlank() }
        val laneName: String
        val profile: AgentSubagent
        if (resumeLaneName != null) {
            val existing = laneManager.get(parentSessionId, resumeLaneName)
            if (existing == null || !existing.name.startsWith("subagent:")) {
                return SubagentExecutionOutcome(
                    spec = spec,
                    subSessionId = resumeLaneName,
                    isSuccess = false,
                    summary = "task_id=$resumeLaneName không tồn tại hoặc không phải Lane nhiệm vụ con của phiên này, không thể chạy tiếp." +
                        "Vui lòng sao chép nguyên văn task_id từ bản tổng hợp invoke_subagent gần nhất; khi Lane đã bị xóa thì chuyển sang phái nhiệm vụ hoàn toàn mới.",
                    toolCallCount = 0,
                    termination = SubagentTermination.INCOMPLETE,
                )
            }
            val resumedProfile = subagentRepository.findEnabledProfile(
                resumeLaneName.removePrefix("subagent:").substringBeforeLast(':'),
            )
            if (resumedProfile == null) {
                return SubagentExecutionOutcome(
                    spec = spec,
                    subSessionId = resumeLaneName,
                    isSuccess = false,
                    summary = "Vai trò chạy tiếp Lane đã bị vô hiệu hóa hoặc xóa, không thể chạy tiếp; vui lòng chuyển sang phái nhiệm vụ hoàn toàn mới.",
                    toolCallCount = 0,
                    termination = SubagentTermination.FAILED,
                )
            }
            profile = resumedProfile
            laneName = resumeLaneName
        } else {
            val resolved = resolveProfile(spec, profileIndex)
            if (resolved == null) {
                return SubagentExecutionOutcome(
                    spec = spec,
                    subSessionId = "",
                    isSuccess = false,
                    summary = routingFailure(spec),
                    toolCallCount = 0,
                    termination = SubagentTermination.FAILED,
                )
            }
            profile = resolved
            laneName = "subagent:${profile.id}:${java.util.UUID.randomUUID()}"
            laneManager.create(parentSessionId, laneName, parentLeaf)
        }
        val readOnlyWriteIntent = spec.writePaths.isEmpty() && declaresWriteIntent(spec.prompt)
        val prompt = if (resumeLaneName != null) {
            buildSubagentResumePrompt(spec, workspace, parentSessionId, readOnlyWriteIntent)
        } else {
            buildSubagentPrompt(spec, profile, workspace, parentSessionId, readOnlyWriteIntent)
        }
        if (readOnlyWriteIntent) {
            logger.logAgent(
                parentSessionId,
                "SubagentWriteScope",
                "task=${spec.taskName} văn bản nhiệm vụ yêu cầu ghi đĩa nhưng chưa khai báo write_paths, Lane này thực thi theo chỉ đọc",
            )
        }
        val targetModel = runCatching {
            when (val route = selectSubagentModel(spec.model, profile.defaultModelId, profile.defaultModelVariant)) {
                SubagentModelRoute.Inherit -> providerClient.resolveRequestedModel(null, modelId, modelVariant)
                is SubagentModelRoute.Requested -> providerClient.resolveRequestedModel(route.selection, modelId, modelVariant)
                is SubagentModelRoute.RoleDefault -> providerClient.resolveSavedModelProfile(route.profileId, route.variant)
            }
        }.getOrElse { failure ->
            return SubagentExecutionOutcome(
                spec = spec,
                subSessionId = resumeLaneName ?: "",
                isSuccess = false,
                summary = failure.message ?: "Không thể phân giải model Agent con",
                toolCallCount = 0,
                resolvedProfileId = profile.id,
                resolvedProfileName = profile.name,
                termination = SubagentTermination.FAILED,
            )
        }
        val laneLockKey = "$parentSessionId::$laneName"
        val laneLock = resumeLaneLocks.getOrPut(laneLockKey) { Mutex() }
        if (!laneLock.tryLock()) {
            return SubagentExecutionOutcome(
                spec = spec,
                subSessionId = laneName,
                isSuccess = false,
                summary = "task_id=$laneName đang chạy, từ chối tiếp chạy đồng thời; vui lòng chờ thực thi hiện tại hoàn tất rồi gửi chỉ thị mới.",
                toolCallCount = 0,
                resolvedProfileId = profile.id,
                resolvedProfileName = profile.name,
                termination = SubagentTermination.INCOMPLETE,
            )
        }
        val laneResult = try {
            withTimeoutOrNull(SUBAGENT_TIMEOUT_MS) {
                laneRunner.run(
                    parentSessionId,
                    laneName,
                    prompt,
                    workspace,
                    modelConfig = targetModel,
                    writePaths = spec.writePaths,
                )
            }
        } finally {
            laneLock.unlock()
        }

        // 超时取消时 withTimeoutOrNull 返回 null，若直接 ?: 0 会把子智能体在超时窗口内
        // 真实执行的工具调用全部归零，汇总里出现"Rõ ràng đang chạy mà hiển thị 0 lần gọi công cụ"。
        // lane 的 tool_call entry 在执行期已逐条落库，从 transcript 恢复真实计数。
        val transcript = runCatching { laneManager.subagentTranscript(parentSessionId, laneName) }
            .getOrElse { runCatching { laneManager.transcript(parentSessionId, laneName) }.getOrDefault(emptyList()) }
        val toolCallCount = resolveSubagentToolCallCount(laneResult?.toolCallCount, transcript)

        var outcome = SubagentExecutionOutcome(
            spec = spec,
            subSessionId = laneName,
            isSuccess = laneResult?.success == true,
            // 超时不再只回一句"Thực thi quá thời gian"：已查到的证据、改过的文件、最后进度都从 transcript
            // 还原成续跑摘要，否则父智能体只能创建全新 Lane 从头重复同样的工作。
            summary = laneResult?.summary
                ?: buildSubagentTimeoutSummary(SUBAGENT_TIMEOUT_MS, toolCallCount, transcript, laneName),
            toolCallCount = toolCallCount,
            resolvedProfileId = profile.id,
            resolvedProfileName = profile.name,
            resolvedModel = "${targetModel.provider}/${targetModel.model}",
            termination = laneResult?.termination ?: SubagentTermination.TIMEOUT,
            pendingApprovals = laneResult?.pendingApprovals.orEmpty(),
            blockedWrites = laneResult?.blockedWrites.orEmpty(),
            readOnlyWriteIntent = readOnlyWriteIntent,
            tokenUsage = SubagentTokenUsage.extractFrom(transcript),
            suspectedShellWrites = detectSuspectedShellWrites(transcript, spec.writePaths),
        )
        // 完成 claim 的 host 裁定：只对 host 已判 CONCLUDED 的结论生效，且只降级不升格。
        // claim 缺失/解析失败 = 旧行为原样保留（fail-open）。
        if (outcome.isSuccess && laneResult != null) {
            val claim = parseSubagentClaim(laneResult.summary)
            if (claim != null) {
                val adjudication = adjudicateSubagentClaim(claim, extractSubagentReceipts(transcript))
                // 剔除协议块后正文可能为空（整条结论只有一个 claim 块）：回退 claim.summary，
                // 再不行保留原文——父汇总的「子任务输出」不能是空串。
                val strippedSummary = stripSubagentClaimBlock(laneResult.summary, claim)
                    .ifBlank { claim.summaryText.ifBlank { laneResult.summary } }
                outcome = outcome.copy(
                    // 只有 host 最终裁定 complete 才算成功；partial/failed 不得升格。
                    isSuccess = adjudication.adjudicatedStatus == "complete",
                    termination = when {
                        adjudication.downgraded -> SubagentTermination.CLAIM_DOWNGRADED
                        adjudication.adjudicatedStatus == "partial" -> SubagentTermination.INCOMPLETE
                        adjudication.adjudicatedStatus == "failed" -> SubagentTermination.FAILED
                        else -> outcome.termination
                    },
                    // 原始 claim JSON 不进父上下文：正文剔除协议块，裁定结论进状态头。
                    summary = strippedSummary,
                    claimAdjudication = adjudication,
                )
            }
        }
        return outcome
    }


    private suspend fun resolveProfile(
        spec: SubagentTaskSpec,
        profileIndex: List<AgentSubagentIndexEntry>,
    ): AgentSubagent? {
        val selected = if (spec.role.isNotBlank()) {
            profileIndex.firstOrNull { entry ->
                entry.id.equals(spec.role, ignoreCase = true) ||
                    entry.name.equals(spec.role, ignoreCase = true)
            }
        } else {
            SubagentProfileMatcher.match(profileIndex, spec.department, spec.agentQuery)
        } ?: return null
        return subagentRepository.findEnabledProfile(selected.id)
    }

    private fun routingFailure(spec: SubagentTaskSpec): String = if (spec.role.isNotBlank()) {
        "Vai trò chính xác ${spec.role} chưa cấu hình hoặc chưa bật. Có thể chuyển dùng department + agentQuery để chỉ mục cục bộ phái đi."
    } else {
        "Trong bộ phận ${spec.department} không có vai trò đã bật nào khớp agentQuery=\"${spec.agentQuery}\"." +
            "Hãy giữ bộ phận và chuyển dùng 2–5 từ khóa chuyên môn tiếng Anh cụ thể hơn."
    }

    private suspend fun buildSubagentPrompt(
        spec: SubagentTaskSpec,
        profile: AgentSubagent,
        workspace: String,
        parentSessionId: String,
        readOnlyWriteIntent: Boolean,
    ): String {
        val factsPack = buildParentFactsPack(parentSessionId, workspace)
        val writeLine = buildWriteLeaseLine(spec, readOnlyWriteIntent)
        return promptAssets.render(
            "prompts/subagent_task.md",
            mapOf(
                "ROLE_NAME" to profile.name,
                "ROLE_ID" to profile.id,
                "TASK_NAME" to spec.taskName,
                "WORKSPACE_LINE" to workspace.takeIf { it.isNotBlank() }?.let { "Vùng làm việc: $it" }.orEmpty(),
                "ROLE_PROMPT" to profile.systemPrompt.trim(),
                "TASK_PROMPT" to spec.prompt,
                "WRITE_LINE" to writeLine,
                "FACTS_PACK" to factsPack,
            ),
        )
    }

    /** 写租约约束文案：全新派发与续跑共用同一份语义（空=只读、精确路径=局部租约、["*"]=独占）。 */
    private fun buildWriteLeaseLine(spec: SubagentTaskSpec, readOnlyWriteIntent: Boolean): String = when {
        spec.writePaths.isEmpty() -> buildString {
            append("Nhiệm vụ này là nhiệm vụ chỉ đọc: cấm gọi write/edit/download, cấm thực thi lệnh sửa workspace; chỉ trả về phân tích hoặc dữ liệu.")
            append("write/edit/download trong Lane này sẽ bị chặn bắt buộc; lệnh base không bị ràng buộc bởi cổng đó,")
            append("Do đó tuyệt đối không được bỏ qua bằng các cách như chuyển hướng shell, sed -i, mv, rm.")
            if (readOnlyWriteIntent) {
                // 任务文字要求落盘却没有写租约：必须显式指出冲突，否则子智能体会在
                // "Cần ghi"与"Không cho ghi"之间自行猜测，并可能把"Không thể ghi"当成完成。
                append("\nLưu ý: văn bản nhiệm vụ này có nhắc tới ghi đĩa/ghi, nhưng Agent chính chưa khai báo write_paths.")
                append("Vui lòng đặt toàn bộ nội dung cần ghi ra đĩa trực tiếp vào phần thân kết luận (gồm đường dẫn mục tiêu và nội dung tệp đầy đủ),")
                append("Do Agent chính ghi vào; đừng tuyên bố tệp đã được tạo.")
            }
        }
        spec.writePaths.any(::isWholeWorkspaceWritePath) ->
            "Nhiệm vụ này giữ write lease độc quyền toàn workspace; chỉ sửa các tệp nhiệm vụ thực sự cần, tránh thay đổi không liên quan."
        else -> buildString {
            append("Giới hạn phạm vi ghi (write/edit/download sẽ bắt buộc kiểm tra, ghi vượt phạm vi bị chặn trực tiếp): ")
            append(spec.writePaths.joinToString("、"))
            append(". Lệnh base không bị ràng buộc bởi cổng đó, cũng phải tuân thủ cùng phạm vi.")
        }
    }

    /**
     * 续跑指令：Lane 已持有原任务的完整上下文（角色提示词、任务原文、前序工具记录），
     * 这里只注入新指令与写租约提醒，不再重放整份任务模板。
     */
    private suspend fun buildSubagentResumePrompt(
        spec: SubagentTaskSpec,
        workspace: String,
        parentSessionId: String,
        readOnlyWriteIntent: Boolean,
    ): String {
        val factsPack = buildParentFactsPack(parentSessionId, workspace)
        return buildString {
            append("【Chạy tiếp nhiệm vụ】Nhiệm vụ gốc「${spec.taskName}」chưa hoàn tất, Agent chính yêu cầu tiếp tục trên nền công việc đã có trong Lane này:\n")
            append(spec.prompt.trim())
            append("\n\n")
            append(buildWriteLeaseLine(spec, readOnlyWriteIntent))
            if (factsPack.isNotBlank()) append("\n\n$factsPack")
        }
    }

    /** 父级 facts pack：把父会话已 pin 的长期指令/事实浓缩为一段低体积背景，而非整段父 transcript。 */
    private suspend fun buildParentFactsPack(parentSessionId: String, workspace: String): String {
        val pinned = runCatching {
            agentContextRepo.getPinnedMemories(
                projectOwnerId = workspace.trim().trimEnd('/'),
                sessionId = parentSessionId,
            )
        }.getOrDefault(emptyList())
        if (pinned.isEmpty()) return ""
        return "## Gói sự kiện ngữ cảnh cấp cha (chỉ thị/sự kiện đã pin của phiên cha, làm tham chiếu nền cho nhiệm vụ con này)\n" +
            pinned.joinToString("\n") { "- [${it.scope}/${it.kind}] ${it.key}: ${it.value}" }
    }

    private companion object {
        // 15 分钟：移动端复杂分析任务（大文件读取 + 多轮工具调用 + 长文本产出）
        // 实测 6 分钟不够用，超时前往往仍在正常执行中途。
        const val SUBAGENT_TIMEOUT_MS = 15 * 60 * 1000L
    }

    /**
     * 结果分页读取：汇总注入父上下文前先做预算控制。
     * 总量 ≤ [SUMMARY_INLINE_BUDGET] 字符 → 原样注入（保持现状，不破坏小批次体验）；
     * 超限 → 每个子任务输出截断为 [PER_TASK_INLINE_BUDGET] 字符，
     * 完整结果落盘 `.tianxuan-subagent/<laneName-safe>.md`（工作区相对路径），
     * 模型可用 read 工具按 offset/limit 分页读取。
     */
    private suspend fun paginateSummary(outcomes: List<SubagentExecutionOutcome>, workspace: String): String =
        paginateSubagentSummary(
            outcomes,
            workspace,
            // 落盘必须与 ToolExecutor 的 read 走同一基准：汇总里给父智能体的是工作区相对路径，
            // 用全局 fileAccess 写会落到应用根目录，模型随后 read 就会"Báo là có nhưng thực tế đọc không được"。
            if (workspace.isNotBlank()) fileAccess.withBase(workspace) else fileAccess,
        )

    internal data class SubagentExecutionOutcome(
        val spec: SubagentTaskSpec,
        val subSessionId: String,
        val isSuccess: Boolean,
        val summary: String,
        val toolCallCount: Int,
        val resolvedProfileId: String? = null,
        val resolvedProfileName: String? = null,
        val resolvedModel: String? = null,
        val termination: SubagentTermination = SubagentTermination.CONCLUDED,
        val pendingApprovals: List<SubagentApprovalHandoff> = emptyList(),
        val blockedWrites: List<String> = emptyList(),
        /** 任务文字要求落盘但未声明 write_paths，本次按只读执行。 */
        val readOnlyWriteIntent: Boolean = false,
        /**
         * 完成 claim 的 host 裁定。null = 模型未提交结构化 claim（旧行为不变）；
         * 非 null 时 [SubagentClaimAdjudication.adjudicatedStatus] 是交给父智能体的唯一可信状态。
         */
        val claimAdjudication: SubagentClaimAdjudication? = null,
        /** 委托经济学：本 lane 的 token 用量（含缓存命中），从 transcript 的 AssistantText 聚合。 */
        val tokenUsage: SubagentTokenUsage? = null,
        /** 疑似租约外 shell 写命令（软警告，无法静态核验范围）；整工作区租约恒为空。 */
        val suspectedShellWrites: List<String> = emptyList(),
    )
}

/**
 * 单个子代理 lane 的 token 用量：
 * 缓存命中单列，让"Ủy thác có đáng không"有数据可看，而不是只看轮数。
 */
internal data class SubagentTokenUsage(
    val promptTokens: Long,
    val cachedTokens: Long,
    val completionTokens: Long,
) {
    /** 缓存命中率（0–100）；无输入时为 0。 */
    val cacheHitPercent: Int
        get() = if (promptTokens > 0) (cachedTokens * 100 / promptTokens).toInt() else 0

    companion object {
        /** 从 lane transcript 聚合；整条 lane 无用量记录（旧数据/失败早终止）返回 null。 */
        internal fun extractFrom(transcript: List<HarnessMessage>): SubagentTokenUsage? {
            var prompt = 0L
            var cached = 0L
            var completion = 0L
            transcript.filterIsInstance<AssistantText>().forEach { text ->
                prompt += text.promptTokens ?: 0
                cached += text.cachedTokens ?: 0
                completion += text.completionTokens ?: 0
            }
            if (prompt <= 0L && completion <= 0L) return null
            return SubagentTokenUsage(prompt, cached, completion)
        }
    }
}

internal sealed interface SubagentModelRoute {
    data object Inherit : SubagentModelRoute
    data class Requested(val selection: String) : SubagentModelRoute
    data class RoleDefault(val profileId: String, val variant: String?) : SubagentModelRoute
}

/** Explicit `inherit` bypasses a role default; an omitted task selection uses that default. */
internal fun selectSubagentModel(
    taskSelection: String?,
    roleDefaultModelId: String?,
    roleDefaultModelVariant: String?,
): SubagentModelRoute {
    val requested = taskSelection?.trim().orEmpty()
    if (requested.equals("inherit", ignoreCase = true)) return SubagentModelRoute.Inherit
    if (requested.isNotBlank()) return SubagentModelRoute.Requested(requested)
    val profileId = roleDefaultModelId?.trim().orEmpty()
    return if (profileId.isNotBlank()) {
        SubagentModelRoute.RoleDefault(profileId, roleDefaultModelVariant?.trim()?.takeIf { it.isNotBlank() })
    } else {
        SubagentModelRoute.Inherit
    }
}


/** background=true 时立即返回给模型的启动确认：明确"Không polling"（对齐 opencode task 工具的输出约定）。 */
internal fun backgroundStartedText(specs: List<SubagentTaskSpec>): String = buildString {
    append("Đã khởi động ${specs.size} nhiệm vụ con nền: ${specs.joinToString("、") { it.taskName }}。\n")
    append("Nhiệm vụ thực thi ở nền, sau khi hoàn thành kết quả sẽ tự động được đưa vào phiên này và kích hoạt suy luận tiếp theo.")
    append("Đừng chờ, poll tiến độ hay phái lại cùng nhiệm vụ; có thể xử lý việc độc lập khác trước, hoặc kết thúc lượt phản hồi này luôn.")
}

/** 后台批次完成后的注入文本：汇总 + 续跑指引；由 HarnessLoop.send 以用户消息触发新轮推理。 */
internal fun backgroundCompletionText(allSucceeded: Boolean, summaryMarkdown: String): String = buildString {
    append("【Nhiệm vụ con nền đã hoàn thành】\n\n")
    append(summaryMarkdown.trim())
    append("\n\n---\nTrên đây là kết quả nhiệm vụ con nền, hãy dựa vào đó tiếp tục đẩy nhiệm vụ gốc.")
    if (!allSucceeded) {
        append("Nhiệm vụ con thất bại có thể tiếp chạy trong lời gọi invoke_subagent mới bằng task_id của nó (kèm chỉ thị mới), hoặc phái lại nhiệm vụ hoàn toàn mới.")
    }
}

/**
 * 将任务按 write_paths 冲突切成若干互不相交的"Ba"。
 * 同一波内所有任务的写路径集合互不重叠 → 可安全并行执行。
 * 空 writePaths 是只读任务，可与其他只读任务并行；["*"] 是整工作区独占写租约。
 * 为避免读到写入中间态，只读波与任何写入波分离；局部写路径互不冲突时可并行。
 */
internal fun buildWriteCleanWaves(specs: List<SubagentTaskSpec>): List<List<SubagentTaskSpec>> {
    val waves = mutableListOf<MutableList<SubagentTaskSpec>>()
    val waveKinds = mutableListOf<SubagentWaveKind>()
    val wavePaths = mutableListOf<MutableSet<String>>() // 该波已占用的写路径
    for (spec in specs) {
        val paths = spec.writePaths.mapTo(hashSetOf()) { normalizeWritePath(it) }
        val kind = when {
            paths.isEmpty() -> SubagentWaveKind.READ_ONLY
            paths.any { it in WHOLE_WORKSPACE_SCOPES } -> SubagentWaveKind.WHOLE_WORKSPACE
            else -> SubagentWaveKind.SCOPED_WRITE
        }
        // 只读任务统一并入首个只读波（SubagentWritePathTest 编码了该语义：只读先行并行、
        // 与写入波隔离避免读到中间态）；声明在写任务之后的只读任务也会前置，
        // 需要校验写入结果的场景应由上层任务拆分时显式声明写路径。
        val joinIndex = waves.indices.firstOrNull { i ->
            when (kind) {
                SubagentWaveKind.READ_ONLY -> waveKinds[i] == SubagentWaveKind.READ_ONLY
                SubagentWaveKind.WHOLE_WORKSPACE -> false
                SubagentWaveKind.SCOPED_WRITE ->
                    waveKinds[i] == SubagentWaveKind.SCOPED_WRITE &&
                        wavePaths[i].none { existing -> paths.any { candidate -> writePathsConflict(existing, candidate) } }
            }
        }
        if (joinIndex != null) {
            waves[joinIndex].add(spec)
            wavePaths[joinIndex].addAll(paths)
        } else {
            waves.add(mutableListOf(spec))
            waveKinds.add(kind)
            wavePaths.add(paths)
        }
    }
    return waves
}

/**
 * 规范化写路径：统一分隔符、按段消解 `.` 与 `..`、去掉首尾斜杠，保证跨子任务的路径比较稳定。
 *
 * 必须按段消解 `..`：只做字符串裁剪时 `docs` 与 `docs/../src` 会被判为互不冲突而排进同一波，
 * 写租约的冲突检测形同虚设；同理租约校验里 `docs/../x` 会因前缀匹配被误放行。
 * （真正写文件时 [WorkspaceFileAccess] 还会拒绝含 `..` 的路径，这里是让租约层自身即可判定。）
 * 逃逸出顶层的 `..` 原样保留，由调用方按越界处理。
 */
internal fun normalizeWritePath(path: String): String {
    val segments = ArrayDeque<String>()
    var escaped = 0
    path.trim().replace('\\', '/').split('/').forEach { segment ->
        when {
            segment.isEmpty() || segment == "." -> Unit
            segment == ".." -> if (segments.isEmpty()) escaped++ else segments.removeLast()
            else -> segments.addLast(segment)
        }
    }
    return "../".repeat(escaped) + segments.joinToString("/")
}

private enum class SubagentWaveKind { READ_ONLY, SCOPED_WRITE, WHOLE_WORKSPACE }

private fun waveLeaseDescription(wave: List<SubagentTaskSpec>): String = when {
    wave.all { it.writePaths.isEmpty() } -> "read-only"
    wave.any { it.writePaths.any(::isWholeWorkspaceWritePath) } -> "whole-workspace exclusive"
    else -> "scoped disjoint write paths"
}

/**
 * 是否为整工作区租约。`*` 与 `.` 都表示整个工作区；`.` 归一化后为空串，
 * 空串同样是"Gốc vùng làm việc"，不能当成未声明。
 */
internal fun isWholeWorkspaceWritePath(path: String): Boolean = normalizeWritePath(path) in WHOLE_WORKSPACE_SCOPES

private val WHOLE_WORKSPACE_SCOPES = setOf("*", "")

internal fun writePathsConflict(left: String, right: String): Boolean {
    val a = normalizeWritePath(left)
    val b = normalizeWritePath(right)
    if (a in WHOLE_WORKSPACE_SCOPES || b in WHOLE_WORKSPACE_SCOPES) return true
    return a == b || a.startsWith("$b/") || b.startsWith("$a/")
}

/**
 * 超时取消时 laneRunner 协程被中止，其内存中的工具调用统计随返回值一起丢失；
 * 此时从 lane 已落库的 transcript（tool_call entry）恢复真实计数，
 * 避免汇总里出现"Agent con rõ ràng đang làm việc nhưng hiển thị 0 lần gọi công cụ"。
 */
internal fun resolveSubagentToolCallCount(
    laneResultToolCalls: Int?,
    persistedTranscript: List<HarnessMessage>,
): Int = laneResultToolCalls ?: persistedTranscript.count { it is ToolCall }

/** 汇总注入父上下文的字符预算；超出即走截断+落盘分页。 */
internal const val SUMMARY_INLINE_BUDGET = 12_000

/** 超限时分任务的截断保留长度。 */
internal const val PER_TASK_INLINE_BUDGET = 3_000

/** 子智能体汇总 Markdown：状态行 + 每任务的输出。 */
internal fun renderSummaryMarkdown(outcomes: List<SubagentOrchestrator.SubagentExecutionOutcome>): String =
    buildString {
        append(subagentBatchHeader(outcomes))
        outcomes.forEachIndexed { index, outcome ->
            append(subagentOutcomeHeader(outcome, index, includeModelBadge = true))
            append("- **Đầu ra nhiệm vụ con**:\n")
            append(outcome.summary.trim())
            append("\n\n")
        }
    }

private fun subagentBatchHeader(outcomes: List<SubagentOrchestrator.SubagentExecutionOutcome>): String {
    val succeeded = outcomes.count { it.isSuccess }
    val batchStatus = when (succeeded) {
        outcomes.size -> "Tất cả thành công"
        0 -> "Tất cả thất bại"
        else -> "Thành công một phần"
    }
    return buildString {
        append("### 🤖 Phối hợp thực thi Agent con hoàn tất · $batchStatus ($succeeded/${outcomes.size})\n\n")
        if (succeeded in 1 until outcomes.size) {
            // 整批 ToolResult 标失败（一个没做完就不能算成功），但重发时不该把已完成的再跑一遍。
            append("(Lô này thành công một phần: chỉ cần xử lý các nhiệm vụ con đánh dấu ⚠️ bên dưới. Khi phái lại chỉ nộp những nhiệm vụ đó,")
            append("Đừng lặp lại công việc đã đánh ✅; sản phẩm của chúng đã có trong bản tổng hợp này.)\n\n")
        }
    }
}

/**
 * 每个子任务的状态头。
 *
 * 状态不再只有 ✅/⚠️ 两态：未完成时必须写出终止原因、待审批交接与被拦截的写入，
 * 否则父智能体（和用户）无法区分"Làm xong rồi"与"Đã thu lại nhưng chưa làm xong"。
 */
private fun subagentOutcomeHeader(
    outcome: SubagentOrchestrator.SubagentExecutionOutcome,
    index: Int,
    includeModelBadge: Boolean,
): String = buildString {
    val statusIcon = if (outcome.isSuccess) "✅" else "⚠️"
    val resolvedRole = if (outcome.resolvedProfileId != null) {
        "${outcome.resolvedProfileName} · ${outcome.resolvedProfileId}"
    } else {
        outcome.spec.role.ifBlank { "${outcome.spec.department} / ${outcome.spec.agentQuery}" }
    }
    val modelBadge = if (includeModelBadge) outcome.resolvedModel?.let { " · Model chuyên dụng: $it" } ?: "" else ""
    append("#### ${index + 1}. $statusIcon 【${outcome.spec.taskName}】(vai trò: $resolvedRole$modelBadge)\n")
    append("- **Số lần gọi công cụ**: ${outcome.toolCallCount} lần\n")
    // task_id 暴露给父智能体：未完结的子任务可凭它在同一 Lane 上续跑（对齐 opencode task 工具）。
    if (outcome.subSessionId.isNotBlank()) {
        append("- **task_id** (dùng để chạy tiếp, sao chép nguyên văn vào trường task_id của invoke_subagent): ${outcome.subSessionId}\n")
    }
    // 委托经济学：把成本摊开给父智能体与用户看——缓存命中率低的大输入是委派不划算的主要信号。
    outcome.tokenUsage?.let { usage ->
        append(
            "- **Mức dùng Token**: đầu vào ${usage.promptTokens} (cache hit ${usage.cachedTokens}, " +
                "${usage.cacheHitPercent}%) · đầu ra ${usage.completionTokens}\n",
        )
    }
    if (!outcome.isSuccess) {
        append("- **Nguyên nhân dừng**: ${subagentTerminationLabel(outcome.termination)}\n")
    }
    // 完成 claim 裁定：无论最终状态如何都展示——✅ 也让父智能体看到"Credential đã chứng thực gì"。
    if (outcome.claimAdjudication != null) {
        append(renderSubagentClaimAdjudication(outcome.claimAdjudication))
    }
    if (outcome.pendingApprovals.isNotEmpty()) {
        append("- **Chờ Agent chính phát lại và phê duyệt**：")
        append(outcome.pendingApprovals.joinToString("、") { it.toolName })
        append("\n")
    }
    if (outcome.blockedWrites.isNotEmpty()) {
        append("- **Bị write lease chặn, chưa ghi đĩa**: ${outcome.blockedWrites.joinToString("、")}\n")
    }
    if (outcome.readOnlyWriteIntent) {
        append("- **Thiếu write lease**: nhiệm vụ yêu cầu ghi đĩa nhưng chưa khai báo write_paths, lần này thực thi theo chỉ đọc; sản phẩm nằm trong phần nội dung, chưa ghi vào tệp\n")
    }
    if (outcome.suspectedShellWrites.isNotEmpty()) {
        append("- **Nghi ngờ ghi shell (chỉ nhắc, chưa tự phán định vượt phạm vi)**:")
        append(outcome.suspectedShellWrites.joinToString("；"))
        append("; vui lòng để Agent chính rà soát lại sản phẩm thực tế và phạm vi write lease.\n")
    }
}

private fun subagentTerminationLabel(termination: SubagentTermination): String = when (termination) {
    SubagentTermination.CONCLUDED -> "Đã sản xuất kết luận"
    SubagentTermination.INCOMPLETE -> "Thu cuộc nhưng chưa cho ra kết luận đáng tin"
    SubagentTermination.NEEDS_APPROVAL -> "Có thao tác cần phê duyệt chưa thực thi"
    SubagentTermination.WRITE_SCOPE_BLOCKED -> "Ghi bị write lease chặn"
    SubagentTermination.WRITE_FAILED -> "Thử ghi thất bại, sản phẩm chưa ghi vào đĩa"
    SubagentTermination.UNPARSEABLE_TOOL_CALL -> "Gọi công cụ dạng văn bản không phân giải được"
    SubagentTermination.MAX_ROUNDS -> "Dùng hết ngân sách số lượt công cụ"
    SubagentTermination.FAILED -> "Thực thi bất thường"
    SubagentTermination.TIMEOUT -> "Thực thi quá thời gian"
    SubagentTermination.CLAIM_DOWNGRADED -> "Tự báo complete chưa được credential host chứng thực đầy đủ, hạ cấp xuống partial"
}

/**
 * 结果分页读取：汇总注入父上下文前的预算控制。
 * 总量 ≤ [SUMMARY_INLINE_BUDGET] 字符 → 原样注入（保持现状，小批次体验不变）；
 * 超限 → 每个超长子任务输出截断为 [PER_TASK_INLINE_BUDGET] 字符，完整结果落盘
 * `.tianxuan-subagent/<laneName-safe>.md`（工作区相对路径），模型可用 read 工具按 offset/limit 分页读取。
 *
 * [fileAccess] 必须已经绑定到 [workspace]：返回给父智能体的是工作区相对路径，
 * 写入基准与 read 的基准不一致就会出现"Báo có báo cáo đầy đủ, thực tế đọc không được"。
 */
internal suspend fun paginateSubagentSummary(
    outcomes: List<SubagentOrchestrator.SubagentExecutionOutcome>,
    workspace: String,
    fileAccess: WorkspaceFileAccess,
): String {
    val full = renderSummaryMarkdown(outcomes)
    if (full.length <= SUMMARY_INLINE_BUDGET || workspace.isBlank()) return full

    val overflowTasks = outcomes.filter { it.summary.length > PER_TASK_INLINE_BUDGET }
    val spillDir = ".tianxuan-subagent"
    val spilled = mutableMapOf<String, String>() // laneName -> 相对路径
    overflowTasks.forEach { outcome ->
        val safeId = outcome.subSessionId
            .filter { it.isLetterOrDigit() || it == '-' || it == ':' }
            .replace(':', '-')
            .takeLast(80)
        // subSessionId 为空时（角色未匹配/解析失败）的防御：用 UUID 后缀避免坍缩为 .md
        val fileName = if (safeId.isBlank()) "task-${java.util.UUID.randomUUID().toString().take(8)}.md" else "$safeId.md"
        val relativePath = "$spillDir/$fileName"
        // 落盘失败不阻塞：该任务按普通截断处理
        if (fileAccess.write(relativePath, outcome.summary) is top.wkbin.tianxuan.core.common.result.AppResult.Success) {
            spilled[outcome.subSessionId] = relativePath
        }
    }

    return buildString {
        append(subagentBatchHeader(outcomes))
        append("(Tổng lượng đầu ra của lô này vượt ngân sách tiêm, các tác vụ con quá dài đã bị cắt; kết quả đầy đủ có thể dùng công cụ read để đọc phân trang theo offset/limit)\n\n")
        outcomes.forEachIndexed { index, outcome ->
            append(subagentOutcomeHeader(outcome, index, includeModelBadge = false))
            append("- **Đầu ra nhiệm vụ con**:\n")
            val spillPath = spilled[outcome.subSessionId]
            if (spillPath != null) {
                append(outcome.summary.take(PER_TASK_INLINE_BUDGET))
                append("\n\n…(bị cắt, tổng ${outcome.summary.length} ký tự. Kết quả đầy đủ: read đường dẫn `$spillPath`)\n\n")
            } else {
                append(outcome.summary.trim())
                append("\n\n")
            }
        }
    }
}
