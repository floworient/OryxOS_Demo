# Verification: 定时任务核心能力

## 基线

- 日期：2026-11-04（Asia/Shanghai）
- Worktree：`E:\Program\OryxOS_Demo\.worktrees\scheduler-core`
- 分支：`codex/scheduler-core`
- 基线提交：`36af053`（Spec-Kit 设计与计划）
- JDK：21；Maven Wrapper：3.9.11
- 工作区状态：隔离 worktree 在基线时无产品代码修改；主工作区的 Sandbox 未提交修改未带入本分支。

### 基线命令

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-boot -am test
```

结果：exit 0，`BUILD SUCCESS`，总耗时 02:11；core 101 tests、boot 22 tests 均 0 failures / 0 errors。由于 `-am`，相关前置模块也完成测试。

环境说明：受限沙箱中 Mockito/ByteBuddy 自附加失败，Surefire dump 为 `could not open (5) pipe ... operation load`；同一命令在获批的沙箱外执行后通过。后续含 Mockito 的 Maven 验证沿用已批准前缀在沙箱外运行，不修改项目配置掩盖环境限制。

测试日志中出现一次既有固定端口占用的 ApplicationContext 启动失败场景，但对应测试继续使用随机端口分支并最终通过；没有基线失败。

## RED / GREEN 记录

### Task 1 / T002：配置校验与稳定身份

- RED 1：`AgentLoaderTest.invalidCronRejectsWholeAgent` 和 `invalidZoneRejectsWholeAgent` 均失败，实际返回含非法配置的 Profile。
- RED 2：`ScheduleIdTest` testCompile 失败，15 处均为缺失 `ScheduleId` 类型/符号。
- GREEN：新增确定性、带长度边界的 `ScheduleId`，显式 `spring-context` 依赖，以及 AgentLoader cron/ZoneId 加载期校验。
- 聚焦结果：`AgentLoaderTest`、`AgentLoaderYamlSafetyTest`、`ScheduleIdTest` 共 12 tests，0 failures / 0 errors。
- 既有契约回归：补充重复 schedule key 整 Agent 拒绝测试；生产代码此前已实现该行为，不将其记录为新增 RED。

### Task 2 / T003：调度状态契约与内存 Store

- RED：`InMemoryScheduledTaskStoreTest` testCompile 失败，25 处均为缺失 `ScheduledTaskDefinition`、`TaskExecution` 与 `InMemoryScheduledTaskStore` 类型/符号。
- GREEN：实现定义与执行值约束、可替换 Store 接口，以及基于 `ConcurrentHashMap`/`CopyOnWriteArrayList` 的当前进程内存实现。
- 聚焦结果：`ScheduleIdTest`、`InMemoryScheduledTaskStoreTest` 共 7 tests，0 failures / 0 errors；8 线程各追加 100 条执行记录，无丢失。
- 边界：`retire` 只移除活动定义并保留执行历史；读取返回不可变快照；未引入持久化、重试或管理 API。

### Task 3 / T004-T005：动态注册与统一 Agent 链路

- RED：首次运行除缺少 `AgentScheduler` 外还暴露 Mockito 对 `ScheduledFuture<?>` 的泛型桩编译问题；改为 `doReturn` 后重跑，testCompile 仅剩 3 处缺少 `AgentScheduler`。
- GREEN：实现从 `ProfileRegistry` 动态登记 `CronTrigger`，使用 schedule 自身 ZoneId，并以固定 `("scheduler", "scheduler", profileName)` 获取 Session 后仅调用 `AgentService.process`。
- 聚焦结果：`AgentSchedulerTest` 2 tests，0 failures / 0 errors；同一捕获 Runnable 连续执行两次，复用确定性 Session 并记录两条 success execution。

### Task 4 / T009-T010：Profile 互斥与失败隔离

- RED：新增 4 个并发/异常测试后共 6 tests，其中 Session、Agent、Store 三类异常隔离测试失败；异常均从 Runnable 外抛。Profile 级同/异规则互斥与不同 Profile 并行测试已通过。
- GREEN：锁表改为 `ConcurrentHashMap<String, ReentrantLock>`；回调以 `tryLock` 跳过重叠执行，分别隔离业务执行和 Store 记录异常，并始终在 `finally` 解锁。
- 安全日志：仅记录 `scheduleId`、`profileName` 和异常简单类名；输出捕获确认 canary 异常消息未出现。
- 稳定性：`AgentSchedulerTest` 连续运行 3 次，每次 6 tests，均 0 failures / 0 errors。

### Task 5 / T011-T012：安全生命周期

- RED：加入生命周期场景后 `AgentSchedulerTest` 11 tests 中 5 个失败，分别证明重复注册会重复 schedule、注册失败不回滚且泄露底层消息、null future 产生非契约 NPE、注销与关闭未取消 future。
- GREEN：所有定义和 Trigger 先构造；相同定义幂等，变化定义显式拒绝；首次登记使用临时 future 集合，失败时 `cancel(false)` 并 retire 本次全部定义。
- 注销与关闭：按 Profile 取消 future、退役活动定义且不删除锁；旧 Runnable 持锁期间注销并同名重注册，新 Runnable 仍会 skip，释放后可继续执行；`close()` 幂等。
- 聚焦结果：`AgentSchedulerTest` 11 tests，0 failures / 0 errors。

### Task 6 / T006-T007/T013：Boot 装配

- RED：校正多模块测试筛选后，`SchedulerConfigurationTest` testCompile 仅因缺少 `SchedulerConfiguration` 失败。
- GREEN：提供唯一 4 线程 `ThreadPoolTaskScheduler`（`oryxos-scheduler-`、remove-on-cancel、等待运行任务完成）、可被自定义 Bean 覆盖的内存 Store，以及 `AgentScheduler` 的 init/destroy 生命周期。
- 聚焦结果：`SchedulerConfigurationTest` 3 tests，0 failures / 0 errors；自定义 Store 覆盖默认实现，关闭上下文后定义被 retire；代码扫描未发现业务 `@Scheduled` 方法。
- 启动回归：`OryxOsApplicationIT` 2 tests 通过；整条 `-pl oryxos-boot -am ... verify` Reactor 15 个模块全部 SUCCESS，Checkstyle、SpotBugs、JaCoCo 门禁通过，总耗时 05:28。
- 既有环境行为：Boot surefire 中固定端口首次启动碰到占用后，既有测试切换随机端口并最终通过；未归因于调度改动。

### Task 7 / T008：真实链路与审计集成

- RED 1：首次 testCompile 因测试夹具的原始 `AtomicReference` 取值需要显式转换而失败；修正后测试进入真实 Spring/SQLite 链路。
- RED 2：调度回调及审计已成功落库，但 Session 断言误用不存在的 `id` 列而失败；核对 Flyway V3 与 `SessionEntity` 后改用契约列 `session_id`，未修改生产代码。
- GREEN：使用捕获的动态调度 Runnable、可控 Provider/Tool 和临时 SQLite，验证固定 scheduler Session、真实 `AgentService -> ReActLoop -> ProviderService -> ToolExecutor` 链路，以及审计与工具执行次数。
- 聚焦结果：`SchedulerAgentServiceIT` 1 test，0 failures / 0 errors；Session 1 条、`llm_calls` 2 条、`tool_invocations` 1 条、工具执行 1 次、成功执行历史 1 条，且 `ProfileContext` 已清理。
- 聚焦命令为缩短反馈跳过静态门禁与覆盖率；Task 6 已通过完整 Reactor 门禁，Task 7 后继续执行 core/boot 相关回归并在最终收敛重新运行完整验证。

## 完整门禁

| 命令 | 结果 |
| --- | --- |
| `.\mvnw.cmd -pl oryxos-core,oryxos-boot -am test` | exit 0，`BUILD SUCCESS`，02:35 |
| `.\mvnw.cmd clean verify` | exit 0，15 个 Reactor 模块全部 SUCCESS，06:04；Checkstyle、SpotBugs、JaCoCo 通过 |
| `npm --prefix website run docs:build` | exit 0，VitePress 1.6.4，2.53s |
| `git diff --check` | exit 0；仅有沙箱无法读取用户级 Git ignore 的环境警告 |

网站首次构建因隔离 worktree 尚未安装 `vitepress` 而未启动。受限沙箱中的 `npm ci` 又因无权写全局 `D:\dev\npm-cache` 失败；获批在沙箱外按 `website/package-lock.json` 执行 `npm --prefix website ci` 后安装 127 个包，重跑构建通过。`website/node_modules/` 和构建输出均由 `.gitignore` 排除。

## 成功标准证据

| 标准 | 自动化证据 |
| --- | --- |
| SC-001 | `AgentSchedulerTest` 断言每条合法定义的 cron、ZoneId、消息保持一致，捕获的回调每次只调用一次 `AgentService`；真实 IT 再验证一次统一入口。 |
| SC-002 | 并发测试在同一 Profile 持锁时触发同/不同规则，额外 Agent 调用与 execution 均为 0；释放后没有延迟补跑。 |
| SC-003 | Session、Agent、Store 异常路径后再次触发均可进入；`AgentSchedulerTest` 连续运行 3 次稳定通过。 |
| SC-004 | `ScheduleIdTest` 对同一二元组独立构造 3 次完全一致，并覆盖跨 Agent 与长度拼接歧义矩阵，无冲突。 |
| SC-005 | 第二条注册失败和 null future 场景均断言已创建 future 使用 `cancel(false)`，活动定义残留为 0。 |
| SC-006 | 单元测试连续两次触发复用 `SessionManager.getOrCreate("scheduler", "scheduler", profileName)`；IT 验证确定性 Session 已持久化。 |
| SC-007 | 聚焦单元、真实 Provider/Tool/SQLite IT、core/boot 回归和全仓 `clean verify` 全部通过，不需要真实密钥或公网。 |
| SC-008 | README 与中英文 Profile 文档均披露内存状态、单进程互斥、无自动重试、无管理 API、无重启恢复和无多实例去重。 |

## 交付映射与未验证范围

- 每日天气/科技日报/GitHub 日报所需的“钟推入口”已实现：`AGENT.md` 配置动态注册、显式时区、固定 scheduler Session、统一 Agent/Tool/审计链路和单进程防重叠。
- 本 feature 没有等待真实 cron 时刻，也没有调用天气、GitHub、通知或真实模型服务；自动化通过捕获 Spring `TaskScheduler` Runnable 与可控 Provider/Tool 证明行为。外部白名单、密钥和服务可用性需在相应 Demo 集成阶段单独验证。
- 调度定义与执行历史仅驻留当前 JVM；未新增数据库表、管理 REST/CLI、运行时热修改、启停/立即执行、自动重试或多实例协调。多实例部署可能重复执行同一规则。

## Reviewer 导读

1. 从 `oryxos-core/src/main/java/io/oryxos/core/scheduler/AgentScheduler.java` 查看动态注册、Profile 锁、失败隔离和生命周期事务边界。
2. 查看同目录的 `ScheduleId`、定义/执行值对象与 `ScheduledTaskStore`，确认稳定身份和后续持久化替换点。
3. 查看 `oryxos-boot/src/main/java/io/oryxos/boot/SchedulerConfiguration.java`，确认 4 线程池、默认内存 Store 与 init/destroy 装配。
4. 对照 `AgentSchedulerTest`、`SchedulerConfigurationTest` 与 `SchedulerAgentServiceIT`，分别审阅行为、Spring 生命周期和真实统一链路审计。

## 独立评审修复

- reviewer 识别出匿名异常类名为空、系统时钟回拨会使 execution 构造二次失败，以及注销/回滚后的旧回调仍可能进入业务链路。
- RED：新增聚焦测试后 21 tests 中 6 failures / 1 error，覆盖匿名异常、成功/失败时间回拨、注册提交前回调、注销后旧代次、定义重排、关闭后注册和非规范 UUID。
- GREEN：引入注册 generation（提交后激活、注销/回滚前失活、取得锁后复验），finishedAt 钳制、异常类型父类回退、顺序无关定义规范化、关闭终态和规范 UUID 校验；聚焦结果 21 tests，0 failures / 0 errors。
- Store 评审项按本阶段范围处理：默认内存 Store 的补偿回滚保持不变；契约明确逐定义接口不天然提供 Profile 级数据库事务，未来持久化适配器必须在启用前实现批量事务或等价补偿。
- 修复后验证：`oryxos-core` 129 tests 全部通过；根目录 `.\mvnw.cmd clean verify` 再次 15 模块全部 SUCCESS（03:42），Boot 15 个 IT 通过；VitePress 构建 2.62s，`git diff --check` exit 0。

