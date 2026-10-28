# CLI 参考

OryxOS 本阶段提供 12 条业务命令。唯一完整可执行物是 Boot exec JAR：

```bash
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar --help
```

以下用 `oryxos` 代指这条命令。所有命令支持 `-h/--help`，根命令支持 `-V/--version`。

## 工作区

所有命令按 `-Doryxos.root=<path>` → `ORYXOS_ROOT` → 当前目录 `.oryxos/` 的顺序解析工作区。

`init`、`status` 和四条 `profile` 命令是轻命令，不启动 Spring，也不需要模型凭据。`init` 幂等补齐 `agents/`、`skills/`、`output/`、`memory/`、`sessions/`、`logs/`、三个 Bootstrap 文件和 `memory/MEMORY.md`，不覆盖已有内容，也不代替 Flyway 建库。

```bash
oryxos init
oryxos status
```

## 12 条命令

| 命令 | 行为 |
| --- | --- |
| `oryxos init` | 安全、幂等地初始化工作区 |
| `oryxos status` | 报告 root、初始化状态、Agent 数和数据库状态 |
| `oryxos chat [--profile <name>]` | 通过统一 `AgentService` 多轮对话；默认 `default`，`/quit` 或 EOF 退出 |
| `oryxos serve [--port <1..65535>]` | 启动现有 HTTP 服务，默认端口 8080 |
| `oryxos gateway` | 启动聚合运行时并报告实际装配组件 |
| `oryxos profile list` | 按名称列出活动 Agent |
| `oryxos profile create <name>` | 创建可被 `AgentLoader` 读取的最小 `AGENT.md` |
| `oryxos profile show <name>` | 原样输出 `AGENT.md` |
| `oryxos profile delete <name>` | 将整个 Agent 目录移动到 `archive/`，不永久删除 |
| `oryxos provider list` | 输出 `NAME/TYPE/MODEL/BASE_URL`，不输出凭据 |
| `oryxos tool list` | 输出真实 `runtimeTools` 的名称和描述 |
| `oryxos session list` | 输出 Session 摘要，按最近活跃时间倒序 |

`profile`、`provider`、`tool`、`session` 是帮助分组，不额外计入 12 条。

## Profile 安全与归档

Agent 的唯一活动定义位于 `<workspace>/agents/<name>/AGENT.md`。名称仅允许 1–64 个 ASCII 字母、数字、点、下划线和连字符，且必须以字母或数字开头；路径穿越、绝对路径、Windows 设备名和操作系统链接均被拒绝。

```bash
oryxos profile create daily-weather
oryxos profile show daily-weather
oryxos profile delete daily-weather
# 输出例如 archive/daily-weather-1793164800000
```

## Provider 配置与查询

`provider list` 使用只绑定配置的最小上下文，不构造 `ChatModel`，因此查询本身不要求凭据。可在工作区根的 `application.yaml` 声明：

```yaml
oryxos:
  providers:
    - name: qwen
      type: DASHSCOPE
      model: qwen-plus
      base-url: https://dashscope.aliyuncs.com
      api-key: ${ALIYUN_API_KEY:}
```

输出不会包含 `api-key` 或展开后的值。`tool list` 与 `session list` 会按需启动运行时/存储上下文，并在查询结束后关闭。

## 运行模式边界

`serve` 和 `gateway` 复用现有 Spring Boot、Web、Session 与审计链路。当前飞书、企微、钉钉模块仍只有模块边界标记，没有可运行的 `ChannelAdapter`；`gateway` 不会把这些未来能力报告为可用。按 `Ctrl-C` 时 shutdown hook 关闭上下文。

## 退出码

| 退出码 | 含义 |
| --- | --- |
| `0` | 成功，包括空查询结果 |
| `1` | 运行时、配置、IO 或外部资源错误 |
| `2` | 未知命令、缺少参数或非法参数 |

正常结果写 stdout，错误与用法写 stderr；异常输出会脱敏且默认不打印堆栈。
