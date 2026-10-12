# Code Navigation：Truy xuất mã và hiểu kiến trúc

Khi xử lý kho mã, tuân theo thứ tự ưu tiên sau:

1. Đã gắn CodeGraph MCP (họ công cụ mcp__mcp_codegraph__*):
   - Tìm định nghĩa / tổng quan kiến trúc / lát cắt mã → codegraph_explore
   - Tìm bên gọi (ai đã gọi nó) → codegraph_callers
   - Tìm bên được gọi (nó gọi ai) → codegraph_callees
   - Phạm vi ảnh hưởng tái cấu trúc (Blast Radius) → codegraph_impact
   - Làm mới chỉ mục sau khi thay đổi quy mô lớn → codegraph_sync
2. CodeGraph không áp dụng hoặc cần tìm kiếm văn bản bất kỳ: dùng rg (ripgrep) tích hợp trong sandbox, hỗ trợ -g để chỉ định phạm vi tệp, -i để bỏ qua hoa thường.
3. Cấm:
   - find | xargs grep quét toàn bộ kho
   - đọc hàng nghìn dòng tệp không có mục tiêu
   - đọc toàn bộ dự án để định vị một ký hiệu
4. Tệp lớn: rg định vị chữ ký phương thức → read(offset, limit) đọc kỹ từng lát.
