# Contract: Agent Memory API

## GET `/api/v1/agents/{name}/memory`

成功 `200`：

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {
    "owner": "daily-tech",
    "backend": "markdown",
    "capabilities": "KEYWORD",
    "archivalTruncated": false,
    "core": [
      { "id": "...", "content": "核心事实", "createdAt": "2026-10-31T01:30:00Z" }
    ],
    "archival": [
      { "id": "...", "content": "用户关注 AI", "createdAt": "2026-10-31T01:31:00Z" }
    ]
  },
  "timestamp": "2026-10-31T01:32:00Z"
}
```

错误：

| HTTP | code | 条件 |
| --- | --- | --- |
| 404 | `AGENT_NOT_FOUND` | ProfileRegistry 无此 Agent |
| 503 | `MEMORY_UNAVAILABLE` | 后端读取或协议失败 |

查询不得创建目录、文件、数据库条目或远端记忆。

