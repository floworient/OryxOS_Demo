# 定时任务核心能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 Agent 按 frontmatter 的 cron/时区自动走统一 `AgentService` 链路，同时提供稳定任务身份、Profile 级防重叠、失败隔离和可替换的内存 Store 边界。

**Architecture:** `oryxos-core` 新增 scheduler 包，持有调度契约、内存状态和 `AgentScheduler` 编排；`oryxos-boot` 只装配 Spring `ThreadPoolTaskScheduler` 与 Bean 生命周期。每条规则以 `(profileName, key)` 确定性派生 ID，同一 Profile 的规则共享进程内锁和 scheduler Session；本期不写调度数据库或管理 API。

**Tech Stack:** JDK 21、Spring Boot 3.5.8、Spring Framework 6.2.14 scheduling、Maven、JUnit 5、AssertJ、Mockito、SQLite/JPA/Flyway（仅复用既有 Session 与审计）

**Spec:** `docs/superpowers/specs/2026-11-03-scheduler-core-design.md`、`specs/008-scheduler-core/spec.md`

## Global Constraints

- 所有触发必须调用现有 `AgentService.process(Session, String)`；不得复制 ReAct、Prompt、Tool 或审计链路。
- `oryxos-core` 不得依赖 `oryxos-storage`、web 或具体 Channel；新增唯一显式依赖为已有版本管理的 `spring-context`。
- Session 身份固定为 `("scheduler", "scheduler", profileName)`；同一 Profile 的全部 schedule 在当前 JVM 内串行。
- schedule message、凭证、异常 message/cause/stack 不得进入调度日志或 `TaskExecution.errorType`。
- 不新增 Flyway 迁移、REST/CLI 管理、自动重试、热修改 cron、多实例协调或分布式锁。
- 保留用户当前 Sandbox 未提交修改；本计划不修改其实现文件。
- 每项行为先运行新增测试得到预期 RED，再写最小 GREEN；没有用户明确授权时不执行 git commit，仅保留建议提交信息。

## Review Focus

- 同 Profile 不同 schedule 同时触发：第二个必须 skip，不能并发写同一 Session；由 Task 4 的并发测试固定。
- 注销后立即同名注册且旧执行未结束：必须复用保留锁；由 Task 5 的 latch 测试固定。
- 多规则首次注册中途失败：future 与活动定义残留均为 0；由 Task 5 的第二次 schedule 失败测试固定。
- Session 获取前失败：必须记录 failure 且 `sessionId == null`；由 Task 4 固定。
- Store 记录失败：不能阻止解锁或把 Agent 成功改成失败；由 Task 4 固定。

---

### Task 1: 配置校验与稳定 ScheduleId

**Files:**
- Modify: `oryxos-core/pom.xml`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/profile/AgentLoader.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/scheduler/ScheduleId.java`
- Modify: `oryxos-core/src/test/java/io/oryxos/core/profile/AgentLoaderTest.java`
- Create: `oryxos-core/src/test/java/io/oryxos/core/scheduler/ScheduleIdTest.java`

**Interfaces:**
- Consumes: `Profile.ScheduleRef(String key, String cron, String zone, String message)`
- Produces: `ScheduleId.from(String profileName, String scheduleKey) -> ScheduleId`；`ScheduleId.value() -> String`

- [ ] **Step 1: 写配置与身份 RED 测试**

在 `AgentLoaderTest` 增加 `invalidCronRejectsWholeAgent`、`invalidZoneRejectsWholeAgent`，保持既有重复 key 测试；在 `ScheduleIdTest` 断言相同二元组稳定、修改 cron 等无关字段不参与 ID、不同 Agent/key 不同、`("ab","c")` 与 `("a","bc")` 不同、null/blank 拒绝。

- [ ] **Step 2: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=AgentLoaderTest,ScheduleIdTest test`

Expected: FAIL，`ScheduleId` 尚不存在且 AgentLoader 仍接受非法 cron/zone。

- [ ] **Step 3: 实现最小 GREEN**

在 core POM 显式加入 `org.springframework:spring-context`。`AgentLoader.schedules` 使用 `CronExpression.parse(cron)` 与 `ZoneId.of(zone)`，将解析异常转换成字段分别为 `schedules.cron` / `schedules.zone` 的现有安全校验异常。新增：

```java
public record ScheduleId(String value) {
    public static ScheduleId from(String profileName, String scheduleKey);
}
```

身份字节使用长度前缀后交给 `UUID.nameUUIDFromBytes`，字符集固定 UTF-8。

- [ ] **Step 4: 运行 GREEN 与回归**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=AgentLoaderTest,AgentLoaderYamlSafetyTest,ScheduleIdTest test`

Expected: PASS。

- [ ] **Step 5: 检查点**

Review: `git diff -- oryxos-core/pom.xml oryxos-core/src/main/java/io/oryxos/core/profile/AgentLoader.java oryxos-core/src/main/java/io/oryxos/core/scheduler/ScheduleId.java`

Suggested commit: `feat(core): validate schedules and derive stable ids`

---

### Task 2: 调度定义、执行结果与内存 Store

**Files:**
- Create: `oryxos-core/src/main/java/io/oryxos/core/scheduler/ScheduledTaskDefinition.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/scheduler/TaskExecution.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/scheduler/ScheduledTaskStore.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/scheduler/InMemoryScheduledTaskStore.java`
- Create: `oryxos-core/src/test/java/io/oryxos/core/scheduler/InMemoryScheduledTaskStoreTest.java`

**Interfaces:**
- Consumes: `ScheduleId`、`Profile`、`Profile.ScheduleRef`
- Produces: contracts/scheduler.md 中 `ScheduledTaskDefinition.from`、`TaskExecution.succeeded/failed`、`ScheduledTaskStore` 五个方法

- [ ] **Step 1: 写值对象与 Store RED 测试**

覆盖 definition ID 一致性、合法 cron/zone、execution 时间顺序、success/errorType 不变量、failure 只保留异常简单类名、reconcile 原子替换、retire 不删历史、find/executions 防御快照、8 线程并发追加不丢记录。

- [ ] **Step 2: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=InMemoryScheduledTaskStoreTest test`

Expected: FAIL，四个生产类型尚不存在。

- [ ] **Step 3: 实现值对象与接口**

严格实现 `specs/008-scheduler-core/contracts/scheduler.md` 的签名。`TaskExecution.failed` 允许 null sessionId，只取 `failure.getClass().getSimpleName()`。

- [ ] **Step 4: 实现线程安全内存 Store**

活动定义使用 `ConcurrentHashMap<ScheduleId, ScheduledTaskDefinition>`；execution 按 ID 使用线程安全列表，读取返回 `List.copyOf`。所有 public 输入 `Objects.requireNonNull`，`retire` 幂等且只移除定义。

- [ ] **Step 5: 运行 GREEN**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=ScheduleIdTest,InMemoryScheduledTaskStoreTest test`

Expected: PASS，无并发丢失。

- [ ] **Step 6: 检查点**

Review: `git diff -- oryxos-core/src/main/java/io/oryxos/core/scheduler oryxos-core/src/test/java/io/oryxos/core/scheduler`

Suggested commit: `feat(core): add scheduler state contracts`

---

### Task 3: 最小动态注册与统一 Agent 链路

**Files:**
- Create: `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`
- Create: `oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java`

**Interfaces:**
- Consumes: `TaskScheduler`、`ProfileRegistry.all()`、`AgentService.process`、`SessionManager.getOrCreate`、`ScheduledTaskStore`
- Produces: `AgentScheduler` 构造器、`registerAll()`、`registerProfile(Profile)`；内部 Runnable 可由捕获的 TaskScheduler 参数测试

- [ ] **Step 1: 写成功路径 RED 测试**

用 mock `TaskScheduler` 返回 mock `ScheduledFuture`，捕获 `Trigger` 和 `Runnable`。断言空 schedules 不注册、合法规则用 `CronTrigger.getExpression()` 和 ZoneId 计算一致的 next execution、执行 Runnable 两次时 `getOrCreate("scheduler","scheduler","weather")` 与 `agents.process(session,"查询天气")` 各两次、两条 success execution 使用同一 sessionId。

- [ ] **Step 2: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=AgentSchedulerTest test`

Expected: FAIL，`AgentScheduler` 尚不存在。

- [ ] **Step 3: 实现构造器与最小注册**

```java
public AgentScheduler(TaskScheduler taskScheduler, ProfileRegistry profiles,
        AgentService agents, SessionManager sessions,
        ScheduledTaskStore store, Clock clock)
```

保存非空依赖；`registerAll` 遍历 registry；`registerProfile` 构造 definition/CronTrigger、reconcile、schedule，并保存 future/definition 快照。此任务只覆盖首次成功注册。

- [ ] **Step 4: 实现成功 Runnable**

先用临时最小锁结构保证可运行；按固定三元组取 Session，调用 AgentService，基于 Clock 记录 succeeded execution。不要调用 `sessions.save`。

- [ ] **Step 5: 运行 GREEN**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=AgentSchedulerTest test`

Expected: 当前成功路径 PASS。

- [ ] **Step 6: 检查点**

Suggested commit: `feat(core): add dynamic agent scheduler`

---

### Task 4: Profile 互斥与失败隔离

**Files:**
- Modify: `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`
- Modify: `oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java`

**Interfaces:**
- Consumes: Task 3 的内部 Runnable
- Produces: Profile 级 `tryLock`、安全 skip、Session/Agent/Store 失败隔离与可靠 unlock

- [ ] **Step 1: 写并发/失败 RED 测试**

使用 `CountDownLatch` 占住 weather Profile：同 rule 和另一 rule 的 Runnable 均不得第二次进入 Agent；news Profile Runnable 必须可进入。分别让 SessionManager、AgentService、Store 抛异常，断言 Runnable 不外抛、失败 execution（Session 失败时 ID 为 null）、Store 失败后再次触发仍进入。用 Logback test appender 或现有日志测试方式断言 message/canary 不出现。

- [ ] **Step 2: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=AgentSchedulerTest test`

Expected: FAIL，当前实现允许同 Profile 不同任务并发或异常外抛。

- [ ] **Step 3: 实现 Profile 锁与执行结果**

使用 `ConcurrentHashMap<String, ReentrantLock>`；Runnable `tryLock` 失败直接返回，不 record。取得锁后保存 startedAt/sessionId，Agent 异常转 failed execution；recordExecution 自身置于单独 try/catch；最外层不抛，finally unlock。日志只带 scheduleId/profileName/error class。

- [ ] **Step 4: 运行 GREEN 与重复压力**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=AgentSchedulerTest test`

Expected: PASS；重复运行 3 次无偶发失败。

- [ ] **Step 5: 检查点**

Suggested commit: `feat(core): isolate scheduled task failures`

---

### Task 5: 幂等、回滚、注销与关闭生命周期

**Files:**
- Modify: `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`
- Modify: `oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java`

**Interfaces:**
- Consumes: 已注册 definition/future 快照与保留的 Profile lock
- Produces: `unregisterProfile(String)`、`close()`，以及 registerAll/registerProfile 的同步幂等/回滚语义

- [ ] **Step 1: 写生命周期 RED 测试**

覆盖：相同定义重复注册只 schedule 一次；cron/message 改变而未注销时抛异常且旧 future 不变；第二条 schedule 抛异常/返回 null 时第一条 cancel(false)、两条 definition 均 retire；unregister 对全部 future cancel(false)；未知 Profile no-op；close 两次安全。

- [ ] **Step 2: 写注销重注册锁复用 RED 测试**

旧 Runnable 持锁时调用 unregister，再注册同名 Profile 并执行新 Runnable；断言新 Agent 调用为 0。旧任务释放后新 Runnable 能进入。

- [ ] **Step 3: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=AgentSchedulerTest test`

Expected: FAIL，生命周期方法或事务语义缺失。

- [ ] **Step 4: 实现同步生命周期**

public 生命周期方法 `synchronized`；按 Profile 保存不可变 definition 集合与 future 集合。全部 Trigger 先构造；首次注册在临时集合中 reconcile/schedule，失败则 cancel/retire 后再抛。unregister/close 不删除 lock，不 interrupt 运行任务。

- [ ] **Step 5: 运行 GREEN 与 core 全测**

Run: `.\mvnw.cmd -pl oryxos-core test`

Expected: PASS。

- [ ] **Step 6: 检查点**

Suggested commit: `feat(core): manage scheduler lifecycle safely`

---

### Task 6: Boot 装配与关闭策略

**Files:**
- Create: `oryxos-boot/src/main/java/io/oryxos/boot/SchedulerConfiguration.java`
- Create: `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerConfigurationTest.java`

**Interfaces:**
- Consumes: Task 5 完整 `AgentScheduler` 与 `ScheduledTaskStore`
- Produces: Spring Bean `ThreadPoolTaskScheduler`、默认 `InMemoryScheduledTaskStore`、init/destroy `AgentScheduler`

- [ ] **Step 1: 写 Boot RED 测试**

用最小 Spring context 与 mock Agent 依赖断言：一个 scheduler Bean，pool size 4，thread prefix `oryxos-scheduler-`，remove-on-cancel，默认 Store；自定义 Store 时默认不创建；上下文 refresh 调用 registerAll，close 调用 scheduler close；扫描 `io.oryxos` 不存在业务 `@Scheduled` 方法。

- [ ] **Step 2: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-boot -am -Dtest=SchedulerConfigurationTest test`

Expected: FAIL，配置类不存在。

- [ ] **Step 3: 实现 SchedulerConfiguration**

使用 `@Configuration(proxyBeanMethods = false)`；TaskScheduler 设置 poolSize 4、prefix、removeOnCancel、waitForTasksToCompleteOnShutdown；默认 Store 使用 `@ConditionalOnMissingBean(ScheduledTaskStore.class)`；AgentScheduler bean 用 initMethod `registerAll`、destroyMethod `close`。

- [ ] **Step 4: 运行 GREEN 与启动回归**

Run:

```powershell
.\mvnw.cmd -pl oryxos-boot -am -Dtest=SchedulerConfigurationTest test
.\mvnw.cmd -pl oryxos-boot -am -Dit.test=OryxOsApplicationIT verify
```

Expected: 两条命令均 PASS，空 schedules 启动不新增触发。

- [ ] **Step 5: 检查点**

Suggested commit: `feat(boot): wire scheduler runtime`

---

### Task 7: 真实统一链路集成验收

**Files:**
- Create: `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerAgentServiceIT.java`
- Modify only if RED proves a scoped defect: scheduler/core files from Tasks 1-6

**Interfaces:**
- Consumes: Boot Scheduler Bean、真实 AgentService/ReActLoop/ToolExecutor/JPA Session 与审计
- Produces: 无新生产接口；提供端到端证据

- [ ] **Step 1: 写 IT**

仿照 `ReActConfigurationTest` 创建临时 Agent（含合法 schedule）、stub Provider 两轮响应和计数 Tool。用可捕获 TaskScheduler 或从 AgentScheduler 测试入口取得已注册 Runnable，触发一次；查询临时 SQLite，断言 scheduler Session 存在、LLM 调用 2 条、Tool 调用 1 条、Tool 执行计数 1、ProfileContext 清理。

- [ ] **Step 2: 运行 IT 并确认真实结果**

Run: `.\mvnw.cmd -pl oryxos-boot -am -Dit.test=SchedulerAgentServiceIT verify`

Expected: PASS。若 RED，先确认是调度缺口而非测试装配错误，再做最小修复并记录证据。

- [ ] **Step 3: 运行相关回归**

Run: `.\mvnw.cmd -pl oryxos-core,oryxos-boot -am test`

Expected: PASS。

- [ ] **Step 4: 检查点**

Suggested commit: `test(boot): verify scheduled agent chain`

---

### Task 8: 文档、完整门禁与 Spec-Kit 收敛

**Files:**
- Modify: `README.md`
- Modify: `website/zh/docs/profile.md`
- Modify: `website/docs/profile.md`
- Create: `specs/008-scheduler-core/verification.md`
- Modify: `specs/008-scheduler-core/tasks.md`（只更新真实完成状态）

**Interfaces:**
- Consumes: 所有已验证行为
- Produces: 准确能力说明与可复核验证记录

- [ ] **Step 1: 更新中英文文档**

说明动态注册、时区、固定 Session、Profile 级单进程互斥、内存 Store；明确无重启恢复、管理 API、自动重试和多实例去重。README 状态只在对应自动化证据通过后更新。

- [ ] **Step 2: 运行完整门禁**

Run sequentially:

```powershell
.\mvnw.cmd clean verify
npm --prefix website run docs:build
git diff --check
```

Expected: 全部 exit 0；任何既有/无关失败原样记录，不弱化门禁。

- [ ] **Step 3: 写 verification.md**

记录每条命令、RED/GREEN 摘要、调度课件交付映射、SC-001～SC-008 证据、未执行真实时间等待/真实外部服务、单实例边界与 reviewer 文件导读。

- [ ] **Step 4: 执行 Spec-Kit 收敛**

完整读取 `.claude/skills/speckit-converge/SKILL.md` 后执行；若产生追加任务，回到相应 Task 的 RED/GREEN 并重跑门禁。仅在无剩余工作时勾选 T016。

- [ ] **Step 5: 最终检查点**

Run: `git status --short` 与 `git diff --stat`

Suggested commit: `feat: complete scheduler core capability`

