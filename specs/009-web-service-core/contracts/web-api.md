# Contract: Web Service Core API

所有响应 Content-Type 为 JSON，并使用：

```json
{"code":"SUCCESS","message":"success","data":{},"timestamp":"2026-11-04T00:00:00Z"}
```

错误时 `data=null`。时间使用 ISO-8601 UTC。

## Session API

### POST `/api/v1/sessions`

Request:

```json
{"profileName":"daily-tech","userId":"erp-user-42"}
```

- 201：创建新活动 Session。
- 200：复用已有活动 Session。
- 400 `VALIDATION_ERROR`；404 `AGENT_NOT_FOUND`；500 `STORAGE_ERROR`。

### POST `/api/v1/sessions/{id}/messages`

Request: `{"message":"请生成今日摘要"}`。

Response data: `{"sessionId":"...","reply":"..."}`。

- 200 成功；400 校验；404 `SESSION_NOT_FOUND`；409 `SESSION_BUSY` 或 `SESSION_CHANNEL_NOT_WRITABLE`；503 `PROVIDER_UNAVAILABLE`；504 `AGENT_TIMEOUT`。

### GET `/api/v1/sessions/{id}`

Response data 为 `SessionView`，包含状态及最多 100 条安全消息投影。活动和归档、任意渠道均可查。

- 200 成功；404 `SESSION_NOT_FOUND`；500 `STORAGE_ERROR`。

### DELETE `/api/v1/sessions/{id}`

Response data: `{"sessionId":"...","status":"ARCHIVED"}`。

- 200 成功；404 `SESSION_NOT_FOUND`；409 `SESSION_BUSY` 或 `SESSION_CHANNEL_NOT_WRITABLE`；500 `STORAGE_ERROR`。

## Agent API

### POST `/api/v1/agents/{name}/invoke`

Request: `{"message":"检查订单异常"}`。

Response data: `{"reply":"..."}`；不得包含内部 Session ID。

- 200 成功；400 校验；404 `AGENT_NOT_FOUND`；503 `PROVIDER_UNAVAILABLE`；504 `AGENT_TIMEOUT`。

## Query API

### GET `/api/v1/profiles`

返回按 name 升序的 `ProfileView[]`。

### GET `/api/v1/memory?agentName={name}`

agentName 必填。返回现有 `MemoryView`。`/api/v1/agents/{name}/memory` 为同实现兼容路由。

### GET `/api/v1/tools?agentName={name}`

agentName 可选；未提供返回全部注册摘要，提供时返回该 Profile 可见摘要。按 name 升序。

### GET `/api/v1/health`

Response data: `{"status":"UP"}`；不得调用外部 Provider。

### GET `/api/v1/info`

返回 `SystemInfoView`；providers 按名称排序，不含凭证、URL、路径或环境变量。

## Core Java 契约

```java
public interface SessionManager {
    Session getOrCreate(String channel, String userId, String profileName);
    default SessionAcquisition acquire(String channel, String userId, String profileName) {
        return new SessionAcquisition(getOrCreate(channel, userId, profileName).id(), false);
    }
    void save(Session session);
    Optional<Session> find(String sessionId);
    Optional<SessionDetails> details(String sessionId);
    boolean archive(String sessionId);
    List<SessionSummary> list();
}

public record SessionAcquisition(String sessionId, boolean created) { }

public interface ProviderCatalog {
    Set<String> configuredNames();
}
```

- `acquire` 在既有活动会话上返回 `created=false`，新建时返回 `created=true`，用于创建端点区分 200/201；默认实现复用 `getOrCreate`，SQLite 实现原子完成“查找或插入”。
- `find` 仅返回 ACTIVE；`details` 返回 ACTIVE/ARCHIVED 防御快照。
- `archive` 只在 ACTIVE -> ARCHIVED 时返回 true。
- ProviderCatalog 不返回 definition、model client 或凭证。

## 错误与安全契约

| HTTP | code | 公开 message |
| --- | --- | --- |
| 400 | VALIDATION_ERROR | 稳定字段校验信息 |
| 404 | AGENT_NOT_FOUND | Agent 不存在 |
| 404 | SESSION_NOT_FOUND | Session 不存在 |
| 409 | SESSION_BUSY | Session 正在处理其他请求 |
| 409 | SESSION_CHANNEL_NOT_WRITABLE | 该来源的 Session 不允许通过此接口修改 |
| 503 | PROVIDER_UNAVAILABLE | 模型服务暂不可用 |
| 503 | MEMORY_UNAVAILABLE | 记忆服务暂不可用 |
| 504 | AGENT_TIMEOUT | Agent 调用超时 |
| 500 | STORAGE_ERROR | 会话存储暂不可用 |
| 500 | INTERNAL_ERROR | 服务器内部错误 |

任何响应不得回显底层 exception message/cause/stack、Tool arguments/responseData、Profile prompt/path、Provider key/base URL 或数据库地址。

