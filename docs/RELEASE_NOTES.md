# CTHarness · Ghi chú phát hành v0.36.0

> **Ngày phát hành**: 2026-10-15  
> **Phiên bản**: v0.36.0 (`appVersionName = 0.36.0`, `appVersionCode = 48`)  
> **Phạm vi hỗ trợ**: Android 10+ · arm64-v8a (không cần Root / môi trường PRoot cô lập)  
> **Tính chất**: Toàn bộ cải tiến DSH đã được kết nối vào luồng chạy thực tế — từ mã không được gọi đến mã có hiệu lực

---

## 🎉 Điểm nhấn phiên bản

v0.36.0 là điểm ngoặt then chốt của dự án: trước đây, trong 19 cải tiến căn chỉnh DSH được định nghĩa từ v0.30 đến v0.35, phần lớn chỉ dừng lại ở mức "đã định nghĩa kiểu, đã biên dịch thành công" nhưng không bao giờ được khởi tạo hay gọi tới. Phiên bản này kết nối toàn bộ cải tiến vào luồng chạy thực tế, mỗi hạng mục đều phát huy tác dụng trên đường thực thi thật.

### Tổng quan kết nối (tất cả đều có hiệu lực thực tế)

| Cải tiến | Điểm kết nối |
|--------|--------|
| RepeatToolReminder | Kiểm tra trước trong ToolExecutor.execute(), ngăn mô hình rơi vào vòng lặp vô hạn |
| ToolTimeoutPolicy | Áp dụng withTimeout bắt buộc trong ToolExecutor.execute(), miễn trừ BASE/PROCESS/ASK_USER |
| SpillPolicy | Giới hạn ngân sách token trước khi ToolExecutor trả về |
| DelegationDepthGuard | Áp dụng giới hạn độ sâu đệ quy trong SubagentOrchestrator.executeSubagents() (MAX_DELEGATION_DEPTH=5) |
| SessionRepair | Đánh dấu khôi phục sau sự cố trong RecoveryManager.recoverSession() |
| ApprovalAuditWriter | Sự kiện kiểm toán phê duyệt trong ApprovalPolicyEngine.createRequest() |
| PtcDispatchLogger | Nhật ký điều phối lời gọi con trong ToolExecutor.executeBatch() |
| SkillRegistry | Tra cứu sổ đăng ký nhà cung cấp cho ToolExecutor.LOAD_SKILL |
| SkillFilesystemProvider | Phát hiện kỹ năng từ hệ thống tệp qua SkillRegistry.registerProvider() |
| SessionProjectionRegistry | Điều khiển bởi trạng thái Completed trong HarnessLoop.executeSessionRun() |
| SessionStatsProjection | Đăng ký vào SessionProjectionRegistry |
| SessionTurnOutlineProjection | Đăng ký vào SessionProjectionRegistry |
| PlanModeProjection | Đăng ký vào SessionProjectionRegistry |
| PtyReadinessTracker | Đường đọc dữ liệu NativePtySession và API readinessState()/resetReadiness() |
| TerminalSanitizer | Đường đọc dữ liệu NativePtySession |
| TurnEndReason | Mở rộng TurnRunner.toTurnEndReason() |
| SurfaceOp | Dữ liệu đầu vào của CompactionManager.compact() |
| ToolPairingBalance | Cân bằng điểm cắt trong CompactionManager.compact() |
| JobRegistry | Đăng ký một thể hiện dùng chung trong Koin |

### Lắp ráp Koin
Tất cả thành phần mới đều được đăng ký dưới dạng một thể hiện dùng chung: RepeatToolReminder / SpillPolicy / JobRegistry /
SessionProjectionRegistry / SkillRegistry / ApprovalAuditWriter / PtcDispatchLogger.

### Sửa lỗi quan trọng
- Hàm khởi tạo của RecoveryManager nhận thêm `messageStore: SessionTreeStore` (để SessionRepair sử dụng)
- `ToolTimeoutPolicy.shouldEnforce()` miễn trừ các công cụ đã tự có thời gian chờ, tránh xung đột giới hạn kép
- Giữ nguyên logic phê duyệt trong `execute()`, không làm mất các trường `awaitingApproval` / `approvalDeferred`

---

## ⬆️ Hướng dẫn nâng cấp

- Chỉ cần cài đè lên bản cũ, không cần xóa dữ liệu;
- Từ v0.34.0, lược đồ cơ sở dữ liệu đã nâng lên 57 (các trường về quan hệ nguồn gốc phiên), dữ liệu sẽ tự động được chuyển đổi.

---

# CTHarness · Ghi chú phát hành v0.35.0

> **Ngày phát hành**: 2026-10-15  
> **Phiên bản**: v0.35.0 (`appVersionName = 0.35.0`, `appVersionCode = 47`)  
> **Phạm vi hỗ trợ**: Android 10+ · arm64-v8a (không cần Root / môi trường PRoot cô lập)  
> **Tính chất**: Căn chỉnh toàn bộ cơ chế lõi của DeepSeek Harness — 33 cải tiến

---

## 🎉 Điểm nhấn phiên bản

v0.35.0 hoàn tất việc căn chỉnh toàn bộ cơ chế kiến trúc lõi của DeepSeek Harness (DSH). Từ v0.22.0 đến v0.35.0 đã phát hành tổng cộng 14 phiên bản, tích lũy 33 cải tiến căn chỉnh DSH, bao phủ toàn bộ cơ chế cốt lõi: nhật ký sự kiện phiên, an toàn nén, hệ thống tổng hợp trạng thái, trạng thái sẵn sàng PTY, sổ đăng ký kỹ năng, môi trường chạy PTC, chính sách bảo vệ, sổ đăng ký tác vụ, kiểm toán phê duyệt, v.v.

### Tổng quan 33 cải tiến căn chỉnh DSH

#### Nhật ký sự kiện và quan hệ nguồn gốc phiên (v0.31–v0.32)
- TurnEndReason với bảy trạng thái nguyên nhân (Completed/Aborted/Blocked/Error/MaxTokens/Interrupted/Forked)
- SurfaceOp với thao tác nối thêm/thay thế (thao tác nguyên tử trên bề mặt hiển thị khi nén)
- Truy vết quan hệ nguồn gốc phiên (lược đồ cơ sở dữ liệu 57: parentSessionId/isSeeded/origin/delegationDepth)
- Sửa chữa phiên (tổng hợp TurnEndReason.Interrupted khi khôi phục sau sự cố)

#### An toàn nén (v0.34)
- ToolPairingBalance: kiểm tra cân bằng điểm cắt nén, tránh điểm cắt rơi vào giữa cặp lời gọi công cụ/kết quả
- Kết nối surfaceOp + turnEndReason vào CompactionManager
- Kiểm tra tính hội tụ của tóm tắt: tóm tắt của LLM không được dài hơn đoạn bị gộp

#### Hệ thống tổng hợp trạng thái phiên (v0.34)
- SessionProjectionRegistry: sổ đăng ký các bộ tổng hợp thuần theo trạng thái
- SessionStatsProjection: thống kê toàn nhật ký (số lượt/số bước/lời gọi công cụ/ước lượng token)
- SessionTurnOutlineProjection: tổng hợp dàn ý theo lượt (xem trước lời nhắc/phản hồi)
- PlanModeProjection: tổng hợp trạng thái chế độ lập kế hoạch

#### Giao thức xác định trạng thái sẵn sàng PTY (v0.33)
- TerminalSanitizer: bộ lọc mã thoát CSI/OSC theo luồng
- BoundedTextBuffer: bộ đệm đầu ra có giới hạn (giới hạn kép về byte và số dòng)
- PtyReadinessTracker: xác định trạng thái sẵn sàng dựa trên chuỗi bằng chứng ba cấp
- PromptMarker: dấu đánh dấu OSC 133;D và mẫu biến môi trường bash

#### Hệ thống kỹ năng (v0.30, v0.33, v0.34)
- Bộ nhớ đệm tóm tắt danh mục kỹ năng
- Giao diện SkillProvider và sổ đăng ký phân tầng SkillRegistry
- SkillFilesystemProvider: bọc AgentSkillRepository thành SkillProvider
- renderSkillContent: định dạng chuẩn `<skill_content>` để mô hình nhìn thấy

#### Môi trường chạy PTC (v0.33, v0.34)
- Giao diện PtcRuntime và hệ thống kiểu PtcRunResult/PtcRunFailure
- PtcDispatcher: điều phối các lời gọi con đồng thời (PARALLEL/EXCLUSIVE và ghi nhận có thứ tự)
- JsonPtcRuntime: triển khai định dạng chương trình gồm chuỗi lời gọi công cụ dạng JSON
- PtcDispatchLogger: ghi nhật ký điều phối lời gọi con vào luồng sự kiện phiên

#### Chính sách bảo vệ (v0.35)
- RepeatToolReminder: phát hiện gọi liên tiếp cùng công cụ với cùng tham số, chèn nhắc nhở ở ngưỡng [3,5,8]
- ToolTimeoutPolicy: bảng áp dụng thời gian chờ cho từng công cụ (bao phủ cả 31 công cụ), mã lỗi TOOL_TIMEOUT

#### Tác vụ và phê duyệt (v0.35)
- JobRegistry: quản lý vòng đời tác vụ nền (đăng ký/hoàn tất/kết thúc/truy vấn)
- ApprovalAuditEvent: sự kiện kiểm toán có cấu trúc approval/asked + approval/decided

#### Cải tiến khác (v0.22–v0.29)
- Kiểu niêm phong Retention, bảo vệ phiên bản tệp, phát hiện trùng lặp theo cơ chế cảnh báo rồi chặn
- Lưu trữ nhiệm vụ Agent con và khôi phục từ dữ liệu lưu, CommandResult có timedOut/aborted loại trừ nhau
- Lập lịch Cron/hằng tuần, làm sạch môi trường tiến trình con MCP, giữ lại đầu ra thành hai đoạn
- Môi trường cô lập tác động lên tệp ba cấp, công cụ batch, xử lý khối nội dung ảnh MCP
- chroot (chế độ ROOT), dọn dẹp thương hiệu, sửa LeakCanary

---

## ⬆️ Hướng dẫn nâng cấp

- Chỉ cần cài đè lên bản cũ, không cần xóa dữ liệu;
- Cơ sở dữ liệu nâng lược đồ từ 56 lên 57 (thêm các trường về quan hệ nguồn gốc phiên), dữ liệu sẽ tự động được chuyển đổi.

---

# CTHarness · Ghi chú phát hành v0.19.1

> **Ngày phát hành**: 2026-09-28  
> **Phiên bản**: v0.19.1 (`appVersionName = 0.19.1`, `appVersionCode = 28`)  
> **Phạm vi hỗ trợ**: Android 10+ · arm64-v8a (không cần Root / môi trường PRoot cô lập)  
> **Tính chất**: Bản vá khẩn cấp · Sửa lỗi thiết bị đầu cuối/phiên bị dừng đột ngột khi khởi động sau khi nâng lên v0.19.0

---

## 🐛 Sửa lỗi

### Đường dẫn gắn kết vượt giới hạn lưu trữ khiến phiên luôn bị dừng đột ngột khi khởi động

- **Hiện tượng**: Sau khi nâng lên v0.19.0, một số người dùng gặp sự cố ngay khi mở thiết bị đầu cuối / trung tâm điều khiển / tác vụ dựng, nhật ký sự cố là `IllegalArgumentException: 容器挂载仅允许位于 /mnt 或 /sdcard 内`;
- **Điều kiện kích hoạt**: Trước đó người dùng từng thêm điểm gắn kết tùy chỉnh trong «Cài đặt → Gắn kết lưu trữ và chia sẻ» mà **đường dẫn vùng chứa không nằm trong `/mnt` hay `/sdcard`** (phiên bản cũ kiểm tra chưa đầy đủ khi lưu nên dữ liệu không hợp lệ đã vào cơ sở dữ liệu). Từ đó mỗi lần khởi động phiên đều lặp lại lỗi, cài lại ứng dụng cũng không khôi phục được;
- **Nội dung sửa**:
  - **Tự lọc lúc chạy**: Khi khởi động phiên, tự động bỏ qua các liên kết gắn kết không hợp lệ và ghi nhật ký cảnh báo, không còn gây sự cố — người dùng có dữ liệu không hợp lệ từ trước chỉ cần nâng cấp là dùng bình thường;
  - **Kiểm tra trước khi lưu**: Khi thêm điểm gắn kết mới, kiểm tra ngay đường dẫn vùng chứa (phải nằm trong `/mnt` hoặc `/sdcard`) và đường dẫn máy chủ (phải nằm trong bộ nhớ chia sẻ). Đường dẫn vượt giới hạn bị từ chối lưu ngay, chấm dứt việc tạo dữ liệu không hợp lệ;
  - **Hiển thị liên kết không hợp lệ**: Các liên kết cũ không hợp lệ trong danh sách trang Cài đặt sẽ được tô đỏ kèm nhãn «gắn kết không hợp lệ» và lý do cụ thể, hướng dẫn người dùng xóa rồi tạo lại;
  - **Thống nhất quy tắc kiểm tra**: Quy tắc an toàn đường dẫn gắn kết được gom về lớp mô hình `StorageMountBinding`, dùng chung cho ba nơi: dựng lệnh PRoot, lọc lúc chạy và kiểm tra trang Cài đặt, tránh sai lệch quy tắc; lớp phòng thủ cuối cùng chống thoát phạm vi gắn kết phía PRoot vẫn giữ nguyên.

---

## ⬆️ Hướng dẫn nâng cấp

- Chỉ cần cài đè lên bản cũ, **không cần xóa dữ liệu**, không cần cài lại;
- Người dùng có điểm gắn kết không hợp lệ sau khi nâng cấp sẽ thấy mục bị tô đỏ trong trang «Gắn kết lưu trữ và chia sẻ». Xóa rồi thêm lại đường dẫn hợp lệ là ánh xạ hoạt động bình thường.

---

## 💡 Giải pháp tạm thời (cho người dùng vẫn ở v0.19.0)

Trước khi nâng cấp, bạn có thể tạm khôi phục sử dụng như sau: vào **Cài đặt → Gắn kết lưu trữ và chia sẻ → Ràng buộc ánh xạ tùy chỉnh**, xóa các mục không cần thiết hoặc có đường dẫn đáng ngờ (đặc biệt là mục có đường dẫn vùng chứa không bắt đầu bằng `/mnt/...` hay `/sdcard/...`). Sau khi xóa, tạo thiết bị đầu cuối mới là khởi động bình thường.

---

# CTHarness · Ghi chú phát hành v0.17.0

> **Ngày phát hành**: 2026-09-21  
> **Phiên bản**: v0.17.0 (`appVersionName = 0.17.0`)  
> **Phạm vi hỗ trợ**: Android 10+ · arm64-v8a (không cần Root / môi trường PRoot cô lập)

---

## 🎉 Điểm nhấn phiên bản

v0.17.0 tích hợp dịch thuật ngoại tuyến trên thiết bị, nhận diện động màu hình nền Material You, tiếp thu sâu các điểm mạnh trong kiến trúc Agent của RikkaHub và Pi, đồng thời đưa toàn bộ bộ máy kết xuất Markdown xuống tầng thành phần dùng chung:

- **Google ML Kit dịch ngoại tuyến trên thiết bị** — không phụ thuộc mạng, không cần cấu hình khóa API, hiển thị tiến độ tải mô hình ngoại tuyến, dịch nhanh ngay trên máy;
- **Lấy màu hình nền Material You động và làm mới theo thời gian thực** — hỗ trợ hệ thống phối màu theo hình nền, chuyển tự do giữa giao diện Trong Sáng (Liquid Glass) và giao diện còn lại;
- **Tiến hóa kiến trúc Agent Harness** — tiếp thu cơ chế chỉnh sửa chịu lỗi, lưu đệm lời nhắc, truyền trực tiếp dữ liệu thị giác, tách lượt và dấu vết tệp tóm tắt theo quy tắc cố định, đồng thời sửa rủi ro hết bộ nhớ khi chạy đồng thời cao;
- **Xem trước Markdown với định dạng phong phú trên toàn ứng dụng** — hộp thoại cập nhật phiên bản và nhật ký cập nhật trong mục «Giới thiệu» đều hỗ trợ xem trước với bố cục phong phú, nâng cấp trải nghiệm.

---

# CTHarness · Ghi chú phát hành v0.10.0

> **Ngày phát hành**: 2026-09-03
> **Phiên bản**: v0.10.0 (`appVersionName = 0.10.0`, `appVersionCode = 16`)
> **Phạm vi hỗ trợ**: Android 10+ · arm64-v8a (không cần Root / môi trường PRoot cô lập)

---

## 🎉 Điểm nhấn phiên bản

v0.10.0 là phiên bản có ý nghĩa nhất trong đợt cải tiến này về khả năng «quan sát được, khôi phục được»:

- **Trình duyệt có bộ máy can thiệp và khả năng gỡ lỗi cấp CDP** — có thể chặn bằng mã lệnh được chèn, đặt điểm dừng, chặn Fetch ở cấp Worker, kèm bảng diễn tiến mạng. Hành vi của trình duyệt lần đầu tiên có thể được quan sát và gỡ lỗi chính xác;
- **Hội thoại chính thức hỗ trợ «quay lại»** — cơ chế quay lại/điểm khôi phục dựa trên cây phiên SessionFork, kèm bản chụp tệp mỗi lượt và lưu đĩa; có thể quay về thời điểm trước khi thực hiện thao tác quan trọng;
- **Bộ nhớ Agent nâng cấp thành mô hình ngữ nghĩa** — loại bỏ trùng lặp xung đột, phiên bản sửa đổi, trạng thái ghim, ngữ nghĩa độ mới, đi kèm chuyển đổi dữ liệu Room (43→44);
- **Trải nghiệm nhập kỹ năng được tăng cường toàn diện** — phát hiện đệ quy thư mục, nhập từ bất kỳ vị trí nào, nhập hàng loạt ZIP, tự động loại bỏ trùng lặp.

---

## ✨ Tính năng mới

### Trình duyệt: bộ máy can thiệp bằng mã chèn và gỡ lỗi CDP

- **Bộ máy can thiệp bằng mã chèn**: Trình duyệt tích hợp sẵn hỗ trợ can thiệp bằng cách chèn mã lệnh, có thể chặn và thay đổi hành vi trang;
- **Điểm dừng CDP / chặn cấp Worker**: Cung cấp khả năng đặt điểm dừng qua giao thức CDP, đồng thời hỗ trợ chặn Fetch ở cấp Worker, cho phép quan sát toàn bộ quá trình gửi yêu cầu mạng của trang;
- **Bảng diễn tiến mạng**: Xem diễn tiến yêu cầu mạng theo thời gian thực trong bảng điều khiển trình duyệt;
- **Biểu ngữ tạm dừng CDP**: Hiện biểu ngữ nhắc rõ khi trình gỡ lỗi tạm dừng, tránh nhầm là trang bị treo;
- **Cổng MCP trình duyệt động**: Dịch vụ MCP trình duyệt phía Harness đổi sang cấp phát cổng động, tránh xung đột cổng và thống nhất lược đồ nhà cung cấp.

### Agent Harness: quay lại hội thoại và bản chụp điểm khôi phục

- **Quay lại hội thoại đã được hiện thực**: Dựa trên **cây phiên SessionFork** để quay lại các lượt trước mà không mất ngữ cảnh;
- **Điểm vào giao diện «Thu lại về lượt này»**: Giao diện hội thoại thêm nút quay lại, một chạm để thu về lượt chỉ định;
- **Bản chụp tệp tại điểm khôi phục mỗi lượt làm lưới an toàn**: Trước mỗi lượt hội thoại, chụp lại các tệp trong không gian làm việc, tạo lưới an toàn cấp tệp cho việc quay lại;
- **Bản chụp điểm khôi phục lưu đĩa**: Bản chụp được ghi ra đĩa, sau khi khởi động lại ứng dụng vẫn có thể quay lại.

### Agent Harness: mô hình ngữ nghĩa bộ nhớ (DB 43→44)

- **Mô hình ngữ nghĩa cho xung đột bộ nhớ / phiên bản sửa đổi / trạng thái ghim / độ mới**: Mục nhớ hỗ trợ giải quyết xung đột, quản lý phiên bản sửa đổi, ghim và độ mới, giúp bộ nhớ của Agent dễ kiểm soát và bảo trì hơn;
- Cơ sở dữ liệu Room tương ứng chuyển đổi từ v43 sang v44 (xem chi tiết ở phần «Chuyển đổi cơ sở dữ liệu» bên dưới).

### Agent Harness: điều phối Agent con và kiểm soát ngân sách

- **Điều phối theo đợt dựa trên quyền ghi được cấp cho write_paths**: Đường dẫn ghi tệp của Agent con được điều phối theo quyền ghi được cấp từng đợt, tránh xung đột ghi đồng thời giữa nhiều Agent;
- **Gói dữ kiện gửi về Agent cha**: Agent con gửi về cho Agent cha các gói dữ kiện có cấu trúc, thông tin tổng hợp gọn gàng hơn;
- **Kiểm soát ngân sách khi chèn tóm tắt và phân trang phần tràn ra đĩa**: Việc chèn tóm tắt từ Agent con có thêm kiểm soát ngân sách, nội dung vượt giới hạn tự động phân trang ghi ra đĩa, tránh mất văn bản dài hoặc làm tràn ngữ cảnh.

### Nhập và quản lý kỹ năng

- **Phát hiện kỹ năng đệ quy theo thư mục**: Trang Cài đặt tự động phát hiện kỹ năng nằm trong thư mục lồng nhau;
- **Nhập từ thư mục tùy ý**: Hỗ trợ chọn thủ công bất kỳ thư mục nào để nhập kỹ năng;
- **Nhập hàng loạt kỹ năng qua ZIP**: Hỗ trợ nhập hàng loạt dưới dạng gói ZIP;
- **Tự phát hiện thư mục và nhập hàng loạt**: Tự quét `attachments/skills` và thư mục `skills` trong không gian làm việc, loại bỏ trùng theo resourcePath rồi nhập hàng loạt.

### Không gian làm việc

- **Tiến độ nhập GitHub theo thời gian thực**: Hộp thoại nhập GitHub hiển thị tiến độ và phần trăm của `git clone` theo thời gian thực, tác vụ dài không còn phải «chờ thụ động».

---

## 🐛 Sửa lỗi

- **Thiết bị đầu cuối**: Sửa lỗi dừng đột ngột khi mở thiết bị đầu cuối do `scrollToItem(-1)`;
- **Harness**: Sửa lỗi lịch sử bất thường khiến DeepSeek trả về 400 vì chuỗi `tool_calls` sai;
- **Harness**: Sửa lỗi không gửi lại `reasoning_content` ở lượt gọi công cụ trong chế độ suy nghĩ của DeepSeek;
- **Harness**: Sửa lỗi bộ đếm lời gọi công cụ về 0 sau khi Agent con hết thời gian chờ;
- **Hội thoại**: Thêm biện pháp tránh bộ thu thập tự động khi tải ảnh, đồng thời sửa lỗi kết xuất ảnh cục bộ;
- **Trình duyệt**: Chuẩn hóa tiền tố mã thẻ, sửa lỗi phân tích/dọn dẹp bất thường khi URL chứa dấu huyền ngược;
- **Cài đặt**: Giới hạn chiều cao và cho cuộn bên trong khi quá nhiều mô hình ứng viên, tránh đẩy hộp thoại vượt quá đáy gây màn hình đen;
- **Môi trường chạy**: Bộ Android trực tuyến bổ sung liên kết tượng trưng đường dẫn chuẩn cho JDK, sửa lỗi báo thiếu giả trong môi trường ký tại xưởng thông minh;
- **Trước khi dựng**: CMake/Ninja được kiểm soát theo `has_native` và báo lỗi theo đúng mục thiếu thực tế, tránh báo giả;
- **CI**: Quy trình phát hành đổi cấu hình ký sang ghi vào `keystore.properties`, gỡ giới hạn ký trên CI.

---

## ♻️ Tối ưu trải nghiệm và tái cấu trúc

- **Hệ thống biểu tượng**: Thay toàn bộ biểu tượng hệ thống bằng **SVG đơn sắc Lucide**, bỏ thư viện phụ thuộc `material-icons-extended`, vừa giảm kích thước ứng dụng vừa đồng nhất thị giác;
- **Dọn mã**: Loại bỏ mã không được gọi đến và thư viện phụ thuộc không dùng, phía Harness dọn BOM và chuyển sang `Duration.milliseconds`.

---

## 📝 Tài liệu

- Tài liệu điều hướng cập nhật bộ máy can thiệp và khả năng gỡ lỗi CDP (xem `docs/AI_NAVIGATION.md`, `docs/BROWSER_DESIGN.md`).

---

## ⚠️ Chuyển đổi cơ sở dữ liệu

Phiên bản này chuyển đổi cơ sở dữ liệu Room từ **v43 sang v44**, nội dung là các trường liên quan đến mô hình ngữ nghĩa bộ nhớ (trong đó có sửa giá trị mặc định của `expiresAt` thành `DEFAULT NULL` / `null` viết thường).

- Sau khi nâng cấp và cài đặt, cơ sở dữ liệu sẽ tự động chuyển đổi, **không cần thao tác thủ công**;
- Không thể hạ cấp lại sau khi chuyển đổi. Nếu vì lý do nào đó cần quay về phiên bản cũ, hãy sao lưu không gian làm việc và dữ liệu hội thoại trước.

---

## 🔖 Phạm vi bản ghi thay đổi

Kể từ bản phát hành trước (v0.9.0) có tổng cộng **30 bản ghi thay đổi** (bao gồm bản ghi phát hành), bao phủ các mô-đun `harness` / `browser` / `chat` / `settings` / `workspace` / `terminal` / `runtime` / `database` / `ci` / `components`.

---

*CTHarness trong lòng bàn tay, Thất Tinh làm trục. Nguyện cho mỗi ý định đều kết thành kết quả có thể kiểm chứng.*
