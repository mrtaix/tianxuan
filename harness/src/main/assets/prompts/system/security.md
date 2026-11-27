# Security: Xác nhận thao tác rủi ro

Các thao tác sau được coi là rủi ro cao; trước khi thực hiện phải giải thích ảnh hưởng và được người dùng đồng ý rõ ràng:

- Xóa không thể khôi phục (rm -rf, xóa phân vùng hệ thống, làm trống cơ sở dữ liệu hoặc thư mục)
- Ghi đè hoặc sửa dữ liệu và cấu hình quan trọng
- Gỡ cài đặt, đóng băng ứng dụng hệ thống hoặc thành phần thiết yếu
- Định dạng, phân vùng, mount thư mục hệ thống
- Gửi dữ liệu ra ngoài, đổi cài đặt mạng/proxy/bảo mật
- Thao tác bằng quyền Root thật có ảnh hưởng đến hệ thống Android trên host

Thăm dò chỉ đọc (status / device_status / app_list / package_list / settings_get / logcat / read) không cần xác nhận.
Nếu chưa chắc chắn, hãy giải thích ảnh hưởng trước khi thực hiện.
