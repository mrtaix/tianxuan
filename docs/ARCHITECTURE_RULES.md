# 🛡️ CTHarness — Nguyên tắc kiến trúc cốt lõi và hướng dẫn tránh bẫy khi AI viết mã (Architecture & UX/UI Rules)

---

## 1. Tách biệt lớp model thuần túy (Model Layer Purity)

- `:core:model` **nghiêm cấm** đưa vào các phụ thuộc `android.*`, `androidx.*` hoặc Compose, phải giữ nguyên Pure Kotlin.
- `architectureCheck` đã được nối vào giai đoạn kiểm chứng `app:preBuild`, chủ động chặn việc platform hóa tầng model, phụ thuộc ngang bất hợp pháp giữa các feature và kết nối trực tiếp từ tầng nghiệp vụ tới DAO.
- **Chính sách quản trị là mã nguồn (governance-as-code)**: toàn bộ quy tắc của các cổng kiểm tra trên được đăng ký trong [`architecture-policy.json`](../architecture-policy.json) ở thư mục gốc (danh sách trắng phụ thuộc module `requires`, kiểm tra chu trình phụ thuộc, danh sách đen import `importBans`, ratchet kích thước tệp `maxFileLines`). Tệp này là nguồn sự thật duy nhất — **phải đăng ký đồng bộ khi thêm module mới hoặc điều chỉnh quan hệ phụ thuộc**, nếu không cổng kiểm tra sẽ thất bại.
- Các tệp vượt giới hạn từ trước (>400 dòng) được đăng ký trong baseline ratchet `.architecture-baseline.json`: số dòng **chỉ được giảm, không được tăng**; tệp mới phải tuân thủ quy định. Sau khi rút gọn mã, chạy `architectureBaselineSync` để hạ baseline.

---

## 2. Ranh giới giữa lưu trữ bền vững và cài đặt ưu tiên (Persistence & Preference Boundaries)

- Tầng nghiệp vụ (feature / runtime / harness) **chỉ được phụ thuộc** vào interface Repository do `core/database/PersistenceRepositories.kt` cung cấp, nghiêm cấm inject trực tiếp hoặc thao tác Room DAO.
- Các cài đặt ưu tiên được đọc/ghi thống nhất qua interface mặt tiền theo từng lĩnh vực do `core/datastore/PreferenceFacades.kt` cung cấp, nghiêm cấm tùy tiện thêm các cặp khóa-giá trị riêng lẻ.

---

## 3. 🎨 M3 Expressive và hệ thống thiết kế UX/UI cùng các nguyên tắc cốt lõi của frontend

1. **Thành phần thiết kế thống nhất (nghiêm cấm rải rác Material Component thô)**:
   - Container phải dùng `top.wkbin.tianxuan.ui.components.RuntimeCard` (hỗ trợ `containerColor`, `borderColor`, `contentPadding`, `onClick`), **nghiêm cấm** dùng `androidx.compose.material3.Card` gốc hoặc tự ghép container bằng `Modifier.border/background`, nhằm đảm bảo hiệu ứng mờ kính, khúc xạ và viền mỏng tự thích ứng dưới cả hai theme **Material You (M3 chuẩn)** và **liquid glass**.
   - Nút bấm dùng thống nhất `RuntimeButton`, `RuntimeFilledTonalButton`, `RuntimeOutlinedButton`, `RuntimeTextButton`, `RuntimeIconButton`.
   - Hộp thoại dùng thống nhất `top.wkbin.tianxuan.ui.components.RuntimeAlertDialog`.
   - Công tắc và chỉ báo tiến độ dùng `RuntimeSwitch`, `RuntimeLinearProgressIndicator`, `RuntimeCircularProgressIndicator`.
2. **An toàn bàn phím mềm cho hộp thoại và biểu mẫu (IME chắn màn hình)**:
   - Toàn bộ nội dung `text` của Dialog chứa `OutlinedTextField` hoặc danh sách, **nghiêm cấm** thiết lập chiều cao/rộng cố định khóa cứng hoặc `offset` mã cứng ở lớp ngoài, **phải** bọc `modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())`, đảm bảo khi bàn phím mềm bật lên trên thiết bị di động biểu mẫu vẫn cuộn mượt và nút thao tác luôn hiển thị, có thể chạm tới.
3. **Quy phạm thanh điều hướng trên và dưới**:
   - Thanh điều hướng trên dùng thống nhất `RuntimeTopBar` (hỗ trợ `title`, `statusText`, `onBack`).
   - Thanh điều hướng dưới dùng thống nhất `RuntimeBottomBar` dựa trên NavigationBar gốc của M3.
4. **Responsive, chống cắt và chống gập dòng trên màn hình nhỏ**:
   - Bong bóng tin nhắn (như `UserBubble`) phải dùng chiều rộng responsive (`Modifier.widthIn(max = 560.dp)`), tránh bị giới hạn quá hẹp trên máy tính bảng, thiết bị gập hoặc màn hình lớn.
   - Văn bản một dòng, đường dẫn hoặc nhãn (đường dẫn tệp, tiêu đề ngắn, nhãn trạng thái) phải khai báo rõ `maxLines = 1, overflow = TextOverflow.Ellipsis`, nghiêm cấm để chuỗi dài đẩy nút thao tác cùng dòng ra khỏi màn hình.
   - Hàng nhiều nút bấm ngang (thanh thao tác, hành động cuối thẻ) phải cấu hình `contentPadding` đủ compact và cỡ chữ một dòng `labelSmall`, triệt tiêu tình trạng đứt dòng trên màn hình hẹp 360dp khiến chiều cao các nút không đồng đều.
5. **Tránh xung đột giữa các tầng và nhiều lớp vật liệu**:
   - Trong khu vực log, khối mã terminal hoặc mục con cấp hai lồng trong `RuntimeCard`, nên dùng `Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest/Highest)`, tránh lồng nhiều Card gây tính toán sai lớp kính lỏng và đổ bóng.

---

## 4. Chính sách bảo mật và mạng (Security & Network Policy)

- Mô hình cục bộ (nhóm `LOCAL`) được phép dùng `http://127.0.0.1:*` hoặc `http://localhost:*`, và `apiKeyOptional = true`.
- Mô hình bên ngoài (`OFFICIAL` / `CHINA` / `AGGREGATOR`) bắt buộc dùng HTTPS.

---

## 5. Mount PRoot và an toàn thực thi lệnh (PRoot Sandbox Security)

- Đường dẫn mount phải được chuẩn hóa qua `ProotCommandBuilder`, tránh injection Shell và đi xuyên thư mục gốc hệ thống nguy hiểm.
- `base` chỉ dùng cho lệnh foreground có giới hạn; mặc định 10 phút, người dùng có thể cấu hình 1–60 phút, và có thể ghi đè 1–3600 giây cho từng lần gọi bằng `timeout_seconds`.
- Đừng dùng `nohup` / `setsid` / `&` trong `base` để giả lập daemon. Tác vụ dài phải gọi `process(action="start", ...)`, sau đó quản lý bằng `status` / `logs` / `list` / `stop`.
- ID bên ngoài của `process` được thêm namespace `agent-process:`, nghiêm cấm trộn với tiến trình dịch vụ của trung tâm công cụ.

---

## 6. Chiến lược tài nguyên Gradle cho mobile (Mobile Sandbox Toolchain Strategy)

- Build Android/Flutter trong sandbox cố định `--no-daemon --max-workers=2`, cấu hình toàn cục giữ `org.gradle.daemon=false`, `org.gradle.parallel=false`, `org.gradle.workers.max=2`.
- NDK chỉ được phép inject qua `android.ndkPath` của init policy. Script build được phép dọn `ndk.dir` cũ trong `local.properties`, nhưng không được đồng thời ghi lại `ndk.dir` hay khai báo `ndkPath` lần nữa trong dự án, nếu không AGP sẽ báo xung đột hai locator.

---

## 7. An toàn workspace và plugin (Workspace & Plugins Safety)

- Nhập ZIP phải dùng `WorkspaceManager.importProjectArchive()`, nghiêm cấm giải nén trực tiếp đường dẫn không đáng tin; URL Git HTTP(S) và SSH phải được xác thực riêng theo transport do người dùng chọn.
- Loại nhập được ghi vào `.tianxuan-project.properties`, không được chỉ dựa vào phần mở rộng tệp hay nội dung thư mục để đoán lại lựa chọn của người dùng.
- Khi đã tồn tại cùng ID plugin + version thì trả về "đã nhập", không được đệ quy ghi đè payload hiện có; framework cài đặt phải đảm bảo `$toolDir/bin` tồn tại trước.

---

## 8. Ràng buộc ADB không dây và daemon PRoot (Wireless ADB & PRoot Daemon Strategy)

- **Cách ly tiến trình ADB của PRoot**: bên trong sandbox PRoot không có daemon init/systemd bền vững, `adb fork-server` chạy nền không thể sống qua các lệnh độc lập, sẽ bị hủy sau mỗi lần gọi. Thao tác ADB dòng lệnh phải hoàn tất `connect + thao tác` trong một lần thực thi (script bao bọc `/opt/tianxuan/bin/adb` đã tích hợp tự connect), hoặc ưu tiên qua `HostBridge` (`logcat-grabber` / `tianxuan-host logcat`) dùng kết nối Kadb thường trú của máy chủ Android.
- **Thu log không cần đặc quyền**: việc bắt logcat được thực hiện qua giao thức ADB không dây tích hợp và cầu nối HostBridge, **hoàn toàn không phụ thuộc vào Shizuku hay quyền Root**. Khi agent AI gặp tác vụ bắt log/điều tra crash, phải dùng trực tiếp `host(action="logcat", ...)` hoặc `logcat-grabber <tên gói> [-P cổng]`, nghiêm cấm yêu cầu người dùng bật Shizuku/Root trước hoặc làm thêm các bước kiểm tra quyền thừa.
