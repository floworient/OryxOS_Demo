# Contract: 工作台对后端 API 的消费

**Version**: 1.0 | **Date**: 2026-11-06 | **Backend**: 009 已冻结 `/api/v1` Web API

工作台 MUST 只调用下列端点，不新增、不修改后端契约。所有响应使用 `ApiResponse` 信封：`{code, message, data, timestamp}`。

## 1. 读取 Agent 与状态

### GET `/api/v1/profiles`

- 响应 `data`: `ProfileSummary[]`，按 `name` 升序。
- 用途：左栏 Agent 列表；`name` 作为会话本地键。
- 约束：不得内置示例 Agent 覆盖或补充响应。

### GET `/api/v1/health`

- 响应 `data`: `{ "status": "UP" }`。
- 用途：顶部可用状态；失败不阻塞 Agent 列表展示，但需显示离线提示。

### GET `/api/v1/info`

- 响应 `data`: `SystemInfo`，含 `profileCount`、`toolCount`、`providers[]`。
- 用途：顶部「Agents N · Tools N · Provider X」。
- 约束：只展示，不提供编辑入口；不展示凭证或地址。

首屏 SHOULD 并行请求三个端点；`profiles` 成功而 `health`/`info` 失败时，仍展示 Agent 列表并标记状态未知。

## 2. 会话与消息

### POST `/api/v1/sessions`

Request:

```json
{ "profileName": "daily-weather", "userId": "anon-xxxx" }
```

- `201`: 新建活动会话；`200`: 复用已有活动会话。
- 响应 `data`: `SessionView`。
- 用途：某个 Agent 首次发送前的会话建立。
- 注意：仅清除本地会话标识不会创建新会话；「新对话」必须先归档当前活动会话。

### POST `/api/v1/sessions/{id}/messages`

Request:

```json
{ "message": "今天杭州天气怎么样？" }
```

- 响应 `data`: `{ "sessionId": "...", "reply": "..." }`。
- 用途：发送用户消息；同步等待，最长约 60 秒。
- 约束：成功后 MUST 调用 `GET /sessions/{id}` 重建消息与工具展示，不得只拼接 `reply` 文本。

### GET `/api/v1/sessions/{id}`

- 响应 `data`: `SessionView`，含 `status`、`historyTruncated` 与最多 100 条 `messages`。
- 用途：刷新恢复、切换 Agent、发送后重建、工具 chips 数据源。
- 约束：只展示 `user`/`assistant` 文本与 `assistant.toolCalls[].name`；不展示 `tool` 消息、arguments 或 result。

### DELETE `/api/v1/sessions/{id}`

- 响应 `data`: `{ "sessionId": "...", "status": "ARCHIVED" }`。
- 用途：归档当前会话；「新对话」与「归档」均使用。
- 约束：归档后清除本地会话标识；旧历史仍由后端保留。

## 3. 错误码映射

| HTTP | `code` | 界面提示 | 可重试 |
| --- | --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 消息不合法，请修改后重试 | 是 |
| 404 | `AGENT_NOT_FOUND` | Agent 已不存在，请刷新 Agent 列表 | 是（刷新） |
| 404 | `SESSION_NOT_FOUND` | 会话已失效，已为你开始新对话 | 是 |
| 409 | `SESSION_BUSY` | 上一轮还在处理中，请稍候 | 是 |
| 409 | `SESSION_CHANNEL_NOT_WRITABLE` | 当前会话不允许写入，请开始新对话 | 是 |
| 503 | `PROVIDER_UNAVAILABLE` | 模型服务不可用，请检查 Provider 配置 | 是 |
| 503 | `MEMORY_UNAVAILABLE` | 记忆服务不可用，请稍后重试 | 是 |
| 504 | `AGENT_TIMEOUT` | 本轮超过 60 秒已取消，请重试 | 是 |
| 500 | `STORAGE_ERROR` | 会话存储不可用，请稍后重试 | 是 |
| 500 | `INTERNAL_ERROR` | 服务器内部错误，请稍后重试 | 是 |
| — | `NETWORK_ERROR` | 无法连接运行时，请确认后端已启动 | 是 |
| — | `INVALID_RESPONSE` | 运行时返回了无法识别的响应 | 是 |

未列出的业务码 MUST 使用通用提示，不得直接展示后端 `message` 原文。

## 4. 请求约束

- 请求路径使用绝对 `/api/v1/...`，开发时由 Vite 代理，生产时由同源静态资源旁的后端提供。
- 请求头 `Content-Type: application/json`；不设置认证头（本版无认证）。
- 前端不发送 `channel`；后端固定 `web`。
- `userId` 由浏览器首次生成并持久化，MUST NOT 使用真实姓名、邮箱或其他个人信息。
- 不在 URL、请求体、日志或本地存储中携带模型密钥。
