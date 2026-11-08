# Contract: GET /api/v1/sessions

## Request

```http
GET /api/v1/sessions
GET /api/v1/sessions?status=active
Accept: application/json
```

`status` 可选。本节唯一支持值为小写 `active`；空白或其他值返回统一校验错误。

## Success response

```json
{
  "code": "SUCCESS",
  "message": "操作成功",
  "data": [{
    "id": "5aa91b23-e48d-47b6-9b44-f1b6b797f645",
    "profileName": "mock-agent",
    "channel": "web",
    "userId": "console-user",
    "status": "ACTIVE",
    "createdAt": "2026-11-06T08:00:00Z",
    "lastActiveAt": "2026-11-06T08:01:00Z",
    "messageCount": 4
  }],
  "timestamp": "2026-11-06T08:01:01Z"
}
```

## Rules

- `data` 始终为数组；无结果为 `[]`。
- 最多 100 条；`lastActiveAt DESC, id ASC`。
- 摘要不包含 `messages`、`historyTruncated`、Tool 参数/结果或 prompt。
- 会话详情继续使用 `GET /api/v1/sessions/{id}`。

## Errors

- 不支持的 `status`: HTTP 400，既有统一 `ApiResponse` 错误信封和稳定校验业务码。
- 存储读取失败: HTTP 500 `STORAGE_ERROR`，不回显 SQL、路径或原始异常。

