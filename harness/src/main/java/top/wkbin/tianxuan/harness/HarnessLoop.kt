package top.wkbin.tianxuan.harness

import top.wkbin.tianxuan.core.common.logging.AppLogger
import top.wkbin.tianxuan.core.database.HarnessSessionRepository
import top.wkbin.tianxuan.core.database.HarnessSessionEntity
import top.wkbin.tianxuan.core.model.SessionRunState
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.cancelAndJoin
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import top.wkbin.tianxuan.core.database.AgentApprovalRepository
import top.wkbin.tianxuan.core.database.AiModelRepository
import top.wkbin.tianxuan.harness.validation.ToolCallLoopDetector
import top.wkbin.tianxuan.harness.metrics.RunMetrics
import top.wkbin.tianxuan.harness.task.AgentStateMachine
import top.wkbin.tianxuan.core.datastore.AgentPreferences
import top.wkbin.tianxuan.harness.approval.ApprovalResumePolicy
import top.wkbin.tianxuan.harness.approval.SessionApprovalGrants
import top.wkbin.tianxuan.harness.checkpoint.RewindController
import top.wkbin.tianxuan.harness.compaction.BranchSummarizer
import top.wkbin.tianxuan.harness.session.SessionTreeStore
import top.wkbin.tianxuan.harness.session.SessionTurnCoordinator
import top.wkbin.tianxuan.harness.session.TurnPriority
import top.wkbin.tianxuan.harness.effects.RetryPolicy
import top.wkbin.tianxuan.harness.operation.OperationCoordinator
import top.wkbin.tianxuan.harness.operation.OperationStatus
import top.wkbin.tianxuan.harness.recovery.RecoveryManager
import top.wkbin.tianxuan.harness.recovery.RecoveryOutcome
import top.wkbin.tianxuan.harness.queue.PromptQueue
import top.wkbin.tianxuan.harness.queue.PromptQueueManager
import top.wkbin.tianxuan.harness.effects.DanglingToolCallPlanner
import top.wkbin.tianxuan.harness.events.AgentEventLogger
import top.wkbin.tianxuan.harness.projection.CurrentSessionTracker
import top.wkbin.tianxuan.harness.projection.SessionMessageProjector
import top.wkbin.tianxuan.harness.projection.SessionStateMirrors
import top.wkbin.tianxuan.harness.skill.SkillEvolutionAdvisor

/** Agent 单次运行的结构化结果，外层据此设置会话状态，避免内部失败被误标为 COMPLETED。 */
private sealed interface RunResult {
    data object Completed : RunResult
    data object WaitingApproval : RunResult
    data object Cancelled : RunResult
    data class Failed(val message: String) : RunResult
}

/**
 * Harness 多智能体会话并发引擎：
 * 支持多会话后台并行运行、实时状态机追踪（就绪/运行中/完成/失败）、
 * 独立的流式消息队列与前台服务多通知分发。
 */
class HarnessLoop(
    private val workspaceRecommendations: HarnessWorkspaceRecommendations,
    private val providerRunner: HarnessProviderRunner,
    private val toolRoundRunner: HarnessToolRoundRunner,
    private val foregroundLauncher: AgentForegroundLauncher,
    private val providerClient: ProviderClient,
    private val toolExecutor: ToolExecutor,
    private val toolRoundDispatcher: ToolRoundDispatcher,
    private val messageStore: SessionTreeStore,
    private val sessionDao: HarnessSessionRepository,
    private val modelRepository: AiModelRepository,
    private val settingsDataStore: AgentPreferences,
    private val json: Json,
    private val logger: AppLogger,
    private val approvalRepository: AgentApprovalRepository,
    private val operationCoordinator: OperationCoordinator,
    private val recoveryManager: RecoveryManager,
    private val promptQueueManager: PromptQueueManager,
    private val sessionTracker: CurrentSessionTracker,
    private val stateMirrors: SessionStateMirrors,
    private val messageProjector: SessionMessageProjector,
    private val agentEventLogger: AgentEventLogger,
    private val resumePolicy: ApprovalResumePolicy,
    private val sessionApprovalGrants: SessionApprovalGrants,
    private val agentTaskStateMachine: AgentStateMachine,
    private val turnRunner: TurnRunner,
    private val rewindController: RewindController,
    private val branchSummarizer: BranchSummarizer,
    private val skillEvolutionAdvisor: SkillEvolutionAdvisor? = null,
    private val turnCoordinator: SessionTurnCoordinator,
) {
    private val loopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val currentSessionId: StateFlow<String> get() = sessionTracker.currentSessionId

    private val sessionJobs = ConcurrentHashMap<String, Job>()
    private val sessionCancelEpochs = ConcurrentHashMap<String, AtomicLong>()
    private val foregroundLoadGeneration = AtomicLong()
    private val cancellingSessions = ConcurrentHashMap.newKeySet<String>()
    private val sessionLoopDetectors = ConcurrentHashMap<String, ToolCallLoopDetector>()
    /** Sessions being deleted; reject new runs and skip pending drainage. */
    private val tombstonedSessions = ConcurrentHashMap.newKeySet<String>()
    private val recoveredSessions = ConcurrentHashMap.newKeySet<String>()
    private val approvalTimeoutJobs = ConcurrentHashMap<String, Job>()

    private val _sessionPendingMessages = ConcurrentHashMap<String, MutableStateFlow<List<PendingMessage>>>()

    /** 全局所有会话的运行状态映射（供会话抽屉、状态点等观察）——委托给状态镜像器。 */
    val sessionRunStates: StateFlow<Map<String, SessionRunState>> get() = stateMirrors.sessionRunStates
    /** 全局各会话当前的动作描述状态。 */
    val sessionStatuses: StateFlow<Map<String, String>> get() = stateMirrors.sessionStatuses

    // ---- 当前前台聚焦会话的响应式镜像（全部委托给投影协作类） ----
    val messages: StateFlow<List<HarnessMessage>> get() = messageProjector.foregroundMessages

    /** Session-scoped message stream used by trusted secondary surfaces such as TianXuan WebChat. */
    fun messagesForSession(sessionId: String): StateFlow<List<HarnessMessage>> =
        messageProjector.messagesFlow(sessionId)

    // ---- Checkpoints & Rewind（每轮文件快照安全网，供 UI / 未来 MCP 调用） ----
    fun sessionCheckpoints(sessionId: String): List<top.wkbin.tianxuan.harness.checkpoint.CheckpointMeta> =
        rewindController.checkpoints(sessionId)

    fun prepareRewind(
        sessionId: String,
        turn: Int,
        scope: top.wkbin.tianxuan.harness.checkpoint.RewindScope,
    ): top.wkbin.tianxuan.harness.checkpoint.RewindPlan = rewindController.prepare(sessionId, turn, scope)

    suspend fun commitRewind(
        plan: top.wkbin.tianxuan.harness.checkpoint.RewindPlan,
        workspace: String = "",
    ): top.wkbin.tianxuan.harness.checkpoint.RewindResult = rewindController.commit(plan, workspace)

    /** 撤销最近一次 rewind（单层级）；null = 当前没有可撤销的 rewind。 */
    suspend fun undoRewind(
        sessionId: String,
        workspace: String = "",
    ): top.wkbin.tianxuan.harness.checkpoint.RewindResult? = rewindController.undoLastRewind(sessionId, workspace)

    /** Loads persisted history without changing the Android UI's foreground session. */
    suspend fun prepareRemoteSession(sessionId: String): List<HarnessMessage> {
        val flow = messageProjector.preparedForLoad(sessionId)
        return flow.value
    }

    val running: StateFlow<Boolean> get() = stateMirrors.running

    private val _workspace = MutableStateFlow("")
    /** 当前会话关联的工作区 Linux 路径（"" = 未关联）。 */
    val workspace: StateFlow<String> = _workspace.asStateFlow()

    private val _projectType = MutableStateFlow("")
    /** 当前会话显式选择的工程类型；空值表示由工作区内容自动识别。 */
    val projectType: StateFlow<String> = _projectType.asStateFlow()

    val mcpRecommendations get() = workspaceRecommendations.recommendations

    fun enableRecommendedMcp(presetId: String) {
        loopScope.launch { workspaceRecommendations.enable(presetId) }
    }

    fun dismissMcpRecommendation(presetId: String) = workspaceRecommendations.dismiss(presetId)

    private fun refreshMcpRecommendations(workspacePath: String) {
        workspaceRecommendations.refresh(loopScope, workspacePath)
    }

    val error: StateFlow<String?> get() = stateMirrors.error

    /** 当前执行状态（供 UI / 后台通知显示进度）。运行结束或出错时置空。 */
    val status: StateFlow<String?> get() = stateMirrors.status

    /** 推理模型思考中（reasoning 正在流式上屏）。开始思考置 true，本回合结束时置 false。 */
    val thinkingLive: StateFlow<Boolean> get() = stateMirrors.thinkingLive

    private val _pendingMessages = MutableStateFlow<List<PendingMessage>>(emptyList())
    /**
     * 运行中排队等待发送的用户消息。当前任务结束后自动按序接续执行；
     * 用户点"停止"时清空。UI 可观察此列表展示排队状态。
     */
    val pendingMessages: StateFlow<List<PendingMessage>> = _pendingMessages.asStateFlow()

    private val _queuedPrompts = MutableStateFlow<List<QueuedPrompt>>(emptyList())
    /** Current session's durable queues, including steering and follow-up semantics. */
    val queuedPrompts: StateFlow<List<QueuedPrompt>> = _queuedPrompts.asStateFlow()

    private fun getOrCreatePendingFlow(sessId: String): MutableStateFlow<List<PendingMessage>> {
        return _sessionPendingMessages.getOrPut(sessId) { MutableStateFlow(emptyList()) }
    }

    private fun isSessionBusy(sessId: String): Boolean =
        sessionJobs[sessId]?.isCompleted == false ||
            cancellingSessions.contains(sessId) ||
            stateMirrors.isWaitingApproval(sessId)

    private fun cancelApprovalTimeout(sessId: String) {
        approvalTimeoutJobs.remove(sessId)?.cancel()
    }

    private fun scheduleApprovalTimeout(sessId: String) {
        cancelApprovalTimeout(sessId)
        val job = loopScope.launch {
            val pending = runCatching { approvalRepository.pendingNow(sessId) }.getOrDefault(emptyList())
            if (pending.isEmpty()) {
                var settled = false
                turnCoordinator.withSessionMutex(sessId) {
                    settled = checkAndSettleExpiredApprovalsLocked(sessId)
                    if (settled) {
                        startNextQueuedLocked(sessId)
                    }
                }
                if (settled) {
                    refreshPendingProjection(sessId)
                }
                return@launch
            }
            val minExpiry = pending.minOf { it.expiresAt }
            val delayMs = (minExpiry - System.currentTimeMillis()).coerceAtLeast(0L)
            delay(delayMs)
            var settled = false
            turnCoordinator.withSessionMutex(sessId) {
                settled = checkAndSettleExpiredApprovalsLocked(sessId)
                if (settled) {
                    startNextQueuedLocked(sessId)
                }
            }
            if (settled) {
                refreshPendingProjection(sessId)
            }
        }
        approvalTimeoutJobs[sessId] = job
    }

    /**
     * 检查并自愈已过期的等待审批：
     * 若会话无活跃 Job 且处于 WAITING_APPROVAL，清扫已过期的 pending 请求并补充超时 ToolResult。
     * 若已无合法 pending 审批，复位状态为 IDLE，返回 true；若仍有合法审批等待中，返回 false。
     * 必须在持有会话互斥锁（turnCoordinator.withSessionMutex(sessId)）下调用。
     */
    private suspend fun checkAndSettleExpiredApprovalsLocked(sessId: String): Boolean {
        if (sessionJobs[sessId]?.isActive == true) return false
        if (!stateMirrors.isWaitingApproval(sessId)) return false

        val swept = approvalRepository.sweepExpiredForSession(sessId)
        val now = now()
        for (expired in swept) {
            val output = if (expired.toolName == AskUserQuestions.TOOL_NAME) {
                "Người dùng chưa trả lời câu hỏi trong thời gian quy định (câu hỏi đã hết thời gian), lần chờ này đã kết thúc."
            } else {
                val ttlMinutes = ApprovalPolicyEngine.APPROVAL_TTL_MS / 60_000L
                resumePolicy.invalidationResultMessage("Phê duyệt này đã hết hạn (quá $ttlMinutes phút chưa xử lý)")
            }
            val result = ToolResult(
                id = newId(),
                createdAt = now,
                toolCallId = expired.toolCallId,
                success = false,
                output = output,
            )
            val active = operationCoordinator.active(sessId)
            if (active != null && (expired.operationId == null || expired.operationId == active.id)) {
                operationCoordinator.toolSettled(active.id, result, round = 0, toolName = expired.toolName)
                messageProjector.publishPersisted(sessId, result)
            } else {
                messageProjector.append(sessId, result)
            }
        }

        val remainingPending = approvalRepository.pendingNow(sessId)
        if (remainingPending.isNotEmpty()) {
            scheduleApprovalTimeout(sessId)
            return false
        }

        cancelApprovalTimeout(sessId)
        logger.i("Session $sessId had approvals expired or settled; resetting WAITING_APPROVAL to IDLE")

        if (swept.isEmpty()) {
            repairDanglingToolCalls(sessId, interrupted = true)
        }

        val activeOp = operationCoordinator.active(sessId)
        if (activeOp != null && activeOp.status == OperationStatus.WAITING_APPROVAL.id) {
            operationCoordinator.finish(sessId, "aborted", details = "Phê duyệt hoặc câu hỏi đã hết hạn")
        }

        agentTaskStateMachine.activeForSession(sessId)?.let { activeTask ->
            if (activeTask.status == top.wkbin.tianxuan.core.database.task.AgentTaskStatus.WAITING_APPROVAL) {
                agentTaskStateMachine.markFailed(activeTask.id, "Phê duyệt hoặc câu hỏi đã hết hạn")
            }
        }

        stateMirrors.setRunState(sessId, SessionRunState.IDLE)
        stateMirrors.setStatus(sessId, null)
        if (sessId == sessionTracker.foregroundId) {
            stateMirrors.setForegroundRunning(false)
        }

        return true
    }

    /** 新建会话。workspace 为关联的工作区 Linux 路径（如 /workspace/proj），空串表示不关联。 */
    suspend fun newSession(title: String, workspace: String = "", projectType: String = ""): String {
        val id = UUID.randomUUID().toString()
        val defaultModel = modelRepository.activeModel()
        foregroundLoadGeneration.incrementAndGet()
        tombstonedSessions.remove(id)
        sessionTracker.setCurrent(id)
        _workspace.value = workspace
        _projectType.value = projectType
        sessionDao.upsert(
            HarnessSessionEntity(
                id = id,
                title = title.ifBlank { "Phiên mới" },
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                modelId = defaultModel?.id,
                modelVariant = defaultModel?.model?.substringBefore(',')?.trim()?.takeIf { it.isNotBlank() },
                workspace = workspace,
                projectType = projectType,
                approvalMode = approvalRepository.currentMode().id,
                runMode = approvalRepository.currentRunMode().id,
            ),
        )
        messageProjector.seedEmpty(id)
        messageProjector.resetForegroundProjection(emptyList())
        _sessionPendingMessages[id] = MutableStateFlow(emptyList())
        stateMirrors.ensureFlows(id)
        stateMirrors.setRunState(id, SessionRunState.IDLE)
        stateMirrors.setStatus(id, null)

        stateMirrors.resetForeground()
        _pendingMessages.value = emptyList()
        _queuedPrompts.value = emptyList()
        refreshMcpRecommendations(workspace)
        return id
    }

    /** 恢复已有会话的历史消息与工作区关联，不中断正在后台运行的任何会话。 */
    suspend fun loadSession(id: String) {
        val generation = foregroundLoadGeneration.incrementAndGet()
        sessionTracker.setCurrent(id)
        val sessionEntity = withContext(Dispatchers.IO) { sessionDao.findById(id) }
        if (!isCurrentLoad(id, generation)) return
        _workspace.value = sessionEntity?.workspace.orEmpty()
        _projectType.value = sessionEntity?.projectType.orEmpty()
        refreshMcpRecommendations(_workspace.value)

        val liveFlow = messageProjector.preparedForLoad(id)
        if (!isCurrentLoad(id, generation)) return

        messageProjector.resetForegroundProjection(liveFlow.value)
        stateMirrors.setForegroundRunning(sessionJobs[id]?.isActive == true)
        stateMirrors.restoreForegroundError(id, stateMirrors.errorOf(id))
        stateMirrors.setStatus(id, stateMirrors.lastStatus(id))
        stateMirrors.setThinkingLive(id, stateMirrors.thinkingLiveOf(id))
        refreshPendingProjection(id)

        val hasPendingApprovals = withContext(Dispatchers.IO) { approvalRepository.pendingNow(id).isNotEmpty() }
        if (!isCurrentLoad(id, generation)) return
        if (hasPendingApprovals) {
            stateMirrors.setRunState(id, SessionRunState.WAITING_APPROVAL)
            stateMirrors.setStatus(id, "Chờ người dùng phê chuẩn")
            scheduleApprovalTimeout(id)
        } else {
            val settled = turnCoordinator.withSessionMutex(id) {
                val ok = checkAndSettleExpiredApprovalsLocked(id)
                if (ok) {
                    startNextQueuedLocked(id)
                }
                ok
            }
            if (settled) {
                refreshPendingProjection(id)
            }
        }

        stateMirrors.recordThinkingModeFromHistory(id, liveFlow.value)
        recoverSessionIfInterrupted(id, liveFlow)
    }

    /**
     * 应用进程重启后批量恢复所有被中断的 Agent 会话。
     *
     * 遍历数据库中所有会话，对存在未完成操作（活跃 operation）的会话执行恢复策略：
     * - 等待审批：保持 WAITING_APPROVAL 状态
     * - 工具中断 / 运行挂起：先应用 replay policy，再由 durable task 的授权与尝试预算
     *   决定自动续跑或保持 SUSPENDED；不可重放工具永不自动再次执行。
     *
     * @return 实际执行了恢复处理的会话数量
     */
    suspend fun recoverAllInterruptedSessions(): Int {
        val sessions = withContext(Dispatchers.IO) { sessionDao.listAll() }
        var recovered = 0
        for (session in sessions) {
            // workflow:<executionId>:<nodeId> 会话由工作流体系自管：所属运行已随进程死亡
            // 终止（启动时由 WorkflowRunManager.reconcileInterruptedRuns 对账），不能被
            // 当作普通中断会话自动续跑，避免副作用重放。
            if (session.id.startsWith(WORKFLOW_SESSION_PREFIX)) continue
            val hasActiveOperation = withContext(Dispatchers.IO) {
                operationCoordinator.active(session.id) != null
            }
            if (hasActiveOperation && recoverSessionIfInterrupted(session.id, null)) {
                recovered++
            }
        }

        // Tasks whose restart budget/authority is exhausted remain visible and resumable by the
        // user, but are never silently executed again.
        agentTaskStateMachine.exhaustedRecoverable().forEach { task ->
            val detail = when {
                task.sessionId.isBlank() -> "Tác vụ cũ chưa liên kết phiên, cần khởi phát lại thủ công"
                !task.autoResume -> "Nhiệm vụ chưa phân quyền tự động tiếp tục sau khi tiến trình khởi động lại"
                else -> "Đã đạt giới hạn số lần thử khôi phục tiến trình (${task.attemptCount}/${task.maxAttempts})"
            }
            agentTaskStateMachine.markSuspended(task.id, detail)
            // 此分支是"Tự động chạy tiếp"的终点：挂起后任务不再产生任何事件，若不落盘则日志里毫无痕迹，
            // 用户侧只表现为"Tin nhắn này vĩnh viễn không có hồi đáp"。与 RunFailure 同理，走 AppLogger 不受
            // agentLoggingEnabled 门控。
            logger.logAgent(
                task.sessionId,
                "DurableTaskSuspended",
                "taskId=${task.id}, reason=$detail, không tự khôi phục nữa (có thể tự khởi tạo lại thủ công trong danh sách nhiệm vụ)",
            )
        }

        val recoverableBySession = agentTaskStateMachine.recoverable().groupBy { it.sessionId }
        for ((sessionId, tasks) in recoverableBySession) {
            val task = tasks.first()
            tasks.drop(1).forEach { duplicate ->
                agentTaskStateMachine.markSuspended(duplicate.id, "Cùng phiên tồn tại tác vụ hoạt động sớm hơn, đã tạm dừng để giữ thứ tự")
            }
            if (sessions.none { it.id == sessionId }) {
                agentTaskStateMachine.markSuspended(task.id, "Phiên liên kết không tồn tại, không thể khôi phục")
                continue
            }
            if (approvalRepository.pendingNow(sessionId).isNotEmpty()) {
                agentTaskStateMachine.markWaitingApproval(task.id)
                continue
            }

            val activeOperation = operationCoordinator.active(sessionId)
            if (activeOperation == null && task.operationId != null) {
                // Normal shutdown writes the task terminal state before it removes the operation.
                // A recoverable task with no operation therefore has no trustworthy outcome.
                // Fail closed: never replay a potentially side-effecting prompt and never invent
                // a successful result that was not durably recorded.
                val detail = "Kết quả chạy không xác định; thao tác liên quan đã kết thúc, để ngăn phát lại tác dụng phụ, nhiệm vụ đã bị chấm dứt"
                agentTaskStateMachine.markFailed(task.id, detail)
                agentEventLogger.log(sessionId, "DurableTaskRecovered", "taskId=${task.id}, outcome=unknown")
                recovered++
                continue
            }

            if (!agentTaskStateMachine.markRecovering(task.id, "Tiến trình ứng dụng khởi động lại, đang khôi phục từ checkpoint bền vững")) continue
            turnCoordinator.withSessionMutex(sessionId) {
                if (isSessionBusy(sessionId) || tombstonedSessions.contains(sessionId)) return@withSessionMutex
                launchSessionJobLocked(
                    sessId = sessionId,
                    taskId = task.id,
                    operationId = activeOperation?.id,
                ) {
                    if (activeOperation == null) {
                        runLoop(sessionId, task.description, taskId = task.id)
                    } else {
                        runLoopInternal(sessionId, now(), activeOperation.id, task.id)
                    }
                }
                recovered++
                agentEventLogger.log(
                    sessionId,
                    "DurableTaskRecovered",
                    "taskId=${task.id}, attempt=${task.attemptCount + 1}/${task.maxAttempts}",
                )
            }
        }

        // A process can die after one operation finishes but before finishRun drains NEXT_RUN.
        // Restart the first durable queue item for otherwise-idle sessions.
        for (sessionId in agentTaskStateMachine.queued().map { it.sessionId }.filter { it.isNotBlank() }.distinct()) {
            if (sessions.none { it.id == sessionId } || approvalRepository.pendingNow(sessionId).isNotEmpty()) continue
            turnCoordinator.withSessionMutex(sessionId) {
                if (!isSessionBusy(sessionId) && startNextQueuedLocked(sessionId)) recovered++
            }
        }
        if (recovered > 0) startForegroundServiceSafe()
        return recovered
    }

    /**
     * 对单个会话执行中断恢复。从 loadSession 中提取，供批量恢复复用。
     *
     * @param id 会话 ID
     * @param existingLiveFlow loadSession 中已初始化的消息流；批量恢复时传 null，内部按需创建
     * @return 是否执行了非 Clean 的恢复处理
     */
    private suspend fun recoverSessionIfInterrupted(
        id: String,
        existingLiveFlow: MutableStateFlow<List<HarnessMessage>>?,
    ): Boolean {
        if (sessionJobs[id]?.isActive == true) return false
        if (!recoveredSessions.add(id)) return false

        val liveFlow = existingLiveFlow ?: messageProjector.preparedForLoad(id)

        return when (val recovery = recoveryManager.recoverSession(id)) {
            RecoveryOutcome.Clean -> false
            RecoveryOutcome.WaitingApproval -> {
                stateMirrors.setRunState(id, SessionRunState.WAITING_APPROVAL)
                stateMirrors.setStatus(id, "Chờ người dùng phê chuẩn")
                true
            }
            is RecoveryOutcome.ToolInterrupted -> {
                val restored = messageProjector.loadHistory(id)
                messageProjector.replaceAll(id, restored)
                stateMirrors.setRunState(id, SessionRunState.IDLE)
                stateMirrors.setStatus(id, "Lần thực thi công cụ trước bị gián đoạn, gửi tin nhắn là có thể tiếp tục")
                true
            }
            is RecoveryOutcome.Suspended -> {
                stateMirrors.setRunState(id, SessionRunState.IDLE)
                stateMirrors.setStatus(id, "Lần chạy trước đã tạm dừng (${recovery.reason}), gửi tin nhắn là có thể bắt đầu lại")
                true
            }
        }
    }

    suspend fun renameSession(id: String, title: String) {
        sessionDao.rename(id, title, System.currentTimeMillis())
    }

    suspend fun deleteSession(id: String) {
        // 注意：这里不能全程持有会话互斥锁——cancelAndJoin 会等待 runLoop 的 finally
        // 段，而 finally 段需要抢同一把锁，全程持锁必然死锁。因此采用 tombstone +
        // 结束时移除 tombstone 的方案；对"Coroutine chỉ lấy được khóa sau khi xóa xong"的窗口，
        // 由 startSessionRun 锁内的 DB 存在性检查兜底（见该函数注释）。
        // Mark tombstoned first so finishRun on the dying job cannot drain pending
        // messages and start a fresh run after we have already begun cleanup.
        tombstonedSessions.add(id)
        cancelApprovalTimeout(id)
        _sessionPendingMessages[id]?.value = emptyList()
        sessionJobs[id]?.cancelAndJoin()
        _sessionPendingMessages.remove(id)
        turnCoordinator.evictSession(id)
        messageProjector.removeSession(id)
        stateMirrors.removeSession(id)

        messageStore.deleteSession(id)
        approvalRepository.deleteForSession(id)
        sessionApprovalGrants.revokeSession(id)
        agentTaskStateMachine.deleteForSession(id)
        sessionDao.deleteSession(id)
        rewindController.dropSession(id)
        sessionLoopDetectors.remove(id)
        sessionCancelEpochs.remove(id)
        cancellingSessions.remove(id)
        tombstonedSessions.remove(id)
        if (sessionTracker.currentSessionId.value == id) {
            val remaining = sessionDao.observeAll().first()
            val nextSession = remaining.firstOrNull { it.id != id }
            if (nextSession != null) {
                loadSession(nextSession.id)
            } else {
                newSession("Phiên mới")
            }
        }
    }

    fun send(text: String, targetSessionId: String? = null, imageUrls: List<String> = emptyList()) {
        val trimmed = text.trim()
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (trimmed.isEmpty() && imageUrls.isEmpty()) return
        if (sessId.isBlank()) return

        val pending = PendingMessage(text = trimmed, imageUrls = imageUrls, taskId = newId())
        startSessionRun(sessId, enqueueOnBusy = pending) {
            runLoop(sessId, pending.text, pending.imageUrls, pending.taskId)
        }
        startForegroundServiceSafe()
    }

    fun steer(text: String, targetSessionId: String? = null, imageUrls: List<String> = emptyList()) {
        enqueueExplicit(PromptQueue.STEER, text, targetSessionId, imageUrls)
    }

    fun followUp(text: String, targetSessionId: String? = null, imageUrls: List<String> = emptyList()) {
        enqueueExplicit(PromptQueue.FOLLOW_UP, text, targetSessionId, imageUrls)
    }

    private fun enqueueExplicit(queue: PromptQueue, text: String, targetSessionId: String?, imageUrls: List<String>) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        val trimmed = text.trim()
        if (sessId.isBlank() || (trimmed.isBlank() && imageUrls.isEmpty())) return
        loopScope.launch {
            turnCoordinator.withSessionMutex(sessId) {
                // 与 startSessionRun 同款幽灵会话防线：deleteSession 先删 DB 行、后移除 tombstone，
                // 等锁的 steer/followUp 协程拿到锁时 tombstone 已不在——不查库就会给已删除会话
                // 重建 durable task 并发起真实 LLM 调用（幽灵运行）。
                if (tombstonedSessions.contains(sessId)) return@withSessionMutex
                if (sessionDao.findById(sessId) == null) return@withSessionMutex
                checkAndSettleExpiredApprovalsLocked(sessId)
                if (isSessionBusy(sessId)) {
                    // Steering/follow-up belongs to the currently active durable task.
                    promptQueueManager.enqueue(sessId, queue, PendingMessage(trimmed, imageUrls))
                } else {
                    val pending = PendingMessage(trimmed, imageUrls, taskId = newId())
                    createDurableTask(sessId, pending)
                    launchSessionJobLocked(sessId, pending.taskId) {
                        runLoop(sessId, pending.text, pending.imageUrls, pending.taskId)
                    }
                }
            }
            refreshPendingProjection(sessId)
        }
    }

    /**
     * 重新生成最后一次回复
     */
    fun regenerateLast(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return

        startSessionRun(sessId) {
            val current = messageProjector.messagesFlow(sessId).value
            val lastUserIndex = current.indexOfLast { it is UserMessage }
            if (lastUserIndex < 0) return@startSessionRun RunResult.Completed
            val lastUserMessage = current[lastUserIndex] as UserMessage
            val toKeep = current.subList(0, lastUserIndex + 1)
            messageProjector.replaceAll(sessId, toKeep)
            messageStore.moveTo(sessId, lastUserMessage.id)
            runLoopInternal(sessId, startedAt = now())
        }
        startForegroundServiceSafe()
    }

    /** Rewinds before a tool call and asks the model to continue again on a preserved new branch. */
    fun retryToolCall(toolCallId: String, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        startSessionRun(sessId) {
            val current = messageProjector.messagesFlow(sessId).value
            val targetIndex = current.indexOfFirst { it.id == toolCallId && it is ToolCall }
            if (targetIndex < 0) return@startSessionRun RunResult.Completed
            val target = current[targetIndex]
            val updated = current.take(targetIndex)
            messageProjector.replaceAll(sessId, updated)
            messageStore.rewindBefore(sessId, target.id)
            runLoopInternal(sessId, startedAt = now())
        }
        startForegroundServiceSafe()
    }

    /** Moves the main conversation cursor to an existing immutable-tree leaf. */
    suspend fun activateBranch(leafId: String?, targetSessionId: String? = null): Boolean {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank() || isSessionBusy(sessId)) return false
        val oldLeafId = messageStore.laneLeafId(sessId)
        messageStore.moveTo(sessId, leafId)
        // 分支摘要（对齐 pi /tree）：切换后把被放弃分支生成摘要注入新位置，
        // 保留旧方案的关键结论。失败不影响切换本身。
        if (leafId != null && oldLeafId != null && oldLeafId != leafId) {
            runCatching {
                val session = sessionDao.findById(sessId)
                val model = session?.modelId?.let { boundId ->
                    runCatching { providerClient.resolveConfigured(boundId, session.modelVariant) }.getOrNull()
                }
                branchSummarizer.summarizeAbandonedBranch(sessId, oldLeafId, leafId, model = model)
            }.onFailure { throwable ->
                logger.w("Tạo tóm tắt nhánh thất bại (không ảnh hưởng chuyển nhánh): ${throwable.message}")
            }
        }
        val history = messageProjector.loadHistory(sessId)
        messageProjector.replaceAll(sessId, history)
        return true
    }

    /**
     * 编辑并重发指定用户消息
     */
    fun truncateAndResend(userMessageId: String, newText: String, targetSessionId: String? = null) {
        val trimmed = newText.trim()
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (trimmed.isEmpty() || sessId.isBlank()) return

        startSessionRun(sessId) {
            val current = messageProjector.messagesFlow(sessId).value
            val targetIndex = current.indexOfFirst { it.id == userMessageId }
            if (targetIndex < 0) {
                return@startSessionRun runLoop(sessId, trimmed)
            }
            val targetMessage = current[targetIndex]
            val toKeep = current.subList(0, targetIndex)
            messageProjector.replaceAll(sessId, toKeep)
            messageStore.rewindBefore(sessId, targetMessage.id)
            runLoop(sessId, trimmed)
        }
        startForegroundServiceSafe()
    }

    /** Navigate the active branch to immediately before this message. */
    suspend fun deleteMessage(messageId: String, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (isSessionBusy(sessId) || sessId.isBlank()) return
        val liveFlow = messageProjector.messagesFlow(sessId)
        val current = liveFlow.value
        val target = current.firstOrNull { it.id == messageId } ?: return
        val targetIndex = current.indexOf(target)
        val updated = current.take(targetIndex)
        messageProjector.replaceAll(sessId, updated)
        messageStore.rewindBefore(sessId, target.id)
    }

    private suspend fun repairDanglingToolCalls(sessId: String, interrupted: Boolean, workspace: String = "") {
        val actions = DanglingToolCallPlanner.plan(messageProjector.messagesFlow(sessId).value, interrupted)
        if (actions.isEmpty()) return
        actions.forEach { action ->
            val result = when (action) {
                is DanglingToolCallPlanner.Replay -> {
                    agentEventLogger.log(
                        sessId,
                        "ToolReplay",
                        "Phát lại công cụ chỉ đọc bị gián đoạn ${action.call.rawToolName ?: action.call.tool.name.lowercase()}",
                    )
                    try {
                        toolExecutor.execute(action.call, sessId, workspace)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = action.call.id,
                            success = false,
                            output = "Phát lại công cụ chỉ đọc bị gián đoạn thất bại: ${friendly(throwable)}",
                        )
                    }
                }
                is DanglingToolCallPlanner.Stubbed -> ToolResult(
                    id = newId(),
                    createdAt = now(),
                    toolCallId = action.call.id,
                    success = false,
                    output = action.note,
                )
            }
            messageProjector.append(sessId, result)
        }
    }

    fun cancel(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        stateMirrors.setStatus(sessId, "Đang dừng…")
        loopScope.launch {
            val job = turnCoordinator.withSessionMutex(sessId) {
                cancellingSessions += sessId
                sessionCancelEpochs.getOrPut(sessId) { AtomicLong() }.incrementAndGet()
                val queuedTaskIds = promptQueueManager.list(sessId, PromptQueue.NEXT_RUN)
                    .mapNotNull { it.second.taskId }
                PromptQueue.entries.forEach { promptQueueManager.clear(sessId, it) }
                queuedTaskIds.forEach { agentTaskStateMachine.markCancelled(it, "Lần chạy phiên đã dừng") }
                sessionJobs[sessId]?.also { it.cancel() }
            }
            job?.cancelAndJoin()
            val restarted = turnCoordinator.withSessionMutex(sessId) {
                cancelApprovalTimeout(sessId)
                // 防御 cancel/approve 竞态：cancelAndJoin 窗口（锁外等旧 job finally）内，
                // resolveApproval 可能已启动新 job 并加入 sessionJobs；需一并取消，
                // 否则用户点"停止"后新 job 仍执行已批准的写入工具。
                sessionJobs[sessId]?.let { if (it.isActive) it.cancel() }
                var approvalsSettled = true
                try {
                    rejectPendingApprovalsForCancel(sessId)
                    agentTaskStateMachine.activeForSession(sessId)?.let { active ->
                        agentTaskStateMachine.markCancelled(active.id)
                    }
                } catch (throwable: Throwable) {
                    approvalsSettled = false
                    logger.e("Failed to settle pending approvals while cancelling $sessId", throwable)
                } finally {
                    cancellingSessions -= sessId
                }
                approvalsSettled && !tombstonedSessions.contains(sessId) && startNextQueuedLocked(sessId)
            }
            refreshPendingProjection(sessId)
            if (!restarted) {
                val stillWaiting = runCatching { approvalRepository.pendingNow(sessId).isNotEmpty() }
                    .getOrDefault(true)
                if (stillWaiting) {
                    stateMirrors.setRunState(sessId, SessionRunState.WAITING_APPROVAL)
                    stateMirrors.setStatus(sessId, "Chờ người dùng phê chuẩn")
                    scheduleApprovalTimeout(sessId)
                } else {
                    stateMirrors.setRunState(sessId, SessionRunState.IDLE)
                    stateMirrors.setStatus(sessId, null)
                    if (sessId == sessionTracker.foregroundId) stateMirrors.setForegroundRunning(false)
                }
            }
        }
    }

    /** 移除某会话排队中的消息 */
    fun removePendingMessage(index: Int, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        loopScope.launch {
            val taskId = promptQueueManager.list(sessId, PromptQueue.NEXT_RUN)
                .getOrNull(index)?.second?.taskId
            promptQueueManager.cancel(sessId, PromptQueue.NEXT_RUN, index)
            taskId?.let { agentTaskStateMachine.markCancelled(it, "Đã gỡ khỏi hàng chờ") }
            refreshPendingProjection(sessId)
        }
    }

    fun removeQueuedPrompt(queue: PromptQueue, index: Int, targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        loopScope.launch {
            val taskId = if (queue == PromptQueue.NEXT_RUN) {
                promptQueueManager.list(sessId, queue).getOrNull(index)?.second?.taskId
            } else {
                null
            }
            promptQueueManager.cancel(sessId, queue, index)
            taskId?.let { agentTaskStateMachine.markCancelled(it, "Đã gỡ khỏi hàng chờ") }
            refreshPendingProjection(sessId)
        }
    }

    /** 清空某会话全部排队消息 */
    fun clearPendingMessages(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        loopScope.launch {
            val queuedTaskIds = promptQueueManager.list(sessId, PromptQueue.NEXT_RUN)
                .mapNotNull { it.second.taskId }
            promptQueueManager.clear(sessId, PromptQueue.NEXT_RUN)
            queuedTaskIds.forEach { agentTaskStateMachine.markCancelled(it, "Hàng đợi chờ đã được dọn sạch") }
            refreshPendingProjection(sessId)
        }
    }

    private suspend fun refreshPendingProjection(sessId: String) {
        val all = promptQueueManager.listAll(sessId)
        val pending = all.filter { it.queue == PromptQueue.NEXT_RUN }.map { it.message }
        getOrCreatePendingFlow(sessId).value = pending
        if (sessId == sessionTracker.currentSessionId.value) {
            _pendingMessages.value = pending
            _queuedPrompts.value = all
        }
    }

    private suspend fun finishRun(sessId: String, job: Job, runEpoch: Long) = withContext(NonCancellable) {
        turnCoordinator.withSessionMutex(sessId) {
            sessionJobs.remove(sessId, job)
            val waitingApproval = stateMirrors.onRunFinished(sessId)
            if (waitingApproval || tombstonedSessions.contains(sessId)) return@withSessionMutex
            if (sessionCancelEpochs[sessId]?.get() != runEpoch || cancellingSessions.contains(sessId)) return@withSessionMutex
            startNextQueuedLocked(sessId)
        }
        refreshPendingProjection(sessId)
    }

    /** Caller holds the session mutex. Consumes and starts exactly one durable next-run item. */
    private suspend fun startNextQueuedLocked(sessId: String): Boolean {
        val (queueItemId, next) = promptQueueManager.first(sessId, PromptQueue.NEXT_RUN) ?: return false
        val taskId = next.taskId ?: newId().also { generated ->
            createDurableTask(sessId, next.copy(taskId = generated))
        }
        val userMessage = UserMessage(newId(), now(), next.text, next.imageUrls)
        val operationId = operationCoordinator.acceptQueuedRun(sessId, queueItemId, userMessage)
        messageProjector.publishPersisted(sessId, userMessage)
        launchSessionJobLocked(sessId, taskId, operationId = operationId) {
            runLoopInternal(sessId, now(), operationId, taskId)
        }
        return true
    }

    /** Caller holds the session mutex. A lazy Job counts as busy as soon as it enters the map. */
    private suspend fun launchSessionJobLocked(
        sessId: String,
        taskId: String? = null,
        operationId: String? = null,
        incrementTaskAttempt: Boolean = true,
        priority: TurnPriority = TurnPriority.NORMAL,
        block: suspend () -> RunResult,
    ) {
        // 占用护栏：已有活跃 Job 时拒绝再启动。审批恢复（startClaimedSessionRun）在
        // claimPending 与拿会话锁之间留有窗口——用户同时点「批准」与「停止」时，
        // cancel 的 startNextQueuedLocked 先启动了新 run，这里若再无条件覆盖
        // sessionJobs[sessId]，两个 run 会并发写同一 lane（历史交错损坏 + 双倍消耗）。
        sessionJobs[sessId]?.takeIf { it.isActive }?.let { active ->
            logger.w(
                "Session $sessId already has an active run; skipping duplicate launch " +
                    "(taskId=$taskId). This indicates an approval/cancel race — the earlier run wins.",
            )
            return
        }
        if (taskId != null && !agentTaskStateMachine.markRunning(
                id = taskId,
                operationId = operationId,
                incrementAttempt = incrementTaskAttempt,
            )
        ) {
            logger.w("Durable task $taskId could not claim RUNNING; session launch skipped")
            return
        }
        val epoch = sessionCancelEpochs.getOrPut(sessId) { AtomicLong() }.get()
        val job = loopScope.launch(start = CoroutineStart.LAZY) {
            executeSessionRun(sessId, epoch, taskId, priority, block)
        }
        sessionJobs[sessId] = job
        job.start()
    }

    fun clearError(targetSessionId: String? = null) {
        val sessId = targetSessionId?.ifBlank { null } ?: sessionTracker.currentSessionId.value
        if (sessId.isBlank()) return
        stateMirrors.setError(sessId, null)
    }

    /**
     * Atomically check-and-occupy the session slot under a per-session Mutex,
     * then run [block] as the single active run. If the session is busy the
     * optional [enqueueOnBusy] message is queued for ordered execution.
     */
    private fun startSessionRun(
        sessId: String,
        enqueueOnBusy: PendingMessage? = null,
        block: suspend () -> RunResult,
    ) {
        if (tombstonedSessions.contains(sessId)) return
        loopScope.launch {
            enqueueOnBusy?.taskId?.let { createDurableTask(sessId, enqueueOnBusy) }
            var refreshQueue = false
            turnCoordinator.withSessionMutex(sessId) {
                if (tombstonedSessions.contains(sessId)) return@withSessionMutex
                // 幽灵复活防线：send() 的协程可能在 deleteSession 全部完成后才拿到锁，
                // 此时 tombstone 已被移除、且并发方可能各自 getOrPut 出不同的 Mutex，
                // tombstone 检查形同虚设。再查一次 DB：会话已删除则拒绝启动 runLoop，
                // 否则会重建数据并发起真实的 LLM 调用。
                if (sessionDao.findById(sessId) == null) return@withSessionMutex
                val selfHealed = checkAndSettleExpiredApprovalsLocked(sessId)
                if (selfHealed) {
                    refreshQueue = true
                }
                if (isSessionBusy(sessId)) {
                    enqueueOnBusy?.let {
                        promptQueueManager.enqueue(sessId, PromptQueue.NEXT_RUN, it)
                        refreshQueue = true
                    }
                    return@withSessionMutex
                }
                if (promptQueueManager.list(sessId, PromptQueue.NEXT_RUN).isNotEmpty()) {
                    enqueueOnBusy?.let {
                        promptQueueManager.enqueue(sessId, PromptQueue.NEXT_RUN, it)
                        refreshQueue = true
                    }
                    startNextQueuedLocked(sessId)
                    return@withSessionMutex
                }
                launchSessionJobLocked(sessId, enqueueOnBusy?.taskId, block = block)
            }
            if (refreshQueue) refreshPendingProjection(sessId)
        }
    }

    /**
     * Claim the session slot unconditionally. Used by approval resumption, which
     * already holds an exclusive claim via claimPending() and is the legitimate
     * successor to a WAITING_APPROVAL run (which still reports busy).
     */
    private fun startClaimedSessionRun(
        sessId: String,
        taskId: String? = null,
        block: suspend () -> RunResult,
    ) {
        if (tombstonedSessions.contains(sessId)) return
        loopScope.launch {
            turnCoordinator.withSessionMutex(sessId) {
                if (tombstonedSessions.contains(sessId)) return@withSessionMutex
                launchSessionJobLocked(
                    sessId = sessId,
                    taskId = taskId,
                    incrementTaskAttempt = false,
                    priority = TurnPriority.HIGH,
                    block = block,
                )
            }
        }
    }

    private suspend fun createDurableTask(sessId: String, pending: PendingMessage) {
        val taskId = pending.taskId ?: return
        agentTaskStateMachine.createQueued(
            id = taskId,
            sessionId = sessId,
            title = pending.text.lineSequence().firstOrNull().orEmpty(),
            description = pending.text,
            nowMs = pending.createdAt,
        )
        agentEventLogger.log(sessId, "DurableTaskQueued", "taskId=$taskId")
    }

    /** Resolve a waiting approval as a cancelled tool call before allowing a new run. */
    private suspend fun rejectPendingApprovalsForCancel(sessId: String) {
        val pending = approvalRepository.pendingNow(sessId)
        if (pending.isEmpty()) return
        val now = now()
        for (request in pending) {
            if (!approvalRepository.claimPending(
                    request.id,
                    top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_REJECTED,
                )
            ) {
                continue
            }
            val result = ToolResult(
                id = newId(),
                createdAt = now,
                toolCallId = request.toolCallId,
                success = false,
                output = "Người dùng đã dừng lần chạy này, công cụ chờ phê duyệt chưa thực thi.",
            )
            val active = operationCoordinator.active(sessId)
            if (active != null && (request.operationId == null || request.operationId == active.id)) {
                operationCoordinator.toolSettled(active.id, result, round = 0, toolName = request.toolName)
                messageProjector.publishPersisted(sessId, result)
            } else {
                messageProjector.append(sessId, result)
            }
        }
        operationCoordinator.finish(sessId, "aborted")
    }

    private fun isCurrentLoad(sessionId: String, generation: Long): Boolean =
        foregroundLoadGeneration.get() == generation && sessionTracker.currentSessionId.value == sessionId

    private suspend fun executeSessionRun(
        sessId: String,
        runEpoch: Long,
        taskId: String?,
        priority: TurnPriority = TurnPriority.NORMAL,
        block: suspend () -> RunResult,
    ) {
        val selfJob = requireNotNull(currentCoroutineContext()[Job])
        try {
            turnCoordinator.withSessionTurn(sessId, priority) {
                stateMirrors.setRunState(sessId, SessionRunState.RUNNING)
                stateMirrors.setError(sessId, null)
                when (val result = block()) {
                    RunResult.Completed -> {
                        taskId?.let { agentTaskStateMachine.markCompleted(it) }
                        operationCoordinator.finish(sessId, "completed", messageProjector.messagesFlow(sessId).value.lastOrNull()?.id)
                        stateMirrors.setRunState(sessId, SessionRunState.COMPLETED)
                        // 「对话后技能进化」：一轮有效工作结束后异步分析是否值得沉淀新技能 /
                        // 修复既有技能；失败与取消路径不触发，且内部全量容错不影响主对话。
                        skillEvolutionAdvisor?.maybeSuggest(sessId)
                    }
                    RunResult.WaitingApproval -> {
                        taskId?.let { agentTaskStateMachine.markWaitingApproval(it) }
                        stateMirrors.setRunState(sessId, SessionRunState.WAITING_APPROVAL)
                        scheduleApprovalTimeout(sessId)
                    }
                    RunResult.Cancelled -> {
                        taskId?.let { agentTaskStateMachine.markCancelled(it) }
                        operationCoordinator.finish(sessId, "aborted")
                        stateMirrors.setRunState(sessId, SessionRunState.IDLE)
                    }
                    is RunResult.Failed -> {
                        taskId?.let { agentTaskStateMachine.markFailed(it, result.message) }
                        operationCoordinator.finish(sessId, "failed", details = result.message)
                        stateMirrors.setError(sessId, result.message)
                        stateMirrors.setRunState(sessId, SessionRunState.FAILED)
                        // 确保错误在前台消息流中明确展示，消除发消息无回复的卡死假象
                        messageProjector.append(
                            sessId,
                            AssistantText(
                                id = newId(),
                                createdAt = now(),
                                text = "❌ Thực thi thất bại: ${result.message}",
                            ),
                        )
                    }
                }
            }
        } catch (_: CancellationException) {
            // cancel() 先入 cancellingSessions 再 cancel job、cancelAndJoin 之后才移除，
            // 因此在本 catch（属于被取消 job 的收尾段，join 会等它跑完）里该集合的
            // 成员关系可判定"Có phải người dùng chủ động dừng không"。会话删除走 tombstoneSessions 而非
            // cancellingSessions，据此避免在正在销毁的会话上补写消息。
            val userInitiated = cancellingSessions.contains(sessId)
            val tombstoned = tombstonedSessions.contains(sessId)
            withContext(NonCancellable) {
                repairDanglingToolCalls(sessId, interrupted = true)
                taskId?.let { agentTaskStateMachine.markCancelled(it) }
                operationCoordinator.finish(sessId, "aborted", details = "cancelled")
                // 原实现只把状态置回 IDLE，前台消息流里什么都不留，用户看到的是
                // "Tin nhắn gửi đi là mất"，与"Gửi luôn bị gián đoạn"的反馈完全吻合。
                if (userInitiated && !tombstoned) {
                    messageProjector.append(
                        sessId,
                        AssistantText(
                            id = newId(),
                            createdAt = now(),
                            text = "⏹ Đã dừng lần chạy này.",
                        ),
                    )
                }
            }
            logger.logAgent(
                sessId,
                "Cancelled",
                if (userInitiated) "Lần chạy này bị người dùng chủ động dừng" else "Lần chạy này bị hệ thống hủy (không phải người dùng dừng)",
            )
            logger.i("Harness loop cancelled for session $sessId")
            stateMirrors.setRunState(sessId, SessionRunState.IDLE)
        } catch (_: ApprovalPauseException) {
            taskId?.let { agentTaskStateMachine.markWaitingApproval(it) }
            stateMirrors.setRunState(sessId, SessionRunState.WAITING_APPROVAL)
            scheduleApprovalTimeout(sessId)
        } catch (throwable: Throwable) {
            logger.e("Harness loop failed for session $sessId", throwable)
            val msg = throwable.message ?: "Thực thi thất bại"
            stateMirrors.setError(sessId, msg)
            taskId?.let { agentTaskStateMachine.markFailed(it, msg) }
            runCatching { operationCoordinator.finish(sessId, "failed", details = msg) }
            stateMirrors.setRunState(sessId, SessionRunState.FAILED)
            messageProjector.append(
                sessId,
                AssistantText(
                    id = newId(),
                    createdAt = now(),
                    text = "❌ Thực thi bất thường: $msg",
                ),
            )
        } finally {
            finishRun(sessId, selfJob, runEpoch)
        }
    }

    private suspend fun runLoop(
        sessId: String,
        userText: String,
        imageUrls: List<String> = emptyList(),
        taskId: String? = null,
    ): RunResult {
        // 拦截器重置已移至 runLoopInternal 入口（覆盖 regenerate/retry/branch 等直达路径）。
        agentEventLogger.log(sessId, "UserPrompt", userText)
        val userMessage = UserMessage(id = newId(), createdAt = now(), text = userText, imageUrls = imageUrls)
        rewindController.beginTurn(sessId, userText, userMessage.id)
        val operationId = operationCoordinator.acceptRun(sessId, userMessage)
        taskId?.let { agentTaskStateMachine.checkpoint(it, operationId, 0, 0, "Nhiệm vụ đã được tiếp nhận") }
        messageProjector.publishPersisted(sessId, userMessage)
        return runLoopInternal(sessId, startedAt = now(), operationId = operationId, taskId = taskId)
    }

    private suspend fun runLoopInternal(
        sessId: String,
        startedAt: Long,
        operationId: String? = null,
        taskId: String? = null,
    ): RunResult {
        // 死循环拦截器在每次运行入口重置：regenerateLast / retryToolCall / activateBranch
        // 直接进入本函数，不重置会带着上一轮的失败连击——用户点"Thử lại gọi công cụ"重新发起的
        // 同一调用会立即命中 sameFailedStreak >= 2 被误杀，重试功能在最该生效时失效。
        sessionLoopDetectors.getOrPut(sessId) { ToolCallLoopDetector() }.reset()
        // Phase 0 基线埋点：每次运行汇总过程指标并写入 Agent 日志（不受日志开关影响），
        // 为"Tỷ lệ tự hoàn thành / Tỷ lệ tự phục hồi / Số lần can thiệp thủ công"等 2.0 目标指标提供 1.0 真实基线。
        val metrics = RunMetrics(startedAt = startedAt)
        try {
            val result = runLoopRounds(sessId, startedAt, operationId, taskId, metrics)
            metrics.finish(
                when (result) {
                    RunResult.Completed -> "completed"
                    RunResult.WaitingApproval -> "waiting_approval"
                    RunResult.Cancelled -> "cancelled"
                    is RunResult.Failed -> "failed"
                },
            )
            // 失败原因必须与 RunMetrics 同源、同等地不受日志开关门控地落盘：
            // agentLoggingEnabled 默认关闭，而 74/545 次运行以 Rounds=1/ToolCalls=0
            // 的形态瞬间 failed，用户侧只表现为"Gửi tin nhắn luôn bị gián đoạn"。只留指标没有原因，
            // 这类投诉永远无法定位。此处记录的是唯一的可行动线索。
            if (result is RunResult.Failed) {
                logger.logAgent(sessId, "RunFailure", "Chạy bị chấm dứt (failed): ${result.message}")
            }
            return result
        } catch (cancellation: CancellationException) {
            metrics.finish("cancelled")
            throw cancellation
        } catch (pause: ApprovalPauseException) {
            metrics.finish("waiting_approval")
            throw pause
        } catch (throwable: Throwable) {
            metrics.finish("error")
            logger.logAgent(
                sessId,
                "RunFailure",
                "Chạy chấm dứt bất thường (error): ${throwable::class.simpleName}: ${throwable.message}",
                throwable,
            )
            throw throwable
        } finally {
            logger.logAgent(sessId, "RunMetrics", metrics.summary())
        }
    }

    private suspend fun runLoopRounds(
        sessId: String,
        startedAt: Long,
        operationId: String?,
        taskId: String?,
        metrics: RunMetrics,
    ): RunResult {
        val activeOperationId = operationId ?: operationCoordinator.beginRun(sessId)
        val maxRounds = runCatching { settingsDataStore.maxToolRounds.first() }.getOrDefault(MAX_ROUNDS)
        val autoContinuations = runCatching { settingsDataStore.roundLimitAutoContinuations.first() }
            .getOrDefault(settingsDataStore.defaultRoundLimitAutoContinuations)
        val budget = RoundBudget(maxRounds, autoContinuations)
        val autoCwd = runCatching { settingsDataStore.autoWorkspaceCwd.first() }.getOrDefault(true)
        val sessionEntity = sessionDao.findById(sessId)
        val sessionWorkspace = sessionEntity?.workspace.orEmpty()
        // 悬空调用修复须带 workspace：SAFE 工具在此重放，读操作需要正确的工作目录
        repairDanglingToolCalls(sessId, interrupted = false, workspace = sessionWorkspace)

        val maxToolsPerRound = runCatching { settingsDataStore.maxToolsPerRound.first() }.getOrDefault(12)
        val maxConsecutiveFailures = runCatching { settingsDataStore.maxConsecutiveFailures.first() }.getOrDefault(8)
        val retryPolicy = RetryPolicy.NETWORK_DEFAULT
        var consecutiveFailures = 0
        var stormHintInjected = false

        while (true) {
            val round = budget.totalRounds
            taskId?.let {
                agentTaskStateMachine.checkpoint(
                    id = it,
                    operationId = activeOperationId,
                    round = round,
                    maxRounds = budget.totalBudget,
                    detail = "Lượt ${round + 1} · Đang suy nghĩ",
                )
            }
            metrics.roundStarted()
            metrics.steeringInjected(drainSteeringMessages(sessId))
            stateMirrors.setStatus(sessId, "Đang tư duy")
            val latestBinding = sessionDao.findById(sessId) ?: sessionEntity
            val model = try {
                providerClient.resolveConfigured(latestBinding?.modelId, latestBinding?.modelVariant)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                agentEventLogger.log(sessId, "ModelResolveError", "Không lấy được cấu hình model", throwable)
                return RunResult.Failed("Không lấy được cấu hình model: ${friendly(throwable)}")
            }
            agentEventLogger.log(sessId, "ModelRequest", "Round=$round, Model=${model.name}, Provider=${model.provider}")
            val effectiveModel = providerRunner.resolveEffectiveModel(sessId, model)
            val assistantId = newId()
            val assistantAt = now()

            val turn = turnRunner.run(
                remainingRounds = budget.remainingInSegment,
                toolsEnabled = !effectiveModel.pureChatMode &&
                    effectiveModel.toolCallMode != ToolCallMode.DISABLED,
                callProvider = {
                    providerRunner.callProviderWithRetry(
                        sessId = sessId,
                        model = effectiveModel,
                        sessionEntity = sessionEntity,
                        sessionWorkspace = sessionWorkspace,
                        operationId = activeOperationId,
                        assistantId = assistantId,
                        assistantAt = assistantAt,
                        round = round,
                        startedAt = startedAt,
                        retryPolicy = retryPolicy,
                        metrics = metrics,
                    )
                },
                observeResponse = { normalized ->
                    agentEventLogger.log(
                        sessId,
                        "ModelResponse",
                        "TextLength=${normalized.rawText.length}, " +
                            "ReasoningLength=${normalized.result.reasoningContent?.length ?: 0}, " +
                            "ToolCallsCount=${normalized.result.toolCalls.size}, " +
                            "TextToolCalls=${normalized.textToolCallCount}, " +
                            "InvalidTextMarkers=${normalized.invalidMarkerCount}, " +
                            // 截断首部：UserPrompt / SteeringMessage 都记全文，模型输出却只留长度，
                            // "Model rốt cuộc đã trả về gì"无从判断。全文会撑爆 5MB 上限的 Agent 日志（会话树
                            // 里已有完整记录），因此只取首部；换行转义以维持单行事件格式，便于逐行检索。
                            "TextHead=" + normalized.rawText.take(MODEL_RESPONSE_HEAD_CHARS)
                                .replace("\n", "\\n"),
                    )
                },
                persistAssistant = { normalized ->
                    providerRunner.persistAssistantOutput(
                        sessId = sessId,
                        assistantId = assistantId,
                        assistantAt = assistantAt,
                        round = round,
                        startedAt = startedAt,
                        operationId = activeOperationId,
                        result = normalized.result,
                        effectiveModel = effectiveModel,
                        displayText = normalized.displayText,
                        hasToolCalls = normalized.toolCalls.isNotEmpty(),
                    )
                    metrics.recordUsage(normalized.result.usage)
                    stateMirrors.setThinkingLive(sessId, false)
                },
                consumeFollowUps = {
                    val followUps = promptQueueManager.consume(sessId, PromptQueue.FOLLOW_UP)
                    refreshPendingProjection(sessId)
                    followUps.forEach { messageProjector.publishPersisted(sessId, it) }
                    metrics.followUpConsumed(followUps.size)
                    followUps.size
                },
                enforceToolLimit = { allCalls, result ->
                    val effectiveCalls = toolRoundRunner.enforceToolRoundLimit(
                        sessId,
                        allCalls,
                        maxToolsPerRound,
                        result.reasoningContent,
                    )
                    metrics.toolCallsDropped(allCalls.size - effectiveCalls.size)
                    effectiveCalls
                },
                executeTools = { effectiveCalls, result ->
                    toolRoundRunner.executeToolCalls(
                        sessId = sessId,
                        specs = effectiveCalls,
                        loopDetector = sessionLoopDetectors.getOrPut(sessId) { ToolCallLoopDetector() },
                        reasoning = result.reasoningContent,
                        sessionWorkspace = sessionWorkspace,
                        autoCwd = autoCwd,
                        effectiveModel = effectiveModel,
                        operationId = activeOperationId,
                        round = round,
                        metrics = metrics,
                    )
                },
            )
            // 连续失败熔断：当一轮内所有工具调用均失败时计数。
            suspend fun tripCircuitBreaker(toolCallCount: Int, toolsHadSuccess: Boolean): RunResult? {
                if (toolCallCount <= 0 || toolsHadSuccess) {
                    consecutiveFailures = 0
                    return null
                }
                consecutiveFailures++
                metrics.consecutiveFailuresObserved(consecutiveFailures)
                // 软收敛提示（storm breaker）：早于硬熔断的一次性 steering 注入，
                // 让模型在"Thất bại liên tiếp"与"Bị dừng cứng"之间有一次换方法自纠的机会。成功即清零、
                // 每次运行只提示一次；硬阈值被用户调低到软阈值之下时自然不触发。
                if (!stormHintInjected &&
                    consecutiveFailures >= STORM_HINT_THRESHOLD &&
                    consecutiveFailures < maxConsecutiveFailures
                ) {
                    stormHintInjected = true
                    stormHintFor(consecutiveFailures)?.let { hint ->
                        promptQueueManager.enqueue(sessId, PromptQueue.STEER, PendingMessage(text = hint))
                        agentEventLogger.log(sessId, "StormAdvisory", "consecutiveFailures=$consecutiveFailures")
                    }
                }
                if (consecutiveFailures < maxConsecutiveFailures) return null
                metrics.circuitBreaker()
                messageProjector.append(
                    sessId,
                    AssistantText(
                        id = newId(),
                        createdAt = now(),
                        text = "Đã $consecutiveFailures lượt gọi công cụ đều thất bại, đã chủ động dừng để tránh rơi vào vòng lặp chết." +
                            "Vui lòng kiểm tra: lệnh có đúng không, đường dẫn vùng làm việc có tồn tại không, phụ thuộc đã cài chưa, hoặc đơn giản hóa nhiệm vụ rồi thử lại.",
                        totalMs = now() - startedAt,
                    ),
                )
                return RunResult.Failed("Đã $consecutiveFailures lượt gọi công cụ đều thất bại, đã chủ động dừng")
            }

            when (turn) {
                is TurnOutcome.Failed -> return RunResult.Failed(turn.message)
                TurnOutcome.Complete -> return RunResult.Completed
                is TurnOutcome.Continue -> {
                    tripCircuitBreaker(turn.effectiveToolCallCount, turn.toolsHadSuccess)?.let { return it }
                    budget.advance()
                }
                is TurnOutcome.RoundLimit -> {
                    tripCircuitBreaker(turn.effectiveToolCallCount, turn.toolsHadSuccess)?.let { return it }
                    budget.advance()
                    if (!budget.canContinue()) return roundBudgetExhausted(sessId, startedAt, budget)
                    val attempt = budget.beginNextSegment()
                    metrics.budgetContinued()
                    injectBudgetCheckpoint(sessId, budget, attempt)
                }
            }
        }
    }

    /**
     * 轮次预算用尽且不再续跑：这是本次运行的终点，给出带具体数字的说明，便于用户判断
     * 到底是预算设小了还是模型跑偏了。
     */
    private suspend fun roundBudgetExhausted(
        sessId: String,
        startedAt: Long,
        budget: RoundBudget,
    ): RunResult {
        val detail = if (budget.maxContinuations > 0) {
            "Đã dùng hết toàn bộ ngân sách lượt công cụ (mỗi đoạn ${budget.roundsPerSegment} lượt × ${budget.maxContinuations + 1} đoạn, " +
                "tổng cộng ${budget.totalBudget} lượt)"
        } else {
            "Đã đạt số lượt công cụ tối đa (${budget.roundsPerSegment})"
        }
        agentEventLogger.log(sessId, "RoundBudgetExhausted", detail)
        messageProjector.append(
            sessId,
            AssistantText(
                id = newId(),
                createdAt = now(),
                text = "$detail, hãy đơn giản hóa nhiệm vụ, tăng ngân sách lượt hoặc chia bước thực hiện.",
                totalMs = now() - startedAt,
            ),
        )
        return RunResult.Failed("$detail, nhiệm vụ chưa xác nhận hoàn thành")
    }

    /**
     * 软检查点：把"Hết ngân sách"从硬停机改成一次收束。续跑提示以 steering 消息入队，下一轮开头
     * 被消费成持久化的用户消息——既让模型在新一段预算前先落盘进度，也让这次自动续跑在
     * 会话记录里留痕，进程被杀后同样可恢复。
     */
    private suspend fun injectBudgetCheckpoint(sessId: String, budget: RoundBudget, attempt: Int) {
        val rounds = budget.roundsPerSegment
        val prompt = buildString {
            append("[Tự động tiếp chạy $attempt/${budget.maxContinuations}] Ngân sách $rounds lượt công cụ của đoạn này đã dùng hết, nhiệm vụ vẫn chưa kết thúc.\n\n")
            append("Trước khi tiếp tục hãy thu gọn một lần:\n")
            append("1. Dùng vài câu nói rõ đã làm xong gì, tiến triển hiện tại, còn gì chưa làm;\n")
            append("2. Nếu nhiệm vụ còn phải thực thi xuyên đoạn, hãy ghi tiến độ trên vào PROGRESS.md của vùng làm việc, đảm bảo sau khi gián đoạn có thể làm tiếp;\n")
            append("3. Nếu nhiệm vụ thực ra đã hoàn thành, hãy đưa ra kết luận cuối cùng trực tiếp, đừng gọi công cụ nữa.\n\n")
            append("Sau khi thu gọn bạn sẽ nhận được ngân sách $rounds lượt mới, có thể tiếp tục ngay, không cần chờ người dùng xác nhận.")
        }
        promptQueueManager.enqueue(sessId, PromptQueue.STEER, PendingMessage(text = prompt))
        agentEventLogger.log(
            sessId,
            "RoundBudgetContinued",
            "attempt=$attempt/${budget.maxContinuations}, roundsPerSegment=$rounds",
        )
        stateMirrors.setStatus(sessId, "Ngân sách lượt đã dùng hết, tự động tiếp chạy $attempt/${budget.maxContinuations}…")
    }

    private suspend fun drainSteeringMessages(sessId: String): Int {
        val queued = promptQueueManager.consume(sessId, PromptQueue.STEER)
        refreshPendingProjection(sessId)
        queued.forEach { message ->
            agentEventLogger.log(sessId, "SteeringMessage", message.text)
            messageProjector.publishPersisted(sessId, message)
        }
        return queued.size
    }

    private fun friendly(throwable: Throwable): String =
        throwable.message?.take(200) ?: throwable::class.simpleName.orEmpty()

    private suspend fun touchSession(sessId: String) {
        sessionDao.touch(sessId, System.currentTimeMillis())
    }

    private fun startForegroundServiceSafe() {
        foregroundLauncher.start()
    }

    private fun newId(): String = UUID.randomUUID().toString()
    private fun now(): Long = System.currentTimeMillis()

    /** Approve or reject a frozen tool call, then resume the same Agent session. */
    fun resolveApproval(requestId: String, approved: Boolean, rememberForSession: Boolean = false) {
        logger.i("resolveApproval called: requestId=$requestId approved=$approved remember=$rememberForSession")
        loopScope.launch {
            val request = approvalRepository.find(requestId)
            if (request == null) {
                logger.w("resolveApproval: request not found: $requestId")
                return@launch
            }
            val sessId = request.sessionId
            cancelApprovalTimeout(sessId)
            if (request.status != top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_PENDING) {
                logger.w("resolveApproval: request not pending (status=${request.status}), ignoring: $requestId")
                return@launch
            }
            logger.i("resolveApproval: waiting for prior session job, sessId=$sessId")
            // The original loop may still be unwinding after it persisted the request.
            // Wait for it before claiming the session slot.
            sessionJobs[sessId]?.takeIf { it.isActive }?.join()

            // —— 审批有效性校验：过期 / 参数摘要 / 工作区 / operation 归属 ——
            // 防止“用户批准的是旧参数、旧环境下的请求，实际执行的却是别的东西”。
            val verdict = resumePolicy.evaluate(request, approved)
            if (!approvalRepository.claimPending(request.id, verdict.claimStatus)) return@launch
            val durableTaskId = agentTaskStateMachine.activeForSession(sessId)
                ?.takeIf { it.status == top.wkbin.tianxuan.core.database.task.AgentTaskStatus.WAITING_APPROVAL }
                ?.id

            // Approval resumption is the legitimate successor to a WAITING_APPROVAL run;
            // claim the slot unconditionally (that state still reports busy to senders).
            startClaimedSessionRun(sessId, durableTaskId) {
                var approvalResultPersisted = false
                try {
                    val result = if (verdict.isInvalid) {
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = request.toolCallId,
                            success = false,
                            output = resumePolicy.invalidationResultMessage(verdict.invalidationReason.orEmpty()),
                        )
                    } else if (approved) {
                        // 「本会话内记住」：用户显式勾选时把该操作的规范化类别写入会话授权表。
                        // critical 风险永不记忆；表纯内存、随会话删除/进程退出销毁（无永久授权）。
                        if (rememberForSession && !request.riskLevel.equals("critical", ignoreCase = true)) {
                            sessionApprovalGrants.grant(
                                request.sessionId,
                                request.toolName,
                                request.argumentsJson,
                                request.riskLevel,
                            )
                        }
                        val args = json.parseToJsonElement(request.argumentsJson) as? JsonObject
                            ?: error("Tham số phê duyệt không phải đối tượng JSON")
                        val tool = HarnessApiMapper.toolByName(request.toolName)
                        // 被批准的通常是 write/base/mcp 等变更类工具：必须与 ToolRoundDispatcher
                        // 走同一把（按工作区分片的）变更互斥锁，否则用户批准的写入会与并发
                        // 会话的同工作区命令并发执行，正是互斥锁要防的写踩踏。
                        toolRoundDispatcher.withMutationLock(request.workspace) {
                            toolExecutor.execute(
                                ToolCall(request.toolCallId, request.createdAt, tool, args, rawToolName = request.toolName),
                                sessId,
                                request.workspace,
                                bypassApproval = true,
                                operationId = request.operationId,
                            )
                        }
                    } else {
                        ToolResult(
                            id = newId(),
                            createdAt = now(),
                            toolCallId = request.toolCallId,
                            success = false,
                            output = resumePolicy.rejectionResultMessage(),
                        )
                    }
                    val activeOperation = operationCoordinator.active(sessId)
                    if (activeOperation != null) {
                        operationCoordinator.toolSettled(activeOperation.id, result, round = 0, toolName = request.toolName)
                        messageProjector.publishPersisted(sessId, result)
                    } else {
                        messageProjector.append(sessId, result)
                    }
                    if (!verdict.isInvalid) {
                        approvalRepository.mark(
                            request.id,
                            resumePolicy.finalStatus(approved, result.success),
                        )
                    }
                    approvalResultPersisted = true
                    runLoopInternal(sessId, startedAt = now(), taskId = durableTaskId)
                } catch (cancellation: CancellationException) {
                    if (!approvalResultPersisted) {
                        withContext(NonCancellable) {
                            approvalRepository.mark(
                                request.id,
                                if (approved) top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_FAILED
                                else top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_REJECTED,
                            )
                            repairDanglingToolCalls(sessId, interrupted = true)
                        }
                    }
                    throw cancellation
                } catch (_: ApprovalPauseException) {
                    // 批准后继续循环，下一个工具又触发了审批门控——这是正常流程，不是失败。
                    // 外层 executeSessionRun 会把状态置为 WAITING_APPROVAL，等待用户下一次批准。
                    RunResult.WaitingApproval
                } catch (throwable: Throwable) {
                    logger.e("Approval resolution failed for request ${request.id}", throwable)
                    if (!approvalResultPersisted) {
                        approvalRepository.mark(request.id, top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_FAILED)
                        messageProjector.append(
                            sessId,
                            ToolResult(
                                id = newId(),
                                createdAt = now(),
                                toolCallId = request.toolCallId,
                                success = false,
                                output = "Thực thi thao tác phê duyệt thất bại: ${friendly(throwable)}",
                            ),
                        )
                    }
                    RunResult.Failed(throwable.message ?: "Thực thi thao tác phê duyệt thất bại: ${throwable::class.simpleName}")
                }
            }
        }
    }

    /**
     * 用户回答 ask_user 提问（answersJson = `[{"index":0,"answer":"..."}]`）：
     * 与 resolveApproval 同轨（认领 → 占位启动 → 续跑），但无需重执行工具——
     * 答案经 [AskUserQuestions.formatAnswers] 直接作为该 toolCall 的结果落库。
     */
    fun resolveQuestion(requestId: String, answersJson: String) {
        logger.i("resolveQuestion called: requestId=$requestId")
        loopScope.launch {
            val request = approvalRepository.find(requestId)
            if (request == null) {
                logger.w("resolveQuestion: request not found: $requestId")
                return@launch
            }
            val sessId = request.sessionId
            cancelApprovalTimeout(sessId)
            if (request.status != top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_PENDING) {
                logger.w("resolveQuestion: request not pending (status=${request.status}), ignoring: $requestId")
                return@launch
            }
            sessionJobs[sessId]?.takeIf { it.isActive }?.join()
            val verdict = resumePolicy.evaluate(request, approved = true)
            if (!approvalRepository.claimPending(request.id, verdict.claimStatus)) {
                return@launch
            }
            if (verdict.isInvalid) {
                val durableTaskId = agentTaskStateMachine.activeForSession(sessId)
                    ?.takeIf { it.status == top.wkbin.tianxuan.core.database.task.AgentTaskStatus.WAITING_APPROVAL }
                    ?.id
                startClaimedSessionRun(sessId, durableTaskId) {
                    val result = ToolResult(
                        id = newId(), createdAt = now(), toolCallId = request.toolCallId,
                        success = false,
                        output = resumePolicy.invalidationResultMessage(verdict.invalidationReason.orEmpty()),
                    )
                    messageProjector.append(sessId, result)
                    approvalRepository.mark(request.id, verdict.claimStatus)
                    runLoopInternal(sessId, startedAt = now(), taskId = durableTaskId)
                }
                return@launch
            }
            val durableTaskId = agentTaskStateMachine.activeForSession(sessId)
                ?.takeIf { it.status == top.wkbin.tianxuan.core.database.task.AgentTaskStatus.WAITING_APPROVAL }
                ?.id
            startClaimedSessionRun(sessId, durableTaskId) {
                var resultPersisted = false
                try {
                    val result = ToolResult(
                        id = newId(),
                        createdAt = now(),
                        toolCallId = request.toolCallId,
                        success = true,
                        output = AskUserQuestions.formatAnswers(request.argumentsJson, answersJson),
                    )
                    val activeOperation = operationCoordinator.active(sessId)
                    if (activeOperation != null) {
                        operationCoordinator.toolSettled(activeOperation.id, result, round = 0, toolName = request.toolName)
                        messageProjector.publishPersisted(sessId, result)
                    } else {
                        messageProjector.append(sessId, result)
                    }
                    approvalRepository.mark(request.id, top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_EXECUTED)
                    resultPersisted = true
                    runLoopInternal(sessId, startedAt = now(), taskId = durableTaskId)
                } catch (cancellation: CancellationException) {
                    if (!resultPersisted) {
                        withContext(NonCancellable) {
                            approvalRepository.mark(request.id, top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_FAILED)
                            repairDanglingToolCalls(sessId, interrupted = true)
                        }
                    }
                    throw cancellation
                } catch (_: ApprovalPauseException) {
                    // 答案落库后继续循环，下一个工具又触发审批门控——正常流程。
                    RunResult.WaitingApproval
                } catch (throwable: Throwable) {
                    logger.e("Question resolution failed for request ${request.id}", throwable)
                    if (!resultPersisted) {
                        approvalRepository.mark(request.id, top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity.STATUS_FAILED)
                    }
                    RunResult.Failed(throwable.message ?: "Nộp câu trả lời thất bại: ${throwable::class.simpleName}")
                }
            }
        }
    }

    companion object {
        internal fun maxNetworkRetriesFor(estimatedRequestTokens: Int, configuredRetries: Int): Int =
            HarnessProviderRunner.maxNetworkRetriesFor(estimatedRequestTokens, configuredRetries)
        const val RETRY_BACKOFF_MS = 1_000L
        const val RETRY_BACKOFF_SEC = 2L

        const val MAX_ROUNDS = 200

        /**
         * ModelResponse 事件里记录的模型输出首部长度。只取首部是刻意的：Agent 日志上限 5 MB、
         * 超限即轮转，长回答全文写入会用一次响应挤掉整段历史；诊断只需"Mở đầu đã nói gì"。
         */
        internal const val MODEL_RESPONSE_HEAD_CHARS = 500

        /** 软收敛提示阈值：连续 N 轮工具全失败即注入一次 steering 提示（须低于硬熔断默认值）。 */
        internal const val STORM_HINT_THRESHOLD = 3

        /** storm breaker 提示文案（纯函数便于测试）；未达阈值返回 null。 */
        internal fun stormHintFor(consecutiveFailures: Int): String? {
            if (consecutiveFailures < STORM_HINT_THRESHOLD) return null
            return buildString {
                append("[Gợi ý tự động] Đã liên tiếp $consecutiveFailures lượt mọi lời gọi công cụ đều thất bại, phương pháp hiện tại nhiều khả năng không khả thi.\n")
                append("Hãy dừng lại phản tỉnh rồi mới bắt tay:\n")
                append("1. So sánh đầu ra lỗi của vài lần thất bại này, tìm ra nguyên nhân chung (đường dẫn không tồn tại? không đủ quyền? môi trường/phụ thuộc chưa sẵn sàng? định dạng tham số?);\n")
                append("2. Đổi sang phương pháp chắc chắn hơn, hoặc dùng read/base làm rõ điều kiện tiền đề trước rồi thử lại;\n")
                append("3. Nếu đúng là giới hạn môi trường không thể tự giải quyết, hãy nói thẳng với người dùng về trở ngại và các bước đã thử, đừng cố chịu đựng.\n")
                append("Đừng tiếp tục tiêu hao các lượt còn lại theo cách tương tự.")
            }
        }
        val KNOWN_TOOL_NAMES: Set<String> = HarnessToolRoundRunner.KNOWN_TOOL_NAMES

        /** 工作流节点专属 Harness 会话的 id 前缀（workflow:<executionId>:<nodeId>）。 */
        const val WORKFLOW_SESSION_PREFIX = "workflow:"
    }
}
