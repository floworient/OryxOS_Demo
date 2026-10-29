# Tool 体系 Phase 0 Research

## 1. Spring AI `@Tool` 适配

### Decision

使用仓库锁定的 `spring-ai-model:1.1.2` 中 `MethodToolCallbackProvider.builder().toolObjects(...).build()` 发现显式 `@Tool` 方法，取得 `ToolCallback[]`；每个 callback 由 `AnnotatedToolAdapter` 包装为 `OryxTool`：

- `getName()` / `getDescription()` / `getInputSchema()` 读取 `ToolCallback.getToolDefinition()`；
- `execute(jsonInput)` 调用 `ToolCallback.call(jsonInput)`；
- callback 只作为 OryxOS 内部适配器，绝不交给 Spring AI 自动执行。

发现器只实例化 Spring Bean 类型上存在 `@Tool` 方法的 Bean，避免把整个 ApplicationContext 交给 provider。AOP Bean 使用目标类型检查，实际执行仍调用容器 Bean。

### Evidence

本地 1.1.2 JAR 的公开 API确认：

- `MethodToolCallbackProvider.Builder.toolObjects(Object...)`；
- `ToolCallback.getToolDefinition()` 与 `ToolCallback.call(String)`；
- `ToolDefinition.name()/description()/inputSchema()`。

### Alternatives Rejected

- 自行反射生成 JSON Schema：重复 Spring AI 已有能力，容易与 Provider schema 漂移。
- 把 callback 直接交给 ChatModel：会绕过 `ToolExecutor`，违反宪章。
- 全量扫描并实例化所有 Spring Bean：可能触发无关 Bean 副作用，启动成本和风险不可控。

## 2. Registry 位于 core 契约、tool 实现

### Decision

`oryxos-core` 提供 `ToolRegistry`、`ToolRegistration`、`ToolSource` 共享契约；`oryxos-tool` 提供 `DefaultToolRegistry`。PromptBuilder、ToolExecutor 和 CLI 只依赖 core 接口。

可见性规则只有一份：

- `BUILTIN` / `JAVA`：名称必须在 `Profile.tools`；
- `MCP`：registration 的 `sourceName` 必须在 `Profile.mcpServers`。

Registry 返回按工具名排序的不可变快照。注册在启动阶段完成；重复名原子失败，不覆盖已有项。

### Rationale

这同时满足 core 不反向依赖 tool、技术方案中 ToolRegistry 属于工具体系、模型清单与执行检查不漂移三项约束。

## 3. Skill 绑定解析复用

### Decision

从 `ContextLoader` 抽取 `SkillBindingResolver` 到 core，上下文加载和文件 Sandbox 每轮共同调用。Resolver 返回已校验的 `SkillBinding(name, description, directory, definitionFile)`，继续拒绝原生链接、绝对/盘符/UNC `.link`、断引用和真实路径逃逸。

相对文件路径以 `Profile.sourcePath().getParent()` 为基准；Agent 目录默认读写，已绑定 Skill 目录只读，额外允许根由管理员配置。

### Alternatives Rejected

- 文件工具只检查公共 skills 根：会放行未绑定 Skill。
- 缓存绑定结果：修改 `.link` 后不能在下一轮生效。
- 复制绑定目录到 Agent：破坏公共实体单一来源。

## 4. Sandbox API

### Decision

保留一个 `Sandbox` 接口，通过重载的强类型动作返回批准后的值：

```text
URI enforce(HttpRequestAction action)
Path enforce(FileAccessAction action)
Path enforce(ShellExecuteAction action)
```

动作实现共同的 `SandboxAction` 标记接口。HTTP 返回规范化 URI，File 返回真实/安全创建路径，Shell 返回白名单中预解析的绝对 executable。调用方必须使用返回值执行。

Shell 白名单配置为 `alias -> absolute executable path`；模型只传 alias，避免 PATH 劫持和 Windows 同名扩展歧义。

### Rationale

比 `Object`/字符串 target 更强类型，也比三个无关 checker 更符合统一 Sandbox 契约。现有 Notify 迁移到 `HttpRequestAction` 后继续得到“批准 URI 与实际 URI 相同”的保证。

## 5. MCP SDK 与传输

### Decision

`oryxos-tool` 增加 `org.springframework.ai:spring-ai-mcp:1.1.2`，手工创建 SDK 0.17.0 的同步客户端，不引入 MCP starter 自动配置。核心阶段支持：

- `stdio`：`StdioClientTransport` + `ServerParameters(command).args(args).env(env)`；
- `sse`：`HttpClientSseClientTransport`；URL 和 header 经显式解析与 Sandbox。

客户端使用 `McpClient.sync(transport).initializationTimeout(...).requestTimeout(...).build()`。工具 schema 与调用使用 `SyncMcpToolCallback` 或等价 SDK对象包装；OryxOS 自己保存 server 来源并统一注册。

Streamable HTTP 虽在 SDK 0.17.0 中存在，但技术方案第 6.4 节明确核心为 stdio/SSE，故本 feature 不扩展 `auto`/streamable 传输。

### Failure Classification

- YAML、重复项、缺失环境变量、Sandbox 拒绝、command 未授权：本地确定性错误，fail-fast。
- 初始化超时、拒绝连接、远端不可用、`tools/list` 远端失败：脱敏 WARN，关闭该客户端并跳过该 server。
- `tools/call`：只调用一次，不在 Adapter 内重试。

### Alternatives Rejected

- 使用 starter 自动连接并把 ToolCallback 直接暴露给 ChatModel：失去 OryxOS Registry、可见性与审计控制。
- 自行实现 JSON-RPC：无必要且协议风险高。
- 本轮实现 streamable/auto fallback：超出课程核心范围和已批准非目标。

## 6. MCP 配置 Schema

### Decision

顶层只允许 `servers`；server 公共字段为 `name`、`transport`、`request_timeout`：

- stdio：`command`（Shell 白名单 alias）、可选 `args`、可选 `env`；
- sse：`url`、可选 `headers`。

`request_timeout` 为 1–300 秒，默认 30 秒；初始化超时固定为 `min(request_timeout, 20)` 秒。`${ENV_VAR}` 只允许作为 env/header 的完整值或值内占位符，缺失即 fail-fast；解析后的秘密不写回配置对象的可显示形式。

### Rationale

结构化 `args` 与 SDK `ServerParameters` 一致，避免对管理员命令字符串做不可靠的 Shell 分词；`command` 使用 alias 与 Shell 白名单复用授权。

## 7. `ask_user` 调用上下文

### Decision

core 增加 `UserInteraction` 和 `UserInteractionContext`。CLI 在单次 `AgentService.process` 前设置基于当前 `BufferedReader`/`PrintWriter` 的实现，finally 清理；其他入口不设置，工具获得 `UnsupportedUserInteraction`。

因为 ToolExecutor 在虚拟线程执行工具，它必须像传播 ProfileContext 一样捕获、设置并清理 UserInteractionContext。禁止把进程 stdin 注册成全局 Bean。

### Alternatives Rejected

- 全局 ConsoleUserInteraction：HTTP/定时任务可能误读 stdin，且并发 Session 串线。
- 在 `ask_user` 内直接访问 `System.in`：不可测试且违反渠道抽象。

## 8. `web_search` Provider

### Decision

提供 `SearchProvider` SPI、`SearchResult` 和 `WebSearchTools`。默认实现命名为 `DuckDuckGoInstantAnswerSearchProvider`，准确表达它使用 Instant Answer/Related Topics，而不是宣称返回完整 SERP；管理员也可替换 Bean。结果统一裁剪为最多 10 条、每字段有长度上限。

真实请求使用 `https://api.duckduckgo.com/`，必须显式加入 HTTP 白名单；未放行或 provider 不可用时 fail-loud。自动测试使用 fake provider，不依赖该公共服务。

### Evidence and Limitation

DuckDuckGo 官方帮助将 Instant Answers 描述为搜索结果页上针对特定查询的答案能力，并说明传统链接是另一部分。因此文档与工具描述必须称“Instant Answer 及相关主题检索”，不能承诺通用网页排名结果。

### Alternatives Rejected

- 抓取 HTML 搜索页：非稳定 API，解析脆弱且可能违反服务使用预期。
- 伪造本地结果：违反交付真实性。
- 要求本轮引入付费搜索凭证：用户已确认 HTTP 工具不增加凭证系统。

## 9. 文件与进程限制

### Decision

默认值如下，均可由 `oryxos.tools` 配置收紧：

- JSON 输入：256 KiB；
- 单个文本文件：1 MiB；
- `read_file` 输出：256 KiB；
- 搜索遍历：最多 10,000 个文件、总读 32 MiB、结果 200 条；
- Shell：默认 30 秒、最大 300 秒，stdout/stderr 各 256 KiB；
- HTTP：连接 10 秒、总请求 60 秒，请求/响应各 1 MiB；
- 搜索结果：最多 10 条，总输出 64 KiB。

写入采用同目录临时文件，优先 `ATOMIC_MOVE`；不支持时使用 `REPLACE_EXISTING`，并在替换前保持原文件未变。临时文件在 finally 清理。

## 10. Verification Strategy

全部网络自动测试使用 JDK `HttpServer` 回环地址并显式白名单；MCP 使用 fake `McpConnection`；Shell 使用 JDK 本身或仓库测试 helper 的绝对 executable，不假设系统有 Bash。Windows 原生链接测试保留现有条件跳过策略。真实 DuckDuckGo/MCP 调用只作为手工 quickstart 可选项。

