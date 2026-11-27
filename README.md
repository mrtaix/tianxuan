<p align="center">
  <img src="app/src/main/res/drawable/tianxuan_logo.webp" width="96" alt="Logo CTHarness" />
</p>

<h1 align="center">CTHarness</h1>

<p align="center"><strong>CTHarness trong lòng bàn tay, với Bảy Sao làm trục.</strong></p>

<p align="center">
  Linux Runtime không cần Root trên Android · Bộ khung Agent AI · Workflow tự động hóa trực quan · Terminal PTY bản địa · Không gian làm việc phát triển di động
</p>

<p align="center">
  <code>v0.36.0</code> · <code>Android 10+ (API 29+)</code> · <code>arm64-v8a</code> · <code>Kotlin 2.4.10</code> · <code>Jetpack Compose</code>
</p>

<p align="center">
  <a href="https://github.com/xiaozhe7772222/tianxuan/releases">Releases</a> ·
  <a href="https://github.com/xiaozhe7772222/tianxuan/issues">Issues</a> ·
  <strong>Tiếng Việt</strong> · <a href="README_EN.md">English</a>
</p>

---

## 🌌 CTHarness là gì

Theo *Dịch · quẻ Khôn · Văn ngôn*: "Huyền hoàng là sắc pha trộn của trời và đất, **trời huyền mà đất hoàng**". *Thuyết văn* lại viết: "Huyền là xa sâu; đen mà pha sắc đỏ gọi là huyền."

**Huyền** là màu gốc của bầu trời — không phải đen thuần, mà là màu của không gian sâu có pha sắc đỏ, là sắc xanh lam mờ trước bình minh ở chân trời phía đông. CTHarness lấy ý **ngự**: không chứa muôn vật bằng vực sâu, mà cai quản vận hành bằng đạo trời.

*Sử ký · Thiên quan thư* có câu: "**Đẩu là xe của ngụy, vận hành giữa trung tâm, cai quản bốn phương; phân âm dương, lập bốn mùa, điều hòa ngũ hành, dời tiết độ, định các kỷ cương — tất cả đều nằm ở Đẩu.**"

Đẩu là xe của ngụy, bảy ngôi sao là bảy càng xe. CTHarness lấy **Bắc Đẩu làm cương, hai mươi tám chòm sao làm mắt, Tứ Tượng làm vùng, Tam Viên làm kho**, viết hệ tọa độ thiên văn cổ đại thực sự này vào chính mã nguồn — chứ không chỉ viết trong khẩu hiệu quảng cáo.

| Tầng | Ý nghĩa | Hiện thực hóa |
| :--- | :--- | :--- |
| **Bảy sao Bắc Đẩu** | Đẩu là xe ngụy, bảy năng lực trục chính | Thiên Xu (trục trí tuệ) · Thiên Huyền (xưởng trí tuệ) · Thiên Cơ (terminal) · Thiên Quyền (workflow) · Ngọc Hành (phiên bản) · Khai Dương (trình duyệt) · Dao Quang (cài đặt) |
| **Hai mươi tám chòm sao** | Hai mươi tám vùng sao, thước tọa độ của nhật nguyệt và ngũ hành | Đông phương Thanh Long＝tầng engine agent · Bắc phương Huyền Vũ＝tầng đáy runtime · Tây phương Bạch Hổ＝tầng công cụ và tích hợp · Nam phương Chu Tước＝tầng trình bày và trải nghiệm |
| **Tam Viên** | Tử Vi／Thái Vi／Thiên Thị, chỗ ở của ngụy, triều chính và chợ trên trời | Kho bộ nhớ ba tầng: Tử Vi Viên＝bộ nhớ lõi · Thái Vi Viên＝bộ nhớ làm việc · Thiên Thị Viên＝bộ nhớ truy xuất |

> Khoảng cách của hai mươi tám chòm sao theo *Hoài Nam tử · Thiên huấn huấn* ("phân độ của các sao"), phương Đông 75¼ (gồm cả phần tư của chòm Cơ), phương Bắc 98, phương Tây 80, phương Nam 112, Tứ Tượng cộng lại **365¼ độ**, khớp với chu vi bầu trời. Mỗi chòm gắn với một thành phần đáy và một chỉ số quan sát được; log và bảng chẩn đoán được phân vùng theo chòm sao, tạo thành khả năng quan sát dạng "bản đồ sao".

CTHarness xây dựng, trong các giới hạn sandbox và quyền của ứng dụng Android, một môi trường Linux và tự động hóa AI **có thể chạy, quan sát, phục hồi và tiến hóa**. Nó không phải là một lớp giao diện chat bọc lấy mô hình lớn, cũng không chỉ là trình giả lập terminal. CTHarness cho phép Agent, công cụ MCP, tiến trình Linux, PTY bản địa, trình duyệt, Git và workspace dự án chia sẻ ngữ cảnh thực thi; workflow trực quan mở rộng một cuộc hội thoại thành tác vụ tự động hóa có thể lưu bền, phê duyệt và hẹn giờ. Bạn bắt đầu từ một ý định bằng ngôn ngữ tự nhiên, và cuối cùng thu được tệp thật, tiến trình đang chạy, mã đã được kiểm chứng hoặc sản phẩm bản dựng Android / Flutter có thể cài đặt.

> CTHarness trong lòng bàn tay, với Bảy Sao làm trục.

```text
Ý định của người ─→ Agent lập kế hoạch ─→ Linux / công cụ / MCP / trình duyệt / năng lực host ─→ Kiểm chứng và bàn giao
                  ↑                                                   ↓
                  └──────────── Chưa xong thì tiếp tục, cần thì phê duyệt ──────────────┘

Workflow bền bỉ ─→ nhánh điều kiện / node song song / lịch hẹn giờ / chạy nền / phê duyệt thủ công
```

---

## ⚡ Năng lực cốt lõi

| Lĩnh vực | Tổng quan năng lực |
| :--- | :--- |
| **Linux Runtime** | Chạy Linux ARM64 trên Android không Root dựa trên **PRoot**; cài đặt và xác minh RootFS từ OCI Registry, hỗ trợ nhiều bản phân phối cùng tồn tại, chuyển đổi, rollback, gắn thư mục bền bỉ và mount bộ nhớ dùng chung của Android. |
| **Bộ khung Agent** | Hỗ trợ OpenAI-compatible Chat Completions, OpenAI Responses API và Anthropic Messages API; xử lý thống nhất streaming nội dung, reasoning, function calling, đầu vào hình ảnh, token usage và nén context. **Cải tiến căn chỉnh DSH**: registry chiếu hội thoại (Stats/TurnOutline/PlanMode), cân bằng tool-pairing khi nén, giữ ngân sách Spill token, registry Provider cho skill, PTC đầy đủ (điều phối con đồng thời + commit có thứ tự), nhắc nhở tool call trùng lặp, áp đặt timeout cho từng công cụ, registry công việc, sự kiện kiểm toán phê duyệt. |
| **Độ tin cậy và phục hồi** | Hội thoại bền bỉ, kế hoạch công việc, bộ nhớ ngữ nghĩa, nén context tự động/thủ công, đầu ra công cụ lớn ghi xuống đĩa và phục hồi sau gián đoạn; hỗ trợ SessionFork quay lại hội thoại, file checkpoint, phát hiện xung đột sửa đổi ngoài và hoàn tác rewind gần nhất. **Cải tiến căn chỉnh DSH**: phân loại TurnEndReason bảy trạng thái, SurfaceOp append/replace, truy vết dòng dõi hội thoại (DB schema 57), synthesis marker Interrupted khi phục hồi sau crash, ngân sách đệ quy cho độ sâu delegating. |
| **Hợp tác subagent** | Hỗ trợ điều phối nhiều subagent, phán quyết kết quả có cấu trúc, ngân sách token, write lease và phân trang đầu ra vượt ngưỡng xuống đĩa, giảm xung đột ghi đồng thời và phình context. **Cải tiến căn chỉnh DSH**: lưu bền tác vụ subagent để phục hồi nguội, áp đặt độ sâu đệ quy (MAX_DELEGATION_DEPTH=5). |
| **Workflow trực quan** | DAG Kotlin bền bỉ, cạnh điều kiện và node song song; kết hợp được Bash, tiến trình nền, Agent, Subagent, bản dựng, hành động host và phê duyệt thủ công, đồng thời hỗ trợ lịch sử chạy, thực thi nền, thông báo phê duyệt và lịch WorkManager một lần／khoảng cách／hàng ngày. |
| **Terminal PTY bản địa** | Backend JNI dùng `openpty + fork + setsid + TIOCSCTTY + execve` để tạo terminal điều khiển thật, sản phẩm là `libpty_native.so`; kết hợp Termux terminal-emulator / TerminalView để hỗ trợ job control, Ctrl+C, thay đổi kích thước cửa sổ và đa phiên, fallback qua kênh `script` khi backend bản địa không khả dụng. **Cải tiến căn chỉnh DSH**: giao thức PTY readiness (marker OSC 133;D), TerminalSanitizer lọc streaming, BoundedTextBuffer đệm có giới hạn, PtyReadinessTracker xác định sẵn sàng bằng chuỗi bằng chứng ba cấp. |
| **Không gian làm việc và bản dựng di động** | Tạo dự án rỗng, nhập ZIP cục bộ (có bảo vệ Zip Slip) hoặc clone kho GitHub; cung cấp cây mã, Diff theo dòng, bản dựng Gradle / Flutter nền trong sandbox, ký APK và cài đặt. |
| **Bàn làm việc Git trên thiết bị** | Quản lý trực tiếp workspace host bằng JGit, hỗ trợ thay đổi, staged, commit, branch, đồ thị commit, tag, push/pull, xem trước khi push, thông báo commit bằng AI và Token HTTPS được mã hóa. |
| **Tự động hóa trình duyệt và Web** | Pool WebView đa tab tích hợp sẵn và Browser MCP; hỗ trợ Hook trang, breakpoint CDP, chặn Fetch cấp Worker, timeline mạng và hiển thị trạng thái debug. |
| **Hệ sinh thái MCP và công cụ** | Các preset tích hợp sẵn Browser, SQLite, Git, kiểm toán APK, CodeGraph, WebSearch; hỗ trợ STDIO, Streamable HTTP dạng request-response thông dụng và legacy SSE, dịch vụ từ xa có thể dùng OAuth Authorization Code + PKCE. |
| **ADB không dây và tự động hóa host** | Phát hiện mDNS, nhập mã ghép đôi trên thanh thông báo, log Android / PRoot, kiểm tra sức khỏe thiết bị và chẩn đoán Intent; sau khi người dùng cấp quyền, có thể thực hiện các cấp độ tự động hóa host và GUI khác nhau qua ADB không dây, Shizuku, Root hoặc accessibility. |
| **Cộng tác trên thiết bị** | Trợ lý nổi toàn cục, WebChat LAN kèm PIN tạm, truyền tệp FTP, cùng khả năng giữ tác vụ dài hạn với FGS, WakeLock và Wi-Fi Lock. |

> Phiên bản hiện tại v0.36.0 đã hoàn thành căn chỉnh toàn bộ cơ chế cốt lõi của DeepSeek Harness (33 cải tiến), bao gồm log sự kiện hội thoại, truy vết dòng dõi, an toàn khi nén, hệ thống chiếu, PTY readiness, registry skill, runtime PTC, chính sách guard, registry công việc, kiểm toán phê duyệt v.v. Tất cả cải tiến đều đã được tích hợp thực tế vào chuỗi chạy (từ v0.36.0 trở đi được lắp ráp qua Koin và có hiệu lực trên các đường thực thi HarnessLoop / ToolExecutor / SubagentOrchestrator / ApprovalPolicyEngine / NativePtySession). Tính năng ổn định và gói cài đặt vui lòng xem trang [Releases](https://github.com/xiaozhe7772222/tianxuan/releases).

---

## 🧠 Độ tin cậy và bảo mật của Agent

CTHarness xem Agent là một hệ thống thực thi chạy dài và thực sự sửa đổi workspace, chứ không phải giao diện hỏi – đáp dùng một lần:

- **Phê duyệt công cụ phân cấp**: các thao tác nguy hiểm có thể yêu cầu xác nhận, tách biệt giữa ủy quyền cấp phiên và chính sách toàn cục.
- **Hội thoại có thể phục hồi**: streaming, tool call, kế hoạch, bộ nhớ và lịch sử thực thi được lưu bền bỉ, có thể tiếp tục sau gián đoạn.
- **Quản trị context**: hỗ trợ nén tự động/thủ công, replay khi tràn, ngân sách token và ghi đầu ra công cụ lớn xuống đĩa.
- **Fallback an toàn**: tạo file checkpoint trước mỗi vòng; khi rewind sẽ phát hiện sửa đổi ngoài phiên và báo xung đột, có thể hoàn tác lần khôi phục tệp gần nhất khi thỏa điều kiện.
- **Song song có kiểm soát**: subagent dùng write lease, kết quả có cấu trúc và ràng buộc điều phối để giảm nguy cơ ghi đè lên thay đổi của nhau.
- **Ranh giới thông tin nhạy cảm**: khóa mô hình, Git Token và OAuth Token dùng bộ nhớ an toàn phía ứng dụng; thông tin nhạy cảm không nên ghi vào workspace hay commit vào kho.

---

## 🚀 Bắt đầu nhanh

### 1. Cài đặt

Cài [APK Release mới nhất](https://github.com/xiaozhe7772222/tianxuan/releases) trên thiết bị **ARM64, Android 10+** (khuyến nghị Android 12+). Trang Release cũng cung cấp sản phẩm bản dựng và SHA-256, nên kiểm tra trước khi cài.

### 2. Khởi tạo Linux

Sau lần đầu khởi động, làm theo 「Trình hướng dẫn khởi hành」 để cài RootFS. Mặc định khuyến nghị Ubuntu 24.04 LTS, cũng có thể chọn bản phân phối được hỗ trợ khác. RootFS không được đóng gói trong APK, lần cài đầu cần kết nối mạng và đủ dung lượng lưu trữ.

### 3. Cấu hình mô hình và công cụ

Trong phần cài đặt, thêm dịch vụ mô hình và API Key. Có thể kết nối các dịch vụ đám mây như DeepSeek, Claude, OpenAI, SiliconFlow, hoặc kết nối endpoint inference cục bộ tương thích OpenAI trong sandbox hoặc LAN, ví dụ dịch vụ dạng llama.cpp / Ollama.

Bật theo nhu cầu MCP, ADB không dây, cửa sổ nổi, thông báo, cài APK, Shizuku hoặc quyền accessibility; khi không dùng năng lực đó thì không cần cấp quyền.

### 4. Tạo workspace

Tạo dự án mới trong Xưởng, hoặc nhập dự án có sẵn từ GitHub / ZIP địa phương. Sau đó mô tả mục tiêu trong Trục trí tuệ để Agent đọc/ghi mã, gọi công cụ, chạy test và bàn giao sản phẩm bản dựng; cũng có thể biến tác vụ lặp lại thành workflow trực quan.

### 5. Các lối vào thông dụng

- **Trục trí tuệ**: hội thoại Agent, kế hoạch, tool call, subagent và thay đổi mã.
- **Terminal**: vào phiên PTY thật của bản phân phối Linux hiện tại.
- **Xưởng**: quản lý dự án, duyệt mã nguồn, Diff, bản dựng và bàn giao APK.
- **Workflow**: dựng DAG, chạy tác vụ nền, thiết lập phê duyệt và lịch hẹn.
- **Git**: quản lý thay đổi, branch, commit, tag và đồng bộ từ xa.
- **Trình duyệt**: truy cập web, debug, Hook và tự động hóa Browser MCP.
- **ADB không dây**: ghép đôi thiết bị, xem log và chạy chẩn đoán trong phạm vi được cấp quyền.

> Khi chạy Agent, bản dựng hoặc workflow thời gian dài, nên cho phép thông báo và thêm CTHarness vào danh sách miễn tối ưu pin của hệ thống. Các giới hạn nền của Android vẫn có thể ảnh hưởng đến thời gian hoàn thành tác vụ.

---

## 🐧 Các bản phân phối Linux được hỗ trợ

Hiện cung cấp 10 RootFS ARM64:

- Ubuntu 24.04 LTS (mặc định)
- Debian 12
- Kali Rolling
- Arch Rolling
- Fedora 40
- Alpine 3.19
- AlmaLinux 9
- Rocky Linux 9
- openSUSE Tumbleweed
- Manjaro Rolling

RootFS được lấy qua image OCI, không phân phối cùng APK. Tính sẵn có và khả năng tương thích phần mềm của từng bản phân phối phụ thuộc vào kho ARM64 và môi trường user-space của PRoot.

---

## 🔌 Giao thức mô hình và công cụ

### API mô hình

- OpenAI-compatible Chat Completions
- OpenAI Responses API
- Anthropic Messages API
- SSE streaming cho nội dung và reasoning
- Native function calling và giao thức công cụ dạng văn bản JSON
- Đầu vào hình ảnh, thống kê usage, ngân sách context và nén theo từng mô hình

### MCP

- STDIO JSON-RPC
- Streamable HTTP dạng request-response thông dụng
- Legacy SSE
- OAuth Authorization Code + PKCE, làm mới Token và callback
- Preset tích hợp sẵn Browser, SQLite, Git, kiểm toán APK, CodeGraph, WebSearch
- Khám phá năng lực theo nhu cầu và kết nối trễ

---

## 🛠️ Xây dựng từ mã nguồn

### Yêu cầu cho bản dựng APK chuẩn

- **JDK 17** (baseline của dự án và CI, khuyến nghị JBR của Android Studio)
- **Android SDK**: compileSdk 37 / targetSdk 37 / minSdk 29
- **Gradle Wrapper**: 9.7.0
- **Android Gradle Plugin**: 9.3.1
- **Kotlin**: 2.4.10
- Checkout Git đầy đủ, bao gồm AAR Maven cục bộ và thư viện native ARM64 biên dịch sẵn trong kho

Bản dựng APK thông thường dùng trực tiếp sản phẩm native biên dịch sẵn trong kho; chỉ khi biên dịch lại các thành phần native như PTY mới cần thêm **Android NDK 30.0.15729638, CMake 3.22.1 và Ninja**.

### Windows

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"

.\gradlew.bat architectureCheck --console=plain
.\gradlew.bat test --console=plain
.\gradlew.bat assembleDebug --console=plain
```

### macOS / Linux

```bash
export JAVA_HOME="/path/to/jdk-17"

./gradlew architectureCheck --console=plain
./gradlew test --console=plain
./gradlew assembleDebug --console=plain
```

Debug APK được xuất mặc định tại:

```text
app/build/outputs/apk/debug/tianxuan-v0.36.0-debug.apk
```

### Bước tùy chọn

Sau checkout đầy đủ bình thường thì không cần chuẩn bị PRoot thủ công. Chỉ chạy lệnh dưới đây khi `app/src/main/jniLibs/arm64-v8a/libproot*.so` bị thiếu, kiểm tra thất bại hoặc cần làm mới sản phẩm PRoot chính thức:

```powershell
.\tools\prepare-proot-runtime.ps1
```

Đặt `TIANXUAN_DEV_BUILD=1` để dựng bản preview **TianXuanDev** với tên gói `top.wkbin.tianxuan.dev`, cùng tồn tại độc lập với gói chính thức `top.wkbin.tianxuan`:

```powershell
$env:TIANXUAN_DEV_BUILD="1"
.\gradlew.bat assembleDebug --console=plain
```

Thêm lệnh build, test, ký và chẩn đoán tại [docs/COMMANDS.md](docs/COMMANDS.md).

---

## 📐 Cấu trúc module

```text
CTHarness/
├── app/                  # vỏ ứng dụng, lắp ráp Koin, Manifest, JNI và foreground service
├── baselineprofile/      # Baseline Profile và điểm chuẩn hiệu năng khởi động
├── core/
│   ├── model/            # mô hình domain thuần Kotlin
│   ├── common/           # dispatcher, log và hạ tầng dùng chung
│   ├── database/         # Room: hội thoại, tin nhắn, bộ nhớ, workflow, OAuth, lịch sử thực thi
│   ├── datastore/        # DataStore: tùy chọn người dùng, giao diện, mount và trạng thái hướng dẫn
│   ├── network/          # OkHttp, SSE và chính sách mạng
│   ├── security/         # khóa, Token và lưu trữ an toàn
│   └── browser/          # mô hình và giao thức dùng chung của trình duyệt
├── runtime/              # runtime PRoot, RootFS, PTY, tiến trình, lưu trữ và workspace
│   └── browser/          # pool WebView, Hook, CDP, timeline mạng và Browser MCP
├── project-template/     # template dự án, biểu mẫu động và engine vật chất hóa
├── harness/              # vòng lặp Agent, Provider, công cụ, phê duyệt, subagent và MCP
├── tools/                # Registry, Recipe, giao dịch cài đặt, phụ thuộc và kho Provider
└── feature/              # tính nghiệp vụ Jetpack Compose
    ├── theme/            # chủ đề và hệ thống thị giác
    ├── components/       # component dùng chung, icon và hướng dẫn Spotlight
    ├── navigation/       # route và lối vào Navigation3
    ├── home/             # trạng thái trang chủ và chẩn đoán
    ├── chat/             # hội thoại Agent, kế hoạch, Diff và trợ lý nổi
    ├── terminal/         # giao diện terminal và đa phiên
    ├── workspace/        # Xưởng, duyệt mã nguồn, bản dựng và bàn giao
    ├── browser/          # giao diện trình duyệt tích hợp
    ├── workflow/         # trình sửa DAG, chạy, phê duyệt và lịch hẹn
    ├── git/              # bàn làm việc JGit
    ├── settings/         # mô hình, MCP, ADB, plugin và cài đặt hệ thống
    ├── developer/        # sandbox nhà phát triển và chẩn đoán cấp thấp
    ├── custom_iteration/ # năng lực lặp tùy chỉnh
    └── onboarding/       # lần đầu khởi động và khởi tạo RootFS
```

Tầng nghiệp vụ của ứng dụng Android chủ yếu dùng Kotlin và Jetpack Compose; tầng đáy còn có các thành phần C/JNI, Python, JavaScript, Shell và TypeScript.

---

## 📚 Điều hướng tài liệu

- 🧭 [Tổng quan điều hướng ngữ nghĩa AI](docs/AI_NAVIGATION.md)
- 🏗️ [Kiến trúc hệ thống và cấu trúc module](docs/ARCHITECTURE.md)
- ⏱️ [Các chuỗi lời gọi runtime chính](docs/EXECUTION_TRACES.md)
- 📜 [Nguyên tắc thiết kế kiến trúc và UI](docs/ARCHITECTURE_RULES.md)
- 🔀 [Workflow trực quan](docs/WORKFLOW.md)
- 🌐 [Trình duyệt tích hợp và engine Hook](docs/BROWSER_DESIGN.md)
- 🔌 [Hướng dẫn phát triển hệ sinh thái plugin](docs/PLUGIN_DEVELOPMENT_GUIDELINES.md)
- 📦 [Giới thiệu plugin ngoại tuyến Android](docs/ANDROID_OFFLINE_PLUGIN.md)
- 💾 [Quản lý lưu trữ](docs/STORAGE_MANAGEMENT.md)
- 🧱 [ADR về sandbox backend](docs/ADR_SANDBOX_BACKEND.md)
- 🩺 [Vấn đề đã biết](docs/KNOWN_ISSUES.md)
- 🧪 [Lệnh build, test và debug](docs/COMMANDS.md)

---

## ⚠️ Quyền, ranh giới và giới hạn đã biết

- **Chỉ hỗ trợ ARM64**: hiện chỉ tương thích `arm64-v8a`, các ABI khác nằm ngoài phạm vi hỗ trợ.
- **PRoot không phải máy ảo**: nó cung cấp môi trường Linux thông qua chặn syscall ở user-space và viết lại đường dẫn, không có KVM, quyền Root hay khả năng tải module kernel.
- **Lần cài đầu cần mạng**: RootFS không đóng gói cùng APK, khi cài cần tải image OCI và chiếm thêm dung lượng lưu trữ.
- **Năng lực host phụ thuộc vào ủy quyền**: ADB không dây, Shizuku, Root và accessibility cung cấp các cấp quyền khác nhau; các hành động host nâng cao sẽ không tự động có năng lực khi chưa được cấp quyền.
- **Tác vụ nền chịu giới hạn của Android**: foreground service và WorkManager có thể tăng độ tin cậy, nhưng Doze, chính sách tiết kiệm pin của nhà sản xuất và giới hạn thời gian foreground service trên Android phiên bản mới vẫn có thể gây trễ hoặc gián đoạn.
- **Cẩn trọng khi bật dịch vụ LAN**: WebChat và FTP chỉ nên bật chủ động trong mạng tin cậy và tắt sau khi dùng.
- **Khả năng tương thích tùy thiết bị**: TUI phức tạp, tổ hợp phím với bàn phím mềm cụ thể và biên dịch chéo C/C++ nặng chịu ảnh hưởng của hiệu năng thiết bị, bộ nhớ và khả năng tương thích của PRoot.
- **Bảo vệ thông tin nhạy cảm**: hãy cất giữ cẩn thận API Key của mô hình, Git Token và thông tin xác thực dịch vụ từ xa, tuyệt đối không commit vào kho công khai.

---

## 🤝 Tham gia đóng góp

Rất hoan nghênh [Issue](https://github.com/wkbin/tianxuan/issues), Pull Request hoặc chia sẻ nhật ký sử dụng trên thiết bị thật của bạn. Trước khi bắt đầu sửa, nên đọc [AI_NAVIGATION.md](docs/AI_NAVIGATION.md) và tài liệu module tương ứng, đồng thời chạy kiểm tra kiến trúc và các test liên quan.

---

## 📜 Lời bạt

> Tu Di nạp trong hạt cải, CTHarness nạp trong lòng bàn tay.

Giới hạn không bao giờ thực sự biến mất; nhưng tự do có thể đến từ việc, ngay trong giới hạn, vẫn có năng lực dựng lên và kiểm chứng một thế giới của riêng mình.
