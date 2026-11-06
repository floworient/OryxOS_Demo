# Tasks: Web Service 核心能力

**Input**: `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/web-api.md`

**Tests**: 本功能强制 TDD。每个实现任务必须先运行对应 RED 任务，确认因缺少目标行为而失败，再写最小 GREEN；RED/GREEN 命令与结果写入 `verification.md`。

## Phase 1: Setup & Session Foundation

- [x] T001 在 `oryxos-core/src/test/java/io/oryxos/core/session/SessionTest.java` 先写 Session 随机代次、显式 ID 恢复、非法 ID/时间拒绝的 RED 测试；运行 `./mvnw.cmd -pl oryxos-core -Dtest=SessionTest test` 并记录预期失败（FR-005、FR-024）。
- [x] T002 [P] 在 `oryxos-storage/src/test/java/io/oryxos/storage/SessionArchiveTest.java`、`SessionMigrationV6Test.java`、现有 `SessionGetOrCreateTest.java`/`SessionPersistenceTest.java` 先写 active 复用、归档详情、归档后新代、保存不复活、并发最多一个 active、V5 数据无损升级的 RED 测试；运行聚焦测试并记录失败（FR-005、FR-008、FR-010、FR-019、SC-003、SC-009）。
- [x] T003 实现 `SessionStatus.java`、`SessionDetails.java`，修改 `Session.java`、`SessionManager.java`、`JpaSessionManager.java`、`SessionEntity.java`、`SessionRepository.java` 并新增 `V6__support_session_generations.sql`；最小实现使 T001/T002 GREEN，随后运行 core/storage 模块测试（FR-005、FR-008、FR-010、FR-019）。
- [x] T004 [P] 在 `oryxos-provider/src/test/java/io/oryxos/provider/ProviderRoutingTest.java` 先写 ProviderCatalog 不可变配置名称测试，确认 RED；新增 core `ProviderCatalog.java` 并由 `ProviderRegistry` 实现，确认 GREEN 且 `ProviderService` 仍可作为 lambda 使用（FR-017、FR-018）。

**Checkpoint**: Session 生命周期和 Provider 只读契约稳定，Web 无需依赖 storage/provider 实现。

---

## Phase 2: Shared Web Execution Foundation

- [x] T005 [P] 在 `oryxos-web/src/test/java/io/oryxos/web/api/Utf8MessageValidatorTest.java` 先写空白、32768/32769 UTF-8 字节（含多字节字符）RED；实现 `Utf8MessageValidator` 并确认 GREEN（FR-007）。
- [x] T006 [P] 在 `oryxos-web/src/test/java/io/oryxos/web/api/AgentCallExecutorTest.java` 先写成功、Provider 异常保留、60 秒可控超时/取消/中断 RED；实现 `AgentCallExecutor`、`AgentCallTimeoutException` 和 `WebExecutionConfiguration` 受管虚拟线程执行器，确认 GREEN（FR-013、FR-020）。
- [x] T007 扩展 `GlobalExceptionHandlerTest`（若不存在则创建）并先写 Agent/Session/Provider/Storage/Timeout 的固定状态码、业务码、脱敏消息 RED；最小修改 `GlobalExceptionHandler.java`，确认 GREEN 且既有 Memory/Notify 映射不回归（FR-002、FR-020）。

**Checkpoint**: 两个 Agent HTTP 入口可复用同一校验、截止与错误映射。

---

## Phase 3: User Story 1 - 连续 Web Session (P1) 🎯 MVP

- [x] T008 [US1] 在 `SessionWebServiceTest.java` 先写创建 201/复用 200、未知 Agent、32 KiB 前置拒绝、仅 web 可写、最近 100 条、安全 Tool 投影、归档、同 Session busy、不同 Session 独立的 RED 测试（FR-004 至 FR-011，重点覆盖 FR-009 的敏感载荷禁止公开）。
- [x] T009 [US1] 实现 `SessionWebService`、请求/View records 与按 Session ID 的 `tryLock`；运行 T008 测试使其 GREEN，并运行存储聚焦测试确保归档竞态不复活（FR-004 至 FR-011，含 FR-009）。
- [x] T010 [US1] 在 `SessionApiControllerTest.java` 先写四个端点的 MockMvc RED，包括 Location/200/201、统一信封、校验、404/409/503/504；实现 `SessionApiController`，确认 GREEN（FR-001、FR-002、FR-004、FR-006、FR-008、FR-010）。
- [x] T011 [US1] 在 `oryxos-boot/src/test/java/io/oryxos/boot/WebServiceSessionRestartIT.java` 先写真实 SQLite 的创建→两轮消息→查询→归档→重启查询→同身份新代 RED；完成装配所需最小修正后确认 GREEN，证明调用只经 AgentService（SC-002、SC-003、SC-009）。

**Checkpoint**: Demo 四完整可运行；独立部署时已具备连续对话、恢复、查询和归档。

---

## Phase 4: User Story 2 - 一次性 Agent 调用 (P1)

- [x] T012 [US2] 在 `AgentInvocationWebServiceTest.java` 先写每次独立 web-invoke Session、执行前保存、只返回 reply、未知 Agent、失败仍有关联 Session、60 秒超时的 RED；实现 `AgentInvocationWebService` 与 DTO，确认 GREEN（FR-012、FR-013）。
- [x] T013 [US2] 在 `AgentApiControllerTest.java` 先写 invoke 路由、32 KiB、统一信封、404/503/504 和响应不含 sessionId 的 RED；实现 `AgentApiController`，确认 GREEN（FR-001、FR-002、FR-007、FR-012、FR-020）。
- [x] T014 [US2] 在 `oryxos-boot/src/test/java/io/oryxos/boot/WebServiceAgentIT.java` 先写两次真实 HTTP invoke 产生不同 Session、LLM/Tool 审计关联、响应无 ID 且 AgentService/Tool 只执行一次的 RED；使用可控 Provider/Tool 与临时 SQLite 确认 GREEN（SC-006、FR-003、FR-024）。

**Checkpoint**: 无状态同步调用可独立验收，失败与超时契约稳定。

---

## Phase 5: User Story 3 - 能力与状态发现 (P2)

- [x] T015 [P] [US3] 在 `ProfileApiControllerTest.java` 和 `ToolApiControllerTest.java` 先写排序、全局/Agent 可见性、未知 Agent、安全字段负断言 RED；实现 `ProfileApiController/ProfileView` 与 `ToolApiController/ToolView`，确认 GREEN（FR-014、FR-016）。
- [x] T016 [P] [US3] 更新 `MemoryApiControllerTest.java` 先写规范 query 路由、兼容路由一致、必填/未知 Agent/Memory 503 RED；实现 `MemoryQueryService` 并重构 Controller 委托，确认 GREEN（FR-015、FR-020）。
- [x] T017 [P] [US3] 在 `SystemApiControllerTest.java` 先写 health 无 Provider 交互、info 排序计数/版本 fallback/敏感字段负断言 RED；实现 `SystemApiController/SystemInfoView`，确认 GREEN（FR-017、FR-018）。
- [x] T018 [US3] 在 `OpenApiContractTest.java` 先写核心 10 端点、统一响应 schema 与 `/api/**` CORS RED；实现 `CorsConfiguration`，补齐 OpenAPI annotation/metadata 并确认 GREEN（FR-001、FR-002、FR-021、FR-022、SC-001）。

**Checkpoint**: Demo 五的查询与调用端点齐备，能力摘要无敏感字段。

---

## Phase 6: Documentation, Verification & Convergence

- [x] T019 [P] 更新 `README.md`、`website/zh/docs/api.md`、`website/docs/api.md`，准确记录 10 端点、示例、32 KiB/100 条/60 秒、归档代次、开放 CORS/无认证/单实例锁/尽力中断限制，不把扩展端点描述为已实现（FR-021、FR-023）。
- [x] T020 运行 `quickstart.md` 的聚焦测试、模块测试、根目录 `.\mvnw.cmd test`、`.\mvnw.cmd clean package`、网站构建和 `git diff --check`；在 `verification.md` 记录每个 RED/GREEN 证据、真实命令、退出码、未执行真实 Provider/公网项（FR-024、SC-010）。
- [x] T021 对照 `spec.md`、`plan.md`、`tasks.md`、`contracts/web-api.md` 和真实交付执行 Spec-Kit analyze/converge 等价检查；发现缺口时追加任务并回到 TDD，只有无剩余范围且 10 端点/OpenAPI/文档一致时勾选（SC-001 至 SC-010）。

## Dependencies & Execution Order

- T001/T002 RED → T003 GREEN；T004 可独立完成。
- T005/T006 可并行，T007 依赖二者异常类型。
- US1：T008 → T009 → T010 → T011。
- US2：T012 → T013 → T014，依赖 T003/T005/T006/T007。
- US3：T015/T016/T017 可并行，T018 等三个 Controller 契约稳定。
- T019 可在行为稳定后准备；T020 等全部实现/文档；T021 最后收敛。

## Parallel Opportunities

- T002 与 T004 修改不同模块，可并行写 RED，但 Maven 不并发共享 `target/`。
- T005 与 T006 文件独立，可并行写测试和实现。
- T015/T016/T017 的 Controller 与测试文件相互独立，可并行。
- 中英文 API 文档可并行编辑，状态说明必须以实际验证结果为准。

## Implementation Strategy

先交付 Session 数据不变量，再构建共享 Web 执行基座；以 US1 形成可演示 MVP，随后添加 US2 和只读 US3。每项行为严格保留 RED 输出再写最小 GREEN，不自动 commit/push/切分支，不修改用户现有 `.claude/settings.local.json` 或无关工作。

## Task Summary

- 总任务数：21
- Setup/Foundation：7
- US1：4
- US2：3
- US3：4
- 文档/验证/收敛：3

## Phase 7: Convergence

- [x] T022 恢复 `/api/v1/notify-channels` 的五个 REST 端点在 `website/docs/api.md` 与 `website/zh/docs/api.md` 中的精简章节，明确占位符与掩码规则、204 删除语义和“不支持数据库字段加密”的限制，使已实现端点不因本次文档重写而从参考文档消失（T019、FR-021，partial）。
- [x] T023 将 `website/docs/api.md` 与 `website/zh/docs/api.md` 中的 OpenAPI JSON 地址由 `/v3/api-docs` 改为 `/api-docs`，与 `oryxos-boot/src/main/resources/application.yaml` 的 `springdoc.api-docs.path` 及 `docs/EngineeringBaseline.md` 保持一致（FR-022，contradicts）。
- [x] T024 在 `specs/009-web-service-core/contracts/web-api.md` 的 Core Java 契约中补充 `acquire` 与 `SessionAcquisition`，说明默认实现区分“新建/复用”而不改变 `getOrCreate` 语义（plan: core contract，partial）。
- [x] T025 统一 OpenAPI 响应契约：`NotifyChannelApiController` 补 `produces=application/json`，Session 创建登记 200/201 与 `Location`，通知渠道创建登记 201、删除登记 204；扩展 `OpenApiContractTest` 对 16 个操作逐一断言 `application/json` 与 `ApiResponse*` schema，防止再次退化（FR-022、SC-001，partial）。

