# 🧭 CTHarness — Tổng quan điều hướng ngữ nghĩa cho AI

> **Dành riêng cho trợ lý lập trình AI (Antigravity / Cursor / Claude / GPT / Copilot) để nắm bối cảnh tổng thể và đọc theo nhu cầu**  
> Tài liệu này là trung tâm điều hướng ngữ nghĩa gọn nhẹ. Kiến trúc chi tiết, chuỗi lời gọi, chỉ mục tệp, nguyên tắc bắt buộc và lệnh đã được tách thành các tài liệu chuyên biệt; hãy mở tài liệu tương ứng khi cần tìm hiểu sâu hơn:

---

## 🎯 Đi thẳng tới nhiệm vụ và chỉ mục tài liệu

| Mục tiêu / Nội dung bạn muốn tìm hiểu | Tài liệu chuyên biệt | Tóm tắt |
| :--- | :--- | :--- |
| **Kiến trúc hệ thống, bộ công nghệ và sơ đồ module** | [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) | Định hướng, bộ công nghệ Android/PRoot/PTY/Harness, sơ đồ phụ thuộc module và nguyên tắc liên module |
| **Luồng dữ liệu, trình tự và chuỗi lời gọi cốt lõi** | [`docs/EXECUTION_TRACES.md`](EXECUTION_TRACES.md) | Vòng lặp agent, mount bộ lưu trữ, terminal PTY, chia màn hình hai cột, quản lý tiến trình và luồng build |
| **Định vị lớp, tệp và thành phần cốt lõi cụ thể** | [`docs/FILE_INDEX.md`](FILE_INDEX.md) | Chỉ mục đường dẫn và trách nhiệm của các tệp quan trọng trong Harness, Runtime, Tools, UI |
| **Giới hạn kiến trúc, hệ thống thiết kế UX/UI và nguyên tắc tránh bẫy** | [`docs/ARCHITECTURE_RULES.md`](ARCHITECTURE_RULES.md) | Cách ly Pure Kotlin, quy chuẩn `RuntimeCard`/`RuntimeAlertDialog`, chống bàn phím IME che nội dung |
| **Lệnh build, kiểm thử, đóng gói và gỡ lỗi** | [`docs/COMMANDS.md`](COMMANDS.md) | Unit test Gradle, đóng gói APK, kiểm tra tuân thủ kiến trúc, triển khai ADB và gỡ lỗi qua log |
| **Phát triển công cụ cục bộ và hệ sinh thái plugin** | [`docs/PLUGIN_DEVELOPMENT_GUIDELINES.md`](PLUGIN_DEVELOPMENT_GUIDELINES.md) | Cấu trúc Manifest plugin, giao dịch cài đặt Recipe và quy chuẩn tiêm biến môi trường |
| **Tạo mẫu dự án và giao thức biến** | [`docs/PROJECT_TEMPLATE_SPEC.md`](PROJECT_TEMPLATE_SPEC.md) | Quy chuẩn template.json, biểu mẫu biến, thay thế mẫu động và hook vòng đời |
| **Bộ công cụ build Android ARM64 ngoại tuyến** | [`docs/ANDROID_OFFLINE_PLUGIN.md`](ANDROID_OFFLINE_PLUGIN.md) | Bộ công cụ JDK/Android SDK/NDK/Flutter trong sandbox và chính sách Gradle trên thiết bị di động |
| **Quyết định thiết kế trình duyệt tích hợp (gồm engine Hook/CDP)** | [`docs/BROWSER_DESIGN.md`](BROWSER_DESIGN.md) | Sơ đồ module, giao thức công cụ, ma trận rủi ro, engine Hook và ADR gỡ lỗi CDP |
| **Lựa chọn backend thực thi sandbox (vì sao không dùng chroot)** | [`docs/ADR_SANDBOX_BACKEND.md`](ADR_SANDBOX_BACKEND.md) | Quyết định PRoot so với chroot/namespace, ràng buộc SELinux/seccomp/namespace trên Android |
| **Định nghĩa workflow, ngữ nghĩa điều phối và ranh giới bảo mật** | [`docs/WORKFLOW.md`](WORKFLOW.md) | Mô hình DAG, điều phối đồng thời, phê duyệt, bộ thực thi, điểm vào và giới hạn năng lực hiện tại |
| **Vấn đề đã biết và hướng dẫn tránh bẫy** | [`docs/KNOWN_ISSUES.md`](KNOWN_ISSUES.md) | Giới hạn đã biết của sandbox PRoot, cân nhắc thiết kế kiến trúc trước đây và cách khắc phục |
| **Phân loại lưu trữ và ranh giới dọn dẹp** | [`docs/STORAGE_MANAGEMENT.md`](STORAGE_MANAGEMENT.md) | Sáu nhóm dung lượng, kế hoạch dọn dẹp, loại trừ tương hỗ khi chạy và đề xuất quản lý tiếp theo |

---

## 💡 Sơ đồ module cốt lõi rút gọn

```text
app (lớp vỏ/lắp ghép/JNI)
 ├── feature/*          (components·theme·home·chat·terminal·workspace·settings·developer·navigation)
 ├── harness            (vòng lặp agent·phân phối công cụ tích hợp·MCP·agent con·Browser MCP Server trong tiến trình·Bootstrap)
 ├── tools              (Registry plugin·giao dịch cài đặt·kho Provider an toàn)
 ├── runtime            (sandbox PRoot·terminal PTY·engine build workspace)
├── runtime/browser    (pool WebView·Snapshot/Screenshot/Network/Storage/JS·engine Hook tiêm vào trang·điểm dừng CDP/chặn Fetch cấp Worker·bộ xử lý công cụ MCP)
 ├── project-template   (Manifest mẫu·giao thức biến·engine hiện thực hóa mẫu)
 └── core/*             (model·common·database·datastore·network·security)
```
