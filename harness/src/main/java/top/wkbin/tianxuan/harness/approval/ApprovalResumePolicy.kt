package top.wkbin.tianxuan.harness.approval

import top.wkbin.tianxuan.core.database.AgentApprovalRequestEntity
import top.wkbin.tianxuan.core.database.HarnessSessionRepository
import top.wkbin.tianxuan.harness.ApprovalPolicyEngine
import top.wkbin.tianxuan.harness.operation.OperationCoordinator

/**
 * 审批恢复执行前的四重校验裁决：
 *
 * 1. 过期：到期未决的请求自动失效；
 * 2. 参数摘要：防止“批准的是旧参数、执行的是新参数”；
 * 3. 工作区：审批后用户切换了工作区，环境已改变；
 * 4. operation 归属：审批所属的运行已结束或被新运行接管。
 *
 * 返回 null 表示有效；非 null 为拒绝原因（会作为 ToolResult 写回，
 * 让模型知晓未执行的理由并重新发起）。
 */
data class ApprovalVerdict(
    val invalidationReason: String?,
    /** claimPending 时写入的请求状态：EXPIRED / FAILED / APPROVED / REJECTED。 */
    val claimStatus: String,
) {
    val isInvalid: Boolean get() = invalidationReason != null
}

class ApprovalResumePolicy(
    private val sessionDao: HarnessSessionRepository,
    private val operationCoordinator: OperationCoordinator,
) {
    suspend fun evaluate(
        request: AgentApprovalRequestEntity,
        approved: Boolean,
        nowMs: Long = System.currentTimeMillis(),
    ): ApprovalVerdict {
        val invalidation = invalidationOf(request, nowMs)
        val claimStatus = when {
            invalidation != null && request.expiresAt <= nowMs -> AgentApprovalRequestEntity.STATUS_EXPIRED
            invalidation != null -> AgentApprovalRequestEntity.STATUS_FAILED
            approved -> AgentApprovalRequestEntity.STATUS_APPROVED
            else -> AgentApprovalRequestEntity.STATUS_REJECTED
        }
        return ApprovalVerdict(invalidation, claimStatus)
    }

    suspend fun invalidationOf(
        request: AgentApprovalRequestEntity,
        nowMs: Long = System.currentTimeMillis(),
    ): String? {
        if (request.expiresAt <= nowMs) {
            val ttlMinutes = ApprovalPolicyEngine.APPROVAL_TTL_MS / 60_000L
            return "Phê duyệt này đã hết hạn (chờ quá $ttlMinutes phút)"
        }
        if (request.argsHash.isNotBlank() && request.argsHash != ApprovalPolicyEngine.argsHash(request.argumentsJson)) {
            return "Kiểm tra tổng kết tham số của bản ghi phê duyệt không khớp, phê duyệt có thể đã hỏng"
        }
        val currentWorkspace = sessionDao.findById(request.sessionId)?.workspace.orEmpty()
        if (currentWorkspace != request.workspace) {
            return "Vùng làm việc hội thoại đã thay đổi (khi phê duyệt: ${request.workspace.ifBlank { "无" }}, hiện tại: ${currentWorkspace.ifBlank { "无" }}）"
        }
        val boundOperationId = request.operationId
        if (boundOperationId != null && !operationCoordinator.operationExists(boundOperationId)) {
            return "Lần chạy sở hữu phê duyệt này đã kết thúc hoặc bị lần chạy mới tiếp quản"
        }
        return null
    }

    /** 工具实际执行完成后的终态落库状态。 */
    fun finalStatus(approved: Boolean, resultSuccess: Boolean): String = when {
        !approved -> AgentApprovalRequestEntity.STATUS_REJECTED
        resultSuccess -> AgentApprovalRequestEntity.STATUS_EXECUTED
        else -> AgentApprovalRequestEntity.STATUS_FAILED
    }

    fun invalidationResultMessage(reason: String): String =
        "$reason. Lời gọi công cụ này chưa thực thi; nếu vẫn cần, hãy khởi tạo lại."

    fun rejectionResultMessage(): String =
        "Người dùng đã từ chối thao tác công cụ này. Vui lòng tôn trọng quyết định của người dùng và chọn phương án thay thế không cần quyền đó."
}
