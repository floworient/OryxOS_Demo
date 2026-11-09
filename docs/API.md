当前 [Swagger](http://127.0.0.1:8080/swagger-ui/index.html) 共公开 **27 个端点**。下面列出每个端点的成功状态码，以及响应中 `data` 的内容。

除通知渠道删除返回 `204` 空响应外，成功响应统一为 `{ "code": "SUCCESS", "message": "...", "data": ..., "timestamp": "..." }`。

## 定时任务

| 端点                                                    | 状态码 | `data`                                             |
| ------------------------------------------------------- | ------ | -------------------------------------------------- |
| `GET /api/v2/schedules`                                 | 200    | 活动任务列表 `Schedule[]`                          |
| `GET /api/v2/schedules/{scheduleId}/executions`         | 200    | 执行历史 `Execution[]`，最新在前，已退役任务也可查 |
| `POST /api/v2/schedules/{scheduleId}/run`               | 200    | 本次补跑记录 `Execution`                           |
| `PUT /api/v2/schedules/{scheduleId}`                    | 200    | 更新启停状态后的 `Schedule`                        |
| `POST /api/v2/agents/{profileName}/schedules/{key}/run` | 200    | 按 Agent 和任务键补跑，返回本次 `Execution`        |
| `GET /api/v1/schedules`                                 | 200    | 活动任务列表 `Schedule[]`                          |
| `GET /api/v1/schedules/{id}/executions`                 | 200    | 执行历史 `Execution[]`                             |
| `POST /api/v1/schedules/{id}/run`                       | 200    | 本次补跑记录 `Execution`                           |
| `PUT /api/v1/schedules/{id}`                            | 200    | 更新启停状态后的 `Schedule`                        |

- **`Schedule`** 含 `scheduleId`、`profileName`、`scheduleKey`、`displayName`、`cron`、`zone`、`enabled`、`nextRunAt`、`lastRunAt`、`lastStatus`、`runCount`。
- **`Execution`** 含 `scheduleId`、`sessionId`、`startedAt`、`success`、`errorMessage`、`durationMs`。补跑返回 `200` 后仍需检查 `data.success`，它表示任务本身是否执行成功。
- v1 的 `{id}` 填配置中的 `scheduleKey`。v2 的 `{scheduleId}` 填列表返回的稳定 ID。启停请求体为 `{"enabled": false}` 或 `{"enabled": true}`。

## Agent 与会话

| 端点                                  | 状态码    | `data`                                                       |
| ------------------------------------- | --------- | ------------------------------------------------------------ |
| `POST /api/v1/agents/{name}/invoke`   | 200       | `{ reply }`，Agent 的一次性回复                              |
| `GET /api/v1/sessions`                | 200       | 会话摘要列表。每项含 `id`、`profileName`、`channel`、`userId`、`status`、`createdAt`、`lastActiveAt`、`messageCount` |
| `POST /api/v1/sessions`               | 200 / 201 | 会话详情 `Session`                                           |
| `GET /api/v1/sessions/{id}`           | 200       | 会话详情 `Session`，含消息历史                               |
| `POST /api/v1/sessions/{id}/messages` | 200       | `{ sessionId, reply }`                                       |
| `DELETE /api/v1/sessions/{id}`        | 200       | `{ sessionId, status }`，归档后 `status` 为 `ARCHIVED`       |

**`Session`** 含 `id`、`channel`、`userId`、`profileName`、`status`、`createdAt`、`lastActiveAt`、`archivedAt`、`historyTruncated`、`messages`。每条消息含 `role`、`text`、`toolCalls`、`toolResults`，工具引用含 `name` 和 `id`。会话状态为 `ACTIVE` 或 `ARCHIVED`。

## 通知渠道

| 端点                                    | 状态码 | `data`                     |
| --------------------------------------- | ------ | -------------------------- |
| `GET /api/v1/notify-channels`           | 200    | 渠道列表 `NotifyChannel[]` |
| `GET /api/v1/notify-channels/{name}`    | 200    | 指定渠道 `NotifyChannel`   |
| `POST /api/v1/notify-channels`          | 201    | 新建的 `NotifyChannel`     |
| `PUT /api/v1/notify-channels/{name}`    | 200    | 更新后的 `NotifyChannel`   |
| `DELETE /api/v1/notify-channels/{name}` | 204    | 空响应，无 `data`          |

**`NotifyChannel`** 含 `name`、`type`、`url`、`description`、`config`、`createdAt`、`updatedAt`。响应中的敏感配置经脱敏处理。

## 记忆、工具与系统

| 端点                                  | 状态码 | `data`                                                       |
| ------------------------------------- | ------ | ------------------------------------------------------------ |
| `GET /api/v1/memory?agentName={name}` | 200    | `Memory`，指定 Agent 的核心记忆和归档记忆                    |
| `GET /api/v1/agents/{name}/memory`    | 200    | 同样返回 `Memory`，通过路径指定 Agent                        |
| `GET /api/v1/profiles`                | 200    | Agent 列表。每项含 `name`、`description`、`agentName`、`providerName`、`model`、`tools`、`mcpServers` |
| `GET /api/v1/tools`                   | 200    | 工具列表。每项含 `name`、`description`、`source`、`sourceName`。可用 `agentName` 筛选 |
| `GET /api/v1/sandbox/allowlist`       | 200    | `{ httpDomains, fileRootCount, commandAliases }`             |
| `GET /api/v1/info`                    | 200    | `{ applicationName, implementationVersion, javaVersion, profileCount, toolCount, providers }` |
| `GET /api/v1/health`                  | 200    | `{ status }`，服务健康状态                                   |

**`Memory`** 含 `owner`、`backend`、`capabilities`、`archivalTruncated`、`core`、`archival`。两个记忆数组中的条目均含 `id`、`content`、`createdAt`。

刚才实测 `GET /api/v2/schedules` 返回 `200`，但当前工作区的任务列表是 `[]`。如果要在 Swagger 里试补跑或启停，需要先有已登记的任务，再从列表复制它的 `scheduleId`。 [本机 OpenAPI 定义](http://127.0.0.1:8080/api-docs)