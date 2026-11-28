package top.wkbin.tianxuan.runtime.doctor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import top.wkbin.tianxuan.core.model.DoctorCategory
import top.wkbin.tianxuan.core.model.DoctorItem
import top.wkbin.tianxuan.core.model.DoctorReport
import top.wkbin.tianxuan.core.model.DoctorStatus
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.shell.CommandResult
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EnvironmentDoctor(
    private val context: Context? = null,
    private val linuxRuntime: LinuxRuntime,
) {
    suspend fun check(): DoctorReport = withContext(Dispatchers.IO) {
        // Kiểm tra quyền ở phía host: không phụ thuộc trạng thái sandbox, luôn hiển thị ở cả hai nhánh
        val allFilesAccessItem = checkAllFilesAccess()

        val state = linuxRuntime.state.value
        if (state !is RuntimeState.Ready) {
            val unreadyItem = DoctorItem(
                id = "sandbox_unready",
                category = DoctorCategory.SANDBOX,
                title = "Trạng thái sandbox PRoot",
                status = DoctorStatus.ERROR,
                summary = if (state is RuntimeState.Error) "Sandbox lỗi: ${state.throwable.message}" else "Sandbox chưa được khởi tạo",
                detail = "Vui lòng khởi tạo và khởi động môi trường sandbox Linux trong bảng điều khiển trước",
                fixable = false,
            )
            return@withContext DoctorReport(
                items = listOf(unreadyItem, allFilesAccessItem),
                timestamp = System.currentTimeMillis(),
                overallStatus = DoctorStatus.ERROR,
                healthyCount = itemsHealthy(listOf(unreadyItem, allFilesAccessItem)),
                warningCount = itemsWarning(listOf(unreadyItem, allFilesAccessItem)),
                errorCount = 1,
            )
        }

        val items = mutableListOf<DoctorItem>()

        // 1. Kiểm tra sandbox và bộ nhớ
        items.add(checkSandboxStorage())
        items.add(allFilesAccessItem)

        // 2. Kiểm tra DNS và kết nối mạng
        items.add(checkDnsAndNetwork())

        // 3. Kiểm tra chứng chỉ gốc CA
        items.add(checkCaCertificates())

        // 4. Kiểm tra nguồn gói APT và mirror tăng tốc
        items.add(checkAptMirrors())

        // 5. Kiểm tra bộ công cụ phát triển cơ bản (git/curl/tar/xz)
        items.add(checkBaseDevTools())

        // 6. Kiểm tra runtime Node.js
        items.add(checkNodeRuntime())

        // 7. Kiểm tra môi trường Android (plugin tùy chọn; nếu chưa cài thì hướng dẫn tải gói offline/online)
        items.add(checkAndroidEnvironment())

        val healthyCount = items.count { it.status == DoctorStatus.HEALTHY }
        val warningCount = items.count { it.status == DoctorStatus.WARNING }
        val errorCount = items.count { it.status == DoctorStatus.ERROR }
        val unknownCount = items.count { it.status == DoctorStatus.UNKNOWN }

        val overallStatus = when {
            errorCount > 0 -> DoctorStatus.ERROR
            warningCount > 0 -> DoctorStatus.WARNING
            unknownCount > 0 -> DoctorStatus.UNKNOWN
            else -> DoctorStatus.HEALTHY
        }

        DoctorReport(
            items = items,
            timestamp = System.currentTimeMillis(),
            overallStatus = overallStatus,
            healthyCount = healthyCount,
            warningCount = warningCount,
            errorCount = errorCount,
            unknownCount = unknownCount,
        )
    }

    /**
     * Điểm dò chung của sandbox: nếu thất bại thì thử lại với timeout gấp đôi.
     * Trả null nếu cả hai lần đều không có kết quả - không dò được ≠ cấu hình lỗi;
     * bên gọi phải báo đúng UNKNOWN (thẻ xám "Sandbox đang bận, tạm thời chưa xác nhận được"),
     * và tuyệt đối không báo nhầm timeout thành WARNING/ERROR cấu hình.
     */
    private suspend fun probe(commandLine: String, timeoutMs: Long): CommandResult? {
        val first = runCatching {
            linuxRuntime.execute(ShellCommand(commandLine, timeoutMs = timeoutMs))
        }.getOrNull()
        if (first != null) return first
        return runCatching {
            linuxRuntime.execute(ShellCommand(commandLine, timeoutMs = timeoutMs * 2))
        }.getOrNull()
    }

    /** Mục thẻ xám "Sandbox đang bận, tạm thời chưa xác nhận được": không tính vào mục cần sửa. */
    private fun unknownItem(
        id: String,
        category: DoctorCategory,
        title: String,
        detail: String? = null,
    ) = DoctorItem(
        id = id,
        category = category,
        title = title,
        status = DoctorStatus.UNKNOWN,
        summary = "Sandbox đang bận, tạm thời chưa xác nhận được",
        detail = detail ?: "Sandbox đang xử lý tác vụ khác nên dò quá thời gian; kiểm tra lại sau sẽ phục hồi",
        fixable = false,
    )

    private fun checkAllFilesAccess(): DoctorItem {
        val granted = if (context == null) {
            true
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
        return if (granted) {
            DoctorItem(
                id = "host_all_files_access",
                category = DoctorCategory.SANDBOX,
                title = "Quyền truy cập tất cả tệp",
                status = DoctorStatus.HEALTHY,
                summary = "Đã cấp quyền truy cập tất cả tệp; trình duyệt tệp truy cập đầy đủ bộ nhớ dùng chung",
            )
        } else {
            DoctorItem(
                id = "host_all_files_access",
                category = DoctorCategory.SANDBOX,
                title = "Quyền truy cập tất cả tệp",
                status = DoctorStatus.WARNING,
                summary = "Chưa cấp quyền truy cập tất cả tệp",
                detail = "Android lọc các tệp của ứng dụng khác trong bộ nhớ dùng chung; mục /sdcard của trình duyệt tệp có thể chỉ hiện thư mục mà không hiện tệp. Nhấn Cấp quyền bên phải để mở cài đặt hệ thống.",
                fixable = true,
            )
        }
    }

    private fun itemsHealthy(items: List<DoctorItem>): Int = items.count { it.status == DoctorStatus.HEALTHY }

    private fun itemsWarning(items: List<DoctorItem>): Int = items.count { it.status == DoctorStatus.WARNING }

    private suspend fun checkSandboxStorage(): DoctorItem {
        val res = probe(
            commandLine = "mkdir -p /workspace /tmp && touch /workspace/.doctor_probe && rm -f /workspace/.doctor_probe",
            timeoutMs = 5000L,
        )

        return when {
            res == null -> unknownItem(
                id = "sandbox_storage",
                category = DoctorCategory.SANDBOX,
                title = "Sandbox PRoot và workspace",
                detail = "Dò đọc/ghi quá thời gian; sandbox có thể đang bận (ví dụ đang build/cài đặt)",
            )
            res.isSuccess -> DoctorItem(
                id = "sandbox_storage",
                category = DoctorCategory.SANDBOX,
                title = "Sandbox PRoot và workspace",
                status = DoctorStatus.HEALTHY,
                summary = "Môi trường ảo sandbox bình thường, /workspace và /tmp sẵn sàng đọc/ghi",
            )
            else -> DoctorItem(
                id = "sandbox_storage",
                category = DoctorCategory.SANDBOX,
                title = "Sandbox PRoot và workspace",
                status = DoctorStatus.ERROR,
                summary = "Kiểm tra đọc/ghi hoặc quyền của workspace thất bại",
                detail = res.stderr.ifBlank { res.stdout }.ifBlank { "Lệnh dò thực thi thất bại" },
            )
        }
    }

    private suspend fun checkDnsAndNetwork(): DoctorItem {
        val resolvCheck = probe("cat /etc/resolv.conf 2>/dev/null", timeoutMs = 3000L)

        // Không dò được (sandbox bận/timeout): chưa chắc thiếu DNS, báo đúng thẻ xám
        if (resolvCheck == null) {
            return unknownItem(
                id = "network_dns",
                category = DoctorCategory.NETWORK_SSL,
                title = "Phân giải tên miền DNS",
            )
        }

        val resolvContent = resolvCheck.stdout
        val hasNameserver = resolvContent.contains("nameserver", ignoreCase = true)

        if (!hasNameserver) {
            return DoctorItem(
                id = "network_dns",
                category = DoctorCategory.NETWORK_SSL,
                title = "Phân giải tên miền DNS",
                status = DoctorStatus.WARNING,
                summary = "Chưa cấu hình máy chủ phân giải DNS hợp lệ",
                detail = "/etc/resolv.conf trống hoặc thiếu, có thể không phân giải được tên miền tải gói",
            )
        }

        return DoctorItem(
            id = "network_dns",
            category = DoctorCategory.NETWORK_SSL,
            title = "Phân giải tên miền DNS",
            status = DoctorStatus.HEALTHY,
            summary = "Cấu hình phân giải DNS bình thường",
            detail = resolvContent.lineSequence().filter { it.startsWith("nameserver") }.take(2).joinToString(", "),
        )
    }

    private suspend fun checkCaCertificates(): DoctorItem {
        val certCheck = probe(
            commandLine = "test -f /etc/ssl/certs/ca-certificates.crt || test -d /etc/ssl/certs",
            timeoutMs = 3000L,
        )

        return when {
            // Không dò được: không coi là thiếu chứng chỉ
            certCheck == null -> unknownItem(
                id = "ca_certificates",
                category = DoctorCategory.NETWORK_SSL,
                title = "Chứng chỉ gốc SSL (CA)",
            )
            certCheck.isSuccess -> DoctorItem(
                id = "ca_certificates",
                category = DoctorCategory.NETWORK_SSL,
                title = "Chứng chỉ gốc SSL (CA)",
                status = DoctorStatus.HEALTHY,
                summary = "Chứng chỉ gốc CA sẵn sàng, hỗ trợ tải dependency qua HTTPS",
            )
            else -> DoctorItem(
                id = "ca_certificates",
                category = DoctorCategory.NETWORK_SSL,
                title = "Chứng chỉ gốc SSL (CA)",
                status = DoctorStatus.WARNING,
                summary = "Hệ thống thiếu chứng chỉ gốc CA",
                detail = "Có thể gặp lỗi xác thực SSL khi tải tài nguyên HTTPS hoặc git clone",
            )
        }
    }

    private suspend fun checkAptMirrors(): DoctorItem {
        val sourcesCheck = probe(
            commandLine = "cat /etc/apt/sources.list /etc/apt/sources.list.d/*.sources /etc/apt/sources.list.d/*.list 2>/dev/null || true",
            timeoutMs = 4000L,
        )

        // Không dò được: không báo nhầm nguồn mặc định chính thức do timeout (sandbox bận nên cat không có output)
        if (sourcesCheck == null) {
            return unknownItem(
                id = "apt_mirrors",
                category = DoctorCategory.PACKAGE_MANAGER,
                title = "Nguồn gói APT",
            )
        }

        val content = sourcesCheck.stdout
        // Lệnh có || true; stdout rỗng nghĩa là không đọc được cấu hình nguồn (không phải nguồn chính thức) - không đủ dữ liệu, báo thẻ xám
        if (content.isBlank()) {
            return unknownItem(
                id = "apt_mirrors",
                category = DoctorCategory.PACKAGE_MANAGER,
                title = "Nguồn gói APT",
                detail = "Không đọc được nội dung cấu hình nguồn APT, chưa xác định được trạng thái mirror; sandbox bận hoặc danh sách nguồn bất thường, kiểm tra lại sau",
            )
        }

        val hasInvalidUbuntuMirror = (content.contains("/ubuntu ") || content.contains("/ubuntu/")) &&
            !content.contains("ubuntu-ports")

        if (hasInvalidUbuntuMirror) {
            return DoctorItem(
                id = "apt_mirrors",
                category = DoctorCategory.PACKAGE_MANAGER,
                title = "Nguồn gói APT",
                status = DoctorStatus.WARNING,
                summary = "Cấu hình nguồn APT sai (Ubuntu ARM64 phải dùng nguồn ubuntu-ports)",
                detail = "Phát hiện đường dẫn mirror x86 trên kiến trúc ARM64, sẽ gây lỗi 404 khi tải gói. Nhấn sửa tự động để hiệu chỉnh.",
            )
        }

        val hasDomesticMirror = content.contains("tsinghua.edu.cn", ignoreCase = true) ||
            content.contains("aliyun.com", ignoreCase = true) ||
            content.contains("ustc.edu.cn", ignoreCase = true) ||
            content.contains("tencent.com", ignoreCase = true) ||
            content.contains("163.com", ignoreCase = true)

        return if (hasDomesticMirror) {
            DoctorItem(
                id = "apt_mirrors",
                category = DoctorCategory.PACKAGE_MANAGER,
                title = "Nguồn gói APT",
                status = DoctorStatus.HEALTHY,
                summary = "Đã cấu hình mirror trong nước để tăng tốc (Tsinghua / Aliyun / USTC)",
            )
        } else {
            DoctorItem(
                id = "apt_mirrors",
                category = DoctorCategory.PACKAGE_MANAGER,
                title = "Nguồn gói APT",
                status = DoctorStatus.WARNING,
                summary = "Đang dùng nguồn mặc định chính thức, cài đặt trong nước có thể chậm hoặc timeout",
                detail = "Nên chuyển sang mirror trong nước để tải dependency nhanh và ổn định hơn",
            )
        }
    }

    private suspend fun checkBaseDevTools(): DoctorItem {
        val toolsCheck = probe(
            commandLine = "for t in curl git tar xz file; do which \$t >/dev/null 2>&1 || echo \$t; done",
            timeoutMs = 4000L,
        )

        // Không dò được: không coi là thiếu công cụ
        if (toolsCheck == null) {
            return unknownItem(
                id = "base_devtools",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Bộ công cụ cơ bản cốt lõi",
            )
        }

        val missingTools = toolsCheck.stdout.lines().map { it.trim() }.filter { it.isNotBlank() }

        return if (missingTools.isEmpty()) {
            DoctorItem(
                id = "base_devtools",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Bộ công cụ cơ bản cốt lõi",
                status = DoctorStatus.HEALTHY,
                summary = "Các công cụ thông dụng Git, Curl, Tar, XZ, File đã sẵn sàng",
            )
        } else {
            DoctorItem(
                id = "base_devtools",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Bộ công cụ cơ bản cốt lõi",
                status = DoctorStatus.WARNING,
                summary = "Thiếu công cụ cơ bản: ${missingTools.joinToString(", ")}",
                detail = "Một số script cài đặt hoặc plugin phụ thuộc các công cụ này để giải nén và lấy mã nguồn",
            )
        }
    }

    /**
     * Dùng lại đúng lệnh dò của thành phần android-core trong trung tâm plugin để kết luận của lần kiểm tra khớp với trạng thái khi cài plugin.
     */
    private suspend fun checkAndroidEnvironment(): DoctorItem {
        val checkCommand = top.wkbin.tianxuan.core.model.BuiltinPluginBundles.bundles
            .firstOrNull { it.id == "android-suite" }
            ?.components?.firstOrNull { it.id == "android-core" }
            ?.checkCommand

        val installed: Boolean?
        if (checkCommand.isNullOrBlank()) {
            installed = null
        } else {
            val res = probe(
                commandLine = checkCommand,
                timeoutMs = 8000L,
            )
            // Khi không dò được thì đặt null: không coi là chưa cài đặt môi trường
            installed = res?.isSuccess
        }

        val notInstalledItem = DoctorItem(
            id = "android_environment",
            category = DoctorCategory.DEV_RUNTIMES,
            title = "Môi trường phát triển Android",
            status = DoctorStatus.WARNING,
            summary = "Chưa cài đặt môi trường Android / Flutter / dịch ngược",
            detail = "Có thể tham gia nhóm QQ chính thức để tải gói plugin offline đầy đủ (đã tích hợp Android, Flutter và dịch ngược, không cần cài online), hoặc cài trực tuyến trong trung tâm plugin.",
            fixable = false,
        )

        return when {
            // Bundle không định nghĩa lệnh dò: hướng dẫn như chưa cài (giữ hành vi cũ)
            checkCommand.isNullOrBlank() -> notInstalledItem
            installed == true -> DoctorItem(
                id = "android_environment",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Môi trường phát triển Android",
                status = DoctorStatus.HEALTHY,
                summary = "JDK 17 / Android SDK / Gradle / NDK đã sẵn sàng, có thể build và dịch ngược APK",
            )
            // Đã chạy lệnh dò nhưng không truy cập được sandbox: thẻ xám, không báo nhầm chưa cài
            installed == null -> unknownItem(
                id = "android_environment",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Môi trường phát triển Android",
                detail = "Dò môi trường Android quá thời gian; sandbox có thể đang bận (ví dụ đang build), kiểm tra lại sau để xác nhận",
            )
            else -> notInstalledItem
        }
    }

    private suspend fun checkNodeRuntime(): DoctorItem {
        val nodeCheck = probe(
            commandLine = "node --version 2>/dev/null || /opt/tianxuan/bin/node --version 2>/dev/null || /usr/bin/node --version 2>/dev/null",
            timeoutMs = 4000L,
        )

        // Không dò được: không coi là chưa cài Node
        if (nodeCheck == null) {
            return unknownItem(
                id = "node_runtime",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Runtime Node.js",
            )
        }

        val rawVersion = nodeCheck.stdout.trim()
        if (rawVersion.isBlank()) {
            return DoctorItem(
                id = "node_runtime",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Runtime Node.js",
                status = DoctorStatus.WARNING,
                summary = "Không phát hiện runtime Node.js",
                detail = "Các công cụ agent AI như OpenClaw, Claude Code phụ thuộc mạnh vào Node.js (khuyến nghị >= v20)",
            )
        }

        val majorVersion = Regex("v?(\\d+)").find(rawVersion)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
        return if (majorVersion >= 20) {
            DoctorItem(
                id = "node_runtime",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Runtime Node.js",
                status = DoctorStatus.HEALTHY,
                summary = "Node.js $rawVersion (đáp ứng yêu cầu của các công cụ AI phổ biến)",
            )
        } else {
            DoctorItem(
                id = "node_runtime",
                category = DoctorCategory.DEV_RUNTIMES,
                title = "Runtime Node.js",
                status = DoctorStatus.WARNING,
                summary = "Node.js $rawVersion phiên bản cũ (nên dùng >= v20)",
                detail = "Các công cụ AI phiên bản mới có thể cần tính năng JavaScript/V8 mới hơn",
            )
        }
    }
}
