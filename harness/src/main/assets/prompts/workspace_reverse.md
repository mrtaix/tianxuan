### Quy tắc phân tích ngược và kiểm toán mã nguồn Android
- Loại dự án hiện tại: phân tích ngược APK Android; thư mục gốc: `{{WORKSPACE_PATH}}`; dấu nhận diện thư mục: `{{MARKER_TEXT}}`.
- Nguyên tắc cốt lõi: APK gốc và `unpacked/` là đầu vào phân tích; trước tiên dùng read để xem `apk-info.properties` và `REVERSE.md`, tuyệt đối không ghi đè APK gốc. Phải tuân thủ quy trình phân tích ngược chuẩn hóa **“lập kế hoạch trước, lấy cấu trúc làm định hướng, ưu tiên mã nguồn, tìm kiếm trong mili giây, lập đồ thị lời gọi phản chiếu”**.

#### 1. Bắt buộc lập kế hoạch trước
- Kiểm toán bằng phân tích ngược là nhiệm vụ phức tạp gồm nhiều giai đoạn; theo khối quy tắc workflow, phải lập bảng theo dõi thực thi ngay trong lượt gọi công cụ đầu tiên, nghiêm cấm dò tìm thiếu định hướng khi chưa có kế hoạch. Các giai đoạn đề xuất:
  1. Nắm cấu trúc (đọc Manifest, xác định tên gói chính và các thành phần điểm vào cốt lõi)
  2. Thu thập mã nguồn (dùng JADX dịch ngược mã nguồn Java bằng một lệnh hoặc trích xuất DEX)
  3. Tìm đặc trưng cốt lõi và từ khóa (dùng `rg` để tìm chính xác trong mili giây)
  4. Truy vết chuyên sâu các lớp cốt lõi và chuỗi lời gọi (đối chiếu Java/Smali và phân tích phản chiếu)
  5. Tổng hợp kết luận kiểm toán và bằng chứng về logic nghiệp vụ
- Sau mỗi giai đoạn, gọi `plan(action="advance")` để cập nhật bảng theo dõi.

#### 2. Nắm cấu trúc và kiến trúc APK
- Ưu tiên đọc `apk-info.properties` và `AndroidManifest.xml`.
- Trích xuất thông tin then chốt: tên gói `package`, Activity điểm vào, Service/Receiver quan trọng, lớp Application tùy chỉnh (thường chứa logic vá nóng/đóng gói bảo vệ/khởi tạo), các khai báo quyền.
- Khi gặp tham chiếu tài nguyên lớp R (như `@string/login_sms_title` hoặc `0x7f...`), dùng `rg` để tìm trong `res/values/strings.xml` hoặc ánh xạ trong `R.smali`.

#### 3. Dịch ngược mã nguồn và lựa chọn bộ công cụ
- **Ưu tiên dịch ngược thành dự án mã nguồn Java bằng một lệnh**:
  `jadx -d out/java <duong-dan-APK/DEX>`
  Mã nguồn Java có đầy đủ luồng điều khiển và cấu trúc kiểu, giúp đọc hiệu quả hơn Smali trên 10 lần! Sau khi dịch ngược, tìm trực tiếp trong mã tại `out/java/sources/`.
- **Sửa tài nguyên và Smali**: dùng `apktool d <duong-dan-APK> -o unpacked/` để giải gói, sau khi sửa dùng `apktool b` để biên dịch lại.
- **Chuyển DEX sang JAR**: nếu cần dùng công cụ phân tích mã byte Java khác, có thể chạy `d2j-dex2jar <duong-dan-APK/DEX> -o out.jar`.

#### 4. Tìm kiếm siêu nhanh trong mili giây và đồ thị tri thức mã nguồn
- Việc tìm mã và truy vết chuỗi lời gọi phải thống nhất theo khối quy tắc code-navigation: ưu tiên CodeGraph MCP (explore/callers/callees/impact), dùng `rg` để tìm văn bản, nghiêm cấm duyệt bằng `find | xargs grep`.
- Ví dụ tìm kiếm thường dùng khi phân tích ngược:
  - Tìm tên lớp/tên phương thức/hằng số: `rg "quickPhoneLogin" out/java/`
  - Chỉ tìm trong file Java: `rg -t java "isNewUser" out/java/`
  - Chỉ tìm trong file Smali: `rg -g "*.smali" "invoke-static.*login" unpacked/`
  - Tìm bằng biểu thức chính quy không phân biệt hoa thường: `rg -i "sms.*code" out/java/`

#### 5. Phân tích đồ thị kiến trúc plugin và lời gọi phản chiếu
- Với APK có kiến trúc plugin/thành phần hoặc dùng nhiều lời gọi phản chiếu, chẳng hạn trò chơi Battle of Balls:
  - **Nhận diện dấu hiệu phản chiếu**: tìm `Class.forName`, `getMethod`, `invoke`, `ReflectUtils` và các lớp bao bọc `Reflect`;
  - **Truy vết chuỗi tên lớp**: phản chiếu thường đi kèm chuỗi tên lớp đầy đủ; dùng `rg` để tìm chuỗi đường dẫn đầy đủ của giao diện hoặc lớp đích (như `"com.ztgame.mobileappsdk"`);
  - **Nạp plugin động**: kiểm tra `DexClassLoader`, `PathClassLoader`, đường dẫn bung nội dung Asset/Zip hoặc cơ chế nạp động `.so`, rồi truy vết DEX phụ hoặc APK plugin.

#### 6. Đọc chính xác từng phần của file lớn và Smali
- Đọc file lớn theo từng phần theo quy tắc code-navigation, nghiêm cấm đọc toàn bộ file thiếu định hướng. Mẹo riêng cho Smali: trước tiên dùng `rg -n '^\.method' Path/To/Class.smali` để lấy dàn ý chữ ký phương thức; sau khi xác định dòng bắt đầu, dùng `read(offset, limit)` để đọc chính xác từng trang.

#### 7. Ghi chú công việc và lưu giữ manh mối
- Ngay khi phát hiện tên lớp quan trọng, chuỗi lời gọi hàm, giả thuyết trung gian và thư mục dịch ngược, hãy lưu lại bằng `scratchpad(action="save", key="findings", value=...)` để tránh mất thông tin trong ngữ cảnh phiên dài.

