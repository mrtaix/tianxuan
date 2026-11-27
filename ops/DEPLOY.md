# ops · Tra cứu nhanh về triển khai

Thư mục này chứa **mã nguồn của hạ tầng**. Toàn bộ hệ thống đang chạy trên Tencent Cloud `124.222.37.253`;
các tệp nguồn lưu tại đây cho phép tái tạo việc triển khai. Xem hướng dẫn đầy đủ trong `README.md`.

## Ánh xạ tệp với đường dẫn trên máy chủ

| Tệp trong thư mục này | Đường dẫn trên máy chủ |
|---|---|
| `tianxuan_dist.py` | `/usr/local/share/tianxuan/tianxuan_dist.py` |
| `apkmanifest.py` | `/usr/local/share/tianxuan/apkmanifest.py` |
| `apkmanifest_test.py` | `/usr/local/share/tianxuan/apkmanifest_test.py` |
| `gen-manifest.sh` | `/usr/local/bin/gen-tianxuan-manifest.sh` |
| `sync-tianxuan-plugins.sh` | `/usr/local/bin/sync-tianxuan-plugins.sh` |
| `tianxuan-fetch.sh` | `/usr/local/bin/tianxuan-fetch.sh` |
| `tianxuan-dist.service` | `/etc/systemd/system/tianxuan-dist.service` |
| `tianxuan-fetch.service` | `/etc/systemd/system/tianxuan-fetch.service` |

## Triển khai bằng một lệnh

```bash
SSHK=~/.ssh/tianxuan   # Hoặc ssh -i /root/tuzhe_key
D=124.222.37.253

for f in tianxuan_dist.py apkmanifest.py apkmanifest_test.py gen_manifest_test.py; do
  scp -i "$SSHK" "$f" root@$D:/usr/local/share/tianxuan/
done
for f in gen-manifest.sh sync-tianxuan-plugins.sh tianxuan-fetch.sh; do
  scp -i "$SSHK" "$f" root@$D:/usr/local/bin/
done
for f in tianxuan-dist.service tianxuan-fetch.service; do
  scp -i "$SSHK" "$f" root@$D:/etc/systemd/system/
done

ssh -i "$SSHK" root@$D '
  mv -f /usr/local/bin/gen-manifest.sh /usr/local/bin/gen-tianxuan-manifest.sh
  chmod +x /usr/local/bin/{gen-tianxuan-manifest,sync-tianxuan-plugins,tianxuan-fetch}.sh
  cd /usr/local/share/tianxuan
  python3 apkmanifest_test.py     # Tự kiểm thử trước: trình phân tích manifest
  python3 gen_manifest_test.py    # Tự kiểm thử tiếp: chọn bản latest và sắp xếp
  systemctl daemon-reload
  /usr/local/bin/sync-tianxuan-plugins                     # Bổ sung liên kết mềm
  /usr/local/bin/gen-tianxuan-manifest                     # Làm mới manifest
  systemctl restart tianxuan-dist
'
```

`gen_manifest_test.py` phải nằm cùng thư mục với `gen-manifest.sh` trên máy triển khai:
bài kiểm thử tìm script theo đường dẫn tương đối và lấy `apkmanifest.py` từ cùng thư mục.

## Triển khai lần đầu (máy trống)

```bash
mkdir -p /opt/tianxuan-offline /opt/tianxuan-dist /usr/local/share/tianxuan

# Không đưa danh sách liên kết tải trực tiếp vào kho mã (chứa thông tin xác thực phiên rkey của QQ Flash Transfer, hết hạn sau 14 ngày).
# Sau khi lấy lại liên kết theo «Quy trình lấy liên kết» trong README.md, tạo TSV trên máy chủ:
#   Các cột bắt buộc là: số byte <TAB> tên tệp <TAB> liên kết tải trực tiếp
python3 -c "import json;[print(f\"{p['size']}\t{p['name']}\t{p['url']}\") \
  for p in json.load(open('/root/offline-manifest.json'))]" \
  > /tmp/tianxuan-manifest.tsv

/usr/local/bin/tianxuan-fetch.sh                # Tiếp tục tải dở, có thể chạy lại nhiều lần
systemctl enable --now tianxuan-fetch.service

# nginx (thay đổi duy nhất đối với cấu hình hiện có: chỉ thêm một location)
# Xem mục «Thay đổi nginx» trong README.md; chạy nginx -t trước khi sửa, sửa sai sẽ gây lỗi 502
systemctl enable --now tianxuan-dist.service
```

## Những mục bắt buộc kiểm tra trước khi đưa vào vận hành

```bash
# 1. Không được ảnh hưởng đến dịch vụ hiện có
for s in fusion-gateway logview st-rotator st-auth nginx; do
  systemctl is-active "$s" || echo "!!! $s 异常"
done
curl -s -o /dev/null -w 'v1/models=%{http_code}\n' http://127.0.0.1/v1/models  # Kết quả phải là 401

# 2. Tự kiểm thử trình phân tích
python3 /usr/local/share/tianxuan/apkmanifest_test.py     # Kết quả phải là 7/7

# 3. Bốn điểm cuối công khai
curl -s https://124.222.37.253/tx-update/healthz
curl -s https://124.222.37.253/tx-update/latest | head -c 200
curl -s -o /dev/null -w '%{http_code}\n' -r 0-1023 \
  https://124.222.37.253/tx-update/apk/0.20.0          # Kết quả phải là 206
curl -s https://124.222.37.253/tx-update/plugins | head -c 120
```

## Vị trí sao lưu

- `/root/tianxuan-backup/` — APK cũ, manifest cũ, script cũ
- `/root/nginx-backups/` — Cấu hình nginx

**Tuyệt đối không đặt bản sao lưu nginx trong `/etc/nginx/sites-enabled/`** vì nginx sẽ quét chúng như tệp cấu hình
và báo `duplicate default server for 0.0.0.0:80`.