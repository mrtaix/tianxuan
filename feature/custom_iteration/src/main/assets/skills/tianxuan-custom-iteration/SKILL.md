---
name: tianxuan-custom-iteration
description: Quy chuẩn tự phát triển tùy chỉnh CTHarness và dựng TianXuanDev, hướng dẫn Agent phát triển an toàn, gửi PR và dựng APK với hai gói cùng tồn tại.
---

# Quy chuẩn phát triển riêng cho tính năng tự phát triển tùy chỉnh CTHarness

Skill này hướng dẫn AI Agent hỗ trợ người dùng phát triển, kiểm thử, dựng bằng CI trên đám mây và đóng góp PR nguồn mở cho chính ứng dụng CTHarness trong hộp cát Linux không Root của CTHarness.

---

## 1. Nguyên tắc cốt lõi về an toàn và cách ly

1. **Cách ly không gian làm việc**: Mọi thay đổi mã nguồn CTHarness phải được thực hiện duy nhất trong không gian làm việc cách ly `~/custom_tianxuan`; nghiêm cấm sửa trực tiếp trong thư mục của ứng dụng chủ đang chạy hoặc cấu hình cấp hệ thống.
2. **Bảo mật thông tin xác thực**:
   - Hướng dẫn người dùng dùng `gh auth login` (quy trình mã thiết bị) hoặc xác thực bằng SSH Key;
   - Nghiêm cấm in GitHub Token, khóa riêng hoặc bất kỳ thông tin xác thực nhạy cảm nào ra đầu ra thiết bị đầu cuối, tin nhắn hội thoại hay lịch sử commit.
3. **Quy chuẩn hai gói cùng tồn tại (Dual-Flavor)**:
   - APK dựng từ quá trình tự phát triển tùy chỉnh phải được đặt tên là `TianXuanDev`;
   - Tên gói phải đổi thành `top.wkbin.tianxuan.dev`;
   - Thư mục lưu trữ và cấu hình mặc định phải được cách ly với bản chính thức, bảo đảm bản thử nghiệm cùng tồn tại hoàn toàn với bản CTHarness chính thức đang chạy trên điện thoại mà không ảnh hưởng lẫn nhau.

---

## 2. Quy trình phát triển lặp

1. **Chẩn đoán môi trường**:
   - Kiểm tra trạng thái cài đặt `git` và `gh`;
   - Xác nhận đang ở trong không gian làm việc `~/custom_tianxuan`;
   - Kiểm tra trạng thái nhánh Git hiện tại và bảo đảm không gian làm việc sạch.
2. **Ràng buộc về quy chuẩn kiến trúc**:
   - **Tầng UI**: Chỉ sử dụng Jetpack Compose + Material3, tuân thủ quy chuẩn đặt tên `top.wkbin.tianxuan.ui`;
   - **Quản lý trạng thái**: Dùng Koin để tiêm ViewModel, công khai trạng thái bằng `StateFlow` + `collectAsStateWithLifecycle`;
   - **Hộp cát Linux**: Các lời gọi hệ thống và tương tác PRoot tuân thủ hợp đồng tiêu chuẩn của mô-đun `runtime`.
3. **Kiểm thử nhanh cục bộ**:
   - Trước khi gửi thay đổi, chạy `./gradlew testDebugUnitTest` để thực hiện các kiểm thử đơn vị cơ bản;
   - Sau khi xác minh không có lỗi, tạo nhánh tính năng `feature/xxx` để commit.

---

## 3. Dựng APK TianXuanDev (ưu tiên GitHub Actions)

Do khác biệt giữa kiến trúc điện thoại (ARM64) và bộ công cụ biên dịch Android tiêu chuẩn (x86_64), ưu tiên dựng bằng CI GitHub Actions trên đám mây:

1. Bảo đảm kho Fork của người dùng đã có `.github/workflows/tianxuandev-build.yml`;
2. Kích hoạt workflow trong thiết bị đầu cuối và theo dõi trạng thái theo thời gian thực:
   ```bash
   gh workflow run tianxuandev-build.yml --ref <feature-branch>
   gh run watch <run-id> --exit-status
   ```
3. Sau khi biên dịch thành công, tải sản phẩm APK đã tạo:
   ```bash
   gh run download <run-id> -n tianxuandev-apk -D /storage/emulated/0/Download/
   ```
4. Xác minh tên gói APK đã tạo (`top.wkbin.tianxuan.dev`) và chữ ký đều chính xác, rồi đề nghị người dùng cài đặt để trải nghiệm.

---

## 4. Quy chuẩn gửi PR nguồn mở

Sau khi cài đặt và kiểm chứng trên thiết bị thật đạt yêu cầu, hỗ trợ người dùng tạo mẫu PR chuẩn và gửi tới kho chính thức của CTHarness:
- **Kho đích**: `wkbin/tianxuan:main`
- **Nhánh nguồn**: `user-fork:feature/xxx`
- **Mẫu PR gồm**: Tổng quan (Summary), các thay đổi (Changes), kết quả kiểm thử (Testing), ảnh chụp/ghi màn hình (Screenshots), rủi ro tiềm ẩn (Risks).
