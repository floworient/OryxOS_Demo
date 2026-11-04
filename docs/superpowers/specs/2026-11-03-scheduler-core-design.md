# 定时任务核心能力设计

## 意图

本阶段让 OryxOS Agent 在无人发起 CLI 或 HTTP 请求时，也能按 `AGENT.md` frontmatter 中的 `schedules` 配置自动运行。定时入口必须与人工入口复用同一个 `AgentService`，为每日天气、每日科技日报等“钟推”场景补齐运行地基。

本设计只交付核心调度能力，同时建立可替换的状态存储契约和稳定任务身份；数据库持久化、管理 API 与管理台留到后续阶段。

## 目标

- 从 `ProfileRegistry` 读取每个 Agent 的 `schedules`，使用 Spring `TaskScheduler` 与 `CronTrigger` 动态注册，不使用静态 `@Scheduled`。
- 每条规则显式使用自己的 cron 和时区；非法 cron 或时区在 Agent 加载阶段被拒绝，不带着坏配置启动。
- 以 `profileName + schedule key` 派生稳定、跨重启一致且跨 Agent 不冲突的 `scheduleId`。
- 到点后使用固定身份 `("scheduler", "scheduler", profileName)` 获取或恢复 Session，并调用既有 `AgentService.process`。
- 同一 Profile 的调度任务在单进程内串行执行；这既保证同一 `scheduleId` 不重叠，也保护该 Profile 共用的 scheduler Session 不发生并发消息交错。拿不到锁时跳过，不排队，也不把进程锁描述成分布式锁。
- 单次失败被隔离并记录，锁始终释放，后续触发和其他任务不受影响。
- 定义最小 `ScheduledTaskStore` 契约并提供内存实现，使调度定义与执行结果分离；后续可由 storage 模块提供 JPA 实现而不改调度主链路。
- 支持幂等的全量注册，以及按 Profile 注册/注销的运行时方法，为后续 Agent 生命周期功能复用同一套逻辑。

## 非目标

- 不新增 Flyway 迁移，不持久化 `scheduled_tasks` 或 `task_executions`，重启后内存运行态与历史会重建。
- 不实现查询、立即执行、启用/停用或修改 cron 的 REST/CLI 管理接口。
- 不通过 API 改写 `AGENT.md`，也不实现文件热监听；启动扫描仍是本阶段的主要入口。
- 不做失败重试、补偿执行、失败告警或错过触发后的补跑。
- 不做多实例选主、分布式锁或租约；多实例可能重复触发的风险必须如实记录。
- 不修改 `ReActLoop`、Provider、Tool 或审计链路；定时触发自然复用其现有行为。

## 约束

- JDK 21、Spring Boot 3.5.8、Maven 多模块；核心契约与编排放 `oryxos-core`，Spring 装配放 `oryxos-boot`。
- `oryxos-core` 不依赖 storage 或具体 Channel；它可以依赖 Spring scheduling API，因为技术方案明确以其作为调度机制。
- 保留当前工作区的 Sandbox 未提交修改，本功能不改动那些实现文件。
- 使用仓库既有 JUnit 5、AssertJ、Mockito 和 Spring Boot 测试设施；不引入新的第三方依赖。
- 行为实现采用 TDD；配置解析、调度注册、重叠跳过、失败释放锁、会话复用和 Boot 装配均需自动化证据。

## 方案比较

### 方案一：核心调度器 + 最小 Store 契约（已选择）

`oryxos-core` 提供 `AgentScheduler`、稳定任务身份、调度定义/执行结果值对象、`ScheduledTaskStore` 及内存实现；`oryxos-boot` 提供 `ThreadPoolTaskScheduler` 和启动装配。本期内存 Store 承担协调与可测试记录，后续 JPA Store 可直接替换。

优点是当前即可保持稳定 ID、定义与历史分离、模块依赖倒置，并把后续持久化改动限制在 storage/boot；代价是本期比单个调度类多几个小型契约和值对象。

### 方案二：只有 `AgentScheduler` 和锁表（未选择）

所有状态直接保存在调度器的 Map 中。本期代码最少，但后续加入持久化、启停、历史和管理 API 时必须重写核心执行路径，也不符合当前宪章要求的稳定 `scheduleId` 与定义/执行历史分离。

### 方案三：本期直接完成持久化和管理 API（未选择）

一次性实现两张表、JPA Store、查询/启停/立即执行端点。能力最完整，但超出“先完成核心能力”的范围，也会把第 28 节的管理职责提前耦合进本阶段。

## 核心设计

### 任务身份与配置校验

现有 `Profile.ScheduleRef(key, cron, zone, message)` 保持公共字段不变。`AgentLoader` 在派生 Profile 时增加：

- `key`、`cron`、`zone`、`message` 继续要求非空；同一 Agent 内 `key` 唯一。
- 使用当前 Spring 版本的 cron 解析能力验证表达式，使用 `ZoneId.of` 验证时区。
- 任一 schedule 非法时，沿用当前 Agent 加载失败的处理：该 Agent 不进入 Registry，日志只给出字段与安全错误原因。

稳定 `scheduleId` 由带长度边界的 `profileName` 与 `key` 组合后做 name-based UUID 派生，避免简单字符串拼接歧义。修改 cron、时区或 message 不改变 ID；修改 key 视为新任务。跨 Agent 使用同一个 key 不冲突。

### Store 契约

`ScheduledTaskStore` 只包含本阶段真实使用的能力：

- `reconcile(ScheduledTaskDefinition)`：登记或更新同一稳定 ID 的配置定义并返回快照。
- `retire(scheduleId)`：注销 Profile 或规则消失时将定义从活动集合移除；内存实现可直接移除，持久化实现后续可保留退役状态。
- `recordExecution(TaskExecution)`：记录一次真正取得锁并开始的执行结果。
- `find(scheduleId)` 与 `executions(scheduleId)`：用于核心测试和后续适配层读取；本期不对外暴露管理端点。

`InMemoryScheduledTaskStore` 使用线程安全集合并返回不可变快照。它不是持久化能力，也不能在文档中被描述为重启可恢复。

执行结果至少包含 `scheduleId`、可空的 `sessionId`、开始时间、结束时间、成功标记和安全错误类型。`SessionManager.getOrCreate` 之前或期间失败时还没有可靠 Session，因此 `sessionId` 为空；一旦取得 Session 就必须记录其 ID。为避免泄露 Provider/Tool 异常中的秘密，本期只记录异常类名，不记录原始异常消息。重叠跳过不算一次执行，不写 execution，只记结构化日志。

### 注册生命周期

`AgentScheduler` 持有 `TaskScheduler`、`ProfileRegistry`、`AgentService`、`SessionManager`、`ScheduledTaskStore`，以及两个并发 Map：`scheduleId -> ScheduledFuture` 和 `profileName -> ReentrantLock`。锁按 Profile 而不是 schedule 划分，是因为技术契约要求同一 Profile 的所有钟推共用固定 Session，而当前 Session 是可变对象且持久化没有并发合并语义。

- `registerAll()` 遍历 Registry 并调用 `registerProfile(profile)`；重复调用必须幂等，不能重复注册同一 ID。
- `registerProfile(profile)` 先为全部规则构造并校验定义与 `CronTrigger`，再 reconcile Store 并逐条注册。相同 Profile、相同定义的重复调用幂等；同名 Profile 的定义已经变化时，本期明确拒绝，调用方必须先 `unregisterProfile` 再注册，避免伪装成尚未实现的热更新事务。
- `unregisterProfile(profileName)` 取消该 Profile 的全部 future 并 retire 当前定义，但不移除 Profile 锁。`cancel(false)` 不会中断已运行任务；保留锁可保证相同 Profile 立即重新注册后仍不能与旧执行并发。锁集合只随当前进程见过的 Profile 名增长，范围受已加载 Agent 数量限制。
- 首次注册中任一 `TaskScheduler.schedule` 失败时，取消本次已经创建的 future、retire 本次登记并抛出启动错误，不得留下“Store 已登记但没有 future”或半套 Profile 句柄。既有且完全相同的注册不参与这次回滚。
- `ScheduledFuture.cancel(false)` 只阻止后续触发，不中断正在执行的 ReAct 调用。

本阶段 Boot 在应用上下文初始化时调用一次 `registerAll()`。运行时注册方法已经可复用，但不额外实现 Agent 生命周期 API 或文件监听器。

### 单次执行

`runOnce(profile, schedule)` 的顺序固定为：

1. 根据 `profileName` 获取该 Profile 的 `ReentrantLock` 并 `tryLock()`；日志和 Store 仍使用稳定 `scheduleId` 标识具体规则。
2. 失败则记录不含 message 内容的跳过日志并返回。
3. 成功后记录开始时间，通过 `SessionManager.getOrCreate("scheduler", "scheduler", profile.name())` 获得 Session。
4. 调用 `AgentService.process(session, schedule.message())`，从而复用 Prompt、Memory、Provider、Tool 与审计链路。
5. 成功或异常都向 Store 写入一条执行结果；Store 自身记录失败只记日志，不能掩盖原始失败，也不能使锁无法释放。
6. 捕获业务异常并记录安全日志，不向 `TaskScheduler` 线程外抛。
7. 在 `finally` 中释放锁；失败后再次触发必须仍能进入。

调度器不自行调用 `SessionManager.save`，因为 `AgentService.process` 已负责成功路径保存；不复制或旁路现有执行链路。

### Spring 装配

`oryxos-boot` 新增聚焦的 Scheduler 配置类：

- 创建并初始化唯一的 `ThreadPoolTaskScheduler`，默认池大小为 4、线程名前缀为 `oryxos-scheduler-`、关闭时等待当前任务完成；本期不新增可配置属性类。
- 注册 `InMemoryScheduledTaskStore` 为默认 Store Bean，允许后续通过条件装配替换为持久化实现。
- 创建 `AgentScheduler` Bean，并用 bean init method 或等价生命周期回调执行 `registerAll()`。

池大小大于 1 允许不同 Profile 的任务并行；同一 Profile 的任务由共享锁防止重叠和 Session 并发写入。该并发约束仅限当前 JVM。

## 错误与边界行为

- 空 `schedules` 的 Profile 不注册任务，也不产生错误。
- 同 Agent 重复 key 已由 Loader 拒绝；不同 Agent 同 key 生成不同 ID。
- 非法 cron/时区导致对应 Agent 加载失败，而不是到触发时才失败。
- 调度注册器拒绝任务时，应用启动失败并暴露不含秘密的配置定位信息，避免表面启动成功但任务永不运行。
- 单次 Agent 执行失败只影响本次；无自动重试。
- Store 记录失败不会阻止锁释放；日志明确区分业务执行失败与记录失败。
- 同一 Profile 的不同 schedule 同时到点时，先拿到锁的执行，其他触发直接跳过；这是固定 scheduler Session 身份下的数据一致性保护。不同 Profile 仍可并行。
- Profile 注销不强制中断当前执行，且其锁在进程生命周期内保留；同名 Profile 重新注册不会绕过旧执行的锁。
- 应用关闭时取消未来触发，并按 Boot 调度器配置等待当前任务完成；不强制中断 ReAct。

## 验证设计

- `AgentLoaderTest`：合法 cron/时区通过；非法 cron、非法时区、重复 key 使 Agent 被拒绝。
- `ScheduleIdTest`：同 profile/key 稳定、改配置不变、跨 Agent/不同 key 不同、组合无拼接歧义。
- `InMemoryScheduledTaskStoreTest`：reconcile 更新不换 ID、快照不可变、retire、并发安全和 execution 记录。
- `AgentSchedulerTest`：捕获 `CronTrigger` 的表达式和时区；全量注册幂等；同名 Profile 配置变更需先注销；首次注册部分失败完整回滚；注销取消句柄且重新注册复用旧锁；同 Profile 的相同/不同任务重叠均跳过；不同 Profile 可独立；固定 Session 身份；两次触发复用 Session；Session 创建失败记录空 sessionId；成功/失败记录；异常不外抛且第二次仍能运行；Store 异常不遗留死锁。
- Boot 配置测试：调度器、默认 Store、线程池和启动注册均被装配，且没有静态 `@Scheduled` 路径。
- 核心链路集成测试：使用可控 Provider/Tool 和临时 SQLite，直接执行一次调度任务，证明经过真实 `AgentService`、产生 scheduler Session，并保留既有 `llm_calls`/`tool_invocations` 审计；无需真实密钥或公网。
- 最终运行聚焦模块测试、根目录 Maven Wrapper 全仓测试与打包/verify、`git diff --check`；真实 cron 到点等待仅作为可选人工验证，不能替代自动测试。

## 文档与 Spec-Kit 交付

顺延创建 `specs/008-scheduler-core/`，包含 `spec.md`、requirements checklist、`plan.md`、`research.md`、`data-model.md`、contracts、`quickstart.md`、`tasks.md`，实现后补 `verification.md`。Spec-Kit 文档以当前设计和四份项目原始文档为依据，不把第 28 节管理能力误写为本期已完成。

README 与中英文网站只在实现验证后更新，准确说明：当前支持启动时动态注册、显式时区、单进程防重叠与内存执行状态；尚不支持持久化管理、多实例去重和运行时 API 改 cron。

## 留给实现者的局部判断

- 值对象是使用 record 还是小型 final class，以现有 Java 风格和不可变性测试为准。
- 日志事件的具体 event name 与字段排布可沿用现有 SLF4J 风格，但不得记录 schedule message、凭证或完整异常载荷。
- Boot 生命周期使用 `@Bean(initMethod=...)` 还是显式 Spring lifecycle 回调，由实现时根据可测试性选择；外部行为必须符合上述注册与关闭契约。
