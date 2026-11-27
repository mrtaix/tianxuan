> pi có thể tạo prompt template. Hãy yêu cầu nó xây dựng một template cho quy trình làm việc của bạn.

# Prompt Templates

Prompt template là các đoạn Markdown được mở rộng thành prompt đầy đủ. Gõ `/name` trong editor để gọi một template, trong đó `name` là tên tệp không có phần mở rộng `.md`.

## Vị trí (Locations)

Pi tải prompt template từ:

- Toàn cục: `~/.pi/agent/prompts/*.md`
- Dự án: `.pi/prompts/*.md` (chỉ sau khi dự án được tin cậy)
- Package: các thư mục `prompts/` hoặc các mục `pi.prompts` trong `package.json`
- Settings: mảng `prompts` với các tệp hoặc thư mục
- CLI: `--prompt-template <path>` (có thể lặp lại)

Vô hiệu hóa việc khám phá bằng `--no-prompt-templates`.

## Định dạng (Format)

```markdown
---
description: Review staged git changes
---
Review the staged changes (`git diff --cached`). Focus on:
- Bugs and logic errors
- Security issues
- Error handling gaps
```

- Tên tệp trở thành tên lệnh. `review.md` trở thành `/review`.
- `description` là tùy chọn. Nếu thiếu, dòng không trống đầu tiên sẽ được dùng.
- `argument-hint` là tùy chọn. Khi được thiết lập, gợi ý sẽ hiển thị trước description trong danh sách autocomplete.

### Gợi ý đối số (Argument Hints)

Dùng `argument-hint` trong frontmatter để hiển thị các đối số dự kiến trong autocomplete. Dùng `<angle brackets>` cho đối số bắt buộc và `[square brackets]` cho đối số tùy chọn:

```markdown
---
description: Review PRs from URLs with structured issue and code analysis
argument-hint: "<PR-URL>"
---
```

Điều này hiển thị trong danh sách autocomplete như sau:

```
→ pr   <PR-URL>       — Review PRs from URLs with structured issue and code analysis
  is   <issue>        — Analyze GitHub issues (bugs or feature requests)
  wr   [instructions] — Finish the current task end-to-end
  cl   — Audit changelog entries before release
```

## Cách dùng (Usage)

Gõ `/` theo sau là tên template trong editor. Autocomplete hiển thị các template có sẵn kèm description.

```
/review                           # Expands review.md
/component Button                 # Expands with argument
/component Button "click handler" # Multiple arguments
```

## Đối số (Arguments)

Template hỗ trợ đối số vị trí, giá trị mặc định và cắt đơn giản:

- `$1`, `$2`, ... đối số vị trí
- `$@` hoặc `$ARGUMENTS` cho tất cả đối số được nối lại
- `${1:-default}` dùng đối số 1 khi có/không rỗng, nếu không dùng `default`
- `${@:-default}` hoặc `${ARGUMENTS:-default}` dùng tất cả đối số khi có/không rỗng, nếu không dùng `default`
- `${@:N}` cho các đối số từ vị trí thứ N (đánh số từ 1)
- `${@:N:L}` cho `L` đối số bắt đầu từ N

Ví dụ:

```markdown
---
description: Create a component
---
Create a React component named $1 with features: $@
```

Giá trị mặc định hữu ích cho các đối số tùy chọn:

```markdown
Summarize the current state in ${1:-7} bullet points.
```

Cách dùng: `/component Button "onClick handler" "disabled support"`

## Quy tắc tải (Loading Rules)

- Việc khám phá template trong `prompts/` không đệ quy.
- Nếu bạn muốn template nằm trong thư mục con, hãy thêm chúng một cách rõ ràng qua settings `prompts` hoặc một package manifest.
