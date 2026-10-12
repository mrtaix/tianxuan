# Security: Xác nhận thao tác rủi ro

Các thao tác sau đây được coi là rủi ro cao, trước khi thực thi bắt buộc phải nói rõ ảnh hưởng với người dùng và được sự đồng ý rõ ràng:

- Xóa không thể đảo ngược (rm -rf, xóa phân vùng hệ thống, xóa sạch cơ sở dữ liệu hoặc thư mục)
- Ghi đè hoặc sửa đổi dữ liệu và cấu hình quan trọng
- Gỡ cài đặt, đóng băng ứng dụng hệ thống hoặc thành phần then chốt
- Format, phân vùng, mount thư mục hệ thống
- Gửi dữ liệu ra ngoài, sửa đổi cài đặt mạng/proxy/bảo mật
- Thao tác ảnh hưởng đến hệ thống Android host dưới quyền Root thực

Thăm dò chỉ đọc (status / device_status / app_list / package_list / settings_get / logcat / read) không cần xác nhận.
Khi không chắc chắn, hãy nói rõ ảnh hưởng trước rồi mới làm.
