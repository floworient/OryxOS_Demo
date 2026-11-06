# Feature Specification: Web Service 核心能力

**Feature Directory**: `009-web-service-core`

**Created**: 2026-11-04

**Status**: Design Approved

**Input**: 按已批准设计完成 OryxOS US-5 核心 Web Service：薄 Controller + Web 应用服务，提供 6 个 Controller、10 个核心 REST 端点，统一响应、Session 归档代次、60 秒超时、并发保护、OpenAPI 与真实统一 Agent 链路。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 业务系统进行连续 Agent 对话 (Priority: P1)

业务系统可以创建一个可恢复的 Web Session，连续发送消息并查询最近历史；所有消息都进入与 CLI、Scheduler 相同的 AgentService/ReAct/Tool/审计链路。会话结束后可归档，随后同一用户可以开始一个新的会话，同时旧历史仍可查询。

**Why this priority**: 这是 Web Service 的主价值和 Demo 四的完整链路，决定 OryxOS 能否作为业务系统可集成的 Agent 运行时。

**Independent Test**: 使用可控 Provider 通过 HTTP 创建 Session、连续发送两条消息、查询历史、归档、查询归档历史并以相同身份创建新 Session；验证新旧 ID 不同、旧历史保留且每次消息只调用一次统一 Agent 链路。

**Acceptance Scenarios**:

1. **Given** 存在目标 Agent 且没有活动 Web Session，**When** 调用方创建会话，**Then** 返回 201、活动 Session ID，并立即持久化空会话。
2. **Given** 同一 Agent/用户已有活动 Web Session，**When** 再次创建，**Then** 返回 200 和相同 Session ID，不建立重复活动会话。
3. **Given** 一个活动 Web Session，**When** 连续发送消息，**Then** 每条消息只经一次 AgentService，响应和历史按顺序保存。
4. **Given** Session 历史超过 100 条，**When** 查询历史，**Then** 只返回最近 100 条并标记 `historyTruncated=true`。
5. **Given** 一个活动 Web Session，**When** 归档后再次以相同身份创建，**Then** 旧会话历史仍可查，新会话获得不同 ID 且为空。
6. **Given** 一个 scheduler Session，**When** 通过 Web 查询，**Then** 可读取历史；尝试续写或归档时被明确拒绝。

---

### User Story 2 - 业务系统进行一次性 Agent 调用 (Priority: P1)

业务系统不管理 Session 即可同步调用指定 Agent，最长等待 60 秒。运行时仍在内部创建并持久化独立 Session，使 LLM/Tool 审计有真实关联，但不向调用方暴露该 Session ID。

**Why this priority**: 无状态同步调用是最常见的系统集成方式，也是 Demo 五验证多端点联动的执行入口。

**Independent Test**: 对同一 Agent 连续调用两次，验证响应只含 reply、内部 Session ID 不同、两次均经过 AgentService；再用阻塞执行验证 60 秒截止映射为 504 且发出中断。

**Acceptance Scenarios**:

1. **Given** 一个可用 Agent，**When** 调用 invoke 端点，**Then** 返回 Agent reply，不返回内部 Session ID。
2. **Given** 对同一 Agent 发起两次 invoke，**When** 两次均完成，**Then** 内部使用两个独立 Session，历史不互相污染。
3. **Given** Agent 执行失败，**When** 请求结束，**Then** 内部 Session 已存在，既有 LLM/Tool 审计仍可关联，响应不泄露异常原文。
4. **Given** Agent 调用超过 60 秒，**When** 截止时间到达，**Then** 返回 504 `AGENT_TIMEOUT` 并尽力中断执行；已发生外部副作用不宣称回滚。

---

### User Story 3 - 集成方发现运行时能力与状态 (Priority: P2)

集成方可以查询可用 Profile、指定 Agent 的长期记忆、全局或 Agent 可见 Tool，以及健康与运行信息，从而在调用前完成能力发现和运行检查。

**Why this priority**: 查询端点让外部系统无需读取本地配置即可完成 Demo 五的多端点联动，但不阻塞最小对话链路。

**Independent Test**: 注册多个 Profile、Provider 和不同来源 Tool，通过 5 个查询端点验证排序、可见性、Agent 记忆、健康与运行信息，并确认响应不含 prompt、路径、schema、凭证或内部地址。

**Acceptance Scenarios**:

1. **Given** 多个 Profile，**When** 查询 profiles，**Then** 按名称排序返回安全摘要，不返回 prompt/sourcePath/渠道配置。
2. **Given** 一个 Agent，**When** 查询 memory，**Then** 返回长期记忆分区；兼容路由与规范路由返回同一 View。
3. **Given** 已注册 Tool，**When** 不指定 Agent 查询，**Then** 返回全局注册摘要；指定 Agent 时只返回该 Agent 可见 Tool。
4. **Given** HTTP 进程可服务，**When** 查询 health，**Then** 返回 UP 且不调用外部 Provider。
5. **Given** 运行时已装配 Profile/Tool/Provider，**When** 查询 info，**Then** 返回计数、版本和 Provider 名称，不返回敏感配置。

### Edge Cases

- 空白消息、缺少字段、未知 Agent、未知 Session 和超过 UTF-8 32768 字节的消息必须在进入 AgentService 前失败。
- 多字节字符按 UTF-8 字节而不是 Java 字符计数；32768 字节接受，32769 字节拒绝。
- 同一 Web Session 已处理消息或归档时，第二个写操作立即返回 409，不排队且不调用 Agent。
- 不同 Session 的消息不得因全局锁互相阻塞。
- 归档与消息竞争时，只有一个操作进入：消息先进入则归档返回 busy；归档先进入则消息看到非活动会话。
- 已归档 Session 可通过 GET 查询，但不得恢复执行或被 save 重新置为 active。
- Migration 必须无损保留既有 Session ID、消息 JSON、状态和时间，并允许归档身份组合创建新代次。
- Tool 历史只公开 tool name/call ID，不公开 arguments 或 responseData。
- Provider/Memory/Storage/Timeout/未知异常必须使用固定公开消息，不回显底层异常、密钥、URL 或路径。
- 兼容 memory 路由不计入核心 10 端点，但必须与规范路由委托同一实现。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 提供需求文档定义的 10 个核心 `/api/v1` 端点，并按 Session、Agent、Profile、Memory、Tool、System 六类 Controller 组织。
- **FR-002**: 所有成功与错误响应 MUST 使用含 `code`、`message`、`data`、`timestamp` 的 `ApiResponse` 信封；不得另建公开错误体。
- **FR-003**: Session 消息和一次性 Agent 调用 MUST 只通过现有 `AgentService.process` 执行，不得直接调用 Provider、ReActLoop 或 Tool。
- **FR-004**: `POST /api/v1/sessions` MUST 固定 `channel=web`，校验 Agent 与 userId，新建时返回 201，复用活动会话时返回 200，并在响应前持久化 Session。
- **FR-005**: 同一 `(channel,userId,profileName)` MUST 最多存在一个活动 Session；活动期重复获取 MUST 复用同一 ID，归档后 MUST 创建新 ID，同时保留旧历史。
- **FR-006**: `POST /api/v1/sessions/{id}/messages` MUST 只允许活动 `web` Session；其他渠道 MUST 返回 409，未知或已归档 Session MUST 返回 404。
- **FR-007**: 单条消息 MUST 去除首尾空白后非空且 UTF-8 编码最多 32768 字节；校验失败 MUST 在 AgentService 调用前返回 400。
- **FR-008**: `GET /api/v1/sessions/{id}` MUST 支持读取所有渠道的活动与归档历史，只返回最近 100 条并明确 `historyTruncated`。
- **FR-009**: 对外历史 MUST NOT 返回 Tool arguments、Tool responseData、Profile prompt、Spring AI 内部对象或其他潜在敏感载荷。
- **FR-010**: `DELETE /api/v1/sessions/{id}` MUST 只归档活动 `web` Session，写入 archivedAt 且不物理删除；不得归档 CLI、Scheduler 或 web-invoke Session。
- **FR-011**: 同一 Web Session 的发消息与归档 MUST 使用非阻塞的单进程互斥；竞争失败 MUST 返回 409 `SESSION_BUSY`，不同 Session MUST 可独立执行。
- **FR-012**: `POST /api/v1/agents/{name}/invoke` MUST 为每次请求创建新的 `web-invoke` 内部 Session，执行前持久化，响应只返回 reply，不返回 Session ID。
- **FR-013**: Agent 同步调用 MUST 在 60 秒后返回 504 `AGENT_TIMEOUT` 并请求中断；系统 MUST NOT 声称回滚超时前已经发生的 Tool 副作用或审计。
- **FR-014**: `GET /api/v1/profiles` MUST 返回按名称排序的安全摘要，不得返回 prompt、sourcePath、渠道配置或凭证。
- **FR-015**: `GET /api/v1/memory?agentName=` MUST 返回指定 Agent 长期记忆；现有 `/api/v1/agents/{name}/memory` MUST 保留为委托同一服务的兼容路由。
- **FR-016**: `GET /api/v1/tools` MUST 支持全局列表及可选 agentName 可见性过滤，只返回 name/description/source/sourceName，且不得执行 Tool。
- **FR-017**: `GET /api/v1/health` MUST 无外部副作用地返回进程可服务状态；`GET /api/v1/info` MUST 返回应用/Java/实现版本、Profile/Tool 数与配置 Provider 名称，不得返回敏感配置。
- **FR-018**: Web 层 MUST 只依赖 core 契约；Provider 名称查询通过 core `ProviderCatalog` 契约反转依赖，Web 不得依赖 provider 或 storage 实现包。
- **FR-019**: Session 归档与历史查询 MUST 通过 core `SessionManager` 契约暴露；SQLite 实现 MUST 使用 Flyway 迁移，不得使用 ddl-auto 更新或 Web 直接访问 Repository。
- **FR-020**: Provider、Memory、Storage、Timeout 与未知错误 MUST 映射为稳定且脱敏的 HTTP 状态/业务码；Provider/Memory 为 503，Timeout 为 504，存储与未知错误为 500。
- **FR-021**: 核心阶段 MUST 对 `/api/**` 开放 CORS 方便调试，并在文档中明确无认证、无限流、开放 CORS 不适用于公网生产部署。
- **FR-022**: OpenAPI MUST 包含核心 10 端点与统一信封；Java 21 virtual thread 配置 MUST 保持启用。
- **FR-023**: 本阶段 MUST NOT 实现认证、RBAC、SSE、WebSocket、Webhook、Agent 动态管理、Memory 写操作、Tool 直接执行、审计查询或多实例分布式锁。
- **FR-024**: 所有新增行为 MUST 采用先失败测试、后最小实现的 TDD 证据，并通过无需真实密钥或公网的单元、存储、Web 与 Boot 集成验证。

### Key Entities

- **Session Generation**: 一次活动或已归档对话，拥有独立 UUID、channel/user/profile 身份、状态、创建/活跃/归档时间和消息历史。
- **Session Details**: core 提供的只读防御快照，可查询活动或归档历史，不可直接交给 Agent 执行。
- **Web Session Lock**: 当前进程内按 Session ID 的非阻塞互斥状态，保护 HTTP 消息与归档操作。
- **Agent Invocation**: 调用方无 Session 状态的一次同步执行；内部关联独立 web-invoke Session 与既有审计。
- **Profile/Tool/System View**: 只包含公开字段的不可变响应投影。
- **Provider Catalog**: core 只读契约，提供已配置 Provider 名称集合，不暴露凭证或基础设施类型。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 核心 10 个端点的自动化契约测试通过率为 100%，且 OpenAPI 中全部可发现。
- **SC-002**: 连续创建、两轮消息、查询、归档、归档后重建的完整 Web Session 流程在无需外网的集成测试中 100% 通过。
- **SC-003**: 相同身份同时或重复创建时活动 Session 数始终为 1；归档后新旧 ID 不同且两代历史均可查询。
- **SC-004**: 对同一 Session 并发发起至少 2 个写操作时，额外操作的 AgentService 调用次数为 0 并返回 409；不同 Session 可独立进入执行。
- **SC-005**: 32768 字节消息被接受、32769 字节消息被拒绝；超过 100 条历史时返回条数精确为 100。
- **SC-006**: 连续 2 次无状态调用产生 2 个不同内部 Session，响应均不含 Session ID，LLM/Tool 审计仍可按各自 ID 关联。
- **SC-007**: 阻塞调用达到 60 秒截止后返回 504，响应不含底层异常；测试观察到取消/中断请求。
- **SC-008**: 对 Profile、Tool、Session 历史和 info 响应进行敏感字段断言时，prompt、sourcePath、tool arguments/responseData、凭证与内部地址泄露数为 0。
- **SC-009**: Flyway 从现有 V5 数据库升级后既有 Session 数据保留率为 100%，且归档身份组合可创建新代次。
- **SC-010**: 聚焦测试、全仓 `mvn test`、`mvn clean package` 与文档构建均有真实执行记录；缺少真实 Provider/公网验证时明确标记未验证。

## Assumptions

- 核心部署仍是单实例；Session busy 锁和 get-or-create 协调仅承诺单进程语义。
- 内网无认证是本阶段明确假设；调用方不得把当前服务直接暴露到不可信公网。
- 60 秒 HTTP 截止采用尽力中断，不能撤回已经发生的外部 Tool 副作用。
- 失败调用至少保留预创建 Session 与既有 LLM/Tool 审计，不承诺持久化未完成的部分对话历史。
- GET 历史面向排障与 Demo，允许查询 scheduler/CLI/archived 会话；公共写端点只管理 web 会话。

## Dependencies

- 依赖现有 AgentService、ReActLoop、ProfileRegistry、MemoryService、ToolRegistry、Provider 路由、Sandbox 与审计链路。
- 依赖现有 SQLite/JPA/Flyway Session 表、LLM 调用表和 Tool 调用表。
- 依赖 Spring MVC、Jakarta Validation、springdoc-openapi 与 Java 21 virtual thread 已装配。
- 不依赖真实模型密钥、外部网络、Webhook 或企业 IM；自动验收使用可控 Provider/Tool 与临时 SQLite。

