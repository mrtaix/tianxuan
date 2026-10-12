package top.wkbin.tianxuan.harness.prompt


/**
 * 系统提示词分层路由器（L2 任务规则选择）。
 *
 * L0（core / tools）与 L1（environment-proot / 权限章节）常驻；
 * 本路由器根据最新用户消息、项目类型与当前状态，决定本轮额外注入哪些 L2 规则块。
 *
 * 路由判断保守宁滥勿缺：多注入一个块的成本远小于模型在缺少规则时犯错；
 * 若代码层判断不确定，模型仍可通过 load_rule 工具按需自取。
 */
class PromptRouter(
    private val promptAssets: PromptAssetLoader,
) {
    /** 可按需加载的规则块。[assetPath] 为 assets 内路径，[loadName] 为 load_rule 工具使用的标识。 */
    enum class RuleBlock(val assetPath: String, val loadName: String) {
        TOOLS("prompts/system/tools.md", "tools"),
        ENVIRONMENT_PROOT("prompts/system/environment-proot.md", "environment-proot"),
        WORKFLOW("prompts/system/workflow.md", "workflow"),
        CODE_NAVIGATION("prompts/system/code-navigation.md", "code-navigation"),
        SECURITY("prompts/system/security.md", "security"),
        MEMORY("prompts/system/memory.md", "memory"),
        IMAGE_DELIVERY("prompts/system/image-delivery.md", "image-delivery"),
        BROWSER_REVERSE("prompts/system/browser-reverse.md", "browser-reverse"),
        ASTRONOMY("prompts/system/astronomy.md", "astronomy"),
    }

    /** 每轮常驻注入的规则块（L0 工具说明 + L1 PRoot 约束）。 */
    val permanentBlocks: List<RuleBlock> = listOf(RuleBlock.TOOLS, RuleBlock.ENVIRONMENT_PROOT)

    /**
     * 根据当前任务上下文选择本轮需要注入的 L2 规则块（不含常驻块）。
     *
     * @param latestUserMessage 最新一条用户消息文本（用于启发式判断）
     * @param projectType 检测到的项目类型（Android / Flutter / Android APK 逆向 / 通用工程）
     * @param hasWorkspace 是否关联了工作区
     * @param activePlanExists 当前是否已有进行中的 plan
     */
    fun route(
        latestUserMessage: String,
        projectType: String,
        hasWorkspace: Boolean,
        activePlanExists: Boolean,
    ): Set<RuleBlock> {
        val blocks = mutableSetOf<RuleBlock>()
        val text = latestUserMessage.lowercase()

        // 代码项目默认注入代码导航；通用工程仅在消息出现强代码信号时注入。
        val codeProject = projectType == "Android" || projectType == "Flutter" || projectType == "Android APK 逆向"
        if (codeProject || (hasWorkspace && CODE_SIGNALS.any { it in text })) {
            blocks.add(RuleBlock.CODE_NAVIGATION)
        }

        // 复杂任务信号或已有活动 plan 时注入工作流规则。
        if (activePlanExists || WORKFLOW_SIGNALS.any { it in text }) {
            blocks.add(RuleBlock.WORKFLOW)
        }

        // 高风险操作信号注入安全确认规则。
        if (SECURITY_SIGNALS.any { it in text }) {
            blocks.add(RuleBlock.SECURITY)
        }

        // 明确的记忆意图注入记忆策略。
        if (MEMORY_SIGNALS.any { it in text }) {
            blocks.add(RuleBlock.MEMORY)
        }

        // 图片查找 / 展示 / 下载任务注入交付规范（Markdown 图片语法、图床反爬规避）。
        if (IMAGE_DELIVERY_SIGNALS.any { it in text }) {
            blocks.add(RuleBlock.IMAGE_DELIVERY)
        }

        // 浏览器逆向 / 调试任务注入 hook 与 CDP 准则；信号刻意偏宽：
        // 漏掉「调试结束必须 debug_resume」铁律会让页面永久冻结，多注入 1.5KB 的代价远小于此。
        if (BROWSER_REVERSE_SIGNALS.any { it in text }) {
            blocks.add(RuleBlock.BROWSER_REVERSE)
        }

        return blocks
    }

    /** load_rule 工具入口：按名称读取规则块正文；未知名称返回 null。 */
    fun loadRule(name: String): String? {
        val block = RuleBlock.entries.firstOrNull { it.loadName == name.trim().lowercase() } ?: return null
        return runCatching { promptAssets.read(block.assetPath) }.getOrNull()
    }

    /** 可用规则块名单（供错误提示动态生成，避免硬编码清单随枚举漂移）。 */
    fun availableRuleNames(): String = RuleBlock.entries.joinToString(" / ") { it.loadName }

    companion object {
        /**
         * load_rule 工具的规则名 JSON 片段。
         *
         * 由 [RuleBlock] 枚举生成，作为「规则清单」的唯一来源：新增规则块时，
         * 工具 schema 自动同步，不再出现「提示词声明可 load、schema 却拒绝」的漂移。
         */
        private val RULE_NAMES_JSON: String =
            RuleBlock.entries.joinToString(",") { "\"${it.loadName}\"" }

        /** load_rule 工具描述（规则清单由枚举生成）。 */
        val LOAD_RULE_DESCRIPTION: String =
            "Nạp theo nhu cầu khối quy tắc chi tiết của system prompt (${RuleBlock.entries.joinToString(" / ") { it.loadName }}）。" +
                "Gọi khi nhiệm vụ hiện tại cần một khối quy tắc nào đó nhưng system prompt chưa chèn; chỉ đọc, không có tác dụng phụ."

        /** load_rule 工具的 JSON Schema。 */
        val LOAD_RULE_SCHEMA: String =
            """{"type":"object","properties":{"rule":{"type":"string","enum":[$RULE_NAMES_JSON],"description":"要加载的规则块名称"}},"required":["rule"]}"""

        private val CODE_SIGNALS = listOf(
            "Tái cấu trúc", "Chuỗi gọi", "Bên gọi", "Được gọi", "callee", "caller", "Phạm vi ảnh hưởng", "Ký hiệu",
            "Hàm", "Phương thức", "Định nghĩa lớp", "Định nghĩa giao diện", "Định nghĩa ở đâu", "Phân tích mã", "架构", "Mã nguồn",
            "Biên dịch thất bại", "Lỗi biên dịch", "Sự cố", "crash", "stacktrace", "Ngăn xếp", "异常",
            "retrofit", "room", "compose", "activity", "fragment", "gradle", "codegraph",
            "refactor", "where is", "call chain", "implementation of",
        )

        private val WORKFLOW_SIGNALS = listOf(
            "Gỡ lỗi", "Điều tra", "Gỡ lỗi", "debug", "Báo lỗi", "错误", "失败", "Sự cố", "crash",
            "异常", "exception", "stacktrace", "Ngăn xếp", "Dịch ngược", "Dịch ngược", "Tái cấu trúc",
            "Build thất bại", "Biên dịch thất bại", "Cài đặt và", "Cấu hình và", "Triển khai", "Vì sao không", "Không hoạt động",
            "doesn't work", "does not work", "not working", "troubleshoot", "fix",
            "Từng bước một", "Nhiều bước", "Phức tạp", "Quy hoạch", "Kế hoạch", "plan",
            "Triển khai", "Phát triển", "Biên soạn", "Tạo", "Xây dựng", "Thiết kế", "Thêm mới", "添加",
            "Trình thu thập", "Thu thập", "Hàng loạt", "Khảo sát", "Phương án", "Di trú", "Tích hợp", "Viết lại", "Cải tạo", "Tối ưu",
            "implement", "develop", "create", "build", "design", "crawler", "scrape", "batch", "migrate", "integrate",
        )

        private val SECURITY_SIGNALS = listOf(
            "删除", "Xóa đi", "清空", "Xóa bỏ", "卸载", "Gỡ bỏ", "Định dạng hóa", "Đặt lại", "Đóng băng",
            "rm -rf", "wipe", "uninstall", "format", "delete all", "drop table",
        )

        private val MEMORY_SIGNALS = listOf(
            "Ghi nhớ", "Ghi lại", "Về sau đều", "Mặc định về sau", "Sở thích của tôi", "Đây là quy chuẩn",
            "remember", "my preference",
        )

        private val IMAGE_DELIVERY_SIGNALS = listOf(
            "Hình ảnh", "Ảnh", "Hình nền", "Ảnh đại diện", "Poster", "Ảnh bìa", "Ảnh minh họa", "Gửi cho tôi", "Gửi ảnh", "Chụp một tấm",
            "Lưu về cục bộ", "Tải về cục bộ", "Lưu giữ", "Ảnh đẹp", "Gói nhãn dán",
            "image", "picture", "photo", "pic",
        )

        private val BROWSER_REVERSE_SIGNALS = listOf(
            "Dịch ngược", "Dịch ngược", "Bắt gói", "Bắt giao diện", "hook", "Điểm dừng", "Mã hóa", "Chữ ký",
            "mock", "Chặn", "worker", "Trình thu thập", "Thu thập",
            "Trang web", "Trang", "Trang web", "Trình duyệt", "Địa chỉ web",
            "browser", "web page", "debug",
        )
    }
}
