# Định dạng tệp Session

Các session được lưu dưới dạng tệp JSONL (JSON Lines). Mỗi dòng là một đối tượng JSON với một trường `type`. Các entry của session tạo thành cấu trúc tree thông qua các trường `id`/`parentId`, cho phép branch ngay tại chỗ mà không cần tạo tệp mới.

## Vị trí tệp

```
~/.pi/agent/sessions/--<path>--/<timestamp>_<session-id>.jsonl
```

Theo mặc định, `<session-id>` là một UUID. Người gọi có thể cung cấp ID tùy chỉnh thông qua SDK hoặc `--session-id`. Với `<path>`, Pi xóa dấu phân cách đường dẫn ở đầu và thay `/`, `\\`, và `:` bằng `-`.

## Xóa Session

Có thể xóa session bằng cách xóa các tệp `.jsonl` tương ứng trong `~/.pi/agent/sessions/`.

Pi cũng hỗ trợ xóa session tương tác từ `/resume` (chọn một session và nhấn `Ctrl+D`, sau đó xác nhận). Khi có sẵn, pi dùng CLI `trash` để tránh xóa vĩnh viễn.

## Phiên bản Session

Session có một trường version trong header:

- **Version 1**: Chuỗi entry tuyến tính (legacy, tự động migrate khi tải)
- **Version 2**: Cấu trúc tree với liên kết `id`/`parentId`
- **Version 3**: Đổi tên role `hookMessage` thành `custom` (thống nhất các extension)

Các session hiện có được tự động migrate sang phiên bản hiện tại (v3) khi tải.

## Tệp nguồn

Mã nguồn trên GitHub ([pi](https://github.com/earendil-works/pi)):
- [`packages/coding-agent/src/core/session-manager.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/session-manager.ts) - Các kiểu entry của session và SessionManager
- [`packages/coding-agent/src/core/messages.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/messages.ts) - Các kiểu message mở rộng (BashExecutionMessage, CustomMessage, v.v.)
- [`packages/ai/src/types.ts`](https://github.com/earendil-works/pi/blob/main/packages/ai/src/types.ts) - Các kiểu message cơ bản (UserMessage, AssistantMessage, ToolResultMessage)
- [`packages/agent/src/types.ts`](https://github.com/earendil-works/pi/blob/main/packages/agent/src/types.ts) - Kiểu union AgentMessage

Để xem các định nghĩa TypeScript trong dự án của bạn, hãy kiểm tra `node_modules/@earendil-works/pi-coding-agent/dist/` và `node_modules/@earendil-works/pi-ai/dist/`.

## Kiểu Message

Các entry của session chứa các đối tượng `AgentMessage`. Hiểu rõ các kiểu này là cần thiết để phân tích session và viết extension.

### Khối nội dung (Content Blocks)

Message chứa các mảng khối nội dung có kiểu:

```typescript
interface TextContent {
  type: "text";
  text: string;
  textSignature?: string;
}

interface ImageContent {
  type: "image";
  data: string;      // base64 encoded
  mimeType: string;  // e.g., "image/jpeg", "image/png"
}

interface ThinkingContent {
  type: "thinking";
  thinking: string;
  thinkingSignature?: string;
  redacted?: boolean;
}

interface ToolCall {
  type: "toolCall";
  id: string;
  name: string;
  arguments: Record<string, any>;
  thoughtSignature?: string;
  namespace?: string;
}
```

### Kiểu Message cơ bản (từ pi-ai)

```typescript
interface UserMessage {
  role: "user";
  content: string | (TextContent | ImageContent)[];
  timestamp: number;  // Unix ms
}

interface AssistantMessage {
  role: "assistant";
  content: (TextContent | ThinkingContent | ToolCall)[];
  api: string;
  provider: string;
  model: string;
  responseModel?: string;
  responseId?: string;
  providerThinkingLevel?: string;
  diagnostics?: AssistantMessageDiagnostic[];
  usage: Usage;
  stopReason: "pending" | "stop" | "length" | "toolUse" | "error" | "aborted" | "deferred";
  deferred?: DeferredHandle;
  errorMessage?: string;
  rawStopReason?: string;
  endTurn?: boolean;
  timestamp: number;
}

interface ToolResultMessage {
  role: "toolResult";
  toolCallId: string;
  toolName: string;
  content: (TextContent | ImageContent)[];
  details?: any;      // Tool-specific metadata
  usage?: Usage;      // Nested LLM work performed by the tool
  addedToolNames?: string[];
  isError: boolean;
  timestamp: number;
}

interface Usage {
  input: number;
  output: number;
  cacheRead: number;
  cacheWrite: number;
  cacheWrite1h?: number;
  reasoning?: number;
  totalTokens: number;
  cost: {
    input: number;
    output: number;
    cacheRead: number;
    cacheWrite: number;
    total: number;
  };
}
```

`"pending"` được dành cho các message một phần trong các sự kiện streaming. Các sự kiện kết thúc thay thế nó bằng lý do hoàn tất trước khi Pi lưu message assistant, do đó `"pending"` không bao giờ xuất hiện trong JSONL của session. `"deferred"` là lý do kết thúc cho một phản hồi từ provider sẽ hoàn tất sau; handle `deferred` của nó chứa dữ liệu provider cần để truy xuất phản hồi đó.

### Kiểu Message mở rộng (từ pi-coding-agent)

```typescript
interface BashExecutionMessage {
  role: "bashExecution";
  command: string;
  output: string;
  exitCode: number | undefined;
  cancelled: boolean;
  truncated: boolean;
  fullOutputPath?: string;
  excludeFromContext?: boolean;  // true for !! prefix commands
  timestamp: number;
}

interface CustomMessage {
  role: "custom";
  customType: string;            // Extension identifier
  content: string | (TextContent | ImageContent)[];
  display: boolean;              // Show in TUI
  details?: any;                 // Extension-specific metadata
  timestamp: number;
}

interface BranchSummaryMessage {
  role: "branchSummary";
  summary: string;
  fromId: string | null;         // Previous leaf whose abandoned path was summarized
  timestamp: number;
}

interface CompactionSummaryMessage {
  role: "compactionSummary";
  summary: string;
  tokensBefore: number;
  timestamp: number;
}
```

### Union AgentMessage

```typescript
type AgentMessage =
  | UserMessage
  | AssistantMessage
  | ToolResultMessage
  | BashExecutionMessage
  | CustomMessage
  | BranchSummaryMessage
  | CompactionSummaryMessage;
```

## Entry cơ sở

Tất cả entry (trừ `SessionHeader`) đều kế thừa `SessionEntryBase`:

```typescript
interface SessionEntryBase {
  type: string;
  id: string;           // Usually an 8-char hex ID; may fall back to a full UUID
  parentId: string | null;  // Parent entry ID (null for a root entry)
  timestamp: string;    // ISO timestamp
}
```

## Các kiểu Entry

### SessionHeader

Dòng đầu tiên của tệp. Chỉ chứa metadata, không thuộc tree (không có `id`/`parentId`).

```json
{"type":"session","version":3,"id":"uuid","timestamp":"2024-12-03T14:00:00.000Z","cwd":"/path/to/project"}
```

Đối với session có parent (được tạo qua `/fork`, `/clone`, hoặc `newSession({ parentSession })`):

```json
{"type":"session","version":3,"id":"uuid","timestamp":"2024-12-03T14:00:00.000Z","cwd":"/path/to/project","parentSession":"/path/to/original/session.jsonl"}
```

### SessionMessageEntry

Một message trong cuộc hội thoại. Trường `message` chứa một `AgentMessage`.

```json
{"type":"message","id":"a1b2c3d4","parentId":"prev1234","timestamp":"2024-12-03T14:00:01.000Z","message":{"role":"user","content":"Hello","timestamp":1733234401000}}
{"type":"message","id":"b2c3d4e5","parentId":"a1b2c3d4","timestamp":"2024-12-03T14:00:02.000Z","message":{"role":"assistant","content":[{"type":"text","text":"Hi!"}],"api":"anthropic-messages","provider":"anthropic","model":"claude-sonnet-4-5","usage":{...},"stopReason":"stop","timestamp":1733234402000}}
{"type":"message","id":"c3d4e5f6","parentId":"b2c3d4e5","timestamp":"2024-12-03T14:00:03.000Z","message":{"role":"toolResult","toolCallId":"call_123","toolName":"bash","content":[{"type":"text","text":"output"}],"isError":false,"timestamp":1733234403000}}
```

### ModelChangeEntry

Được phát ra khi người dùng chuyển model giữa chừng trong session.

```json
{"type":"model_change","id":"d4e5f6g7","parentId":"c3d4e5f6","timestamp":"2024-12-03T14:05:00.000Z","provider":"openai","modelId":"gpt-4o"}
```

### ThinkingLevelChangeEntry

Được phát ra khi người dùng thay đổi mức thinking/reasoning.

```json
{"type":"thinking_level_change","id":"e5f6g7h8","parentId":"d4e5f6g7","timestamp":"2024-12-03T14:06:00.000Z","thinkingLevel":"high"}
```

### CompactionEntry

Được tạo khi context được nén (compact). Lưu bản tóm tắt các message trước đó.

```json
{"type":"compaction","id":"f6g7h8i9","parentId":"e5f6g7h8","timestamp":"2024-12-03T14:10:00.000Z","summary":"User discussed X, Y, Z...","firstKeptEntryId":"c3d4e5f6","tokensBefore":50000}
```

`firstKeptEntryId` là bắt buộc. Nó xác định entry đầu tiên được giữ lại từ trước entry compaction. Khi xây dựng lại context, Pi thay thế các entry cũ đã được tóm tắt bằng bản tóm tắt compaction và giữ lại phạm vi bắt đầu từ entry này.

Các trường tùy chọn:
- `usage`: Mức sử dụng LLM khi tạo bản tóm tắt; được tính vào tổng token và chi phí của session
- `details`: Dữ liệu riêng cho từng implementation (ví dụ `{ readFiles: string[], modifiedFiles: string[] }` cho mặc định, hoặc dữ liệu tùy chỉnh cho extension)
- `fromHook`: `true` nếu được tạo bởi extension, `false`/`undefined` nếu do pi tạo (tên trường legacy)

### BranchSummaryEntry

Được tạo khi chuyển branch qua `/tree`, kèm bản tóm tắt do LLM tạo cho nhánh bên trái đến tổ tiên chung. Ghi lại context từ đường đi bị bỏ rơi.

```json
{"type":"branch_summary","id":"g7h8i9j0","parentId":"a1b2c3d4","timestamp":"2024-12-03T14:15:00.000Z","fromId":"f6g7h8i9","summary":"Branch explored approach A..."}
```

`parentId` là entry mà từ đó branch mới tiếp tục. `fromId` là leaf trước đó có đường đi bị bỏ rơi đã được tóm tắt.

Các trường tùy chọn:
- `usage`: Mức sử dụng LLM khi tạo bản tóm tắt; được tính vào tổng token và chi phí của session
- `details`: Dữ liệu theo dõi tệp (`{ readFiles: string[], modifiedFiles: string[] }`) cho mặc định, hoặc dữ liệu tùy chỉnh cho extension
- `fromHook`: `true` nếu được tạo bởi extension, `false`/`undefined` nếu do pi tạo (tên trường legacy)

### CustomEntry

Lưu trữ trạng thái của extension. KHÔNG tham gia vào context của LLM.

```json
{"type":"custom","id":"h8i9j0k1","parentId":"g7h8i9j0","timestamp":"2024-12-03T14:20:00.000Z","customType":"my-extension","data":{"count":42}}
```

Dùng `customType` để xác định entry của extension khi tải lại. Chế độ tương tác có thể render các entry tùy chỉnh qua `pi.registerEntryRenderer(customType, renderer)`, nhưng chúng vẫn không tham gia context của LLM.

### CustomMessageEntry

Các message do extension chèn vào CÓ tham gia context của LLM.

```json
{"type":"custom_message","id":"i9j0k1l2","parentId":"h8i9j0k1","timestamp":"2024-12-03T14:25:00.000Z","customType":"my-extension","content":"Injected context...","display":true}
```

Các trường:
- `content`: Chuỗi hoặc `(TextContent | ImageContent)[]` (giống UserMessage)
- `display`: `true` = hiển thị trong TUI với kiểu dáng riêng, `false` = ẩn
- `details`: Metadata tùy chọn riêng cho extension (không gửi tới LLM)

### LabelEntry

Đánh dấu/bookmark do người dùng định nghĩa trên một entry.

```json
{"type":"label","id":"j0k1l2m3","parentId":"i9j0k1l2","timestamp":"2024-12-03T14:30:00.000Z","targetId":"a1b2c3d4","label":"checkpoint-1"}
```

Đặt `label` thành `undefined` để xóa nhãn.

### SessionInfoEntry

Metadata của session (ví dụ tên hiển thị do người dùng định nghĩa). Đặt qua `/name`, `--name` / `-n`, hoặc `pi.setSessionName()` trong extension.

```json
{"type":"session_info","id":"k1l2m3n4","parentId":"j0k1l2m3","timestamp":"2024-12-03T14:35:00.000Z","name":"Refactor auth module"}
```

Tên session được hiển thị trong bộ chọn session (`/resume`) thay vì message đầu tiên khi đã được đặt.

## Cấu trúc Tree

Thông thường các entry tạo thành một tree, nhưng các API điều hướng có thể tạo nhiều root:

- Entry root có `parentId: null`; entry đầu tiên ban đầu là root
- Mỗi entry không phải root trỏ tới parent của nó qua `parentId`
- Việc branch tạo các con mới từ một entry trước đó
- "leaf" là vị trí hiện tại trong tree
- Gọi `resetLeaf()` hoặc `branchWithSummary(null, ...)` cho phép một entry sau đó trở thành root khác

```
[user msg] ─── [assistant] ─── [user msg] ─── [assistant] ─┬─ [user msg] ← current leaf
                                                            │
                                                            └─ [branch_summary] ─── [user msg] ← alternate branch
```

## Xây dựng Context

`buildContextEntries()` đi từ leaf hiện tại đến root, tạo danh sách entry hoạt động trong khi tuân theo compaction:

1. Thu thập tất cả entry trên đường đi
2. Nếu có một hoặc nhiều `CompactionEntry` trên đường đi, dùng cái mới nhất:
   - Đưa entry compaction vào trước tiên
   - Đưa các entry từ `firstKeptEntryId` đến trước (không bao gồm) entry compaction
   - Đưa các entry sau entry compaction
3. Giữ lại các entry không phải message trong phạm vi đã chọn để chế độ tương tác có thể render chúng

`buildSessionContext()` dựa trên danh sách entry đó để tạo danh sách message cho LLM:

1. Trích xuất cài đặt model hiện tại và mức thinking từ toàn bộ đường đi
2. Chuyển các entry đã chọn thành message:
   - `message` -> `AgentMessage` đã lưu
   - `compaction` -> `compactionSummary`
   - `branch_summary` -> `branchSummary`
   - `custom_message` -> `CustomMessage`
   - `custom` -> không có message context

Bản tóm tắt compaction thay thế các entry trước `firstKeptEntryId`. Các entry được giữ lại và mọi entry sau compaction vẫn sẵn sàng cho LLM.

## Ví dụ phân tích

```typescript
import { readFileSync } from "fs";

const lines = readFileSync("session.jsonl", "utf8").trim().split("\n");

for (const line of lines) {
  const entry = JSON.parse(line);

  switch (entry.type) {
    case "session":
      console.log(`Session v${entry.version ?? 1}: ${entry.id}`);
      break;
    case "message":
      console.log(`[${entry.id}] ${entry.message.role}: ${JSON.stringify(entry.message.content)}`);
      break;
    case "compaction":
      console.log(`[${entry.id}] Compaction: ${entry.tokensBefore} tokens summarized`);
      break;
    case "branch_summary":
      console.log(`[${entry.id}] Branch from ${entry.fromId}`);
      break;
    case "custom":
      console.log(`[${entry.id}] Custom (${entry.customType}): ${JSON.stringify(entry.data)}`);
      break;
    case "custom_message":
      console.log(`[${entry.id}] Extension message (${entry.customType}): ${entry.content}`);
      break;
    case "label":
      console.log(`[${entry.id}] Label "${entry.label}" on ${entry.targetId}`);
      break;
    case "model_change":
      console.log(`[${entry.id}] Model: ${entry.provider}/${entry.modelId}`);
      break;
    case "thinking_level_change":
      console.log(`[${entry.id}] Thinking: ${entry.thinkingLevel}`);
      break;
  }
}
```

## API SessionManager

Các phương thức chính để làm việc với session theo cách lập trình.

### Phương thức tạo tĩnh
- `SessionManager.create(cwd, sessionDir?, options?)` - Session mới; `options` có thể đặt `id` và `parentSession`
- `SessionManager.open(path, sessionDir?, cwdOverride?)` - Mở tệp session hiện có
- `SessionManager.continueRecent(cwd, sessionDir?)` - Tiếp tục session gần nhất hoặc tạo mới
- `SessionManager.inMemory(cwd?, options?, entries?)` - Không lưu vào tệp, có thể khởi tạo từ các entry
- `SessionManager.forkFrom(sourcePath, targetCwd, sessionDir?, options?)` - Fork session từ một dự án khác

### Phương thức liệt kê tĩnh
- `SessionManager.list(cwd, sessionDir?, onProgress?)` - Liệt kê session cho một thư mục
- `SessionManager.listAll(onProgress?)` - Liệt kê tất cả session trên mọi dự án
- `SessionManager.listAll(sessionDir?, onProgress?)` - Liệt kê session từ một thư mục gốc session tùy chỉnh

### Phương thức instance - Quản lý Session
- `newSession(options?)` - Bắt đầu session mới (options: `{ id?: string, parentSession?: string }`)
- `setSessionFile(path)` - Chuyển sang tệp session khác
- `createBranchedSession(leafId)` - Trích xuất branch sang tệp session mới

### Phương thức instance - Thêm entry (đều trả về entry ID)
- `appendMessage(message)` - Thêm message
- `appendThinkingLevelChange(level)` - Ghi lại thay đổi thinking
- `appendModelChange(provider, modelId)` - Ghi lại thay đổi model
- `appendCompaction(summary, firstKeptEntryId, tokensBefore, details?, fromHook?, usage?)` - Thêm compaction
- `appendCustomEntry(customType, data?)` - Trạng thái extension (không nằm trong context)
- `appendSessionInfo(name)` - Đặt tên hiển thị của session
- `appendCustomMessageEntry(customType, content, display, details?)` - Message của extension (nằm trong context)
- `appendLabelChange(targetId, label)` - Đặt/xóa nhãn

### Phương thức instance - Điều hướng Tree
- `getLeafId()` - Vị trí hiện tại
- `getLeafEntry()` - Lấy entry leaf hiện tại
- `getEntry(id)` - Lấy entry theo ID
- `getBranch(fromId?)` - Đi từ entry đến root
- `getTree()` - Lấy toàn bộ cấu trúc tree
- `getChildren(parentId)` - Lấy các con trực tiếp
- `getLabel(id)` - Lấy nhãn của entry
- `branch(entryId)` - Di chuyển leaf tới entry trước đó
- `resetLeaf()` - Đặt leaf về null (trước mọi entry)
- `branchWithSummary(entryId, summary, details?, fromHook?, usage?)` - Branch kèm tóm tắt context; `entryId` có thể là `null` để branch từ root

### Phương thức instance - Context & Thông tin
- `buildContextEntries()` - Lấy các entry của branch hoạt động sau khi áp dụng compaction
- `buildSessionContext()` - Lấy message, thinkingLevel và model cho LLM
- `getEntries()` - Tất cả entry (không gồm header)
- `getHeader()` - Metadata header của session
- `getSessionName()` - Lấy tên hiển thị từ entry session_info mới nhất
- `getCwd()` - Thư mục làm việc
- `getSessionDir()` - Thư mục lưu session
- `getSessionId()` - UUID của session
- `getSessionFile()` - Đường dẫn tệp session (undefined với in-memory)
- `isPersisted()` - Session đã được lưu vào đĩa hay chưa
