### Quy ước thao tác dịch ngược Android và kiểm toán mã
- Loại dự án hiện tại: Dịch ngược Android APK；thư mục gốc：`{{WORKSPACE_PATH}}`；dấu thư mục：`{{MARKER_TEXT}}`.
- Chuẩn tắc cốt lõi：APK gốc và `unpacked/` là đầu vào phân tích, trước tiên read `apk-info.properties` và `REVERSE.md`, tuyệt đối không ghi đè APK gốc. Bắt buộc tuân theo quy trình dịch ngược chuẩn hóa **"Lập kế hoạch trước, cấu trúc dẫn đường, mã nguồn ưu tiên, truy xuất mili giây, phổ phản chiếu"**.

#### 1. Bắt buộc lập kế hoạch trước (Planning First)
- Kiểm toán dịch ngược thuộc nhiệm vụ phức tạp nhiều giai đoạn, theo khối quy tắc workflow lập bảng thực thi ở lượt gọi công cụ đầu tiên, nghiêm cấm khám phá mù quáng trong trạng thái không có kế hoạch. Giai đoạn gợi ý：
  1. Cảm nhận cấu trúc (đọc Manifest, nhận diện tên gói chính và các thành phần điểm vào cốt lõi)
  2. Lấy mã nguồn (JADX dịch ngược một chạm ra mã nguồn Java hoặc trích xuất DEX)
  3. Truy xuất đặc trưng cốt lõi và từ khóa (dùng `rg` tìm kiếm chính xác cấp mili giây)
  4. Truy vết sâu lớp cốt lõi và chuỗi gọi (đối chiếu chéo Java/Smali và phân tích phản chiếu)
  5. Tổng hợp kết luận kiểm toán và chứng minh logic nghiệp vụ
- Mỗi khi hoàn thành một giai đoạn, gọi `plan(action="advance")` thúc đẩy bảng kế hoạch.

#### 2. Cảm nhận cấu trúc APK và kiến trúc (Structure & Components)
- Ưu tiên đọc `apk-info.properties` và `AndroidManifest.xml`.
- Trích xuất thông tin then chốt：`package` tên gói, Activity điểm vào, Service/Receiver then chốt, lớp tùy chỉnh Application (thường chứa logic hotfix/đóng gói bảo vệ/khởi tạo), khai báo quyền.
- Khi gặp tham chiếu tài nguyên lớp R (như `@string/login_sms_title` hoặc `0x7f...`), dùng `rg` truy xuất ánh xạ `res/values/strings.xml` hoặc `R.smali`.

#### 3. Dịch ngược mã nguồn và lựa chọn chuỗi công cụ (Decompilation Toolchain)
- **Ưu tiên dịch ngược một chạm thành dự án mã nguồn Java**：
  `jadx -d out/java <đường dẫn tệp APK/DEX>`
  Mã nguồn Java có luồng điều khiển và cấu trúc kiểu đầy đủ, hiệu suất đọc cao hơn Smali hơn 10 lần！Sau khi dịch ngược trực tiếp truy xuất mã dưới `out/java/sources/`.
- **Sửa tài nguyên và Smali**：dùng `apktool d <đường dẫn APK> -o unpacked/` để giải nén, sau khi sửa dùng `apktool b` để biên dịch lại.
- **DEX chuyển JAR**：nếu cần dùng công cụ phân tích bytecode Java khác, có thể thực thi `d2j-dex2jar <đường dẫn APK/DEX> -o out.jar`.

#### 4. Truy xuất siêu tốc cấp mili giây và đồ thị tri thức mã (Ripgrep & CodeGraph)
- Truy xuất mã và truy vết chuỗi gọi thống nhất thực thi theo khối quy tắc code-navigation：ưu tiên CodeGraph MCP (explore/callers/callees/impact), truy xuất văn bản dùng `rg`, nghiêm cấm duyệt `find | xargs grep`.
- Mẫu truy xuất thường dùng trong kịch bản dịch ngược：
  - Tìm tên lớp/tên phương thức/hằng số：`rg "quickPhoneLogin" out/java/`
  - Chỉ tìm tệp Java：`rg -t java "isNewUser" out/java/`
  - Chỉ tìm tệp Smali：`rg -g "*.smali" "invoke-static.*login" unpacked/`
  - Tìm regex bỏ qua hoa thường：`rg -i "sms.*code" out/java/`

#### 5. Phân tích đồ thị gọi plugin hóa và phản chiếu (Plugin & Reflection Architecture)
- Đối với APK chứa kiến trúc plugin hóa/component hóa hoặc nhiều lời gọi phản chiếu như game Ball Battle：
  - **Nhận diện đặc trưng phản chiếu**：truy xuất `Class.forName`、`getMethod`、`invoke`、`ReflectUtils`、lớp đóng gói `Reflect`；
  - **Truy vết chuỗi ký tự tên lớp**：phản chiếu thường kèm chuỗi tên lớp đầy đủ, dùng `rg` truy xuất chuỗi đường dẫn đầy đủ của giao diện hoặc lớp mục tiêu (như `"com.ztgame.mobileappsdk"`)；
  - **Nạp plugin động**：kiểm tra `DexClassLoader`、`PathClassLoader`、đường dẫn giải phóng Asset/Zip hoặc nạp động `.so`, truy vết DEX con hoặc APK plugin.

#### 6. Đọc phân mảnh chính xác tệp lớn và Smali (Targeted Method Reading)
- Tệp lớn đọc phân mảnh theo quy tắc code-navigation, nghiêm cấm đọc mù quáng toàn tệp. Kỹ thuật chuyên dụng cho Smali：trước tiên `rg -n '^\.method' Path/To/Class.smali` trích xuất đại cương chữ ký phương thức, định vị dòng bắt đầu rồi dùng `read(offset, limit)` phân trang chính xác.

#### 7. Ghi chú công việc và lưu giữ manh mối bền vững (Scratchpad Tracking)
- Những tên lớp then chốt, chuỗi gọi hàm, giả thuyết trung gian và thư mục dịch ngược phát hiện trong quá trình dịch ngược, ngay lập tức lắng đọng qua `scratchpad(action="save", key="findings", value=...)`, phòng mất ngữ cảnh trong phiên dài.
