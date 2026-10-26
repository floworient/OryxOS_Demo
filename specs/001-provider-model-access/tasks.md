# Tasks: 第 16 节核心契约与 Provider 接入

**Input**: `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`

**Tests**: 本节采用测试先行。每个测试任务必须先运行并确认因缺少目标行为而失败，再实施对应代码；不得删除断言、添加 `@Disabled` 或放宽质量阈值。

**门禁**: 不创建分支，不 commit/push，不运行 `package.sh`；新增依赖仅限 plan 已列出的四项。任何新增公开类型、配置键、表列、接口签名或第三方依赖必须先触发软门禁。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 不修改同一文件且不依赖未完成任务，可并行
- **[USn]**: 对应 `spec.md` 的 User Story
- 每项均包含精确文件路径

---

## Phase 1: Setup（依赖与测试基线）

**Purpose**: 只声明 plan 中已核实的依赖与测试能力，不引入额外 starter 或配置键。

- [X] T001 修改 `pom.xml`：导入 `org.springframework.ai:spring-ai-bom:1.1.2`，不得改变现有 Spring Boot 3.5.8 / Spring AI Alibaba 1.1.2.2 版本
- [X] T002 [P] 修改 `oryxos-core/pom.xml`：新增 `spring-ai-model`、Boot 管理版本的 `org.yaml:snakeyaml` 和测试依赖，不写显式版本
- [X] T003 [P] 修改 `oryxos-provider/pom.xml`：新增 `spring-ai-openai` 与测试依赖；保留现有 DashScope 依赖，不新增 OpenAI starter
- [X] T004 [P] 修改 `oryxos-storage/pom.xml`：新增 Boot 管理版本的 `hibernate-community-dialects` 与测试依赖，不写显式版本
- [X] T005 运行 Maven 离线依赖树并记录单一版本证据：`mvn -o -Denforcer.skip=true -pl oryxos-provider -am dependency:tree`；如本地坐标不存在，触发软门禁 5，不联网猜测 API

**Checkpoint**: 依赖只来自 plan，现有骨架仍可编译；若 Maven Wrapper 环境不可用，只报告环境问题，不改 Wrapper 或降低 Enforcer。

---

## Phase 2: Foundational（跨故事阻塞契约）

**Purpose**: 建立 Profile、Provider、Tool 与审计共享契约；本阶段完成前不得实现具体故事。

- [X] T006 [P] 新建 `oryxos-core/src/main/java/io/oryxos/core/tool/ToolResult.java`：字段严格为成功标识、内容、错误信息、是否可重试
- [X] T007 [P] 新建 `oryxos-core/src/main/java/io/oryxos/core/tool/OryxTool.java`：仅声明 `getName`、`getDescription`、`getInputSchema`、`execute` 四个技术方案固定方法
- [X] T008 [P] 新建 `oryxos-core/src/main/java/io/oryxos/core/provider/ProviderErrorCode.java` 与 `ProviderException.java`：包含契约列出的错误码，异常消息必须可安全输出且不含凭据
- [X] T009 [P] 新建 `oryxos-core/src/main/java/io/oryxos/core/provider/ProviderResponse.java`、`TokenUsage.java`、`ToolCallIntent.java`：token 未知使用三个 null 字段显式表示
- [X] T010 新建 `oryxos-core/src/main/java/io/oryxos/core/provider/ProviderService.java`：签名严格为 `chat(String sessionId, Profile profile, Prompt prompt)`，不增加查询或启动校验方法
- [X] T011 [P] 新建 `oryxos-core/src/main/java/io/oryxos/core/audit/LlmCallStatus.java`、`LlmCallRecord.java`、`LlmCallRecorder.java`：字段与 `data-model.md` 的 `llm_calls` 列一一对应并包含 `profileName`
- [X] T012 新建 `oryxos-storage/src/main/resources/db/migration/sqlite/V1__create_llm_calls.sql`：逐列实现 `data-model.md`，包括 `profile_name NOT NULL`、三个可空 token 列、`usage_known`、状态/错误字段和两个索引
- [X] T013 修改 `oryxos-boot/src/main/resources/application.yaml`：启用 DataSource/JPA/Flyway，配置 SQLite、`ddl-auto=validate` 与 `classpath:db/migration/sqlite`；保留全部 DashScope 自动配置排除，不新增未列入 plan 的配置键

**Checkpoint**: core 不依赖 provider/storage；数据库只能由 Flyway 建表，禁止 `ddl-auto=update`。

---

## Phase 3: User Story 1 — 加载完整 Agent 运行配置（Priority: P1）🎯 MVP

**Goal**: 从 Agent 目录安全派生完整 Profile，坏 Agent 隔离，合法 Agent 可按名称查询。

**Independent Test**: 两个合法 Agent 和一个非法 Agent 同时加载；合法 Profile 字段完整，非法 Agent 不注册且错误可定位，空工作区返回空结果。

### Tests for User Story 1（先红后绿）

- [X] T014 [P] [US1] 新建 `oryxos-core/src/test/java/io/oryxos/core/profile/AgentLoaderTest.java`：覆盖全部字段、默认值、列表顺序、正文不参与 YAML 解析和空工作区
- [X] T015 [P] [US1] 新建 `oryxos-core/src/test/java/io/oryxos/core/profile/AgentLoaderIsolationTest.java`：覆盖坏 YAML、缺字段、目录名与 `name` 不一致、`skills`/`notify_channels` 被拒绝且不阻断其他 Agent
- [X] T016 [P] [US1] 新建 `oryxos-core/src/test/java/io/oryxos/core/profile/AgentLoaderYamlSafetyTest.java`：覆盖重复键、任意类型标签，以及 aliases=50、nesting=50、code points=3145728 的边界，断言越界安全失败且不实例化任意类
- [X] T017 [P] [US1] 新建 `oryxos-core/src/test/java/io/oryxos/core/profile/ProfileRegistryTest.java`：覆盖按名查询、全量快照、Provider 引用集合和同名注册拒绝
- [X] T018 [US1] 运行 US1 测试并保存红灯证据；失败原因必须是目标类型/行为尚未实现，不能是测试语法或环境错误

### Implementation for User Story 1

- [X] T019 [P] [US1] 新建 `oryxos-core/src/main/java/io/oryxos/core/profile/Profile.java`：字段严格为 `name/description/identity/provider/tools/mcpServers/channels/schedules/bootstrap/settings/sourcePath`，子结构使用该文件内的嵌套 record；不含 `skills`、`notifyChannels` 或 Persona
- [X] T020 [P] [US1] 新建 `oryxos-core/src/main/java/io/oryxos/core/profile/AgentLoader.java` 骨架：公开加载结果只返回成功 Profile；Agent、源路径、字段、脱敏原因等错误细节保持实现内部并写结构化日志，不新增公开错误类型
- [X] T021 [US1] 新建 `oryxos-core/src/main/java/io/oryxos/core/profile/ProfileRegistry.java`：实现登记、移除、按名查询、只读全量快照、Provider 引用集合；重复名称报错，不使用 last-write-wins
- [X] T022 [US1] 修改 `oryxos-core/src/main/java/io/oryxos/core/profile/AgentLoader.java`：扫描 `${oryxos.workspace}/agents/*/AGENT.md`，用 `SafeConstructor(LoaderOptions)` 解析 frontmatter，禁止重复键并显式设置 aliases=50、nesting=50、code points=3145728
- [X] T023 [US1] 在 `oryxos-core/src/main/java/io/oryxos/core/profile/AgentLoader.java` 实现 `contracts/agent-profile.md` 全字段映射与逐 Agent 失败隔离；本节只派生 Bootstrap/Tool/MCP 名称，不检查文件或注册表存在性
- [X] T024 [US1] 运行 `AgentLoaderTest`、`AgentLoaderIsolationTest`、`AgentLoaderYamlSafetyTest`、`ProfileRegistryTest`，保存从红到绿证据

**Checkpoint**: US1 可独立验收，且未创建 `.oryxos/profiles/` 或第二份 Skill 绑定。

---

## Phase 4: User Story 2 — 配置一个供应商并完成首次模型调用（Priority: P1）

**Goal**: 通过 `ProviderService.chat` 完成一次精确路由的单轮调用并持久化成功审计。

**Independent Test**: 注入一个 `StubChatModel`，返回固定文本和 token；断言响应元数据正确，`llm_calls` 新增一行且重启后仍存在。

### Tests for User Story 2（先红后绿）

- [X] T025 [P] [US2] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/support/StubChatModel.java`：支持纯文本、工具意图、异常、空响应四种受控结果，禁止真实网络
- [X] T026 [P] [US2] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/DefaultProviderServiceChatTest.java` 与 `Lesson16QuickstartTest.java`：前者覆盖固定 `chat(sessionId, Profile, Prompt)` 签名、响应元信息、两个 Agent 并发模型/温度零串线，并用即时桩测量 Provider 内部 p95 开销 ≤50 ms；后者用临时 Provider 配置和 Agent 目录完成「配置 → 加载 → 单轮调用」无网络闭环，供 SC-001 计时
- [X] T027 [P] [US2] 新建 `oryxos-storage/src/test/java/io/oryxos/storage/LlmCallRepositoryTest.java`：临时 SQLite 经 Flyway 建表后写入/读回，断言 `profile_name` 与全部列映射
- [X] T028 [P] [US2] 新建 `oryxos-storage/src/test/java/io/oryxos/storage/LlmCallPersistenceAcrossRestartTest.java`：同一 SQLite 文件重建上下文后记录条数与字段保持不变
- [X] T029 [US2] 运行 US2 测试并保存红灯证据，确认失败来自未实现 Provider/审计行为

### Implementation for User Story 2

- [X] T030 [P] [US2] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderProperties.java` 与 `ProviderDefinition.java`：`oryxos.providers` 使用 List，字段仅为 plan/契约已有的 `name/type/model/baseUrl/apiKey`
- [X] T031 [P] [US2] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/CredentialResolver.java`：解析环境变量占位符并拒绝空值/未展开值，所有输出脱敏
- [X] T032 [P] [US2] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/dashscope/DashScopeChatModelFactory.java`：仅使用已核实 builder；通过 `SimpleClientHttpRequestFactory` 与 `RestClient.Builder` 固定 connect/read timeout=60 秒，并提供包内测试重载注入短 Duration
- [X] T033 [P] [US2] 新建 `oryxos-storage/src/main/java/io/oryxos/storage/entity/LlmCallEntity.java` 与 `repository/LlmCallRepository.java`：实体列与 V1 迁移逐字一致，SQLite 主键使用 `IDENTITY`
- [X] T034 [US2] 新建 `oryxos-storage/src/main/java/io/oryxos/storage/JpaLlmCallRecorder.java` 与 `config/StorageConfiguration.java`：写入成功返回；任何持久化异常向上传播，不吞异常
- [X] T035 [US2] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderRegistry.java`：维护不可变的已配置名称清单，并仅为凭据合法的定义构建不可变 `name → ChatModel` 显式映射；未引用且缺凭据的名称保留但不构造模型，不扫描同类型 Bean
- [X] T036 [US2] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/DefaultProviderService.java`：实现单轮调用、计时、响应映射和成功/空响应审计；请求参数只作用于本次调用
- [X] T037 [US2] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderAutoConfiguration.java`：显式装配 Registry、Service 与 Recorder，不开启 Spring AI 自动 Tool 执行
- [X] T038 [US2] 运行 US2 测试并保存绿灯证据；核对 SQLite 文件重启后记录可读

**Checkpoint**: US2 可在无密钥环境用桩独立验收；不得宣称真实 DashScope 已验证。

---

## Phase 5: User Story 3 — 多供应商按名称精确路由（Priority: P2）

**Goal**: 多个同类型模型并存时按 Provider 名称精确路由，重名配置拒绝。

**Independent Test**: `alpha`/`beta` 两个不同桩分别被对应 Agent 命中，审计 Provider 与实际调用一致，重名配置在构建映射前失败。

- [X] T039 [P] [US3] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderRoutingTest.java`：双 Provider 双 Agent 零串线，审计 `provider/model/profile_name` 一致
- [X] T040 [P] [US3] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/DuplicateProviderTest.java`：相同名称必须报 `DUPLICATE_PROVIDER`，不得静默覆盖
- [X] T041 [P] [US3] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/OpenAiCompatibleProtocolTest.java`：用 JDK `HttpServer` 绑定回环地址，断言请求路径、Authorization、model/messages JSON，并返回最小 Chat Completions 响应验证 `OpenAiChatModel.call` 闭环
- [X] T042 [US3] 运行 US3 测试并保存红灯证据
- [X] T043 [US3] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/openai/OpenAiCompatibleChatModelFactory.java`：仅使用已核实 builder；复用 connect/read timeout=60 秒的 request factory，不引入 starter
- [X] T044 [US3] 修改 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderRegistry.java`：按 `type` 显式分派两个工厂，重复名称在构造阶段失败
- [X] T045 [US3] 运行 US3 测试并保存绿灯证据

**Checkpoint**: 多 Provider 路由可独立验收，0 次按 Bean 类型或注册顺序猜测。

---

## Phase 6: User Story 4 — 配置与密钥错误可诊断、不泄露（Priority: P2）

**Goal**: 被引用 Provider 的凭据在启动期校验，坏 Agent 隔离，任何输出不泄露密钥。

**Independent Test**: 缺凭据、未知 Provider、非法 URL、占位符未展开和哨兵密钥场景均在外呼前失败；合法 Agent 不受未知 Provider Agent 影响。

- [X] T046 [P] [US4] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderStartupValidatorTest.java`：覆盖未知 Provider Profile 被移除且其他 Agent 保留、被引用凭据缺失导致启动失败、未引用凭据缺失只告警且不构造 ChatModel、随后直接调用该名称在零网络请求下返回 `MISSING_CREDENTIAL`，以及必填字段/非法 URL
- [X] T047 [P] [US4] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/CredentialRedactionTest.java`：用哨兵值扫描异常、日志、`toString` 和审计错误，命中次数必须为 0
- [X] T048 [US4] 运行 US4 测试并保存红灯证据
- [X] T049 [US4] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderStartupValidator.java`：逐个核对 Profile；未知 Provider 调用 `ProfileRegistry.remove(profile.name)` 并记录 Agent 级错误，已知且被引用 Provider 缺凭据时启动失败，未引用缺凭据只告警并标记为不可调用，不新增公共状态类型
- [X] T050 [US4] 修改 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderDefinition.java`、`CredentialResolver.java`、异常与日志：禁止凭据进入 `toString`、消息、堆栈附加上下文或审计字段
- [X] T051 [US4] 新建 `oryxos-boot/src/main/java/io/oryxos/boot/ProviderBootstrapConfiguration.java`：固定执行 `ProviderProperties 绑定 → AgentLoader → ProfileRegistry.register → ProviderStartupValidator.validateAndPrune → ProviderRegistry 构建`，保证未知 Provider Agent 在模型构造前移除、被引用凭据在外呼前失败、未引用且缺凭据的定义不构造 ChatModel 但保留名称状态；不得新建业务入口或 REST/CLI 概念
- [X] T052 [US4] 运行 US4 测试并保存绿灯证据

**Checkpoint**: 配置错误可定位且不泄密；未被引用且缺凭据的 Provider 只告警，不阻断启动。

---

## Phase 7: User Story 5 — 调用失败与审计失败关闭（Priority: P3）

**Goal**: Provider 失败如实上报并审计；审计持久化失败时绝不返回成功结果。

**Independent Test**: 认证、限流、超时、网络、空响应、token 未知和 Recorder 失败均得到固定错误/状态；无 fallback、静默重试或审计吞异常。

- [X] T053 [P] [US5] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderFailureMappingTest.java`：覆盖 `AUTH_FAILED/RATE_LIMITED/NETWORK_ERROR`；另用 JDK 回环服务和包内短 Duration 验证真实 read timeout 映射为 `TIMEOUT`，所有模型失败仍写 `FAILED` 审计
- [X] T054 [P] [US5] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/EmptyResponseAndUnknownUsageTest.java`：空白内容记 `EMPTY_RESPONSE`，未知 usage 三个 token 均为 null 且 `usage_known=0`
- [X] T055 [P] [US5] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/AuditPersistenceFailureTest.java`：模型成功与失败两条路径的 Recorder 异常都返回 `AUDIT_PERSISTENCE_FAILED`，原始异常保留且脱敏
- [X] T056 [US5] 运行 US5 测试并保存红灯证据
- [X] T057 [US5] 修改 `oryxos-provider/src/main/java/io/oryxos/provider/DefaultProviderService.java`：实现错误映射、无 fallback/重试、失败审计与审计失败关闭；不得新增 `oryxos.provider-timeout-ms` 等 plan 未列配置键
- [X] T058 [US5] 修改 `oryxos-storage/src/main/java/io/oryxos/storage/JpaLlmCallRecorder.java`：保留持久化 cause 并向上传播，日志不输出 Prompt/凭据
- [X] T059 [US5] 运行 US5 测试并保存绿灯证据

**Checkpoint**: 任一外呼成功/失败都有持久化记录；记录失败时调用失败关闭。

---

## Phase 8: User Story 6 — Tool Schema 保真且 Provider 不执行工具（Priority: P3）

**Goal**: OryxTool Schema 可供模型使用，但 Spring AI 与 Provider 均无真实工具执行通道。

**Independent Test**: 名称、说明、必填项、枚举和嵌套结构 100% 保持；模型返回 Tool Call 时 `OryxTool.execute` 为 0 次。

- [X] T060 [P] [US6] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/ToolSchemaAdapterTest.java`：使用已核实的 Spring AI `JsonParser` 验证名称、说明、类型、required、enum、嵌套对象原样保持以及非法 JSON/非 object schema 拒绝
- [X] T061 [P] [US6] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/AutomaticToolExecutionDisabledTest.java`：callback 的 `call` 固定失败，请求 options 为 `internalToolExecutionEnabled=false`，`OryxTool.execute` 为 0 次
- [X] T062 [US6] 运行 US6 测试并保存红灯证据
- [X] T063 [US6] 新建 `oryxos-provider/src/main/java/io/oryxos/provider/ToolSchemaAdapter.java`：用 Spring AI `JsonParser` 校验 object schema，构造 `DefaultToolDefinition` 与 schema-only `ToolCallback`，两个 `call` 入口都不得委托真实工具
- [X] T064 [US6] 修改 `oryxos-provider/src/main/java/io/oryxos/provider/DefaultProviderService.java`：使用已核实的 `ChatOptions.copy()` 与 `Prompt(List<Message>, ChatOptions)` 保留已有 schema-only callbacks，覆盖本次模型/温度并显式设置 `internalToolExecutionEnabled=false`；不得从 `Profile.tools` 查找实例或提前实现 `ToolRegistry`
- [X] T065 [US6] 运行 US6 测试并保存绿灯证据

**Checkpoint**: Tool Schema 可见但不可执行，后续只能由 `ToolExecutor` 接管 Tool Call 意图。

---

## Phase 9: Polish、集成验证与节级验收

- [X] T066 [P] 新建 `oryxos-boot/src/test/java/io/oryxos/boot/ProviderSmokeIT.java`：经 boot 真实装配 Provider、storage 与 Flyway，标记 `@Tag("integration")`；只有 `-Doryxos.provider.smoke=true` 和 `ALIYUN_API_KEY` 同时存在时才允许联网并核对 `llm_calls`，默认 `test`/`verify` 不发起外部请求，缺条件时报告未验证且不得使用 `@Disabled`
- [X] T067 [P] 更新 `specs/002-react-loop-cli/spec.md` 中与 001 冲突的范围描述：002 复用完整 Profile/AgentLoader/Tool Schema，不重复首次定义；不得改变 002 其他需求
- [X] T068 新建 `specs/001-provider-model-access/lesson-16-acceptance.md` 并运行 `./mvnw test` 硬门禁：记录命令、退出码、测试数与失败数；失败不得放行
- [X] T069 运行 `./mvnw clean verify` 硬门禁并把退出码、各模块结果、Checkstyle/SpotBugs/JaCoCo 结果写入 `specs/001-provider-model-access/lesson-16-acceptance.md`；失败不得放行
- [X] T070 逐项执行 `specs/001-provider-model-access/quickstart.md` 的无密钥路径和存在性核对；按 A3 定义从输入计时命令到 `Lesson16QuickstartTest` 成功退出记录实测秒数并断言 ≤300 秒，把结果写入 `specs/001-provider-model-access/lesson-16-acceptance.md`；真实 Provider 仅在用户提供密钥与网络时执行
- [X] T071 检查反作弊与范围：搜索 `@Disabled`、删除/弱化断言、阈值变化、plan 外依赖/配置键/公共类型，把结论写入 `specs/001-provider-model-access/lesson-16-acceptance.md`；任何命中先报告，不自行放宽
- [X] T072 完成 `specs/001-provider-model-access/lesson-16-acceptance.md`：汇总实际行为、真实调用是否执行、未验证项、软硬门禁结论和与第 16 节交付物映射

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2：依赖声明先于共享契约
- Phase 2 → US1/US2：核心契约、迁移和配置是阻塞项
- US1 → US2/US4：Provider 调用和启动校验需要完整 Profile/Registry
- US2 → US3/US5/US6：多路由、失败策略和 Tool options 复用单 Provider 调用骨架
- US3、US4、US5、US6 在各自前置完成后可并行
- Phase 9 依赖全部目标故事完成

### User Story Dependencies

| Story | Depends on | Independent completion signal |
| --- | --- | --- |
| US1 | Phase 2 | 完整 Profile 加载、坏 Agent 隔离、Registry 查询通过 |
| US2 | US1 + Phase 2 | 单 Provider 响应与成功审计可跨重启读取 |
| US3 | US2 | 双 Provider 零串线、重名拒绝 |
| US4 | US1 + US2 | 配置错误在外呼前发现、哨兵密钥 0 泄露 |
| US5 | US2 | 失败映射与审计失败关闭全部通过 |
| US6 | US2 | Schema 100% 保真、工具执行 0 次 |

### Parallel Opportunities

- T002/T003/T004 可并行；T006/T007/T008/T009/T011 可并行
- 每个故事中标记 `[P]` 的测试文件可并行编写，但必须在实现前共同跑出红灯
- US3、US4、US5、US6 在 US1/US2 完成后可由不同执行者并行，但合并前必须重跑全量硬门禁

---

## Parallel Example: User Story 1

```text
Task: "T014 AgentLoader 完整字段测试"
Task: "T015 坏 Agent 隔离测试"
Task: "T016 YAML 安全边界测试"
Task: "T017 ProfileRegistry 测试"
```

## Parallel Example: Provider 后续故事

```text
Task: "T039–T045 多 Provider 路由"
Task: "T046–T052 配置与脱敏"
Task: "T053–T059 失败与审计关闭"
Task: "T060–T065 Tool Schema 与零执行"
```

---

## Implementation Strategy

1. 先完成 Setup + Foundational。
2. 以 US1 为第一可验收增量，锁定 Agent/Profile 契约。
3. 完成 US2，形成最小 Provider 调用与持久化审计闭环。
4. 按 US3/US4/US5/US6 扩展路由、安全、失败与 Tool Schema，每个故事坚持红—绿—重构。
5. 全故事完成后执行硬门禁与节级验收报告。

## Notes

- 不创建本节清单之外的公共概念；发现必要新增时触发软门禁。
- 不修改固定字面量：`ProviderService.chat(String, Profile, Prompt)`、Profile 字段、`llm_calls` 表列、`oryxos.providers` 配置形状。
- 不自动 commit/push，不运行 `package.sh`。
- 真实密钥、真实外部调用和网络验证只在用户明确提供条件时进行；桩测试不得冒充真实端到端通过。
