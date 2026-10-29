# Tasks: Notify 渠道管理完整闭环

**Input**: `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`

**Tests**: 本功能强制 TDD。每个测试任务先运行并保留因目标行为缺失而失败的 RED 证据；实现任务只写使对应测试通过的最小代码，然后运行受影响模块回归。

**Organization**: 先完成共享领域和持久化，再交付渠道管理 API；Adapter 扩展契约是安全发送的依赖，因此 US3 在 US2 前实现。所有发送最终经 US2 的 `ToolExecutor` 集成验收。

## Phase 1: Baseline 与依赖确认

- [x] T001 运行 `./mvnw.cmd test`、`./mvnw.cmd clean verify` 和 `git status --short`，记录现有通过数、门禁、耗时及 `.claude/settings.local.json` 为用户未跟踪文件；若 baseline 失败，先报告而不把失败归因于 Notify。
- [x] T002 对照 `specs/004-notify-channel-management/spec.md` 建立 FR-001～FR-021 / SC-001～SC-007 追踪表于本文件 Notes，并确认 `oryxos-tool/pom.xml` 仅需新增 `spring-web` 与 test scope 的 `spring-boot-starter-test`，不新增 OkHttp 或另一套 JSON 库。

---

## Phase 2: Foundational - 通知领域与 SQLite 注册表

**Goal**: 建立 Web 与 Tool 共享的唯一渠道领域语义和可跨重启持久化的注册表。

### RED

- [x] T003 [P] 在 `oryxos-core/src/test/java/io/oryxos/core/notify/NotifyChannelServiceTest.java` 写失败测试：合法创建、名称/设备名/路径拒绝、只允许 webhook、URL 或严格 `${ENV_VAR}`、config 保留键/大小限制、按 `createdAt ASC,name ASC` 缺省、更新保留 createdAt、空/不存在明确失败、冲突异常；运行 `./mvnw.cmd -pl oryxos-core -Dtest=NotifyChannelServiceTest test` 确认因领域类型缺失而失败（FR-001、FR-005、FR-007、FR-012、FR-017）。
- [x] T004 [P] 在 `oryxos-storage/src/test/java/io/oryxos/storage/NotifyChannelPersistenceTest.java` 写失败集成测试：Flyway V4 建表、CRUD、稳定排序、config 严格 round-trip、重启恢复、更新不改变 createdAt、删除、并发同名只成功一次且映射冲突；运行 storage 聚焦测试确认因迁移/实现缺失而失败（FR-002、FR-005、SC-001）。

### GREEN

- [x] T005 实现 `oryxos-core/src/main/java/io/oryxos/core/notify/NotifyChannel.java`、`NotifyChannelRegistry.java`、`NotifyChannelValidator.java`、`NotifyChannelService.java`、`NotifyChannelNotFoundException.java`、`NotifyChannelConflictException.java`；接口签名按 plan，使用注入 `Clock`，集合防御性复制，运行 T003 通过。
- [x] T006 实现 `oryxos-storage/src/main/resources/db/migration/sqlite/V4__create_notify_channels.sql`、`entity/NotifyChannelEntity.java`、`repository/NotifyChannelJpaRepository.java`、`JpaNotifyChannelRegistry.java`；使用 `saveAndFlush`、严格 `Map<String,String>` JSON 与 `findAllByOrderByCreatedAtAscNameAsc()`，必要时只调整 `StorageConfiguration.java` 扫描；运行 T004 与全部 storage 回归通过。

**Checkpoint**: core service 与 SQLite registry 可独立创建、恢复、更新、解析缺省和删除渠道，无 Web/Tool 依赖。

---

## Phase 3: User Story 1 - 管理全局通知渠道（P1）🎯 Management MVP

**Goal**: 通过 REST 完成真实 SQLite 渠道 CRUD、状态码和敏感值编辑闭环。

**Independent Test**: MockMvc/随机端口对临时 SQLite 执行 POST→GET→PUT→重启 GET→DELETE，验证状态码、顺序、掩码和真实内部值。

### RED

- [x] T007 [US1] 在 `oryxos-web/src/test/java/io/oryxos/web/notify/NotifyChannelMaskerTest.java` 写失败测试：真实 URL 变 `****`+末四位、`${ENV_VAR}` 原样、敏感 config 键大小写变体脱敏、PUT null/空白/当前掩码保留旧值、新值替换、非敏感 config 完整替换；运行聚焦测试确认失败（FR-006、FR-019、Review Focus 3）。
- [x] T008 [US1] 在 `oryxos-web/src/test/java/io/oryxos/web/notify/NotifyChannelApiControllerTest.java` 写失败 MockMvc 测试：POST 201、list/detail 200、PUT 200、DELETE 204、400/404/409、稳定 ApiResponse、Bean Validation、非法类型零持久化、canary URL/token 在所有响应与捕获日志中零出现；运行聚焦测试确认失败（FR-003、FR-004、FR-006、FR-007、SC-005）。

### GREEN

- [x] T009 [US1] 实现 `oryxos-web/src/main/java/io/oryxos/web/notify/CreateNotifyChannelRequest.java`、`UpdateNotifyChannelRequest.java`、`NotifyChannelView.java`、`NotifyChannelMasker.java`；DTO 只做 HTTP 输入约束，转换为 core command，运行 T007 通过。
- [x] T010 [US1] 实现 `NotifyChannelApiController.java`，并在 `oryxos-web/src/main/java/io/oryxos/web/api/GlobalExceptionHandler.java` 增加 core not-found/conflict/validation 的 404/409/400 安全映射；Controller 只依赖 `NotifyChannelService`，运行 T008 及全部 web 回归通过。
- [x] T011 [US1] 在 `oryxos-boot/src/test/java/io/oryxos/boot/NotifyChannelApiIT.java` 写失败后实现最小 Boot 装配，使真实聚合上下文完成 CRUD、关闭重启再读取和 DELETE；不得让 web 依赖 storage，运行该 IT 与 `ModuleBoundaryTest` 通过（SC-001）。

**Checkpoint**: 运维人员可管理全局渠道，API 不泄密；尚未具备发送能力时不得在文档中宣称 notify 可用。

---

## Phase 4: User Story 3 - Adapter 可扩展契约（P2）

**Goal**: 固化第 19 节接口先行要求和显式类型路由，为 US2 提供安全发送插件点。

**Independent Test**: 两个测试 Adapter 按类型各自命中，重复/空白类型构造失败，未知类型安全失败，NotifyTools 不含渠道分支。

### RED

- [x] T012 [US3] 在 `oryxos-tool/src/test/java/io/oryxos/tool/notify/NotifyAdapterRegistryTest.java` 写失败测试：两种类型显式路由、Map 不受原 List 后续修改、重复/空白/null 类型立即失败、未知类型错误不包含目标配置；运行聚焦测试确认失败（FR-008、FR-018、SC-006）。

### GREEN

- [x] T013 [US3] 修改 `oryxos-tool/pom.xml` 增加既定依赖，并实现 `notify/NotifyTarget.java`、`NotifyChannelAdapter.java`、`NotifyAdapterRegistry.java`；`NotifyTarget.config` 防御性复制，registry 使用不可变显式 Map，运行 T012 通过。

**Checkpoint**: 新增 Adapter 不需要修改调用方；核心阶段仍只注册 Webhook 实现。

---

## Phase 5: User Story 2 - Sandbox 与目标解析（P1）

**Goal**: 环境变量与 URI 在发送前解析，白名单默认拒绝，并把批准的同一 URI交给 Adapter。

### RED

- [x] T014 [P] [US2] 在 `oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java` 写失败参数化测试：精确 host、`*.` 标签子域、根域规则、大小写/IDN 规范化；拒绝空 allowlist、相对/ftp/file、userinfo、fragment、无 host、非法端口、`evil-example.com`、通配根域与未配置 host；运行聚焦测试确认失败（FR-013、FR-014、SC-004、Review Focus 2）。
- [x] T015 [P] [US2] 在 `oryxos-tool/src/test/java/io/oryxos/tool/notify/NotifyTargetResolverTest.java` 写失败测试：直接 URL、`${ENV_VAR}` 解析、缺失/空白/非法解析值、仅完整占位符、config 的 url 保留键防覆盖、Sandbox 返回 URI精确进入 target；失败路径零 Adapter/网络副作用（FR-013、FR-021、Review Focus 1）。

### GREEN

- [x] T016 [US2] 实现 `sandbox/ActionType.java`、`SandboxAction.java`、`Sandbox.java`、`WhitelistSandbox.java`；`Sandbox.enforce(SandboxAction)` 返回批准的规范化 URI，host 通配按标签边界，运行 T014 通过。
- [x] T017 [US2] 实现 `notify/NotifyTargetResolver.java`，构造注入 `Function<String,String>` 与 Sandbox，只支持完整 `${ENV_VAR}`，不缓存或写回解析值；运行 T015 和全部 oryxos-tool 当前测试通过。

---

## Phase 6: User Story 2 - Webhook Adapter 与 Notify Tool（P1）

**Goal**: 满足第 19 节 HTTP 协议和顺序验收，并接入现有 `OryxTool`。

### RED

- [x] T018 [US2] 在 `oryxos-tool/src/test/java/io/oryxos/tool/notify/WebhookNotifyAdapterTest.java` 用 JDK `HttpServer` 写失败测试：URL 只取 `NotifyTarget.config.url`、POST、application/json、精确 `{"content":"hello"}`、Unicode/转义、2xx 成功、3xx/4xx/5xx/断连失败、请求计数恒为 1、异常不含 canary URL/响应 body；运行聚焦测试确认失败（FR-009、FR-010、SC-003、Review Focus 4）。
- [x] T019 [US2] 在 `oryxos-tool/src/test/java/io/oryxos/tool/builtin/NotifyToolsTest.java` 写失败测试：schema required/additionalProperties、严格 JSON/trailing token、content 缺失/空白/32KiB 边界/未知字段、显式渠道、空白缺省首渠道、注册表空/不存在、环境解析失败、Adapter 未注册；用事件列表钉死 `sandbox.enforce` 先于 `adapter.send`，所有失败 `retryable=false` 且不泄密（FR-011～FR-013、FR-019、SC-003）。

### GREEN

- [x] T020 [US2] 实现 `notify/WebhookNotifyAdapter.java`，使用注入 `RestClient` 和不跟随重定向的 request factory，一次 POST、固定 content payload、只接受 2xx、安全异常且不重试；运行 T018 通过。
- [x] T021 [US2] 实现 `builtin/NotifyTools.java` 直接实现 `OryxTool`，schema 与 `contracts/notify-tool.md` 一致；严格解析 `NotifyInput`，通过 `NotifyChannelService.resolve`→resolver→registry→send，所有失败返回安全且不可重试 ToolResult；运行 T019 与全部 tool 回归通过。

**Checkpoint**: 第 19 节 `WebhookNotifyAdapterTest`/`NotifyToolsTest` 验收全部自动化，且采用当前全局注册表而非旧 Profile 字段。

---

## Phase 7: Boot 装配、统一审计与端到端闭环

**Goal**: `notify` 出现在真实 runtimeTools，只经 ToolExecutor 发送并产生单条成功/失败审计。

### RED

- [x] T022 [US2] 在 `oryxos-boot/src/test/java/io/oryxos/boot/NotifyConfigurationTest.java` 写失败上下文测试：默认空白名单、配置精确/通配 host、唯一 webhook Adapter、`runtimeTools.notify` 存在、重复 Adapter 类型启动失败、Agent 未声明 notify 时不可见、旧 frontmatter 字段仍被拒绝；运行聚焦测试确认失败（FR-015～FR-018）。
- [x] T023 [US2] 在 `oryxos-boot/src/test/java/io/oryxos/boot/NotifyToolAuditIT.java` 写失败集成测试：临时 SQLite + JDK HttpServer + ProfileContext + 真实 ToolExecutor；成功恰好 1 POST/1 success audit，Sandbox 拒绝 0 POST/1 failure audit，HTTP 500 恰好 1 POST/1 failure audit；扫描记录和结果无 canary URL/token（SC-002、SC-005、Review Focus 5）。

### GREEN

- [x] T024 [US2] 实现 `oryxos-boot/src/main/java/io/oryxos/boot/HttpSandboxProperties.java` 与 `NotifyConfiguration.java`，用 `@ConfigurationProperties("oryxos.http")` 显式提供 allowed domains，并装配 Clock/service、System.getenv resolver、Sandbox、RestClient、Webhook Adapter、Adapter Registry、Target Resolver 与 NotifyTools；更新 `application.yaml` 只加入空的安全默认配置，运行 T022 通过。
- [x] T025 [US2] 必要时最小调整 `ReActConfiguration.java` 的 Bean 消歧义，不改变自动工具关闭和 ToolExecutor；运行 T023、既有 `ReActConfigurationTest`、`ToolExecutorTest`、`AutomaticToolExecutionDisabledTest` 及 boot 回归通过。
- [x] T026 扩展 `oryxos-boot/src/test/java/io/oryxos/architecture/ModuleBoundaryTest.java`：core 禁止依赖 infrastructure/tool/channel，web 禁止依赖 storage，渠道模块不进入 notify 出站；运行架构测试通过（Constitution III）。

**Checkpoint**: 管理 API 创建的同一渠道可由 Agent Tool 读取并安全发送，成功/拒绝/远端失败均有真实持久化审计且不重复。

---

## Phase 8: Documentation 与全量验证

- [x] T027 [P] 更新 `README.md`、`website/zh/docs/tool.md`、`website/docs/tool.md`、`website/zh/docs/api.md`、`website/docs/api.md` 中 Notify 的实际范围：全局渠道、webhook-only、`${ENV_VAR}`、Sandbox、CRUD、无专用 IM/富文本；移除任何与实现冲突或声称未来 Adapter 已可用的内容（Constitution VI）。
- [x] T028 按 `specs/004-notify-channel-management/quickstart.md` 运行本地回环 CRUD/发送/失败场景，记录命令、请求计数、审计计数和未执行的真实 Webhook 验收；不得输出 canary 全值。
- [x] T029 运行 `./mvnw.cmd -pl oryxos-core,oryxos-storage,oryxos-tool,oryxos-web,oryxos-boot -am test`，确认第 19 节与完整闭环聚焦测试全绿；任何失败按名称记录并修复后重跑完整命令。
- [x] T030 运行 `./mvnw.cmd test`、`./mvnw.cmd clean verify`、`git diff --check` 和敏感模式扫描；核对 `.claude/settings.local.json` 未纳入，且真实 URL/token canary 不出现在源码、测试报告公开输出或文档。
- [x] T031 逐项对照 FR-001～FR-021、SC-001～SC-007、`contracts/` 和本任务清单，运行 Spec-Kit analyze/converge；补齐遗漏后将完成任务勾选并记录真实验证结果。

---

## Dependencies & Execution Order

- Phase 1 baseline → Phase 2 core/storage → Phase 3 API。
- Phase 4 Adapter 契约是 Phase 6 Tool 的依赖；Phase 5 Sandbox/Resolver 是 Phase 6 Webhook 发送的安全前置。
- Phase 3 与 Phase 4/5 在文件上可并行，但单执行者按编号推进，便于保持 RED 证据。
- Phase 7 依赖 Phase 2～6；Phase 8 依赖全部功能 phase。
- `[P]` 仅表示不同文件可并行，不授权多 Agent；当前执行方式由用户在计划确认后选择。

## Implementation Strategy

1. 先交付 core/storage + 管理 API，形成可独立验证的 Management MVP。
2. 固化 Adapter 和 Sandbox 安全边界。
3. 交付第 19 节 Webhook/NotifyTools 行为。
4. 最后接入 Boot 与真实 ToolExecutor 审计闭环。

## Traceability Notes

- FR-001/005/007/012/017 → T003/T005。
- FR-002 → T004/T006。
- FR-003/004/006/019 → T007～T011。
- FR-008/018 → T012/T013/T022/T024。
- FR-009/010 → T018/T020。
- FR-011～014/021 → T014～T019/T021。
- FR-015/016/020 → T022～T025/T028～T030。
- SC-001 → T004/T011；SC-002 → T023；SC-003 → T018/T019；SC-004 → T014；SC-005 → T008/T023/T030；SC-006 → T012/T013；SC-007 → T029/T030。

## Notes

- 不自动提交或推送；用户明确要求时再按 phase 形成聚焦 commit。
- 生产代码前必须看到对应测试因行为缺失而失败；编译环境错误、拼写错误或无关失败不算 RED。
- 若真实外部 Webhook 未验证，交付结论只报告本地协议/安全闭环，不冒充企业渠道端到端通过。
