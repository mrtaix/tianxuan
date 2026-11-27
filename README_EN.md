<p align="center">
  <img src="app/src/main/res/drawable/tianxuan_logo.webp" width="96" alt="Logo CTHarness" />
</p>

<h1 align="center">CTHarness</h1>

<p align="center"><strong>Muôn hiện tượng trong Khoảng Không Lớn.</strong></p>

<p align="center">
  Linux Runtime không cần Root trên Android · Bộ khung Agent bản địa · Terminal PTY · Không gian phát triển di động · Chẩn đoán ADB không dây
</p>

<p align="center">
  <code>v0.36.0</code> · <code>Android 10+ (SDK 29+)</code> · <code>arm64-v8a</code> · <code>Kotlin 2.4.10</code> · <code>Jetpack Compose</code>
</p>

<p align="center">
  <a href="README.md">Tiếng Việt</a> · <strong>English</strong>
</p>

---

## 🌌 Mở đầu: CTHarness là gì

Trong *Liệt tử · Vấn Đường*, có câu:

> Về phía đông của biển Bột Hải... có một hẻm núi lớn, đúng là một thung lũng không đáy. Sâu thẳm không thể dò tới đáy, người ta gọi nó là GuiXu (Trở về Khoảng Không). Nước của tám phương và chín tầng trời, dòng chảy của sông trên trời, đổ vào đó mà nó chẳng tăng cũng chẳng giảm.

**CTHarness** mượn tên và tinh thần từ đó: ngay trong những ranh giới sandbox nghiêm ngặt của Android, nó dựng lên một thế giới Linux **có thể chạy, quan sát, tự phục hồi và không ngừng tiến hóa**.

Đây không phải một lớp vỏ chat hời hợt hay một trình giả lập terminal đồ chơi. Nó cho phép LLM, công cụ MCP, user-space Linux, terminal PTY bản địa và workspace dự án chia sẻ một ngữ cảnh thực thi và chuỗi nhân quả đẳng cấu — biến ý định ngôn ngữ tự nhiên thành tệp có thể kiểm chứng, tiến trình đang sống, mã đã được xác thực và sản phẩm bản dựng Android / Flutter có thể bàn giao.

> Dựng cọc cực trong Khoảng Không Lớn; tạo thế giới trong một tấc đất.

```text
Ý định con người ─→ Phân rã tác vụ (TaskPlan) ─→ Công cụ / MCP / Linux / Trình duyệt ─→ Kiểm chứng
                           ↑                                                            ↓
                           └────────────────── Tiếp tục nếu chưa xong ──────────────────┘
```

---

## ⚡ Bảng năng lực đầy đủ

| Lĩnh vực | Năng lực cốt lõi và điểm nhấn kỹ thuật |
| :--- | :--- |
| **Linux Sandbox** | Chạy 10 bản phân phối Linux ARM64 (Ubuntu 24.04, Debian 12, Kali, Arch, Fedora, Alpine, v.v.) ở user-space nhờ **PRoot**; kéo tầng từ OCI Registry và xác minh SHA-256; rollback giao dịch hai pha, gắn thư mục bền bỉ và mount bộ nhớ dùng chung của Android. |
| **Bộ khung Agent** | Tương thích **chuẩn OpenAI** và **Anthropic Messages API**; SSE streaming, chain-of-thought (`reasoning_content` / DeepSeek / Claude), thẻ tiến độ kế hoạch tác vụ (TaskPlanCard) và luồng phê duyệt công cụ nhiều cấp. |
| **Quay lại hội thoại & Checkpoint** | **Dialogue Rewind** một chạm dựa trên **cây phân nhánh SessionFork**; tự động chụp **file snapshot (Checkpoint)** xuống đĩa trước mỗi lượt hội thoại để hoàn tác ngay các refactor rủi ro. |
| **Bộ nhớ ngữ nghĩa & Subagent** | Mô hình bộ nhớ ngữ nghĩa (giải quyết xung đột, bản sửa, mục ghim, chấm điểm theo thời gian gần đây); điều phối nhiều subagent với **write-lease wave scheduling**, gom kết quả facts có cấu trúc và bảo vệ tràn bằng phân trang. |
| **Terminal PTY bản địa** | Cầu nối JNI `openpty`/`forkpty` (`libtianxuan_pty.so`) với vòng đời tiến trình thật, terminal điều khiển, máy trạng thái ANSI/VT100, thanh phím rung và lưu bền đa phiên; fallback sang PTY `script` khi cần. |
| **Trình duyệt trong ứng dụng & CDP DevTools** | Pool WebView đa tab tích hợp và Browser MCP ngay trong tiến trình; **engine Hook** chèn bằng script, **breakpoint CDP**, **chặn Fetch cấp Worker** và bảng Timeline mạng trực quan. |
| **ADB không dây & Chẩn đoán** | **Bàn làm việc ADB không dây** chuyên biệt cấp một; **nhập mã ghép đôi trên thanh thông báo** mà không rời màn hình hiện tại, khám phá mDNS cục bộ, stream log PRoot / Android thống nhất và kiểm tra sức khỏe phần cứng một chạm. |
| **Workspace & Engine bản dựng** | Tạo dự án rỗng, nhập ZIP (có kiểm tra bảo mật Zip Slip) và **nhập kho GitHub với tiến độ clone trực tiếp**; duyệt mã, Diff trực quan theo dòng, biên dịch Gradle / Flutter nền và ký APK. |
| **Hệ sinh thái công cụ & MCP** | Nhập gói plugin offline, Registry tích hợp/ký số, trình quản lý giao dịch recipe và giải quyết phụ thuộc; **Open-WebSearch** tích hợp sẵn, đồ thị tri thức CodeGraph và hỗ trợ đầy đủ Stdio / SSE / Streamable HTTP cho MCP. |
| **Cộng tác đầu thiết bị & UI nổi** | **Cửa sổ nổi toàn màn hình** cho đa nhiệm xuyên ứng dụng, WebChat trong LAN, máy chủ tệp FTP tích hợp, chạy GGUF `llama.cpp` offline và giữ nền với FGS wake / Wi-Fi lock. |

---

## 🚀 Bắt đầu nhanh

1. **Cài đặt & Khởi chạy**: tải và cài [APK Release mới nhất](https://github.com/wkbin/tianxuan/releases) trên thiết bị ARM64 Android 10+ (khuyến nghị Android 12+).
2. **Khởi tạo sandbox**: làm theo Trình hướng dẫn để chọn bản phân phối (ví dụ Ubuntu 24.04) và hoàn tất thiết lập RootFS ban đầu qua mạng.
3. **Cấu hình mô hình**: thêm API Key LLM (DeepSeek, Claude, OpenAI, SiliconFlow, v.v.) trong Cài đặt, hoặc chạy mô hình GGUF cục bộ qua `llama.cpp` trong sandbox.
4. **Mở workspace**: tạo dự án trong Xưởng hoặc nhập kho có sẵn từ GitHub / ZIP địa phương.
5. **Kỹ thuật theo ý định**: mô tả mục tiêu cho Agent; nó sẽ tự lập kế hoạch, gọi công cụ Linux/MCP, sửa mã, chạy bản dựng và kiểm chứng kết quả.

---

## 🛠️ Xây dựng từ mã nguồn

### Yêu cầu

- **JDK**: Java 17 hoặc Java 21 (khuyến nghị JBR của Android Studio)
- **Android SDK**: compileSdk 37 / targetSdk 37 / minSdk 29
- **Android NDK**: `30.0.15729638`
- **CMake**: `3.22.1`
- **Hệ thống build**: Gradle 9.7.0 / AGP 9.3.1 / Kotlin 2.4.10

### Lệnh build

```powershell
# 1. Chuẩn bị file nhị phân native ARM64 của runtime PRoot
.\tools\prepare-proot-runtime.ps1

# 2. Đặt JAVA_HOME rồi chạy kiểm tra tuân thủ kiến trúc & unit test
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat architectureCheck --console=plain
.\gradlew.bat testDebugUnitTest --console=plain

# 3. Lắp ráp Debug APK
.\gradlew.bat assembleDebug --console=plain
```

Sản phẩm Debug APK: `app/build/outputs/apk/debug/tianxuan-v0.36.0-debug.apk`

> 📦 **Bản dựng song gói TianXuanDev**: đặt `$env:TIANXUAN_DEV_BUILD="1"` trong CI hoặc môi trường cục bộ để dựng preview APK `top.wkbin.tianxuan.dev`, có thể cài cạnh gói chính thức.

---

## 📐 Cấu trúc kiến trúc

```text
CTHarness/
├── app/                  # Ứng dụng host: MainActivity, Koin DI, mã JNI C, Foreground Service
├── core/
│   ├── model/           # Mô hình dữ liệu thuần Kotlin (tách rời hoàn toàn khỏi Android SDK)
│   ├── common/          # Dispatcher coroutine, log, Global Navigation Bus
│   ├── database/        # Room: hội thoại, luồng tin nhắn, Checkpoint, lịch sử công cụ
│   ├── datastore/       # Jetpack DataStore: tùy chọn, mount, chủ đề, trạng thái hướng dẫn
│   ├── network/         # Client OkHttp, parser SSE streaming, chính sách bảo mật
│   └── security/        # Lưu trữ mã hóa an toàn cho API Key
├── runtime/              # Lõi sandbox Linux: binding PRoot, registry tiến trình, phiên PTY, engine build
│   └── browser/         # Trình duyệt trong ứng dụng: pool WebView, engine Hook, breakpoint CDP, Browser MCP
├── project-template/     # Parser manifest template, biểu mẫu biến động & engine vật chất hóa
├── harness/              # Engine Agent: vòng lặp, inference streaming, dispatcher công cụ, subagent, MCP, rewind
├── tools/                # Hệ sinh thái: Plugin Registry, trình quản lý giao dịch Recipe, kho Provider
└── feature/              # Module UI Jetpack Compose
    ├── components/      # Hệ thống thiết kế Material 3 Expressive, icon Lucide, overlay Spotlight Guide
    ├── theme/           # Bảng màu chủ đề & hỗ trợ hình nền
    ├── home/            # Bảng điều khiển runtime, giám sát tài nguyên & kiểm tra sức khỏe phần cứng
    ├── chat/            # UI hội thoại Agent: thẻ phân rã tác vụ, view Diff, cửa sổ nổi
    ├── terminal/        # UI terminal bản địa, thanh phím rung & quản lý đa phiên
    ├── workspace/       # Quản lý dự án Xưởng, duyệt cây mã, log build & trình cài APK
    ├── settings/        # Trung tâm cài đặt: cấu hình mô hình, bàn ADB, quản lý plugin
    ├── developer/       # Sandbox nhà phát triển & bảng chẩn đoán cấp thấp
    └── onboarding/      # Trình hướng dẫn lần đầu & luồng thiết lập RootFS
```

---

## ⚠️ Ranh giới & giới hạn đã biết

- **Kiến trúc**: chỉ hỗ trợ chính thức `arm64-v8a`. Các ABI khác sẽ dừng trong quá trình khởi tạo.
- **PRoot ở user-space**: PRoot chặn syscall qua `ptrace`. Đây không phải ảo hóa phần cứng hay máy KVM, và không cung cấp quyền Root hay module kernel tùy biến.
- **Khả năng tương thích**: TUI phức tạp và tác vụ biên dịch nặng phụ thuộc vào RAM và hiệu năng CPU của thiết bị.
- **Bảo mật**: API từ xa và endpoint tải xuống đều yêu cầu HTTPS; không bao giờ commit khóa API hay thông tin xác thực nhạy cảm vào kho công khai.

---

## 📜 Lời bạt triết học

> Núi Tu Di được chứa trong hạt cải; Khoảng Không Lớn được chứa trong lòng bàn tay.

Giới hạn không bao giờ thực sự biến mất; nhưng tự do mà kỹ thuật mang lại thường đến từ việc hiểu ranh giới, lập luật trong đó và có năng lực dựng lên thế giới của riêng mình.

Rất hoan nghênh gửi Issue, Pull Request hoặc chia sẻ trải nghiệm sử dụng trên thiết bị thật!
