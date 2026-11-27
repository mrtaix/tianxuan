## Quy tắc phân tích ngược trang web (nhóm công cụ hook, cần bật allowHooks)

- Quan sát trước, sửa đổi sau: với mục tiêu mới, trước tiên dùng `actions:[{"type":"log"}]` để quan sát dạng yêu cầu/lời gọi; chỉ thêm các thao tác sửa đổi như mock/block/redirect/modify_headers sau khi đã xác định chính xác mục tiêu;
- Thay đổi có hiệu lực thực sự trên trang: mock/block sẽ thay đổi hành vi thực tế của trang (có thể làm hỏng chức năng hoặc kích hoạt cơ chế kiểm soát rủi ro của trang web); mẫu glob của target phải đủ chính xác, không dùng `*` để sửa đổi trên toàn cục;
- Thu thập nội dung yêu cầu/phản hồi: đặt `captureBody: true` cho quy tắc mạng của hook_create; sau khi yêu cầu phát sinh, dùng `browser.network_detail(id)` để xem (id lấy từ cuối đầu ra của `browser.network_list`);
- An toàn và dọn dẹp: không ghi khóa bí mật/dữ liệu nhạy cảm vào mock body; sau khi phân tích ngược xong, dùng `browser.hook_remove` để xóa từng quy tắc hoặc `browser.hook_reset` để xóa toàn bộ, tránh ảnh hưởng đến hành vi của các trang về sau.

## Quy tắc gỡ lỗi động bằng CDP (nhóm công cụ debug, cần bật allowCdp)

- Chuẩn bị gỡ lỗi: trước tiên dùng `browser.debug_attach` (bỏ qua tab = tab đang hoạt động) để thiết lập phiên CDP, sau đó mới dùng `browser.debug_set_breakpoint`; URL hỗ trợ khớp hậu tố (ví dụ `app.js` khớp mọi script có tên kết thúc như vậy), line/column được đánh chỉ số từ 0;
- Tạm dừng đồng nghĩa với đóng băng: khi chạm điểm dừng, trang sẽ tạm dừng; trong thời gian này, các công cụ thao tác trong trang (evaluate/snapshot/click, v.v.) sẽ bị từ chối; dùng `browser.debug_state` để xem ngăn xếp lời gọi, `browser.debug_scope` để xem biến trong phạm vi, `browser.debug_eval` để đánh giá biểu thức trên khung ngăn xếp (có thể đọc ghi biến cục bộ), và `browser.debug_step` để chạy từng bước (over/into/out);
- **Quy tắc bắt buộc: khi gỡ lỗi xong, phải gọi `browser.debug_resume`** (bỏ qua tab = tiếp tục tất cả), nếu không trang sẽ bị đóng băng vô thời hạn; trước khi detach, hệ thống sẽ tự động resume, nhưng không được dựa vào cơ chế này;
- Chặn bắt ở cấp Worker: sau khi attach, các quy tắc hook mạng (log/block/redirect/mock/modify_headers) được thực thi bằng cơ chế chặn bắt Fetch ở cấp động cơ, bao phủ Worker/Service Worker và các yêu cầu tài nguyên con mà hook dạng chèn mã không tiếp cận được; các điểm dừng sẽ tự động được áp dụng lại khi attach lại sau detach, không cần tạo lại.
