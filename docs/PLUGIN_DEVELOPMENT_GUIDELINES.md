# Hướng dẫn phát triển plugin CTHarness

Tài liệu này mô tả hành vi thực tế của trình nhập plugin, Registry, trình cài đặt và môi trường chạy PRoot hiện tại của CTHarness. Ngoài những nội dung được đánh dấu rõ là "khuyến nghị", các ví dụ đều dựa trên cách triển khai hiện tại.

## 1. Nguồn plugin

CTHarness chia plugin thành hai loại:

| Nguồn | `source` | Cách cài | Vị trí tài nguyên |
| --- | --- | --- | --- |
| Plugin trực tuyến | `REMOTE` | Thường là `SCRIPT` | Registry tích hợp hoặc đã ký |
| Plugin cục bộ | `LOCAL` | `LOCAL_PACKAGE` | `.txplugin` do người dùng nhập |

Khi nhập gói cục bộ, ứng dụng sẽ ép ghi đè thành:

```json
{
  "source": "LOCAL",
  "offlineOnly": true,
  "installMethod": "LOCAL_PACKAGE"
}
```

Khi plugin cục bộ và plugin trực tuyến dùng cùng `id`, plugin cục bộ được ưu tiên hiển thị. `permissions` hiện chỉ dùng để kiểm tra manifest và hiển thị giao diện, **không phải sandbox phân quyền cho tập lệnh cài đặt**; tập lệnh cài đặt vẫn chạy trong PRoot với quyền của người dùng Linux hiện tại.

## 2. Định dạng gói `.txplugin`

Plugin cục bộ dùng gói chứa ZIP với phần mở rộng `.txplugin`:

```text
my-plugin.txplugin
├── manifest.json
└── payload/
    ├── archives/       # Tệp nén SDK, Gradle, Flutter, NDK và các công cụ khác
    ├── scripts/        # Tập lệnh cài đặt, xác minh và gỡ cài đặt
    ├── config/         # Mẫu cấu hình
    └── bin/            # Tệp ARM64 có thể triển khai trực tiếp
```

Giới hạn cứng:

- Thư mục gốc của ZIP chỉ cho phép `manifest.json` và `payload/`.
- Mục không được dùng đường dẫn tuyệt đối, thoát bằng dấu gạch chéo ngược hoặc `..`.
- `manifest.json` tối đa 1 MiB.
- Tổng dữ liệu của tất cả mục giải nén tối đa 8 GiB.
- Khi nhập gói lớn, cần chứa đồng thời bản sao plugin riêng của ứng dụng, bản sao dữ liệu gói trong bản phân phối Linux, vùng giải nén tạm và thư mục cài đặt cuối cùng; dung lượng dự phòng nên lớn hơn rõ rệt so với bản thân tệp `.txplugin`.

Sau khi nhập, gói được giải nén vào thư mục phiên bản riêng của ứng dụng; trước khi cài, `payload/` được sao chép vào bản phân phối hiện tại:

```text
/opt/tianxuan/imports/<id>
```

## 3. `manifest.json`

Manifest tối thiểu của plugin cục bộ:

```json
{
  "schemaVersion": 1,
  "id": "hello-arm64",
  "name": "Hello ARM64",
  "description": "TianXuan 本地插件示例",
  "version": "1.0.0",
  "publisher": "Your Name",
  "category": "DEVELOPER",
  "launchType": "command",
  "architectures": ["ARM64"],
  "permissions": [],
  "updateStrategy": "REINSTALL",
  "source": "LOCAL",
  "offlineOnly": true,
  "installMethod": "LOCAL_PACKAGE",
  "installSteps": [
    "/bin/sh \"$TIANXUAN_PLUGIN_PAYLOAD/scripts/install.sh\""
  ],
  "uninstallSteps": [
    "/bin/sh \"$TIANXUAN_PLUGIN_PAYLOAD/scripts/uninstall.sh\""
  ],
  "launchCommand": "hello",
  "verifyCommand": "hello",
  "commandLinks": ["hello"]
}
```

Ràng buộc trường:

| Trường | Ràng buộc hiện tại |
| --- | --- |
| `schemaVersion` | Hiện phải là `1`. |
| `id` | Biểu thức chính quy là `[a-z0-9][a-z0-9-]{1,63}`, độ dài 2–64. |
| `version` | Phải không rỗng; nên dùng dạng chuẩn `x.y.z`. |
| `architectures` | Phải chứa `ARM64`, không phân biệt chữ hoa/thường. |
| `launchType` | `one_shot`、`command`、`pty`、`web` hoặc `service`. |
| `permissions` | Chỉ chấp nhận `NETWORK`、`WORKSPACE_READ`、`WORKSPACE_WRITE`、`LOCAL_WEB`. |
| `updateStrategy` | `REINSTALL` hoặc `IN_PLACE`. |
| `homepage` | Nếu có, phải dùng HTTPS. |
| `servicePort` | Nếu có, phải từ 1–65535 và kiểu khởi chạy phải là `web/service`. |
| `servicePath` | Phải bắt đầu bằng `/` và không chứa `..`. |

Plugin trực tuyến nên dùng `source=REMOTE` và `installMethod=SCRIPT`. Bộ kiểm tra hiện chưa bắt buộc tổ hợp `REMOTE` + `SCRIPT`, nhưng người phát hành Registry vẫn phải tuân theo quy ước này.

## 4. Biến môi trường do trình cài đặt cung cấp

```text
$TIANXUAN_TOOL_ID         ID plugin hiện tại
$TIANXUAN_TOOL_DIR        /opt/tianxuan/tools/<id>
$TIANXUAN_TOOL_DATA       /opt/tianxuan/data/<id>
$TIANXUAN_PLUGIN_PAYLOAD  /opt/tianxuan/imports/<id> (chỉ plugin cục bộ)
```

Tệp chương trình đặt trong `$TIANXUAN_TOOL_DIR`; cấu hình, bộ nhớ đệm, mô hình hoặc dữ liệu dự án của người dùng đặt trong `$TIANXUAN_TOOL_DATA`. Thư mục sau mặc định được giữ lại khi gỡ cài đặt thông thường.

Mỗi tên trong `commandLinks` mặc định trỏ tới:

```text
$TIANXUAN_TOOL_DIR/bin/<command>
```

Vì vậy tập lệnh phải tạo tệp tương ứng và khôi phục quyền thực thi.

## 5. Quy tắc tương thích với RootFS tối giản

Plugin chỉ được mặc định phụ thuộc vào `/bin/sh` và các lệnh cơ bản mà RootFS thực tế cung cấp. Đừng giả định các công cụ tùy chọn sau tồn tại:

```text
unzip  xz  file  readelf  jq  python  node
```

Quy tắc cụ thể:

- Tập lệnh độc lập bắt đầu bằng `#!/bin/sh`, dùng cú pháp POSIX Shell và xuống dòng LF.
- Trong manifest dùng `/bin/sh` tuyệt đối, đừng phụ thuộc vào `sh` trong `PATH`.
- Ưu tiên phát hành `.tar.gz`; khi dùng `.tar.xz` phải đóng kèm trình giải nén tương thích, không được giả định hệ thống có `xz`.
- ZIP không đảm bảo bảo lưu bit thực thi Unix; sau khi giải nén phải `chmod` rõ ràng.
- Đừng dùng `find -type f` để tìm công cụ có thể là liên kết tượng trưng; dùng `\( -type f -o -type l \)`.
- Đừng truyền ký tự đại diện trực tiếp làm đối số đơn cho `test -x`; trước tiên dùng `find ... -print -quit` để lấy đường dẫn xác định.
- Đừng phụ thuộc vào `file` hay `readelf` để kiểm tra kiến trúc; có thể đọc trực tiếp phần đầu ELF.

Ví dụ kiểm tra ELF64 AArch64 theo POSIX:

```sh
elf_bytes() { od -An -t x1 "$@" 2>/dev/null | tr -d ' \n'; }
is_elf() { test "$(elf_bytes -N 4 "$1")" = "7f454c46"; }
is_aarch64_elf() { test "$(elf_bytes -j 18 -N 2 "$1")" = "b700"; }
```

`e_machine` nằm tại độ lệch 18 trong phần đầu ELF; AArch64 là 183, tức các byte theo thứ tự đầu nhỏ `b7 00`.

## 6. Plugin Hello

Cấu trúc thư mục:

```text
hello-arm64/
├── manifest.json
└── payload/
    └── bin/
        └── hello
```

`payload/bin/hello`:

```sh
#!/bin/sh
echo "hello from TianXuan"
```

Các bước cài đặt trong manifest:

```json
{
  "installSteps": [
    "test -f \"$TIANXUAN_PLUGIN_PAYLOAD/bin/hello\"",
    "mkdir -p \"$TIANXUAN_TOOL_DIR/bin\"",
    "cp \"$TIANXUAN_PLUGIN_PAYLOAD/bin/hello\" \"$TIANXUAN_TOOL_DIR/bin/hello\"",
    "chmod 755 \"$TIANXUAN_TOOL_DIR/bin/hello\""
  ],
  "uninstallSteps": [
    "rm -f \"$TIANXUAN_TOOL_DIR/bin/hello\""
  ],
  "launchCommand": "hello",
  "verifyCommand": "hello",
  "commandLinks": ["hello"]
}
```

## 7. Gói phụ thuộc ngoại tuyến dung lượng lớn

Tài nguyên lớn đặt trong `payload/archives/`; tập lệnh trước tiên kiểm tra giá trị băm, rồi giải nén vào vùng tạm, sau khi xác minh mới chuyển vào vị trí cuối cùng:

```sh
#!/bin/sh
set -eu

archive="$TIANXUAN_PLUGIN_PAYLOAD/archives/flutter-arm64.tar.gz"
expected_sha256="<SHA256>"

test -s "$archive"
printf '%s  %s\n' "$expected_sha256" "$archive" | sha256sum -c -

rm -rf /opt/flutter.staging
mkdir -p /opt/flutter.staging
tar -xzf "$archive" -C /opt/flutter.staging

test -x /opt/flutter.staging/flutter/bin/flutter
rm -rf /opt/flutter
mv /opt/flutter.staging/flutter /opt/flutter
```

Yêu cầu:

- `offlineOnly=true` sẽ bỏ qua việc lấy các thành phần phụ thuộc khi chạy như Node/Python/Curl, nhưng không ngăn tập lệnh tự kết nối mạng; bản thân plugin ngoại tuyến không được gọi mạng hay trình quản lý gói.
- Công cụ chạy phía chủ phải chạy được trong Linux ARM64 PRoot. Chương trình Android ARM64/Bionic và chương trình Linux ARM64/glibc không được trộn lẫn chỉ vì cùng kiến trúc.
- Mọi tệp nén lớn đều phải cố định SHA-256.
- Vùng tạm phải nằm trên cùng hệ thống tệp, sau khi xác minh xong mới `mv` vào thư mục cuối cùng.
- Tập lệnh cài đặt có thời gian chờ mặc định 15 phút; mỗi lần `verifyCommand` có thời gian chờ mặc định 60 giây.

## 8. Tiến độ thời gian thực và nhật ký

Trình cài đặt ghi lại sự kiện giai đoạn, đầu ra chuẩn và đầu ra lỗi chuẩn. Tập lệnh plugin có thể xuất tiến độ tương đối có cấu trúc:

```text
[TIANXUAN_PROGRESS:47] [EXTRACT] 正在解压 Android NDK r29
```

Định dạng:

```text
[TIANXUAN_PROGRESS:<0..100>] <用户可读消息>
```

Tiến độ phải tăng đơn điệu. Nhãn thông điệp khuyến nghị:

- `[COPY]`: sao chép tài nguyên.
- `[EXTRACT]`: giải nén tệp nén.
- `[COMMAND]`: thực thi lệnh hoặc bước cấu hình rõ ràng, đã che thông tin nhạy cảm.
- `[VERIFY]`: kiểm tra giá trị băm, kiến trúc và trạng thái cuối cùng.

Đừng dùng `set -x` để tạo nhật ký cài đặt, vì nó có thể in ra mã truy cập, địa chỉ máy chủ ủy nhiệm hoặc biến môi trường khác. Tiến độ có cấu trúc là tiến độ tương đối bên trong công thức cài đặt; ứng dụng sẽ ánh xạ sang thanh tiến độ cài đặt đầy đủ.

## 9. Đóng gói, phiên bản và nhập gói

### Plugin nhỏ

Windows PowerShell:

```powershell
Compress-Archive `
  -LiteralPath .\hello-arm64\manifest.json, .\hello-arm64\payload `
  -DestinationPath .\hello-arm64.zip `
  -Force
Move-Item .\hello-arm64.zip .\hello-arm64.txplugin
```

Linux/macOS:

```sh
cd hello-arm64
zip -r ../hello-arm64.txplugin manifest.json payload
```

Plugin lớn nên dùng công cụ hỗ trợ Zip64. Các mục vốn đã là tệp nén như `.zip`, `.tar.gz`, `.deb` nên dùng chế độ ZIP Store/NoCompression để tránh nén lại mà không có lợi.

### Cập nhật nhanh tập lệnh trong gói

`.txplugin` là ZIP. Khi chỉ đổi `manifest.json` hoặc tập lệnh, có thể sao chép gói cũ rồi cập nhật vài mục ZIP; không cần giải nén rồi nén lại toàn bộ tệp nén lớn. Chỉ khi thay tệp nén hoặc đổi định dạng nén thì mới cần xử lý tệp lớn tương ứng.

### Hành vi phiên bản

- Nếu `id + version` đã được nhập, ứng dụng sẽ thông báo ngay, không giải nén hay cài đặt lại.
- Các phiên bản khác nhau của cùng `id` hiện cùng tồn tại trong thư mục riêng của ứng dụng, không xóa bản cũ.
- Lựa chọn phiên bản hiện sắp xếp theo chuỗi tên thư mục phiên bản, không so sánh SemVer đầy đủ. Trước khi sửa cơ chế này, hãy giữ độ rộng của các đoạn phiên bản có thể so sánh được, tránh dùng đồng thời các phiên bản có thứ tự chuỗi khác nhau như `1.0.9` và `1.0.10`.
- Sau khi chọn phiên bản mới và xác nhận, giao diện hiện sẽ nhập và cài đặt liên tục, không cần bấm "Cài đặt" lần thứ hai.

## 10. Plugin Web/Service

Dịch vụ web nên khai báo rõ cổng và đường dẫn:

```json
{
  "launchType": "web",
  "servicePort": 8787,
  "servicePath": "/",
  "permissions": ["LOCAL_WEB"],
  "launchCommand": "dashboard --host 127.0.0.1 --port 8787"
}
```

Bộ kiểm tra hiện chỉ kiểm tra cổng khi có `servicePort`, chưa bắt buộc mọi manifest `web/service` phải có cổng. Người viết plugin vẫn nên khai báo cổng; trừ khi thực sự cần truy cập LAN, dịch vụ ưu tiên lắng nghe `127.0.0.1`.

## 11. Xác minh, giao dịch và gỡ cài đặt

`verifyCommand` nên trả về 0 và thực sự chạy tệp nhị phân cốt lõi. Để tương thích plugin cũ, trình cài đặt hiện có thể giữ trạng thái thành công ngay cả khi xác minh thất bại nhưng phát hiện bất kỳ điểm vào lệnh nào thực thi được; plugin không được dựa vào hành vi dự phòng này.

Giao dịch cài đặt chỉ chụp trạng thái và khôi phục:

```text
/opt/tianxuan/tools/<id>
```

Các thư mục toàn cục mà plugin ghi như `/opt/android-sdk`, `/opt/flutter`, `/root/.gradle` hay nơi khác không nằm trong bản chụp trạng thái giao dịch của bộ khung. Vì vậy:

- Tài nguyên toàn cục phải tự dùng vùng tạm, kiểm tra và chuyển vào vị trí cuối cùng.
- Khi thất bại, tập lệnh nên dọn vùng tạm qua `trap`.
- Nhật ký hiện `ROLLED_BACK` không có nghĩa là mọi tệp toàn cục mà plugin tạo đều đã được khôi phục.
- `uninstallSteps` phải xóa tệp chương trình toàn cục thuộc về plugin, nhưng mặc định giữ lại `$TIANXUAN_TOOL_DATA`.

## 12. Kiểm tra trước khi phát hành

```text
[ ] Thư mục gốc ZIP chỉ có manifest.json và payload/
[ ] manifest.json nhỏ hơn 1 MiB, tổng dung lượng giải nén nhỏ hơn 8 GiB
[ ] Các trường id, version, ARM64, launchType, nguồn và quyền đều đúng
[ ] Mọi tệp lớn đều có SHA-256 cố định
[ ] Không phụ thuộc lệnh tùy chọn chưa khai báo như unzip, xz, file, readelf
[ ] Xử lý đúng ELF, liên kết tượng trưng và bit thực thi trong ZIP
[ ] Có thể nhập, cài đặt, xác minh và khởi chạy khi mất mạng
[ ] Đã thử cài mới, nhập lại cùng phiên bản, nâng cấp phiên bản mới và gỡ cài đặt
[ ] verifyCommand, liên kết lệnh và dịch vụ PTY/Web đều hoạt động
[ ] Tiến độ có cấu trúc tăng đơn điệu, nhật ký không chứa khóa hoặc biến môi trường nhạy cảm
[ ] Khi thất bại có thể dọn vùng tạm, không báo nhầm đã hoàn tác toàn bộ thư mục toàn cục
[ ] Đã tính dung lượng cho bản sao plugin, bản sao sandbox, vùng tạm và thư mục cuối cùng
```

## 13. Lỗi thường gặp

### Sau khi nhập, danh sách không có plugin

Kiểm tra thư mục gốc ZIP, JSON, `id`, `architectures` và giới hạn kích thước gói.

### Không nhập lại được cùng một gói

`id + version` trùng sẽ bị từ chối. Sau khi sửa nội dung phải tăng `version`.

### `cannot create .../bin/...`

Đảm bảo tạo `$TIANXUAN_TOOL_DIR/bin` trước khi cài. Trình cài đặt dùng chung hiện cũng tạo sẵn thư mục này.

### `xz: Cannot exec`

RootFS không có `xz`. Đổi sang `.tar.gz`, hoặc đóng kèm trình giải nén ARM64 chạy được.

### `unexpected operator`

Thường là `test` của `/bin/sh` nhận nhiều đối số sau khi ký tự đại diện được mở rộng. Dùng `find ... -print -quit` để lấy một đường dẫn duy nhất rồi đặt trong dấu ngoặc kép.

### Chương trình ARM64 vẫn không chạy được

Ngoài ELF `e_machine`, hãy kiểm tra trình thông dịch và ABI. Android ARM64/Bionic và Linux ARM64/glibc không tự động tương thích.

### Nhật ký hiện đã hoàn tác nhưng tệp toàn cục vẫn còn

Giao dịch của bộ khung chỉ bao phủ `$TIANXUAN_TOOL_DIR`. Các đường dẫn toàn cục thuộc plugin phải do chính tập lệnh plugin dọn dẹp.
