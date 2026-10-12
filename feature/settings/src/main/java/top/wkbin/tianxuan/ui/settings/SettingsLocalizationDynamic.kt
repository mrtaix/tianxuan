package top.wkbin.tianxuan.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.wkbin.tianxuan.core.model.Community
import top.wkbin.tianxuan.feature.settings.R

/**
 * 动态文案的翻译规则。
 *
 * [SettingsLocalization.legacyStringResource] 是「中文原文 → 资源ID」的静态表，
 * 只能覆盖编译期已知的字面量。像「3 个模型」「群号: xxx」这类**运行时拼出来的
 * 字符串**不在表里，需要在此按结构匹配后注入参数。
 *
 * 群号尤其不能进静态表：它是[Community.QQ_GROUP_ID] 的运行时值，
 * 进表就等于多一份要同步的副本，改群号必然漏改（历史上就是这么漏的）。
 */
@Composable
internal fun resolveDynamicString(source: String): String? {
    // NOTE: raw 字符串不做转义处理，此处必须写 \d；写成 \\d 会匹配字面 "\d" 导致永不命中。
    matchOne(source, """(\d+) bộ hệ thống · (.+)""") {
        return stringResource(R.string.settings_dynamic_system_mode, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """(\d+) bộ hệ thống""") {
        return stringResource(R.string.settings_dynamic_system_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) model · (\d+) kỹ năng""") {
        return stringResource(R.string.settings_dynamic_model_skill_count, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """(\d+) model""") {
        return stringResource(R.string.settings_dynamic_model_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) kỹ năng""") {
        return stringResource(R.string.settings_dynamic_skill_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) cái""") {
        return stringResource(R.string.settings_dynamic_item_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) lượt""") {
        return stringResource(R.string.settings_dynamic_round_count, it.groupValues[1])
    }
    matchOne(source, """Đang tải：(.+) / (.+) MB""") {
        return stringResource(R.string.settings_dynamic_download_progress, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """Đã tải：(.+) MB""") {
        return stringResource(R.string.settings_dynamic_downloaded, it.groupValues[1])
    }
    if (source.startsWith("发现新版本 v")) {
        return stringResource(R.string.settings_dynamic_new_version, source.removePrefix("发现新版本 v"))
    }
    if (source.startsWith("确定删除 ") && source.endsWith("？")) {
        return stringResource(
            R.string.settings_dynamic_delete_confirm,
            source.removePrefix("确定删除 ").removeSuffix("？"),
        )
    }
    matchOne(source, """Dò thành công (\d+) công cụ""") {
        return stringResource(R.string.settings_dynamic_tool_count, it.groupValues[1])
    }
    matchOne(
        source,
        """Khi hết lượt sẽ cho model thu lại và ghi tiến độ trước, rồi tự động chạy tiếp, không cần người dùng nhấp tiếp tục; giới hạn tổng ngân sách (\d+) lượt""",
    ) {
        return stringResource(R.string.settings_text_0340, it.groupValues[1])
    }
    matchOne(source, """(\d+) lần""") {
        return stringResource(R.string.settings_text_0341, it.groupValues[1])
    }
    // 群号匹配到结构后，一律注入 Community 的当前值而非正则捕获组：
    // 调用方传的可能是过期文案，用真值才不会出现两个群号并存
    matchOne(source, """Số nhóm: (\d+) · .+""") {
        return stringResource(R.string.settings_dynamic_qq_group, Community.QQ_GROUP_ID)
    }
    matchOne(source, """Tham gia nhóm giao lưu QQ \((\d+)\)""") {
        return stringResource(R.string.settings_dynamic_qq_join, Community.QQ_GROUP_ID)
    }
    return null
}

/** 整串匹配才回调，否则 null。避免前缀命中导致误翻译。 */
private inline fun matchOne(source: String, pattern: String, block: (MatchResult) -> String): String? =
    Regex(pattern).matchEntire(source)?.let(block)