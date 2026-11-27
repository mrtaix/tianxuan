# 🧭 Hướng dẫn dùng plugin ngoại tuyến CTHarness cho người mới

> **Hiểu nhanh trong một câu**: Gói plugin ngoại tuyến của CTHarness (`.txplugin`) là "bộ đầy đủ" đóng gói sẵn toàn bộ công cụ cần thiết để **phát triển Android, lập trình Flutter, biên dịch chéo Rust, dịch ngược/giải mã APK và ký chỉ với một chạm** ngay trên điện thoại. **Không cần tải bất kỳ gói phụ thuộc nào qua mạng, nhập là dùng ngay!**

---

## 🎯 Bảng chọn gói cho người mới: tôi nên cài cái nào?

Nếu bạn không biết mình cần cài gì, chỉ cần đối chiếu nhu cầu theo hướng dẫn dưới đây:

```
                       ┌───────────────────────────────────┐
                       │ Điện thoại còn đủ dung lượng không?│
                       └─────────────────┬─────────────────┘
                                         │
                     ┌───────────────────┴───────────────────┐
                     ▼ Có                                   ▼ Không (muốn tiết kiệm)
    ┌────────────────────────────────────┐    ┌──────────────────────────────────┐
    │ Cài Bộ phát triển Android toàn diện│    │ Chọn plugin thành phần theo nhu cầu│
    │ (2.46 GB, cài trọn bộ một lần)      │    │ (xem danh sách nhu cầu bên dưới) │
    └────────────────────────────────────┘    └─────────────────┬────────────────┘
                                                               │
        ┌──────────────────────────────────────────────────────┼──────────────────────────────────────────────────┐
        ▼                                                      ▼                                                  ▼
【Tôi muốn dịch ngược APK / sửa ứng dụng】       【Tôi muốn viết ứng dụng Flutter】          【Tôi muốn viết mã tầng thấp C/C++ hoặc Rust】
  1. 📦 Bộ nền tảng Android (bắt buộc)            1. 📦 Bộ nền tảng Android (bắt buộc)       1. 📦 Bộ nền tảng Android (bắt buộc)
  2. 📦 Bộ phân tích dịch ngược Android          2. 📦 Bộ phát triển đa nền tảng Flutter    2. 📦 Bộ phát triển mã gốc Android NDK
  👉 Tổng cộng chỉ ~595 MB                       👉 Tổng cộng chỉ ~1.25 GB                  3. 📦 Bộ phát triển biên dịch chéo Rust (tùy nhu cầu)
```

---

## 📦 Giải thích từng plugin bằng ngôn ngữ dễ hiểu (chúng làm gì?)

### 1. 🌟 `Bộ nền tảng Android (bắt buộc)` (~449 MB) — nền tảng bắt buộc
- **Ẩn dụ dễ hiểu**: như **móng nhà** khi xây một căn nhà.
- **Bao gồm gì**:
  - **Java 17 (JDK)**: "bộ não" chạy công cụ phát triển Android và công cụ dịch ngược;
  - **Android SDK & Build-Tools**: lõi đóng gói và biên dịch chính thức của Android;
  - **Gradle**: công cụ quản lý dựng tự động;
  - **tianxuan-apk-sign**: công cụ **căn chỉnh và ký chỉ với một chạm** tích hợp sẵn của CTHarness (giải quyết vấn đề sửa gói xong không cài được).
- **Ai nên cài**: **mọi người đều bắt buộc cài** (trừ khi bạn chỉ dùng C thuần hoặc Dart thuần, không làm bất kỳ APK nào).

---

### 2. 🔍 `Bộ phân tích dịch ngược Android` (~146 MB) — công cụ dịch ngược và chỉnh sửa
- **Ẩn dụ dễ hiểu**: **bộ dụng cụ phẫu thuật** để mổ xẻ, xem và sửa ứng dụng Android.
- **Bao gồm gì**:
  - **JADX**: khôi phục APK thành **mã nguồn Java** dễ đọc;
  - **Apktool**: tách APK thành hình ảnh, cấu hình bố cục và **mã hợp ngữ Smali**, sửa xong còn đóng gói lại cài được;
  - **Dex2jar**: chuyển tệp DEX của Android thành gói Java Jar chuẩn;
  - **Ripgrep (`rg`)**: công cụ tìm mã siêu tốc, 1 giây tìm ra từ khóa trong hàng trăm nghìn dòng;
  - **Uber-apk-signer**: công cụ tự ký một chạm độc lập.
- **Ai nên cài**: người mới muốn giải mã APK, xem mã nguồn, dịch ngôn ngữ/chỉnh sửa, phân tích cơ chế bẻ khóa, trích xuất tài nguyên.

---

### 3. ⚡ `Bộ phát triển mã gốc Android NDK` (~607 MB) — bộ C/C++ hiệu năng cao tầng thấp
- **Ẩn dụ dễ hiểu**: **nhà máy vũ khí hạng nặng** để làm bộ máy trò chơi, module mã hóa và trình điều khiển tầng thấp.
- **Bao gồm gì**:
  - **NDK r29**: trình biên dịch C/C++ chính thức của Android (Clang / LLVM);
  - **CMake & Ninja**: công cụ dựng dự án C/C++ phổ biến quốc tế;
  - **readelf / objdump / nm**: công cụ dịch ngược để xem hàm xuất và ký hiệu bên trong thư viện động `.so`.
- **Ai nên cài**: người mới và kỹ sư cần viết/biên dịch thư viện động C/C++, biên dịch module trò chơi, phân tích tệp `.so`.

---

### 4. 📱 `Bộ phát triển đa nền tảng Flutter` (~809 MB) — phát triển giao diện hiện đại đa nền tảng
- **Ẩn dụ dễ hiểu**: viết một bộ mã là tạo được giao diện đẹp mắt, tối ưu — **công cụ phát triển hiện đại**.
- **Bao gồm gì**:
  - **Flutter 3.47 SDK**: bộ khung đa nền tảng di động chính thức (đã loại bỏ phần thừa cho máy tính, tinh gọn cho điện thoại);
  - **Môi trường thực thi Dart**: môi trường ngôn ngữ lập trình Dart.
- **Ai nên cài**: nhà phát triển muốn học/làm ứng dụng Flutter trên điện thoại, tạo giao diện đẹp.

---

### 5. 🦀 `Bộ phát triển biên dịch chéo Rust` (~505 MB) — ngôn ngữ hiện đại an toàn bộ nhớ
- **Ẩn dụ dễ hiểu**: **công cụ lập trình hệ thống thế hệ mới** nhanh và hầu như không bao giờ gặp sự cố.
- **Bao gồm gì**:
  - **Trình biên dịch Rust 1.85 (`rustc`) & trình quản lý gói (`cargo`)**;
  - **Thư viện biên dịch chéo Android JNI**: trực tiếp biên dịch mã Rust trên điện thoại thành thư viện `.so` dùng được cho Android;
  - Đã tự cấu hình máy chủ phản chiếu tại Trung Quốc của Đại học Thanh Hoa và trình liên kết NDK.
- **Ai nên cài**: người yêu thích Rust, nhà phát triển muốn dùng Rust viết module bảo mật hiệu năng cao cho Android.

---

### 6. 👑 `Bộ phát triển Android toàn diện` (~2.46 GB) — bộ tổng hợp trọn gói
- **Bao gồm tất cả các bộ từ 1 đến 5 ở trên**, nhập một lần là có đủ! Lựa chọn tốt nhất khi điện thoại còn nhiều dung lượng.

---

## 🚀 Hướng dẫn cài đặt chi tiết (nhập vào CTHarness thế nào?)

1. **Bước 1: đưa tệp vào điện thoại**
   - Gửi tệp plugin `.txplugin` trên máy tính (qua QQ, WeChat, cáp dữ liệu hoặc đám mây) tới điện thoại.
2. **Bước 2: nhập trong CTHarness**
   - Mở ứng dụng **CTHarness**;
   - Chạm **「Công cụ」** hoặc **「Cài đặt」** trên thanh điều hướng dưới;
   - Tìm **「Nhập gói plugin cục bộ」** (hoặc chạm dấu `+` góc trên bên phải);
   - Trong trình chọn tệp của điện thoại, chọn tệp `.txplugin` đã tải.
3. **Bước 3: đợi cài đặt xong**
   - Giao diện hiện thanh tiến độ, lần lượt chạy "nạp tài nguyên ➔ kiểm tra tệp nén ➔ giải nén triển khai ➔ tạo liên kết lệnh";
   - Sau khi hiện **「Cài đặt hoàn tất」**, về **Bảng điều khiển** của ứng dụng, trạng thái kiểm tra sức khỏe sẽ hiện ngay **「Sẵn sàng」**!

---

## 💡 Lệnh thực chiến thông dụng cho người mới (dùng terminal thế nào?)

Trong terminal Linux của CTHarness, bạn có thể chạy trực tiếp các lệnh sau:

### 1. Dịch ngược sửa gói và ký một chạm (dùng nhiều nhất!)
```bash
# Bước 1: giải mã APK thành một thư mục
apktool d myapp.apk -o myapp_src

# Bước 2: sửa hình ảnh, văn bản hoặc mã smali bên trong...

# Bước 3: đóng gói thư mục lại thành APK chưa ký
apktool b myapp_src -o myapp_unsigned.apk

# Bước 4: căn chỉnh + tự ký một chạm (kỹ năng tích hợp của CTHarness, tạo thẳng APK cài được!)
tianxuan-apk-sign myapp_unsigned.apk myapp_signed.apk
```

### 2. Xem mã nguồn Java của APK
```bash
# Xuất thẳng mã nguồn APK thành thư mục mã Java
jadx -d out_src myapp.apk
```

### 3. Tìm mã cực nhanh
```bash
# Trong tất cả code của thư mục hiện tại, tìm "password" hoặc "token" trong tích tắc
rg "password"
```

### 4. Lệnh biên dịch và phát triển
```bash
java -version     # xem phiên bản Java
gradle --version  # xem phiên bản Gradle
flutter doctor    # xem trạng thái môi trường Flutter
rustc --version   # xem phiên bản trình biên dịch Rust
cmake --version   # xem phiên bản CMake
```

---

## ❓ Câu hỏi thường gặp

- **C1: Cài mấy plugin này có cần lên mạng không?**
  - **Đáp**: **Hoàn toàn không!** Đây là gói thuần ngoại tuyến 100%, dù điện thoại bật chế độ máy bay vẫn giải nén và dùng hoàn hảo.
- **C2: Sau khi cài báo không đủ dung lượng thì làm sao?**
  - **Đáp**: Đừng cài gói toàn diện 2.46 GB. Chỉ cần cài `Bộ nền tảng Android` (449 MB) + đúng một plugin nhỏ tương ứng (ví dụ chỉ dịch ngược thì cần 146 MB) là được.
- **C3: Vì sao tôi sửa APK của người khác rồi đóng gói lại, điện thoại báo "lỗi phân tích gói cài đặt" hoặc "chữ ký không khớp"?**
  - **Đáp**: Vì APK sau khi đóng gói lại là **chưa ký**, Android cấm cài APK chưa ký. **Hãy chạy `tianxuan-apk-sign myapp_unsigned.apk` để ký một chạm!**
