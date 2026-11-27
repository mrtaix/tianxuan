# 📚 CTHarness — Chỉ mục tệp quan trọng

> Giúp trợ lý lập trình AI định vị nhanh một tính năng/lớp. Chi tiết kiến trúc xem [`ARCHITECTURE.md`](ARCHITECTURE.md).

---

## 🤖 Agent Harness (điều phối cốt lõi)

| Mô-đun | Tệp quan trọng | Trách nhiệm |
| --- | --- | --- |
| `harness/HarnessLoop.kt` | Vòng lặp chính, đa phiên đồng thời | Vòng lặp Agent chính |
| `harness/HarnessProviderRunner.kt` | Chọn năng lực mô hình, gửi yêu cầu phản hồi theo luồng và thử lại, hoàn tất xử lý phản hồi của trợ lý | Lượt mô hình |
| `harness/HarnessToolRoundRunner.kt` | Kiểm tra tham số công cụ, giới hạn mỗi lượt, thực thi và tạm dừng phê duyệt | Lượt công cụ |
| `harness/HarnessWorkspaceRecommendations.kt` | Ranh giới đường dẫn không gian làm việc, quét đề xuất MCP và đưa lên giao diện tiền cảnh | Đề xuất MCP |
| `harness/ToolExecutor.kt` | Phân phối các công cụ tích hợp `read / write / edit / base / process / host / download / build_script / subagent` | Thực thi công cụ |
| `harness/ApprovalPolicyEngine.kt` | Chính sách phê duyệt lời gọi công cụ (ba cấp `normal` / `high` / `critical`) | Phê duyệt |
| `harness/ToolRoundDispatcher.kt` | Điều phối nhiều công cụ đồng thời trong một lượt (thao tác thay đổi dữ liệu loại trừ lẫn nhau / tối đa 4 thao tác chỉ đọc đồng thời) | Song song công cụ |
| `harness/SubagentOrchestrator.kt` | Điều phối luồng thực thi riêng của Agent con | Phái Agent con |
| `harness/mcp/*` | Giao thức MCP: `McpManager` / `McpHttpTransport` / `McpJsonRpc` | Giao tiếp MCP |
| `harness/browser/BrowserMcpBootstrap.kt` | Khởi động máy chủ MCP trình duyệt tích hợp và đăng ký vào bộ máy thực thi | MCP trình duyệt |
| `harness/mcp/server/*` | Máy chủ MCP trong tiến trình: `McpServerRuntime` / xác thực / điều phối công cụ và tài nguyên | MCP nội bộ |
| `harness/HarnessMessage.kt` | Kiểu liệt kê `HarnessTool` và `ToolResult` (gồm `imageAttachments`) | Mô hình tin nhắn |

## 🌐 Trình duyệt tích hợp

| Mô-đun | Tệp quan trọng | Trách nhiệm |
| --- | --- | --- |
| `core/browser/...` | `BrowserFamily / Risk / Capability / SelectionPolicy / Preferences / FileOps` | Mô hình thuần Kotlin và chính sách |
| `runtime/browser/BrowserRegistry.kt` | Giao diện trung tâm đăng ký trình duyệt | Quản lý nhiều họ trình duyệt |
| `runtime/browser/BrowserRegistryImpl.kt` | Triển khai WebView đơn trong ứng dụng | Đăng ký / khởi động / chọn họ trình duyệt |
| `runtime/browser/BrowserEngine.kt` | Giao diện thao tác bộ máy trình duyệt (24 hành động) | Trừu tượng hóa mọi hành động trình duyệt |
| `runtime/browser/AndroidInAppBrowserEngine.kt` | Triển khai bộ máy WebView trong ứng dụng | Hiện thực hóa toàn bộ hành động |
| `runtime/browser/engine/WebViewTabPool.kt` | Nhóm tái sử dụng nhiều thẻ | Luồng chính và StateFlow |
| `runtime/browser/snapshot/SnapshotBuilder.kt` | Mã lệnh quét DOM và chèn tham chiếu | Sinh PageSnapshot |
| `runtime/browser/screenshot/ScreenshotRecorder.kt` | `view.draw` kết xuất bằng phần mềm thành PNG | ToolImageRef |
| `runtime/browser/network/NetworkInterceptor.kt` | Chặn `shouldInterceptRequest` | CapturedRequest |
| `runtime/browser/storage/StorageController.kt` | Thao tác cookie và bộ lưu trữ cục bộ/theo phiên | Đánh giá mã qua WebView |
| `runtime/browser/secret/SecretRedactingInterceptor.kt` | Kết nối `SecretRedactor` hiện có | Che dữ liệu nhạy cảm trong sản phẩm đầu ra của công cụ |
| `runtime/browser/hook/HookRuleStore.kt` | Lưu quy tắc can thiệp (an toàn luồng) | Tạo/đọc/cập nhật/xóa quy tắc và sinh dữ liệu gửi đi |
| `runtime/browser/hook/HookInstaller.kt` | `TianxuanBridge` và chèn mã ngay khi tài liệu bắt đầu tải | Cài môi trường chạy phía trang |
| `runtime/browser/hook/HookEventPipeline.kt` | Sự kiện cầu nối → kênh sự kiện | Gộp lần kích hoạt can thiệp và dữ liệu mạng thu được |
| `runtime/browser/hook/NetworkBodyStore.kt` | Bộ nhớ đệm LRU cho nội dung yêu cầu/phản hồi | Truy xuất nội dung trong ngân sách byte |
| `runtime/browser/hook/hook_runtime.js` (tài nguyên đóng gói) | Chặn `fetch`/XHR/hàm/thuộc tính phía trang | Viết lại mạng và can thiệp hàm |
| `runtime/browser/cdp/CdpTransport.kt` | LocalSocket → ổ cắm DevTools | `webview_devtools_remote_<pid>` |
| `runtime/browser/cdp/CdpSession.kt` | Mã hóa/giải mã khung WebSocket và liên kết lệnh/phân phối sự kiện | Phiên CDP JSON-RPC |
| `runtime/browser/cdp/CdpManager.kt` | Vòng đời gắn kết và đếm tham chiếu ổ cắm | `setWebContentsDebuggingEnabled` |
| `runtime/browser/cdp/CdpTabConnection.kt` | Kết nối một thẻ (trình gỡ lỗi, Fetch và phiên con Worker) | Định tuyến điểm dừng/chặn |
| `runtime/browser/cdp/CdpDebugController.kt` | Điểm dừng thật / tạm dừng / chạy từng bước / phạm vi / đánh giá mã | Máy trạng thái gỡ lỗi JavaScript |
| `runtime/browser/cdp/CdpFetchInterceptor.kt` | Chặn `Fetch.requestPaused` ở cấp bộ máy trình duyệt | Viết lại mạng cho Worker/tài nguyên con |
| `runtime/browser/tools/BrowserMcpTools.kt` | Phân phối công cụ `mcp__browser__*` và cấp độ rủi ro | Hơn 50 công cụ (kiểm soát quyền với `hook_*`/`debug_*`) |
| `runtime/browser/tools/BrowserMcpResources.kt` | Tài nguyên `browser://*` | 6 tài nguyên |
| `feature/browser/BrowserScreen.kt` | Màn hình Compose chính của trình duyệt tích hợp | Điểm vào giao diện |
| `feature/browser/BrowserViewModel.kt` | Giữ sổ đăng ký, kênh sự kiện và trạng thái ảnh chụp | Trạng thái |
| `feature/browser/BrowserActionCard.kt` | Thẻ sản phẩm đầu ra tái sử dụng cho hội thoại (ảnh thu nhỏ) | Tái sử dụng giữa các mô-đun |
| `feature/browser/BrowserNavRoute.kt` | Hằng BrowserRoute và hàm hỗ trợ chuyển trang | Điểm vào tuyến điều hướng |
| `docs/BROWSER_DESIGN.md` | Tài liệu thiết kế trình duyệt tích hợp | Quyết định và bản ghi quyết định kiến trúc |

## 🖥️ Môi trường chạy Linux (PRoot)

| Mô-đun | Tệp quan trọng | Trách nhiệm |
| --- | --- | --- |
| `runtime/.../LinuxRuntime.kt` | Điểm khởi động PRoot / bảng lệnh `base` | Ranh giới thực thi lệnh |
| `runtime/.../ProcessRegistry.kt` | PID / bộ đệm nhật ký dạng vòng của lệnh `process` | Quản lý tiến trình nền |
| `runtime/.../ProotCommandBuilder.kt` | Chuẩn hóa điểm gắn kết `-b` và phòng chống chèn lệnh trình bao | Bảo mật |
| `runtime/.../WorkspaceFileService.kt` | Đọc/ghi/tìm/băm/nén ZIP/chia sẻ không gian làm việc | Khớp với nhóm công cụ `file.*` |
| `runtime/.../shell/VT100.kt` | Máy trạng thái VT100 của thiết bị đầu cuối | Kết xuất thiết bị đầu cuối |

## 📱 ADB không dây và Logcat tích hợp

| Mô-đun | Tệp quan trọng | Trách nhiệm |
| --- | --- | --- |
| `runtime/.../bridge/adb/EmbeddedAdbManager.kt` | Máy khách Kadb, khám phá mDNS và khóa bền vững | Tự phát hiện `_adb-tls-pairing` / `_adb-tls-connect`, ghép đôi một lần, tự kết nối lại, thu thập Logcat |
| `runtime/.../bridge/HostBridge.kt` | Cầu nối HTTP cho môi trường cô lập (127.0.0.1:7980) | Cung cấp `/api/logcat`, `/api/shell` (dự phòng bằng ADB không dây tích hợp) và cài APK im lặng |
| `app/src/main/assets/bin/logcat-grabber` | Công cụ dòng lệnh thu thập nhật ký tích hợp trong môi trường cô lập | Mã lệnh đóng gói `logcat-grabber` / `logcat-tail` / `logcat-export` |
| `feature/developer/.../AdbLogcatScreen.kt` | Bàn làm việc ADB không dây và nhật ký độc lập | Truy cập trực tiếp để giữ tiến trình và chẩn đoán hệ thống: nhập mã ghép đôi, dò mDNS, lọc và sao chép Logcat đa chiều |
| `feature/developer/.../DeveloperScreen.kt` | Bảng điều khiển nhà phát triển | Gồm giám sát sức khỏe tầng dưới, thẻ trạng thái ADB không dây, cập nhật nguồn công cụ… |
| `harness/ToolExecutor.kt` | Phân phối `host.logcat` | Ưu tiên ADB không dây, nếu thất bại thì chuyển sang Shizuku/Root |

## 🌿 Quản lý nhánh Git (feature:git)

| Mô-đun | Tệp quan trọng | Trách nhiệm |
| --- | --- | --- |
| `feature/git/.../GitManager.kt` | Bọc JGit (cùng nền tảng thư viện với MGit) | Liệt kê/chuyển/tạo/xóa nhánh, thuật toán làn cho cây bản ghi thay đổi, tiến độ đẩy/kéo, ánh xạ lỗi dễ hiểu |
| `feature/git/.../GitCredentialsStore.kt` | Lưu thông tin xác thực HTTPS theo máy chủ | Mã truy cập được SecretManager (AndroidKeyStore AES/GCM) mã hóa rồi ghi JSON |
| `feature/git/.../GitViewModel.kt` | Máy trạng thái GitScreen | Gắn dự án / loại trừ thao tác / cập nhật tiến độ |
| `feature/git/.../GitScreen.kt` | Trang quản lý nhánh (hai thẻ: nhánh và lịch sử bản ghi thay đổi) | Điểm vào: mục "Kho" trên thanh công cụ trên cùng của CTHarness |
| `feature/git/.../GitCommitGraph.kt` | Đồ thị bản ghi thay đổi dạng làn vẽ bằng Canvas | Đường chéo hợp nhất/tách nhánh và nút đa sắc |
| `feature/chat/.../ChatWorkbenchPanels.kt` | Điểm vào "Kho" trên thanh công cụ trên cùng | Mẫu hàm gọi lại `onOpenRepository` tùy chọn |
| `feature/navigation/.../TianXuanNavHost.kt` | `GitRepositoryDestination(projectName)` | Đăng ký tuyến điều hướng |

> JGit mở trực tiếp kho mã trong không gian làm việc ở phía máy chủ (`RepositoryBuilder` và cấu hình hệ thống/người dùng rỗng để tránh vấn đề đường dẫn Android), không phụ thuộc vào Git cài trong môi trường cô lập.

## 🤝 Tham chiếu Web Reverse MCP

Thiết kế trình duyệt/MCP tích hợp của dự án tham khảo `mnjh666/WebReverse-MCP` (cách chia mô-đun, tập động từ công cụ và ma trận rủi ro), không dùng lại mã nguồn của họ.
