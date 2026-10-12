package top.wkbin.tianxuan.runtime.doctor

import top.wkbin.tianxuan.core.model.RepairProgress
import top.wkbin.tianxuan.core.model.RuntimeState
import top.wkbin.tianxuan.runtime.LinuxRuntime
import top.wkbin.tianxuan.runtime.shell.CommandResult
import top.wkbin.tianxuan.runtime.shell.ShellCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class EnvironmentRepairer(
    private val linuxRuntime: LinuxRuntime,
    private val environmentDoctor: EnvironmentDoctor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _progress = MutableStateFlow<RepairProgress?>(null)
    val progress: StateFlow<RepairProgress?> = _progress.asStateFlow()
    private val _isRepairing = MutableStateFlow(false)
    val isRepairing: StateFlow<Boolean> = _isRepairing.asStateFlow()
    private var currentRepairJob: Job? = null

    fun startRepair(): Job {
        val existing = currentRepairJob
        if (existing?.isActive == true) return existing
        val job = scope.launch {
            _isRepairing.value = true
            try {
                repair().collect { p ->
                    _progress.value = p
                }
            } finally {
                _isRepairing.value = false
            }
        }
        currentRepairJob = job
        return job
    }

    fun cancelRepair() {
        currentRepairJob?.cancel()
        _isRepairing.value = false
        _progress.value = null
    }

    fun repair(): Flow<RepairProgress> = flow {
        val totalSteps = 5
        val logs = mutableListOf<String>()

        fun addLog(message: String) {
            logs.add(message)
            if (logs.size > 200) {
                logs.removeAt(0)
            }
        }

        if (linuxRuntime.state.value !is RuntimeState.Ready) {
            emit(
                RepairProgress(
                    stepTitle = "Sandbox Linux chưa sẵn sàng",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = listOf("Lỗi: Sandbox Linux chưa khởi tạo hoặc đang ở trạng thái bất thường, không thể thực thi tự chữa lành"),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "Sandbox Linux chưa sẵn sàng, vui lòng khởi tạo sandbox trước",
                ),
            )
            return@flow
        }

        try {
            // ==========================================
            // Step 1: 修复 DNS 与证书
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cấu hình DNS cao khả dụng và chứng chỉ gốc SSL...",
                    stepIndex = 1,
                    totalSteps = totalSteps,
                    progress = 0.15f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 1/5] Ghi DNS công cộng (114.114.114.114, 223.5.5.5, 8.8.8.8)")
            val dnsCmd = "mkdir -p /etc && printf 'nameserver 114.114.114.114\\nnameserver 223.5.5.5\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf"
            val dnsRes = executeCommand(dnsCmd, logs)
            if (!dnsRes.isSuccess) {
                addLog("Cảnh báo: ghi /etc/resolv.conf thất bại: ${dnsRes.stderr}")
            }

            // ==========================================
            // Step 2: 清理残留锁并切换国内镜像加速源
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cấu hình nguồn mirror tăng tốc trong nước của Đại học Thanh Hoa...",
                    stepIndex = 2,
                    totalSteps = totalSteps,
                    progress = 0.35f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 2/5] Dọn khóa giao dịch dpkg/apt và thay nguồn trong nước")
            val mirrorScript = """
                rm -rf /var/lib/dpkg/updates/* /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true
                DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true
                mkdir -p /etc/apt/sources.list.d
                # 停用所有自带官方源与旧源，避免与新源冲突或引发 404
                for f in /etc/apt/sources.list /etc/apt/sources.list.d/*.list /etc/apt/sources.list.d/*.sources; do
                    if [ -f "${'$'}f" ] && [ "${'$'}(basename "${'$'}f")" != "tianxuan-mirrors.list" ]; then
                        mv "${'$'}f" "${'$'}f.tianxuan-disabled" 2>/dev/null || true
                    fi
                done
                if [ -f /etc/os-release ]; then
                    . /etc/os-release
                    if [ "${'$'}ID" = "ubuntu" ]; then
                        CN="${'$'}{VERSION_CODENAME:-noble}"
                        printf "deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s main restricted universe multiverse\ndeb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s-updates main restricted universe multiverse\ndeb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s-security main restricted universe multiverse\ndeb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports %s-backports main restricted universe multiverse\n" "${'$'}CN" "${'$'}CN" "${'$'}CN" "${'$'}CN" > /etc/apt/sources.list.d/tianxuan-mirrors.list
                    elif [ "${'$'}ID" = "debian" ] || [ "${'$'}ID_LIKE" = "debian" ]; then
                        CN="${'$'}{VERSION_CODENAME:-bookworm}"
                        printf "deb https://mirrors.tuna.tsinghua.edu.cn/debian %s main contrib non-free non-free-firmware\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian %s-updates main contrib non-free non-free-firmware\ndeb https://mirrors.tuna.tsinghua.edu.cn/debian-security %s-security main contrib non-free non-free-firmware\n" "${'$'}CN" "${'$'}CN" "${'$'}CN" > /etc/apt/sources.list.d/tianxuan-mirrors.list
                    elif [ "${'$'}ID" = "kali" ]; then
                        printf "deb https://mirrors.tuna.tsinghua.edu.cn/kali kali-rolling main contrib non-free\n" > /etc/apt/sources.list.d/tianxuan-mirrors.list
                    fi
                fi
                # 清理旧的 apt lists 缓存，确保重新从镜像拉取完整的 index
                rm -rf /var/lib/apt/lists/* 2>/dev/null || true
            """.trimIndent()
            executeCommand(mirrorScript, logs)

            // ==========================================
            // Step 3: 更新 APT 软件包索引
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cập nhật chỉ mục gói phần mềm APT...",
                    stepIndex = 3,
                    totalSteps = totalSteps,
                    progress = 0.55f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 3/5] Chạy apt-get update làm mới chỉ mục")
            val updateRes = executeCommand("rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive apt-get update -y", logs, timeoutMs = 120_000L)
            if (!updateRes.isSuccess) {
                addLog("Gợi ý: apt-get update tạo ra một số cảnh báo không nghiêm trọng")
            }

            // ==========================================
            // Step 4: 安装核心工具链 (curl, git, tar, xz-utils)
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cài đặt chuỗi công cụ cốt lõi (Git / Curl / Tar / XZ)...",
                    stepIndex = 4,
                    totalSteps = totalSteps,
                    progress = 0.75f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 4/5] Cài đặt ca-certificates, curl, git, tar, xz-utils, procps, file")
            val installToolsCmd = "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl git tar xz-utils procps file"
            val installToolsRes = executeCommand(installToolsCmd, logs, timeoutMs = 180_000L)
            if (!installToolsRes.isSuccess) {
                addLog("Cảnh báo: cài đặt toolchain cơ bản gặp bất thường: ${installToolsRes.stderr.ifBlank { installToolsRes.stdout }}")
            }

            // ==========================================
            // Step 5: 配置并准备/升级 Node.js 与国内包镜像
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang sẵn sàng/nâng cấp runtime Node.js và mirror nội địa NPM/PIP...",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 0.90f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Step 5/5] Kiểm tra và tự động nâng cấp Node.js (>= v20 LTS) và cấu hình mirror tăng tốc")
            val setupNodeAndMirrors = """
                NODE_VER=${'$'}(node -v 2>/dev/null | tr -d 'v' | cut -d. -f1)
                if [ -z "${'$'}NODE_VER" ] || [ "${'$'}NODE_VER" -lt 20 ]; then
                    echo "Phát hiện thiếu Node.js hoặc phiên bản thấp (hiện tại: ${'$'}{NODE_VER:-chưa cài}), đang tải và cài Node.js v22 (LTS ARM64)..."
                    mkdir -p /tmp/node_setup
                    curl -fsSL --connect-timeout 10 --max-time 180 https://npmmirror.com/mirrors/node/v22.14.0/node-v22.14.0-linux-arm64.tar.xz -o /tmp/node_setup/node.tar.xz || \
                    curl -fsSL --connect-timeout 10 --max-time 180 https://nodejs.org/dist/v22.14.0/node-v22.14.0-linux-arm64.tar.xz -o /tmp/node_setup/node.tar.xz || true
                    if [ -f /tmp/node_setup/node.tar.xz ]; then
                        tar -xJf /tmp/node_setup/node.tar.xz -C /usr/local --strip-components=1
                        rm -rf /tmp/node_setup
                        echo "Nâng cấp Node.js v22 hoàn tất: ${'$'}(node -v 2>/dev/null)"
                    else
                        echo "Lấy gói biên dịch sẵn bị hạn chế, thử sẵn sàng Node cơ bản qua trình quản lý gói hệ thống..."
                        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends nodejs npm || true
                    fi
                fi
                which npm >/dev/null 2>&1 && npm config set registry https://registry.npmmirror.com || true
                mkdir -p ${'$'}HOME/.pip
                printf '[global]\nindex-url = https://pypi.tuna.tsinghua.edu.cn/simple\n' > ${'$'}HOME/.pip/pip.conf 2>/dev/null || true
                echo "Trạng thái sẵn sàng runtime: Node ${'$'}(node -v 2>/dev/null || echo 'chưa sẵn sàng'), NPM ${'$'}(npm -v 2>/dev/null || echo 'chưa sẵn sàng')"
                if [ -d /opt/android-sdk ] || [ -d /opt/tianxuan/toolchains/android ]; then
                    if [ ! -f /etc/profile.d/tianxuan-android.sh ]; then
                        mkdir -p /etc/profile.d
                        cat << 'EOF' > /etc/profile.d/tianxuan-android.sh
# TianXuan Android development environment (self-healed by EnvironmentRepairer)
export JAVA_HOME="${'$'}{JAVA_HOME:-/opt/tianxuan/toolchains/android/jdk}"
export ANDROID_HOME="/opt/android-sdk"
export ANDROID_SDK_ROOT="/opt/android-sdk"
export GRADLE_HOME="/opt/gradle-8.14.2"
export TIANXUAN_AAPT2_PATH="/opt/android-sdk/build-tools/35.0.0/aapt2"
export TIANXUAN_NDK_PATH="/opt/tianxuan/toolchains/android/ndk"
export TIANXUAN_NDK_VERSION="r29"
export ANDROID_NDK_HOME="/opt/tianxuan/toolchains/android/ndk"
export ANDROID_NDK_ROOT="/opt/tianxuan/toolchains/android/ndk"
export TIANXUAN_CMAKE_HOME="/opt/tianxuan/tools/android-suite-offline/cmake"
export TIANXUAN_NINJA_HOME="/opt/tianxuan/tools/android-suite-offline/bin"
export PATH="/opt/tianxuan/bin:/opt/tianxuan/tools/android-suite-offline/bin:/opt/tianxuan/tools/android-suite-offline/cmake/bin:${'$'}JAVA_HOME/bin:${'$'}GRADLE_HOME/bin:/opt/flutter/bin:${'$'}PATH"
export _JAVA_OPTIONS="-Djava.security.egd=file:/dev/urandom"
EOF
                        chmod 644 /etc/profile.d/tianxuan-android.sh 2>/dev/null || true
                    fi
                    if [ -f /root/.bashrc ] && ! grep -q "tianxuan-android" /root/.bashrc 2>/dev/null; then
                        echo '. /etc/profile.d/tianxuan-android.sh 2>/dev/null || true' >> /root/.bashrc
                    fi
                fi
            """.trimIndent()
            executeCommand(setupNodeAndMirrors, logs, timeoutMs = 240_000L)

            // 触发重新体检
            addLog("Tự chữa lành đã thực thi hoàn tất toàn bộ, đang làm mới báo cáo kiểm tra môi trường...")
            environmentDoctor.check()

            emit(
                RepairProgress(
                    stepTitle = "Hoàn tất tự chữa và cấu hình tăng tốc môi trường!",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 1.0f,
                    logs = logs.toList(),
                    isCompleted = true,
                    isFailed = false,
                ),
            )
        } catch (cancellation: CancellationException) {
            addLog("Quy trình sửa chữa bị người dùng hủy")
            emit(
                RepairProgress(
                    stepTitle = "Đã hủy sửa chữa",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "Nhiệm vụ sửa chữa đã bị hủy thủ công",
                ),
            )
            throw cancellation
        } catch (throwable: Throwable) {
            addLog("Ngoại lệ sửa chữa: ${throwable.message}")
            emit(
                RepairProgress(
                    stepTitle = "Tự chữa lành thất bại",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = throwable.message ?: "Tự khắc phục môi trường gặp ngoại lệ không xác định",
                ),
            )
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun executeCommand(
        command: String,
        logs: MutableList<String>,
        timeoutMs: Long = 60_000L,
    ): CommandResult {
        val result = runCatching {
            linuxRuntime.execute(ShellCommand(commandLine = command, timeoutMs = timeoutMs))
        }.getOrElse {
            CommandResult(
                exitCode = 1,
                stdout = "",
                stderr = it.message ?: "Thực thi lệnh gặp lỗi",
                durationMs = 0L,
            )
        }

        result.stdout.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(50)
            .forEach { logs.add(it) }

        if (!result.isSuccess) {
            result.stderr.lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .take(30)
                .forEach { logs.add("ERR: $it") }
        }

        return result
    }
}
