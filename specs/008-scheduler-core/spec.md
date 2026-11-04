# Feature Specification: 定时任务核心能力

**Feature Directory**: `008-scheduler-core`

**Created**: 2026-11-03

**Status**: Design Approved

**Input**: 按已确认设计完成 OryxOS 定时任务核心能力：由 Agent 的 `schedules` 配置自动触发，复用统一 Agent 执行链路，显式时区，稳定任务身份，单进程防重叠和失败隔离；本阶段使用内存状态并预留后续持久化接口。

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Agent 到点自动运行 (Priority: P1)

Agent 配置维护者在 `AGENT.md` 中声明定时规则后，重启 OryxOS 即可让 Agent 在指定时区按 cron 自动收到配置消息，无需人工发起 CLI 或 HTTP 调用。自动触发必须与人工触发复用相同的 Agent 执行链路和会话能力。

**Why this priority**: “钟推”是每日天气和每日科技日报等场景成立的最小前提，也是本模块的核心用户价值。

**Independent Test**: 使用一个带定时规则的测试 Agent 和可控执行链路启动运行时，捕获已注册规则并触发一次；验证 cron、时区和消息保持一致，且产生固定 scheduler 身份的 Session 并走既有 Agent 调用链。

**Acceptance Scenarios**:

1. **Given** 一个 Agent 声明了合法 cron、时区和消息，**When** OryxOS 启动并加载该 Agent，**Then** 该规则被动态注册且使用声明的时区。
2. **Given** 一个已注册规则到达触发点，**When** 系统执行本次触发，**Then** 系统用 `scheduler` 渠道和用户身份取得该 Agent 的 Session，并把配置消息交给统一 Agent 入口。
3. **Given** 同一 Agent 已有一次 scheduler Session，**When** 该 Agent 后续再次触发，**Then** 两次触发复用同一个确定性 Session，历史可由既有 Session 机制累积。
4. **Given** 一个 Agent 没有任何定时规则，**When** OryxOS 启动，**Then** 不为它注册任务，也不影响其人工触发能力。

---

### User Story 2 - 长任务和失败不破坏调度 (Priority: P1)

运行维护者需要确信某个 Agent 的一次执行很慢或失败时，不会产生同一 Session 的并发写入、任务堆积、永久死锁或整个调度器失效。

**Why this priority**: 自动任务无人值守，重叠和失败若不能隔离，会造成重复通知、对话历史损坏或后续任务永久停止。

**Independent Test**: 用可控阻塞与异常执行替代真实外部服务，直接触发同一 Agent 的多个规则；验证重叠触发被跳过、不同 Agent 可独立运行、异常不外抛且下一次触发仍能进入。

**Acceptance Scenarios**:

1. **Given** 同一 Agent 的一个任务仍在运行，**When** 同一或另一条规则再次触发，**Then** 新触发被直接跳过，不排队且不会再次调用 Agent。
2. **Given** 两个不同 Agent 的规则同时触发，**When** 调度资源可用，**Then** 两者可独立执行，不因对方的进程内锁被跳过。
3. **Given** Agent 执行抛出异常，**When** 本次调度结束，**Then** 异常被隔离、失败结果被记录、锁被释放，并且下一次触发仍可执行。
4. **Given** 执行结果记录失败，**When** 本次调度清理，**Then** 锁仍被释放，原始 Agent 执行结果不被记录异常掩盖。

---

### User Story 3 - 配置与任务身份可安全演进 (Priority: P2)

Agent 生命周期维护者需要稳定识别每条规则，并能幂等注册、注销和重新注册 Profile，为后续持久化状态、启停和立即执行管理保留不改写主链路的扩展点。

**Why this priority**: 核心自动触发可以先运行，但若任务身份不稳定或生命周期不可控，后续管理功能会被迫重写调度内核。

**Independent Test**: 对同一 Profile 重复注册、注销、重新注册并修改非身份字段；验证身份稳定、无重复句柄、注销会取消未来触发、运行中的旧执行与重新注册后的触发仍受同一 Profile 锁保护。

**Acceptance Scenarios**:

1. **Given** 同一 Agent 名和 schedule key，**When** cron、时区或消息改变，**Then** 任务身份保持不变；Agent 名或 key 改变时身份不同。
2. **Given** 同一 Profile 和相同定义已经注册，**When** 再次注册，**Then** 操作幂等且不会创建第二个触发句柄。
3. **Given** 同名 Profile 的定义已改变但尚未注销，**When** 请求重新注册，**Then** 系统明确拒绝，不静默形成两套定义。
4. **Given** Profile 被注销，**When** 注销完成，**Then** 未来触发被取消、活动定义被退役；已运行任务不被强制中断。
5. **Given** Profile 注销后立即以同名重新注册，**When** 旧执行尚未结束，**Then** 新触发不能绕过原有互斥保护。

### Edge Cases

- 同一 Agent 内 schedule key 重复时，整个 Agent 配置应被拒绝，不能只静默丢弃一条。
- cron 语法非法或时区不存在时，Agent 应在加载阶段被拒绝，不能等到触发时才发现。
- 不同 Agent 使用相同 schedule key 时，任务身份不得冲突。
- Profile 名与 key 的不同组合即使简单拼接文本相同，也不得派生相同任务身份。
- 首次注册多条规则时，若其中一条无法注册，必须回滚本次已创建的句柄和活动定义，不留下半注册状态。
- Session 获取失败时仍要记录失败执行，但 Session 标识允许为空。
- 重叠跳过不算一次执行，不写执行记录，只留下不含任务消息的安全日志。
- 进程关闭时不得强制中断正在运行的 Agent；后续触发必须停止。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 从所有已加载 Profile 的 `schedules` 声明动态登记定时规则，不得要求为每条业务规则修改或重新编译 Java 代码。
- **FR-002**: 每条规则 MUST 包含 Agent 内唯一的非空 key、合法 cron、合法显式时区和非空消息；任一字段非法 MUST 使该 Agent 在加载阶段被拒绝。
- **FR-003**: 系统 MUST 为 `(profileName, schedule key)` 派生稳定的全局任务身份；修改 cron、时区或消息 MUST NOT 改变身份，不同二元组 MUST NOT 因组合歧义产生相同身份。
- **FR-004**: 系统 MUST 在启动时注册所有合法规则；无规则的 Profile MUST 正常保留且不创建任务。
- **FR-005**: 自动触发 MUST 使用固定 `channel=scheduler`、`user=scheduler` 和目标 Profile 名取得 Session，并调用与 CLI/Web 相同的 `AgentService` 处理链路。
- **FR-006**: 同一 Profile 的所有定时规则 MUST 在单进程内共享互斥保护；已有执行未结束时，新触发 MUST 立即跳过且 MUST NOT 排队或调用 Agent。
- **FR-007**: 不同 Profile MUST 使用独立互斥保护，系统不得因一个 Profile 正在运行而主动跳过另一个 Profile。
- **FR-008**: Agent 执行异常 MUST 被当前触发隔离，MUST NOT 从调度回调继续外抛，且互斥锁 MUST 在所有路径释放。
- **FR-009**: 系统 MUST 为每次真正取得锁并开始的触发记录成功或失败结果；记录至少包含稳定任务身份、可空 Session 标识、起止时间、成功标记和不含原始异常消息的安全错误类型。
- **FR-010**: 重叠跳过 MUST NOT 写执行记录；日志 MUST NOT 包含 schedule message、凭证或完整异常载荷。
- **FR-011**: 调度定义与执行结果 MUST 通过 core 层存储契约分离；本阶段默认实现 MUST 是线程安全、返回不可变快照的内存实现，且不得宣称重启可恢复。
- **FR-012**: 全量注册和相同 Profile/定义的重复注册 MUST 幂等；同名 Profile 定义变化而未先注销时 MUST 明确拒绝。
- **FR-013**: 注销 Profile MUST 取消其未来触发并退役活动定义，但 MUST NOT 强制中断已经运行的调用；同名 Profile 重新注册 MUST 继续复用原有互斥保护。
- **FR-014**: 首次注册任一规则失败时，系统 MUST 回滚本次已创建的触发句柄和活动定义，并使启动或注册操作明确失败。
- **FR-015**: 执行结果存储失败 MUST 被单独隔离和记录，MUST NOT 阻止互斥锁释放，也 MUST NOT 改写 Agent 执行本身的成功或失败事实。
- **FR-016**: 应用关闭时 MUST 停止未来触发并等待当前任务按既有关闭策略结束，不得强制中断 ReAct 调用。
- **FR-017**: 本阶段 MUST NOT 新增调度数据库表、管理 REST/CLI、失败重试、多实例协调或运行时 API 修改 cron，并 MUST 在交付文档中明确这些边界。
- **FR-018**: 所有新增行为 MUST 采用先失败测试、后最小实现的 TDD 证据，并验证配置、注册、会话、互斥、失败隔离、存储和真实统一调用链。

### Key Entities

- **Schedule Definition**: 一条来源于 Agent 配置的活动定时定义，包含稳定任务身份、Profile 名、Agent 内 key、cron、时区和触发消息。
- **Task Execution**: 一次已经取得互斥锁并开始执行的结果，关联任务身份和可选 Session，记录起止时间、成功状态及安全错误类型。
- **Scheduled Task Store**: 定义与执行结果的存储边界；本阶段以内存方式提供当前进程快照，后续可由持久化实现替换。
- **Scheduled Handle**: 调度系统返回的未来触发句柄，用于幂等判断、注销和关闭时取消。
- **Profile Execution Lock**: 当前进程内按 Profile 名共享的互斥状态，保护同一 scheduler Session 不被并发写入。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 对 100% 合法定时规则，系统登记的 cron、时区和消息与 Agent 声明完全一致，触发后只调用一次统一 Agent 入口。
- **SC-002**: 在同一 Profile 已运行时并发发起至少 2 次触发，额外触发的 Agent 调用次数为 0，且没有排队后的延迟补跑。
- **SC-003**: 对一次失败触发连续再执行 1 次，第二次能够进入 Agent 调用，证明失败路径没有留下永久锁。
- **SC-004**: 同一 `(profileName, key)` 在至少 3 次独立构造中得到完全相同的任务身份；不同组合的测试矩阵中冲突数为 0。
- **SC-005**: 首次注册中模拟任一规则失败后，新增活动句柄和定义残留数均为 0。
- **SC-006**: 自动触发产生的 Session 身份与既有确定性公式一致，连续 2 次触发复用同一 Session。
- **SC-007**: 聚焦单元与集成测试 100% 通过，并在无需真实密钥或公网的条件下证明 scheduler 触发经过既有 Agent、Provider、Tool、Session 与审计链路。
- **SC-008**: 文档中对内存状态、单进程互斥、无自动重试、无管理 API 和无多实例去重的限制披露完整率为 100%。

## Assumptions

- 核心阶段部署为单实例；多实例时同一规则可能在每个实例各执行一次，这是已知非目标。
- `AGENT.md` 是定时定义的唯一来源；本阶段规则变化通过重启生效，运行时注册/注销仅作为内部生命周期能力。
- 同一 Profile 的所有定时触发按技术契约复用同一个 scheduler Session，因此需要比“同一任务不重叠”更强的 Profile 级串行保护。
- 现有 `AgentService.process` 负责成功路径的 Session 保存以及 Provider/Tool 审计；调度器不复制这些职责。
- 本阶段执行历史仅存在于当前进程内；后续持久化实现将沿用 core Store 契约，但可扩展启停、列表和历史分页能力。

## Dependencies

- 依赖现有 Profile/AgentLoader、ProfileRegistry、AgentService、SessionManager 和审计链路已经可用。
- 依赖运行环境提供能够按 cron 与时区动态登记回调的调度能力。
- 不依赖真实模型密钥、外部网络或企业 IM；自动验收使用可控 Provider、Tool 和本地存储。

