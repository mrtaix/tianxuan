# Hướng dẫn tối ưu khởi động bằng Baseline Profile

> Giai đoạn hai tối ưu khởi động nguội của CTHarness: biên dịch trước (AOT) các phương thức được dùng nhiều ngay khi cài đặt, loại bỏ chi phí JIT biên dịch tại chỗ trong lần khởi động đầu tiên.

## Nguyên lý

APK nhúng `assets/dexopt/baseline.prof` (được biên dịch và đóng gói từ `app/src/release/generated/baselineProfiles/`).
Sau lần cài đặt đầu tiên, `androidx.profileinstaller` gửi hồ sơ này cho ART biên dịch trước trong nền, bao phủ:

- Toàn bộ đường khởi động từ `Application.onCreate` → dựng đồ thị Koin → tạo cây giao diện Compose của màn hình đầu tiên
- Đường thực thi cuộn LazyColumn ở khung hình đầu tiên

## Cấu trúc mô-đun

| Đường dẫn | Trách nhiệm |
| :--- | :--- |
| `baselineprofile/` | Mô-đun tạo hồ sơ (`com.android.test` + macrobenchmark), chứa các ca thu thập |
| `baselineprofile/.../StartupBaselineProfile.kt` | Kịch bản thu thập: khởi động nguội + vuốt nhẹ màn hình chính |
| `app/src/release/generated/baselineProfiles/` | Sản phẩm đầu ra, **được đưa vào cùng kho mã nguồn** |
| `app/build.gradle.kts` | Cấu hình bên sử dụng: plugin + `baselineProfile(project(":baselineprofile"))` |

## Thu thập lại (sau thay đổi lớn ở giao diện ứng dụng hoặc luồng khởi động)

Điều kiện tiên quyết: một thiết bị/trình giả lập đã kết nối và mở khóa (thiết bị thật hoặc AVD đều được).

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:generateBaselineProfile --console=plain
```

Dấu hiệu thành công: `app/src/release/generated/baselineProfiles/*.txt` được cập nhật, kiểm thử báo `1/1 completed (0 failed)`.

### Chỉ định đích khi có nhiều thiết bị

```powershell
$env:ANDROID_SERIAL = "emulator-5554"   # Hoặc số sê-ri thiết bị cụ thể
```

### Khi máy cục bộ không có thiết bị: tự khởi động một AVD không có giao diện

```powershell
# Tạo một lần (có thể dùng trực tiếp khi repository này đã có image android-37.1 google_apis_playstore x86_64)
powershell -NoProfile -Command "Start-Process -FilePath \"$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe\" -ArgumentList '@<avd名>','-no-window','-no-audio','-no-boot-anim'"
adb -s emulator-5554 shell settings put secure user_setup_complete 1
adb -s emulator-5554 shell settings put global device_provisioned 1
```

Trình giả lập x86_64 có thể chạy ứng dụng chỉ hỗ trợ arm64 (trình giả lập 37+ tự động dịch nhị phân), nên việc CTHarness chỉ hỗ trợ arm64-v8a không bị ảnh hưởng.

## Các bẫy đã biết

1. **Cơ chế tăng cường bảo mật USB của các ROM Trung Quốc như vivo** có thể chặn cơ chế kiểm thử instrumentation bằng lỗi `Calling from not trusted UID!`,
   biểu hiện là `Failed to install split APK(s)` hoặc tiến trình kiểm thử chết ngay — không thể dùng loại thiết bị này để thu thập, hãy chuyển sang trình giả lập.
2. Quá trình thu thập dùng biến thể nonMinified; nếu luồng chính của bản phát hành ném lỗi (chẳng hạn lỗi biểu thức chính quy chỉ lộ ra sau R8),
   giai đoạn thu thập sẽ thất bại với `Process crashed`. Hãy sửa lỗi văng ứng dụng trước rồi mới thu thập.
3. Buộc dừng tiến trình nền Gradle có thể để lại bộ nhớ đệm KSP hỏng (báo `NoSuchFileException ... kspCaches\...\*_Factory.java`),
   chỉ cần xóa `build/kspCaches` của mô-đun tương ứng rồi chạy lại.

## Xác minh hiệu quả

```powershell
adb install -r app/build/outputs/apk/release/tianxuan-v0.6.0-release.apk
adb shell am start -W top.wkbin.tianxuan/.MainActivity   # So sánh TotalTime
adb logcat -s TianXuanStartup                            # Thời gian splash dismissed
```

Việc chuyển sang Koin đã loại bỏ các mục trong hồ sơ của lớp tiêm phụ thuộc (DI) được sinh trước đây; cần thu thập lại đầy đủ các điểm nóng khởi động mới trên thiết bị ARM64 theo quy trình này, không thể chỉ thay tên lớp.
