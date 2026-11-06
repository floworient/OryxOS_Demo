# OryxOS Web Service 核心能力设计

## 意图

为企业内部业务系统提供稳定的 HTTP 门面，使调用方能够创建并延续 Agent 会话、发起一次性调用、查询可用 Agent/Tool/Memory 与运行状态，而不复制 CLI、调度入口中的 ReAct、Prompt、Tool 或审计逻辑。

本次交付对应 US-5 核心阶段的 6 个 Controller、10 个 REST 端点。完成后，外部系统可以通过同步 HTTP 请求复用现有 `AgentService` 主链路，并通过现有 SQLite 实现恢复会话历史。

## 目标

- 实现需求文档列出的核心 10 个 `/api/v1` 端点。
- 所有成功和失败响应统一使用 `ApiResponse(code, message, data, timestamp)`。
- HTTP 调用和 CLI、Scheduler 一样复用 `AgentService`、`SessionManager`、`ProfileRegistry`、`MemoryService` 与 `ToolRegistry`。
- 单条用户消息按 UTF-8 编码后不得超过 32 KiB；返回的会话历史最多包含最近 100 条消息。
- Provider、存储、超时及未知异常转换为稳定、脱敏的 HTTP 错误。
- 保留并复用现有 OpenAPI、Correlation ID、Java 21 虚拟线程和 Web 测试约定。

## 非目标与反目标

- 不实现认证、RBAC、租户隔离、SSE、WebSocket、Webhook 或 CORS 白名单治理。
- 不实现 Agent/Profile 动态创建、更新、删除、重载或调度规则管理。
- 不新增 Tool 直接执行端点，不建立绕过 `ToolExecutor` 的调用通道。
- 不新增 Memory 写入、清空、搜索或审计查询端点。
- 不重构 ReAct、Provider、Memory、Tool、CLI 或 Scheduler 内核。
- 不把应用层校验或白名单描述成容器级安全隔离。

## 已选方案与备选方案

### 已选：薄 Controller + Web 应用服务

Controller 只负责路由、Bean Validation、状态码和响应信封；`oryxos-web` 内的应用服务负责把 HTTP 请求适配为 core 调用、构建对外 View、裁剪历史并映射资源不存在等业务情况。底层能力仍由 core 接口提供。

该方案避免把 Web DTO 和 HTTP 语义放入 core，也避免把会话身份、历史裁剪、无状态调用身份等逻辑散落在 Controller 中。用户已于 2026-11-04 选择此方案。

### 未选：Controller 直接调用所有 core 服务

文件更少，但适配逻辑会散落在六个 Controller，测试与后续扩展成本更高。

### 未选：扩充 core 为统一管理门面

Web 层最薄，但会为单一接入层扩大 core 公共契约，并提前引入扩展阶段的管理职责，不符合本次聚焦范围。

## 总体结构

```text
HTTP /api/v1
  -> Controller（路由、校验、ApiResponse）
  -> oryxos-web 应用服务（HTTP 到 core 的适配与 View 映射）
  -> core 契约（AgentService、SessionManager、ProfileRegistry、MemoryService、ToolRegistry）
  -> storage/provider/tool 既有实现
```

`oryxos-web` 只依赖 `oryxos-core`。Session 归档能力属于所有入口可复用的持久化契约，因此在 `SessionManager` 增加显式 `archive(sessionId)` 方法；SQLite 状态变更仍由 `oryxos-storage` 实现。

当前 Session ID 由 `(channel, userId, profileName)` 确定性生成，数据库也对该三元组永久唯一；这与需求文档“无活跃 Session 时创建新 Session、归档历史仍可查”冲突。本次将 Session 身份调整为每代独立 UUID，由 `SessionManager.getOrCreate` 按三元组查找唯一活动会话：活动会话继续复用，归档后同一身份组合创建新 ID。Flyway 新迁移负责保留历史数据并把永久唯一约束调整为“同一三元组最多一个 active 会话”的部分唯一索引。Scheduler 在活动期仍稳定复用同一 Session，归档历史也不会被覆盖。

## HTTP 契约

### 会话管理

1. `POST /api/v1/sessions`
   - 请求：`profileName`、`userId`，两者去除首尾空白后必须非空；`userId` 最大 255 字符。
   - 固定使用 `channel=web` 调用 `SessionManager.getOrCreate` 并立即保存，使空会话也可被后续 ID 路由找到。
   - 新建时返回 `201`，已有活动会话时返回 `200`；响应均为 `SessionView`。相同身份组合在活动期幂等返回同一会话，归档后再次创建会得到新 ID。
   - Profile 不存在返回 `404 AGENT_NOT_FOUND`。

2. `POST /api/v1/sessions/{id}/messages`
   - 请求：`message`，去除首尾空白后必须非空，UTF-8 字节数最多 32768。
   - 仅允许向 `channel=web` 的活动会话发消息；读取后调用 `AgentService.process(session, message)`，不得直接调用 Provider 或 Tool。Scheduler、CLI 与内部一次性调用会话不可经此端点续写。
   - 返回 `200`，数据包含 `sessionId` 与 `reply`。
   - 会话不存在或已归档返回 `404 SESSION_NOT_FOUND`；其他渠道返回 `409 SESSION_CHANNEL_NOT_WRITABLE`。
   - 同一会话已有消息请求或归档操作进行中时立即返回 `409 SESSION_BUSY`，不排队进入 ReAct。

3. `GET /api/v1/sessions/{id}`
   - 可读取所有渠道的活动或已归档会话，以满足 Web 查询 scheduler Demo 历史；返回身份元数据、状态、时间和最近 100 条消息。
   - 消息按原始顺序排列；若总量超过 100，只返回尾部 100 条，并通过 `historyTruncated=true` 明示裁剪。
   - 对外消息 View 对 user/assistant 暴露文本；对 assistant/tool 的工具部分只暴露工具名与调用 ID。Tool 输入参数、完整 `ToolResult.responseData`、Spring AI 内部对象均不对外返回，避免文件、Shell 或 HTTP 结果中的敏感数据泄露。

4. `DELETE /api/v1/sessions/{id}`
   - 仅允许归档 `channel=web` 的活动会话；标记为 `archived` 并写入 `archived_at`，不物理删除历史和审计记录。Scheduler、CLI 与内部一次性调用会话不可由该公共端点归档。
   - 成功返回 `200` 的统一响应信封；不存在或已归档返回 `404 SESSION_NOT_FOUND`。
   - 若同一会话正在处理消息，立即返回 `409 SESSION_BUSY`；若归档先取得互斥权，后到的消息请求读取不到活动会话并返回 404。

### Agent 调用

5. `POST /api/v1/agents/{name}/invoke`
   - 请求：`message`，使用与会话消息相同的非空和 32 KiB 限制。
   - “无状态”指调用方不持有或复用 Session；服务内部为每次请求生成新的不可预测 `userId`，固定 `channel=web-invoke`，再通过 `SessionManager` 和 `AgentService` 执行。
   - 内部 Session 在进入 `AgentService` 前先持久化，确保 Provider/Tool 即使失败也能用 session ID 关联到真实会话；成功后由既有链路保存完整历史。失败时不承诺保存未完成的部分对话轨迹，但 LLM/Tool 审计仍按既有记录器落库。响应不返回 Session ID，只返回 `reply`。
   - Agent 不存在返回 `404 AGENT_NOT_FOUND`。
   - 调用最长等待 60 秒，超时返回 `504 AGENT_TIMEOUT`。系统取消执行线程并中断后续工作是尽力而为；已经完成的外部 Tool 副作用和已写审计不回滚，文档不得把 HTTP 超时描述为事务取消。

### 查询端点

6. `GET /api/v1/profiles`
   - 返回按 name 升序排列的 Profile 摘要，只含 name、description、identity agentName、provider name/model 与可见能力名称；不返回 sourcePath、prompt、渠道配置或凭证。

7. `GET /api/v1/memory?agentName={name}`
   - `agentName` 必填，返回指定 Agent 的长期记忆分区 View。
   - 复用现有 `MemoryService.loadLongTerm` 和 `MemoryView`。
   - 已有 `/api/v1/agents/{name}/memory` 作为兼容路由保留，并委托同一 Web 应用服务；它不改变核心 10 端点的契约。

8. `GET /api/v1/tools?agentName={name}`
   - `agentName` 可选。未提供时返回全局已注册 Tool；提供时只返回 `ToolRegistry.visibleTo(profile)` 的可见 Tool。
   - 返回按 name 升序排列的 name、description、source、sourceName；不返回执行入口，不执行 Tool。
   - 未知 Agent 返回 `404 AGENT_NOT_FOUND`。

### 系统状态

9. `GET /api/v1/health`
   - 只表示 OryxOS HTTP 进程已可响应，返回 `status=UP`；不通过调用真实外部 Provider 制造副作用。

10. `GET /api/v1/info`
    - 返回应用名、实现版本、Java 版本、Profile 数、Tool 数和已配置 Provider 名称。
    - 不返回模型密钥、数据库地址、工作区绝对路径、环境变量或内部服务地址。

## 应用服务与 DTO 边界

- `SessionWebService`：创建、查找、发消息、读取历史和归档会话。
- `AgentInvocationWebService`：校验 Agent 存在，创建一次性内部 Session 并调用 `AgentService`。
- `AgentInvocationWebService` 使用受控执行器和 60 秒截止时间；超时后发出中断并返回固定错误，不把底层异常原文暴露给调用方。
- `SessionWebService` 对每个可写 Web Session 使用进程内互斥状态；消息与归档使用非阻塞争用，竞争失败映射为 `409 SESSION_BUSY`。这只解决单进程 HTTP 并发，不宣称是多实例分布式锁。
- Profile、Tool、System 查询保持无副作用；映射逻辑使用各自小型 View 工厂，避免建立一个无边界的总门面。
- 请求 DTO 使用 Jakarta Bean Validation 完成字段级校验；UTF-8 32 KiB 限制使用自定义校验器或应用服务中的单一校验函数，确保不会误用 Java 字符数。
- View 使用不可变 record/list 快照，Controller 不返回 core 可变对象。

## 错误处理

- `400 VALIDATION_ERROR`：Bean Validation、非法 UUID/ID 形态、空消息或消息超过 32 KiB。
- `404 AGENT_NOT_FOUND`：目标 Agent/Profile 不存在。
- `404 SESSION_NOT_FOUND`：会话不存在或已经归档。
- `409 SESSION_BUSY`：同一 Web 会话已有消息或归档操作执行中。
- `409 SESSION_CHANNEL_NOT_WRITABLE`：试图通过公共写端点续写或归档非 Web 会话。
- `503 PROVIDER_UNAVAILABLE`：`ProviderException` 及其受控错误类型；公开消息固定为“模型服务暂不可用”。
- `503 MEMORY_UNAVAILABLE`：沿用现有 Memory 映射。
- `504 AGENT_TIMEOUT`：同步 Agent 调用超过 60 秒。
- `500 STORAGE_ERROR`：会话读写/归档失败；公开消息固定，不回显数据库异常。
- `500 INTERNAL_ERROR`：其余异常；日志保留 correlation ID 与异常栈，但响应不回显异常、凭证或内部路径。

成功与错误都保留统一信封。错误映射应先匹配具体异常，再由最终兜底处理未知异常。

## 数据与状态变化

- 不新增业务表；新增一条 SQLite Flyway 迁移，将 Session 主键改为每代独立 UUID，并把 `(channel, user_id, profile_name)` 永久唯一约束改为仅约束 `status='active'` 的部分唯一索引。迁移必须原样保留现有 ID、消息、状态和时间字段。
- `SessionManager.archive(String sessionId)` 返回是否成功或等价的显式结果，core 不依赖 JPA。
- `JpaSessionManager.archive` 在事务中只允许 `active -> archived`，设置 `archived_at`；归档后 `find` 不恢复该 Session。
- `save` 更新既有活动会话时不得把已归档记录重新激活。
- `SessionManager` 增加可按 ID 查询包括归档状态在内的只读快照能力，供 GET 历史使用；现有 `find` 继续只返回可执行的活动 Session，避免已归档会话被误送入 Agent。
- 一次性 Agent 调用使用独立内部 Session，既保留审计，又不会污染调用方的连续会话。

## 安全与运行约束

- 请求体由 Spring MVC 限制并在应用层再次执行 32 KiB 消息限制。
- CORS 按核心阶段为调试开放所有源，但仅作用于 `/api/**`；文档明确该设置不适用于公网生产部署。
- 对外 DTO 不返回 Profile prompt/sourcePath、Tool input schema、通知地址、Provider 凭证或异常原文。
- 所有 Agent 运行仍通过现有 Sandbox、Tool visibility、`ToolExecutor` 和审计记录器。
- Java 21 virtual thread 继续由 `spring.threads.virtual.enabled=true` 启用。

## 测试与验收

实现严格采用测试先行：每个行为先运行聚焦测试并观察预期失败，再写最小实现。

- Web 单元测试：6 个 Controller 的路由、校验、状态码、响应信封和安全字段。
- 应用服务单元测试：确定性会话创建、最近 100 条历史、UTF-8 字节限制、一次性调用隔离、未知资源。
- Storage 测试：迁移无损、同一身份活动期复用、归档后生成新 ID、同一身份最多一个 active、重复归档、归档后不可执行但历史可查、保存不能复活归档会话、跨重启保持归档状态。
- 并发测试：同一 Web Session 的并发消息/归档返回 409 且不进入第二次 ReAct；不同 Session 不因全局锁互相阻塞。
- 超时测试：超过 60 秒返回 504，执行线程收到中断；已发生副作用不作回滚承诺，响应不泄露内部异常。
- Boot 集成测试：用可控 Provider 发起真实 Web 请求，证明 Session 与无状态调用都经过同一个 `AgentService`，响应可查询且 Session 可跨上下文恢复。
- 契约测试：10 个核心端点均出现在 OpenAPI，并统一使用 `ApiResponse`。
- 门禁：聚焦模块测试、全仓 `mvn test`、`mvn clean package`，并记录无需真实密钥/公网的验证结果。

## 完成标准

- 10 个核心端点全部可调用，并满足上述状态码、信封、校验和脱敏契约。
- Web 入口没有复制 ReAct/Prompt/Tool 业务逻辑，Agent 执行只经 `AgentService`。
- Session 创建、连续消息、查询、归档以及一次性调用均有自动化集成证据。
- OpenAPI、README/网站 API 文档和实际实现一致。
- 未实现的认证、流式、动态管理与治理能力继续明确标记为非目标。

