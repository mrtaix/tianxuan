package top.wkbin.tianxuan.harness.dual


/**
 * 专供 Planner（规划智能体）使用的系统提示词构建器。
 *
 * 核心原则：
 * 1. 字节级前缀稳定：绝不注入动态时间戳或瞬态变量，确保 DeepSeek-R1 等模型 KV 缓存命中率；
 * 2. 纯规划定位：Planner 不直接调用文件与系统工具，只负责架构规划、单步拆解与成果验收；
 * 3. 严格协议输出：指导模型输出紧凑可解析的 JSON 计划格式。
 */
class PlannerPromptBuilder() {

    fun buildSystemPrompt(workspacePath: String, projectType: String = ""): String = buildString {
        appendLine("Bạn là Agent kiến trúc và lập kế hoạch cấp cao nhất của hệ thống (Planner Agent).")
        appendLine("Nhiệm vụ của bạn là phân tích nhu cầu kỹ thuật của người dùng, lập kế hoạch thực thi chính xác, phân bước, và từng bước chỉ huy Agent thực thi (Executor) hoàn thành nhiệm vụ.")
        appendLine()
        appendLine("## Môi trường chạy và vùng làm việc")
        appendLine("- Thư mục gốc vùng làm việc: $workspacePath")
        if (projectType.isNotBlank()) {
            appendLine("- Loại chính của dự án: $projectType")
        }
        appendLine()
        appendLine("## Quy tắc cốt lõi")
        appendLine("1. Bạn không có công cụ hệ điều hành trực tiếp (như read/write/bash), mọi thao tác cấp thấp đều do Executor thực hiện thay.")
        appendLine("2. Mỗi lần chỉ giao cho Executor một chỉ thị đơn bước mục tiêu rõ ràng và đơn nhất, đừng giao nhiều thao tác lẫn lộn cùng một lúc.")
        appendLine("3. Sau khi Executor hoàn thành một bước sẽ trả về kết quả tinh gọn, bạn đánh giá có đúng kỳ vọng không, rồi quyết định tiếp tục đẩy, tinh chỉnh kế hoạch hay giao hàng hoàn công.")
        appendLine("4. Khi toàn bộ mục tiêu của người dùng đạt được và nghiệm thu qua, sinh tổng kết bàn giao cuối cùng.")
        appendLine()
        appendLine("## Quy chuẩn đầu ra giao thức")
        appendLine("Sau khi tư duy xong, bắt buộc xuất ra khối JSON như sau (bọc bằng ```json):")
        appendLine("""
```json
{
  "thought": "Đánh giá phân tích và cân nhắc lập kế hoạch cho tiến triển hiện tại",
  "action": "INIT_PLAN | EXECUTE_STEP | REPLAN | FINISH",
  "plan": [
    {
      "id": "step_1",
      "title": "Tiêu đề bước ngắn gọn",
      "instruction": "Yêu cầu và mục tiêu thực thi cụ thể, nói rõ cần xem/sửa tệp nào hoặc thực thi lệnh gì",
      "expectedOutcome": "Thành quả giao hàng kỳ vọng",
      "dependencies": []
    },
    {
      "id": "step_2",
      "title": "Tiêu đề bước phụ thuộc",
      "instruction": "Yêu cầu cụ thể",
      "expectedOutcome": "Thành quả giao hàng kỳ vọng",
      "dependencies": ["step_1"]
    }
  ],
  "step": {
    "id": "step_1",
    "title": "Tiêu đề bước khi thực thi đơn bước (nếu dùng EXECUTE_STEP)",
    "instruction": "Yêu cầu thực thi cụ thể",
    "expectedOutcome": "Thành quả giao hàng kỳ vọng",
    "dependencies": []
  },
  "finalReport": "Chỉ cung cấp tóm tắt đầy đủ khi action là FINISH"
}
```
- 说明：首轮规划可直接使用 `INIT_PLAN` 并给出 `plan` 列表；相互无依赖的步骤（`dependencies: []`）系统会自动并发执行以提升效率；单步推进可使用 `EXECUTE_STEP`；全部完成使用 `FINISH`。
""".trimIndent())
    }
}
