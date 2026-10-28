# Data Model: 第 18 节 CLI 命令

## Workspace

文件系统聚合根，运行时绝对路径由统一解析器产生。

| 成员 | 类型 | 约束 |
| --- | --- | --- |
| root | Path | 绝对、规范化；不存在时仅 `init` 可创建；现存时不得为符号链接 |
| agents | directory | 活动 Agent 唯一来源 |
| skills | directory | 公共 Skill 实体库 |
| output | directory | Agent 产出物 |
| memory | directory | 包含 `MEMORY.md` |
| sessions | directory | 保留的工作区目录；数据库仍是当前 Session 事实源 |
| logs | directory | 日志目录 |
| archive | directory | 首次归档时创建，不由 `init` 强制创建 |
| AGENTS.md/SOUL.md/USER.md | UTF-8 file | create-if-absent，永不覆盖 |
| oryxos.db | SQLite file | 由运行时/Flyway创建，`init` 不创建 |

状态：`absent → initialized → partially missing → repaired`。`init` 只完成 absent/missing 项，不改变 existing 项；类型冲突或链接立即失败。

## AgentDefinition

| 成员 | 类型 | 约束 |
| --- | --- | --- |
| name | String | 1–64；ASCII 字母数字开头；后续仅字母数字、`.`、`_`、`-`；拒绝 `.`/`..`、路径形式和 Windows 设备名 |
| directory | Path | `<root>/agents/<name>` 的直接子目录，普通目录且真实路径不越界 |
| source | Path | `<directory>/AGENT.md` 普通 UTF-8 文件，不得为链接 |
| attachments | files/directories | Agent 目录内可选 `skills/`、`scripts/`、`REFERENCE.md` 等，归档时整体移动 |

状态：`absent → active → archived`。本 feature 不提供 archived → active 命令，但归档保留全部内容供人工恢复。

归档目标：`<root>/archive/<name>-yyyyMMdd'T'HHmmssSSS'Z'[-N]`，时间固定 UTC；存在冲突时从 `-1` 递增。

## CliCommand

| 成员 | 类型 | 约束 |
| --- | --- | --- |
| path | String list | 根/分组/业务命令层级，12 条业务路径固定 |
| options | option list | Picocli 校验；缺失/非法时不进入执行器 |
| executionKind | LIGHT/RUNTIME | LIGHT 不创建 Spring；RUNTIME 经 `CliRuntime` |
| stdout/stderr | text stream | 正常与错误分流 |
| exitCode | int | 0 成功，1 执行失败，2 用法错误 |

## ProviderInfo

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| name | String | 非空、配置顺序稳定 |
| type | String | 已绑定的 Provider 类型 |
| model | String | 非空 |
| baseUrl | String? | 可空；仅显示非敏感地址 |

不得含 apiKey、展开后的占位符值或凭据状态原文。

## ToolInfo

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| name | String | 非空、唯一，按名称排序输出 |
| description | String | 可为空字符串，不执行 Tool |

## SessionSummary

core record：`SessionSummary(String id, String profileName, String status, Instant lastActiveAt)`。

| 字段 | 约束 |
| --- | --- |
| id/profileName/status | 非空；status 只允许 `active`/`archived` |
| lastActiveAt | 非空 UTC Instant |

查询按 `lastActiveAt DESC, id ASC` 稳定排序，不加载或输出 messages_json、userId、工具参数和模型内容。

## RuntimeMode

| 模式 | Web 类型 | 生命周期 | 共享数据 |
| --- | --- | --- | --- |
| chat | NONE | 对话退出即关闭上下文 | 同一 workspace、Session 与审计 |
| serve | SERVLET | 进程终止时关闭 | 同一 workspace、Session 与审计 |
| gateway | SERVLET 聚合 | 进程终止时关闭 | 同一 workspace、Session 与审计 |

