# Feature Specification: Notify 渠道管理完整闭环

**Feature Branch**: `004-notify-channel-management`

**Created**: 2026-10-28

**Status**: Implemented

**Input**: User description: "使用渠道管理完整闭环，但是19节课程要求要满足"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 管理全局通知渠道 (Priority: P1)

作为运维人员，我可以通过统一 REST API 创建、查看、修改和删除全局通知渠道，使 Agent 只需引用稳定的渠道名称，无需接触 Webhook 地址或凭据。

**Why this priority**: 没有可管理的真实渠道，发送链路无法配置，也不构成完整闭环。

**Independent Test**: 在临时 SQLite 数据库中通过 API 完成一个 `webhook` 渠道的 CRUD，重启上下文后仍能读取；列表和详情只返回脱敏 URL，重复名称、非法类型和不存在资源返回稳定状态码。

**Acceptance Scenarios**:

1. **Given** 注册表为空，**When** 创建合法的 `webhook` 渠道，**Then** 返回 201，并可通过列表和详情读取脱敏后的渠道信息。
2. **Given** 渠道已持久化，**When** 应用上下文重启，**Then** 渠道名称、类型、真实内部配置和创建顺序保持不变。
3. **Given** 渠道已存在，**When** 使用脱敏 URL 或空 URL 更新描述，**Then** 保留真实 URL；提交新的合法 URL 时替换它。
4. **Given** 同名渠道已存在，**When** 再次创建或发生并发同名创建，**Then** 返回 409，数据库中仍只有一条记录。
5. **Given** 渠道不存在，**When** 查询、更新或删除，**Then** 返回 404，不创建隐式数据。

---

### User Story 2 - Agent 安全推送纯文本通知 (Priority: P1)

作为业务 Agent，我可以调用 `notify(content, channel?)`，让运行时按全局名称解析渠道、先执行 Sandbox 白名单校验，再通过通用 Webhook 推送纯文本，并由现有工具链记录审计。

**Why this priority**: 这是 Notify 的核心业务价值，也是第 19 节要求的主动出站能力。

**Independent Test**: 以本地假 Webhook、显式白名单和一个带 `notify` 可见工具的 Profile，经真实 `ToolExecutor` 执行一次通知；验证只产生一次 POST、一次工具审计，且消息体、渠道解析和调用顺序符合契约。

**Acceptance Scenarios**:

1. **Given** 指定渠道存在且目标在白名单，**When** 调用 `notify`，**Then** 发送一次 JSON POST `{"content":"..."}` 并返回成功。
2. **Given** 调用未提供或提供空白 `channel`，**When** 注册表非空，**Then** 选择最早创建的渠道；同一时间戳时按名称升序稳定选择。
3. **Given** 注册表为空或显式渠道不存在，**When** 调用 `notify`，**Then** 返回明确失败且不发起网络请求。
4. **Given** 目标不在白名单或 URI 非法，**When** 调用 `notify`，**Then** Sandbox 在 Adapter 前拒绝，网络请求次数为零。
5. **Given** Webhook 返回 4xx/5xx 或网络异常，**When** 调用 `notify`，**Then** 不静默成功、不泄露 URL 或响应体，并由 `ToolExecutor` 记录失败审计。

---

### User Story 3 - 扩展通知渠道而不修改调用方 (Priority: P2)

作为平台开发者，我可以新增一个实现相同中立接口的渠道 Adapter，并通过显式类型映射接入，而不修改 `NotifyTools`、Agent Profile 或 ReAct 链路。

**Why this priority**: 第 19 节强调接口先行；核心阶段虽只交付 Webhook，也必须守住后续扩展边界。

**Independent Test**: 用测试 Adapter 注册第二种类型，验证按 `channelType` 正确路由；重复类型在启动/构造时失败，未知类型在管理入口被拒绝。

**Acceptance Scenarios**:

1. **Given** 两个不同类型 Adapter，**When** 发送到各自类型渠道，**Then** 仅匹配 Adapter 被调用，`NotifyTools` 无类型分支。
2. **Given** 两个 Adapter 声明相同类型，**When** 构造 Adapter 注册表，**Then** 立即失败，不按扫描顺序静默选择。
3. **Given** 核心阶段只开放 `webhook`，**When** API 创建 `feishu`、`wecom` 或其他未实现类型，**Then** 返回 400，不持久化不可发送渠道。

### Edge Cases

- `content` 缺失、空白、非字符串、超过 32 KiB，或输入包含未知字段时拒绝且零网络请求。
- 渠道名称为空、超过 64 字符、包含控制字符、路径分隔符、盘符/UNC 形态或 Windows 设备名时拒绝。
- URL 为相对地址、非 HTTP(S)、含 user-info/fragment、无 host、非法端口或 host 混淆时拒绝。
- 白名单为空时默认拒绝；`example.com` 不得放行 `evil-example.com`，允许子域的规则必须是标签边界匹配。
- 删除当前缺省渠道后，下一个最早创建渠道成为缺省；删除最后一个后缺省发送明确失败。
- 管理 API 对 URL 和 `config` 中的敏感键只返回掩码，日志与 Tool 结果不出现真实值。
- URL 可保存为严格的 `${ENV_VAR}` 占位符；发送时变量缺失或解析后不是合法 HTTP(S) URI，必须明确失败且零网络请求。
- Webhook 返回重定向时不得绕过已校验目标；核心阶段不自动跟随到未校验地址。
- Adapter 返回、抛出或 HTTP 客户端产生异常时，工具调用只记录一次最终审计，不旁路重试发送。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在 `oryxos-core` 提供不依赖基础设施的 `NotifyChannel` 与 `NotifyChannelRegistry` 契约。
- **FR-002**: 系统 MUST 通过 SQLite 和 Flyway 迁移持久化全局通知渠道，字段至少包含 `name`、`type`、`url`、`description`、`config`、`createdAt`、`updatedAt`。
- **FR-003**: 系统 MUST 提供 `/api/v1/notify-channels` 的 POST、GET 列表、GET 单项、PUT 和 DELETE 管理端点，并复用 `ApiResponse` 与统一异常格式。
- **FR-004**: 系统 MUST 将创建成功映射为 201、删除成功映射为 204、校验失败映射为 400、缺失映射为 404、名称冲突映射为 409。
- **FR-005**: 系统 MUST 按 `createdAt ASC, name ASC` 返回渠道并用首项作为缺省渠道；更新不得改变创建顺序。
- **FR-006**: 系统 MUST 在列表、详情、异常和日志中隐藏真实 Webhook URL 及敏感 config；编辑时掩码原样提交或空值表示保留旧值。
- **FR-007**: 核心阶段 API MUST 只接受有可用 Adapter 的 `webhook` 类型，未知或未实现类型不得持久化。
- **FR-008**: 系统 MUST 提供 `NotifyTarget` 与中立的 `NotifyChannelAdapter.send(NotifyTarget,String)`；类型选择通过显式 Adapter Registry 完成。
- **FR-009**: `WebhookNotifyAdapter` MUST 从 `NotifyTarget.config.url` 获取地址并发送一次 `application/json` POST，body 为 `{"content": content}`。
- **FR-010**: Webhook 非 2xx、重定向、网络故障和非法响应 MUST 作为失败向上返回，不得静默吞掉或在 Adapter 内自动重试。
- **FR-011**: 系统 MUST 注册名为 `notify` 的 `OryxTool`；输入 schema 中 `content` 必填、`channel` 可选，且严格拒绝未知字段和非法值。
- **FR-012**: `channel` 缺失或空白时 MUST 使用缺省渠道；显式名称按精确名称解析；无渠道或不存在时 MUST 明确失败。
- **FR-013**: 每次发送 MUST 在 Adapter 前通过 HTTP Sandbox 校验同一个规范化 URI；拒绝时 Adapter 调用次数和网络请求次数 MUST 为零。
- **FR-014**: Sandbox MUST 默认拒绝，只允许配置的精确 host 或标签边界子域，并拒绝相对 URI、非 HTTP(S)、user-info、fragment、无 host 和非法端口。
- **FR-015**: `notify` MUST 仅通过现有 `ToolExecutor` 执行，继续受 Profile `tools` 可见性、超时、错误处理和 `tool_invocations` 审计约束。
- **FR-016**: 系统 MUST 保持 Spring AI 自动工具执行关闭，不引入 Controller、Adapter 或其他直接发送旁路。
- **FR-017**: 系统 MUST 保持 `AGENT.md` frontmatter 的 `notify_channels` 为废弃字段，不恢复第二配置源。
- **FR-018**: Adapter 类型重名 MUST 在装配期失败；新增不同类型 Adapter 不得要求修改 `NotifyTools`。
- **FR-019**: Tool 与 API 的失败内容 MUST 不包含真实 URL、token、响应 body、堆栈或数据库细节。
- **FR-020**: 所有无需真实外部凭据的验收 MUST 使用本地可控 Webhook 和临时数据库执行；真实渠道验证必须单独标记。
- **FR-021**: 渠道 URL MUST 支持精确 `${ENV_VAR}` 占位符并仅在发送时从进程环境解析；不得把解析后的值写回数据库、API、日志或对话。

### Key Entities

- **NotifyChannel**: 全局可管理通知目标，名称是 Agent 和 Tool 的稳定引用；类型决定 Adapter；URL/config 只在运行时内部以真实值存在。
- **NotifyTarget**: 发送时传给 Adapter 的不可变目标快照，包含 `channelType` 与只读配置，不暴露持久化实现。
- **NotifyChannelAdapter**: 把纯文本内容送到某类通知目标的扩展契约。
- **SandboxAction**: 发送前需要授权的 HTTP 目标动作，携带规范化 URI。
- **Notify Tool Invocation**: 现有工具调用审计中的一次 `notify` 执行，关联 Session、输入、结果、状态和耗时。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 在无外网和真实密钥环境中，渠道 CRUD、重启恢复、脱敏和本地 Webhook 发送测试 100% 自动通过。
- **SC-002**: 每次成功通知恰好产生 1 次网络 POST 和 1 条成功工具审计；每次 Sandbox 拒绝产生 0 次网络请求和 1 条失败工具审计。
- **SC-003**: 第 19 节验收点全部自动化：URL 来自目标配置、body 含 content、5xx 失败、未配置明确失败、缺省首渠道、`enforce` 先于 `send`。
- **SC-004**: 针对 10 类恶意/非法 URI 的参数化测试全部被 Sandbox 拒绝，且不存在后缀混淆放行。
- **SC-005**: 管理 API 的所有响应、日志捕获和 Tool 结果扫描中，预置 Webhook token 的明文出现次数为 0。
- **SC-006**: 新增测试 Adapter 时只新增 Adapter/测试代码，`NotifyTools` 源文件改动为 0。
- **SC-007**: `mvnw.cmd test` 与 `mvnw.cmd clean verify` 均通过，不降低现有 Checkstyle、SpotBugs、JaCoCo 或集成测试门禁。

## Assumptions

- 核心阶段通用 Webhook 使用固定 `{"content":"..."}` 载荷；各企业 IM 的专有纯文本格式留给专用 Adapter。
- 缺省渠道以最早创建记录表达旧课件中的“第一个渠道”，以名称作为同时间戳稳定次序。
- 当前功能不含管理台 UI、SMTP、短信、签名算法和富文本卡片。
- Webhook URL 本身视为敏感凭据，API 仅返回可识别但不可使用的掩码。
- 部署配置优先保存 `${ENV_VAR}` 占位符；测试可使用本地回环 URL 验证协议。
- 应用层 Sandbox 是出站策略控制，不等同容器级或网络层隔离。
