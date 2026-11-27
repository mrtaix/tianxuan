# Session

Pi lưu các cuộc hội thoại thành session để bạn có thể tiếp tục công việc, rẽ nhánh từ các lượt trước và xem lại các đường dẫn đã đi qua.

## Lưu trữ Session (Session Storage)

Session tự động lưu vào `~/.pi/agent/sessions/`, được tổ chức theo thư mục làm việc. Mỗi session là một tệp JSONL với cấu trúc dạng cây.

```bash
pi -c                  # Continue most recent session
pi -r                  # Browse and select from past sessions
pi --no-session        # Ephemeral mode; do not save
pi --name "my task"    # Set session display name at startup
pi --session <path|id> # Use a specific session file or partial session ID
pi --fork <path|id>    # Fork a session file or partial session ID into a new session
```

Dùng `/session` trong chế độ tương tác để xem tệp session hiện tại, session ID, số tin nhắn, token và chi phí.

Để biết định dạng tệp JSONL và SessionManager API, xem [Session Format](session-format.md).

## Lệnh Session (Session Commands)

| Command | Description |
|---------|-------------|
| `/resume` | Browse and select previous sessions |
| `/new` | Start a new session |
| `/name <name>` | Set the current session display name |
| `/session` | Show session info |
| `/tree` | Navigate the current session tree |
| `/fork` | Create a new session from a previous user message |
| `/clone` | Duplicate the current active branch into a new session |
| `/compact [prompt]` | Summarize older context; see [Compaction](compaction.md) |
| `/export [file]` | Export session to HTML |
| `/share` | Upload as private GitHub gist with shareable HTML link |

## Tiếp tục và Xóa Session

`/resume` mở bộ chọn session tương tác cho dự án hiện tại. `pi -r` mở cùng bộ chọn này lúc khởi động.

Trong bộ chọn bạn có thể:

- tìm kiếm bằng cách gõ
- chuyển đổi hiển thị đường dẫn bằng Ctrl+P
- chuyển đổi chế độ sắp xếp bằng Ctrl+S
- lọc chỉ các session có tên bằng Ctrl+N
- đổi tên bằng Ctrl+R
- xóa bằng Ctrl+D, rồi xác nhận

Khi có sẵn, pi dùng CLI `trash` để xóa thay vì xóa vĩnh viễn các tệp.

## Đặt tên Session

Dùng `/name <name>` để đặt tên session dễ đọc cho con người:

```text
/name Refactor auth module
```

Đặt tên lúc khởi động với `--name` hoặc `-n`:

```bash
pi --name "Refactor auth module"
pi --name "CI audit" -p "Review this build failure"
```

Session có tên dễ tìm hơn trong `/resume` và `pi -r`.

## Rẽ nhánh với `/tree`

Session được lưu dưới dạng cây. Mỗi entry có `id` và `parentId`, và vị trí hiện tại là leaf đang hoạt động. `/tree` cho phép bạn nhảy tới bất kỳ điểm trước đó và tiếp tục từ đó mà không tạo tệp mới.

<p align="center"><img src="images/tree-view.png" alt="Tree View" width="600"></p>

Ví dụ về hình dạng:

```text
├─ user: "Hello, can you help..."
│  └─ assistant: "Of course! I can..."
│     ├─ user: "Let's try approach A..."
│     │  └─ assistant: "For approach A..."
│     │     └─ user: "That worked..."  ← active
│     └─ user: "Actually, approach B..."
│        └─ assistant: "For approach B..."
```

### Điều khiển cây (Tree Controls)

| Key | Action |
|-----|--------|
| ↑/↓ | Navigate visible entries |
| ←/→ | Page up/down |
| Ctrl+←/Ctrl+→ or Alt+←/Alt+→ | Fold/unfold or jump between branch segments |
| Shift+L | Set or clear a label on the selected entry |
| Shift+T | Toggle label timestamps |
| Enter | Select entry |
| Escape/Ctrl+C | Cancel |
| Ctrl+O | Cycle filter mode |

Các chế độ lọc là: default, no-tools, user-only, labeled-only và all. Cấu hình giá trị mặc định với `treeFilterMode` trong [Settings](settings.md).

### Hành vi chọn (Selection Behavior)

Chọn một tin nhắn user hoặc custom:

1. Di chuyển leaf tới phần tử cha của tin nhắn đã chọn.
2. Đặt nội dung tin nhắn đã chọn vào editor.
3. Cho phép bạn sửa và gửi lại, tạo một nhánh mới.

Chọn một entry assistant, tool, compaction hoặc entry phi-user khác:

1. Di chuyển leaf tới entry đó.
2. Để editor trống.
3. Cho phép bạn tiếp tục từ điểm đó.

Chọn tin nhắn user gốc sẽ đặt lại leaf về một cuộc hội thoại trống và đặt prompt ban đầu vào editor.

## `/tree`, `/fork`, và `/clone`

| Feature | `/tree` | `/fork` | `/clone` |
|---------|---------|---------|----------|
| Output | Same session file | New session file | New session file |
| View | Full tree | User-message selector | Current active branch |
| Typical use | Explore alternatives in place | Start a new session from an earlier prompt | Duplicate current work before continuing |
| Summary | Optional branch summary | None | None |

Dùng `/tree` khi bạn muốn giữ các phương án thay thế cùng nhau. Dùng `/fork` hoặc `/clone` khi bạn muốn một tệp session riêng biệt.

## Branch Summary

Khi `/tree` chuyển từ một nhánh này sang nhánh khác, pi có thể tóm tắt nhánh bị bỏ lại và đính kèm bản tóm tắt đó tại vị trí mới. Điều này bảo toàn ngữ cảnh quan trọng từ đường dẫn bạn đã rời đi mà không cần phát lại toàn bộ nhánh.

Khi được nhắc, hãy chọn một trong các phương án:

1. không tóm tắt
2. tóm tắt với prompt mặc định
3. tóm tắt với hướng dẫn tập trung tùy chỉnh

Xem [Compaction](compaction.md) để biết chi tiết branch summarization và các hook extension.

## Định dạng Session (Session Format)

Tệp session là JSONL và chứa các entry tin nhắn, thay đổi model, thay đổi thinking-level, nhãn, compaction, branch summary và entry extension.

Để biết parser, extension, cách dùng SDK và SessionManager API đầy đủ, xem [Session Format](session-format.md).
