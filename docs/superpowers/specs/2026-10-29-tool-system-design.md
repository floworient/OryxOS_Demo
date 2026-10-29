# OryxOS 第 20 节 Tool 体系设计

## 1. 意图与背景

本设计交付课程第 20 节定义的完整 Tool 体系，使 OryxOS 在既有 Provider、ReAct、CLI、Notify 和审计基础上，能够统一注册、筛选并执行内置工具、Java `@Tool` 工具和 MCP 工具。业务 Agent 仍通过 `AGENT.md` 的 `tools`、`mcp_servers` 以及公共 Skill `.link` 绑定声明能力，不把 Agent 或 Skill 目录误当成可执行 Tool。

当前仓库已经具备 `OryxTool`、`ToolResult`、`ToolExecutor`、Provider schema 适配、HTTP Sandbox、`notify` 与 `tool_invocations` 审计。本功能在这些已验证契约上补齐缺口，不重新实现 ReAct，也不启用 Spring AI 自动工具执行。

## 2. 目标

- 建立统一 `ToolRegistry`，让内置工具、Java `@Tool` 与 MCP 工具以同一种运行时契约注册、发现、过滤和执行。
- 保留现有 `OryxTool` 的 String JSON 输入与 String JSON Schema 契约，避免无收益地重写 Provider、ReAct、Notify 和已有测试。
- 交付第 20 节基础工具：`read_file`、`write_file`、`list_dir`、`shell`、`http_get`、`http_post`。
- 交付第 20 节扩展工具：`edit_file`、`grep`、`glob`、`ask_user`、`web_search`。
- 将既有 `notify` 纳入统一注册表；后续 Memory 功能提供的 `save_memory`、`recall_memory` 可通过同一注册机制加入，但本功能不伪造尚未实现的 Memory 后端。
- 支持 Java Spring Bean 上的 `@Tool` 方法自动生成 schema 并包装为 `OryxTool`，真实执行仍只由 `ToolExecutor` 发起。
- 支持从 `.oryxos/mcp_servers.yaml` 接入外部 MCP server，将 `tools/list` 结果包装为 `OryxTool`，并通过 `tools/call` 原样转发 JSON 参数。
- 对文件、Shell、HTTP、搜索和 MCP 远程连接落实真实、可测试的安全边界。
- 建立课程要求的验收 harness，覆盖契约完整性、注册与可见性、正常执行、越界拒绝、MCP 转发及失联隔离。

## 3. 非目标与反目标

### 非目标

- 不实现 Tool Policy 管理端、按需加载、并行 Tool Call、OryxOS MCP Server、容器/MicroVM Runner 或多租户隔离。
- 不实现课程第 20 节之外、当前网站文档中前瞻描述的 `append_file`、`make_dir`、`move_file`、`copy_file`、`delete_file`、`http_request`、`fetch_webpage`、`download_file`、`current_time`、`json_extract` 等扩展。
- 不交付 Memory 本体或用占位实现冒充 `save_memory`、`recall_memory`；Memory 是独立 US-3。
- 不交付 MCP 管理 API、管理台、精选 server 市场或远程传输自动探测。
- 不把应用层白名单描述为容器级沙箱。

### 反目标

- 不允许任何工具绕过 `ToolExecutor` 直接由 Spring AI、Controller 或 MCP SDK 自动执行。
- 不允许文件工具仅以公共 `skills/` 根为边界，从而读取当前 Agent 未绑定的 Skill。
- 不允许 Shell 通过字符串拼接启动系统 Shell；命令和参数必须结构化并通过 `ProcessBuilder` argv 直传。
- 不允许 MCP server 失联拖垮整个 OryxOS 启动，也不允许错误信息泄露环境变量、header、token 或外部响应正文。

## 4. 已考虑方案

### 方案 A：兼容式完善（已选择）

保留现有 `OryxTool`、`ToolResult` 和 Provider schema 适配，在其上增加 Registry、Adapter、内置工具、MCP 与装配。优点是沿用已经通过测试的 ReAct/审计链路，改动集中且回归风险最低；代价是接口形式与课件中的 `JsonNode` 示意代码不同，但语义等价。

### 方案 B：按课件示例整体迁移为 `JsonNode` / `JsonSchema`

形式上更接近示意代码，但需要修改 Provider、ReAct、Notify、测试桩和装配。课件明确说明代码是示意，迁移没有带来新的产品能力，因此不采用。

### 方案 C：拆成核心与扩展两个 feature

便于缩小单次改动，但第一期结束时无法满足“第 20 节完整交付”，并增加两套 Spec-Kit artifact 与跨 spec 契约协调成本，因此不采用。实施仍会在同一 spec 内按可独立验证的阶段推进。

## 5. 核心设计

### 5.1 统一注册模型

`oryxos-core` 增加不依赖基础设施的注册契约：

- `ToolRegistry`：注册、按名查找、列出全部工具、按 `Profile` 计算有效工具集；工具名为空或全局重名时立即失败。
- `ToolRegistration`：包含 `OryxTool`、来源类型（`BUILTIN`、`JAVA`、`MCP`）以及可选的 MCP server 名。

内置工具和 Java 工具继续由 `Profile.tools` 精确授权；MCP 工具由 `Profile.mcpServers` 选择 server 后整体可见。模型提示词生成和执行前检查必须调用同一个 Registry 过滤逻辑，防止“模型看不到但可执行”或“模型看得到但执行被另一套规则拒绝”的漂移。

`PromptBuilder`、`ToolExecutor`、CLI `tool list` 和 Boot 装配改为依赖 Registry，而不是各自持有或重建 `Map<String,OryxTool>`。`ToolExecutor` 仍是唯一执行入口，继续负责超时、重试语义、ProfileContext 传播和单次最终审计。

### 5.2 Java `@Tool` 适配

`oryxos-tool` 提供 `AnnotatedToolAdapter` 与发现器：

- 从 Spring 管理的业务 Bean 中发现显式 `@Tool` 方法；不扫描 OryxOS 内部任意方法。
- 复用当前 Spring AI 版本的 schema 生成能力，把方法元数据适配为一个或多个 `OryxTool`。
- Adapter 的 `execute` 只做严格 JSON 参数绑定、方法调用和安全错误映射，不注册 Spring AI 自动执行 callback。
- 方法名、描述、schema 缺失、重复工具名或不支持的方法签名在装配期失败。

具体采用 Spring AI `MethodToolCallbackProvider` 还是底层 method callback builder，由 plan 的依赖 API 研究确定；选择标准是能复用 schema 生成且不启用自动执行。

### 5.3 Sandbox 与批准后的值

现有 Sandbox 只表达 HTTP URI，无法安全覆盖文件与 Shell。本功能将动作扩展为按类型承载目标的安全契约，并要求 `enforce` 返回经过规范化和批准的目标；工具必须使用返回值执行，不能校验一个值后执行另一个值。

- HTTP：仅允许绝对 HTTP(S) URI，拒绝 user-info、fragment、非法端口与 host 混淆；按配置域名白名单检查。
- 文件：解析为真实路径或“最近存在父目录 + 待创建文件名”，拒绝 `..`、盘符/UNC 绕过和符号链接逃逸。
- Shell：校验规范化 executable 白名单，参数以 argv 原样传递，限制超时和输出大小。

文件可见边界由一个共享的 `SkillBindingResolver` 计算。`ContextLoader` 与文件 Sandbox 复用它，确保每轮重新解析 `.link`：

- 工具输入的相对路径以当前 Agent 目录为基准解析。
- 当前 Agent 目录是内建的最小允许根，即使管理员没有配置额外文件白名单也可读写；额外白名单为空只表示不放行其他目录。
- 公共 `skills/` 下仅当前 Agent 已绑定 Skill 的真实目录可读。
- 未绑定 Skill、断引用、原生软连接和越界目标均拒绝。

写操作不允许修改公共 Skill；公共 Skill 生命周期仍由其管理能力负责。`read_file` 可读取已绑定 Skill，`write_file` / `edit_file` 只允许管理员配置的可写根与当前 Agent 自有目录。

### 5.4 文件工具

`FileTools` 作为一组 `OryxTool` 注册，所有输入严格拒绝未知字段：

- `read_file(path)`：UTF-8 文本读取，限制最大返回字符数；二进制或非法编码明确失败。
- `write_file(path, content)`：创建或覆盖普通文件，父目录必须存在且可写；不隐式创建目录。
- `list_dir(path)`：稳定排序，返回名称、类型和必要元数据，限制条数。
- `edit_file(path, old_text, new_text)`：`old_text` 必须恰好出现一次；零次或多次时不修改文件。
- `grep(path, pattern)`：Java 正则搜索 UTF-8 普通文件，跳过二进制/非法编码，最多返回 200 条并标明截断。
- `glob(path, pattern)`：在批准根内按 glob 查找，稳定排序，最多返回 200 条并标明截断。

文件变更采用同目录临时文件加原子替换（平台支持时），降低部分写入风险。所有搜索都拒绝通过链接逃逸，并限制遍历数量、单文件大小和总读取量，避免耗尽上下文与进程资源。

### 5.5 Shell 工具

`shell(executable, arguments, working_directory, timeout_seconds)` 使用 `ProcessBuilder`：

- `executable` 必须在管理员白名单中；不接受一整段命令字符串。
- `arguments` 是字符串数组，不解释管道、重定向、命令替换、变量展开或分隔符。
- `working_directory` 必须通过文件 Sandbox，默认为当前 Agent 目录。
- 超时默认 30 秒，并受配置上限约束；超时后终止进程树。
- stdout/stderr 分别有字节上限，结果包含退出码和是否截断；日志与返回值不包含环境变量快照。
- 将解释器加入白名单意味着授予进程用户代码执行权限，配置与文档必须明确这一点。

### 5.6 HTTP 工具

`HttpTools` 提供：

- `http_get(url, headers)`；
- `http_post(url, headers, body, content_type)`。

二者在发送前使用同一个 HTTP Sandbox 并执行批准后的 URI；默认不跟随重定向。请求头使用显式允许列表，禁止 `Authorization`、`Proxy-Authorization`、`Cookie`、`Set-Cookie`、`Host` 等凭证或路由敏感头，模型不能通过工具参数提供认证凭证。本 feature 不实现命名凭证；需要认证的系统应通过 MCP 或后续独立凭证管理能力接入。请求体、响应体和耗时均设上限。非 2xx 返回安全失败，不把大响应、凭证或内部异常直接放入 ToolResult。无需真实网络的测试使用本地回环服务。

### 5.7 MCP Client

`.oryxos/mcp_servers.yaml` 在本 feature 支持第 20 节最小配置：

- 顶层 `servers`；每项包含唯一 `name`、`transport`、请求超时及传输所需字段。
- `stdio` 使用结构化 `command` 与 `args`，环境变量仅允许 `${ENV_VAR}` 占位符解析；不把密钥写入日志。
- 远程 MCP 支持当前 Spring AI MCP SDK稳定提供且经兼容性测试验证的 SSE 或 Streamable HTTP 传输；最终支持项在 research/plan 中按仓库锁定版本记录，不自行实现协议。
- 远程 URL 先经 HTTP Sandbox；本地 command 先经 Shell 白名单。

`McpClientService` 逐 server 初始化、调用 `tools/list` 并注册 `McpToolAdapter`。配置结构错误、server 重名、环境变量缺失、远程 URL 被 HTTP Sandbox 拒绝、stdio command 不在 Shell 白名单或工具全局重名，均属于本地确定性配置/授权错误，启动失败而不是静默降级。只有在配置与授权均有效后发生的连接超时、拒绝连接、远端不可用或 `tools/list` 外部故障，才记录脱敏 WARN 并跳过该 server；这类单 server 可用性故障不影响内置、Java 或其他健康 MCP 工具。

`McpToolAdapter.execute` 原样转发 JSON 对象参数，聚合 MCP 文本结果为 ToolResult；协议错误、超时和断连返回安全失败。是否可重试按错误类型保守设置，禁止在 Adapter 内对有副作用的 `tools/call` 自动重发。应用关闭时释放客户端与 stdio 子进程。

### 5.8 `ask_user`

`InteractionTools` 依赖 core 中立接口 `UserInteraction`：

- CLI 装配 `ConsoleUserInteraction`，同步读取一行回答。
- 无人值守入口装配 `UnsupportedUserInteraction`，立即返回“不支持交互”，不得无限等待。
- 工具限制问题长度和回答长度，不记录终端控制字符。

当前运行时 Bean 是进程级装配，而渠道能力与单次调用相关；因此本 feature 将交互能力放入调用上下文或等价的可替换解析器，确保 CLI 可交互而 HTTP/定时调用不会误读进程 stdin。具体上下文承载方式由 plan 在不复制 AgentService 链路的前提下确定。

### 5.9 `web_search`

`WebSearchTools` 依赖中立 `SearchProvider`，核心实现使用无需密钥、能在测试中替换的 provider。正式请求必须先通过 HTTP Sandbox；搜索结果只返回标题、URL 和摘要，限制数量与总长度。

课程提到 DuckDuckGo Instant Answer API，可作为默认候选，但它不是通用网页搜索结果 API。plan 研究阶段应验证其能力与稳定性；若不能满足“网页检索”，默认实现应改为显式可配置 provider，未配置时清晰失败，测试使用 fake provider，不能用伪造结果冒充真实搜索。

## 6. 配置与装配

在既有 `oryxos.http.allowed-domains` 基础上增加聚焦配置，命名在 Spec-Kit plan 中统一：

- 文件读/写允许根、单文件与搜索上限；工作区路径按运行时解析。
- Shell executable 白名单、默认/最大超时、输出上限。
- HTTP 请求/响应大小、超时和允许 header。
- MCP 配置路径、初始化与调用超时。
- 搜索 provider 与结果上限。

HTTP 和 Shell 空白名单默认拒绝；文件额外白名单为空时仍保留当前 Agent 目录读写与已绑定 Skill 目录只读这两个内建最小边界。Boot 创建一个 Registry，先注册内置与 Java 工具，再由 MCP 服务增加外部工具；`notify` 继续复用第 19 节实现。CLI `oryxos tool list` 从 Registry 读取稳定排序结果。

## 7. 错误、审计与保密

- 参数结构错误、越界、超时、外部失败都转换为无密钥、无堆栈的 ToolResult。
- `ToolExecutor` 对每个模型发起的调用只写一条最终 `tool_invocations`；工具内部不得另建审计旁路。
- 文件内容、HTTP body、Shell 输出和 MCP 结果会进入模型上下文，必须执行尺寸限制，但不进行未经需求定义的内容审查。
- 环境变量、HTTP Authorization、Webhook URL、MCP header 和完整进程环境不得进入日志、异常、审计结果或 CLI 输出。

## 8. 验收与测试

### 契约和注册

- `OryxToolContractTest` 参数化遍历 Registry，断言名称、描述和合法 JSON Schema 非空。
- `ToolRegistryTest` 覆盖三种来源、全局重名、稳定列表，以及 `Profile.tools` / `mcp_servers` 精确可见性。
- 验证 PromptBuilder 与 ToolExecutor 使用同一有效工具集，Spring AI 自动执行仍关闭。

### 内置工具

- `FileToolsTest` 覆盖六个工具的正常行为、未知字段、遍历、符号链接逃逸、未绑定 Skill、唯一编辑、二进制跳过和 200 条截断。
- `ShellToolsTest` 覆盖 argv 直传、白名单、工作目录、超时、进程终止和输出截断。
- `HttpToolsTest` 使用本地回环服务覆盖 GET/POST、Sandbox 先行、header 限制、重定向、非 2xx、超时和响应上限。
- `InteractionToolsTest` 覆盖 CLI 回答及无人值守快速失败。
- `WebSearchToolsTest` 使用 fake provider 覆盖查询转发、Sandbox、结果限制与失败。

### Java 与 MCP

- `AnnotatedToolAdapterTest` 覆盖 schema、严格参数绑定、调用结果、异常脱敏和重复名。
- `McpConfigLoaderTest` 覆盖严格 YAML、占位符、重复 server、非法 transport、配置/授权错误 fail-fast 与秘密不泄露。
- `McpToolAdapterTest` 覆盖参数原样转发、内容映射、超时和副作用调用不自动重试。
- `McpClientServiceTest` 使用 mock client 覆盖 `tools/list` 注册、纯连接故障的单 server 隔离、其他来源不受影响和资源关闭。

### 集成与交付门禁

- Boot 集成测试验证 Registry 中包含本 feature 的 11 个新增工具与既有 `notify`，CLI `tool list` 可见。
- 使用真实 `ToolExecutor` 验证成功、拒绝和失败均只有一条审计，未授权工具不执行。
- 运行聚焦模块测试、全仓 `mvnw.cmd test`、`mvnw.cmd clean verify`、Checkstyle、SpotBugs、JaCoCo 和模块边界测试。
- 无真实 MCP server、搜索服务或外网时，只报告 mock/回环验证通过；真实外部端到端单独标记。

## 9. Spec-Kit 产物与实施顺序

设计批准后创建 `005-tool-system`，按以下顺序生成并审批 Spec-Kit 产物：

1. `spec.md`：用户故事、边界条件、功能需求、成功标准和 requirements checklist。
2. `plan.md` / `research.md`：Spring AI `@Tool` 与 MCP 1.1.2 API 验证、Sandbox 结构、模块边界和配置模型。
3. `data-model.md` / `contracts/` / `quickstart.md`：Registry、工具输入 schema、MCP 配置和运行示例。
4. `tasks.md`：按 TDD 切分 Registry/Sandbox、内置工具、Java Adapter、MCP、装配、文档与门禁。
5. 实现阶段逐项保留 RED→GREEN 证据，并在所有门禁通过后才声明完成。

## 10. 交由实施者决定的细节

- 只要满足 Spring AI 1.1.2 兼容、schema 可验证且不启用自动执行，可选择其公开 callback/provider API 的具体组合。
- 文件遍历与输出限制的确切默认值可在 plan 中结合测试成本确定，但必须有可配置硬上限。
- MCP 测试 double 的实现形式可选择 fake、mock 或进程内 server；自动测试不得依赖公网或真实凭证。
- 配置类可按职责拆分，只要保持 `oryxos-core` 不反向依赖 tool/boot/storage。

