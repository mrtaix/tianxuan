package top.wkbin.tianxuan.harness.subagent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import top.wkbin.tianxuan.harness.HarnessMessage
import top.wkbin.tianxuan.harness.HarnessTool
import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.ToolResult
import top.wkbin.tianxuan.harness.normalizeWritePath

/**
 * 子智能体完成 claim 的 host 裁定。
 *
 * 模型提交的 status 是**主张**，不是事实。交给父智能体之前，host 用自己落库的
 * lane transcript receipts 逐条核验 acceptance_criteria：
 * - `verification` 必须对应 host 真实记录过的成功命令执行；
 * - `files` 必须对应 host 观察到的成功结构化写入（write/edit/download）；
 * - `manual` 永远不能自证（只有用户能验证）。
 * 凭据不背书的条目降级为 unsatisfied；持有 unsatisfied 条目的 complete 主张整体降级为 partial。
 * **host 永远不升格状态**：partial/failed 只原样保留。
 *
 * 协议是文本协议（与 PlannerProtocolParser 同风格）：结论文末的 ```json 块，解析失败
 * 或缺失 = 无 claim，行为与旧版完全一致（fail-open）；解析成功才启用裁定。
 */

@Serializable
internal data class SubagentClaimCriterionDto(
    val type: String = "",
    val claim: String = "",
    val command: String? = null,
    val paths: List<String> = emptyList(),
    @SerialName("acceptance") val acceptance: String? = null,
)

@Serializable
internal data class SubagentClaimDto(
    val status: String = "",
    val summary: String = "",
    @SerialName("acceptance_criteria")
    val acceptanceCriteria: List<SubagentClaimCriterionDto> = emptyList(),
)

/** 解析并规范化后的完成主张。 */
internal data class SubagentClaim(
    val status: String,
    /** claim 块内模型自述的一句话摘要；正文剔除协议块后为空时的回退。 */
    val summaryText: String,
    val criteria: List<SubagentClaimCriterion>,
    /** claim JSON 块在原文中的区间，渲染父汇总时剔除。 */
    val blockRange: IntRange,
)

internal data class SubagentClaimCriterion(
    /** Unknown types are retained so they become an explicit unendorsed criterion. */
    val type: String,
    val claim: String,
    val command: String,
    val paths: List<String>,
)

/** host 从 lane transcript 提取的执行凭据。 */
internal data class SubagentHostReceipts(
    /** 成功执行的 base/process 命令（归一化后）。 */
    val commands: List<String>,
    /** 成功落盘的结构化写目标（归一化后）。 */
    val writtenPaths: List<String>,
)

internal data class SubagentCriterionVerdict(
    val criterion: SubagentClaimCriterion,
    val backed: Boolean,
    val reason: String,
)

internal data class SubagentClaimAdjudication(
    val claimedStatus: String,
    val adjudicatedStatus: String,
    val verdicts: List<SubagentCriterionVerdict>,
    val receipts: SubagentHostReceipts,
) {
    val downgraded: Boolean get() = adjudicatedStatus != claimedStatus
}

private val claimJson = Json { ignoreUnknownKeys = true; isLenient = true }

private val CLAIM_BLOCK = Regex("```json\\s*\\n([\\s\\S]*?)```", RegexOption.IGNORE_CASE)

/** 可接受的 status 词表：complete 的近似拼写归一化为 complete。 */
private val STATUS_ALIASES = mapOf(
    "complete" to "complete",
    "completed" to "complete",
    "done" to "complete",
    "partial" to "partial",
    "failed" to "failed",
)

/**
 * 从结论文本解析完成 claim；缺失/非法返回 null（调用方保持旧行为）。
 * 取**最后一个**合法块——多轮修正后以最终提交为准。
 */
internal fun parseSubagentClaim(finalText: String): SubagentClaim? {
    val matches = CLAIM_BLOCK.findAll(finalText).toList()
    for (match in matches.asReversed()) {
        val dto = runCatching { claimJson.decodeFromString<SubagentClaimDto>(match.groupValues[1]) }.getOrNull()
            ?: continue
        if (dto.status.isBlank()) continue
        val status = STATUS_ALIASES[dto.status.trim().lowercase()] ?: continue
        val criteria = dto.acceptanceCriteria.map { criterion ->
            // Keep unknown criterion types: silently dropping them would turn an
            // unendorsed claim into an apparently backed complete result.
            val type = criterion.type.trim().lowercase().ifBlank { "unknown" }
            SubagentClaimCriterion(
                type = type,
                claim = (criterion.claim.ifBlank { criterion.acceptance.orEmpty() }).trim(),
                command = criterion.command?.trim().orEmpty(),
                paths = criterion.paths.map { it.trim() }.filter { it.isNotBlank() },
            )
        }
        return SubagentClaim(
            status = status,
            summaryText = dto.summary.trim(),
            criteria = criteria,
            blockRange = match.range,
        )
    }
    return null
}

/** 从 lane transcript 提取 host 执行凭据：只统计**成功**的调用。 */
internal fun extractSubagentReceipts(transcript: List<HarnessMessage>): SubagentHostReceipts {
    val calls = transcript.filterIsInstance<ToolCall>().associateBy { it.id }
    val successByCallId = transcript.filterIsInstance<ToolResult>()
        .filter { it.success }
        .mapTo(mutableSetOf()) { it.toolCallId }
    val commands = mutableListOf<String>()
    val writtenPaths = mutableListOf<String>()
    for ((id, call) in calls) {
        if (call.id !in successByCallId) continue
        val command = call.args.stringArg("command")
        if (call.tool in setOf(HarnessTool.BASE, HarnessTool.PROCESS) && !command.isNullOrBlank()) {
            commands += normalizeCommand(command)
        }
        val path = call.args.stringArg("path") ?: call.args.stringArg("destination")
        if (call.tool in LANE_WRITE_TOOLS && !path.isNullOrBlank()) {
            writtenPaths += normalizeWritePath(path)
        }
    }
    return SubagentHostReceipts(
        commands = commands.distinct(),
        writtenPaths = writtenPaths.distinct(),
    )
}

/**
 * 核验 claim。规则：verification 凭成功命令、files 凭成功结构化写入、manual 永不背书；
 * 任一 unsatisfied 的 complete 主张降级为 partial；host 永不升格。
 */
internal fun adjudicateSubagentClaim(
    claim: SubagentClaim,
    receipts: SubagentHostReceipts,
): SubagentClaimAdjudication {
    val verdicts = claim.criteria.map { criterion ->
        when (criterion.type) {
            "verification" -> {
                val claimed = normalizeCommand(criterion.command)
                when {
                    claimed.isBlank() -> SubagentCriterionVerdict(
                        criterion, false, "Chưa cung cấp văn bản lệnh có thể kiểm chứng, host không thể bảo chứng",
                    )
                    // 精确相等任意长度可背书；子串匹配要求足够长（"test" 这类短串会误匹配 echo test）。
                    receipts.commands.any { it == claimed || (claimed.length >= 8 && it.contains(claimed)) } ->
                        SubagentCriterionVerdict(criterion, true, "host ghi nhận việc thực thi thành công của lệnh khớp")
                    else -> SubagentCriterionVerdict(
                        criterion, false, "host chưa ghi nhận lệnh này thực thi thành công (lệnh thành công ${receipts.commands.size} lệnh)",
                    )
                }
            }
            "files" -> {
                if (criterion.paths.isEmpty()) {
                    SubagentCriterionVerdict(criterion, false, "Chưa cung cấp đường dẫn có thể kiểm chứng")
                } else {
                    val unbacked = criterion.paths.filter { path ->
                        normalizeWritePath(path) !in receipts.writtenPaths
                    }
                    if (unbacked.isEmpty()) {
                        SubagentCriterionVerdict(criterion, true, "host đã quan sát toàn bộ đường dẫn ghi thành công")
                    } else {
                        SubagentCriterionVerdict(
                            criterion, false,
                            "host chưa quan sát thấy việc ghi thành công các đường dẫn sau: ${unbacked.joinToString("、")}" +
                                if (receipts.writtenPaths.isEmpty()) "(Lane này không có bất kỳ bản ghi ghi thành công nào)" else "",
                        )
                    }
                }
            }
            "manual" -> SubagentCriterionVerdict(
                criterion, false, "Mục manual không thể được bảo chứng bởi thông tin xác thực host, chỉ có thể do người dùng xác minh",
            )
            else -> SubagentCriterionVerdict(
                criterion, false, "Loại tiêu chuẩn nghiệm thu không xác định \"${criterion.type}\", host không bảo chứng",
            )
        }
    }
    // A complete claim with no criteria is not a host-backed completion: there is
    // nothing the host can independently endorse. Unknown criteria are retained
    // above and arrive here as explicitly unbacked rather than disappearing.
    val criteriaUnbacked = claim.criteria.isEmpty() || verdicts.any { !it.backed }
    val adjudicated = when {
        claim.status == "complete" && criteriaUnbacked -> "partial"
        else -> claim.status
    }
    return SubagentClaimAdjudication(
        claimedStatus = claim.status,
        adjudicatedStatus = adjudicated,
        verdicts = verdicts,
        receipts = receipts,
    )
}

/** 剔除 claim JSON 块后的结论文本（父汇总与落盘文件展示用，原始 JSON 不进父上下文）。 */
internal fun stripSubagentClaimBlock(finalText: String, claim: SubagentClaim): String =
    finalText.removeRange(claim.blockRange).trim()

/** 父汇总中的裁定段：状态、逐条核验、host 凭据摘要。 */
internal fun renderSubagentClaimAdjudication(adjudication: SubagentClaimAdjudication): String = buildString {
    append("- **Kiểm chứng tuyên bố hoàn thành**: tự báo ${statusLabel(adjudication.claimedStatus)} → host phán quyết ")
    append("**${statusLabel(adjudication.adjudicatedStatus)}**")
    if (adjudication.downgraded) append("(Thông tin xác thực không bảo chứng, đã hạ cấp)")
    append("\n")
    adjudication.verdicts.forEach { verdict ->
        val icon = if (verdict.backed) "✓" else "✗"
        append("  - $icon [${verdict.criterion.type}] ${verdict.criterion.claim.ifBlank { "（未描述）" }}")
        if (!verdict.backed) append(" — ${verdict.reason}")
        append("\n")
    }
}

internal fun statusLabel(status: String): String = when (status) {
    "complete" -> "complete"
    "partial" -> "partial"
    "failed" -> "failed"
    else -> status
}

private fun normalizeCommand(command: String): String =
    command.replace(Regex("\\s+"), " ").trim().removePrefix("./").trim()

private fun kotlinx.serialization.json.JsonObject.stringArg(key: String): String? =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
