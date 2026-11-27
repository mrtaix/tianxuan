## Các ràng buộc của môi trường chạy PRoot

Môi trường này là lớp mô phỏng PRoot trên Android, không phải quyền root ở cấp nhân; bắt buộc tuân thủ:

- Root giả lập: các thao tác cấp nhân như đổi chủ sở hữu bằng chown/chgrp, mount, insmod, sysctl, Linux capabilities và setuid sẽ bị bỏ qua âm thầm hoặc báo lỗi; không được dựa vào chúng.
- Liên kết cứng: khi giải nén hoặc cài đặt phụ thuộc (Perl/Python, v.v.) mà gặp lỗi ownership của liên kết cứng, hãy chuyển sang liên kết tượng trưng (ln -s).
- Loại bỏ quyền setuid: setuid không có hiệu lực, nhưng bit setuid còn sót lại có thể làm treo quá trình nâng cấp dpkg; khi gặp bất thường về quyền, ưu tiên dọn *.dpkg-tmp và dùng chmod để gỡ quyền này.
- Không có systemd: dịch vụ không tự khởi động, systemctl không khả dụng. Tiến trình thường trú phải được công cụ process quản lý và luôn chạy ở chế độ foreground; nohup / setsid / & trong base không thể tồn tại xuyên các phiên PRoot.
- Tiến trình nền ADB: trong sandbox PRoot không có init/systemd thường trú; adb daemon (fork-server) chạy nền sẽ phải khởi động lại sau mỗi lần gọi do tiến trình con thoát; các thao tác ADB qua dòng lệnh cần thực hiện connect + thao tác đích trong cùng một lệnh (script bao bọc /opt/tianxuan/bin/adb đã tích hợp connect tự động), hoặc dùng trực tiếp logcat-grabber / host(action="logcat") qua cầu nối thường trú của máy chủ.
- Git: đã cấu hình toàn cục safe.directory = *, có thể thực thi git trong bất kỳ thư mục được gắn kết nào.
- Năng lực tính toán: CPU/IO của thiết bị di động tương đối yếu, cài gói lớn và biên dịch mất nhiều thời gian, cần chú ý thời gian chờ; đọc log dài theo từng phần bằng grep/head/tail.
