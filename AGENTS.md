# 🧭 CTHarness — Lối vào điều hướng cho AI

> Tệp này là lối vào điều hướng, được tải theo mặc định. Toàn bộ chi tiết (stack kỹ thuật / cấu trúc module / chuỗi lời gọi / chỉ mục tệp / nguyên tắc cốt lõi / lệnh) nằm tại [`docs/AI_NAVIGATION.md`](docs/AI_NAVIGATION.md), hãy đọc theo nhu cầu.

## Là cái gì

Chạy sandbox Linux đa bản phân phối bằng PRoot trên Android không Root, kết hợp AI Agent Harness và terminal PTY bản địa. Kotlin 2.4 / Compose / Koin / Room / Navigation3, chỉ hỗ trợ `arm64-v8a`. Phiên bản hiện tại v0.36.4 — đã hoàn thành căn chỉnh toàn bộ cơ chế cốt lõi của DeepSeek Harness (33 cải tiến, tất cả đều được tích hợp thực tế vào chuỗi chạy), đồng thời hoàn thành nhiều vòng rà soát và sửa lỗi sâu theo từng tệp (tính nguyên tử của concurrent, truyền qua việc hủy coroutine, v.v.).

## Các module

`app`(vỏ ứng dụng/lắp ráp/JNI) · `core`(model·common·database·datastore·network·security) · `runtime`(PRoot/RootFS/PTY/workspace) · `tools`(Registry/giao dịch cài đặt/bảo mật Provider) · `harness`(vòng lặp Agent/MCP/subagent) · `feature`(components·theme·home·chat·terminal·workspace·settings·developer·onboarding·custom\_iteration·navigation)

## Đọc trước khi bắt tay vào việc

| Việc cần làm | Tài liệu nên đọc |
| :--- | :--- |
| **Nắm bắt nhanh toàn cảnh** | [`docs/AI_NAVIGATION.md`](docs/AI_NAVIGATION.md) (tổng quan điều hướng ngữ nghĩa) |
| **Viết mã / sửa module / rà soát cấu trúc** | [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (kiến trúc và cấu trúc module) |
| **Theo dõi luồng dữ liệu / chuỗi lời gọi** | [`docs/EXECUTION_TRACES.md`](docs/EXECUTION_TRACES.md) (trình tự thực thi cốt lõi) |
| **Tìm một class / định vị một tệp** | [`docs/FILE_INDEX.md`](docs/FILE_INDEX.md) (tra cứu nhanh chỉ mục tệp quan trọng) |
| **Hệ thống thiết kế UI/UX và nguyên tắc kiến trúc** | [`docs/ARCHITECTURE_RULES.md`](docs/ARCHITECTURE_RULES.md) (quy chuẩn thiết kế và cách tránh bẫy) |
| **Build / test / đóng gói / debug** | [`docs/COMMANDS.md`](docs/COMMANDS.md) (xem nhanh lệnh thông dụng) |
