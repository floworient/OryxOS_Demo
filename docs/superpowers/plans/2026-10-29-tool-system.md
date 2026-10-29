# Tool System Implementation Plan

> For agentic workers: Execute each task in order with strict RED-GREEN-REFACTOR. Do not add production behavior before observing the designated test fail for the expected reason.

**Goal:** 在保留现有 String JSON `OryxTool` 契约的基础上，完整交付统一 Registry、强类型 Sandbox、11 个新增内置工具、Java `@Tool` 与 stdio/SSE MCP 接入，并确保可见性、审计和安全边界一致。

**Architecture:** core 只保存共享契约和调用编排，oryxos-tool 保存 Registry、Sandbox、内置工具与 adapter 实现，oryxos-channel-cli 实现交互，oryxos-boot 显式装配。PromptBuilder 和 ToolExecutor 必须读取同一 `ToolRegistry.visibleTo(Profile)` 结果；所有真实调用仍只经过 ToolExecutor。

**Tech Stack:** Java 21、Spring Boot 3.5.8、Spring AI 1.1.2、Spring AI MCP 1.1.2 / MCP SDK 0.17.0、Maven、JUnit 5、AssertJ、Mockito。

**Spec:** `specs/005-tool-system/spec.md`

## Global Constraints

- 禁止启用 Spring AI 自动工具执行。
- 不改变 `OryxTool.execute(String)` 和 `ToolResult` 的兼容契约。
- 每个工具都必须经 Registry 可见性、ToolExecutor、Sandbox（适用时）和最终审计。
- 文件相对路径以当前 Agent 目录为基准；绑定 Skill 只读；未绑定 Skill 不可见。
- Shell 只接受配置 alias 与 argv；HTTP 模型输入禁止 auth/cookie/host 等凭证头。
- MCP 配置或安全错误 fail-fast；已授权 server 的纯连接失败 WARN 并跳过。
- 不修改用户现有 `.claude/settings.local.json`。

## Review Focus

- Prompt 展示集合与执行允许集合完全一致。
- 路径真实边界、写入原子性、Shell 子进程回收、HTTP 零请求拒绝。
- Java/MCP 只做 adapter，不建立绕过 ToolExecutor 的调用通道。
- 交互上下文不跨线程/Session 泄漏；秘密不进入日志、异常或审计。

---

### Task 1: 统一 ToolRegistry 与核心调用链

**Files:**
- Create: `oryxos-core/src/main/java/io/oryxos/core/tool/ToolRegistry.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/tool/ToolRegistration.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/tool/ToolSource.java`
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/registry/DefaultToolRegistry.java`
- Test: `oryxos-tool/src/test/java/io/oryxos/tool/registry/DefaultToolRegistryTest.java`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/context/PromptBuilder.java`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/tool/ToolExecutor.java`

**Interfaces:** `register`, `find`, `registrations`, `visibleTo(Profile)`；重复名称必须拒绝；BUILTIN/JAVA 由 `Profile.tools`，MCP 由 `Profile.mcpServers` 控制。

1. 先写 Registry 参数化测试，以及 PromptBuilder/ToolExecutor 集合一致测试。
2. 运行 `mvn -pl oryxos-tool -am -Dtest=DefaultToolRegistryTest test`，确认因类型/实现缺失而 RED。
3. 添加最小契约与实现，迁移 PromptBuilder、ToolExecutor 和 boot 调用点。
4. 再运行聚焦测试与 core/tool 模块测试，确认 GREEN 后重构重复选择逻辑。
5. Commit: `feat(tool): add unified tool registry`

### Task 2: Skill 绑定解析、交互上下文与强类型 Sandbox

**Files:**
- Create: `oryxos-core/src/main/java/io/oryxos/core/context/SkillBinding.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/context/SkillBindingResolver.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/interaction/UserInteraction.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/interaction/UserInteractionContext.java`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/context/ContextLoader.java`
- Modify/Create: `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/*`
- Test: `oryxos-core/src/test/java/io/oryxos/core/context/SkillBindingResolverTest.java`
- Test: `oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java`

1. 写 `.link` 合法、绝对路径、断链、未绑定、原生链接逃逸和只读边界 RED 测试。
2. 写 HTTP/File/Shell typed action 拒绝测试，以及交互上下文捕获/清理测试。
3. 运行聚焦测试，确认旧 Sandbox 无法满足新接口。
4. 抽取 resolver、实现 sealed action/重载 `enforce`，迁移 notify，保持 ContextLoader 逐轮读取无缓存。
5. Commit: `refactor(tool): enforce typed sandbox boundaries`

### Task 3: 六个文件工具

**Files:**
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/builtin/FileTools.java`
- Create/Test: `oryxos-tool/src/test/java/io/oryxos/tool/builtin/FileToolsTest.java`

1. 分别为 `read_file`、`write_file`、`list_dir`、`edit_file`、`grep`、`glob` 写成功和拒绝 RED 测试。
2. 特别断言失败写入不改变原文件、绑定 Skill 不可写、扫描达到 10k 文件/32MiB/200 hits 时停止。
3. 实现 JSON schema、输入 256KiB、单文件 1MiB、输出 256KiB 及确定性排序。
4. 运行聚焦和 oryxos-tool 全模块测试。
5. Commit: `feat(tool): add bounded file tools`

### Task 4: Shell 与 HTTP 工具

**Files:**
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/builtin/ShellTools.java`
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/builtin/HttpTools.java`
- Test: `oryxos-tool/src/test/java/io/oryxos/tool/builtin/ShellToolsTest.java`
- Test: `oryxos-tool/src/test/java/io/oryxos/tool/builtin/HttpToolsTest.java`

1. Shell RED：未知 alias、元字符原样传递、stdout/stderr 并发排水、默认 30 秒/最大 300 秒、超时回收 descendants。
2. HTTP RED：回环 GET/POST 单次请求；拒绝敏感 header、非白名单、重定向、超限/超时并验证拒绝时零请求。
3. 实现 alias→绝对 executable、`ProcessBuilder(List<String>)`、双流有界读取和进程树终止。
4. 实现不跟随重定向且不自动重试的 JDK HttpClient 调用，connect 10 秒/total 60 秒/body 1MiB。
5. Commit: `feat(tool): add sandboxed shell and http tools`

### Task 5: Java @Tool 与 MCP adapter

**Files:**
- Modify: `oryxos-tool/pom.xml`
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/adapter/*`
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/mcp/*`
- Test: `oryxos-tool/src/test/java/io/oryxos/tool/adapter/AnnotatedToolAdapterTest.java`
- Test: `oryxos-tool/src/test/java/io/oryxos/tool/mcp/*Test.java`

1. 用真实 annotated fixture 写 schema/call/异常 RED 测试。
2. 用 fake `McpConnection` 写 list/call/close、单次副作用调用和来源可见性 RED 测试。
3. 写严格 YAML、stdio/SSE、`${ENV_VAR}`、缺失/空白变量、秘密脱敏 RED 测试。
4. 通过 `MethodToolCallbackProvider`/`ToolCallback` 实现 Java adapter；通过同步 MCP SDK 实现 factory，adapter 仍只返回 OryxTool。
5. 运行 tool 模块测试并确认没有自动执行配置。
6. Commit: `feat(tool): add java and mcp tool adapters`

### Task 6: ask_user、web_search 与 CLI 传播

**Files:**
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/builtin/InteractionTools.java`
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/builtin/WebSearchTools.java`
- Create: `oryxos-tool/src/main/java/io/oryxos/tool/search/*`
- Create: `oryxos-channel-cli/src/main/java/io/oryxos/channel/cli/ConsoleUserInteraction.java`
- Modify: `oryxos-channel-cli/src/main/java/io/oryxos/channel/cli/CliChannel.java`
- Test: corresponding `*Test.java`

1. 写 CLI 单问单答、无人值守 1 秒内失败、两 Session 不串线、finally 清理 RED 测试。
2. 写 fake SearchProvider 的结果数量/字段/64KiB 输出 RED 测试。
3. 实现 ask_user 与 ToolExecutor 虚拟线程显式传播；非 CLI 默认 unsupported。
4. 实现 DuckDuckGo Instant Answer provider，并明确它不是完整网页 SERP。
5. Commit: `feat(tool): add interaction and web search tools`

### Task 7: Boot 显式装配与端到端一致性

**Files:**
- Create: `oryxos-boot/src/main/java/io/oryxos/boot/ToolConfiguration.java`
- Create: `oryxos-boot/src/main/java/io/oryxos/boot/ToolProperties.java`
- Modify: `oryxos-boot/src/main/java/io/oryxos/boot/ReActConfiguration.java`
- Modify: `oryxos-boot/src/main/java/io/oryxos/boot/BootCliRuntime.java`
- Modify: `oryxos-boot/src/main/resources/application.yaml`
- Test: `oryxos-boot/src/test/java/io/oryxos/boot/ToolConfigurationTest.java`
- Test: `oryxos-boot/src/test/java/io/oryxos/boot/ToolRegistryAuditIT.java`

1. 写启动上下文、稳定 tool list、Profile 可见性和一次 ToolExecutor 调用产生一次审计记录 RED 测试。
2. 显式注册既有 notify 与 11 个新增工具、Java tools、已授权 MCP tools；默认配置 deny-by-default。
3. 验证 CLI、HTTP、调度仍复用 AgentService/ReAct/ToolExecutor。
4. Commit: `feat(boot): wire unified tool system`

### Task 8: 文档、验收和质量门禁

**Files:**
- Modify: `README.md`
- Modify: `website/docs/tool.md`
- Modify: `website/zh/docs/tool.md`
- Modify: `website/docs/profile.md`
- Modify: `website/zh/docs/profile.md`
- Modify: `specs/005-tool-system/tasks.md`

1. 对照实现更新中英文文档，不宣称 Memory、Tool Policy、Runner、管理 API、完整 SERP 已完成。
2. 运行 `mvn test`、`mvn clean verify`、quickstart 自动部分、`git diff --check` 和 canary/secret 扫描。
3. 对照 FR-001～FR-037、SC-001～SC-010 勾选任务并记录未能真实外联验证项。
4. 请求独立代码审查，修复一轮高置信问题后重新执行完整门禁。
5. Commit: `docs(tool): document complete tool delivery`
