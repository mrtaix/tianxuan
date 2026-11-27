# Chế độ RPC

Chế độ RPC cho phép vận hành coding agent ở chế độ không giao diện (headless) thông qua một giao thức JSON trên stdin/stdout. Điều này hữu ích khi nhúng agent vào các ứng dụng khác, IDE, hoặc các UI tùy chỉnh.

**Lưu ý cho người dùng Node.js/TypeScript**: Nếu bạn đang xây dựng một ứng dụng Node.js, hãy cân nhắc dùng trực tiếp `AgentSession` từ `@earendil-works/pi-coding-agent` thay vì khởi chạy một tiến trình con (subprocess). Xem [`src/core/agent-session.ts`](../src/core/agent-session.ts) để biết API. Đối với client TypeScript dựa trên subprocess, xem [`src/modes/rpc/rpc-client.ts`](../src/modes/rpc/rpc-client.ts).

## Khởi động chế độ RPC

```bash
pi --mode rpc [options]
```

Các tùy chọn thường dùng:
- `--provider <name>`: Đặt nhà cung cấp LLM (anthropic, openai, google, v.v.)
- `--model <pattern>`: Mẫu hoặc ID của model (hỗ trợ `provider/id` và `:<thinking>` tùy chọn)
- `--name <name>` / `-n <name>`: Đặt tên hiển thị của session lúc khởi động
- `--no-session`: Tắt lưu trữ session
- `--session-dir <path>`: Thư mục lưu trữ session tùy chỉnh

## Tổng quan giao thức

- **Commands**: Các đối tượng JSON gửi tới stdin, mỗi đối tượng trên một dòng
- **Responses**: Các đối tượng JSON với `type: "response"` cho biết command thành công hay thất bại
- **Events**: Các sự kiện của agent được phát liên tục ra stdout dưới dạng các dòng JSON

Tất cả command đều hỗ trợ trường `id` tùy chọn để đối chiếu giữa yêu cầu và phản hồi. Nếu có, response tương ứng sẽ chứa cùng `id` đó. Các sự kiện `bash_execution_update` cũng chứa `id` của command `bash` đã phát sinh ra chúng.

### Đóng khung dữ liệu (Framing)

Chế độ RPC dùng ngữ nghĩa JSONL nghiêm ngặt, với LF (`\n`) là dấu phân tách bản ghi duy nhất.

Điều này quan trọng đối với client:
- Chỉ tách bản ghi tại `\n`
- Chấp nhận đầu vào `\r\n` tùy chọn bằng cách bỏ `\r` ở cuối
- Không dùng các trình đọc dòng thông thường coi các ký tự phân tách Unicode là xuống dòng

Cụ thể, `readline` của Node không tuân thủ giao thức ở chế độ RPC vì nó cũng tách tại `U+2028` và `U+2029`, vốn là các ký tự hợp lệ bên trong chuỗi JSON.

## Commands

### Nhắc lệnh (Prompting)

#### prompt

Gửi một prompt của người dùng tới agent. Response của command được phát sau khi prompt được chấp nhận, đưa vào hàng đợi, hoặc xử lý ngay. Các event tiếp tục được phát bất đồng bộ sau khi đã chấp nhận.

```json
{"id": "req-1", "type": "prompt", "message": "Hello, world!"}
```

Kèm hình ảnh:
```json
{"type": "prompt", "message": "What's in this image?", "images": [{"type": "image", "data": "base64-encoded-data", "mimeType": "image/png"}]}
```

**Trong lúc phát streaming**: Nếu agent đang streaming, bạn phải chỉ định `streamingBehavior` để đưa tin nhắn vào hàng đợi:

```json
{"type": "prompt", "message": "New instruction", "streamingBehavior": "steer"}
```

- `"steer"`: Đưa tin nhắn vào hàng đợi trong khi agent đang chạy. Tin nhắn được gửi sau khi lượt assistant hiện tại kết thúc thực thi các tool call, trước lần gọi LLM tiếp theo.
- `"followUp"`: Chờ đến khi agent kết thúc. Tin nhắn chỉ được gửi khi agent dừng lại.

Nếu agent đang streaming và không chỉ định `streamingBehavior`, command sẽ trả về lỗi.

**Extension commands**: Nếu tin nhắn là một extension command (ví dụ `/mycommand`), nó được thực thi ngay lập tức ngay cả trong lúc streaming. Các extension command tự quản lý tương tác LLM của riêng chúng qua `pi.sendMessage()`.

**Mở rộng đầu vào**: Các skill command (`/skill:name`) và prompt template (`/template`) được mở rộng trước khi gửi/đưa vào hàng đợi.

Response:
```json
{"id": "req-1", "type": "response", "command": "prompt", "success": true}
```

`success: true` nghĩa là prompt đã được chấp nhận, đưa vào hàng đợi, hoặc xử lý ngay. `success: false` nghĩa là prompt bị từ chối trước khi được chấp nhận. Các lỗi sau khi chấp nhận được báo qua luồng event và message thông thường, không phải bằng một `response` thứ hai cho cùng một request id.

Trường `images` là tùy chọn. Mỗi hình ảnh dùng định dạng `ImageContent`: `{"type": "image", "data": "base64-encoded-data", "mimeType": "image/png"}`.

#### steer

Đưa một steering message vào hàng đợi trong khi agent đang chạy. Tin nhắn được gửi sau khi lượt assistant hiện tại kết thúc thực thi các tool call, trước lần gọi LLM tiếp theo. Các skill command và prompt template được mở rộng. Không cho phép extension command (hãy dùng `prompt` thay thế).

```json
{"type": "steer", "message": "Stop and do this instead"}
```

Kèm hình ảnh:
```json
{"type": "steer", "message": "Look at this instead", "images": [{"type": "image", "data": "base64-encoded-data", "mimeType": "image/png"}]}
```

Trường `images` là tùy chọn. Mỗi hình ảnh dùng định dạng `ImageContent` (giống như `prompt`).

Response:
```json
{"type": "response", "command": "steer", "success": true}
```

Xem [set_steering_mode](#set_steering_mode) để kiểm soát cách xử lý các steering message.

#### follow_up

Đưa một follow-up message vào hàng đợi để xử lý sau khi agent kết thúc. Chỉ gửi khi agent không còn tool call hay steering message nào nữa. Các skill command và prompt template được mở rộng. Không cho phép extension command (hãy dùng `prompt` thay thế).

```json
{"type": "follow_up", "message": "After you're done, also do this"}
```

Kèm hình ảnh:
```json
{"type": "follow_up", "message": "Also check this image", "images": [{"type": "image", "data": "base64-encoded-data", "mimeType": "image/png"}]}
```

Trường `images` là tùy chọn. Mỗi hình ảnh dùng định dạng `ImageContent` (giống như `prompt`).

Response:
```json
{"type": "response", "command": "follow_up", "success": true}
```

Xem [set_follow_up_mode](#set_follow_up_mode) để kiểm soát cách xử lý các follow-up message.

#### abort

Hủy thao tác hiện tại và chờ session về trạng thái rỗi (idle) trước khi phản hồi.

```json
{"type": "abort"}
```

Response:
```json
{"type": "response", "command": "abort", "success": true}
```

#### clear_queue

Xóa các steering và follow-up message đang ở hàng đợi và trả về nội dung văn bản của chúng.

```json
{"type": "clear_queue"}
```

Response:
```json
{
  "type": "response",
  "command": "clear_queue",
  "success": true,
  "data": {
    "steering": ["Change direction"],
    "followUp": ["Summarize when finished"]
  }
}
```

Để triển khai hành vi Esc tương tác, hãy gửi `clear_queue` trước `abort`, rồi khôi phục văn bản đã trả về trong trình soạn thảo của client. `abort` sẽ tiếp tục các tin nhắn trong hàng đợi nếu chúng vẫn còn trong session.

#### new_session

Bắt đầu một session hoàn toàn mới. Có thể bị hủy bởi một extension event handler `session_before_switch`.

```json
{"type": "new_session"}
```

Kèm theo dõi session cha tùy chọn:
```json
{"type": "new_session", "parentSession": "/path/to/parent-session.jsonl"}
```

Response:
```json
{"type": "response", "command": "new_session", "success": true, "data": {"cancelled": false}}
```

Nếu một extension hủy bỏ:
```json
{"type": "response", "command": "new_session", "success": true, "data": {"cancelled": true}}
```

### Trạng thái (State)

#### get_state

Lấy trạng thái session hiện tại.

```json
{"type": "get_state"}
```

Response:
```json
{
  "type": "response",
  "command": "get_state",
  "success": true,
  "data": {
    "model": {...},
    "thinkingLevel": "medium",
    "isStreaming": false,
    "isCompacting": false,
    "steeringMode": "all",
    "followUpMode": "one-at-a-time",
    "sessionFile": "/path/to/session.jsonl",
    "sessionId": "abc123",
    "sessionName": "my-feature-work",
    "autoCompactionEnabled": true,
    "messageCount": 5,
    "pendingMessageCount": 0
  }
}
```

Trường `model` là một đối tượng [Model](#model) đầy đủ hoặc `null`. Trường `sessionName` là tên hiển thị được đặt qua `set_session_name`, hoặc bị bỏ qua nếu chưa đặt.

#### get_messages

Lấy tất cả tin nhắn trong cuộc hội thoại.

```json
{"type": "get_messages"}
```

Response:
```json
{
  "type": "response",
  "command": "get_messages",
  "success": true,
  "data": {"messages": [...]}
}
```

Các tin nhắn là đối tượng `AgentMessage` (xem [Message Types](#message-types)).

### Model

#### set_model

Chuyển sang một model cụ thể.

```json
{"type": "set_model", "provider": "anthropic", "modelId": "claude-sonnet-4-20250514"}
```

Response chứa đối tượng [Model](#model) đầy đủ:
```json
{
  "type": "response",
  "command": "set_model",
  "success": true,
  "data": {...}
}
```

#### cycle_model

Chuyển sang model tiếp theo có sẵn. Trả về `null` trong `data` nếu chỉ có một model.

```json
{"type": "cycle_model"}
```

Response:
```json
{
  "type": "response",
  "command": "cycle_model",
  "success": true,
  "data": {
    "model": {...},
    "thinkingLevel": "medium",
    "isScoped": false
  }
}
```

Trường `model` là một đối tượng [Model](#model) đầy đủ.

#### get_available_models

Liệt kê tất cả model đã được cấu hình.

```json
{"type": "get_available_models"}
```

Response chứa một mảng gồm các đối tượng [Model](#model) đầy đủ:
```json
{
  "type": "response",
  "command": "get_available_models",
  "success": true,
  "data": {
    "models": [...]
  }
}
```

### Tư duy (Thinking)

#### set_thinking_level

Đặt mức độ suy luận/tư duy cho các model hỗ trợ tính năng này.

```json
{"type": "set_thinking_level", "level": "high"}
```

Các mức: `"off"`, `"minimal"`, `"low"`, `"medium"`, `"high"`, `"xhigh"`, `"max"`

`"xhigh"` và `"max"` chỉ được cung cấp khi model được chọn hỗ trợ. Một số model, bao gồm GPT-5.6, cung cấp cả hai mức.

Response:
```json
{"type": "response", "command": "set_thinking_level", "success": true}
```

#### cycle_thinking_level

Chuyển tuần tự qua các mức tư duy có sẵn. Trả về `null` trong `data` nếu model không hỗ trợ tư duy.

```json
{"type": "cycle_thinking_level"}
```

Response:
```json
{
  "type": "response",
  "command": "cycle_thinking_level",
  "success": true,
  "data": {"level": "high"}
}
```

#### get_available_thinking_levels

Liệt kê các mức tư duy mà model hiện tại hỗ trợ. Trả về `["off"]` cho model không hỗ trợ suy luận.

```json
{"type": "get_available_thinking_levels"}
```

Response:
```json
{
  "type": "response",
  "command": "get_available_thinking_levels",
  "success": true,
  "data": {
    "levels": ["off", "minimal", "low", "medium", "high"]
  }
}
```

### Chế độ hàng đợi (Queue Modes)

#### set_steering_mode

Kiểm soát cách các steering message (từ `steer`) được gửi đi.

```json
{"type": "set_steering_mode", "mode": "one-at-a-time"}
```

Các chế độ:
- `"all"`: Gửi tất cả steering message sau khi lượt assistant hiện tại kết thúc thực thi các tool call
- `"one-at-a-time"`: Gửi một steering message cho mỗi lượt assistant hoàn tất (mặc định)

Response:
```json
{"type": "response", "command": "set_steering_mode", "success": true}
```

#### set_follow_up_mode

Kiểm soát cách các follow-up message (từ `follow_up`) được gửi đi.

```json
{"type": "set_follow_up_mode", "mode": "one-at-a-time"}
```

Các chế độ:
- `"all"`: Gửi tất cả follow-up message khi agent kết thúc
- `"one-at-a-time"`: Gửi một follow-up message cho mỗi lần agent hoàn tất (mặc định)

Response:
```json
{"type": "response", "command": "set_follow_up_mode", "success": true}
```

### Nén context (Compaction)

#### compact

Nén context của cuộc hội thoại theo cách thủ công để giảm lượng token sử dụng.

```json
{"type": "compact"}
```

Kèm chỉ dẫn tùy chỉnh:
```json
{"type": "compact", "customInstructions": "Focus on code changes"}
```

Response:
```json
{
  "type": "response",
  "command": "compact",
  "success": true,
  "data": {
    "summary": "Summary of conversation...",
    "firstKeptEntryId": "abc123",
    "tokensBefore": 150000,
    "estimatedTokensAfter": 32000,
    "usage": {
      "input": 32000,
      "output": 1200,
      "cacheRead": 0,
      "cacheWrite": 0,
      "totalTokens": 33200,
      "cost": {"input": 0.01, "output": 0.02, "cacheRead": 0, "cacheWrite": 0, "total": 0.03}
    },
    "details": {}
  }
}
```

`estimatedTokensAfter` là ước tính heuristic trên context message được xây dựng lại ngay sau khi nén, không phải số token chính xác từ nhà cung cấp. `usage` báo cáo lần (hoặc các lần) gọi LLM đã tạo ra bản tóm tắt và có thể bị bỏ qua bởi các handler nén tùy chỉnh.

#### set_auto_compaction

Bật hoặc tắt tính năng nén tự động khi context gần đầy.

```json
{"type": "set_auto_compaction", "enabled": true}
```

Response:
```json
{"type": "response", "command": "set_auto_compaction", "success": true}
```

### Thử lại (Retry)

#### set_auto_retry

Bật hoặc tắt tính năng tự động thử lại khi gặp lỗi tạm thời (quá tải, giới hạn tốc độ, lỗi 5xx).

```json
{"type": "set_auto_retry", "enabled": true}
```

Response:
```json
{"type": "response", "command": "set_auto_retry", "success": true}
```

#### abort_retry

Hủy một lần thử lại đang diễn ra (hủy khoảng chờ và dừng thử lại).

```json
{"type": "abort_retry"}
```

Response:
```json
{"type": "response", "command": "abort_retry", "success": true}
```

### Bash

#### bash

Thực thi một lệnh shell và thêm đầu ra vào context của cuộc hội thoại. Đầu ra được phát dưới dạng các event `bash_execution_update` trong lệnh đang chạy; response chứa kết quả cuối cùng.

```json
{"id": "req-1", "type": "bash", "command": "ls -la"}
```

Hãy kèm `id` để liên kết các event `bash_execution_update` được phát liên tục với command này.

Response:
```json
{
  "id": "req-1",
  "type": "response",
  "command": "bash",
  "success": true,
  "data": {
    "output": "total 48\ndrwxr-xr-x ...",
    "exitCode": 0,
    "cancelled": false,
    "truncated": false
  }
}
```

Nếu đầu ra bị cắt bớt, response sẽ chứa `fullOutputPath`:
```json
{
  "type": "response",
  "command": "bash",
  "success": true,
  "data": {
    "output": "truncated output...",
    "exitCode": 0,
    "cancelled": false,
    "truncated": true,
    "fullOutputPath": "/tmp/pi-bash-abc123.log"
  }
}
```

**Cách kết quả bash đến với LLM:**

Command `bash` được thực thi ngay lập tức và trả về một `BashResult`. Bên trong, một `BashExecutionMessage` được tạo và lưu vào trạng thái message của agent.

Khi command `prompt` tiếp theo được gửi, tất cả message (bao gồm `BashExecutionMessage`) được chuyển đổi trước khi gửi tới LLM. `BashExecutionMessage` được chuyển thành một `UserMessage` với định dạng sau:

````
Ran `ls -la`
```
total 48
drwxr-xr-x ...
```
````

Điều này có nghĩa là:
1. Đầu ra bash được đưa vào context của LLM ở **lần prompt tiếp theo**, không phải ngay lập tức
2. Có thể thực thi nhiều command bash trước một prompt; tất cả đầu ra sẽ được đưa vào

#### abort_bash

Hủy một command bash đang chạy.

```json
{"type": "abort_bash"}
```

Response:
```json
{"type": "response", "command": "abort_bash", "success": true}
```

### Session

#### get_session_stats

Lấy số liệu sử dụng token, thống kê chi phí và mức sử dụng context window hiện tại.

```json
{"type": "get_session_stats"}
```

Response:
```json
{
  "type": "response",
  "command": "get_session_stats",
  "success": true,
  "data": {
    "sessionFile": "/path/to/session.jsonl",
    "sessionId": "abc123",
    "userMessages": 5,
    "assistantMessages": 5,
    "toolCalls": 12,
    "toolResults": 12,
    "totalMessages": 22,
    "tokens": {
      "input": 50000,
      "output": 10000,
      "cacheRead": 40000,
      "cacheWrite": 5000,
      "total": 105000
    },
    "cost": 0.45,
    "contextUsage": {
      "tokens": 60000,
      "contextWindow": 200000,
      "percent": 30
    }
  }
}
```

`tokens` và `cost` bao gồm các message của assistant, mức sử dụng do tool báo cáo, và việc tạo bản tóm tắt nén/tóm tắt nhánh trong toàn bộ session. `contextUsage` chứa ước tính context-window thực tế hiện tại được dùng cho việc nén và hiển thị footer.

`contextUsage` bị bỏ qua khi không có model hoặc context window nào. `contextUsage.tokens` và `contextUsage.percent` là `null` ngay sau khi nén cho đến khi một phản hồi assistant mới sau khi nén cung cấp dữ liệu sử dụng hợp lệ.

#### export_html

Xuất session ra một tệp HTML.

```json
{"type": "export_html"}
```

Kèm đường dẫn tùy chỉnh:
```json
{"type": "export_html", "outputPath": "/tmp/session.html"}
```

Response:
```json
{
  "type": "response",
  "command": "export_html",
  "success": true,
  "data": {"path": "/tmp/session.html"}
}
```

#### switch_session

Tải một tệp session khác. Có thể bị hủy bởi một extension event handler `session_before_switch`.

```json
{"type": "switch_session", "sessionPath": "/path/to/session.jsonl"}
```

Response:
```json
{"type": "response", "command": "switch_session", "success": true, "data": {"cancelled": false}}
```

Nếu một extension hủy bỏ việc chuyển session:
```json
{"type": "response", "command": "switch_session", "success": true, "data": {"cancelled": true}}
```

#### fork

Tạo một nhánh (fork) mới từ một message của người dùng trước đó trên nhánh đang hoạt động. Có thể bị hủy bởi một extension event handler `session_before_fork`. Trả về văn bản của message được fork.

```json
{"type": "fork", "entryId": "abc123"}
```

Response:
```json
{
  "type": "response",
  "command": "fork",
  "success": true,
  "data": {"text": "The original prompt text...", "cancelled": false}
}
```

Nếu một extension hủy bỏ việc fork:
```json
{
  "type": "response",
  "command": "fork",
  "success": true,
  "data": {"text": "The original prompt text...", "cancelled": true}
}
```

#### clone

Sao chép nhánh đang hoạt động hiện tại thành một session mới tại vị trí hiện tại. Có thể bị hủy bởi một extension event handler `session_before_fork`.

```json
{"type": "clone"}
```

Response:
```json
{
  "type": "response",
  "command": "clone",
  "success": true,
  "data": {"cancelled": false}
}
```

Nếu một extension hủy bỏ việc clone:
```json
{
  "type": "response",
  "command": "clone",
  "success": true,
  "data": {"cancelled": true}
}
```

#### get_fork_messages

Lấy các message của người dùng có thể dùng để fork.

```json
{"type": "get_fork_messages"}
```

Response:
```json
{
  "type": "response",
  "command": "get_fork_messages",
  "success": true,
  "data": {
    "messages": [
      {"entryId": "abc123", "text": "First prompt..."},
      {"entryId": "def456", "text": "Second prompt..."}
    ]
  }
}
```

#### get_entries

Lấy tất cả entry của session theo thứ tự thêm vào (không tính header của session). Session là một cây chỉ thêm (append-only) gồm các entry với id ổn định, nên entry id hoạt động như một con trỏ bền vững: hãy truyền entry id cuối cùng bạn đã thấy làm `since` để chỉ lấy các entry nằm sau nó, ngay cả khi client khởi động lại. Khác với `get_messages`, command này bao gồm cả lịch sử trước khi nén và các nhánh bị bỏ rơi.

```json
{"type": "get_entries"}
```

Kèm một con trỏ:
```json
{"type": "get_entries", "since": "abc123"}
```

Response:
```json
{
  "type": "response",
  "command": "get_entries",
  "success": true,
  "data": {
    "entries": [
      {"type": "message", "id": "def456", "parentId": "abc123", "timestamp": "...", "message": {"role": "user", "...": "..."}}
    ],
    "leafId": "def456"
  }
}
```

`leafId` là id của entry lá hiện tại (`null` đối với session rỗng), giúp client xác định trong một lượt đi lại duy nhất xem nhánh đang hoạt động có thay đổi hay không. Nếu `since` không khớp với bất kỳ entry id nào, response là `success: false`.

#### get_tree

Lấy session dưới dạng cây entry. Mỗi node là `{entry, children, label?, labelTimestamp?}`. Một session hợp lệ có một gốc duy nhất; các entry mồ côi (chuỗi parent bị gãy) cũng xuất hiện dưới dạng gốc.

```json
{"type": "get_tree"}
```

Response:
```json
{
  "type": "response",
  "command": "get_tree",
  "success": true,
  "data": {
    "tree": [
      {
        "entry": {"type": "message", "id": "abc123", "parentId": null, "...": "..."},
        "children": [
          {"entry": {"type": "message", "id": "def456", "parentId": "abc123", "...": "..."}, "children": []}
        ]
      }
    ],
    "leafId": "def456"
  }
}
```

#### get_last_assistant_text

Lấy nội dung văn bản của message assistant cuối cùng.

```json
{"type": "get_last_assistant_text"}
```

Response:
```json
{
  "type": "response",
  "command": "get_last_assistant_text",
  "success": true,
  "data": {"text": "The assistant's response..."}
}
```

Trả về `{"text": null}` nếu không có message assistant nào.

#### set_session_name

Đặt tên hiển thị cho session hiện tại. Tên này xuất hiện trong danh sách session và giúp nhận diện các session.

```json
{"type": "set_session_name", "name": "my-feature-work"}
```

Response:
```json
{
  "type": "response",
  "command": "set_session_name",
  "success": true
}
```

Tên session hiện tại có thể lấy qua `get_state` trong trường `sessionName`. Để đặt tên ban đầu khi khởi động chế độ RPC, hãy truyền `--name <name>` hoặc `-n <name>` cho tiến trình `pi --mode rpc`.

### Commands

#### get_commands

Lấy các command có sẵn (extension command, prompt template, và skill). Chúng có thể được gọi qua command `prompt` bằng cách thêm tiền tố `/`.

```json
{"type": "get_commands"}
```

Response:
```json
{
  "type": "response",
  "command": "get_commands",
  "success": true,
  "data": {
    "commands": [
      {"name": "session-name", "description": "Set or clear session name", "source": "extension", "path": "/home/user/.pi/agent/extensions/session.ts"},
      {"name": "fix-tests", "description": "Fix failing tests", "source": "prompt", "location": "project", "path": "/home/user/myproject/.pi/agent/prompts/fix-tests.md"},
      {"name": "skill:brave-search", "description": "Web search via Brave API", "source": "skill", "location": "user", "path": "/home/user/.pi/agent/skills/brave-search/SKILL.md"}
    ]
  }
}
```

Mỗi command có:
- `name`: Tên command (gọi bằng `/name`)
- `description`: Mô tả dễ hiểu (tùy chọn đối với extension command)
- `source`: Loại command:
  - `"extension"`: Đăng ký qua `pi.registerCommand()` trong một extension
  - `"prompt"`: Tải từ một tệp template `.md`
  - `"skill"`: Tải từ một thư mục skill (tên được thêm tiền tố `skill:`)
- `location`: Nơi tải command (tùy chọn, không có đối với extension):
  - `"user"`: Cấp người dùng (`~/.pi/agent/`)
  - `"project"`: Cấp dự án (`./.pi/agent/`)
  - `"path"`: Đường dẫn rõ ràng qua CLI hoặc settings
- `path`: Đường dẫn tệp tuyệt đối tới nguồn của command (tùy chọn)

**Lưu ý**: Các command TUI tích hợp sẵn (`/settings`, `/hotkeys`, v.v.) không được đưa vào. Chúng chỉ được xử lý ở chế độ tương tác và sẽ không thực thi nếu gửi qua `prompt`.

## Events

Các event được phát liên tục ra stdout dưới dạng các dòng JSON trong lúc agent vận hành. Nhìn chung event không chứa trường `id`; `bash_execution_update` là ngoại lệ, nó chứa `id` của command `bash` đã phát sinh ra nó khi có cung cấp.

### Các loại Event

| Event | Mô tả |
|-------|-------------|
| `agent_start` | Agent bắt đầu xử lý |
| `agent_end` | Một lượt chạy agent cấp thấp hoàn tất (có thể vẫn tiếp theo bởi thử lại, nén, hoặc các tiếp nối trong hàng đợi) |
| `agent_settled` | Lượt chạy agent đã ổn định hoàn toàn; không còn thử lại tự động, thử lại sau nén, hay tiếp nối trong hàng đợi |
| `turn_start` | Bắt đầu một lượt (turn) mới |
| `turn_end` | Lượt hoàn tất (bao gồm message assistant và kết quả tool) |
| `message_start` | Bắt đầu một message |
| `message_update` | Cập nhật streaming (các delta text/thinking/toolcall) |
| `message_end` | Message hoàn tất |
| `bash_execution_update` | Một đoạn đầu ra của command bash trực tiếp qua RPC |
| `tool_execution_start` | Tool bắt đầu thực thi |
| `tool_execution_update` | Tiến trình thực thi tool (đầu ra streaming) |
| `tool_execution_end` | Tool hoàn tất |
| `queue_update` | Hàng đợi steering/follow-up đang chờ thay đổi |
| `compaction_start` | Bắt đầu nén context |
| `compaction_end` | Nén context hoàn tất |
| `auto_retry_start` | Bắt đầu thử lại tự động (sau lỗi tạm thời) |
| `auto_retry_end` | Thử lại tự động hoàn tất (thành công hoặc thất bại cuối cùng) |
| `summarization_retry_scheduled` | Đã lên lịch thử lại cho lỗi tạm thời trong quá trình nén hoặc tóm tắt nhánh |
| `summarization_retry_attempt_start` | Bắt đầu yêu cầu tóm tắt đã được thử lại |
| `summarization_retry_finished` | Vòng lặp thử lại tóm tắt hoàn tất |
| `extension_error` | Extension ném ra một lỗi |

### agent_start

Được phát khi agent bắt đầu xử lý một prompt.

```json
{"type": "agent_start"}
```

### agent_end

Được phát khi một lượt chạy agent cấp thấp hoàn tất. Chứa tất cả message được tạo trong lượt chạy này. Nếu `willRetry` là true, một lần thử lại tự động sẽ diễn ra tiếp theo.

```json
{
  "type": "agent_end",
  "messages": [...],
  "willRetry": false
}
```

### agent_settled

Được phát sau khi lượt chạy ở cấp session đã ổn định hoàn toàn. Tại thời điểm này, Pi sẽ không tiếp tục tự động thông qua thử lại, thử lại sau nén, hay các follow-up message trong hàng đợi.

```json
{"type": "agent_settled"}
```

### turn_start / turn_end

Một lượt (turn) gồm một phản hồi của assistant cùng với các tool call và kết quả tương ứng.

```json
{"type": "turn_start"}
```

```json
{
  "type": "turn_end",
  "message": {...},
  "toolResults": [...]
}
```

### message_start / message_end

Được phát khi một message bắt đầu và hoàn tất. Trường `message` chứa một `AgentMessage`.

```json
{"type": "message_start", "message": {...}}
{"type": "message_end", "message": {...}}
```

### message_update (Streaming)

Được phát trong lúc streaming các message assistant. Chứa một event delta mà không kèm bản snapshot tích lũy của message.

```json
{
  "type": "message_update",
  "usage": {
    "input": 100,
    "output": 1,
    "cacheRead": 0,
    "cacheWrite": 0,
    "totalTokens": 101,
    "cost": {"input": 0, "output": 0, "cacheRead": 0, "cacheWrite": 0, "total": 0}
  },
  "assistantMessageEvent": {
    "type": "text_delta",
    "contentIndex": 0,
    "delta": "Hello "
  }
}
```

Trường `assistantMessageEvent` chứa một trong các loại delta sau:

| Type | Mô tả |
|------|-------------|
| `text_start` | Bắt đầu một block nội dung text |
| `text_delta` | Một đoạn nội dung text |
| `text_end` | Kết thúc block nội dung text |
| `thinking_start` | Bắt đầu một block thinking |
| `thinking_delta` | Một đoạn nội dung thinking |
| `thinking_end` | Kết thúc block thinking |
| `toolcall_start` | Bắt đầu một tool call (chứa `id` và `toolName`) |
| `toolcall_delta` | Một đoạn đối số của tool call |
| `toolcall_end` | Tool call kết thúc (chứa đối tượng `toolCall` đầy đủ) |

Ví dụ streaming một phản hồi dạng text:
```json
{"type":"message_update","usage":{...},"assistantMessageEvent":{"type":"text_start","contentIndex":0}}
{"type":"message_update","usage":{...},"assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":"Hello"}}
{"type":"message_update","usage":{...},"assistantMessageEvent":{"type":"text_delta","contentIndex":0,"delta":" world"}}
{"type":"message_update","usage":{...},"assistantMessageEvent":{"type":"text_end","contentIndex":0,"content":"Hello world"}}
```

Trường `usage` ở cấp cao nhất chứa mức sử dụng mới nhất do nhà cung cấp báo cáo theo kiểu tích lũy. Nó có thể giữ ở mức không cho đến khi hoàn tất nếu nhà cung cấp không báo cáo mức sử dụng trong lúc streaming.

Ví dụ bắt đầu một tool call:
```json
{"type":"message_update","usage":{...},"assistantMessageEvent":{"type":"toolcall_start","contentIndex":1,"id":"call_abc123","toolName":"write"}}
```

`message_update` cố tình bỏ qua trường `message` tích lũy trước đây và `assistantMessageEvent.partial`. Client cần một message tạm thời theo thời gian thực phải tự lắp ráp từ `message_start` và các event tiếp theo bằng `contentIndex`. Hãy coi `message_end.message` là dữ liệu chuẩn xác. Đối với tool call, `toolcall_start` cung cấp `id` và `toolName` của lời gọi; hãy đệm `toolcall_delta.delta` làm đối số. `toolcall_end.toolCall` chứa lời gọi đã hoàn tất.

### bash_execution_update

Được phát một lần cho mỗi đoạn đầu ra từ một command `bash` trực tiếp. `id` khớp với `id` của command, giúp client liên kết đầu ra với đúng command.

Các event phát toàn bộ đầu ra trong lúc command chạy, ngay cả khi trường `output` của response `bash` cuối cùng bị cắt bớt.

```json
{
  "type": "bash_execution_update",
  "id": "req-1",
  "delta": "total 48\n"
}
```

### tool_execution_start / tool_execution_update / tool_execution_end

Được phát khi một tool bắt đầu, phát tiến trình streaming, và hoàn tất thực thi.

```json
{
  "type": "tool_execution_start",
  "toolCallId": "call_abc123",
  "toolName": "bash",
  "args": {"command": "ls -la"}
}
```

Trong lúc thực thi, các event `tool_execution_update` phát kết quả tạm thời (ví dụ đầu ra bash khi nó xuất hiện):

```json
{
  "type": "tool_execution_update",
  "toolCallId": "call_abc123",
  "toolName": "bash",
  "args": {"command": "ls -la"},
  "partialResult": {
    "content": [{"type": "text", "text": "partial output so far..."}],
    "details": {"truncation": null, "fullOutputPath": null}
  }
}
```

Khi hoàn tất:

```json
{
  "type": "tool_execution_end",
  "toolCallId": "call_abc123",
  "toolName": "bash",
  "result": {
    "content": [{"type": "text", "text": "total 48\n..."}],
    "details": {...}
  },
  "isError": false
}
```

Hãy dùng `toolCallId` để đối chiếu các event. `partialResult` trong `tool_execution_update` chứa đầu ra tích lũy tính đến thời điểm đó (không chỉ phần delta), giúp client chỉ cần thay thế nội dung hiển thị sau mỗi lần cập nhật.

### queue_update

Được phát bất cứ khi nào hàng đợi steering hoặc follow-up đang chờ thay đổi.

```json
{
  "type": "queue_update",
  "steering": ["Focus on error handling"],
  "followUp": ["After that, summarize the result"]
}
```

### compaction_start / compaction_end

Được phát khi quá trình nén diễn ra, dù là thủ công hay tự động.

```json
{"type": "compaction_start", "reason": "threshold"}
```

Trường `reason` là `"manual"`, `"threshold"`, hoặc `"overflow"`.

```json
{
  "type": "compaction_end",
  "reason": "threshold",
  "result": {
    "summary": "Summary of conversation...",
    "firstKeptEntryId": "abc123",
    "tokensBefore": 150000,
    "estimatedTokensAfter": 32000,
    "usage": {
      "input": 32000,
      "output": 1200,
      "cacheRead": 0,
      "cacheWrite": 0,
      "totalTokens": 33200,
      "cost": {"input": 0.01, "output": 0.02, "cacheRead": 0, "cacheWrite": 0, "total": 0.03}
    },
    "details": {}
  },
  "aborted": false,
  "willRetry": false
}
```

Nếu `reason` là `"overflow"` và quá trình nén thành công, `willRetry` là `true` và agent sẽ tự động thử lại prompt.

Nếu quá trình nén bị hủy, `result` là `null` và `aborted` là `true`.

Nếu quá trình nén thất bại (ví dụ vượt hạn mức API), `result` là `null`, `aborted` là `false`, và `errorMessage` chứa mô tả lỗi.

### auto_retry_start / auto_retry_end

Được phát khi thử lại tự động được kích hoạt sau một lỗi tạm thời (quá tải, giới hạn tốc độ, 5xx).

```json
{
  "type": "auto_retry_start",
  "attempt": 1,
  "maxAttempts": 3,
  "delayMs": 2000,
  "errorMessage": "529 {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}"
}
```

```json
{
  "type": "auto_retry_end",
  "success": true,
  "attempt": 2
}
```

Khi thất bại cuối cùng (vượt số lần thử lại tối đa):
```json
{
  "type": "auto_retry_end",
  "success": false,
  "attempt": 3,
  "finalError": "529 overloaded_error: Overloaded"
}
```

### summarization_retry_scheduled / summarization_retry_attempt_start / summarization_retry_finished

Được phát khi quá trình nén hoặc tóm tắt nhánh thử lại sau một lỗi tạm thời từ nhà cung cấp. Các event này dùng cùng cài đặt thử lại với việc thử lại tự động các lượt assistant.

```json
{
  "type": "summarization_retry_scheduled",
  "attempt": 1,
  "maxAttempts": 3,
  "delayMs": 2000,
  "errorMessage": "terminated"
}
```

```json
{
  "type": "summarization_retry_attempt_start",
  "source": "compaction",
  "reason": "threshold"
}
```

Đối với bản tóm tắt nhánh, `source` là `"branchSummary"` và không có `reason`.

```json
{
  "type": "summarization_retry_finished"
}
```

### extension_error

Được phát khi một extension ném ra lỗi.

```json
{
  "type": "extension_error",
  "extensionPath": "/path/to/extension.ts",
  "event": "tool_call",
  "error": "Error message..."
}
```

## Giao thức UI của Extension

Các extension có thể yêu cầu tương tác người dùng qua `ctx.ui.select()`, `ctx.ui.confirm()`, v.v. Trong chế độ RPC, những lời gọi này được chuyển thành một giao thức con dạng yêu cầu/phản hồi nằm trên luồng command/event cơ bản.

Có hai nhóm phương thức UI của extension:

- **Phương thức hộp thoại (Dialog)** (`select`, `confirm`, `input`, `editor`): phát một `extension_ui_request` ra stdout và chờ cho đến khi client gửi lại một `extension_ui_response` trên stdin với `id` tương ứng.
- **Phương thức fire-and-forget** (`notify`, `setStatus`, `setWidget`, `setTitle`, `set_editor_text`): phát một `extension_ui_request` ra stdout nhưng không mong đợi phản hồi. Client có thể hiển thị thông tin hoặc bỏ qua.

Nếu một phương thức hộp thoại có trường `timeout`, phía agent sẽ tự động phân giải với một giá trị mặc định khi hết thời gian chờ. Client không cần theo dõi timeout.

Một số phương thức của `ExtensionUIContext` không được hỗ trợ hoặc bị suy giảm ở chế độ RPC vì chúng cần truy cập trực tiếp vào TUI:
- `custom()` trả về `undefined`
- `setWorkingMessage()`, `setWorkingIndicator()`, `setFooter()`, `setHeader()`, `setEditorComponent()`, `setToolsExpanded()` là các phép toán vô hiệu (no-op)
- `getEditorText()` trả về `""`
- `getToolsExpanded()` trả về `false`
- `pasteToEditor()` ủy thác cho `setEditorText()` (không xử lý dán/thu gọn)
- `getAllThemes()` trả về `[]`
- `getTheme()` trả về `undefined`
- `setTheme()` trả về `{ success: false, error: "..." }`

Lưu ý: `ctx.mode` là `"rpc"` và `ctx.hasUI` là `true` ở chế độ RPC vì các phương thức hộp thoại và fire-and-forget hoạt động được thông qua giao thức con UI của extension. Hãy dùng `ctx.mode === "tui"` để bảo vệ các tính năng riêng của TUI như `custom()` — vốn cần một terminal thật.

### Yêu cầu UI của Extension (stdout)

Tất cả yêu cầu đều có `type: "extension_ui_request"`, một `id` duy nhất, và một trường `method`.

#### select

Nhắc người dùng chọn từ một danh sách. Các phương thức hộp thoại có trường `timeout` sẽ kèm thời gian chờ tính bằng mili giây; agent tự phân giải với `undefined` nếu client không phản hồi kịp thời.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-1",
  "method": "select",
  "title": "Allow dangerous command?",
  "options": ["Allow", "Block"],
  "timeout": 10000
}
```

Response mong đợi: `extension_ui_response` với `value` (chuỗi tùy chọn đã chọn) hoặc `cancelled: true`.

#### confirm

Nhắc người dùng xác nhận có/không.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-2",
  "method": "confirm",
  "title": "Clear session?",
  "message": "All messages will be lost.",
  "timeout": 5000
}
```

Response mong đợi: `extension_ui_response` với `confirmed: true/false` hoặc `cancelled: true`.

#### input

Nhắc người dùng nhập văn bản tự do.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-3",
  "method": "input",
  "title": "Enter a value",
  "placeholder": "type something..."
}
```

Response mong đợi: `extension_ui_response` với `value` (văn bản đã nhập) hoặc `cancelled: true`.

#### editor

Mở một trình soạn thảo văn bản nhiều dòng với nội dung điền sẵn tùy chọn.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-4",
  "method": "editor",
  "title": "Edit some text",
  "prefill": "Line 1\nLine 2\nLine 3"
}
```

Response mong đợi: `extension_ui_response` với `value` (văn bản đã chỉnh sửa) hoặc `cancelled: true`.

#### notify

Hiển thị một thông báo. Fire-and-forget, không mong đợi phản hồi.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-5",
  "method": "notify",
  "message": "Command blocked by user",
  "notifyType": "warning"
}
```

Trường `notifyType` là `"info"`, `"warning"`, hoặc `"error"`. Mặc định là `"info"` nếu bỏ qua.

#### setStatus

Đặt hoặc xóa một mục trạng thái trong footer/thanh trạng thái. Fire-and-forget.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-6",
  "method": "setStatus",
  "statusKey": "my-ext",
  "statusText": "Turn 3 running..."
}
```

Gửi `statusText: undefined` (hoặc bỏ qua trường này) để xóa mục trạng thái của key đó.

#### setWidget

Đặt hoặc xóa một widget (khối các dòng văn bản) hiển thị phía trên hoặc phía dưới trình soạn thảo. Fire-and-forget.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-7",
  "method": "setWidget",
  "widgetKey": "my-ext",
  "widgetLines": ["--- My Widget ---", "Line 1", "Line 2"],
  "widgetPlacement": "aboveEditor"
}
```

Gửi `widgetLines: undefined` (hoặc bỏ qua) để xóa widget. Trường `widgetPlacement` là `"aboveEditor"` (mặc định) hoặc `"belowEditor"`. Trong chế độ RPC chỉ hỗ trợ mảng chuỗi; các factory component bị bỏ qua.

#### setTitle

Đặt tiêu đề của cửa sổ/tab terminal. Fire-and-forget.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-8",
  "method": "setTitle",
  "title": "pi - my project"
}
```

#### set_editor_text

Đặt văn bản trong trình soạn thảo đầu vào. Fire-and-forget.

```json
{
  "type": "extension_ui_request",
  "id": "uuid-9",
  "method": "set_editor_text",
  "text": "prefilled text for the user"
}
```

### Phản hồi UI của Extension (stdin)

Phản hồi chỉ được gửi cho các phương thức hộp thoại (`select`, `confirm`, `input`, `editor`). `id` phải khớp với yêu cầu.

#### Phản hồi giá trị (select, input, editor)

```json
{"type": "extension_ui_response", "id": "uuid-1", "value": "Allow"}
```

#### Phản hồi xác nhận (confirm)

```json
{"type": "extension_ui_response", "id": "uuid-2", "confirmed": true}
```

#### Phản hồi hủy bỏ (bất kỳ hộp thoại nào)

Đóng bất kỳ phương thức hộp thoại nào. Extension nhận `undefined` (với select/input/editor) hoặc `false` (với confirm).

```json
{"type": "extension_ui_response", "id": "uuid-3", "cancelled": true}
```

## Xử lý lỗi

Các command thất bại trả về một response với `success: false`:

```json
{
  "type": "response",
  "command": "set_model",
  "success": false,
  "error": "Model not found: invalid/model"
}
```

Lỗi phân tích cú pháp (parse errors):

```json
{
  "type": "response",
  "command": "parse",
  "success": false,
  "error": "Failed to parse command: Unexpected token..."
}
```

## Types

Các tệp nguồn:
- [`packages/ai/src/types.ts`](../../ai/src/types.ts) - `Model`, `UserMessage`, `AssistantMessage`, `ToolResultMessage`
- [`packages/agent/src/types.ts`](../../agent/src/types.ts) - `AgentMessage`, `AgentEvent`
- [`src/core/messages.ts`](../src/core/messages.ts) - `BashExecutionMessage`
- [`src/modes/json-event.ts`](../src/modes/json-event.ts) - `JsonAgentSessionEvent`
- [`src/modes/rpc/rpc-types.ts`](../src/modes/rpc/rpc-types.ts) - Các kiểu command/response của RPC, các kiểu yêu cầu/phản hồi UI của extension

### Model

```json
{
  "id": "claude-sonnet-4-20250514",
  "name": "Claude Sonnet 4",
  "api": "anthropic-messages",
  "provider": "anthropic",
  "baseUrl": "https://api.anthropic.com",
  "reasoning": true,
  "input": ["text", "image"],
  "contextWindow": 200000,
  "maxTokens": 16384,
  "cost": {
    "input": 3.0,
    "output": 15.0,
    "cacheRead": 0.3,
    "cacheWrite": 3.75
  }
}
```

### UserMessage

```json
{
  "role": "user",
  "content": "Hello!",
  "timestamp": 1733234567890,
  "attachments": []
}
```

Trường `content` có thể là một chuỗi hoặc một mảng gồm các block `TextContent`/`ImageContent`.

### AssistantMessage

```json
{
  "role": "assistant",
  "content": [
    {"type": "text", "text": "Hello! How can I help?"},
    {"type": "thinking", "thinking": "User is greeting me..."},
    {"type": "toolCall", "id": "call_123", "name": "bash", "arguments": {"command": "ls"}}
  ],
  "api": "anthropic-messages",
  "provider": "anthropic",
  "model": "claude-sonnet-4-20250514",
  "usage": {
    "input": 100,
    "output": 50,
    "cacheRead": 0,
    "cacheWrite": 0,
    "cost": {"input": 0.0003, "output": 0.00075, "cacheRead": 0, "cacheWrite": 0, "total": 0.00105}
  },
  "stopReason": "stop",
  "timestamp": 1733234567890
}
```

Lý do dừng (stop reasons): `"stop"`, `"length"`, `"toolUse"`, `"error"`, `"aborted"`

### ToolResultMessage

```json
{
  "role": "toolResult",
  "toolCallId": "call_123",
  "toolName": "bash",
  "content": [{"type": "text", "text": "total 48\ndrwxr-xr-x ..."}],
  "usage": {
    "input": 100,
    "output": 50,
    "cacheRead": 0,
    "cacheWrite": 0,
    "totalTokens": 150,
    "cost": {"input": 0.0003, "output": 0.00075, "cacheRead": 0, "cacheWrite": 0, "total": 0.00105}
  },
  "isError": false,
  "timestamp": 1733234567890
}
```

`usage` là tùy chọn và báo cáo công việc LLM lồng nhau do tool thực hiện. Khi có, nó được tính vào tổng token và chi phí của session.

### BashExecutionMessage

Được tạo bởi command RPC `bash` (không phải bởi tool call của LLM):

```json
{
  "role": "bashExecution",
  "command": "ls -la",
  "output": "total 48\ndrwxr-xr-x ...",
  "exitCode": 0,
  "cancelled": false,
  "truncated": false,
  "fullOutputPath": null,
  "timestamp": 1733234567890
}
```

### Attachment

```json
{
  "id": "img1",
  "type": "image",
  "fileName": "photo.jpg",
  "mimeType": "image/jpeg",
  "size": 102400,
  "content": "base64-encoded-data...",
  "extractedText": null,
  "preview": null
}
```

## Ví dụ: Client cơ bản (Python)

```python
import subprocess
import json

proc = subprocess.Popen(
    ["pi", "--mode", "rpc", "--no-session"],
    stdin=subprocess.PIPE,
    stdout=subprocess.PIPE,
    text=True
)

def send(cmd):
    proc.stdin.write(json.dumps(cmd) + "\n")
    proc.stdin.flush()

def read_events():
    for line in proc.stdout:
        yield json.loads(line)

# Send prompt
send({"type": "prompt", "message": "Hello!"})

# Process events
for event in read_events():
    if event.get("type") == "message_update":
        delta = event.get("assistantMessageEvent", {})
        if delta.get("type") == "text_delta":
            print(delta["delta"], end="", flush=True)
    
    if event.get("type") == "agent_end":
        print()
        break
```

## Ví dụ: Client tương tác (Node.js)

Xem [`test/rpc-example.ts`](../test/rpc-example.ts) để có một ví dụ tương tác đầy đủ, hoặc [`src/modes/rpc/rpc-client.ts`](../src/modes/rpc/rpc-client.ts) để có một triển khai client có kiểu rõ ràng (typed).

Để có ví dụ đầy đủ về xử lý giao thức UI của extension, xem [`examples/rpc-extension-ui.ts`](../examples/rpc-extension-ui.ts), đi kèm với extension [`examples/extensions/rpc-demo.ts`](../examples/extensions/rpc-demo.ts).

```javascript
const { spawn } = require("child_process");
const { StringDecoder } = require("string_decoder");

const agent = spawn("pi", ["--mode", "rpc", "--no-session"]);

function attachJsonlReader(stream, onLine) {
    const decoder = new StringDecoder("utf8");
    let buffer = "";

    stream.on("data", (chunk) => {
        buffer += typeof chunk === "string" ? chunk : decoder.write(chunk);

        while (true) {
            const newlineIndex = buffer.indexOf("\n");
            if (newlineIndex === -1) break;

            let line = buffer.slice(0, newlineIndex);
            buffer = buffer.slice(newlineIndex + 1);
            if (line.endsWith("\r")) line = line.slice(0, -1);
            onLine(line);
        }
    });

    stream.on("end", () => {
        buffer += decoder.end();
        if (buffer.length > 0) {
            onLine(buffer.endsWith("\r") ? buffer.slice(0, -1) : buffer);
        }
    });
}

attachJsonlReader(agent.stdout, (line) => {
    const event = JSON.parse(line);

    if (event.type === "message_update") {
        const { assistantMessageEvent } = event;
        if (assistantMessageEvent.type === "text_delta") {
            process.stdout.write(assistantMessageEvent.delta);
        }
    }
});

// Send prompt
agent.stdin.write(JSON.stringify({ type: "prompt", message: "Hello" }) + "\n");

// Abort on Ctrl+C
process.on("SIGINT", () => {
    agent.stdin.write(JSON.stringify({ type: "abort" }) + "\n");
});
```

