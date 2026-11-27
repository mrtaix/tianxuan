# Giai đoạn 3 của bản dựng di động: tự kiểm tra thống nhất và phương án dự phòng bằng lệnh

## Mục tiêu

Giai đoạn 3 thống nhất các điểm vào quy trình dựng Android/Flutter theo cùng một chiến lược môi trường. Dù người dùng bắt đầu dựng từ nút dựng trong không gian làm việc, từ Agent, hay từ bảng điều khiển PRoot, hệ thống đều phải xác nhận trước rằng bộ công cụ hiện tại khả dụng và ưu tiên dùng tài nguyên ARM64 do CTHarness cài đặt.

## Điểm vào chuẩn

```sh
tianxuan-build doctor /workspace/project
tianxuan-build android /workspace/project assembleDebug
tianxuan-build flutter /workspace/project apk --debug
tianxuan-build analyze /workspace/project
tianxuan-build android /workspace/project assembleDebug --offline
```

`doctor` ít nhất kiểm tra:

- `/bin/sh` và kiến trúc của RootFS hiện tại;
- JDK 17 và `JAVA_HOME`;
- Gradle hoặc Gradle Wrapper của dự án;
- Android SDK, gói nền tảng, Build-Tools và AAPT2 ARM64;
- Flutter/Dart (đối với dự án Flutter);
- xem các phiên bản Gradle Wrapper, AGP, `compileSdk` và NDK mà dự án khai báo có vượt quá khả năng của máy hay không;
- công tắc QEMU và môi trường tương thích x86_64 cô lập đã đầy đủ hay chưa.

Giai đoạn 4 bổ sung `analyze`, chế độ dựng ngoại tuyến và bước kiểm tra ABI của APK đầu ra, xem chi tiết tại [`MOBILE_BUILD_STAGE4.md`](MOBILE_BUILD_STAGE4.md).

## Quy tắc quyết định

1. Mặc định chỉ dùng bộ công cụ ARM64.
2. Khi phiên bản dự án không khớp, trước tiên hãy in ra phần khác biệt, không lặng lẽ tải xuống một bộ SDK/JDK/NDK khác.
3. Khi cần điều chỉnh dự án, kỹ năng "mobile build environment guard" của Agent sẽ thực hiện căn chỉnh phiên bản tối thiểu.
4. Chỉ khi gặp lỗi rõ ràng về công cụ ELF x86_64 trên máy chủ thì mới cho phép dùng `--qemu` để vào phiên QEMU cô lập.
5. QEMU không làm thay đổi ABI của APK; sản phẩm cuối cùng vẫn chỉ chứa ARM64.

## Lưu ý về bảng điều khiển

Một trình bao thông thường không thể chặn hoàn toàn 100% khi người dùng nhập lệnh với một đường dẫn tuyệt đối bất kỳ. Vì vậy, giai đoạn 3 dùng lớp bọc tại `/opt/tianxuan/bin`, lệnh `tianxuan-build` thống nhất và kỹ năng của Agent để bao phủ các điểm vào dựng thông thường `gradle`, `gradlew`, `flutter`. Trường hợp người dùng cố ý bỏ qua lớp bọc và thực thi trực tiếp một đường dẫn tuyệt đối bên trong SDK được xem là thao tác thủ công nâng cao.
