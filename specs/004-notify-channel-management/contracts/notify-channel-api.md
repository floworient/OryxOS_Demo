# Notify Channel REST API

所有有响应体的结果使用：

```json
{"code":"SUCCESS","message":"success","data":{},"timestamp":"2026-10-28T00:00:00Z"}
```

## POST `/api/v1/notify-channels`

Request:

```json
{
  "name": "team-webhook",
  "type": "webhook",
  "url": "${TEAM_WEBHOOK_URL}",
  "description": "团队通知",
  "config": {}
}
```

Response: `201` + `ApiResponse<NotifyChannelView>`。重名 `409 NOTIFY_CHANNEL_CONFLICT`；非法值 `400 VALIDATION_ERROR`。

## GET `/api/v1/notify-channels`

Response: `200` + `ApiResponse<List<NotifyChannelView>>`，按 `createdAt ASC, name ASC`。

## GET `/api/v1/notify-channels/{name}`

Response: `200`；缺失 `404 NOTIFY_CHANNEL_NOT_FOUND`。

## PUT `/api/v1/notify-channels/{name}`

Request:

```json
{
  "type": "webhook",
  "url": "****_URL",
  "description": "新描述",
  "config": {}
}
```

路径名称不可变。`url` 为 null、空白或当前 view 的掩码时保留；否则替换。`config=null` 保留；Map 完整替换，但敏感键的 null/空白/原掩码保留旧值。

Response: `200`；缺失 `404`；非法值 `400`。

## DELETE `/api/v1/notify-channels/{name}`

Response: `204` 无 body；缺失 `404`。

## View example

```json
{
  "name": "team-webhook",
  "type": "webhook",
  "url": "${TEAM_WEBHOOK_URL}",
  "description": "团队通知",
  "config": {},
  "createdAt": "2026-10-28T08:00:00Z",
  "updatedAt": "2026-10-28T08:00:00Z"
}
```

实际 URL 示例只返回 `****` 加末 4 字符，绝不返回完整值。
