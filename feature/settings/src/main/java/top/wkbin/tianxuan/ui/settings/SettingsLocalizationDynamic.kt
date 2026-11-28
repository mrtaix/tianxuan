package top.wkbin.tianxuan.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import top.wkbin.tianxuan.core.model.Community
import top.wkbin.tianxuan.feature.settings.R

/**
 * Quy tắc dịch nội dung động.
 *
 * [SettingsLocalization.legacyStringResource] là bảng tĩnh ánh xạ văn bản gốc tiếng Trung
 * sang ID tài nguyên, chỉ bao phủ các chuỗi literal đã biết khi biên dịch. Các chuỗi
 * ghép lúc chạy, như số mô hình hoặc mã nhóm, cần được khớp theo cấu trúc rồi truyền tham số tại đây.
 * Giữ nguyên literal tiếng Trung trong các mẫu và điều kiện để tương thích với nội dung gốc.
 *
 * Không đưa mã nhóm vào bảng tĩnh: giá trị được lấy lúc chạy từ [Community.QQ_GROUP_ID].
 * Thêm vào bảng sẽ tạo một bản sao cần đồng bộ và dễ bị bỏ sót khi đổi mã nhóm, như đã từng xảy ra.
 */
@Composable
internal fun resolveDynamicString(source: String): String? {
    // Chuỗi raw không xử lý escape: phải dùng \d; dùng \\d sẽ khớp literal "\d" nên không khớp số.
    matchOne(source, """(\d+) 套系统 · (.+)""") {
        return stringResource(R.string.settings_dynamic_system_mode, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """(\d+) 套系统""") {
        return stringResource(R.string.settings_dynamic_system_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 个模型 · (\d+) 技能""") {
        return stringResource(R.string.settings_dynamic_model_skill_count, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """(\d+) 个模型""") {
        return stringResource(R.string.settings_dynamic_model_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 个技能""") {
        return stringResource(R.string.settings_dynamic_skill_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 个""") {
        return stringResource(R.string.settings_dynamic_item_count, it.groupValues[1])
    }
    matchOne(source, """(\d+) 轮""") {
        return stringResource(R.string.settings_dynamic_round_count, it.groupValues[1])
    }
    matchOne(source, """下载中：(.+) / (.+) MB""") {
        return stringResource(R.string.settings_dynamic_download_progress, it.groupValues[1], it.groupValues[2])
    }
    matchOne(source, """已下载：(.+) MB""") {
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
    matchOne(source, """成功探测到 (\d+) 个工具""") {
        return stringResource(R.string.settings_dynamic_tool_count, it.groupValues[1])
    }
    matchOne(
        source,
        """轮次用尽时先让模型收束并记录进度，再自动续跑，无需用户点击继续；总预算上限 (\d+) 轮""",
    ) {
        return stringResource(R.string.settings_text_0340, it.groupValues[1])
    }
    matchOne(source, """(\d+) 次""") {
        return stringResource(R.string.settings_text_0341, it.groupValues[1])
    }
    // Sau khi khớp cấu trúc mã nhóm, luôn truyền giá trị hiện tại từ Community thay vì nhóm bắt của regex:
    // Bên gọi có thể truyền nội dung cũ; dùng giá trị chuẩn để tránh hiển thị hai mã nhóm khác nhau.
    matchOne(source, """群号: (\d+) · .+""") {
        return stringResource(R.string.settings_dynamic_qq_group, Community.QQ_GROUP_ID)
    }
    matchOne(source, """加入 QQ 交流群 \((\d+)\)""") {
        return stringResource(R.string.settings_dynamic_qq_join, Community.QQ_GROUP_ID)
    }
    return null
}

/** Chỉ gọi callback khi khớp toàn bộ chuỗi, nếu không trả null; tránh dịch nhầm do chỉ khớp tiền tố. */
private inline fun matchOne(source: String, pattern: String, block: (MatchResult) -> String): String? =
    Regex(pattern).matchEntire(source)?.let(block)