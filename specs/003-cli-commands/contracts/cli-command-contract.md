# Contract: OryxOS CLI

## 全局契约

```text
oryxos [-hV] <command> [args]
```

- `-h, --help`：打印当前命令帮助，退出 0。
- `-V, --version`：根命令打印 `OryxOS <implementation-version>`，退出 0。
- 工作区：`-Doryxos.root=<path>` → `ORYXOS_ROOT` → `<user.dir>/.oryxos`。
- 退出码：0 成功（包括空列表）；1 执行/配置/IO/启动失败；2 未知命令、缺参数或非法参数。
- stdout 仅放正常结果；stderr 放用法和错误；默认不打印堆栈、密钥或完整敏感配置。

## 12 条业务命令

| # | 命令 | 类型 | 参数 | 成功行为 |
| --- | --- | --- | --- | --- |
| 1 | `oryxos init` | LIGHT | 无 | 补齐工作区并打印 created/skipped 摘要 |
| 2 | `oryxos status` | LIGHT | 无 | 打印 root、initialized、agentCount、database 状态 |
| 3 | `oryxos chat` | RUNTIME | `--profile <name>`，默认 `default` | 进入交互循环 |
| 4 | `oryxos serve` | RUNTIME | `--port <1..65535>`，默认 8080 | 启动 HTTP 服务 |
| 5 | `oryxos gateway` | RUNTIME | 无 | 启动聚合运行时并报告真实组件 |
| 6 | `oryxos profile list` | LIGHT | 无 | Agent 名按字典序逐行输出；空时输出 `未找到 Agent` |
| 7 | `oryxos profile create <name>` | LIGHT | 1 个名称 | 创建目录和最小 AGENT.md |
| 8 | `oryxos profile show <name>` | LIGHT | 1 个名称 | 原样输出 AGENT.md |
| 9 | `oryxos profile delete <name>` | LIGHT | 1 个名称 | 归档整个目录并打印归档相对路径 |
| 10 | `oryxos provider list` | RUNTIME 查询 | 无 | 表格列 NAME/TYPE/MODEL/BASE_URL；空时明确提示 |
| 11 | `oryxos tool list` | RUNTIME 查询 | 无 | 表格列 NAME/DESCRIPTION；空时明确提示 |
| 12 | `oryxos session list` | RUNTIME 查询 | 无 | 表格列 SESSION_ID/PROFILE/STATUS/LAST_ACTIVE_AT；空时明确提示 |

分组节点 `profile`、`provider`、`tool`、`session` 无业务操作，裸调用时打印该组帮助并退出 0，不计入 12 条。

## chat 交互协议

```text
> <用户输入>
<Agent 最终回复>
```

- userId 取非空 `user.name`，缺失时使用 `local-user`。
- Session 身份为 `channel=cli`、userId、profileName；由 SessionManager get-or-create。
- 输入在移除行终止符后传给 Agent，保留内容空格；纯空白行不调用 Agent并继续提示。
- trim 后完全等于 `/quit` 时退出 0；EOF 同样退出 0。
- 单轮处理失败输出脱敏错误并退出 1，不自动重发用户消息。

## Profile 名称与文件安全

- 名称正则基线：`[A-Za-z0-9][A-Za-z0-9._-]{0,63}`。
- 额外拒绝 `.`、`..`、绝对/盘符/UNC/含分隔符名称和 Windows 设备名（大小写不敏感）。
- 所有现存目标使用 NOFOLLOW_LINKS 与 real path 验证；目标必须是 agents 根的直接子项。
- create 不覆盖；show 不解析正文；delete 移动整个目录到 archive，不递归删除。

## Profile create 模板

模板字段必须符合既有 AgentLoader 契约：name/description/identity/provider 必填，tools/mcp_servers/schedules 为空，channels 包含 cli，bootstrap 包含 AGENTS.md/SOUL.md/USER.md，settings 为 10/20。模板不包含 apiKey 或 Skill frontmatter 清单。

## Runtime 接口

`oryxos-cli` 暴露以下基础设施无关接口，由 Boot 实现：

```java
interface CliRuntime {
    void chat(Path workspace, String profileName, String userId, Reader input, PrintWriter output);
    void serve(Path workspace, int port);
    void gateway(Path workspace);
    List<ProviderInfo> providers(Path workspace);
    List<ToolInfo> tools(Path workspace);
    List<SessionSummary> sessions(Path workspace);
}
```

`ProviderInfo`/`ToolInfo` 为 CLI 内只读 record；`SessionSummary` 来自 core。实现可抛运行时异常，由根命令执行异常处理器脱敏并映射退出码 1。

