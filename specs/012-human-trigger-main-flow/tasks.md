# Tasks: 人推 Agent 主流程串联

**Input**: `specs/012-human-trigger-main-flow/` 下的 spec、plan、research、data-model、contracts 与 quickstart

**Tests**: 本功能以全链路可验证为目标，所有行为任务均按 RED -> GREEN -> REFACTOR 执行；每个测试任务先运行聚焦命令并记录因缺少目标行为产生的预期失败。

**Organization**: 任务按四个用户故事组织；同一文件的修改保持串行，标记 `[P]` 的任务只并行处理不同文件。

## Phase 1: Setup（保护现状与基线）

**Purpose**: 固定当前工作区和既有门禁，避免覆盖 011 控制台管理及 Sandbox 未提交改动。

- [x] T001 记录 `git status --short`、现有未提交文件及相关模块基线测试结果到 `specs/012-human-trigger-main-flow/verification.md`，不得回滚 `.gitignore`、`frontend/`、`oryxos-boot/.../ReActConfiguration.java`、`ToolConfiguration.java`、`oryxos-tool/.../WhitelistSandbox.java` 和 `oryxos-web/.../OpenApiContractTest.java`
- [x] T002 [P] 核对 Spring AI 1.1.2 的 `ChatModel`、`AssistantMessage.ToolCall`、`ChatResponseMetadata` 和 `DefaultUsage` 本地编译表面，并把无需新增依赖的结论补入 `specs/012-human-trigger-main-flow/verification.md`

---

## Phase 2: Foundational（共享契约门禁）

**Purpose**: 钉住所有故事共同依赖的“不旁路、无外部密钥、兼容现有契约”约束。

- [x] T003 [P] 运行 `oryxos-provider/src/test/java/io/oryxos/provider/AutomaticToolExecutionDisabledTest.java` 并在 `specs/012-human-trigger-main-flow/verification.md` 记录现有真实 Provider 请求保持 `internalToolExecutionEnabled=false` 的 GREEN 基线（FR-002、FR-013）
- [x] T004 [P] 运行 `oryxos-core/src/test/java/io/oryxos/core/agent/AgentServiceTest.java` 与 `oryxos-core/src/test/java/io/oryxos/core/agent/ReActLoopTest.java`，在 `specs/012-human-trigger-main-flow/verification.md` 记录统一入口、消息累积和单次工具执行现状；若既有断言缺失，只补不会依赖 Mock 的基线回归并保持 GREEN（FR-012 至 FR-016）

**Checkpoint**: 共享链路不变量明确，后续只能扩展边缘能力。

---

## Phase 3: User Story 1 - 无密钥验证完整 Agent 主流程 (Priority: P1) 🎯 MVP

**Goal**: 配置显式 Mock Provider 后，不使用真实 key/公网即可真实运行两轮 ReAct + 一次 `save_memory`，并对会话、记忆和审计对账。

**Independent Test**: 临时工作区中发送“记住：我喜欢喝美式咖啡”，断言 4 条消息、LLM 审计 2 条、工具审计 1 条和 archival 记忆 1 条。

### Tests for User Story 1（先 RED）

- [x] T005 [P] [US1] 新建 `oryxos-provider/src/test/java/io/oryxos/provider/mock/MockChatModelTest.java`，并扩展 `oryxos-provider/src/test/java/io/oryxos/provider/AutomaticToolExecutionDisabledTest.java`，覆盖第一轮唯一 `save_memory`、第二轮最终答复、非零 token、工具不可见/输入不匹配/结果不匹配、多会话隔离及 Mock 请求仍关闭自动工具执行，运行聚焦测试确认因 Mock 尚不存在而 RED（FR-001 至 FR-004、FR-013）
- [x] T006 [P] [US1] 扩展 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderStartupValidatorTest.java`、`ProviderRoutingTest.java` 与 `CredentialRedactionTest.java`，先写 MOCK 无 key 可显式路由、真实 Provider 仍须凭据且绝不 fallback 的 RED（FR-004、FR-018）
- [x] T007 [US1] 新建 `oryxos-boot/src/test/java/io/oryxos/boot/MockProviderFlowTest.java`，手工装配正式 ReAct/Tool/Memory/Session/审计并写出 2/1/4/1 精确对账 RED；确认失败来自 Mock Provider 尚未实现（FR-002、FR-014 至 FR-017，SC-001）

### Implementation for User Story 1

- [x] T008 [US1] 在 `oryxos-provider/src/main/java/io/oryxos/provider/mock/MockChatModel.java` 实现无共享可变状态的确定性两轮脚本：只接受“记住：<非空事实>”，第一次输出唯一 `save_memory(content=<事实>, scope=archival)`，匹配 ToolResponse 后输出固定非空答复并提供非零 token（FR-001、FR-003）
- [x] T009 [US1] 在 `oryxos-provider/src/main/java/io/oryxos/provider/ProviderDefinition.java`、`ProviderRegistry.java`、`ProviderStartupValidator.java` 与 `ProviderAutoConfiguration.java` 增加显式 `MOCK` 映射和无凭据接线；真实类型的凭据、base URL、错误映射保持原样（FR-002、FR-004）
- [x] T010 [US1] 依次运行 `MockChatModelTest`、Provider 路由/校验测试和 `MockProviderFlowTest` 到 GREEN，必要重构仅限 `oryxos-provider` 与测试装配，并把 RED/GREEN 命令及结果写入 `specs/012-human-trigger-main-flow/verification.md`（SC-001）

**Checkpoint**: 无 HTTP 和 UI 也能独立证明正式内核的两轮 ReAct、记忆与审计链路。

---

## Phase 4: User Story 2 - 通过 REST 与管理台对账会话 (Priority: P1)

**Goal**: 最近 100 条会话可通过统一 API 和 `/console/` 发现并打开，摘要不读取完整历史且支持 `active` 过滤。

**Independent Test**: 预置超过 100 条混合状态/渠道会话，验证稳定排序、过滤、字段与脱敏；前端覆盖 loading/empty/error/retry/selection。

### Tests for User Story 2（先 RED）

- [x] T011 [P] [US2] 更新 `oryxos-storage/src/test/java/io/oryxos/storage/SessionSummaryQueryTest.java` 并新增 `SessionMigrationV7Test.java`，先写 `message_count >= 0`、既有合法 JSON 回填、损坏正文不影响摘要、100 条上限、`lastActiveAt DESC/id ASC` 和状态过滤 RED（FR-005 至 FR-009）
- [x] T012 [P] [US2] 在 `oryxos-web/src/test/java/io/oryxos/web/session/SessionWebServiceTest.java` 与 `SessionApiControllerTest.java` 先写 `GET /api/v1/sessions` 默认/active/空集/非法 status/存储失败/无敏感字段 RED（FR-005 至 FR-009）
- [x] T013 [P] [US2] 在 `frontend/tests/sessionHistory.test.ts` 和 `frontend/tests/useConsole.session.test.ts` 先写真实列表的 loading/empty/error/retry、摘要选择、归档或非 web 只读和对话后刷新 RED（FR-010、FR-011，SC-003）

### Implementation for User Story 2

- [x] T014 [US2] 在 `oryxos-core/src/main/java/io/oryxos/core/session/SessionSummary.java` 扩展不可变摘要为 `id/profileName/channel/userId/status/createdAt/lastActiveAt/messageCount`（`messageCount >= 0`），并在 `SessionManager.java` 保留 `list()` 兼容同时新增有界、可选 `SessionStatus` 的 `listRecent` 契约（FR-006 至 FR-009）
- [x] T015 [US2] 新增 `oryxos-storage/src/main/resources/db/migration/sqlite/V7__add_session_message_count.sql`，并修改 `SessionEntity.java`、`SessionRepository.java`、`JpaSessionManager.java`：保存时同步消息数，查询使用 Pageable 在存储层完成上限/状态/稳定排序且不解码 `messages_json`（FR-006 至 FR-009）
- [x] T016 [US2] 修改 `oryxos-boot/src/test/java/io/oryxos/boot/BootCliQueryTest.java` 等 `SessionSummary` 构造调用点，保证现有 `session list` 输出兼容且 storage 聚焦测试全部 GREEN（FR-009）
- [x] T017 [US2] 新增 `oryxos-web/src/main/java/io/oryxos/web/session/SessionSummaryView.java`，并修改 `SessionWebService.java`、`SessionApiController.java` 实现默认最近 100 条与唯一公开过滤 `status=active`；统一信封和稳定脱敏错误保持既有约定（FR-005 至 FR-009）
- [x] T018 [US2] 修改 `oryxos-web/src/test/java/io/oryxos/web/OpenApiContractTest.java`，在保留用户现有 Sandbox allowlist 断言的基础上登记并验证会话列表 operation、JSON schema 和统一响应；运行 web 聚焦测试到 GREEN（FR-005、FR-008）
- [x] T019 [US2] 在 `frontend/src/api/types.ts` 与 `frontend/src/api/oryxos.ts` 增加 `SessionSummary` 和 `fetchSessions`，新建 `frontend/src/components/SessionHistory.vue`，增量修改 `AgentSidebar.vue`、`App.vue` 与 `useConsole.ts` 以加载/重试/选择真实摘要，非 web 或归档详情只读且不覆盖 011 的七入口管理视图（FR-010、FR-011）
- [x] T020 [US2] 运行 `npm --prefix frontend test` 与 `npm --prefix frontend run build` 到 GREEN，确认构建仍输出 `oryxos-web/src/main/resources/static/console/`，并在 `specs/012-human-trigger-main-flow/verification.md` 记录结果（SC-002、SC-003）

**Checkpoint**: REST 和控制台能独立发现同一份 Session 数据；列表无消息正文且最多 100 条。

---

## Phase 5: User Story 3 - 从不同人推入口复用同一引擎 (Priority: P2)

**Goal**: CLI 与 REST 均通过同一 AgentService/ReAct/Tool/Session/审计语义运行，整机 HTTP 自动完成三根支柱对账。

**Independent Test**: 启动随机端口和临时 SQLite，HTTP 创建会话、发送 Mock 消息并查回列表/详情/记忆/工具/审计；CLI 对等场景验证单次工具执行。

### Tests for User Story 3（先 RED）

- [x] T021 [US3] 新建 `oryxos-boot/src/test/java/io/oryxos/boot/MockAgentE2ETest.java`，使用临时 `oryxos.root`、Agent、SQLite 和真实 HTTP 写出 Session 创建/消息/列表/详情/记忆/工具/2 条 LLM 审计/1 条 Tool 审计的整机 RED（FR-017、FR-019，SC-004、SC-006）
- [x] T022 [P] [US3] 扩展 `oryxos-boot/src/test/java/io/oryxos/boot/BootCliChatTest.java`，使用同一 Mock Provider 通过 CLI 路径断言 2 次模型、1 次工具和 4 条持久化历史，先确认接线缺口 RED（FR-012、FR-013）

### Implementation for User Story 3

- [x] T023 [US3] 仅在 `oryxos-boot/src/main/java/io/oryxos/boot/ProviderBootstrapConfiguration.java`、现有配置装配或测试夹具中补齐 Mock 全应用接线，使 CLI 与 REST 都复用正式 `AgentService`，不得在入口直接调用 `MockChatModel`、Provider 或 Tool（FR-012、FR-013）
- [x] T024 [US3] 运行 `MockAgentE2ETest`、`BootCliChatTest`、`WebServiceAgentIT` 与 `ToolRegistryAuditIT` 到 GREEN，核对每个工具意图执行次数恰为 1，并把命令/计数证据写入 `specs/012-human-trigger-main-flow/verification.md`（SC-004、SC-006）
- [x] T025 [US3] 新建 `oryxos-boot/src/test/java/io/oryxos/boot/HumanTriggerFlowIT.java`，标记 `@Tag("integration")` 并用环境条件在缺少真实 key/网络时明确 skipped；有条件时验证真实天气对话、会话和审计，不将其结果冒充默认门禁（FR-018，SC-007）

**Checkpoint**: Mock 整机 gate 自动通过；真实模型场景有明确、诚实的可选入口。

---

## Phase 6: User Story 4 - 失败路径仍可审计且不拖垮进程 (Priority: P2)

**Goal**: Provider 失败、Sandbox 拒绝和工具异常各留下唯一失败审计，随后运行时仍可处理独立请求。

**Independent Test**: 三种失败分别发生一次，查询两类审计的失败状态并在同一进程执行后续健康或独立 Agent 请求。

### Tests for User Story 4（先 RED）

- [x] T026 [P] [US4] 扩展 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderFailureMappingTest.java` 与 `AuditPersistenceFailureTest.java`，断言 Provider 每条失败路径恰写一条关联 Session 的 FAILED 审计且不泄密（FR-014，SC-005）
- [x] T027 [P] [US4] 扩展 `oryxos-core/src/test/java/io/oryxos/core/tool/ToolExecutorTest.java` 与 `oryxos-boot/src/test/java/io/oryxos/boot/SandboxAuditIT.java`，先写 Sandbox 拒绝和工具异常各恰一条 `success=false`、零副作用及后续请求可用的 RED（FR-015，SC-005）

### Implementation for User Story 4

- [x] T028 [US4] 仅在聚焦 RED 证明断点存在时，最小修改 `oryxos-provider/src/main/java/io/oryxos/provider/DefaultProviderService.java` 或 `oryxos-core/src/main/java/io/oryxos/core/tool/ToolExecutor.java` 补齐失败审计；不得重复记录、吞异常或改变公开错误语义（FR-014、FR-015）
- [x] T029 [US4] 运行三类失败聚焦测试与 `MockAgentE2ETest` 到 GREEN，确认失败后的进程健康和成功主流程均未回退，并把证据写入 `specs/012-human-trigger-main-flow/verification.md`（SC-005）

**Checkpoint**: 成功与失败两侧都可对账，且没有双写审计。

---

## Phase 7: Polish, Documentation & Convergence

**Purpose**: 配置、文档、全量门禁和 Spec-Kit 收敛。

- [x] T030 [P] 在 `config/application.yml.example` 增加无凭据 Mock Provider 示例，在 `README.md` 与 `website/zh/docs/api.md`、`website/docs/api.md` 补充 `GET /api/v1/sessions`、`/console/` 无密钥对账流程和真实模型未默认验证边界（FR-020）
- [x] T031 按 `specs/012-human-trigger-main-flow/quickstart.md` 执行后端聚焦测试、`npm --prefix frontend test`、`npm --prefix frontend run build`、`./mvnw.cmd clean verify` 与 `git diff --check`，完整记录退出码、跳过项和失败摘要到 `verification.md`；任何失败不得通过禁用测试或放宽门禁处理（SC-006、SC-007）
- [x] T032 对照第 27 节课件逐项检查 `SessionManager.listRecent`、REST 列表、摘要 DTO、Mock Provider、三类 Harness、控制台真实会话、示例配置和文档的存在性，并在 `verification.md` 标记完成或缺口
- [x] T033 执行 `speckit-converge`，核对 constitution/spec/plan/tasks/contracts/代码；如产生追加任务则回到对应 RED/GREEN 和 T031 全量门禁，只有无剩余项时勾选本任务

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 无依赖，先保护用户改动并建立证据文件。
- Phase 2 依赖 Phase 1，钉住共享内核不变量。
- US1 和 US2 均依赖 Phase 2；二者修改文件基本独立，可分别推进，但 Maven 测试不要并发写共享 `target/`。
- US3 依赖 US1 的 Mock Provider和 US2 的列表 API。
- US4 依赖共享审计链，功能上可在 US1 后进行；最终需与 US3 整机测试一起回归。
- Phase 7 依赖所有目标用户故事完成。

### User Story Dependencies

- **US1**: 独立 MVP；完成后即可无 HTTP 验证主流程。
- **US2**: 可独立用预置 Session 验证，不依赖 Mock；为 US3 提供列表与 UI 可观测面。
- **US3**: 依赖 US1 + US2，完成三面同源整机验收。
- **US4**: 可独立验证失败审计，最终与 US3 一起证明进程可恢复。

### Within Each User Story

- 测试任务必须先执行并观察正确 RED。
- core/data/provider 模型先于 service/endpoint/UI。
- 聚焦 GREEN 后才能进入跨模块集成。
- 同一文件的任务串行，保留用户现有未提交内容。

### Parallel Opportunities

- T002、T003、T004 修改不同文件，可并行调查/写测试。
- US1 的 T005 与 T006 可并行写 RED；实现 T008/T009 串行收敛。
- US2 的 storage、web、frontend RED（T011/T012/T013）可并行；实现按 T014→T015/T017→T018/T019 顺序。
- US4 的 Provider 与 Tool/Sandbox RED（T026/T027）可并行。
- T030 的中英文文档可分文件并行编辑，但最终术语以实际契约为准。

---

## Implementation Strategy

### MVP First

1. 完成 Setup + Foundational。
2. 完成 US1 的 Mock Provider 与快速主流程 Harness。
3. 停下运行 `MockProviderFlowTest`，独立确认 2/1/4/1 对账。

### Incremental Delivery

1. US1：无密钥内核链路。
2. US2：REST/控制台可观察面。
3. US3：CLI + HTTP 整机三面同源。
4. US4：失败审计兜底。
5. 文档、全量 verify、converge 收口。

## Task Summary

- 总任务数：33
- Setup/Foundation：4
- US1：6
- US2：10
- US3：5
- US4：4
- Documentation/Verification/Convergence：4

## Notes

- 不自动 commit、push、切分支、创建 PR/Issue 或使用真实密钥。
- `[P]` 只表示文件独立；共享 Maven `target/` 的命令仍串行执行。
- 认为课件、规格或测试依据有误时停止并请求决定，不通过弱化断言让门禁变绿。

## Phase 8: Convergence

- [x] T034 在 `ProviderFailureMappingTest.java` 与 `ToolExecutorTest.java` 增加同一运行时实例先失败、后续独立请求成功且审计各唯一的回归，满足 SC-005 与 US4 验收场景（partial）
- [x] T035 在新增 CLI Mock 与 `HumanTriggerFlowIT` 后重新执行前端测试/构建、`.\mvnw.cmd clean verify` 和 `git diff --check`，把最终结果回填 `verification.md`，满足 T031 与 SC-006（partial）

