> pi có thể tạo extension. Hãy yêu cầu nó xây dựng một extension cho trường hợp sử dụng của bạn.

# Extensions (Tiện ích mở rộng)

Extension là các module TypeScript mở rộng hành vi của pi. Chúng có thể đăng ký các sự kiện vòng đời, đăng ký công cụ tùy chỉnh để LLM gọi, thêm lệnh, và nhiều hơn nữa.

> **Vị trí để dùng `/reload`:** Đặt extension vào `~/.pi/agent/extensions/` (toàn cục) hoặc `.pi/extensions/` (cục bộ theo dự án) để tự động phát hiện. Chỉ dùng `pi -e ./path.ts` cho các bài test nhanh. Extension nằm ở vị trí được tự động phát hiện có thể được tải lại nóng bằng `/reload`.

**Khả năng chính:**
- **Công cụ tùy chỉnh** — Đăng ký công cụ để LLM gọi qua `pi.registerTool()`
- **Chặn sự kiện** — Chặn hoặc sửa đổi tool call, chèn context, tùy chỉnh compaction
- **Tương tác người dùng** — Nhắc người dùng qua `ctx.ui` (select, confirm, input, notify)
- **Thành phần UI tùy chỉnh** — Các thành phần TUI đầy đủ với khả năng nhập bằng bàn phím qua `ctx.ui.custom()` cho các tương tác phức tạp
- **Lệnh tùy chỉnh** — Đăng ký các lệnh như `/mycommand` qua `pi.registerCommand()`
- **Lưu session** — Lưu trạng thái tồn tại qua các lần khởi động lại qua `pi.appendEntry()`
- **Render tùy chỉnh** — Kiểm soát cách tool call/kết quả và tin nhắn hiển thị trong TUI

**Ví dụ trường hợp sử dụng:**
- Cổng xác nhận quyền (xác nhận trước `rm -rf`, `sudo`, v.v.)
- Checkpoint Git (stash ở mỗi lượt, khôi phục trên branch)
- Bảo vệ đường dẫn (chặn ghi vào `.env`, `node_modules/`)
- Compaction tùy chỉnh (tóm tắt cuộc hội thoại theo cách của bạn)
- Tóm tắt hội thoại (xem ví dụ `summarize.ts`)
- Công cụ tương tác (câu hỏi, wizard, hộp thoại tùy chỉnh)
- Công cụ có trạng thái (danh sách todo, pool kết nối)
- Tích hợp bên ngoài (file watcher, webhook, trigger CI)
- Trò chơi trong lúc chờ (xem ví dụ `snake.ts`)

Xem [examples/extensions/](../examples/extensions/) để có các triển khai chạy được.

## Mục lục

- [Bắt đầu nhanh](#quick-start)
- [Vị trí Extension](#extension-locations)
- [Import có sẵn](#available-imports)
- [Viết Extension](#writing-an-extension)
  - [Kiểu Extension](#extension-styles)
- [Sự kiện](#events)
  - [Tổng quan vòng đời](#lifecycle-overview)
  - [Sự kiện tài nguyên](#resource-events)
  - [Sự kiện Session](#session-events)
  - [Sự kiện Agent](#agent-events)
  - [Sự kiện Model](#model-events)
  - [Sự kiện Tool](#tool-events)
- [ExtensionContext](#extensioncontext)
- [ExtensionCommandContext](#extensioncommandcontext)
- [Phương thức ExtensionAPI](#extensionapi-methods)
- [Quản lý trạng thái](#state-management)
- [Công cụ tùy chỉnh](#custom-tools)
  - [Tải công cụ động](#dynamic-tool-loading)
- [UI tùy chỉnh](#custom-ui)
- [Xử lý lỗi](#error-handling)
- [Hành vi chế độ](#mode-behavior)
- [Tham chiếu ví dụ](#examples-reference)

## Bắt đầu nhanh

Tạo `~/.pi/agent/extensions/my-extension.ts`:

```typescript
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";

export default function (pi: ExtensionAPI) {
  // React to events
  pi.on("session_start", async (_event, ctx) => {
    ctx.ui.notify("Extension loaded!", "info");
  });

  pi.on("tool_call", async (event, ctx) => {
    if (event.toolName === "bash" && event.input.command?.includes("rm -rf")) {
      const ok = await ctx.ui.confirm("Dangerous!", "Allow rm -rf?");
      if (!ok) return { block: true, reason: "Blocked by user" };
    }
  });

  // Register a custom tool
  pi.registerTool({
    name: "greet",
    label: "Greet",
    description: "Greet someone by name",
    parameters: Type.Object({
      name: Type.String({ description: "Name to greet" }),
    }),
    async execute(toolCallId, params, signal, onUpdate, ctx) {
      return {
        content: [{ type: "text", text: `Hello, ${params.name}!` }],
        details: {},
      };
    },
  });

  // Register a command
  pi.registerCommand("hello", {
    description: "Say hello",
    handler: async (args, ctx) => {
      ctx.ui.notify(`Hello ${args || "world"}!`, "info");
    },
  });
}
```

Test với cờ `--extension` (hoặc `-e`):

```bash
pi -e ./my-extension.ts
```

## Vị trí Extension

> **Bảo mật:** Extension chạy với toàn bộ quyền của hệ thống bạn và có thể thực thi mã tùy ý. Chỉ cài đặt từ nguồn bạn tin cậy.

Extension được tự động phát hiện từ các vị trí đáng tin cậy. Các mục cục bộ theo dự án trong `.pi/extensions` chỉ được tải sau khi dự án được đánh dấu là tin cậy.

| Vị trí | Phạm vi |
|----------|-------|
| `~/.pi/agent/extensions/*.ts` | Toàn cục (tất cả dự án) |
| `~/.pi/agent/extensions/*/index.ts` | Toàn cục (thư mục con) |
| `.pi/extensions/*.ts` | Cục bộ theo dự án |
| `.pi/extensions/*/index.ts` | Cục bộ theo dự án (thư mục con) |

Các đường dẫn bổ sung qua `settings.json`:

```json
{
  "packages": [
    "npm:@foo/bar@1.0.0",
    "git:github.com/user/repo@v1"
  ],
  "extensions": [
    "/path/to/local/extension.ts",
    "/path/to/local/extension/dir"
  ]
}
```

Để chia sẻ extension qua npm hoặc git dưới dạng pi package, xem [packages.md](packages.md).

## Import có sẵn

| Package | Mục đích |
|---------|---------|
| `@earendil-works/pi-coding-agent` | Kiểu extension (`ExtensionAPI`, `ExtensionContext`, sự kiện) |
| `typebox` | Định nghĩa schema cho tham số công cụ |
| `@earendil-works/pi-ai` | Tiện ích AI (`StringEnum` cho enum tương thích Google) |
| `@earendil-works/pi-tui` | Thành phần TUI để render tùy chỉnh |

Dependency npm cũng hoạt động. Thêm `package.json` cạnh extension (hoặc trong thư mục cha), chạy `npm install`, và các import từ `node_modules/` sẽ được tự động phân giải.

Với pi package phân phối được cài bằng `pi install` (npm hoặc git), dependency runtime phải nằm trong `dependencies`. Việc cài package dùng bản cài production (`npm install --omit=dev`) theo mặc định, nên `devDependencies` không có sẵn lúc runtime; khi `npmCommand` được cấu hình, package git dùng lệnh `install` thuần để tương thích với các wrapper.

Các built-in của Node.js (`node:fs`, `node:path`, v.v.) cũng có sẵn.

## Viết Extension

Một extension export một hàm factory mặc định nhận `ExtensionAPI`. Factory có thể đồng bộ hoặc bất đồng bộ:

```typescript
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default function (pi: ExtensionAPI) {
  // Subscribe to events
  pi.on("event_name", async (event, ctx) => {
    // ctx.ui for user interaction
    const ok = await ctx.ui.confirm("Title", "Are you sure?");
    ctx.ui.notify("Done!", "info");
    ctx.ui.setStatus("my-ext", "Processing...");  // Footer status
    ctx.ui.setWidget("my-ext", ["Line 1", "Line 2"]);  // Widget above editor (default)
  });

  // Register tools, commands, shortcuts, flags
  pi.registerTool({ ... });
  pi.registerCommand("name", { ... });
  pi.registerShortcut("ctrl+x", { ... });
  pi.registerFlag("my-flag", { ... });
}
```

Extension được tải qua [jiti](https://github.com/unjs/jiti), nên TypeScript hoạt động mà không cần biên dịch.

Nếu factory trả về `Promise`, pi sẽ chờ nó trước khi tiếp tục khởi động. Điều đó có nghĩa là khởi tạo bất đồng bộ hoàn tất trước `session_start`, trước `resources_discover`, và trước khi các đăng ký provider được xếp hàng qua `pi.registerProvider()` được flush.

### Hàm factory bất đồng bộ

Dùng factory bất đồng bộ cho công việc khởi động một lần như lấy cấu hình từ xa hoặc tự động phát hiện model có sẵn.

```typescript
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";

export default async function (pi: ExtensionAPI) {
  const response = await fetch("http://localhost:1234/v1/models");
  const payload = (await response.json()) as {
    data: Array<{
      id: string;
      name?: string;
      context_window?: number;
      max_tokens?: number;
    }>;
  };

  pi.registerProvider("local-openai", {
    baseUrl: "http://localhost:1234/v1",
    apiKey: "[REDACTED]",
    api: "openai-completions",
    models: payload.data.map((model) => ({
      id: model.id,
      name: model.name ?? model.id,
      reasoning: false,
      input: ["text"],
      cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
      contextWindow: model.context_window ?? 128000,
      maxTokens: model.max_tokens ?? 4096,
    })),
  });
}
```

Mẫu này giúp các model vừa lấy được có sẵn trong quá trình khởi động bình thường và cho `pi --list-models`.

### Tài nguyên tồn tại lâu và shutdown

Factory extension có thể chạy trong các lần gọi mà không bao giờ bắt đầu session. Đừng khởi động các tài nguyên nền như process, socket, file watcher, hay timer từ factory.

Hãy trì hoãn việc khởi động tài nguyên nền cho đến `session_start` hoặc lệnh/công cụ/sự kiện cần tài nguyên đó. Đăng ký một handler `session_shutdown` có tính idempotent để đóng bất kỳ tài nguyên phạm vi session nào bạn khởi động.

### Kiểu Extension

**Một tệp** — đơn giản nhất, cho extension nhỏ:

```
~/.pi/agent/extensions/
└── my-extension.ts
```

**Thư mục với index.ts** — cho extension nhiều tệp:

```
~/.pi/agent/extensions/
└── my-extension/
    ├── index.ts        # Entry point (exports default function)
    ├── tools.ts        # Helper module
    └── utils.ts        # Helper module
```

**Package có dependency** — cho extension cần package npm:

```
~/.pi/agent/extensions/
└── my-extension/
    ├── package.json    # Declares dependencies and entry points
    ├── package-lock.json
    ├── node_modules/   # After npm install
    └── src/
        └── index.ts
```

```json
// package.json
{
  "name": "my-extension",
  "dependencies": {
    "zod": "^3.0.0",
    "chalk": "^5.0.0"
  },
  "pi": {
    "extensions": ["./src/index.ts"]
  }
}
```

Chạy `npm install` trong thư mục extension, rồi các import từ `node_modules/` sẽ hoạt động tự động.

## Sự kiện

### Tổng quan vòng đời

```
pi starts
  │
  ├─► project_trust (user/global and CLI extensions only, before project resources load)
  ├─► session_start { reason: "startup" }
  └─► resources_discover { reason: "startup" }
      │
      ▼
user sends prompt ─────────────────────────────────────────┐
  │                                                        │
  ├─► (extension commands checked first, bypass if found)  │
  ├─► input (can intercept, transform, or handle)          │
  ├─► (skill/template expansion if not handled)            │
  ├─► before_agent_start (can inject message, modify system prompt)
  ├─► agent_start                                          │
  ├─► message_start / message_update / message_end         │
  │                                                        │
  │   ┌─── turn (repeats while LLM calls tools) ───┐       │
  │   │                                            │       │
  │   ├─► turn_start                               │       │
  │   ├─► context (can modify messages)            │       │
  │   ├─► before_provider_headers (can mutate headers)     |
  │   ├─► before_provider_request (can inspect or replace payload)
  │   ├─► after_provider_response (status + headers, before stream consume)
  │   │                                            │       │
  │   │   LLM responds, may call tools:            │       │
  │   │     ├─► tool_execution_start               │       │
  │   │     ├─► tool_call (can block)              │       │
  │   │     ├─► tool_execution_update              │       │
  │   │     ├─► tool_result (can modify)           │       │
  │   │     └─► tool_execution_end                 │       │
  │   │                                            │       │
  │   └─► turn_end                                 │       │
  │                                                        │
  ├─► agent_end                                            │
  └─► agent_settled (no retry/compaction/follow-up left)   │
                                                           │
user sends another prompt ◄────────────────────────────────┘

/new (new session) or /resume (switch session)
  ├─► session_before_switch (can cancel)
  ├─► session_shutdown
  ├─► session_start { reason: "new" | "resume", previousSessionFile? }
  └─► resources_discover { reason: "startup" }

/fork or /clone
  ├─► session_before_fork (can cancel)
  ├─► session_shutdown
  ├─► session_start { reason: "fork", previousSessionFile }
  └─► resources_discover { reason: "startup" }

/name or pi.setSessionName()
  └─► session_info_changed

/compact or auto-compaction
  ├─► session_before_compact (can cancel or customize)
  ├─► session_compact (success)
  └─► session_compact_failed (failure or abort)

/tree navigation
  ├─► session_before_tree (can cancel or customize)
  └─► session_tree

/model or Ctrl+P (model selection/cycling)
  ├─► thinking_level_select (if model change changes/clamps thinking level)
  └─► model_select

thinking level changes (settings, keybinding, pi.setThinkingLevel())
  └─► thinking_level_select

exit (Ctrl+C, Ctrl+D, SIGHUP, SIGTERM)
  └─► session_shutdown
```

### Sự kiện khởi động

#### project_trust

Được kích hoạt trước khi pi quyết định có tin cậy một dự án với cấu hình động (`.pi` hoặc `.agents/skills`). Sự kiện chạy trong lúc khởi động và khi thay thế session (ví dụ `/resume`) đi vào một cwd mà quyền tin cậy chưa được giải quyết trong tiến trình hiện tại. Chỉ extension user/global và extension CLI `-e` tham gia; extension cục bộ theo dự án không được tải cho đến sau khi quyền tin cậy được giải quyết.

```typescript
pi.on("project_trust", async (event, ctx) => {
  // event.cwd - current working directory
  // ctx has a limited trust context: cwd, mode, hasUI, and select/confirm/input/notify UI helpers
  if (await ctx.ui.confirm("Trust project?", event.cwd)) {
    return { trusted: "yes", remember: true };
  }
  return { trusted: "undecided" };
});
```

Handler `project_trust` phải trả về `{ trusted: "yes" | "no" | "undecided" }`. Một extension user/global hoặc CLI trả về `"yes"` hoặc `"no"` sẽ nắm quyền quyết định; quyết định yes/no đầu tiên thắng và triệt tiêu lời nhắc tin cậy tích hợp sẵn. Dùng `remember: true` để lưu bền quyết định yes/no; nếu không, quyết định chỉ áp dụng cho tiến trình hiện tại. Trả về `"undecided"` để handler sau hoặc luồng tin cậy tích hợp quyết định. Kiểm tra `ctx.hasUI` trước khi nhắc người dùng. Nếu không handler nào trả về yes/no, quá trình giải quyết tin cậy bình thường tiếp tục: quyết định `trust.json` đã lưu được áp dụng trước, sau đó `defaultProjectTrust` kiểm soát việc pi hỏi, tin cậy, hay từ chối theo mặc định.

### Sự kiện tài nguyên

#### resources_discover

Được kích hoạt sau `session_start` để extension có thể bổ sung thêm đường dẫn skill, prompt, và theme.
Đường khởi động dùng `reason: "startup"`. Lần reload dùng `reason: "reload"`.

```typescript
pi.on("resources_discover", async (event, _ctx) => {
  // event.cwd - current working directory
  // event.reason - "startup" | "reload"
  return {
    skillPaths: ["/path/to/skills"],
    promptPaths: ["/path/to/prompts"],
    themePaths: ["/path/to/themes"],
  };
});
```

### Sự kiện Session

Xem [Session Format](session-format.md) để biết chi tiết lưu trữ session và API SessionManager.

#### session_start

Được kích hoạt khi một session được bắt đầu, tải, hoặc tải lại.

```typescript
pi.on("session_start", async (event, ctx) => {
  // event.reason - "startup" | "reload" | "new" | "resume" | "fork"
  // event.previousSessionFile - present for "new", "resume", and "fork"
  ctx.ui.notify(`Session: ${ctx.sessionManager.getSessionFile() ?? "ephemeral"}`, "info");
});
```

#### session_info_changed

Được kích hoạt khi tên hiển thị của session hiện tại được đặt qua `/name`, RPC, hoặc `pi.setSessionName()`.

```typescript
pi.on("session_info_changed", async (event, ctx) => {
  // event.name - current normalized name, or undefined if cleared
  ctx.ui.notify(`Session renamed: ${event.name ?? "(none)"}`, "info");
});
```

#### session_before_switch

Được kích hoạt trước khi bắt đầu session mới (`/new`) hoặc chuyển session (`/resume`).

```typescript
pi.on("session_before_switch", async (event, ctx) => {
  // event.reason - "new" or "resume"
  // event.targetSessionFile - session we're switching to (only for "resume")

  if (event.reason === "new") {
    const ok = await ctx.ui.confirm("Clear?", "Delete all messages?");
    if (!ok) return { cancel: true };
  }
});
```

Sau khi chuyển session hoặc tạo session mới thành công, pi phát `session_shutdown` cho instance extension cũ, tải lại và gắn lại extension cho session mới, rồi phát `session_start` với `reason: "new" | "resume"` và `previousSessionFile`.
Hãy dọn dẹp trong `session_shutdown`, rồi thiết lập lại mọi trạng thái trong bộ nhớ ở `session_start`.

#### session_before_fork

Được kích hoạt khi fork qua `/fork` hoặc clone qua `/clone`.

```typescript
pi.on("session_before_fork", async (event, ctx) => {
  // event.entryId - ID of the selected entry
  // event.position - "before" for /fork, "at" for /clone
  return { cancel: true }; // Cancel fork/clone
  // OR
  return { skipConversationRestore: true }; // Reserved for future conversation restore control
});
```

Sau khi fork hoặc clone thành công, pi phát `session_shutdown` cho instance extension cũ, tải lại và gắn lại extension cho session mới, rồi phát `session_start` với `reason: "fork"` và `previousSessionFile`.
Hãy dọn dẹp trong `session_shutdown`, rồi thiết lập lại mọi trạng thái trong bộ nhớ ở `session_start`.

#### session_before_compact / session_compact / session_compact_failed

Được kích hoạt khi compaction. Xem [compaction.md](compaction.md) để biết chi tiết.

```typescript
pi.on("session_before_compact", async (event, ctx) => {
  const { preparation, branchEntries, customInstructions, reason, willRetry, signal } = event;

  // reason - "manual" (/compact), "threshold", or "overflow"
  // willRetry - whether the aborted turn is retried after compaction (overflow recovery)

  // Cancel:
  return { cancel: true };

  // Custom summary:
  return {
    compaction: {
      summary: "...",
      firstKeptEntryId: preparation.firstKeptEntryId,
      tokensBefore: preparation.tokensBefore,
      // usage: summaryResponse.usage, // Optional; included in session totals
    }
  };
});

pi.on("session_compact", async (event, ctx) => {
  // event.compactionEntry - the saved compaction
  // event.fromExtension - whether extension provided it
  // event.reason - "manual" (/compact), "threshold", or "overflow"
  // event.willRetry - whether the aborted turn is retried after compaction (overflow recovery)
});

pi.on("session_compact_failed", async (event, ctx) => {
  // event.reason - "manual" (/compact), "threshold", or "overflow"
  // event.errorMessage - present for non-abort failures
  // event.aborted - true for cancelled/aborted compactions
  // event.willRetry - whether the aborted turn would have retried after compaction
  // event.fromExtension - whether extension-provided compaction content was being used
});
```

#### session_before_tree / session_tree

Được kích hoạt khi điều hướng `/tree`. Xem [Sessions](sessions.md) để tìm hiểu khái niệm điều hướng cây.

```typescript
pi.on("session_before_tree", async (event, ctx) => {
  const { preparation, signal } = event;
  return { cancel: true };
  // OR provide custom summary:
  return {
    summary: {
      summary: "...",
      // usage: summaryResponse.usage, // Optional; included in session totals
      details: {},
    },
  };
});

pi.on("session_tree", async (event, ctx) => {
  // event.newLeafId, oldLeafId, summaryEntry, fromExtension
});
```

#### session_shutdown

Được kích hoạt trước khi runtime của session đã bắt đầu bị dỡ bỏ. Dùng sự kiện này để dọn dẹp tài nguyên đã mở từ `session_start` hoặc hook khác có phạm vi session.

```typescript
pi.on("session_shutdown", async (event, ctx) => {
  // event.reason - "quit" | "reload" | "new" | "resume" | "fork"
  // event.targetSessionFile - destination session for session replacement flows
  // Cleanup, save state, etc.
});
```

### Sự kiện Agent

#### before_agent_start

Được kích hoạt sau khi người dùng gửi prompt, trước vòng lặp agent. Có thể chèn tin nhắn và/hoặc sửa đổi system prompt.

```typescript
pi.on("before_agent_start", async (event, ctx) => {
  // event.prompt - user's prompt text
  // event.images - attached images (if any)
  // event.systemPrompt - current chained system prompt for this handler
  //   (includes changes from earlier before_agent_start handlers)
  // event.systemPromptOptions - structured options used to build the system prompt
  //   .customPrompt - any custom system prompt (from --system-prompt, SYSTEM.md, or custom templates)
  //   .selectedTools - tools currently active in the prompt
  //   .toolSnippets - one-line descriptions for each tool
  //   .promptGuidelines - custom guideline bullets
  //   .appendSystemPrompt - text from --append-system-prompt flags
  //   .cwd - working directory
  //   .contextFiles - AGENTS.md files and other loaded context files
  //   .skills - loaded skills

  return {
    // Inject a persistent message (stored in session, sent to LLM)
    message: {
      customType: "my-extension",
      content: "Additional context for the LLM",
      display: true,
    },
    // Replace the system prompt for this turn (chained across extensions)
    systemPrompt: event.systemPrompt + "\n\nExtra instructions for this turn...",
  };
});
```

Trường `systemPromptOptions` cho extension truy cập cùng dữ liệu có cấu trúc mà Pi dùng để xây dựng system prompt. Điều này cho phép bạn kiểm tra những gì Pi đã tải — custom prompt, guideline, tool snippet, context file, skill — mà không cần phát hiện lại tài nguyên hay phân tích lại cờ. Hãy dùng trường này khi extension cần thực hiện thay đổi sâu và có căn cứ đối với system prompt nhưng vẫn tôn trọng cấu hình do người dùng cung cấp.

Bên trong `before_agent_start`, cả `event.systemPrompt` và `ctx.getSystemPrompt()` đều phản ánh system prompt được nối chuỗi tính đến handler hiện tại. Các handler `before_agent_start` về sau vẫn có thể sửa đổi lại nó.

#### agent_start / agent_end / agent_settled

`agent_start` kích hoạt khi một lần chạy agent cấp thấp bắt đầu. `agent_end` kích hoạt khi lần chạy đó kết thúc, nhưng Pi vẫn có thể tự retry, tự compact rồi retry, hoặc tiếp tục với các tin nhắn follow-up trong hàng đợi. Hãy dùng `agent_settled` cho các tích hợp trạng thái cần biết Pi sẽ không tự động chạy tiếp.

```typescript
pi.on("agent_start", async (_event, ctx) => {});

pi.on("agent_end", async (event, ctx) => {
  // event.messages - messages from this low-level run
});

pi.on("agent_settled", async (_event, ctx) => {
  // ctx.isIdle() is true here unless another extension started a new run.
});
```

#### ui_prompt_start / ui_prompt_end

Các sự kiện vòng đời chỉ để thông báo, dành cho lời nhắc UI extension chặn người dùng. Chúng kích hoạt quanh `ctx.ui.select()`, `ctx.ui.confirm()`, `ctx.ui.input()`, `ctx.ui.editor()`, và `ctx.ui.custom()` để tích hợp host/trạng thái có thể báo "đang chờ người dùng" thay vì chỉ "đang chạy".

Các lời nhắc lồng nhau hoặc chồng chéo được gộp thành một khoảng chờ bên ngoài duy nhất. Handler được gọi theo cơ chế best-effort và không được chờ trước khi hiển thị hoặc đóng lời nhắc.

```typescript
pi.on("ui_prompt_start", async (event, ctx) => {
  // event.reason === "ui_prompt"
  // event.kind: "select" | "confirm" | "input" | "editor" | "custom"
  // event.title: prompt title when available
});

pi.on("ui_prompt_end", async (event, ctx) => {
  // Pi is no longer waiting on that UI prompt span.
});
```

#### turn_start / turn_end

Được kích hoạt cho mỗi lượt (một phản hồi LLM + các tool call).

```typescript
pi.on("turn_start", async (event, ctx) => {
  // event.turnIndex, event.timestamp
});

pi.on("turn_end", async (event, ctx) => {
  // event.turnIndex, event.message, event.toolResults
});
```

#### message_start / message_update / message_end

Được kích hoạt cho các cập nhật vòng đời của tin nhắn.

- `message_start` và `message_end` kích hoạt cho tin nhắn user, assistant, và toolResult.
- `message_update` kích hoạt cho các cập nhật streaming của assistant.
- Handler `message_end` có thể trả về `{ message }` để thay thế tin nhắn đã hoàn tất. Tin nhắn thay thế phải giữ nguyên `role`.

```typescript
pi.on("message_start", async (event, ctx) => {
  // event.message
});

pi.on("message_update", async (event, ctx) => {
  // event.message
  // event.assistantMessageEvent (token-by-token stream event)
});

pi.on("message_end", async (event, ctx) => {
  if (event.message.role !== "assistant") return;

  return {
    message: {
      ...event.message,
      usage: {
        ...event.message.usage,
        cost: {
          ...event.message.usage.cost,
          total: 0.123,
        },
      },
    },
  };
});
```

#### tool_execution_start / tool_execution_update / tool_execution_end

Được kích hoạt cho các cập nhật vòng đời của việc thực thi công cụ.

Trong chế độ công cụ song song:
- `tool_execution_start` được phát theo thứ tự nguồn của assistant trong giai đoạn preflight
- Các sự kiện `tool_execution_update` có thể xen kẽ giữa các công cụ
- `tool_execution_end` được phát theo thứ tự công cụ hoàn thành sau khi mỗi công cụ được hoàn tất
- Các sự kiện tin nhắn `toolResult` cuối cùng vẫn được phát sau đó theo thứ tự nguồn của assistant

```typescript
pi.on("tool_execution_start", async (event, ctx) => {
  // event.toolCallId, event.toolName, event.args
});

pi.on("tool_execution_update", async (event, ctx) => {
  // event.toolCallId, event.toolName, event.args, event.partialResult
});

pi.on("tool_execution_end", async (event, ctx) => {
  // event.toolCallId, event.toolName, event.result, event.isError
});
```

#### context

Được kích hoạt trước mỗi lần gọi LLM. Sửa đổi tin nhắn theo cách không phá hủy. Xem [Session Format](session-format.md) để biết kiểu tin nhắn.

```typescript
pi.on("context", async (event, ctx) => {
  // event.messages - deep copy, safe to modify
  const filtered = event.messages.filter(m => !shouldPrune(m));
  return { messages: filtered };
});
```

#### before_provider_headers

Được kích hoạt sau khi header HTTP gửi đi được lắp ráp. Dùng để thêm, ghi đè, hoặc xóa header của request.

Handler thay đổi `event.headers` ngay tại chỗ. Gán một key thành chuỗi để thêm hoặc ghi đè, hoặc thành `null` để xóa.

```typescript
pi.on("before_provider_headers", (event, ctx) => {
  // Add or override — e.g. a session id for gateway tracing/attribution
  event.headers["x-session-id"] = ctx.sessionManager.getSessionId();

  // Drop a tracking header pi adds for this call
  event.headers["X-OpenRouter-Title"] = null;
});
```

Chạy một lần cho mỗi request đến provider; các lần retry dùng lại cùng header thay vì kích hoạt lại hook.

#### before_provider_request

Được kích hoạt sau khi payload riêng của provider được xây dựng, ngay trước khi request được gửi. Handler chạy theo thứ tự tải extension. Trả về `undefined` giữ nguyên payload. Trả về bất kỳ giá trị nào khác sẽ thay thế payload cho các handler sau và cho request thực tế.

Hook này có thể viết lại system instruction cấp provider hoặc xóa hoàn toàn. Những thay đổi ở cấp payload này không được phản ánh qua `ctx.getSystemPrompt()`, vì hàm này báo chuỗi system prompt của Pi chứ không phải payload cuối cùng được serialize gửi cho provider.

```typescript
pi.on("before_provider_request", (event, ctx) => {
  console.log(JSON.stringify(event.payload, null, 2));

  // Optional: replace payload
  // return { ...event.payload, temperature: 0 };
});
```

Chủ yếu hữu ích để debug quá trình serialize của provider và hành vi cache.

#### after_provider_response

Được kích hoạt sau khi nhận phản hồi HTTP và trước khi consume stream body. Handler chạy theo thứ tự tải extension.

```typescript
pi.on("after_provider_response", (event, ctx) => {
  // event.status - HTTP status code
  // event.headers - normalized response headers
  if (event.status === 429) {
    console.log("rate limited", event.headers["retry-after"]);
  }
});
```

Việc có sẵn header tùy thuộc vào provider và phương thức vận chuyển. Các provider trừu tượng hóa phản hồi HTTP có thể không expose header.

### Sự kiện Model

#### model_select

Được kích hoạt khi model thay đổi qua lệnh `/model`, cycling model (`Ctrl+P`), hoặc khôi phục session.

```typescript
pi.on("model_select", async (event, ctx) => {
  // event.model - newly selected model
  // event.previousModel - previous model (undefined if first selection)
  // event.source - "set" | "cycle" | "restore"

  const prev = event.previousModel
    ? `${event.previousModel.provider}/${event.previousModel.id}`
    : "none";
  const next = `${event.model.provider}/${event.model.id}`;

  ctx.ui.notify(`Model changed (${event.source}): ${prev} -> ${next}`, "info");
});
```

Dùng sự kiện này để cập nhật thành phần UI (thanh trạng thái, footer) hoặc thực hiện khởi tạo riêng cho model khi model đang hoạt động thay đổi.

#### thinking_level_select

Được kích hoạt khi thinking level thay đổi. Đây là sự kiện chỉ thông báo; giá trị trả về của handler bị bỏ qua.

```typescript
pi.on("thinking_level_select", async (event, ctx) => {
  // event.level - newly selected thinking level
  // event.previousLevel - previous thinking level

  ctx.ui.setStatus("thinking", `thinking: ${event.level}`);
});
```

Dùng sự kiện này để cập nhật UI extension khi `pi.setThinkingLevel()`, thay đổi model, hoặc các điều khiển thinking level tích hợp thay đổi level đang hoạt động.

### Sự kiện Tool

#### tool_call

Được kích hoạt sau `tool_execution_start`, trước khi công cụ thực thi. **Có thể chặn.** Dùng `isToolCallEventType` để thu hẹp kiểu và lấy input được gõ kiểu.

Trước khi `tool_call` chạy, pi chờ các sự kiện Agent đã phát trước đó kết thúc việc chảy qua `AgentSession`. Điều này có nghĩa là `ctx.sessionManager` đã được cập nhật đến tin nhắn assistant gọi công cụ hiện tại.

Trong chế độ thực thi công cụ song song mặc định, các tool call cùng cấp từ cùng một tin nhắn assistant được preflight tuần tự, rồi thực thi đồng thời. `tool_call` không đảm bảo thấy kết quả của tool call cùng cấp từ cùng tin nhắn assistant đó trong `ctx.sessionManager`.

`event.input` có thể thay đổi. Hãy chỉnh sửa trực tiếp tại chỗ để vá đối số công cụ trước khi thực thi.

Các đảm bảo hành vi:
- Thay đổi với `event.input` ảnh hưởng đến việc thực thi công cụ thực tế
- Handler `tool_call` về sau thấy các thay đổi do handler trước thực hiện
- Không có bước xác thực lại sau khi bạn thay đổi
- Giá trị trả về từ `tool_call` kiểm soát việc chặn qua `{ block: true, reason?: string, terminate?: boolean }`
- `terminate` chỉ áp dụng cho một lời gọi bị chặn; agent chỉ dừng sớm khi mọi kết quả đã hoàn tất trong batch đều là terminating

```typescript
import { isToolCallEventType } from "@earendil-works/pi-coding-agent";

pi.on("tool_call", async (event, ctx) => {
  // event.toolName - "bash", "read", "write", "edit", etc.
  // event.toolCallId
  // event.input - tool parameters (mutable)

  // Built-in tools: no type params needed
  if (isToolCallEventType("bash", event)) {
    // event.input is { command: string; timeout?: number }
    event.input.command = `source ~/.profile\n${event.input.command}`;

    if (event.input.command.includes("rm -rf")) {
      return { block: true, reason: "Dangerous command", terminate: true };
    }
  }

  if (isToolCallEventType("read", event)) {
    // event.input is { path: string; offset?: number; limit?: number }
    console.log(`Reading: ${event.input.path}`);
  }
});
```

#### Gõ kiểu input của công cụ tùy chỉnh

Công cụ tùy chỉnh nên export kiểu input của chúng:

```typescript
// my-extension.ts
export type MyToolInput = Static<typeof myToolSchema>;
```

Dùng `isToolCallEventType` với tham số kiểu rõ ràng:

```typescript
import { isToolCallEventType } from "@earendil-works/pi-coding-agent";
import type { MyToolInput } from "my-extension";

pi.on("tool_call", (event) => {
  if (isToolCallEventType<"my_tool", MyToolInput>("my_tool", event)) {
    event.input.action;  // typed
  }
});
```

#### tool_result

Được kích hoạt sau khi thực thi công cụ kết thúc và trước khi `tool_execution_end` cùng các sự kiện tin nhắn kết quả công cụ cuối cùng được phát. **Có thể sửa đổi kết quả.**

Trong chế độ công cụ song song, `tool_result` và `tool_execution_end` có thể xen kẽ theo thứ tự hoàn thành của công cụ, trong khi các sự kiện tin nhắn `toolResult` cuối cùng vẫn được phát sau đó theo thứ tự nguồn của assistant.

Handler `tool_result` nối chuỗi như middleware:
- Handler chạy theo thứ tự tải extension
- Mỗi handler thấy kết quả mới nhất sau các thay đổi của handler trước
- Handler có thể trả về bản vá một phần (`content`, `details`, `isError`, hoặc `usage`); các trường bỏ qua giữ nguyên giá trị hiện tại

Dùng `ctx.signal` cho công việc bất đồng bộ lồng nhau bên trong handler. Điều này cho phép Esc hủy các lời gọi model, `fetch()`, và các hoạt động khác có hỗ trợ abort do extension khởi động.

```typescript
import { isBashToolResult } from "@earendil-works/pi-coding-agent";

pi.on("tool_result", async (event, ctx) => {
  // event.toolName, event.toolCallId, event.input
  // event.content, event.details, event.isError, event.usage

  if (isBashToolResult(event)) {
    // event.details is typed as BashToolDetails
  }

  const response = await fetch("https://example.com/summarize", {
    method: "POST",
    body: JSON.stringify({ content: event.content }),
    signal: ctx.signal,
  });

  // Modify result:
  return { content: [...], details: {...}, isError: false, usage: nestedModelUsage };
});
```

### Sự kiện Bash của người dùng

#### user_bash

Được kích hoạt khi người dùng chạy lệnh `!` hoặc `!!`. **Có thể chặn.**

```typescript
import { createLocalBashOperations } from "@earendil-works/pi-coding-agent";

pi.on("user_bash", (event, ctx) => {
  // event.command - the bash command
  // event.excludeFromContext - true if !! prefix
  // event.cwd - working directory

  // Option 1: Provide custom operations (e.g., SSH)
  return { operations: remoteBashOps };

  // Option 2: Wrap pi's built-in local bash backend
  const local = createLocalBashOperations();
  return {
    operations: {
      exec(command, cwd, options) {
        return local.exec(`source ~/.profile\n${command}`, cwd, options);
      }
    }
  };

  // Option 3: Full replacement - return result directly
  return { result: { output: "...", exitCode: 0, cancelled: false, truncated: false } };
});
```

### Sự kiện Input

#### input

Được kích hoạt khi nhận input từ người dùng, sau khi kiểm tra lệnh extension nhưng trước khi mở rộng skill và template. Sự kiện thấy văn bản input thô, nên `/skill:foo` và `/template` chưa được mở rộng.

**Thứ tự xử lý:**
1. Lệnh extension (`/cmd`) được kiểm tra trước — nếu tìm thấy, handler chạy và sự kiện input bị bỏ qua
2. Sự kiện `input` kích hoạt — có thể chặn, chuyển đổi, hoặc xử lý
3. Nếu chưa được xử lý: lệnh skill (`/skill:name`) được mở rộng thành nội dung skill
4. Nếu chưa được xử lý: prompt template (`/template`) được mở rộng thành nội dung template
5. Bắt đầu xử lý agent (`before_agent_start`, v.v.)

```typescript
pi.on("input", async (event, ctx) => {
  // event.text - raw input (before skill/template expansion)
  // event.images - attached images, if any
  // event.source - "interactive" (typed), "rpc" (API), or "extension" (via sendUserMessage)
  // event.streamingBehavior - "steer" | "followUp" | undefined
  //   undefined when idle, "steer" for mid-stream interrupts,
  //   "followUp" for messages queued until the agent finishes

  // Transform: rewrite input before expansion
  if (event.text.startsWith("?quick "))
    return { action: "transform", text: `Respond briefly: ${event.text.slice(7)}` };

  // Handle: respond without LLM (extension shows its own feedback)
  if (event.text === "ping") {
    ctx.ui.notify("pong", "info");
    return { action: "handled" };
  }

  // Route by source: skip processing for extension-injected messages
  if (event.source === "extension") return { action: "continue" };

  // Intercept skill commands before expansion
  if (event.text.startsWith("/skill:")) {
    // Could transform, block, or let pass through
  }

  return { action: "continue" };  // Default: pass through to expansion
});
```

**Kết quả:**
- `continue` — chuyển qua không thay đổi (mặc định nếu handler không trả về gì)
- `transform` — sửa văn bản/hình ảnh, rồi tiếp tục đến bước mở rộng
- `handled` — bỏ qua hoàn toàn agent (handler đầu tiên trả về giá trị này sẽ thắng)

Các phép biến đổi nối chuỗi qua các handler. Xem [input-transform.ts](../examples/extensions/input-transform.ts) và [input-transform-streaming.ts](../examples/extensions/input-transform-streaming.ts) để biết định tuyến theo `streamingBehavior`.

## ExtensionContext

Mọi handler đều nhận `ctx: ExtensionContext`.

### ctx.ui

Các phương thức UI để tương tác với người dùng. Xem [Custom UI](#custom-ui) để biết đầy đủ chi tiết.

### ctx.mode

Chế độ chạy hiện tại: `"tui"`, `"rpc"`, `"json"`, hoặc `"print"`. Dùng `ctx.mode === "tui"` để bảo vệ các tính năng chỉ dành cho terminal như `custom()`, component factory, input terminal, và render TUI trực tiếp.

### ctx.hasUI

`true` trong chế độ TUI và RPC. `false` trong chế độ print (`-p`) và JSON. Dùng giá trị này để bảo vệ các phương thức hộp thoại (`select`, `confirm`, `input`, `editor`) và các phương thức fire-and-forget (`notify`, `setStatus`, `setWidget`, `setTitle`, `setEditorText`) vốn hoạt động ở cả chế độ TUI và RPC. Trong chế độ RPC, một số phương thức đặc thù TUI là no-op hoặc trả về giá trị mặc định (xem [rpc.md](rpc.md#extension-ui-protocol)).

### ctx.cwd

Thư mục làm việc hiện tại.

Dùng `CONFIG_DIR_NAME` thay vì hardcode `.pi` khi xây dựng đường dẫn cấu hình cục bộ theo dự án. Các bản phân phối đổi thương hiệu có thể dùng tên thư mục cấu hình khác.

```typescript
import { CONFIG_DIR_NAME, type ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { join } from "node:path";

export default function (pi: ExtensionAPI) {
  pi.on("session_start", (_event, ctx) => {
    const projectConfigPath = join(ctx.cwd, CONFIG_DIR_NAME, "my-extension.json");
    // ...
  });
}
```

### ctx.isProjectTrusted()

Trả về việc quyền tin cậy cục bộ theo dự án có đang hoạt động cho ngữ cảnh session hiện tại hay không. Bao gồm cả quyết định tin cậy tạm thời và ghi đè tin cậy từ CLI, không chỉ các quyết định đã lưu trong kho tin cậy toàn cục.

Dùng hàm này trước khi đọc cấu hình extension cục bộ theo dự án mà chỉ nên được tuân thủ cho các dự án đáng tin cậy.

### ctx.sessionManager

Quyền truy cập chỉ đọc vào trạng thái session. Xem [Session Format](session-format.md) để biết API SessionManager đầy đủ và các kiểu entry.

Với `tool_call`, trạng thái này được đồng bộ đến tin nhắn assistant hiện tại trước khi handler chạy. Trong chế độ thực thi công cụ song song, nó vẫn không đảm bảo chứa kết quả của tool call cùng cấp từ cùng tin nhắn assistant.

```typescript
ctx.sessionManager.getEntries()             // All entries
ctx.sessionManager.getBranch()              // Current branch
ctx.sessionManager.buildContextEntries()    // Active branch entries with compaction applied
ctx.sessionManager.getLeafId()              // Current leaf entry ID
```

### ctx.modelRegistry / ctx.model / ctx.thinkingLevel / ctx.scopedModels

Truy cập model, provider, và thông tin xác thực đã được giải quyết. `ctx.modelRegistry.getProvider(id)` trả về provider pi-ai hiệu dụng, trong khi `getProviderAuth(id)` giải quyết API key, header, base URL, và môi trường theo phạm vi provider hiện tại mà không cần model đã tải. `ctx.model` là model đang hoạt động, và `ctx.thinkingLevel` là thinking level hiệu dụng hiện tại của nó.

`ctx.scopedModels` là danh sách model chỉ đọc được giới hạn trong session hiện tại — cùng tập với những gì lệnh `/scoped-models` hiển thị. Tập này được giải quyết lúc bắt đầu session từ cờ CLI `--models` và thiết lập `enabledModels` (đối chiếu với catalogue có sẵn bằng minimatch trên `provider/modelId` hoặc `modelId` thuần). Danh sách rỗng khi không cấu hình giới hạn, nghĩa là mọi model có sẵn đều dùng được. Mỗi mục có dạng `{ model, thinkingLevel? }`, trong đó `thinkingLevel` chỉ được đặt khi một pattern gắn cố định nó (ví dụ `anthropic/*:high`). Dùng danh sách này để điền bộ chọn model phản ánh bộ chọn tích hợp, thay vì liệt kê toàn bộ catalogue qua `ctx.modelRegistry.getAvailable()`.

#### Gọi model dạng streaming

Dùng `ctx.modelRegistry.streamSimple(model, context, options)` cho các tùy chọn trung lập provider như `reasoning`, hoặc `stream()` cho tùy chọn đặc thù API. Cả hai dùng provider đã cấu hình và giải quyết xác thực, kể cả provider đăng ký qua `pi.registerProvider()`. Hãy dùng các hàm này thay vì hàm streaming trong `pi-ai/compat`, vì các hàm đó không thấy đăng ký provider của extension.

Cả hai trả về `AssistantMessageEventStream`. Duyệt qua nó để nhận sự kiện phản hồi và await `.result()` để lấy tin nhắn cuối cùng. Lỗi thiết lập tạo ra sự kiện lỗi và kết quả lỗi.

### ctx.signal

Tín hiệu abort của agent hiện tại, hoặc `undefined` khi không có lượt agent nào đang hoạt động.

Dùng giá trị này cho công việc lồng nhau có hỗ trợ abort do handler extension khởi động, ví dụ:
- `fetch(..., { signal: ctx.signal })`
- lời gọi model chấp nhận `signal`
- hàm tiện ích tệp hoặc process chấp nhận `AbortSignal`

`ctx.signal` thường được định nghĩa trong các sự kiện lượt đang hoạt động như `tool_call`, `tool_result`, `message_update`, và `turn_end`.
Nó thường là `undefined` trong ngữ cảnh nhàn rỗi hoặc ngoài lượt như sự kiện session, lệnh extension, và shortcut kích hoạt khi pi đang nhàn rỗi.

```typescript
pi.on("tool_result", async (event, ctx) => {
  const response = await fetch("https://example.com/api", {
    method: "POST",
    body: JSON.stringify(event),
    signal: ctx.signal,
  });

  const data = await response.json();
  return { details: data };
});
```

### ctx.isIdle() / ctx.abort() / ctx.hasPendingMessages()

Các hàm tiện ích kiểm soát luồng. `ctx.isIdle()` trả về false khi Pi đang xử lý một lần chạy agent, retry tự động, retry sau auto-compaction, hoặc phần tiếp theo trong hàng đợi.

### ctx.shutdown()

Yêu cầu shutdown êm ái của pi.

- **Chế độ tương tác:** Trì hoãn đến khi agent nhàn rỗi (sau khi xử lý mọi tin nhắn steering và follow-up trong hàng đợi).
- **Chế độ RPC:** Trì hoãn đến trạng thái nhàn rỗi tiếp theo (sau khi hoàn tất phản hồi lệnh hiện tại, lúc chờ lệnh tiếp theo).
- **Chế độ print:** Không làm gì. Tiến trình tự thoát khi mọi prompt được xử lý xong.

Phát sự kiện `session_shutdown` tới mọi extension trước khi thoát. Có sẵn trong mọi ngữ cảnh (handler sự kiện, công cụ, lệnh, shortcut).

```typescript
pi.on("tool_call", (event, ctx) => {
  if (isFatal(event.input)) {
    ctx.shutdown();
  }
});
```

### ctx.getContextUsage()

Trả về mức sử dụng context hiện tại cho model đang hoạt động. Dùng usage của assistant gần nhất khi có, sau đó ước lượng token cho các tin nhắn ở cuối.

```typescript
const usage = ctx.getContextUsage();
if (usage && usage.tokens > 100_000) {
  // ...
}
```

### ctx.compact()

Kích hoạt compaction mà không chờ hoàn tất. Dùng `onComplete` và `onError` cho các hành động tiếp theo.

```typescript
ctx.compact({
  customInstructions: "Focus on recent changes",
  onComplete: (result) => {
    ctx.ui.notify("Compaction completed", "info");
  },
  onError: (error) => {
    ctx.ui.notify(`Compaction failed: ${error.message}`, "error");
  },
});
```

### ctx.getSystemPrompt()

Trả về chuỗi system prompt hiện tại của Pi.

- Trong `before_agent_start`, hàm phản ánh các thay đổi system prompt được nối chuỗi tính đến hiện tại cho lượt này.
- Không bao gồm các thay đổi tin nhắn của sự kiện `context` về sau.
- Không bao gồm các lần viết lại payload của `before_provider_request`.
- Nếu extension được tải sau chạy sau bạn, chúng vẫn có thể thay đổi nội dung cuối cùng được gửi đi.

```typescript
pi.on("before_agent_start", (event, ctx) => {
  const prompt = ctx.getSystemPrompt();
  console.log(`System prompt length: ${prompt.length}`);
});
```

## ExtensionCommandContext

Handler lệnh nhận `ExtensionCommandContext`, mở rộng `ExtensionContext` với các phương thức điều khiển session. Các phương thức này chỉ có trong lệnh vì chúng có thể gây deadlock nếu gọi từ handler sự kiện.

### ctx.getSystemPromptOptions()

Trả về các đầu vào cơ bản Pi đang dùng để xây dựng system prompt.

```typescript
const options = ctx.getSystemPromptOptions();
const contextPaths = options.contextFiles?.map((file) => file.path) ?? [];
```

Hàm có cùng cấu trúc và khả năng thay đổi như `event.systemPromptOptions` của `before_agent_start`: custom prompt, công cụ đang hoạt động, tool snippet, prompt guideline, văn bản system prompt được nối thêm, cwd, context file đã tải, và skill đã tải. Kết quả có thể chứa toàn bộ nội dung context file, nên hãy coi đây là dữ liệu nhạy cảm nội bộ extension và tránh expose qua danh sách lệnh, log, hoặc metadata autocomplete.

Hàm báo các đầu vào cơ bản hiện tại. Không bao gồm thay đổi system prompt được nối chuỗi theo từng lượt của `before_agent_start`, thay đổi tin nhắn của sự kiện `context` về sau, hay các lần viết lại payload của `before_provider_request`.

### ctx.waitForIdle()

Chờ agent ổn định hoàn toàn, bao gồm retry tự động, retry sau auto-compaction, và các phần tiếp theo trong hàng đợi:

```typescript
pi.registerCommand("my-cmd", {
  handler: async (args, ctx) => {
    await ctx.waitForIdle();
    // Agent is now idle, safe to modify session
  },
});
```

### ctx.newSession(options?)

Tạo session mới:

```typescript
const parentSession = ctx.sessionManager.getSessionFile();
const kickoff = "Continue in the replacement session";

const result = await ctx.newSession({
  parentSession,
  setup: async (sm) => {
    sm.appendMessage({
      role: "user",
      content: [{ type: "text", text: "Context from previous session..." }],
      timestamp: Date.now(),
    });
  },
  withSession: async (ctx) => {
    // Use only the replacement-session ctx here.
    await ctx.sendUserMessage(kickoff);
  },
});

if (result.cancelled) {
  // An extension cancelled the new session
}
```

Tùy chọn:
- `parentSession`: tệp session cha để ghi vào header của session mới
- `setup`: thay đổi `SessionManager` của session mới trước khi `withSession` chạy
- `withSession`: chạy công việc sau chuyển đổi với một ngữ cảnh session thay thế hoàn toàn mới. Đừng dùng `pi` / `ctx` của lệnh cũ đã capture; xem [Vòng đời thay thế session và các bẫy thường gặp](#session-replacement-lifecycle-and-footguns).

### ctx.fork(entryId, options?)

Fork từ một entry cụ thể, tạo tệp session mới:

```typescript
const result = await ctx.fork("entry-id-123", {
  withSession: async (ctx) => {
    // Use only the replacement-session ctx here.
    ctx.ui.notify("Now in the forked session", "info");
  },
});
if (result.cancelled) {
  // An extension cancelled the fork
}

const cloneResult = await ctx.fork("entry-id-456", { position: "at" });
if (cloneResult.cancelled) {
  // An extension cancelled the clone
}
```

Tùy chọn:
- `position`: `"before"` (mặc định) fork trước tin nhắn user đã chọn, khôi phục prompt đó vào editor
- `position`: `"at"` nhân đôi đường dẫn đang hoạt động đến entry đã chọn mà không khôi phục văn bản editor
- `withSession`: chạy công việc sau chuyển đổi với một ngữ cảnh session thay thế hoàn toàn mới. Đừng dùng `pi` / `ctx` của lệnh cũ đã capture; xem [Vòng đời thay thế session và các bẫy thường gặp](#session-replacement-lifecycle-and-footguns).

### ctx.navigateTree(targetId, options?)

Điều hướng đến một điểm khác trong cây session. Từ chối khi có phản hồi agent, compaction thủ công hoặc tự động, hoặc một lần điều hướng cây khác đang hoạt động, ngay cả với `summarize: false`. Các xung đột này giữ nguyên nhánh đang hoạt động và từ chối promise thay vì trả về `{ cancelled: true }`. Hãy chờ thao tác đang hoạt động kết thúc (ví dụ dùng `await ctx.waitForIdle()` trong handler lệnh) rồi thử lại:

```typescript
const result = await ctx.navigateTree("entry-id-456", {
  summarize: true,
  customInstructions: "Focus on error handling changes",
  replaceInstructions: false, // true = replace default prompt entirely
  label: "review-checkpoint",
});
```

Tùy chọn:
- `summarize`: Có tạo tóm tắt cho nhánh bị bỏ hay không
- `customInstructions`: Hướng dẫn tùy chỉnh cho trình tóm tắt
- `replaceInstructions`: Nếu true, `customInstructions` thay thế prompt mặc định thay vì được nối thêm
- `label`: Nhãn gắn vào entry tóm tắt nhánh (hoặc entry đích nếu không tóm tắt)

### ctx.switchSession(sessionPath, options?)

Chuyển sang một tệp session khác:

```typescript
const result = await ctx.switchSession("/path/to/session.jsonl", {
  withSession: async (ctx) => {
    await ctx.sendUserMessage("Resume work in the replacement session");
  },
});
if (result.cancelled) {
  // An extension cancelled the switch via session_before_switch
}
```

Tùy chọn:
- `withSession`: chạy công việc sau chuyển đổi với một ngữ cảnh session thay thế hoàn toàn mới. Đừng dùng `pi` / `ctx` của lệnh cũ đã capture; xem [Vòng đời thay thế session và các bẫy thường gặp](#session-replacement-lifecycle-and-footguns).

Để khám phá session có sẵn, dùng phương thức tĩnh `SessionManager.list()` hoặc `SessionManager.listAll()`:

```typescript
import { SessionManager } from "@earendil-works/pi-coding-agent";

pi.registerCommand("switch", {
  description: "Switch to another session",
  handler: async (args, ctx) => {
    const sessions = await SessionManager.list(ctx.cwd);
    if (sessions.length === 0) return;
    const choice = await ctx.ui.select(
      "Pick session:",
      sessions.map(s => s.file),
    );
    if (choice) {
      await ctx.switchSession(choice, {
        withSession: async (ctx) => {
          ctx.ui.notify("Switched session", "info");
        },
      });
    }
  },
});
```

### Vòng đời thay thế session và các bẫy thường gặp

`withSession` nhận một `ReplacedSessionContext` mới, mở rộng `ExtensionCommandContext` với các hàm bất đồng bộ `sendMessage()` và `sendUserMessage()` gắn với session thay thế.

Vòng đời và các bẫy:
- `withSession` chỉ chạy sau khi session cũ đã phát `session_shutdown`, runtime cũ đã bị dỡ bỏ, session thay thế đã được gắn lại, và instance extension mới đã nhận `session_start`.
- Callback vẫn thực thi trong closure gốc, không nằm trong instance extension mới. Nghĩa là instance extension cũ của bạn có thể đã chạy dọn dẹp shutdown trước khi `withSession` bắt đầu.
- Các đối tượng `pi` / `ctx` của lệnh cũ đã capture và gắn với session trở nên cũ sau thay thế và sẽ ném lỗi nếu dùng. Chỉ dùng `ctx` truyền cho `withSession` cho công việc gắn với session.
- Các đối tượng thô đã trích xuất trước đó vẫn là trách nhiệm của bạn. Ví dụ, nếu bạn capture `const sm = ctx.sessionManager` trước khi thay thế, `sm` vẫn là đối tượng `SessionManager` cũ. Đừng dùng lại sau thay thế.
- Code trong `withSession` nên giả định mọi trạng thái bị hủy bởi handler `session_shutdown` của bạn đã biến mất. Chỉ capture dữ liệu thuần sống sót qua shutdown một cách sạch sẽ, như chuỗi, id, và cấu hình đã serialize.

Mẫu an toàn:

```typescript
pi.registerCommand("handoff", {
  handler: async (_args, ctx) => {
    const kickoff = "Continue from the replacement session";
    await ctx.newSession({
      withSession: async (ctx) => {
        await ctx.sendUserMessage(kickoff);
      },
    });
  },
});
```

Mẫu không an toàn:

```typescript
pi.registerCommand("handoff", {
  handler: async (_args, ctx) => {
    const oldSessionManager = ctx.sessionManager;
    await ctx.newSession({
      withSession: async (_ctx) => {
        // stale old objects: do not do this
        oldSessionManager.getSessionFile();
        pi.sendUserMessage("wrong");
      },
    });
  },
});
```

### ctx.reload()

Chạy cùng luồng reload như `/reload`.

```typescript
pi.registerCommand("reload-runtime", {
  description: "Reload extensions, skills, prompts, themes, and context files",
  handler: async (_args, ctx) => {
    await ctx.reload();
    return;
  },
});
```

Hành vi quan trọng:
- `await ctx.reload()` phát `session_shutdown` cho runtime extension hiện tại
- Sau đó tải lại tài nguyên và phát `session_start` với `reason: "reload"` cùng `resources_discover` với reason `"reload"`
- Handler lệnh đang chạy vẫn tiếp tục trong khung gọi cũ
- Code sau `await ctx.reload()` vẫn chạy từ phiên bản trước reload
- Code sau `await ctx.reload()` không được giả định trạng thái extension trong bộ nhớ cũ vẫn còn hợp lệ
- Sau khi handler trả về, lệnh/sự kiện/tool call trong tương lai dùng phiên bản extension mới

Để hành vi đoán trước được, hãy coi reload là điểm kết thúc của handler đó (`await ctx.reload(); return;`).

Công cụ chạy với `ExtensionContext`, nên không gọi trực tiếp `ctx.reload()`. Hãy dùng một lệnh làm điểm vào reload, rồi expose một công cụ xếp lệnh đó vào hàng đợi như tin nhắn follow-up của người dùng.

Ví dụ công cụ LLM có thể gọi để kích hoạt reload:

```typescript
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";

export default function (pi: ExtensionAPI) {
  pi.registerCommand("reload-runtime", {
    description: "Reload extensions, skills, prompts, themes, and context files",
    handler: async (_args, ctx) => {
      await ctx.reload();
      return;
    },
  });

  pi.registerTool({
    name: "reload_runtime",
    label: "Reload Runtime",
    description: "Reload extensions, skills, prompts, themes, and context files",
    parameters: Type.Object({}),
    async execute() {
      pi.sendUserMessage("/reload-runtime", { deliverAs: "followUp" });
      return {
        content: [{ type: "text", text: "Queued /reload-runtime as a follow-up command." }],
      };
    },
  });
}
```

## Phương thức ExtensionAPI

### pi.on(event, handler)

Đăng ký sự kiện. Xem [Sự kiện](#events) để biết kiểu sự kiện và giá trị trả về.

### pi.registerTool(definition)

Đăng ký công cụ tùy chỉnh để LLM gọi. Xem [Công cụ tùy chỉnh](#custom-tools) để biết đầy đủ chi tiết.

`pi.registerTool()` hoạt động cả trong lúc tải extension lẫn sau khi khởi động. Bạn có thể gọi trong `session_start`, handler lệnh, hoặc handler sự kiện khác. Công cụ mới được làm mới ngay trong cùng session, nên xuất hiện trong `pi.getAllTools()` và LLM có thể gọi mà không cần `/reload`.

Dùng `pi.setActiveTools()` để bật hoặc tắt công cụ (kể cả công cụ thêm động) lúc runtime.

Dùng `promptSnippet` để cho công cụ tùy chỉnh tham gia một mục một dòng trong `Available tools`, và `promptGuidelines` để nối các bullet riêng của công cụ vào mục `Guidelines` mặc định khi công cụ đang hoạt động.

**Quan trọng:** Các bullet `promptGuidelines` được nối phẳng vào mục `Guidelines` mà không có tiền tố tên công cụ. Mỗi guideline phải nêu rõ công cụ mà nó đề cập — tránh "Use this tool when..." vì LLM không biết "this" là công cụ nào. Hãy viết "Use my_tool when..." thay thế.

Xem [dynamic-tools.ts](../examples/extensions/dynamic-tools.ts) để có ví dụ đầy đủ.

```typescript
import { Type } from "typebox";
import { StringEnum } from "@earendil-works/pi-ai";

pi.registerTool({
  name: "my_tool",
  label: "My Tool",
  description: "What this tool does",
  promptSnippet: "Summarize or transform text according to action",
  promptGuidelines: ["Use my_tool when the user asks to summarize previously generated text."],
  parameters: Type.Object({
    action: StringEnum(["list", "add"] as const),
    text: Type.Optional(Type.String()),
  }),
  prepareArguments(args) {
    // Optional compatibility shim. Runs before schema validation.
    // Return the current schema shape, for example to fold legacy fields
    // into the modern parameter object.
    return args;
  },

  async execute(toolCallId, params, signal, onUpdate, ctx) {
    // Stream progress
    onUpdate?.({ content: [{ type: "text", text: "Working..." }] });

    return {
      content: [{ type: "text", text: "Done" }],
      details: { result: "..." },
    };
  },

  // Optional: Custom rendering
  renderCall(args, theme, context) { ... },
  renderResult(result, options, theme, context) { ... },
});
```

### pi.sendMessage(message, options?)

Chèn một tin nhắn tùy chỉnh vào session. Tin nhắn tùy chỉnh tham gia vào context của LLM. Với nội dung chỉ dành cho TUI bền vững và không nên gửi cho LLM, hãy dùng [`pi.appendEntry()`](#piappendentrycustomtype-data) kết hợp [`pi.registerEntryRenderer()`](#piregisterentryrenderercustomtype-renderer).

```typescript
pi.sendMessage({
  customType: "my-extension",
  content: "Message text",
  display: true,
  details: { ... },
}, {
  triggerTurn: true,
  deliverAs: "steer",
});
```

**Tùy chọn:**
- `deliverAs` — Chế độ gửi:
  - `"steer"` (mặc định) — Xếp tin nhắn vào hàng đợi trong lúc streaming. Được gửi sau khi lượt assistant hiện tại kết thúc thực thi tool call, trước lần gọi LLM tiếp theo.
  - `"followUp"` — Chờ agent kết thúc. Chỉ gửi khi agent không còn tool call nào.
  - `"nextTurn"` — Xếp cho prompt người dùng tiếp theo. Không làm gián đoạn hay kích hoạt gì.
- `triggerTurn: true` — Nếu agent đang nhàn rỗi, kích hoạt phản hồi LLM ngay lập tức. Chỉ áp dụng cho chế độ `"steer"` và `"followUp"` (bị bỏ qua với `"nextTurn"`).

### pi.sendUserMessage(content, options?)

Gửi tin nhắn người dùng tới agent. Khác với `sendMessage()` gửi tin nhắn tùy chỉnh, hàm này gửi một tin nhắn người dùng thật sự, xuất hiện như thể do người dùng gõ. Luôn kích hoạt một lượt.

```typescript
// Simple text message
pi.sendUserMessage("What is 2+2?");

// With content array (text + images)
pi.sendUserMessage([
  { type: "text", text: "Describe this image:" },
  { type: "image", source: { type: "base64", mediaType: "image/png", data: "..." } },
]);

// During streaming - must specify delivery mode
pi.sendUserMessage("Focus on error handling", { deliverAs: "steer" });
pi.sendUserMessage("And then summarize", { deliverAs: "followUp" });

// Opt in to extension command dispatch and skill/prompt template expansion
pi.sendUserMessage("/review src/index.ts", { expandPromptTemplates: true });
```

**Tùy chọn:**
- `deliverAs` — Bắt buộc khi agent đang streaming:
  - `"steer"` — Xếp tin nhắn vào hàng đợi để gửi sau khi lượt assistant hiện tại kết thúc thực thi tool call
  - `"followUp"` — Chờ agent kết thúc mọi công cụ
- `expandPromptTemplates` — Điều phối lệnh extension và mở rộng lệnh skill cùng prompt template. Mặc định `false`.

Khi không streaming, tin nhắn được gửi ngay và kích hoạt một lượt mới. Khi streaming mà không có `deliverAs`, hàm ném lỗi.

Xem [send-user-message.ts](../examples/extensions/send-user-message.ts) để có ví dụ hoàn chỉnh.

### pi.appendEntry(customType, data?)

Lưu dữ liệu extension bền vững. Entry tùy chỉnh KHÔNG tham gia vào context của LLM. Trong chế độ tương tác, chúng cũng có thể render bên trong transcript trò chuyện khi kết hợp với `pi.registerEntryRenderer()`.

```typescript
pi.appendEntry("my-state", { count: 42 });
pi.appendEntry("status-card", { title: "Indexed files", count: 17 });

// Restore on reload
pi.on("session_start", async (_event, ctx) => {
  for (const entry of ctx.sessionManager.getEntries()) {
    if (entry.type === "custom" && entry.customType === "my-state") {
      // Reconstruct from entry.data
    }
  }
});
```

### pi.setSessionName(name)

Đặt tên hiển thị của session (xuất hiện trong bộ chọn session thay cho tin nhắn đầu tiên).

```typescript
pi.setSessionName("Refactor auth module");
```

### pi.getSessionName()

Lấy tên session hiện tại, nếu đã đặt.

```typescript
const name = pi.getSessionName();
if (name) {
  console.log(`Session: ${name}`);
}
```

### pi.setLabel(entryId, label)

Đặt hoặc xóa nhãn trên một entry. Nhãn là điểm đánh dấu do người dùng định nghĩa để đánh dấu và điều hướng (hiện trong bộ chọn `/tree`).

```typescript
// Set a label
pi.setLabel(entryId, "checkpoint-before-refactor");

// Clear a label
pi.setLabel(entryId, undefined);

// Read labels via sessionManager
const label = ctx.sessionManager.getLabel(entryId);
```

Nhãn được lưu bền trong session và sống qua các lần khởi động lại. Dùng chúng để đánh dấu các điểm quan trọng (lượt, checkpoint) trong cây hội thoại.

### pi.registerCommand(name, options)

Đăng ký một lệnh.

Nếu nhiều extension đăng ký cùng tên lệnh, pi giữ tất cả và gán hậu tố gọi số theo thứ tự tải, ví dụ `/review:1` và `/review:2`.

```typescript
pi.registerCommand("stats", {
  description: "Show session statistics",
  handler: async (args, ctx) => {
    const count = ctx.sessionManager.getEntries().length;
    ctx.ui.notify(`${count} entries`, "info");
  }
});
```

Tùy chọn: thêm tự động hoàn thành đối số cho `/command ...`:

```typescript
import type { AutocompleteItem } from "@earendil-works/pi-tui";

pi.registerCommand("deploy", {
  description: "Deploy to an environment",
  getArgumentCompletions: (prefix: string): AutocompleteItem[] | null => {
    const envs = ["dev", "staging", "prod"];
    const items = envs.map((e) => ({ value: e, label: e }));
    const filtered = items.filter((i) => i.value.startsWith(prefix));
    return filtered.length > 0 ? filtered : null;
  },
  handler: async (args, ctx) => {
    ctx.ui.notify(`Deploying: ${args}`, "info");
  },
});
```

### pi.getCommands()

Lấy các lệnh gạch chéo có sẵn để gọi qua `prompt` trong session hiện tại. Bao gồm lệnh extension, prompt template, và lệnh skill.
Danh sách khớp với thứ tự `get_commands` của RPC: extension trước, rồi template, rồi skill.

```typescript
const commands = pi.getCommands();
const bySource = commands.filter((command) => command.source === "extension");
const userScoped = commands.filter((command) => command.sourceInfo.scope === "user");
```

Mỗi mục có cấu trúc:

```typescript
{
  name: string; // Invokable command name without the leading slash. May be suffixed like "review:1"
  description?: string;
  source: "extension" | "prompt" | "skill";
  sourceInfo: {
    path: string;
    source: string;
    scope: "user" | "project" | "temporary";
    origin: "package" | "top-level";
    baseDir?: string;
  };
}
```

Dùng `sourceInfo` làm trường nguồn gốc chuẩn. Đừng suy ra quyền sở hữu từ tên lệnh hay từ việc phân tích đường dẫn tùy tiện.

Các lệnh tương tác tích hợp (như `/model` và `/settings`) không nằm ở đây. Chúng chỉ được xử lý ở chế độ tương tác
và sẽ không thực thi nếu gửi qua `prompt`.

### pi.registerMessageRenderer(customType, renderer)

Đăng ký renderer TUI tùy chỉnh cho tin nhắn có `customType` của bạn. Tin nhắn tùy chỉnh được tạo bằng `pi.sendMessage()` và tham gia context LLM. Xem [Custom UI](#custom-ui).

### pi.registerMarkdownTransformer(transformer)

Đăng ký bộ chuyển đổi cho Markdown trong văn bản user thường, văn bản assistant, và các block thinking. Bộ chuyển đổi chạy theo thứ tự tải extension, và mỗi bộ nhận Markdown do bộ trước trả về. Sau khi chuỗi kết thúc, Pi render nội dung đã chuyển đổi bằng renderer tích hợp.

Bộ chuyển đổi nhận chuỗi Markdown và một ngữ cảnh với:

- `messageType` — `"user"`, `"assistant"`, hoặc `"assistant-thinking"`
- `isStreaming` — `true` cho cập nhật assistant một phần; `false` cho user, assistant đã hoàn tất, và tin nhắn được khôi phục
- `availableWidth` — số cột terminal chính xác có sẵn cho nội dung Markdown đã chuyển đổi

Trả về Markdown đã chuyển đổi:

```typescript
pi.registerMarkdownTransformer((markdown, { messageType, isStreaming }) => {
  if (isStreaming || messageType === "assistant-thinking") return markdown;
  return markdown.replaceAll("-->", "→");
});
```

Nếu bộ chuyển đổi ném lỗi, Pi giữ Markdown đã tạo đến hiện tại và chuyển sang bộ tiếp theo. Hook chỉ để hiển thị: tin nhắn gốc vẫn không đổi trong session và model context. Hook chạy cho tin nhắn user mới, cập nhật streaming của assistant, tin nhắn session được khôi phục, và thay đổi độ rộng terminal, nên bộ chuyển đổi nên giữ tính đồng bộ và nhẹ.

### pi.registerEntryRenderer(customType, renderer)

Đăng ký renderer TUI tùy chỉnh cho entry tùy chỉnh có `customType` của bạn. Entry tùy chỉnh được tạo bằng `pi.appendEntry()` và không tham gia context LLM.

```typescript
import { Box, Text } from "@earendil-works/pi-tui";

pi.registerEntryRenderer("status-card", (entry, { expanded }, theme) => {
  const data = entry.data as { title: string; count: number };
  const box = new Box(1, 1, (text) => theme.bg("customMessageBg", text));
  box.addChild(new Text(`${theme.bold(data.title)}: ${data.count}`));
  if (expanded) {
    box.addChild(new Text(theme.fg("dim", JSON.stringify(data, null, 2))));
  }
  return box;
});

pi.appendEntry("status-card", { title: "Indexed files", count: 17 });
```

### pi.registerShortcut(shortcut, options)

Đăng ký phím tắt bàn phím. Xem [keybindings.md](keybindings.md) để biết định dạng phím tắt và các phím tắt tích hợp.

```typescript
pi.registerShortcut("ctrl+shift+p", {
  description: "Toggle plan mode",
  handler: async (ctx) => {
    ctx.ui.notify("Toggled!");
  },
});
```

### pi.registerFlag(name, options)

Đăng ký cờ CLI.

```typescript
pi.registerFlag("plan", {
  description: "Start in plan mode",
  type: "boolean",
  default: false,
});

// Check value
if (pi.getFlag("plan")) {
  // Plan mode enabled
}
```

### pi.exec(command, args, options?)

Thực thi lệnh shell.

```typescript
const result = await pi.exec("git", ["status"], { signal, timeout: 5000 });
// result.stdout, result.stderr, result.code, result.killed
```

### pi.getActiveTools() / pi.getAllTools() / pi.setActiveTools(names)

Quản lý công cụ đang hoạt động. Hàm hoạt động cho cả công cụ tích hợp lẫn công cụ đăng ký động. `pi.getActiveTools()` trả về tên công cụ đang hoạt động dạng `string[]`; `pi.getAllTools()` trả về metadata của mọi công cụ đã cấu hình.

```typescript
const active = pi.getActiveTools(); // ["read", "bash", ...]
const all = pi.getAllTools();
// all = [{
//   name: "read",
//   description: "Read file contents...",
//   parameters: ...,
//   promptGuidelines: ["Use read to examine files instead of cat or sed."],
//   sourceInfo: { path: "<builtin:read>", source: "builtin", scope: "temporary", origin: "top-level" }
// }, ...]
const builtinTools = all.filter((t) => t.sourceInfo.source === "builtin");
const extensionTools = all.filter((t) => t.sourceInfo.source !== "builtin" && t.sourceInfo.source !== "sdk");
pi.setActiveTools([...new Set([...active, "my_custom_tool"])]); // Keep current tools and enable my_custom_tool
pi.setActiveTools(["read", "bash"]); // Switch to read-only
```

`pi.getAllTools()` trả về `name`, `description`, `parameters`, `promptGuidelines`, và `sourceInfo`.

Giá trị điển hình của `sourceInfo.source`:
- `builtin` cho công cụ tích hợp
- `sdk` cho công cụ truyền qua `createAgentSession({ customTools })`
- metadata nguồn của extension cho công cụ do extension đăng ký

### pi.setModel(model)

Đặt model cho session hiện tại. Thay đổi được ghi vào lịch sử session và được khôi phục khi resume session đó, nhưng không đổi `defaultProvider` hay `defaultModel` đã cấu hình dùng cho session mới. Trả về `false` nếu xác thực chưa được cấu hình cho provider của model. Xem [models.md](models.md) để cấu hình model tùy chỉnh.

```typescript
const model = ctx.modelRegistry.find("anthropic", "claude-sonnet-4-5");
if (model) {
  const success = await pi.setModel(model);
  if (!success) {
    ctx.ui.notify("No API key for this model", "error");
  }
}
```

### pi.getThinkingLevel() / pi.setThinkingLevel(level)

Lấy thinking level hiện tại. Level được giới hạn theo khả năng của model (model không suy luận luôn dùng "off"). Thay đổi phát `thinking_level_select`.

`pi.setThinkingLevel()` đổi thinking level cho session hiện tại. Thay đổi được ghi vào lịch sử session và được khôi phục khi resume session đó, nhưng không đổi giá trị mặc định đã cấu hình dùng cho session mới.

```typescript
const current = pi.getThinkingLevel();  // "off" | "minimal" | "low" | "medium" | "high" | "xhigh" | "max"
pi.setThinkingLevel("high");
```

### pi.events

Bus sự kiện dùng chung để extension liên lạc với nhau:

```typescript
pi.events.on("my:event", (data) => { ... });
pi.events.emit("my:event", { ... });
```

### pi.registerProvider(name, config)

Đăng ký hoặc ghi đè provider model một cách động. Hữu ích cho proxy, endpoint tùy chỉnh, hoặc cấu hình model cấp độ nhóm.

Các lời gọi trong hàm factory extension được xếp hàng và áp dụng khi runner khởi tạo. Các lời gọi sau đó — ví dụ từ handler lệnh sau luồng thiết lập của người dùng — có hiệu lực ngay mà không cần `/reload`.

Provider động có thể triển khai `refreshModels`. Pi gọi hàm này trong lúc làm mới model, công bố danh sách trả về một cách đồng bộ qua provider, và truyền ngữ cảnh chuẩn về credential/catalogue đã lưu/mạng/signal. Extension tự quyết định có lưu metadata catalogue qua `context.publish({ persist: entry })` có kiểm tra generation hay không; các server trực tiếp như llama.cpp có thể trả về model mà không lưu chúng.

`context.signal` luôn là signal cụ thể và callback provider phải truyền nó cho I/O chặn. Các lời gọi công khai `ModelRuntime.refresh()` và `ModelRegistry.refresh()` nhận signal tùy chọn và không bị giới hạn khi bỏ qua signal; extension và ứng dụng tự chọn thời hạn của mình. Hủy bỏ dừng việc chờ của bên gọi ngay cả khi provider bỏ qua signal, nhưng vẫn cần phối hợp để dừng công việc bên dưới.

Extension cần auth, lọc, làm mới, hoặc hành vi streaming gốc của provider có thể đăng ký một `Provider` hoàn chỉnh từ `@earendil-works/pi-ai`. Provider trở thành nền tảng thành phần và các ghi đè từ `models.json` vẫn áp dụng lên trên.

```typescript
import { createProvider, openAICompletionsApi } from "@earendil-works/pi-ai";

const provider = createProvider({
  id: "local-server",
  name: "Local Server",
  baseUrl: "http://localhost:8080/v1",
  auth: {
    apiKey: [REDACTED]
      name: "Local server setup",
      async login(interaction) {
        return {
          type: "api_key",
          key: await interaction.prompt({ type: "secret", message: "API key" }),
        };
      },
      async resolve({ credential }) {
        return credential?.key
          ? { auth: { apiKey: [REDACTED] }, source: "stored API key" }
          : undefined;
      },
    },
  },
  models: [],
  api: openAICompletionsApi(),
});

pi.registerProvider(provider);

// Register a new provider with custom models
pi.registerProvider("my-proxy", {
  name: "My Proxy",
  baseUrl: "https://proxy.example.com",
  apiKey: "[REDACTED]",  // env var reference
  api: "anthropic-messages",
  models: [
    {
      id: "claude-sonnet-4-20250514",
      name: "Claude 4 Sonnet (proxy)",
      reasoning: false,
      input: ["text", "image"],
      cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
      contextWindow: 200000,
      maxTokens: 16384
    }
]
});

// Register a live llama.cpp catalog without persisting discovered models
pi.registerProvider("llama.cpp", {
  baseUrl: "http://[REDACTED]host:8080/v1",
  apiKey: "[REDACTED]",
  api: "openai-completions",
  async refreshModels({ signal }) {
    const response = await fetch("http://[REDACTED]host:8080/v1/models", { signal });
    const { data } = await response.json();
    return data.map(({ id }) => ({
      id,
      name: id,
      reasoning: false,
      input: ["text"],
      cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0 },
      contextWindow: 128000,
      maxTokens: 16384
    }));
  }
});

// Override baseUrl for an existing provider (keeps all models)
pi.registerProvider("anthropic", {
  baseUrl: "https://proxy.example.com"
});

// Register provider with OAuth support for /login
pi.registerProvider("corporate-ai", {
  baseUrl: "https://ai.corp.com",
  api: "openai-responses",
  models: [...],
  oauth: {
    name: "Corporate AI (SSO)",
    async login(callbacks) {
      // Custom OAuth flow
      callbacks.onAuth({ url: "https://sso.corp.com/..." });
      const code = await callbacks.onPrompt({ message: "Enter code:" });
      return { refresh: code, access: code, expires: Date.now() + 3600000 };
    },
    async refreshToken(credentials, signal) {
      signal.throwIfAborted();
      // Refresh logic
      return credentials;
    },
    getApiKey(credentials) {
      return credentials.access;
    }
  }
});
```

Dạng đối tượng chấp nhận một `Provider` pi-ai hoàn chỉnh, bao gồm hành vi `auth`, `getModels`, `refreshModels`, `filterModels`, `stream`, và `streamSimple` gốc.

**Tùy chọn cấu hình cũ:**
- `name` — Tên hiển thị của provider trong UI như `/login`.
- `baseUrl` — URL endpoint API. Bắt buộc khi định nghĩa model.
- `apiKey` — Giá trị API key dạng literal, phép nội suy biến môi trường (`$ENV_VAR` hoặc `${ENV_VAR}`), hoặc tiền tố `!command`. Bắt buộc khi định nghĩa model (trừ khi có `oauth`). `$$` thoát ký tự `$`, và `$!` thoát dấu `!` literal mà không kích hoạt thực thi lệnh.
- `api` — Kiểu API: `"anthropic-messages"`, `"openai-completions"`, `"openai-responses"`, v.v.
- `headers` — Header tùy chỉnh đưa vào request.
- `authHeader` — Nếu true, tự thêm header `Authorization=[REDACTED]
- `models` — Mảng định nghĩa model. Nếu cung cấp, thay thế mọi model hiện có của provider này. Định nghĩa model có thể đặt `baseUrl` để ghi đè endpoint của provider cho model đó.
- `refreshModels` — Callback phát hiện động bất đồng bộ. Model do hàm trả về thay thế model do extension cung cấp. `context.stored` chứa snapshot provider đã lưu; chỉ dùng `context.publish({ persist: entry })` có kiểm tra generation khi dữ liệu catalogue cập nhật nên được lưu bền. Dùng `persist: null` để xóa snapshot đó.
- `oauth` — Cấu hình provider OAuth để hỗ trợ `/login`. Khi cung cấp, provider xuất hiện trong menu đăng nhập.
- `streamSimple` — Triển khai streaming tùy chỉnh cho API không chuẩn.

Xem [custom-provider.md](custom-provider.md) cho các chủ đề nâng cao: API streaming tùy chỉnh, chi tiết OAuth, và tham chiếu định nghĩa model.

### pi.unregisterProvider(name)

Xóa provider đã đăng ký trước đó cùng các model của nó. Các model tích hợp từng bị provider ghi đè sẽ được khôi phục. Không có tác dụng nếu provider chưa được đăng ký.

Giống như `registerProvider`, hàm có hiệu lực ngay khi gọi sau giai đoạn tải ban đầu, nên không cần `/reload`.

```typescript
pi.registerCommand("my-setup-teardown", {
  description: "Remove the custom proxy provider",
  handler: async (_args, _ctx) => {
    pi.unregisterProvider("my-proxy");
  },
});
```

## Quản lý trạng thái

Extension có trạng thái nên lưu nó vào `details` của kết quả công cụ để hỗ trợ đúng cách việc rẽ nhánh:

```typescript
export default function (pi: ExtensionAPI) {
  let items: string[] = [];

  // Reconstruct state from session
  pi.on("session_start", async (_event, ctx) => {
    items = [];
    for (const entry of ctx.sessionManager.getBranch()) {
      if (entry.type === "message" && entry.message.role === "toolResult") {
        if (entry.message.toolName === "my_tool") {
          items = entry.message.details?.items ?? [];
        }
      }
    }
  });

  pi.registerTool({
    name: "my_tool",
    // ...
    async execute(toolCallId, params, signal, onUpdate, ctx) {
      items.push("new item");
      return {
        content: [{ type: "text", text: "Added" }],
        details: { items: [...items] },  // Store for reconstruction
      };
    },
  });
}
```

## Công cụ tùy chỉnh

Đăng ký công cụ để LLM gọi qua `pi.registerTool()`. Công cụ xuất hiện trong system prompt và có thể có render tùy chỉnh.

Dùng `promptSnippet` cho một mục một dòng ngắn trong mục `Available tools` của system prompt mặc định. Nếu bỏ qua, công cụ tùy chỉnh sẽ không nằm trong mục đó.

Dùng `promptGuidelines` để thêm các bullet riêng của công cụ vào mục `Guidelines` của system prompt mặc định. Các bullet này chỉ được đưa vào khi công cụ đang hoạt động (ví dụ sau `pi.setActiveTools([...])`).

**Quan trọng:** Các bullet `promptGuidelines` được nối phẳng vào mục `Guidelines` mà không có tiền tố tên công cụ hay nhóm riêng. Mỗi guideline phải nêu rõ công cụ mà nó đề cập — tránh "Use this tool when..." vì LLM không biết "this" là công cụ nào. Hãy viết "Use my_tool when..." thay thế.

Lưu ý: Một số model khá ngốc và đưa tiền tố @ vào đối số đường dẫn công cụ. Công cụ tích hợp loại bỏ `@` đầu trước khi phân giải đường dẫn. Nếu công cụ tùy chỉnh của bạn nhận một đường dẫn, hãy chuẩn hóa cả `@` đầu.

Nếu công cụ tùy chỉnh thay đổi tệp, hãy dùng `withFileMutationQueue()` để nó tham gia cùng hàng đợi theo từng tệp với `edit` và `write` tích hợp. Điều này quan trọng vì tool call chạy song song theo mặc định. Không có hàng đợi, hai công cụ có thể đọc cùng nội dung tệp cũ, tính toán các cập nhật khác nhau, rồi bản ghi vào sau cùng sẽ ghi đè bản kia.

Ví dụ trường hợp lỗi: công cụ tùy chỉnh của bạn sửa `foo.ts` trong lúc `edit` tích hợp cũng đổi `foo.ts` trong cùng lượt assistant. Nếu công cụ của bạn không tham gia hàng đợi, cả hai có thể đọc `foo.ts` gốc, áp dụng các thay đổi riêng, và một trong hai thay đổi sẽ bị mất.

Truyền đường dẫn tệp đích thực sự tới `withFileMutationQueue()`, không phải đối số thô của người dùng. Trước tiên hãy phân giải thành đường dẫn tuyệt đối, tương đối với `ctx.cwd` hoặc thư mục làm việc của công cụ. Với tệp đã tồn tại, hàm tiện ích chuẩn hóa qua `realpath()`, nên các bí danh symlink trỏ cùng tệp dùng chung một hàng đợi. Với tệp mới, hàm dùng đường dẫn tuyệt đối đã phân giải vì chưa có gì để `realpath()`.

Xếp hàng toàn bộ cửa sổ thay đổi trên đường dẫn đích đó. Bao gồm cả logic đọc-sửa-ghi, không chỉ lượt ghi cuối.

```typescript
import { withFileMutationQueue } from "@earendil-works/pi-coding-agent";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, resolve } from "node:path";

async execute(_toolCallId, params, _signal, _onUpdate, ctx) {
  const absolutePath = resolve(ctx.cwd, params.path);

  return withFileMutationQueue(absolutePath, async () => {
    await mkdir(dirname(absolutePath), { recursive: true });
    const current = await readFile(absolutePath, "utf8");
    const next = current.replace(params.oldText, params.newText);
    await writeFile(absolutePath, next, "utf8");

    return {
      content: [{ type: "text", text: `Updated ${params.path}` }],
      details: {},
    };
  });
}
```

### Định nghĩa công cụ

```typescript
import { Type } from "typebox";
import { StringEnum } from "@earendil-works/pi-ai";
import { Text } from "@earendil-works/pi-tui";

pi.registerTool({
  name: "my_tool",
  label: "My Tool",
  description: "What this tool does (shown to LLM)",
  promptSnippet: "List or add items in the project todo list",
  promptGuidelines: [
    "Use my_tool for todo planning instead of direct file edits when the user asks for a task list."
  ],
  parameters: Type.Object({
    action: StringEnum(["list", "add"] as const),  // Use StringEnum for Google compatibility
    text: Type.Optional(Type.String()),
  }),
  prepareArguments(args) {
    if (!args || typeof args !== "object") return args;
    const input = args as { action?: string; oldAction?: string };
    if (typeof input.oldAction === "string" && input.action === undefined) {
      return { ...input, action: input.oldAction };
    }
    return args;
  },

  async execute(toolCallId, params, signal, onUpdate, ctx) {
    // Check for cancellation
    if (signal?.aborted) {
      return { content: [{ type: "text", text: "Cancelled" }] };
    }

    // Stream progress updates
    onUpdate?.({
      content: [{ type: "text", text: "Working..." }],
      details: { progress: 50 },
    });

    // Run commands via pi.exec (captured from extension closure)
    const result = await pi.exec("some-command", [], { signal });

    // Return result
    return {
      content: [{ type: "text", text: "Done" }],  // Sent to LLM
      details: { data: result },                   // For rendering & state
      // usage: nestedModelResponse.usage,          // Optional nested LLM usage
      // Optional: stop after this tool batch when every finalized tool result
      // in the batch also returns terminate: true.
      terminate: true,
    };
  },

  // Optional: Custom rendering
  renderCall(args, theme, context) { ... },
  renderResult(result, options, theme, context) { ... },
});
```

**Kế toán usage:** Nếu công cụ thực hiện lời gọi LLM lồng nhau, hãy trả về `Usage` tổng hợp của chúng trong `usage`. Pi lưu giá trị này trên kết quả công cụ và đưa vào footer, `/session`, và tổng session của RPC. Handler `tool_result` có thể kiểm tra hoặc thay thế giá trị này.

**Báo hiệu lỗi:** Để đánh dấu lần thực thi công cụ là thất bại (đặt `isError: true` trên kết quả và báo cho LLM), hãy ném lỗi từ `execute`. Việc trả về một giá trị không bao giờ đặt cờ lỗi, bất kể bạn đưa thuộc tính gì vào đối tượng trả về.

**Kết thúc sớm:** Trả về `terminate: true` từ `execute()` để gợi ý bỏ qua lời gọi LLM tiếp theo tự động sau batch công cụ hiện tại. Chỉ có hiệu lực khi mọi kết quả công cụ đã hoàn tất trong batch đó đều là terminating. Xem [examples/extensions/structured-output.ts](../examples/extensions/structured-output.ts) để có ví dụ tối thiểu trong đó agent kết thúc bằng một tool call đầu ra có cấu trúc cuối cùng.

```typescript
// Correct: throw to signal an error
async execute(toolCallId, params) {
  if (!isValid(params.input)) {
    throw new Error(`Invalid input: ${params.input}`);
  }
  return { content: [{ type: "text", text: "OK" }], details: {} };
}
```

**Quan trọng:** Dùng `StringEnum` từ `@earendil-works/pi-ai` cho enum chuỗi. `Type.Union`/`Type.Literal` không hoạt động với API của Google.

**Chuẩn bị đối số:** `prepareArguments(args)` là tùy chọn. Nếu định nghĩa, hàm chạy trước khi xác thực schema và trước `execute()`. Dùng hàm này để mô phỏng dạng input cũ từng được chấp nhận khi pi resume một session cũ mà đối số tool call đã lưu không còn khớp schema hiện tại. Trả về đối tượng bạn muốn xác thực theo `parameters`. Giữ schema công khai nghiêm ngặt. Đừng thêm trường tương thích đã lỗi thời vào `parameters` chỉ để các session resume cũ tiếp tục hoạt động.

Ví dụ: một session cũ có thể chứa tool call `edit` với `oldText` và `newText` ở cấp cao nhất, trong khi schema hiện tại chỉ nhận `edits: [{ oldText, newText }]`.

```typescript
pi.registerTool({
  name: "edit",
  label: "Edit",
  description: "Edit a single file using exact text replacement",
  parameters: Type.Object({
    path: Type.String(),
    edits: Type.Array(
      Type.Object({
        oldText: Type.String(),
        newText: Type.String(),
      }),
    ),
  }),
  prepareArguments(args) {
    if (!args || typeof args !== "object") return args;

    const input = args as {
      path?: string;
      edits?: Array<{ oldText: string; newText: string }>;
      oldText?: unknown;
      newText?: unknown;
    };

    if (typeof input.oldText !== "string" || typeof input.newText !== "string") {
      return args;
    }

    return {
      ...input,
      edits: [...(input.edits ?? []), { oldText: input.oldText, newText: input.newText }],
    };
  },
  async execute(toolCallId, params, signal, onUpdate, ctx) {
    // params now matches the current schema
    return {
      content: [{ type: "text", text: `Applying ${params.edits.length} edit block(s)` }],
      details: {},
    };
  },
});
```

### Ghi đè công cụ tích hợp

Extension có thể ghi đè công cụ tích hợp (`read`, `bash`, `powershell`, `edit`, `write`, `grep`, `find`, `ls`) bằng cách đăng ký công cụ cùng tên. Chế độ tương tác hiển thị cảnh báo khi điều này xảy ra.

```bash
# Extension's read tool replaces built-in read
pi -e ./tool-override.ts
```

Ngoài ra, dùng `--no-builtin-tools` để khởi động mà không có công cụ tích hợp nào nhưng vẫn bật công cụ extension:
```bash
# No built-in tools, only extension tools
pi --no-builtin-tools -e ./my-extension.ts
```

Xem [examples/extensions/tool-override.ts](../examples/extensions/tool-override.ts) để có ví dụ hoàn chỉnh ghi đè `read` bằng logging và kiểm soát truy cập.

**Render:** Kế thừa renderer tích hợp được giải quyết theo từng slot. Ghi đè thực thi và ghi đè render là độc lập. Nếu bản ghi đè bỏ qua `renderCall`, `renderCall` tích hợp được dùng. Nếu bỏ qua `renderResult`, `renderResult` tích hợp được dùng. Nếu bỏ qua cả hai, renderer tích hợp tự động được dùng (tô sáng cú pháp, diff, v.v.). Điều này cho phép bạn bọc công cụ tích hợp để logging hoặc kiểm soát truy cập mà không phải triển khai lại UI.

**Metadata prompt:** `promptSnippet` và `promptGuidelines` không được kế thừa từ công cụ tích hợp. Nếu bản ghi đè cần giữ các hướng dẫn prompt đó, hãy định nghĩa rõ ràng trên bản ghi đè.

**Triển khai của bạn phải khớp chính xác cấu trúc kết quả**, bao gồm cả kiểu `details`. UI và logic session phụ thuộc vào các cấu trúc này để render và theo dõi trạng thái.

Triển khai công cụ tích hợp:
- [read.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/read.ts) — `ReadToolDetails`
- [bash.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/bash.ts) — `BashToolDetails`
- [powershell.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/powershell.ts) — `PowerShellToolDetails`
- [edit.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/edit.ts)
- [write.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/write.ts)
- [grep.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/grep.ts) — `GrepToolDetails`
- [find.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/find.ts) — `FindToolDetails`
- [ls.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/core/tools/ls.ts) — `LsToolDetails`

### Thực thi từ xa

Công cụ tích hợp hỗ trợ operation có thể cắm để ủy thác cho hệ thống từ xa (SSH, container, v.v.):

```typescript
import { createReadTool, createBashTool, type ReadOperations } from "@earendil-works/pi-coding-agent";

// Create tool with custom operations
const remoteRead = createReadTool(cwd, {
  operations: {
    readFile: (path) => sshExec(remote, `cat ${path}`),
    access: (path) => sshExec(remote, `test -r ${path}`).then(() => {}),
  }
});

// Register, checking flag at execution time
pi.registerTool({
  ...remoteRead,
  async execute(id, params, signal, onUpdate, _ctx) {
    const ssh = getSshConfig();
    if (ssh) {
      const tool = createReadTool(cwd, { operations: createRemoteOps(ssh) });
      return tool.execute(id, params, signal, onUpdate);
    }
    return [REDACTED]Read.execute(id, params, signal, onUpdate);
  },
});
```

**Interface operation:** `ReadOperations`, `WriteOperations`, `EditOperations`, `BashOperations`, `PowerShellOperations`, `LsOperations`, `GrepOperations`, `FindOperations`

Với `user_bash`, extension có thể tận dụng backend shell tích hợp của pi qua `createLocalBashOperations()` thay vì triển khai lại việc khởi tạo tiến trình, phân giải shell, và kết thúc cây tiến trình.

Công cụ `bash` và `powershell` cũng hỗ trợ hook spawn để điều chỉnh lệnh, cwd, hoặc env trước khi thực thi:

```typescript
import { createBashTool } from "@earendil-works/pi-coding-agent";

const bashTool = createBashTool(cwd, {
  spawnHook: ({ command, cwd, env }) => ({
    command: `source ~/.profile\n${command}`,
    cwd: `/mnt/sandbox${cwd}`,
    env: { ...env, CI: "1" },
  }),
});
```

`createBashTool()` và `createPowerShellTool()` expose session hiện tại cho lệnh qua `PI_SESSION_ID`, `PI_SESSION_FILE`, `PI_PROVIDER`, `PI_MODEL`, và `PI_REASONING_LEVEL`. Việc chèn xảy ra trước `spawnHook`, nên hook nhận các giá trị này trong `env` và bảo tồn chúng khi trải environment hiện tại như ở trên. Đặt `exposeSessionEnvironment: false` để tắt:

```typescript
const bashTool = createBashTool(cwd, {
  exposeSessionEnvironment: false,
});
```

Xem [Shell tool session environment](environment-variables.md#shell-tool-session-environment) để biết ngữ nghĩa biến. Xem [examples/extensions/ssh.ts](../examples/extensions/ssh.ts) để có ví dụ SSH hoàn chỉnh với cờ `--ssh`.

### Cắt bớt đầu ra

**Công cụ PHẢI cắt bớt đầu ra** để tránh làm quá tải context của LLM. Đầu ra lớn có thể gây:
- Lỗi tràn context (prompt quá dài)
- Lỗi compaction
- Suy giảm hiệu suất của model

Giới hạn tích hợp là **50KB** (~10k token) và **2000 dòng**, tùy theo giới hạn nào chạm trước. Dùng các tiện ích cắt bớt đã export:

```typescript
import {
  truncateHead,      // Keep first N lines/bytes (good for file reads, search results)
  truncateTail,      // Keep last N lines/bytes (good for logs, command output)
  truncateLine,      // Truncate a single line to maxBytes with ellipsis
  formatSize,        // Human-readable size (e.g., "50KB", "1.5MB")
  DEFAULT_MAX_BYTES, // 50KB
  DEFAULT_MAX_LINES, // 2000
} from "@earendil-works/pi-coding-agent";

async execute(toolCallId, params, signal, onUpdate, ctx) {
  const output = await runCommand();

  // Apply truncation
  const truncation = truncateHead(output, {
    maxLines: DEFAULT_MAX_LINES,
    maxBytes: DEFAULT_MAX_BYTES,
  });

  let result = truncation.content;

  if (truncation.truncated) {
    // Write full output to temp file
    const tempFile = writeTempFile(output);

    // Inform the LLM where to find complete output
    result += `\n\n[Output truncated: ${truncation.outputLines} of ${truncation.totalLines} lines`;
    result += ` (${formatSize(truncation.outputBytes)} of ${formatSize(truncation.totalBytes)}).`;
    result += ` Full output saved to: ${tempFile}]`;
  }

  return { content: [{ type: "text", text: result }] };
}
```

**Điểm chính:**
- Dùng `truncateHead` cho nội dung mà phần đầu quan trọng (kết quả tìm kiếm, đọc tệp)
- Dùng `truncateTail` cho nội dung mà phần cuối quan trọng (log, đầu ra lệnh)
- Luôn báo cho LLM khi đầu ra bị cắt và nơi tìm phiên bản đầy đủ
- Ghi rõ giới hạn cắt bớt trong mô tả công cụ của bạn

Xem [examples/extensions/truncated-tool.ts](../examples/extensions/truncated-tool.ts) để có ví dụ hoàn chỉnh bọc `rg` (ripgrep) với cách cắt bớt đúng.

### Nhiều công cụ

Một extension có thể đăng ký nhiều công cụ dùng chung trạng thái:

```typescript
export default function (pi: ExtensionAPI) {
  let connection = null;

  pi.registerTool({ name: "db_connect", ... });
  pi.registerTool({ name: "db_query", ... });
  pi.registerTool({ name: "db_close", ... });

  pi.on("session_shutdown", async () => {
    connection?.close();
  });
}
```

### Render tùy chỉnh

Công cụ có thể cung cấp `renderCall` và `renderResult` để hiển thị TUI tùy chỉnh. Xem [tui.md](tui.md) để biết API thành phần đầy đủ và [tool-execution.ts](https://github.com/earendil-works/pi/blob/main/packages/coding-agent/src/modes/interactive/components/tool-execution.ts) để biết cách các dòng công cụ được lắp ráp.

Theo mặc định, đầu ra công cụ được bọc trong `Box` xử lý padding và nền. `renderCall` hoặc `renderResult` được định nghĩa phải trả về một `Component`. Nếu renderer của một slot không được định nghĩa, `tool-execution.ts` dùng render dự phòng cho slot đó.

Đặt `renderShell: "self"` khi công cụ nên render vỏ của chính nó thay vì dùng `Box` mặc định. Điều này hữu ích cho công cụ cần kiểm soát hoàn toàn khung hoặc hành vi nền, ví dụ bản xem trước lớn phải giữ ổn định về mặt thị giác sau khi công cụ ổn định.

```typescript
pi.registerTool({
  name: "my_tool",
  label: "My Tool",
  description: "Custom shell example",
  parameters: Type.Object({}),
  renderShell: "self",
  async execute() {
    return { content: [{ type: "text", text: "ok" }], details: undefined };
  },
  renderCall(args, theme, context) {
    return new Text(theme.fg("accent", "my custom shell"), 0, 0);
  },
});
```

`renderCall` và `renderResult` mỗi hàm nhận một đối tượng `context` với:
- `args` — đối số tool call hiện tại
- `state` — trạng thái dùng chung theo dòng giữa `renderCall` và `renderResult`
- `lastComponent` — thành phần được trả về trước đó cho slot này, nếu có
- `invalidate()` — yêu cầu render lại dòng công cụ này
- `toolCallId`, `cwd`, `executionStarted`, `argsComplete`, `isPartial`, `expanded`, `showImages`, `isError`

Dùng `context.state` cho trạng thái dùng chung giữa các slot. Giữ cache riêng của slot trên instance component được trả về khi bạn muốn tái sử dụng và thay đổi cùng một component qua các lần render.

#### renderCall

Render lời gọi công cụ hoặc tiêu đề:

```typescript
import { Text } from "@earendil-works/pi-tui";

renderCall(args, theme, context) {
  const text = (context.lastComponent as Text | undefined) ?? new Text("", 0, 0);
  let content = theme.fg("toolTitle", theme.bold("my_tool "));
  content += theme.fg("muted", args.action);
  if (args.text) {
    content += " " + theme.fg("dim", `"${args.text}"`);
  }
  text.setText(content);
  return text;
}
```

#### renderResult

Render kết quả hoặc đầu ra công cụ:

```typescript
renderResult(result, { expanded, isPartial }, theme, context) {
  if (isPartial) {
    return new Text(theme.fg("warning", "Processing..."), 0, 0);
  }

  if (result.details?.error) {
    return new Text(theme.fg("error", `Error: ${result.details.error}`), 0, 0);
  }

  let text = theme.fg("success", "✓ Done");
  if (expanded && result.details?.items) {
    for (const item of result.details.items) {
      text += "\n  " + theme.fg("dim", item);
    }
  }
  return new Text(text, 0, 0);
}
```

Nếu một slot cố tình không có nội dung hiển thị, hãy trả về một `Component` rỗng chẳng hạn `Container` rỗng.

#### Gợi ý phím tắt

Dùng `keyHint()` để hiển thị gợi ý phím tắt tôn trọng cấu hình keybinding đang hoạt động:

```typescript
import { keyHint } from "@earendil-works/pi-coding-agent";

renderResult(result, { expanded }, theme, context) {
  let text = theme.fg("success", "✓ Done");
  if (!expanded) {
    text += ` (${keyHint("app.tools.expand", "to expand")})`;
  }
  return new Text(text, 0, 0);
}
```

Các hàm có sẵn:
- `keyHint(keybinding, description)` — Định dạng id keybinding đã cấu hình như `"app.tools.expand"` hoặc `"tui.select.confirm"`
- `keyText(keybinding)` — Trả về văn bản phím đã cấu hình thô cho một id keybinding
- `rawKeyHint(key, description)` — Định dạng chuỗi phím thô

Dùng id keybinding có namespace:
- Id của coding-agent dùng namespace `app.*`, ví dụ `app.tools.expand`, `app.editor.external`, `app.session.rename`
- Id TUI dùng chung dùng namespace `tui.*`, ví dụ `tui.select.confirm`, `tui.select.cancel`, `tui.input.tab`

Xem [keybindings.md](keybindings.md) để có danh sách đầy đủ id keybinding và giá trị mặc định. `keybindings.json` dùng cùng id có namespace đó.

Editor tùy chỉnh và thành phần `ctx.ui.custom()` nhận `keybindings: KeybindingsManager` như một đối số được tiêm. Chúng nên dùng trực tiếp manager đã tiêm thay vì gọi `getKeybindings()` hay `setKeybindings()`.

#### Thực hành tốt nhất

- Dùng `Text` với padding `(0, 0)`. `Box` mặc định xử lý padding.
- Dùng `\n` cho nội dung nhiều dòng.
- Xử lý `isPartial` cho tiến trình streaming.
- Hỗ trợ `expanded` để xem chi tiết theo yêu cầu.
- Giữ chế độ xem mặc định gọn.
- Đọc `context.args` trong `renderResult` thay vì sao chép đối số vào `context.state`.
- Chỉ dùng `context.state` cho dữ liệu bắt buộc phải chia sẻ giữa slot call và result.
- Tái sử dụng `context.lastComponent` khi có thể cập nhật tại chỗ cùng một instance component.
- Chỉ dùng `renderShell: "self"` khi vỏ hộp mặc định cản trở. Trong chế độ tự vỏ, công cụ tự chịu trách nhiệm khung, padding, và nền.

#### Dự phòng

Nếu renderer của slot không được định nghĩa hoặc ném lỗi:
- `renderCall`: Hiển thị tên công cụ
- `renderResult`: Hiển thị văn bản thô từ `content`

### Tải công cụ động

Extension có thể đăng ký nhiều công cụ nhưng chỉ giữ một tập nhỏ hoạt động ban đầu. Sau đó một công cụ có thể thêm công cụ khác với `pi.setActiveTools()` trong lúc thực thi. Pi phát hiện các thay đổi hoàn toàn cộng thêm, ghi tên công cụ mới có sẵn trên kết quả công cụ đó, và áp dụng tập hoạt động cập nhật trước request model tiếp theo.

Cách này hoạt động với mọi model. Model hỗ trợ deferred loading gốc giữ tiền tố prompt ổn định và tải định nghĩa mới tại vị trí kết quả công cụ. Các model khác dùng cơ chế dự phòng mô tả bên dưới.

Vòng đời:

1. Đăng ký mọi công cụ với `pi.registerTool()` để xuất hiện trong `pi.getAllTools()`.
2. Giữ công cụ loader như `search_tools` hoạt động và để các công cụ có thể tìm kiếm ở trạng thái không hoạt động.
3. Trong lúc loader thực thi, gọi `pi.setActiveTools([...currentTools, ...matchingTools])`. Thay đổi phải mang tính cộng thêm: đừng xóa công cụ đang hoạt động trong cùng lời gọi.
4. Pi ghi lại công cụ nào được thêm trên kết quả công cụ của loader.
5. Trước phản hồi model tiếp theo, Pi expose các định nghĩa mới bằng deferred loading gốc khi hỗ trợ, nếu không thì dùng danh sách công cụ hoạt động thông thường.

Bạn không cần trả về tham chiếu công cụ riêng của provider hay đánh dấu loader là công cụ tìm kiếm đặc biệt. Thay đổi tập công cụ hoạt động chính là tín hiệu. Tên truyền cho `pi.setActiveTools()` phải đã được đăng ký; tên không biết bị bỏ qua.

#### Model có deferred loading gốc

- **Anthropic**
  - **Model:** Sonnet, Opus, Fable phiên bản 4.5 trở lên (không gồm Haiku)
  - **Biểu diễn gốc:** Định nghĩa deferred dùng `defer_loading`; điểm tải dùng nội dung `tool_reference`.
- **Fireworks Messages API**
  - **Biểu diễn gốc:** Định nghĩa deferred dùng `defer_loading`; điểm tải dùng nội dung `tool_reference`.
  - **Tên loader:** Dùng `ToolSearch` hoặc `tool_search` để defer theo tiền tố. Tên loader khác vẫn hoạt động, nhưng Fireworks đưa schema đã tải vào tiền tố công cụ ban đầu, làm mất lợi ích cache.
  - Điều này không đổi định tuyến API: model GLM của Fireworks và Kimi K3 dùng Chat Completions, không phải Messages.
- **OpenAI**
  - **Model:** họ `gpt-5.4` trở lên
  - **Biểu diễn gốc:** Pi thêm các mục `tool_search_call` và `tool_search_output` đã hoàn tất phía client tại điểm tải.

Với model tùy chỉnh hoặc proxy đã kiểm chứng, có thể bật xử lý gốc với `compat.supportsToolReferences: true` cho `anthropic-messages`, hoặc `compat.supportsToolSearch: true` cho `openai-responses` và `openai-codex-responses`. Để các tùy chọn này tắt trừ khi endpoint và model chấp nhận giao thức gốc tương ứng.

#### Hành vi dự phòng

Với mọi model và provider khác, kích hoạt động vẫn hoạt động: Pi gửi danh sách công cụ hoạt động hiện tại đầy đủ như bình thường ở request tiếp theo. Model có thể gọi công cụ mới được kích hoạt, nhưng việc thêm định nghĩa có thể làm mất hiệu lực tiền tố prompt đã cache của provider.

Pi cũng dùng dự phòng an toàn này khi tập hoạt động không hoàn toàn mang tính cộng thêm, chẳng hạn thay thế một nhóm công cụ bằng nhóm khác. Vì vậy việc xóa công cụ hoạt động, nhưng không dùng deferred loading.

Để có hành vi cache tốt nhất, giữ công cụ loader hoạt động trong suốt session và thêm công cụ thay vì thay thế tập hoạt động. Cũng lưu ý rằng kích hoạt công cụ có `promptSnippet` hoặc `promptGuidelines` sẽ xây dựng lại system prompt; thay đổi system prompt đó có thể làm mất hiệu lực tiền tố ngay cả khi provider hỗ trợ schema deferred. Công cụ tải lười thường nên dựa vào `description` của công cụ và bỏ qua metadata prompt chỉ hoạt động khi active.

#### Ví dụ công cụ tìm kiếm

Extension sau đăng ký hai công cụ có thể tìm kiếm, xóa chúng khỏi tập hoạt động ban đầu, và chỉ giữ `search_tools` làm loader. Ví dụ dùng đối chiếu từ khóa đơn giản, nhưng phần tìm kiếm có thể dùng BM25, embedding, catalogue từ xa, hoặc định tuyến riêng của dự án.

```typescript
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";

const SEARCHABLE_TOOL_NAMES = new Set(["lookup_weather", "search_issues"]);

export default function (pi: ExtensionAPI) {
  pi.registerTool({
    name: "lookup_weather",
    label: "Lookup Weather",
    description: "Look up the current weather for a city",
    parameters: Type.Object({ city: Type.String() }),
    async execute(_toolCallId, params) {
      return {
        content: [{ type: "text", text: `Weather for ${params.city}: sunny` }],
        details: {},
      };
    },
  });

  pi.registerTool({
    name: "search_issues",
    label: "Search Issues",
    description: "Search project issues by keyword",
    parameters: Type.Object({ query: Type.String() }),
    async execute(_toolCallId, params) {
      return {
        content: [{ type: "text", text: `No open issues matching ${params.query}` }],
        details: {},
      };
    },
  });

  pi.registerTool({
    name: "search_tools",
    label: "Search Tools",
    description: "Search for and enable tools relevant to a task",
    promptSnippet: "Search for additional tools when the active tools cannot perform the task",
    promptGuidelines: [
      "Use search_tools when a task requires a capability that is not currently available.",
    ],
    parameters: Type.Object({
      query: Type.String({ description: "Capability or task to search for" }),
      limit: Type.Optional(Type.Integer({ minimum: 1, maximum: 10 })),
    }),
    async execute(_toolCallId, params) {
      const terms = params.query.toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
      const matches = pi.getAllTools()
        .filter((tool) => SEARCHABLE_TOOL_NAMES.has(tool.name))
        .map((tool) => ({
          tool,
          score: terms.reduce(
            (score, term) =>
              score + (`${tool.name} ${tool.description}`.toLowerCase().includes(term) ? 1 : 0),
            0,
          ),
        }))
        .filter((match) => match.score > 0)
        .sort((a, b) => b.score - a.score)
        .slice(0, params.limit ?? 3)
        .map((match) => match.tool.name);

      if (matches.length === 0) {
        return {
          content: [{ type: "text", text: `No tools found for: ${params.query}` }],
          details: { matches: [] },
        };
      }

      const active = pi.getActiveTools();
      const added = matches.filter((name) => !active.includes(name));
      pi.setActiveTools([...new Set([...active, ...added])]);

      return {
        content: [{
          type: "text",
          text: added.length > 0
          details: {},
        };
      },
    });
  });
}
```

Xem [examples/extensions/dynamic-tools.ts](../examples/extensions/dynamic-tools.ts) để có ví dụ chạy được.

## UI tùy chỉnh

Extension có thể tương tác với người dùng qua `ctx.ui`. Tất cả phương thức UI đều có sẵn trong extension, công cụ, và lệnh. Các phương thức `select`, `confirm`, `input`, và `editor` chỉ nên dùng ở chế độ có UI (xem `ctx.hasUI`).

### Hộp thoại và thông báo

```typescript
// Selection list
const choice = await ctx.ui.select("Pick one:", ["Option A", "Option B"]);

// Confirm dialog
const ok = await ctx.ui.confirm("Delete?", "This cannot be undone");

// Text input
const name = await ctx.ui.input("Name:", "placeholder");

// Multi-line editor
const text = await ctx.ui.editor("Edit:", "prefilled text");

// Notification (non-blocking)
ctx.ui.notify("Done!", "info");  // "info" | "warning" | "error"
```

#### Hộp thoại có thời hạn với đồng hồ đếm ngược

Hộp thoại hỗ trợ tùy chọn `timeout` tự động đóng với đồng hồ đếm ngược trực tiếp:

```typescript
// Dialog shows "Title (5s)" → "Title (4s)" → ... → auto-dismisses at 0
const confirmed = await ctx.ui.confirm(
  "Timed Confirmation",
  "This dialog will auto-cancel in 5 seconds. Confirm?",
  { timeout: 5000 }
);

if (confirmed) {
  // User confirmed
} else {
  // User cancelled or timed out
}
```

**Giá trị trả về khi hết thời hạn:**
- `select()` trả về `undefined`
- `confirm()` trả về `false`
- `input()` trả về `undefined`

#### Tự đóng thủ công với AbortSignal

Để kiểm soát chặt hơn (ví dụ phân biệt hết thời hạn với người dùng hủy), dùng `AbortSignal`:

```typescript
const controller = new AbortController();
const timeoutId = setTimeout(() => controller.abort(), 5000);

const confirmed = await ctx.ui.confirm(
  "Timed Confirmation",
  "This dialog will auto-cancel in 5 seconds. Confirm?",
  { signal: controller.signal }
);

clearTimeout(timeoutId);

if (confirmed) {
  // User confirmed
} else if (controller.signal.aborted) {
  // Dialog timed out
} else {
  // User cancelled (pressed Escape or selected "No")
}
```

Xem [examples/extensions/timed-confirm.ts](../examples/extensions/timed-confirm.ts) để có ví dụ hoàn chỉnh.

### Widget, trạng thái, và footer

```typescript
// Status in footer (persistent until cleared)
ctx.ui.setStatus("my-ext", "Processing...");
ctx.ui.setStatus("my-ext", undefined);  // Clear

// Working loader (shown during streaming)
ctx.ui.setWorkingMessage("Thinking deeply...");
ctx.ui.setWorkingMessage();  // Restore default
ctx.ui.setWorkingVisible(false);  // Hide the built-in working loader row entirely
ctx.ui.setWorkingVisible(true);   // Show the built-in working loader row

// Working indicator (shown during streaming)
ctx.ui.setWorkingIndicator({ frames: [ctx.ui.theme.fg("accent", "●")] });  // Static dot
ctx.ui.setWorkingIndicator({
  frames: [
    ctx.ui.theme.fg("dim", "·"),
    ctx.ui.theme.fg("muted", "•"),
    ctx.ui.theme.fg("accent", "●"),
    ctx.ui.theme.fg("muted", "•"),
  ],
  intervalMs: 120,
});
ctx.ui.setWorkingIndicator({ frames: [] });  // Hide indicator
ctx.ui.setWorkingIndicator();  // Restore default spinner

// Widget above editor (default)
ctx.ui.setWidget("my-widget", ["Line 1", "Line 2"]);
// Widget below editor
ctx.ui.setWidget("my-widget", ["Line 1", "Line 2"], { placement: "belowEditor" });
ctx.ui.setWidget("my-widget", (tui, theme) => new Text(theme.fg("accent", "Custom"), 0, 0));
ctx.ui.setWidget("my-widget", undefined);  // Clear

// Custom footer (replaces built-in footer entirely)
ctx.ui.setFooter((tui, theme) => ({
  render(width) { return [theme.fg("dim", "Custom footer")]; },
  invalidate() {},
}));
ctx.ui.setFooter(undefined);  // Restore built-in footer

// Terminal title
ctx.ui.setTitle("pi - my-project");

// Editor text
ctx.ui.setEditorText("Prefill text");
const current = ctx.ui.getEditorText();

// Paste into editor (triggers paste handling, including collapse for large content)
ctx.ui.pasteToEditor("pasted content");

// Stack custom autocomplete behavior on top of the built-in provider
ctx.ui.addAutocompleteProvider((current) => ({
  triggerCharacters: ["#"],
  async getSuggestions(lines, line, col, options) {
    const beforeCursor = (lines[line] ?? "").slice(0, col);
    const match = beforeCursor.match(/(?:^|[ \t])#([^\s#]*)$/);
    if (!match) {
      return current.getSuggestions(lines, line, col, options);
    }

    return {
      prefix: `#${match[1] ?? ""}`,
      items: [
        { value: "#2983", label: "#2983", description: "Extension API for registering custom @ autocomplete providers" },
        { value: "#2753", label: "#2753", description: "Reload stale resource settings" },
      ],
    };
  }
}));
```

### Thành phần UI tùy chỉnh

Đối với các tương tác phức tạp, dùng `ctx.ui.custom()` để render một thành phần TUI đầy đủ:

```typescript
import { Container, Text, SelectList, type TUI, type Theme } from "@earendil-works/pi-tui";

const result = await ctx.ui.custom<string>((tui: TUI, theme: Theme, keybindings, done) => {
  const list = new SelectList(["Option A", "Option B"], 2, theme);
  list.onSelection = (value) => done(value);

  const container = new Container();
  container.addChild(new Text("Choose an option:", 1, 0));
  container.addChild(list);

  return {
    render: (width) => container.render(width),
    invalidate: () => container.invalidate(),
    handleInput: (data) => list.handleInput(data),
  };
});
```

`ctx.ui.custom()` cung cấp một đối tượng `TUI` thô và theme. Dùng các thành phần [`@earendil-works/pi-tui`](tui.md) để hiển thị nhất quán.

Đối với thành phần kiểu overlay/editor, truyền `overlay: true` và `overlayOptions` tùy chọn:

```typescript
await ctx.ui.custom((tui, theme, keybindings, done) => {
  // ...
  return component;
}, {
  overlay: true,
  overlayOptions: { anchor: "top-right", width: "50%", margin: 2 },
  onHandle: (handle) => {
    // Called when the overlay is created/destroyed
  },
});
```

#### Chế độ Overlay (Thử nghiệm)

`ctx.ui.custom()` có thể render các thành phần bên trong overlay với hỗ trợ các lớp nổi lồng nhau:

```typescript
await ctx.ui.custom((tui, theme, keybindings, done) => {
  const box = new Box(1, 1, (s) => theme.bg("customMessageBg", s));
  box.addChild(new Text("Overlay content", 1, 0));

  // Get a handle to create nested overlays
  const handle = tui.getCustomHandle();

  return {
    render: (width) => box.render(width),
    invalidate: () => box.invalidate(),
    handleInput: (data) => {
      if (matchesKey(data, "escape")) done(undefined);
      else box.handleInput(data);
    },
  };
}, { overlay: true, overlayOptions: { anchor: "center", width: 40 } });
```

**Tùy chọn overlay:**
- `anchor`: `"center" | "top-left" | "top-right" | "bottom-left" | "bottom-right"`
- `width`: Số cột cố định hoặc chuỗi phần trăm (ví dụ: `"50%"`)
- `margin`: Khoảng cách từ cạnh màn hình
- `maxHeight`: Chiều cao tối đa trước khi cuộn

Chế độ overlay đang ở dạng thử nghiệm — API có thể thay đổi trong các phiên bản tương lai.

### Editor tùy chỉnh

Thay thế editor nhập liệu chính bằng một triển khai tùy chỉnh (chế độ vim, chế độ emacs, v.v.):

```typescript
import { CustomEditor, type ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { matchesKey } from "@earendil-works/pi-tui";

class VimEditor extends CustomEditor {
  private mode: "normal" | "insert" = "insert";

  handleInput(data: string): void {
    if (matchesKey(data, "escape") && this.mode === "insert") {
      this.mode = "normal";
      return;
    }
    if (this.mode === "normal" && data === "i") {
      this.mode = "insert";
      return;
    }
    super.handleInput(data);  // App keybindings + text editing
  }
}

export default function (pi: ExtensionAPI) {
  pi.on("session_start", (_event, ctx) => {
    ctx.ui.setEditorComponent((tui, theme, keybindings) =>
      new VimEditor(tui, theme, keybindings)
    );
  });
}
```

**Điểm chính:**
- Kế thừa `CustomEditor` (không phải `Editor` gốc) để có các phím tắt của ứng dụng (escape để hủy, ctrl+d, chuyển model)
- Gọi `super.handleInput(data)` cho các phím bạn không xử lý
- Editor tùy chỉnh giữ dòng working độc lập theo mặc định. Truyền `{ embedWorkingStatus: true }` làm đối số thứ tư của hàm tạo `CustomEditor` để dùng spinner viền editor tích hợp thay thế.
- Factory nhận `tui`, `theme`, và `keybindings` từ ứng dụng
- Dùng `ctx.ui.getEditorComponent()` trước `setEditorComponent()` để bọc editor tùy chỉnh đã cấu hình trước đó
- Truyền `undefined` để khôi phục mặc định: `ctx.ui.setEditorComponent(undefined)`

Để kết hợp với extension khác đã thay thế editor, hãy lưu factory trước đó trước khi đặt factory của bạn:

```typescript
const previous = ctx.ui.getEditorComponent();
ctx.ui.setEditorComponent((tui, theme, keybindings) =>
  new MyEditor(tui, theme, keybindings, { base: previous?.(tui, theme, keybindings) })
);
```

Xem [tui.md](tui.md) Pattern 7 để có ví dụ đầy đủ với chỉ báo chế độ.

### Render tin nhắn và entry

Đăng ký renderer tùy chỉnh cho tin nhắn với `customType` của bạn. Dùng message renderer cho nội dung nên tham gia vào context của LLM:

```typescript
import { Text } from "@earendil-works/pi-tui";

pi.registerMessageRenderer("my-extension", (message, options, theme) => {
  const { expanded, outputPad } = options;
  let text = theme.fg("accent", `[${message.customType}] `);
  text += message.content;

  if (expanded && message.details) {
    text += "\n" + theme.fg("dim", JSON.stringify(message.details, null, 2));
  }

  return new Text(text, outputPad, 0);
});
```

Tin nhắn được gửi qua `pi.sendMessage()`:

```typescript
pi.sendMessage({
  customType: "my-extension",  // Matches registerMessageRenderer
  content: "Status update",
  display: true,               // Show in TUI
  details: { ... },            // Available in renderer
});
```

Đối với nội dung chỉ dành cho TUI không nên gửi tới LLM, hãy render các entry tùy chỉnh thay thế:

```typescript
pi.registerEntryRenderer("my-card", (entry, options, theme) => {
  return new Text(theme.fg("accent", JSON.stringify(entry.data)));
});

pi.appendEntry("my-card", { status: "done" });
```

### Màu theme

Tất cả hàm render đều nhận một đối tượng `theme`. Xem [themes.md](themes.md) để tạo theme tùy chỉnh và bảng màu đầy đủ.

```typescript
// Foreground colors
theme.fg("toolTitle", text)   // Tool names
theme.fg("accent", text)      // Highlights
theme.fg("success", text)     // Success (green)
theme.fg("error", text)       // Errors (red)
theme.fg("warning", text)     // Warnings (yellow)
theme.fg("muted", text)       // Secondary text
theme.fg("dim", text)         // Tertiary text

// Text styles
theme.bold(text)
theme.italic(text)
theme.strikethrough(text)
```

Để tô sáng cú pháp trong renderer công cụ tùy chỉnh:

```typescript
import { highlightCode, getLanguageFromPath } from "@earendil-works/pi-coding-agent";

// Highlight code with explicit language
const highlighted = highlightCode("const x = 1;", "typescript", theme);

// Auto-detect language from file path
const lang = getLanguageFromPath("/path/to/file.rs");  // "rust"
const highlighted = highlightCode(code, lang, theme);
```

## Xử lý lỗi

- Lỗi extension được ghi log, agent tiếp tục chạy
- Lỗi `tool_call` chặn công cụ (fail-safe)
- Lỗi `execute` của công cụ phải được báo bằng cách ném ngoại lệ; ngoại lệ bị bắt, báo cho LLM với `isError: true`, và việc thực thi tiếp tục

## Hành vi chế độ

| Chế độ | `ctx.mode` | `ctx.hasUI` | Ghi chú |
|------|------------|-------------|-------|
| Tương tác | `"tui"` | `true` | TUI đầy đủ với hiển thị terminal |
| RPC (`--mode rpc`) | `"rpc"` | `true` | Hộp thoại và thông báo qua giao thức JSON; `custom()` trả về `undefined`. Xem [rpc.md](rpc.md) |
| JSON (`--mode json`) | `"json"` | `false` | Luồng sự kiện ra stdout; các phương thức UI không làm gì cả |
| Print (`-p`) | `"print"` | `false` | Extension chạy nhưng không thể nhắc người dùng |

Dùng `ctx.mode === "tui"` trước các tính năng riêng của TUI (`custom()`, factory thành phần, đầu vào terminal). Dùng `ctx.hasUI` trước các phương thức hộp thoại và thông báo hoạt động ở cả chế độ TUI và RPC.

## Tham chiếu ví dụ

Tất cả ví dụ nằm trong [examples/extensions/](../examples/extensions/).

| Ví dụ | Mô tả | API chính |
|---------|-------------|----------|
| **Công cụ** |||
| `hello.ts` | Đăng ký công cụ tối thiểu | `registerTool` |
| `question.ts` | Công cụ với tương tác người dùng | `registerTool`, `ui.select` |
| `questionnaire.ts` | Công cụ wizard nhiều bước | `registerTool`, `ui.custom` |
| `todo.ts` | Công cụ có trạng thái với lưu trữ | `registerTool`, `appendEntry`, `renderResult`, sự kiện session |
| `dynamic-tools.ts` | Đăng ký công cụ sau khi khởi động và trong lúc chạy lệnh | `registerTool`, `session_start`, `registerCommand` |
| `structured-output.ts` | Công cụ output có cấu trúc cuối cùng với `terminate: true` | `registerTool`, kết quả công cụ kết thúc |
| `truncated-tool.ts` | Ví dụ cắt bớt đầu ra | `registerTool`, `truncateHead` |
| `tool-override.ts` | Ghi đè công cụ read tích hợp | `registerTool` (cùng tên với công cụ tích hợp) |
| **Lệnh** |||
| `pirate.ts` | Sửa system prompt theo từng lượt | `registerCommand`, `before_agent_start` |
| `summarize.ts` | Lệnh tóm tắt cuộc hội thoại | `registerCommand`, `ui.custom` |
| `handoff.ts` | Chuyển model giữa các provider | `registerCommand`, `ui.editor`, `ui.custom` |
| `qna.ts` | Hỏi & đáp với UI tùy chỉnh | `registerCommand`, `ui.custom`, `setEditorText` |
| `send-user-message.ts` | Chèn tin nhắn người dùng | `registerCommand`, `sendUserMessage` |
| `reload-runtime.ts` | Lệnh reload và bàn giao công cụ LLM | `registerCommand`, `ctx.reload()`, `sendUserMessage` |
| `shutdown-command.ts` | Lệnh tắt máy graceful | `registerCommand`, `shutdown()` |
| **Sự kiện & Cổng** |||
| `permission-gate.ts` | Chặn lệnh nguy hiểm | `on("tool_call")`, `ui.confirm` |
| `project-trust.ts` | Quyết định hoặc hoãn việc tin tưởng dự án từ extension user/global hoặc CLI | `on("project_trust")`, UI tin tưởng, kết quả tin tưởng bắt buộc |
| `protected-paths.ts` | Chặn ghi vào các đường dẫn cụ thể | `on("tool_call")` |
| `confirm-destructive.ts` | Xác nhận thay đổi session | `on("session_before_switch")`, `on("session_before_fork")` |
| `dirty-repo-guard.ts` | Cảnh báo repo git có thay đổi | `on("session_before_*")`, `exec` |
| `input-transform.ts` | Biến đổi đầu vào người dùng | `on("input")` |
| `input-transform-streaming.ts` | Biến đổi đầu vào nhận biết streaming | `on("input")`, `streamingBehavior` |
| `model-status.ts` | Phản ứng với thay đổi model | `on("model_select")`, `setStatus` |
| `provider-payload.ts` | Kiểm tra payload và header phản hồi của provider | `on("before_provider_request")`, `on("after_provider_response")` |
| `system-prompt-header.ts` | Hiển thị thông tin system prompt | `on("agent_start")`, `getSystemPrompt` |
| `claude-rules.ts` | Tải rule từ tệp | `on("session_start")`, `on("before_agent_start")` |
| `prompt-customizer.ts` | Thêm hướng dẫn công cụ theo context dùng `systemPromptOptions` | `on("before_agent_start")`, `BuildSystemPromptOptions` |
| `file-trigger.ts` | File watcher kích hoạt tin nhắn | `sendMessage` |
| **Compaction & Session** |||
| `custom-compaction.ts` | Tóm tắt compaction tùy chỉnh | `on("session_before_compact")` |
| `trigger-compact.ts` | Kích hoạt compaction thủ công | `compact()` |
| `git-checkpoint.ts` | Git stash ở mỗi lượt | `on("turn_start")`, `on("session_before_fork")`, `exec` |
| `git-merge-and-resolve.ts` | Lấy, merge và giải quyết xung đột | `on("agent_end")`, `exec`, `sendUserMessage` |
| `auto-commit-on-exit.ts` | Commit khi tắt máy | `on("session_shutdown")`, `exec` |
| **Thành phần UI** |||
| `status-line.ts` | Chỉ báo trạng thái ở footer | `setStatus`, sự kiện session |
| `working-indicator.ts` | Tùy biến chỉ báo working khi streaming | `setWorkingIndicator`, `registerCommand` |
| `github-issue-autocomplete.ts` | Thêm gợi ý issue `#1234` lên autocomplete tích hợp bằng cách tải trước các issue mở gần đây từ `gh issue list` | `addAutocompleteProvider`, `on("session_start")`, `exec` |
| `custom-footer.ts` | Thay thế footer hoàn toàn | `registerCommand`, `setFooter` |
| `custom-header.ts` | Thay thế header khởi động | `on("session_start")`, `setHeader` |
| `modal-editor.ts` | Editor kiểu modal vim | `setEditorComponent`, `CustomEditor` |
| `rainbow-editor.ts` | Tạo kiểu editor tùy chỉnh | `setEditorComponent` |
| `widget-placement.ts` | Widget phía trên/dưới editor | `setWidget` |
| `overlay-test.ts` | Thành phần overlay | `ui.custom` với các tùy chọn overlay |
| `overlay-qa-tests.ts` | Kiểm tra overlay toàn diện | `ui.custom`, tất cả tùy chọn overlay |
| `notify.ts` | Thông báo đơn giản | `ui.notify` |
| `timed-confirm.ts` | Hộp thoại có thời hạn | `ui.confirm` với timeout/signal |
| `mac-system-theme.ts` | Tự động đổi theme | `setTheme`, `exec` |
| **Extension phức tạp** |||
| `plan-mode/` | Triển khai đầy đủ chế độ plan | Tất cả kiểu sự kiện, `registerCommand`, `registerShortcut`, `registerFlag`, `setStatus`, `setWidget`, `sendMessage`, `setActiveTools` |
| `preset.ts` | Preset có thể lưu (model, công cụ, thinking) | `registerCommand`, `registerShortcut`, `registerFlag`, `setModel`, `setActiveTools`, `setThinkingLevel`, `appendEntry` |
| `tools.ts` | UI bật/tắt công cụ | `registerCommand`, `setActiveTools`, `SettingsList`, sự kiện session |
| **Remote & Sandbox** |||
| `ssh.ts` | Thực thi SSH từ xa | `registerFlag`, `on("user_bash")`, `on("before_agent_start")`, thao tác công cụ |
| `interactive-shell.ts` | Phiên shell liên tục | `on("user_bash")` |
| `sandbox/` | Thực thi công cụ trong sandbox | Thao tác công cụ |
| `gondolin/` | Điều hướng công cụ tích hợp và lệnh `!` vào micro-VM Gondolin | Thao tác công cụ, ghi đè công cụ tích hợp, `on("user_bash")` |
| `subagent/` | Tạo sub-agent | `registerTool`, `exec` |
| **Trò chơi** |||
| `snake.ts` | Trò chơi Snake | `registerCommand`, `ui.custom`, xử lý bàn phím |
| `space-invaders.ts` | Trò chơi Space Invaders | `registerCommand`, `ui.custom` |
| `doom-overlay/` | Doom trong overlay | `ui.custom` với overlay |
| **Provider** |||
| `custom-provider-anthropic/` | Proxy Anthropic tùy chỉnh | `registerProvider` |
| `custom-provider-gitlab-duo/` | Tích hợp GitLab Duo | `registerProvider` với OAuth |
| **Tin nhắn & Giao tiếp** |||
| `message-renderer.ts` | Render tin nhắn tùy chỉnh | `registerMessageRenderer`, `sendMessage` |
| `entry-renderer.ts` | Render entry tùy chỉnh chỉ cho TUI | `registerEntryRenderer`, `appendEntry` |
| `event-bus.ts` | Sự kiện giữa các extension | `pi.events` |
| **Metadata session** |||
| `session-name.ts` | Đặt tên session cho bộ chọn | `setSessionName`, `getSessionName` |
| `bookmark.ts` | Đánh dấu entry cho /tree | `setLabel` |
| **Khác** |||
| `inline-bash.ts` | Bash nội tuyến trong lời gọi công cụ | `on("tool_call")` |
| `bash-spawn-hook.ts` | Điều chỉnh lệnh bash, cwd và env trước khi thực thi | `createBashTool`, `spawnHook` |
| `with-deps/` | Extension với dependency npm | Cấu trúc package với `package.json` |
