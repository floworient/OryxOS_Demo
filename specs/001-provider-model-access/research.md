# Phase 0 Research: 核心契约与 Provider 接入

**Date**: 2026-10-22 | **Plan**: [plan.md](./plan.md)

本阶段共 11 项技术未知，全部已解决，无遗留 `NEEDS CLARIFICATION`。

---

## R1. Spring AI 版本如何纳入依赖管理

**Decision**: 在根 `pom.xml` 的 `dependencyManagement` 中补一条 `org.springframework.ai:spring-ai-bom:1.1.2` 的 `import`，与 `spring-ai-alibaba-bom:1.1.2.2` 并列。

**Rationale**: 仓库内 `.m2/repository` 显示 Spring AI 核心模块版本为 `1.1.2`，由 `spring-ai-alibaba-starter-dashscope` 传递引入；但 `spring-ai-alibaba-bom-1.1.2.2.pom` 只有 96 行、未导入 `spring-ai-bom`，因此 Spring AI 的版本当前是「传递依赖的偶然结果」。本 feature 需要额外引入 OpenAI 兼容实现，若不显式导入 BOM，会出现两个来源的 Spring AI 版本漂移。显式导入后由 BOM 统一管理，符合宪章「依赖版本必须验证兼容性后统一管理」。

**Alternatives considered**:
- 在每个模块里手写 `spring-ai-openai:1.1.2` 的版本号 —— 版本散落，后续升级容易漏改。
- 不引入 OpenAI 兼容实现，只做 DashScope —— 违反 FR-003 的第二类接入要求。

**Verification step**: `mvn dependency:tree -pl oryxos-provider` 应只出现单一 Spring AI 版本。

---

## R2. OpenAI 兼容端点的依赖坐标

**Decision**: 使用 `org.springframework.ai:spring-ai-openai`（核心 SDK，非 starter），由 R1 的 BOM 定版，并在代码中手工构造 `OpenAiChatModel`，不引入自动配置。

**Rationale**: Spring AI 1.x 把自动配置拆成 `spring-ai-starter-model-openai`；本项目的 `application.yaml` 已经显式排除了 DashScope 的一系列自动配置，并明确要求「Provider 名称到 `ChatModel` 的显式映射，不依赖扫描同类型 Bean」。引入 starter 会把自动配置 Bean 重新带进容器，与显式映射原则冲突。只取核心 SDK 即可获得 `OpenAiApi` / `OpenAiChatModel`，由 `oryxos-provider` 自己构造。

**Alternatives considered**:
- `spring-ai-starter-model-openai` + 排除其自动配置 —— 多一层排除维护，且容易在升级时漏排除。
- 直接用 HTTP 客户端自己实现 OpenAI 协议 —— 重复造轮子，违反技术方案的「用 Spring AI 做协议转换」。

**Local API evidence**: 已对本地 `spring-ai-openai-1.1.2.jar` 执行 `javap`：`OpenAiApi.Builder` 提供 `baseUrl(String)`、`apiKey(String)`、`build()`；`OpenAiChatModel.Builder` 提供 `openAiApi(OpenAiApi)`、`defaultOptions(OpenAiChatOptions)`、`build()`。实现任务只能使用这些已核实入口。

---

## R3. Flyway 11 对 SQLite 的支持方式

**Decision**: 只需 `org.flywaydb:flyway-core`（Boot 管理的 11.7.2），**不需要**任何额外的 `flyway-database-*` 模块。

**Rationale**: Flyway 10 起把数据库支持模块化，但官方迁移说明中明确将 H2、SQLite 这类内存/文件数据库保留在 `flyway-core` 内；独立模块列表里没有也不会有 `flyway-database-sqlite`。仓库内 `.m2/repository/org/flywaydb` 下也只有 `flyway-core`，与此结论一致。

**Alternatives considered**:
- 引入 `flyway-database-sqlite` —— 该坐标不存在，会直接导致依赖解析失败。
- 改用 Liquibase —— 技术方案已定 Flyway，不在本阶段更换。

**已知限制（需在迁移脚本中规避）**: SQLite 不支持并发迁移、不支持多 schema、迁移脚本内不支持嵌套事务。

---

## R4. Hibernate 如何支持 SQLite 方言

**Decision**: 在 `oryxos-storage` 增加 `org.hibernate.orm:hibernate-community-dialects`（版本由 Boot 管理的 Hibernate 6.6.x），配置 `spring.jpa.properties.hibernate.dialect=org.hibernate.community.dialect.SQLiteDialect`。

**Rationale**: Hibernate 6.x 的 SQLite 方言不在 `hibernate-core` 中，而在社区方言包。`spring-boot-dependencies:3.5.8` 已把 `org.hibernate.orm:hibernate-community-dialects` 纳入 `dependencyManagement`（`${hibernate.version}` = 6.6.36.Final），因此**不需要写版本号**，与 `hibernate-core` 自动对齐。

**Alternatives considered**:
- 自己写一个 `Dialect` 子类 —— 工作量与维护成本都高于引入官方社区包。
- 不用 JPA，纯 JDBC —— 违反技术方案「SQLite + Spring Data JPA」的明确决策。

**Verification step**: 启动时 `ddl-auto=validate` 能通过，说明方言与迁移脚本一致。

---

## R5. 如何在不调用真实模型的情况下测试 Provider 行为

**Decision**: 在 `oryxos-provider` 的测试中实现一个 `StubChatModel implements ChatModel`，按测试要求返回固定 `ChatResponse`（纯文本 / 工具调用意图 / 认证失败 / 超时）；真实 DashScope 调用放在 `oryxos-boot` 的 `ProviderSmokeIT`，经 boot 装配 Provider、storage 与 Flyway。该 IT 标记 `@Tag("integration")`，且只有显式设置 `-Doryxos.provider.smoke=true` 并同时存在 `ALIYUN_API_KEY` 时才允许联网；默认 `test`/`verify` 即使发现该测试也不得发出真实请求。

**Rationale**: `ProviderService` 依赖的是 Spring AI 的 `ChatModel` 接口，注册表接收的是构造好的 `ChatModel` 实例，因此桩可以直接注入映射表，无需 mock 框架深挖内部结构。真实调用需要同时观察 `llm_calls`，放到已依赖 provider/storage 的 boot 模块可避免 provider 反向依赖 storage。显式开关与凭据双门禁保证 CI 默认无网络，也满足 AGENTS.md「缺少密钥时明确报告未验证项，不用模拟结果冒充端到端通过」。

**Alternatives considered**:
- Mockito mock `ChatModel` —— 可行但可读性差，且难以表达「多轮响应序列」。
- 用本地 Ollama 做替身 —— 需要额外运行环境，且验证的不是 DashScope 的真实协议。

---

## R6. Agent 定义如何加载并判断「哪些供应商被引用」

**Decision**: 启动时由 `AgentLoader` 扫描 `${oryxos.workspace}/agents/*/AGENT.md`，用 SnakeYAML 2.4 的 `SafeConstructor(LoaderOptions)` 解析 frontmatter，派生当前技术方案 8.2 的完整 Profile 后注册到 `ProfileRegistry`；随后 `ProviderStartupValidator` 逐个核对 Provider 引用：未知 Provider 的 Profile 从 Registry 移除并记录 Agent 级错误，其他 Agent 继续；已知且被引用 Provider 的凭据缺失则启动失败。工作区不存在或没有 Agent 目录时返回空结果，不阻断启动。

**Rationale**: 完整 Profile 与加载隔离是第 16 节明确交付物，也是 FR-013/FR-014。`name` 必须等于目录名；单个 Agent 失败只进入加载错误集合，不注册且不阻断其他 Agent。`skills` 与 `notify_channels` 是已废弃字段，出现时该 Agent 加载失败。Tool 是否注册、Bootstrap 是否存在等依赖后续模块的校验留给 002，但字段本身必须在本节完整派生。

**Alternatives considered**:
- 在 US-1 校验全部供应商凭据 —— 与 spec 的边界案例「未被引用的供应商不阻止启动」直接矛盾，已在澄清中被否决。
- 只派生 `name`/`provider.name`，其余字段推迟到 002 —— 不满足第 16 节课件和当前技术方案 8.2，已否决。

**Local API evidence**: Spring Boot 3.5.8 管理 SnakeYAML 2.4，本地 JAR 已存在；`SafeConstructor(LoaderOptions)`、`LoaderOptions.setAllowDuplicateKeys(false)`、`setNestingDepthLimit(int)`、`setCodePointLimit(int)` 均已通过 `javap` 核实。内部限值显式锁定为 SnakeYAML 2.4 默认安全上限：aliases=50、nesting=50、code points=3,145,728，并额外禁止重复键。

---

## R7. OryxTool 如何转换成 Spring AI Tool Schema 且不执行

**Decision**: `ToolSchemaAdapter` 把调用方已经选出的 `OryxTool` 映射为 Spring AI `ToolDefinition`，再包装成 **schema-only `ToolCallback`**：`getToolDefinition()` 返回 Schema，`call(...)` 固定抛出禁止执行异常，绝不委托 `OryxTool.execute`。调用方在构造 `Prompt` 前把 callbacks 放入 options；`ProviderService` 保留这些 callbacks并强制 `internalToolExecutionEnabled=false`。本节不根据 `Profile.tools` 解析实例，不提前实现 002 的 `ToolRegistry`。

**Rationale**: Spring AI 1.1.2 的通用 `ToolCallingChatOptions` 通过 `ToolCallback` 取得 Schema，没有独立的 `ToolDefinition` 列表入口。因此使用拒绝执行的 callback 提供 Schema，并用 `internalToolExecutionEnabled=false` 构成双门禁；Provider 即使发生错误调用也只能失败，不能触达真实工具。

**Local API evidence**: 本地 `spring-ai-model-1.1.2.jar` 已核实 `ToolDefinition.name/description/inputSchema`、`DefaultToolDefinition.Builder.name/description/inputSchema/build`、`ToolCallback.getToolDefinition/call`、`ToolCallingChatOptions.Builder.toolCallbacks/internalToolExecutionEnabled`。Schema 校验使用同一 JAR 已核实的 `org.springframework.ai.util.json.JsonParser`，不新增 Jackson 直接依赖。`ChatModel.call(Prompt)` 也是已核实的抽象调用入口。

**Alternatives considered**:
- 让 callback 的 `call` 委托 `OryxTool.execute` —— 会建立绕过 `ToolExecutor` 的执行通道，禁止。
- 自己拼各家 Provider 的 tools JSON —— 重复实现协议转换，违反技术方案。

---

## R8. ProviderService 的稳定调用契约

**Decision**: core 暴露课程规定的 `chat(String sessionId, Profile profile, Prompt prompt)`；`Prompt` 使用 Spring AI 1.1.2 的稳定类型，工具定义与 `internalToolExecutionEnabled=false` 作为请求级 options 注入。服务按 `profile.provider().name()` 精确选取 `ChatModel`，并把模型响应映射为 OryxOS 响应对象。

**Rationale**: 该签名直接表达「会话 + 当前 Agent 配置 + 本轮输入」，后续 ReAct 无需知道底层 Provider；也避免另造与 `Prompt` 重复的消息容器。core 因此显式依赖 `spring-ai-model`，但仍不依赖具体 Provider 实现。

**Alternatives considered**:
- 保留旧设计的 `call(ProviderCallRequest)` —— 与第 16 节固定方法契约不一致，已否决。
- core 自建一套完整 Prompt/Message 类型 —— 会重复 Spring AI 已稳定提供的抽象，增加转换层。

**Local API evidence**: 本地 `Prompt` 已核实 `getInstructions()`、`getOptions()` 与 `Prompt(List<Message>, ChatOptions)`；`ChatOptions.copy()` 与 `ToolCallingChatOptions` 的 callbacks/internal-execution setter 已核实。实现先复制 Prompt options，再覆盖本次 Profile 的 model/temperature 和禁用标志，不修改共享对象。

---

## R9. 审计持久化失败策略

**Decision**: `LlmCallRecorder.record` 写入失败必须向上传播为 `AUDIT_PERSISTENCE_FAILED`；无论模型原始结果是成功还是失败，Provider 调用都不得被报告为成功。保留原始模型异常为 cause/suppressed 信息，但异常文本必须脱敏。

**Rationale**: 用户在 clarify 阶段选择失败关闭；这与 FR-010 和企业可审计目标一致。写入重试不在本节计划中，避免在未定义幂等键时产生重复记录。

**Alternatives considered**:
- 只写 ERROR 日志并返回模型结果 —— 会产生不可查询的审计缺口，用户已否决。
- 自动重试写入 —— plan 未定义幂等键与退避策略，超出本节交付物。

---

## R10. 如何保证 Provider 调用不会无限等待

**Decision**: DashScope 与 OpenAI 兼容工厂共用内部常量 `Duration.ofSeconds(60)`，通过 `SimpleClientHttpRequestFactory` 同时设置 connect/read timeout，再交给已核实的 `RestClient.Builder.requestFactory(...)`。该值不暴露为配置键；测试通过包内可见的工厂重载注入短 Duration，并用 JDK 回环服务验证真实超时映射。

**Rationale**: 课件要求处理超时但未定义外部配置；固定内部上限满足不无限等待，同时避免新增 plan 外配置键。测试注入只用于缩短自动化测试，不成为公共契约。

**Local API evidence**: 本地 `spring-web-6.2.14.jar` 已通过 `javap` 核实 `SimpleClientHttpRequestFactory.setConnectTimeout(Duration)`、`setReadTimeout(Duration)` 与 `RestClient.Builder.requestFactory(ClientHttpRequestFactory)`；DashScope/OpenAI API builder 的 `restClientBuilder(...)` 已核实。

---

## R11. OpenAI 兼容协议如何在无外部服务时验收

**Decision**: 测试使用 JDK 自带 `com.sun.net.httpserver.HttpServer` 启动仅绑定回环地址的临时服务，断言请求路径、Authorization、模型与消息 JSON，并返回最小 OpenAI Chat Completions 响应；通过真实 `OpenAiChatModel.call` 验证协议闭环，不新增测试依赖、不访问公网。

**Rationale**: 只构造 builder 不能证明协议可用；回环协议测试覆盖序列化、HTTP 调用和响应映射，同时符合默认测试无外网要求。

**Alternatives considered**:
- 只断言 builder 字段 —— 无法证明 FR-003 的第二类接入。
- 引入 WireMock/MockWebServer —— 属于 plan 外第三方依赖，没有必要。

---

## 汇总

| 编号 | 未知项 | 结论 |
| --- | --- | --- |
| R1 | Spring AI 版本管理 | 根 POM 导入 `spring-ai-bom:1.1.2` |
| R2 | OpenAI 兼容依赖 | `spring-ai-openai` 核心 SDK，手工构造 |
| R3 | Flyway SQLite 支持 | 仅 `flyway-core`，无需额外模块 |
| R4 | Hibernate SQLite 方言 | `hibernate-community-dialects` |
| R5 | 无密钥测试 | 实现 `ChatModel` 桩 + boot 层 `ProviderSmokeIT` 的显式开关/凭据双门禁 |
| R6 | Agent/Profile 加载 | 完整派生、单 Agent 失败隔离、安全 YAML 解析 |
| R7 | Tool Schema | schema-only callback 固定拒绝执行 + 显式关闭内部执行 |
| R8 | ProviderService 契约 | `chat(sessionId, Profile, Prompt)` |
| R9 | 审计写入失败 | 失败关闭，不返回成功结果 |
| R10 | HTTP 超时 | 内部固定 60 秒，回环短超时测试，不新增配置键 |
| R11 | OpenAI 兼容验证 | JDK HttpServer 回环协议测试，无外网/新依赖 |

无遗留 `NEEDS CLARIFICATION`。

## 依赖可达性实测（已执行）

用 `maven-dependency-plugin:3.8.1:get` 对三个新增坐标实际解析，全部下载成功，确认本地仓库已存在：

| 坐标 | 结果 |
| --- | --- |
| `org.springframework.ai:spring-ai-bom:1.1.2` | 已解析 |
| `org.springframework.ai:spring-ai-openai:1.1.2` | 已解析；jar 内含 `org/springframework/ai/openai/OpenAiChatModel` 与 `OpenAiApi` |
| `org.hibernate.orm:hibernate-community-dialects:6.6.36.Final` | 已解析，与 Boot 管理的 `hibernate.version` 一致 |
| `org.yaml:snakeyaml:2.4` | 已解析；安全构造器与资源限制 API 已通过 `javap` 核实 |
| `org.springframework.ai:spring-ai-model:1.1.2` | 已由现有 DashScope starter 解析；`ChatModel`、`Prompt`、`ToolDefinition`、`ToolCallingChatOptions` API 已通过 `javap` 核实 |

`flyway-core:11.7.2` 已在本地仓库中，并核实 JAR 内含 `org.flywaydb.core.internal.database.sqlite` 实现，无需新增数据库模块。DashScope 本地 JAR 已核实 `DashScopeApi.Builder` 与 `DashScopeChatModel.Builder` 的构造入口。
