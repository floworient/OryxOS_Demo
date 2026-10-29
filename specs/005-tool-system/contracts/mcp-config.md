# `.oryxos/mcp_servers.yaml` Contract

```yaml
servers:
  - name: local-example
    transport: stdio
    command: node
    args: ["server.js"]
    request_timeout: 30
    env:
      API_TOKEN: ${EXAMPLE_TOKEN}

  - name: remote-example
    transport: sse
    url: https://mcp.example.com/sse
    request_timeout: 30
    headers:
      Authorization: Bearer ${EXAMPLE_TOKEN}
```

## Validation

- YAML 使用安全构造器，拒绝重复 key、别名滥用、未知字段和过深嵌套。
- `servers` 缺失等价于空列表；文件不存在表示未配置 MCP。
- server name 全局唯一；transport 仅 `stdio` / `sse`。
- `request_timeout` 为整数秒，1–300，默认 30。
- stdio 只允许 `command`、`args`、`env`；command 是 Shell 白名单 alias。
- sse 只允许 `url`、`headers`；URL 必须通过 HTTP Sandbox。
- env/header 值支持 `${ENV_VAR}`；引用的变量缺失或空白时启动失败。
- 配置错误与授权拒绝 fail-fast；纯连接/远端故障只跳过单个 server。
- 解析后的 env/header 不得出现在日志、异常、审计或 CLI 列表。

## Runtime

- 初始化调用一次；成功后调用 `tools/list` 并注册全部合法工具。
- 全局工具重名导致启动失败。
- `tools/call` 参数为原始 JSON object，一次调用最多发送一次，不由 Adapter 重试。
- 应用关闭时关闭 client；stdio transport 同时回收子进程。

