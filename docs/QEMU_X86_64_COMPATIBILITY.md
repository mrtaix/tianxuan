# Build tương thích QEMU x86_64

## Mục tiêu

Thiết bị chạy CTHarness và bộ công cụ build mặc định vẫn dùng `arm64-v8a`. Môi trường tương thích QEMU chỉ xử lý **công cụ build chạy trên máy chủ Linux x86_64** mà dự án bên thứ ba phụ thuộc, không khiến APK cuối cùng tự động chứa ABI x86.

## Cách bật

Vào "Cài đặt → Duy trì hoạt động hệ thống và chẩn đoán cho nhà phát triển", nhập và cài `qemu-x86-64-compat` trong trung tâm plugin cục bộ. Chỉ khi phát hiện plugin đã sẵn sàng mới có thể bật "Chế độ tương thích QEMU x86_64".

Plugin tải xuống và kiểm tra:

- Trình giả lập chế độ người dùng `qemu-x86_64` chạy trên ARM64;
- RootFS Ubuntu Base x86_64 cách ly;
- Trình liên kết động x86_64, glibc và các thư viện chế độ người dùng cơ bản.

Plugin chỉ cung cấp lớp tương thích QEMU user-mode, không còn đóng gói kèm JDK, Gradle, Android SDK hay Flutter.

Tắt công tắc chỉ ngừng việc tự động chọn chế độ tương thích về sau, không xóa ngay tài nguyên đã tải. Nên gỡ tài nguyên qua trung tâm plugin để tránh xóa nhầm môi trường tương thích đang được sử dụng.

## Thư mục cách ly

```text
/opt/tianxuan/compat/x86_64/
├── qemu-x86_64       # Tệp thực thi ARM64
└── rootfs/            # Không gian người dùng Linux x86_64
```

RootFS ARM64 thông thường không thêm `proot -q`. Chỉ phiên chuyên dụng đáp ứng đồng thời các điều kiện sau mới được thêm `-q`:

1. Người dùng đã bật chế độ tương thích;
2. Plugin QEMU đã vượt qua kiểm tra;
3. RootFS được chọn là x86_64 cách ly;
4. Điểm vào build yêu cầu rõ chế độ tương thích.

## Quan hệ với proot-distro

QEMU user-mode không phải năng lực riêng của `proot-distro`. `proot-distro` chỉ chuẩn bị giúp RootFS, đường dẫn QEMU và tham số khởi chạy; tham số thực sự có tác dụng là `-q` của chính PRoot, ví dụ:

```text
proot -q /path/to/qemu-x86_64 -r /path/to/x86_64-rootfs /bin/sh
```

Vào Linux ARM64 thông thường rồi thực hiện một lệnh liên quan đến QEMU của `proot-distro` không khiến Linux hiện tại có năng lực x86_64 vĩnh viễn hay trên toàn hệ thống. Mọi tiến trình cần chạy ELF x86_64 đều phải nằm trong phiên PRoot chuyên dụng có `-q`. Vì vậy CTHarness dùng phiên RootFS x86_64 cách ly, không thêm QEMU trên toàn bộ RootFS ARM64 thông thường.

## Giới hạn hiện tại

Giai đoạn hai đã tích hợp vào điểm vào build workspace thống nhất: mặc định chạy `build_android.sh` / `build_flutter.sh` của ARM64 trước; chỉ khi script báo rõ ELF/kiến trúc máy chủ không tương thích (thường là mã thoát 126), công tắc cài đặt đã bật và plugin tương thích vượt kiểm tra, mới thử lại bằng `build_android_qemu.sh` / `build_flutter_qemu.sh`. Không âm thầm chuyển toàn bộ terminal thông thường sang QEMU.

Chạy trực tiếp `./gradlew` hoặc `flutter build apk` trong terminal vẫn có thể bỏ qua điểm vào build thống nhất. Cơ chế dự phòng chính thức nên được thực hiện qua trình bọc lệnh hoặc Skill build của agent, thay vì sửa Shell trên toàn hệ thống.

## Lưu ý bảo mật

- Bản thân QEMU phải là ELF ARM64 (ELF machine `183`).
- `/bin/sh` và trình liên kết động của RootFS x86_64 phải tồn tại.
- Gói Ubuntu Base do plugin tải về được kiểm tra bằng SHA256 cố định.
- QEMU/PRoot không cung cấp đầy đủ namespace, cgroup, module kernel hay năng lực ảo hóa phần cứng.
- Build chéo kiến trúc sẽ chậm hơn và tiêu tốn bộ nhớ nhiều hơn đáng kể so với chạy trực tiếp trên ARM64.
