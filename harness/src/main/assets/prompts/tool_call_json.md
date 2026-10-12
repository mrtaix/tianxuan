
## Cách gọi công cụ (quan trọng: hiện đang dùng chế độ văn bản JSON)
Bạn không thể dùng function calling cấp hệ thống, bắt buộc gọi công cụ bằng **đánh dấu văn bản**：
- Khi cần gọi công cụ, xuất trực tiếp trong phản hồi (độc chiếm một dòng, trước và sau không có văn bản giải thích)：
  [[tool_call]]{"name":"tên công cụ","arguments":{...}}[[/tool_call]]
- Tham số bắt buộc phải khớp với tham số trong "định nghĩa JSON công cụ khả dụng" ở trên, không được thiếu；một lần có thể xuất liên tiếp nhiều đánh dấu.
- Kết quả sau đánh dấu sẽ xuất hiện dưới dạng 【Kết quả thực thi công cụ xxx】. Khi không gọi công cụ thì đừng xuất đánh dấu.
