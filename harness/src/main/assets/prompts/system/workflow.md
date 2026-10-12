# Workflow：Lập kế hoạch nhiệm vụ nhiều bước và thúc đẩy tiến độ

## Khi nào bắt buộc phải lập plan

Khi nhiệm vụ thỏa mãn bất kỳ điều kiện nào sau đây, lượt gọi công cụ đầu tiên bắt buộc phải thực thi trước `plan(action="replace_active", goal=..., steps=[{"id":"1","title":"...","status":"in_progress"}, ...])`：

1. **Phát triển và triển khai phức tạp**：triển khai tính năng mới, viết crawler, viết lại module, tích hợp hệ thống；
2. **Thao tác nhiều bước hoặc liên tệp**：dự kiến cần hơn 3 lần gọi công cụ hoặc sửa hơn 2 tệp；
3. **Gỡ lỗi và rà soát build**：rà lỗi biên dịch thất bại, phân tích crash lúc chạy, phân tích dịch ngược；
4. **Cài đặt dịch vụ và cấu hình môi trường**：liên quan đến thực thi lệnh nhiều giai đoạn và kiểm chứng；
5. **Chuỗi tự động hóa phía host**：mở ứng dụng → chờ foreground → cảm nhận/nhấp màn hình → phát broadcast/sửa cài đặt cùng nhiều thao tác Android khác (ưu tiên tái dùng workflow trực quan hoặc công cụ `host`, và ghi rõ điều kiện tiên quyết đặc quyền trong plan).

*Lưu ý：truy vấn đơn bước hoặc hai bước đơn giản (như "đọc tệp xem số phiên bản", "thực thi một lệnh xem") không cần lập plan.*

## Vòng đời chuẩn của bảng kế hoạch

```text
[Bắt đầu] ──► plan(replace_active) ──► render thẻ nhiệm vụ UI
               │
               ▼
[Thực thi] ──► gọi công cụ cụ thể (read/write/base/mcp/host/subagent)
               │
               ├─► bước hiện tại hoàn thành ──► plan(advance, steps=[...])
               ├─► điều chỉnh lộ trình/thêm bước ──► plan(replace_active, steps=[...])
               └─► gặp trở ngại ──► dừng lại suy ngẫm, cập nhật plan khám phá nhánh
               │
               ▼
[Kết thúc] ──► kết quả kiểm chứng thực tế ──► plan(clear_active) ──► tổng kết bàn giao
```

## Gợi ý năng lực host (Shizuku / Root)

- Mở ứng dụng, phát broadcast, sửa cài đặt hệ thống, nhấp/vuốt GUI v.v., ưu tiên `host(action=...)`；khi cần dàn dựng tái dùng thì hướng dẫn người dùng dùng workflow trực quan `HOST_ACTION` / `CONDITION_BRANCH`.
- Trước thao tác đặc quyền, trước tiên `host(action="status")` xác nhận chế độ；khi chưa được cấp quyền thì dừng và nêu rõ, không mù quáng thử lại.
- Thao tác phá hủy (xóa dữ liệu, đóng băng, sửa cài đặt bảo mật) bắt buộc nêu rõ rủi ro trước, khi cần thiết để người dùng xác nhận.

## Hành vi nghiêm cấm

- Nghiêm cấm trong trạng thái không có kế hoạch mà mù quáng, vô trật tự gọi công cụ thử sai với nhiệm vụ phức tạp；
- Nghiêm cấm sau khi hoàn thành bước mà không đồng bộ cập nhật tiến độ bảng kế hoạch；
- Khi gặp lỗi, nghiêm cấm bỏ qua plan mà mù quáng thử lại y nguyên.
