## Quy tắc hành vi dịch ngược trang web (họ công cụ hook, cần bật allowHooks)

- Quan sát trước, ghi đè sau: với mục tiêu mới trước tiên dùng `actions:[{"type":"log"}]` quan sát hình thái yêu cầu/lời gọi, xác nhận mục tiêu chính xác rồi mới thêm các thao tác ghi đè như mock/block/redirect/modify_headers;
- Ghi đè thực sự có hiệu lực lên trang: mock/block sẽ thay đổi hành vi thực tế của trang (có thể phá vỡ chức năng, kích hoạt kiểm soát rủi ro của trang web), target glob phải đủ chính xác, đừng dùng `*` để ghi đè toàn cục;
- Bắt thân yêu cầu/phản hồi: quy tắc loại mạng của hook_create đặt `captureBody: true`, sau khi yêu cầu xảy ra thì dùng `browser.network_detail(id)` để xem (id lấy từ cuối đầu ra của `browser.network_list`);
- An toàn và dọn dẹp: đừng ghi khóa bí mật/dữ liệu nhạy cảm vào mock body; sau khi dịch ngược xong dùng `browser.hook_remove` để xóa từng cái hoặc `browser.hook_reset` để xóa sạch quy tắc, tránh làm ô nhiễm hành vi trang sau đó.

## Quy tắc hành vi gỡ lỗi động CDP (họ công cụ debug, cần bật allowCdp)

- Tiền đề gỡ lỗi: trước tiên `browser.debug_attach` (tab bỏ qua = tab đang hoạt động hiện tại) để thiết lập phiên CDP, rồi `browser.debug_set_breakpoint`; URL hỗ trợ khớp hậu tố (như `app.js` khớp mọi script kết thúc bằng nó), line/column là 0-based;
- Tạm dừng tức đóng băng: sau khi breakpoint kích hoạt trang sẽ tạm dừng, trong thời gian đó các công cụ trong trang (evaluate/snapshot/click v.v.) sẽ bị từ chối; dùng `browser.debug_state` xem ngăn xếp lời gọi, `browser.debug_scope` xem biến phạm vi, `browser.debug_eval` tính giá trị trên khung ngăn xếp (có thể đọc/ghi biến cục bộ), `browser.debug_step` chạy từng bước (over/into/out);
- **Quy tắc sắt: kết thúc gỡ lỗi phải `browser.debug_resume`** (tab bỏ qua = khôi phục toàn bộ), nếu không trang sẽ đóng băng vĩnh viễn; trước khi detach sẽ tự động resume, nhưng đừng phụ thuộc vào điều đó;
- Chặn cấp Worker: sau khi attach, quy tắc hook mạng (log/block/redirect/mock/modify_headers) được thực thi bởi Fetch chặn cấp engine, bao phủ các yêu cầu tài nguyên con mà Worker/Service Worker và hook kiểu tiêm không bao phủ được; breakpoint sau khi detach rồi attach lại sẽ tự động phát lại, không cần tạo lại.
