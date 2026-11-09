---
description: "第 28 节定时任务管理与运行恢复实施清单"
---

# Tasks: 定时任务管理与运行恢复

**Input**: [spec.md](spec.md)、[plan.md](plan.md)、[research.md](research.md)、[data-model.md](data-model.md)、[调度契约](contracts/schedules.md) 与 [quickstart.md](quickstart.md)

测试任务先运行 RED，随后实现任务运行同一测试验证 GREEN。记录命令、退出码和失败原因。

## Phase 1: Setup

- [x] T001 核对 `pom.xml`、`oryxos-storage/pom.xml`、`oryxos-boot/pom.xml`、`frontend/package.json` 和 `specs/013-scheduled-task-operations/quickstart.md` 的基线。确认 JDK 21、默认测试发现和现有真密钥 opt-in 约定（FR-010）。

## Phase 2: Foundational

- [x] T002 在 `oryxos-core/src/test/java/io/oryxos/core/scheduler/InMemoryScheduledTaskStoreTest.java` 添加状态契约 RED，验证稳定身份、活动列表、默认启用、计数、失败历史、退役后可查及同键重协调保留状态（FR-001、FR-002、FR-004）。
- [x] T003 在 `oryxos-core/src/main/java/io/oryxos/core/scheduler/ScheduledTaskStore.java` 扩充契约，在同目录的 `oryxos-core/src/main/java/io/oryxos/core/scheduler/ScheduledTaskView.java`、`oryxos-core/src/main/java/io/oryxos/core/scheduler/TaskExecutionView.java` 定义视图，修改 `oryxos-core/src/main/java/io/oryxos/core/scheduler/InMemoryScheduledTaskStore.java`，使 T002 GREEN。保留原定义和执行记录签名，复用统一的可读安全错误映射（FR-001、FR-002、FR-004、FR-009）。
- [x] T004 在 `oryxos-storage/src/test/java/io/oryxos/storage/JpaScheduledTaskStoreTest.java` 加入 RED，覆盖 V7→V8 保留旧数据、外键与组合唯一约束、启停与执行并发不丢更新、状态写失败时历史回滚、退役和同键重登记（FR-002、FR-004、FR-007）。
- [x] T005 在 `oryxos-storage/src/main/resources/db/migration/sqlite/V8__create_scheduled_tasks_and_task_executions.sql` 加两表。依照数据模型，UUID 主键、组合唯一、定义字段必填、启用/退役初始 true/false、下次和上次时间可空、状态仅 success/failed 或空、次数初始 0 且非负、更新时间必填。历史主键自增，任务外键可空但新执行必有关联，Session 可空，开始时间和非负耗时必填，成功标记必填且成功无错误。创建历史排序索引（FR-002、FR-004、FR-007）。
- [x] T006 新增 `oryxos-storage/src/main/java/io/oryxos/storage/entity/ScheduledTask.java`、`oryxos-storage/src/main/java/io/oryxos/storage/entity/TaskExecution.java`、`oryxos-storage/src/main/java/io/oryxos/storage/repository/ScheduledTaskRepository.java`、`oryxos-storage/src/main/java/io/oryxos/storage/repository/TaskExecutionRepository.java`、`oryxos-storage/src/main/java/io/oryxos/storage/JpaScheduledTaskStore.java`。用短事务原子记录历史和累计状态，启停、下次触发单独更新列。保留旧执行查询契约。运行 T004 至 GREEN（FR-002、FR-004、FR-007）。

## Phase 3: User Story 1 - 查看、补跑和暂停任务 (P1)

**Independent Test**: 临时库启动、登记、补跑和停用，并捕获一次被跳过的 cron。

- [x] T007 [US1] 在 `oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java` 和 `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerConfigurationTest.java` 加 RED，覆盖下次触发点、停用自动跳过、手动忽略启用、并发跳过、运行异常登记和 JPA 优先装配。创建 `oryxos-boot/src/test/java/io/oryxos/boot/ScheduledTaskE2ETest.java`，在真实 Spring Boot、SQLite、现有 Mock Provider 和捕获 cron 回调下对未实现的管理链路先跑 RED（FR-001、FR-003、FR-004、FR-005、FR-010、SC-001）。
- [x] T008 [US1] 修改 `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java` 与 `oryxos-boot/src/main/java/io/oryxos/boot/SchedulerConfiguration.java`，实现 `runNow(ScheduleId)` 与共用触发路径、计划时间登记、同 Profile 互斥和可替换 Store 装配。保留原生命周期入口。跑 T007 至 GREEN（FR-001、FR-003、FR-004、SC-001）。
- [x] T009 [P] [US1] 在 `oryxos-web/src/test/java/io/oryxos/web/schedule/ScheduleApiControllerTest.java` 写九路由 RED，覆盖统一信封、排序、停用仍可补跑、冲突、无效 ID/请求、退役读写和跨域 PUT 预检（FR-005）。
- [x] T010 [US1] 实现 `oryxos-web/src/main/java/io/oryxos/web/schedule/ScheduleApiController.java`、`oryxos-web/src/main/java/io/oryxos/web/schedule/UpdateScheduleRequest.java`，按契约序列化视图和转换错误。按需在控制器内定义传输 DTO。调整 `oryxos-web/src/main/java/io/oryxos/web/config/CorsConfiguration.java`，限调度启停路由放行 PUT。运行 T009 至 GREEN（FR-005）。
- [x] T011 [P] [US1] 在 `frontend/tests/scheduleManagement.test.ts` 与 `frontend/tests/managementNavigation.test.ts` 先加 RED，覆盖导航、列表字段、空态、待处理行按钮、操作失败、刷新和 v2 请求（FR-006）。
- [x] T012 [US1] 在 `frontend/src/components/ScheduleManagement.vue` 实现页面并接入 `frontend/src/components/consoleSections.ts` 与 `frontend/src/components/ManagementPanel.vue`，在 `frontend/src/api/types.ts`、`frontend/src/api/oryxos.ts`、`frontend/src/api/http.ts` 定义 v2 请求与视图。运行 T011 至 GREEN（FR-006）。
- [x] T013 [US1] 运行 `oryxos-boot/src/test/java/io/oryxos/boot/ScheduledTaskE2ETest.java` 至 GREEN，断言启动登记、补跑、次数与历史、一次 `save_memory` 可经现有 Memory API 查到、停用后回调无新增执行或历史（FR-001、FR-003、FR-004、FR-005、FR-010、SC-001）。

## Phase 4: User Story 2 - 服务重启后继续运行 (P1)

**Independent Test**: 用同一临时工作区起两个独立上下文，人工 IT 实际终止并重启子进程。

- [x] T014 [US2] 在 `oryxos-boot/src/test/java/io/oryxos/boot/ScheduleRestartTest.java`、`oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java` 与 `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerConfigurationTest.java` 加生命周期 RED，断言关机取消句柄但保留状态、显式注销退役、跨启动全量协调删除/改键、历史仍可读，以及会话、核心记忆、审计四类证据（FR-002、FR-007、SC-002）。
- [x] T015 [US2] 修改 `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`，全量登记成功后才协调缺失项，关闭只取消句柄，显式注销保留退役语义。使用 `oryxos-storage/src/main/java/io/oryxos/storage/JpaScheduledTaskStore.java` 已有事务能力，通过 T014（FR-002、FR-007、SC-002）。
- [x] T016 [US2] 在 `oryxos-boot/src/test/java/io/oryxos/boot/RestartRecoveryIT.java` 增加 `@Tag("integration")` 真实验证。使用打包产物和独立工作区，通过现有 API 核对原 Session、核心记忆、任务启停/历史、下次时间和前后审计。对测试启动的进程执行强制终止并再次启动。默认构建发现该用例并按 opt-in 条件跳过（FR-007、FR-010、SC-002）。

## Phase 5: User Story 3 - 多 Agent 稳定运行 (P1)

**Independent Test**: A、B 隔离工具和会话。A 失败或超时后 B 仍可执行，再核对天气和通知审计。

- [x] T017 [US3] 在 `oryxos-boot/src/test/java/io/oryxos/boot/ScheduleStabilityTest.java`、`oryxos-core/src/test/java/io/oryxos/core/scheduler/AgentSchedulerTest.java` 和 `oryxos-web/src/test/java/io/oryxos/web/schedule/ScheduleApiControllerTest.java` 加故障场景。用短超时和 latch 验证 504、失败历史只记一次、迟到 worker 持锁、B 可继续、错误脱敏和 Session ID 可关联。用本地慢 webhook 验证超时。复测 Provider/Tool 超时、外部 MCP 不可达告警后仍启动、无 OpenAI key 启动。对新增行为保存 RED（FR-003、FR-005、FR-009、SC-005）。
- [x] T018 [US3] 修改 `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java`、`oryxos-boot/src/main/java/io/oryxos/boot/SchedulerConfiguration.java` 与 `oryxos-web/src/main/java/io/oryxos/web/schedule/ScheduleApiController.java`。装配 60 秒边界和受管虚拟线程，超时只完成一次并请求中断，保持 worker 持锁直到退出。记录安全原因和 Session ID，复用 Web 504 映射（FR-003、FR-005、FR-009、SC-005）。
- [x] T019 [P] [US3] 在 `oryxos-boot/src/main/java/io/oryxos/boot/NotifyConfiguration.java` 配置通知连接 3 秒、读取 10 秒，保留禁止重定向与 Sandbox。执行 T017 至 GREEN，报告已有 Provider 与 MCP 保护的回归证据（FR-009、SC-005）。
- [x] T020 [US3] 在 `oryxos-boot/src/test/java/io/oryxos/boot/MultiAgentScheduleTest.java` 用同键的 A/B、不同工具、交替调用和捕获触发验证两者工具及 Session 隔离。让 A 定时异常，B 下次照常。用可控天气和 webhook 的三轮脚本断言两条工具及三条模型审计、单次通知、会话复用和白名单失败不推送（FR-008、FR-010、SC-003、SC-004）。
- [x] T021 [US3] 在 `oryxos-boot/src/test/java/io/oryxos/boot/SchedulerFlowIT.java` 增加 `@Tag("integration")` 真实 Provider、天气和 webhook 接收测试。按 Session 和每次触发的新增记录断言模型实际轮次和各一次工具调用、通知实收、连续触发复用及白名单失败隔离。使用 opt-in 条件（FR-008、FR-010、SC-004）。

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T022 更新 `README.md` 的新旧管理端点表、停止/补跑语义、Demo 的安全白名单和测试 Agent 配置。为 webhook、新闻 MCP、记忆偏好及显式时区提供核对步骤。真实配置留在独立测试工作区（FR-005、FR-010）。
- [x] T023 执行 `specs/013-scheduled-task-operations/quickstart.md` 的聚焦测试、前端测试与构建、根目录 Maven Wrapper `clean verify`。按课件逐项核对产物、测试发现、配置和脱敏，检查 `git diff --check`。报告真实外部运行或条件跳过（FR-010、SC-001、SC-002、SC-003、SC-004、SC-005）。
- [x] T024 按 `.claude/skills/speckit-converge/SKILL.md` 对照 `specs/013-scheduled-task-operations/spec.md`、`specs/013-scheduled-task-operations/plan.md`、`specs/013-scheduled-task-operations/tasks.md` 和宪章。追加缺口须回到实施和完整验收，最后在对话给出节级报告（FR-010）。

## Dependencies & Execution Order

- T001 → T002/T003 → T004–T006。每个故事完成后运行独立验收。US2、US3 使用 US1 的运行入口和状态查询，真实 IT 使用已打包产物。
- T007 包含 T013 的 RED，完整 GREEN 在 T010 后运行。T009 与 T011 先写各自的失败测试，代码按后端契约到前端消费的顺序接通。
- 并行机会仅限互不写同一文件的工作，例如 US1 的 Web 与前端测试。US3 的通知装配和调度超时实现可分别完成，T017 的整体 GREEN 等 T018 与 T019。
- Maven 命令串行执行。US1 是首个可验收增量，US2 增加恢复，US3 完成稳定性和独立外部链路验收。

## 实施记录

- RED 阶段发现调度状态契约和 JPA 装配缺失。评审补充的回归测试复现了首次 Cron 时间偏差、工具线程超时后同一 Profile 提前再入、Provider 清除中断后仍可启动工具、短命 CLI 改写服务的调度状态，以及慢 webhook 未及时结束。
- 聚焦 Maven 首轮退出码为 1。现有 Mock Provider 因 `save_memory` 注册为 `JAVA` 而被默认策略拒绝。修正后按名称和注册来源校验该记忆工具，仍保留可见性校验、统一执行和自定义策略优先。复跑聚焦 Maven 命令退出码为 0，62 项通过。
- 前端复跑退出码为 1，运行状态页在接口缺少 `javaVersion` 时出现渲染异常。补上缺省值后，`npm --prefix frontend test -- --no-cache` 通过 56 项，`npm --prefix frontend run build` 退出码为 0。
- Windows 验收使用 JDK 21、Maven Wrapper 离线缓存，以及现有 Mockito Java agent。回环网络仅用于本地测试替身和临时 Spring Boot 服务。
- 全量 `mvnw.cmd -o clean verify` 前两轮退出码均为 1，分别命中 Checkstyle 的字段间空行规则，以及 SpotBugs 的外部可见锁对象规则。补齐空行，并将 Cron 状态写入与注销同步移到私有注册代次，保留原静态检查配置。
- 第三轮全量测试已通过 Storage 用例，但 SpotBugs 报告 JPA 字段写入和构造器可见性问题。显式声明列映射，把 Spring 注入构造器收为包内可见。随后独立编译、打包及静态预检退出码为 0。
- 可执行 JAR 冒烟检查退出码为 0。通过 `/console/index.html` 读取页面及资源，Mock Provider 补跑后停用任务，强制终止进程并用同一临时工作区启动新进程，核对稳定 ID、停用状态、执行次数、历史和记忆。
- 最终一轮 `mvnw.cmd -o clean verify` 已按用户要求中断。万文件检索集成测试已通过，日志停在 CLI 模块测试阶段。T023 全量验收与 T024 收敛核对仍待完成，两个真实链路 IT 尚未执行。
- 恢复后重新执行 `mvnw.cmd -o clean verify`，退出码为 0。Failsafe 发现 `RestartRecoveryIT` 和 `SchedulerFlowIT`，各按显式启用条件跳过 1 项。打包检查确认 Web JAR 包含管理台及资源，Storage JAR 包含 SQLite V8。文件存在性和 `git diff --check` 检查通过。
- 收敛核对覆盖 FR-001 至 FR-010、SC-001 至 SC-005 及宪章。登记与恢复由核心和 SQLite 测试覆盖，v1/v2 与管理台由 Web 和前端测试覆盖，故障隔离与审计由 Boot 测试及本机 JAR 冒烟覆盖。真实外部验证依照 quickstart 独立启用。本轮无追加实施任务。
- 补充真实验收使用本地 YAML 配置。首次运行 `SchedulerFlowIT` 通过，`RestartRecoveryIT` 因 Windows 临时工作区未初始化 `MEMORY.md` 而退出。按项目初始格式补齐测试文件后单独复跑，`RestartRecoveryIT` 通过。两项均使用真实 Provider，通知由本机 Webhook 接收端核验。
