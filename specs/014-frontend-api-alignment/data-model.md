# Data Model: 新增前端类型定义

## NotifyChannel

对应 `GET/POST /api/v1/notify-channels` 响应。

| 字段 | 类型 | 说明 |
|------|------|------|
| name | string | 渠道唯一标识 |
| type | string | 渠道类型（如 webhook、email） |
| url | string \| null | 目标地址（接口层已脱敏） |
| description | string \| null | 渠道描述 |
| config | object \| null | 配置对象（已脱敏） |
| createdAt | string | 创建时间 |
| updatedAt | string | 更新时间 |

### TypeScript 定义

```typescript
export interface NotifyChannelSummary {
  name: string
  type: string
  url: string | null
  description: string | null
  config: Record<string, unknown> | null
  createdAt: string
  updatedAt: string
}

export interface NotifyChannelCreateInput {
  name: string
  type: string
  url?: string
  description?: string
  config?: Record<string, unknown>
}

export interface NotifyChannelUpdateInput {
  type?: string
  url?: string
  description?: string
  config?: Record<string, unknown>
}

export interface NotifyChannelDeleteView {
  channelName: string
  status: 'DELETED'
}
```

## Execution History Entry

对应 `GET /api/v2/schedules/{scheduleId}/executions` 列表项，复用现有 `ScheduledExecution` 类型。无需新增定义。

## Agent Invoke Response

对应 `POST /api/v1/agents/{name}/invoke`，复用现有 `MessageReplyView`：

```typescript
// Already exists in types.ts line 65
export interface MessageReplyView {
  sessionId: string
  reply: string
}
```

**注意**: `POST /api/v1/agents/{name}/invoke` 单次调用返回 `{ reply }`，可能不需要 `sessionId`。需确认返回结构。如果仅返回 `{ reply: string }`，应新增独立类型 `AgentSingleInvokeResponse`。

## Schedule Run by Key

对应 `POST /api/v1/agents/{name}/schedules/{key}/run`，复用现有 `ScheduledExecution` 类型。
