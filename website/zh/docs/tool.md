# 工具体系

OryxOS 把内置工具、Java `@Tool` 方法和 MCP 工具统一包装为 `OryxTool`，注册到同一个 `ToolRegistry`。`PromptBuilder` 与 `ToolExecutor` 使用同一套可见性规则；Spring AI 的自动工具执行保持关闭，真实调用只经过 `ToolExecutor`，并写入 `tool_invocations`。

```java
interface OryxTool {
    String getName();
    String getDescription();
    String getInputSchema();
    ToolResult execute(String jsonInput);
}
```

工具名全局唯一。名称重复、描述为空或 input schema 不是 JSON object 时，注册会明确失败，不做静默覆盖。

## 当前内置工具

本次 Tool 交付新增 11 个内置工具；已有 `notify` 也通过同一 Registry 注册。Memory 工具不在本次范围内，不能把尚未实现的 `save_memory` / `recall_memory` 当作可用能力。

| 工具 | 行为与边界 |
| --- | --- |
| `read_file` | 读取当前 Agent、管理员批准普通目录或已绑定公共 Skill 的 UTF-8 文本 |
| `write_file` | 在当前 Agent 或管理员批准普通目录原子写入 UTF-8 文本 |
| `list_dir` | 稳定列出批准目录，最多 200 项 |
| `edit_file` | 仅当 `old_text` 恰好出现一次时替换 |
| `grep` | RE2/J 线性时间正则搜索，受文件数、字节数和 200 条结果上限约束；不支持反向引用和环视 |
| `glob` | glob 搜索并稳定排序，最多 200 条 |
| `shell` | 以批准的 executable alias 和原始 argv 执行，限制工作目录、超时和输出 |
| `http_get` | 向白名单域名发送一次 GET，不自动跟随重定向 |
| `http_post` | 向白名单域名发送一次 POST，不自动跟随重定向 |
| `ask_user` | 仅交互式 CLI 可用；无人值守入口立即失败且不读 stdin |
| `web_search` | 通过可替换 `SearchProvider` 返回有界标题、URL 和摘要 |
| `notify` | 向按名注册的通知渠道发送消息；当前出站适配器为 webhook |

HTTP 工具仅允许 Accept、Accept-Language、Content-Type 和 User-Agent headers。仅 2xx 返回成功；3xx/4xx/5xx 返回脱敏失败且不返回响应正文，不重试、不跟随重定向。需要凭证的企业接口应通过管理员配置的 MCP server 封装。

`web_search` 默认适配 DuckDuckGo Instant Answer/Related Topics；它不是完整网页 SERP。目标域名必须在 HTTP 白名单内，否则明确失败，不生成模拟结果。

## Sandbox 配置

工作区 `.oryxos/application.yaml` 示例：

```yaml
oryxos:
  file:
    allowed-paths: []
  http:
    allowed-domains:
      - api.duckduckgo.com
      - api.example.com
  tool:
    allowed-commands:
      node: C:/Program Files/nodejs/node.exe
      git: C:/Program Files/Git/cmd/git.exe
```

Shell alias 必须映射到存在的绝对普通文件。把 Node、Python 或 PowerShell 等解释器加入白名单，等于授予 OryxOS 进程用户相应的代码执行权限；argv 直传不提供文件或网络隔离。

文件相对路径以当前 Agent 目录为基准。当前 Agent 目录可读写；`oryxos.file.allowed-paths` 可增加普通目录的读写权限，每项必须是存在的绝对普通目录，包含原生链接组件、相对路径、空白或不存在的根会在启动时拒绝。配置在启动时形成快照，修改后须重启。空列表只表示不额外授权，仍保留 Agent 自有目录读写和绑定 Skill 只读；空 HTTP/Shell 白名单则拒绝对应请求。

公共 Skill 只有通过 `skills/*.link` 绑定后才可读，始终不可写。即使管理员授权公共 Skill 的上级目录，未绑定 Skill 仍不可见；从上级目录列举和搜索时会剪枝公共 Skill 根，已绑定 Skill 可通过上下文提供的真实路径直接读取/搜索。额外文件根不会扩大 Shell 工作目录权限，工作目录仍限当前 Agent。目录穿越、盘符/UNC 授权绕过和原生链接逃逸会被拒绝。

拒绝前允许必要的路径、绑定和 Skill 元数据校验，但不能读取被拒绝操作的目标正文、修改文件、启动进程或发送网络请求。`edit_file` 先通过读写授权再读取正文。安全拒绝提供固定类别原因，复用现有一次最终审计，且不可重试；Shell 输入仅保存 UTF-8 字节数及 SHA-256 摘要，不将 argv 凭证明文写入审计。错误消息脱敏和审计输入脱敏分别验证。

第 24 课中的 `file.allowed_paths`、`shell.allowed_commands`、`http.allowed_domains` 分别对应当前的 `oryxos.file.allowed-paths`、`oryxos.tool.allowed-commands`、`oryxos.http.allowed-domains`；Shell 保留 alias 到绝对 executable 的映射，未引入第二套配置。

这些检查是应用层白名单，不是容器、MicroVM 或多租户安全边界。

## Java `@Tool`

Spring Bean 的显式 `@Tool` 方法会在启动完成时由 Spring AI 生成 schema，再包装为 `OryxTool`，来源记为 `JAVA`。Java 工具仍需在 Agent 的 `tools:` 中列出才会对模型可见和可执行。

## MCP

当前支持 `stdio` 与 `sse` 两种传输。`.oryxos/mcp_servers.yaml` 示例：

```yaml
servers:
  - name: github-local
    transport: stdio
    command: node
    args: ["C:/tools/github-mcp/server.js"]
    request_timeout: 30
    env:
      GITHUB_TOKEN: ${GITHUB_TOKEN}

  - name: internal-search
    transport: sse
    url: https://mcp.example.com/sse
    request_timeout: 30
    headers:
      Authorization: Bearer ${MCP_TOKEN}
```

`command` 是 `oryxos.tool.allowed-commands` 中的 alias，不是任意可执行路径。SSE URL 必须通过 HTTP 白名单；环境变量缺失、未知字段、server 重名、未授权 command/URL 或工具重名都会使启动失败且不回显秘密。配置和授权有效、但单个 server 连接或 `tools/list` 失败时，仅记录脱敏 WARN 并跳过该 server。

MCP 工具按 server 来源注册。Agent 通过 `mcp_servers:` 选择 server，而不是把 MCP 工具名加入 `tools:`。工具调用不会由 Adapter 自动重试。

## 可见性与查看

- `tools:` 精确选择内置工具和 Java 工具。
- `mcp_servers:` 精确选择 MCP server 暴露的工具。
- 模型即使幻觉调用未授权工具，`ToolExecutor` 也会再次拒绝并记录失败审计。
- `oryxos tool list` 展示全局 Registry 中已注册的工具，并按名称排序；它不是某个 Agent 的有效工具子集。

真实 MCP、外网搜索和需凭证的外部调用需要部署者单独配置并验证；自动测试使用临时目录、回环 HTTP、受控进程和 fake provider，不用模拟结果冒充真实外部联通。
