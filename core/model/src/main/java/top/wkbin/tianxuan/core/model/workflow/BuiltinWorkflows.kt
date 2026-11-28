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
        name = "Kiểm tra môi trường sandbox Linux và phần cứng",
        description = "Kiểm tra toàn diện chỉ với một lần chạy, không cần cấu hình: kernel, kiến trúc, CPU, bộ nhớ, ổ đĩa và trạng thái toolchain phổ biến của bản phân phối Linux trong sandbox.",
        category = "Kiểm tra hệ thống",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf linux_system_health"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu kiểm tra hệ thống", canvasX = 40f, canvasY = 120f),
            WorkflowNode(
                "os_kernel",
                WorkflowNodeType.BASH_COMMAND,
                "Kiểm tra hệ thống và kernel",
                config = mapOf("command" to "echo '=== 1. Kernel và kiến trúc ===' && uname -a && echo '' && echo '=== 2. Thông tin bản phân phối ===' && (cat /etc/os-release | head -n 8 || cat /etc/issue || echo 'Bản phân phối không xác định')"),
                canvasX = 280f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "hardware",
                WorkflowNodeType.BASH_COMMAND,
                "Trạng thái bộ nhớ và lưu trữ",
                config = mapOf("command" to "echo '=== 1. Tình trạng sử dụng bộ nhớ ===' && (free -h || cat /proc/meminfo | head -n 4) && echo '' && echo '=== 2. Dung lượng đĩa thư mục gốc ===' && df -h /"),
                canvasX = 520f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "toolchain",
                WorkflowNodeType.BASH_COMMAND,
                "Kiểm tra toolchain cốt lõi",
                config = mapOf("command" to "echo '=== Kiểm tra công cụ dòng lệnh cốt lõi ===' && for cmd in bash sh git python3 python curl wget make gcc tar find grep awk sed; do which \$cmd 2>/dev/null && echo \"  ✔ \$cmd: khả dụng (\$(which \$cmd))\" || echo \"  ✘ \$cmd: chưa cài đặt\"; done"),
                canvasX = 760f,
                canvasY = 120f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Tạo báo cáo kiểm tra hệ thống", canvasX = 1000f, canvasY = 120f),
        ),
        edges = chain("start", "os_kernel", "hardware", "toolchain", "done"),
    )

    val conditionBranchDemo = WorkflowDefinition(
        id = "condition_branch_demo",
        name = "Kiểm tra logic nhánh điều kiện (Demo)",
        description = "Kiểm tra môi trường cơ bản của sandbox, sau đó rẽ nhánh đến 【Nhánh A: Môi trường bình thường】 hoặc 【Nhánh B: Môi trường bất thường】.",
        category = "Ví dụ",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf condition_branch_demo"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu kiểm tra", canvasX = 40f, canvasY = 160f),
            WorkflowNode(
                "check_env",
                WorkflowNodeType.BASH_COMMAND,
                "Kiểm tra môi trường sandbox cơ bản",
                config = mapOf("command" to "echo '=== Đang kiểm tra thư mục gốc sandbox Linux ===' && test -d /etc && echo '✔ Thư mục /etc tồn tại'"),
                canvasX = 280f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "branch",
                WorkflowNodeType.CONDITION_BRANCH,
                "Điều hướng nhánh điều kiện",
                description = "Dựa trên kết quả kiểm tra trước đó để điều hướng quy trình sang các nhánh xử lý khác nhau",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
                canvasX = 520f,
                canvasY = 160f,
            ),
            WorkflowNode(
                "branch_success",
                WorkflowNodeType.BASH_COMMAND,
                "【Nhánh A】Môi trường hoạt động tốt",
                config = mapOf("command" to "echo '==============================' && echo '🎉【Nhánh A kích hoạt thành công】' && echo 'Cấu hình cơ bản của sandbox hoàn toàn bình thường, tiếp tục thực hiện thao tác nghiệp vụ.' && echo '=============================='"),
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
                "Hoàn tất tổng hợp kiểm tra",
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
        description = "Kiểm tra hệ thống mạng không cần cấu hình: kiểm tra tệp cấu hình DNS trong container, khả năng phân giải tên miền và kết nối dịch vụ HTTP công cộng.",
        category = "Mạng",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf network_dns_diagnostic"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu chẩn đoán", canvasX = 40f, canvasY = 120f),
            WorkflowNode(
                "dns_conf",
                WorkflowNodeType.BASH_COMMAND,
                "Xem cấu hình DNS",
                config = mapOf("command" to "echo '=== /etc/resolv.conf ===' && (cat /etc/resolv.conf || echo 'Không có resolv.conf') && echo '' && echo '=== /etc/hosts ===' && head -n 10 /etc/hosts"),
                canvasX = 280f,
                canvasY = 120f,
            ),
            WorkflowNode(
                "http_ping",
                WorkflowNodeType.BASH_COMMAND,
                "Kiểm tra yêu cầu HTTP công cộng",
                config = mapOf("command" to "echo '=== Kiểm tra kết nối Internet công cộng (HTTP HEAD) ===' && (curl -I -s -m 5 https://www.baidu.com | head -n 4 || curl -I -s -m 5 https://www.qq.com | head -n 4 || curl -I -s -m 5 https://www.bilibili.com | head -n 4 || echo 'Kiểm tra curl hoàn tất')"),
                canvasX = 520f,
                canvasY = 120f,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Hoàn tất chẩn đoán mạng", canvasX = 760f, canvasY = 120f),
        ),
        edges = chain("start", "dns_conf", "http_ping", "done"),
    )

    val releaseApk = WorkflowDefinition(
        id = "release_apk_direct_install",
        name = "Đóng gói và cài đặt APK",
        description = "Kiểm tra repository, build APK Release ARM64 và chuyển cho host cài đặt sau khi xác nhận.",
        category = "Android",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf release_apk_direct_install"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu", canvasX = 40f, canvasY = 80f),
            WorkflowNode("git", WorkflowNodeType.BASH_COMMAND, "Kiểm tra trạng thái Git", config = mapOf("command" to "git status --short"), canvasX = 260f, canvasY = 80f),
            WorkflowNode("build", WorkflowNodeType.TIANXUAN_BUILD, "Build APK Release", config = mapOf("projectType" to "android", "task" to "assembleRelease"), timeoutSeconds = 3600, canvasX = 500f, canvasY = 80f),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận cài đặt", description = "Sau khi build xong, xác nhận có cài đặt lên thiết bị host hay không.", canvasX = 740f, canvasY = 80f),
            WorkflowNode("install", WorkflowNodeType.HOST_ACTION, "Cài đặt APK", config = mapOf("action" to "install-apk", "artifactFrom" to "build"), timeoutSeconds = 600, canvasX = 980f, canvasY = 80f),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Hoàn tất", canvasX = 1220f, canvasY = 80f),
        ),
        edges = chain("start", "git", "build", "approve", "install", "done"),
    )

    val buildDoctor = WorkflowDefinition(
        id = "mobile_build_doctor",
        name = "Chẩn đoán môi trường build di động",
        description = "Chạy tianxuan-build doctor và analyze, xuất báo cáo tương thích có thể thực hiện.",
        category = "Chẩn đoán",
        isBuiltin = true,
        trigger = WorkflowTrigger.Proactive("BUILD_FAILED", suggestionLabel = "Chẩn đoán lỗi build"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu"),
            WorkflowNode("doctor", WorkflowNodeType.TIANXUAN_BUILD, "Chẩn đoán môi trường", config = mapOf("mode" to "doctor"), timeoutSeconds = 900),
            WorkflowNode("analyze", WorkflowNodeType.TIANXUAN_BUILD, "Phân tích dự án", config = mapOf("mode" to "analyze"), timeoutSeconds = 900),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Xem kết quả chẩn đoán"),
        ),
        edges = chain("start", "doctor", "analyze", "done"),
    )

    val installGeneratedApk = WorkflowDefinition(
        id = "install_generated_apk",
        name = "Cài đặt APK mới tạo nhất",
        description = "Dùng đường dẫn sản phẩm build và chuyển host cài đặt sau khi được xác nhận thủ công.",
        category = "Android",
        isBuiltin = true,
        trigger = WorkflowTrigger.Proactive("APK_GENERATED", suggestionLabel = "Cài đặt APK vừa tạo"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Đọc APK", config = mapOf("requiredVariables" to "APK_PATH")),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận cài đặt", description = "Xác nhận cài APK vừa tạo lên thiết bị host:\${APK_PATH}", canvasX = 260f),
            WorkflowNode("install", WorkflowNodeType.HOST_ACTION, "Cài đặt APK", config = mapOf("action" to "install-apk"), timeoutSeconds = 600, canvasX = 520f),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Đã gửi yêu cầu cài đặt", canvasX = 780f),
        ),
        edges = chain("start", "approve", "install", "done"),
    )

    val reverseAudit = WorkflowDefinition(
        id = "android_reverse_audit",
        name = "Kiểm toán phân tích ngược APK",
        description = "Sau khi giải nén và dịch ngược, agent sẽ kiểm toán chỉ đọc và xuất báo cáo rủi ro kèm bằng chứng; không ghi đè thư mục đầu ra đã có.",
        category = "Phân tích ngược",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf android_reverse_audit"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Chọn APK", config = mapOf("requiredVariables" to "APK_PATH")),
            WorkflowNode("apktool", WorkflowNodeType.BASH_COMMAND, "Giải nén tài nguyên", config = mapOf("command" to "apktool d \"${'$'}{APK_PATH}\" -o ./workflow-output/apktool"), timeoutSeconds = 1200),
            WorkflowNode("jadx", WorkflowNodeType.BASH_COMMAND, "Dịch ngược mã nguồn", config = mapOf("command" to "jadx -d ./workflow-output/jadx \"${'$'}{APK_PATH}\""), timeoutSeconds = 1800),
            WorkflowNode("audit", WorkflowNodeType.AGENT_INFERENCE, "Kiểm toán chỉ đọc và báo cáo", config = mapOf("prompt" to "Kiểm toán chỉ đọc thư mục workflow-output/apktool và workflow-output/jadx. Trước tiên lập chỉ mục và tìm manifest, thành phần được export, cấu hình mạng và cách xử lý dữ liệu nhạy cảm, sau đó đọc mã nguồn liên quan. Không được sửa tệp hoặc chạy chương trình bị kiểm toán. Xuất báo cáo Markdown, liệt kê tệp và số dòng, bằng chứng, ảnh hưởng, khuyến nghị và các mục không thể xác minh; đừng viết phỏng đoán thành lỗ hổng đã xác minh."), timeoutSeconds = 1800),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Báo cáo kiểm toán"),
        ),
        edges = chain("start", "apktool", "jadx", "audit", "done"),
    )

    val atomicCommit = WorkflowDefinition(
        id = "git_atomic_smart_commit",
        name = "Rà soát và commit thay đổi",
        description = "Hiển thị thay đổi, chờ xác nhận thủ công rồi dùng thông điệp commit được truyền vào để tạo commit cục bộ; mặc định không push.",
        category = "Git",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf git_atomic_smart_commit"),
        defaultVariables = mapOf("COMMIT_MESSAGE" to "chore: update workspace"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu"),
            WorkflowNode("diff", WorkflowNodeType.BASH_COMMAND, "Rà soát thay đổi", config = mapOf("command" to "git status --short && git diff --stat && git diff && git diff --cached")),
            WorkflowNode("suggest", WorkflowNodeType.AGENT_INFERENCE, "Sinh gợi ý commit", config = mapOf("prompt" to "Rà soát chỉ đọc các thay đổi Git sau đây, xuất một gợi ý Conventional Commit và tóm tắt rủi ro ngắn gọn. Không được sửa, stage, commit hay push bất kỳ tệp nào. Thay đổi:\n${'$'}{diff.output}")),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận commit cục bộ", config = mapOf("requestedVariables" to "COMMIT_MESSAGE")),
            WorkflowNode("commit", WorkflowNodeType.BASH_COMMAND, "Tạo commit", config = mapOf("command" to "git add -A && git commit -F - <<< \"\${COMMIT_MESSAGE}\"")),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Commit hoàn tất"),
        ),
        edges = chain("start", "diff", "suggest", "approve", "commit", "done"),
    )

    val buildRepair = WorkflowDefinition(
        id = "assisted_build_repair",
        name = "Sửa lỗi build sau khi xác nhận",
        description = "Chẩn đoán nguyên nhân thất bại, sau khi xác nhận thì giao agent sửa ở mức tối thiểu, rồi hiển thị Diff và xác nhận build lại; không tự động commit hay push.",
        category = "Chẩn đoán",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf assisted_build_repair"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Nhập lỗi build", config = mapOf("requiredVariables" to "BUILD_ERROR")),
            WorkflowNode("diagnose", WorkflowNodeType.AGENT_INFERENCE, "Chẩn đoán chỉ đọc", config = mapOf("prompt" to "Kiểm tra chỉ đọc workspace hiện tại, dựa trên mô tả lỗi sau để nêu nguyên nhân gốc và kế hoạch sửa tối thiểu. Không được sửa tệp.\n${'$'}{BUILD_ERROR}"), timeoutSeconds = 900),
            WorkflowNode("approve", WorkflowNodeType.HUMAN_APPROVAL, "Cho phép sửa tệp", description = "Xác nhận cho phép agent sửa workspace hiện tại theo kết quả chẩn đoán. Hãy đảm bảo những thay đổi quan trọng đã được sao lưu trước."),
            WorkflowNode("repair", WorkflowNodeType.SUBAGENT_DELEGATE, "Thực hiện sửa tối thiểu", config = mapOf("writePaths" to ".", "prompt" to "Theo chẩn đoán sau, thực hiện bản sửa build tối thiểu và giữ nguyên các thay đổi sẵn có của người dùng; cấm xoá dự án, reset Git, commit hay push. Khi không chắc chắn thì dừng lại và giải thích. Chẩn đoán:\n${'$'}{diagnose.output}\nLỗi gốc:\n${'$'}{BUILD_ERROR}"), timeoutSeconds = 1800),
            WorkflowNode("diff", WorkflowNodeType.BASH_COMMAND, "Kiểm tra Diff sửa lỗi", config = mapOf("command" to "git status --short && git diff && git diff --cached")),
            WorkflowNode("rebuildApproval", WorkflowNodeType.HUMAN_APPROVAL, "Xác nhận build lại", description = "Rà soát bản sửa rồi khởi động build để xác minh. Từ chối sẽ không hoàn nguyên các thay đổi đã thực hiện."),
            WorkflowNode("build", WorkflowNodeType.TIANXUAN_BUILD, "Xác minh build", config = mapOf("task" to "assembleDebug"), timeoutSeconds = 3600),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Kết quả xác minh sửa lỗi"),
        ),
        edges = chain("start", "diagnose", "approve", "repair", "diff", "rebuildApproval", "build", "done"),
    )

    val hostAutomationLab = WorkflowDefinition(
        id = "host_automation_lab",
        name = "Phòng thí nghiệm tự động hoá Host",
        description = "Dò quyền → mở cài đặt → chờ foreground → cảm nhận màn hình → rẽ nhánh điều kiện → thông báo kết thúc. Cần Shizuku/Root mới chạy được toàn bộ chuỗi.",
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
                "Đã có quyền đặc biệt chưa?",
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
                "Cảm nhận thành phần màn hình",
                config = mapOf("action" to "screen_observe", "onlyInteractive" to "true"),
                canvasX = 1140f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "notify_ok",
                WorkflowNodeType.HOST_ACTION,
                "Thông báo: tự động hoá thành công",
                config = mapOf(
                    "action" to "notification",
                    "title" to "Workflow TianXuan",
                    "text" to "Phòng thí nghiệm tự động hoá Host đã hoàn tất cảm nhận màn hình",
                ),
                canvasX = 1360f,
                canvasY = 60f,
            ),
            WorkflowNode(
                "degraded",
                WorkflowNodeType.HOST_ACTION,
                "Hạ cấp: chỉ hiện Toast",
                config = mapOf(
                    "action" to "toast",
                    "text" to "Không phát hiện Shizuku/Root, đã bỏ qua bước tự động hoá đặc quyền",
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
        name = "Broadcast + Trình diễn cài đặt hệ thống",
        description = "Ghi biến → gửi broadcast tuỳ chỉnh → đọc/ghi lại các cài đặt liên quan độ sáng (cần quyền đặc biệt). Có xác nhận thủ công trước thao tác phá huỷ.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_broadcast_and_settings"),
        defaultVariables = mapOf("DEMO_FLAG" to "tianxuan-workflow"),
        nodes = listOf(
            WorkflowNode("start", WorkflowNodeType.TRIGGER, "Bắt đầu"),
            WorkflowNode(
                "vars",
                WorkflowNodeType.SET_VARIABLE,
                "Chuẩn bị biến trình diễn",
                config = mapOf(
                    "variables" to "BROADCAST_ACTION=top.wkbin.tianxuan.action.WORKFLOW_DEMO\nPAYLOAD=\${DEMO_FLAG}",
                ),
            ),
            WorkflowNode(
                "broadcast",
                WorkflowNodeType.HOST_ACTION,
                "Gửi broadcast trình diễn",
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
                "Xác nhận có điều chỉnh độ sáng",
                description = "Sẽ đặt độ sáng thành 120 (có thể bỏ qua nếu từ chối). Giá trị hiện tại xem ở đầu ra phía trước.",
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Người dùng có chấp thuận không?",
                config = mapOf("expression" to "exitCode == 0"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "write_brightness",
                WorkflowNodeType.HOST_ACTION,
                "Đặt độ sáng thành 120",
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
                "Chờ một chút",
                config = mapOf("seconds" to "1"),
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Trình diễn hoàn tất"),
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
        name = "Chạy thử GUI của agent",
        description = "Vòng lặp cục bộ dưới Shizuku/Root 「cảm nhận màn hình → model quyết định một bước → chạm/nhập」 để hoàn tất mục tiêu GUI. Mặc định: mở QQ → nhóm TianXuan → gửi nội dung thử nghiệm. Hãy theo dõi màn hình và phê duyệt trước khi chạy.",
        category = "Host",
        isBuiltin = true,
        trigger = WorkflowTrigger.Manual("/wf host_agent_gui_pilot"),
        defaultVariables = mapOf(
            "GUI_GOAL" to "Mở QQ, vào nhóm chat QQ liên quan đến「TianXuan」, gửi một câu vào ô nhập: 「TianXuan đỉnh thật (từ workflow)」. Không gửi lì xì, không chuyển tiền, không đổi cài đặt nhóm. Nếu không tìm thấy nhóm có tên chứa「TianXuan」, hãy dừng lại và mô tả danh sách hội thoại đang thấy.",
            "TARGET_PACKAGE" to "com.tencent.mobileqq",
        ),
        nodes = listOf(
            WorkflowNode(
                "start",
                WorkflowNodeType.TRIGGER,
                "Nhập mục tiêu",
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
                "Đã sẵn sàng quyền đặc biệt chưa?",
                config = mapOf("expression" to "\${HOST_PRIVILEGED} == true"),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "no_priv",
                WorkflowNodeType.HOST_ACTION,
                "Thông báo thiếu quyền đặc biệt",
                config = mapOf(
                    "action" to "toast",
                    "text" to "Hãy cấp quyền trong cài đặt và chuyển sang Shizuku hoặc Root trước, rồi chạy lại workflow này",
                ),
            ),
            WorkflowNode(
                "approve",
                WorkflowNodeType.HUMAN_APPROVAL,
                "Xác nhận cho agent điều khiển màn hình",
                description = "Agent sẽ dùng host(screen_observe/click/swipe/input_text/key/app_launch) để tự động thao tác giao diện điện thoại nhằm hoàn tất GUI_GOAL.\n\n" +
                    "Mục tiêu mặc định sẽ mở QQ, vào nhóm TianXuan và gửi 「TianXuan đỉnh thật (từ workflow)」. Hãy tự theo dõi màn hình; nếu giao diện nhảy sang thanh toán/lì xì, hãy từ chối hoặc dừng ngay.\n\n" +
                    "Từ chối thì kết thúc, sẽ không khởi động thao tác điều khiển.",
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode(
                "approve_gate",
                WorkflowNodeType.CONDITION_BRANCH,
                "Người dùng có chấp thuận không?",
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
                "Vòng lặp chạy thử GUI",
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
                "Thông báo kết thúc chạy thử",
                config = mapOf(
                    "action" to "notification",
                    "title" to "Kết thúc chạy thử GUI",
                    "text" to "Chạy thử GUI của agent đã kết thúc, hãy xem đầu ra của workflow",
                ),
                failurePolicy = FailurePolicy.CONTINUE,
            ),
            WorkflowNode("done", WorkflowNodeType.TERMINAL_OUTPUT, "Báo cáo chạy thử"),
            WorkflowNode("aborted", WorkflowNodeType.TERMINAL_OUTPUT, "Đã hủy hoặc thiếu quyền đặc biệt"),
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
