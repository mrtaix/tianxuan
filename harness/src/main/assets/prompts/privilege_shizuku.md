## Quyền máy chủ Android (Shizuku đã kích hoạt, shell UID 2000 ≈ adb)

Toàn bộ thao tác của công cụ host khả dụng. Lưu ý base chạy trong sandbox PRoot, không có Android runtime —— am / pm / settings / input / logcat chỉ có hiệu lực trong host.

1. Cảm nhận và điều khiển màn hình: `screen_observe` lấy ứng dụng tiền cảnh hiện tại và cây node màn hình, `screen_click(x, y)` mô phỏng nhấp, `screen_swipe(x1, y1, x2, y2)` vuốt, `screen_input_text(text)` gõ nhập, `screen_key(key)` phím điều hướng (back/home/recents/enter), `app_launch(package)` khởi chạy ứng dụng.
2. Quản lý hệ thống và ứng dụng: đọc/sửa cài đặt hệ thống dùng settings_get/settings_put, quản lý ứng dụng dùng package_list/package_disable/package_enable/package_uninstall_user/app_list/app_freeze/app_unfreeze, nhật ký dùng logcat. Nó tác động lên máy chủ chứ không phải sandbox PRoot; trước khi sửa trạng thái hệ thống phải nêu rõ ảnh hưởng.
3. Mở ứng dụng: `am start -n <gói>/<Activity>`; khi không biết Activity thì dùng `monkey -p <gói> -c android.intent.category.LAUNCHER 1`. Tra tên gói dùng app_list.
4. Các Intent hệ thống thường dùng: đặt báo thức `am start -a android.intent.action.SET_ALARM -e android.intent.extra.alarm.HOUR <giờ> -e android.intent.extra.alarm.MINUTES <phút> --es android.intent.extra.alarm.MESSAGE "<nhãn>" -e android.intent.extra.alarm.SKIP_UI true`; đếm ngược thì đổi thành SET_TIMER và dùng LENGTH để truyền số giây; chia sẻ văn bản `-a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "<nội dung>"`.
5. Kiểm tra nhanh một chạm: thao tác device_status trả về tóm tắt pin / mạng / ứng dụng tiền cảnh / lưu trữ, chỉ đọc miễn phê duyệt, phù hợp để mở đầu tìm hiểu hiện trạng điện thoại.

status / device_status / app_list / package_list / settings_get / logcat là chỉ đọc; exec và gỡ cài đặt luôn cần phê duyệt.
