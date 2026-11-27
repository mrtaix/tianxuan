# Quy trình làm việc CTHarness

Quy trình làm việc CTHarness là định nghĩa DAG thuần Kotlin, được lưu trữ bền vững và do Harness điều phối; tầng tính năng Compose đảm nhiệm duyệt, khởi động, hiển thị dòng thời gian thực thi và xử lý phê duyệt của con người. Bản thân quy trình không phụ thuộc vào Android SDK, có thể kiểm chứng và kiểm thử độc lập.

## Điểm vào

- Mục "Quy trình làm việc" ở góc trên bên phải của trang không gian làm việc.
- Mục "Chạy quy trình làm việc" trong menu thẻ dự án.
- Gõ `/wf` trong Tri-Shu để mở danh mục quy trình của dự án hiện tại; gõ `/wf <workflow-id>` để mở biểu mẫu xác nhận chạy và tham số của quy trình cụ thể.
- Sau khi dựng không gian làm việc thất bại hoặc tạo APK, ô nhập của Tri-Shu hiển thị các thẻ gợi ý dựa trên sự kiện có kiểu; bấm vào sẽ khởi động quy trình tương ứng kèm lỗi dựng hoặc đường dẫn APK.

## Trình biên tập trực quan

- Tạo quy trình tùy chỉnh mới, hoặc tạo bản sao có thể chỉnh sửa từ quy trình tích hợp sẵn.
- Nút có thể kéo thả và bắt dính theo lưới; nút và đường nối dùng chung một phép biến đổi khung vẽ.
- Hỗ trợ thêm, xóa, chọn nút; chỉnh tiêu đề, mô tả, thời gian chờ, chiến lược xử lý thất bại và cấu hình khóa-giá trị dạng chuỗi JSON. Lời nhắc nhiều dòng được lưu bằng `\n`; JSON không hợp lệ không thể áp dụng và sẽ không làm mất nội dung một cách im lặng.
- Tự sắp xếp dùng phân tầng đường dài nhất và sắp theo trọng tâm để giảm giao nhau; thuộc cùng một lịch sử chỉnh sửa, có thể hoàn tác/làm lại. Quy trình tích hợp mặc định dùng tọa độ phân tầng.
- Kéo đường nối từ nút và chọn nút đích; tự động từ chối kết nối trùng, tự nối và các kết nối tạo chu trình.
- Đường nối có thể chọn bất kỳ cổng đầu ra, thành công hay thất bại, hỗ trợ điều kiện `exitCode == 0`, `output contains 文本` (đầu ra chứa văn bản) và biểu thức chính quy; trình biên tập và bộ điều phối dùng chung một ngữ nghĩa khớp.
- Hỗ trợ xóa kết nối, hoàn tác/làm lại 60 bước, xác nhận khi rời đi có thay đổi chưa lưu và lưu bền vững qua Room.
- Có thể thêm nút suy luận của Agent và nút phái Agent con; cả hai hỗ trợ biến lời nhắc, chờ, giới hạn thời gian và hủy có cấu trúc.

## Ngữ nghĩa điều phối

- Tất cả nút sẵn sàng trong cùng một lượt chạy song song; nút kế tiếp chờ trạng thái kết thúc của các nút phụ thuộc.
- Cạnh điều kiện chỉ kích hoạt nhánh khớp; nhánh không chọn được lan truyền thành `SKIPPED`, không làm nút hợp nhất chờ vĩnh viễn.
- Nút hỗ trợ giới hạn thời gian, một lần tự thử lại, thử lại sau xác nhận của người dùng và hủy.
- Hết thời gian chờ trả về thất bại (mã thoát 124), không báo sai thành người dùng hủy; khi hết lượt thử lại hoặc bị từ chối sẽ dừng, chỉ `CONTINUE` rõ ràng mới tiếp tục điều phối.
- Phê duyệt song song hiển thị theo thứ tự đến; đóng/hủy sẽ giải phóng yêu cầu tương ứng, không ghi đè hay làm mất phê duyệt của nút khác.
- Trước khi lưu định nghĩa, thực hiện kiểm tra cấu trúc gồm ID trùng, cạnh treo, thời gian chờ không hợp lệ và phát hiện chu trình.
- Toàn bộ lịch sử trạng thái của mỗi lần chạy được ghi vào Room; phiên bản cơ sở dữ liệu là 47.
- Chỉnh sửa định nghĩa hay khởi tạo lại quy trình tích hợp sẵn không xóa lịch sử; lịch sử mang bản chụp định nghĩa tại thời điểm đó, nên lưu lần chạy cũ không ghi đè bản chỉnh sửa mới. Cuối danh mục có thể mở 5 bản ghi gần nhất để xem lại, hoặc một chạm "Chạy lại" (dùng lại định nghĩa, biến và không gian làm việc lúc đó; chỉ khởi động bằng hành động rõ ràng của người dùng).

## Giao diện chạy trên điện thoại và màn hình rộng

- Dòng thời gian trên điện thoại hiển thị trạng thái, thời gian thực, tiến độ chạy, mã thoát, lỗi và đường dẫn sản phẩm đầu ra.
- Khi chạy, nếu đã cấp quyền "hiện trên các ứng dụng khác", sẽ hiện cửa sổ nổi quy trình: nút/bước hiện tại, thành công hay thất bại và nút dừng. Trước các thao tác màn hình như nhận diện và chạm, cửa sổ nổi sẽ tạm tháo ra để uiautomator không đọc được nút HUD; lúc mô hình suy nghĩ và nút chờ thì hiện lại.
- Nhật ký / bản so sánh thay đổi có thể mở rộng, cuộn và chọn để sao chép; trong lúc chạy lệnh, phần cuối nhật ký được làm mới khoảng mỗi 300 ms, giữ tối đa khoảng 2 triệu ký tự trong bộ đệm luồng.
- Màn hình rộng hiển thị khung vẽ cùng thanh chi tiết bên phải; bấm nút để xem nhật ký và kết quả tương tự; nút và đường nối vẫn dùng chung phép biến đổi tọa độ thế giới.
- Trước khi chạy luôn hiển thị biểu mẫu xác nhận: biến bắt buộc, biến tùy chọn có giá trị mặc định; nếu quy trình có nút Agent, có thể chọn mô hình dùng cho lần này từ danh sách mô hình đã cấu hình (ghi vào `WORKFLOW_MODEL_ID` / `WORKFLOW_MODEL_VARIANT`, không đổi mặc định toàn cục). Phê duyệt thủ công hiển thị kết quả từ các bước trước để người dùng xem xét.

## Quy trình tích hợp sẵn

- Dựng bản phát hành → xác nhận → cài đặt APK.
- Sự kiện dựng hoàn tất → xác nhận cài APK vừa tạo.
- Sự kiện dựng thất bại → chẩn đoán / phân tích môi trường.
- Nhập lỗi dựng → Agent chỉ đọc chẩn đoán → xác nhận sửa → Agent con sửa tối thiểu → so sánh thay đổi → xác nhận dựng lại.
- Giải nén và dịch ngược APK → Agent kiểm toán chỉ đọc → báo cáo có bằng chứng; không ép ghi đè thư mục giải nén đã có.
- So sánh thay đổi Git → Agent gợi ý bản ghi → người dùng nhập xác nhận thông điệp → ghi nhận thay đổi cục bộ; không đẩy lên kho từ xa.
- **Phòng thí nghiệm tự động hóa phía chủ**: dò quyền → mở cài đặt → chờ ứng dụng lên tiền cảnh → nhận diện màn hình → thông báo (không có đặc quyền thì chuyển sang Toast).
- **Minh họa phát tin quảng bá + cài đặt hệ thống**: biến → phát tin tùy chỉnh → đọc độ sáng → sau phê duyệt ghi độ sáng.
- **Chuyến bay thử giao diện của Agent**: kiểm tra đặc quyền → xác nhận thủ công → mở QQ → vòng `gui_pilot` cục bộ (nhận diện → mô hình quyết định JSON → chạm), mặc định gửi nội dung thử tới nhóm CTHarness.

## Nút và ranh giới bảo mật

Các nút thực thi hiện tại bao gồm kích hoạt, nút kết thúc, Bash, tiến trình được quản lý, suy luận Agent, phái Agent con, dựng CTHarness, **hành động phía chủ (ứng dụng/phát tin quảng bá/cài đặt/giao diện/shell đặc quyền)**, **đánh giá điều kiện**, chờ, đặt biến và phê duyệt thủ công.

### Hành động phía chủ (HOST_ACTION)

Trình biên tập chọn hành động theo danh mục; mục đánh dấu "cần đặc quyền" yêu cầu trong cài đặt đã chuyển sang **Shizuku hoặc Root** và ủy quyền có hiệu lực:

- **Chẩn đoán**: trạng thái quyền, sức khỏe cầu nối, bản chụp trạng thái thiết bị, logcat
- **Ứng dụng**: mở ứng dụng, buộc dừng, xóa dữ liệu, đóng băng/mở băng, cấp/rút quyền, chờ ứng dụng lên tiền cảnh
- **Intent**: phát tin quảng bá, khởi động Activity/Service (hỗ trợ dữ liệu bổ sung: `key=value` / `key:int=1` / `flag:bool=true`)
- **Hệ thống**: đọc/ghi cài đặt, chế độ máy bay, Wi‑Fi, âm lượng đa phương tiện
- **Giao diện**: nhận diện màn hình; chạm/chạm đúp/nhấn giữ/vuốt/cuộn/nhấn phím/dán (`HostGuiToolkit`: có Shizuku/Root thì tự bật dịch vụ cử chỉ trợ năng, rồi chuyển sang `cmd input` / `bin input` nếu cần; tiếng Trung đi qua dán bộ nhớ tạm)
- **Tương tác**: Toast, rung, bộ nhớ tạm, thông báo
- **Nâng cao**: shell đặc quyền, gọi dịch vụ `cmd`; hành động không nhận diện được có thể quay về `command` tùy chỉnh

Phát tin quảng bá và khởi động Activity mặc định đi qua Application Context trước; khi thất bại hoặc chọn ép shell thì dùng `am broadcast` / `am start`.

### Nhánh điều kiện (CONDITION_BRANCH)

Nút cấu hình `expression` và thực sự đánh giá (không còn chỉ truyền qua):

- `exitCode == 0` / `!=`
- `output contains …` / `output matches …`
- `${VAR} == …` / `!=` / `contains` / `matches` / so sánh số
- `empty VAR` / `notEmpty VAR`
- kết hợp `&&` / `||`

Khi đúng, nút có `exitCode=0` và ghi `CONDITION_RESULT=true`; khi sai là `exitCode=1`. Cạnh vẫn dùng được `exitCode` / cổng success·failure để tách luồng.

### Khác

- `DELAY`: chờ số giây; `SET_VARIABLE`: chèn biến toàn cục qua `KEY=value` nhiều dòng.
- Quy trình phát hành tích hợp yêu cầu xác nhận trước khi cài APK; ghi nhận thay đổi thông minh chỉ ghi cục bộ, mặc định không đẩy lên kho từ xa.
- Thêm mẫu tích hợp mới: `host_automation_lab` (mở cài đặt + nhận diện màn hình), `host_broadcast_and_settings` (phát tin quảng bá + cài đặt).

Nút Agent chạy qua phiên Harness và Lane bền vững riêng cho từng lần chạy, từng nút, không chuyển phiên hiện tại của Tri-Shu. `prompt` hỗ trợ `${WORKSPACE_PATH}`, `${变量名}` (tên biến), `${节点ID.output}` (đầu ra của ID nút) và `${previous.output}`; biến sau chỉ hợp nhất các nút tiền nhiệm trực tiếp thực sự được kích hoạt, xuất theo thứ tự đường nối, không bị ảnh hưởng bởi nút song song không liên quan. Nút Agent con có thể cấu hình `role`, hoặc cấu hình đồng thời `department` và `agentQuery`; phạm vi quyền ghi do `writePaths` phân tách bằng dấu phẩy khai báo.

Gợi ý chủ động hiện mới nối với hai loại sự kiện nghiệp vụ: dựng thất bại và APK tạo ra; chỉ gợi ý, không tự thực thi. Trạng thái Git có thay đổi chưa ghi nhận và FileWatch vẫn là điểm mở rộng, chưa bật lắng nghe nền.

## Chạy nền và lịch trình (2026-09)

- **Tách chạy khỏi giao diện**: việc thực thi do đối tượng duy nhất cấp tiến trình `WorkflowRunManager` (harness) giữ; rời hoặc hủy ViewModel trang chạy không hủy quy trình; tầng bộ máy hỗ trợ nhiều lần chạy đồng thời, trang dùng `viewRun(executionId)` để đổi lần đang theo dõi. Trang danh mục hiện biểu ngữ "Đang chạy nền" cho các lần vẫn đang tiến triển, bấm một lần để xem.
- **Lưu bền vững**: khởi động là ghi dòng RUNNING, trong lúc chạy cập nhật hoặc chèn dấu vết tiến độ theo nhịp khoảng 3 giây, khi kết thúc ghi bản chụp trạng thái cuối; dòng lịch sử ghi `triggerSource` (MANUAL/SCHEDULE) và `scheduleId`. Sau khi tiến trình bị giết, lúc khởi động đối soát (`reconcileInterruptedRuns`) đánh dấu các dòng chưa kết thúc là CANCELLED ("tiến trình từng bị hệ thống kết thúc"), giữ nguyên trạng thái nút và nhật ký, có thể chạy lại thủ công từ trang danh mục. Không tiếp tục từ điểm ngắt.
- **Giữ tiến trình tiền cảnh**: khi có lần chạy, Application khởi động `WorkflowForegroundService` (dataSync + WakeLock/WifiLock), hiển thị thông báo tiến độ cho từng lần chạy; khi kết thúc gửi thông báo trạng thái cuối rồi tự thoát. Trên Android 15+, sau giới hạn cứng 6h của dataSync, dịch vụ thoát tiền cảnh theo kiểu DETACH, lần chạy vẫn tiếp tục.
- **Phê duyệt**: khi có nút chờ phê duyệt trong nền, gửi thông báo IMPORTANCE_HIGH, cho phép "Phê duyệt/Từ chối" trực tiếp (`WorkflowApprovalReceiver`), bấm vào nội dung mở thẳng trang chạy (`AppNavigationTarget.WorkflowRun`).
- **Lịch trình**: bảng `workflow_schedules` + WorkManager (UniqueWork, tag=id lịch trình). Lặp lại: mỗi ngày HH:mm (chu kỳ 24h + initialDelay), mỗi N phút (tối thiểu 15), một lần sau thời gian trễ (tự vô hiệu sau khi kích hoạt). WorkManager khởi tạo theo nhu cầu (`Configuration.Provider` + KoinWorkerFactory), sau khi tiến trình bị giết/khởi động lại thiết bị sẽ tự kích hoạt theo lịch. Worker đến hạn đi qua `WorkflowRunManager.start(trigger=SCHEDULE)`; nếu sandbox chưa sẵn sàng thì chờ (khoảng 2 phút), nếu chưa cài RootFS sẽ không tự tải, ghi thẳng lịch sử FAILED. DAILY/INTERVAL dưới Doze có thể trễ vài phút.
- **Ranh giới khôi phục phiên**: các phiên Harness có tiền tố `workflow:<executionId>:<nodeId>` không tham gia `recoverAllInterruptedSessions` tự chạy tiếp, tránh phát lại tác dụng phụ của lần chạy đã chết. Việc từ chối phê duyệt tiếp theo trong luồng sửa không tự hoàn tác các thay đổi đã có.

## Phạm vi kiểm chứng

Tự động hóa bao phủ rẽ nhánh và song song của DAG, phục hồi sau khi hết thời gian chờ, hủy ngay lập tức, hết lượt thử lại, phê duyệt song song, truyền tham số từ nút tiền nhiệm trực tiếp, ổn định bố cục và lưu lịch sử Room. Chuỗi công cụ Linux trên thiết bị, tài khoản mô hình và quyền cài đặt vẫn là điều kiện tiên quyết để chạy đầu cuối; dựng hay kiểm thử đơn vị vượt qua không đồng nghĩa với nghiệm thu toàn chuỗi mô hình/dựng/cài đặt thật.
