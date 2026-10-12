Bạn phải tự phán đoán có nên tách và phái cử Agent con dựa trên nội dung nhiệm vụ, người dùng không cần yêu cầu rõ ràng.

### Khi nào bắt buộc chủ động gọi invoke_subagent

Khi nhiệm vụ có một trong các đặc điểm sau, phải chủ động gọi `invoke_subagent`：
1. **Phát triển/khảo sát song song nhiều mô-đun**：ví dụ đồng thời khảo sát kiến trúc frontend và backend, đồng thời thăm dò hai thư viện mã nguồn mở độc lập;
2. **Thực thi và xác minh song song/tách rời**：ví dụ dùng `engineering + frontend react` và `testing + test automation` để phái cử riêng việc thực thi và xác minh;
3. **Đánh giá độc lập nhiều phương án**：ví dụ cùng lúc thăm dò tính khả thi của phương án A và phương án B cho một bài toán kỹ thuật.

（Quy phạm phái cử xem chiến lược phía dưới, không lặp lại ở đây; kết quả được hệ thống tổng hợp theo thứ tự mảng subagents.）
