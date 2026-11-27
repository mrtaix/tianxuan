# Giai đoạn 4 của bản dựng di động

Giai đoạn 4 nâng giai đoạn 3 từ mức "môi trường chạy được" lên mức "có thể kiểm chứng dự án và sản phẩm đầu ra".

## Năng lực mới

```sh
tianxuan-build analyze /workspace/project
tianxuan-build analyze /workspace/project --offline
tianxuan-build android /workspace/project assembleDebug --offline
tianxuan-build flutter /workspace/project apk --debug --offline
```

`analyze` là kiểm tra chỉ đọc, báo cáo:

- loại dự án Android/Flutter;
- `compileSdk` có vượt quá gói nền tảng 34 tích hợp sẵn hay không;
- Gradle Wrapper có lệch khỏi 8.14.2 hay không;
- có khai báo ABI `x86`/`x86_64` hay không;
- trong chế độ ngoại tuyến, bộ nhớ đệm Gradle/Pub có tồn tại hay không.

Trình phân tích sẽ không sửa đổi dự án của bên thứ ba. Kỹ năng `mobile_project_align` trước tiên phải trình bày giá trị hiện tại, giá trị mục tiêu, các tệp sẽ sửa và rủi ro, chỉ được căn chỉnh sau khi có xác nhận của người dùng.

## Chiến lược ngoại tuyến

`--offline` được áp dụng xuyên suốt Gradle và Flutter:

- Gradle dùng `--offline`;
- Flutter Pub dùng `pub get --offline`;
- bản dựng Flutter dùng `build ... --offline`;
- nếu bộ nhớ đệm không tồn tại thì báo lỗi trước khi bắt đầu dựng, không lặng lẽ kết nối mạng.

## Bước kiểm tra sản phẩm đầu ra

Sau khi dựng thành công, cả điểm vào bảng điều khiển và phía máy chủ quản lý không gian làm việc đều quét tệp ZIP của APK:

- cho phép `lib/arm64-v8a/`;
- từ chối `lib/x86/` và `lib/x86_64/`;
- từ chối bất kỳ ABI mã máy nào khác;
- khi thất bại thì không xuất và không cài đặt.

QEMU chỉ giải quyết vấn đề thực thi của các công cụ máy chủ Linux x86_64, không làm thay đổi ABI của APK cuối cùng.
