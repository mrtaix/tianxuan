# 🏗️ CTHarness — Kiến trúc hệ thống và cấu trúc module

---

## 1. 🚀 Định vị dự án và tóm tắt nền tảng công nghệ

- **Định vị cốt lõi**: Chạy một **sandbox Linux** đầy đủ dựa trên **PRoot** trong **không gian người dùng Android không cần Root**, tích hợp sâu **bộ máy Agent AI Harness**, **terminal PTY gốc**, **quản lý tệp trong không gian làm việc** và **giao diện Material 3 Expressive**.
- **Tầng Android / giao diện**:
  - **Chuỗi công cụ dựng của dự án chính**: Gradle 9.7.0 / AGP 9.3.1 / Kotlin 2.4.10 / compileSdk 37.1 / targetSdk 37 / NDK 30.0.15729638
  - **Ngôn ngữ**: Kotlin (100% Kotlin + Jetpack Compose)
  - **Phong cách giao diện**: Material 3 Expressive (bề mặt động M3, phản hồi xúc giác, bố cục thích ứng hai cột)
  - **Tiêm phụ thuộc**: Koin 4.2.2 (Kotlin DSL tường minh)
  - **Trạng thái và bất đồng bộ**: Kotlin Coroutines, StateFlow, SharedFlow
  - **Lưu trữ cục bộ**: Jetpack DataStore (tùy chọn người dùng), Room Database (SQLite)
- **Môi trường thực thi tầng dưới và mã gốc**:
  - **C/C++ JNI**: `app/src/main/cpp/pty.c` (openpty, fork, exec, ioctl — cầu nối terminal PTY, biên dịch thành `libtianxuan_pty.so`, `arm64-v8a`)
  - **Container Linux**: bộ máy PRoot tích hợp sẵn, khởi động hệ thống tệp gốc Debian
  - **Ánh xạ bộ nhớ phía chủ**: cơ chế gắn kết gốc `-b <host>:<guest>` của PRoot (Download, Documents, /sdcard)
- **Bộ máy Agent thông minh (Agent Harness)**:
  - **Tương thích giao thức**: chuẩn OpenAI Chat Completion API (hỗ trợ truyền luồng SSE, quá trình suy nghĩ `reasoning_content`, gọi công cụ hàm `tool_calls`)
  - **Công cụ tích hợp**: `read` / `write` / `edit` / `base` / `process`, có thể mở rộng thêm công cụ MCP và Agent con
  - **Vòng đời lệnh**: `base` dùng cho lệnh chạy trực tiếp có giới hạn, thời gian chờ mặc định do người dùng cấu hình; `process` dùng `ProcessRegistry` của môi trường thực thi để quản lý các lệnh chạy xuyên nhiều lần gọi công cụ
  - **Mô hình cục bộ trên thiết bị**: sandbox hỗ trợ chạy trực tiếp `llama.cpp` và `Ollama`, không cần khóa API
- **Bộ công cụ phát triển Android trong sandbox (đừng nhầm với chuỗi công cụ dựng của dự án chính)**: Gradle 8.14.2 / AGP 8.11.1 / Kotlin 2.2.20 / compileSdk & targetSdk 34 / NDK 29.0.14206865, chỉ phục vụ dựng APK Android/Flutter trong sandbox ARM64.

---

## 2. 🗺️ Cấu trúc module và trách nhiệm

```text
TianXuan/
├── app/                  # Khung ứng dụng: MainActivity, khởi tạo Koin, mã C JNI, Service tiền cảnh giữ tiến trình
├── core/
│   ├── model/           # Mô hình dữ liệu Kotlin thuần (không phụ thuộc Android SDK)
│   ├── common/          # Bộ điều phối coroutine, nhật ký, lớp tiện ích dùng chung
│   ├── database/        # Cơ sở dữ liệu Room: hội thoại Harness, phiên, lịch sử thực thi công cụ
│   ├── datastore/       # Jetpack DataStore: tùy chọn người dùng, gắn kết bộ nhớ, mô hình đang dùng, thời gian chờ lệnh Agent
│   ├── network/         # Máy khách OkHttp, bộ phân tích luồng SSE, chính sách thời gian chờ
│   └── security/        # Mã hóa và giải mã khóa API an toàn tại thiết bị
├── runtime/              # Lõi sandbox Linux và PRoot: đăng ký lệnh/tiến trình, PTY, nhập/xuất và dựng không gian làm việc
├── project-template/     # Mẫu dự án chuẩn hóa: manifest, giao thức biểu mẫu biến, nhập/xuất, tạo dự án thực và tài nguyên mẫu tích hợp
├── harness/              # Lõi Agent: vòng lặp Agent, suy luận dạng luồng, công cụ/phê duyệt tích hợp, MCP
├── tools/                # Trung tâm hệ sinh thái công cụ: Registry, plugin cục bộ, giao dịch cài đặt, cài thành phần hàng loạt, bảo mật Provider
└── feature/              # Tầng tính năng nghiệp vụ Compose UI
    ├── components/      # Quy chuẩn thiết kế CTHarness M3 Expressive, thành phần dùng chung (RuntimeCard, TopBar, Icons)
    ├── theme/           # Bảng màu, phông chữ và cấu hình chủ đề Material 3
    ├── home/            # Bảng điều khiển trang chủ: trạng thái Linux, giám sát bộ nhớ/đĩa/tiến trình theo thời gian thực
    ├── chat/            # Giao diện hội thoại Agent Tri-Shu, thẻ phân tách tác vụ TaskPlanCard, bố cục hai cột trên màn hình rộng
    ├── terminal/        # Giao diện terminal Compose và thanh phím xúc giác; phiên/máy trạng thái VT100 nằm trong runtime
    ├── workspace/       # Quản lý không gian làm việc: tạo/nhập ZIP/GitHub, xuất, duyệt mã, dựng nền
    ├── workflow/        # Danh mục quy trình, khung vẽ DAG, dòng thời gian thực thi và giao diện phê duyệt thủ công
    ├── settings/        # Trung tâm cài đặt: hồ sơ mô hình, thời gian chờ Agent, công cụ/plugin cục bộ, gắn kết bộ nhớ, giao diện và chẩn đoán
    ├── developer/       # Sandbox gốc và bảng chẩn đoán dành cho nhà phát triển
    ├── onboarding/      # Hướng dẫn lần đầu khởi động và quy trình giải nén, chuẩn bị RootFS
    ├── custom_iteration/ # Điểm vào tính năng cải tiến tùy chỉnh (AI sửa ứng dụng)
    └── navigation/      # Tuyến điều hướng cấp cao nhất và điều phối NavHost
```

---

## 3. 🔗 Nguyên tắc tương tác liên module

1. **Luồng phụ thuộc một chiều**:
   - Hướng phụ thuộc giữ nghiêm ngặt `feature/*` → `core/*` / `runtime` / `harness` / `tools`.
   - Giữa các module tính năng nghiệp vụ **nghiêm cấm** phụ thuộc ngang nhau, chỉ được phụ thuộc giao diện dùng chung `feature/components` và `feature/theme`; tầng cao nhất `feature/navigation` đảm nhiệm lắp ráp trang và điều phối tuyến.
2. **Cách ly lưu trữ bền vững**:
   - Các tầng tính năng / thực thi / Harness chỉ được phụ thuộc giao diện Repository do `core/database/PersistenceRepositories.kt` cung cấp, nghiêm cấm tiêm trực tiếp hay thao tác Room DAO.
   - Cấu hình tùy chọn thống nhất đọc/ghi qua các giao diện theo miền nghiệp vụ do `core/datastore/PreferenceFacades.kt` cung cấp.
3. **Cách ly tầng mô hình thuần**:
   - `:core:model` phải giữ Kotlin thuần, **nghiêm cấm** đưa vào `android.*`, `androidx.*` hay phụ thuộc Compose.
   - Tác vụ `architectureCheck` đã nối vào giai đoạn xác thực `app:preBuild`, chặn việc đưa nền tảng vào tầng mô hình và các phụ thuộc vi phạm.
4. **Ranh giới thực thi lệnh**:
   - Lệnh chạy trực tiếp ngắn, có trạng thái thoát rõ ràng gọi `LinuxRuntime.execute()` / `base` của Harness.
   - Dịch vụ hoặc tác vụ dài chạy xuyên nhiều lần gọi dùng `LinuxRuntime.startBackground()` / `process` của Harness, thống nhất do `ProcessRegistry` quản lý PID, vòng đời và bộ đệm nhật ký dạng vòng.
5. **Ranh giới thao tác không gian làm việc**:
   - Vòng đời dự án và tương tác thư mục vật lý thống nhất qua `WorkspaceManager` và `WorkspaceFileService`.
   - Nhập ZIP phải phòng chống vượt đường dẫn (Zip Slip), kiểm tra giới hạn kích thước tệp và số mục; sao chép kho Git phải chạy cô lập trong sandbox.
6. **Ranh giới điều phối dựng và giữ tiến trình**:
   - Việc dựng không gian làm việc do đối tượng duy nhất cấp tiến trình `WorkspaceBuildTaskCoordinator` giữ toàn cục, xoay hay dựng lại giao diện không làm ngắt tác vụ dựng; phần thực thi dựng và tiền kiểm tra môi trường do `WorkspaceBuildRunner` đảm nhiệm.

## 4. Lắp ráp phụ thuộc (Koin)

- Tệp `src/main/java/top/wkbin/tianxuan/di/**/KoinModule.kt` của mỗi module khai báo liên kết hàm khởi tạo và giao diện của module đó; `app/di/TianXuanModules.kt` tổng hợp toàn bộ đồ thị phụ thuộc, đăng ký Application Context lúc Application khởi động và cấm ghi đè định nghĩa.
- Lớp nghiệp vụ dùng hàm khởi tạo thông thường; chỉ các điểm vào do Android tạo là Application, Activity, Service, Receiver và Compose ViewModel mới truy cập Koin. Khi thêm phụ thuộc vào hàm khởi tạo, cập nhật đồng bộ phần đăng ký của module tương ứng và chạy kiểm thử đồ thị phụ thuộc `:app:testDebugUnitTest`.
- Đối tượng duy nhất cấp tiến trình dùng `single`, đối tượng không có phạm vi dùng `factory`, ViewModel dùng `viewModel`, giữ quyền sở hữu ViewModelStoreOwner của Navigation3.
- Kotlin `Lazy` giữ việc tạo trễ của môi trường thực thi, kho dữ liệu và máy chủ MCP; Agent con dùng `() -> ToolExecutor` để cắt vòng phụ thuộc. Các tùy chọn của ToolExecutor được lắp tường minh qua hàm khởi tạo.
- Tập bộ thực thi quy trình và tập bộ thích nghi công cụ dùng định danh phân biệt khác nhau để tránh xung đột định nghĩa Set sau khi thông tin kiểu tổng quát bị xóa.
- WorkManager giữ khởi tạo theo nhu cầu `Configuration.Provider`, dùng `KoinWorkerFactory` tạo Worker; WorkerParameters do hệ thống cung cấp, Context lấy từ bộ chứa ứng dụng.
- Dùng DSL cổ điển, không đưa plugin biên dịch của Koin vào; KSP chỉ dùng cho Room. Tính toàn vẹn phụ thuộc do kiểm thử đồ thị đảm bảo, không được coi Kotlin biên dịch thành công là đã xác thực xong đồ thị DI.
