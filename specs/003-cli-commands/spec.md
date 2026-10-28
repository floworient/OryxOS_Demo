# Feature Specification: 第 18 节 CLI 命令

**Feature Branch**: `003-cli-commands`

**Created**: 2026-10-28

**Status**: Draft

**Input**: User description: "按照第 18 节实现 OryxOS 的 12 个 CLI 命令，轻重命令分流，复用既有 Agent 运行链路与持久化，不提前伪造后续章节能力。"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 初始化并管理本地工作区 (Priority: P1)

作为本地开发者，我可以初始化 OryxOS 工作区、查看其状态，并创建、查看、列出或归档 Agent，从而在不启动完整运行时的情况下完成日常配置管理。

**Why this priority**: 工作区和 Agent 是其余命令的前置条件；轻命令必须快速、安全且可重复执行。

**Independent Test**: 在空临时目录中依次执行 `init`、`status`、`profile create/list/show/delete`，即可验证目录、模板、幂等性、归档和错误处理，不需要模型凭据或网络。

**Acceptance Scenarios**:

1. **Given** 当前目录尚无工作区，**When** 用户执行 `oryxos init`，**Then** 系统创建完整基础结构和默认文件，并报告创建结果。
2. **Given** 工作区已经包含用户修改过的文件，**When** 用户再次执行 `oryxos init`，**Then** 系统保留所有现有内容，只补齐缺失项。
3. **Given** 已初始化工作区，**When** 用户创建、列出或查看 Agent，**Then** 命令只操作工作区 `agents/` 下合法的 Agent 目录，并返回与磁盘一致的结果。
4. **Given** 某 Agent 存在，**When** 用户执行 `profile delete <name>`，**Then** 系统将整个 Agent 目录归档且不再把它列为活动 Agent，归档内容仍可恢复。

---

### User Story 2 - 在终端与 Agent 连续对话 (Priority: P1)

作为使用者，我可以选择一个 Agent 进入交互式对话，多轮输入共享同一会话；每条消息都经过既有 Agent 处理链路，输入 `/quit` 或结束标准输入后安全退出。

**Why this priority**: `chat` 是第 18 节首个可直接体验 ReAct 内核的入口，也是 CLI 的核心价值。

**Independent Test**: 使用可控 Provider 和已注册 Agent 启动 `chat`，输入两条消息后退出；验证回复顺序、会话身份、持久化数据和调用审计均来自同一处理链路。

**Acceptance Scenarios**:

1. **Given** 默认 Agent 有效，**When** 用户执行 `oryxos chat` 并输入消息，**Then** 系统把消息交给统一 Agent 服务并打印最终回复。
2. **Given** 用户指定 `--profile weather`，**When** 进入对话，**Then** 所有轮次均使用 `weather` Agent，且会话按渠道、用户和 Agent 稳定关联。
3. **Given** 对话正在进行，**When** 用户输入 `/quit` 或标准输入结束，**Then** 命令正常退出，不产生空消息或额外模型调用。

---

### User Story 3 - 启动统一运行模式 (Priority: P2)

作为使用者，我可以通过 `serve` 或 `gateway` 启动当前构建实际具备的服务和渠道，且这些模式与 CLI 对话共享 Agent 配置和会话数据。

**Why this priority**: 运行模式必须有统一入口，但其业务能力仍由对应模块提供，CLI 不复制实现。

**Independent Test**: 使用临时工作区启动每种模式，验证进程保持运行、健康关闭、使用同一工作区，并且未实现的可选渠道不会被宣称为可用。

**Acceptance Scenarios**:

1. **Given** 配置有效，**When** 用户执行 `oryxos serve`，**Then** HTTP 服务在默认端口启动；指定合法端口时使用该端口。
2. **Given** 当前构建只包含部分渠道，**When** 用户执行 `oryxos gateway`，**Then** 系统仅启动实际可用的渠道并如实报告其状态。
3. **Given** 端口被占用或运行时配置非法，**When** 启动重命令，**Then** 命令返回可操作的错误和非零退出状态，并关闭已创建的资源。

---

### User Story 4 - 查询 Provider、Tool 与 Session (Priority: P2)

作为运维或调试人员，我可以查询当前实例真实可见的 Provider、Tool 和会话，以确认配置、能力和历史状态，而不会看到写死的示例数据。

**Why this priority**: 查询命令为问题定位提供最小可观测性，并验证 CLI 与真实注册表和持久化之间的连接。

**Independent Test**: 分别在空数据和预置多条数据的工作区执行三个 `list` 命令，核对输出、排序、空状态、敏感字段过滤及退出状态。

**Acceptance Scenarios**:

1. **Given** 配置中声明了多个 Provider，**When** 用户执行 `provider list`，**Then** 系统列出真实名称和非敏感连接信息，不输出密钥。
2. **Given** 运行时注册了 Tool，**When** 用户执行 `tool list`，**Then** 系统列出当前真实可用的 Tool；没有 Tool 时显示明确空状态。
3. **Given** 会话存储中有历史，**When** 用户执行 `session list`，**Then** 系统按最近活跃时间倒序列出会话标识、Agent、状态和最后活跃时间；空库返回成功并显示空状态。

### Edge Cases

- 工作区根路径不存在、不是目录、不可写，或经过规范化后越出用户指定边界。
- Agent 名称为空、包含路径分隔符、`.`/`..`、绝对路径、盘符、UNC 形式，或与现有归档目标冲突。
- `profile create` 目标已存在、`profile show/delete` 目标不存在，或目标目录/`AGENT.md` 是逃逸边界的操作系统链接。
- `init` 遇到部分结构已存在、文件与预期目录同名、默认文件已由用户修改。
- `chat` 收到空行、前后空白、连续多轮、`/quit`、标准输入结束，或一次处理失败后退出。
- 端口超出有效范围、被占用，重命令启动一半后失败，或进程收到正常终止信号。
- Provider、Tool 或 Session 列表为空；单条数据包含超长名称或非 ASCII 文本；存储中存在无法读取的数据。
- `--help`、`--version`、未知命令、缺少必填参数和互斥参数组合。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供且只把以下 12 条业务命令作为本阶段承诺：`init`、`status`、`chat`、`serve`、`gateway`、`profile list`、`profile create`、`profile show`、`profile delete`、`provider list`、`tool list`、`session list`。
- **FR-002**: 根命令、分组命令和 12 条业务命令 MUST 提供可用帮助；根命令 MUST 提供版本信息，帮助与版本查询不得要求模型密钥。
- **FR-003**: 不依赖 Agent 运行时的轻命令 MUST 不启动完整运行时；需要 Agent、Web、注册表或持久化协作者的重命令 MUST 延迟启动运行时。
- **FR-004**: 所有命令 MUST 使用同一工作区根解析规则：系统属性优先于环境变量，二者均未设置时使用当前目录下的 `.oryxos/`；所有文件访问 MUST 拒绝路径穿越和链接逃逸。
- **FR-005**: `init` MUST 创建 `agents/`、`skills/`、`output/`、`memory/`、`sessions/`、`logs/` 及默认 Bootstrap/用户文件；重复执行 MUST 不覆盖任何现有目录或文件，数据库结构仍由运行时迁移管理。
- **FR-006**: `status` MUST 如实报告工作区初始化状态、活动 Agent 数量和数据库文件状态；缺失项属于可报告状态，不得通过隐式创建掩盖问题。
- **FR-007**: `profile list/create/show/delete` MUST 以 `agents/<name>/AGENT.md` 为唯一活动定义源，创建内容可被既有 Agent 加载器解析，显示内容与磁盘一致，删除操作归档整个目录且不得不可恢复地清除用户数据。
- **FR-008**: Profile 管理 MUST 拒绝非法名称、重复创建、未知 Agent、越界路径和操作系统链接；失败时不得留下半创建或半归档状态。
- **FR-009**: `chat` MUST 支持默认 Agent 与 `--profile <name>`，每轮只调用一次既有统一 Agent 服务；CLI 自身不得执行模型推理、ReAct 编排或 Tool 调用。
- **FR-010**: `chat` MUST 通过会话管理器按 `cli + 当前用户 + Agent` 获取稳定会话，输入 `/quit` 或标准输入结束时不调用 Agent；成功轮次保存的消息和审计 MUST 可跨进程恢复。
- **FR-011**: `serve` MUST 启动当前构建已有的 HTTP 服务并支持默认端口 `8080` 与显式端口；`gateway` MUST 启动当前构建实际支持的常驻渠道。两者 MUST 复用同一运行时和存储，不得在 CLI 中复制业务入口。
- **FR-012**: `provider list`、`tool list`、`session list` MUST 查询真实配置、注册表或持久化数据；未实现或未注册的能力显示明确空状态，不得写死示例条目或把未来能力描述为已可用。
- **FR-013**: Provider 查询 MUST 隐藏凭据；Session 查询 MUST 至少显示会话标识、Agent、状态和最后活跃时间，并按最后活跃时间倒序排列。
- **FR-014**: 命令输出 MUST 将正常结果写到标准输出、错误写到标准错误，并使用稳定退出语义：成功为 `0`，用户输入或用法错误为非零，运行时或外部资源错误为非零。
- **FR-015**: 重命令启动失败或结束时 MUST 关闭其创建的应用上下文、输入资源和后台资源；不得因重复启动或失败路径遗留当前 Agent 上下文。
- **FR-016**: 所有入口 MUST 沿用既有模型路由、受控工具执行与调用审计安全契约，且不得在输出或异常中泄露密钥。
- **FR-017**: 本阶段 MUST 提供自动化验证覆盖命令树、轻重分流、工作区幂等、安全边界、交互退出、会话恢复、真实查询和启动失败清理；不得只依赖人工执行 `--help`。

### Key Entities

- **CLI Command**: 用户可调用的命令路径，包含名称、参数、帮助、输出和退出状态。
- **Workspace**: OryxOS 本地运行根目录，包含 Agent、公共 Skill、输出、记忆、会话与 Bootstrap 文件。
- **Runtime Mode**: 需要按需启动运行时的对话、HTTP 服务或多渠道常驻模式。
- **Session Summary**: 面向查询输出的会话标识、Agent、状态和最近活跃时间，不包含完整敏感上下文。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 12 条业务命令及其根/分组帮助路径 100% 可解析，所有帮助调用均无需有效模型密钥。
- **SC-002**: 在普通本地开发环境中，95% 的 `init`、`status` 和 Profile 管理调用在 1 秒内完成，且运行时启动次数为零。
- **SC-003**: 重复执行 `init` 10 次后，用户预先修改的文件字节保持率为 100%，缺失结构补齐率为 100%。
- **SC-004**: 非法 Profile 路径、绝对路径、盘符、UNC 和链接逃逸用例的越界读写次数为零；归档后的 Agent 文件恢复率为 100%。
- **SC-005**: 两轮 CLI 对话的回复顺序、会话三元组和持久化历史关联正确率为 100%；`/quit` 与输入结束导致的额外 Agent 调用次数为零。
- **SC-006**: `chat`、`serve`、`gateway` 的启动失败测试中，资源清理率为 100%，敏感信息泄漏数为零。
- **SC-007**: Provider、Tool 和 Session 查询结果与其真实来源逐项一致，空集合误报条目数为零，Session 排序错误数为零。
- **SC-008**: 相关模块测试、全量测试和打包验证全部通过；无真实密钥或网络时，所有必需验收均可由受控桩完成。

## Assumptions

- 本 feature 以第 18 节课程列出的 12 个命令为边界；根命令 `oryxos`、`profile`/`provider`/`tool`/`session` 分组节点不额外计数。
- CLI 面向单机、受信任的企业内网部署；本阶段不新增认证、RBAC、远程管理或多租户隔离。
- `serve` 仅启动当前构建已有的 HTTP 能力；新增完整 REST 端点属于后续 feature。
- `gateway` 只挂载当前构建真实存在且已配置的渠道；飞书、企微、钉钉的完整接入不在本 feature 内。
- Tool 的具体实现、MCP 接入、Sandbox 扩展不在本 feature 内；`tool list` 只查询真实已注册项。
- 数据库创建和升级继续由既有迁移机制管理；`init` 不手工创建表或替代迁移。
- 自动化测试使用可控 Provider、输入输出流、临时工作区和本地数据库，不把真实外部调用作为通过条件。

