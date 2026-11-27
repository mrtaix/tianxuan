# Ghi chú về khả năng tương thích của Hermes Agent

- Adapter: `HermesToolInstaller`
- Trạng thái nền tảng chính thức: Linux/WSL2 hỗ trợ x86_64 và aarch64; Android Termux là Tier 2 cho aarch64
- Cách cài đặt: dùng `install.sh` chính thức của Hermes với `--skip-setup --skip-browser --non-interactive`, thư mục mã nguồn cố định tại `/opt/tianxuan/tools/hermes-agent`, thư mục dữ liệu tại `/opt/tianxuan/data/hermes-agent`
- Điều kiện tiên quyết khi cài đặt: Manifest khai báo Python `>=3.11`, curl, CA certificate và Git; script cài đặt sẽ tự xử lý uv cùng các phụ thuộc còn lại
- Xác minh: `hermes --version`
- Dashboard cục bộ: gắn `0.0.0.0:9119` (hỗ trợ truy cập trong LAN), `ToolManager.startGateway` sẽ chờ cổng sẵn sàng mới đánh dấu là "đang chạy", tránh trạng thái khởi động giả
- Token truy cập: khi `startService` khởi động sẽ inject các biến môi trường `HERMES_DASHBOARD_TOKEN` / `TIANXUAN_GATEWAY_TOKEN` (đồng nhất với OpenClaw); sau khi tạo lại Token, thực thể đang chạy sẽ tự khởi động lại để áp dụng Token mới
- Chính sách dữ liệu: mặc định gỡ cài đặt chỉ xóa `/opt/tianxuan/tools/hermes-agent` và lối vào lệnh, giữ lại `/opt/tianxuan/data/hermes-agent`; nếu người dùng chọn xóa dữ liệu thì xóa thêm thư mục này.
- Lần đối chiếu cuối: 2026-08-21

Nguồn: [Hỗ trợ nền tảng chính thức của Hermes](https://github.com/NousResearch/hermes-agent/blob/main/website/docs/getting-started/platform-support.md)、, [Script cài đặt chính thức](https://github.com/NousResearch/hermes-agent/blob/main/scripts/install.sh)

Trước khi phát hành chính thức, vẫn cần xác nhận trên thiết bị Android ARM64 mục tiêu về các giới hạn liên quan đến `.[termux]`, Web extra của Dashboard và giấy phép upstream.
