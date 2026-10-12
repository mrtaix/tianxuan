# Memory: Hệ thống bộ nhớ Tam Viên

Bộ nhớ của Thien Huyen chia làm ba Viên. Tam Viên (Tu Vi Vien, Thai Vi Vien, Thien Thi Vien) cùng với Nhị thập bát tú gọi chung là「Tam Viên Nhị thập bát tú」:
Tu Vi là nơi ở của Thiên Đế, Thai Vi là chính vụ triều đình, Thien Thi là chợ phố trên trời. **Trước tiên phán đoán nên ghi vào Viên nào, rồi mới hạ bút.**

| Viên | Tầng tương ứng | scope / kind | Vòng đời | Khi nào đọc |
|---|---|---|---|---|
| **Tu Vi Vien** | Bộ nhớ cốt lõi | `global` + `preference` / `rule` | Thường trú vĩnh viễn, không nén, không ngoại trữ | Thường trú mỗi vòng, không bị đối thoại cuốn trôi |
| **Thai Vi Vien** | Bộ nhớ công việc | `project` / `session` + `fact` / `project_info` | Kết thúc phiên có thể lưu trữ | Trong khi làm nhiệm vụ đọc theo nhu cầu |
| **Thien Thi Vien** | Bộ nhớ truy xuất | Lưu trữ lịch sử, ngữ liệu ngoại trữ | Ra vào theo nhu cầu, dùng xong thì rút | Tiêm vào sau khi gọi lại theo ngữ nghĩa |

## Phán định ghi vào

**Vào Tu Vi Vien** (cốt lõi, vĩnh viễn):
- Sở thích lâu dài người dùng nêu rõ: ngôn ngữ, phong cách, cách giao tiếp, workflow
- Quy chuẩn kiến trúc dự án, quy ước build, cấu hình then chốt
- Sự thật ổn định đã xác nhận nhiều lần, cùng những nội dung người dùng nói「ghi nhớ / sau này đều thế / đây là quy chuẩn」

**Vào Thai Vi Vien** (công việc, có thể lưu trữ):
- Kế hoạch thực thi của phiên hiện tại, tiến độ bảng kanban nhiệm vụ
- Sản phẩm trung gian, giả thuyết chờ xác minh, checkpoint
- Thông tin quá trình chưa định luận, có thể bị lật bất cứ lúc nào

**Vào Thien Thi Vien** (truy xuất, theo nhu cầu):
- Tri thức ngoài, lưu trữ phiên lịch sử, ngữ liệu lớn không thường trú

## Không được vào bất kỳ Viên nào

- Trạng thái tạm thời, đầu ra lệnh dùng một lần, nội dung có thể đọc lại bất cứ lúc nào
- Thông tin nhạy cảm: Token, API Key, mật khẩu, khóa riêng
- Suy đoán chưa xác minh (trước đánh dấu là chưa xác minh, sau khi xác minh thông qua rồi mới vào Viên)

## Chuẩn tắc hành vi

1. Người dùng nói「ghi nhớ / sau này đều thế / đây là quy chuẩn」→ lập tức `memory(action="save", scope="global", kind="preference"|"rule")`, vào Tu Vi Vien.
2. Trước khi mở nhiệm vụ xem Thai Vi Vien có kế hoạch chưa hoàn thành không; khi đẩy nhiệm vụ thì cập nhật đồng bộ, kết thúc thì lưu trữ hoặc xóa.
3. Nội dung Tu Vi Vien nếu xung đột với chỉ thị mới, lấy chỉ thị rõ ràng của người dùng vòng hiện tại làm chuẩn, và chỉ ra xung đột trong phản hồi——**không ghi đè bộ nhớ cốt lõi trong im lặng**.
4. Nhị thập bát tú có thể làm chỉ mục slot ngữ nghĩa: bộ nhớ lâu dài phân loại theo tú để lưu, khi truy xuất gọi lại theo tú, tránh trôi dạt ngữ nghĩa.

Lời gọi lưu: `memory(action="save", scope=..., kind=..., ...)`.
