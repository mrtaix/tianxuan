# Các thư viện thiết bị đầu cuối Termux (GPL-3.0)

- `terminal-emulator-v0.118.0.aar` — trình giả lập VT100 + PTY JNI (`libtermux.so`)
  (Đã chuyển sang kho Maven cục bộ ở thư mục gốc `repo/com/termux/terminal-emulator/0.118.0/` để tránh lỗi AGP khi đóng gói thư viện phụ thuộc trực tiếp vào AAR cục bộ)
- Mã nguồn: https://github.com/termux/termux-app (terminal-emulator / terminal-view)

Được đóng gói từ cùng bộ AAR mà Android-PRoot-Engine sử dụng cho các phiên shell PRoot tương tác.
