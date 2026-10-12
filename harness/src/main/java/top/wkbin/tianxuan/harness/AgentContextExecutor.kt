package top.wkbin.tianxuan.harness

import top.wkbin.tianxuan.core.database.AgentContextRepository
import top.wkbin.tianxuan.core.database.AgentMemoryEntity
import top.wkbin.tianxuan.core.database.AgentPlanEntity
import top.wkbin.tianxuan.core.database.AgentScratchpadEntity
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 负责执行 agent-context 核心系统能力工具：
 * - memory: 长期语义与事实记忆
 * - plan: 多步骤任务执行计划管理
 * - scratchpad: 任务局部工作草稿与排查便签
 */
class AgentContextExecutor(
    private val agentContextDao: AgentContextRepository,
    private val json: Json,
) {
    suspend fun executeMemory(args: JsonObject, sessionId: String, workspace: String): Pair<Boolean, String> {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "list"
        return when (action) {
            "save" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "memory save bắt buộc cung cấp tham số key"
                val value = args["value"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "memory save bắt buộc cung cấp tham số value"
                if (key.length > MAX_MEMORY_KEY_CHARS) return false to "memory key không được vượt quá $MAX_MEMORY_KEY_CHARS ký tự"
                if (value.length > MAX_MEMORY_VALUE_CHARS) return false to "memory value không được vượt quá $MAX_MEMORY_VALUE_CHARS ký tự"
                val kind = args["kind"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "fact"
                val scope = args["scope"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    ?: if (workspace.isNotBlank()) "project" else "global"
                val ownerId = memoryOwner(scope, sessionId, workspace)
                    ?: return false to "scope chỉ hỗ trợ global/project/session, và project/session phải có ngữ cảnh tương ứng"
                val subjectKey = args["subjectKey"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?.takeIf { it.isNotBlank() } ?: key
                val volatility = args["volatility"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    ?.takeIf { it in setOf("reference", "project", "user") } ?: "reference"
                val pinned = (args["pinned"]?.jsonPrimitive?.contentOrNull?.lowercase()) == "true"
                val expiresAt = args["expiresAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                val now = System.currentTimeMillis()

                // 同主题冲突去重：同 (scope, ownerId, subjectKey) 只有一个活动修订。
                val existing = agentContextDao.getMemoryBySubjectKey(subjectKey, scope, ownerId)
                    ?: agentContextDao.getMemoryByKey(key, scope, ownerId)
                if (existing == null && agentContextDao.countMemories(scope, ownerId) >= MAX_MEMORIES_PER_OWNER) {
                    return false to "[$scope] ký ức đã đạt giới hạn $MAX_MEMORIES_PER_OWNER mục, hãy xoá ký ức cũ rồi lưu lại"
                }
                if (pinned) {
                    val pinnedBudget = MAX_PINNED_CHARS - agentContextDao.getPinnedMemories(projectOwner(workspace), sessionId)
                        .filter { it.id != existing?.id }.sumOf { it.value.length }
                    if (value.length > pinnedBudget) {
                        return false to "Tổng ký tự của ký ức pinned đã gần đạt giới hạn (~$MAX_PINNED_CHARS), vui lòng tinh gọn nội dung hoặc chuyển sang không pinned"
                    }
                }
                val id = existing?.id ?: UUID.randomUUID().toString()
                val valueChanged = existing?.value != value
                agentContextDao.saveMemory(
                    AgentMemoryEntity(
                        id = id,
                        scope = scope,
                        ownerId = ownerId,
                        kind = kind,
                        key = key,
                        value = value,
                        subjectKey = subjectKey,
                        revision = (existing?.revision ?: 0) + if (valueChanged) 1 else 0,
                        pinned = pinned,
                        expiresAt = expiresAt,
                        lastVerifiedAt = existing?.lastVerifiedAt ?: 0,
                        volatility = volatility,
                        createdAt = existing?.createdAt ?: now,
                        updatedAt = now,
                    )
                )
                val conflictNote = if (existing != null && subjectKey != key && existing.key != subjectKey) {
                    " cùng chủ đề ($subjectKey) khử trùng lặp và nâng lên revision=${existing.revision + if (valueChanged) 1 else 0}"
                } else ""
                val savedMessage = if (existing != null) {
                    val revNote = if (valueChanged) {
                        ", nâng lên revision=${existing.revision + 1}"
                    } else {
                        "(nội dung không thay đổi, revision=${existing.revision})"
                    }
                    "Đã cập nhật ký ức dài hạn hiện có[$revNote, giá trị cũ bị ghi đè][$scope/$kind] $key = $value$conflictNote"
                } else {
                    "Đã lưu thành công ký ức dài hạn [$scope/$kind] $key = $value"
                }
                true to savedMessage
            }
            "verify" -> {
                // 新鲜度续期：模型确认记忆仍有效时刷新 lastVerifiedAt。
                val id = args["id"]?.jsonPrimitive?.contentOrNull?.trim()
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                val target = when {
                    id != null -> agentContextDao.getMemoryById(id)
                    key != null -> {
                        val scope = args["scope"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "global"
                        val ownerId = memoryOwner(scope, sessionId, workspace)
                            ?: return false to "scope chỉ hỗ trợ global/project/session"
                        agentContextDao.getMemoryByKey(key, scope, ownerId)
                    }
                    else -> return false to "memory verify cần cung cấp id hoặc key"
                } ?: return false to "Không tìm thấy ký ức cần xác minh"
                agentContextDao.touchMemory(target.id, System.currentTimeMillis())
                true to "Đã làm mới độ tươi mới của ký ức ${target.key} (gia hạn lastVerifiedAt)"
            }
            "query", "search" -> {
                val query = args["query"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "memory query bắt buộc cung cấp query hoặc key"
                val includeExpired = (args["include_expired"]?.jsonPrimitive?.contentOrNull?.lowercase()) == "true"
                val results = agentContextDao.searchMemories(
                    query = query.take(MAX_MEMORY_QUERY_CHARS),
                    projectOwnerId = projectOwner(workspace),
                    sessionId = sessionId,
                ).filter { includeExpired || isFresh(it, System.currentTimeMillis()) }
                if (results.isEmpty()) {
                    true to "Không tra được bộ nhớ liên quan tới '$query'"
                } else {
                    val formatted = results.joinToString("\n") { it.render(lineBreak = true) }
                    true to "Đã truy vấn được các ký ức sau:\n$formatted"
                }
            }
            "list" -> {
                val includeExpired = (args["include_expired"]?.jsonPrimitive?.contentOrNull?.lowercase()) == "true"
                val results = agentContextDao.getFreshMemories(
                    projectOwnerId = projectOwner(workspace),
                    sessionId = sessionId,
                    pinned = false,
                    now = System.currentTimeMillis(),
                    limit = MAX_MEMORY_LIST,
                ).plus(
                    if (includeExpired) agentContextDao.getMemoriesForContext(projectOwner(workspace), sessionId)
                        .filter { !isFresh(it, System.currentTimeMillis()) }
                    else emptyList(),
                ).distinctBy { it.id }
                if (results.isEmpty()) {
                    true to "Hiện chưa có ký ức dài hạn"
                } else {
                    val formatted = results.joinToString("\n") { it.render() }
                    true to "Danh sách ký ức đã lưu:\n$formatted"
                }
            }
            "delete" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                val id = args["id"]?.jsonPrimitive?.contentOrNull?.trim()
                if (id != null) {
                    val memory = agentContextDao.getMemoryById(id)
                        ?: return false to "Không tìm thấy bộ nhớ id=$id"
                    val visible = when (memory.scope) {
                        "global" -> memory.ownerId.isEmpty()
                        "project" -> memory.ownerId == projectOwner(workspace) && memory.ownerId.isNotBlank()
                        "session" -> memory.ownerId == sessionId && sessionId.isNotBlank()
                        else -> false
                    }
                    if (!visible) return false to "Không có quyền xóa ký ức không thuộc dự án hoặc phiên hiện tại"
                    agentContextDao.deleteMemoryById(id)
                    true to "Đã xóa ký ức id=$id"
                } else if (key != null) {
                    val scope = args["scope"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "global"
                    val ownerId = memoryOwner(scope, sessionId, workspace)
                        ?: return false to "scope chỉ hỗ trợ global/project/session, và project/session phải có ngữ cảnh tương ứng"
                    agentContextDao.deleteMemoryByKey(key, scope, ownerId)
                    true to "Đã xóa ký ức [$scope] key=$key"
                } else {
                    false to "memory delete cần cung cấp id hoặc key"
                }
            }
            else -> false to "Động tác memory không rõ: $action"
        }
    }

    private fun memoryOwner(scope: String, sessionId: String, workspace: String): String? = when (scope) {
        "global" -> ""
        "project" -> projectOwner(workspace).takeIf { it.isNotBlank() }
        "session" -> sessionId.trim().takeIf { it.isNotBlank() }
        else -> null
    }

    private fun projectOwner(workspace: String): String = workspace.trim().trimEnd('/')

    /** 新鲜度判定：expiresAt 为 null 或晚于 now 视为新鲜（过期只降权，不删除）。 */
    private fun isFresh(memory: AgentMemoryEntity, now: Long): Boolean {
        val expires = memory.expiresAt
        return expires == null || expires > now
    }

    private fun AgentMemoryEntity.render(lineBreak: Boolean = false): String {
        val suffix = buildString {
            if (pinned) append(" pinned")
            if (expiresAt != null && !isFresh(this@render, System.currentTimeMillis())) append(" Đã hết hạn")
            append(" v${revision}")
        }
        val separator = if (lineBreak) "\n        " else " "
        return if (lineBreak) {
            "- [${scope}/${kind}] ${key}: ${value}${separator}(volatility=$volatility, freshness=$suffix)"
        } else {
            "- [${scope}/${kind}] ${key}: ${value}$separator(volatility=$volatility, freshness=$suffix)"
        }
    }

    private companion object {
        const val MAX_MEMORY_KEY_CHARS = 128
        const val MAX_MEMORY_VALUE_CHARS = 4_096
        const val MAX_MEMORY_QUERY_CHARS = 256
        const val MAX_MEMORIES_PER_OWNER = 100
        const val MAX_MEMORY_LIST = 100
        const val MAX_PINNED_CHARS = 1_500
    }

    suspend fun executePlan(args: JsonObject, sessionId: String): Pair<Boolean, String> {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "get"
        return when (action) {
            "replace_active", "set_active", "create" -> {
                val goal = args["goal"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "plan replace_active bắt buộc cung cấp mô tả mục tiêu goal"
                val stepsElement = args["steps"]
                val stepsJson = stepsElement?.toString() ?: "[]"
                agentContextDao.savePlan(
                    AgentPlanEntity(
                        sessionId = sessionId,
                        goal = goal,
                        stepsJson = stepsJson,
                        status = "active",
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true to "Đã tạo/cập nhật thành công kế hoạch thực thi nhiệm vụ:\nMục tiêu: $goal\nCác bước: $stepsJson\n\nGợi ý: hãy thực thi theo kế hoạch. Khi hoàn thành mỗi bước hãy gọi plan(action=\"advance\", ...) để cập nhật trạng thái bước; khi gặp trở ngại hãy gọi replace_active để điều chỉnh phương án; khi hoàn tất tất cả hãy gọi clear_active."
            }
            "get_active", "get" -> {
                val plan = agentContextDao.getActivePlan(sessionId)
                if (plan == null) {
                    true to "Phiên hiện tại chưa có kế hoạch nhiệm vụ nào đang hoạt động"
                } else {
                    true to "Kế hoạch nhiệm vụ đang hoạt động hiện tại:\nMục tiêu: ${plan.goal}\nCác bước: ${plan.stepsJson}\nTrạng thái: ${plan.status}"
                }
            }
            "advance", "update_steps" -> {
                val plan = agentContextDao.getActivePlan(sessionId)
                    ?: return false to "Phiên hiện tại không có kế hoạch đang hoạt động nào để thúc đẩy, hãy dùng replace_active để tạo trước"
                val stepsElement = args["steps"]
                val newStepsJson = stepsElement?.toString() ?: plan.stepsJson
                // status 是计划生命周期枚举（active/completed/cancelled），不是自然语言进度描述。
                // 模型常把阶段说明塞进 status，直接写入会导致 getActivePlan 永久找不到记录。
                val requestedStatus = args["status"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val newStatus = when (requestedStatus) {
                    "completed", "cancelled", "active" -> requestedStatus
                    else -> plan.status
                }
                agentContextDao.savePlan(
                    plan.copy(
                        stepsJson = newStepsJson,
                        status = newStatus,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true to "[Tiến độ quy hoạch đã cập nhật đồng bộ]\nTrạng thái: $newStatus\nBước hiện tại: $newStepsJson\nHãy tiếp tục đẩy các nhiệm vụ tiếp theo."
            }
            "clear_active", "clear" -> {
                agentContextDao.deletePlanBySession(sessionId)
                true to "Đã xóa quy hoạch nhiệm vụ của hội thoại hiện tại"
            }
            else -> false to "Hành động plan không xác định: $action"
        }
    }

    suspend fun executeScratchpad(args: JsonObject, sessionId: String): Pair<Boolean, String> {
        val action = args["action"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: "list"
        return when (action) {
            "save", "set" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad save bắt buộc cung cấp key"
                val value = args["value"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad save bắt buộc cung cấp value"
                agentContextDao.saveScratchpad(
                    AgentScratchpadEntity(
                        sessionId = sessionId,
                        key = key,
                        value = value,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                true to "Đã ghi bản nháp công việc [$key] = $value"
            }
            "get" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad get bắt buộc cung cấp key"
                val item = agentContextDao.getScratchpad(sessionId, key)
                if (item == null) {
                    true to "Bản nháp [$key] không tồn tại"
                } else {
                    true to "Bản nháp [$key]: ${item.value}"
                }
            }
            "list" -> {
                val list = agentContextDao.listScratchpads(sessionId)
                if (list.isEmpty()) {
                    true to "Hiện không có bản ghi bản nháp công việc"
                } else {
                    val formatted = list.joinToString("\n") { "- [${it.key}]: ${it.value}" }
                    true to "Bản nháp công việc hiện tại:\n$formatted"
                }
            }
            "delete" -> {
                val key = args["key"]?.jsonPrimitive?.contentOrNull?.trim()
                    ?: return false to "scratchpad delete bắt buộc cung cấp key"
                agentContextDao.deleteScratchpad(sessionId, key)
                true to "Đã xóa bản nháp [$key]"
            }
            "clear" -> {
                agentContextDao.clearScratchpads(sessionId)
                true to "Đã xóa toàn bộ bản nháp công việc của nhiệm vụ hiện tại"
            }
            else -> false to "Thao tác scratchpad không xác định: $action"
        }
    }
}
