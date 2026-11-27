# Mô-đun tự phát triển tùy chỉnh CTHarness và dựng TianXuanDev (`feature:custom_iteration`)

Mô-đun này là hệ thống tự phát triển tiêu chuẩn, không hạ cấp chức năng, được thiết kế riêng cho **CTHarness**. Người dùng có thể dùng AI Agent tự chủ của CTHarness ngay trên điện thoại để phát triển mở rộng chính ứng dụng, kiểm thử, dựng bằng CI trên đám mây và đóng góp mã cho cộng đồng nguồn mở.

---

## I. Kiến trúc mô-đun và các thành phần cốt lõi

```
tianxuan-custom-iteration-module/
├── build.gradle.kts                        # Cấu hình phụ thuộc Gradle đa mô-đun (theo chuẩn CTHarness)
├── README.md                               # Tài liệu đầy đủ về kiến trúc và tích hợp mô-đun
├── templates/
│   └── workflows/
│       └── tianxuandev-build.yml              # Mẫu CI dựng tự động trên đám mây bằng GitHub Actions
└── src/
    ├── main/
    │   ├── assets/
    │   │   └── skills/
    │   │       └── tianxuan-custom-iteration/
    │   │           └── SKILL.md            # Skill quy định phát triển và PR riêng cho Agent
    │   └── java/top/wkbin/tianxuan/
    │       ├── iteration/engine/
    │       │   ├── CustomIterationBootstrap.kt   # Bộ máy khởi tạo (khởi tạo không gian làm việc / tạo prompt hướng dẫn)
    │       │   └── TianXuanDevBuildCoordinator.kt   # Bộ điều phối CI, tải và xác minh APK
    │       └── ui/iteration/
    │           ├── CustomIterationScreen.kt      # Giao diện UI Jetpack Compose
    │           └── CustomIterationViewModel.kt   # ViewModel quản lý trạng thái bằng Koin
```

---

## II. Tính năng cốt lõi và quy chuẩn thiết kế (không hạ cấp chức năng)

1. **Cách ly vật lý không gian làm việc**:
   * Tự động tạo thư mục cách ly `~/custom_tianxuan` trong thư mục cá nhân của hộp cát Linux; hoạt động phát triển không làm hỏng hệ thống chủ đang chạy.
2. **Ràng buộc bằng Agent Skill riêng (`SKILL.md`)**:
   * Hướng dẫn AI Agent tuân thủ nghiêm ngặt các quy chuẩn Kotlin DSL đa mô-đun, Material3 Compose, thiết kế coroutine và bảo mật thông tin xác thực của CTHarness.
3. **Dựng bằng CI GitHub Actions trên đám mây (`tianxuandev-build.yml`)**:
   * Tránh chi phí dung lượng lớn khi triển khai Android SDK/NDK trên điện thoại;
   * Runner đám mây (`ubuntu-24.04` + JDK 17) tự động biên dịch, sau đó dùng `gh run watch` / `gh run download` để đưa sản phẩm về điện thoại.
4. **Hai gói cùng tồn tại (Dual-Flavor)**:
   * Tên gói của sản phẩm biên dịch tự động đổi thành `top.wkbin.tianxuan.dev`, tên ứng dụng hiển thị là `TianXuanDev`;
   * Bản thử nghiệm và bản CTHarness chính thức trên điện thoại chạy song song, không ghi đè nhau.
5. **Quy trình khép kín để đóng góp PR nguồn mở**:
   * Sau khi trải nghiệm trên thiết bị thật đạt yêu cầu, Agent hỗ trợ tạo PR theo định dạng chuẩn và gửi tới `TensorHub-ORG/TianXuan:main`.

---

## III. Hướng dẫn tích hợp nhanh (vào dự án chính CTHarness)

### 1. Đăng ký mô-đun con Gradle
Thêm vào `settings.gradle.kts`:
```kotlin
include(":feature:custom_iteration")
```

### 2. Thêm tuyến điều hướng
Thêm mục định tuyến trong `TianXuanNavHost.kt`:
```kotlin
composable("custom_iteration") {
    CustomIterationScreen(
        onBack = { navController.popBackStack() },
        onNavigateToChat = { prompt ->
            navController.navigate("chat?prompt=${Uri.encode(prompt)}")
        }
    )
}
```

### 3. Thêm lối vào trong phần cài đặt hoặc trung tâm nhà phát triển
Chỉ cần thêm nút điều hướng trong `SettingsScreen.kt` hoặc `DeveloperScreen.kt`!
