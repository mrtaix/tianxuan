package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

/**
 * 🧩 Định nghĩa thành phần con của plugin (Plugin Sub-Component)
 * Thành phần năng lực nguyên tử trực thuộc một gói plugin tổng hợp lớn.
 */
@Serializable
data class PluginComponent(
    val id: String,
    val name: String,
    val description: String,
    val isRequired: Boolean = false, // Có phải thành phần cốt lõi bắt buộc (không thể bỏ chọn)
    val aptPackages: List<String> = emptyList(),
    val postInstallSteps: List<String> = emptyList(),
    val checkCommand: String, // Lệnh dò trạng thái, trả về 0 nghĩa là đã sẵn sàng
)

/**
 * 📦 Định nghĩa gói plugin tổng hợp lớn (Plugin Bundle)
 * Gom sâu các năng lực cùng lĩnh vực (môi trường nền, công cụ mở rộng, gỡ lỗi dịch ngược v.v.) thành một nhóm plugin lớn duy nhất.
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
    /** Gói cốt lõi nền tảng: luôn tự động cài ngầm, đảm bảo terminal và công cụ Linux cơ bản luôn dùng được */
    val baseRequiredPackages: List<String> = listOf(
        "curl", "wget", "git", "python3", "ca-certificates", "ripgrep", "fd-find", "fzf", "bat", "jq", "tmux", "tar", "gzip", "xz-utils", "file",
    )

    /** Danh sách plugin tổng hợp lớn cốt lõi */
    val bundles: List<PluginBundle> = listOf(
        PluginBundle(
            id = "android-suite",
            name = "Bộ công cụ phát triển full-stack Android & di động",
            summary = "Gradle 8.14, ARM64 AAPT2/NDK và tuỳ chọn Flutter, kiểm toán dịch ngược",
            description = "Tích hợp OpenJDK 17, Gradle 8.14.2, AAPT2 ARM64 có digest cố định và lzhiyong/termux-ndk, đồng thời cung cấp tuỳ chọn toolchain Flutter, C/C++ và JADX/APKTool. Trong giai đoạn build cấm tự động tải công cụ máy chủ x86_64 chính thức.",
            iconName = "Android",
            category = "Phát triển di động",
            components = listOf(
                PluginComponent(
                    id = "android-core",
                    name = "Môi trường nền tảng Android cốt lõi",
                    description = "OpenJDK 17, Android 34, Build Tools 35, Gradle 8.14.2, AAPT2 ARM64 bất biến, lzhiyong NDK r29, ADB và mirror Maven trong nước, tất cả được đặt sẵn một lần trong giai đoạn lắp ghép",
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
                    description = "Flutter ARM64 SDK, runtime Dart và phụ thuộc build APK Android (cần Môi trường nền tảng Android cốt lõi)",
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
                    name = "Toolchain build native C/C++ và NDK",
                    description = "Toolchain lzhiyong/termux-ndk r29 Linux AArch64, cùng các công cụ hỗ trợ build native như CMake, Ninja, GCC/G++, Clang",
                    isRequired = false,
                    aptPackages = listOf("cmake", "ninja-build", "gcc", "g++", "clang", "make", "pkg-config", "util-linux"),
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_termux_ndk.sh",
                    ),
                    checkCommand = ". /etc/profile.d/tianxuan-android.sh 2>/dev/null || . /opt/tianxuan/toolchains/android/ndk/tianxuan-ndk.env 2>/dev/null || true; (command -v cmake >/dev/null 2>&1 || test -x /opt/tianxuan/bin/cmake || test -x /opt/tianxuan/tools/android-suite-offline/cmake/bin/cmake || test -x /usr/bin/cmake) && (test -f \"\${TIANXUAN_NDK_PATH:-/opt/tianxuan/toolchains/android/ndk}/source.properties\" || test -f /opt/tianxuan/toolchains/android/ndk/source.properties)",
                ),
                PluginComponent(
                    id = "android-re",
                    name = "Phân tích ngược và kiểm toán mã Android",
                    description = "Biên dịch lại tài nguyên APKTool, trình dịch ngược mã Java JADX-CLI và dịch vụ MCP dịch ngược APK tích hợp (python3 là phụ thuộc để chạy)",
                    isRequired = false,
                    aptPackages = listOf("openjdk-17-jdk-headless", "curl", "apktool", "python3"),
                    postInstallSteps = listOf(
                        "/bin/sh /opt/tianxuan/scripts/setup_jadx.sh",
                    ),
                    checkCommand = ". /etc/profile.d/tianxuan-android.sh 2>/dev/null || true; (command -v apktool >/dev/null 2>&1 || test -x /opt/tianxuan/bin/apktool || test -f /opt/tianxuan/tools/android-suite-offline/lib/apktool.jar || command -v jadx >/dev/null 2>&1 || test -x /opt/tianxuan/bin/jadx || test -x /opt/jadx/bin/jadx || test -x /opt/tianxuan/tools/android-suite-offline/jadx/bin/jadx)",
                ),
                PluginComponent(
                    id = "rust-dev",
                    name = "Toolchain biên dịch chéo Rust hệ thống và Android JNI",
                    description = "Toolchain phát triển độc lập Rust 1.85+ ARM64, quản lý gói Cargo, thư viện chuẩn biên dịch chéo kiến trúc native Android ARM64 và liên kết NDK Clang",
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
            name = "Bộ công cụ tìm mã và tăng hiệu quả terminal",
            summary = "Bộ tứ tra cứu mã rg, fd, fzf, bat",
            description = "Tích hợp tìm kiếm toàn văn ripgrep, tìm tệp fd, lọc mờ fzf và xem trước tô sáng cú pháp bat, đồng thời thống nhất tên lệnh trên Debian/Ubuntu.",
            iconName = "Search",
            category = "Hiệu suất phát triển",
            components = listOf(
                PluginComponent(
                    id = "code-search-toolkit",
                    name = "Bộ tứ tra cứu mã (rg / fd / fzf / bat)",
                    description = "Tìm kiếm toàn văn tốc độ cao, phát hiện tệp, lọc mờ tương tác và xem trước mã nguồn có tô sáng cú pháp",
                    isRequired = true,
                    aptPackages = listOf("ripgrep", "fd-find", "fzf", "bat"),
                    checkCommand = "(command -v rg >/dev/null 2>&1 || test -x /opt/tianxuan/bin/rg) && (command -v fd >/dev/null 2>&1 || command -v fdfind >/dev/null 2>&1 || test -x /usr/local/bin/fd) && (command -v fzf >/dev/null 2>&1 || test -x /usr/bin/fzf) && (command -v bat >/dev/null 2>&1 || command -v batcat >/dev/null 2>&1 || test -x /usr/local/bin/bat)",
                ),
            ),
        ),
        PluginBundle(
            id = "python-suite",
            name = "Bộ công cụ phát triển Python và AI",
            summary = "Runtime Python 3, pip, môi trường ảo venv và phụ thuộc biên dịch tính toán khoa học AI",
            description = "Bao gồm môi trường chạy Python 3 đầy đủ, quản lý gói pip, môi trường cách ly venv và thư viện nền build-essential cần để biên dịch wheel C extension của Python.",
            iconName = "Code",
            category = "AI và script",
            components = listOf(
                PluginComponent(
                    id = "python-core",
                    name = "Nền chạy Python 3 cốt lõi",
                    description = "Trình thông dịch Python 3, trình quản lý gói pip và công cụ môi trường ảo venv",
                    isRequired = true,
                    aptPackages = listOf("python3", "python3-pip", "python3-venv"),
                    checkCommand = "command -v python3 && command -v pip3",
                ),
                PluginComponent(
                    id = "python-ai-dev",
                    name = "Thư viện tính toán khoa học AI và biên dịch C extension",
                    description = "python3-dev, build-essential, pkg-config và các header hệ thống tầng thấp",
                    isRequired = false,
                    aptPackages = listOf("python3-dev", "build-essential", "pkg-config", "libffi-dev"),
                    checkCommand = "dpkg -s python3-dev 2>/dev/null || test -f /usr/include/python3*/Python.h",
                ),
            ),
        ),
        PluginBundle(
            id = "nodejs-suite",
            name = "Bộ công cụ full-stack Node.js và Web",
            summary = "Runtime Node.js, npm, pnpm và hệ sinh thái full-stack frontend hiện đại",
            description = "Tích hợp runtime Node.js LTS hiện đại, trình quản lý gói npm, hỗ trợ quản lý gói hiện đại như pnpm và phát triển full-stack JavaScript / TypeScript.",
            iconName = "Globe",
            category = "Phát triển full-stack",
            components = listOf(
                PluginComponent(
                    id = "nodejs-core",
                    name = "Runtime Node.js cốt lõi",
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
     * Gom hàng loạt để sinh một pipeline script cài đặt an toàn, tốc độ cao
     */
    fun buildBatchInstallScript(selectedComponentIds: Set<String>): List<String> {
        val allComponents = bundles.flatMap { it.components }.filter { it.id in selectedComponentIds }
        val allAptPackages = (baseRequiredPackages + allComponents.flatMap { it.aptPackages }).distinct()

        val steps = mutableListOf<String>()
        // 1. Khoá dpkg và tự phục hồi môi trường
        steps.add("mkdir -p /etc/dpkg/dpkg.cfg.d /usr/bin /usr/sbin /usr/lib 2>/dev/null || true")
        steps.add("printf 'force-unsafe-io\\nforce-overwrite\\n' > /etc/dpkg/dpkg.cfg.d/tianxuan-proot 2>/dev/null || true")
        steps.add("rm -rf /var/lib/dpkg/updates/* /var/lib/dpkg/lock* /var/lib/apt/lists/lock /var/cache/apt/archives/lock /usr/bin/*.dpkg-new /usr/sbin/*.dpkg-new /usr/lib/*.dpkg-new 2>/dev/null || true")
        // A previously interrupted unzip/java-wrappers transaction can never
        // complete in PRoot because dpkg cannot chown zipinfo.dpkg-new. These
        // optional helpers are not needed: the APK supplies its own JAR-backed
        // unzip command and setup_android_core.sh links it into PATH.
        steps.add("DEBIAN_FRONTEND=noninteractive dpkg --remove --force-remove-reinstreq --force-depends unzip java-wrappers 2>/dev/null || true")
        steps.add("DEBIAN_FRONTEND=noninteractive dpkg --configure -a 2>/dev/null || true")

        // 2. Cài APT gom hàng loạt (chỉ chạy 1 lần update và 1 lần install;
        //    khi cả lô thất bại thì hạ cấp sang --ignore-missing, tránh việc một bản phân phối thiếu gói làm hỏng toàn bộ)
        if (allAptPackages.isNotEmpty()) {
            val packageArg = allAptPackages.joinToString(" ")
            // Runtime configures TUNA ubuntu-ports/debian mirrors. Keep apt
            // retries bounded so a slow mirror does not stall the whole suite.
            // ForceIPv4/Languages=en: chống treo do IPv6 trên điện thoại chưa hoàn chỉnh + bỏ qua tải Translation (đã thử trên máy thật).
            val aptOpts = "-o Acquire::Retries=2 -o Acquire::http::Timeout=30 -o Acquire::https::Timeout=30 " +
                "-o Acquire::ForceIPv4=true -o Acquire::Languages=en"
            steps.add("DEBIAN_FRONTEND=noninteractive apt-get $aptOpts update -y || true")
            steps.add("DEBIAN_FRONTEND=noninteractive apt-get $aptOpts install -y --no-install-recommends $packageArg || DEBIAN_FRONTEND=noninteractive apt-get $aptOpts -f install -y --no-install-recommends && DEBIAN_FRONTEND=noninteractive apt-get $aptOpts install -y --no-install-recommends $packageArg")
        }

        // 3. Debian/Ubuntu đặt tên fd, bat lần lượt là fdfind, batcat; thống nhất tên lệnh thường dùng.
        steps.add("mkdir -p /usr/local/bin; if ! command -v fd >/dev/null 2>&1 && command -v fdfind >/dev/null 2>&1; then ln -sf \"${'$'}(command -v fdfind)\" /usr/local/bin/fd; fi")
        steps.add("mkdir -p /usr/local/bin; if ! command -v bat >/dev/null 2>&1 && command -v batcat >/dev/null 2>&1; then ln -sf \"${'$'}(command -v batcat)\" /usr/local/bin/bat; fi")

        // 4. Xử lý hậu kỳ cho từng thành phần con
        allComponents.forEach { comp ->
            steps.addAll(comp.postInstallSteps)
        }

        return steps
    }
}
