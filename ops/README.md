# CTHarness · Bàn giao hạ tầng

Tài liệu này ghi lại việc lưu trữ mã nguồn, quy trình phát hành và phân phối tài nguyên ngoại tuyến của CTHarness.
**Ràng buộc hàng đầu: không làm ảnh hưởng đến bất kỳ dịch vụ hiện có nào trên máy chủ.**

---

## 1. GitHub

| Mục | Giá trị |
|---|---|
| Kho mã | `xiaozhe7772222/tianxuan` |
| Chế độ hiển thị | PRIVATE |
| Nhánh mặc định | `main` |

Sao chép kho mã:

```bash
git clone https://github.com/xiaozhe7772222/tianxuan.git
```

### Nguồn cập nhật là một mục cấu hình, không được mã hóa cứng

Tọa độ kho mã dùng cho chức năng «Kiểm tra cập nhật» và trang «Giới thiệu» trong ứng dụng được đọc từ `app/src/main/assets/update_source.properties`:

```properties
update.repo=xiaozhe7772222/tianxuan
update.baseUrl=https://124.222.37.253
update.manifestPrefix=/tx-update/
update.manifestUrl=https://124.222.37.253/tx-update/latest
update.fallbackUrls=
update.versionsUrl=https://124.222.37.253/tx-update/versions
update.repoUrl=https://github.com/xiaozhe7772222/tianxuan
```

Chỉ cần sửa các dòng này để đổi nguồn phát hành, không cần sửa mã Kotlin.
Logic đọc nằm trong `core/network/.../UpdateSourceConfig.kt` (76 dòng),
logic phân tích manifest và so sánh phiên bản nằm trong `UpdateManifestParser.kt` cùng gói (88 dòng, được kiểm thử bằng 18 ca kiểm thử đơn vị).
Khi một mục bị thiếu hoặc sai định dạng, mục đó sẽ dùng giá trị dự phòng từ các hằng `DEFAULT_*` của `UpdateSourceConfig`.

> Ngoại lệ: `feature/custom_iteration` cố ý không phụ thuộc vào `core:network` (để không vi phạm danh sách cho phép về phụ thuộc kiến trúc),
> nên `TIANXUAN_OFFICIAL_REPO` của mô-đun này là hằng cục bộ. Khi đổi kho mã, cần đồng bộ cả hai nơi.

> **Vì sao nguồn cập nhật là API tự triển khai thay vì GitHub Releases API**:
> Kho mã ở chế độ PRIVATE; yêu cầu tới Releases API không có xác thực sẽ trả về 404 (đã kiểm chứng),
> trong khi APK cần được tải xuống công khai. Vì vậy, máy chủ riêng triển khai một dịch vụ manifest
> có cấu trúc hoàn toàn tương thích với GitHub Releases (cùng tên trường: `tag_name`/`name`/`body`/`html_url`/
> `published_at`/`assets[].name`/`assets[].size`/`assets[].browser_download_url`),
> giúp logic phân tích phía máy khách không cần xử lý riêng cho nguồn tự triển khai. GitHub API vẫn được giữ ở cuối danh sách nguồn dự phòng.

---

## 2. Quy trình phát hành

```bash
./release.sh <版本号> [--apk] [--publish]
```

| Tham số | Tác dụng |
|---|---|
| Không có | Biên dịch + gắn thẻ + đẩy mã + tạo bản phát hành |
| `--apk` | Biên dịch thêm APK và đính kèm vào bản phát hành |
| `--publish` | Đồng bộ thêm lên máy chủ phân phối |

Quy trình cố định: **Kiểm tra môi trường → `architectureCheck` + `AstronomyTest` → Biên dịch → Tạo commit và gắn thẻ →
Đẩy mã → Tạo bản phát hành → (Tùy chọn) Đồng bộ máy chủ**.

Nếu một trong hai bước đầu thất bại, quá trình phát hành sẽ dừng ngay; không được tạo gói khi vẫn còn lỗi.

Chuẩn bị môi trường:

```bash
source ./setup-env.sh   # Daemon JDK 25 / SDK / bộ công cụ JDK 17
```

### Cơ chế siết chặt dần giới hạn kiến trúc

`architecture-policy.json` đặt `global.maxFileLines=400`, với đường cơ sở trong `.architecture-baseline.json`.
**Đường cơ sở chỉ được hạ xuống, không được nâng lên.** Nếu một thay đổi khiến tệp vượt giới hạn, hãy chia nhỏ tệp trước, thay vì nâng đường cơ sở.

---

## 3. Máy chủ Tencent Cloud (124.222.37.253)

### Các dịch vụ hiện có (không được can thiệp)

`fusion-gateway` (cổng new-api), `logview`, `st-auth`, `st-rotator`, `nginx` — tất cả đều do systemd quản lý.

Cần kiểm tra trước và sau mọi thao tác:

```bash
for s in fusion-gateway logview st-rotator st-auth nginx; do
  printf '%-16s %s\n' "$s" "$(systemctl is-active $s)"
done
```

### Các đường dẫn bổ sung (tách biệt với thư mục hiện có)

| Đường dẫn | Mục đích |
|---|---|
| `/opt/tianxuan-offline` | Lưu 6.44 GiB gói plugin ngoại tuyến trên đĩa |
| `/opt/tianxuan-dist` | Thư mục phân phối công khai, chứa `0.20.0/`, `plugins/` |
| `/usr/local/share/tianxuan/tianxuan_dist.py` | Dịch vụ cập nhật/phân phối tự triển khai |
| `/usr/local/share/tianxuan/apkmanifest.py` | Phân tích AXML chỉ bằng thư viện chuẩn (đọc phiên bản/tên gói APK) |
| `/usr/local/bin/tianxuan-fetch.sh` | Trình tải gói ngoại tuyến |
| `/usr/local/bin/sync-tianxuan-plugins.sh` | Tạo liên kết mềm cho các gói đã tải xong vào `dist/plugins` |
| `/usr/local/bin/gen-tianxuan-manifest.sh` | Quét dist để tạo `manifest.json` |
| `/etc/systemd/system/tianxuan-fetch.service` | Đơn vị dịch vụ tải xuống |
| `/etc/systemd/system/tianxuan-dist.service` | Đơn vị dịch vụ phân phối |
| `/var/log/tianxuan-fetch.log` | Nhật ký tải xuống |

### Thay đổi nginx (thay đổi duy nhất đối với cấu hình hiện có)

Chỉ **thêm một location** vào `/etc/nginx/sites-enabled/fusion-gateway`,
không sửa bất kỳ location hiện có nào:

```nginx
location ^~ /tx-update/ {
    # Dấu / ở cuối để nginx bỏ tiền tố /tx-update
    proxy_pass http://127.0.0.1:8443/;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_buffering off;          # Truyền thẳng tệp lớn, không lưu vào bộ đệm nginx
    proxy_request_buffering off;
    proxy_cache off;
    proxy_read_timeout 600s;
    proxy_send_timeout 600s;
    proxy_set_header Host $host;
    proxy_set_header Range $http_range;      # Chuyển tiếp Range, cần thiết để tiếp tục tải dở
    proxy_set_header If-Range $http_if_range;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    client_max_body_size 1m;
}
```

Cổng 8443 không được mở trong nhóm bảo mật của Tencent Cloud, chỉ lắng nghe trên loopback và không được công khai.
Các bản sao lưu đều đặt trong `/root/nginx-backups/` (**không được đặt trong `sites-enabled/`**,
nếu không nginx sẽ quét chúng và báo `duplicate default server`).

### Biện pháp tránh ảnh hưởng đến dịch vụ hiện có

Cấu hình cách ly của `tianxuan-fetch.service`:

```ini
Nice=19
IOSchedulingClass=idle
CPUWeight=10
IOWeight=10
MemoryMax=256M
```

`tianxuan-dist.service` bổ sung `ProtectSystem=strict` + `NoNewPrivileges=yes`
+ `ReadWritePaths` để giới hạn quyền ghi chỉ trong thư mục nhật ký.

Đồng thời, script giới hạn tốc độ bằng `wget --limit-rate=8M`. Kết quả kiểm chứng trong suốt quá trình tải và phân phối toàn bộ 6.44 GiB:
`fusion-gateway`/`logview`/`st-rotator`/`st-auth`/`nginx` vẫn ở trạng thái active,
tải hệ thống cao nhất là 0.08, bộ nhớ khả dụng là 2912M.

### Lệnh vận hành

```bash
systemctl status  tianxuan-fetch tianxuan-dist
systemctl restart tianxuan-fetch     # Tiếp tục tải dở, có thể chạy lại an toàn
sync-tianxuan-plugins# Bổ sung liên kết mềm, có tính lũy đẳng
gen-tianxuan-manifest                # Làm mới manifest.json, có tính lũy đẳng
python3 /usr/local/share/tianxuan/apkmanifest_test.py   # Tự kiểm thử trình phân tích AXML (7 ca)
python3 /usr/local/share/tianxuan/gen_manifest_test.py  # Tự kiểm thử tạo manifest (6 nhóm)
tail -f /var/log/tianxuan-fetch.log
grep -E 'DONE|FAIL|ALL DONE' /var/log/tianxuan-fetch.log
journalctl -u tianxuan-dist -f
```

Trước khi phát hành còn có **bài tự kiểm thử bộ kiểm soát độ sạch của sản phẩm**, chạy trong kho mã (cần Android SDK):

```bash
python3 ops/release_guard_test.py
# release_guard_test: OK（干净包放行、manifest 含组件的包被拦、
#                      dex 含类的包被拦、release.sh 守卫模式逐分支核对通过）
```

Bài kiểm thử bao phủ ba việc: ① Dùng `aapt2 link` để thực sự biên dịch một APK chứa thành phần LeakCanary,
xác nhận bộ kiểm soát của release.sh chặn gói đó; ② Gói sạch không chứa thành phần phải được cho qua (bộ kiểm soát «chặn tất cả»
chẳng khác gì không có bộ kiểm soát); ③ **Trích đúng biểu thức thực sự được grep thực thi** từ `release.sh`,
kiểm tra từng nhánh — các dòng chú thích được loại bỏ trước, nên cả ba kiểu sai lệch «gõ sai một ký tự trong biểu thức», «xóa một nhánh»
và «biến cả dòng thành chú thích» đều khiến bài kiểm thử thất bại. Khi không có SDK, bài kiểm thử báo `SKIP` và thoát với mã 0,
không báo lỗi giả.

### Đọc số phiên bản APK (apkmanifest.py)

Máy chủ không có aapt, cũng không nên cài toàn bộ Android SDK chỉ để đọc một số phiên bản,
nên dùng `struct` + `zipfile` để phân tích trực tiếp AXML nhị phân. Ba lỗi đã từng gặp:

1. **Cờ UTF-8 nằm ở bit 8 (0x100) của flags**. Manifest do aapt2 tạo thường dùng
   UTF-16 (`flags=0x0`); giải mã theo UTF-8 sẽ làm lệch toàn bộ bảng chuỗi.
2. **Phải bỏ qua toàn bộ từng chunk theo `size` trong header**, không được chỉ bỏ qua `headerSize`.
   Giữa vùng chứa chuỗi và phần tử đầu tiên còn có `RES_XML_RESOURCE_MAP_TYPE(0x0180)`.
3. **`attributeStart` tính từ đầu attrExt (tức `off + headerSize`)**,
   không phải từ cuối attrExt — cộng thêm độ dài attrExt sẽ bỏ qua thuộc tính đầu tiên,
   dẫn đến việc không đọc được `versionCode` trong khi `versionName` vẫn bình thường.

Tên thuộc tính trong vùng chứa chuỗi là **tên thuần** (`versionCode`); thông tin không gian tên được lưu ở trường
`ns` của mục thuộc tính, nên so sánh theo tên ngắn là đúng, không cần loại bỏ tiền tố.

Số phiên bản và tên gói luôn phải căn cứ vào nội dung thực tế của APK, **không suy ngược từ tên thư mục** —
trước đây từng dùng `số phiên bản×10` để tạo giá trị giả như 200 cho `versionCode`, không khớp với giá trị thực 29 trong APK.
Nếu không đọc được thì để 0, không tự suy đoán.

```bash
python3 /usr/local/share/tianxuan/apkmanifest.py <apk>
# package=top.wkbin.tianxuan.debug versionName=0.20.0-debug versionCode=29
```

Kết quả đầu ra khớp từng trường với `aapt2 dump badging` (tệp không phải APK / bị cắt cụt / bị hỏng đều trả về giá trị rỗng, không phát sinh ngoại lệ).

---

## 4. Dịch vụ cập nhật tự triển khai (các điểm cuối công khai)

Địa chỉ gốc là `https://124.222.37.253/tx-update`, với cấu trúc trường tương thích GitHub Releases.

| Điểm cuối | Mục đích |
|---|---|
| `GET /tx-update/healthz` | Kiểm tra dịch vụ còn hoạt động |
| `GET /tx-update/latest` | Manifest phiên bản mới nhất (có `assets[]`, cùng cấu trúc với GitHub) |
| `GET /tx-update/versions` | Danh sách các phiên bản trước |
| `GET /tx-update/apk/<version>` | Tải APK, hỗ trợ Range |
| `GET /tx-update/plugins` | Danh sách plugin ngoại tuyến |
| `GET /tx-update/plugin/<name>` | Tải plugin, hỗ trợ Range |

Đã xác minh (kiểm tra hồi quy lại sau `nginx -s reload`, ngày 2026-10-07):

```
/healthz  → {"status":"ok","service":"tianxuan-dist"}
/latest   → tag_name=v0.20.0, versionCode=29,
            assets[0].browser_download_url = https://124.222.37.253/tx-update/apk/0.20.0
/apk/0.20.0 → 206, content-range: bytes 0-1023/51349736,
            content-type: application/vnd.android.package-archive
/plugins  → count=11, total_bytes=6911578380
/plugin/<11 个中文包名> → 全部 206 + 1024 字节
```

> `/latest` trả về **một đối tượng phiên bản duy nhất** (giống `/releases/latest` của GitHub),
> còn mảng `versions` nằm ở điểm cuối `/versions`.

Tên plugin bao gồm các ký tự đặc biệt như chữ Trung Quốc, `+`, `·`(U+00B7), `、`(U+3001).
Cả 11 URL đều tải được — cho thấy toàn bộ luồng mã hóa phần trăm và `Content-Disposition` hoạt động đúng.

**An toàn đường dẫn**: `_within_dist()` dùng `abspath` thay vì `realpath`
(vì `realpath` phân giải liên kết mềm ra ngoài `dist`, dẫn đến nhận định sai rằng đường dẫn vượt giới hạn),
từ chối rõ ràng đường dẫn tuyệt đối và `..`, đồng thời yêu cầu hậu tố `.txplugin`.
Kiểm thử hồi quy đã xác nhận cả ba loại đầu vào `..`, đường dẫn tuyệt đối và tệp không có hậu tố `.txplugin` đều bị chặn.

**Nhận biết tiền tố proxy**: Dịch vụ suy ra địa chỉ gốc công khai từ `X-Forwarded-Proto` / `X-Forwarded-Prefix`.
Đơn vị systemd đặt `TIANXUAN_BASE_PATH=/tx-update`,
`TIANXUAN_BASE_PROTO=https`, bảo đảm `browser_download_url` trả về là
địa chỉ https tuyệt đối có tiền tố, để máy khách sử dụng trực tiếp.

---

## 5. Gói plugin ngoại tuyến 6.44 GiB

**Trạng thái: đã tải xong 11/11 gói, `failures=0`, số byte khớp chính xác từng mục với danh sách QQ.**

11 gói `.txplugin`, tổng cộng `6911578380` byte (6.44 GiB), arm64.

| Gói | Số byte |
|---|---|
| Rust系统与Android-JNI交叉编译链-1.0.0 | 530441917 |
| 现代包管理器-yarn-1.0.0 | 49901704 |
| Android全栈开发套件-单体离线包-1.0.9 | 3009295789 |
| 代码检索四件套-rg、fd、fzf、bat-1.0.0 | 11138636 |
| Python3核心运行基座-1.0.0 | 17164034 |
| Node.js核心运行时-1.0.0 | 61283397 |
| C、C++与NDK原生构建链-1.0.0 | 637157113 |
| Flutter跨平台开发环境-1.0.0 | 1205967688 |
| AI科学计算与C扩展编译库-1.0.0 | 118207075 |
| Android逆向分析与代码审计-1.0.0 | 162991149 |
| Android核心基础环境-1.0.0 | 1108029878 |

Danh sách thông số: `ops/offline-packages.json` (chứa `name` / `size`, **đã được đưa vào kho mã**, tổng số byte là `total_bytes`).
Danh sách đầy đủ chứa liên kết tải trực tiếp **không được đưa vào kho mã** — các liên kết này chứa thông tin xác thực phiên `rkey` của QQ Flash Transfer và hết hạn sau 14 ngày.
Danh sách được loại trừ bằng `ops/.gitignore`; chỉ cần tạo lại theo «Quy trình lấy liên kết» ở phần tiếp theo.

### Kiểm tra tính toàn vẹn

Trình tải đối chiếu số byte từng gói; nếu có bất kỳ gói nào không khớp thì thử lại (tối đa 5 lượt, dùng `wget -c` để tiếp tục tải dở).
Có thể kiểm tra lại sau đó:

```bash
cd /opt/tianxuan-offline
while IFS=$'\t' read -r size name url; do
  printf '%-12s %s\n' "$(stat -c %s "$name" 2>/dev/null || echo MISSING)" "$name"
done < /tmp/tianxuan-manifest.tsv
```

### Nguồn và thời hạn của liên kết tải trực tiếp

Liên kết tải trực tiếp lấy từ trang chia sẻ QQ Flash Transfer `https://qfile.qq.com/q/x4LyndiNna`, xác thực bằng `rkey` trong URL.
**Liên kết chia sẻ này hết hạn sau 14 ngày**; khi hết hạn phải lấy lại liên kết. Các bước lấy lại nằm ở phần 3 của `天玄改造台账.md`.
Liên kết tải trực tiếp hỗ trợ `Range`, nên có thể tiếp tục tải sau khi bị gián đoạn.