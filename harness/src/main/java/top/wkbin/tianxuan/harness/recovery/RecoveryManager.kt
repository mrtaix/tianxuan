package top.wkbin.tianxuan.harness.recovery

import java.util.UUID
import kotlinx.serialization.json.Json
import top.wkbin.tianxuan.core.database.AgentApprovalRepository
import top.wkbin.tianxuan.core.database.HarnessRuntimeRepository
import top.wkbin.tianxuan.harness.ToolResult
import top.wkbin.tianxuan.harness.events.HarnessEvent
import top.wkbin.tianxuan.harness.events.HarnessEventBus
import top.wkbin.tianxuan.harness.operation.OperationCoordinator
import top.wkbin.tianxuan.harness.operation.OperationPhase
import top.wkbin.tianxuan.harness.operation.OperationSnapshot
import top.wkbin.tianxuan.harness.operation.OperationStatus
import top.wkbin.tianxuan.harness.operation.ReplayPolicy
import top.wkbin.tianxuan.harness.session.SessionTreeStore

sealed interface RecoveryOutcome {
    data object Clean : RecoveryOutcome
    data class Suspended(val operationId: String, val reason: String) : RecoveryOutcome
    data class ToolInterrupted(val operationId: String, val toolCallId: String) : RecoveryOutcome
    data object WaitingApproval : RecoveryOutcome
}

/** Applies explicit crash policy from the last durable operation snapshot. */
class RecoveryManager(
    private val repository: HarnessRuntimeRepository,
    private val coordinator: OperationCoordinator,
    private val approvalRepository: AgentApprovalRepository? = null,
    private val json: Json,
    private val eventBus: HarnessEventBus,
) {
    suspend fun recoverSession(sessionId: String): RecoveryOutcome {
        val laneNames = listOf(SessionTreeStore.MAIN_LANE) +
            repository.listActiveOperations(sessionId).map { it.laneName }
                .filter { it != SessionTreeStore.MAIN_LANE }.distinct()
        var mainOutcome: RecoveryOutcome = RecoveryOutcome.Clean
        for (laneName in laneNames) {
            // An old operation row can survive a lane move; only its current pointer is recoverable.
            if (laneName != SessionTreeStore.MAIN_LANE && repository.findLane(sessionId, laneName)?.currentOperationId == null) continue
            val outcome = recoverSessionInternal(sessionId, laneName)
            if (laneName == SessionTreeStore.MAIN_LANE) mainOutcome = outcome
            if (outcome !is RecoveryOutcome.Clean) {
                val detail = when (outcome) {
                    is RecoveryOutcome.Suspended -> outcome.reason
                    is RecoveryOutcome.ToolInterrupted -> "Công cụ không thể phát lại đã ghi kết quả gián đoạn"
                    RecoveryOutcome.WaitingApproval -> "Có phê duyệt đang chờ"
                    RecoveryOutcome.Clean -> null
                }
                val operationId = when (outcome) {
                    is RecoveryOutcome.Suspended -> outcome.operationId
                    is RecoveryOutcome.ToolInterrupted -> outcome.operationId
                    else -> null
                }
                eventBus.emit(
                    HarnessEvent.RecoveryApplied(
                        sessionId = sessionId,
                        timestamp = System.currentTimeMillis(),
                        operationId = operationId,
                        outcome = outcome::class.simpleName ?: "unknown",
                        detail = detail,
                    ),
                )
            }
        }
        return mainOutcome
    }

    private suspend fun recoverSessionInternal(sessionId: String, laneName: String): RecoveryOutcome {
        val operation = coordinator.active(sessionId, laneName) ?: return RecoveryOutcome.Clean
        if (operation.status == OperationStatus.WAITING_APPROVAL.id) {
            // 审批等待期间进程死亡：审批可能已在停机期间过期/丢失。
            // pendingNow 会先清扫过期请求；无可恢复审批时终止该操作，避免 lane 永久占用。
            if (approvalRepository != null && approvalRepository.pendingNow(sessionId).isEmpty()) {
                coordinator.finish(sessionId, "aborted", details = "Phê duyệt đang chờ đã hết hạn hoặc mất hiệu lực", laneName = operation.laneName)
                return RecoveryOutcome.Suspended(operation.id, "Phê duyệt đang chờ đã hết hạn hoặc mất hiệu lực")
            }
            return RecoveryOutcome.WaitingApproval
        }
        val snapshot = runCatching {
            json.decodeFromString(OperationSnapshot.serializer(), operation.stateJson)
        }.getOrElse {
            coordinator.suspendOperation(operation.id, "Không thể giải mã trạng thái chạy đã lưu bền vững")
            return RecoveryOutcome.Suspended(operation.id, "Trạng thái chạy bị hỏng")
        }

        if (snapshot.phase == OperationPhase.TOOL_INTENT.id && operation.replayPolicy == ReplayPolicy.NEVER.id) {
            val toolCallId = snapshot.effectId ?: operation.pendingEffectId ?: "unknown"
            coordinator.toolSettled(
                operationId = operation.id,
                message = ToolResult(
                    id = UUID.randomUUID().toString(),
                    createdAt = System.currentTimeMillis(),
                    toolCallId = toolCallId,
                    success = false,
                    output = "Tiến trình ứng dụng bị gián đoạn trong khi công cụ thực thi. Công cụ này được khai báo là không thể phát lại an toàn, vì vậy không thực thi lại.",
                ),
                round = snapshot.round,
            )
            coordinator.suspendOperation(operation.id, "Công cụ không thể phát lại đã ghi kết quả gián đoạn, chờ khôi phục chạy")
            return RecoveryOutcome.ToolInterrupted(operation.id, toolCallId)
        }

        val reason = when {
            snapshot.phase == OperationPhase.TOOL_INTENT.id -> "Công cụ có thể phát lại an toàn bị gián đoạn trong lúc thực thi"
            snapshot.phase == OperationPhase.PROVIDER_INTENT.id -> "Kết quả yêu cầu model chưa lưu đĩa, không xác định được có phát sinh phí hay không"
            else -> "Vận hành bị gián đoạn ngoài checkpoint"
        }
        coordinator.suspendOperation(operation.id, reason)
        return RecoveryOutcome.Suspended(operation.id, reason)
    }
}
