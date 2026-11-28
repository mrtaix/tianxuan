package top.wkbin.tianxuan.harness.btw

import top.wkbin.tianxuan.core.database.AiModelRepository
import top.wkbin.tianxuan.harness.ApiMessage
import top.wkbin.tianxuan.harness.AssistantText
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.ProviderClient
import top.wkbin.tianxuan.harness.UserMessage
import top.wkbin.tianxuan.harness.session.SessionTreeStore

/**
 * Câu hỏi phụ "/btw" theo đúng ngữ nghĩa của Claude Code:
 * - trả lời độc lập, không chen vào lượt đang chạy và không làm gián đoạn agent;
 * - không ghi vào lịch sử hội thoại (chỉ đọc ảnh chụp ngữ cảnh hiện có);
 * - không cấp quyền gọi công cụ, chỉ trả lời từ ngữ cảnh đã có.
 *
 * Lớp này chỉ đọc nhánh tin nhắn rồi gọi provider một lần: không ghi DB,
 * không đụng OperationCoordinator và không chạm PromptQueue.
 */
class BtwQuestionService(
    private val providerClient: ProviderClient,
    private val messageStore: SessionTreeStore,
    private val modelRepository: AiModelRepository,
) {
    companion object {
        const val COMMAND = "/btw"

        /** Giới hạn số tin nhắn đưa vào ngữ cảnh để câu hỏi phụ luôn nhẹ và nhanh. */
        private const val MAX_CONTEXT_MESSAGES = 40
    }

    /** Bóc câu hỏi khỏi chuỗi người dùng nhập; null nếu không phải lệnh /btw. */
    fun parseQuestion(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (!trimmed.startsWith(COMMAND)) return null
        return trimmed.removePrefix(COMMAND).trim().ifBlank { null }
    }

    /**
     * Trả lời câu hỏi phụ, không ghi gì xuống DB.
     * @return nội dung trả lời đã trim.
     */
    suspend fun ask(sessionId: String, question: String, modelId: String? = null): Result<String> = runCatching {
        val active = modelRepository.activeModel() ?: error("Chưa cấu hình mô hình đang dùng")
        val history = runCatching { messageStore.load(sessionId) }.getOrDefault(emptyList())
        val resolved = providerClient.resolveConfigured(modelId ?: active.id)
        val result = providerClient.chat(
            resolved,
            listOf(ApiMessage(role = "user", content = buildPrompt(buildContext(history), question))),
        )
        result.content.orEmpty().trim().ifBlank { error("Mô hình không trả về nội dung") }
    }

    /** Chỉ lấy văn bản người dùng/trợ lý, bỏ tool call để câu trả lời không bị lệch. */
    private fun buildContext(history: List<HarnessMessage>): String =
        history.asSequence()
            .mapNotNull { message ->
                when (message) {
                    is UserMessage -> "người dùng: ${message.text}"
                    is AssistantText -> "trợ lý: ${message.text}"
                    else -> null
                }
            }
            .filter { it.substringAfter(": ").isNotBlank() }
            .toList()
            .takeLast(MAX_CONTEXT_MESSAGES)
            .joinToString("\n\n")

    private fun buildPrompt(context: String, question: String): String = buildString {
        appendLine("Bạn đang trả lời một câu hỏi phụ (/btw) của người dùng về cuộc hội thoại dưới đây.")
        appendLine("Quy tắc: chỉ dùng thông tin trong ngữ cảnh; không gọi công cụ; trả lời ngắn gọn, trực tiếp.")
        appendLine()
        appendLine("<ngữ_cảnh_hội_thoại>")
        appendLine(context.ifBlank { "(chưa có nội dung)" })
        appendLine("</ngữ_cảnh_hội_thoại>")
        appendLine()
        append("Câu hỏi phụ: ")
        append(question)
    }
}
