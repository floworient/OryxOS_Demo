# Implementation Plan: 人推 Agent 主流程串联

**Feature**: `012-human-trigger-main-flow` | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: 第 27 节课件、当前仓库实现、项目四份原始文档与 OryxOS Constitution。

## Summary

本功能不另建执行引擎，而是在现有 `AgentService -> PromptBuilder -> ProviderService -> ReActLoop -> ToolExecutor -> SessionManager` 链路上补齐可观测和可重复验收能力：新增显式配置的确定性 `mock` Provider；把现有无界 `SessionManager.list()` 演进为兼容的有界、可过滤摘要查询；公开 `GET /api/v1/sessions`；让 Vue 控制台从该接口发现并打开真实会话；最后用快速装配测试、整机 HTTP 测试和按需真实模型测试对会话、记忆及两类审计逐项对账。

实现遵循当前仓库而非课件旧快照：发布页面继续使用 `/console/`，工作区覆盖继续复用已经存在的 `oryxos.root -> oryxos.workspace` 解析链，不创建课件中已不存在的 `OryxOsRuntime`；现有 `SessionManager.list()` 保留兼容，新增有界查询契约供 Web 使用。

## Technical Context

**Language/Version**: Java 21；TypeScript 5.9；Vue 3.5

**Primary Dependencies**: Spring Boot 3.5.8、Spring AI 1.1.2、Spring MVC、Spring Data JPA、Flyway、SQLite JDBC、Vue 3、Vite 8、Vitest 5

**Storage**: 默认 SQLite；Session、`llm_calls`、`tool_invocations` 使用现有 JPA/Flyway；默认长期记忆使用工作区 `MEMORY.md`

**Testing**: JUnit 5、AssertJ、Spring Boot Test、MockMvc/TestRestTemplate、临时 SQLite、Vitest、Vue Test Utils；真实 Provider 测试通过环境条件显式启用

**Target Platform**: 单体可执行 JAR，Windows/Linux 私有化部署；浏览器访问同进程 `/console/`

**Project Type**: Maven 多模块后端 + 同仓 Vue 单页控制台

**Performance Goals**: 会话列表单次最多返回 100 条摘要；列表查询不反序列化完整消息历史；默认门禁不访问公网

**Constraints**: 不新增第三方依赖；Spring AI 自动工具执行保持关闭；Mock Provider 仅可显式选择；所有入口复用 `AgentService`；用户未提交的控制台和 Sandbox 改动必须保留

**Scale/Scope**: 1 个 core 契约演进、1 个 storage 查询投影与迁移、1 个 Provider 类型、1 个 REST 端点、1 个控制台最近会话视图、4 类主流程测试及文档

## Constitution Check

*GATE: Phase 0 前与 Phase 1 后均检查。*

| 原则 | 计划符合性 | 证据 |
| --- | --- | --- |
| I 单一运行时内核 | PASS | Mock 只替换 `ChatModel` 响应，CLI/REST/E2E 仍进入 `AgentService`、自实现 ReAct 和统一 Prompt/Tool 链路；不启用 Spring AI 自动执行 |
| II 工具调用统一受控 | PASS | `save_memory` 与失败场景均通过 `ToolExecutor`、可见性策略和 `ToolInvocationRecorder`；不增加旁路 |
| III 模块边界与依赖倒置 | PASS | Session 查询契约在 core，JPA/Flyway 在 storage，Provider 实现在 provider，HTTP/UI 适配在 web/frontend |
| IV 安全边界真实生效 | PASS | Mock 不含凭证；列表不返回消息载荷；Sandbox 拒绝场景验证无副作用且有审计 |
| V 持久化与显式契约 | PASS | Provider 名显式映射；消息计数经 SQLite Flyway 迁移持久化；两类审计 day one 对账 |
| VI 交付真实性 | PASS | 默认测试零密钥；真实模型测试单独条件启用；报告明确未验证外部项 |
| 开发工作流 | PASS | Spec-Kit 工件先行，实施任务要求 RED -> GREEN，并以 Wrapper `clean verify` 收口 |

**Phase 1 后复核**: PASS。API、数据模型和 UI 契约均未引入旁路执行、未扩张模块边界、未把 Mock 冒充真实模型；新增 `message_count` 由 Flyway 管理且只服务既定摘要契约。

## Project Structure

### Documentation (this feature)

```text
specs/012-human-trigger-main-flow/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── session-list-api.md
│   ├── mock-provider.md
│   └── console-sessions.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/io/oryxos/core/session/
├── SessionManager.java
└── SessionSummary.java

oryxos-storage/
├── src/main/java/io/oryxos/storage/{JpaSessionManager.java,entity/SessionEntity.java,repository/SessionRepository.java}
├── src/main/resources/db/migration/sqlite/V7__add_session_message_count.sql
└── src/test/java/io/oryxos/storage/{SessionSummaryQueryTest.java,SessionMigrationV7Test.java}

oryxos-provider/
├── src/main/java/io/oryxos/provider/{ProviderAutoConfiguration.java,ProviderDefinition.java,ProviderRegistry.java,ProviderStartupValidator.java}
├── src/main/java/io/oryxos/provider/mock/MockChatModel.java
└── src/test/java/io/oryxos/provider/mock/MockChatModelTest.java

oryxos-web/
├── src/main/java/io/oryxos/web/session/{SessionApiController.java,SessionSummaryView.java,SessionWebService.java}
└── src/test/java/io/oryxos/web/session/{SessionApiControllerTest.java,SessionWebServiceTest.java}

oryxos-boot/src/test/java/io/oryxos/boot/
├── MockProviderFlowTest.java
├── MockAgentE2ETest.java
└── HumanTriggerFlowIT.java

frontend/
├── src/api/{types.ts,oryxos.ts}
├── src/components/{SessionHistory.vue,AgentSidebar.vue}
├── src/state/useConsole.ts
└── tests/{sessionHistory.test.ts,useConsole.session.test.ts}

config/application.yml.example
README.md
website/{docs,zh/docs}/api.md
```

**Structure Decision**: 沿用现有 14 模块分层和 `frontend/` 工程。核心只增加查询契约；基础设施、Provider、Web DTO 与 UI 各留在责任模块。测试复用现有 `oryxos-boot` 的临时工作区/SQLite 装配方式，不新增测试模块。

## Design

### 1. Session 摘要与有界查询

- `SessionSummary` 扩展为 `id/profileName/channel/userId/status/createdAt/lastActiveAt/messageCount`，状态使用既有 `SessionStatus`。
- `SessionManager` 保留 `list()` 供现有 CLI 使用，并新增 `listRecent(int limit)` 与带可选 `SessionStatus` 的有界查询；默认兼容方法委托新契约，避免破坏 CLI 与已有测试。
- `SessionRepository` 使用 `Pageable` 提供全状态和按状态的稳定排序查询；Web 固定请求最多 100 条。
- 为避免列表读取或解析完整 `messages_json`，SQLite V7 增加非空 `message_count`，既有行按可识别 JSON 数组长度回填，新保存 Session 直接写 `session.messages().size()`。
- V7 迁移与实体测试证明旧数据保留、计数正确、损坏消息正文不会影响摘要查询。

### 2. REST 会话列表

- `SessionWebService.list(status)` 负责参数语义与 storage 错误映射，Controller 只接收 query、包装统一 `ApiResponse`。
- `GET /api/v1/sessions` 默认返回最近 100 条；`status=active` 只返回活动会话。课件只承诺 `active` 过滤，因此不扩大为任意状态枚举。
- `SessionSummaryView` 显式映射公开字段，不复用 JPA 实体，也不含消息正文、Tool 参数/结果或 prompt。
- OpenAPI、Controller、Web Service、Boot HTTP 测试和 API 文档使用同一字段名与错误码。

### 3. 显式 Mock Provider

- `ProviderDefinition.Type` 增加 `MOCK`；只有配置 `type: mock` 且 Agent 引用对应 provider name 时才创建 `MockChatModel`。
- `ProviderStartupValidator` 与 `ProviderRegistry.build` 对 MOCK 不解析 api-key、不要求 base URL；其他 Provider 验证保持不变。
- `MockChatModel` 实现当前 Spring AI `ChatModel`：没有匹配工具结果时从最后用户消息提取“记住：”后的内容，发出唯一 `save_memory` 工具调用；存在同一调用的 ToolResponse 时返回固定最终答复和非零 token usage；缺少可见工具、输入不匹配或工具结果不匹配时明确失败。
- 模型行为从 Prompt 历史判断，不使用跨会话可变全局计数器，因此天然按会话隔离、支持并行且测试顺序无关。
- Mock 不做真实 Provider fallback，也不在未配置时自动注册。

### 4. 控制台最近会话

- 保留现有 `/console/` 对话工作台和 Agent 选择；不恢复课件旧 `/admin/` 路径。
- 在会话区域侧栏增加 `SessionHistory`，独立调用列表 API，展示最近会话的 Agent、渠道、状态、消息数与最后活跃时间。
- 选择 Web 会话时复用 `fetchSession` 将详情装入对应 Agent 对话状态；非 Web 或归档会话以只读方式打开，不允许从控制台续写。
- 列表具备 loading/empty/error/retry，刷新对话后同步刷新列表；浏览器本地 sessionId 继续用于当前对话恢复，但不再被当作系统会话列表真相源。

### 5. 主流程 Harness

- `MockChatModelTest` 固定脚本、参数、token、错误输入和工具结果匹配。
- `MockProviderFlowTest` 快速手工装配正式 ReAct/Tool/Memory/Session/审计，断言 2 次 LLM、1 次 `save_memory`、4 条历史和记忆落盘。
- `MockAgentE2ETest` 启动真实随机 HTTP 端口、临时 Agent/SQLite/工作区，通过创建 Session、发消息、列表/详情/记忆/工具查询和数据库审计完成整机对账。
- `HumanTriggerFlowIT` 标记 `integration` 并以环境条件启用，使用真实 Provider/网络验证天气场景；默认无 key 时明确 skipped。
- 失败审计复用并扩展现有 Provider/Tool/Sandbox 测试；只有跨模块缺口才新增 Boot 场景，避免重复同义测试。

## Dependency and Migration Impact

- 不新增 Maven/NPM 第三方依赖。
- 新增 SQLite V7 migration；不使用 `ddl-auto=update`。
- `SessionSummary` 构造签名变化会同步更新 `BootCliQueryTest` 等少量调用点。
- `ProviderDefinition.Type` 新枚举值要求所有 switch 显式处理；其余 Provider 配置向后兼容。
- 前端改动与当前未提交的 011 控制台管理工作重叠，实施时必须在其现状上增量修改，不覆盖或回滚。

## Verification Strategy

1. 每个行为先运行聚焦测试并记录预期 RED，再写最小实现到 GREEN。
2. 后端聚焦运行 provider、storage、web、boot 测试；前端执行 `npm test` 与 `npm run build`。
3. 运行 `./mvnw.cmd clean verify`，确认 Mock 全链路进入默认门禁、真实外部测试缺少 key 时只显示为跳过。
4. 运行 `git diff --check`，检查示例配置与文档没有真实密钥、Webhook 或内部地址。
5. 按 [quickstart.md](quickstart.md) 进行无密钥人工对账；真实模型步骤仅在用户自行提供本地密钥后执行。

## Complexity Tracking

无宪章例外。本功能新增的 V7 计数字段是为满足摘要消息数且保持列表不解码完整历史的最小持久化代价；未新增模块、缓存、审计查询 API 或另一套执行链路。

