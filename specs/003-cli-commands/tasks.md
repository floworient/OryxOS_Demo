# Tasks: 第 18 节 CLI 命令

**Input**: `specs/003-cli-commands/` 下的 spec、plan、research、data-model、contracts、quickstart

**Tests**: 本 feature 按 FR-017 和宪章 VI 强制 TDD；每组实现前必须运行新增测试并确认因缺少目标行为而失败，完成后再确认通过。

**Organization**: 任务按四个用户故事组织；任务完成标记必须以实际测试证据为准。

## Phase 1: Setup（共享构建与基线）

**Purpose**: 固定现状并准备唯一可执行 JAR 与测试依赖。

- [x] T001 记录 `git status --short`，执行 `./mvnw.cmd -pl oryxos-core,oryxos-storage,oryxos-channel-cli,oryxos-cli,oryxos-boot -am test` 并把基线结果写入 `specs/003-cli-commands/quickstart.md`；不得修改 `.claude/settings.local.json`
- [x] T002 调整 `oryxos-cli/pom.xml`、`oryxos-channel-cli/pom.xml`、`oryxos-boot/pom.xml`：为前两者增加测试依赖，移除 CLI 独立 shade exec，显式配置 Boot exec 的 `io.oryxos.OryxOsApplication` 主类；运行 `./mvnw.cmd -pl oryxos-cli,oryxos-channel-cli,oryxos-boot -am package -DskipTests`

---

## Phase 2: Foundational（命令树与运行时依赖倒置）

**Purpose**: 建立所有故事共用的命令结构、DTO、输出和退出码，但不实现任何占位成功。

**⚠️ CRITICAL**: 本阶段完成前不得进入用户故事实现。

- [x] T003 [P] 在 `oryxos-cli/src/test/java/io/oryxos/cli/OryxOsCliCommandTreeTest.java` 先写失败测试：根/4 个分组/12 条业务路径均可解析，裸分组打印帮助，`-h/-V` 不调用 `CliRuntime`，未知命令和缺参数退出 2；运行 `./mvnw.cmd -pl oryxos-cli -am -Dtest=OryxOsCliCommandTreeTest -Dsurefire.failIfNoSpecifiedTests=false test` 确认失败（FR-001、FR-002、FR-014、SC-001）
- [x] T004 [P] 在 `oryxos-cli/src/test/java/io/oryxos/cli/CliFailureHandlerTest.java` 先写失败测试：正常输出走 stdout、异常走 stderr、api-key/token/Authorization 值被脱敏、执行异常退出 1 且普通输出不含堆栈；运行聚焦测试确认失败（FR-014～FR-016）
- [x] T005 新建 `oryxos-cli/src/main/java/io/oryxos/cli/CliRuntime.java`，定义返回 void 的 `chat(Path,String,String,Reader,PrintWriter)`、`serve(Path,int)`、`gateway(Path)`，以及 `providers(Path)`、`tools(Path)`、`sessions(Path)` 只读清单和不可变 `ProviderInfo/ToolInfo`；修改 `OryxOsCli.java` 提供 `int execute(String...)` 并构造命令树；在 `oryxos-cli/src/main/java/io/oryxos/cli/command/` 新建 12 个业务命令与 `Profile/Provider/Tool/Session` 4 个分组命令，只允许委托注入协作者，不返回占位成功（T003）
- [x] T006 在 `oryxos-cli/src/main/java/io/oryxos/cli/CliFailureHandler.java` 实现退出码 1/2、stdout/stderr 分流与凭据脱敏，并在 `OryxOsCli.java` 注册 execution/parameter exception handlers；运行 T003/T004 确认通过（T004）

**Checkpoint**: 命令契约完整且可用 Fake runtime 测试，尚未完成的业务委托必须显式失败，不能假装成功。

---

## Phase 3: User Story 1 - 初始化并管理本地工作区（P1）🎯 MVP

**Goal**: 无 Spring 启动地完成 init/status/Profile CRUD（删除为归档）及所有路径安全约束。

**Independent Test**: 在临时目录执行两次 init、status、create/list/show/delete；文件零覆盖、Agent 可解析、归档可恢复，所有恶意名称/链接零越界 IO。

### Tests for User Story 1 ⚠️

- [x] T007 [P] [US1] 在 `oryxos-cli/src/test/java/io/oryxos/cli/WorkspaceRootResolverTest.java` 先写失败测试：`oryxos.root` > `ORYXOS_ROOT` > `<user.dir>/.oryxos`，相对输入转绝对规范路径，空白配置回退；运行聚焦测试确认失败（FR-004、Review Focus 2）
- [x] T008 [P] [US1] 在 `oryxos-cli/src/test/java/io/oryxos/cli/WorkspaceServiceTest.java` 先写失败测试：六目录、`memory/MEMORY.md`、三个 Bootstrap 文件创建；10 次 init 不覆盖；文件占目录位/根链接失败；status 不隐式修复；名称约束 `1–64`、`[A-Za-z0-9][A-Za-z0-9._-]*`、`.`/`..`/绝对/盘符/UNC/Windows 设备名/链接全部拒绝且外部哨兵读取写入为零；运行聚焦测试确认失败（FR-004～FR-008、SC-002、SC-003、SC-004、Review Focus 1）
- [x] T009 [P] [US1] 在 `oryxos-cli/src/test/java/io/oryxos/cli/WorkspaceCommandTest.java` 先写失败测试：`init/status/profile list/create/show/delete` 的 stdout/stderr/退出码，create 模板经 `AgentLoader.loadAll` 可解析，list 字典序，show 原样，delete 归档整个 attachments 且同毫秒冲突使用 `-N`；运行聚焦测试确认失败（FR-005、FR-006、FR-007、FR-008、FR-014）

### Implementation for User Story 1

- [x] T010 [US1] 新建 `oryxos-cli/src/main/java/io/oryxos/cli/WorkspaceRootResolver.java`，实现 `Path resolve(Properties,Map<String,String>,Path)` 的固定优先级与绝对规范化；运行 T007 确认通过
- [x] T011 [US1] 新建 `oryxos-cli/src/main/java/io/oryxos/cli/WorkspaceService.java`，实现 `initialize()`、`status()`、`listProfiles()`、`createProfile(String)`、`showProfile(String)`、`archiveProfile(String)`；构造参数为 `Path root, Clock clock`；使用 NOFOLLOW_LINKS/real path 边界，模板固定 qwen/qwen-plus、cli channel、三个 Bootstrap、10/20，不写密钥（T008）
- [x] T012 [US1] 完成 `InitCommand.java`、`StatusCommand.java`、`ProfileListCommand.java`、`ProfileCreateCommand.java`、`ProfileShowCommand.java`、`ProfileDeleteCommand.java` 与 `OryxOsCli.java` 的 WorkspaceService 装配；不得启动或引用 Spring 类型；运行 T007～T009 及 `./mvnw.cmd -pl oryxos-cli -am test`（T009）

**Checkpoint**: US1 可独立作为本地工作区管理 MVP 演示，轻命令 Spring 启动次数为零。

---

## Phase 4: User Story 2 - 在终端与 Agent 连续对话（P1）

**Goal**: chat 通过稳定 Session 和唯一 AgentService 链路完成多轮交互，/quit/空白/EOF 零调用。

**Independent Test**: 可控输入两问后 /quit，以桩 Agent 链路验证恰好两次处理、同一 Session、输出顺序和关闭；storage 重建后恢复历史。

### Tests for User Story 2 ⚠️

- [x] T013 [P] [US2] 在 `oryxos-storage/src/test/java/io/oryxos/storage/SessionGetOrCreateTest.java` 先写失败测试：同一 channel/user/profile 重启前后返回同一 ID 和历史，任一身份不同则隔离，并发顺序调用不创建重复行；运行 `./mvnw.cmd -pl oryxos-storage -am -Dtest=SessionGetOrCreateTest -Dsurefire.failIfNoSpecifiedTests=false test` 确认失败（FR-010、SC-005）
- [x] T014 [P] [US2] 在 `oryxos-channel-cli/src/test/java/io/oryxos/channel/cli/CliChannelTest.java` 先写失败测试：Reader 输入两问、空白、`/quit`、EOF、单轮异常；仅两问调用 `AgentService.process`，profile/user/channel 正确，输出顺序稳定，失败不重试且不关闭调用方 Reader/Writer；运行聚焦测试确认失败（FR-009、FR-010、Review Focus 4）
- [x] T015 [P] [US2] 在 `oryxos-boot/src/test/java/io/oryxos/boot/BootCliChatTest.java` 先写失败集成测试：chat 以 WebApplicationType.NONE、统一 workspace 启动真实 Spring 装配，使用 Provider 桩完成一轮并关闭上下文，LLM/session 审计关联存在；运行聚焦测试确认失败（FR-009、FR-015、FR-016）

### Implementation for User Story 2

- [x] T016 [US2] 修改 `oryxos-core/src/main/java/io/oryxos/core/session/SessionManager.java` 增加 `Session getOrCreate(String,String,String)`；在 `oryxos-storage/src/main/java/io/oryxos/storage/JpaSessionManager.java` 以 `new Session(...).id()` + `find` 实现稳定恢复/创建，Session ID 算法不得复制到其他文件；运行 T013 和既有 `SessionPersistenceTest`（T013）
- [x] T017 [US2] 新建 `oryxos-channel-cli/src/main/java/io/oryxos/channel/cli/CliChannel.java`，构造注入 `AgentService, SessionManager, Reader, PrintWriter, userId`，实现 `void run(String profileName)` 的提示/空白/quit/EOF/失败语义；运行 T014（T014）
- [x] T018 [US2] 在 `oryxos-boot/src/main/java/io/oryxos/boot/BootCliRuntime.java` 实现 `CliRuntime.chat`：把 root 注入 `oryxos.workspace`，启动 NONE 上下文，取 AgentService/SessionManager 构造 CliChannel，并 try-with-resources 关闭；完成 `ChatCommand.java` 的 `--profile` 默认 `default` 和当前用户 fallback `local-user`；运行 T015 及 core/storage/channel/boot 相关回归（T015）

**Checkpoint**: US2 可在模型桩下独立完成多轮 chat，真实模型仅作为可选联网验收。

---

## Phase 5: User Story 3 - 启动统一运行模式（P2）

**Goal**: serve/gateway 由 Boot 惰性启动真实聚合运行时，共享工作区并在失败/终止时正确清理。

**Independent Test**: 随机端口启动 serve 并访问 health；端口冲突返回 1 且无遗留上下文；gateway 只报告实际 Bean，不把 IM marker 当可用渠道。

### Tests for User Story 3 ⚠️

- [x] T019 [US3] 在 `oryxos-boot/src/test/java/io/oryxos/boot/BootCliRuntimeTest.java` 先写失败测试：serve 默认/显式端口、health UP、统一 root→workspace、gateway 聚合上下文与真实组件清单；端口越界由命令层退出 2，端口占用/启动中途异常退出 1 且上下文关闭计数 100%；运行聚焦测试确认失败（FR-003、FR-011、FR-015、SC-006、Review Focus 2/5）

### Implementation for User Story 3

- [x] T020 [US3] 在 `BootCliRuntime.java` 实现 `serve(Path,int)` 与 `gateway(Path)`，使用 servlet 聚合上下文、shutdown hook 和真实 Bean 探测；完成 `ServeCommand.java` 的 `--port` 1..65535 与 `GatewayCommand.java`；不在 CLI 复制 Web/Channel 逻辑（T019）
- [x] T021 [US3] 修改 `oryxos-boot/src/main/java/io/oryxos/OryxOsApplication.java`：main 构造 WorkspaceRootResolver/WorkspaceService/BootCliRuntime/OryxOsCli 并执行，仅非零结果调用 `System.exit`，成功轻命令自然返回，serve/gateway 由非 daemon 线程维持并注册 shutdown hook 关闭 `BootCliRuntime`；保留 `@SpringBootApplication` 供上下文启动；新增 `oryxos-boot/src/test/java/io/oryxos/OryxOsCliApplicationIT.java` 验证 help 不启动 Spring、serve 启动现有 Web；运行 boot 集成测试

**Checkpoint**: US3 的两个运行模式真实启动且不夸大未实现渠道。

---

## Phase 6: User Story 4 - 查询 Provider、Tool 与 Session（P2）

**Goal**: 三个 list 命令读取真实配置/注册表/存储，空清单成功、Session 稳定倒序、Provider 零凭据泄漏。

**Independent Test**: 对空集合和两条预置数据执行三个命令，逐项核对来源、列、排序、脱敏和退出码。

### Tests for User Story 4 ⚠️

- [x] T022 [P] [US4] 在 `oryxos-storage/src/test/java/io/oryxos/storage/SessionSummaryQueryTest.java` 先写失败测试：active/archived 摘要只含 id/profile/status/lastActiveAt，按 `lastActiveAt DESC, id ASC`，不反序列化损坏 messages_json；运行聚焦测试确认失败（FR-012、FR-013、SC-007）
- [x] T023 [P] [US4] 在 `oryxos-boot/src/test/java/io/oryxos/boot/BootCliQueryTest.java` 先写失败测试：ProviderProperties 两条声明在凭据缺失时仍返回 name/type/model/baseUrl 且输出不含 apiKey/变量值；runtimeTools 返回真实 name/description 和空集合；Session 返回 storage 摘要；运行聚焦测试确认失败（FR-012、FR-013、FR-016、Review Focus 3）
- [x] T024 [P] [US4] 在 `oryxos-cli/src/test/java/io/oryxos/cli/QueryCommandTest.java` 先写失败测试：provider/tool/session 表头、空状态、稳定排序、非 ASCII/超长字段、stdout/stderr 和退出码；运行聚焦测试确认失败（FR-012～FR-014、SC-007）

### Implementation for User Story 4

- [x] T025 [US4] 新建 `oryxos-core/src/main/java/io/oryxos/core/session/SessionSummary.java`，给 `SessionManager` 增加 `List<SessionSummary> list()`；修改 `SessionEntity.java` 提供 `summary()`、`SessionRepository.java` 增加 `findAllByOrderByLastActiveAtDescIdAsc()`、`JpaSessionManager.java` 映射摘要且不 decode messages；运行 T022 与既有 storage 测试（T022）
- [x] T026 [US4] 在 `BootCliRuntime.java` 实现 `providers/tools/sessions`：Provider 使用仅配置绑定上下文而不构造 ChatModel，Tool 使用 `runtimeTools`，Session 使用 SessionManager；完成 `ProviderListCommand.java`、`ToolListCommand.java`、`SessionListCommand.java` 的表格/空状态输出；运行 T023/T024（T023、T024）

**Checkpoint**: 12 条业务命令全部具备真实行为，三个查询没有示例或占位数据。

---

## Phase 7: Polish & Cross-Cutting Validation

**Purpose**: 锁定发行物、文档一致性和全量质量门禁。

- [x] T027 在 `oryxos-boot/src/test/java/io/oryxos/OryxOsCliPackagingIT.java` 增加进程级测试：Boot exec JAR 的根/分组/12 条 help、version、轻命令无需密钥、未知命令退出 2；确认 `oryxos-cli` 不再产出误导性的 exec JAR（FR-001～FR-003、SC-001）
- [x] T028 [P] 更新 `website/zh/docs/cli.md`、`website/docs/cli.md` 和 `README.md` 中的 JAR 路径、12 命令实际范围、Agent delete 归档语义、root/workspace 规则和未实现渠道说明；不得保留与实现冲突的示例（Constitution VI）
- [x] T029 按 `specs/003-cli-commands/quickstart.md` 运行聚焦模块测试和打包，记录实际命令、通过数、耗时及未执行的真实模型/外部渠道验证；检查 `git diff --check`、密钥模式和 `.claude/settings.local.json` 未被纳入
- [x] T030 执行 `./mvnw.cmd test` 与 `./mvnw.cmd clean verify`，不得降低 Checkstyle/SpotBugs/JaCoCo/Failsafe 门禁；运行 speckit analyze/converge 对照 FR-001～FR-017、SC-001～SC-008，若生成补充任务则按新 ID 完成后重跑全量验证

---

## Dependencies & Execution Order

### Phase Dependencies

- Phase 1 → Phase 2：构建与测试依赖先稳定。
- Phase 2 → US1/US2/US3/US4：命令树和 `CliRuntime` 是共同阻塞项。
- US1 提供统一 workspace root，US2/US3/US4 的 Boot 适配均依赖 T010～T012。
- US2 的 Session get-or-create 与 US4 的摘要读取修改相邻文件，按 US2 → US4 顺序避免冲突。
- US3/US4 均修改 `BootCliRuntime.java`，单执行者按 US3 → US4；多执行者不得并行编辑同一文件。
- Phase 7 依赖四个故事全部完成。

### User Story Dependencies

- **US1 (P1)**: Foundational 后可独立完成，是最小可交付 MVP。
- **US2 (P1)**: 依赖 US1 的 workspace root；不依赖 US3/US4。
- **US3 (P2)**: 依赖 US1 和 BootCliRuntime 基础；不依赖 US2 的对话逻辑。
- **US4 (P2)**: 依赖 US1；Session 摘要实现安排在 US2 get-or-create 之后。

### Parallel Opportunities

- T003/T004 可并行；T007/T008/T009 可并行；T013/T014/T015 可并行；T022/T023/T024 可并行。
- 不同模块的失败测试可并行编写，但同一实现文件上的任务必须串行。
- T028 文档更新可与不修改相同文件的最终包装测试 T027 并行。

---

## Implementation Strategy

### MVP First

1. 完成 T001～T006 基础。
2. 完成 T007～T012 交付 US1。
3. 停止并验证 init/status/Profile 管理、安全边界和轻命令性能。

### Incremental Delivery

1. US1 工作区管理。
2. US2 chat 与 Session 恢复。
3. US3 serve/gateway。
4. US4 三类真实查询。
5. 全量包装、文档和门禁。

## Notes

- 每个测试任务必须先保留一次预期失败证据，失败原因必须是目标行为尚未实现，而非编译环境或无关回归。
- 每个实现任务完成后运行其列出的聚焦测试；每个 phase checkpoint 运行受影响模块全部测试。
- `[P]` 只表示文件和依赖允许并行，不授权多 Agent；当前执行按单会话顺序推进。
- 不自动提交或推送；若用户要求提交，再按 phase 形成聚焦 commit。

