# Tasks: Tool 体系完整交付

**Input**: `spec.md`、`plan.md`、`research.md`、`data-model.md`、`contracts/`

**Tests**: 所有实现任务严格遵循 RED → GREEN → REFACTOR；每个阶段先添加失败测试并确认失败原因，再添加生产代码。

## Phase 1: 统一注册与调用链

- [x] T001 在 `oryxos-core/src/main/java/io/oryxos/core/tool/` 定义 `ToolRegistry`、`ToolRegistration`、`ToolSource`
- [x] T002 [P] 在 `oryxos-tool/src/test/java/io/oryxos/tool/registry/DefaultToolRegistryTest.java` 覆盖重复注册、稳定顺序和三来源可见性
- [x] T003 在 `oryxos-tool/src/main/java/io/oryxos/tool/registry/DefaultToolRegistry.java` 实现注册与可见性规则
- [x] T004 在 `PromptBuilderTest`、`ToolExecutorTest` 添加同源可见性与拒绝执行测试并迁移实现
- [x] T005 在 core 增加 `UserInteraction`、`UserInteractionContext`，验证虚拟线程传播与 finally 清理

## Phase 2: Skill 边界与强类型 Sandbox

- [x] T006 从 `ContextLoader` 抽取 `SkillBindingResolver`、`SkillBinding`，保持 `.link` 每轮解析语义
- [x] T007 在 `oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java` 覆盖 Agent 根、绑定 Skill 只读、未绑定/链接逃逸、Shell/HTTP 拒绝
- [x] T008 扩展 `Sandbox` 为文件、Shell、HTTP 强类型 action，并迁移 `NotifyTools`

## Phase 3: 文件工具

- [x] T009 [P] 为 `read_file`、`write_file`、`list_dir`、`edit_file`、`grep`、`glob` 编写 RED 测试
- [x] T010 实现 `FileTools`：Agent 目录默认读写、绑定 Skill 只读、结果与扫描有界、写失败不破坏原文件
- [x] T011 验证目录穿越、原生链接逃逸、未绑定 Skill、超限和无匹配等边界行为

## Phase 4: Shell 与 HTTP

- [x] T012 [P] 编写 `ShellToolsTest`，覆盖 alias、argv 直传、双流排水、超时及进程树终止
- [x] T013 实现 `ShellTools` 与命令白名单配置，模型输入不得直接选择 executable
- [x] T014 [P] 编写 `HttpToolsTest`，覆盖 GET/POST、敏感 header、白名单、重定向、超时和 body 上限
- [x] T015 实现 `HttpTools`，拒绝自动重定向与自动重试，确保拒绝用例零网络请求

## Phase 5: Java Tool 与 MCP

- [x] T016 [P] 编写 `AnnotatedToolAdapterTest`，覆盖 Spring AI `@Tool` schema、调用和异常映射
- [x] T017 实现 `AnnotatedToolDiscovery`、`AnnotatedToolAdapter` 并注册为 `JAVA`
- [x] T018 [P] 编写 `McpConfigLoaderTest`，覆盖严格 schema、stdio/SSE、环境占位、秘密脱敏和 fail-fast
- [x] T019 [P] 编写 fake connection 驱动的 `McpClientServiceTest`、`McpToolAdapterTest`
- [x] T020 实现 MCP 配置、同步客户端工厂、连接生命周期和 adapter；配置/安全错误失败启动，纯连接失败 WARN 跳过

## Phase 6: 交互、搜索与装配

- [x] T021 [P] 编写 `InteractionToolsTest` 与 `CliChannelTest`，覆盖 CLI 问答、无人值守快速失败和并发隔离
- [x] T022 实现 `ask_user` 与 CLI `ConsoleUserInteraction`，在每次调用 finally 清理上下文
- [x] T023 [P] 编写 `WebSearchToolsTest`，使用 fake provider 覆盖查询、数量和输出上限
- [x] T024 实现 `SearchProvider`、`WebSearchTools` 与 `DuckDuckGoInstantAnswerSearchProvider`
- [ ] T025 在 boot 显式装配 Registry、Sandbox、11 个新增工具、Java/MCP 工具；tool list 改读 Registry

## Phase 7: 集成、文档与门禁

- [ ] T026 添加 Prompt/Executor 可见性一致、ToolExecutor 单次执行与审计、Boot 启动集成测试
- [ ] T027 更新 `README.md` 与中英文网站 Tool/Profile 文档，仅声明实际能力和限制
- [ ] T028 执行聚焦测试、`mvn test`、`mvn clean verify`、`git diff --check` 与敏感信息扫描
- [ ] T029 对照 FR-001～FR-037、SC-001～SC-010 和 quickstart 逐项记录验证证据

## Dependencies

- Phase 1 → Phase 2 → Phase 3/4；Phase 1 → Phase 5；Phase 1 → Phase 6。
- Phase 3、Phase 4、Phase 5 在共享契约稳定后可独立推进。
- Phase 7 依赖所有前置 Phase 完成。
- 标记 `[P]` 的测试可并行编写，但生产代码合并前仍须逐项确认 RED 证据。
