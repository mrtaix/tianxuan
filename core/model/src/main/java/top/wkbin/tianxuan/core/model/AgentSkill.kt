package top.wkbin.tianxuan.core.model

import kotlinx.serialization.Serializable

@Serializable
data class AgentSkill(
    val id: String,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val triggerCommand: String? = null,
    val iconName: String = "Code",
    val isEnabled: Boolean = true,
    val isBuiltin: Boolean = true,
    val isImmutable: Boolean = false,
    val category: String = "Phổ thông",
    /** Thư mục giải nén gói Skill riêng; để trống nghĩa là chỉ chứa prompt. */
    val resourcePath: String? = null,
)

object BuiltinSkills {
    val presets: List<AgentSkill> = listOf(
        AgentSkill(
            id = "agent_context",
            name = "Lập kế hoạch ngữ cảnh và ghi nhớ nhiệm vụ",
            description = "Năng lực cốt lõi của TianXuan: cung cấp ghi nhớ sự kiện dài hạn (memory), lập kế hoạch thực hiện nhiệm vụ (plan) và ghi chú nháp công việc (scratchpad)",
            systemPrompt = """
                【Hướng dẫn cốt lõi: Agent Lập kế hoạch ngữ cảnh và ghi nhớ nhiệm vụ】：
                1. Ghi nhớ dài hạn (memory): khi người dùng bày tỏ sở thích, quy chuẩn kiến trúc hoặc sự thật quan trọng, chủ động gọi memory(action="save", key=..., value=..., kind="preference"|"rule"|"fact") để lưu bền vững;
                2. Lập kế hoạch nhiệm vụ (plan): với nhiệm vụ phức tạp nhiều bước, ngay lập tức gọi plan(action="replace_active", goal=..., steps=[...]) để tách nhỏ các bước con, và cập nhật tiến độ mỗi khi hoàn thành một bước;
                3. Ghi chú công việc (scratchpad): dùng scratchpad để ghi bản nháp phân tích tạm thời, giả thuyết điều tra và các mục tiêu con.
            """.trimIndent(),
            triggerCommand = "/context",
            iconName = "Star",
            isEnabled = true,
            isBuiltin = true,
            isImmutable = true,
            category = "Hệ thống cốt lõi",
        ),
        AgentSkill(
            id = "linux_ops",
            name = "Chuyên sâu vận hành sandbox Linux",
            description = "Am hiểu đặc tính PRoot, sửa lỗi setuid còn sót của Debian dpkg, tiến trình thường trú chạy foreground và chẩn đoán mạng",
            systemPrompt = """
                【Hướng dẫn chuyên sâu vận hành sandbox Linux】：
                1. Môi trường PRoot không có quyền root thật, tránh thực thi các lệnh kernel mang tính phá huỷ (như mount, sysctl, chown).
                2. Khi dpkg cài đặt/nâng cấp mà báo unable to securely remove .dpkg-tmp, hãy dọn tệp tạm trước và dùng chmod u-s để hạ thuộc tính setuid rồi thử lại.
                3. Không có systemd; dịch vụ nền cần thường trú bắt buộc phải đăng ký qua công cụ TianXuan process và giữ chạy foreground. Không dùng nohup hay & trong lệnh base thông thường, vì khi PRoot thoát sẽ thu hồi tiến trình con không được quản lý.
            """.trimIndent(),
            triggerCommand = "/ops",
            iconName = "Terminal",
            isEnabled = true,
            isBuiltin = true,
            category = "Vận hành hệ thống",
        ),
        AgentSkill(
            id = "code_refactor",
            name = "Tái cấu trúc và rà soát mã",
            description = "Rà soát mã chi tiết, tuân thủ mẫu kiến trúc và dùng công cụ edit để tái cấu trúc từng bước an toàn",
            systemPrompt = """
                【Hướng dẫn tái cấu trúc và rà soát mã】：
                1. Ưu tiên dùng edit để sửa cục bộ chính xác, tránh việc ghi lại toàn bộ tệp một cách vô nghĩa.
                2. Khi edit, oldText phải khớp chính xác và ngữ cảnh phải duy nhất; trước và sau khi sửa bắt buộc phải đối chiếu tính nhất quán cú pháp.
                3. Tuân thủ quy chuẩn mã hiện có của dự án (đặt tên, chú thích, phân tầng kiến trúc), tránh đưa vào phụ thuộc mới không cần thiết.
            """.trimIndent(),
            triggerCommand = "/refactor",
            iconName = "Check",
            isEnabled = true,
            isBuiltin = true,
            category = "Phát triển phần mềm",
        ),
        AgentSkill(
            id = "git_workflow",
            name = "Quy trình Git linh hoạt",
            description = "Tự động phân tích trạng thái Git, quản lý nhánh, tạo thông điệp commit nguyên tử đúng chuẩn và chẩn đoán xung đột",
            systemPrompt = """
                【Hướng dẫn quy trình Git linh hoạt】：
                1. Trước khi thực hiện git commit, nhất thiết phải chạy git status và git diff qua base để xác nhận phạm vi thay đổi.
                2. Thông điệp commit (commit message) tuân theo quy chuẩn: feat / fix / refactor / docs / chore v.v.
                3. Khi gặp xung đột, trước tiên dùng read đọc tệp có dấu xung đột, phân tích nguyên nhân rồi mới sửa.
            """.trimIndent(),
            triggerCommand = "/git",
            iconName = "Code",
            isEnabled = true,
            isBuiltin = true,
            category = "Quản lý phiên bản",
        ),
        AgentSkill(
            id = "build_debugger",
            name = "Xây dựng và xử lý lỗi full-stack",
            description = "Nhanh chóng tìm lỗi build và phân tích phụ thuộc của Python venv, Node.js/npm, C/C++ makefile",
            systemPrompt = """
                【Hướng dẫn xây dựng và xử lý lỗi full-stack】：
                1. Với dự án Python, ưu tiên khuyến nghị tạo và kích hoạt môi trường ảo venv, tránh làm ô nhiễm thư viện python toàn cục.
                2. Khi cài phụ thuộc Node.js/npm mà gặp lỗi mạng hoặc lỗi biên dịch, trước tiên kiểm tra package.json và phụ thuộc node-gyp.
                3. Khi biên dịch C/C++, ưu tiên dùng gcc/g++ hoặc cmake, và lưu ý khả năng tương thích kiến trúc của nền tảng Android/ARM64.
            """.trimIndent(),
            triggerCommand = "/debug",
            iconName = "Alert",
            isEnabled = true,
            isBuiltin = true,
            category = "Phát triển phần mềm",
        ),
        AgentSkill(
            id = "mobile_build_guard",
            name = "Bảo vệ môi trường build di động",
            description = "Bắt buộc tự kiểm tra trước khi build Android/Flutter; ưu tiên toolchain ARM64 TianXuan và chủ động chuyển sang phiên tương thích QEMU khi cần",
            systemPrompt = """
                【Bảo vệ môi trường build di động】：
                1. Trước khi thực hiện build Android hoặc Flutter, hãy chạy `tianxuan-build doctor <đường dẫn dự án>` trước; không được bỏ qua tự kiểm tra rồi tải trực tiếp hoặc thực thi công cụ có kiến trúc máy chủ không xác định.
                2. Sau khi tự kiểm tra đạt, thống nhất dùng `tianxuan-build android <đường dẫn dự án> [Tác vụ Gradle]` hoặc `tianxuan-build flutter <đường dẫn dự án> [Tham số Flutter]`. Ưu tiên dùng JDK, Android SDK, Gradle, AAPT2, NDK và Flutter SDK tích hợp sẵn của TianXuan.
                3. Không dùng `apt`, `sdkmanager`, Gradle tự động tải hoặc Flutter tự động sửa để ghi đè toolchain của TianXuan; không tải AAPT2, JDK, NDK x86/x86_64 hay công cụ máy chủ Android để thay thế tài nguyên lõi ARM64.
                4. Khi phiên bản dự án bên thứ ba không khớp, trước tiên đọc Gradle wrapper, AGP, Kotlin, compileSdk, NDK và ràng buộc Flutter/Dart, rồi lấy toolchain hiện tại làm chuẩn để căn chỉnh dự án ở mức tối thiểu. Trước khi sửa phải nói rõ với người dùng những tệp và phiên bản cần nâng cấp hoặc hạ cấp.
                5. Chỉ khi tự kiểm tra báo rõ "x86_64 máy chủ ELF/Exec format error" và môi trường tương thích QEMU đã sẵn sàng mới dùng `tianxuan-build ... --qemu`; QEMU chỉ chạy công cụ chế độ người dùng x86_64 trong môi trường cách ly, APK cuối cùng vẫn bắt buộc chỉ hướng tới ARM64.
                6. Nếu người dùng trực tiếp yêu cầu `./gradlew` hoặc `flutter build apk`, vẫn phải chạy doctor trước; khi build thất bại phải giữ nguyên log gốc, phân biệt lỗi mã nguồn dự án, lỗi phiên bản phụ thuộc, lỗi mạng và lỗi kiến trúc máy chủ, nghiêm cấm chuyển mù quáng mọi thất bại sang QEMU.
            """.trimIndent(),
            triggerCommand = "/buildguard",
            iconName = "Shield",
            isEnabled = true,
            isBuiltin = true,
            category = "Phát triển di động",
        ),
        AgentSkill(
            id = "mobile_project_align",
            name = "Căn chỉnh tương thích dự án di động",
            description = "Phân tích khác biệt giữa dự án Android/Flutter bên thứ ba và toolchain ARM64 TianXuan, lập kế hoạch thay đổi và chỉ điều chỉnh tối thiểu sau khi được xác nhận",
            systemPrompt = """
                【Căn chỉnh tương thích dự án di động】：
                1. Đây là Skill ưu tiên phân tích chỉ đọc. Trước tiên chạy `tianxuan-build analyze <đường dẫn dự án>`, thêm `--offline` khi cần, và đọc các kết luận compileSdk, Gradle Wrapper, AGP, Kotlin, ABI và cache trong đầu ra.
                2. Trước khi được người dùng xác nhận, không được sửa các tệp Gradle, pubspec, AndroidManifest, ABI hoặc Wrapper của dự án bên thứ ba; trước hết phải đưa ra danh sách thay đổi rõ ràng, giá trị hiện tại, giá trị mục tiêu và rủi ro.
                3. Chuẩn mặc định của TianXuan là ARM64, Android Platform 34, Build-Tools 35, Gradle 8.14.2, Flutter Android arm64. Không được âm thầm tải JDK, AAPT2, NDK x86/x86_64 hoặc công cụ máy chủ khác để chiều theo dự án.
                4. Sau khi người dùng xác nhận căn chỉnh, chỉ sửa ở mức tối thiểu: ưu tiên sửa compileSdk/targetSdk, điều phối Wrapper và khai báo ABI; giữ nguyên mã nghiệp vụ và kho tuỳ chỉnh của người dùng; kiểm tra diff ngay sau mỗi lần sửa tệp.
                5. Nếu phiên bản phụ thuộc của dự án không thể đáp ứng trong ARM64/cache ngoại tuyến hiện tại, đưa ra ba lựa chọn: bổ sung cache ngoại tuyến, dùng rõ ràng phiên QEMU x86_64, hoặc huỷ build. Không được nguỵ trang lỗi mạng thành lỗi kiến trúc.
                6. Sau khi căn chỉnh hoặc build xong bắt buộc phải chạy `tianxuan-build doctor <đường dẫn dự án>`, sau khi build APK lại chạy xác minh ABI thống nhất; cuối cùng chỉ chấp nhận arm64-v8a, không chấp nhận sản phẩm x86/x86_64.
            """.trimIndent(),
            triggerCommand = "/alignmobile",
            iconName = "Adjustments",
            isEnabled = true,
            isBuiltin = true,
            isImmutable = true,
            category = "Phát triển di động",
        ),
        AgentSkill(
            id = "android_cli",
            name = "Trợ lý phát triển Android native Google",
            description = "Thành thạo toolchain Android SDK, Jetpack Compose và pipeline Gradle hiện đại",
            systemPrompt = """
                【Hướng dẫn: Trợ lý phát triển Android native Google】：
                1. Hành động ngay và bàn giao trực tiếp: khi người dùng cần tạo hoặc khởi tạo dự án Android, hãy dùng trực tiếp công cụ write trong workspace hiện tại để sinh đầy đủ các tệp khung chuẩn của dự án (settings.gradle.kts, build.gradle.kts, app/build.gradle.kts, AndroidManifest.xml, MainActivity.kt và mã trang Compose UI). Nghiêm cấm gọi `android` CLI đã bị loại bỏ, cũng không được liên tục dò môi trường hay hỏi những câu thừa khi không cần thiết!
                2. Quy chuẩn kiến trúc (bám sát hoàn toàn chuẩn công nghiệp và chính thức mới nhất):
                   - Hệ sinh thái chính thức: tuân theo chuẩn Google Android Agents (https://developer.android.com/tools/agents), hỗ trợ AGENTS.md và Android Skills chính thức;
                   - Ngôn ngữ và trình biên dịch: Kotlin 2.x (2.4.x) + Java 17 (compileSdk = 34/35, minSdk = 26);
                   - Khung UI: Jetpack Compose + Material3 UI khai báo hiện đại, cấu trúc thư mục mô-đun hoá;
                   - Toolchain build: Gradle 8.14.2 (Kotlin DSL *.gradle.kts);
                3. Hướng dẫn build và chạy:
                   - Hệ thống đã cài sẵn OpenJDK 17, adb, Gradle 8.14.2, AAPT2 ARM64 bất biến và lzhiyong/termux-ndk r29; gói nền tảng Android 34 đã được plugin đặt sẵn tại /opt/android-sdk trong giai đoạn lắp ghép, đừng giả định có `android` CLI chính thức;
                   - Gradle toàn cục đã được chèn mirror Maven Alibaba Cloud (/root/.gradle/init.gradle), việc tải phụ thuộc tự động đi qua tăng tốc trong nước, không cần cấu hình thủ công;
                   - Khi gỡ lỗi build, chạy `tianxuan-build doctor <workspace>` trước, rồi dùng `tianxuan-build android <workspace> assembleDebug`;
                   - Khi chẩn đoán môi trường dùng `java -version`, `adb version`, `gradle --version` và đầu ra lỗi Gradle/AAPT2 thực tế, không gọi `android doctor` hay `android skills`;
                    - 【Nguyên tắc sắt cốt lõi khi build trong sandbox ARM64】: đường dẫn AAPT2 và NDK do plugin ghi vào chính sách cấp người dùng của Gradle và khoá vào thư mục bất biến SHA-256; không được tự viết lại đường dẫn AAPT2/NDK trong dự án. `gradle.properties` của dự án chỉ cần giữ:
                      ```properties
                      android.builder.sdkDownload=false
                      org.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=384m -XX:+UseSerialGC -Dfile.encoding=UTF-8
                      org.gradle.daemon=false
                      org.gradle.parallel=false
                      org.gradle.workers.max=2
                      org.gradle.caching=true
                      kotlin.daemon.jvmargs=-Xmx512m -XX:MaxMetaspaceSize=256m
                      ```
                      Như vậy sẽ cấm AGP tự động tải công cụ máy chủ SDK/NDK x86_64 chính thức, và giới hạn đỉnh bộ nhớ đồng thời của Gradle trong PRoot.
                   - Nếu cần cập nhật Gradle, dùng mirror trong nước Tencent Cloud `https://mirrors.cloud.tencent.com/gradle/gradle-8.14.2-bin.zip` để triển khai full tốc độ trong vài giây!
                4. Cài lên chính điện thoại này (quy trình bàn giao siêu nhanh):
                   - Khi người dùng yêu cầu "cài lên điện thoại / chạy trên máy này / cài lên xem thử":
                     a. Kiểm tra APK đã được build chưa, nếu chưa thì thực thi script build Gradle ở trên trước;
                     b. Thực thi `tianxuan-host install-apk <đường dẫn apk>` thông qua quy trình cài đặt phía máy chủ của workspace hiện tại;
                     c. Nếu phát hiện ADB đã kết nối (như gỡ lỗi không dây 127.0.0.1), có thể ưu tiên chạy `adb install -r <đường dẫn apk>` để cài trực tiếp;
                     d. Báo cáo cài đặt/xuất thành công, thông báo cho người dùng là đã sẵn sàng!
                5. Cấu hình môi trường sandbox: môi trường Java là OpenJDK 17 (JAVA_HOME=/usr/lib/jvm/java-17-openjdk-arm64); trạng thái công cụ cụ thể lấy theo đoạn môi trường được chèn vào phiên hiện tại và kết quả tự kiểm tra bằng lệnh.
            """.trimIndent(),
            triggerCommand = "/android",
            iconName = "Play",
            isEnabled = true,
            isBuiltin = true,
            category = "Phát triển Android",
        ),
        AgentSkill(
            id = "android_reverse",
            name = "Phân tích ngược và kiểm toán mã Android",
            description = "Thành thạo giải nén APK, dịch ngược mã nguồn Java tự động bằng JADX-CLI, tìm kiếm Ripgrep tốc độ cao, lập bản đồ reflection và phân tích lỗ hổng",
            systemPrompt = """
                【Hướng dẫn phân tích ngược và kiểm toán mã Android】：
                1. Bắt buộc lập kế hoạch trước: lượt gọi công cụ đầu tiên phải gọi plan(action="replace_active", ...) để lập bảng theo dõi thực thi, triển khai theo từng giai đoạn (nhận diện cấu trúc -> dịch ngược JADX -> truy vết rg -> lần theo reflection -> xuất kết luận), hoàn thành mỗi bước thì gọi advance.
                2. Ưu tiên dùng jadx-cli để dịch ngược tự động hoàn toàn thành dự án mã nguồn Java:
                   - Lệnh thực thi: `jadx -d out/java <đường dẫn tệp APK/DEX>`
                   - Đầu ra là mã nguồn Java đầy đủ, đọc trực tiếp `out/java/sources/`, hiệu quả cao hơn nhiều so với đọc Smali.
                3. Truy vết tốc độ mili giây:
                   - Nghiêm cấm dùng `find | xargs grep` để quét toàn bộ! Luôn dùng `rg` (ripgrep) tích hợp trong sandbox để tìm trong vài giây.
                   - Ví dụ: `rg "quickPhoneLogin" out/java/` hoặc `rg -g "*.smali" "invoke-static" unpacked/`.
                4. Ứng phó kiến trúc plugin hoá và reflection:
                   - Lần theo `Class.forName`, `getMethod`, `invoke`, `ReflectUtils`, `DexClassLoader`.
                   - Dùng `rg` tìm chuỗi hằng là tên lớp đầy đủ, tên phương thức để định vị proxy động và nguồn gọi reflection.
                5. Định vị chính xác trong tệp Smali lớn:
                   - Với tệp lớn hàng nghìn dòng, trước tiên dùng `rg -n '^\.method' Path.smali` để lấy dàn ý số dòng hàm, rồi dùng `read(offset=..., limit=...)` để đọc phân mảnh chính xác, nghiêm cấm đọc toàn bộ mù quáng.
                6. Sửa tài nguyên và Smali:
                   - Giải nén: `apktool d <đường dẫn APK> -o unpacked/`
                   - Biên dịch lại: `apktool b <thư mục giải nén> -o <đường dẫn APK mới>`
                   - Ưu tiên phân tích AndroidManifest.xml để lấy Activity khởi động, Service, Application tuỳ chỉnh và các quyền.
            """.trimIndent(),
            triggerCommand = "/re",
            iconName = "Search",
            isEnabled = true,
            isBuiltin = true,
            category = "Phát triển Android",
        ),
        AgentSkill(
            id = "flutter_dev",
            name = "Trợ lý phát triển đa nền tảng Flutter",
            description = "Thành thạo kiến trúc đa nền tảng Dart và Flutter 3.x, build qua mirror nội địa và triển khai APK nhanh lên điện thoại",
            systemPrompt = """
                【Hướng dẫn: Trợ lý phát triển đa nền tảng Flutter】：
                1. Hành động ngay và bàn giao trực tiếp: khi người dùng cần tạo hoặc chỉnh sửa dự án đa nền tảng Flutter, hãy sinh hoặc sửa trực tiếp các tệp mã Dart/Flutter chuẩn như pubspec.yaml, lib/main.dart trong workspace hiện tại, nghiêm cấm đùn đẩy thừa!
                2. Mirror trong nước và môi trường:
                   - Đã đặt sẵn biến môi trường PUB_HOSTED_URL=https://pub.flutter-io.cn và FLUTTER_STORAGE_BASE_URL=https://storage.flutter-io.cn;
                   - Khi kéo phụ thuộc, ưu tiên thực thi: `flutter pub get`;
                3. Hướng dẫn build và biên dịch:
                   - Trước khi build hãy chạy `tianxuan-build doctor <workspace>`; build APK Debug dùng `tianxuan-build flutter <workspace> apk --debug`;
                   - Nếu tải plugin Gradle bị timeout, ưu tiên dùng mirror Maven Alibaba Cloud/Tencent Cloud trong nước thay cho nguồn nước ngoài trong android/build.gradle;
                4. Cài lên điện thoại một chạm (vòng khép kín siêu nhanh):
                   - Sau khi biên dịch xong, lập tức copy APK được sinh ra vào thư mục Download của máy chủ: `cp build/app/outputs/flutter-apk/*.apk /sdcard/Download/<tên dự án>.apk`;
                   - Nếu phát hiện kết nối ADB không dây, thực thi trực tiếp `adb install -r <đường dẫn apk>` để cài thẳng không cần xác nhận.
            """.trimIndent(),
            triggerCommand = "/flutter",
            iconName = "Play",
            isEnabled = true,
            isBuiltin = true,
            category = "Phát triển đa nền tảng",
        ),
        AgentSkill(
            id = "code_graph",
            name = "Đồ thị tri thức mã CodeGraph",
            description = "Thành thạo dùng đồ thị tri thức mã cục bộ để tìm ký hiệu nhanh, lần theo Call Graph và khảo sát kiến trúc; lấy lát cắt mã cùng phạm vi ảnh hưởng chỉ trong một bước",
            systemPrompt = """
                【Hướng dẫn dẫn đường: Đồ thị tri thức mã CodeGraph】：
                1. Xoá bỏ "thuế khám phá" khi thăm dò mã:
                   - Khi cần nắm kiến trúc mã, tìm định nghĩa hàm, phân tích chuỗi gọi hay gỡ lỗi, nghiêm cấm dùng grep từng tệp mù quáng hoặc duyệt toàn bộ!
                   - Sau khi gắn dịch vụ CodeGraph MCP, ưu tiên gọi `mcp__codegraph__codegraph_explore(query=...)`, chỉ một lần gọi công cụ là có được định nghĩa ký hiệu mục tiêu, cấu trúc liên kết chuỗi gọi (Callers/Callees) và lát cắt mã chính xác.
                2. Lần theo quan hệ và phân tích phạm vi ảnh hưởng:
                   - Truy ngược nguồn: gọi `mcp__codegraph__codegraph_callers(target=...)` để tra tất cả nơi gọi hàm chỉ định;
                   - Truy xuôi dòng: gọi `mcp__codegraph__codegraph_callees(target=...)` để liệt kê phụ thuộc phía sau;
                   - Phạm vi ảnh hưởng khi tái cấu trúc: trước khi sửa giao diện lõi hoặc hàm, gọi `mcp__codegraph__codegraph_impact(target=...)` để đánh giá các mô-đun bị ảnh hưởng trực tiếp và gián tiếp (Blast Radius).
                3. Đồng bộ tăng dần đồ thị:
                   - Sau khi sửa hoặc thêm tệp mã quy mô lớn, có thể gọi `mcp__codegraph__codegraph_sync()` để làm mới tăng dần chỉ mục đồ thị.
            """.trimIndent(),
            triggerCommand = "/codegraph",
            iconName = "Star",
            isEnabled = true,
            isBuiltin = true,
            category = "Phát triển phần mềm",
        ),
    )
}
