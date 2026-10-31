# 记忆系统

OryxOS 为 Agent 提供三层记忆。每一层有不同的作用域、生命周期和存储后端。`MemoryService` 是统一的门面，`ReActLoop` 和 `PromptBuilder` 都通过它来访问记忆，不直接接触各层的实现。

## 三层记忆

| 层级 | 存储 | 作用域 | 核心阶段 |
| --- | --- | --- | --- |
| 会话记忆 | SQLite `sessions.messages_json` | 仅限当前对话 | 是 |
| 长期记忆 | Markdown（默认）、SQLite 或自托管 Mem0 | 跨会话持久化，按 Agent 隔离 | 是 |
| 情景记忆 | 向量数据库（规划中） | 跨历史会话的语义检索 | 否 |

**会话记忆** 是对话历史——当前会话中积累的消息列表。以 JSON 格式序列化存入 SQLite。`PromptBuilder` 在每次构建 Prompt 时注入最近 `max_history_turns` 轮。它自动积累，Agent 不需要显式写入。

**长期记忆** 由所选后端持久化，Agent 通过 `save_memory` 显式写入、通过 `recall_memory` 召回。跨会话持久化，每次构建 Prompt 都重新读取。默认 Markdown 档使用带时间戳的文件；SQLite 和 Mem0 保持同一上层契约。

**情景记忆** 规划在扩展阶段实现。它会将历史对话建索引到向量数据库，在构建 Prompt 时检索语义相关的历史片段。

## 长期记忆按 Agent 隔离

长期记忆按经过校验的 `Profile.name` 隔离。Markdown 档把每个 Agent 的记忆放在 `.oryxos/agents/<name>/MEMORY.md`，与 `AGENT.md` 同目录；保留的 global owner 使用 `.oryxos/memory/MEMORY.md`。非法 Agent 名或不安全路径会 fail-loud，不会静默回退到全局文件。

### 时间戳与两个区块

每条记录都写成一行**带时间戳**的内容：`- [yyyy-MM-dd HH:mm:ss] <内容>`。一个 `MEMORY.md` 文件按两个区块组织：

- `## 核心记忆`（**core**）——始终完整注入，从不截断
- `## 归档记忆`（**archival**）——Agent 用 `save_memory` 主动沉淀的条目；量大时超限截断

`save_memory` 写入其中一个区块（`core` 或 `archival`）。recall 和截断只作用于**归档区**；核心区物理隔离，始终完整保留。

### 不再自动写触发足迹

OryxOS **不会**在每次触发后自动把「用户问题 ⇒ 模型回答」写入归档记忆。失败回答一旦带上问句原文，语义召回会把它排到最前，轻量模型容易照抄，形成失败自我固化。执行留痕已在 `tool_invocations` / `agent_executions`；要沉淀的事实由 Agent 显式调用 `save_memory`。

## 长期记忆的操作

`LongTermMemoryStore`（默认后端：`MarkdownMemoryStore`）提供这些操作：

| 方法 | 行为 |
| --- | --- |
| `append(content, scope)` | 向核心区或归档区追加一条带时间戳的记录 |
| `load()` | 返回两个区块；归档区超限则截断，核心区完整返回 |
| `recallByKeyword(keyword)` | 返回归档区中包含该关键词的行 |
| 有界视图 | 归档区超过 4000 字符时，只返回可完整容纳的最新条目，不改写本体 |

4000 字符的限制防止归档区占用过多 context window。超限时上下文视图排除最旧条目，底层本体不被截断或改写；核心区永远完整返回。

这个接口在设计上为向量检索预留了升级路径。`recallByKeyword` 可以替换为语义检索实现，不需要改动 `MemoryService` 或任何调用方。

## 可插拔后端

长期记忆后端通过 `oryxos.memory.backend` 配置项可插拔——换后端只换注入的 `LongTermMemoryStore`，`MemoryService` 门面签名与所有调用方保持不变。

| `oryxos.memory.backend` | Store | 说明 |
| --- | --- | --- |
| `markdown`（默认） | `MarkdownMemoryStore` | 零依赖、人可读、git 可跟踪的 Markdown 文件 |
| `sqlite` | `JpaMemoryStore` | 记忆按条存入 Flyway V5 创建的 `memory_entries` 表；复用既有 SQLite |
| `mem0` | `Mem0MemoryStore` | 委托给自托管 Mem0 OSS；启动时只读校验 `/openapi.json` 与分区能力 |

Mem0 写入固定使用 `infer=false`，并显式携带 owner 与 `metadata.scope`。非法 backend、非正上限、Mem0 缺地址/凭证或分区能力不满足都会阻止启动。内置混合检索能力 `HYBRID_BUILTIN` 只为 015 预留，当前版本会 fail-fast，不包含 embedding、RRF 或异步向量索引实现。

```yaml
oryxos:
  memory:
    backend: mem0 # markdown | sqlite | mem0
    archival-max-characters: 4000
    recall-max-results: 20
    entry-max-characters: 4000
    zone: ${TZ:Asia/Shanghai}
    mem0:
      base-url: ${MEM0_BASE_URL:}
      api-key: ${MEM0_API_KEY:}
      connect-timeout: 3s
      request-timeout: 10s
      max-response-bytes: 1048576
```

## save_memory 和 recall_memory 工具

Agent 通过两个内置工具与长期记忆交互，不直接写 `MEMORY.md`。

**`save_memory`** — 向当前 Agent 的 `MEMORY.md` 追加一段文本，写入 `core` 或 `archival` 区块。Agent 自己决定保存什么、写哪个区、何时保存。核心阶段没有自动摘要。

```text
工具: save_memory
输入: { "content": "用户偏好简洁的回复，不用列表。", "scope": "archival" }
效果: 向该 Agent 的 MEMORY.md 归档区追加带时间戳的条目
```

**`recall_memory`** — 按关键词检索归档区，返回匹配的行。在保存之前，Agent 可以先检索确认自己是否已经知道某件事，避免重复记录。

```text
工具: recall_memory
输入:  { "keyword": "偏好" }
输出: "- [2026-07-22 09:30:00] 用户偏好简洁的回复，不用列表。"
```

两个工具都经过 `ToolExecutor` 处理，和其他工具调用一样记录在 `tool_invocations` 里。

## 通过 REST 读取记忆

管理台通过以下端点读取某个 Agent 的长期记忆：

```text
GET /api/v1/agents/{name}/memory
```

响应使用统一的 `{ code, message, data, timestamp }` 信封，返回 owner、backend、capabilities、完整 core 和与 Prompt 相同的有界 archival 视图，不返回原始后端响应或无上限导出。未知 Agent 返回 404，后端不可用返回脱敏的 503。

## 记忆如何注入到 Prompt

`PromptBuilder` 在每次循环迭代时调用 `MemoryService.load(session, profile)`，将结果插入系统提示词的第二段，紧跟在 Agent 身份和 Bootstrap 文件之后：

```text
[1] 系统提示词（AGENT.md 正文 + bootstrap）
[2] MEMORY.md  ← 在这里注入：核心区全量 + 归档区截断至 4000 字符
[3] 对话历史（最近 N 轮）
[4] 可用工具（JSON Schema）
```

记忆块始终存在，即使 `MEMORY.md` 为空。

## MEMORY.md 与 USER.md 的区别

这两个文件看起来相似，但服务于相反的目的：

| | `MEMORY.md` | `USER.md` |
| --- | --- | --- |
| 谁来写 | Agent（通过 `save_memory`） | 用户（手动编写） |
| OryxOS 的行为 | 读 + 写 | 只读 |
| 内容 | Agent 显式保存的偏好、事实和长期上下文 | 用户的初始偏好和背景信息 |
| 重置 | 从不（只追加；仅归档区过长时截断） | 从不（由用户自行管理） |
| 位置 | `.oryxos/agents/<name>/MEMORY.md`（按 Agent 隔离） | `.oryxos/USER.md` |

`USER.md` 属于 Bootstrap 组，在 Prompt 的第 [1] 段注入。`MEMORY.md` 在第 [2] 段注入。两者对 Agent 都可见，但只有 `MEMORY.md` 可以被 Agent 修改。

## 规划中的功能

- **情景记忆** — 将完整会话记录建索引到向量数据库；构建 Prompt 时检索最相关的 top-k 片段
- **015 混合检索** — 在 archival 本体上增加关键词、语义与时间融合；当前版本仅预留能力声明
- **记忆摘要** — 截断前自动压缩旧条目，用更少的字符保留更多信息
