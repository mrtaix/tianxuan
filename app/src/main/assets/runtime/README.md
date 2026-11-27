Các thành phần môi trường chạy ở giai đoạn 1

Rootfs Linux ARM64 mặc định được tải trực tuyến, không đưa vào APK. Android 10+ không cho phép ứng dụng đích thực thi ELF từ
thư mục riêng có quyền ghi, vì vậy APK chỉ mang theo chương trình chính ARM64 PRoot rất nhỏ, loader bên ngoài
và các phụ thuộc động của chúng; hệ thống Linux được cài ở lần khởi chạy đầu bằng quy trình OCI của `proot-distro 5.9.0`.

Trình cài đặt chọn manifest `linux/arm64` từ Docker/OCI Registry, lưu các layer đã được xác minh digest
vào bộ nhớ đệm trong thư mục riêng của ứng dụng, hợp nhất theo quy tắc OCI whiteout rồi kích hoạt RootFS theo cách nguyên tử.
Chương trình chính và loader của PRoot phải đến từ cùng một gói Termux chính thức; chạy
`tools/prepare-proot-runtime.ps1` trước khi dựng để xác minh SHA-256 của gói và chuẩn bị đồng thời cả hai.
