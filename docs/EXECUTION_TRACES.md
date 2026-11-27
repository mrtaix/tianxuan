# ⚡ CTHarness — Các luồng gọi cốt lõi và trình tự thực thi (Execution Traces)

---

## 1. Khởi tạo Agent và vòng lặp thực thi công cụ (Harness Loop)

```text
ChatScreen (UI)
  └─► ChatViewModel.send(prompt)
        └─► HarnessLoop.send()
              ├─► ProviderRepository đọc BaseURL / Model / ApiKey
              ├─► ProviderClient / ChatApi ➔ nhận luồng reasoning & text / tool_calls
              └─► Khi mô hình trả về tool_calls:
                    ├─► HarnessApiMapper ánh xạ read / write / edit / base / process
                    ├─► ApprovalPolicyEngine tính yêu cầu phê duyệt
                    ├─► ToolExecutor phân phối truy cập tệp hoặc LinuxRuntime
                    ├─► trả ToolResult về lịch sử hội thoại (core:database)
                    └─► tiếp tục vòng suy luận tiếp theo cho đến khi hoàn thành mọi nhiệm vụ
```

---

## 2. Mount bộ lưu trữ giữa máy chủ Android và sandbox (Storage Mount)

```text
StorageMountSettingsScreen (UI)
  └─► SettingsDataStore (lưu mountDownloadEnabled / customMountBindings)
        └─► ProotCommandBuilder.build()
              └─► thêm động "-b /storage/emulated/0/Download:/sdcard/Download"
                    └─► Tiến trình PRoot khởi chạy, trong sandbox có thể truy cập trực tiếp /sdcard
```

---

## 3. Tương tác terminal và PTY bản địa (Matrix Terminal)

```text
TerminalScreen (UI)
  └─► TerminalViewModel
        └─► TerminalPtyManager.createPty()
              └─► JNI pty.c (openpty / fork / execve proot)
                    ├─► PtyInputStream ➔ TerminalViewModel.screen (phân tích bằng máy trạng thái VT100)
                    └─► PtyOutputStream ◄─ TerminalScreen nhập từ bàn phím và các phím hỗ trợ (ExtraKeys)
```

---

## 4. Chia nhỏ nhiệm vụ và thẻ tiến độ (Task Plan Checkpoints)

```text
Model Output (chứa văn bản dạng - [ ] / - [x])
  └─► ChatScreen.kt -> extractTaskPlanSteps(message.text)
        └─► Khi số bước >= 2 ➔ hiển thị TaskPlanCard
              ├─► tính động phần trăm tiến độ và huy hiệu hoàn thành (LinearProgressIndicator)
              ├─► cung cấp phản hồi xúc giác và hiệu ứng mở rộng/thu gọn mượt mà
```

---

## 5. Bố cục hai cột cho màn hình rộng và thiết bị gập (Dual-Pane Layout)

```text
ChatScreen.kt (BoxWithConstraints)
  ├─► maxWidth >= 720.dp:
  │     ├─► Cột trái (48% chiều rộng): ChatPaneContent (hội thoại agent và ô nhập)
  │     ├─► Ở giữa: VerticalDivider
  │     └─► Cột phải (52% chiều rộng): TerminalScreen(project = workspace) (terminal Linux thời gian thực)
  └─► maxWidth < 720.dp:
        └─► Chế độ xem một cột trên điện thoại
```

---

## 6. Vòng đời lệnh foreground của Agent và tiến trình được quản lý (Command Lifecycle)

```text
AgentSettingsScreen
  └─► SettingsDataStore.baseCommandTimeoutSeconds(1–60 phút, mặc định 10 phút)
        └─► ToolExecutor.executeBase()
              ├─► Không truyền timeout_seconds: đọc giá trị mặc định của người dùng
              └─► Ghi đè cho một lần: kiểm tra 1–3600 giây ➔ LinuxRuntime.execute(ShellCommand)

Harness process(action=start, id, command)
  └─► ToolExecutor.executeProcess()
        └─► LinuxRuntime.startBackground(type=COMMAND)
              └─► ProcessRegistry
                    ├─► Lưu agent-process:<id> / PID / LinuxSession
                    ├─► Lưu cache và cung cấp log
                    └─► status / logs / list / stop
```

> **Lưu ý đặc biệt về vòng đời tiến trình**：
> Tham số khởi động PRoot chứa `--kill-on-exit`. Việc dùng `nohup`, `setsid`, `&` hoặc tự daemonize trong `base` thông thường không đảm bảo tiến trình sống qua các phiên PRoot; phải dùng `process` để quản lý và giữ lệnh được quản lý chạy ở foreground. Android force-stop, hệ thống thu hồi tiến trình ứng dụng hoặc khởi động lại thiết bị nằm ngoài phạm vi đảm bảo của cơ chế quản lý tiến trình.

---

## 7. Vòng đời workspace: nhập, xuất và build (Workspace Lifecycle)

```text
WorkspaceScreen
  └─► WorkspaceViewModel
        ├─► WorkspaceManager.createProject()
        ├─► importProjectArchive() ➔ SAF URI ➔ giải nén an toàn vào /workspace
        ├─► importGithubProject() ➔ LinuxRuntime.execute(git clone)
        ├─► exportProject() ➔ ZIP ➔ thư mục đích SAF
        └─► WorkspaceBuildTaskCoordinator.start(project)
              └─► WorkspaceBuildRunner.runProject()
                    ├─► BuildEnvironmentPreflight
                    ├─► Android / Flutter: build tối đa 30 phút
                    ├─► Liên tục cập nhật StateFlow, thông báo và log build
                    └─► ApkArtifactVerifier ➔ điểm vào cài APK
```

---

## 8. Cài đặt và build bộ công cụ Android ngoại tuyến ARM64 (Sandbox Android Toolchain)

```text
WorkspaceScreen / ToolCenterScreen
  └─► ToolManager.batchInstallComponents() / GenericRecipeInstaller
        ├─► LocalPluginPayloadManager sao chép payload theo luồng và báo tiến độ [COPY] theo số byte
        └─► android-suite-offline manifest + install-android-suite.sh
              ├─► [TIANXUAN_PROGRESS:n] giao thức ➔ InstallEvent.Progress
              ├─► Cài đặt bất biến JDK / SDK / AAPT2 / NDK / Gradle / CMake / Ninja / Flutter
              ├─► /root/.gradle/init.d/tianxuan-android-ndk.gradle điểm duy nhất tiêm android.ndkPath
              └─► gradle.properties cố định chính sách tài nguyên cho thiết bị di động
                    ├─► daemon=false / parallel=false / workers.max=2
                    └─► Gradle Xmx=1024m / Metaspace=384m / SerialGC

WorkspaceBuildRunner
  └─► build_android.sh / build_flutter.sh
        ├─► Xóa ndk.dir còn sót trong local.properties, không ghi lại nữa
                    └─► --no-daemon --max-workers=2 ➔ build và kiểm tra sản phẩm
```

---

## 9. Tự phát hiện ADB không dây và thu log (Wireless ADB / Logcat)

```text
DeveloperScreen
  └─► EmbeddedAdbManager
        ├─► NsdManager/mDNS phát hiện _adb-tls-pairing._tcp và _adb-tls-connect._tcp
        ├─► Lần đầu nhập mã ghép đôi 6 chữ số ➔ ghép đôi TLS + SPAKE2+ ➔ ghi khóa riêng vào thư mục riêng của ứng dụng
        ├─► Các lần khởi động sau phát hiện cổng kết nối ➔ tự dùng lại khóa bền vững để kết nối lại
        └─► UID shell thực thi logcat (lọc PID theo tên gói / Tag / mức ưu tiên / từ khóa)

Harness host(action=logcat)
  ├─► Ưu tiên EmbeddedAdbManager (không cần Shizuku/Root)
  └─► Chuyển sang PrivilegeManager khi ADB không dây không khả dụng

sandbox PRoot /opt/tianxuan/bin/logcat-grabber & tianxuan-host logcat
  └─► HostBridge (127.0.0.1:7980)
        ├─► POST /api/logcat ➔ EmbeddedAdbManager.captureLogcat()
        ├─► POST /api/shell ➔ phương án dự phòng ADB không dây tích hợp (vẫn thực thi Shell khi không có Shizuku)
        └─► POST /api/install-apk ➔ cài đặt im lặng qua ADB không dây tích hợp (không có hộp thoại hệ thống khi đã kết nối)
```
