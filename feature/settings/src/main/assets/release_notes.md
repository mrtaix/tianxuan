# Ghi chú phát hành CTHarness v0.17.0

> **Phiên bản hiện tại**: v0.17.0  
> **Môi trường chạy**: Android 10+ · arm64-v8a (hộp cát PRoot Linux + lập trình cặp cùng AI)

---

### ✨ Tính năng mới

- **Dịch ngoại tuyến trên thiết bị**: Tích hợp sâu khả năng dịch ngoại tuyến trên thiết bị của Google ML Kit, hỗ trợ tự động nhận diện ngôn ngữ và hiển thị tiến độ tải mô hình dùng ngoại tuyến, cho phép dịch hai chiều chính xác mà không cần API Key.
- **Lấy màu động từ hình nền Material You**: Hỗ trợ sâu khả năng lấy màu động từ hình nền hệ thống Android và làm mới theo thời gian thực, đồng thời vẫn cho phép chuyển đổi tự do giữa hai chủ đề: kính mờ “Trong trẻo (Liquid Glass)” và phong cách nguyên bản “Material You”.
- **Cải tiến kiến trúc Agent Harness**:
  - Tiếp thu các phương pháp tốt nhất từ RikkaHub / Pi, cung cấp cơ chế chỉnh sửa chịu lỗi, Prompt Caching, truyền hình ảnh trực tiếp và thử lại với ngữ cảnh sạch.
  - Hoàn thiện cơ chế phát hiện `.agents/skills`, giữ lại nội dung khi dừng, công tắc Prompt Cache và tùy chỉnh TTL.
  - Triển khai cầu nối vai trò Split-Turn và dấu vết tệp trong bản tóm tắt theo quy tắc, bổ sung các khả năng kỹ thuật tùy chọn.
  - Loại bỏ lỗi tràn bộ nhớ 512MB (OOM) do escape lồng trong ảnh chụp trạng thái nén và tải toàn bộ các nhánh.
  - Luôn xuất trường `content` đúng chuẩn và lọc thông minh các gợi ý kỹ năng chỉ dành cho giao diện (UI-only).
  - Đơn giản hóa Schema tham số của công cụ `history_read` và đồng bộ chính xác với việc truy xuất lịch sử.

### 🐛 Sửa lỗi

- **Thiết bị đầu cuối và kết nối**: Chuyển mDNS sang cơ chế phát hiện theo nhu cầu, loại bỏ hoàn toàn hiện tượng hộp thoại bất thường `NSD_PICKER` xuất hiện liên tục trên Android 17.
- **Trải nghiệm trò chuyện tại trung tâm AI**: Bong bóng tin nhắn hỗ trợ nhấn giữ để chọn văn bản và sao chép bằng một lần chạm; tối ưu độ khớp góc bo và khoảng đệm của thanh tiến độ mức sử dụng mô hình.
- **Độ ổn định bố cục UI**: Rà soát và loại bỏ nhiều lỗi khiến mục danh sách cao bất thường hoặc văn bản xuống dòng bị chèn ép, bảo đảm bố cục hài hòa trên màn hình nhỏ và màn hình gập.
- **Cấu hình mô hình**: Loại bỏ ràng buộc bắt buộc về ngôn ngữ suy nghĩ, để mô hình ngôn ngữ tự điều chỉnh theo ngữ cảnh chỉ dẫn.

### ⚡ Bản dựng và kỹ thuật

- Bản dựng Debug tự động dùng lại tham số ký của Release khi có cấu hình khóa ký, tránh sự khác biệt chữ ký giữa bản gỡ lỗi và bản chính thức.
- Chuẩn hóa phân tầng kiến trúc: chuyển toàn bộ bộ máy kết xuất Markdown xuống tầng thành phần dùng chung; hộp thoại cập nhật và nhật ký trong mục Giới thiệu đều hỗ trợ đầy đủ việc xem trước văn bản có định dạng.
