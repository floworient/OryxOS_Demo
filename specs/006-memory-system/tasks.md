---
description: "Memory 核心完整交付的 TDD 实施任务"
---

# Tasks: Memory 核心完整交付

**Input**: `/specs/006-memory-system/` 下的 spec、plan、research、data-model、contracts、quickstart  
**Tests**: 所有行为改动必须 RED → GREEN；每个失败/通过命令保存到对应任务记录或交付总结。  
**Organization**: 先完成共享契约，再按 US1 → US2 → US3 → US4 交付；同一文件不并行编辑。

## Format

- `[P]`：依赖满足后可与其他不同文件任务并行。
- `[USn]`：映射到 `spec.md` User Story。
- 每个实现项前都有明确失败测试，禁止先写产品代码。

## Phase 1: Baseline 与依赖确认

- [x] T001 运行 `.\mvnw.cmd test`，记录当前基线；任何既有失败先报告，不把它归因于 Memory。
- [x] T002 检查并仅在必要时修改 `oryxos-memory/pom.xml` 的 Spring AI/Jackson/test 依赖，以及 `oryxos-boot/pom.xml`、`oryxos-storage/pom.xml`、`oryxos-web/pom.xml` 的现有模块依赖；运行 `mvnw.cmd -pl oryxos-memory -am test -DskipTests` 验证依赖图。

---

## Phase 2: Foundational Core Contracts

**Purpose**: 建立所有后端、工具、Prompt 与 API 共同依赖的类型和安全 owner 规则。

- [x] T003 [P] 在 `oryxos-core/src/test/java/io/oryxos/core/profile/AgentNameTest.java` 编写 Agent 名合法/非法/Windows 设备名参数化测试，并声明生产变化“共享校验器变宽或失效时测试失败”。
- [x] T004 运行 `.\mvnw.cmd -pl oryxos-core -am -Dtest=AgentNameTest test`，确认因 `AgentName` 不存在而 RED。
- [x] T005 在 `oryxos-core/src/main/java/io/oryxos/core/profile/AgentName.java` 实现共享校验，并修改 `AgentLoader.java`、`oryxos-cli/src/main/java/io/oryxos/cli/WorkspaceService.java` 复用它，删除 CLI 私有重复规则。
- [x] T006 运行 `AgentNameTest`、`AgentLoaderTest`、`AgentLoaderIsolationTest`、`WorkspaceServiceTest`，确认 GREEN。
- [x] T007 [P] 在 `oryxos-core/src/test/java/io/oryxos/core/memory/MemoryContractsTest.java` 编写 owner/global 保留值、scope、不可变集合、空值/非法内容和异常码测试。
- [x] T008 运行 `.\mvnw.cmd -pl oryxos-core -am -Dtest=MemoryContractsTest test`，确认因 memory 契约不存在而 RED。
- [x] T009 在 `oryxos-core/src/main/java/io/oryxos/core/memory/` 创建 `MemoryScope`、`MemoryCapabilities`、`MemoryOwner`、`MemoryEntry`、`MemorySnapshot`、`MemoryContext`、`MemoryException`、`MemoryService`，签名严格遵循 `contracts/memory-service.md`。
- [x] T010 运行 `MemoryContractsTest` 与 `oryxos-core` 全模块测试，确认 GREEN。

**Checkpoint**: core 提供稳定契约，且不依赖 memory/storage/web。

---

## Phase 3: User Story 1 - 默认后端跨会话记忆 (Priority: P1) 🎯 MVP

**Goal**: Markdown 后端、统一门面和 Prompt 形成按 Agent 隔离、下一轮可见、重启恢复的闭环。

**Independent Test**: 临时工作区保存 core/archival，重建 store/service 后为 Agent A 构建 Prompt，验证完整 core、有界 archival、Agent B 隔离和空块。

### Store contract 与 Markdown

- [x] T011 [US1] 在 `oryxos-core/src/test/java/io/oryxos/core/memory/LongTermMemoryStoreContract.java` 定义共享断言：owner/scope 隔离、core 不截断/不召回、archival 最新完整条目、大小写召回、最新 N 后升序、每次读取、失败不为空。
- [x] T012 [US1] 在 `MarkdownMemoryStoreTest.java` 和 `MarkdownMemoryStoreConcurrencyTest.java` 接入契约，并添加旧空骨架升级、畸形分区、换行/header 注入、链接/边界、原子回退和并发追加测试。
- [x] T013 [US1] 运行 `.\mvnw.cmd -pl oryxos-memory -am -Dtest=MarkdownMemoryStoreTest,MarkdownMemoryStoreConcurrencyTest test`，确认因实现缺失而 RED。
- [x] T014 [US1] 在 `oryxos-memory/src/main/java/io/oryxos/memory/MarkdownMemoryStore.java` 实现解析、渲染、裁剪、关键词匹配、real-path 边界、细粒度锁与原子替换；必要内部类保持包私有且单一职责。
- [x] T015 [US1] 重跑两个 Markdown 测试和 `oryxos-memory` 全模块测试，确认 GREEN。

### MemoryService

- [x] T016 [US1] 在 `oryxos-memory/src/test/java/io/oryxos/memory/DefaultMemoryServiceTest.java` 编写 Session 快照、owner 路由、KEYWORD/DELEGATED、HYBRID fail-fast、save/recall/loadLongTerm 和后端异常传播测试。
- [x] T017 [US1] 运行 `.\mvnw.cmd -pl oryxos-memory -am -Dtest=DefaultMemoryServiceTest test`，确认 RED。
- [x] T018 [US1] 在 `DefaultMemoryService.java` 与 `MemoryOwnerResolver.java` 实现门面；禁止缓存并保持结构化 Message。
- [x] T019 [US1] 重跑 `DefaultMemoryServiceTest` 与 memory 模块测试，确认 GREEN。

### Prompt 集成

- [x] T020 [US1] 修改 `oryxos-core/src/test/java/io/oryxos/core/context/PromptBuilderTest.java`：以 MemoryService fake 断言空记忆块始终存在、core/archival 边界标签、历史顺序/轮数不变、自动工具执行仍关闭。
- [x] T021 [US1] 运行 `.\mvnw.cmd -pl oryxos-core -am -Dtest=PromptBuilderTest test`，确认构造器/行为 RED。
- [x] T022 [US1] 修改 `oryxos-core/src/main/java/io/oryxos/core/context/PromptBuilder.java`，以 `MemoryService` 替换 `BiFunction`，渲染不可覆盖系统指令的长期记忆 SystemMessage，再追加裁剪后的结构化历史。
- [x] T023 [US1] 重跑 `PromptBuilderTest`、`ReActLoopTest`、`AgentServiceTest` 和 core 全模块测试，确认 GREEN。

**Checkpoint**: US1 可在默认 Markdown 后端独立演示。

---

## Phase 4: User Story 2 - Memory Tools 与审计 (Priority: P1)

**Goal**: `save_memory`/`recall_memory` 通过唯一 Tool 链执行、授权和审计，正文不泄漏。

**Independent Test**: 真实 ToolRegistry/ToolExecutor 下执行授权、未授权、非法参数、默认 scope、召回和审计持久化失败场景。

- [x] T024 [P] [US2] 在 `oryxos-memory/src/test/java/io/oryxos/memory/MemoryToolsTest.java` 编写严格 JSON、默认 archival、显式 core、无 Profile、未知 scope、超限、召回格式与不回显保存内容测试。
- [x] T025 [US2] 运行 `.\mvnw.cmd -pl oryxos-memory -am -Dtest=MemoryToolsTest test`，确认 RED。
- [x] T026 [US2] 在 `oryxos-memory/src/main/java/io/oryxos/memory/MemoryTools.java` 使用 Spring AI `@Tool` 实现两个方法，委托 MemoryService，不直接访问 store。
- [x] T027 [US2] 重跑 `MemoryToolsTest` 与 `AnnotatedToolAdapterTest`，确认 schema 与行为 GREEN。
- [x] T028 [US2] 在 `oryxos-core/src/test/java/io/oryxos/core/tool/ToolExecutorTest.java` 添加 Memory content canary 脱敏、单次最终审计、未授权零副作用测试。
- [x] T029 [US2] 运行 `.\mvnw.cmd -pl oryxos-core -am -Dtest=ToolExecutorTest test`，确认 canary 当前进入审计而 RED。
- [x] T030 [US2] 修改 `oryxos-core/src/main/java/io/oryxos/core/tool/ToolExecutor.java`，对 `save_memory` 生成 scope/bytes/SHA-256 摘要且不保留 content，不改变其他工具既有语义。
- [x] T031 [US2] 重跑 `ToolExecutorTest` 与 core 模块测试，确认 GREEN。
- [x] T032 [US2] 在 `oryxos-boot/src/test/java/io/oryxos/boot/MemoryToolAuditIT.java` 编写默认 Markdown 下 Registry 可见性、真实 ToolExecutor 调用、一次审计和下一轮可见的集成测试。
- [x] T033 [US2] 运行 `.\mvnw.cmd -pl oryxos-boot -am -Dtest=MemoryToolAuditIT test`，确认缺少装配而 RED。
- [x] T034 [US2] 创建 `oryxos-boot/src/main/java/io/oryxos/boot/MemoryProperties.java`、`MemoryConfiguration.java`，先完成默认 Markdown、MemoryService、MemoryTools bean 与 PromptBuilder 注入。
- [x] T035 [US2] 重跑 `MemoryToolAuditIT`、`ReActConfigurationTest`、`ToolConfigurationTest`，确认 GREEN。

**Checkpoint**: US2 经统一 Tool 链独立验收。

---

## Phase 5: User Story 3 - SQLite 与 Mem0 可插拔后端 (Priority: P2)

**Goal**: 三档后端通过单一配置切换并通过相同契约，失败不静默降级。

**Independent Test**: SQLite 临时库跨重启；Mem0 回环 fake 覆盖协议和故障；boot 参数化启动三后端。

### SQLite

- [x] T036 [P] [US3] 在 `oryxos-storage/src/test/java/io/oryxos/storage/JpaMemoryStoreTest.java` 与 `MemoryPersistenceAcrossRestartTest.java` 接入 StoreContract，覆盖 Flyway、scope CHECK、同时间 id 排序、事务失败和跨重启。
- [x] T037 [US3] 运行 `.\mvnw.cmd -pl oryxos-storage -am -Dtest=JpaMemoryStoreTest,MemoryPersistenceAcrossRestartTest test`，确认缺表/实现而 RED。
- [x] T038 [US3] 新增 `V5__create_memory_entries.sql`、`MemoryEntryEntity.java`、`MemoryEntryRepository.java`、`JpaMemoryStore.java`，并更新 `StorageConfiguration` 扫描范围。
- [x] T039 [US3] 重跑两个 storage 测试及 storage 全模块测试，确认 GREEN 且 `ddl-auto=validate`。

### Mem0 OSS

- [x] T040 [P] [US3] 在 `oryxos-memory/src/test/java/io/oryxos/memory/mem0/` 创建 `Mem0ProtocolValidatorTest.java`、`Mem0MemoryStoreTest.java`，用回环 HttpServer 覆盖 OpenAPI 只读校验、无 `/v1`、agent_id/scope、infer=false、core 不 search、401/5xx/重定向/非法或超大 JSON/缺 metadata/超时、写零重试和秘密脱敏。
- [x] T041 [US3] 运行 `.\mvnw.cmd -pl oryxos-memory -am -Dtest=Mem0ProtocolValidatorTest,Mem0MemoryStoreTest test`，确认 RED。
- [x] T042 [US3] 在 `oryxos-memory/src/main/java/io/oryxos/memory/mem0/` 实现 `Mem0Client`、`Mem0ProtocolValidator`、`Mem0MemoryStore`，只用 JDK HttpClient，DTO 保持私有。
- [x] T043 [US3] 重跑 Mem0 测试和 memory 模块测试，确认 GREEN。

### 后端选择

- [x] T044 [US3] 在 `oryxos-boot/src/test/java/io/oryxos/boot/MemoryConfigurationTest.java` 编写默认/markdown/sqlite/mem0 选择、非法 backend、非正上限、缺 Mem0 配置、HYBRID fail-fast 和非 Mem0 不触网测试。
- [x] T045 [US3] 运行 `.\mvnw.cmd -pl oryxos-boot -am -Dtest=MemoryConfigurationTest test`，确认 SQLite/Mem0 选择 RED。
- [x] T046 [US3] 扩展 `MemoryProperties.java`、`MemoryConfiguration.java` 与 `application.yaml`，用条件 bean 选择 `MarkdownMemoryStore`、`JpaMemoryStore`、`Mem0MemoryStore`，后端切换不改上层 bean。
- [x] T047 [US3] 重跑 `MemoryConfigurationTest`、三后端 StoreContract 和 boot 上下文测试，确认 GREEN。

**Checkpoint**: US3 三档后端均可独立验收，真实 Mem0 仍标为未验证项。

---

## Phase 6: User Story 4 - 只读 Memory API (Priority: P2)

**Goal**: 管理端以统一信封查看已注册 Agent 的实际有界 Memory 视图。

**Independent Test**: MockMvc 覆盖 200/404/503、空视图、截断和查询零副作用。

- [x] T048 [P] [US4] 在 `oryxos-web/src/test/java/io/oryxos/web/memory/MemoryApiControllerTest.java` 编写正常、空数据、未知 Agent、MemoryException、字段映射和秘密不泄漏测试。
- [x] T049 [US4] 运行 `.\mvnw.cmd -pl oryxos-web -am -Dtest=MemoryApiControllerTest test`，确认 Controller 缺失而 RED。
- [x] T050 [US4] 创建 `MemoryApiController.java`、`MemoryView.java`，并扩展 `GlobalExceptionHandler.java` 映射 `AGENT_NOT_FOUND` 404 与 `MEMORY_UNAVAILABLE` 503。
- [x] T051 [US4] 重跑 `MemoryApiControllerTest`、`ApiResponseTest` 与 web 模块测试，确认 GREEN。
- [x] T052 [US4] 在 `oryxos-boot/src/test/java/io/oryxos/boot/MemoryApiIT.java` 编写真实 Spring 上下文下 Markdown 200、未知 Agent 404、查询无副作用测试。
- [x] T053 [US4] 运行 `.\mvnw.cmd -pl oryxos-boot -am -Dtest=MemoryApiIT test`，确认不完整 Agent fixture 先 RED；补齐已注册 Agent 目录后 GREEN，现有扫描配置无需修改。

**Checkpoint**: 四个 User Story 全部独立可验收。

---

## Phase 7: Documentation、Regression 与交付真实性

- [x] T054 [P] 更新 `website/zh/docs/memory.md`、`website/docs/memory.md`、`website/zh/docs/api.md`、`website/docs/api.md` 与实际三后端、无自动触发足迹、有界 API、015 非目标一致；修正 architecture 中冲突的自动记录描述。
- [x] T055 [P] 更新 `oryxos-cli/src/main/java/io/oryxos/cli/WorkspaceService.java` 与对应测试，使 `oryxos init` 创建的新 `MEMORY.md` 使用两个分区骨架且不覆盖旧文件。
- [ ] T056 运行 `.\mvnw.cmd -pl oryxos-core,oryxos-memory,oryxos-storage,oryxos-web,oryxos-boot -am test`，列出并修复所有相关回归。
- [ ] T057 运行 `.\mvnw.cmd test`，确认完整单元/集成测试套件通过；任何无关既有失败按名称报告。
- [ ] T058 运行 `.\mvnw.cmd clean verify`，确认 Checkstyle、SpotBugs、JaCoCo、Failsafe、ArchUnit 和打包全部通过。
- [ ] T059 按 `quickstart.md` 在临时工作区验证 Markdown 与 SQLite；没有真实 Mem0 服务/密钥时明确只验证 fake server，不冒充真实互操作。
- [ ] T060 更新 `specs/006-memory-system/verification.md`，记录 RED/GREEN 证据、最终命令、实际完成行为、未验证范围和运行方式。

---

## Dependencies & Execution Order

- Phase 1 → Phase 2 为所有故事前置。
- US1 提供 MemoryService/Prompt 基线，US2 依赖它。
- US3 的 SQLite 与 Mem0 子任务在 StoreContract 完成后可彼此并行，但后端选择任务依赖两者。
- US4 依赖 MemoryService 和 boot 装配，不依赖真实 Mem0。
- Phase 7 依赖所有目标故事完成。

## Parallel Opportunities

- T003 与 T007 可并行，文件不重叠。
- T024 与 T028 的测试编写可并行，但实现依次合并。
- T036（storage）与 T040（memory/mem0）可并行。
- T048（web）可在 T044 后与 Mem0 后端实现并行。
- T054 与 T055 可并行。

## Implementation Strategy

1. 先交付 US1 Markdown MVP 并验证跨会话。
2. 接入 US2 统一 Tool/审计，不允许特殊执行路径。
3. 复用 StoreContract 补齐 SQLite 与 Mem0。
4. 最后接 API、文档和全仓门禁。
5. 每个 checkpoint 都运行相关模块套件，不把所有风险堆到最终验证。

