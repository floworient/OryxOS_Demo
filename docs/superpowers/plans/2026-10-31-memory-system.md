# Memory Core Delivery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付按 Agent 隔离、core/archival 分区、Markdown/SQLite/Mem0 三后端的 Memory 核心能力，并通过统一门面接入 Prompt、Tool 审计链和只读 API。

**Architecture:** 共享类型与接口位于 `oryxos-core`，默认 Markdown/Mem0/门面/工具位于 `oryxos-memory`，SQLite 基础设施位于 `oryxos-storage`，条件装配位于 `oryxos-boot`，只读适配位于 `oryxos-web`。所有写入经 `MemoryService`，Agent 写入只通过 `ToolExecutor`；015 只预留能力声明与 archival 本体视图。

**Tech Stack:** Java 21、Spring Boot 3.5.8、Spring AI 1.1.2、Spring Data JPA、Flyway、SQLite、JDK HttpClient、JUnit 5、MockMvc

**Spec:** `specs/006-memory-system/spec.md`；详细架构与契约见同目录 `plan.md`、`research.md`、`data-model.md`、`contracts/`；权威任务追踪见 `tasks.md`。

## Global Constraints

- JDK 21；Maven Wrapper；现有 Spring Boot/Spring AI 版本不升级。
- core 不依赖 memory/storage/web 或具体后端。
- Spring AI 自动 Tool 执行保持关闭；Memory Tool 只经 ToolExecutor。
- core 永不截断、不检索；archival 上下文默认上限 4000 字符。
- Mem0 仅适配 OSS 无 `/v1` REST，写请求零重试，自动测试只用回环 fake。
- SQLite schema 只通过 Flyway V5，JPA 保持 `ddl-auto=validate`。
- 不实现 015 embedding、RRF、异步索引、对账或自动抽取。

## Review Focus

- 非法 Agent 名、Windows 设备名、原生链接或父目录替换必须在任何文件写入前拒绝（Task 1/2 测试）。
- 单条 archival 超过 4000 字符时不得返回半条或截断 core（Task 2 测试）。
- 同一时间戳多条 SQLite 记录必须用 id 稳定排序，跨重启一致（Task 5 测试）。
- Mem0 响应缺 owner/scope metadata、重定向或超限时必须 fail-loud 且写请求不重试（Task 5 测试）。
- `save_memory.content` 不得出现在审计、日志、API 或错误中（Task 4/6 测试）。

---

### Task 1: 安全 owner 与 core Memory 契约

**Files:**
- Create: `oryxos-core/src/main/java/io/oryxos/core/profile/AgentName.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/memory/*.java`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/profile/AgentLoader.java`
- Modify: `oryxos-cli/src/main/java/io/oryxos/cli/WorkspaceService.java`
- Test: `oryxos-core/src/test/java/io/oryxos/core/profile/AgentNameTest.java`
- Test: `oryxos-core/src/test/java/io/oryxos/core/memory/MemoryContractsTest.java`

**Interfaces:**
- Produces: `MemoryService`、`LongTermMemoryStore` 及 `contracts/memory-service.md` 中的确切 record/enum 签名。
- Consumes: 现有 `Profile`、`Session`、Spring AI `Message`。

- [ ] 写 AgentName 与 MemoryContracts 的失败测试（Spec-Kit T003/T007）。
- [ ] 分别运行定向测试，确认因类型不存在而 RED（T004/T008）。
- [ ] 实现最小类型与校验，并让 CLI/AgentLoader 复用（T005/T009）。
- [ ] 运行 core/CLI 相关回归，确认 GREEN（T006/T010）。

### Task 2: Markdown Store

**Files:**
- Create: `oryxos-memory/src/main/java/io/oryxos/memory/MarkdownMemoryStore.java`
- Create: `oryxos-memory/src/test/java/io/oryxos/memory/LongTermMemoryStoreContract.java`
- Test: `MarkdownMemoryStoreTest.java`、`MarkdownMemoryStoreConcurrencyTest.java`

**Interfaces:**
- Consumes: `LongTermMemoryStore`、`MemoryOwner`、`MemoryScope`。
- Produces: backend name `markdown`、capability `KEYWORD`；`load` 有界、`archivalEntries` 全量。

- [ ] 写共享契约与 Markdown 特有路径/格式/并发测试（T011-T012）。
- [ ] 运行测试并确认 RED（T013）。
- [ ] 实现解析、完整条目裁剪、大小写召回、real-path、锁和原子替换（T014）。
- [ ] 运行 memory 测试确认 GREEN（T015）。

### Task 3: MemoryService 与 Prompt

**Files:**
- Create: `oryxos-memory/src/main/java/io/oryxos/memory/DefaultMemoryService.java`
- Create: `oryxos-memory/src/main/java/io/oryxos/memory/MemoryOwnerResolver.java`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/context/PromptBuilder.java`
- Test: `DefaultMemoryServiceTest.java`、`PromptBuilderTest.java`

**Interfaces:**
- Consumes: Task 1/2 类型与 store。
- Produces: `load/save/recall/loadLongTerm`；Prompt 中固定长期记忆 SystemMessage。

- [ ] 写 Service 路由/失败测试和 Prompt 空块/顺序/历史回归测试（T016/T020）。
- [ ] 运行定向测试确认 RED（T017/T021）。
- [ ] 实现门面并替换 PromptBuilder 的 `BiFunction` seam（T018/T022）。
- [ ] 运行 memory/core/ReAct 回归确认 GREEN（T019/T023）。

### Task 4: Memory Tools、审计与默认装配

**Files:**
- Create: `oryxos-memory/src/main/java/io/oryxos/memory/MemoryTools.java`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/tool/ToolExecutor.java`
- Create: `oryxos-boot/src/main/java/io/oryxos/boot/MemoryProperties.java`
- Create: `oryxos-boot/src/main/java/io/oryxos/boot/MemoryConfiguration.java`
- Test: `MemoryToolsTest.java`、`ToolExecutorTest.java`、`MemoryToolAuditIT.java`

**Interfaces:**
- Produces: `save_memory(content, scope=archival)`、`recall_memory(keyword)`；Memory content 审计摘要。
- Consumes: Task 3 MemoryService、现有 AnnotatedToolDiscovery/Registry/Executor。

- [ ] 写工具严格输入、授权、审计脱敏与下一轮可见测试（T024/T028/T032）。
- [ ] 逐组运行确认 RED（T025/T029/T033）。
- [ ] 实现工具、精确审计摘要和默认 Markdown bean（T026/T030/T034）。
- [ ] 运行 memory/core/boot 回归确认 GREEN（T027/T031/T035）。

### Task 5: SQLite、Mem0 与三后端选择

**Files:**
- Create: `oryxos-storage/src/main/resources/db/migration/sqlite/V5__create_memory_entries.sql`
- Create: `MemoryEntryEntity.java`、`MemoryEntryRepository.java`、`JpaMemoryStore.java`
- Create: `oryxos-memory/src/main/java/io/oryxos/memory/mem0/{Mem0Client,Mem0ProtocolValidator,Mem0MemoryStore}.java`
- Modify: `MemoryConfiguration.java`、`MemoryProperties.java`、`application.yaml`
- Test: storage、Mem0、boot configuration 测试（Spec-Kit T036/T040/T044）。

**Interfaces:**
- SQLite produces `KEYWORD`；Mem0 produces `DELEGATED`。
- Mem0 exact endpoints/header/limits follow `contracts/memory-backends.md`。

- [ ] 写 SQLite StoreContract/重启测试与 Mem0 fake 协议/故障测试（T036/T040）。
- [ ] 运行定向测试确认 RED（T037/T041）。
- [ ] 实现 Flyway/JPA store 与 JDK HttpClient Mem0 adapter（T038/T042）。
- [ ] 运行各后端测试确认 GREEN（T039/T043）。
- [ ] 写三后端条件装配测试，确认 SQLite/Mem0 分支 RED（T044-T045）。
- [ ] 扩展配置选择并运行 boot/三后端回归确认 GREEN（T046-T047）。

### Task 6: Memory API

**Files:**
- Create: `oryxos-web/src/main/java/io/oryxos/web/memory/MemoryApiController.java`
- Create: `oryxos-web/src/main/java/io/oryxos/web/memory/MemoryView.java`
- Modify: `oryxos-web/src/main/java/io/oryxos/web/api/GlobalExceptionHandler.java`
- Test: `MemoryApiControllerTest.java`、`MemoryApiIT.java`

**Interfaces:**
- Produces: `GET /api/v1/agents/{name}/memory`，200/404/503 如 `contracts/memory-api.md`。
- Consumes: ProfileRegistry、MemoryService。

- [ ] 写 MockMvc 与 boot 集成失败测试（T048/T052）。
- [ ] 运行定向测试确认 RED（T049；T053 首次运行）。
- [ ] 实现 Controller/View/异常映射和必要扫描配置（T050/T053）。
- [ ] 运行 web/boot 测试确认 GREEN（T051/T053）。

### Task 7: 文档与完整验证

**Files:**
- Modify: 中英文 `website/*/memory.md`、`api.md`、冲突的 `architecture.md`
- Modify: `WorkspaceService.java` 及测试，初始化新分区骨架
- Create: `specs/006-memory-system/verification.md`

**Interfaces:**
- Produces: 与实现一致的运行说明和可复核 RED/GREEN 证据。

- [ ] 更新文档和 init 骨架测试/实现（T054-T055）。
- [ ] 运行相关模块测试（T056）。
- [ ] 运行全仓 `mvnw.cmd test`（T057）。
- [ ] 运行 `mvnw.cmd clean verify`（T058）。
- [ ] 执行 quickstart 临时工作区验证并记录真实 Mem0 未验证范围（T059-T060）。

