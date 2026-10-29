# Notify 渠道管理完整闭环设计

## 意图与背景

OryxOS 已有 ReAct、统一 `ToolExecutor`、Session 与工具调用审计，但缺少主动把结果送出运行时的能力。此次交付补齐出站通知闭环，使 Agent 能通过 `notify` 内置 Tool，按全局注册名选择通知渠道，经 Sandbox 白名单校验后向 Webhook 发送纯文本消息；运维人员可通过 REST API 管理这些渠道。

第 19 节课件是行为验收依据，但其中 `Profile.notify_channels` 属于旧契约。当前架构明确要求通知渠道持久化在 SQLite 全局注册表中，Agent 只在正文和 Tool 参数中按名称引用。因此本设计保留课件的 Adapter、Webhook、缺省渠道、安全顺序与测试要求，不恢复旧 Profile 字段。

## 目标

- 提供中立的 `NotifyChannelAdapter.send(NotifyTarget, String)` 接口和 `NotifyTarget` 值对象。
- 核心阶段实现 `WebhookNotifyAdapter`，向目标 URL 发送一次 JSON POST，通用载荷为 `{"content":"..."}`。
- 提供实现 `OryxTool` 的 `notify` 内置 Tool，输入包含必填 `content` 和可选 `channel`。
- 渠道通过 SQLite 全局注册表持久化，并通过 `/api/v1/notify-channels` 完成创建、列表、详情、更新和删除。
- 发送前必须先执行 HTTP Sandbox 白名单校验，失败时不得调用 Adapter。
- `notify` 继续经过现有 `ToolExecutor`，复用工具可见性、超时、失败和 `tool_invocations` 审计，不增加旁路。
- 无真实密钥和外网条件下，使用本地假 Webhook 完成协议与完整运行链路验证。

## 非目标

- 本阶段不实现 SMTP、短信、PagerDuty 或富文本卡片。
- 本阶段不实现飞书、企微、钉钉专用载荷或签名算法；这些渠道可在后续新增专用 Adapter。
- 不把 Webhook URL、token 或其他凭据注入 Prompt、Agent 正文或 Profile。
- 不实现管理台页面，也不修改入站 Channel Adapter。
- 不恢复或兼容 `AGENT.md` frontmatter 的 `notify_channels` 字段。

## 约束与反目标

- `oryxos-core` 只放共享领域契约，不依赖 storage、web 或具体 Adapter。
- Tool、Adapter 和 Sandbox 实现在 `oryxos-tool`；JPA/Flyway 在 `oryxos-storage`；REST 入口在 `oryxos-web`；装配在 `oryxos-boot`。
- 数据库结构只通过 `db/migration/{vendor}/` 的 Flyway 脚本演进。
- 所有发送必须经过统一注册、可见性、安全校验、执行编排和审计。任何直接从 Controller 或业务服务调用 Webhook 的旁路都视为失败。
- API 和日志不得回显真实敏感配置；示例不得包含真实 Webhook。
- 渠道 URL 支持保存为严格的 `${ENV_VAR}` 占位符，发送时解析但绝不把真实值写回存储、API、日志或 Prompt。
- 应用层域名白名单只能描述为 Sandbox 策略，不能宣称网络或容器级隔离。

## 方案比较

### 方案一：复刻第 19 节旧 Profile 配置

实现量最小，课件代码几乎可以直接迁移，但会恢复已废弃的 `Profile.notify_channels`，形成 Agent 目录与运行时配置双重真相源，也无法独立管理渠道。否决。

### 方案二：仅实现安全运行时，不提供管理 API

能完成 Adapter、Sandbox、Tool 和审计，但渠道只能通过直接写库或临时配置注入，缺少可用的运维入口，无法形成可演示、可集成闭环。否决。

### 方案三：全局注册表、管理 API 与安全发送闭环

采用当前技术方案的全局注册表，同时把第 19 节的行为验收迁移到新配置模型。范围更大，但各层边界清晰，渠道可管理、发送可审计、协议可测试。用户已选择此方案。

## 领域模型与接口

`oryxos-core` 定义：

- `NotifyChannel`：`name`、`type`、`url`、可空 `description`、不可空 `config`、`createdAt`、`updatedAt`。
- `NotifyChannelRegistry`：创建、按名查询、稳定顺序列表、更新、删除，以及解析缺省渠道。
- `NotifyChannelNotFoundException`、`NotifyChannelConflictException` 等不携带敏感信息的领域错误。

`oryxos-tool` 定义：

- `NotifyTarget(String channelType, Map<String,String> config)`；由服务把持久化实体的 `url` 合并为只读 config 的 `url` 项后交给 Adapter。
- `NotifyChannelAdapter`：`String channelType()` 与 `void send(NotifyTarget target, String content)`；Adapter 注册表按类型显式映射，重复类型启动失败。
- `NotifyTools`：实现 `OryxTool`，工具名固定为 `notify`，schema 中 `content` 必填、`channel` 可选。

核心阶段 API 只接受 `type=webhook`。未知类型在创建或更新时立即返回 400，不把错误延迟到发送阶段。

## 缺省渠道规则

第 19 节要求 `channel` 缺省时取第一个渠道。全局注册表没有 Profile 内列表顺序，因此将“第一个”定义为最早创建的渠道，按 `createdAt ASC, name ASC` 稳定排序；更新不改变创建顺序。注册表为空时返回明确失败，不能把未发送误报为成功。

显式 `channel` 会按精确名称查找；空白字符串按缺省处理。渠道名称采用与现有 Agent 名称一致的安全字符子集，长度 1–64，拒绝控制字符、路径形态和纯空白。

## 发送流程

1. LLM 产生 `notify` ToolCall，仍由 `ToolExecutor` 校验工具已注册且在当前 Profile 的 `tools` 中可见。
2. `NotifyTools` 严格解析 JSON；拒绝缺失/空白 `content`、非字符串字段和未知字段。
3. 按显式名称或缺省规则从 `NotifyChannelRegistry` 读取渠道。
4. 将渠道转换为 `NotifyTarget`；若 URL 是 `${ENV_VAR}`，此时从进程环境解析，缺失或空白则失败。
5. 调用 `Sandbox.enforce(HTTP_REQUEST, url)`；拒绝时立即返回失败，不调用 Adapter。
6. Adapter Registry 按 `type` 选择 `WebhookNotifyAdapter`，发送单次 POST。
7. 2xx 返回成功；4xx、5xx、网络错误和非法响应均作为失败向上返回，不静默吞掉，也不在 Adapter 内重试。
8. `ToolExecutor` 把最终结果和耗时写入现有 `tool_invocations`。输入审计需要对 URL/token 类字段统一脱敏；正常 `notify` 输入只含渠道名和内容，不记录目标 URL。

## Sandbox 规则

- 新增最小 HTTP Sandbox 契约和白名单实现，配置键沿用 `http.allowed_domains` 的语义，由 Boot 显式绑定。
- 只允许绝对 `http`/`https` URI，必须有 host，拒绝 user-info、片段、非法端口和非 HTTP scheme。
- host 采用小写 ASCII 规范化后与白名单精确匹配或受控子域匹配；不得用简单字符串后缀造成 `evil-example.com` 绕过。
- 白名单为空时默认拒绝。测试中的本地假 Webhook 必须显式把 loopback host 加入白名单。
- 校验对象与 Adapter 实际发送的 URI 必须是同一个规范化 URI，避免校验与执行目标分离。

## 持久化与管理 API

SQLite 新增 `notify_channels` 表，`name` 唯一；保存 `type`、`url`、`description`、JSON `config`、`created_at`、`updated_at`。迁移使用下一个 Flyway 版本，不依赖 Hibernate 自动建表。

REST 契约：

- `POST /api/v1/notify-channels`：创建，成功 201；重名 409。
- `GET /api/v1/notify-channels`：按缺省渠道顺序列出。
- `GET /api/v1/notify-channels/{name}`：查询，缺失 404。
- `PUT /api/v1/notify-channels/{name}`：原名更新，缺失 404。
- `DELETE /api/v1/notify-channels/{name}`：删除，成功返回 204。

请求和响应统一使用现有 `ApiResponse`（204 无响应体除外）。请求 DTO 通过 Bean Validation 校验名称、类型、URL、描述和 config。URL 接受绝对 HTTP(S) 地址或精确 `${ENV_VAR}` 占位符。响应不返回任何被判定为敏感的 config 值；`password`、`secret`、`token`、`api_key` 等键返回掩码。Webhook URL 本身通常含 token，视为敏感配置：列表和详情仅返回脱敏 URL（环境变量占位符可原样展示），更新请求若原样提交掩码或留空则保留旧值。Tool 内部使用未脱敏值或发送时解析出的环境变量值。

## 错误与安全语义

- 渠道未配置、显式渠道不存在、类型不支持、Sandbox 拒绝、HTTP 非 2xx均返回可区分但不泄密的失败信息。
- Tool 返回内容不包含 URL、响应 body、token、堆栈或内部数据库信息。
- API 的统一异常处理映射 400/404/409；其他错误保持通用 500 响应，并记录脱敏日志。
- 删除当前缺省渠道后，下一个最早创建渠道自然成为缺省；注册表为空则后续缺省发送明确失败。
- 同名并发创建由数据库唯一约束兜底并映射为 409。

## 测试与验收

第 19 节要求全部保留：

- `WebhookNotifyAdapterTest` 用本地假 Webhook 验证 POST、`{"content":...}`、URL 来自 `NotifyTarget.config`，并验证 5xx 向上失败。
- `NotifyToolsTest` 验证未配置渠道明确失败、缺省选择第一个渠道，以及 Sandbox `enforce` 严格先于 Adapter `send`。
- 接口中立性由不同类型 Adapter 的参数化注册测试保护，新增 Adapter 不修改 `NotifyTools`。

完整闭环额外验证：

- Flyway 创建表、CRUD 跨上下文重启恢复、稳定缺省顺序、并发重名。
- API 的 201/200/204/400/404/409、统一响应、校验与敏感值脱敏/保留语义。
- 非白名单、恶意 URI、子域混淆、空白 content、未知 JSON 字段均不产生网络请求。
- 经真实 `ToolExecutor` 执行一次 `notify`，断言只发送一次且产生一条成功或失败审计记录。
- Boot 上下文中 `notify` 出现在 `runtimeTools`，重复 Adapter 类型或缺少安全配置时按约定失败。
- 聚焦模块测试后运行 `mvnw.cmd test` 与 `mvnw.cmd clean verify`；真实外部 Webhook 仅作为可选人工验收，未执行时明确报告。

## Spec-Kit 交付

设计确认后创建 `004-notify-channel-management`，生成并校验：

- `spec.md`：用户故事、功能需求、边界条件和可量化成功标准。
- `research.md`：课件旧配置与当前全局注册表的取舍、HTTP 客户端测试方式、脱敏和 Sandbox 决策。
- `data-model.md`：领域模型、数据库字段、状态与校验。
- `contracts/`：Tool schema、Adapter 契约和 REST API 契约。
- `plan.md`：模块依赖、装配、测试策略和宪章检查。
- `quickstart.md`：本地假 Webhook、渠道 CRUD、Agent 调用和审计验证。
- `tasks.md`：按测试先行拆解，保留 RED 证据，再实现 GREEN 并执行回归。

## 设计者裁量

- “第一个渠道”采用最早创建且名称作稳定次序的全局渠道，作为旧 Profile 列表顺序在新模型中的等价行为。
- 核心阶段只开放 `webhook` 类型；文档中出现的专用 IM Adapter 属于后续扩展，不提前伪造。
- URL 按敏感值处理，管理 API 展示脱敏值，避免技术方案要求的凭据泄漏。
- 不新增独立审计通道，完全复用 `ToolExecutor`。
