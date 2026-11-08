# Contract: 控制台最近会话

## Placement

继续使用 `/console/`。顶级导航仍为现有七项；“会话”区域同时提供 Agent 新对话入口、最近会话发现和当前会话详情。

## States

| State | Required behavior |
| --- | --- |
| Loading | 显示正在加载最近会话，不阻塞已打开的对话草稿 |
| Empty | 明确显示暂无会话，仍允许选择 Agent 开始新对话 |
| Error | 显示脱敏错误和重试按钮，保留当前对话状态 |
| Ready | 按服务端顺序显示最近会话摘要 |

## Summary item

每项显示 Agent、渠道、状态、消息数和最后活跃时间；用户标识可用于辅助区分，但不得与凭证或通知地址混排。

## Selection

- 点击摘要调用既有 `GET /api/v1/sessions/{id}`。
- 活动 `web` 会话可恢复到对应 Agent 对话状态。
- 归档或非 `web` 会话只读展示；发送与归档控件不得错误启用。
- 新消息成功或归档后刷新最近会话列表。

## Accessibility

- 列表使用语义化列表和按钮。
- 当前选中项通过 `aria-current` 或等价状态暴露。
- Loading/error 文本使用现有 `role=status/alert` 约定。

