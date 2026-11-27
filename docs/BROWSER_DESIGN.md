# 🌐 CTHarness — Phương án tích hợp trình duyệt và Harness

> **Phạm vi**: Tích hợp một `in-app WebView` vào CTHarness trên Android không cần Root, cho phép harness điều khiển qua MCP tương tự cách Codex điều khiển trình duyệt trong ứng dụng; đồng thời cho phép Claude / Cursor / Copilot trên máy tính kết nối qua `adb reverse`.
> **Trạng thái**: Các phần triển khai v0.9.0-MVP (giai đoạn đầu triển khai nhóm in-app + nhóm hệ thống tệp).
> **Tác giả**: Nhóm kiến trúc CTHarness
> **Ngày tạo**: 2026-09-02

---

## 1. 🎯 Mục tiêu và đối chiếu

### 1.1 Mục tiêu

Cho phép Agent Harness của CTHarness hoạt động như trình duyệt tích hợp trong Codex trên máy tính:

1. **Tích hợp và hiển thị được**: Người dùng mở được một trang trình duyệt thực sự trong ứng dụng (nhiều tab, thanh URL, thanh trạng thái cùng duyệt web).
2. **AI điều khiển được**: LLM trong harness điều phối cùng một phiên bản trình duyệt thông qua công cụ MCP (`mcp__browser__open` / `navigate` / `snapshot` / `click` / `type` / `screenshot` …).
3. **Cùng duyệt web (Co-browsing)**: Thao tác UI và AI luân phiên trên cùng một `WebView`, hiển thị rõ ai vừa thao tác và ai đang nắm quyền điều khiển.
4. **Kết nối ngoài tùy chọn**: Cùng một cổng (`127.0.0.1:8787` hoặc `0.0.0.0:8787`) cho phép AI / CLI trên máy tính kết nối qua MCP JSON-RPC.
5. **Bảo mật xuyên suốt**: Tái sử dụng `core:security/SecretRedactor`, mặc định che Cookie / Authorization / Password nhạy cảm; yêu cầu phê duyệt theo mức rủi ro (LOW / MEDIUM / HIGH / CRITICAL).

### 1.2 Đối chiếu với Codex

| Codex trên máy tính | CTHarness (phương án này) |
| --- | --- |
| `agent.browsers.list()` / `get(name)` / `getForUrl()` / `getDefault()` | `BrowserRegistry.listFamilies()` / `get(family)` / `getForUrl()` / `getDefault()` |
| Trình duyệt trong ứng dụng (XAML / WKWebView) | `WebView` trong ứng dụng (`androidx.webkit`) |
| Điều khiển Chrome trên máy tính qua Chrome Extension + CDP | Điều khiển WebView trong chính ứng dụng qua **MCP server chạy trong tiến trình**; mở Chrome bên ngoài bằng Intent (v1.1+) |
| Kết quả công cụ chứa ảnh chụp base64 | `ToolResult.imageAttachments: List<ToolImageRef>`, UI dùng Coil hiển thị tệp cục bộ |
| Duy trì liên kết trình duyệt qua các lượt | Singleton `BrowserRegistry` + `BrowserSessionToken` (theo phiên agent) |
| Chính sách chọn: người dùng chỉ định rõ > suy ra từ URL > mặc định | `BrowserSelectionPolicy.decide(request, prefs, urlHint)` |

### 1.3 Quan hệ với WebReverse-MCP

Phương án này **tham khảo** cách chia module / tập động từ công cụ / ma trận rủi ro / thiết kế Evidence Store; **không tái sử dụng mã nguồn** (cấu hình `pluginManagement` và `gradle 7.x` của họ không phù hợp với CTHarness). Nếu sau này có nhu cầu rõ rệt, có thể tham khảo từng module qua PR riêng.

---

## 2. 🏗️ Sơ đồ module

```
feature/browser               ── Compose UI (hệ thống thiết kế RuntimeCard/RuntimeTopBar)
       │
       ▼
runtime/browser               ── BrowserRegistry + pool WebView + InApp Engine + bộ xử lý công cụ MCP
       │                          │
       │                          ├─► McpInProcessServer (HTTP loopback)
       │                          │
       └─► Tái sử dụng runtime/WorkspaceManager & WorkspaceFileService (mọi công cụ file.* đều đi qua đây)

harness/mcp/server            ── Ktor khởi chạy dịch vụ: POST /mcp/sse, Auth, phân phối công cụ, Resources
       │
       ▼
harness (McpManager hiện có)  ── bootstrapBuiltinBrowser() chèn cấu hình lúc khởi động + giữ dịch vụ hoạt động
       │
       ▼
core/browser (Pure Kotlin)    ── BrowserFamily / Risk / Capability / SnapshotRef / PageSnapshot
core/datastore                ── Facade BrowserPreferences (homeUrl / allowRemote / allowEvalJs / coBrowsing)
core/model                    ── ToolResult.imageAttachments (đã thêm), McpServerConfig.isBuiltin (đã có)
```

### 2.1 Phụ thuộc một chiều

Giữ thống nhất với toàn dự án: `feature/* → runtime/* → core/*`; **nghiêm cấm phụ thuộc ngược vào các module mới**.

---

## 3. 🔌 Giao thức và dạng dữ liệu

### 3.1 Đặt tên công cụ (thống nhất theo kiểu MCP)

Cách các công cụ được trình bày cho mô hình:

```
mcp__browser__open                ── Tạo mới hoặc tái sử dụng tab để mở URL
mcp__browser__navigate            ── Điều hướng tab hiện tại
mcp__browser__snapshot            ── Trích ref của phần tử tương tác
mcp__browser__click               ── Nhấp theo ref
mcp__browser__type                ── Nhập văn bản theo ref
mcp__browser__press               ── Nhấn phím theo ref (Enter/Tab/Escape…)
mcp__browser__screenshot          ── Lưu ảnh chụp toàn màn hình tab hiện tại và trả image_ref
mcp__browser__current_url         ── Lấy URL / tiêu đề
mcp__browser__list_tabs           ── Liệt kê mọi tab
mcp__browser__close_tab           ── Đóng tab chỉ định
mcp__browser__evaluate            ── Thực thi một đoạn JS trong trang hiện tại (critical)
mcp__browser__page_source         ── Trả HTML của trang được định dạng dễ đọc
mcp__browser__network_list        ── Liệt kê yêu cầu mạng đã thu thập
mcp__browser__cookies_get         ── Lấy Cookie (rủi ro cao)
mcp__browser__local_get / list / delete / clear
mcp__browser__session_*           ── Tương tự local
mcp__browser__file_*              ── Đọc/ghi/tìm/hash/zip/share trong thư mục làm việc (tái sử dụng WorkspaceFileService)
```

> Phân giải tên dùng cơ chế tương thích `harness/mcp/McpToolApiName.encode` / `legacy` hiện có. Tên mô hình thực sự thấy là `mcp__browser__open__<hash>`, nhưng **không ảnh hưởng** việc harness khôi phục `McpToolInfo`.

### 3.2 Giao thức Snapshot Ref (tránh lộ selector nội bộ)

```json
{
  "url": "https://example.com/login",
  "title": "Sign in",
  "refMap": {
    "e12":  { "selector": "form#login > input[name=email]",  "tag": "input", "type": "email",   "role": "input" },
    "e15":  { "selector": "form#login > input[name=pwd]",    "tag": "input", "type": "password","role": "input" },
    "e18":  { "selector": "button[type=submit]",             "tag": "button","text": "Continue","role": "button" }
  }
}
```

- Mô hình chỉ nhận được `e12 / e15 / e18`, **không nhận được** selector thật.
- `runtime/browser/snapshot/RefResolver` duy trì ánh xạ `tabId → refMap` bên trong; sau khi người dùng nhấp phần tử (sự kiện người dùng), cũng ghi vào refMap để tránh AI và UI làm mất hiệu lực tham chiếu của nhau.

### 3.3 Kết quả trả về của Screenshot

`ToolResult.imageAttachments[0] = ToolImageRef(uri = "screenshots://tianxuan-browser/t1/2026-09-02T11-09-09.png", mime = "image/png", w = 1080, h = 2400)`.

UI dùng `Coil` hiển thị ảnh thu nhỏ; nhấp để mở bản xem trước toàn màn hình trong `BrowserActionCard`. Tệp được lưu vào `/data/data/<app>/cache/tianxuan-browser/screenshots/...`; **không gửi ra ngoài**, tuân thủ nguyên tắc "dữ liệu nhạy cảm không rời thiết bị".

### 3.4 MCP Resources (AI / IDE bên ngoài có thể đăng ký theo dõi)

```
browser://current-page      ── URL + tiêu đề tab hiện tại
browser://dom               ── Toàn bộ snapshot gần nhất
browser://console           ── Log console gần nhất
browser://network           ── Yêu cầu mạng đã thu thập
browser://tabs              ── Tóm tắt mọi tab
browser://storage           ── Tóm tắt Cookie / Local / Session
```

---

## 4. ⚖️ Ma trận rủi ro và phê duyệt

| Tiền tố công cụ | riskLevel | Phê duyệt chế độ REQUEST | Phê duyệt chế độ ASSISTED | Tự động trong FULL_ACCESS |
| --- | --- | --- | --- | --- |
| `*snapshot` / `*screenshot` / `*current_url` / `*list_tabs` / `*page_source` / `console.*` / `network.list` / `page.*_info` / `*_get` (chỉ đọc) | LOW | Không | Không | Không |
| `*open` / `*navigate` / `*back` / `*forward` / `*refresh` / `*close_tab` | MEDIUM | **Có** | Có | Không |
| `*click` / `*type` / `*press` / `*scroll` / `*network_set_cache_disabled` / `*local_set` / `*local_delete` / `*cookies_set` | HIGH | **Có** | Có | Không |
| `*evaluate` (tiêm JS vào trang) / `*cookies_get` / `*local_get_specific` (trường nhạy cảm) | CRITICAL | **Có** + xác nhận cấp hai | Có | **Có** (vẫn khuyến nghị xác nhận lần hai) |
| `*file.delete` / `*file.unzip_to_system` | HIGH/CRITICAL | **Có** | Có | Không |

Điểm triển khai: dùng `McpManager.executeTool` + `AgentApprovalRequestEntity` hiện có; bổ sung bảng cấp quyền theo công cụ vào `harness/mcp/server/McpAuth` (kết hợp với quy tắc "chỉ trả về công cụ người dùng đã cấp quyền").

---

## 5. ⚙️ Trình tự lời gọi cốt lõi

### 5.1 Agent chủ động gọi

```
ChatScreen
  └─► ChatViewModel.send(prompt)
        └─► HarnessLoop.send()
              ├─► ProviderClient gọi mô hình (danh sách công cụ chứa mcp__browser__*)
              └─► Mô hình trả tool_call { name = "mcp__browser__<server>__<hash>", args = {action, ...} }
                    └─► McpManager.executeTool(fullToolName, args)
                          └─► McpHttpTransport.execute(builtinServer, tool, args)
                                └─► POST http://127.0.0.1:8787/mcp   (trong tiến trình)
                                      └─► McpInProcessServer → McpToolDispatcher
                                            └─► BrowserMcpTools.dispatch(tool, args)
                                                  └─► BrowserRegistry.getDefault().<engine>.<action>(args)
                                                        └─► AndroidInAppBrowser / NetworkInterceptor / …
                          └─► Trả về (ok, output + image_ref)
                    └─► Trở lại HarnessLoop: vòng công cụ tiếp theo hoặc tạo AssistantText
```

### 5.2 Người dùng thao tác thủ công (Co-browsing)

```
BrowserScreen
  └─► BrowserViewModel.onUrlBarCommit(text)
        └─► AndroidInAppBrowser.loadUrl(text)
              └─► WebViewClient.onPageFinished
                    ├─► Chạy script trích ref
                    ├─► SnapshotBuilder → đẩy tới BrowserEventBus
                    └─► Phía harness lấy refMap cho lần gọi công cụ tiếp theo qua đăng ký resource (nếu có)

Người dùng nhấp
  └─► AndroidInAppBrowser.onUserInteraction
        └─► Tạm dừng quyền điều khiển của harness (khi coBrowsingDisabled=true, ref vẫn tích lũy nhưng không ghi tiếp)
```

### 5.3 Kết nối AI trên máy tính

```
Claude/Cursor trên máy tính
  └─► mcpServers.webreverse.url = "http://<Android-IP>:8787/mcp"
       (hoặc dùng http://127.0.0.1:8787/mcp sau adb reverse tcp:8787 tcp:8787)
        └─► McpHttpTransport (cùng JSON-RPC 2.0 / Streamable HTTP)
              └─► Bearer [REDACTED] do AgentPreferences cung cấp
                    └─► McpInProcessServer → BrowserTools.dispatch
```

---

## 6. 🔐 Chính sách bảo mật

| Lớp | Chính sách |
| --- | --- |
| SecretRedactor | Tự động thay mọi cookie / Authorization / password / set-cookie / token bằng `[REDACTED_*]` (tái sử dụng triển khai sẵn có trong `core:security`) |
| Mức rủi ro | Bốn mức LOW/MEDIUM/HIGH/CRITICAL; HIGH trở lên kích hoạt ApprovalRequestDialog |
| Công tắc kết nối ngoài | `browserPreferences.allowRemoteConnect` mặc định `false`; khi bật, chỉ bind cổng vào `0.0.0.0`, đồng thời yêu cầu người dùng đã đặt `browserRemoteToken` (base64 ≥32 byte) |
| Danh sách cho phép miền nhạy cảm | Danh sách miền chính (`localhost`, `127.0.0.1`, miền được người dùng cho phép) được dùng `cleartext`; các miền khác bắt buộc HTTPS (Koin Qualifier + NetworkSecurityConfig) |
| Sandbox tệp | Công cụ `*file.*` tái sử dụng kiểm tra đường dẫn của `WorkspaceFileService`, cấm `/proc /sys /dev /system /root` |
| Token | Server tích hợp `mcp__browser__*` mặc định đi qua loopback nên không cần token; kết nối ngoài bắt buộc token; token được tạo bằng SecureRandom, mã hóa và lưu bằng SecretRedactor |

---

## 7. 📁 Chỉ mục tệp (danh sách cần tạo trên đĩa)

### Module và thư mục mới

```
core/browser/src/main/java/top/wkbin/tianxuan/core/browser/
    BrowserFamily.kt
    BrowserRisk.kt
    BrowserCapability.kt
    BrowserDescriptor.kt
    SnapshotRef.kt
    PageSnapshot.kt
    BrowserSelectionPolicy.kt          (Pure Kotlin, có unit test)
    BrowserPreferences.kt              (data class cấu hình Pure Kotlin)

core/browser/src/test/java/top/wkbin/tianxuan/core/browser/
    BrowserSelectionPolicyTest.kt
    SnapshotRefTest.kt

runtime/browser/src/main/java/top/wkbin/tianxuan/runtime/browser/
    BrowserRegistry.kt                (interface)
    BrowserRegistryImpl.kt            (@Singleton)
    BrowserSessionToken.kt            (liên kết theo phiên agent)
    BrowserEventBus.kt                (sự kiện snapshot/url/title/console/network)
    engine/BrowserEngine.kt           (interface)
    engine/AndroidInAppBrowserEngine.kt
    engine/AndroidWebViewFactory.kt
    engine/WebViewTabPool.kt
    snapshot/SnapshotBuilder.kt       (evaluateJavascript + AccessibilityNode rút gọn)
    snapshot/RefResolver.kt           (bảng ref → selector)
    screenshot/ScreenshotRecorder.kt  (chụp bất đồng bộ PixelCopy → internalCache)
    network/NetworkInterceptor.kt     (lớp cache shouldInterceptRequest)
    storage/StorageController.kt      (CookieManager + WebStorage)
    js/JsEvaluator.kt                 (bọc evaluateJavascript + timeout)
    secret/SecretRedactingInterceptor.kt (che header / body trang trước khi lưu cơ sở dữ liệu)
    tools/BrowserMcpTools.kt          (đăng ký mọi bộ xử lý công cụ mcp__browser__*)
    tools/BrowserMcpResources.kt      (bộ xử lý resource browser://*)
    capabilities/BrowserCapabilities.kt (engine.family → tập năng lực)
    di/BrowserModule.kt               (liên kết Koin)

runtime/browser/src/main/AndroidManifest.xml

feature/browser/src/main/java/top/wkbin/tianxuan/ui/browser/
    BrowserScreen.kt
    BrowserViewModel.kt
    BrowserTopBar.kt                  (thanh URL / tiến / lùi / làm mới / bật tắt cùng duyệt web)
    BrowserTabBar.kt                  (chuyển tab với cuộn ngang)
    BrowserCoBrowsingPill.kt          ("AI đang điều khiển" / "Bạn đang điều khiển")
    BrowserActionCard.kt              (thẻ ảnh thu nhỏ để ChatToolCards tái sử dụng)
    snapshot/SnapshotSheet.kt         (xem trước snapshot toàn màn hình)
    BrowserNavRoute.kt

harness/src/main/java/top/wkbin/tianxuan/harness/mcp/server/
    McpInProcessServer.kt             (Ktor khởi chạy dịch vụ, POST /mcp/sse + Bearer [REDACTED])
    McpAuthFilter.kt
    McpToolDispatcher.kt              (mcp__<server>__<tool> → định tuyến tới BrowserMcpTools)
    McpResourceDispatcher.kt
    McpServerRuntime.kt               (singleton, khởi động/dừng)
    di/McpServerModule.kt

harness/src/main/java/top/wkbin/tianxuan/harness/browser/
    BrowserMcpBootstrap.kt            (McpManager.bootstrapBuiltinBrowser())
```

### Tệp cần sửa

```
settings.gradle.kts                                       ── Thêm :core:browser / :runtime:browser / :feature:browser
gradle/libs.versions.toml                                 ── Thêm ktor-server-cio / ktor-server-sse / androidx-webkit
core/model/.../HarnessMessage.kt                          ── Thêm imageAttachments + ToolImageRef vào ToolResult
core/model/.../McpModels.kt                               ── Thêm builtinRisk / bootstrapOrder vào McpServerConfig (nếu chưa thống nhất với isBuiltin hiện có)
core/datastore/.../PreferenceFacades.kt                   ── Thêm facade BrowserPreferences
harness/.../McpManager.kt                                 ── Gọi McpServerRuntime.bootstrap() trong giai đoạn init { ... }
app/build.gradle.kts                                      ── implementation(project(":runtime:browser"))
runtime/build.gradle.kts                                  ── implementation(project(":core:browser"))
feature/navigation/.../TianXuanNavHost.kt                    ── Đăng ký BrowserRoute
feature/home/.../HomeScreen.kt                            ── Điểm vào RuntimeCard
feature/chat/.../ChatToolCards.kt                         ── Tái sử dụng BrowserActionCard
docs/AI_NAVIGATION.md, docs/ARCHITECTURE.md, docs/FILE_INDEX.md ── Cập nhật đồng bộ module mới
```

---

## 8. 🧪 Chiến lược kiểm thử

| Lớp | Mục tiêu bao phủ | Công cụ |
| --- | --- | --- |
| **Lớp model thuần túy (bắt buộc đạt)** | Tuần tự hóa `BrowserSelectionPolicy` / `SnapshotRef` / `ToolImageRef` | JUnit4, JVM thuần |
| **Phân phối MCP (bắt buộc đạt)** | Định tuyến `McpToolDispatcher`, `McpAuthFilter` từ chối khi không có token | JUnit4 + Mockk |
| **WebView (Robolectric)** | Tái sử dụng `WebViewTabPool` / phân giải `RefResolver` | Robolectric 4.16 |
| **UI (ảnh chụp Compose)** | Bố cục màn hình đầu tiên của `BrowserScreen` | Kiểm thử Compose UI |
| **Đầu cuối (thủ công)** | Luồng chính: Agent → MCP server → WebView → screenshot → đưa kết quả trở lại | adb + ca kiểm thử thủ công |

Các tệp unit test quan trọng đã được liệt kê ở §7, cần commit cùng mã nguồn.

---

## 9. 🚀 Các phần triển khai MVP (2 tuần)

| Bước | Mục tiêu | PR |
| --- | --- | --- |
| 1 | Lớp model `:core:browser` + unit test | #1 |
| 2 | Facade BrowserPreferences trong `:core:datastore` | #2 |
| 3 | Khung `:runtime/browser` (BrowserRegistry, InApp Engine, bản rút gọn SnapShot/Screenshot) | #3 |
| 4 | Khởi chạy dịch vụ Ktor trong `harness/mcp/server` + Bootstrap | #4 |
| 5 | Màn hình chính Compose `:feature/browser` (thanh URL / một tab / nhãn cùng duyệt web) | #5 |
| 6 | Điểm vào NavHost + HomeScreen + tích hợp ChatToolCards | #6 |
| 7 | Cập nhật tài liệu + architectureCheck | #7 |

Sau mỗi PR, chạy `architectureCheck` và `:app:preBuild` hiện có.

---

## 9.5 🎣 Engine Hook tiêm vào trang và gỡ lỗi CDP (giai đoạn 1 + 2, đã triển khai)

> Năng lực dịch ngược trang web mở rộng trên nền MVP từ 2026-09, đối chiếu với tính năng hook/điểm dừng của WebReverse-MCP.

### 9.5.1 Engine Hook tiêm vào trang (giai đoạn 1, kiểm soát bằng allowHooks)

- **Kiến trúc**: Gói `hook/` + tài nguyên `hook_runtime.js`. Quy tắc lưu trong `HookRuleStore` (an toàn luồng), được `HookInstaller` tiêm vào trang ở document-start (`WebViewCompat.addDocumentStartJavaScript`, WebView 105+; bản cũ chuyển sang tiêm bổ sung ở onPageStarted); phía trang ghi đè fetch/XHR/hàm/thuộc tính, trả sự kiện qua `TianxuanHookBridge` (addJavascriptInterface) tới `HookEventPipeline` → `BrowserEventBus`.
- **Tập thao tác**: Quy tắc mạng hỗ trợ `log / block / redirect / mock / modify_headers` + `captureBody`; body lưu trong `NetworkBodyStore` (LRU, giới hạn tổng số byte).
- **Công cụ MCP**: `browser.hook_create / hook_list / hook_remove / hook_reset / hook_hits / inject_script / network_detail`.
- **Chuỗi dự phòng**: document-start không khả dụng → tiêm ở onPageStarted → kiểm tra lũy đẳng và tiêm bổ sung ở onPageFinished.

### 9.5.2 Điểm dừng CDP và chặn ở cấp Worker (giai đoạn 2, kiểm soát bằng allowCdp)

- **Truyền tải**: `CdpTransport` (LocalSocket kết nối `webview_devtools_remote_<pid>`) → tự triển khai bắt tay WS và mã hóa/giải mã khung (`WsFrameCodec`) → `CdpSession` (ghép lệnh với phản hồi/phân phối sự kiện/dọn dẹp khi ngắt kết nối).
- **Vòng đời**: `BrowserMcpBootstrap` do Application khởi chạy chờ luồng chính áp dụng công tắc gỡ lỗi WebView cấp tiến trình theo `allowCdp` trước khi đăng ký engine và tạo tab đầu tiên; `CdpManager` có thể thử bật lại mỗi lần attach, không còn dùng đếm tham chiếu hay latch luồng chính. detach/shutdown chỉ giải phóng phiên, công tắc giữ nguyên đến khi tiến trình kết thúc; tắt tùy chọn cần khởi động lại. `CdpTargetMatcher` dùng dấu `window.__tianxuanTabId` để ghép tab MCP với target CDP; `CdpTabConnection` giữ kết nối một tab + phiên Worker con (`Target.setAutoAttach` flatten).
- **Kết nối và chẩn đoán**: LocalSocket phải connect trước rồi mới đặt timeout đọc/ghi; đặt timeout trước khi kết nối sẽ ném `socket not created` vì fd phía client chưa được tạo, không được từ đó suy ra phía WebView chưa lắng nghe. Lỗi kết nối giữ lại giai đoạn, loại ngoại lệ và nguyên nhân, đồng thời ghi pid, WebView provider, trạng thái công tắc và nguyên nhân đọc `/proc/net/unix` thất bại; ứng viên pid tiến trình hiện tại không phụ thuộc khả năng đọc proc. WS dùng đường dẫn đầy đủ `/devtools/page/...`; việc dò marker chỉ gửi lệnh đánh giá sau khi khởi chạy phiên nhận dữ liệu.
- **Điểm dừng**: `CdpDebugController` — đặt/xóa/liệt kê điểm dừng, trạng thái tạm dừng, chạy từng bước (over/into/out), đánh giá trong khung đang dừng (`debug_eval`, có thể đọc/ghi biến cục bộ), đọc scope. Điểm dừng tự động được áp dụng lại khi attach sau detach.
- **Chặn cấp Worker**: `CdpFetchInterceptor` dùng `Fetch.requestPaused` để sửa lưu lượng mạng ở cấp engine, chia sẻ quyết định `HookRuleStore` (`CdpFetchDecision`) với quy tắc tiêm vào trang, bao phủ Worker/Service Worker và tài nguyên con nằm ngoài vùng tiêm.
- **Công cụ MCP**: `browser.debug_attach / detach / set_breakpoint / remove_breakpoint / list_breakpoints / resume / step / state / eval / scope / status`, tổng cộng 11 công cụ.

### 9.5.3 Cổng kiểm soát và bảo mật

- `allowHooks` / `allowCdp` là **các tùy chọn DataStore độc lập** (mặc định false); trang cài đặt "Plugin MCP và hệ sinh thái giao thức → Cổng bảo mật MCP trình duyệt" có bốn công tắc (kết nối từ xa/thực thi JS/Hook/CDP); công tắc áp dụng ở cấp pool, thay đổi cần khởi động lại ứng dụng.
- Trang bị tạm dừng trong thời gian attach sẽ làm đóng băng công cụ trong trang (từ chối evaluate/snapshot/click), tự động resume trước detach để dự phòng.
- **Khả năng chịu lỗi đã có**: `tokenOf` chuẩn hóa và ghép hậu tố cho tab id khi mô hình bỏ tiền tố `t:`; `sanitizeUrl` bỏ cặp dấu backtick mà mô hình đôi khi thêm vào URL.

---

## 10. 📜 Bản ghi quyết định (tóm tắt ADR)

- **ADR-001**: Cung cấp công cụ qua MCP, **không** qua công cụ gốc (`HarnessTool.BROWSER`). Lý do: không xung đột với pipeline `McpManager` hiện có của harness; tập công cụ có thể mở rộng mượt lên hơn 200 trong tương lai.
- **ADR-002**: MCP server chạy trong chính tiến trình Android của CTHarness (loopback), dùng Ktor khởi chạy dịch vụ. Lý do: không cần fork WebReverse-MCP; cùng giao thức và dạng truyền tải với `McpHttpTransport` hiện có; kết nối ngoài không cần phát triển thêm.
- **ADR-003**: Server tích hợp mặc định `isBuiltin=true` và `isEnabled=true`, nhưng cấu hình ghi vào `McpServerRepository`. Lý do: phù hợp cách người dùng hiểu "bật/tắt" MCP server; khi tắt không ảnh hưởng MCP server khác.
- **ADR-004**: ref không tiết lộ selector thật; thao tác người dùng và AI dùng chung một `refMap`, ref đặt lại về không khi tạo lại tab. Lý do: tránh đưa đường dẫn nội bộ vào prompt / giảm ảo giác, đồng thời hỗ trợ cùng duyệt web.
- **ADR-005**: MVP đầu tiên **không** triển khai CDP hub, điểm dừng, hook, JSVMP/WASM, Evidence Graph. Lý do: trường hợp sử dụng chính của CTHarness là "AI mở trang web, đọc DOM, đọc API, nhấp đăng nhập", không bắt buộc năng lực dịch ngược web. **(Tiến triển 2026-09: giai đoạn 1 engine Hook tiêm vào trang và giai đoạn 2 điểm dừng CDP/chặn cấp Worker đã triển khai, xem §9.5; JSVMP/WASM và Evidence Graph vẫn chưa triển khai)**

---

## 11. 🔗 Tài liệu liên quan

- Tổng quan kiến trúc: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Điều hướng ngữ nghĩa cho AI: [`AI_NAVIGATION.md`](AI_NAVIGATION.md)
- Luồng dữ liệu và trình tự: [`EXECUTION_TRACES.md`](EXECUTION_TRACES.md)
- Nguyên tắc kiến trúc bắt buộc: [`ARCHITECTURE_RULES.md`](ARCHITECTURE_RULES.md)
- Chỉ mục tệp: [`FILE_INDEX.md`](FILE_INDEX.md)
- Triển khai MCP hiện có: `harness/src/main/java/top/wkbin/tianxuan/harness/mcp/`
- Bộ thực thi công cụ: `harness/src/main/java/top/wkbin/tianxuan/harness/ToolExecutor.kt`
- Engine phê duyệt: `harness/src/main/java/top/wkbin/tianxuan/harness/ApprovalPolicyEngine.kt`
