# Bảo mật

Pi là một coding agent chạy cục bộ. Nó chạy với quyền của tài khoản người dùng khởi tạo nó, và coi các tệp mà người dùng đó có thể ghi như nằm trong cùng một ranh giới tin cậy cục bộ.

## Tin cậy dự án (Project Trust)

Tin cậy dự án kiểm soát việc pi có tải các settings, tài nguyên, package và extension cục bộ của dự án hay không. Đây không phải là một sandbox và nó không hạn chế những gì model có thể yêu cầu tools thực hiện sau khi bạn bắt đầu làm việc trong một thư mục.

Pi coi một dự án có tài nguyên cần tin cậy khi nó tìm thấy bất kỳ mục nào sau đây từ thư mục làm việc hiện tại:

- `.pi/settings.json`
- `.pi/extensions`, `.pi/skills`, `.pi/prompts`, hoặc `.pi/themes`
- `.pi/SYSTEM.md` hoặc `.pi/APPEND_SYSTEM.md`
- project `.agents/skills` trong thư mục hiện tại hoặc một thư mục cha

Một thư mục `.pi` trơn sẽ không được tính là tài nguyên dự án cần tin cậy.

Khi một phiên tương tác bắt đầu trong một dự án có tài nguyên cần tin cậy và chưa có quyết định đã lưu cho thư mục hiện tại hoặc thư mục cha, pi sẽ tuân theo `defaultProjectTrust` từ settings toàn cục. Giá trị mặc định là `"ask"`, sẽ hỏi xem có tin cậy dự án hay không khi có UI. Các quyết định đã lưu được lưu theo thư mục chuẩn hóa trong `~/.pi/agent/trust.json`, và quyết định đã lưu gần nhất trên đường dẫn hiện tại hoặc đường dẫn cha được áp dụng trước giá trị mặc định toàn cục.

Tin cậy một dự án cho phép pi tải các tài nguyên dự án cần tin cậy, bao gồm:

- `.pi/settings.json`
- các tài nguyên `.pi` như extension, skill, prompt template, theme và tệp system prompt
- các package dự án bị thiếu được cấu hình qua project settings
- extension cục bộ của dự án và extension do project package quản lý

Từ chối tin cậy sẽ bỏ qua các tài nguyên được bảo vệ. Các tệp ngữ cảnh như `AGENTS.override.md`, `AGENTS.md` và `CLAUDE.md` vẫn được tải bất kể tin cậy dự án, trừ khi việc tải ngữ cảnh bị vô hiệu hóa. Trước khi quyết định tin cậy được resolving, pi chỉ tải các tệp ngữ cảnh, extension người dùng/toàn cục và extension `-e` từ CLI. Extension người dùng/toàn cục và extension CLI có thể xử lý sự kiện `project_trust`; extension đầu tiên trả về quyết định có/không sẽ sở hữu quyết định đó.

Các chế độ không tương tác (`-p`, `--mode json` và `--mode rpc`) không hiển thị lời nhắc tin cậy. Khi không có quyết định tin cậy đã lưu phù hợp, `defaultProjectTrust: "ask"` và `"never"` sẽ bỏ qua các tài nguyên đó, trong khi `"always"` sẽ tin cậy chúng. Dùng `--approve`/`-a` hoặc `--no-approve`/`-na` để ghi đè tin cậy dự án cho một lần chạy.

## Không có sandbox tích hợp sẵn

Pi không bao gồm sandbox tích hợp sẵn. Các built-in tool có thể đọc tệp, ghi tệp, sửa tệp và chạy lệnh shell với quyền của tiến trình pi. Extension là các module TypeScript chạy với cùng quyền. Cài đặt package, lệnh shell, language server, lệnh test và các công cụ phát triển khác hoạt động như các tiến trình cục bộ thông thường.

Điều này là có chủ đích. Pi được thiết kế để hoạt động trên các cây nguồn cục bộ, gọi toolchain của dự án và tích hợp với môi trường phát triển hiện có của người dùng. Một sandbox in-process một phần rất dễ bị hiểu nhầm là một ranh giới bảo mật, trong khi vẫn phụ thuộc vào shell của host, filesystem, trình quản lý package, credential và mã extension. Sự cô lập thực sự cần đến từ hệ điều hành hoặc ranh giới ảo hóa/container.

Tin cậy dự án chỉ là một cổng kiểm soát việc tải đầu vào. Nó ngăn một repository âm thầm thay đổi settings hoặc extension của pi trước khi bạn phê duyệt. Nó không làm cho mã không đáng tin, prompt không đáng tin hay đầu ra model không đáng tin trở nên an toàn. Prompt injection từ các tệp repository, comment, tài liệu, tệp ngữ cảnh hoặc đầu ra build là rủi ro local-agent dự kiến và không thể được pi ngăn chặn một cách đáng tin cậy.

## Chạy công việc không đáng tin hoặc không giám sát

Đối với repository không đáng tin, mã được tạo mà bạn không định theo dõi sát, hoặc tự động hóa không người giám sát, hãy chạy pi trong một môi trường cô lập. Dùng container, VM, micro-VM, sandbox từ xa hoặc sandbox do policy kiểm soát, với chỉ các tệp và credential cần thiết cho tác vụ.

Các mô hình phổ biến được ghi lại trong [Containerization](containerization.md):

- chạy toàn bộ tiến trình `pi` bên trong container/sandbox
- chạy pi trên host trong khi định tuyến thực thi built-in tool vào micro-VM Gondolin
- chỉ mount các đường dẫn workspace mà agent nên truy cập
- tránh mount `~/.pi/agent` của host trừ khi container cần truy cập session, settings và credential của host
- truyền các API key tối thiểu cần thiết hoặc dùng credential ngắn hạn
- hạn chế truy cập mạng khi tác vụ không cần
- xem lại diff và đầu ra trước khi sao chép kết quả trở lại hệ thống đáng tin

Nếu bạn bind-mount workspace của host ở chế độ đọc/ghi, các thao tác ghi từ bên trong container hoặc VM vẫn có thể sửa đổi tệp host. Hãy dùng mount chỉ-đọc hoặc sao chép tệp vào và ra khỏi sandbox khi bạn cần bảo vệ mạnh hơn trước các thao tác ghi ngoài ý muốn.

## Báo cáo vấn đề bảo mật

Để báo cáo vấn đề bảo mật, hãy làm theo [Security Policy](https://github.com/earendil-works/pi/blob/main/SECURITY.md) của repository. Không mở issue công khai cho các báo cáo nhạy cảm về bảo mật.

Hành vi local-agent dự kiến, việc thiếu sandbox tích hợp sẵn, prompt injection từ nội dung không đáng tin, và hành vi của extension hoặc skill do người dùng cài đặt nói chung nằm ngoài ranh giới bảo mật, trừ khi báo cáo chứng minh được một sự vượt ranh giới quyền thực sự hoặc chỉ ra cách pi cấp quyền mà người dùng cục bộ vốn đã không có.
