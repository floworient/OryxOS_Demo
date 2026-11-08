# 第 27 节实现验证记录

## 2026-11-08：分支与实现前基线

- 开发分支：`codex/lesson27-main-flow`
- 工作方式：在当前脏工作区原地创建分支，未回滚、覆盖或暂存用户已有修改。
- 受保护的既有修改包括：`.gitignore`、`frontend/`、`ReActConfiguration.java`、`ToolConfiguration.java`、`WhitelistSandbox.java`、`OpenApiContractTest.java`，以及 `git status --short` 中其余既有未提交文件。
- Maven 沙箱说明：沙箱内 Mockito/ByteBuddy 动态 Java Agent 自挂载会阻塞；涉及 Mockito 的测试在获批的非沙箱环境运行。非 Mockito 测试在沙箱内可正常完成，确认不是代码死锁。

### 命令与结果

| 命令 | 结果 |
| --- | --- |
| `.\mvnw.cmd -pl oryxos-core,oryxos-provider,oryxos-storage,oryxos-web,oryxos-boot -am test` | GREEN，退出码 0；15 个 Reactor 项全部 SUCCESS，总耗时 01:55。core 131 项、provider 29 项、web 41 项、boot 29 项均无失败；tool/memory 中仅有既有条件跳过。 |
| `npm --prefix frontend test` | GREEN，退出码 0；10 个测试文件、51 项测试全部通过。 |

### 共享契约门禁

- `AutomaticToolExecutionDisabledTest`：1 项通过，确认真实 Provider 请求继续设置 `internalToolExecutionEnabled=false`。
- `AgentServiceTest`：2 项通过，确认入口继续统一委托 `ReActLoop` 并保存会话。
- `ReActLoopTest`：4 项通过，确认消息累积、工具结果回填和单次工具执行基线。
- Spring AI 1.1.2 本地编译表面已由 `StubChatModel` 及 provider 全套测试验证：使用 `ChatModel.call(Prompt)`、`AssistantMessage.ToolCall`、`ChatResponseMetadata.builder()` 与 `DefaultUsage` 即可实现确定性 Mock，无需新增依赖。

### 基线备注

- `BootCliRuntimeTest` 会主动验证固定端口冲突并输出一次预期的“Port was already in use”，该测试及整个 Reactor 最终为 GREEN。
- 本节后续行为继续按 RED → GREEN → REFACTOR 记录聚焦命令和结果。

## 2026-11-08：主流程实现与最终门禁

### RED → GREEN 证据

- Mock Provider 的首轮聚焦编译按预期因 `MockChatModel` 与 `MOCK` 类型尚不存在而 RED；实现后
  `MockChatModelTest`、路由、启动校验和自动工具执行门禁共 12 项 GREEN，provider 全套 35 项 GREEN。
- `MockProviderFlowTest` 通过正式 `AgentService → ReActLoop → ProviderService → ToolExecutor`
  链路完成精确对账：2 条 LLM 审计、1 条工具审计、4 条 Session 消息、1 条 archival 记忆。
- storage 聚焦测试 3 项 GREEN：V7 从合法 JSON 数组回填 `message_count`，损坏历史回填 0；
  摘要查询在数据库层完成状态过滤、稳定排序和上限。
- web 会话聚焦测试 10 项 GREEN，覆盖默认/active/空集/非法过滤和统一错误响应；
  OpenAPI 合同包含 `GET /api/v1/sessions`。
- `MockAgentE2ETest` 在随机真实 HTTP 端口和临时 SQLite 上 GREEN，完成创建 Session、发送消息、
  列表、详情、记忆、工具审计与两条 LLM 审计的整机对账。

### 最终命令

| 命令 | 结果 |
| --- | --- |
| `.\mvnw.cmd clean verify` | 首次退出码 1；所有已运行测试通过，storage Checkstyle 准确发现 `SessionMigrationV7Test` 一行超过 120 字符。未禁用门禁，已拆行修正。 |
| `.\mvnw.cmd verify` | GREEN，退出码 0；15 个 Reactor 模块全部 SUCCESS，总耗时 08:51。包含 Surefire/Failsafe、Checkstyle、SpotBugs 与 JaCoCo 门禁；boot 单元测试 31 项、集成测试 19 项（1 项既有条件跳过）无失败。 |
| `npm --prefix frontend test` | GREEN，退出码 0；11 个测试文件、54 项测试全部通过。 |
| `npm --prefix frontend run build` | GREEN，退出码 0；44 个模块转换成功，产物输出到 `oryxos-web/src/main/resources/static/console/`。 |
| `git diff --check` | GREEN，退出码 0。 |
| 最终 `.\mvnw.cmd clean verify` | GREEN，退出码 0；15 个 Reactor 模块全部 SUCCESS，总耗时 12:00。provider 36 项、web 43 项、boot 单元 32 项、boot 集成 20 项均无失败；真实天气和既有平台条件测试共跳过 2 项。 |
| 最终前端测试与构建 | GREEN；11 个测试文件、54 项测试通过，44 个模块构建成功。 |

### 课件存在性复核与边界

- 已存在并验证：`SessionManager.listRecent`、Session 摘要 DTO、SQLite V7 迁移、REST 列表、
  OpenAPI、控制台真实会话列表、确定性 Mock Provider、内核 Harness、HTTP 整机 Harness、
  示例配置及中英文 API 文档。
- Mock Provider 仅在显式配置 `type: MOCK` 时启用；真实 Provider 仍要求凭据，不做静默 fallback。
- Windows 下 Markdown Memory 的安全写入要求目标 Agent 已有标准 `MEMORY.md` 骨架；
  测试按 `oryxos init` 的正式工作区约定创建该文件。
- CLI Mock Harness 已通过正式 `BootCliRuntime` 精确核对 2 次模型、1 次工具和 4 条消息；
  HTTP E2E 与 CLI 均证明入口复用正式运行时。
- Provider 失败、Sandbox 拒绝和工具异常均有唯一失败审计；Provider 与工具异常还验证了
  同一服务/执行器实例在失败后可继续处理后续独立成功请求。
- `HumanTriggerFlowIT` 已作为显式按需集成入口加入默认构建；只有同时设置
  `-Doryxos.human-trigger.it=true` 和 `ALIYUN_API_KEY` 时才调用真实 Provider 与公网，
  本次无密钥门禁中按预期 skipped。
- 未使用真实模型 key 或公网执行天气调用；这部分没有被描述为已验证。

### Convergence

- 首轮 `speckit-converge` 核对 20 条 FR、7 条 SC、14 个验收场景、5 项计划决策和
  6 条宪章原则，发现 2 个 partial：失败后同实例恢复证据与最终 clean verify。
- 追加的 T034/T035 已完成：`ToolExecutorTest` 13 项、`ProviderFailureMappingTest`
  6 项聚焦测试 GREEN；最终后端、前端和 diff 门禁均 GREEN。
- 二次复核未发现 missing、partial、contradicts 或 unrequested 项，Spec-Kit 工件与实现已收敛。
