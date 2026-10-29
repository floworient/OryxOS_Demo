# `notify` Tool 契约

## Tool metadata

- Name: `notify`
- Description: `把纯文本消息推送到按名称注册的通知渠道`
- Execution: 只允许通过现有 `ToolExecutor`

## Input schema

```json
{
  "type": "object",
  "additionalProperties": false,
  "properties": {
    "content": {
      "type": "string",
      "minLength": 1,
      "maxLength": 32768,
      "description": "要推送的纯文本内容"
    },
    "channel": {
      "type": "string",
      "maxLength": 64,
      "description": "全局通知渠道名称；省略或空白时使用最早创建的渠道"
    }
  },
  "required": ["content"]
}
```

## Result

- 成功：`ToolResult(true, "已推送", null, false)`。
- 失败：`ToolResult(false, "", <安全中文原因>, false)`。
- 所有 Notify 失败均不可重试，避免 `ToolExecutor` 自动重放产生重复通知。

## Required ordering

```text
parse input
  -> resolve channel/default
  -> resolve ${ENV_VAR}
  -> Sandbox.enforce(HTTP_REQUEST, uri)
  -> NotifyAdapterRegistry.require(channel.type)
  -> adapter.send(target, content)
  -> ToolExecutor audit
```

Sandbox 失败时 `adapter.send` 必须零调用。Adapter 失败由 `NotifyTools` 转为安全 ToolResult，再由 `ToolExecutor` 记录一次失败。
