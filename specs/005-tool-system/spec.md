# Feature Specification: Tool 体系完整交付

**Feature Branch**: `005-tool-system`

**Created**: 2026-10-29

**Status**: Approved

**Input**: User description: "完成第 20 节完整 Tool 交付，采用兼容现有 OryxTool 契约的方案"

**Design Source**: `docs/superpowers/specs/2026-10-29-tool-system-design.md`

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 统一注册并精确暴露工具 (Priority: P1)

作为平台开发者，我可以把内置工具、Java `@Tool` 方法和 MCP 工具注册到同一个工具注册表；每个 Agent 只看见并执行自己声明的工具，ReAct、CLI 与执行层不会各自维护不同清单。

**Why this priority**: 统一注册和双重可见性检查是所有 Tool 能力、安全和审计的共同地基；没有它，后续工具即使能运行也可能绕过 Agent 授权或无法被模型发现。

**Independent Test**: 注册三种来源的可控测试工具，为两个 Profile 配置不同的 `tools` 与 `mcp_servers`，验证模型工具清单和执行结果严格一致；验证同名、空名或无 schema 的工具不能进入运行时。

**Acceptance Scenarios**:

1. **Given** 注册表包含内置、Java 和两个 MCP server 的工具，**When** 为 Agent 计算有效工具集，**Then** 内置/Java 工具严格等于 `tools` 声明，MCP 工具严格来自 `mcp_servers` 选中的 server。
2. **Given** 模型幻觉调用未授权或未注册工具，**When** 调用进入 `ToolExecutor`，**Then** 工具不执行并产生一条失败审计。
3. **Given** 两个来源注册同名工具，**When** 构建运行时注册表，**Then** 启动明确失败，不按扫描顺序静默覆盖。
4. **Given** 一个 Spring Bean 暴露合法 `@Tool` 方法，**When** 应用装配工具，**Then** 方法名称、说明和参数 schema 被包装为 `OryxTool`，但 Spring AI 不自动执行它。
5. **Given** 已有 `notify` 工具，**When** 查看 `oryxos tool list`，**Then** 它与本 feature 的工具来自同一注册表且按名称稳定排序。

---

### User Story 2 - Agent 安全使用完整基础工具集 (Priority: P1)

作为 Agent 使用者，我可以让 Agent 读取、写入、编辑和搜索允许范围内的文件，执行管理员批准的程序，并调用允许域名的 HTTP 接口；越界、注入、超时或过量输出会被明确拒绝，且不会先产生副作用。

**Why this priority**: 文件、Shell 和 HTTP 是 Agent 真正操作外部世界的基础能力，也是第 20 节的主要可演示成果与最高风险入口。

**Independent Test**: 在临时 `.oryxos` 工作区、受控本地进程和回环 HTTP 服务中，对 11 个新增内置工具分别执行正常和越界用例，不依赖外网或真实凭证。

**Acceptance Scenarios**:

1. **Given** Agent 自有目录中的 UTF-8 文件，**When** 调用 `read_file`、`write_file`、`list_dir` 或 `edit_file`，**Then** 操作成功，且 `edit_file` 只在旧文本恰好出现一次时修改。
2. **Given** Agent 绑定一个公共 Skill，**When** `read_file` 读取该 Skill 的真实 `SKILL.md`，**Then** 允许读取；读取未绑定 Skill 或写入任何公共 Skill 时拒绝。
3. **Given** 目录含文本、二进制文件和大量匹配项，**When** 调用 `grep` 或 `glob`，**Then** 跳过不可读文本、稳定返回最多 200 条并明确标记截断。
4. **Given** 管理员允许某 executable，**When** `shell` 以结构化 argv 和允许的工作目录执行，**Then** 不经过系统 Shell并返回退出码与有界输出。
5. **Given** 命令不在白名单、工作目录越界或进程超时，**When** 调用 `shell`，**Then** 拒绝或终止进程树，不把完整环境变量写入结果或日志。
6. **Given** URL 在 HTTP 白名单内，**When** 调用 `http_get` 或 `http_post`，**Then** 使用经过批准的同一个 URI 发送一次请求，并以有界结果返回。
7. **Given** 请求携带认证/路由敏感 header、目标越界、发生重定向或返回非 2xx，**When** 调用 HTTP 工具，**Then** 请求在相应安全边界停止并返回脱敏失败。

---

### User Story 3 - 通过 MCP 扩展企业能力 (Priority: P2)

作为业务集成者，我可以在 `.oryxos/mcp_servers.yaml` 配置获授权的本地或远程 MCP server；OryxOS 启动时发现其工具并统一注册，单个已授权 server 的临时失联不会拖垮其他能力。

**Why this priority**: MCP 是零代码和轻代码 Plugin Tool 的主路径，但依赖统一注册与安全执行基础。

**Independent Test**: 使用 mock 或进程内 MCP client 模拟 `tools/list` 与 `tools/call`，覆盖健康 server、配置错误、安全拒绝、连接失败、结果映射和资源关闭，全程不访问公网。

**Acceptance Scenarios**:

1. **Given** 一个合法且获授权的 MCP server，**When** 初始化完成，**Then** `tools/list` 返回的每个工具都保留名称、说明和 schema，并以该 server 来源注册。
2. **Given** Agent 的 `mcp_servers` 选择该 server，**When** 模型调用其工具，**Then** JSON 对象参数原样转发，文本结果进入 ReAct 上下文且调用只产生一条审计。
3. **Given** server 配置格式错误、环境变量缺失、URL 被 HTTP Sandbox 拒绝、stdio command 未授权或工具名冲突，**When** 应用启动，**Then** 以脱敏配置错误失败。
4. **Given** 配置和授权均有效但单个 server 连接超时、拒绝连接或 `tools/list` 远端失败，**When** 应用启动，**Then** 只记录脱敏 WARN 并跳过该 server，其他工具继续可用。
5. **Given** 有副作用的 `tools/call` 发生超时或协议错误，**When** Adapter 返回失败，**Then** Adapter 不自动重发调用。

---

### User Story 4 - 支持人工确认与网络检索 (Priority: P2)

作为 Agent 使用者，我可以在交互式 CLI 中回答 Agent 的问题，也可以让 Agent 通过可替换搜索 Provider 检索网页；无人值守入口不会误读 stdin，未配置真实搜索服务时不会伪造结果。

**Why this priority**: `ask_user` 和 `web_search` 是第 20 节明确补齐的扩展工具，使基础工具面达到课程定义的完整范围。

**Independent Test**: 用内存输入输出和 fake SearchProvider 验证交互、无人值守拒绝、搜索转发、HTTP Sandbox 与结果上限，不依赖真实搜索引擎。

**Acceptance Scenarios**:

1. **Given** 当前调用来自交互式 CLI，**When** Agent 调用 `ask_user`，**Then** 问题显示一次并把用户的一行回答作为工具结果返回。
2. **Given** 当前调用来自 HTTP、定时任务或其他无人值守入口，**When** Agent 调用 `ask_user`，**Then** 立即返回“不支持交互”，不读取进程 stdin、不无限等待。
3. **Given** 已配置 SearchProvider 且其目标通过 HTTP Sandbox，**When** Agent 调用 `web_search`，**Then** 返回有界的标题、URL 和摘要列表。
4. **Given** 未配置可用 SearchProvider 或 Provider 失败，**When** Agent 调用 `web_search`，**Then** 返回明确失败，不生成模拟搜索结果。

### Edge Cases

- 工具名、说明或 input schema 为空，schema 不是合法 JSON object，或同名工具来自不同来源。
- `Profile.tools` 含未知工具、`mcp_servers` 含未知/失联 server，或模型直接调用未出现在本轮提示词中的工具。
- JSON 参数为空、不是对象、含未知字段、类型错误、尾随第二个 JSON 值或超过输入大小上限。
- 文件路径为空、绝对路径、盘符/UNC、包含 `..`，目标不存在、父目录不存在、途中为符号链接，或创建目标的最近存在父目录越界。
- `.link` 被修改、删除、断开或指向公共 Skill 根外；同一运行进程下一轮必须重新解析并应用新边界。
- `edit_file` 旧文本零次或多次匹配、替换后超过大小上限，或原子替换不受平台支持。
- `grep` 正则非法、目录循环、文件在遍历中被删除，或搜索达到文件数/字节数/结果数硬上限。
- Shell executable 使用相对路径、同名不同路径、Windows 扩展名差异，参数含 Shell 元字符，子进程忽略中断，或 stdout/stderr 同时持续输出。
- HTTP URL 含 user-info、fragment、非法端口、IDN/后缀混淆、回环/私网目标、重定向，header 大小写混淆或响应无限流。
- MCP YAML 重复 key、未知字段、server 重名、环境占位符为空、远端返回重复工具名、非文本内容或超大结果。
- CLI 与 HTTP 调用并存时，交互能力不得在线程或 Session 间串用。
- 工具执行成功但审计持久化失败时，保持现有 fail-loud 语义，不伪装为已审计成功。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供统一 `ToolRegistry`，注册信息至少包含 `OryxTool`、来源类型和可选 MCP server 名。
- **FR-002**: Registry MUST 拒绝空白工具名、空白描述、空白或非法 JSON input schema 以及任何全局同名注册。
- **FR-003**: 系统 MUST 以同一 Registry 为 PromptBuilder、ToolExecutor 和 CLI 提供工具数据，不得维护行为不同的副本。
- **FR-004**: 内置与 Java 工具 MUST 仅按 `Profile.tools` 精确可见；MCP 工具 MUST 仅按 `Profile.mcpServers` 选中的 server 可见。
- **FR-005**: ToolExecutor MUST 在执行前按当前 Profile 再次检查 Registry 可见性，并继续统一处理超时、错误、ProfileContext 和单次最终审计。
- **FR-006**: 系统 MUST 保持 Spring AI 自动工具执行关闭，所有来源的真实调用只能经过 ToolExecutor。
- **FR-007**: 系统 MUST 发现显式 Java `@Tool` 方法，复用 Spring AI 1.1.2 的 schema 生成能力并包装成 `OryxTool`。
- **FR-008**: Java Adapter MUST 严格绑定 JSON 对象参数、拒绝未知字段和不支持的方法签名，并对反射/业务异常脱敏。
- **FR-009**: 系统 MUST 注册 `read_file`、`write_file`、`list_dir`、`edit_file`、`grep` 和 `glob` 六个文件工具。
- **FR-010**: 文件工具相对路径 MUST 以当前 Agent 目录解析；当前 Agent 目录默认可读写，额外文件白名单为空时不得放行其他普通目录。
- **FR-011**: 文件读取 MUST 只允许当前 Agent 目录、管理员额外允许根和当前 Agent 已绑定公共 Skill 的真实目录；公共 Skill 目录 MUST 只读。
- **FR-012**: 文件边界 MUST 拒绝目录穿越、盘符/UNC 绕过、原生链接逃逸和未绑定 Skill，并与 ContextLoader 复用同一 `.link` 解析结果/契约。
- **FR-013**: `edit_file` MUST 只在 `old_text` 恰好出现一次时修改；失败时原文件一个字节都不改变。
- **FR-014**: `grep` 与 `glob` MUST 稳定排序、最多返回 200 条、明确标记截断，并受文件数、单文件和总读取量硬上限约束。
- **FR-015**: 文件写入 MUST 避免暴露部分内容；平台支持时使用同目录原子替换，不支持时使用可验证的安全回退并明确测试。
- **FR-016**: 系统 MUST 注册 `shell`，输入为 `executable`、字符串 `arguments`、可选工作目录和超时，不接受整段 Shell 命令字符串。
- **FR-017**: Shell MUST 使用 ProcessBuilder argv 直传，不解释管道、重定向、变量、命令替换或命令分隔符。
- **FR-018**: Shell MUST 校验 executable 白名单和工作目录文件边界，限制超时及 stdout/stderr，超时后终止可达进程树。
- **FR-019**: 系统 MUST 注册 `http_get` 和 `http_post`，在发送前通过 HTTP Sandbox 并使用其返回的同一个规范化 URI。
- **FR-020**: HTTP 工具 MUST 默认不跟随重定向，并限制请求体、响应体、连接/读取时间和允许 header。
- **FR-021**: HTTP 工具 MUST 禁止模型提供 `Authorization`、`Proxy-Authorization`、`Cookie`、`Set-Cookie`、`Host` 等凭证或路由敏感 header；本 feature 不实现命名凭证。
- **FR-022**: HTTP 与搜索外发 MUST 默认拒绝未配置域名，并拒绝非法绝对 URI、host 混淆及白名单绕过。
- **FR-023**: 系统 MUST 从 `.oryxos/mcp_servers.yaml` 严格加载 MCP server 配置，并拒绝重复 key、未知字段、重复 server 和不支持 transport。
- **FR-024**: MCP stdio command MUST 经 Shell 白名单，远程 URL MUST 经 HTTP Sandbox；环境/header 中的秘密只能从严格 `${ENV_VAR}` 占位符解析。
- **FR-025**: MCP 的本地配置或授权错误 MUST 阻止启动；已授权 server 的纯连接/远端可用性故障 MUST 只跳过该 server。
- **FR-026**: McpClientService MUST 将 `tools/list` 结果包装注册，McpToolAdapter MUST 原样转发 JSON 对象并安全映射结果。
- **FR-027**: MCP Adapter MUST NOT 对 `tools/call` 自动重试；关闭应用时 MUST 释放所有客户端和 stdio 子进程。
- **FR-028**: 系统 MUST 注册 `ask_user` 并通过中立 UserInteraction 契约解析当前调用的交互能力。
- **FR-029**: CLI 调用 MUST 支持一问一答；HTTP、定时和其他无人值守调用 MUST 快速失败且不得读取 stdin。
- **FR-030**: 系统 MUST 注册 `web_search` 并通过中立 SearchProvider 返回有界标题、URL、摘要；真实请求必须经过 HTTP Sandbox。
- **FR-031**: SearchProvider 未配置或失败时 MUST 明确失败，不得伪造搜索结果；自动测试 MUST 使用 fake provider。
- **FR-032**: 所有 11 个新增工具 MUST 严格拒绝未知字段、错误类型、非对象/尾随 JSON 和超过硬上限的输入。
- **FR-033**: 所有工具错误 MUST 不包含环境变量、认证 header、Webhook URL、MCP header、外部响应正文、堆栈或数据库细节。
- **FR-034**: `oryxos tool list` MUST 从 Registry 返回全部已注册工具的稳定名称和说明，包含既有 `notify`。
- **FR-035**: 系统 MUST 保持 `.link` 为公共 Skill 唯一绑定真相源，不恢复 frontmatter `skills:` 或把 Agent/Skill 目录注册为 Tool。
- **FR-036**: 本 feature MUST NOT 提供占位 `save_memory` / `recall_memory`；它们由独立 Memory 能力通过同一 Registry 接入。
- **FR-037**: 无需真实凭证的验收 MUST 使用临时目录、受控进程、回环 HTTP 与 mock/fake MCP/Search；真实外部调用单独标记。

### Key Entities

- **ToolRegistration**: 一个运行时工具及其来源元数据；包含工具契约、来源类型和可选 MCP server 名。
- **ToolRegistry**: 全局唯一工具目录，负责注册不变量、稳定发现和按 Profile 计算有效工具集。
- **Sandbox Action/Decision**: 文件、Shell 或 HTTP 操作的待审批请求及规范化批准结果；执行方必须使用批准值。
- **MCP Server Definition**: 来自工作区 YAML 的外部 server 定义，包含名称、传输、超时和受控的启动/连接配置。
- **User Interaction Context**: 当前调用是否支持与人同步交互的能力描述，不与某个全局 stdin 永久绑定。
- **Search Result**: 返回模型的有界网页条目，包含标题、URL 和摘要。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Registry 契约测试对所有已注册工具的名称、说明和合法 input schema 覆盖率为 100%，三种来源重名测试全部失败启动。
- **SC-002**: 针对两个差异 Profile，PromptBuilder 展示的工具集合与 ToolExecutor 实际允许集合 100% 一致，未授权工具执行次数为 0。
- **SC-003**: 11 个新增内置工具均至少有“正常成功”和“安全/边界拒绝”自动化测试，且无需公网、真实密钥或人工输入即可通过。
- **SC-004**: 文件路径穿越、链接逃逸和未绑定 Skill 的参数化用例全部拒绝；拒绝用例的目标文件修改次数为 0。
- **SC-005**: Shell 元字符作为普通 argv 传递而不被解释；超时测试在配置上限内结束并确认子进程不再存活。
- **SC-006**: HTTP Sandbox 拒绝和禁止 header 用例产生 0 次网络请求；成功 GET/POST 各产生且仅产生 1 次回环请求。
- **SC-007**: MCP harness 证明健康 server 工具可调用、一个纯连接故障不影响其他工具、配置/授权错误 fail-fast、一次 tools/call 最多发送 1 次。
- **SC-008**: 无人值守 `ask_user` 在 1 秒内失败且 stdin 读取次数为 0；CLI 用例恰好读取并返回 1 行。
- **SC-009**: 工具错误、日志和审计结果对预置 canary secret 的明文出现次数为 0。
- **SC-010**: `mvnw.cmd test` 与 `mvnw.cmd clean verify` 全部通过，不降低现有 Checkstyle、SpotBugs、JaCoCo 和模块边界门禁。

## Assumptions

- 课程第 20 节的“完整交付”指本 spec 中 6 个基础工具、5 个扩展工具、三来源注册、MCP 与 harness；Memory 两个工具随 US-3 Memory 功能交付。
- 当前 Spring AI/Spring AI Alibaba 锁定版本保持 1.1.2/1.1.2.2；plan 研究只选择兼容 API，不主动升级版本。
- MCP 传输只采用当前锁定 SDK 经测试稳定支持的 stdio 与一种远程传输；不自行实现 MCP 协议或自动传输探测。
- 文件工具面向 UTF-8 文本；二进制写入、目录创建和删除不属于本 feature。
- HTTP 工具适合无需认证的通用接口；需认证的企业能力优先通过 MCP 接入。
- 应用层 Sandbox 是进程内最小权限控制，不提供容器、网络命名空间或多租户隔离。
- 真实搜索 Provider 若无法在无需凭证条件下可靠提供网页结果，可要求管理员显式配置；未配置时 fail-loud。

