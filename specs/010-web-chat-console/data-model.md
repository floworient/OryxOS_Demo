# Data Model: 第一版 Web 对话工作台

**Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

本版不新增后端持久化结构。以下实体均为前端镜像类型或界面状态，字段必须与 009 已冻结的 Web API 保持一致。

## 1. 后端响应镜像

### ApiEnvelope\<T\>

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `string` | 成功固定为 `SUCCESS`，失败为业务码 |
| `message` | `string` | 后端提示，前端只用于日志/兜底，不直接展示底层详情 |
| `data` | `T \| null` | 业务数据；失败时为 `null` |
| `timestamp` | `string` | ISO-8601 时间 |

### ProfileSummary

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | `string` | Agent/Profile 名称，作为选择与会话键 |
| `description` | `string` | 展示用描述 |
| `agentName` | `string` | Agent 标识 |
| `providerName` | `string` | Provider 名称 |
| `model` | `string` | 模型名 |
| `tools` | `string[]` | 可见工具名 |
| `mcpServers` | `string[]` | MCP 服务名 |

### SystemInfo

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `applicationName` | `string` | 应用名 |
| `implementationVersion` | `string` | 实现版本 |
| `javaVersion` | `string` | 运行时 Java 版本 |
| `profileCount` | `number` | Agent 数 |
| `toolCount` | `number` | Tool 数 |
| `providers` | `string[]` | 已配置 Provider 名称 |

### SessionView

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `string` | 会话 ID |
| `channel` | `string` | 固定为 `web` |
| `userId` | `string` | 匿名用户标识 |
| `profileName` | `string` | 所属 Agent |
| `status` | `ACTIVE \| ARCHIVED` | 会话状态 |
| `createdAt` / `lastActiveAt` / `archivedAt` | `string \| null` | 时间 |
| `historyTruncated` | `boolean` | 是否因超过 100 条被截断 |
| `messages` | `MessageView[]` | 最近最多 100 条安全消息 |

### MessageView

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `role` | `user \| assistant \| tool` | 消息角色 |
| `text` | `string \| null` | 文本内容；`tool` 角色为 `null` |
| `toolCalls` | `ToolReferenceView[]` | assistant 发起的工具调用 |
| `toolResults` | `ToolReferenceView[]` | tool 结果引用 |

### ToolReferenceView

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `name` | `string` | 工具名，界面 chips 只展示此字段 |
| `id` | `string` | 调用 ID，仅用于去重/键 |

### MessageReplyView

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 服务端确认的会话 ID |
| `reply` | `string` | 模型回复文本 |

### SessionArchiveView

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string` | 被归档会话 ID |
| `status` | `ARCHIVED` | 固定值 |

## 2. 前端界面状态

### ConsoleState

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `bootStatus` | `loading \| ready \| offline \| empty` | 首屏加载结果 |
| `profiles` | `ProfileSummary[]` | 运行时 Agent 列表 |
| `systemInfo` | `SystemInfo \| null` | 顶部状态摘要 |
| `activeAgent` | `string \| null` | 当前选中的 Agent |
| `agents` | `Record<string, AgentConversation>` | 按 Agent 隔离的会话状态 |
| `banner` | `UiError \| null` | 全局错误或引导提示 |

### AgentConversation

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `sessionId` | `string \| null` | 本地保存的会话标识 |
| `status` | `idle \| loadingHistory \| sending \| ready \| archived \| error` | 会话状态机 |
| `messages` | `DisplayMessage[]` | 已展示消息（不含 `tool` 角色） |
| `historyTruncated` | `boolean` | 历史截断标记 |
| `draft` | `string` | 未发送输入，失败后保留 |
| `waitingSeconds` | `number` | 当前请求已等待秒数 |
| `error` | `UiError \| null` | 当前会话错误 |

### DisplayMessage

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `key` | `string` | 稳定渲染键 |
| `role` | `user \| assistant` | 仅展示两类角色 |
| `text` | `string` | 消息文本 |
| `tools` | `string[]` | 该轮工具名（仅 assistant 可能非空） |

### UiError

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `string` | 业务码或前端 `NETWORK_ERROR` / `INVALID_RESPONSE` |
| `message` | `string` | 固定中文提示 |
| `retryable` | `boolean` | 是否展示重试入口 |

### LocalSessionRef

| 键 | 值 | 失效规则 |
| --- | --- | --- |
| `oryxos.console.userId` | 随机匿名 ID | 用户清除浏览器存储后重建 |
| `oryxos.console.session.<agentName>` | 会话 ID | 会话 404、归档、用户点击新对话或归档时删除 |

## 3. 状态转换

```text
boot: loading -> ready | empty | offline
selectAgent: ready -> loadingHistory -> ready | error
send:
  ready/idle --发送--> sending --POST 成功--> loadingHistory(重建历史) -> ready
                     |--错误--> error（保留 draft、允许重试）
                     |--等待超 55s--> sending（提示即将超时）
newConversation:
  ready --DELETE(若有活动会话)--> idle -> 下一条发送时 POST /sessions
archive:
  ready --DELETE--> archived -> 清空 sessionId 与消息 -> idle
refresh:
  读取 localStore.sessionId -> GET /sessions/{id}
    -> ACTIVE: ready
    -> ARCHIVED: archived（只读展示 + 引导新对话）
    -> 404: 删除本地键 -> idle
```

## 4. 校验与展示规则

- 发送前对 `draft.trim()` 判空；为空不发起请求并给出输入区提示。
- 消息长度与 UTF-8 上限以后端校验为准；`VALIDATION_ERROR` 映射为用户可读提示。
- 只有 `role=assistant` 的 `toolCalls` 参与工具 chips 展示，`role=tool` 消息不渲染为聊天气泡。
- 同一轮多个工具调用按后端返回顺序去重后展示名称。
- `historyTruncated=true` 时在消息列表顶部展示「仅显示最近 100 条」提示。
- 归档会话只读：禁用输入框与发送，提供「开始新对话」。
- 错误提示不得拼接后端 `message` 原文；未知码使用通用提示。
