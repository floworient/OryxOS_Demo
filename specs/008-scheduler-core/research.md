# Research: 定时任务核心能力

## Decision 1: 使用现有 Spring 动态调度 API

**Decision**: core 显式依赖 `spring-context`，使用 `TaskScheduler.schedule(Runnable, Trigger)` 和 `CronTrigger(String, ZoneId)`；boot 提供 `ThreadPoolTaskScheduler`。

**Rationale**: 技术方案明确要求配置驱动动态注册且禁止静态 `@Scheduled`。本地 Maven 依赖中的 Spring Framework 6.2.14 已通过 `javap` 核实上述构造器和方法真实存在，避免依据课件示意代码猜 API。

**Alternatives considered**:

- 自建 ScheduledExecutorService + cron 解析：重复造轮子，且会引入 cron/时区语义风险。
- 静态 `@Scheduled`：无法按 Agent frontmatter 动态生成规则。
- 把调度器放 boot：会把业务生命周期编排挤出 core，后续 Agent 生命周期无法复用。

## Decision 2: 稳定 ID 由 Profile 名与 key 确定性派生

**Decision**: `ScheduleId.from(profileName, scheduleKey)` 对带长度边界的 UTF-8 输入使用 `UUID.nameUUIDFromBytes`，返回 UUID 文本值。

**Rationale**: 同一配置任务跨重启稳定，cron/zone/message 更新不换身份；长度边界避免 `ab+c` 与 `a+bc` 一类拼接歧义。跨 Agent 同 key 自然隔离。

**Alternatives considered**:

- 随机 UUID：无数据库时跨重启不稳定。
- 简单 `profile + ":" + key`：分隔符可出现在合法名称/key 中，存在组合歧义。
- 由 SQLite 自增：本阶段不新增调度表，且会让 core 依赖 storage。

## Decision 3: 按 Profile 而不是按 schedule 加锁

**Decision**: `profileName -> ReentrantLock`，同一 Profile 的所有 schedule 互斥；不同 Profile 独立。

**Rationale**: 技术契约要求同一 Profile 的钟推固定使用 `("scheduler", "scheduler", profileName)` Session。当前 `Session` 可变且 `JpaSessionManager` 没有并发合并语义，只锁单条 schedule 会让同 Profile 的不同规则并发写同一会话。

**Alternatives considered**:

- 每个 schedule 一把锁：只满足字面上的同任务防重叠，无法保护共享 Session。
- 每个 schedule 独立 Session：违反既有会话身份约定，破坏跨触发历史累积。
- 全局单锁：安全但阻塞无关 Agent，削弱多 Agent 运行能力。

## Decision 4: 最小 Store 契约 + 当前进程内存实现

**Decision**: `ScheduledTaskStore` 只定义 `reconcile`、`retire`、`find`、`recordExecution`、`executions`；默认 `InMemoryScheduledTaskStore` 使用线程安全集合和不可变快照。

**Rationale**: 当前真实需求需要稳定定义和执行结果分离，后续 JPA Store 可替换；启停、列表分页、立即执行等尚无本期消费者，不提前虚构接口。

**Alternatives considered**:

- 状态全部放 `AgentScheduler`：后续持久化会重写核心路径，违反依赖倒置。
- 本期直接建表/JPA：超出用户确认范围。
- 预先定义第 28 节完整接口：未实现方法会形成占位能力并增加错误契约。

## Decision 5: 注册采用可回滚的首次登记，更新需显式注销

**Decision**: 全部定义和 Trigger 先构造；首次注册若任一 schedule 被拒绝，取消本次 future 并 retire 本次定义。完全相同定义重复注册幂等；同名 Profile 定义变化时明确拒绝，要求先注销后注册。

**Rationale**: `TaskScheduler` 没有事务，静默逐条更新会留下半套句柄。核心阶段只承诺启动注册，显式两步更新比伪事务热更新更真实可靠。

**Alternatives considered**:

- 失败后保留成功子集：表面启动成功但部分任务永不运行。
- 在线原子替换旧/新整组 future：需要复杂补偿和竞态处理，属于后续热更新能力。
- 先取消旧任务再尝试新任务：新注册失败会丢失原有可用调度。

## Decision 6: 失败历史最小化敏感信息

**Decision**: `TaskExecution` 保存可空 sessionId 与异常类名，不保存原始异常消息；重叠跳过不写 execution。

**Rationale**: Provider、Tool 或外部系统异常消息可能带凭证、URL 或业务内容。异常类名足以区分核心失败类型，详细排障仍使用既有受控日志/审计。

**Alternatives considered**:

- 保存完整异常消息/堆栈：泄密风险高。
- 所有失败统一固定字符串：无法区分 Session、Agent 或 Store 层失败。
- Session 获取失败不记 execution：会让已开始的触发静默丢失。

## Decision 7: Boot 默认 4 线程并等待关闭

**Decision**: 唯一 `ThreadPoolTaskScheduler` 使用 pool size 4、`oryxos-scheduler-` 前缀、remove-on-cancel，并在关闭时等待当前任务完成；`AgentScheduler.close` 先取消未来触发。

**Rationale**: 单线程会让一个长 Agent 阻塞所有其他 Agent，无法体现 Profile 锁的独立性。固定小线程池足够核心阶段演示，且不增加未要求的配置面。

**Alternatives considered**:

- 默认单线程：不同 Profile 无法并行，长任务扩大故障面。
- 按 CPU 动态扩容：部署行为不够可预测。
- 本期新增线程池配置属性：增加非核心配置与验证面，后续可按实际负载引入。

