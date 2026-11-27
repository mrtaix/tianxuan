# Compaction & Tóm tắt nhánh (Branch Summarization)

LLM có context window hạn chế. Khi cuộc hội thoại quá dài, Pi dùng compaction để tóm tắt nội dung cũ trong khi vẫn bảo toàn công việc gần đây. Trang này đề cập cả auto-compaction lẫn branch summarization.

**Tệp nguồn** ([pi](https://github.com/earendil-works/pi)):
- [`packages/coding-agent/src/core/compaction/compaction.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/compaction.ts) - Logic auto-compaction
- [`packages/coding-agent/src/core/compaction/branch-summarization.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/branch-summarization.ts) - Branch summarization
- [`packages/coding-agent/src/core/compaction/utils.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/utils.ts) - Tiện ích dùng chung (theo dõi tệp, serialize)
- [`packages/coding-agent/src/core/session-manager.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/session-manager.ts) - Kiểu entry (`CompactionEntry`, `BranchSummaryEntry`)
- [`packages/coding-agent/src/core/extensions/types.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/extensions/types.ts) - Kiểu sự kiện extension

Để xem định nghĩa TypeScript trong dự án của bạn, hãy kiểm tra `node_modules/@earendil-works/pi-coding-agent/dist/`.

## Tổng quan (Overview)

Pi có hai cơ chế tóm tắt:

| Mechanism | Trigger | Purpose |
|-----------|---------|---------|
| Compaction | Context exceeds threshold, or `/compact` | Summarize old messages to free up context |
| Branch summarization | `/tree` navigation | Preserve context when switching branches |

Cả hai dùng cùng định dạng tóm tắt có cấu trúc và theo dõi các thao tác tệp một cách tích lũy. Các yêu cầu compaction và branch-summary dùng routing session ID mới và, ở nơi provider hỗ trợ, vô hiệu hóa ghi prompt-cache vì các prompt một lần này khó có thể được tái sử dụng.

## Compaction

### Khi nào kích hoạt (When It Triggers)

Auto-compaction kích hoạt khi:

```
contextTokens > contextWindow - reserveTokens
```

Theo mặc định, `reserveTokens` là 16384 token (có thể cấu hình trong `~/.pi/agent/settings.json` hoặc `<project-dir>/.pi/settings.json`). Điều này chừa chỗ cho phản hồi của LLM.

Trong một lần chạy agent nhiều lượt, Pi kiểm tra ngưỡng này sau khi tools kết thúc và kết quả của chúng được nối vào, trước khi bắt đầu phản hồi assistant tiếp theo. Nếu vượt ngưỡng, Pi thực hiện compaction ngay trong cùng lần chạy agent và tiếp tục với bản tóm tắt cùng các tin nhắn được giữ lại. Nó bỏ qua bước kiểm tra giữa các lượt này khi batch tool đã hoàn thành kết thúc lần chạy và không có tin nhắn xếp hàng nào cần phản hồi khác. Pi cũng kiểm tra ngưỡng trước một prompt user mới và sau khi một lần chạy agent cấp thấp kết thúc.

Bạn cũng có thể kích hoạt thủ công bằng `/compact [instructions]`, trong đó instructions tùy chọn giúp tập trung vào nội dung tóm tắt.

### Cách hoạt động (How It Works)

1. **Tìm điểm cắt (cut point)**: Đi ngược từ tin nhắn mới nhất, cộng dồn ước lượng token cho đến khi đạt `keepRecentTokens` (mặc định 20k, có thể cấu hình trong `~/.pi/agent/settings.json` hoặc `<project-dir>/.pi/settings.json`)
2. **Trích xuất tin nhắn**: Thu thập các tin nhắn từ ranh giới được giữ trước đó (hoặc đầu session) tới điểm cắt
3. **Tạo tóm tắt**: Gọi LLM để tóm tắt theo định dạng có cấu trúc, truyền bản tóm tắt trước đó làm ngữ cảnh lặp khi có
4. **Nối entry**: Lưu `CompactionEntry` kèm tóm tắt và `firstKeptEntryId`
5. **Dựng lại context**: Session dựng lại context cho yêu cầu tiếp theo, dùng tóm tắt + các tin nhắn từ `firstKeptEntryId` trở đi

```
Before compaction:

  entry:  0     1     2     3      4     5     6      7      8     9
        ┌─────┬─────┬─────┬──────┬─────┬─────┬──────┬──────┬─────┬─────┐
        │ hdr │ usr │ ass │ tool │ usr │ ass │ tool │ tool │ ass │ tool│
        └─────┴─────┴─────┴──────┴─────┴─────┴──────┴──────┴─────┴─────┘
                └────────┬───────┘ └──────────────┬──────────────┘
               messagesToSummarize            kept messages
                                   ↑
                          firstKeptEntryId (entry 4)

After compaction (new entry appended):

  entry:  0     1     2     3      4     5     6      7      8     9     10
        ┌─────┬─────┬─────┬──────┬─────┬─────┬──────┬──────┬─────┬─────┬─────┐
        │ hdr │ usr │ ass │ tool │ usr │ ass │ tool │ tool │ ass │ tool│ cmp │
        └─────┴─────┴─────┴──────┴─────┴─────┴──────┴──────┴─────┴─────┴─────┘
               └──────────┬──────┘ └──────────────────────┬───────────────────┘
                 not sent to LLM                    sent to LLM
                                                         ↑
                                              starts from firstKeptEntryId

What the LLM sees:

  ┌────────┬─────────┬─────┬─────┬──────┬──────┬─────┬──────┐
  │ system │ summary │ usr │ ass │ tool │ tool │ ass │ tool │
  └────────┴─────────┴─────┴─────┴──────┴──────┴─────┴──────┘
       ↑         ↑      └─────────────────┬────────────────┘
    prompt   from cmp          messages from firstKeptEntryId
```

Khi compaction lặp lại, đoạn được tóm tắt bắt đầu từ ranh giới được giữ lại của lần compaction trước (`firstKeptEntryId`), không phải từ chính entry compaction, và quay về entry ngay sau lần compaction trước nếu không tìm thấy entry đã giữ đó trong đường dẫn. Điều này bảo toàn các tin nhắn đã sống sót qua compaction trước bằng cách đưa chúng vào luồng tóm tắt tiếp theo. Pi cũng tính lại `tokensBefore` từ context session đã dựng lại trước khi ghi `CompactionEntry` mới, để số token phản ánh đúng context thực tế trước compaction đang bị thay thế.

### Lượt bị tách (Split Turns)

Một "turn" bắt đầu bằng tin nhắn user và bao gồm tất cả phản hồi assistant cùng tool call cho đến tin nhắn user tiếp theo. Thông thường, compaction cắt tại ranh giới turn.

Khi một turn đơn vượt quá `keepRecentTokens`, điểm cắt nằm giữa turn tại một tin nhắn assistant. Đó là "split turn":

```
Split turn (one huge turn exceeds budget):

  entry:  0     1     2      3     4      5      6     7      8
        ┌─────┬─────┬─────┬──────┬─────┬──────┬──────┬─────┬──────┐
        │ hdr │ usr │ ass │ tool │ ass │ tool │ tool │ ass │ tool │
        └─────┴─────┴─────┴──────┴─────┴──────┴──────┴─────┴──────┘
                ↑                                     ↑
         turnStartIndex = 1                  firstKeptEntryId = 7
                │                                     │
                └──── turnPrefixMessages (1-6) ───────┘
                                                      └── kept (7-8)

  isSplitTurn = true
  messagesToSummarize = []  (no complete turns before)
  turnPrefixMessages = [usr, ass, tool, ass, tool, tool]
```

Với split turn, Pi tạo hai bản tóm tắt rồi hợp nhất:
1. **History summary**: Ngữ cảnh trước đó (nếu có)
2. **Turn prefix summary**: Phần đầu của turn bị tách

### Quy tắc điểm cắt (Cut Point Rules)

Các điểm cắt hợp lệ là:
- Tin nhắn user
- Tin nhắn assistant
- Tin nhắn BashExecution
- Tin nhắn custom (custom_message, branch_summary)

Không bao giờ cắt tại kết quả tool (chúng phải ở cùng với tool call của mình).

### Cấu trúc CompactionEntry

Định nghĩa trong [`session-manager.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/session-manager.ts):

```typescript
interface CompactionEntry<T = unknown> {
  type: "compaction";
  id: string;
  parentId: string;
  timestamp: number;
  summary: string;
  firstKeptEntryId: string;
  tokensBefore: number;
  usage?: Usage;       // LLM usage that generated the summary
  fromHook?: boolean;  // true if provided by extension (legacy field name)
  details?: T;         // implementation-specific data
}

// Default compaction uses this for details (from compaction.ts):
interface CompactionDetails {
  readFiles: string[];
  modifiedFiles: string[];
}
```

Extension có thể lưu bất kỳ dữ liệu nào có thể serialize JSON trong `details`. Compaction mặc định theo dõi các thao tác tệp, nhưng các triển khai extension tùy chỉnh có thể dùng cấu trúc riêng. Bản tóm tắt do hệ thống tạo và do extension cung cấp đều lưu `usage` của LLM khi có, để tổng số token của session tính cả công việc tóm tắt.

Xem [`prepareCompaction()`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/compaction.ts) và [`compact()`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/compaction.ts) để biết triển khai. Để tóm tắt trực tiếp bằng lập trình, `generateSummary()` trả về văn bản tóm tắt và `generateSummaryWithUsage()` trả về `{ text, usage }`.

## Branch Summarization (Tóm tắt nhánh)

### Khi nào kích hoạt (When It Triggers)

Khi bạn dùng `/tree` để điều hướng sang nhánh khác, Pi đề nghị tóm tắt công việc bạn đang rời đi. Điều này đưa ngữ cảnh từ nhánh bị bỏ vào nhánh mới.

### Cách hoạt động (How It Works)

1. **Tìm tổ tiên chung (common ancestor)**: Node sâu nhất được cả vị trí cũ và mới chia sẻ
2. **Thu thập entry**: Đi từ leaf cũ ngược về tổ tiên chung
3. **Chuẩn bị theo ngân sách (budget)**: Bao gồm các tin nhắn trong giới hạn token (mới nhất trước)
4. **Tạo tóm tắt**: Gọi LLM với định dạng có cấu trúc
5. **Nối entry**: Lưu `BranchSummaryEntry` tại điểm điều hướng

```
Tree before navigation:

         ┌─ B ─ C ─ D (old leaf, being abandoned)
    A ───┤
         └─ E ─ F (target)

Common ancestor: A
Entries to summarize: B, C, D

After navigation with summary:

         ┌─ B ─ C ─ D
    A ───┤
         └─ E ─ F ─ [summary of B,C,D] (new leaf)
```

### Theo dõi tệp tích lũy (Cumulative File Tracking)

Cả compaction và branch summarization đều theo dõi tệp một cách tích lũy. Khi tạo tóm tắt, pi trích xuất các thao tác tệp từ:
- Tool call trong các tin nhắn đang được tóm tắt
- `details` của compaction hoặc branch summary trước đó (nếu có)

Điều này nghĩa là việc theo dõi tệp tích lũy qua nhiều lần compaction hoặc branch summary lồng nhau, bảo toàn lịch sử đầy đủ của các tệp đã đọc và đã sửa.

### Cấu trúc BranchSummaryEntry

Định nghĩa trong [`session-manager.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/session-manager.ts):

```typescript
interface BranchSummaryEntry<T = unknown> {
  type: "branch_summary";
  id: string;
  parentId: string;
  timestamp: number;
  summary: string;
  fromId: string;      // Entry we navigated from
  usage?: Usage;       // LLM usage that generated the summary
  fromHook?: boolean;  // true if provided by extension (legacy field name)
  details?: T;         // implementation-specific data
}

// Default branch summarization uses this for details (from branch-summarization.ts):
interface BranchSummaryDetails {
  readFiles: string[];
  modifiedFiles: string[];
}
```

Giống như compaction, extension có thể lưu dữ liệu tùy chỉnh trong `details`.

Xem [`collectEntriesForBranchSummary()`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/branch-summarization.ts), [`prepareBranchEntries()`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/branch-summarization.ts) và [`generateBranchSummary()`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/branch-summarization.ts) để biết triển khai.

## Định dạng tóm tắt (Summary Format)

Cả compaction và branch summarization đều dùng cùng định dạng có cấu trúc:

```markdown
## Goal
[What the user is trying to accomplish]

## Constraints & Preferences
- [Requirements mentioned by user]

## Progress
### Done
- [x] [Completed tasks]

### In Progress
- [ ] [Current work]

### Blocked
- [Issues, if any]

## Key Decisions
- **[Decision]**: [Rationale]

## Next Steps
1. [What should happen next]

## Critical Context
- [Data needed to continue]

<read-files>
path/to/file1.ts
path/to/file2.ts
</read-files>

<modified-files>
path/to/changed.ts
</modified-files>
```

### Serialize tin nhắn (Message Serialization)

Trước khi tóm tắt, tin nhắn được serialize thành văn bản qua [`serializeConversation()`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/compaction/utils.ts):

```
[User]: What they said
[Assistant thinking]: Internal reasoning
[Assistant]: Response text
[Assistant tool calls]: read(path="foo.ts"); edit(path="bar.ts", ...)
[Tool result]: Output from tool
```

Điều này ngăn model coi đây là cuộc hội thoại cần tiếp tục.

Kết quả tool bị cắt ngắn ở 2000 ký tự trong quá trình serialize. Nội dung vượt quá giới hạn được thay bằng một marker cho biết đã cắt bao nhiêu ký tự. Điều này giữ cho yêu cầu tóm tắt nằm trong ngân sách token hợp lý, vì kết quả tool (đặc biệt từ `read` và `bash`) thường là nguồn đóng góp lớn nhất vào kích thước context.

## Tóm tắt tùy chỉnh qua Extension

Extension có thể chặn và tùy chỉnh cả compaction lẫn branch summarization. Xem [`extensions/types.ts`](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/extensions/types.ts) để biết định nghĩa kiểu sự kiện.

### session_before_compact

Kích hoạt trước auto-compaction hoặc `/compact`. Có thể hủy hoặc cung cấp bản tóm tắt tùy chỉnh. Xem `SessionBeforeCompactEvent` và `CompactionPreparation` trong tệp types.

```typescript
pi.on("session_before_compact", async (event, ctx) => {
  const { preparation, branchEntries, customInstructions, reason, willRetry, signal } = event;

  // preparation.messagesToSummarize - messages to summarize
  // preparation.turnPrefixMessages - split turn prefix (if isSplitTurn)
  // preparation.previousSummary - previous compaction summary
  // preparation.fileOps - extracted file operations
  // preparation.tokensBefore - context tokens before compaction
  // preparation.firstKeptEntryId - where kept messages start
  // preparation.settings - effective settings after applying model overrides

  // branchEntries - all entries on current branch (for custom state)
  // reason - "manual" (/compact), "threshold", or "overflow"
  // willRetry - whether the aborted turn is retried after compaction (overflow recovery)
  // signal - AbortSignal (pass to LLM calls)

  // Cancel:
  return { cancel: true };

  // Custom summary:
  return {
    compaction: {
      summary: "Your summary...",
      firstKeptEntryId: preparation.firstKeptEntryId,
      tokensBefore: preparation.tokensBefore,
      // usage: summaryResponse.usage, // Optional; included in session totals
      details: { /* custom data */ },
    }
  };
});
```

#### Chuyển tin nhắn thành văn bản

Để tạo tóm tắt bằng model của riêng bạn, hãy chuyển tin nhắn thành văn bản bằng `serializeConversation`:

```typescript
import { convertToLlm, serializeConversation } from "@earendil-works/pi-coding-agent";

pi.on("session_before_compact", async (event, ctx) => {
  const { preparation } = event;
  
  // Convert AgentMessage[] to Message[], then serialize to text
  const conversationText = serializeConversation(
    convertToLlm(preparation.messagesToSummarize)
  );
  // Returns:
  // [User]: message text
  // [Assistant thinking]: thinking content
  // [Assistant]: response text
  // [Assistant tool calls]: read(path="..."); bash(command="...")
  // [Tool result]: output text

  // Now send to your model for summarization
  const { summary, usage } = await myModel.summarize(conversationText);
  
  return {
    compaction: {
      summary,
      firstKeptEntryId: preparation.firstKeptEntryId,
      tokensBefore: preparation.tokensBefore,
      usage,
    }
  };
});
```

Xem [custom-compaction.ts](../examples/extensions/custom-compaction.ts) để có ví dụ hoàn chỉnh dùng một model khác.

### session_compact_failed

Kích hoạt khi compaction thủ công hoặc tự động thất bại hoặc bị hủy. Hữu ích cho extension telemetry cần ghép các cố gắng `session_before_compact` với kết quả cuối cùng.

```typescript
pi.on("session_compact_failed", async (event, ctx) => {
  const { reason, errorMessage, aborted, willRetry, fromExtension } = event;
  // reason - "manual" (/compact), "threshold", or "overflow"
  // errorMessage - present for non-abort failures
  // aborted - true for cancelled/aborted compactions
  // willRetry - whether the aborted turn would have retried after compaction
  // fromExtension - whether extension-provided compaction content was being used
});
```

### session_before_tree

Kích hoạt trước điều hướng `/tree`. Luôn kích hoạt bất kể người dùng có chọn tóm tắt hay không. Có thể hủy điều hướng hoặc cung cấp bản tóm tắt tùy chỉnh.

```typescript
pi.on("session_before_tree", async (event, ctx) => {
  const { preparation, signal } = event;

  // preparation.targetId - where we're navigating to
  // preparation.oldLeafId - current position (being abandoned)
  // preparation.commonAncestorId - shared ancestor
  // preparation.entriesToSummarize - entries that would be summarized
  // preparation.userWantsSummary - whether user chose to summarize

  // Cancel navigation entirely:
  return { cancel: true };

  // Provide custom summary (only used if userWantsSummary is true):
  if (preparation.userWantsSummary) {
    return {
      summary: {
        summary: "Your summary...",
        // usage: summaryResponse.usage, // Optional; included in session totals
        details: { /* custom data */ },
      }
    };
  }
});
```

Xem `SessionBeforeTreeEvent` và `TreePreparation` trong tệp types.

## Settings

Cấu hình compaction trong `~/.pi/agent/settings.json` hoặc `<project-dir>/.pi/settings.json`:

```json
{
  "compaction": {
    "enabled": true,
    "reserveTokens": 16384,
    "keepRecentTokens": 20000
  }
}
```

| Setting | Default | Description |
|---------|---------|-------------|
| `enabled` | `true` | Enable auto-compaction |
| `reserveTokens` | `16384` | Tokens to reserve for LLM response |
| `keepRecentTokens` | `20000` | Recent tokens to keep (not summarized) |

Vô hiệu hóa auto-compaction với `"enabled": false`. Bạn vẫn có thể compact thủ công bằng `/compact`.

### Ghi đè theo từng model (Per-model overrides)

Dùng `compaction.modelOverrides` để tinh chỉnh ngân sách token cho các model khác nhau:

```json
{
  "compaction": {
    "reserveTokens": 16384,
    "keepRecentTokens": 20000,
    "modelOverrides": {
      "some-provider/big-model": {
        "reserveTokens": 400000
      }
    }
  }
}
```

Đối với model có context window 1M, ghi đè này kích hoạt compaction trên 600K token và giữ nguyên 20000 token gần đây như thường. Các model khác giữ nguyên reserve 16384 token như thường. `reserveTokens` cũng ảnh hưởng đến giới hạn đầu ra tóm tắt, bị chặn bởi số token đầu ra tối đa của model; nó không chỉ là ngưỡng kích hoạt.

Khóa là giá trị `provider/modelId` chính xác, phân biệt chữ hoa/thường, bao gồm cả các dấu gạch chéo trong model ID. Mỗi giá trị `reserveTokens` và `keepRecentTokens` được fallback độc lập từ ghi đè model đến setting thường đến giá trị mặc định tích hợp. Giá trị phải là số nguyên an toàn không âm. Giá trị không hợp lệ trong ghi đè model khớp sẽ gây lỗi khi đọc; chỉ các trường bị bỏ sót mới fallback về setting thường. Các mục ghi đè model phải là đối tượng. Setting token thường không hợp lệ gây lỗi khi đọc, ngay cả khi model đang hoạt động có ghi đè hợp lệ. Chỉ các giá trị thường bị bỏ sót mới dùng giá trị mặc định tích hợp. `enabled` vẫn mang tính toàn cục, không theo từng model.

Các giá trị đã resolve này được dùng cho compaction thủ công, mọi kiểm tra ngưỡng tự động, phục hồi overflow và `preparation.settings` visible với extension. Việc đổi model ảnh hưởng đến các kiểm tra và compaction sau đó mà không thay đổi setting thường. Compaction đang tiến hành dùng model và settings đã được ghi nhận cho thao tác đó. Setting branch summarization không bị ảnh hưởng.

Ghi đè hoạt động trong cả settings toàn cục lẫn dự án. Các tệp được hợp nhất đệ quy trước khi tra cứu, nên giá trị global theo model cụ thể sẽ thắng fallback toàn dự án; dự án phải ghi đè chính mục model đó thì mới thay đổi được. Xem [settings.md](settings.md#per-model-compaction-overrides) để biết chi tiết.
