# 🛠️ CTHarness — Sổ tay lệnh phát triển và tự động hóa thường dùng

---

## 1. Yêu cầu môi trường biên dịch

- **Android SDK**: Dự án chính cần Android SDK Platform 37.1 (do A2UI alpha yêu cầu); targetSdk vẫn là 37. Khi giấy phép SDK đã được chấp nhận, Gradle có thể tự động cài đặt nền tảng bị thiếu.

- **Thiết lập biến môi trường JDK (Windows PowerShell)**:
  Phải thiết lập `JAVA_HOME` trỏ tới JBR đi kèm Android Studio (hỗ trợ Java 25 / 17+) rồi mới chạy các tác vụ Gradle:
  ```powershell
  $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
  # 或自定义安装路径（例如 D:\Program Files\Android\Android Studio\jbr）
  ```

---

## 2. Lệnh biên dịch và kiểm thử cốt lõi

```powershell
# 1. Chạy toàn bộ unit test (hồi quy toàn kho)
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat test --console=plain

# 2. Chạy unit test của một module
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :feature:settings:testDebugUnitTest --console=plain

# 3. Chỉ biên dịch mã Kotlin (kiểm tra nhanh cú pháp và kiểu)
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat compileDebugKotlin --console=plain

# 4. Kiểm tra biên dịch nhanh cho một module
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :harness:compileDebugKotlin --console=plain -q

# 5. Biên dịch và đóng gói Debug APK
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat assembleDebug --console=plain

# 6. Kiểm tra ranh giới phụ thuộc kiến trúc (nguồn sự thật duy nhất của quy tắc: architecture-policy.json ở thư mục gốc)
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat architectureCheck --console=plain
# Nội dung kiểm tra: danh sách trắng phụ thuộc module (bao gồm phụ thuộc ngược), đồ thị phụ thuộc không có chu trình,
#          danh sách đen import (tầng model thuần Kotlin / DAO kết nối trực tiếp),
#          cơ chế ratchet kích thước tệp (maxFileLines=400; các tệp vượt giới hạn từ trước được đăng ký trong .architecture-baseline.json và chỉ được phép giảm)

# 6.1 Đồng bộ baseline ratchet kích thước (thu hạ khi rút gọn / từ chối tăng / dọn mục đã hết hạn; chạy lệnh dưới đây sau khi rút gọn mã để hạ baseline)
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat architectureBaselineSync --console=plain
# Lưu ý: khi thêm module mới hoặc điều chỉnh quan hệ phụ thuộc module, phải đồng bộ đăng ký mục requires trong architecture-policy.json, nếu không cổng kiểm tra sẽ thất bại

# 7. Chỉ xác minh hợp đồng công cụ tích hợp sẵn của Harness và chính sách thực thi
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat :harness:testDebugUnitTest --tests "top.wkbin.tianxuan.harness.*" --console=plain
```

---

## 3. Triển khai lên thiết bị và xem nhật ký gỡ lỗi thời gian thực

```powershell
# 1. Cài đặt Debug APK lên thiết bị thật hoặc emulator đã kết nối
adb install -r app/build/outputs/apk/debug/tianxuan-v0.5.0-debug.apk

# 2. Khởi chạy Activity cổng vào chính của CTHarness
adb shell am start -n top.wkbin.tianxuan/.MainActivity

# 3. Lọc log thời gian thực của runtime CTHarness và lõi agent
adb logcat -s TianXuan:V HarnessLoop:V ProotProcess:V
```

## 4. Hồi quy đồ thị phụ thuộc Koin

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "top.wkbin.tianxuan.di.*" --console=plain
```

Sau khi sửa đăng ký phụ thuộc, hãy chạy đồng thời toàn bộ kiểm thử đơn vị và `:app:assembleDebug`. Cơ chế tiêm phụ thuộc đã được chuyển sang Koin DSL.
