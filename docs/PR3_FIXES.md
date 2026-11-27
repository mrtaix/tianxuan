# Mô tả PR: combine nuốt mất cập nhật khiến UI không làm mới (4 chỗ cùng nguồn gốc) + lỗi 5xx từ thượng nguồn chưa được đưa vào retry tạm thời

> **Baseline**: `wkbin/tianxuan` @ `f82a44f7` (Release v0.16.0)
> **Thay đổi**: 3 tệp (2 sửa + 1 thêm test), +117 / −20 dòng
> **Cách đối chiếu**: mỗi mục đều dùng `grep` xác nhận mã hiện tại ở thượng nguồn vẫn còn lỗi, không suy diễn.

---

## 1. combine bỏ qua upstream khiến UI không làm mới (P0, 4 chỗ cùng nguồn gốc)

### Mẫu nguyên nhân gốc

```kotlin
combine(flowA, flowB, flowC, flowD) { a, _, _, _ -> a }   // 输出恒等于 a
    .distinctUntilChanged()                                // 去重
    .flatMapLatest { /* 重新投影 */ }
```

Lambda của `combine` thực chất **sẽ** chạy lại khi bất kỳ upstream nào phát ra giá trị (điều này thường bị hiểu nhầm), nhưng đầu ra của nó luôn bằng `flowA`; kết hợp với `distinctUntilChanged()` ở downstream, **mọi thay đổi từ các upstream còn lại bị nuốt hoàn toàn**.

### Lỗi 1 · Danh sách nhánh không làm mới

**Tệp**: `feature/chat/ChatViewModel.kt:256-261` (mã hiện tại ở thượng nguồn)

```kotlin
) { sessionId, _, _, _ -> sessionId }.mapLatest { sessionId ->
```

Trong bốn luồng upstream, ba luồng sau bị bỏ qua:
- `branchMessageRevision` (cập nhật tin nhắn)
- `_branchRefresh` (số lần làm mới thủ công)
- `branchEventRevision` (sự kiện chạy của sub-agent)

Hệ quả: cả `_branchRefresh++` lẫn tiến độ của sub-agent **đều không kích hoạt chiếu lại dữ liệu**. Thế nhưng comment ngay bên cạnh lại viết:

> "Khi sub-agent chạy trong lane độc lập, tin nhắn của phiên chính không đổi, nên dùng sự kiện chạy để điều khiển việc chiếu lại nhánh"

**Điều comment tuyên bố, implementation hoàn toàn làm không được** (comment và implementation là hai chuyện riêng).

**Sửa**: đưa vào `ProjectionKey` để nén bốn luồng thành một tín hiệu có thể so sánh; bất kỳ upstream nào thay đổi cũng tạo ra key khác → tải lại danh sách nhánh.

### Lỗi 2 · Bảng tác vụ không làm mới

**Tệp**: `feature/chat/ChatViewModel.kt:288` (mã hiện tại ở thượng nguồn)

```kotlin
combine(harnessLoop.currentSessionId, harnessLoop.status) { sessionId, _ -> sessionId }
```

Trong một vòng thực thi, trạng thái thay đổi nhiều lần (tool qua lại, compaction, chờ phê duyệt). Khi `status` bị nuốt, tiến độ bảng tác vụ **không thể làm mới theo thời gian thực** — trong khi comment của chính luồng trạng thái này lại viết "dùng currentSessionId + trạng thái làm khóa để đọc lại: trong một vòng thực thi trạng thái đổi nhiều lần, nhờ đó xấp xỉ làm mới tiến độ bảng theo thời gian thực".

**Sửa**: dùng `(sessionId, status)` làm tín hiệu chiếu lại.

### Lỗi 3 · Ảnh chụp compaction không làm mới

**Tệp**: `feature/chat/ChatViewModel.kt`, `activeCompaction`

Compaction ngữ cảnh luôn đi kèm chuyển trạng thái (đang chạy → đang compaction → đang chạy); `status` bị nuốt khiến **ảnh chụp không được cập nhật sau khi compaction xong**, banner phải đợi đổi session mới xuất hiện.

**Sửa**: giống như trên.

### Lỗi 4 · Ghi chú nháp không làm mới

**Tệp**: `feature/chat/ChatViewModel.kt`, `scratchpads`

```kotlin
) { sessionId, _, _ -> sessionId }
```

Lỗi cùng nguồn gốc (`status` + bộ đếm làm mới thủ công bị nuốt). **Sửa**: giống như trên.

### Xác minh

Cả 4 chỗ sửa đều vượt qua biên dịch CI trên cloud và unit test. **Không chỉnh sửa bất kỳ tệp UI nào** — chỉ sửa điều kiện kích hoạt chiếu lại của luồng dữ liệu.

---

## 2. Lỗi 5xx từ thượng nguồn chưa được đưa vào ngân sách retry tạm thời

**Tệp**: `harness/HarnessProviderRunner.kt`

### Hiện trạng (thượng nguồn)

`ProviderClient` ném `TransientHttpException` cho lỗi 5xx từ thượng nguồn, nhưng **logic retry chưa bao giờ nhận diện được nó** — `isTransientConnectionFailure` chỉ nhận `SocketException` / `InterruptedIOException` / `SSLException` / `EOFException`.

Hệ quả:

```kotlin
val maxNetworkRetries = maxNetworkRetriesFor(estimatedRequestTokens, retryPolicy.maxRetries)
// 估算输入 ≥ 64K tokens 时被压到 1 次
```

**Một lần 503 / 524 (ví dụ Cloudflare timeout) trong phiên dài sẽ làm cả vòng thất bại**, người dùng chỉ có thể tiếp tục thủ công. Trong khi loại lỗi này **không liên quan** tới kích thước request hay quy mô ngữ cảnh, lẽ ra không bị ảnh hưởng bởi cơ chế hạ cấp khi ngữ cảnh lớn.

### Cách sửa

- `isTransientFailure`: thêm `TransientHttpException` vào danh sách trắng; truy ngược chuỗi cause (tối đa 10 tầng, tránh tham chiếu vòng lẫn nhau gây lặp vô hạn)
- `effectiveRetryBudget`: lỗi tạm thời được đảm bảo tối thiểu `TRANSIENT_MAX_RETRIES = 3` lần, không bị hạ cấp

### Sửa luôn cách diễn đạt trong log

Log cũ `"网络中断重试 $netRetry/$maxNetworkRetries"`. Khi giới hạn là 3, trước lần thất bại thứ 4 sẽ in "thử lại 4/3", thực chất là "bỏ cuộc sau lần thất bại thứ 4", **rất dễ bị hiểu nhầm là "đã chạy retry lần 3, vẫn đang tiếp tục"**. Đổi thành "lần thất bại thứ N (giới hạn retry M lần)".

### Test mới

`HarnessTransientFailureTest` (8 ca): 5xx / mất kết nối / cause được bọc / cause dạng vòng / phân loại ngoại lệ thường, và đảm bảo tối thiểu 3 lần retry cho lỗi tạm thời trong ngữ cảnh lớn.

> Lưu ý: trong số đó, ca "cause dạng vòng" ở bản đầu viết `it.initCause(it)` sẽ ném thẳng `IllegalArgumentException` trên JDK 17+ (Java cấm cause tự tham chiếu), test không bao giờ chạy tới logic cần kiểm tra. Đã đổi dùng vòng `a.cause = b, b.cause = a` để phủ đúng giới hạn độ sâu.

---

## Kết quả xác minh

**Không biên dịch cục bộ** (máy phát triển là thiết bị Android, chi phí biên dịch quá lớn), toàn bộ được xác minh qua **CI trên cloud**.

- **Biên dịch (Kotlin + C)**: ✅ 0 lỗi
- **Test mới**: ✅ tất cả đạt
- **Unit test**: ⚠️ 4 ca thất bại — **có sẵn trong baseline, không do PR này gây ra**

### Về kết quả test (lưu ý dành cho tác giả)

| Test | Nhánh của PR này | Baseline thượng nguồn sạch (không đổi mã nguồn) |
|---|---|---|
| `ToolExecutorTest` (4 ca: base tool …) | 4 ca thất bại | **4 ca thất bại (hoàn toàn giống nhau)** |

Cách đối chiếu: checkout `f82a44f7` (không đổi mã nguồn) chạy cùng CI, thu được **đúng 4 ca thất bại giống hệt** (76 tests completed, 4 failed). PR này không đụng vào `ToolExecutor` hay mã liên quan.

---

## Tương thích

- Không thay đổi signature API, không thêm phụ thuộc
- Thay đổi ngữ nghĩa luồng dữ liệu chỉ thể hiện ở "kích hoạt chiếu lại kịp thời hơn", hợp đồng hành vi và UI không đổi
- Số lần retry tăng chỉ giới hạn trong trường hợp lỗi tạm thời, tối đa 3 lần, không làm thay đổi hành vi hiện tại với lỗi không tạm thời
