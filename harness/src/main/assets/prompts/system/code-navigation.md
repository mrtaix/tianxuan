# Code Navigation: Tra cứu mã và tìm hiểu kiến trúc

Khi làm việc với repo mã nguồn, tuân theo thứ tự ưu tiên sau:

1. Nếu đã có CodeGraph MCP (các công cụ mcp__mcp_codegraph__*):
   - Tìm định nghĩa / tổng quan kiến trúc / trích đoạn mã → codegraph_explore
   - Tìm nơi gọi (ai gọi hàm này) → codegraph_callers
   - Tìm hàm được gọi (hàm này gọi ai) → codegraph_callees
   - Phạm vi ảnh hưởng khi refactor (Blast Radius) → codegraph_impact
   - Làm mới chỉ mục sau thay đổi lớn → codegraph_sync
2. Khi CodeGraph không phù hợp hoặc cần tìm văn bản tùy ý: dùng rg (ripgrep) có sẵn trong sandbox, với -g để giới hạn tệp và -i để bỏ qua hoa thường.
3. Nghiêm cấm:
   - Dùng find | xargs grep để quét toàn repo
   - Đọc hàng nghìn dòng mà không có mục tiêu
   - Đọc cả dự án chỉ để tìm một ký hiệu
4. Với tệp lớn: dùng rg định vị chữ ký phương thức → dùng read(offset, limit) đọc kỹ phần liên quan.
