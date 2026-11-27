# Đặc tả mẫu dự án di động của CTHarness (v1)

Mẫu CTHarness là một gói ZIP có thể nhập, xuất và chia sẻ. Người dùng có thể nhập ZIP trong "Xưởng thông minh → Thêm → Quản lý mẫu", hoặc xuất bất kỳ mẫu tích hợp sẵn nào làm ví dụ để làm theo.

## 1. Cấu trúc thư mục

Thư mục gốc của ZIP có thể chứa trực tiếp `template.json`, hoặc chỉ chứa một thư mục mẫu duy nhất:

```text
my-template/
├── template.json
├── preview.png                   # tùy chọn, cố định 270×270
├── template-hooks/             # tùy chọn
│   ├── before-create.sh
│   └── after-create.sh
└── ...tệp dự án hoặc *.template
```

Gói mẫu tối đa 5000 mục; mỗi tệp không vượt quá 16 MiB; tổng kích thước sau khi giải nén không vượt quá 128 MiB. Đường dẫn ZIP đi xuyên thư mục sẽ bị từ chối.

## 2. template.json

Cấu trúc đầy đủ được mô tả trong [project-template.schema.json](project-template.schema.json). Ví dụ tối thiểu:

```json
{
  "schemaVersion": 1,
  "id": "example.android-basic",
  "name": "Basic Android",
  "version": "1.0.0",
  "projectType": "ANDROID",
  "category": { "id": "starter", "name": "Starter", "sortOrder": 0 },
  "previewImage": "preview.png",
  "variables": []
}
```

- `id` chỉ được chứa chữ cái thường, chữ số và các dấu phân cách `.`, `_`, `-`.
- `builtin.` là tiền tố dành riêng cho mẫu tích hợp sẵn của hệ thống, mẫu do người dùng tạo không được dùng.
- `projectType` hỗ trợ `ANDROID`, `FLUTTER`, `GENERAL`.
- `category` là nhóm phân cấp thứ hai trên trang chọn mẫu; `sortOrder` càng nhỏ thì xếp càng trên.
- JSON được phân tích nghiêm ngặt; trường không xác định sẽ bị từ chối, tránh lỗi chính tả bị bỏ qua âm thầm.

## 3. Tên dự án và đường dẫn

Tên dự án và đường dẫn tạo không phải là trường động, được Xưởng thông minh cung cấp thống nhất cho mọi mẫu:

- Tên dự án là bắt buộc.
- Đường dẫn có thể để trống; mặc định không gian làm việc được tạo tại `/workspace/<项目名称>`.
- Mẫu có thể dùng các biến do hệ thống dẫn xuất `projectName`, `appName` và `projectPath`; nếu `appName` do trường động cung cấp thì ưu tiên giá trị người dùng nhập.
- Mẫu có thể khai báo `projectName` là biến cố định với `prompt: false`, và ràng buộc tên dự án bằng `validationRegex`; Xưởng thông minh sẽ hiển thị `description` làm gợi ý trong ô nhập tên dự án chung và chặn các giá trị không hợp lệ. Ví dụ mẫu Flutter có thể yêu cầu tên khớp `^[a-z][a-z0-9_]*$`.
- Xưởng thông minh không âm thầm đổi tên dự án do người dùng nhập; khi tên không đúng quy tắc của mẫu, bắt buộc người dùng phải sửa.
- `packageName`, `packagePath` chỉ được tạo và xác thực khi có khai báo trong bản kê mẫu; mẫu không phải Android không phụ thuộc vào tên gói Java.

## 4. Biến động

`variables` hỗ trợ `TEXT`, `MULTILINE`, `NUMBER`, `BOOLEAN`, `SELECT`, `SECRET`, đồng thời có thể khai báo `label`, `description`, `placeholder`, `required`, `defaultValue`, `validationRegex` và `options`.

- `prompt: true`: được hiển thị thành biểu mẫu động khi tạo dự án.
- `prompt: false`: không hiển thị; biến bắt buộc ẩn phải có `defaultValue`, trừ biến do hệ thống dẫn xuất.
- Tên biến phải khớp `^[A-Za-z][A-Za-z0-9_]*$` và không được trùng lặp.
- `SELECT` phải có ít nhất một lựa chọn không trùng nhau; `SELECT` không bắt buộc được phép để trống.

## 5. Thay thế tệp và đường dẫn

Cả nội dung văn bản và đường dẫn tương đối đều có thể dùng `{{variableName}}`, ví dụ `src/{{moduleName}}/config.json.template`. Khi tạo sẽ thay thế biến và bỏ hậu tố `.template` ở cuối.

Mọi tệp văn bản cần thay thế đều nên thêm hậu tố `.template`; các phần mở rộng nguồn và cấu hình phổ biến cũng tự động được xử lý như văn bản. Thư mục gói Android/Kotlin dùng `TIANXUAN_PACKAGE_PATH`, khi tạo sẽ được thay bằng cấp thư mục tương ứng với tên gói. Đừng dùng thư mục giữ chỗ bắt đầu bằng dấu gạch dưới, nếu không có thể bị Android AAPT bỏ qua.

Nếu khi kết thúc quá trình tạo vẫn còn `{{unknownVariable}}`, việc tạo sẽ thất bại. Mọi đường dẫn đầu ra đều được chuẩn hóa và kiểm tra vượt giới hạn.

## 6. Ảnh xem trước

Mẫu khai báo ảnh xem trước thông qua một trường duy nhất `previewImage`, không còn phân biệt điện thoại, máy tính bảng hay chiều ngang/dọc. Hỗ trợ PNG, JPEG, WebP, GIF, bắt buộc dùng đường dẫn tương đối bên trong gói.

Giới hạn cứng: tỉ lệ ảnh xem trước thống nhất là **1:1**, kích thước pixel phải là **270×270 px**, mỗi ảnh không quá 4 MiB. Nên đặt logo, tiêu đề và nội dung chính vào vùng an toàn ở giữa, tránh sát mép.

## 7. Mã lệnh đi kèm

```json
{
  "hooks": {
    "beforeCreate": "template-hooks/before-create.sh",
    "afterCreate": "template-hooks/after-create.sh"
  }
}
```

- Mã lệnh phải nằm trong `template-hooks/`, mỗi tệp không quá 1 MiB.
- Việc nhập, xem và xuất mẫu sẽ không chạy mã lệnh; thời điểm tạo dự án mới cần người dùng cấp quyền rõ ràng.
- Mã lệnh chạy trong môi trường Linux cô lập, trong thư mục dự án mới, mỗi giai đoạn tối đa 60 giây.
- `beforeCreate` chạy trước khi sao chép tệp mẫu, `afterCreate` chạy sau khi hoàn tất việc tạo tệp và thay thế biến.
- Đường dẫn dự án được cung cấp qua `TIANXUAN_PROJECT_DIR`.
- Biến được cung cấp qua `TIANXUAN_VAR_<大写变量名>`, ví dụ `packageName` tương ứng `TIANXUAN_VAR_PACKAGENAME`.
- Bất kỳ mã lệnh nào thoát với mã không phải 0 sẽ chấm dứt quá trình tạo và hiển thị đầu ra lỗi.

## 8. Kiểm chứng kết quả tạo

Mẫu có thể tự khai báo điều kiện phải thỏa mãn sau khi tạo, không phụ thuộc vào ID mẫu hay mã cứng trong ứng dụng:

```json
{
  "validation": {
    "requiredFiles": ["app/src/main/java/TIANXUAN_PACKAGE_PATH/MainHook.kt"],
    "forbiddenFiles": ["app/src/main/java/TIANXUAN_PACKAGE_PATH/LegacyHook.kt"],
    "contentRules": [
      {
        "path": "app/src/main/assets/xposed_init",
        "equals": "{{packageName}}.MainHook"
      }
    ]
  }
}
```

- `requiredFiles`: tệp phải tồn tại.
- `forbiddenFiles`: tệp hoặc thư mục không được tạo.
- `contentRules`: hỗ trợ các quy tắc văn bản `equals`, `contains`, `excludes`.
- Cả đường dẫn và nội dung mong đợi đều hỗ trợ biến mẫu và `TIANXUAN_PACKAGE_PATH`.
- Việc kiểm chứng chạy sau khi mã lệnh `afterCreate` kết thúc, vì vậy mã lệnh có thể tham gia vào quá trình kiến tạo dự án cuối cùng.

## 9. Nhập, xuất và chia sẻ

Khi nhập sẽ kiểm chứng bản kê, biến, phân loại, ảnh xem trước, đường dẫn mã lệnh và giới hạn an toàn của ZIP. Mẫu của người dùng được cài đặt vào thư mục mẫu riêng của ứng dụng, không ghi đè mẫu tích hợp sẵn.

Trang quản lý mẫu hỗ trợ nhập ZIP của người dùng, xuất mẫu của người dùng hoặc mẫu tích hợp sẵn, và xóa mẫu người dùng; mẫu tích hợp sẵn không thể xóa. Khi dùng mẫu tích hợp sẵn làm ví dụ để tạo mẫu mới, bắt buộc phải đổi `id` và bỏ tiền tố dành riêng `builtin.`.

Mẫu tích hợp sẵn của ứng dụng được tự động phát hiện từ `project-template/src/main/assets/templates/<项目类型>/<模板目录>/template.json`; thêm thư mục chuẩn không cần sửa sổ đăng ký Kotlin.
