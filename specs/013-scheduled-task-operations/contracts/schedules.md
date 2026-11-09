# 调度契约

## Core

- 保留 `ScheduledTaskStore.reconcile(ScheduledTaskDefinition)`、`retire(ScheduleId)`、`find(ScheduleId)`、`recordExecution(TaskExecution)`、`executions(ScheduleId)`。增加 `boolean isEnabled(ScheduleId)`、`Optional<ScheduledTaskView> setEnabled(ScheduleId, boolean)`、`List<ScheduledTaskView> list()`、`boolean exists(ScheduleId)`、`List<TaskExecutionView> executionViews(ScheduleId)` 与 `void updateNextRun(ScheduleId, Instant)`。后两条查询用于区分已退役任务与未知 ID，以及保留既有内部执行记录接口。
- 增加 `AgentScheduler.runNow(ScheduleId)`，返回 `Optional<TaskExecutionView>`。实际执行完成返回记录，重叠跳过为空，未知或退役任务抛 `NoSuchElementException`，等待超时抛 `TimeoutException`。现有构造、`registerAll()`、`registerProfile(Profile)`、`unregisterProfile(String)` 保留。
- `ScheduledTaskView` 提供 `scheduleId, profileName, scheduleKey, displayName, cron, zone, enabled, nextRunAt, lastRunAt, lastStatus, runCount`。`TaskExecutionView` 提供 `scheduleId, sessionId, startedAt, success, errorMessage, durationMs`。

## HTTP

所有响应使用现有 `ApiResponse`。任务字段采用上述 lower camel case。HTTP `scheduleId` 为字符串，时间为 ISO-8601，未有值的字段为 null。PUT 请求为 `{"enabled": false}`，enabled 必须为布尔值。

| 路径 | 结果 |
|---|---|
| `GET /api/v2/schedules` | 200，活动任务列表，按 Agent 名和配置键稳定排序 |
| `GET /api/v2/schedules/{scheduleId}/executions` | 200，按最新在前排列的执行历史，已退役任务也可查 |
| `POST /api/v2/schedules/{scheduleId}/run` | 200，执行记录。执行失败时记录内 `success=false` |
| `PUT /api/v2/schedules/{scheduleId}` | 200，更新后的任务状态，重复设置同值有效 |
| `POST /api/v2/agents/{profileName}/schedules/{key}/run` | 200，按 Agent 与配置键定位，同稳定 ID 补跑语义 |

旧版保留 `GET /api/v1/schedules` 和按配置键的 `GET /api/v1/schedules/{id}/executions`、`POST /api/v1/schedules/{id}/run`、`PUT /api/v1/schedules/{id}`。列表返回相同视图，旧版单任务路由只解析活动任务。匹配多条返回 409 `SCHEDULE_KEY_CONFLICT`。

格式无效返回 400 `VALIDATION_ERROR`。未知任务及对已退役任务的写操作返回 404 `SCHEDULE_NOT_FOUND`。补跑重叠返回 409 `SCHEDULE_BUSY`，超时返回 504 `AGENT_TIMEOUT` 并登记一次失败历史。持久化异常按现有安全错误响应处理。
