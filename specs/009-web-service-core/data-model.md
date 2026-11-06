# Data Model: Web Service 核心能力

## Session

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id` | String | 必填；每代随机 UUID；恢复时必须与持久化主键一致 |
| `channel` | String | 必填；公共连续会话固定 `web`，一次性调用固定 `web-invoke` |
| `userId` | String | 必填；Web 创建输入最多 255 字符；一次性调用使用随机内部身份 |
| `profileName` | String | 必填；创建前必须存在于 ProfileRegistry |
| `messages` | List<Message> | 内部可变、对外防御快照；只允许既有三类 Spring AI 消息 |
| `createdAt` | Instant | 必填 |
| `lastActiveAt` | Instant | 必填；不得早于 createdAt |

`Session` 只表示活动执行对象，不携带 archived 状态。新建构造器生成随机 ID；恢复构造器显式接收并验证 UUID ID。

## SessionDetails

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `id/channel/userId/profileName` | String | 必填、非空 |
| `status` | SessionStatus | `ACTIVE` 或 `ARCHIVED` |
| `createdAt/lastActiveAt` | Instant | 必填且顺序合法 |
| `archivedAt` | Instant | ACTIVE 时为空；ARCHIVED 时必填且不早于 createdAt |
| `messages` | List<Message> | 防御性不可变快照 |

只读历史允许包含归档记录；不得把 `SessionDetails` 直接转换为可执行 Session。

## Session 状态与身份

```text
NEW -> ACTIVE -> ARCHIVED
```

- `getOrCreate(channel,userId,profile)`：存在 ACTIVE 则返回；否则创建新 UUID 并持久化 ACTIVE。
- `save(session)`：只更新同 ID 的 ACTIVE 行；ARCHIVED 或不存在时不得静默 upsert/复活。
- `archive(id)`：仅 ACTIVE -> ARCHIVED，设置 archivedAt；其他状态返回 false。
- `find(id)`：只恢复 ACTIVE。
- `details(id)`：读取 ACTIVE 或 ARCHIVED。

数据库不变量：

- `session_id` 跨所有代次唯一。
- 相同 `(channel,user_id,profile_name)` 最多一条 `status='active'`。
- ARCHIVED 行数量不受该活动唯一索引限制。
- V6 迁移保留 V3-V5 的所有现有字段和值。

## Web Session Lock

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `sessionId` | String | Map key |
| `lock` | ReentrantLock | 单进程、非公平、仅 `tryLock` |

锁竞争失败不产生 Agent 调用。锁不在归档后移除，避免旧/新操作取得不同锁；新 Session 使用新 ID，因此不受旧代锁影响。

## API Views

### SessionView

`id`、`channel`、`userId`、`profileName`、`status`、`createdAt`、`lastActiveAt`、`archivedAt`、`historyTruncated`、`messages`。

历史裁剪在 View 映射时完成：`max(0, size-100)` 到末尾，保持原顺序。

### MessageView

| role | 公开字段 | 禁止字段 |
| --- | --- | --- |
| user | text | metadata/internal object |
| assistant | text、toolCalls(name,id) | arguments |
| tool | toolResults(name,id) | responseData |

### ProfileView

`name`、`description`、`agentName`、`providerName`、`model`、`tools`、`mcpServers`；所有集合使用不可变副本。不得包含 identity prompt、sourcePath 或 channel config。

### ToolView

`name`、`description`、`source`、`sourceName`。不得包含 inputSchema、可执行对象或调用结果。

### SystemInfoView

`applicationName`、`implementationVersion`、`javaVersion`、`profileCount`、`toolCount`、`providers`。Provider 名称排序，版本未知时使用稳定的 `dev`，不返回环境或路径。

## 请求值

- `CreateSessionRequest(profileName,userId)`：trim 后非空；userId 最大 255 字符。
- `MessageRequest(message)`：trim 后非空；UTF-8 字节数 `<=32768`。
- `agentName` query/path：trim 后非空，按 ProfileRegistry 精确查找。

