# Workflow: Lập kế hoạch nhiệm vụ nhiều bước và cập nhật tiến độ

## Khi nào bắt buộc tạo plan

Khi nhiệm vụ thỏa mãn bất kỳ điều kiện nào dưới đây, ngay vòng gọi công cụ đầu tiên phải thực thi `plan(action="replace_active", goal=..., steps=[{"id":"1","title":"...","status":"in_progress"}, ...])`:

1. **Phát triển và triển khai phức tạp**: hiện thực chức năng mới, viết crawler, viết lại module, tích hợp hệ thống;
2. **Thao tác nhiều bước hoặc xuyên tệp**: dự kiến cần hơn 3 lần gọi công cụ hoặc sửa hơn 2 tệp;
3. **Gỡ lỗi và kiểm tra build**: điều tra lỗi biên dịch, phân tích crash runtime, phân tích dịch ngược;
4. **Cài dịch vụ và cấu hình môi trường**: cần thực thi lệnh qua nhiều giai đoạn và xác minh;
5. **Chuỗi tự động hóa host**: mở ứng dụng → chờ foreground → nhận biết màn hình/nhấn → gửi broadcast/đổi cài đặt và các thao tác Android nhiều bước khác (ưu tiên tái sử dụng workflow trực quan hoặc công cụ `host`, đồng thời ghi rõ điều kiện quyền trong plan).

*Lưu ý: truy vấn đơn giản một hoặc hai bước (như “đọc tệp xem phiên bản”, “chạy một lệnh kiểm tra”) không cần tạo plan.*

## Vòng đời chuẩn của bảng nhiệm vụ

```text
[Bắt đầu] ──► plan(replace_active) ──► Hiển thị thẻ nhiệm vụ UI
               │
               ▼
[Thực thi] ──► Gọi công cụ cụ thể (read/write/base/mcp/host/subagent)
               │
               ├─► Bước hiện tại hoàn tất ──► plan(advance, steps=[...])
               ├─► Đổi hướng/thêm bước ──► plan(replace_active, steps=[...])
               └─► Gặp trở ngại ──► Dừng phân tích, cập nhật nhánh điều tra trong plan
               │
               ▼
[Kết thúc] ──► Xác minh kết quả thực tế ──► plan(clear_active) ──► Tổng kết bàn giao
```

## Lưu ý về năng lực host (Shizuku / Root)

- Mở ứng dụng, gửi broadcast, đổi cài đặt hệ thống, nhấn/vuốt GUI... ưu tiên `host(action=...)`; khi cần tái sử dụng chuỗi điều phối, hướng dẫn người dùng dùng workflow trực quan `HOST_ACTION` / `CONDITION_BRANCH`.
- Trước thao tác đặc quyền, dùng `host(action="status")` xác nhận chế độ; nếu chưa được cấp quyền thì dừng và giải thích, không thử lại mù quáng.
- Thao tác có tính phá hủy (xóa dữ liệu, đóng băng, đổi cài đặt bảo mật) phải giải thích rủi ro trước, khi cần phải xin người dùng xác nhận.

## Hành vi nghiêm cấm

- Không thử sai công cụ một cách mù quáng, thiếu trật tự với nhiệm vụ phức tạp khi chưa có kế hoạch;
- Không bỏ qua việc đồng bộ tiến độ bảng nhiệm vụ sau khi hoàn tất một bước;
- Khi gặp lỗi, không bỏ qua plan rồi gọi lại nguyên trạng một cách mù quáng.
