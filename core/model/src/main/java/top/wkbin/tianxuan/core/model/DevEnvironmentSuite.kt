package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

/**
 * 🧩 插件子组件定义 (Plugin Sub-Component)
 * 隶属于某个聚合插件大套件下的原子能力组件。
 */
@Serializable
data class PluginComponent(
    val id: String,
    val name: String,
    val description: String,
    val isRequired: Boolean = false, // 是否为必选核心组件（不可取消勾选）
    val aptPackages: List<String> = emptyList(),
    val postInstallSteps: List<String> = emptyList(),
    val checkCommand: String, // 状态探针命令，返回 0 表示已就绪
)

/**
 * 📦 聚合插件大套件定义 (Plugin Bundle)
 * 将领域相关的能力（基础环境、扩展工具、逆向调试等）深度聚合成单一插件大类。
 */
@Serializable
data class PluginBundle(
    val id: String,
    val name: String,
    val summary: String,
    val description: String,
    val iconName: String = "Code",
    val category: String = "Bộ công cụ phát triển",
    val components: List<PluginComponent> = emptyList(),
)

object BuiltinPluginBundles {
    /** 基础核心包：始终隐式自动预装，保证 Linux 基础终端与工具可用 */
    val baseRequiredPackages: List<String> = listOf(
        "curl", "wget", "git", "python3", "ca-certificates", "ripgrep", "fd-find", "fzf", "bat", "jq", "tmux", "tar", "gzip", "xz-utils", "file",
    )

    /** 核心聚合大插件清单 */
    val bundles: List<PluginBundle> = listOf(
        PluginBundle(
            id = "android-suite",
            name = "Bộ công cụ phát triển full-stack Android & di động",
            summary = "Gradle 8.14, AAPT2/NDK ARM64 và Flutter tùy chọn, kiểm toán dịch ngược",
            description = "Tích hợp OpenJDK 17, Gradle 8.14.2, ARM64 AAPT2 với digest cố định và lzhiyong/termux-ndk, đồng thời cung cấp các chuỗi công cụ tùy chọn Flutter, C/C++ và JADX/APKTool. Trong khi xây dựng cấm tự động tải công cụ máy chủ x86_64 chính thức.",
            iconName = "Android",
            category = "Phát triển di động",
            components = listOf(
                PluginComponent(
                    id = "android-core",
                    name = "Môi trường cơ sở cốt lõi Android",
                    description = "OpenJDK 17, Android 34, Build Tools 35, Gradle 8.14.2, ARM64 AAPT2 bất biến, lzhiyong NDK r29, ADB và mirror Maven trong nước, tất cả sẵn sàng một lần trong khi lắp ráp",
                    isRequired = true,
                    // aapt/zipalign/apksigner come from the downloaded Google
                    // Build-Tools archive. Installing Ubuntu's similarly named
                    // packages pulls GUI/D-Bus/OpenJDK 21 dependencies that
                    // are unnecessary and fragile inside PRoot.
                    aptPackages = listOf("openjdk-17-jdk-headless", "ca-certificates-java", "adb", "curl", "ca-certificates", "util-linux"),
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_android_core.sh",
                    ),
                    checkCommand = ". /etc/profile.d/tianxuan-android.sh 2>/dev/null || true; JAVA_BIN=\"\${JAVA_HOME:-/opt/tianxuan/toolchains/android/jdk}/bin/java\"; (test -x \"\$JAVA_BIN\" || test -x /opt/tianxuan/bin/java || command -v java >/dev/null 2>&1) && test -f /opt/android-sdk/platforms/android-34/android.jar && test -f /opt/android-sdk/build-tools/35.0.0/lib/d8.jar && (test -f /opt/gradle-8.14.2/lib/gradle-launcher-8.14.2.jar || test -x /opt/tianxuan/bin/gradle || command -v gradle >/dev/null 2>&1) && (test -x \"\${TIANXUAN_AAPT2_PATH:-/opt/android-sdk/build-tools/35.0.0/aapt2}\" || test -x /opt/android-sdk/build-tools/35.0.0/aapt2 || test -x /opt/tianxuan/bin/aapt2) && (test -f \"\${TIANXUAN_NDK_PATH:-/opt/tianxuan/toolchains/android/ndk}/source.properties\" || test -f /opt/tianxuan/toolchains/android/ndk/source.properties)",
                ),
                PluginComponent(
                    id = "flutter",
                    name = "Môi trường phát triển đa nền tảng Flutter",
                    description = "Flutter ARM64 SDK, runtime Dart và phụ thuộc build APK Android (cần môi trường cơ sở lõi Android)",
                    isRequired = false,
                    // Archives are extracted by the setup script (Python/BusyBox);
                    // Ubuntu's unzip package is unreliable in PRoot during dpkg
                    // ownership updates (zipinfo.dpkg-new).
                    aptPackages = listOf("git", "curl", "ca-certificates", "xz-utils"),
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_flutter.sh",
                    ),
                    checkCommand = ". /etc/profile.d/tianxuan-android.sh 2>/dev/null || true; (test -x /opt/flutter/bin/flutter || test -x /opt/tianxuan/bin/flutter || command -v flutter >/dev/null 2>&1) && test -f /opt/android-sdk/platforms/android-34/android.jar && test -f /opt/android-sdk/build-tools/35.0.0/lib/d8.jar",
                ),
                PluginComponent(
                    id = "android-ndk",
                    name = "Chuỗi build native C/C++ & NDK",
                    description = "Chuỗi công cụ lzhiyong/termux-ndk r29 Linux AArch64, cùng CMake, Ninja, GCC/G++, Clang và các công cụ hỗ trợ build native khác",
                    isRequired = false,
                    aptPackages = listOf("cmake", "ninja-build", "gcc", "g++", "clang", "make", "pkg-config", "util-linux"),
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_termux_ndk.sh",
                    ),
                    checkCommand = ". /etc/profile.d/tianxuan-android.sh 2>/dev/null || . /opt/tianxuan/toolchains/android/ndk/tianxuan-ndk.env 2>/dev/null || true; (command -v cmake >/dev/null 2>&1 || test -x /opt/tianxuan/bin/cmake || test -x /opt/tianxuan/tools/android-suite-offline/cmake/bin/cmake || test -x /usr/bin/cmake) && (test -f \"\${TIANXUAN_NDK_PATH:-/opt/tianxuan/toolchains/android/ndk}/source.properties\" || test -f /opt/tianxuan/toolchains/android/ndk/source.properties)",
                ),
                PluginComponent(
                    id = "android-re",
                    name = "Phân tích đảo ngược và kiểm toán mã Android",
                    description = "Biên dịch lại tài nguyên APKTool, trình dịch ngược mã nguồn Java JADX-CLI và dịch vụ MCP đảo ngược APK tích hợp (python3 là phụ thuộc chạy của nó)",
                    isRequired = false,
                    aptPackages = listOf("openjdk-17-jdk-headless", "curl", "apktool", "python3"),
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_jadx.sh",
                    ),
                    checkCommand = ". /etc/profile.d/tianxuan-android.sh 2>/dev/null || true; (command -v apktool >/dev/null 2>&1 || test -x /opt/tianxuan/bin/apktool || test -f /opt/tianxuan/tools/android-suite-offline/lib/apktool.jar || command -v jadx >/dev/null 2>&1 || test -x /opt/tianxuan/bin/jadx || test -x /opt/jadx/bin/jadx || test -x /opt/tianxuan/tools/android-suite-offline/jadx/bin/jadx)",
                ),
                PluginComponent(
                    id = "rust-dev",
                    name = "Chuỗi biên dịch chéo hệ thống Rust và Android JNI",
                    description = "Chuỗi công cụ phát triển độc lập Rust 1.85+ ARM64, quản lý gói Cargo, thư viện chuẩn biên dịch chéo kiến trúc gốc Android ARM64 và liên kết trình link NDK Clang",
                    isRequired = false,
                    aptPackages = listOf("curl", "ca-certificates", "build-essential"),
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_rust.sh",
                    ),
                    checkCommand = ". /etc/profile.d/tianxuan-android.sh 2>/dev/null || true; (command -v rustc >/dev/null 2>&1 || test -x /opt/tianxuan/bin/rustc || test -x /opt/tianxuan/toolchains/rust/bin/rustc) && (command -v cargo >/dev/null 2>&1 || test -x /opt/tianxuan/bin/cargo || test -x /opt/tianxuan/toolchains/rust/bin/cargo)",
                ),
            ),
        ),
        PluginBundle(
            id = "code-search-suite",
            name = "Bộ công cụ truy xuất mã và hiệu suất terminal",
            summary = "Bộ tứ truy xuất mã rg, fd, fzf, bat",
            description = "Tích hợp tìm kiếm toàn văn ripgrep, tìm tệp fd, lọc mờ fzf và xem trước tô sáng cú pháp bat, đồng thời thống nhất tên lệnh trên Debian/Ubuntu.",
            iconName = "Search",
            category = "Hiệu suất phát triển",
            components = listOf(
                PluginComponent(
                    id = "code-search-toolkit",
                    name = "Bộ tứ truy xuất mã (rg / fd / fzf / bat)",
                    description = "Tìm kiếm toàn văn tốc độ cao, phát hiện tệp, lọc mờ tương tác và xem trước mã nguồn có tô sáng cú pháp",
                    isRequired = true,
                    aptPackages = listOf("ripgrep", "fd-find", "fzf", "bat"),
                    checkCommand = "(command -v rg >/dev/null 2>&1 || test -x /opt/tianxuan/bin/rg) && (command -v fd >/dev/null 2>&1 || command -v fdfind >/dev/null 2>&1 || test -x /usr/local/bin/fd) && (command -v fzf >/dev/null 2>&1 || test -x /usr/bin/fzf) && (command -v bat >/dev/null 2>&1 || command -v batcat >/dev/null 2>&1 || test -x /usr/local/bin/bat)",
                ),
            ),
        ),
        PluginBundle(
            id = "python-suite",
            name = "Bộ công cụ phát triển Python & AI",
            summary = "Runtime Python 3, pip, môi trường ảo venv và phụ thuộc biên dịch tính toán khoa học AI",
            description = "Bao gồm môi trường chạy Python 3 đầy đủ, quản lý gói pip, môi trường cách ly venv cùng thư viện nền build-essential cần để biên dịch wheel mở rộng Python C.",
            iconName = "Code",
            category = "AI và script",
            components = listOf(
                PluginComponent(
                    id = "python-core",
                    name = "Nền tảng chạy lõi Python 3",
                    description = "Trình thông dịch Python 3, trình quản lý gói pip và công cụ môi trường ảo venv",
                    isRequired = true,
                    aptPackages = listOf("python3", "python3-pip", "python3-venv"),
                    checkCommand = "command -v python3 && command -v pip3",
                ),
                PluginComponent(
                    id = "python-ai-dev",
                    name = "Thư viện tính toán khoa học AI và biên dịch mở rộng C",
                    description = "python3-dev, build-essential, pkg-config và các header hệ thống tầng thấp",
                    isRequired = false,
                    aptPackages = listOf("python3-dev", "build-essential", "pkg-config", "libffi-dev"),
                    checkCommand = "dpkg -s python3-dev 2>/dev/null || test -f /usr/include/python3*/Python.h",
                ),
            ),
        ),
        PluginBundle(
            id = "nodejs-suite",
            name = "Bộ công cụ full-stack Node.js & Web",
            summary = "Runtime Node.js, npm, pnpm và hệ sinh thái full-stack frontend hiện đại",
            description = "Tích hợp runtime Node.js LTS hiện đại, trình quản lý gói npm, hỗ trợ pnpm cùng các trình quản lý gói hiện đại và phát triển full-stack JavaScript / TypeScript.",
            iconName = "Globe",
            category = "Phát triển full-stack",
            components = listOf(
                PluginComponent(
                    id = "nodejs-core",
                    name = "Runtime cốt lõi Node.js",
                    description = "Runtime Node.js và trình quản lý gói npm",
                    isRequired = true,
                    aptPackages = listOf("nodejs", "npm"),
                    checkCommand = "command -v node && command -v npm",
                ),
                PluginComponent(
                    id = "nodejs-pkg",
                    name = "Trình quản lý gói hiện đại và tăng tốc biên dịch (pnpm / yarn)",
                    description = "Trình quản lý cache gói cục bộ hiệu năng cao pnpm và yarn",
                    isRequired = false,
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_pnpm.sh",
                    ),
                    checkCommand = "command -v pnpm || command -v yarn",
                ),
            ),
        ),
    )

    /**
     * 批量聚合生成单条安全、极速的安装脚本流水线
     */
    fun buildBatchInstallScript(selectedComponentIds: Set<String>): List<String> {
        val allComponents = bundles.flatMap { it.components }.filter { it.id in selectedComponentIds }
        val allAptPackages = (baseRequiredPackages + allComponents.flatMap { it.aptPackages }).distinct()

        val steps = mutableListOf<String>()
        // 1. dpkg 锁与环境自愈
        steps.add("mkdir -p /etc/dpkg/dpkg.cfg.d /usr/bin /usr/sbin /usr/lib 2>/dev/null || true")
        steps.add("printf 'force-unsafe-io\\nforce-overwrite\\n' > /etc/dpkg/dpkg.cfg.d/tianxuan-proot 2>/dev/null || true")
        steps.add("rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* /var/lib/apt/lists/lock /var/cache/apt/archives/lock /usr/bin/*.dpkg-new /usr/sbin/*.dpkg-new /usr/lib/*.dpkg-new 2>/dev/null || true")
        // A previously interrupted unzip/java-wrappers transaction can never
        // complete in PRoot because dpkg cannot chown zipinfo.dpkg-new. These
        // optional helpers are not needed: the APK supplies its own JAR-backed
        // unzip command and setup_android_core.sh links it into PATH.
        steps.add("DEBIAN_FRONTEND=noninteractive dpkg --remove --force-remove-reinstreq --force-depends unzip java-wrappers 2>/dev/null || true")
        steps.add("DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true")

        // 2. 批量聚合 APT 安装（仅执行 1 次 update 和 1 次 install；
        //    整批失败时降级为 --ignore-missing，避免个别发行版缺包导致全部装不上）
        if (allAptPackages.isNotEmpty()) {
            val packageArg = allAptPackages.joinToString(" ")
            // Runtime configures TUNA ubuntu-ports/debian mirrors. Keep apt
            // retries bounded so a slow mirror does not stall the whole suite.
            // ForceIPv4/Languages=en：手机 IPv6 半残防假死 + 跳过 Translation 下载（真机实测）。
            val aptOpts = "-o Acquire::Retries=2 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 " +
                "-o Acquire::ForceIPv4=true -o Acquire::Languages=en"
            steps.add("DEBIAN_FRONTEND=noninteractive apt-get $aptOpts update -y || true")
            steps.add("DEBIAN_FRONTEND=noninteractive apt-get $aptOpts install -y --no-install-recommends $packageArg || DEBIAN_FRONTEND=noninteractive apt-get $aptOpts -f install -y --no-install-recommends && DEBIAN_FRONTEND=noninteractive apt-get $aptOpts install -y --no-install-recommends $packageArg")
        }

        // 3. Debian/Ubuntu 将 fd、bat 分别命名为 fdfind、batcat；统一暴露常用命令名。
        steps.add("mkdir -p /usr/local/bin; if ! command -v fd >/dev/null 2>&1 && command -v fdfind >/dev/null 2>&1; then ln -sf \"${'$'}(command -v fdfind)\" /usr/local/bin/fd; fi")
        steps.add("mkdir -p /usr/local/bin; if ! command -v bat >/dev/null 2>&1 && command -v batcat >/dev/null 2>&1; then ln -sf \"${'$'}(command -v batcat)\" /usr/local/bin/bat; fi")

        // 4. 各子组件后置处理
        allComponents.forEach { comp ->
            steps.addAll(comp.postInstallSteps)
        }

        return steps
    }
}
