# Profile 配置（AGENT.md frontmatter）

一个 `.oryxos/agents/<name>/` 目录定义一个 Agent，且必须直接包含普通文件 `AGENT.md`。YAML frontmatter 派生运行时 `Profile`，结束边界后的 Markdown 正文作为任务指令进入 system prompt。Agent 目录和 Skill 都是上下文来源，不是 Tool。

## 完整示例

`.oryxos/agents/ops-agent/AGENT.md`：

```markdown
---
name: ops-agent
description: 运维检查助手
identity:
  agent_name: 运维小欧
  prompt: 你是谨慎的运维助手，只报告有证据的结论。
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.2
tools:
  - read_file
  - grep
  - shell
  - http_get
  - ask_user
mcp_servers:
  - github-local
channels:
  - name: cli
    config: {}
bootstrap:
  - AGENTS.md
schedules:
  - key: morning-check
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: 执行晨间检查。
settings:
  max_iterations: 10
  max_history_turns: 20
---

检查 Agent 目录中的配置和日志；需要外部数据时只使用已批准工具。
```

`name` 必须与目录名完全一致。Agent 目录、`AGENT.md` 和公共 Skill 路径中的原生符号链接不会被当作兼容机制。

## 字段

| 字段 | 必填 | 当前契约 |
| --- | --- | --- |
| `name` | 是 | 非空字符串；与目录名一致 |
| `description` | 是 | 非空的人类可读说明 |
| `identity` | 是 | `agent_name` 与 `prompt` 都是非空字符串 |
| `provider` | 是 | `name`、`model` 必填；`temperature` 可选且范围为 0～2 |
| `tools` | 否 | 内置和 Java 工具名列表；默认空 |
| `mcp_servers` | 否 | MCP server 名列表；默认空 |
| `channels` | 否 | 每项含 `name`，`config` 可选对象 |
| `bootstrap` | 否 | 按顺序加载的文件路径列表 |
| `schedules` | 否 | 每项含 `key`、`cron`、`zone`、`message`；同一 Agent 内 `key` 唯一 |
| `settings` | 否 | `max_iterations` 默认 10，`max_history_turns` 默认 20，均须为正整数 |

Provider 凭证不属于 Agent frontmatter。通过工作区 `application.yaml` 与环境变量配置 Provider，不在 `AGENT.md` 中写 API key。

## 定时任务行为与边界

应用启动时会从所有已加载 Profile 的 `schedules` 动态注册规则。`cron` 使用 Spring 六字段格式，`zone` 必须是 Java `ZoneId` 可解析的显式时区（推荐使用 `Asia/Shanghai` 这类 IANA region ID；固定 offset 不处理夏令时变化）；非法 cron、非法时区、空字段或同一 Agent 内重复 `key` 会使整个 Agent 在加载阶段被拒绝。

每条规则的稳定身份只由 `(Agent name, schedule key)` 派生。修改 cron、时区或消息不会改变身份。触发时运行时固定使用 `channel=scheduler`、`user=scheduler` 和目标 Profile 名取得确定性 Session，再调用与 CLI、HTTP 相同的 `AgentService` 链路；不会建立独立的 ReAct 或 Tool 执行通道。

同一 Profile 的所有规则在单个 JVM 内共享互斥锁。已有执行尚未结束时，新触发会立即跳过，不排队也不自动补跑；不同 Profile 可独立执行。执行失败会被隔离并记录安全的错误类型，不会自动重试。

当前调度 Store 是线程安全的内存实现。活动定义和执行历史不会跨重启恢复，规则变更通过重启重新加载。本阶段没有调度管理 REST/CLI、运行时修改 cron、启停、立即执行、失败重试或多实例协调；多实例部署不会去重，同一规则可能在每个实例各执行一次。

## Tool 可见性

`tools:` 与 `mcp_servers:` 是两条不同的授权路径：

- 内置工具和 Java `@Tool` 必须按工具名出现在 `tools:`。
- MCP 工具按来源 server 授权；server 名必须出现在 `mcp_servers:`，无需把每个 MCP 工具名重复写进 `tools:`。
- 未声明工具不会进入模型清单；模型直接调用时，`ToolExecutor` 仍会再次拒绝并写失败审计。
- `oryxos tool list` 显示全局 Registry，不代表某个 Agent 全部可用。

当前新增内置工具名为 `read_file`、`write_file`、`list_dir`、`edit_file`、`grep`、`glob`、`shell`、`http_get`、`http_post`、`ask_user` 和 `web_search`。`notify` 是已有工具。`save_memory` / `recall_memory` 不由本次 Tool 交付提供。

## Skill 绑定

公共 Skill 位于 `.oryxos/skills/<name>/`。Agent 通过普通 UTF-8 `.link` 文件选择可见 Skill：

```text
.oryxos/
├── agents/ops-agent/
│   ├── AGENT.md
│   └── skills/deploy.link
└── skills/deploy/SKILL.md
```

`deploy.link` 只含一行、相对于该文件父目录的路径：

```text
../../../skills/deploy
```

目标真实路径必须位于本工作区公共 `skills/` 根内。绝对路径、盘符、UNC、断引用和原生软连接逃逸会被拒绝。每轮上下文构建与文件访问都会重新解析绑定；绑定 Skill 可读但不可写。frontmatter 中的旧 `skills:` 字段会被拒绝。

## 交互和外部能力限制

`ask_user` 只在交互式 CLI 调用期间可用；HTTP、调度等无人值守入口会快速失败，不读取进程 stdin。`web_search`、HTTP、通知和远程 MCP 仍受管理员白名单及外部服务可用性约束；仅把名称写入 Profile 不会绕过 Sandbox。
