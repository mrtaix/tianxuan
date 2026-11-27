## Thư viện máy khách Shower (mô-đun `showerclient`)

Thư viện máy khách Shower tối giản, chỉ trình bày **cách tích hợp và sử dụng**.

---

## 1. Thêm phụ thuộc

Trong `build.gradle.kts` của mô-đun ứng dụng chủ:

```kotlin
dependencies {
    implementation(project(":showerclient"))
}
```

---

## 2. Cung cấp phần triển khai `ShellRunner`

`showerclient` không trực tiếp thực thi lệnh shell mà yêu cầu bên ngoài cung cấp khả năng này qua một giao diện:

```kotlin
fun interface ShellRunner {
    suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult
}
```

Triển khai giao diện trong mô-đun ứng dụng và đưa vào khi ứng dụng khởi động:

```kotlin
class OperitShowerShellRunner : ShellRunner {
    override suspend fun run(command: String, identity: ShellIdentity): ShellCommandResult {
        // Gọi trình thực thi shell của bạn tại đây
        // rồi chuyển đổi và trả về kết quả dưới dạng ShellCommandResult
    }
}

class YourApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        ShowerEnvironment.shellRunner = OperitShowerShellRunner()
    }
}
```

`ShellIdentity` dùng để phân biệt danh tính thực thi, chẳng hạn `DEFAULT` / `SHELL` / `ROOT`; hãy ánh xạ theo nhu cầu của dự án.

---

## 3. Shower Server JAR (đã tích hợp sẵn)

Thư viện đã tích hợp `shower-server.jar` ngay trong mô-đun:

- Ứng dụng chủ **không cần** đóng gói hay sao chép thêm tệp JAR nào theo cách thủ công;
- Khi chạy, thư viện tự đọc tệp từ `assets` của mình, sao chép trước vào `/sdcard/Download/TianXuan/shower-server.jar`
  (cần quyền lưu trữ), rồi chép vào **thư mục làm việc có quyền ghi được phát hiện** (lần lượt thử
  `ShowerEnvironment.workDirCandidates`, mặc định là `/data/local/tmp` → `/data/data/com.android.shell/files`).

---

## 4. Xử lý broadcast bàn giao Binder

Sau khi khởi động, Shower server gửi Binder của `IShowerService` dưới dạng `IBinder` tới máy khách qua broadcast. Ứng dụng chủ cần chuyển Binder cho thư viện trong bộ nhận broadcast:

Giao thức broadcast (đồng bộ với dự án chính):

 - **Action**: `com.ai.assistance.operit.action.SHOWER_BINDER_READY`
 - **Extra key**: `binder_container`
 - **Kiểu Extra**: `com.ai.assistance.shower.ShowerBinderContainer` (`Parcelable`, chứa `IBinder`)

```kotlin
class ShowerBinderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SHOWER_BINDER_READY) return

        val container = intent.getParcelableExtra<ShowerBinderContainer>(EXTRA_BINDER_CONTAINER)
        val binder = container?.binder ?: return
        val service = IShowerService.Stub.asInterface(binder)
        ShowerBinderRegistry.setService(service)
    }

    companion object {
        const val ACTION_SHOWER_BINDER_READY =
            "com.ai.assistance.operit.action.SHOWER_BINDER_READY"
        const val EXTRA_BINDER_CONTAINER = "binder_container"
    }
}
```

Đăng ký Receiver trong `AndroidManifest.xml` (**cần `exported=true`**, vì bên gửi broadcast chạy trong tiến trình shower-server riêng):

```xml
<receiver
    android:name=".ShowerBinderReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="com.ai.assistance.operit.action.SHOWER_BINDER_READY" />
    </intent-filter>
</receiver>
```

Giải thích:

 - Lệnh khởi động của `ShowerServerManager` kèm tham số tên gói ứng dụng chủ: `CLASSPATH=<workDir>/shower-server.jar app_process / com.ai.assistance.shower.Main <hostPackage> &`;
   `<workDir>` là thư mục có quyền ghi được phát hiện khi chạy (xem phần trên).
 - Sau khi đọc tham số này, shower-server gửi `SHOWER_BINDER_READY` qua `IActivityManager.broadcastIntent(...)` và dùng `Intent.setPackage(<hostPackage>)` để chỉ gửi tới gói ứng dụng chủ đích.
 - Vì vậy, phía ứng dụng chủ cần bảo đảm **action của intent-filter khai báo trong Manifest phải khớp**, đồng thời chuyển `ShowerBinderContainer` cho `ShowerBinderRegistry.setService()` trong `onReceive()`.

---

## 5. Khởi động Shower server và tạo màn hình ảo

Tại nơi cần dùng Shower (chẳng hạn trong một Agent hoặc Service):

```kotlin
// 1) Bảo đảm server đã khởi động và đã nhận Binder thành công
val ok = ShowerServerManager.ensureServerStarted(context)
if (!ok) return

// 2) Tạo / cập nhật màn hình ảo
val displayOk = ShowerController.ensureDisplay(
    context = context,
    width = 1080,
    height = 2400,
    dpi = 480,
    bitrateKbps = 8000,
)
if (!displayOk) return
```

Sau đó có thể gửi sự kiện đầu vào qua `ShowerController`:

```kotlin
ShowerController.touchDown(x, y)
ShowerController.touchMove(x, y)
ShowerController.touchUp(x, y)
ShowerController.key(KeyEvent.KEYCODE_BACK)
```

Chụp ảnh màn hình:

```kotlin
val pngBytes: ByteArray? = ShowerController.requestScreenshot()
```

---

## 6. Sử dụng thành phần video tích hợp sẵn (tùy chọn)

Nếu muốn dùng lại trực tiếp khả năng giải mã và kết xuất video tích hợp sẵn, bạn có thể sử dụng:

- `com.ai.assistance.showerclient.ShowerVideoRenderer`
- `com.ai.assistance.showerclient.ui.ShowerSurfaceView`

Ví dụ: kết xuất màn hình ảo Shower trong một lớp phủ:

```kotlin
// SurfaceView (cũng có thể kế thừa ui.ShowerSurfaceView để tạo lớp bọc)
class ShowerSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : com.ai.assistance.showerclient.ui.ShowerSurfaceView(context, attrs)
```

`ui.ShowerSurfaceView` đã xử lý sẵn các việc sau:

- Chờ `ShowerController.getVideoSize()` sẵn sàng;
- Gọi `ShowerVideoRenderer.attach(surface, w, h)` khi `surfaceCreated`;
- Gắn hàm gọi lại nhận khung hình nhị phân của `ShowerController` với `ShowerVideoRenderer.onFrame`;
- Khi `surfaceDestroyed`, dọn dẹp handler và gọi `detach()`.

Nếu cần tùy chỉnh giải mã/kết xuất (ghi màn hình, phát luồng, v.v.), bạn có thể bỏ qua các thành phần này và tự triển khai:

```kotlin
ShowerController.setBinaryHandler { frame: ByteArray ->
    // Tự giải mã và kết xuất
}
```

---

## 7. Mô hình sử dụng tối giản

- Bạn cung cấp: `ShellRunner`, bộ nhận broadcast;
- Thư viện cung cấp: `ShowerServerManager` + `ShowerController` + `ShowerVideoRenderer` / `ui.ShowerSurfaceView` tùy chọn;
- Trình tự gọi thông thường: **đưa ShellRunner vào → khởi động server → nhận Binder → ensureDisplay → gửi đầu vào / kết xuất video**.
