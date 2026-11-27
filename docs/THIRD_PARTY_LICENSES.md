# Danh sách thành phần và giấy phép bên thứ ba

Tài liệu này ghi lại các thành phần bên thứ ba được dự án dùng trực tiếp, tải về qua mạng hoặc đi kèm trong APK. Đây là danh sách rà soát trước khi phân phối, không phải ý kiến pháp lý; trước khi phát hành cần đối chiếu lại giấy phép thượng nguồn, NOTICE và điều kiện tái phân phối của từng phiên bản.

| Thành phần | Mục đích | Nguồn / Phiên bản | Giấy phép và ghi chú phân phối |
|---|---|---|---|
| Debian / Ubuntu ARM64 rootfs | Hệ thống không gian người dùng Linux trên Android | Dùng cơ chế OCI của `proot-distro 5.9.0` để tải từ Docker/OCI Registry | Mỗi bản phân phối và gói phần mềm bên trong tuân theo giấy phép riêng; không đóng gói trong APK, trước khi phát hành cần cung cấp địa chỉ lấy thông tin bản quyền và giấy phép tương ứng |
| Termux PRoot | Bộ khởi động PRoot ARM64 trên Android | Gói nhị phân từ kho chính của Termux, phiên bản và SHA-256 xem trong `ProotInstaller` | Phải đối chiếu giấy phép, NOTICE và địa chỉ lấy mã nguồn cùng với gói nhị phân tương ứng; APK chỉ đi kèm bản dựng ARM64 hiện tại |
| `libandroid-shmem.so` / `libtalloc.so` | Phụ thuộc thư viện động của PRoot | Gói Termux khớp với bản dựng PRoot | Không được giả định là có thể tái phân phối tự do chỉ vì tệp có đuôi `.so`; trước khi phát hành đối chiếu văn bản giấy phép của gói thượng nguồn |
| `zstd-jni` | Giải nén `.tar.zst` trên Android ARM64 | Maven Central, AAR `1.5.5-4` | Phân phối theo giấy phép và NOTICE của zstd-jni thượng nguồn |
| RTK | Rút gọn an toàn đầu ra lệnh tiền cảnh cho Agent | `rtk-ai/rtk` `v0.47.0`, đi kèm trong APK tệp thực thi `aarch64-unknown-linux-gnu`; SHA-256 của gói lưu trữ `960ceb5f1f5f0b0939b32b5b1d41dec6d9a7113137b0703c68dca0d169a260fc` | Apache License 2.0; giấy phép đầy đủ đi kèm APK tại `assets/licenses/rtk-LICENSE`. Chỉ dùng trong môi trường Linux/glibc ARM64 cô lập; lệnh không chạy được hoặc không được hỗ trợ sẽ quay về lệnh gốc |
| Môi trường chạy Node.js ARM64 | Node 22 dự phòng cần thiết cho OpenClaw | Node.js chính thức `v22.22.3` Linux ARM64 tar.xz, SHA-256 được cố định trong `RuntimeBinaryInstaller` | Giấy phép/NOTICE chính thức của Node.js; tải qua mạng, không đóng gói trong APK; trước khi phát hành giữ lại thông tin bản quyền và địa chỉ lấy mã nguồn tương ứng |
| lzhiyong Android SDK Tools | `aapt`, `aapt2`, `aidl`, `zipalign` cho ARM64 | Gói lưu trữ AArch64 tĩnh `35.0.2`, URL và SHA-256 cố định trong `setup_android_core.sh` | Tải qua mạng, không đóng gói trong APK; trước khi phát hành đối chiếu LICENSE, NOTICE và yêu cầu tái phân phối của Apache/AOSP thượng nguồn |
| lzhiyong/termux-ndk | Bộ công cụ máy chủ NDK Linux AArch64 cho dự án Android/Flutter trong môi trường cô lập | r29 / NDK `29.0.14206865`, `android-ndk-r29-aarch64.tar.xz`, SHA-256 cố định trong `setup_termux_ndk.sh` | Tải qua mạng, không đóng gói trong APK; thượng nguồn dựa trên AOSP LLVM/NDK, trước khi phát hành đối chiếu LICENSE, NOTICE, mã nguồn và mô tả thay đổi theo phiên bản cố định |
| OkHttp | Tải HTTPS và gửi yêu cầu tới sổ đăng ký | Maven Central, `4.12.0` | Apache License 2.0; gói phát hành phải chứa văn bản theo yêu cầu bản quyền và NOTICE của Apache |
| AndroidX / Jetpack Compose Material 3 | Giao diện Android và tích hợp hệ thống | Google Maven, phiên bản xem trong `gradle/libs.versions.toml` | Phân phối theo yêu cầu giấy phép và NOTICE của từng thành phần AndroidX/Jetpack |
| Ktor Client | Máy khách HTTP (truyền theo luồng cho Agent) và tải tệp | Maven Central, phiên bản xem trong `gradle/libs.versions.toml` | Apache License 2.0 |
| Kotlin / Kotlinx Serialization / Coroutines | Thư viện môi trường chạy nền tảng của ứng dụng | Maven Central, phiên bản xem trong `gradle/libs.versions.toml` | Phân phối theo yêu cầu giấy phép và NOTICE thượng nguồn của từng thành phần |
| Koin | Tiêm phụ thuộc | Maven Central, phiên bản xem trong `gradle/libs.versions.toml` | Apache-2.0 |
| Room | Cơ sở dữ liệu cục bộ | Google Maven, phiên bản xem trong `gradle/libs.versions.toml` | Phân phối theo giấy phép và NOTICE thượng nguồn |
| Agency Agents (tuyển chọn cho phát triển phần mềm) | Hồ sơ vai trò Agent con tích hợp sẵn và quy trình chuyên môn; cung cấp 136 vai trò theo 9 phòng nghiên cứu và phát triển | `msitarzewski/agency-agents`, bản ghi thay đổi cố định `3c9588880b7cafaec325a104899fd8bbe27e7d72` | MIT; Markdown gốc và giấy phép đầy đủ đi kèm APK trong tài nguyên `agency_agents/`, ứng dụng chỉ chọn lọc các vai trò liên quan đến phát triển phần mềm |

## Nguồn thượng nguồn của trình cài đặt công cụ

OpenAI Codex, OpenClaw và Hermes Agent mặc định do người dùng chủ động nhấn cài đặt sau khi khởi tạo Linux; trình cài đặt gọi qua HTTPS tới điểm cài đặt chính thức của từng dự án; chúng không phải nhị phân tích hợp sẵn trong APK hiện tại. Liên kết thượng nguồn tương ứng và tham số cài đặt được ghi trong:

- `docs/tools/codex.md`
- `docs/tools/openclaw.md`
- `docs/tools/hermes.md`

Vì vậy, giấy phép, nhãn hiệu, điều khoản dịch vụ và thay đổi mã lệnh cài đặt qua mạng của công cụ phải được rà soát lại trước mỗi lần phát hành. Ứng dụng không cho phép sổ đăng ký từ xa phát xuống bất kỳ lệnh trình bao tùy ý nào; sổ đăng ký chỉ cung cấp dữ liệu, hành vi cài đặt thực tế do bộ thích ứng tích hợp trong APK thực hiện và bị ràng buộc bởi danh sách cho phép.

## Kiểm tra trước khi phát hành

- Khóa và ghi lại phiên bản chính xác, SHA-256, giấy phép và NOTICE của từng tệp AAR/JAR/mã máy trong APK.
- Giữ địa chỉ lấy mã nguồn/giấy phép và phiên bản tương ứng cho rootfs của bản phân phối tùy chọn, PRoot và thư viện động.
- Xác nhận rootfs và công cụ tải qua mạng không bị đóng gói nhầm vào APK, cũng không đi vòng qua yêu cầu giấy phép thượng nguồn.
- Bổ sung trang "Giấy phép bên thứ ba" có thể truy cập trong APK hoặc gói văn bản đầy đủ trước khi phát hành trên cửa hàng hoặc công khai.
