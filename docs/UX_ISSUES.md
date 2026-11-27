# Sổ theo dõi vấn đề UX (2026-08-29)

> Ngày 2026-08-29 đã hoàn tất một đợt rà soát UX toàn diện (khoảng 150 mục, bao phủ mọi module UI trong feature + lớp lắp ghép app) và **sửa toàn bộ**; xem chi tiết từng mục trong lịch sử commit ngày đó.
> Tệp này hiện chỉ ghi lại: các bản sửa có tính hệ thống đã triển khai, **mục còn tồn đọng** và khu vực đăng ký vấn đề UX mới.
> Thêm vấn đề mới vào cuối tệp theo dạng "`tệp:số dòng — mô tả — mức độ nghiêm trọng`", xóa sau khi sửa.

## Các bản sửa có tính hệ thống đã triển khai (chuẩn hóa ở lớp thành phần/VM)

| # | Bản sửa | Vị trí |
|---|------|------|
| S1 | Ngữ nghĩa nút biểu tượng: thêm `contentDescription` (qua stringResource) cho nút ở từng màn hình; sửa lỗi nhánh Material của RuntimeIconButton bỏ qua mô tả; thêm semantics cho các chỉ báo chỉ dùng màu như ToolCard/MCP/trạng thái quyền | Các module feature |
| S2 | Xác nhận lần hai cho thao tác phá hủy (12 chỗ): xóa phiên/tin nhắn/hồ sơ model, xóa toàn bộ bản nháp, đóng phiên terminal (đếm ngược 3 giây), bỏ chỉnh sửa, xóa script build, xóa model GGUF (đếm ngược), gỡ công cụ (thẻ và trang chi tiết dùng chung `UninstallToolConfirmDialog`, internal), xóa cụm từ nhanh, xóa điểm mount, xóa dịch vụ MCP, xóa Skill | Các module feature |
| S3 | Trạng thái biểu mẫu/hộp thoại chuyển từ `remember` → `rememberSaveable`/listSaver/mapSaver; toàn bộ tiến độ cài DistroManagement chuyển vào VM `DistroInstallUiState` (khôi phục được sau xoay màn hình); bản nháp nhập chat lưu vào SavedStateHandle; chuyển trạng thái mở/đóng hộp thoại chat, đối tượng chỉnh sửa, trình hướng dẫn workspace ba bước (gồm mapSaver) và mọi trường của trình chỉnh sửa model | Các module feature |
| S4 | Biến lỗi âm thầm thành lỗi quan sát được: `operationError`+`isSyncing` trong ToolCenterViewModel, đẩy lỗi toggleAutoStart của ToolDetailViewModel lên trên, thông báo lỗi tải APK trong hộp thoại, bộ đếm lỗi đính kèm chat + Toast, Toast khi điều hướng từ trang hệ thống thất bại | Các module |
| S5 | Mọi chỗ xuất trực tiếp `throwable.message` đều chuyển sang thông báo cho người dùng; ngoại lệ gốc đưa vào Log | Các module |
| S6 | Thay toàn bộ cách nhận diện lỗi bằng chuỗi `contains("失败")` bằng cờ có kiểu trong VM (`xxxIsError: StateFlow<Boolean>`): workspace ×3 (`messageIsError`), settings ×8 | workspace/settings |
| S7 | Mọi vùng chạm <48dp được thêm `Modifier.minimumInteractiveComponentSize()` hoặc tăng kích thước; đồng bộ nhánh kính của RuntimeIconButton | Toàn ứng dụng |
| S8 | Chuyển các chuỗi viết cứng đã xác định sang tài nguyên strings và đồng bộ values-en (chat +30 mục, toàn màn hình mẫu/workshop trong workspace, home/terminal, settings, components, tạo res cho custom_iteration) | Các module |

Các mục chuyên biệt (không liệt kê hết): cho phép thử lại khi khởi động terminal thất bại (`retryInitialize`), ẩn nút quay lại của terminal nhúng (`showBackButton`), một điểm hiển thị lỗi terminal, key ổn định cho LazyColumn (phiên/Skill/MCP/dòng terminal), cuộn ngang bảng Markdown, ảo hóa diff (LazyColumn + DiffLine), huy hiệu FlowRow, chu kỳ thăm dò 100/200ms→500ms, trạng thái busy của thẻ phê duyệt, gợi ý cho `/clear` và hồ sơ model trùng tên, kiểm tra dữ liệu nhập (cổng 1024-65535/đường dẫn mount/gguf/URL/tên tệp, kiểm tra trước trong VM), phản hồi khi đang tạo dự án, lớp phủ khi tải thư mục con, thống nhất log hai điểm vào tự phục hồi của AI, debounce 400ms khi sửa prompt, tài nguyên hóa chuỗi như `terminal_*`/`home_*`, xác định động kiến trúc trong tiêu đề PTY từ `Build.SUPPORTED_ABIS`.

## Mục còn tồn đọng (đã đánh giá và tạm chưa xử lý)

- `contentDescription` của `RuntimeIcon` mặc định vẫn là null (đổi thành bắt buộc sẽ phải sửa chữ ký mọi điểm gọi); bản sửa ngữ nghĩa được áp dụng ở điểm gọi và lớp RuntimeIconButton.
- Mã chat không còn dùng: vẫn giữ `ContextUsageButton`, `ChatTurnAnchorBar`.
- `TextFieldState` của `CodeEditorScreen` không lưu được (mất vị trí con trỏ khi xoay; VM khôi phục nội dung); màu tối của trình chỉnh sửa không theo theme (hạng mục thiết kế lại).
- Module settings chưa có BackHandler biểu mẫu toàn cục (phạm vi ảnh hưởng lớn, mới chỉ lưu trạng thái).
- Nội dung Screen custom_iteration, trình hướng dẫn WorkspaceScreen :1107-1154 và các chuỗi viết cứng chưa được chỉ rõ khác chưa chuyển đổi (hạng mục dọn dẹp dài hạn).
- Chưa bổ sung hồi tố các mục còn thiếu trong values-en từ trước.
- Quản lý bộ nhớ LMK/SIGKILL của phiên agent xem riêng tại docs/KNOWN_ISSUES.md.

## Khu vực đăng ký vấn đề mới

(Trống)
