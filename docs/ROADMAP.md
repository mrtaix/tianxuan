# CTHarness — Lộ trình phát triển

> Một câu tóm tắt: dùng **Debian** làm môi trường chạy, dùng **OpenClaw/Hermes/Codex** làm các công cụ được quản lý, dùng **Android** làm tầng sản phẩm, và để một **Agent** tích hợp sẵn (dạng pi) điều khiển mọi thứ bằng ngôn ngữ tự nhiên.

## Kiến trúc (đa mô-đun)

```
:app                tầng lắp ráp: Application / MainActivity / DI / dịch vụ tiền cảnh / res
:feature:nav        điều hướng (tổng hợp các màn hình)
:feature:{home,chat,terminal,workspace,settings,developer,components,theme}
:harness            Agent: HarnessLoop / ProviderClient(streaming) / ToolExecutor(read/write/edit/base)
:tools              ToolManager / trình cài đặt (core/tools + runtime/tools)
:runtime            PRoot / PTY / rootfs / shell
:core:{common,model,database,datastore,network,security}
```

## Đã hoàn thành

- **Môi trường Linux cô lập**: khởi tạo Debian 13 trên thiết bị thật, PRoot, đã chạy `cat /etc/os-release` trên thiết bị thật thành công
- **Thiết bị đầu cuối**: JNI forkpty (cùng cách Termux dùng), đa phiên (siêu dữ liệu được lưu bằng Room), danh sách phiên/đổi tên, thanh phím tắt
- **Agent**: vòng lặp bốn công cụ, phản hồi LLM theo luồng, quản lý mô hình (Room), liên kết phiên ⇄ không gian làm việc
- **Hệ thống công cụ**: ToolCenter cài đặt/xác minh/cập nhật/gỡ cài đặt/khôi phục phiên bản cũ, đếm tham chiếu cho phụ thuộc dùng chung, sổ đăng ký (ký Ed25519)
- **Chất lượng kỹ thuật**: tách đa mô-đun, kiểm thử đơn vị, chuỗi di chuyển dữ liệu Room 5→11

## Tiếp theo (theo thứ tự ưu tiên)

1. **Nghiệm thu trên thiết bị thật** (khi đã kết nối thiết bị): cài APK mới nhất → chạy thông suốt quy trình hoàn chỉnh cài đặt → khởi động → kiểm tra sức khỏe với bất kỳ một trong Codex / OpenClaw / Hermes
2. **Hoàn thiện thiết bị đầu cuối**: chạm để cuộn xem lịch sử, nhập trực tiếp tiếng Trung qua IME
3. **Ổn định**: khôi phục phiên/tác vụ cài đặt sau khi tiến trình bị kết thúc; Agent chạy dài hạn (thông báo dịch vụ tiền cảnh)
4. **Tuân thủ**: rà soát THIRD_PARTY, chiến lược phân phối trên cửa hàng (tác động của việc tải mã thực thi động)

## Giới hạn hiện tại

- Lớp nhập liệu của thiết bị đầu cuối chặn thao tác chạm cuộn trên LazyColumn; tổ hợp nhập tiếng Trung chưa được truyền qua hoàn toàn
- Sổ đăng ký từ xa chưa được cấu hình sẵn địa chỉ đã ký, mặc định dùng bản kê tích hợp trong APK
- Khóa API được lưu trong bộ nhớ mã hóa; toàn bộ chuỗi nhật ký/lưu trữ đều được che dữ liệu nhạy cảm
