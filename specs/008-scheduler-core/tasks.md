# Tasks: 定时任务核心能力

**Input**: `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/scheduler.md`、`quickstart.md` 与已确认设计。

**Tests**: FR-018 明确要求 TDD。所有新增行为先运行聚焦测试确认 RED，再实现最小 GREEN；既有回归不人为制造失败。

**Organization**: 任务按三个用户故事组织。所有路径均相对仓库根；checkbox 是实施阶段的唯一完成状态来源。

## Phase 1: Setup

**Purpose**: 核对前序链路和本阶段依赖，不覆盖现有 Sandbox 未提交修改。

- [x] T001 运行 `oryxos-core`、`oryxos-boot` 现有聚焦基线并在 `specs/008-scheduler-core/verification.md` 记录 git status、JDK/Maven、测试结果与既有失败，确认 `ProfileRegistry`、`AgentService`、`SessionManager` 和审计链可用（FR-017、FR-018；SC-007）。

---

## Phase 2: Foundational

**Purpose**: 建立三个用户故事共同依赖的配置验证、稳定身份和 Store 边界。

- [x] T002 在 `oryxos-core/src/test/java/io/oryxos/core/profile/AgentLoaderTest.java` 与新增 `oryxos-core/src/test/java/io/oryxos/core/scheduler/ScheduleIdTest.java` 先写合法/非法 cron、非法 zone、重复 key、稳定 ID、跨 Agent 和拼接歧义 RED；再修改 `oryxos-core/pom.xml`、`oryxos-core/src/main/java/io/oryxos/core/profile/AgentLoader.java` 并新增 `oryxos-core/src/main/java/io/oryxos/core/scheduler/ScheduleId.java` 完成 GREEN（FR-002、FR-003；SC-004）。
- [x] T003 在新增 `oryxos-core/src/test/java/io/oryxos/core/scheduler/InMemoryScheduledTaskStoreTest.java` 先固定定义/执行值约束、reconcile/retire、不可变快照、并发追加和可空 sessionId RED；再新增 `ScheduledTaskDefinition.java`、`TaskExecution.java`、`ScheduledTaskStore.java`、`InMemoryScheduledTaskStore.java` 于 `oryxos-core/src/main/java/io/oryxos/core/scheduler/` 完成 GREEN（FR-009、FR-011、FR-015）。

**Checkpoint**: 稳定身份与可替换的当前进程 Store 契约可独立测试，尚未注册真实 cron。

---

## Phase 3: User Story 1 - Agent 到点自动运行 (Priority: P1) 🎯 MVP

**Goal**: 从 Profile 动态注册 cron/时区，到点使用固定 scheduler Session 调用同一 AgentService。

**Independent Test**: 捕获 TaskScheduler 注册的 Trigger/Runnable，断言配置保持一致；执行 Runnable 两次，证明固定身份 Session 复用且每次只调用统一 Agent 入口一次。

- [x] T004 [US1] 新增 `oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java`，先覆盖 `registerAll`、空 schedules、CronTrigger 表达式/ZoneId、固定 `("scheduler","scheduler",profileName)` Session、消息透传、两次触发复用 Session 与 success execution，运行确认缺少 `AgentScheduler` 的 RED（FR-001、FR-004、FR-005、FR-009；SC-001、SC-006）。
- [x] T005 [US1] 新增 `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`，实现最小启动注册与成功回调，使用 `TaskScheduler.schedule` + `CronTrigger` 且仅调用 `AgentService.process`，通过 T004 GREEN（FR-001、FR-004、FR-005）。
- [x] T006 [P] [US1] 新增 `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerConfigurationTest.java`，先固定唯一 4 线程 `ThreadPoolTaskScheduler`、线程前缀、remove-on-cancel、等待关闭、默认可替换内存 Store、AgentScheduler init/destroy 注册及不存在业务 `@Scheduled` 的 RED（FR-004、FR-011、FR-016）。
- [x] T007 [US1] 新增 `oryxos-boot/src/main/java/io/oryxos/boot/SchedulerConfiguration.java`，按 contract 装配 TaskScheduler、默认 Store 和 AgentScheduler 生命周期，通过 T006 与启动上下文 GREEN（FR-004、FR-011、FR-016）。
- [x] T008 [US1] 新增 `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerAgentServiceIT.java`，使用可控 Provider/Tool、捕获的调度 Runnable 和临时 SQLite 验证真实 `AgentService -> ReActLoop -> ProviderService -> ToolExecutor`、scheduler Session、`llm_calls`/`tool_invocations` 各自审计且工具不重复执行，运行 Failsafe GREEN（FR-005、FR-018；SC-001、SC-006、SC-007）。

**Checkpoint**: 启动加载 Agent 后可自动登记并通过真实统一链路完成一次钟推；无需真实时间等待或外网。

---

## Phase 4: User Story 2 - 长任务和失败不破坏调度 (Priority: P1)

**Goal**: 保护共享 scheduler Session，隔离 Agent/Session/Store 失败并保证后续触发可继续。

**Independent Test**: 在一个执行持锁时并发触发同/不同 schedule，验证同 Profile skip、不同 Profile 可进入；分别注入 Session、Agent 和 Store 异常后再次触发，验证无外抛和无永久锁。

- [x] T009 [US2] 扩展 `oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java`，先加入同 Profile 同/不同规则重叠 skip 且无 execution、不同 Profile 独立、Session 获取失败空 sessionId、Agent 失败不外抛、第二次可进入、Store 失败不掩盖执行事实与敏感消息不进入日志的 RED（FR-006～FR-010、FR-015；SC-002、SC-003）。
- [x] T010 [US2] 修改 `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`，实现进程内 `profileName -> ReentrantLock`、`tryLock` skip、Clock 计时、成功/失败 execution、安全日志、Store 异常隔离和 finally 解锁，通过 T009 GREEN（FR-006～FR-010、FR-015）。

**Checkpoint**: 同一 Profile 不会并发写 scheduler Session；任一单次失败不会终止调度或留下死锁。

---

## Phase 5: User Story 3 - 配置与任务身份可安全演进 (Priority: P2)

**Goal**: 幂等注册、首次失败回滚、显式注销与同名重注册复用锁，为后续 Agent 生命周期/持久化管理保留真实接口。

**Independent Test**: 重复注册相同定义、尝试覆盖变化定义、模拟第二条 schedule 注册失败、注销并立即重注册，断言句柄/Store/锁状态符合 contract。

- [x] T011 [US3] 扩展 `oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java`，先加入 registerAll/相同定义幂等、变化定义拒绝且旧句柄不变、第二条注册失败时 cancel/retire 全回滚、schedule 返回 null 失败、注销 cancel(false)/retire、未知 Profile no-op、运行中注销后同名重注册仍 skip、close 幂等的 RED（FR-012～FR-014、FR-016；SC-005）。
- [x] T012 [US3] 修改 `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`，实现同步 lifecycle、定义集合比较、临时 future 回滚、按 Profile 句柄索引、保留锁的 unregister 与幂等 close，通过 T011 GREEN（FR-012～FR-014、FR-016）。
- [x] T013 [US3] 在 `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerConfigurationTest.java` 增加自定义 `ScheduledTaskStore` 覆盖默认 Bean 和应用关闭取消未来触发的 Spring 上下文测试，必要时仅调整 `oryxos-boot/src/main/java/io/oryxos/boot/SchedulerConfiguration.java`，通过 GREEN（FR-011、FR-013、FR-016）。

**Checkpoint**: core 生命周期接口可供后续 Agent 管理复用，但没有伪装成已实现热更新、管理 API 或持久化恢复。

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T014 [P] 更新 `README.md`、`website/zh/docs/profile.md`、`website/docs/profile.md`，准确说明启动动态注册、显式时区、稳定身份、Profile 级单进程互斥、内存状态，以及无重启恢复/管理 API/重试/多实例去重边界（FR-017；SC-008）。
- [x] T015 运行 `specs/008-scheduler-core/quickstart.md` 的聚焦测试、IT、模块测试、根目录 `.\mvnw.cmd clean verify`、网站构建和 `git diff --check`，在 `specs/008-scheduler-core/verification.md` 记录 RED/GREEN、真实命令、结果、未验证人工 cron/外部服务与 reviewer 导读（FR-018；SC-007、SC-008）。
- [x] T016 完整读取并执行 `.claude/skills/speckit-converge/SKILL.md`，核对 `specs/008-scheduler-core/spec.md`、`plan.md`、`tasks.md` 与真实交付；若发现缺口追加任务并回到 TDD，最终只在无剩余工作时勾选本任务（FR-017、FR-018；SC-007、SC-008）。

---

## Dependencies & Execution Order

- T001 → T002 → T003，完成共同契约后进入用户故事。
- US1：T004 → T005；T006 可在 T004 后并行写 RED，T007 等 T005/T006，T008 等 core 与 boot GREEN。
- US2：T009 → T010，依赖 US1 的可运行回调。
- US3：T011 → T012 → T013，依赖 US2 的锁语义。
- Polish：T014 可在行为稳定后并行准备；T015 等所有实现/文档；T016 最后收敛。

## Parallel Opportunities

- T006 与 core 的 T004/T005 修改不同模块，可并行写测试，但 Boot GREEN 依赖 AgentScheduler 类型存在。
- T014 的中英文文档文件相互独立，可并行编辑；README 状态必须以测试完成结果为准。
- Maven 测试不要并发使用同一 `target/`；验证命令保持串行。

## Implementation Strategy

先完成 US1 的最小钟推链路并以真实 IT 证明统一入口；再在同一核心类上补 US2 的并发/失败保护，最后补 US3 生命周期事务边界。每个任务都先保存对应 RED 输出，再写最小 GREEN；不自动 commit/push/切分支，不修改用户现有 Sandbox 工作。

## Task Summary

- 总任务数：16
- Setup/Foundational：3
- US1：5
- US2：2
- US3：3
- Polish/验证：3

