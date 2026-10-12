## Ràng buộc môi trường chạy PRoot

Môi trường này là lớp mô phỏng PRoot trên Android, không phải root cấp nhân, nhất định phải tuân thủ:

- Giả Root: các thao tác cấp nhân như chown/chgrp đổi chủ sở hữu, mount, insmod, sysctl, Linux capabilities, setuid... sẽ bị bỏ qua trong im lặng hoặc báo lỗi, đừng dựa vào.
- Hard link: giải nén hoặc cài phụ thuộc (Perl/Python...) gặp lỗi ownership của hard link thì chuyển sang symbolic link (ln -s).
- Hạ cấp setuid: setuid không có hiệu lực và phần dư còn lại sẽ khiến dpkg nâng cấp bị treo; gặp quyền hạn bất thường thì ưu tiên dọn dẹp *.dpkg-tmp và chmod hạ cấp.
- Không có systemd: dịch vụ sẽ không tự khởi động, systemctl không dùng được. Tiến trình thường trú bắt buộc phải dùng công cụ process để quản lý và giữ chạy foreground; nohup / setsid / & trong base không thể sống sót qua các phiên PRoot.
- Tiến trình nền ADB: trong sandbox PRoot không có init/systemd bền vững, adb daemon (fork-server) nền sẽ khởi động lại sau mỗi lần gọi khi tiến trình con thoát; thao tác ADB dòng lệnh cần hoàn thành connect + thao tác mục tiêu trong cùng một lệnh (script đóng gói /opt/tianxuan/bin/adb đã tích hợp sẵn tự động connect), hoặc trực tiếp dùng logcat-grabber / host(action="logcat") qua cầu nối bền vững của host.
- Git: đã cấu hình toàn cục safe.directory = *, có thể chạy git ở bất kỳ thư mục mount nào.
- Năng lực tính toán: CPU/IO của thiết bị di động khá yếu, cài gói lớn và biên dịch tốn thời gian, chú ý timeout; log dài thì dùng grep/head/tail đọc phân mảnh.
