package top.wkbin.tianxuan.harness

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ask_user 工具的参数模型（对齐 opencode question 工具）：
 * 模型一次可提 1~4 个问题，每题可带候选项（label + 说明）并允许自定义输入。
 * ToolExecutor 校验入参与 UI 渲染问题卡共用同一解析，避免两处口径漂移。
 */
@Serializable
data class AskUserQuestion(
    val question: String,
    val header: String? = null,
    val options: List<AskUserOption> = emptyList(),
    /** 有候选项时默认允许自定义输入；纯自由文本问题恒为 true。 */
    val allowCustom: Boolean = true,
)

@Serializable
data class AskUserOption(
    val label: String,
    val description: String? = null,
)

object AskUserQuestions {
    const val MAX_QUESTIONS = 4
    const val MAX_OPTIONS = 6
    const val TOOL_NAME = "ask_user"

    /** 解析并校验 ask_user 入参；不合法时抛 [IllegalArgumentException]（文案可直接回写模型）。 */
    fun parse(args: JsonObject): List<AskUserQuestion> {
        val rawQuestions = args["questions"] as? JsonArray
            ?: throw IllegalArgumentException("questions phải là mảng đối tượng câu hỏi")
        if (rawQuestions.isEmpty() || rawQuestions.size > MAX_QUESTIONS) {
            throw IllegalArgumentException("questions độ dài mảng phải trong khoảng 1~$MAX_QUESTIONS")
        }
        return rawQuestions.mapIndexed { index, element ->
            val item = element as? JsonObject
                ?: throw IllegalArgumentException("questions[$index] phải là đối tượng JSON")
            val question = item["question"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (question.isEmpty()) {
                throw IllegalArgumentException("questions[$index].question không được rỗng")
            }
            val options = (item["options"] as? JsonArray)?.mapIndexed { optionIndex, optionElement ->
                val option = optionElement as? JsonObject
                    ?: throw IllegalArgumentException("questions[$index].options[$optionIndex] phải là đối tượng JSON")
                val label = option["label"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (label.isEmpty()) {
                    throw IllegalArgumentException("questions[$index].options[$optionIndex].label không được rỗng")
                }
                AskUserOption(
                    label = label,
                    description = option["description"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null },
                )
            }.orEmpty()
            if (options.size > MAX_OPTIONS) {
                throw IllegalArgumentException("questions[$index].options tối đa $MAX_OPTIONS mục")
            }
            val duplicate = options.groupBy { it.label }.filterValues { it.size > 1 }.keys.firstOrNull()
            if (duplicate != null) {
                throw IllegalArgumentException("questions[$index] tồn tại lựa chọn trùng lặp 「$duplicate」, vui lòng dùng các ứng viên loại trừ lẫn nhau")
            }
            val allowCustom = item["allow_custom"]?.jsonPrimitive?.booleanOrNull ?: options.isNotEmpty()
            AskUserQuestion(
                question = question,
                header = item["header"]?.jsonPrimitive?.contentOrNull?.trim()?.ifEmpty { null },
                options = options,
                allowCustom = allowCustom || options.isEmpty(),
            )
        }
    }

    /** 把 UI 提交的答案（`[{"index":0,"answer":"..."}]`）格式化为回写给模型的工具结果。 */
    fun formatAnswers(questionsJson: String, answersJson: String): String {
        val questions = runCatching {
            parse(kotlinx.serialization.json.Json.parseToJsonElement(questionsJson).jsonObject)
        }.getOrNull().orEmpty()
        val answers = runCatching {
            (kotlinx.serialization.json.Json.parseToJsonElement(answersJson) as? JsonArray)
                ?.mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val index = item["index"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return@mapNotNull null
                    val answer = item["answer"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                    if (answer.isEmpty()) null else index to answer
                }
        }.getOrNull().orEmpty()
        if (questions.isEmpty() || answers.isEmpty()) {
            return "Người dùng đã trả lời câu hỏi ask_user: $answersJson"
        }
        return buildString {
            append("Người dùng đã trả lời câu hỏi ask_user:\n")
            answers.forEach { (index, answer) ->
                val question = questions.getOrNull(index)?.question ?: "Câu hỏi ${index + 1}"
                append("${index + 1}. $question\n   → $answer\n")
            }
        }.trimEnd()
    }
}
