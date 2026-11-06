# REST API

OryxOS Web Service 在 `/api/v1` 下提供 JSON REST API。当前核心版本面向受信任的企业内网集成，不包含认证、授权或租户隔离。

```bash
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar serve --port 8080
```

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/api-docs`

## 统一响应

成功和错误均使用同一信封：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {},
  "timestamp": "2026-11-06T08:30:00Z"
}
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `code` | string | 成功为 `SUCCESS`；失败为稳定的错误码 |
| `message` | string | 面向调用方的简短说明，不包含内部异常或密钥 |
| `data` | any/null | 业务数据；失败时为 `null` |
| `timestamp` | string | ISO-8601 UTC 时间 |

消息正文按 UTF-8 编码后不得超过 32768 字节。同步 Agent 调用的截止时间为 60 秒。

## 核心端点

### 创建或复用连续会话

`POST /api/v1/sessions`

```json
{
  "profileName": "assistant",
  "userId": "customer-42"
}
```

同一 `profileName`、`userId` 和 `web` 渠道仅复用当前活动会话。新建返回 `201 Created`，复用返回 `200 OK`；两者都设置 `Location: /api/v1/sessions/{id}`。会话 ID 是随机 UUID。归档后再次调用会创建新一代 UUID，不会复活旧会话。

### 向连续会话发送消息

`POST /api/v1/sessions/{id}/messages`

```json
{ "message": "总结今天的工作" }
```

返回 `sessionId` 和 `reply`。同一 JVM 中，同一会话正在执行其他消息或归档操作时返回 `409 SESSION_BUSY`。非 `web` 渠道会话不可通过此端点写入。

### 查询会话

`GET /api/v1/sessions/{id}`

返回会话身份、渠道、状态、时间戳及安全投影后的消息历史。最多返回最近 100 条消息，并用 `historyTruncated` 指示是否截断；工具参数和工具结果正文不会暴露。

### 归档会话

`DELETE /api/v1/sessions/{id}`

仅归档活动的 `web` 会话，不物理删除数据。成功后返回 `ARCHIVED`；已归档或不存在的 ID 返回 `404 SESSION_NOT_FOUND`。

### 无状态调用 Agent

`POST /api/v1/agents/{name}/invoke`

```json
{ "message": "生成一份日报" }
```

每次调用都会创建独立的内部 `web-invoke` 会话以保留审计，但响应只返回 `reply`，不暴露内部会话 ID，也不复用上一次调用。

### 查询 Profile

`GET /api/v1/profiles`

返回已注册 Profile 的名称、描述、Agent、Provider、模型及已配置工具/MCP 元数据，不返回密钥。

### 查询 Agent 记忆

`GET /api/v1/memory?agentName={name}`

返回该 Agent 的 core/archival 分区快照和后端能力。兼容旧调用方的 `GET /api/v1/agents/{name}/memory` 暂时保留，但不是新的主路径。

### 查询工具

- `GET /api/v1/tools`
- `GET /api/v1/tools?agentName={name}`

不带参数时返回全局注册工具元数据；指定 Agent 时按该 Profile 的可见性过滤。接口不返回可执行代码、参数值或凭证。

### 健康状态

`GET /api/v1/health`

进程可响应时返回 `{ "status": "UP" }`。该端点不是对外部 Provider、数据库容量或第三方网络的深度探针。

### 系统信息

`GET /api/v1/info`

返回应用版本、Java 版本、Profile/Tool 数量及已注册 Provider 名称，不返回 Provider 配置和密钥。

## 通知渠道

通知渠道通过 `/api/v1/notify-channels` 动态管理并持久化到 SQLite 的 `notify_channels` 表，当前仅支持 `webhook` 出站适配器。返回的 `url` 和敏感 `config` 值以 `****` 加末四位掩码，`${ENV_VAR}` 占位符原样可见；更新时传 `null`、空白或当前掩码表示保留原值。本版本不声明数据库字段加密，生产环境建议使用 `${ENV_VAR}`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/v1/notify-channels` | 创建渠道，返回 `201` 和 `Location` |
| GET | `/api/v1/notify-channels` | 列出全部渠道 |
| GET | `/api/v1/notify-channels/{name}` | 查询单个渠道 |
| PUT | `/api/v1/notify-channels/{name}` | 更新渠道，掩码值表示保留原配置 |
| DELETE | `/api/v1/notify-channels/{name}` | 删除渠道，返回 `204` |

请求字段为 `name`、`type`、`url`、`description`、`config`；响应额外包含 `createdAt` 和 `updatedAt`。错误码为 `NOTIFY_CHANNEL_NOT_FOUND`（404）与 `NOTIFY_CHANNEL_CONFLICT`（409）。

## 状态码与错误码

| HTTP | 常见 `code` | 含义 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 请求字段缺失、空白、超长或格式错误 |
| 404 | `AGENT_NOT_FOUND`, `SESSION_NOT_FOUND` | Agent 或会话不存在 |
| 409 | `SESSION_BUSY`, `SESSION_CHANNEL_NOT_WRITABLE` | 会话冲突或渠道不允许 Web 写入 |
| 500 | `STORAGE_ERROR`, `INTERNAL_ERROR` | 存储或未预期的内部错误 |
| 503 | `PROVIDER_UNAVAILABLE`, `MEMORY_UNAVAILABLE` | 模型或记忆服务暂不可用 |
| 504 | `AGENT_TIMEOUT` | 同步 Agent 调用超过 60 秒 |

## 当前限制与部署边界

- `/api/**` 当前允许跨域请求，且没有认证、RBAC、限流或多租户隔离；只能部署在经过网关和网络策略保护的可信内网。
- 会话互斥锁和“创建或复用”活动会话语义仅保证单 JVM 行为，不是多实例分布式锁。
- 60 秒超时是调用方等待上限和尽力取消，不会回滚此前已经发生的工具副作用或审计记录。
- 当前没有 SSE、WebSocket、入站 Webhook、动态 Agent 管理或调度管理 REST API。
- 自动化测试使用可控 Provider、Tool 和本地 SQLite；真实模型凭证及公网依赖需要在目标部署环境单独验证。
