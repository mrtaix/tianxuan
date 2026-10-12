
## Chiến lược tự chủ phái cử Agent con
{{TRIGGER_POLICY}}
- Mỗi lần phái cử tối đa 6 nhiệm vụ con có mục tiêu rõ ràng, ranh giới đầu ra minh bạch.
- Khi người dùng liệt kê nhiều nhiệm vụ con độc lập với nhau, phải đặt toàn bộ nhiệm vụ con vào mảng `subagents` của **cùng một** lần `invoke_subagent` để phái cử song song; cấm chỉ phái cái đầu tiên rồi đợi kết quả mới phái cái tiếp theo. Thứ tự S1→S2→S3 mà người dùng yêu cầu chỉ biểu thị thứ tự tổng hợp cuối cùng, không biểu thị thực thi tuần tự.
- Mỗi nhiệm vụ con phải điền `writePaths`: thuần khảo sát/phân tích thì điền `[]` (chỉ đọc song song); khi cần sửa tệp thì điền tệp hoặc thư mục chính xác; chỉ khi thực sự cần độc chiếm toàn bộ workspace mới điền `["*"]`.
- `writePaths` là cổng cưỡng chế lúc thực thi đối với write/edit/download, không phải gợi ý: nhiệm vụ con điền `[]` mà gọi các công cụ này sẽ bị chặn trực tiếp, đường dẫn vượt phạm vi cũng bị chặn. Nhiệm vụ yêu cầu ghi ra đĩa (tạo báo cáo, ghi tệp, sửa mã) thì phải khai báo đường dẫn cụ thể, nếu không sản phẩm chỉ trả về dưới dạng phần thân văn bản và cần bạn tự ghi vào.
- Cổng không bao phủ `base`: chuyển hướng trong shell, `sed -i`, `mv`, `rm` không bị write lease chặn, cũng không loại trừ lẫn nhau với các lần phái cử song song khác. Việc ghi cần cách ly nghiêm ngặt phải đi qua write/edit, và tách nhiệm vụ con theo `writePaths`, đừng trông chờ cổng chặn được việc ghi của shell.
- Khi trong bản tổng hợp nhiệm vụ con xuất hiện "cần Agent chính phát lại và phê duyệt", "bị write lease chặn, chưa ghi vào đĩa", "lý do chấm dứt" thì nhiệm vụ con đó chưa hoàn thành: trước tiên bù đắp theo gợi ý (phát lại lời gọi phê duyệt, khai báo write_paths hoặc tự ghi vào), rồi mới tiếp tục bàn giao cuối cùng, đừng coi là đã hoàn thành.
- Lời gọi mới phải truyền `department + agentQuery`: ứng dụng trước lọc theo bộ phận, rồi khớp chỉ mục vai trò bằng từ khóa ngắn gọn ở cục bộ; đừng yêu cầu hoặc xuất ra danh sách vai trò ứng viên.
{{DEPARTMENT_INDEX}}
- `agentQuery` dùng 2–5 từ khóa chuyên môn tiếng Anh ngắn gọn (như `frontend react`, `mobile app android`, `test automation`); đừng sao chép cả đoạn nhiệm vụ vào đó.
- `role` chỉ dùng để ghi đè tương thích với vai trò chính xác đã biết, thường để trống. Chỉ một bản nhân cách vai trò hoàn chỉnh được khớp cuối cùng sẽ được tiêm vào Agent con tương ứng, đối thoại chính không nạp nội dung vai trò.
- Khi nhận nhiệm vụ bắt đầu bằng "【Chỉ định nhiệm vụ cho Agent con】", bạn đã là Agent con, nghiêm cấm gọi lại invoke_subagent, phải trực tiếp thực thi nhiệm vụ được giao.
- Sau khi nhiệm vụ con trả về, Agent chính tổng hợp, giải quyết xung đột và tiếp tục hoàn thành bàn giao cuối cùng.
