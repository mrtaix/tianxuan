# Memory: Hệ thống ký ức Tam Viên

Ký ức của CTHarness chia thành ba viên. Tam Viên (Tử Vi Viên, Thái Vi Viên, Thiên Thị Viên) cùng 28 Tú tạo thành hệ thống “Tam Viên, Nhị Thập Bát Tú”:
Tử Vi là nơi Thiên Đế ngự, Thái Vi là triều đình xử lý chính sự, Thiên Thị là phố chợ trên trời. **Xác định nên ghi vào viên nào trước khi ghi.**

| Viên | Tầng tương ứng | scope / kind | Vòng đời | Khi nào đọc |
|---|---|---|---|---|
| **Tử Vi Viên** | Ký ức cốt lõi | `global` + `preference` / `rule` | Lưu giữ lâu dài, không nén, không chuyển ra ngoài | Luôn hiện diện mỗi lượt, không bị hội thoại đẩy mất |
| **Thái Vi Viên** | Ký ức làm việc | `project` / `session` + `fact` / `project_info` | Có thể lưu trữ khi kết thúc phiên | Đọc khi cần trong lúc thực hiện nhiệm vụ |
| **Thiên Thị Viên** | Ký ức truy hồi | Lịch sử đã lưu trữ, tài liệu bên ngoài | Nạp khi cần, dùng xong thì bỏ khỏi context | Chèn vào sau khi truy hồi ngữ nghĩa |

## Quyết định nơi ghi

**Ghi vào Tử Vi Viên** (cốt lõi, lâu dài):
- Sở thích lâu dài mà người dùng nói rõ: ngôn ngữ, phong cách, cách giao tiếp, workflow
- Chuẩn kiến trúc dự án, quy ước build, cấu hình quan trọng
- Sự thật ổn định đã được xác nhận nhiều lần, cùng nội dung người dùng yêu cầu “hãy nhớ / từ nay làm như vậy / đây là quy chuẩn”

**Ghi vào Thái Vi Viên** (làm việc, có thể lưu trữ):
- Kế hoạch thực thi và tiến độ bảng nhiệm vụ của phiên hiện tại
- Sản phẩm trung gian, giả thuyết chờ kiểm chứng, checkpoint
- Thông tin trong quá trình xử lý chưa có kết luận và có thể bị bác bỏ bất cứ lúc nào

**Ghi vào Thiên Thị Viên** (truy hồi khi cần):
- Kiến thức bên ngoài, lịch sử hội thoại đã lưu trữ, tài liệu dài không cần luôn giữ trong context

## Không ghi vào bất kỳ viên nào

- Trạng thái tạm thời, đầu ra lệnh dùng một lần, nội dung có thể đọc lại bất cứ lúc nào
- Thông tin nhạy cảm: token, API Key, mật khẩu, khóa riêng
- Phỏng đoán chưa được kiểm chứng (đánh dấu chưa xác minh trước, chỉ ghi sau khi xác minh thành công)

## Quy tắc hành vi

1. Khi người dùng nói “hãy nhớ / từ nay làm như vậy / đây là quy chuẩn” → gọi ngay `memory(action="save", scope="global", kind="preference"|"rule")` để ghi vào Tử Vi Viên.
2. Trước khi bắt đầu nhiệm vụ, kiểm tra Thái Vi Viên xem còn kế hoạch dang dở không; đồng bộ khi thực hiện, lưu trữ hoặc dọn khi kết thúc.
3. Nếu ký ức Tử Vi Viên mâu thuẫn với chỉ dẫn mới, ưu tiên chỉ dẫn rõ ràng của người dùng trong lượt hiện tại và nêu mâu thuẫn trong phản hồi — **không âm thầm ghi đè ký ức cốt lõi**.
4. Có thể dùng 28 Tú làm chỉ mục ngữ nghĩa: phân loại ký ức dài hạn theo từng Tú và truy hồi theo Tú để tránh lệch nghĩa.

Lời gọi lưu: `memory(action="save", scope=..., kind=..., ...)`.
