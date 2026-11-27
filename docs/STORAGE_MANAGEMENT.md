# Phân loại bộ lưu trữ và chiến lược dọn dẹp

## Triển khai hiện tại

Trang bộ lưu trữ dùng sáu hạng mục loại trừ lẫn nhau. Mỗi tệp thường chỉ được tính vào một mục theo quy tắc đường dẫn dài nhất; tệp không xác định được giữ lại trong mục "Tệp khác" của hạng mục tương ứng. Việc thống kê quét thư mục riêng của ứng dụng, bao gồm thư mục cá nhân của bản phân phối được gắn riêng, tệp ở gốc không gian làm việc, cơ sở dữ liệu, bộ nhớ đệm, cấu hình và dữ liệu WebView; không tính APK hay thư mục gắn ngoài. Không đi theo liên kết tượng trưng, và các liên kết cứng cùng định danh tệp chỉ được tính một lần.

| Hạng mục | Nội dung | Thao tác và đề xuất hiện tại |
| --- | --- | --- |
| Tệp dự án và tệp cá nhân | Dự án trong không gian làm việc, tệp xuất, thư mục cá nhân của bản phân phối | Dọn bộ nhớ đệm riêng cũ của dự án Gradle/Dart đã nhận diện; mã nguồn và thư mục đầu ra dùng chung do không gian làm việc quản lý |
| Linux và môi trường phát triển | Hệ thống của từng bản phân phối, SDK, môi trường chạy dùng chung, trình quản lý phiên bản | Giữ lại; gỡ cài đặt qua tính năng môi trường hoặc công cụ, cần xác nhận tham chiếu từ dự án và phần bổ trợ trước |
| Dữ liệu phần bổ trợ và mô hình | Chương trình phần bổ trợ, dữ liệu bền vững và mô hình đi kèm | Giữ lại; gỡ hoặc đặt lại qua tính năng công cụ, không xóa tệp trực tiếp kẻo phá vỡ sổ đăng ký |
| Phiên và tệp đính kèm | Cơ sở dữ liệu, tệp đính kèm tác vụ, tệp tạo ra | Giữ lại; xóa theo vòng đời phiên, cần kiểm tra tham chiếu và bản duy nhất trước |
| Bộ nhớ đệm và tài nguyên cài đặt | Bộ nhớ đệm tải thư viện phụ thuộc, gói nguồn ngoại tuyến, vùng tạm cài đặt, bản sao lưu khôi phục phiên bản cũ | Có thể dọn một phần bộ nhớ đệm thư viện phụ thuộc cũ theo nhu cầu; không xóa hàng loạt gói nguồn cài đặt, vùng tạm và bản sao lưu |
| Ứng dụng và chẩn đoán | Nhật ký, cấu hình, kỹ năng và dữ liệu riêng khác | Nên dọn nhật ký lưu trữ đã quá hạn; nhật ký và cấu hình hiện tại giữ lại |

Tổng dung lượng chiếm dụng và dung lượng có thể dọn được hiển thị riêng. Dung lượng là kích thước logic của tệp, không thể cam kết bằng với phần ổ đĩa được cấp trong cài đặt Android hay mức tăng không gian trống của thiết bị. Lỗi khi quét sẽ được báo trên giao diện; không diễn giải tình trạng không đọc được thành "đã quét đầy đủ".

## Ranh giới dọn dẹp

- Dọn theo đề xuất chỉ bao phủ nhật ký lưu trữ `.log.gz`, `.log.数字` và `.log.数字.gz` cũ hơn 7 ngày. Việc này làm mất thông tin chẩn đoán lịch sử, nên không còn tuyên bố "không có tác dụng phụ".
- Bộ nhớ đệm thư viện phụ thuộc thuộc dạng dọn theo nhu cầu: có thể tăng thời gian tải và dựng, ảnh hưởng khả năng dùng ngoại tuyến. Chỉ xử lý tệp cũ trong đường dẫn thuộc danh sách cho phép; giữ lại tệp khóa, tệp `partial`, tệp gần đây, công cụ Pub cài toàn cục, kho pnpm, môi trường npm npx, mã nguồn đã giải nén của Go, bản phân phối Gradle Wrapper và bộ nhớ đệm đã giải nén của Yarn.
- Dọn dự án yêu cầu tệp dấu nhận diện dự án; hiện chỉ hỗ trợ tệp cũ ở gốc `.gradle` / `.dart_tool`. Không suy diễn nội dung có thể xóa dựa trên tên `build`, `target`, `dist`, `out`. Đa mô-đun và thư mục đầu ra tùy chỉnh chưa có dọn tự động.
- Bản sao lưu khôi phục phiên bản cũ khi nâng cấp và vùng tạm do giao dịch cài đặt quản lý; tệp đính kèm, dữ liệu kỹ năng, dữ liệu phần bổ trợ và gói nguồn không có nút dọn giả tạo.
- Việc thực thi dùng đúng kế hoạch tệp cụ thể từ lần quét trước; dọn theo hạng mục không mở rộng sang hạng mục khác. Tệp mới, tệp đã thay đổi, liên kết cứng và đường dẫn liên kết không nằm trong phạm vi xóa; chỉ xóa tệp thường, giữ nguyên cấu trúc thư mục.
- Trước khi chạy, kiểm tra lại định danh tệp, kích thước, thời gian sửa, số liên kết và các thư mục tổ tiên của đường dẫn. Trường hợp thất bại hoặc bỏ qua sẽ được báo rõ; ID không xác định, sai hạng mục hoặc không có kế hoạch dọn đều trả về thất bại, và việc hủy coroutine vẫn tiếp tục được truyền đi.
- Dọn dẹp loại trừ lẫn nhau với lệnh chạy qua LinuxRuntime; từ chối dọn khi đang có thiết bị đầu cuối hoạt động, dịch vụ nền, quá trình khởi tạo hoặc cài đặt môi trường; trong lúc dọn sẽ chặn lệnh mới. Các lệnh thường vẫn được phép chạy song song với nhau.

Kiểm tra và xóa không phải là giao dịch hệ thống tệp, không thể đảm bảo tuyệt đối trước các lần ghi đồng thời đi vòng qua môi trường chạy của ứng dụng. Hiện tại không tuyên bố sở hữu khóa tiến trình toàn hệ thống. Nếu sau này bổ sung dọn gói tải về, tài nguyên cài đặt phần bổ trợ hoặc tệp người dùng, phải nối vào kiểm tra giao dịch/tham chiếu của chủ sở hữu tương ứng trước, thay vì chỉ mở rộng danh sách thư mục cho phép.

## Hướng quản lý không gian hiệu quả hơn về sau

1. **Quản lý tệp lớn theo dự án**: hiển thị mã nguồn, thư viện phụ thuộc, sản phẩm và tệp xuất, kèm sắp xếp theo kích thước và điểm vào duyệt/xuất. Sản phẩm chỉ được phép dọn sau khi bộ điều phối dựng ghi nhận đường dẫn đầu ra thực tế; bản thân quy tắc bỏ qua của Git không đủ làm căn cứ cho phép xóa.
2. **Gỡ môi trường phát triển theo phiên bản**: SDK, NDK, JDK, mô hình và môi trường chạy hiển thị bên tham chiếu cùng phiên bản, phân biệt rõ "gỡ cài đặt" và "dọn bộ nhớ đệm". Không thể chỉ dựa vào tuổi của tệp để kết luận không còn dùng.
3. **Giải phóng tệp đính kèm theo phiên**: truy vấn tham chiếu qua tầng kho dữ liệu, cung cấp xóa theo thời gian, theo phiên và thu hồi tệp đính kèm cô lập; cơ sở dữ liệu do tầng nghiệp vụ xóa bản ghi và bảo trì, không xóa trực tiếp WAL/SHM.
4. **Thu hồi tài nguyên cài đặt**: giao dịch cài đặt ghi lại đường dẫn tài nguyên, trạng thái hoàn tất, nhu cầu khôi phục và chủ sở hữu. Chỉ thu hồi gói lưu trữ đã hoàn tất và không còn tham chiếu; bản sao lưu khôi phục phiên bản cũ phải nhắc rõ việc mất khả năng quay về bản cũ.
5. **Gợi ý chi phí khôi phục bộ nhớ đệm**: hiển thị lượng cần tải lại ước tính, ảnh hưởng đến việc dùng ngoại tuyến và thông tin sử dụng gần đây, cho người dùng tự chọn theo nhu cầu. Thời gian sửa tệp không phải thời gian dùng gần đây; mốc 7 ngày hiện tại chỉ là ngưỡng tuổi tệp thận trọng.

## Xác minh

`StorageManagerTest` bao phủ đường dẫn gắn thư mục cá nhân thực tế, tổng theo hạng mục và khớp mục tiêu, bảo vệ thư mục sao lưu/tạm và thư mục mã nguồn, tệp thay đổi, ID không xác định, từ chối dọn khi đang chạy, hủy coroutine và loại trừ với môi trường chạy. Kiểm thử liên kết tượng trưng sẽ được bỏ qua trên Windows không có quyền tạo, cần chạy bổ sung trên máy chủ có quyền hoặc môi trường Android/Linux. Chưa xác minh trên giao diện thiết bị thật và lượng dung lượng giải phóng thực tế.
