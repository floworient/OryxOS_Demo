# Tasks: 第 17 节 ReAct 运行内核

**Input**: `specs/002-react-loop-cli/` 的 spec、plan、research、data-model、contracts、quickstart。
**Status**: 当前30项任务全部完成；已采用用户批准的.link项目引用契约，普通权限全量test与clean verify通过。历史原生绑定失败保留为实施记录。
**Tests**: 按课件五个测试类及持久化回归执行 RED → GREEN → REFACTOR。先确认测试因行为缺失而失败，再写实现；编译缺类型只作为首轮脚手架阶段，随后必须断言行为。
**Scope approval**: 批准本清单同时批准 plan/contracts 明列的最小 Session 与审计支撑类型及 sessions 表。无需改前序公共接口，不新增第三方 artifact/配置键/REST/CLI。

## Phase 1: Setup

- [X] T001 核对 `pom.xml`、`oryxos-core/pom.xml`、`oryxos-storage/pom.xml` 与 `specs/002-react-loop-cli/research.md` 的本地依赖、JDK21、Wrapper；记录前序测试基线，保护已有修改（FR-013）。

## Phase 2: Foundational

- [X] T002 在 `oryxos-core/src/test/java/io/oryxos/core/session/SessionTest.java` 先测试渠道/用户/Agent 身份隔离、追加顺序和工具 ID/name 关联（FR-002、FR-012）。
- [X] T003 在 `oryxos-core/src/main/java/io/oryxos/core/session/Session.java`、`SessionManager.java` 实现最小会话与 save/find 契约；稳定身份不允许分隔歧义，消息快照不可被外部修改（FR-002、FR-012）。
- [X] T004 在 `oryxos-core/src/main/java/io/oryxos/core/audit/ToolInvocationRecord.java`、`ToolInvocationRecorder.java` 定义审计数据和 record 契约；sessionId/toolName/JSON 非空，durationMs >= 0，失败含原因（FR-009）。
- [X] T005 在 `oryxos-core/src/test/java/io/oryxos/core/profile/ProfileContextTest.java` 先测试未设置返回 null、设置可读、清理及线程隔离，再实现 `oryxos-core/src/main/java/io/oryxos/core/profile/ProfileContext.java`（FR-011）。

## Phase 3: User Story 1 - 多轮推理（P1，桩验证 MVP）

**Goal**: 核心循环可独立用 PromptBuilder/ToolExecutor 桩验证，不依赖后续完整实现。
**Independent Test**: ReActLoopTest 验证直接回复、顺序多工具、结果回填、10 次停止和失败。

- [X] T006 [US1] 在 `oryxos-core/src/test/java/io/oryxos/core/agent/ReActLoopTest.java` 先覆盖单轮、空文本、多工具顺序和关联、完整累积、默认及自定义上限、Provider 失败无后续调用；mock 尚未实现的协作者最小签名来自 contracts（FR-001～FR-003、SC-001、SC-002）。
- [X] T007 [US1] 在 `oryxos-core/src/main/java/io/oryxos/core/agent/ReActLoop.java` 实现 run；使用既有 ProviderResponse/ToolCallIntent 与 Profile.settings，不增加 Response/ToolCall 公共类型，所有调用携带 sessionId（FR-001～FR-003、FR-013）。
- [X] T008 [US1] 在 `oryxos-core/src/test/java/io/oryxos/core/agent/ReActLoopTest.java` 确认每轮响应和结果均进入 Session，达到上限追加终止文本且恰好 10 次模型调用，执行 core 聚焦测试并保留实际结果（SC-001、SC-002）。

## Phase 4: User Story 2 - 上下文（P1）

**Goal**: 每次读取正文与绑定，只向模型呈现允许的上下文和完整近期轮次。
**Independent Test**: ContextLoaderTest 与 PromptBuilderTest 不依赖真实模型、工具或 Memory 后端。

- [X] T009 [P] [US2] 在 `oryxos-core/src/test/java/io/oryxos/core/context/ContextLoaderTest.java` 先覆盖正文/Bootstrap 热读、Bootstrap 缺失 WARN、Skill 元数据/正文哨兵、.link重新绑定/删除/项目移动、断引用/绝对/盘符/UNC/多行/复制目录/越界拒绝；现存原生链接攻击保留附加IT（FR-006、FR-007、SC-005）。
- [X] T010 [P] [US2] 在 `oryxos-core/src/test/java/io/oryxos/core/context/PromptBuilderTest.java` 先覆盖 system/可选记忆/历史/options 顺序、固定 Clock 时间、超过 20 个用户轮次裁剪但完整历史不变、同轮多工具组、可见工具 schema-only、自动执行 false（FR-004、FR-005、FR-013、SC-003）。
- [X] T011 [US2] 在 `oryxos-core/src/main/java/io/oryxos/core/context/ContextLoader.java` 实现安全热读和普通 .link 相对引用绑定元数据加载，错误带 Agent 与路径，不缓存、不复制、不预载 Skill 正文（FR-006、FR-007）。
- [X] T012 [US2] 在 `oryxos-core/src/main/java/io/oryxos/core/context/PromptBuilder.java` 实现完整轮次视图、日期时间及可选空记忆槽，注入 schema adapter 函数和同一工具池，声明未知工具时报错，不改变既有 Profile/Provider（FR-004、FR-005、FR-013）。
- [X] T013 [US2] 在 `oryxos-core/src/test/java/io/oryxos/core/context/PromptBuilderTest.java` 与 `ContextLoaderTest.java` 运行上下文全部聚焦回归，检查恶意路径外文件读取次数为零（SC-003、SC-005）。

## Phase 5: User Story 3 - 工具与审计（P1）

**Goal**: 工具统一执行且成功失败均可靠记录。
**Independent Test**: ToolExecutorTest 使用桩策略，存储测试使用真实文件 SQLite。

- [X] T014 [P] [US3] 在 `oryxos-core/src/test/java/io/oryxos/core/tool/ToolExecutorTest.java` 先覆盖成功/业务失败/异常/未知工具/不可见/无 Profile/非法 JSON/策略拒绝、默认拒绝、审计失败、最多四次尝试及退避、超时不重试、执行线程 ProfileContext 传播和清理（FR-008～FR-010、FR-013、SC-002）。
- [X] T015 [P] [US3] 在 `oryxos-storage/src/test/java/io/oryxos/storage/ToolInvocationRepositoryTest.java` 先覆盖成功失败字段、duration_ms >= 0、created_at 非空、V1 升级到 V2 及跨上下文恢复（FR-009、SC-004）。
- [X] T016 [US3] 在 `oryxos-core/src/main/java/io/oryxos/core/tool/ToolExecutor.java` 实现 Map 注册解析、Profile.tools、JSON/策略校验、内部默认 60 秒有界执行；仅 retryable 失败额外重试三次、退避 100/200/400 ms；每次审计，异常不伪装成功、审计失败向外抛（FR-008～FR-010）。
- [X] T017 [US3] 在 `oryxos-storage/src/main/resources/db/migration/sqlite/V2__create_tool_invocations.sql`、`oryxos-storage/src/main/java/io/oryxos/storage/entity/ToolInvocation.java`、`repository/ToolInvocationRepository.java`、`JpaToolInvocationRecorder.java` 实现需求第 10 章字段及 core recorder；input_json/result_json 非空，success 非空，失败 error_message 有原因，不改 V1（FR-009）。
- [X] T018 [US3] 在 `oryxos-core/src/test/java/io/oryxos/core/tool/ToolExecutorTest.java` 与 `oryxos-storage/src/test/java/io/oryxos/storage/ToolInvocationRepositoryTest.java` 执行聚焦回归，确认拒绝执行零次、正常一次、重试逐次审计、无凭据哨兵泄漏（FR-013、SC-002、SC-004）。

## Phase 6: User Story 4 - 统一入口和恢复（P1）

**Goal**: 后续入口可复用同一 AgentService；保存结果可跨重启恢复。
**Independent Test**: AgentServiceTest 与 SessionPersistenceTest；完整 Boot 桩链路。

- [X] T019 [P] [US4] 在 `oryxos-core/src/test/java/io/oryxos/core/agent/AgentServiceTest.java` 先覆盖处理中当前 Agent、成功和上限后保存、未知 Agent、模型抛错及保存抛错后的 clear、同线程后续请求无串号（FR-011、SC-004）。
- [X] T020 [P] [US4] 在 `oryxos-storage/src/test/java/io/oryxos/storage/SessionPersistenceTest.java` 先覆盖完整多工具消息 JSON 往返、文件 SQLite 重建恢复、稳定联合身份、status 仅 active/archived、坏 JSON/未知 role/断裂关联拒绝、写入失败（FR-012、SC-003、SC-004）。
- [X] T021 [US4] 在 `oryxos-core/src/main/java/io/oryxos/core/agent/AgentService.java` 实现 process：注册表查 Profile、set、run、save、finally clear，失败不得虚报保存完成（FR-011）。
- [X] T022 [US4] 在 `oryxos-storage/src/main/resources/db/migration/sqlite/V3__create_sessions.sql`、`oryxos-storage/src/main/java/io/oryxos/storage/entity/SessionEntity.java`、`repository/SessionRepository.java`、`JpaSessionManager.java` 实现显式消息 DTO 编解码和 save/find；主键/联合身份/JSON/状态/创建及活跃时间非空，archived_at 可空，status active/archived；不改 core 前序接口（FR-012）。
- [X] T023 [US4] 在 `oryxos-boot/src/test/java/io/oryxos/boot/ReActConfigurationTest.java` 先写装配测试，覆盖真实 ProviderService + 模型桩 + schema-only 工具 + AgentService 链路、默认策略拒绝、session_id 审计关联与存储重建（FR-011～FR-013、SC-002、SC-004）。
- [X] T024 [US4] 在 `oryxos-boot/src/main/java/io/oryxos/boot/ReActConfiguration.java` 装配 core、storage 与 ToolSchemaAdapter；工具显式注册、重名拒绝、默认全拒绝策略、空记忆槽、Clock 和内部超时执行资源生命周期；若扫描配置需要调整仅改 `oryxos-storage/src/main/java/io/oryxos/storage/config/StorageConfiguration.java`（FR-008、FR-011～FR-013）。
- [X] T025 [US4] 在 `oryxos-core/src/test/java/io/oryxos/core/agent/AgentServiceTest.java`、`oryxos-storage/src/test/java/io/oryxos/storage/SessionPersistenceTest.java`、`oryxos-boot/src/test/java/io/oryxos/boot/ReActConfigurationTest.java` 运行全部相关模块聚焦测试，验证恢复数据含完整 tool ID 和结果（SC-003、SC-004）。

## Phase 7: Polish & Cross-Cutting

- [X] T026 按 `specs/002-react-loop-cli/quickstart.md` 运行 Wrapper test 与全量 clean verify，检查 Checkstyle/SpotBugs/JaCoCo/架构门禁；不得改阈值、禁用或删除断言（FR-013、SC-001～SC-005）。
- [X] T027 对照课件逐项核对 `specs/002-react-loop-cli/tasks.md` 所列交付物、五个课件测试类与 V2/V3 迁移，检查密钥泄漏、git diff --check、改动范围，执行 speckit-converge；有追加任务则重新实现并验证（FR-001～FR-013）。
- [X] T028 更新 `specs/002-react-loop-cli/quickstart.md` 与 `tasks.md` 的实际结果及完成标记，在对话交付节级报告；明确真实天气 Demo、CLI/HTTP/Sandbox/Memory 后续范围和未验证项（SC-001～SC-005）。

## Dependencies & Execution Order

T001 → T002～T005 → US1 → US2 → US3 → US4 → T026～T028。
US1 以契约桩独立验证，真实链路需等 US2/US3；T006 后可按需创建这些协作者的最小签名，不能提前写行为，行为必须在对应红测后实现。
T004 阻塞 T014～T017；T005 阻塞工具线程上下文和 AgentService；T017 的 V2 先于 T022 的 V3；T023 红测先于 T024 装配。

## Parallel Examples

- US1 测试与实现顺序执行，同文件不并行。
- US2：T009 和 T010 可分别编写；T011/T012 在各自红测后实施。
- US3：T014 和 T015；US4：T019 和 T020 可并行准备，不共享文件。
- [P] 仅表示独立文件机会，不要求自动启用多代理开发。

## Implementation Strategy

先 US1 桩链路建立最小可演示循环，再上下文、审计、持久化和装配。每个实现前先红测，每阶段运行相关聚焦测试。MVP 仅说明循环行为，不视为第 17 节整体完成。全量 verify 与 converge 无剩余工作后方可报告完成。未经批准不进入 implement，不提交或切换 Git 分支。

## 实施记录（2026-10-26）

- 用户明确批准当前 28 项 tasks，沿用 feature/react，不创建/切换分支。
- T002/T005 的初始最小实现测试红：3 个预期断言失败；随后实现。
- T006 的循环测试红：4 个预期断言失败；随后实现。
- core 全套测试 19 项通过、0 失败、0 错误（`.m2/lesson17-logs/core-first-green.log`）；包含 SessionTest、ProfileContextTest、ReActLoopTest 和已有 Provider/Profile 测试。
- 初始基线受默认临时目录权限影响，已通过 JAVA_TOOL_OPTIONS/TEMP/TMP 定向项目 `.m2/lesson17-tmp` 修正；Wrapper 使用项目 MAVEN_USER_HOME 与本地仓库。
- SessionTest 最初错误地读取 ToolResponseMessage.getText()，本地 API 的数据位于 getResponses().responseData()，已修正断言访问方式并保留内容/ID/name 语义。
- 上下文测试处于 RED；其中合法相对软连接场景在 Files.createSymbolicLink 阶段报“客户端没有所需的特权”。独立 PowerShell 提升权限探测也报 Administrator privilege required。
- 遵循课程硬门禁停止实施：未复制替代软连接，未禁用断言，未运行全量 clean verify 或 converge。ContextLoader/PromptBuilder/ToolExecutor 仍是待实现的最小签名，不代表能力交付。
- 解除阻塞：使用具备创建 Windows 原生软连接能力的管理员进程，或由用户配置允许原生软连接的开发环境，然后重跑上下文 RED 测试继续 T009。

### 2026-10-27 普通权限续作

- 用户明确要求“不使用管理员权限继续”，不再申请提升权限；保留软连接原生测试及其环境错误，不跳过、不复制替代。
- T014 ToolExecutor 6 项红→绿：单次执行、线程上下文传播/清理、拒绝、失败、4 次尝试上限、退避、超时取消和审计失败关闭。
- T019 AgentService 2 项红→绿；修正 Mockito 再打桩时触发旧 answer 的测试写法，未改变产品断言。
- T015/T020 先因缺少存储 Bean 失败，补齐 V2/V3/JPA 后通过；相关 core/storage 聚焦检查共6项测试通过（storage-green.log）。文件 SQLite 关闭重建后完整消息和成功失败审计均恢复；无效历史和非法状态拒绝。
- T023 Boot 测试先因缺少 AgentService Bean 失败，补齐 ReActConfiguration 后通过；真实 DefaultProviderService 加模型桩、可控工具和 SQLite 走同一 session_id，默认策略拒绝实际工具执行。
- 日志位于 `.m2/lesson17-logs/`；T009/T011/T013 因 Windows 软连接实际覆盖不足暂不勾选完成。

### 独立审查修复与完整验收

- 独立只读审查发现2项 Important：JSON 未拒绝尾部内容；Spring AI 消息内部可变列表可改写恢复的历史。均补充测试，确认 RED 后修复：局部严格 ObjectReader，不修改全局 mapper；Session 入站/出站消息快照隔离及内部不可变列表。
- 审查修复后聚焦回归21项通过（review-fixes-green.log），含 core18项、storage2项、boot1项。
- 全量 `-fae clean verify` 实际执行失败（full-clean-verify.log）：core36项，33通过、0断言失败、3环境错误、0跳过；3项均为 ContextLoaderTest 创建 Windows 原生软连接时“客户端没有所需的特权”。下游模块因core失败未进入全量验收，不能称为全量通过。
- 既有未提交的 `.github/dependabot.yml`、`.claude/settings.local.json` 和 `.workbuddy/` 保留，不属于本节改动。
- 审查未列Critical或Minor；逐项核对重复执行、重试上限、上下文清理、审计失败、轮次截断与Skill正文未预载，未发现其他实现缺陷。取消不合作工具仅保证调用方有界等待；Memory/HTTP/Sandbox/CLI/并发会话治理仍为已批准的后续范围。

- Session 无正文工具调用的快照归一化补充回归先RED后修复；最新完整test共36项，33通过、0断言失败、3原生软连接环境错误、0跳过（full-test.log）。

## Phase 8: Convergence

收敛日期：2026-10-27。检查13项FR、5项SC、17个用户验收场景及6项宪章原则；代码所需交付物均存在。2项审查缺陷已由RED→GREEN修复，无新增实现缺陷；以下为尚未满足的验收工作，不能由静态审阅替代。

- [X] T029 按用户2026-10-27批准的新契约，在普通权限下执行 `oryxos-core/src/test/java/io/oryxos/core/context/ContextLoaderTest.java` 全部.link真实文件测试，验证绑定/重新绑定/移动/元数据及非法路径拒绝，保留 `ContextLoaderNativeLinksIT.java` 为显式opt-in原生攻击附加验证；原native绑定要求由批准设计替代，安全边界不变；按 FR-007、SC-005 和 T009/T011/T013 补齐证据（partial）。
- [X] T030 在T029通过后按 `specs/002-react-loop-cli/quickstart.md` 重新执行完整 test 与 clean verify，确认JaCoCo/架构/集成测试及全部任务门禁，再重跑speckit-converge，不能把本次独立静态检查当完整verify通过；按 Constitution VI、T026/T027 完成验收（partial）。

### 最终证据汇总（普通权限）

- 编译、Checkstyle、SpotBugs 全部15个构建项目通过（quality-final.log），未改变规则、阈值或排除项。
- 最终完整 test 与 clean verify 均因同3个原生软连接环境错误非零退出；core36项中33通过、0断言失败、0跳过。JaCoCo及完整集成门禁未随verify完成，不能称为全量验收通过。
- 最新SessionTest、SQLite恢复/工具审计、Boot链路与模块边界聚焦8项通过（final-storage-boot.log）；这是明确的聚焦验证，不替代完整套件。
- 课件全部代码及5个测试类、V2/V3迁移均实际存在；本节新增27个Java文件、2个迁移SQL，范围限core/storage/boot及本feature规范。git diff --check通过，无禁用测试或安全阈值修改。
- 收敛状态tasks_appended：T029/T030为待补验工作，当前不宣称完成。真实模型天气Demo及后续CLI/HTTP/Sandbox/Memory仍未验证/不在本节实现范围。

## 2026-10-27 用户批准的项目引用修订

- 用户明确确认 `docs/superpowers/specs/2026-10-27-project-skill-bindings-design.md`，授权用统一.link引用替换原生绑定并同步宪章2.0.0；上方原生绑定环境失败仍保留为历史，不再作为现行项目引用的先决条件。
- 已写实现计划 `docs/superpowers/plans/2026-10-27-project-skill-bindings.md`。不新增产品接口/配置/依赖；附加原生攻击IT的测试-only开关不属于产品配置。
- 新的真实普通文件夹具在旧实现上有4项合法场景失败（project-links-red.log），改造后core51项全部通过，0失败、0错误、0跳过（project-links-core-green.log）。绝对/盘符/UNC/越界/断引用/多行/过大及未知格式均保留拒绝断言。

- 绑定改造后core全套51项通过，0默认跳过，T009/T011/T013/T029的新契约验收通过。旧native攻击保留独立附加IT，未执行时必须明确披露，不将其当作动态通过。
- 全量 `test` 已通过，15个构建项目SUCCESS（project-links-full-test.log）；完整clean verify进行中，T026/T027/T030尚不提前勾选。

## 最终自动验收（2026-10-27，项目引用契约）

- `test`：全部15个构建项目SUCCESS，87项单元/组件测试通过，0失败、0错误、0跳过。日志 `.m2/lesson17-logs/project-links-full-test.log`。
- `clean verify`：全部15个构建项目SUCCESS，XML汇总91项，89通过、0失败、0错误、2按条件跳过。Checkstyle15个项目零违规，SpotBugs零缺陷，5个有业务代码的覆盖率门禁通过。日志 `.m2/lesson17-logs/project-links-clean-verify.log`。
- 两项未执行验证分别为缺少真实调用双门禁条件的ProviderSmokeIT，以及未显式开启测试-only原生攻击验证的ContextLoaderNativeLinksIT；普通.link绑定19项实际文件测试全部执行且通过，不依赖管理员权限或系统设置。
- 可执行JAR已生成：`oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar`。该产物不代表后续CLI/HTTP/Memory/Sandbox业务功能已交付；本节运行时通过AgentService复用。
- 引用修改、重新绑定、删除、工程整体移动、BOM/换行/跨平台分隔符、非法绝对/盘符/UNC/多行/过长/缺失/越界目标均已实测；公共Skill不复制，正文不预载，Prompt给出真实可读取路径。
- 保留此前原生绑定失败的历史证据；用户明确批准的契约修订消除了其环境先决条件，不是禁用新契约断言或放宽路径边界。
- 独立审查无Critical/Important；示例读取路径已同步。git diff --check通过，交付物存在性核对通过，无真实密钥进入新增代码。用户无关改动未回滚，未commit/push。

## 审查修复（2026-10-27，实现质量复查）

代码审查发现 5 项问题，按 TDD 先红测后修复。其中 settings 边界 2 项已被 `AgentLoader.settings()` 的 `> 0` 校验拦截，属防御性缺口；其余 3 项为实际缺陷。

- 修复 A（正确性，P1）：`ToolExecutor` 对 `call.id()` 有空校验，却缺对称的 `call.name()` 校验。name 为 null/空白时，`tools.get(null)` 与审计记录 null 名会使 `ToolInvocationRecord` 抛异常，经包装后以 `IllegalStateException("工具审计持久化失败")` 中止整轮。现改为 `named` 前置校验：非法名称返回 `failed("工具调用名称非法")`，审计名兜底 `"<invalid>"`，与 id 校验对称。新增 `ToolExecutorTest.nullOrBlankToolNameIsAuditedAsFailureInsteadOfAbortingTheTurn`。
- 修复 B（一致性）：`AGENT.md`/`SKILL.md` 的 frontmatter 解析未剥离 UTF-8 BOM，而 `.link` 解析器已剥离，属遗漏。三处补 U+FEFF 处理：`ContextLoader.load` 读正文后、`ContextLoader.metadata` 读首个 frontmatter 行、`AgentLoader.parseFrontmatter` 读内容后。新增 `ContextLoaderTest.bomPrefixedAgentAndSkillFrontmatterLoad` 与 `AgentLoaderTest.bomPrefixedAgentMarkdownLoads`。
- 修复 C（效率）：`ContextLoader.appendSkills` 在循环内重复 `toRealPath` 计算公共 skills 根。`lexicalRoot` 提到循环外，`publicRoot` 改为懒计算；依赖既有 `ContextLoaderTest` 守护，无行为变化。
- 修复 D（防御性）：`Profile.Settings` 原先仅在 `AgentLoader.settings()` 要求 `> 0`，直接构造 Profile 可绕过。现 `Profile.Settings` 紧凑构造器对非正值抛 `IllegalArgumentException`。新增 `ProfileSettingsTest`。

验证：

- RED：5 个新测试分别以 `缺少 frontmatter 起始边界`、`工具审计持久化失败`、settings 断言失败，日志 `.m2/lesson17-logs/fix-red.log`。
- GREEN：core 56 项全过，日志 `.m2/lesson17-logs/fix-green.log`。
- 全量 `clean verify`：15 个构建项目 SUCCESS，92 项测试 0 失败、0 错误、0 跳过；Checkstyle 15 个项目零违规，SpotBugs 零缺陷，5 个覆盖率门禁通过，日志 `.m2/lesson17-logs/fix-clean-verify.log`。
- 中途一次 `test` 失败为 IDE 的 ECJ 向 `oryxos-provider/target/test-classes` 写入带 `Unresolved compilation problems` 的错误字节码（`NoClassDefFoundError: ProviderErrorCode`），`clean` 重建后消失，与本次修复及产品代码无关。
- 真实 Provider/天气 Demo 仍未执行；未 commit/push，用户未提交改动保留。`git diff --check` 通过，新增测试无字面 BOM 字符。
