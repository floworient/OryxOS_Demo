# Tool 体系 Data Model

本 feature 不新增数据库表；运行时模型与配置模型如下。

## ToolRegistration

| Field | Type | Rules |
| --- | --- | --- |
| `tool` | `OryxTool` | 非空；名称、说明、schema 在注册时校验 |
| `source` | `ToolSource` | `BUILTIN`、`JAVA`、`MCP` |
| `sourceName` | `String?` | MCP 必填且为 server name；其他来源为空 |

不变量：工具名全局唯一；注册后不可修改；Registry 对外返回不可变、按名称排序的快照。

## ToolRegistry

行为：

- `register(ToolRegistration)`：原子注册，非法或重名失败；
- `find(String)`：按全局名查询；
- `registrations()`：稳定全量快照；
- `visibleTo(Profile)`：按 `tools` / `mcpServers` 计算稳定子集。

状态转换：`UNREGISTERED -> REGISTERED`。本 feature 不支持运行中注销；失败注册不改变已有状态。

## SkillBinding

| Field | Type | Rules |
| --- | --- | --- |
| `name` | `String` | 来自 SKILL.md frontmatter，非空 |
| `description` | `String` | 来自 frontmatter，非空 |
| `directory` | `Path` | 位于公共 skills 根内的真实目录 |
| `definitionFile` | `Path` | directory 内真实普通 `SKILL.md` |

每轮从 Agent 的普通 `.link` 文件重新计算，不缓存。

## Sandbox Actions

### HttpRequestAction

- `URI target`
- 返回：规范化且获批准的 `URI`

### FileAccessAction

- `Profile profile`
- `Path requested`
- `FileAccessMode mode`: `READ`、`WRITE`、`LIST`、`SEARCH`、`WORKING_DIRECTORY`
- 返回：真实已存在路径，或写入场景下由真实最近父目录验证得到的安全目标路径

### ShellExecuteAction

- `String executableAlias`
- 返回：白名单映射的绝对真实 executable `Path`

## MCP Server Definition

| Field | Type | Rules |
| --- | --- | --- |
| `name` | `String` | 1–64，配置内唯一 |
| `transport` | enum | `STDIO` 或 `SSE` |
| `requestTimeout` | `Duration` | 1–300 秒，默认 30 秒 |
| `command` | `String?` | stdio 必填；Shell alias |
| `args` | `List<String>` | stdio 可选；argv 原样传递 |
| `environment` | `Map<String,String>` | stdio 可选；解析后的内部快照不得显示 |
| `url` | `URI?` | sse 必填；经 HTTP Sandbox |
| `headers` | `Map<String,String>` | sse 可选；值可引用环境变量，日志禁止输出 |

配置加载后状态：

`DECLARED -> VALIDATED -> AUTHORIZED -> CONNECTING -> ACTIVE`

- 在 VALIDATED/AUTHORIZED 失败：应用启动失败；
- CONNECTING 纯外部失败：`SKIPPED_UNAVAILABLE`；
- ACTIVE 在应用关闭时：`CLOSED`。

## MCP Remote Tool

- `serverName`
- `name`
- `description`
- `inputSchema`
- 对应同步 client/callback 引用

注册时转换为 `ToolRegistration(source=MCP, sourceName=serverName)`。

## User Interaction Context

- `UserInteraction ask(String question)`：返回一行回答或抛出安全异常；
- Context 是单次调用线程上下文，ToolExecutor 传播到工具虚拟线程并在 finally 清理；
- 未设置时解析为 `UnsupportedUserInteraction`，而不是全局 stdin。

## SearchResult

| Field | Type | Rules |
| --- | --- | --- |
| `title` | `String` | 非空，有长度上限 |
| `url` | `URI` | 绝对 HTTP(S) |
| `snippet` | `String` | 有长度上限，可为空 |

WebSearchTools 对 provider 输出做二次验证、数量限制和总输出限制。

