> pi có thể tạo skill. Hãy yêu cầu nó xây dựng một skill cho trường hợp sử dụng của bạn.

# Skill

Skill là các gói năng lực độc lập được agent tải theo yêu cầu. Một skill cung cấp quy trình chuyên biệt, hướng dẫn thiết lập, script hỗ trợ và tài liệu tham khảo cho các tác vụ cụ thể.

Pi triển khai [Agent Skills standard](https://agentskills.io/specification), cảnh báo về hầu hết các vi phạm nhưng vẫn linh hoạt. Pi cho phép tên skill khác với thư mục cha dù chuẩn không cho phép điều đó; quy tắc đó là chưa tối ưu đối với các thư mục skill dùng chung across nhiều agent harness.

## Mục lục (Table of Contents)

- [Locations](#locations)
- [How Skills Work](#how-skills-work)
- [Skill Commands](#skill-commands)
- [Skill Structure](#skill-structure)
- [Frontmatter](#frontmatter)
- [Validation](#validation)
- [Example](#example)
- [Skill Repositories](#skill-repositories)

## Vị trí (Locations)

> **Bảo mật:** Skill có thể chỉ thị model thực hiện bất kỳ hành động nào và có thể chứa mã thực thi mà model gọi. Hãy xem lại nội dung skill trước khi dùng.

Pi tải skill từ:

- Toàn cục:
  - `~/.pi/agent/skills/`
  - `~/.agents/skills/`
- Dự án (chỉ sau khi dự án được tin cậy):
  - `.pi/skills/`
  - `.agents/skills/` trong `cwd` và các thư mục cha (đến gốc repo git, hoặc gốc filesystem khi không nằm trong repo)
- Package: các thư mục `skills/` hoặc các mục `pi.skills` trong `package.json`
- Settings: mảng `skills` với các tệp hoặc thư mục
- CLI: `--skill <path>` (có thể lặp lại, mang tính cộng dồn ngay cả với `--no-skills`)

Quy tắc khám phá:
- Trong `~/.pi/agent/skills/` và `.pi/skills/`, các tệp `.md` ngay tại gốc được khám phá như skill riêng lẻ khi chúng có frontmatter skill hợp lệ với `description` không rỗng
- Trong tất cả vị trí skill, các thư mục chứa `SKILL.md` được khám phá đệ quy
- Trong `~/.agents/skills/` và project `.agents/skills/`, các tệp `.md` ở gốc bị bỏ qua, nhưng các tệp `.md` lồng trong thư mục nhóm được khám phá khi chúng khai báo frontmatter skill
- Các tệp Markdown gốc khác ngoài `SKILL.md` mà không trông như skill sẽ bị bỏ qua im lặng

Vô hiệu hóa việc khám phá bằng `--no-skills` (các đường dẫn `--skill` rõ ràng vẫn được tải).

### Dùng skill từ harness khác

Để dùng skill từ Claude Code hoặc OpenAI Codex, hãy thêm thư mục của chúng vào settings:

```json
{
  "skills": [
    "~/.claude/skills",
    "~/.codex/skills"
  ]
}
```

Đối với skill Claude Code ở cấp dự án, hãy thêm vào `.pi/settings.json`:

```json
{
  "skills": ["../.claude/skills"]
}
```

## Cách hoạt động của Skill

1. Lúc khởi động, pi quét các vị trí skill và trích xuất tên cùng description
2. System prompt bao gồm các skill có sẵn ở định dạng XML theo [specification](https://agentskills.io/integrate-skills)
3. Khi tác vụ khớp, agent dùng `read`, hoặc `bash` khi `read` không khả dụng, để tải toàn bộ SKILL.md (model không phải lúc nào cũng làm vậy; hãy dùng prompt hoặc `/skill:name` để ép thực hiện)
4. Agent làm theo hướng dẫn, dùng đường dẫn tương đối để tham chiếu script và tài nguyên

Đây là progressive disclosure: chỉ description luôn nằm trong context, hướng dẫn đầy đủ được tải theo yêu cầu.

## Lệnh Skill (Skill Commands)

Skill đăng ký thành lệnh `/skill:name`:

```bash
/skill:brave-search           # Load and execute the skill
/skill:pdf-tools extract      # Load skill with arguments
```

Đối số sau lệnh được nối vào nội dung skill dưới dạng `User: <args>`.

Bật/tắt lệnh skill qua `/settings` trong chế độ tương tác hoặc trong `settings.json`:

```json
{
  "enableSkillCommands": true
}
```

## Cấu trúc Skill (Skill Structure)

Một skill là thư mục chứa tệp `SKILL.md`. Mọi thứ khác đều tùy ý.

```
my-skill/
├── SKILL.md              # Required: frontmatter + instructions
├── scripts/              # Helper scripts
│   └── process.sh
├── references/           # Detailed docs loaded on-demand
│   └── api-reference.md
└── assets/
    └── template.json
```

### Định dạng SKILL.md

````markdown
---
name: my-skill
description: What this skill does and when to use it. Be specific.
---

# My Skill

## Setup

Run once before first use:
```bash
cd /path/to/skill && npm install
```

## Usage

```bash
./scripts/process.sh <input>
```
````

Dùng đường dẫn tương đối từ thư mục skill:

```markdown
See [the reference guide](references/REFERENCE.md) for details.
```

## Frontmatter

Theo [Agent Skills specification](https://agentskills.io/specification#frontmatter-required):

| Field | Required | Description |
|-------|----------|-------------|
| `name` | Yes | Max 64 chars. Lowercase a-z, 0-9, hyphens. Unlike the standard, Pi does not require this to match the parent directory because that standard requirement is suboptimal for shared skill directories. |
| `description` | Yes | Max 1024 chars. What the skill does and when to use it. |
| `license` | No | License name or reference to bundled file. |
| `compatibility` | No | Max 500 chars. Environment requirements. |
| `metadata` | No | Arbitrary key-value mapping. |
| `allowed-tools` | No | Space-delimited list of pre-approved tools (experimental). |
| `disable-model-invocation` | No | When `true`, skill is hidden from system prompt. Users must use `/skill:name`. |

### Quy tắc đặt tên (Name Rules)

- 1-64 ký tự
- Chỉ chữ cái thường, số và dấu gạch ngang
- Không có dấu gạch ngang ở đầu/cuối
- Không có dấu gạch ngang liên tiếp
Pi không yêu cầu tên phải khớp với thư mục cha. Chuẩn Agent Skills thì có, nhưng yêu cầu đó là chưa tối ưu cho các thư mục skill dùng chung bởi nhiều tool.

Hợp lệ: `pdf-processing`, `data-analysis`, `code-review`
Không hợp lệ: `PDF-Processing`, `-pdf`, `pdf--processing`

### Thực hành tốt nhất cho description

Description quyết định thời điểm agent tải skill. Hãy cụ thể.

Tốt:
```yaml
description: Extracts text and tables from PDF files, fills PDF forms, and merges multiple PDFs. Use when working with PDF documents.
```

Kém:
```yaml
description: Helps with PDFs.
```

## Kiểm tra hợp lệ (Validation)

Pi kiểm tra skill theo chuẩn Agent Skills. Hầu hết vấn đề tạo cảnh báo nhưng vẫn tải skill:

- Tên vượt quá 64 ký tự hoặc chứa ký tự không hợp lệ
- Tên bắt/kết thúc bằng dấu gạch ngang hoặc có dấu gạch ngang liên tiếp
- Description vượt quá 1024 ký tự

Các trường frontmatter không xác định được bỏ qua.

Skill đã khai báo nhưng thiếu description sẽ không được tải. Tệp `SKILL.md` sai định dạng và tệp `SKILL.md` không có description tạo cảnh báo và không được tải. Các tệp Markdown khác không có frontmatter skill hợp lệ được bỏ qua.

Trùng tên (cùng tên từ các vị trí khác nhau) tạo cảnh báo và giữ skill tìm thấy đầu tiên.

## Ví dụ (Example)

```
brave-search/
├── SKILL.md
├── search.js
└── content.js
```

**SKILL.md:**
````markdown
---
name: brave-search
description: Web search and content extraction via Brave Search API. Use for searching documentation, facts, or any web content.
---

# Brave Search

## Setup

```bash
cd /path/to/brave-search && npm install
```

## Search

```bash
./search.js "query"              # Basic search
./search.js "query" --content    # Include page content
```

## Extract Page Content

```bash
./content.js https://example.com
```
````

## Kho skill (Skill Repositories)

- [Anthropic Skills](https://github.com/anthropics/skills) - Document processing (docx, pdf, pptx, xlsx), web development
- [Pi Skills](https://github.com/badlogic/pi-skills) - Web search, browser automation, Google APIs, transcription
