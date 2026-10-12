package top.wkbin.tianxuan.harness.validation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

/**
 * 智能体工具调用死循环与重复调用检测器。
 *
 * 专门防御以下异常行为：
 * 1. 同一工具使用完全相同的参数连续失败多次（盲目重试而不纠错）；
 * 2. 相同工具调用序列在多轮对话中反复空转（无论成功与否，没有取得实质性进展）；
 * 3. 参数校验连续失败却不按 Schema 修正。
 */
class ToolCallLoopDetector(
    private val maxSameCallFailures: Int = 2,
    private val maxIdenticalCallsStreak: Int = 3,
) {
    private val callHistory = mutableListOf<CallRecord>()

    data class CallRecord(
        val toolName: String,
        val argsJson: String,
        var success: Boolean? = null,
        /** 相同 (tool, args) 的上一次调用输出是否发生了变化：变了 = 有进展，不计入空转。 */
        var outputChanged: Boolean = false,
        val timestamp: Long = System.currentTimeMillis(),
    )

    /** (toolName:argsJson) → 上一次输出的指纹，用于识别"Tham số giống nhau nhưng kết quả thay đổi"的合法轮询。 */
    private val lastOutputFingerprints = mutableMapOf<String, Int>()

    sealed interface LoopVerdict {
        data object Pass : LoopVerdict
        data class Warn(val message: String) : LoopVerdict
        data class Block(val reason: String, val guidance: String) : LoopVerdict
    }

    /**
     * 在工具执行前进行循环检测。
     *
     * @param toolName 工具 API 名称
     * @param args 工具入参
     * @return 检测裁决结果
     */
    @Synchronized
    fun evaluate(toolName: String, args: JsonObject): LoopVerdict {
        val currentArgsJson = canonical(args).toString()
        // 窗口须显著大于单轮工具上限（默认 12）：跨轮震荡的模式可能被同轮后续调用推出
        // 小窗口而逃过检测；上限 50 条历史内取 30 是覆盖与噪声的平衡。
        val recentCalls = callHistory.takeLast(30)

        // 1. 检测连续相同调用的失败历史
        val sameFailedStreak = recentCalls.reversed().takeWhile { record ->
            record.toolName.equals(toolName, ignoreCase = true) &&
                record.argsJson == currentArgsJson &&
                record.success == false
        }.count()

        if (sameFailedStreak >= maxSameCallFailures) {
            return LoopVerdict.Block(
                reason = "Phát hiện vòng lặp chết: công cụ `$toolName` đã thực thi thất bại $sameFailedStreak lần liên tiếp với tham số hoàn toàn giống nhau",
                guidance = buildString {
                    append("【Đã chặn bắt buộc lời gọi lặp này và yêu cầu suy ngẫm】\n")
                    append("Bạn đang liên tục phát đi thao tác thất bại hoàn toàn giống hệt: `$toolName($currentArgsJson)`.\n")
                    append("Đây là hành vi vòng lặp chết điển hình, đã chặn thực thi trực tiếp.\n\n")
                    append("Hãy thực thi ngay phản tư và thay đổi chiến lược sau:\n")
                    append("1. **Định vị căn nguyên**: đọc kỹ đầu ra lỗi nêu trên, phân tích nguyên nhân cụ thể của vài lần thất bại trước (sai đường dẫn? nội dung không khớp? thiếu tham số? thiếu phụ thuộc?);\n")
                    append("2. **Nghiêm cấm thử lại**: nghiêm cấm phát lại lời gọi hoàn toàn giống hệt;\n")
                    append("3. **Thay đổi chiến lược**:\n")
                    append("   - Nếu là `edit` khớp thất bại: phải gọi `read` trước để xem nội dung chính xác và số dòng mới nhất của tệp;\n")
                    append("   - Nếu là `base` thất bại: gọi `read`/`base` để kiểm tra môi trường, đường dẫn hoặc xem log, hoặc đổi cách triển khai;\n")
                    append("   - Nếu thiếu công cụ hoặc năng lực: nói rõ với người dùng trở ngại gặp phải hiện tại và các bước đã hoàn thành.")
                },
            )
        }

        // 2. 检测完全相同的调用连续出现（即使成功也可能是无进展空转）。
        // 输出有变化的不计入：`process logs`/`read` 轮询增长中的日志时参数完全相同，
        // 但每次输出都在变，这正是被提示词鼓励的合法用法，按字面判空转会误杀。
        val identicalStreak = recentCalls.reversed().takeWhile { record ->
            record.toolName.equals(toolName, ignoreCase = true) &&
                record.argsJson == currentArgsJson &&
                !record.outputChanged
        }.count()

        if (identicalStreak >= maxIdenticalCallsStreak) {
            return LoopVerdict.Block(
                reason = "Phát hiện lặp vòng rỗng: công cụ `$toolName` đã thực thi liên tiếp $identicalStreak lần, tham số và đầu ra đều không thay đổi",
                guidance = buildString {
                    append("【Đã cưỡng chế chặn lời gọi lặp không tiến triển này】\n")
                    append("Bạn đã thực hiện thao tác hoàn toàn giống nhau `$toolName` liên tục $identicalStreak lần, hệ thống phát hiện nhiệm vụ rơi vào bế tắc chạy không.\n")
                    append("Vui lòng thoát khỏi vòng lặp hiện tại, tổng kết thông tin đã lấy, đẩy sang giai đoạn tiếp theo hoặc báo cáo cho người dùng.")
                },
            )
        }

        // 3. 检测多工具交替震荡死循环（例如 A -> B -> A -> B 或 A -> B -> C -> A -> B -> C）
        val simulatedHistory = recentCalls.map { "${it.toolName.lowercase()}:${it.argsJson}" } + "${toolName.lowercase()}:$currentArgsJson"
        for (period in 2..3) {
            if (simulatedHistory.size >= period * 3) {
                val cycle1 = simulatedHistory.takeLast(period)
                val cycle2 = simulatedHistory.dropLast(period).takeLast(period)
                val cycle3 = simulatedHistory.dropLast(period * 2).takeLast(period)
                if (cycle1 == cycle2 && cycle2 == cycle3) {
                    return LoopVerdict.Block(
                        reason = "Phát hiện vòng lặp dao động luân phiên: chuỗi lời gọi công cụ lặp luân phiên theo chu kỳ $period tới 3 lần",
                        guidance = buildString {
                            append("【Đã cưỡng chế chặn vòng lặp chết dao động xen kẽ】\n")
                            append("Bạn đang lặp đi lặp lại cùng một chuỗi thao tác xen kẽ (chu kỳ $period).\n")
                            append("Hệ thống phát hiện nhiệm vụ rơi vào vòng lặp chết dao động qua lại, đã chặn thực thi trực tiếp.\n\n")
                            append("Hãy thực thi ngay phản tư sau:\n")
                            append("1. Vì sao kết quả bước trước không thể thúc đẩy nhiệm vụ?\n")
                            append("2. Lập tức dừng chế độ vòng lặp hiện tại, chuyển sang hướng giải quyết mới hoặc báo cáo trở ngại hiện tại cho người dùng.")
                        },
                    )
                }
            }
        }

        return LoopVerdict.Pass
    }

    /**
     * 登记单次工具调用的开始。
     */
    @Synchronized
    fun recordIntent(toolName: String, args: JsonObject) {
        callHistory.add(CallRecord(toolName = toolName, argsJson = canonical(args).toString()))
        // 限制历史记录上限
        if (callHistory.size > 50) {
            callHistory.removeAt(0)
        }
    }

    /**
     * 记录单次工具调用的结算结果。
     *
     * @param output 工具输出正文（可空）。相同 (tool, args) 之间输出发生变化时标记
     *  [CallRecord.outputChanged]，使结果感知的空转检测不再误杀合法轮询。
     */
    @Synchronized
    fun recordSettled(toolName: String, args: JsonObject, success: Boolean, output: String? = null) {
        val currentArgsJson = canonical(args).toString()
        val lastRecord = callHistory.lastOrNull {
            it.toolName.equals(toolName, ignoreCase = true) && it.argsJson == currentArgsJson && it.success == null
        }
        if (lastRecord != null) {
            lastRecord.success = success
            lastRecord.outputChanged = markOutputProgress(toolName, currentArgsJson, output)
        } else {
            callHistory.add(
                CallRecord(
                    toolName = toolName,
                    argsJson = currentArgsJson,
                    success = success,
                    outputChanged = markOutputProgress(toolName, currentArgsJson, output),
                ),
            )
            if (callHistory.size > 50) callHistory.removeAt(0)
        }
    }

    /** 相同 (tool, args) 的输出与上一次相比是否发生变化；同时滚动更新指纹。 */
    private fun markOutputProgress(toolName: String, argsJson: String, output: String?): Boolean {
        if (output == null) return false
        val key = "${toolName.lowercase()}:$argsJson"
        val fingerprint = output.hashCode()
        val previous = lastOutputFingerprints.put(key, fingerprint)
        return previous != null && previous != fingerprint
    }

    /**
     * 清空当前检测器的历史（在用户发送新消息或会话重置时调用）。
     */
    @Synchronized
    fun reset() {
        callHistory.clear()
        lastOutputFingerprints.clear()
    }

    private fun canonical(value: JsonElement): JsonElement = when (value) {
        is JsonObject -> JsonObject(value.toSortedMap().mapValues { canonical(it.value) })
        is JsonArray -> JsonArray(value.map(::canonical))
        else -> value
    }
}
