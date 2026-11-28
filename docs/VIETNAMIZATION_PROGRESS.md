# Tiến độ Việt hóa và bản vá hàng đợi CTHarness

## Phạm vi đã xác minh trong đợt tiếp tục này

- Đã đọc/ghi và kiểm tra trực tiếp 22 tệp Markdown dưới `harness/src/main/assets/prompts/`.
- Không còn ký tự Hán trong 22 tệp này, kể cả nhãn minh họa trong ví dụ lệnh.
- Giữ tên công cụ, khóa tham số, đường dẫn thực và placeholder template `{{...}}`. Các nhãn minh họa tiếng Trung như tên gói/đường dẫn APK đã chuyển thành nhãn tiếng Việt không dấu; không đổi tên lệnh.
- Sửa phần mở đầu `system/core.md`: mô tả nguồn cảm hứng của hệ tọa độ, không gán nguồn gốc cổ văn cho tên CTHarness.
- Chưa hoàn tất dịch toàn repo: persona agent, một phần tài liệu, website/HTML và các nội dung khác vẫn cần xử lý.

## Lỗi hàng đợi: bằng chứng và bản vá

Trước sửa, `PromptQueueManager.enqueue` gắn operation hiện tại cho mọi loại queue. Khi operation kết thúc, `HarnessRuntimeDao.finishOperation` xóa các queue item cùng operation. Vì vậy `NEXT_RUN` được gửi trong lúc agent chạy tool có thể bị xóa trước khi `finishRun` xử lý lượt kế tiếp.

Đã sửa:

1. `PromptQueueManager`: `NEXT_RUN` mới có `operationId = null`; `STEER`/`FOLLOW_UP` giữ cách gắn operation hiện tại.
2. `HarnessRuntimeDao`: trong transaction kết thúc operation, tách `NEXT_RUN` đã lưu với operation cũ trước khi xóa queue thuộc operation. Không đổi schema DB, không cần migration.
3. Giữ xử lý đóng operation khi launch thất bại; Việt hóa thông báo và ghi log nếu đóng operation thất bại.
4. Bổ sung test Room cho dữ liệu queue cũ/mới, thứ tự B/C/D và consume B không làm mất C/D.
5. Bổ sung test queue manager với operation đang chạy và test reclaim bảo toàn tin kế tiếp; cập nhật fake repository theo hợp đồng DAO mới.

Bản vá launch-fail cũ một mình chưa đủ để kết luận đã sửa triệu chứng người dùng. Bản vá mới xử lý đường xóa NEXT_RUN xác nhận được từ mã; chưa được tái hiện end-to-end trên thiết bị.

## Kết quả kiểm tra thực tế

- `python3 -B tools/test-queue-lifecycle.py`: **3/3 đạt**. Thực thi SQL lấy trực tiếp từ DAO trên SQLite in-memory; tái hiện cách dọn cũ làm mất B/C, xác minh cách mới giữ dữ liệu và thứ tự, không ảnh hưởng lane khác.
- `python3 -B tools/check-ctharness.py`: **16/16 đạt**. Đây là kiểm tra hợp đồng mã/tài nguyên, không phải test Android runtime.
- Rà soát 22 tệp prompt bằng Unicode regex: không còn chữ Hán.
- Test Kotlin/Room đã bổ sung nhưng **chưa chạy**: Gradle wrapper báo thiếu `JAVA_HOME` và không tìm được `java`. Các thư mục JVM hiện có không chứa executable Java. Không coi preflight trả exit 0 là bằng chứng toolchain đầy đủ.
- Chưa build APK, chưa cài lên điện thoại, chưa xác nhận UI/provider tự nhận B/C sau tool call.

## Bước tiếp theo

1. Chuẩn bị JDK/Android SDK/Gradle thực sự khả dụng rồi chạy:
   - `:core:database:testDebugUnitTest --tests '*HarnessRuntimeRepositoryIntegrationTest'`
   - `:harness:testDebugUnitTest --tests '*HarnessCompactionAndQueueIntegrationTest'`
   - `:harness:testDebugUnitTest --tests '*OperationCoordinatorTest'`
2. Kiểm thử end-to-end: giữ tool của lượt A, gửi B/C khi A bận, thả tool, xác nhận B/C được chạy đúng thứ tự và đúng một lần, không cần gửi thêm tin D.
3. Rà lại approval/recovery và trường hợp item đầu không claim được task; chưa có xác nhận end-to-end cho các nhánh này.
4. Tiếp tục dịch persona/docs/website theo nhóm file không chồng lấn; kiểm chứng trực tiếp nội dung sau mỗi đợt, không chỉ kiểm tra hết chữ Hán vì prose tiếng Anh cũng cần dịch.

## Đợt tiếp theo — chuỗi hiển thị còn sót (theo yêu cầu mới)

Theo yêu cầu mới, **bỏ toàn bộ `harness/src/main/assets/prompts/**` khỏi phạm vi Việt hóa**; chỉ xử lý chuỗi người dùng thấy trong app/feature/runtime.

Đã dịch và đọc lại trực tiếp các nhóm sau:

- Kho tri thức: `KnowledgeScreen.kt`, `AddDocumentDialog.kt`, `EmbeddingSettingsCard.kt`, `KnowledgeViewModel.kt`.
- Thông báo/dịch vụ nền: `AdbNotificationReceiver.kt`, `AdbNotificationManager.kt`, `WorkflowForegroundService.kt`, `AgentForegroundService.kt`, `AgentMcpForegroundService.kt`, `RuntimeServiceController.kt`, `WorkflowScheduleWorker.kt`, `WorkflowApprovalReceiver.kt`, `WorkflowApprovalNotifier.kt`, `TianXuanApplication.kt`.
- Settings/onboarding/stats: `OnboardingViewModel.kt`, `SshSettingsViewModel.kt`, `CcSwitchViewModel.kt`, `CcSwitchScreen.kt` (đồng bộ khóa màu log `[Bước `), `RomAutostartHelper.kt`, `SponsorList*`, `StatsRepository.kt`, `StatsMetricGrid/Heatmap/RankSection/UsageChart`.
- Khác: `WorkflowEditorState.kt`, `WorkflowScheduleDialog.kt`, `WorkflowViewModel.kt`, `GitManager.kt`, `HomeViewModel.kt`, `TianXuanNavHost.kt`, `ChatAttachment.kt`, `ChatViewModel.kt`, `DeveloperScreen.kt`, template build trong `WorkshopSettingsScreen.kt`.

Cố ý **giữ nguyên** chuỗi là khóa kỹ thuật / hợp đồng nội bộ, không dịch:

- `"连接成功"` (so sánh kết quả trong `ModelEditorScreen.kt`/`SettingsViewModel.kt`).
- `"无环图"` (`WorkflowValidator` message được `contains` trong `WorkflowEditorState.kt`).
- `"自定义"` (giá trị `category` lưu DB, có assertion trong test).
- `"环境不完整"` và regex `^(生成|创建)签名失败`/`^签名导入失败` trong `WorkshopSigningScreen.kt`.
- Delimiter `任务详情：`/`你是被主智能体派发的子智能体`/`[附件：` trong `ChatWorkbenchPanels.kt` (dùng để cắt prompt).
- Tên nhà cung cấp tiếng Trung trong `ProviderBadge.kt` (`阿里云`, `智谱`, …) là khóa tra cứu alias.
- Tên bước build `拉取`/`编译`/`打包` trong `WorkspaceBuildDialogs.kt` (khớp màu với nhãn toolchain).
- `ToolSelfHealingHelper.kt` tạo prompt cho agent → nằm ngoài phạm vi theo yêu cầu mới.

Kiểm tra: `tools/check-ctharness.py` **16/16 đạt**, `tools/test-queue-lifecycle.py` **3/3 đạt**, `git diff --check` sạch, đọc lại từng tệp đã sửa bằng regex Unicode xác nhận không còn chữ Hán trong string literal (trừ các khóa cố ý giữ ở trên). Chưa chạy được test Kotlin/Unit do môi trường thiếu JDK lúc đầu; chỉ build qua GitHub Actions.

### Phát hành (cập nhật cuối)

- Commit `6d71984` (Việt hóa + sửa lỗi build) và `decc07f` (chữ ký cố định) đã push `feat/ctharness-vi`. Trong quá trình build đã sửa 3 lỗi do đợt trước để lại: ratchet kích thước tệp (`ProviderClient.kt`, `ChatMessageBubbles.kt`, `StorageMountSettingsScreen.kt` đưa về đúng baseline) và `RuntimeIconName.Bolt` không tồn tại trong `WorkflowEditorScreen.kt` → đổi về `RuntimeIconName.Play`.
- Build CI: run `46091514190` (success, chữ ký ngẫu nhiên) → phát hiện lỗi cài `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Đã tạo keystore cố định, lưu 4 GitHub Secrets (`TIANXUAN_RELEASE_KEYSTORE_BASE64`, `TIANXUAN_RELEASE_STORE_PASSWORD`, `TIANXUAN_RELEASE_KEY_ALIAS`, `TIANXUAN_RELEASE_KEY_PASSWORD`), cập nhật workflow decode keystore. Run `46130618955` **success**.
- APK `CTHarnessDev-arm64-debug.apk` sha256 `7cd92fa620578568dc7b78cc13ed51cfa6d07a775364aa33e11b6396b5fb3378` (khớp file `.sha256` đi kèm), kích thước 48.099.112 bytes.
- Cài lên thiết bị thành công: gỡ riêng `com.ctharness.app` (bắt buộc do đổi chữ ký → mất dữ liệu của app này), `pm install` → Success. Xác minh versionCode 52 / versionName `0.36.4-dev`, chữ ký v3 `a42edf44`. App cũ `top.wkbin.tianxuan` vẫn còn nguyên, không đụng vào.
- Keystore lưu ngoài repo tại `/workspace/signing/ctharness-release.jks` và trong GitHub Secrets (không commit private key). Các build sau dùng chung chữ ký này.
