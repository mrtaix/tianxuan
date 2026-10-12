package top.wkbin.tianxuan.harness.projection

import top.wkbin.tianxuan.harness.ToolCall
import top.wkbin.tianxuan.harness.HarnessTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** 会话抽屉 / 状态点所展示的"Đang thực thi gì"动作描述。 */
object ToolStatusDescriber {
    const val MAX_STATUS_ARG_LENGTH = 60

    private fun arg(args: JsonObject, name: String): String? =
        runCatching { args[name]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

    fun describe(tool: HarnessTool, args: JsonObject, rawToolName: String? = null): String = when (tool) {
        HarnessTool.BASE -> {
            val command = arg(args, "command")?.lineSequence()?.first()?.trim().orEmpty()
            if (command.isEmpty()) "执行命令" else "Thực thi lệnh: ${command.take(MAX_STATUS_ARG_LENGTH)}"
        }
        HarnessTool.PROCESS -> "Quản lý tiến trình nền: ${arg(args, "action") ?: "process"}${arg(args, "id")?.let { " · ${it.take(MAX_STATUS_ARG_LENGTH)}" }.orEmpty()}"
        HarnessTool.HOST -> {
            val action = arg(args, "action") ?: "host"
            when (action) {
                "screen_observe" -> "Đang cảm nhận điều khiển màn hình và ứng dụng foreground…"
                "screen_click" -> "Đang mô phỏng nhấp tọa độ màn hình (${arg(args, "x")}, ${arg(args, "y")})…"
                "screen_double_click" -> "Đang nhấp đúp tọa độ màn hình (${arg(args, "x")}, ${arg(args, "y")})…"
                "screen_long_press" -> "Đang nhấn giữ tọa độ màn hình (${arg(args, "x")}, ${arg(args, "y")})…"
                "screen_swipe" -> "Đang vuốt màn hình (${arg(args, "x1")}, ${arg(args, "y1")}) ➔ (${arg(args, "x2")}, ${arg(args, "y2")})…"
                "screen_scroll" -> "Đang cuộn màn hình (${arg(args, "direction")}）…"
                "paste_text" -> "Đang dán văn bản vào control đang focus…"
                "screen_input_text" -> "Đang gõ vào ô nhập hiện tại: ${arg(args, "text")?.take(20)}…"
                "screen_key" -> "Đang kích hoạt phím hệ thống: ${arg(args, "key")}…"
                "app_launch" -> "Đang gọi ứng dụng chủ：${arg(args, "package")}…"
                "screen_capture" -> "Đang chụp màn hình…"
                else -> "Đang sử dụng quyền host: $action${arg(args, "command")?.let { " · ${it.take(MAX_STATUS_ARG_LENGTH)}" }.orEmpty()}"
            }
        }
        HarnessTool.DOWNLOAD -> arg(args, "destination")?.let { "Tải tệp: ${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "Tải tệp"
        HarnessTool.READ -> arg(args, "path")?.let { "Đọc tệp: ${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "Đọc tệp"
        HarnessTool.WRITE -> arg(args, "path")?.let { "Ghi tệp: ${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "Ghi tệp"
        HarnessTool.EDIT -> arg(args, "path")?.let { "Chỉnh sửa tệp: ${it.takeLast(MAX_STATUS_ARG_LENGTH)}" } ?: "Sửa tệp"
        HarnessTool.MEMORY -> "Đang truy xuất ký ức dài hạn: ${arg(args, "key") ?: arg(args, "action") ?: "memory"}"
        HarnessTool.PLAN -> "Đang cập nhật quy hoạch thực thi nhiệm vụ: ${arg(args, "goal") ?: arg(args, "action") ?: "plan"}"
        HarnessTool.SCRATCHPAD -> "Đang ghi ghi chú bản nháp công việc: ${arg(args, "key") ?: arg(args, "action") ?: "scratchpad"}"
        HarnessTool.HISTORY_SEARCH -> "Đang truy xuất tin nhắn lịch sử: ${arg(args, "query")?.take(MAX_STATUS_ARG_LENGTH).orEmpty()}"
        HarnessTool.HISTORY_READ -> "Đang đọc tin nhắn lịch sử: ${arg(args, "message_id") ?: arg(args, "index") ?: "history"}"
        HarnessTool.BUILD_SCRIPT -> "Đang quản lý script build: ${arg(args, "action") ?: "build_script"}${arg(args, "name")?.let { " · $it" }.orEmpty()}"
        HarnessTool.SUBAGENT -> "Đang phái và thực thi nhiệm vụ phối hợp Agent con…"
        HarnessTool.MCP -> "Đang gọi công cụ plugin MCP: ${rawToolName ?: "mcp"}…"
        HarnessTool.LOAD_RULE -> "Đang nạp khối quy tắc: ${arg(args, "rule") ?: "load_rule"}…"
        HarnessTool.LOAD_SKILL -> "Đang nạp kỹ năng: ${arg(args, "name") ?: "load_skill"}…"
        HarnessTool.COMPRESS -> "Đang nén lịch sử hội thoại…"
        HarnessTool.ASK_USER -> "Đang đặt câu hỏi cho bạn…"
        HarnessTool.RENDER_SURFACE -> "Đang tạo giao diện native A2UI: ${arg(args, "title") ?: arg(args, "surfaceId") ?: "surface"}"
        HarnessTool.KNOWLEDGE -> "Đang truy xuất kho tri thức: ${arg(args, "query")?.take(MAX_STATUS_ARG_LENGTH) ?: "查询"}"
        HarnessTool.KB_ADD -> "Đang ghi tài liệu kho tri thức: ${arg(args, "name")?.take(MAX_STATUS_ARG_LENGTH) ?: "新文档"}"
        HarnessTool.KB_LIST -> "Đang liệt kê tài liệu kho tri thức"
    }
}
