# Vấn đề đã biết

Tệp này theo dõi các vấn đề đã biết và ghi chú về môi trường của dự án Android CTHarness.

## 1. Bản dựng ngoại tuyến phụ thuộc vào bộ nhớ đệm thư viện Gradle đã được nạp sẵn

- Tập thư viện phụ thuộc của Giai đoạn 0 dùng Room `2.6.1` và KSP `1.9.22-1.0.17`.
- Room `2.6.1` kéo theo các thư viện phụ thuộc bắc cầu của bộ xử lý:
  - `com.google.devtools.ksp:symbol-processing-api:1.9.0-1.0.13`
  - `org.jetbrains.kotlin:kotlin-stdlib:1.8.22`
  - `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.8.22`
- Trên máy mới chưa lưu đệm các gói này, lần đầu chạy `assembleDebug` ở chế độ ngoại tuyến sẽ thất bại với lỗi
  `No cached version of ... available for offline mode`.
- Trên máy phát triển hiện tại các gói đã được tải xuống và lưu đệm, nên
  `assembleDebug --offline` thành công.
- Nếu dựng trên máy khác có kết nối mạng, hãy bỏ `--offline` một lần để nạp bộ nhớ đệm, sau đó các bản dựng ngoại tuyến sẽ chạy được.

## 2. Lịch sử di chuyển dữ liệu của Room

  các trường phiên bản/siêu dữ liệu của công cụ;
  các lần nâng cấp bình thường không được quay về cách di chuyển phá hủy dữ liệu.
- Nếu lược đồ lại thay đổi, hãy thêm bước di chuyển tiến và một bài kiểm thử di chuyển dữ liệu trên JVM/thiết bị trước khi đổi phiên bản cơ sở dữ liệu. Tuyệt đối không đưa lại `fallbackToDestructiveMigration()` cho bản dựng đã phát hành.

## 3. Bộ thư viện phụ thuộc cố ý giữ phiên bản cũ nhưng ổn định cho Giai đoạn 0

- Kotlin `1.9.22`, AGP `8.2.2`, Gradle `8.5`, KSP `1.9.22-1.0.17`, Room `2.6.1`.
- Các phiên bản này đủ dùng cho khung Giai đoạn 0 và khớp với ràng buộc của kế hoạch dự án ban đầu.
- Một đợt hiện đại hóa trong tương lai có thể căn chỉnh theo bộ công cụ được lưu đệm mới hơn (Kotlin 2.2.x / AGP 8.7.x) nếu dự án sau này cần, nhưng điều đó nằm ngoài phạm vi Giai đoạn 0.

## 4. Các gói môi trường chạy của Giai đoạn 1 được tải xuống khi khởi tạo lần đầu

- RootFS Linux không được đóng gói sẵn trong APK; lần khởi động đầu tiên tải ảnh hệ thống ARM64 do người dùng chọn thông qua quy trình OCI Registry của `proot-distro 5.9.0`.
- Trình cài đặt chọn bản kê `linux/arm64`, kiểm tra từng lớp theo giá trị băm SHA-256 do Registry cung cấp, hợp nhất theo ngữ nghĩa xóa tệp OCI vào thư mục chuẩn bị rồi kích hoạt một cách nguyên tử.
- Bộ nhớ đệm các lớp nằm trong thư mục riêng của ứng dụng, hỗ trợ lớp OCI gzip và zstd; không có điểm vào tương thích cho các gói tar phát hành cũ.
- Nếu thiết bị không phải ARM64, quá trình khởi tạo sẽ dừng ở bước phát hiện kiến trúc; sau khi nhập PRoot xong còn chạy kiểm tra khởi động `--version`.
- Thành phần mã máy của PRoot vẫn cần cố định SHA-256; tính toàn vẹn RootFS được đảm bảo bởi giá trị băm của từng lớp trong bản kê OCI.

## 5. Năng lực cần bổ sung trước bản Beta

- Thiết bị đầu cuối tương tác hiện dùng Debian `script` + `ProcessBuilder` để tạo PTY; bằng cách ghi lại thiết bị phụ `/dev/pts/*` và chạy lệnh `stty -F` riêng, UTF-8, Ctrl+C, đổi kích thước động, ANSI SGR/con trỏ/xóa màn hình và bộ đệm cuộn đã được nối thông, không đưa `stty` vào đầu vào chuẩn của Codex/OpenClaw/Hermes. Bộ xử lý JNI `forkpty` thật vẫn có thể dùng làm phương án thay thế về sau; hiện cần kiểm chứng thêm nhiều giao diện văn bản phức tạp trên thiết bị ARM64.
- Toàn bộ mã cho bộ xử lý PTY thật (JNI forkpty) đã sẵn sàng (`app/src/main/cpp/pty_native.c` + `NativePty.kt`/`NativePtySession.kt`, cùng ngữ nghĩa setsid/thiết bị đầu cuối điều khiển như Termux); máy hiện tại chưa cài NDK, cấu hình `externalNativeBuild` trong `build.gradle.kts` đang tạm thời bị vô hiệu hóa bằng chú thích. Sau khi cài NDK chỉ cần khôi phục cấu hình để bật; khi chưa bật sẽ tự động quay về bộ xử lý `script`, chức năng không bị ảnh hưởng.
- Bộ thích ứng của Codex, OpenClaw, Hermes đã cô lập các quy trình cài đặt, kiểm chứng, cập nhật và gỡ bỏ; mã lệnh chính thức sẽ được tải qua HTTPS xuống `/tmp` của Linux, đặt quyền chỉ chủ sở hữu thực thi, rồi chạy và dọn dẹp bằng `trap`. Nội dung mã lệnh vẫn là mã thượng nguồn dễ thay đổi, nên trước khi phát hành chính thức cần cố định phiên bản theo đúng tài liệu từng bên, rà soát giấy phép và nghiệm thu khả năng tương thích từng công cụ trên thiết bị ARM64.
- Công cụ được cài thống nhất vào `/opt/tianxuan/tools/{toolId}`, điểm vào lệnh ổn định nằm ở `/opt/tianxuan/bin`; dữ liệu cấu hình của Codex, OpenClaw, Hermes lần lượt trỏ tới `/opt/tianxuan/data/{toolId}` qua `CODEX_HOME`, `OPENCLAW_HOME` hoặc `--hermes-home` của Hermes. Mặc định gỡ cài đặt giữ lại dữ liệu; chỉ xóa dữ liệu công cụ tương ứng khi người dùng chọn rõ.
- `RemoteScriptRunner` cấm chuyển hướng sang máy chủ khác khi tải mã lệnh và hỗ trợ cấu hình SHA-256 cố định cho mã lệnh chính thức, kiểm tra bằng `sha256sum -c` trước khi chạy; hiện ba địa chỉ thượng nguồn vẫn chưa có giá trị băm của mã lệnh phiên bản cố định đáng tin cậy lâu dài cho dự án, nên trước khi phát hành Beta phải đổi URL sang địa chỉ có đánh số phiên bản và điền giá trị băm, hoặc dùng gói phát hành cố định.
- Dịch vụ tiền cảnh đã khai báo và yêu cầu quyền thông báo Android 13+; nếu người dùng từ chối thông báo, Agent chạy nền vẫn bị hệ thống hạn chế, nên vào cài đặt hệ thống cho phép lại thông báo rồi dùng dịch vụ chạy liên tục.
- Sổ đăng ký công cụ từ xa đã hỗ trợ HTTPS + kiểm chứng chữ ký Ed25519, siêu dữ liệu cổng dịch vụ và thẻ công cụ động; hiện chưa có sẵn địa chỉ sổ đăng ký đã ký của dự án, mặc định tiếp tục dùng bản kê trong APK.
- RuntimeManager tiếp tục dùng Debian apt cho Python/Git; Node thử apt trước, nếu phiên bản không thỏa ràng buộc công cụ thì chuyển sang gói nhị phân ARM64 chính thức có SHA-256 cố định và cài bền vững vào `/opt/tianxuan`. Trước khi phát hành chính thức vẫn cần kiểm chứng tương thích thư viện động trong môi trường ARM64 mục tiêu.
- Môi trường chạy dùng chung đã có đếm tham chiếu và điểm vào dọn dẹp `apt purge` rõ ràng; mặc định không tự xóa khi gỡ công cụ, vẫn cần bổ sung ước tính dung lượng đĩa chi tiết hơn trước khi phát hành sản phẩm.
- Thống kê dung lượng lưu trữ trên trang nhà phát triển hiện chia theo RootFS, thư mục gốc môi trường chạy, chương trình công cụ, dữ liệu công cụ, không gian làm việc và bộ nhớ đệm, tránh đếm trùng cùng một thư mục; con số vẫn là ước tính kích thước tệp, không bằng số khối thực tế mà hệ thống tệp dùng.
- Sổ đăng ký công cụ đã bổ sung `schemaVersion`, kiểm tra ARM64/HTTPS/đường dẫn dịch vụ, công tắc bật/tắt và siêu dữ liệu phiên bản; cập nhật trên trang chủ giờ đi qua giao dịch UPDATE riêng, thất bại hoặc hủy không giải phóng tham chiếu môi trường chạy dùng chung của phiên bản cũ.
- Giao dịch cài đặt lưu bản chụp thư mục chương trình tại `/opt/tianxuan/.transactions`; khi ứng dụng khởi động sẽ dựa vào các tác vụ vẫn ở trạng thái RUNNING trong Room để khôi phục thư mục trước khi cập nhật hoặc dọn thư mục cài đặt dở, rồi xóa bản chụp sau khi xong. Dữ liệu cấu hình công cụ không nằm trong bản chụp.
- Bản kê thư viện phụ thuộc hỗ trợ ràng buộc phiên bản có kiểm soát (ví dụ `node>=22.22.3`, `python>=3.11`), được phân giải thống nhất thành RuntimeRequirement; bộ thích ứng cụ thể vẫn thực hiện một lần tìm và kiểm chứng thư viện phụ thuộc rõ ràng trước bước cài đặt chính thức.
- URL gốc của nhà cung cấp được phân tích theo URI: HTTPS cho phép API từ xa, HTTP chỉ cho phép các địa chỉ vòng lặp cục bộ chính xác `localhost`, `127.0.0.1` và `::1`, không chấp nhận giả mạo tiền tố kiểu `localhost.evil`.
- Trình tải từ chối kết nối không dùng HTTPS, `Content-Range` tiếp tục tải không hợp lệ và phản hồi trên 1 GiB, đồng thời dùng tệp tạm và ghi nhận nguyên tử; các tài nguyên môi trường chạy vẫn nên cập nhật SHA-256 cố định khi nâng cấp.
- Thiết bị đầu cuối nhận diện URL HTTPS kèm gợi ý OAuth/đăng nhập/ủy quyền thiết bị, hiển thị nút để người dùng chủ động mở trình duyệt; liên kết thường không tự mở, đăng nhập bằng mã thiết bị vẫn hoàn tất ngay trong thiết bị đầu cuối.
- Cờ cài đặt RootFS ghi lại phiên bản nguồn trực tuyến; cập nhật RootFS trên trang nhà phát triển sẽ chuẩn bị trước, giữ `/root` và `/opt/tianxuan`, chạy kiểm tra sức khỏe và khôi phục bản cũ khi thất bại. Vì công cụ bên thứ ba có thể ghi tệp vào các thư mục hệ thống khác của bản phân phối, bản Beta chính thức vẫn nên kiểm chứng thêm khả năng tương thích di chuyển dữ liệu của nhiều tổ hợp công cụ trên ARM64.
- Cập nhật RootFS dùng cơ chế ghi nhận hai giai đoạn bền vững với `rootfs.update.pending` và `rootfs.previous`; nếu ứng dụng bị kết thúc sau khi kích hoạt RootFS mới, lần khởi tạo sau sẽ khôi phục bản cũ trước rồi mới bắt đầu lại quy trình cập nhật.
- Cấu hình công cụ và `/opt/tianxuan` đã được di chuyển ra khỏi rootfs sang vùng gắn kết riêng của ứng dụng; rootfs cũ sẽ được di chuyển một lần khi cấu hình lần đầu, các lần cập nhật công cụ sau không bị mất khi thay rootfs.
- Phòng chống lỗi liên kết cứng và I/O khi dpkg giải nén dưới PRoot:
  - Tự cấu hình `/etc/dpkg/dpkg.cfg.d/01_tianxuan_nodoc` để loại tài liệu/trang hướng dẫn/bản địa hóa, tiết kiệm >50% I/O và loại bỏ nguy cơ giải nén liên kết cứng dạng tài liệu;
  - Tự cấu hình `/etc/apt/apt.conf.d/99tianxuan-apt-config` để tăng cường thử lại và cấu hình phi tương tác;
  - Tự đặt sẵn công cụ tự sửa liên kết cứng ma Perl (liên kết cứng `perlthanks`) tại `/usr/local/sbin/tianxuan-fix-perl` và tự kích hoạt khi `RuntimeManager` bắt được ngoại lệ.
  - Tự cấu hình Git toàn cục `safe.directory = *`, giải quyết triệt để lỗi quyền sở hữu đáng ngờ do UID 0 ảo của PRoot xung đột quyền của không gian làm việc phía máy chủ.

## 7. Môi trường lõi Android: triển khai một lần trong giai đoạn lắp ráp (tái cấu trúc 2026-08)

- Bộ công cụ Android và di động · android-core hiện hoàn tất toàn bộ triển khai môi trường **trong giai đoạn lắp ráp phần bổ trợ**; `build_android.sh` không còn đảm nhiệm tải/xử lý tự phục hồi nào nữa (bản cũ mỗi lần dựng lại lặp việc ghi giấy phép, sinh init.gradle, tự sửa tải Gradle, tốn rất nhiều thời gian).
- Nội dung lắp ráp (`setup_android_core.sh`, chạy lặp lại không đổi kết quả, có thể chạy lại và tiếp tục từ điểm ngắt):
  - Gói nền tảng Android 34 thật: `platform-34-ext7_r03.zip` (android.jar + core-for-system-modules.jar, ~60MB) tải từ máy chủ phản chiếu Tencent Cloud `mirrors.cloud.tencent.com/AndroidSDK/` (nguồn chính làm dự phòng), cố định SHA-1, cài vào `/opt/android-sdk/platforms/android-34/`;
  - Gói độc lập chính thức Gradle 8.9: hai nguồn phản chiếu Tencent Cloud → Huawei Cloud, cố định SHA-256 (`d725d707...cab`);
  - Chấp nhận trước toàn bộ giấy phép SDK và công cụ/siêu dữ liệu Java của Build Tools 35.0.0; chương trình mã máy phía máy chủ dùng aapt/aapt2/aidl/zipalign ARM64 của lzhiyong 35.0.2 với SHA-256 cố định;
  - Bộ công cụ tĩnh Linux AArch64 `lzhiyong/termux-ndk` r29 (NDK `29.0.14206865`), cố định SHA-256 rồi cài vào `/opt/tianxuan/toolchains/android/ndk/artifacts/<digest>/ndk`; AGP dùng đường dẫn bất biến này qua `android.ndkPath` trong chính sách khởi tạo Gradle cục bộ;
  - Nguồn phản chiếu Maven Aliyun toàn cục `/root/.gradle/init.gradle` (chèn qua beforeSettings, áp dụng mọi dự án);
  - Biến môi trường bền vững `/etc/profile.d/tianxuan-android.sh` (JAVA_HOME/ANDROID_HOME/GRADLE_HOME/PATH), `/root/.bashrc` tự nạp;
  - Suy luận JAVA_HOME, liên kết mềm java/gradle toàn cục, chèn cacerts.
- `build_android.sh` được tái cấu trúc thành phần thực thi thuần: nạp cấu hình môi trường → kiểm tra trước (thiếu java.jar/gradle thì dừng ngay và hướng tới trung tâm phần bổ trợ) → ghi local.properties → điều phối Gradle nhiều cấp. Khung `android init` đặt sẵn nguồn phản chiếu Aliyun (thay cho cơ chế tự sửa sed trong lúc dựng của bản cũ).
- Quy trình lắp ráp hàng loạt của ToolManager nới thời gian chờ cho các mã lệnh tải nặng (setup_android_core.sh / setup_flutter.sh / setup_jadx.sh / setup_pnpm.sh) từ 180s lên 20 phút (khớp với GenericRecipeInstaller); khi apt gộp cả lô thất bại thì chuyển sang `--ignore-missing`, tránh việc thiếu một gói lẻ (như apksigner) khiến tất cả không cài được.
- Bộ lõi Android cài AAPT2 và NDK vào thư mục tài nguyên bất biến kèm SHA-256; bản dựng chính chỉ chèn đường dẫn tài nguyên chuẩn. `/opt/tianxuan/android-sdk-tools/aapt2` và `/opt/android-sdk/ndk/<version>` chỉ là đường dẫn tương thích cho dự án cũ. `/root/.gradle/gradle.properties` cố định `android.aapt2FromMavenOverride` và đặt `android.builder.sdkDownload=false`; `/root/.gradle/init.d/tianxuan-android-ndk.gradle` cố định `android.ndkPath`, ngăn AGP tự cài công cụ máy chủ x86_64 chính thức trong lúc dựng. Mã lệnh dựng Android/Flutter dọn `ndk.dir` còn sót lại trong `local.properties` nhưng không ghi ngược lại, để AGP chỉ thấy một bộ định vị NDK duy nhất là `ndkPath`. Quy trình lắp ráp bản dựng và bộ công cụ nội bộ còn dùng khóa chia sẻ/loại trừ qua `/opt/tianxuan/locks/android-toolchain.lock`.
- Lưu ý: bộ dò mức sẵn sàng thành phần của trung tâm phần bổ trợ đã nâng cấp để kiểm tra SDK thật (java + aapt + android.jar + gradle launcher jar); sau khi nâng cấp ứng dụng, môi trường cô lập cũ sẽ hiện chưa sẵn sàng, chỉ cần lắp ráp lại một lần (chạy lặp lại không đổi kết quả, thành phần đã tải sẽ được bỏ qua).

---

# 6. Trạng thái triển khai gần đây (tính đến 2026-08)

## Mô-đun hóa
- Đã tách thành đa mô-đun theo kế hoạch §4: `core/{common,model,database,datastore,network,security}`, `feature/{theme,components,home,chat,terminal,workspace,settings,developer,navigation}`, `runtime`, `tools` (core/tools + trình cài môi trường chạy/công cụ), `harness`, `app`; tên gói thống nhất thành `top.wkbin.tianxuan.*`.

## PTY thật
- Đã cài NDK (30.0), bật bộ xử lý JNI forkpty (`NativePtySession` + `pty_native.c`), tự động chuyển về bộ xử lý `script` khi cần. Đã nối thông nhiều phiên thiết bị đầu cuối (`TerminalSessionManager`), siêu dữ liệu phiên lưu ở bảng `terminal_sessions`, sau khi khởi động lại tiến trình sẽ dựng lại trình bao của phiên cùng tên (chỉ lưu siêu dữ liệu, không lưu đầu ra).
- Chuyển cách chọn phiên từ dòng nhãn giữ chỗ sang nút trên thanh tiêu đề và danh sách phiên bật lên (chuyển/đóng/tạo mới/đổi tên).

## Agent Harness
- Agent tích hợp trực tiếp (kiểu pi): bốn công cụ read/write/edit/base và vòng lặp công cụ, LLM đi qua mô hình được kích hoạt trong `harness_models` (Room), dự phòng bằng cấu hình nhà cung cấp của ứng dụng.
- Đầu ra theo luồng: `ChatApi.chatStream` dùng okio của OkHttp để phân tích SSE từng dòng, cập nhật nội dung tăng dần theo thời gian thực; tham số của tool_calls được tích lũy theo chỉ số; quá trình phân tích dùng chuyển đổi an toàn `as? JsonObject/JsonArray/JsonPrimitive`, tránh JsonNull ném lỗi.
- Đã thêm quản lý mô hình trong hội thoại (thêm/chuyển/xóa mô hình) và đổi tên phiên.
- Phiên có thể gắn không gian làm việc; công cụ base của Agent mặc định chạy trong thư mục làm việc đó.

## Việc cần làm đã biết
- Cuộn chạm trên thiết bị đầu cuối / nhập tiếng Trung qua IME chưa hoàn thiện: lớp nhập trong suốt chặn cuộn chạm LazyColumn khi bàn phím hiện; tổ hợp nhập của IME tiếng Trung chưa thông suốt hoàn toàn.
- Nghiệm thu cài đặt toàn bộ máy, khởi động, kiểm tra sức khỏe của ba công cụ thật Codex / OpenClaw / Hermes **chưa chạy thông suốt** (chỉ sửa phần ngoại vi: tự sửa apt, thời gian chờ 30s→20phút, git HTTP/1.1, thử lại khi cài thất bại).
- Các màn hình `feature` thiếu kiểm thử đơn vị (kiểm thử Compose cần Robolectric, chưa tích hợp).
- Thiết bị hiện ngoại tuyến, APK mới nhất (đa phiên/đầu ra theo luồng/quản lý mô hình/mô-đun hóa) chưa được cài và kiểm chứng trên máy thật.

## Thư viện phụ thuộc / bản dựng
- Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.7 / Room 2.8.4 / Compose BOM 2026.08. Chuỗi di chuyển dữ liệu Room 5→11 (gồm workspaces, harness sessions/messages/models, terminal_sessions). Kiểm thử phân bổ ở `core/*`, `harness`, `feature:terminal` và `app`.
