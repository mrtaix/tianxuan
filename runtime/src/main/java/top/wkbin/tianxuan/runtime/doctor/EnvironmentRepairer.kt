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
                    logs = listOf("Lỗi: Sandbox Linux chưa khởi tạo hoặc đang bất thường, không thể tự phục hồi"),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "Sandbox Linux chưa sẵn sàng, vui lòng khởi tạo trước",
                ),
            )
            return@flow
        }

        try {
            // ==========================================
            // Bước 1: Sửa DNS và chứng chỉ
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cấu hình DNS dự phòng và chứng chỉ gốc SSL...",
                    stepIndex = 1,
                    totalSteps = totalSteps,
                    progress = 0.15f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Bước 1/5] Ghi DNS công cộng (114.114.114.114, 223.5.5.5, 8.8.8.8)")
            val dnsCmd = "mkdir -p /etc && printf 'nameserver 114.114.114.114\\nnameserver 223.5.5.5\\nnameserver 8.8.8.8\\n' > /etc/resolv.conf"
            val dnsRes = executeCommand(dnsCmd, logs)
            if (!dnsRes.isSuccess) {
                addLog("Cảnh báo: ghi /etc/resolv.conf thất bại: ${dnsRes.stderr}")
            }

            // ==========================================
            // Bước 2: Dọn khóa tồn dư và chuyển sang mirror trong nước
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cấu hình mirror tăng tốc Tsinghua...",
                    stepIndex = 2,
                    totalSteps = totalSteps,
                    progress = 0.35f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Bước 2/5] Dọn khóa giao dịch dpkg/apt và thay bằng nguồn trong nước")
            val mirrorScript = """
                rm -rf /var/lib/dpkg/updates/* /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* 2>/dev/null || true
                DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true
                mkdir -p /etc/apt/sources.list.d
                # Vô hiệu hóa mọi nguồn chính thức và nguồn cũ để tránh xung đột hoặc lỗi 404
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
                # Dọn bộ đệm apt lists cũ để bảo đảm lấy lại chỉ số đầy đủ từ mirror
                rm -rf /var/lib/apt/lists/* 2>/dev/null || true
            """.trimIndent()
            executeCommand(mirrorScript, logs)

            // ==========================================
            // Bước 3: Cập nhật chỉ số gói APT
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cập nhật chỉ số gói APT...",
                    stepIndex = 3,
                    totalSteps = totalSteps,
                    progress = 0.55f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Bước 3/5] Chạy apt-get update để làm mới chỉ số")
            val updateRes = executeCommand("rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true; DEBIAN_FRONTEND=noninteractive apt-get update -y", logs, timeoutMs = 120_000L)
            if (!updateRes.isSuccess) {
                addLog("Ghi chú: apt-get update có một số cảnh báo không nghiêm trọng")
            }

            // ==========================================
            // Bước 4: Cài bộ công cụ cốt lõi (curl, git, tar, xz-utils)
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang cài bộ công cụ cốt lõi (Git / Curl / Tar / XZ)...",
                    stepIndex = 4,
                    totalSteps = totalSteps,
                    progress = 0.75f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Bước 4/5] Cài ca-certificates, curl, git, tar, xz-utils, procps, file")
            val installToolsCmd = "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends ca-certificates curl git tar xz-utils procps file"
            val installToolsRes = executeCommand(installToolsCmd, logs, timeoutMs = 180_000L)
            if (!installToolsRes.isSuccess) {
                addLog("Cảnh báo: cài bộ công cụ cơ bản bất thường: ${installToolsRes.stderr.ifBlank { installToolsRes.stdout }}")
            }

            // ==========================================
            // Bước 5: Cấu hình/chuẩn bị/nâng cấp Node.js và mirror gói trong nước
            // ==========================================
            emit(
                RepairProgress(
                    stepTitle = "Đang chuẩn bị/nâng cấp runtime Node.js và mirror NPM/PIP trong nước...",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 0.90f,
                    logs = logs.toList(),
                ),
            )
            addLog("[Bước 5/5] Kiểm tra và tự nâng cấp Node.js (>= v20 LTS), cấu hình mirror tăng tốc")
            val setupNodeAndMirrors = """
                NODE_VER=${'$'}(node -v 2>/dev/null | tr -d 'v' | cut -d. -f1)
                if [ -z "${'$'}NODE_VER" ] || [ "${'$'}NODE_VER" -lt 20 ]; then
                    echo "Phát hiện Node.js thiếu hoặc phiên bản cũ (hiện tại: ${'$'}{NODE_VER:-chưa cài}), đang tải và cài Node.js v22 (LTS ARM64)..."
                    mkdir -p /tmp/node_setup
                    curl -fsSL --connect-timeout 10 --max-time 180 https://npmmirror.com/mirrors/node/v22.14.0/node-v22.14.0-linux-arm64.tar.xz -o /tmp/node_setup/node.tar.xz || \
                    curl -fsSL --connect-timeout 10 --max-time 180 https://nodejs.org/dist/v22.14.0/node-v22.14.0-linux-arm64.tar.xz -o /tmp/node_setup/node.tar.xz || true
                    if [ -f /tmp/node_setup/node.tar.xz ]; then
                        tar -xJf /tmp/node_setup/node.tar.xz -C /usr/local --strip-components=1
                        rm -rf /tmp/node_setup
                        echo "Nâng cấp Node.js v22 hoàn tất: ${'$'}(node -v 2>/dev/null)"
                    else
                        echo "Không tải được gói biên dịch sẵn, thử chuẩn bị Node cơ bản qua trình quản lý gói..."
                        DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends nodejs npm || true
                    fi
                fi
                which npm >/dev/null 2>&1 && npm config set registry https://registry.npmmirror.com || true
                mkdir -p ${'$'}HOME/.pip
                printf '[global]\nindex-url = https://pypi.tuna.tsinghua.edu.cn/simple\n' > ${'$'}HOME/.pip/pip.conf 2>/dev/null || true
                echo "Trạng thái sẵn sàng: Node ${'$'}(node -v 2>/dev/null || echo 'chưa sẵn sàng'), NPM ${'$'}(npm -v 2>/dev/null || echo 'chưa sẵn sàng')"
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

            // Kích hoạt kiểm tra lại môi trường
            addLog("Đã hoàn tất tự phục hồi, đang làm mới báo cáo kiểm tra môi trường...")
            environmentDoctor.check()

            emit(
                RepairProgress(
                    stepTitle = "Tự phục hồi và cấu hình tăng tốc hoàn tất!",
                    stepIndex = 5,
                    totalSteps = totalSteps,
                    progress = 1.0f,
                    logs = logs.toList(),
                    isCompleted = true,
                    isFailed = false,
                ),
            )
        } catch (cancellation: CancellationException) {
            addLog("Người dùng đã hủy quy trình sửa chữa")
            emit(
                RepairProgress(
                    stepTitle = "Đã hủy sửa chữa",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = "Tác vụ sửa chữa đã bị hủy bởi người dùng",
                ),
            )
            throw cancellation
        } catch (throwable: Throwable) {
            addLog("Lỗi khi sửa chữa: ${throwable.message}")
            emit(
                RepairProgress(
                    stepTitle = "Tự phục hồi thất bại",
                    stepIndex = 0,
                    totalSteps = totalSteps,
                    progress = 0.0f,
                    logs = logs.toList(),
                    isCompleted = false,
                    isFailed = true,
                    errorMessage = throwable.message ?: "Đã xảy ra lỗi không xác định trong quá trình tự phục hồi",
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
                stderr = it.message ?: "Thực thi lệnh thất bại",
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
