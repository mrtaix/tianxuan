# 🧱 CTHarness — Lựa chọn bộ thực thi nền cho sandbox (chroot / không gian tên so với PRoot)

> **Phạm vi**: Đánh giá việc dùng `chroot` (hoặc `unshare` + `pivot_root`) để thay thế / bổ sung bộ thực thi nền PRoot hiện tại cho sandbox.
> **Trạng thái**: **Đã quyết định — không triển khai**. PRoot vẫn là bộ thực thi nền duy nhất cho sandbox; phương án hiệu năng cao được xếp vào danh sách lựa chọn dài hạn, mặc định không lên lịch.
> **Ngày quyết định**: 2026-09-16
> **Phạm vi ảnh hưởng**: `runtime/proot`, `runtime/shell`, `runtime/privilege`, `runtime/pty`, `runtime/doctor`, `StorageManager`

---

## 1. 🎯 Bối cảnh và vấn đề

PRoot chặn từng lời gọi hệ thống và viết lại thông qua `ptrace`, gây chi phí khoảng 2–10 lần trên các tác vụ có nhiều lời gọi hệ thống. CTHarness lại đặt chính những tác vụ nặng nhất này vào trong sandbox:

- Bản dựng Android của Gradle / AGP (xem [`ANDROID_OFFLINE_PLUGIN.md`](ANDROID_OFFLINE_PLUGIN.md))
- Giải nén `dpkg` / `apt` (xem [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §6 Phòng thủ liên kết cứng)
- Cài đặt gói phụ thuộc `npm` / `pnpm`

`chroot` là phép đổi thư mục gốc do nhân hệ điều hành cung cấp trực tiếp, không có chi phí ptrace, vì vậy đặt ra câu hỏi: có nên chuyển sang chroot trên các thiết bị đã Root hay không.

---

## 2. ⚖️ Ba cấp chế độ thực thi và tính khả thi của chroot

Rào cản của chroot không phải là "có đặc quyền" nói chung, mà cụ thể là cần `CAP_SYS_CHROOT`; để gắn kết `/proc`, `/dev` và gắn kết liên kết thì còn cần `CAP_SYS_ADMIN`.

| `ExecutionMode` | Danh tính chạy | chroot được | Ghi chú |
| :--- | :--- | :--- | :--- |
| `PROOT` | UID ứng dụng (10xxx) | ❌ | Không có bất kỳ năng lực đặc quyền liên quan nào |
| `SHIZUKU` | UID shell 2000 | ❌ | **Cấp ADB không chứa `CAP_SYS_CHROOT` / `CAP_SYS_ADMIN`** |
| `ROOT` | UID 0 | ✅ | Chỉ cấp này khả thi |

**Kết luận then chốt**: Shizuku là trường hợp sử dụng chính cho năng lực đặc quyền của CTHarness (`applyPrivilegeOptimizations` của `PrivilegeManager`, gỡ tiến trình ma, tự cấp quyền `appops` đều nằm ở cấp này), nhưng nó **hoàn toàn không hưởng lợi** từ chroot. Phạm vi thụ hưởng chỉ giới hạn ở thiết bị đã Root.

### 2.1 Hiện trạng: chế độ ROOT không tham gia thực thi sandbox

`ExecutionMode.ROOT` hiện chỉ được các tệp sau tham chiếu:

```
runtime/privilege/PrivilegeManager.kt        ── Thực thi su phía chủ, thăm dò, chuyển sang phương án dự phòng
harness/ToolExecutor.kt                      ── Xác định chế độ ở phía công cụ
harness/prompt/PrivilegeSectionRenderer.kt   ── Kết xuất lời nhắc
feature/chat/ChatViewModel.kt                ── Trạng thái giao diện
```

`runtime/proot` và `runtime/shell` **hoàn toàn không tham chiếu đến nó**. Nghĩa là dù người dùng đã cấp Root, mọi thứ trong sandbox vẫn đi qua PRoot; root chỉ được dùng để thực thi các lệnh Android như `settings put` / `appops set` ở phía chủ.

Vì vậy "chuyển sang chroot" không phải là thêm một công tắc, mà là **xây dựng toàn bộ một chuỗi thực thi song song mới**.

---

## 3. ✅ Quyết định

1. **PRoot vẫn là bộ thực thi nền duy nhất cho sandbox**, không đưa chroot vào làm phương án thay thế hay đường mặc định. Việc dùng được mà không cần Root là định vị sản phẩm cốt lõi của CTHarness; bất kỳ đường nào lấy root làm tiền đề đều không được phép vào luồng chính.
2. **`chroot` thuần túy bị loại vĩnh viễn**. Trên Android nó bắt buộc phải đi kèm gắn kết liên kết trong không gian tên gắn kết toàn cục (xem §4.3), rủi ro tồn lưu không thể chấp nhận.
3. Nếu tương lai thực sự có nhu cầu hiệu năng cấp thiết, **hình thức duy nhất được chấp nhận** là bộ thực thi tùy chọn `unshare --mount --pid --fork` + `pivot_root` trong chế độ ROOT, mặc định tắt và tự động chuyển sang phương án dự phòng khi thất bại (xem §6).
4. Ưu tiên khai thác tối đa tiềm năng còn lại của PRoot (xem §7) thay vì mở bộ thực thi nền mới.

---

## 4. 🔒 Các ràng buộc nền tảng Android (xếp theo mức độ khó tăng dần)

### 4.1 SELinux — về cơ bản không phải rào cản

su của Magisk / KernelSU / APatch không chỉ đổi UID; chúng còn chèn bản vá vào chính sách SELinux, đưa tiến trình sinh ra bởi su vào một **miền được khai báo ở chế độ cho phép** (Magisk là `u:r:magisk:s0`). Trong miền này, vi phạm chính sách chỉ được ghi vào nhật ký kiểm toán chứ không bị từ chối thực tế.

Hơn nữa, các tệp trong `/data/data/<pkg>/` có kiểu `app_data_file`; việc thực thi chúng từ miền cho phép **không có quy tắc chuyển miền tương ứng**, nên toàn bộ cây tiến trình sandbox vẫn nằm trong miền cho phép. Chế độ root của Linux Deploy / AndroNix hoạt động ổn định lâu dài cũng dựa trên điều này.

**Điều kiện tiên quyết**: phải thực sự đi qua dịch vụ nền của su. Nếu tạo tiến trình con trong tiến trình ứng dụng rồi gọi trực tiếp `chroot`/`mount`, tiến trình vẫn ở miền `untrusted_app` và ở chế độ cưỡng chế, nên sẽ bị từ chối.

### 4.2 seccomp — kế thừa và không thể hủy bỏ

Android cài đặt bộ lọc seccomp của zygote cho tiến trình ứng dụng; `fork` / `execve` đều kế thừa và nhân hệ điều hành không cho phép gỡ bỏ. Các lời gọi hệ thống đặc quyền thuộc nhóm gắn kết không nằm trong danh sách cho phép của ứng dụng, nên **dù có root, tiến trình con sinh ra trực tiếp từ tiến trình ứng dụng vẫn không gọi được**.

Chỉ có một cách vòng qua: su của Magisk dùng kiến trúc máy khách–dịch vụ nền — máy khách `su` kết nối magiskd, magiskd tạo tiến trình đặc quyền, rồi nhận đầu vào/đầu ra/đầu ra lỗi chuẩn của máy khách qua `SCM_RIGHTS`. Vì vậy tiến trình thực sự làm việc là **tiến trình con của magiskd, không mang bộ lọc seccomp của ứng dụng**.

**Hệ quả**:

- `HostProcessRunner` hiện tại (`ProcessBuilder("su", "-c", command)`) đúng là phương tiện duy nhất chuẩn xác.
- `runtime/pty/NativePtySession` hiện gọi `forkpty` trong tiến trình ứng dụng rồi trực tiếp thực thi PRoot; con đường này **không thể đổi sang chroot trực tiếp**; phải để tiến trình con của forkpty thực thi `su`, truyền đầu phụ PTY làm các luồng vào/ra chuẩn. Terminal được giữ lại, nhưng luồng PTY phải nối lại.

### 4.3 Quyền sở hữu không gian tên gắn kết — cái bẫy tinh vi nhất

Mỗi tiến trình ứng dụng trên Android có không gian tên gắn kết riêng (khi zygote tạo tiến trình con, nó dùng `unshare(CLONE_NEWNS)` để làm sandbox lưu trữ riêng cho từng ứng dụng). magiskd nằm trong một không gian tên khác.

Do đó những thứ được gắn kết bởi `su -c "mount --bind ..."` **tiến trình của chính ứng dụng không thấy được**, và ngược lại. Magisk cung cấp `su -mm` (`--mount-master`) để vào không gian tên toàn cục, nhưng điều đó có nghĩa các gắn kết liên kết **nhìn thấy được bởi mọi tiến trình trên toàn máy**:

> Gắn kết liên kết `/data/data/top.wkbin.tianxuan/.../rootfs` còn tồn lưu sẽ được gắn trên không gian tên toàn cục. Sau khi gỡ cài đặt ứng dụng, nó vẫn còn; người dùng chỉ có thể **khởi động lại điện thoại** để xóa.

Điều này phóng đại vấn đề tồn lưu gắn kết ở §5.1 lên một bậc, xung đột trực tiếp với cam kết ranh giới dọn dẹp trong [`STORAGE_MANAGEMENT.md`](STORAGE_MANAGEMENT.md).

### 4.4 Đứt gãy quan hệ cha-con của tiến trình — xuyên thủng hoàn toàn cơ chế quản lý tiến trình hiện tại

`ProcessRegistryImpl.stop()` / `stopAll()` đi qua `session.close()` → `Process.destroy()`, kết hợp với `--kill-on-exit` của PRoot, hoàn toàn dựa vào quan hệ cha-con của Java `Process`.

Sau khi đi qua su, `Process` trong tay chỉ là máy khách su, còn tiến trình cha thực sự của cây tiến trình sandbox là magiskd:

- Máy khách bị giết, cây tiến trình đặc quyền có thể sống sót thành tiến trình mồ côi, tiếp tục giữ điểm gắn kết và khóa tệp.
- `destroy()` / `destroyForcibly()` của `HostProcessRunner.stopProcess()` không có tác động với nó.
- Phải tự triển khai ghi chép PID/PGID + dọn dẹp qua kênh riêng `su -c "kill -TERM -<pgid>"`, đồng thời phải đối mặt với trường hợp ứng dụng bị LMK giết trực tiếp, không có chút cơ hội chạy dọn dẹp.

### 4.5 Điều kiện triển khai `pivot_root`

`pivot_root` yêu cầu bản thân thư mục gốc mới phải là một điểm gắn kết, trong khi `/data/data/.../rootfs` chỉ là thư mục thường. Phải `mount --bind rootfs rootfs` trước để biến nó thành điểm gắn kết.

### 4.6 nosuid trên `/data`

Android áp dụng nosuid cho `/data`, nên bit setuid trong chroot (như `sudo`) không có tác dụng. Ảnh hưởng thực tế có hạn (đã là UID 0), nhưng các gói trong bản phân phối phụ thuộc setuid sẽ có hành vi khác. Muốn đổi thì cần `mount -o remount,suid /data`, là thay đổi cấp chủ ảnh hưởng toàn máy nên **bị loại**.

---

## 5. 💸 Danh sách chi phí kỹ thuật (tác động lên mã đã chạy ổn định)

### 5.1 Gắn kết từ không trạng thái sang có trạng thái

`-b` của `ProotCommandBuilder` là phép dịch đường dẫn thuần trong không gian người dùng, tiến trình chết là không còn dư vết. Với chroot, những mục này phải là `mount --bind` thật và phải gỡ từng mục:

```
-b /dev  -b /proc  -b /sys
-b <tmpDir>:/tmp            -b <workspaceDir>:/workspace
-b <homeDir>:/root          -b <optDir>:/opt/tianxuan
-b <attachmentsDir>:/attachments
-b <rootfs>/.l2s:<rootfs>/.l2s
ProotMountLayout.hostSystemPaths (một nhóm đường dẫn hệ thống Android)
addStorageMountBindings(...)  (bộ nhớ dùng chung được người dùng cấp quyền)
```

Bất kỳ lần thoát bất thường nào (Android giết tiến trình, luồng gỡ cài đặt bị ngắt) cũng để lại điểm gắn kết ở phía chủ; phía người dùng thấy "dọn không sạch bộ nhớ, gỡ ứng dụng không sạch, bộ nhớ dùng chung bị chiếm". Sáu loại dọn dẹp không gian của `StorageManager` phải làm lại toàn bộ.

### 5.2 Từ root giả sang root thật, ngữ nghĩa quyền sở hữu tệp bị đảo ngược

Hiện tại `--change-id=0:0` là giả; `chown` trong môi trường khách rốt cuộc trên phía chủ vẫn là UID ứng dụng, nên `WorkspaceFileService`, `AndroidFtpServer`, `SshServiceManager` đều đọc/ghi không gian làm việc bình thường. Với chroot, UID 0 là thật; sau khi `chown root:root` trong môi trường khách, chính ứng dụng (UID 10xxx) có thể lại không đọc được tệp trong không gian làm việc của mình.

### 5.3 Một loạt logic tự phục hồi tách làm hai nhánh

Các biện pháp phòng thủ riêng cho PRoot trong [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md) §6 đều không cần thiết với ext4 thật + root thật:

| Cơ chế hiện tại | Với chroot |
| :--- | :--- |
| `--link2symlink` + vùng lưu trữ nền `.l2s` | Không cần (liên kết cứng gốc) |
| Tự phục hồi liên kết cứng ma `/usr/local/sbin/tianxuan-fix-perl` | Không cần |
| `dpkg.cfg.d/01_tianxuan_nodoc` | Chỉ còn ý nghĩa tiết kiệm I/O |
| Git `safe.directory = *` | Ngữ nghĩa thay đổi (UID thật đồng nhất) |
| Giả lập `--kernel-release` | Không dùng được (nhân hệ điều hành là phiên bản thật) |

Đây không phải là "ít việc hơn", mà là `EnvironmentDoctor`, `EnvironmentRepairer`, `RuntimeHealthChecker` đều phải tách hai đường phán đoán, phạm vi kiểm thử tăng gấp đôi.

### 5.4 chroot không cung cấp cách ly

chroot chỉ đổi thư mục gốc, không phải container; cách thoát qua bộ mô tả tệp kinh điển luôn có hiệu quả với tiến trình root. **Nó chỉ được coi là tối ưu hiệu năng, không phải ranh giới bảo mật.** Nếu mục tiêu là tăng cường cách ly thì đây là hướng đi ngược lại.

### 5.5 Phóng đại rủi ro và xung đột định vị sản phẩm (lý do mang tính quyết định)

Bên trong chroot thực thi là **lệnh do Agent AI tự động tạo ra** (`HarnessLoop` → `ToolExecutor` → sandbox).

- Với PRoot, hậu quả tồi tệ nhất được sandbox Android chặn lại, giới hạn trong thư mục riêng của ứng dụng + bộ nhớ dùng chung được người dùng cấp quyền rõ ràng.
- Với chroot root thật, chỉ một lệnh `rm -rf` hay `dd` sai cũng có thể biến máy thành cục gạch.

Với định vị sản phẩm "Agent tự thực thi trong vòng lặp", hệ số phóng đại rủi ro này không thể chấp nhận. Đây là **lý do hàng đầu** khiến ADR này loại chroot; lợi ích hiệu năng không đủ để bù đắp.

---

## 6. 🧪 Nếu triển khai trong tương lai: hình thức tối thiểu duy nhất được chấp nhận

Ba ràng buộc cứng, thiếu một thì không làm:

1. **Dùng `unshare --mount --pid --fork` + `pivot_root`, không dùng chroot thuần.**
   - Không gian tên gắn kết được nhân hệ điều hành tự thu hồi khi cây tiến trình kết thúc, đồng thời giải quyết ô nhiễm toàn cục ở §4.3 và tồn lưu gắn kết ở §5.1.
   - Không gian tên PID khiến `ps` / `kill` trong môi trường khách không chạm tới tiến trình phía chủ (với chroot thuần, `/proc` được gắn kết liên kết vào sẽ để môi trường khách thấy và giết được tiến trình toàn máy).

2. **Tách giao diện `SandboxLauncher`, với hai cách triển khai song song.**
   ```
   runtime/sandbox/SandboxLauncher.kt          (giao diện)
   runtime/proot/ProotLauncher.kt              (tập trung ProotCommandBuilder hiện có vào đây)
   runtime/sandbox/NamespaceLauncher.kt        (unshare + pivot_root)
   ```
   **Nghiêm cấm** nhét `if (mode == ROOT)` vào trong `ProotCommandBuilder`. Tham chiếu ranh giới đỏ về trách nhiệm đơn nhất trong [`ARCHITECTURE_RULES.md`](ARCHITECTURE_RULES.md).

3. **Mặc định tắt + tự động chuyển sang phương án dự phòng.** Có công tắc thử nghiệm rõ ràng trên trang nhà phát triển; bất kỳ khâu nào thất bại cũng dùng lại ngữ nghĩa `PrivilegeAvailability.DEGRADED` có sẵn của `PrivilegeManager` để về PRoot.

Bổ sung kèm theo: ghi chép PID/PGID và dọn dẹp qua kênh riêng (§4.4), nối lại luồng PTY qua su (§4.2), cải tạo vòng đời `ProcessRegistry`, viết lại ranh giới dọn dẹp của `StorageManager`.

---

## 7. 🔧 Các hành động chuẩn bị có thể làm ngay (không phụ thuộc quyết định triển khai của ADR này)

### 7.1 Kiểm chứng tăng tốc seccomp của PRoot có hiệu lực hay không

PRoot bản Termux mặc định bật tăng tốc seccomp (chỉ chặn các lời gọi hệ thống cần dịch), có thể đã giảm một phần đáng kể chi phí lý thuyết. Chạy thử một lần bản dựng Gradle trong sandbox với `PROOT_NO_SECCOMP=1` làm đối chứng để định lượng chênh lệch thực tế. **Trước khi có con số này, mọi ước tính lợi ích của bộ thực thi nền mới chỉ là phỏng đoán.**

### 7.2 Kiểm toán số lượng gắn kết `-b`

`addHostSystemBindings()` duyệt `ProotMountLayout.hostSystemPaths` và gắn kết từng mục; số lượng gắn kết ảnh hưởng trực tiếp đến chi phí dịch đường dẫn của PRoot. Cần rà soát xem có mục nào có thể cắt bỏ.

### 7.3 Sửa ngữ nghĩa dò tính hợp lệ của `checkRootPrivilege()`

`PrivilegeManager.checkRootPrivilege()` hiện chỉ kiểm tra `su -c id` có trả `uid=0` hay không. Nhưng theo §4 thì **"root dùng được" ≠ "không gian tên/chroot dùng được"**:

- Người dùng có thể dùng root thuần chưa được chèn bản vá miền cho phép;
- Có thể đã đưa CTHarness vào danh sách loại trừ (umount) trong KernelSU / APatch;
- Nhân hệ điều hành có thể đã cắt bỏ hỗ trợ không gian tên liên quan.

Nếu tương lai triển khai §6, phép dò phải thực sự chạy thử một hành động tối thiểu (như `su -c "unshare -m true"`) thay vì suy diễn. **Dù không triển khai, câu chữ hiện tại trong `Authorized` là "đã giải phóng năng lực tăng tốc phần cứng Linux và nhân hệ điều hành gốc" cũng vượt quá năng lực thực tế, nên hiệu chỉnh lại.**

---

## 8. 📜 Biên bản quyết định (tóm tắt ADR)

- **ADR-S001**: PRoot vẫn là bộ thực thi nền duy nhất cho sandbox. Lý do: dùng được không cần Root là định vị sản phẩm cốt lõi; chroot chỉ có lợi trên thiết bị đã Root và cấp Shizuku hoàn toàn không thụ hưởng được.
- **ADR-S002**: `chroot` thuần bị loại vĩnh viễn. Lý do: trên Android bắt buộc đi kèm gắn kết liên kết trong không gian tên gắn kết toàn cục, tồn lưu chỉ xóa được bằng cách khởi động lại điện thoại, xung đột cam kết dọn dẹp bộ nhớ.
- **ADR-S003**: Lý do hàng đầu để loại là **phóng đại rủi ro**, không phải chi phí kỹ thuật. Bên trong sandbox thực thi lệnh do AI tự động tạo; với root thật, một thao tác sai có thể biến máy thành cục gạch; ranh giới sandbox Android của PRoot là lớp bảo vệ cần thiết cho dạng sản phẩm này.
- **ADR-S004**: SELinux **không phải** rào cản chính của vấn đề này (miền cho phép của su đã giải quyết). Các rào cản thật theo thứ tự là: đối tượng kế thừa seccomp, quyền sở hữu không gian tên gắn kết, đứt gãy quan hệ cha-con của tiến trình.
- **ADR-S005**: Nếu triển khai trong tương lai, hình thức cố định là `unshare --mount --pid` + `pivot_root`, triển khai song song qua giao diện `SandboxLauncher`, mặc định tắt và tự chuyển sang phương án dự phòng khi thất bại.
- **ADR-S006**: Ưu tiên khai thác tiềm năng còn lại của PRoot (kiểm chứng tăng tốc seccomp, cắt bỏ gắn kết `-b`); không lên lịch bộ thực thi nền mới trước khi có số liệu định lượng.

---

## 9. 🔗 Tài liệu liên quan

- Kiến trúc và cấu trúc module: [`ARCHITECTURE.md`](ARCHITECTURE.md)
- Nguyên tắc cứng về kiến trúc: [`ARCHITECTURE_RULES.md`](ARCHITECTURE_RULES.md)
- Vấn đề đã biết (giới hạn PRoot và phòng thủ liên kết cứng): [`KNOWN_ISSUES.md`](KNOWN_ISSUES.md)
- Phân loại bộ nhớ và ranh giới dọn dẹp: [`STORAGE_MANAGEMENT.md`](STORAGE_MANAGEMENT.md)
- Trình tự thực thi cốt lõi: [`EXECUTION_TRACES.md`](EXECUTION_TRACES.md)
- Điểm triển khai then chốt:
  - `runtime/proot/ProotCommandBuilder.kt` — xây dựng tham số PRoot
  - `runtime/proot/ProotMountLayout.kt` — danh sách gắn kết đường dẫn hệ thống phía chủ
  - `runtime/shell/ProcessRegistry.kt` — quản lý tiến trình và vòng đời
  - `runtime/privilege/PrivilegeManager.kt` — dò ba cấp chế độ và chuyển sang phương án dự phòng
  - `runtime/privilege/HostProcessRunner.kt` — bộ thực thi su / Shizuku phía chủ
  - `runtime/pty/NativePtySession.kt` — bộ thực thi nền terminal forkpty
  - `core/model/ExecutionMode.kt` — định nghĩa chế độ
