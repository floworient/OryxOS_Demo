# Data Model: Memory 核心完整交付

## 1. MemoryOwner

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `key` | String | Agent 名或保留值 `__global__`；不可空；最长 64 |
| `agentScoped` | boolean | `true` 表示来自已验证 Profile；`false` 仅用于 global |

不变量：普通 owner 必须通过共享 `AgentName` 校验；`__global__` 不能被 Agent 名占用。

## 2. MemoryScope

枚举：`CORE`、`ARCHIVAL`。

- `CORE`：全量加载、不裁剪、不检索、不进入未来向量索引。
- `ARCHIVAL`：可按上下文上限裁剪，可关键词或委托检索。

## 3. MemoryEntry

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | String | 后端稳定 id；Markdown 可由 owner/scope/时间/序号派生 |
| `owner` | MemoryOwner | 必填 |
| `scope` | MemoryScope | 必填 |
| `content` | String | trim 后非空；最多 4000 字符；内部换行已折叠 |
| `createdAt` | Instant | 必填 |

排序：`createdAt ASC`，相同时间按后端稳定 id/SQLite id ASC。

## 4. MemorySnapshot

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `owner` | MemoryOwner | 必填 |
| `core` | List&lt;MemoryEntry&gt; | 完整、不可变、稳定升序 |
| `archival` | List&lt;MemoryEntry&gt; | 有界、不可变、最新窗口内升序 |
| `archivalTruncated` | boolean | 后端存在未返回 archival 时为 true |
| `backend` | String | `markdown` / `sqlite` / `mem0` |
| `capabilities` | MemoryCapabilities | 必填 |

## 5. MemoryContext

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `longTerm` | MemorySnapshot | 必填 |
| `sessionMessages` | List&lt;Message&gt; | Session 的不可变结构化快照 |

`PromptBuilder` 从 `sessionMessages` 中选择最近 `maxHistoryTurns`，不修改原 Session。

## 6. MemoryCapabilities

- `KEYWORD`：Markdown、SQLite；MemoryService 调 `recallByKeyword`。
- `DELEGATED`：Mem0；同一接口由后端执行其远端搜索语义。
- `HYBRID_BUILTIN`：只为 015 预留；本 feature 选择它时失败。

## 7. SQLite `memory_entries`

| 列 | SQL 类型 | 约束 |
| --- | --- | --- |
| `id` | INTEGER | PK, AUTOINCREMENT |
| `owner_key` | VARCHAR(64) | NOT NULL |
| `scope` | VARCHAR(16) | NOT NULL, CHECK in CORE/ARCHIVAL |
| `content` | TEXT | NOT NULL, 非空 CHECK |
| `created_at` | TIMESTAMP | NOT NULL |

索引：`idx_memory_entries_owner_scope_created(owner_key, scope, created_at, id)`。

## 8. Markdown 表示

```markdown
# MEMORY

## 核心记忆

- [2026-10-31 09:30:00] 核心内容

## 归档记忆

- [2026-10-31 09:31:00] 归档内容
```

物理文件按 owner 隔离；渲染时间使用配置 zone，解析后转 Instant。

## 9. Mem0 表示

请求标识：`agent_id = owner.key`。

metadata：

```json
{
  "scope": "ARCHIVAL",
  "created_by": "oryxos"
}
```

Mem0 id 映射为 `MemoryEntry.id`；缺少 scope/owner/内容/时间的记录视为协议错误，不猜测默认分区。

## 10. 状态转换

```text
validated input
  -> append requested
  -> backend write succeeds
  -> MemoryEntry durable
  -> next load/Prompt sees entry

validated input
  -> backend write fails or response ambiguous
  -> MemoryException
  -> tool failure; no success claim; no automatic retry
```

