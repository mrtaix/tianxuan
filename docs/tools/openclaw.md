# Ghi chú về khả năng tương thích của OpenClaw

- Adapter: `OpenClawToolInstaller`
- Cách cài đặt: dùng script cài đặt Linux chính thức của OpenClaw, theo phương thức npm với `--no-prompt --no-onboard`, và trỏ npm prefix tới `/opt/tianxuan/tools/openclaw`
- Điều kiện tiên quyết khi cài đặt: Manifest khai báo Node.js `>=22.22.3`, curl, CA certificate và Git; Adapter sẽ lấy các phụ thuộc này qua DependencyManager trước khi cài đặt
- Xác minh: `openclaw --version` và `openclaw doctor`
- Dịch vụ cục bộ: gắn `127.0.0.1:18789`, `LocalServiceLauncher` sẽ chờ cổng sẵn sàng rồi mới bàn giao cho WebView
- Chính sách dữ liệu: chương trình được cài tại `/opt/tianxuan/tools/openclaw`, `OPENCLAW_HOME` và `XDG_CONFIG_HOME` trỏ tới `/opt/tianxuan/data/openclaw`; mặc định gỡ cài đặt giữ lại thư mục dữ liệu này, chỉ xóa khi người dùng chọn xóa dữ liệu.
- Lần đối chiếu cuối: 2026-08-17

Nguồn: [Tài liệu cài đặt chính thức của OpenClaw](https://docs.openclaw.ai/install)

Trước khi phát hành chính thức, vẫn cần xác minh trên thiết bị Android ARM64 mục tiêu về phiên bản Node, trang Gateway và giấy phép chính thức; nếu kho Debian không cung cấp phiên bản Node thỏa mãn ràng buộc, cần chuyển sang Runtime ARM64 chính thức đã được kiểm tra SHA-256.
