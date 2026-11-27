# Tôi đã nhét toàn bộ sandbox Linux, Agent mô hình lớn và trình biên dịch vào điện thoại Android: CTHarness đã mã nguồn mở!

> **"Chứa cả núi Tu-di trong hạt cải, chứa cả CTHarness trong lòng bàn tay."**  
> Trong ranh giới sandbox và quyền hạn khắt khe của Android, chúng tôi dựng nên một thế giới Linux thu nhỏ trên đầu ngón tay: có thể chạy được, quan sát được, tự phục hồi được và tiến hóa được.

---

![Hình minh họa 01: ảnh đầu / ảnh bìa](https://your-image-url/tianxuan-cover.png)
> 💡 *【Gợi ý hình 01】: Logo CTHarness trên nền tối công nghệ làm ảnh đầu, hoặc ảnh áp phích đẹp mắt chụp màn hình khởi động của CTHarness trên máy thật.*

---

## 📱 01. Mở đầu: điện thoại cao cấp của bạn có đang dư thừa sức mạnh tính toán?

Hãy nhìn vào chiếc máy chính bạn đang dùng: chip **Snapdragon 8 Gen 3/4 hay Dimensity 9300**, **RAM LPDDR5X 16GB thậm chí 24GB**, bộ nhớ UFS 4.0 tốc độ cao… Xét về sức mạnh đơn nhân, đa nhân và băng thông bộ nhớ, điện thoại cao cấp hiện tại hoàn toàn không thua kém máy tính xách tay mỏng nhẹ cách đây vài năm.

Nhưng thực tế lại thường rất trớ trêu:
- Con quái vật sức mạnh di động đó phần lớn thời gian chỉ để lướt video ngắn, đọc bảng tin và chơi vài ván trò chơi;
- Trên máy tính, công cụ nghiên cứu phát triển AI bùng nổ (Cursor, Claude Code, Windsurf, Cline), trong khi các "trợ lý AI" trên điện thoại hầu hết vẫn chỉ là **"vỏ bọc trò chuyện API"** — mô hình lớn xuất ra cả đoạn mã hoa mỹ nhưng không lưu được vào đĩa, không chạy được, càng không tự kiểm chứng được môi trường;
- Termux, công cụ kỳ cựu trên thiết bị di động, quả thực rất mạnh, nhưng nó là một terminal thuần túy, thiếu vòng lặp nhân-quả do mô hình lớn dẫn dắt, thiếu không gian làm việc IDE di động hiện đại, thiếu bản so sánh mã và bảng tác vụ trực quan.

**Liệu trên điện thoại, mô hình lớn chỉ được làm "bạn trò chuyện"?**

Để phá vỡ tình trạng này, chúng tôi đã phát triển và chính thức mã nguồn mở bộ môi trường thực thi AI phát triển toàn diện cho thiết bị di động — **CTHarness**.

Nó không làm vỏ bọc suông, mà hợp nhất sâu **hệ điều hành Linux không cần Root, bộ máy Agent, terminal PTY gốc C/JNI và không gian làm việc dự án**.

- 🌐 **Kho GitHub**: [https://github.com/wkbin/tianxuan](https://github.com/wkbin/tianxuan)
- 📦 **Thông số kỹ thuật**: `Android 10+ (SDK 29+)` · thuần `arm64-v8a` · `Kotlin 2.4` · `Jetpack Compose` · `Material 3 Expressive`

---

## 🌌 02. CTHarness là gì: không chỉ là terminal, mà là vòng lặp nhân-quả

《Liệt tử · Thang vấn》 có câu: "Phía đông biển Bột Hải… có một hố lớn, thật là thung lũng không đáy, gọi là Quy Hư. Nước của tám phương chín cõi, dòng của Ngân Hà, đều đổ vào đó mà không tăng không giảm."

**CTHarness** lấy ý tưởng từ đây: nó để mô hình lớn, công cụ hệ thống, sandbox Linux và dự án kỹ thuật chia sẻ cùng một **ngữ cảnh thực thi đồng cấu**.

Mỗi câu lệnh ngôn ngữ tự nhiên của người dùng đều đi trong CTHarness qua một vòng lặp kỹ thuật thật sự:

```text
Ý định người dùng (Intent) ─► Lập kế hoạch bước (TaskPlanCard) ─► Phân công công cụ Linux / MCP / trình duyệt
                                         ▲                                  │
                                         │                                  ▼
                              Tự phân tích và sửa lỗi ◄──────────── Chạy và kiểm chứng môi trường thật (Verification)
```

![Hình minh họa 02: giao diện tổng thể CTHarness và minh họa vòng lặp nhân-quả](https://your-image-url/tianxuan-overview.png)
> 💡 *【Gợi ý hình 02】: màn hình chính của CTHarness hoặc giao diện hai cột/chia đôi, thể hiện tổng thể bên trái là hội thoại mô hình lớn tách tác vụ, bên phải là terminal hoặc trình duyệt mã.*

---

## ⚡ 03. Giải mã các tính năng cốt lõi "cứng"

### 1. Sandbox Linux trong không gian người dùng không cần Root (10+ bản phân phối phổ biến)
Điện thoại không Root vẫn chạy được Linux đầy đủ? Đúng vậy!
- **PRoot chặn trong không gian người dùng**: tầng dưới dùng `ptrace` để bắt lời gọi hệ thống và viết lại đường dẫn, không cần bất kỳ quyền Root nào nhưng vẫn cung cấp hệ thống tệp gốc Linux hoàn chỉnh;
- **Đầy đủ bản phân phối phổ biến**: hỗ trợ Ubuntu 24.04, Debian 12, Kali, Arch Linux, Alpine, Fedora… tải về một chạm và chuyển đổi liền mạch;
- **Kiểm chứng OCI Registry và gắn kết hai chiều**: hỗ trợ kiểm tra giá trị băm ảnh hệ thống an toàn; có thể gắn kết an toàn các thư mục như `/sdcard/Download` của điện thoại vào sandbox, tệp tải ngoài sandbox đọc ngay, sản phẩm dựng trong sandbox lưu ngay ra phía chủ.

![Hình minh họa 03: môi trường sandbox Linux và bảng giám sát](https://your-image-url/proot-dashboard.png)
> 💡 *【Gợi ý hình 03】: ảnh chụp giao diện quản lý sandbox, thể hiện trạng thái chạy của Ubuntu và các bản phân phối khác, chỉ số CPU/bộ nhớ.*

---

### 2. Vòng lặp Agent tự chủ thật sự (bộ máy Harness)
CTHarness tích hợp sẵn một bộ máy điều khiển Agent được tự nghiên cứu bài bản:
- **Thông suốt giao thức chính thống**: tương thích sâu với giao diện chuẩn OpenAI và giao thức Messages của Anthropic (khớp hoàn hảo DeepSeek-R1, Claude 3.7, SiliconFlow và các nền tảng trung gian khác);
- **Theo dõi sâu chuỗi tư duy**: phân tích đầy đủ `reasoning_content`, quá trình suy luận chặt chẽ của mô hình hiển thị động theo luồng;
- **TaskPlanCard động**: tự phân tích danh sách bước `- [ ]` trong đầu ra của mô hình, chuyển thành thẻ tiến độ động có phản hồi xúc giác, nhìn là biết đang ở bước nào;
- **Ma trận công cụ nguyên tử**: mô hình có đủ bộ công cụ xem thư mục, đọc/ghi tệp, thay thế cục bộ chính xác (Edit), tìm kiếm ký hiệu mã (Grep/Find), thực thi lệnh shell trong sandbox…;
- **Mô hình nhỏ ngoại tuyến trong sandbox**: hỗ trợ triển khai `llama.cpp` bên trong sandbox, chạy ngoại tuyến các mô hình lập trình GGUF như Qwen2.5-Coder, Llama-3, mất mạng vẫn tự viết mã!

![Hình minh họa 04: chuỗi tư duy Agent và thực thi TaskPlanCard](https://your-image-url/agent-task-plan.png)
> 💡 *【Gợi ý hình 04】: ảnh chụp giao diện hội thoại Tri-Shu, thể hiện thẻ gập tư duy của DeepSeek/Claude, thanh bước TaskPlanCard động và thẻ gọi công cụ.*

---

### 3. SessionFork và lưới an toàn bản chụp trạng thái cấp đĩa
Để mô hình trực tiếp sửa mã trên điện thoại, nếu hỏng thì sao?
- **SessionFork phái sinh cây phiên**: hỗ trợ phái sinh nhánh từ bất kỳ vòng hội thoại nào như Git tạo nhánh, hoặc một chạm "quay lại vòng này" (Rewind);
- **Điểm kiểm tra với bản chụp trạng thái cấp đĩa**: trước mỗi vòng Agent bắt đầu sửa, tự tạo bản chụp trạng thái cấp đĩa cho không gian làm việc. Một khi Agent ghi sai hoặc gây lỗi nghiêm trọng, hoàn tác không mất mát trong mili-giây, không còn lo ngại gì.

---

### 4. Terminal PTY gốc C/JNI (Matrix Terminal)
Không phải mấy ô nhập liệu mô phỏng `TextView` sơ sài trên thị trường!
- **Cầu nối tầng thấp bằng C**: tự nghiên cứu `libtianxuan_pty.so`, dựa trên POSIX `openpty`/`forkpty` kết nối trực tiếp vòng đời tiến trình con Linux;
- **Máy trạng thái ANSI/VT100 tăng dần**: hỗ trợ tô màu theo luồng, định vị con trỏ chính xác và tương tác giao diện văn bản toàn màn hình;
- **Thanh phím xúc giác tùy biến (ExtraKeys)**: bàn phím phụ thiết kế riêng cho thiết bị di động, cung cấp `Ctrl`, `Alt`, `Tab`, `Esc`, phím mũi tên và bánh xe lịch sử lệnh có rung phản hồi, gõ Vim / Nano trên điện thoại vẫn mượt.

![Hình minh họa 05: terminal PTY gốc và thanh phím xúc giác](https://your-image-url/pty-terminal.png)
> 💡 *【Gợi ý hình 05】: ảnh chụp terminal toàn màn hình, thể hiện neofetch/htop có màu và thanh phím ảo ExtraKeys riêng ở dưới.*

---

### 5. Không gian làm việc di động và biên dịch ngay trên máy thật
- **Nhập đa dạng**: hỗ trợ sao chép kho GitHub một chạm (có phần trăm tiến độ tải theo luồng thời gian thực), giải nén ZIP cục bộ an toàn (kiểm tra chặn vượt đường dẫn Zip-Slip nghiêm ngặt tích hợp sẵn);
- **Xem mã chuyên nghiệp**: cây tệp tô cú pháp và so sánh thay đổi cấp dòng chuyên nghiệp;
- **Dựng APK trực tiếp trong sandbox**: bạn thậm chí có thể cấu hình JDK, Android SDK hoặc Flutter trong sandbox, gõ `./gradlew assembleDebug` ngay trên điện thoại, APK dựng ra có thể gọi thẳng trình cài đặt hệ thống để cài lên máy!

![Hình minh họa 06: so sánh thay đổi cấp dòng và bàn giao bản dựng APK](https://your-image-url/workspace-diff-build.png)
> 💡 *【Gợi ý hình 06】: ảnh chụp giao diện không gian làm việc, thể hiện bản so sánh thay đổi rõ ràng đỏ-xanh, hoặc thẻ bàn giao cho phép bấm cài APK ngay sau khi dựng xong.*

---

### 6. Trình duyệt tích hợp, điểm dừng CDP và cơ chế móc cấp Worker
CTHarness tích hợp module trình duyệt độc lập cho mô hình lớn và nhà phát triển:
- **Dịch vụ Browser MCP trong tiến trình**: mô hình lớn có thể mở trực tiếp trang web, lấy cây DOM, chụp màn hình để phân tích thị giác;
- **Dịch ngược và gỡ lỗi sâu**: hỗ trợ cơ chế móc bằng cách chèn tập lệnh, điểm dừng CDP chặn luồng, chặn lưu lượng Fetch cấp Worker, kết hợp dòng thời gian yêu cầu mạng trực quan, thu thập dữ liệu web và dịch ngược động nằm trong tầm tay.

![Hình minh họa 07: bảng gỡ lỗi trình duyệt tích hợp và dòng thời gian yêu cầu mạng](https://your-image-url/browser-devtools.png)
> 💡 *【Gợi ý hình 07】: ảnh chụp khi trình duyệt tích hợp đang mở, thể hiện biểu đồ thác nước yêu cầu mạng hoặc bảng chặn tập lệnh móc.*

---

### 8. Cửa sổ nổi toàn màn hình (Tri-Shu theo bạn)
Không chỉ giới hạn trong ứng dụng! CTHarness hỗ trợ **cửa sổ nổi tiền cảnh cấp hệ thống**. Khi bạn đang tra cứu trên trình duyệt, xem vấn đề trên GitHub di động hay dùng ứng dụng khác, chỉ cần vuốt mép để gọi cửa sổ nhỏ Tri-Shu, vừa xem màn hình vừa nhờ AI chạy tập lệnh truy dữ liệu, đa nhiệm thật sự xuyên ứng dụng.

![Hình minh họa 09: cửa sổ nổi toàn màn hình tương tác xuyên ứng dụng](https://your-image-url/floating-window.png)
> 💡 *【Gợi ý hình 09】: trên màn hình chính hoặc giao diện ứng dụng khác, cửa sổ nhỏ Tri-Shu của CTHarness nổi lên để hỏi đáp và nhận lệnh.*

---

## 📐 04. Thiết kế kiến trúc và vẻ đẹp kỹ thuật

CTHarness không chỉ có những tính năng ấn tượng, mà trong triển khai kỹ thuật Android cũng tuân thủ nghiêm ngặt kiến trúc sạch và chuẩn module hóa công nghiệp:

```text
TianXuan/
├── app/                  # Khung ứng dụng phía chủ: cầu nối JNI C/C++, lắp ráp Koin, Service tiền cảnh giữ tiến trình, dịch vụ cửa sổ nổi
├── core/
│   ├── model/           # Mô hình dữ liệu Kotlin thuần (nghiêm cấm mọi phụ thuộc nền tảng và bộ khung)
│   ├── database/        # Cơ sở dữ liệu Room: cây phiên, chuỗi nhân-quả thông điệp, bản chụp điểm kiểm tra, kiểm toán thực thi
│   ├── datastore/       # Jetpack DataStore: cài đặt người dùng, cấu hình điểm gắn kết
│   └── network/         # Máy khách OkHttp, bộ phân tích luồng SSE phản ứng
├── runtime/              # Lõi sandbox Linux: gắn kết PRoot, quản lý vòng đời tiến trình, phiên PTY, dựng không gian làm việc
│   └── browser/         # Trình duyệt tích hợp: nhóm phiên bản WebView, bộ chặn CDP, Browser MCP trong tiến trình
├── harness/              # Bộ máy Agent: vòng lặp Agent, điều phối công cụ, phối hợp Agent con, phái sinh phiên
├── tools/                # Trung tâm hệ sinh thái: Registry plugin, giao dịch công thức cài đặt, phân giải phụ thuộc
└── feature/              # Tầng tính năng nghiệp vụ Jetpack Compose (quy chuẩn thiết kế M3 Expressive)
    ├── chat/            # Hội thoại Tri-Shu và kết xuất bản so sánh thay đổi / TaskPlan
    ├── terminal/        # Terminal gốc và thanh phím xúc giác
    ├── workspace/       # Duyệt dự án, so sánh thay đổi và bàn giao APK
    └── settings/        # Cấu hình mô hình, bảng chẩn đoán ADB không dây
```

- **Nền tảng công nghệ hiện đại thuần túy**: xây dựng với `Kotlin 2.4` + `Jetpack Compose`, giao diện dùng hoàn toàn chuẩn thiết kế **Material 3 Expressive** (chủ đề Huyền Thống / Trừng Minh);
- **Giữ tiến trình tiền cảnh và ổn định**: dịch vụ tiền cảnh (FGS) kết hợp khóa đánh thức CPU và khóa Wi-Fi, dù lui vào nền hay tắt màn hình, tác vụ dựng dài vẫn không bị ngắt;
- **Ranh giới kiến trúc nghiêm ngặt**: cấm nền tảng xâm nhập tầng mô hình, tuân thủ chặt chẽ luồng dữ liệu giao diện/ViewModel một chiều, đảm bảo khả năng mở rộng và độ phủ kiểm thử của hàng chục nghìn dòng mã.

---

## 💡 05. Những "tình huống ấn tượng" chỉ CTHarness làm được

1. **Đang đi tàu điện ngầm, hệ thống vận hành bất ngờ gặp lỗi?**  
   Không cần lấy máy tính xách tay, mở CTHarness trên điện thoại, bảo Tri-Shu sao chép kho báo lỗi từ GitHub, AI tự định vị và sửa mã, hiện bản so sánh thay đổi cấp dòng để bạn xác nhận, chạy kiểm thử trong sandbox thành công, rồi đẩy bản sửa khẩn cấp lên Git để tạo yêu cầu hợp nhất.
2. **Đi tàu cao tốc / máy bay đường dài, mất mạng?**  
   Trong sandbox chạy mô hình cục bộ trên thiết bị qua `llama.cpp`, vẫn tự viết được Python, Bash, xử lý tệp và dữ liệu ngoại tuyến.
3. **Dịch ngược và tự động hóa thiết bị di động hằng ngày?**  
   Dùng ADB không dây tích hợp theo dõi ứng dụng hiện tại, mở trình duyệt trong máy chạy tập lệnh móc lấy dữ liệu động của đối tượng, xuất thẳng báo cáo CSV có cấu trúc.

---

## 🚀 06. CTHarness trong lòng bàn tay, bảy sao làm trục

CTHarness đã mã nguồn mở toàn diện. Chúng tôi tin: **giới hạn chưa từng thật sự biến mất; nhưng tự do có thể đến từ việc — dù đang ở trong giới hạn, vẫn có năng lực dựng lên và kiểm chứng thế giới của riêng mình.**

Dù bạn là:
- người thích khám phá "AI trên thiết bị kết hợp sâu với hệ điều hành di động";
- lập trình viên than phiền năng lực phát triển trên thiết bị di động bị giới hạn, tìm kiếm **một trạm làm việc di động trong lòng bàn tay**;
- hay nhà phát triển Android muốn học sâu kiến trúc công nghiệp **Jetpack Compose + JNI C/C++ + Linux PRoot**.

CTHarness đã chuẩn bị cho bạn một kho mã nóng hổi, đầy đặn!

- ⭐️ **Đi thẳng tới mã nguồn GitHub**: [https://github.com/wkbin/tianxuan](https://github.com/wkbin/tianxuan)
- 📥 **APK phát hành mới nhất**: [https://github.com/wkbin/tianxuan/releases](https://github.com/wkbin/tianxuan/releases)

---

### 💬 Giờ tương tác:
> Bạn nghĩ trong tương lai thiết bị di động có thể đảm nhận 30% thậm chí 50% công việc lập trình và vận hành nhẹ không? Bạn từng thử làm việc kỹ thuật "khó nhằn" nhất nào trên điện thoại? Mời cùng trao đổi trong phần bình luận! Nếu CTHarness truyền cảm hứng cho bạn, đừng quên ghé GitHub bấm ⭐️ **Star** ủng hộ nhé!
