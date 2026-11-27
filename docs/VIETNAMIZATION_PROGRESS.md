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
