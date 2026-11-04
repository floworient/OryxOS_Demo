# Contract: Memory Tools

## `save_memory`

Input schema:

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "content": { "type": "string", "minLength": 1, "maxLength": 4000 },
    "scope": { "type": "string", "enum": ["core", "archival"], "default": "archival" }
  },
  "required": ["content"]
}
```

成功结果：`ToolResult.success`，只说明保存到哪个 scope，不回显 content。失败结果使用稳定原因，不包含路径、后端响应或秘密。

以上 schema 的 4000 为默认上限；运行时正文规范化与后端共用 `oryxos.memory.entry-max-characters`。scope 的规范值仍为 core/archival，调用时兼容大小写和首尾空白，省略或空白仍写 archival。

## `recall_memory`

Input schema:

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "keyword": { "type": "string", "minLength": 1, "maxLength": 256 }
  },
  "required": ["keyword"]
}
```

成功结果：最多 20 条 archival，按时间升序，以带时间戳文本返回；无匹配返回明确空结果。

## Execution contract

- 必须通过 `ToolRegistry` + `ToolExecutor`。
- 必须出现在 `Profile.tools`。
- 每次调用一条最终审计。
- `save_memory.content` 审计只保留 bytes/SHA-256/scope。
