package top.wkbin.tianxuan.core.model.workflow

object BuiltinWorkflows {
    val all: List<WorkflowDefinition>
        get() = listOf(
            systemHealth,
            conditionBranchDemo,
            networkDiagnostic,
            releaseApk,
            installGeneratedApk,
            buildDoctor,
            buildRepair,
            reverseAudit,
            atomicCommit,
            hostAutomationLab,
            hostBroadcastDemo,
            hostAgentGuiPilot,
        ).map(WorkflowLayout::arrange)

    fun find(id: String): WorkflowDefinition? = all.firstOrNull { it.id == id }

    private fun chain(vararg ids: String): List<WorkflowEdge> = ids.toList().zipWithNext().mapIndexed { index, pair ->
        WorkflowEdge("edge_$index", pair.first, "success", pair.second)
    }

    val systemHealth = WorkflowDefinition(
        id = "linux_system_health",
        name = "Khám môi trường sandbox Linux và phần cứng",
        description = "Khám sức khỏe một chạm không cấu hình: kiểm tra toàn diện bản phân phối Linux của sandbox về kernel, kiến trúc, CPU, bộ nhớ, đĩa và trạng thái toolchain thường dùng.",
        category = "Kiểm tra sức khỏe",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf linux_system_health"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu kiểm tra", canvasX = 40f, canvasY = 120f),
            WorkflowNode(
                "os_kernel",
                WorkflowNodeType.BASH_COMMAND,
                "Kiểm tra hệ thống và nhân",
                config = mapOf("command" to "echo '=== 1. Nhân và kiến trúc ===' && uname -a && echo '' && echo '=== 2. Thông tin bản phát hành ===' && (cat /etc/os-release | head -n 8 || cat /etc/issue || echo 'Bản phát hành không xác định')"),
                canvasX = 280f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "hardware",
                WorkflowNodeType.BASH_COMMAND,
                "Trạng thái bộ nhớ và lưu trữ",
                config = mapOf("command" to "echo '=== 1. Tình hình sử dụng bộ nhớ ===' && (free -h || cat /proc/meminfo | head -n 4) && echo '' && echo '=== 2. Dung lượng đĩa thư mục gốc ===' && df -h /"),
                canvasX = 520f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "toolchain",
                WorkflowNodeType.BASH_COMMAND,
                "Điều tra chuỗi công cụ cốt lõi",
                config = mapOf("command" to "echo '=== Rà soát công cụ dòng lệnh cốt lõi ===' && for cmd in bash sh git python3 python curl wget make gcc tar find grep awk sed; do which \$cmd 2>/dev/null && echo \"  ✔ \$cmd: khả dụng (\$(which \$cmd))\" || echo \"  ✘ \$cmd: chưa cài đặt\"; done"),
                canvasX = 760f,
                canvasY = 120f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Sinh báo cáo kiểm tra sức khỏe", canvasX = 1000f, canvasY = 120f),
        ),
        edges = chain("start", "os_kernel", "hardware", "toolchain", "done"),
    )

    val conditionBranchDemo = WorkflowDefinition(
        id = "condition_branch_demo",
        name = "Kiểm thử logic nhánh điều kiện (Demo)",
        description = "Dò môi trường cơ sở sandbox, qua nhánh điều kiện lần lượt đi tới 【Nhánh A: Môi trường bình thường】 hoặc 【Nhánh B: Môi trường bất thường】.",
        category = "Ví dụ",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf condition_branch_demo"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Khởi động test", canvasX = 40f, canvasY = 160f),
            WorkflowNode(
                "check_env",
                WorkflowNodeType.BASH_COMMAND,
                "Kiểm tra môi trường cơ sở sandbox",
                config = mapOf("command" to "echo '=== Đang kiểm tra thư mục gốc sandbox Linux ===' && test -d /etc && echo '✔ Thư mục /etc tồn tại'"),
                canvasX = 280f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "branch",
                WorkflowNodeType.CONDITION_BRANCH,
                "Phân luồng nhánh điều kiện",
                description = "Phân nhánh luồng tới các nhánh xử lý khác nhau dựa trên kết quả dò thượng nguồn",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
                canvasX = 520f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "branch_success",
                WorkflowNodeType.BASH_COMMAND,
                "【Nhánh A】Môi trường nguyên vẹn",
                config = mapOf("command" to "echo '==============================' && echo '🎉【Nhánh A kích hoạt thành công】' && echo 'Cấu hình cơ bản của sandbox hoàn toàn bình thường, tiếp tục thực thi thao tác nghiệp vụ.' && echo '=============================='"),
                canvasX = 780f,
                canvasY = 80f,
            ),
            WorkflowNode(
                "branch_failure",
                WorkflowNodeType.BASH_COMMAND,
                "【Nhánh B】Cảnh báo môi trường bất thường",
                config = mapOf("command" to "echo '==============================' && echo '⚠️【Nhánh B kích hoạt thành công】' && echo 'Phát hiện sandbox thiếu thư mục cần thiết, chuyển sang quy trình hạ cấp và cảnh báo.' && echo '=============================='"),
                canvasX = 780f,
                canvasY = 240f,
            ),
            WorkflowNode(
                "done",
                WorkflowNodeType.TERMINAL_OUTPUT,
                "Tổng hợp test hoàn tất",
                canvasX = 1040f,
                canvasY = 160f,
            ),
        ),
        edges = listOf(
            WorkflowEdge("edge_demo_1", "start", "output", "check_env"),
            WorkflowEdge("edge_demo_2", "check_env", "output", "branch"),
            WorkflowEdge("edge_demo_3", "branch", "output", "branch_success", conditionExpression = "exitCode == 0"),
            WorkflowEdge("edge_demo_4", "branch", "output", "branch_failure", conditionExpression = "exitCode != 0"),
            WorkflowEdge("edge_demo_5", "branch_success", "output", "done"),
            WorkflowEdge("edge_demo_6", "branch_failure", "output", "done"),
        ),
    )

    val networkDiagnostic = WorkflowDefinition(
        id = "network_dns_diagnostic",
        name = "Chẩn đoán kết nối mạng và DNS",
        description = "Khám mạng một chạm không cấu hình: kiểm tra tệp cấu hình DNS trong container, khả năng phân giải tên miền và kết nối dịch vụ HTTP công cộng.",
        category = "Mạng",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf network_dns_diagnostic"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Khởi động chẩn đoán", canvasX = 40f, canvasY = 120f),
            WorkflowNode(
                "dns_conf",
                WorkflowNodeType.BASH_COMMAND,
                "Xem cấu hình DNS",
                config = mapOf("command" to "echo '=== /etc/resolv.conf ===' && (cat /etc/resolv.conf || echo 'không có resolv.conf') && echo '' && echo '=== /etc/hosts ===' && head -n 10 /etc/hosts"),
                canvasX = 280f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "http_ping",
                WorkflowNodeType.BASH_COMMAND,
                "Kiểm tra yêu cầu HTTP mạng công cộng",
                config = mapOf("command" to "echo '=== Kiểm tra kết nối mạng công cộng (HTTP HEAD) ===' && (curl -I -s -m 5 https://www.baidu.com | head -n 4 || curl -I -s -m 5 https://www.qq.com | head -n 4 || curl -I -s -m 5 https://www.bilibili.com | head -n 4 || echo 'curl kiểm tra hoàn tất')"),
                canvasX = 520f,
                canvasY = 120f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Chẩn đoán mạng hoàn tất", canvasX = 760f, canvasY = 120f),
        ),
        edges = chain("start", "dns_conf", "http_ping", "done"),
    )

    val releaseApk = WorkflowDefinition(
        id = "release_apk_direct_install",
        name = "Đóng gói và cài đặt APK",
        description = "Kiểm tra kho, build ARM64 Release APK, và sau khi xác nhận giao cho máy chủ cài đặt.",
        category = "Android",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf release_apk_direct_install"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu", canvasX = 40f, canvasY = 80f),
            WorkflowNode("git", WorkflowNodeType.BASH_COMMAND, "Kiểm tra trạng thái Git", config = mapOf("command" to "git status --short"), canvasX = 260f, canvasY = 80f),
            WorkflowNode("build", WorkflowNodeType.TIANXUAN_BUILD, "Build Release APK", config = mapOf("projectType" to "android", "task" to "assembleRelease"), timeoutSeconds = 3600, canvasX = 500f, canvasY = 80f),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận cài đặt", description = "Xác nhận có cài vào thiết bị máy chủ không sau khi xây dựng xong.", canvasX = 740f, canvasY = 80f),
            WorkflowNode("install", WorkflowNodeType.HOST_ACTION, "Cài APK", config = mapOf("action" to "install-apk", "artifactFrom" to "build"), timeoutSeconds = 600, canvasX = 980f, canvasY = 80f),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "完成", canvasX = 1220f, canvasY = 80f),
        ),
        edges = chain("start", "git", "build", "approve", "install", "done"),
    )

    val buildDoctor = WorkflowDefinition(
        id = "mobile_build_doctor",
        name = "Chẩn đoán môi trường build di động",
        description = "Chạy tianxuan-build doctor và analyze, xuất báo cáo tương thích có thể thao tác.",
        category = "Chẩn đoán",
        isBuiltin = true,
        trigger = WorkflowTrigger.Proactive("BUILD_FAILED", suggestionLabel = "Chẩn đoán build thất bại"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu"),
            WorkflowNode("doctor", WorkflowNodeType.TIANXUAN_BUILD, "Chẩn đoán môi trường", config = mapOf("mode" to "doctor"), timeoutSeconds = 900),
            WorkflowNode("analyze", WorkflowNodeType.TIANXUAN_BUILD, "Kỹ thuật phân tích", config = mapOf("mode" to "analyze"), timeoutSeconds = 900),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Xem kết quả chẩn đoán"),
        ),
        edges = chain("start", "doctor", "analyze", "done"),
    )

    val installGeneratedApk = WorkflowDefinition(
        id = "install_generated_apk",
        name = "Cài APK mới sinh gần nhất",
        description = "Dùng đường dẫn sản phẩm build, sau khi xác nhận thủ công giao cho máy chủ cài đặt.",
        category = "Android",
        isBuiltin = true,
        trigger = WorkflowTrigger.Proactive("APK_GENERATED", suggestionLabel = "Cài APK vừa sinh"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Đọc APK", config = mapOf("requiredVariables" to "APK_PATH")),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận cài đặt", description = "Xác nhận cài APK vừa tạo lên thiết bị máy chủ: \${APK_PATH}", canvasX = 260f),
            WorkflowNode("install", WorkflowNodeType.HOST_ACTION, "Cài APK", config = mapOf("action" to "install-apk"), timeoutSeconds = 600, canvasX = 520f),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Yêu cầu cài đặt đã gửi", canvasX = 780f),
        ),
        edges = chain("start", "approve", "install", "done"),
    )

    val reverseAudit = WorkflowDefinition(
        id = "android_reverse_audit",
        name = "Kiểm toán dịch ngược APK",
        description = "Sau khi giải gói, dịch ngược thì Agent kiểm toán chỉ đọc, xuất báo cáo rủi ro kèm bằng chứng; thư mục đầu ra đã có sẽ không bị ghi đè.",
        category = "Dịch ngược",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf android_reverse_audit"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Chọn APK", config = mapOf("requiredVariables" to "APK_PATH")),
            WorkflowNode("apktool", WorkflowNodeType.BASH_COMMAND, "Giải nén tài nguyên", config = mapOf("command" to "apktool d \"${'$'}{APK_PATH}\" -o ./workflow-output/apktool"), timeoutSeconds = 1200),
            WorkflowNode("jadx", WorkflowNodeType.BASH_COMMAND, "Dịch ngược mã nguồn", config = mapOf("command" to "jadx -d ./workflow-output/jadx \"${'$'}{APK_PATH}\""), timeoutSeconds = 1800),
            WorkflowNode("audit", WorkflowNodeType.AGENT_INFERENCE, "Kiểm toán và báo cáo chỉ đọc", config = mapOf("prompt" to "Kiểm toán chỉ đọc workspace workflow-output/apktool và workflow-output/jadx. Trước tiên lập chỉ mục và tìm kiếm manifest, thành phần export, cấu hình mạng và xử lý dữ liệu nhạy cảm, rồi đọc mã nguồn liên quan. Không được sửa tệp hoặc chạy chương trình bị kiểm toán. Xuất báo cáo Markdown, liệt kê tệp và số dòng, bằng chứng, ảnh hưởng, khuyến nghị và những điều không thể xác minh; đừng ghi suy đoán thành lỗ hổng đã xác minh."), timeoutSeconds = 1800),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Báo cáo kiểm toán"),
        ),
        edges = chain("start", "apktool", "jadx", "audit", "done"),
    )

    val atomicCommit = WorkflowDefinition(
        id = "git_atomic_smart_commit",
        name = "Rà soát và nộp thay đổi",
        description = "Hiển thị thay đổi, chờ xác nhận thủ công rồi dùng mô tả commit truyền vào để tạo commit cục bộ; mặc định không push.",
        category = "Git",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf git_atomic_smart_commit"),
        defaultVariables = mapOf("COMMIT_MESSAGE" to "chore: update workspace"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu"),
            WorkflowNode("diff", WorkflowNodeType.BASH_COMMAND, "Rà soát thay đổi", config = mapOf("command" to "git status --short && git diff --stat && git diff && git diff --cached")),
            WorkflowNode("suggest", WorkflowNodeType.AGENT_INFERENCE, "Tạo gợi ý commit", config = mapOf("prompt" to "Rà soát chỉ đọc các thay đổi Git dưới đây, xuất ra một gợi ý Conventional Commit và tóm tắt rủi ro ngắn gọn. Không được sửa, stage, commit hay push bất kỳ tệp nào. Thay đổi:\n${'$'}{diff.output}")),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận commit cục bộ", config = mapOf("requestedVariables" to "COMMIT_MESSAGE")),
            WorkflowNode("commit", WorkflowNodeType.BASH_COMMAND, "Tạo commit", config = mapOf("command" to "git add -A && git commit -F - <<< \"\${COMMIT_MESSAGE}\"")),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Commit hoàn tất"),
        ),
        edges = chain("start", "diff", "suggest", "approve", "commit", "done"),
    )

    val buildRepair = WorkflowDefinition(
        id = "assisted_build_repair",
        name = "Xác nhận rồi sửa build",
        description = "Chẩn đoán nguyên nhân thất bại, sau khi xác nhận thì ủy thác Agent sửa tối thiểu, rồi hiển thị Diff và xác nhận build lại; sẽ không tự động commit hoặc push.",
        category = "Chẩn đoán",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf assisted_build_repair"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Nhập lỗi build", config = mapOf("requiredVariables" to "BUILD_ERROR")),
            WorkflowNode("diagnose", WorkflowNodeType.AGENT_INFERENCE, "Chẩn đoán chỉ đọc", config = mapOf("prompt" to "Kiểm tra chỉ đọc workspace hiện tại, dựa vào lỗi sau để giải thích nguyên nhân gốc và kế hoạch sửa tối thiểu. Không được sửa tệp.\n${'$'}{BUILD_ERROR}"), timeoutSeconds = 900),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Cho phép sửa tệp", description = "Xác nhận cho phép Agent sửa đổi workspace hiện tại theo kết quả chẩn đoán. Hãy đảm bảo đã sao lưu các thay đổi quan trọng trước."),
            WorkflowNode("repair", WorkflowNodeType.SUBAGENT_DELEGATE, "Thực hiện sửa lỗi tối thiểu", config = mapOf("writePaths" to ".", "prompt" to "Thực hiện sửa build tối thiểu theo chẩn đoán sau, giữ lại các sửa đổi hiện có của người dùng; cấm xóa dự án, reset Git, commit hoặc push. Khi không chắc chắn hãy dừng lại và giải thích. Chẩn đoán:\n${'$'}{diagnose.output}\nLỗi gốc:\n${'$'}{BUILD_ERROR}"), timeoutSeconds = 1800),
            WorkflowNode("diff", WorkflowNodeType.BASH_COMMAND, "Kiểm tra sửa chữa Diff", config = mapOf("command" to "git status --short && git diff && git diff --cached")),
            WorkflowNode("rebuildApproval", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận build lại", description = "Rà soát sửa xong thì khởi động build xác minh. Từ chối sẽ không rollback các thay đổi đã hoàn thành."),
            WorkflowNode("build", WorkflowNodeType.TIANXUAN_BUILD, "Xác minh build", config = mapOf("task" to "assembleDebug"), timeoutSeconds = 3600),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Kết quả xác minh sửa chữa"),
        ),
        edges = chain("start", "diagnose", "approve", "repair", "diff", "rebuildApproval", "build", "done"),
    )

    val hostAutomationLab = WorkflowDefinition(
        id = "host_automation_lab",
        name = "Phòng thí nghiệm tự động hóa host",
        description = "Dò quyền → mở cài đặt → chờ foreground → cảm nhận màn hình → phân nhánh theo điều kiện → kết thúc bằng thông báo. Cần Shizuku/Root mới chạy hết chuỗi.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_automation_lab"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu", canvasX = 40f, canvasY = 160f),
            WorkflowNode(
                "status",
                WorkflowNodeType.HOST_ACTION,
                "Kiểm tra chế độ quyền",
                config = mapOf("action" to "status"),
                canvasX = 240f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Đã đặc quyền chưa?",
                config = mapOf("expression" to "\${HOST_PRIVILEGED} == true"),
                failurePolicy = FailurePolicy.CONTINUE,
                canvasX = 460f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "launch",
                WorkflowNodeType.HOST_ACTION,
                "Mở cài đặt hệ thống",
                config = mapOf("action" to "app_launch", "package" to "com.android.settings"),
                canvasX = 700f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "wait",
                WorkflowNodeType.HOST_ACTION,
                "Chờ cài đặt vào foreground",
                config = mapOf("action" to "wait_foreground", "package" to "com.android.settings", "timeoutSeconds" to "12"),
                canvasX = 920f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "observe",
                WorkflowNodeType.HOST_ACTION,
                "Cảm nhận điều khiển màn hình",
                config = mapOf("action" to "screen_observe", "onlyInteractive" to "true"),
                canvasX = 1140f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "notify_ok",
                WorkflowNodeType.HOST_ACTION,
                "Thông báo: tự động hóa thành công",
                config = mapOf(
                    "action" to "notification",
                    "title" to "Luồng công việc Thiên Huyền",
                    "text" to "Phòng thí nghiệm tự động hóa máy chủ đã hoàn tất cảm nhận màn hình",
                ),
                canvasX = 1360f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "degraded",
                WorkflowNodeType.HOST_ACTION,
                "Hạ cấp: chỉ nhắc Toast",
                config = mapOf(
                    "action" to "toast",
                    "text" to "Chưa phát hiện Shizuku/Root, đã bỏ qua bước tự động hóa đặc quyền",
                ),
                canvasX = 700f,
                canvasY = 260f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Kết thúc phòng thí nghiệm", canvasX = 1580f, canvasY = 160f),
        ),
        edges = listOf(
            WorkflowEdge("e1", "start", "output", "status"),
            WorkflowEdge("e2", "status", "output", "gate"),
            WorkflowEdge("e3", "gate", "output", "launch", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e4", "gate", "output", "degraded", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e5", "launch", "output", "wait"),
            WorkflowEdge("e6", "wait", "output", "observe"),
            WorkflowEdge("e7", "observe", "output", "notify_ok"),
            WorkflowEdge("e8", "notify_ok", "output", "done"),
            WorkflowEdge("e9", "degraded", "output", "done"),
        ),
    )

    val hostBroadcastDemo = WorkflowDefinition(
        id = "host_broadcast_and_settings",
        name = "Demo broadcast + cài đặt hệ thống",
        description = "Ghi biến → gửi broadcast tùy chỉnh → đọc/ghi lại cài đặt liên quan độ sáng (cần đặc quyền). Trước thao tác phá hủy có xác nhận thủ công.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_broadcast_and_settings"),
        defaultVariables = mapOf("DEMO_FLAG" to "tianxuan-workflow"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu"),
            WorkflowNode(
                "vars",
                WorkflowNodeType.SET_VARIABLE,
                "Chuẩn bị biến demo",
                config = mapOf(
                    "variables" to "BROADCAST_ACTION=top.wkbin.tianxuan.action.WORKFLOW_DEMO\nPAYLOAD=\${DEMO_FLAG}",
                ),
            ),
            WorkflowNode(
                "broadcast",
                WorkflowNodeType.HOST_ACTION,
                "Gửi broadcast demo",
                config = mapOf(
                    "action" to "send_broadcast",
                    "intentAction" to "\${BROADCAST_ACTION}",
                    "extras" to "source=workflow\npayload=\${PAYLOAD}\nflag:bool=true",
                ),
            ),
            WorkflowNode(
                "read_brightness",
                WorkflowNodeType.HOST_ACTION,
                "Đọc độ sáng hiện tại",
                config = mapOf(
                    "action" to "settings_get",
                    "namespace" to "system",
                    "key" to "screen_brightness",
                    "outputVariable" to "BRIGHTNESS",
                ),
            ),
            WorkflowNode(
                "approve",
                WorkflowNodeType.HUMAN_APPROVAL,
                "Xác nhận có tinh chỉnh độ sáng không",
                description = "Sẽ đặt độ sáng thành 120 (có thể bỏ qua sau khi từ chối). Giá trị hiện tại xem đầu ra bước trước.",
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Người dùng có phê duyệt không?",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "write_brightness",
                WorkflowNodeType.HOST_ACTION,
                "Ghi độ sáng 120",
                config = mapOf(
                    "action" to "settings_put",
                    "namespace" to "system",
                    "key" to "screen_brightness",
                    "value" to "120",
                ),
            ),
            WorkflowNode(
                "delay",
                WorkflowNodeType.DELAY,
                "Chờ một lát",
                config = mapOf("seconds" to "1"),
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Demo hoàn thành"),
        ),
        edges = listOf(
            WorkflowEdge("e1", "start", "output", "vars"),
            WorkflowEdge("e2", "vars", "output", "broadcast"),
            WorkflowEdge("e3", "broadcast", "output", "read_brightness"),
            WorkflowEdge("e4", "read_brightness", "output", "approve"),
            WorkflowEdge("e5", "approve", "output", "gate"),
            WorkflowEdge("e6", "gate", "output", "write_brightness", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e7", "gate", "output", "done", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e8", "write_brightness", "output", "delay"),
            WorkflowEdge("e9", "delay", "output", "done"),
        ),
    )

    /**
     * Agent-in-the-loop GUI pilot: privilege check → approve → optional launch →
     * AGENT_INFERENCE that drives host(screen_*) tools to chase [GUI_GOAL].
     */
    val hostAgentGuiPilot = WorkflowDefinition(
        id = "host_agent_gui_pilot",
        name = "Thử bay GUI Agent",
        description = "Nén lịch sử phiên cũ hơn thành tóm tắt có cấu trúc để giải phóng không gian ngữ cảnh; nguyên văn đã gộp vẫn có thể đọc lại theo nhu cầu qua history_read. Chỉ gọi khi người dùng yêu cầu rõ ràng nén ngữ cảnh/tiết kiệm không gian, đừng tự quyết định sử dụng.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_agent_gui_pilot"),
        defaultVariables = mapOf(
            "GUI_GOAL" to "Mở QQ, vào nhóm chat QQ liên quan đến 「Thiên Huyền」, gửi một câu trong khung nhập: 「Thiên Huyền tuyệt vời (từ workflow)」. Không gửi lì xì, không chuyển tiền, không đổi cài đặt nhóm. Nếu không tìm thấy nhóm có tên chứa 「Thiên Huyền」, hãy dừng lại và nêu rõ danh sách hội thoại hiện thấy.",
            "TARGET_PACKAGE" to "com.tencent.mobileqq",
        ),
        nodes = listOf(
            WorkflowNode(
                "start",
                WorkflowNodeType.TRIGGER,
                "Điền mục tiêu",
                config = mapOf("requiredVariables" to "GUI_GOAL"),
            ),
            WorkflowNode(
                "status",
                WorkflowNodeType.HOST_ACTION,
                "Kiểm tra Shizuku/Root",
                config = mapOf("action" to "status"),
            ),
            WorkflowNode(
                "priv_gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Đặc quyền có sẵn sàng không?",
                config = mapOf("expression" to "\${HOST_PRIVILEGED} == true"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "no_priv",
                WorkflowNodeType.HOST_ACTION,
                "Nhắc thiếu đặc quyền",
                config = mapOf(
                    "action" to "toast",
                    "text" to "Vui lòng cấp quyền và chuyển sang Shizuku hoặc Root trong cài đặt trước, rồi thử lại workflow này",
                ),
            ),
            WorkflowNode(
                "approve",
                WorkflowNodeType.HUMAN_APPROVAL,
                "Xác nhận cho Agent điều khiển màn hình",
                description = "Agent sẽ dùng host(screen_observe/click/swipe/input_text/key/app_launch) để tự động thao tác giao diện điện thoại nhằm hoàn thành GUI_GOAL.\n\n" +
                    "Mục tiêu mặc định sẽ mở QQ, vào nhóm Thiên Huyền và gửi 「Thiên Huyền tuyệt vời (từ workflow)」. Vui lòng tự theo dõi màn hình; nếu giao diện nhảy sang thanh toán/lì xì, nên từ chối hoặc ngắt ngay.\n\n" +
                    "Từ chối thì kết thúc, sẽ không khởi động điều khiển.",
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "approve_gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Người dùng có phê duyệt không?",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "launch",
                WorkflowNodeType.HOST_ACTION,
                "Mở ứng dụng mục tiêu (tùy chọn)",
                config = mapOf(
                    "action" to "app_launch",
                    "package" to "\${TARGET_PACKAGE}",
                ),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "wait_fg",
                WorkflowNodeType.DELAY,
                "Chờ giao diện ổn định",
                config = mapOf("seconds" to "2"),
            ),
            WorkflowNode(
                "pilot",
                WorkflowNodeType.HOST_ACTION,
                "Vòng lặp GUI thử nghiệm",
                config = mapOf(
                    "action" to "gui_pilot",
                    "goal" to "\${GUI_GOAL}",
                    "package" to "\${TARGET_PACKAGE}",
                    "maxSteps" to "18",
                ),
                timeoutSeconds = 900,
            ),
            WorkflowNode(
                "notify",
                WorkflowNodeType.HOST_ACTION,
                "Thông báo kết thúc thử bay",
                config = mapOf(
                    "action" to "notification",
                    "title" to "Kết thúc chạy thử GUI",
                    "text" to "GUI thử nghiệm Agent đã kết thúc, vui lòng xem đầu ra workflow",
                ),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Báo cáo thử nghiệm"),
            WorkflowNode("aborted", WorkflowNodeType.TERMINAL_OUTPUT, "Đã hủy hoặc thiếu đặc quyền"),
        ),
        edges = listOf(
            WorkflowEdge("e1", "start", "output", "status"),
            WorkflowEdge("e2", "status", "output", "priv_gate"),
            WorkflowEdge("e3", "priv_gate", "output", "approve", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e4", "priv_gate", "output", "no_priv", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e5", "no_priv", "output", "aborted"),
            WorkflowEdge("e6", "approve", "output", "approve_gate"),
            WorkflowEdge("e7", "approve_gate", "output", "launch", conditionExpression = "exitCode == 0"),
            WorkflowEdge("e8", "approve_gate", "output", "aborted", conditionExpression = "exitCode != 0"),
            WorkflowEdge("e9", "launch", "output", "wait_fg"),
            WorkflowEdge("e10", "wait_fg", "output", "pilot"),
            WorkflowEdge("e11", "pilot", "output", "notify"),
            WorkflowEdge("e12", "notify", "output", "done"),
        ),
    )
}
