# Báo cáo rà soát mã toàn dự án CTHarness

> Thời gian rà soát: 2026-08-31
> Phạm vi rà soát: toàn bộ mô-đun (app / core / runtime / tools / harness / feature)
> Quy mô mã: khoảng 330+ tệp nguồn Kotlin + mã JNI C

---

## Tóm tắt

| Mức độ nghiêm trọng | Số lượng | Giải thích |
| :--- | :---: | :--- |
| 🔴 Nghiêm trọng | 2 | Nguy cơ mất dữ liệu, vượt ranh giới bảo mật |
| 🟠 Cao | 5 | Bế tắc, văng ứng dụng do truy cập đồng thời, rò rỉ tài nguyên |
| 🟡 Trung bình | 7 | Lỗi chức năng, hiệu năng, độ bền |
| 🟢 Thấp | 6 | Chất lượng mã, khả năng bảo trì |

---

### C2. Lỗ hổng duyệt đường dẫn trong HostBridge: môi trường cách ly có thể truy cập tùy ý đường dẫn tệp trên máy chủ

**Vị trí**: `runtime/src/main/java/top/wkbin/tianxuan/runtime/bridge/HostBridge.kt:305-315`

**Vấn đề**:
```kotlin
private fun resolveSandboxPath(sandboxPath: String): String {
    return when {
        sandboxPath.startsWith("/sdcard/") -> "/storage/emulated/0/${...}"
        sandboxPath == "/sdcard" -> "/storage/emulated/0"
        sandboxPath.startsWith("/storage/emulated/0/") -> sandboxPath
        else -> sandboxPath  // ← 原样返回任意宿主绝对路径
    }
}
```

`handleInstallApk` nhận đường dẫn do môi trường cách ly truyền vào, và `resolveSandboxPath` **trả nguyên vẹn** các đường dẫn không có tiền tố chuẩn. Điều này có nghĩa là tiến trình trong môi trường cách ly có thể truyền vào:
- `/data/data/top.wkbin.tianxuan/databases/tianxuan.db` (cơ sở dữ liệu riêng của ứng dụng)
- `/data/app/.../base.apk` (APK của ứng dụng khác)
- `/system/...` (phân vùng hệ thống)

Dù có xác thực Bearer [REDACTED], nhưng mã xác thực được ghi vào `/opt/tianxuan/.bridge-key`, người dùng root trong môi trường cách ly có thể đọc trực tiếp.

**Tác động**: Thoát môi trường cách ly, có thể đọc/cài APK ở bất kỳ đường dẫn nào trên máy chủ.

**Gợi ý sửa**:
```kotlin
private fun resolveSandboxPath(sandboxPath: String): String? {
    return when {
        sandboxPath.startsWith("/sdcard/") -> "/storage/emulated/0/${sandboxPath.removePrefix("/sdcard/")}"
        sandboxPath.startsWith("/storage/emulated/0/") -> sandboxPath
        else -> null  // 拒绝其他路径
    }
}
```
Trong `handleInstallApk`, trả lỗi 400 khi kết quả là null. Đồng thời nên đổi mã xác thực để mỗi lần khởi tạo tạo ra mã mới và không ghi vào vị trí môi trường cách ly đọc được.

---

## 🟠 Vấn đề ưu tiên cao

### H1. `runBlocking` trong hàm suspend có thể gây bế tắc

**Vị trí**: `runtime/src/main/java/top/wkbin/tianxuan/runtime/LinuxRuntimeImpl.kt:783`

**Vấn đề**:
```kotlin
private fun configureRootfs(distroId: String = "ubuntu") {
    // ...
    runCatching {
        kotlinx.coroutines.runBlocking {  // ← 在普通函数中阻塞等待协程
            assetSynchronizer.syncAssetsToDistro(distroId)
        }
    }
}
```

`configureRootfs` được gọi từ các hàm suspend như `initialize()`, `installDistro()`, `resetSandbox()`, vốn chạy trong ngữ cảnh `Dispatchers.IO`. `runBlocking` sẽ chặn luồng hiện tại; nếu `syncAssetsToDistro` cần chuyển sang chính nhóm luồng đang bị chặn đó (ví dụ khi `Dispatchers.IO` cạn luồng), sẽ dẫn đến **bế tắc**.

**Gợi ý sửa**: Đổi `configureRootfs` thành hàm suspend, gọi trực tiếp `assetSynchronizer.syncAssetsToDistro(distroId)` và bỏ `runBlocking`.

---

### H2. `ProcessRegistry` truy cập `LinkedHashMap` đồng thời gây văng ứng dụng

**Vị trí**: `runtime/src/main/java/top/wkbin/tianxuan/runtime/shell/ProcessRegistry.kt:159`

**Vấn đề**:
```kotlin
private val processes = LinkedHashMap<String, ManagedProcess>()  // 非线程安全

override fun list(): List<ManagedProcess> = processes.values.toList()  // ← 无锁访问
```

`start()`, `stop()`, `stopAll()`, `cleanupDeadProcesses()` đều sửa `processes` trong `mutex.withLock`, nhưng `list()` lại đọc trực tiếp không có khóa. Trong môi trường đa luồng (luồng giao diện gọi `listBackground()`, luồng vào/ra gọi `stopAll()`), có thể ném `ConcurrentModificationException`.

**Gợi ý sửa**:
```kotlin
override fun list(): List<ManagedProcess> = synchronized(processes) {
    processes.values.toList()
}
```
Hoặc đổi sang `ConcurrentHashMap` (nhưng cần lưu ý yêu cầu về thứ tự).

---

### H3. `ProcessRegistry` có khối bắt ngoại lệ rỗng, nuốt tất cả ngoại lệ

**Vị trí**: `runtime/src/main/java/top/wkbin/tianxuan/runtime/shell/ProcessRegistry.kt:122`

**Vấn đề**:
```kotlin
scope.launch {
    try {
        session.output.collect { terminalOutput -> ... }
    } catch (_: Exception) {  // ← 静默吞掉所有异常
    } finally { ... }
}
```

Bắt `Exception` rồi không làm gì cả, bao gồm:
- `OutOfMemoryError` (kế thừa Error, không bị Exception bắt, nhưng các lỗi nghiêm trọng khác như `IOException` bị nuốt)
- Ngoại lệ hủy coroutine (`CancellationException` phải ném lại)
- Bộ thu thập nhật ký bị lỗi, khiến các nhật ký sau không thu thập được mà không có bất kỳ cảnh báo nào

**Gợi ý sửa**:
```kotlin
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (e: Exception) {
    logger.w("Process log collector failed for $id", e)
}
```

---

### H4. `NativePtySession.close()` có lỗi tranh chấp

**Vị trí**: `runtime/src/main/java/top/wkbin/tianxuan/runtime/pty/NativePtySession.kt:106-119`

**Vấn đề**:
```kotlin
override suspend fun close() = withContext(Dispatchers.IO) {
    if (closed.compareAndSet(false, true)) {
        readerJob.cancel()        // 异步取消，不等待
        sessionScope.cancel()     // 异步取消
        NativePty.killPid(childPid, 1)
        NativePty.killPid(childPid, 9)
        NativePty.waitPid(childPid)
        NativePty.closeFd(masterFd)  // ← readerJob 可能仍在 readFd
        outputChannel.close()
    }
}
```

Sau `readerJob.cancel()` không có `join()`, coroutine đọc có thể vẫn đang bị chặn trong `NativePty.readFd(masterFd)`. Lúc này `closeFd(masterFd)` đóng bộ mô tả tệp; nếu luồng/coroutine khác mở tệp mới và dùng lại số fd đó, bộ đọc sẽ đọc dữ liệu sai hoặc ghi vào kênh đã đóng.

**Gợi ý sửa**:
```kotlin
readerJob.cancel()
readerJob.join()  // 等待 reader 退出
NativePty.closeFd(masterFd)
```

---

### H5. `WorkspaceFileAccess` bị tấn công TOCTOU qua liên kết tượng trưng khi ghi

**Vị trí**: `harness/src/main/java/top/wkbin/tianxuan/harness/WorkspaceFileAccess.kt:80-99`

**Vấn đề**:
```kotlin
suspend fun write(path: String, content: String): AppResult<Unit> {
    val file = resolveWritable(path)  // ① 检查路径在工作区内
    file.parentFile?.mkdirs()
    val temporary = File(file.parentFile, ".${file.name}.tmp-${System.nanoTime()}")
    temporary.writeText(content, Charsets.UTF_8)
    if (!temporary.renameTo(file)) {  // ② 此时 file 可能已被替换为符号链接
        temporary.copyTo(file, overwrite = true)  // ← 跟随符号链接写出工作区
        temporary.delete()
    }
}
```

`resolveWritable` kiểm tra đường dẫn chuẩn hóa nằm trong không gian làm việc, nhưng trước khi `renameTo`/`copyTo`, nếu kẻ tấn công (hoặc tiến trình trong môi trường cách ly) thay thế tệp đích bằng liên kết tượng trưng trỏ ra ngoài không gian làm việc, thì `copyTo(file, overwrite = true)` sẽ **đi theo liên kết tượng trưng** và ghi vào bất kỳ tệp nào.

**Gợi ý sửa**: Kiểm tra lại `file.canonicalFile` trước khi ghi để đảm bảo vẫn nằm trong không gian làm việc; hoặc dùng ngữ nghĩa `O_NOFOLLOW` (Java NIO `LinkOption.NOFOLLOW_LINKS`).

---

## 🟡 Vấn đề ưu tiên trung bình

### M1. `SecretManager.decrypt()` có ngữ nghĩa kiểu trả về không nhất quán

**Vị trí**: `core/security/src/main/java/top/wkbin/tianxuan/core/security/SecretManager.kt:25-33`

**Vấn đề**:
```kotlin
fun decrypt(value: String): String? = runCatching {
    if (value.isEmpty()) return ""  // ← 空串返回 "" 而非 null
    // ...
}.getOrNull()  // ← 失败返回 null
```

Hàm khai báo trả về `String?`, nhưng chuỗi rỗng đầu vào trả về `""` (không phải null), còn giải mã thất bại trả về `null`. Bên gọi không phân biệt được "đầu vào rỗng" và "giải mã thành công ra chuỗi rỗng". Trong `SettingsDataStore.workshopKeystores`, `WorkshopKeystoreCodec.decode(secretManager.decrypt(ciphertext))` có thể NPE khi nhận null.

**Gợi ý sửa**: Thống nhất ngữ nghĩa — chuỗi rỗng trả về `null`, hoặc đổi sang trả về `Result<String>`.

---

### M2. `HostBridge` phân tích yêu cầu HTTP thiếu giới hạn độ dài

**Vị trí**: `runtime/src/main/java/top/wkbin/tianxuan/runtime/bridge/HostBridge.kt:140-174`

**Vấn đề**:
- `reader.readLine()` đọc dòng yêu cầu và các dòng tiêu đề không có giới hạn độ dài
- Máy khách độc hại có thể gửi một dòng tiêu đề dài 100MB gây cạn bộ nhớ
- `Content-Length` bị giới hạn 4MB, nhưng bản thân phần tiêu đề thì không bị giới hạn

**Gợi ý sửa**: Khi dùng `readLine()`, cộng dồn số byte đã đọc, vượt quá 8KB thì ngắt kết nối.

---

### M3. Thời gian chờ phát hiện công cụ MCP quá ngắn, thường xuyên thất bại khi môi trường cách ly khởi động nguội

**Vị trí**: `harness/src/main/java/top/wkbin/tianxuan/harness/mcp/McpManager.kt:136`

**Vấn đề**:
```kotlin
const val DISCOVERY_TIMEOUT_MS = 4_000L  // 4 秒
```

Kênh truyền STDIO của MCP cần khởi động tiến trình trong môi trường cách ly (có thể liên quan đến khởi động PRoot, nạp trình thông dịch Python, nhập các thư viện phụ thuộc); 4 giây thường không đủ trên thiết bị yếu. Khi hết thời gian chờ, lượt hội thoại đó không được bổ sung công cụ MCP, trải nghiệm người dùng là "công cụ lúc có lúc không".

**Gợi ý sửa**:
- Kéo dài lần phát hiện đầu tiên lên 15–30 giây
- Lưu kết quả phát hiện vào bộ nhớ đệm (đã có cơ chế này), các lượt sau dùng bộ nhớ đệm
- Làm mới bộ nhớ đệm bất đồng bộ trong nền, không chặn hội thoại

---

### M4. `ToolExecutor` dùng cơ chế tiêm phụ thuộc qua trường, thứ tự khởi tạo không an toàn

**Vị trí**: `harness/src/main/java/top/wkbin/tianxuan/harness/ToolExecutor.kt:65-66`

**Vấn đề**:
```kotlin
@Inject
lateinit var settingsDataStore: AgentPreferences
```

Hàm khởi tạo có 18 tham số tùy chọn (đa số là `? = null`), nhưng `settingsDataStore` lại dùng cơ chế tiêm phụ thuộc qua trường. Nếu truy cập `settingsDataStore` trong lúc hàm khởi tạo đang chạy (hoặc trong lambda khởi tạo của một tham số tùy chọn), sẽ ném `UninitializedPropertyAccessException`.

**Gợi ý sửa**: Chuyển `settingsDataStore` vào tham số hàm khởi tạo, dùng thống nhất cơ chế tiêm phụ thuộc qua hàm khởi tạo.

---

### M5. `HttpClientProvider` thiếu cấu hình nhóm kết nối và bộ chặn

**Vị trí**: `core/network/src/main/java/top/wkbin/tianxuan/core/network/HttpClientProvider.kt:13-17`

**Vấn đề**:
```kotlin
fun create(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(60, TimeUnit.SECONDS)
    .build()
```

- Không cấu hình nhóm kết nối (mặc định 5 kết nối nhàn rỗi, sống 5 phút, có thể không đủ cho kịch bản đồng thời cao)
- Không thêm bộ chặn ghi nhật ký HTTP (khó gỡ lỗi)
- Không cấu hình chiến lược thử lại (`retryOnConnectionFailure` mặc định là true nhưng không có khoảng chờ tăng dần tùy chỉnh)
- Một máy khách duy nhất dùng cho cả API LLM (truyền dữ liệu liên tục qua kết nối dài) lẫn tải tệp (lưu lượng lớn), hai bên ảnh hưởng lẫn nhau

**Gợi ý sửa**: Cấu hình đối tượng OkHttpClient riêng cho API LLM và cho tải tệp; thêm tinh chỉnh `ConnectionPool`.

---

### M6. `ProotCommandBuilder.build()` có tác dụng phụ (tạo thư mục)

**Vị trí**: `runtime/src/main/java/top/wkbin/tianxuan/runtime/proot/ProotCommandBuilder.kt:62`

**Vấn đề**:
```kotlin
attachmentsDir.mkdirs()  // ← 在"构建命令"的纯函数中创建目录
```

`build()` và `buildInteractive()` được thiết kế là hàm thuần để dựng lệnh, nhưng bên trong lại gọi `mkdirs()` tạo tác dụng phụ. Gọi `build()` trong kiểm thử đơn vị sẽ vô tình tạo thư mục; trong kịch bản gọi tần suất cao, việc gọi `mkdirs()` lặp lại gây tốn hiệu năng.

**Gợi ý sửa**: Bỏ `mkdirs()`, để bên gọi (`LinuxRuntimeImpl.initialize()`) tạo thư mục một lần lúc khởi tạo.

---

### M7. `ChatViewModel` có khai báo nhập trùng và không dùng

**Vị trí**: `feature/chat/src/main/java/top/wkbin/tianxuan/ui/chat/ChatViewModel.kt:46,53,55,61`

**Vấn đề**:
- Dòng 46 và 53 khai báo nhập trùng `import kotlinx.coroutines.flow.asStateFlow`
- Dòng 55 và 61 khai báo nhập trùng `import kotlinx.coroutines.flow.distinctUntilChanged`
- `android.util.Log` được nhập nhưng có thể không dùng
- `android.widget.Toast` được nhập nhưng có thể không dùng

**Tác động**: Ảnh hưởng độ gọn của mã, không ảnh hưởng chạy, nhưng cho thấy thiếu kiểm tra lint.

**Gợi ý sửa**: Chạy `./gradlew lint` để dọn toàn bộ khai báo nhập không dùng và trùng lặp.

---

## 🟢 Vấn đề ưu tiên thấp

### L1. Máy chủ DNS được cấu hình cố định, truy cập mạng tại Trung Quốc có thể chậm

**Vị trí**: `runtime/.../LinuxRuntimeImpl.kt:1056`
```kotlin
resolvConf.writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
```
Cloudflare (1.1.1.1) và Google (8.8.8.8) trong môi trường mạng nội địa có thể không ổn định hoặc bị nhiễu. Nên chọn theo môi trường mạng của người dùng, hoặc cung cấp tùy chọn DNS nội địa (223.5.5.5 / 119.29.29.29).

---

### L2. `HostBridge` trả lỗi làm lộ đường dẫn nội bộ

**Vị trí**: `runtime/.../HostBridge.kt:235`
```kotlin
return HttpResponse(404, errorJson("APK file not found: $apkPath (resolved: $hostPath)"))
```
Thông báo lỗi chứa `resolved: $hostPath`, làm lộ cấu trúc đường dẫn hệ thống tệp máy chủ cho bên gọi. Nên chỉ trả đường dẫn môi trường cách ly, đường dẫn nội bộ chỉ ghi nhật ký.

---

### L3. `stripSetuidBits` duyệt toàn bộ hệ thống tệp gốc, hiệu năng kém và có thể chặn luồng

**Vị trí**: `runtime/.../LinuxRuntimeImpl.kt:979-997`

Mỗi lần `configureRootfs` đều duyệt toàn bộ hệ thống tệp gốc (có thể hàng chục nghìn tệp), chặn vài giây đến vài chục giây lúc khởi tạo. Gợi ý:
- Chạy một lần khi khởi tạo đầu tiên
- Về sau xử lý phần thay đổi qua điểm móc `dpkg` (đã có `99tianxuan-strip-setuid`)
- Hoặc chạy bất đồng bộ trong nền, không chặn luồng khởi tạo

---

### L4. Thiếu bộ xử lý ngoại lệ toàn cục và bảo vệ ANR

**Vị trí**: `app/src/main/java/top/wkbin/tianxuan/TianXuanApplication.kt`

Dự án có `CrashReporter` (`core/common/.../CrashReporter.kt`), nhưng chưa xác nhận có đăng ký `Thread.setDefaultUncaughtExceptionHandler` trong Application hay không. Vòng lặp tác tử chạy trong dịch vụ tiền cảnh nếu ném ngoại lệ không bắt được có thể làm tiến trình dừng đột ngột mà không có nhật ký.

**Gợi ý sửa**: Xác nhận `TianXuanApplication.onCreate()` đã khởi tạo `CrashReporter`, đồng thời thêm xử lý ngoại lệ riêng cho dịch vụ tiền cảnh.

---

### L5. Phạm vi kiểm tra của tác vụ `architectureCheck` chưa đầy đủ

**Vị trí**: `build.gradle.kts:13-67`

`architectureCheck` kiểm tra:
- Phụ thuộc nền tảng của tầng mô hình
- Phụ thuộc ngang giữa các mô-đun tính năng
- Khai báo nhập trực tiếp DAO

Nhưng chưa kiểm tra:
- `core:common` có phụ thuộc `core:database` không (nguy cơ phụ thuộc vòng)
- `runtime` / `harness` có phụ thuộc `feature` không (phụ thuộc ngược)
- `:core:model` có vô tình làm lộ phụ thuộc Android qua `api` không

Gợi ý mở rộng quy tắc kiểm tra, hoặc đưa plugin `module-graph-assert` vào để kiểm tra phụ thuộc mô-đun nghiêm ngặt hơn.

---

### L6. Độ phủ kiểm thử chưa đủ

Theo `KNOWN_ISSUES.md` và quét mã:
- Các màn hình `feature/*` về cơ bản không có kiểm thử đơn vị
- `HostBridge` (đường dẫn trọng yếu về bảo mật) không có kiểm thử
- `SecretManager` chỉ có `SecretRedactorTest`, không có kiểm thử vòng mã hóa rồi giải mã
- `NativePtySession` không có kiểm thử tầng JVM (phần JNI cần kiểm thử trên thiết bị)

Gợi ý ưu tiên bổ sung kiểm thử cho các đường dẫn trọng yếu về bảo mật (HostBridge, WorkspaceFileAccess, SecretManager).

---

## Gợi ý tối ưu (không phải lỗi)

### O1. Đưa vào `AppCoroutineScope` dùng chung qua cơ chế tiêm phụ thuộc

Nhiều đối tượng đơn nhất trong dự án tự tạo `CoroutineScope(SupervisorJob() + Dispatchers.IO)`:
- `ProcessRegistryImpl.scope`
- `TerminalSessionManager.scope`
- `McpManager.scope`
- `HostBridge.bridgeScope`
- `HarnessLoop.loopScope`

Gợi ý cung cấp một `CoroutineScope` toàn cục qua Koin, quản lý vòng đời tập trung, tiện hủy cùng lúc khi ứng dụng thoát.

---

### O2. Nguy cơ rò rỉ luồng dữ liệu nóng `StateFlow`

`TerminalSessionManager.activeHandle` dùng `stateIn(scope, SharingStarted.Eagerly, null)`; chế độ `Eagerly` vẫn giữ hoạt động ngay cả khi không có bên thu thập, có thể khiến phép `combine` ở thượng nguồn chạy liên tục. Gợi ý đổi sang `SharingStarted.WhileSubscribed(5_000)`.

---

### O3. Hệ thống nhật ký có thể tối ưu

`AppLogger` hiện là tự triển khai, gợi ý:
- Tích hợp thư viện trưởng thành như `Timber` hoặc `Napier`
- Hỗ trợ xuất nhật ký theo cấp (bản phát hành tự động tắt nhật ký gỡ lỗi)
- Che giấu thông tin nhạy cảm nên xử lý tập trung tại lớp ghi nhật ký, thay vì từng điểm gọi tự làm `secretRedactor.redact()`

---

## Gợi ý thứ tự ưu tiên sửa

| Ưu tiên | Vấn đề | Ước lượng công việc |
| :--- | :--- | :--- |
| **Sửa ngay** | C1 Chuỗi di chuyển cơ sở dữ liệu đứt gãy | 2–4 giờ (cần xác nhận thay đổi lược đồ) |
| **Sửa ngay** | C2 Lỗ hổng duyệt đường dẫn HostBridge | 30 phút |
| **Sửa trong tuần** | H1 Bế tắc do runBlocking | 1 giờ |
| **Sửa trong tuần** | H2 Truy cập đồng thời ProcessRegistry | 30 phút |
| **Sửa trong tuần** | H3 Khối bắt ngoại lệ rỗng | 15 phút |
| **Sửa trong tuần** | H4 Tranh chấp khi đóng PTY | 1 giờ |
| **Sửa trong tuần** | H5 TOCTOU qua liên kết tượng trưng | 2 giờ |
| **Tối ưu trong đợt phát triển** | M1–M7 mức trung bình | Mỗi mục 30 phút–2 giờ |
| **Cải tiến dài hạn** | L1–L6 + O1–O3 | Liên tục |

---

## Giải thích phương pháp rà soát

Lần rà soát này dùng các phương pháp sau:
1. **Tài liệu trước**: Đọc trọn vẹn `AI_NAVIGATION.md`, `ARCHITECTURE.md`, `ARCHITECTURE_RULES.md`, `FILE_INDEX.md`, `KNOWN_ISSUES.md`
2. **Quét mẫu**: Dùng Grep quét các mẫu thường gặp như `!!`, khối bắt ngoại lệ rỗng, `GlobalScope`, `runBlocking`, `Thread.sleep`, `TODO/FIXME`
3. **Đọc kỹ tệp trọng yếu**: Đọc sâu `LinuxRuntimeImpl`, `ProcessRegistry`, `HostBridge`, `HarnessLoop`, `ProviderClient`, `ToolExecutor`, `WorkspaceFileAccess`, `NativePtySession`, `AppModule`, `DatabaseMigrations` và các tệp lõi khác
4. **Kiểm tra tuân thủ kiến trúc**: Đối chiếu từng mục với các nguyên tắc cốt lõi trong `ARCHITECTURE_RULES.md`

**Phạm vi chưa bao phủ**:
- Chưa chạy công cụ biên dịch/phân tích tĩnh (`./gradlew lint`, `detekt`)
- Chưa chạy kiểm thử đơn vị
- Chưa xác minh hành vi khi chạy trên thiết bị thật
- Chưa rà soát chi tiết toàn bộ mã giao diện dưới `feature/*` (chỉ lấy mẫu)
- Chưa rà soát toàn bộ triển khai trình cài đặt dưới mô-đun `tools/`

Gợi ý chạy thêm `./gradlew lint detekt` để bổ sung kết quả phân tích tĩnh.
