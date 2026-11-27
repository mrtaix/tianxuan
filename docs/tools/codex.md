# Ghi chú về khả năng tương thích của Codex

- Adapter: `CodexToolInstaller`
- Cách cài đặt: dùng script cài đặt Linux chính thức của OpenAI
- Lệnh: tải `install.sh` chính thức của OpenAI qua HTTPS về thư mục tạm của Linux, kiểm tra tệp không rỗng rồi thực thi; quá trình cài đặt không dùng `curl | sh`, đồng thời trỏ `HOME` tới `/opt/tianxuan/tools/codex`, sau đó tạo lối vào `/opt/tianxuan/bin/codex`.
- Xác minh: `codex --version`
- Runtime: Linux ARM64; cung cấp `curl` và CA certificate trước khi cài đặt
- Chính sách dữ liệu: chương trình được cài tại `/opt/tianxuan/tools/codex`, `CODEX_HOME` trỏ tới `/opt/tianxuan/data/codex`; mặc định gỡ cài đặt giữ lại thư mục dữ liệu này, chỉ xóa khi người dùng chọn xóa dữ liệu.
- Lần đối chiếu cuối: 2026-08-17

Nguồn: [README chính thức của OpenAI Codex](https://github.com/openai/codex/blob/main/README.md)

Trước khi phát hành chính thức, vẫn cần xác nhận trên thiết bị Android ARM64 mục tiêu về khả năng tương thích của file nhị phân chính thức hoặc npm fallback, đồng thời rà soát lại giấy phép.
