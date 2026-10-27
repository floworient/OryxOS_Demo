# Research: 第 17 节 ReAct

日期：2026-10-26。仅查本地源码、POM 和 `.m2`，未联网、未新增依赖。

## 1. 课程与架构

- Decision: 复用 `002-react-loop-cli`，重写范围为本节内核，后续 CLI/HTTP 不提前实现。
- Rationale: 唯一课件 7 页的交付锚点为 ReActLoop、PromptBuilder、ToolExecutor、AgentService、ProfileContext、ContextLoader 和工具审计；Session 是其入口和保存所需支撑。
- Alternatives considered: 新增重复 feature 会使 US-2 来源分裂；按旧整份 US-2 实现会越过课程范围。
- 已完整提取课件 7 页，核对 TechnicalSolution 第 4、8.3、9.2、10 章、oryxos 项目定义、DemandAnalysis 第 8/10 章、AiProgrammingGuide US-2、IndustryResearch 的定位及宪章。
- 用户已同意以 Flyway 和相对软连接/元数据预载取代旧课件相应内容。

## 2. 本地 API 核实

由研究子任务使用 `D:/IDE/JDK/JDK21/bin/javap.exe` 检查 `.m2/repository/org/springframework/ai/spring-ai-model/1.1.2/spring-ai-model-1.1.2.jar`。

| API | 本地可用签名 |
| --- | --- |
| AssistantMessage | builder().content(String).toolCalls(List).build() |
| AssistantMessage.ToolCall | (String id, String type, String name, String arguments)，type=function |
| ToolResponseMessage | builder().responses(List).build() |
| ToolResponseMessage.ToolResponse | (String id, String name, String responseData) |
| Prompt | (List<Message>, ChatOptions) |
| ToolCallingChatOptions | builder().toolCallbacks(List<ToolCallback>).internalToolExecutionEnabled(Boolean).build() |

Decision: boot 注入 `new ToolSchemaAdapter()::adapt` 到 core 的 Function，复用 schema-only callback。DefaultProviderService 已复制 callback 并固定 internalToolExecutionEnabled=false。
Rationale: core 已直接依赖 spring-ai-model；无需反向依赖 provider。
Alternatives considered: core 直接实例化 ToolSchemaAdapter 破坏模块边界；复制 adapter 会引入两份规则。

## 3. 前序契约

已有 `ProviderService.chat(String,Profile,Prompt)`、`ProviderResponse.content/toolCalls`、`ToolCallIntent(id,name,argumentsJson)`、`OryxTool.execute(String)`、`ToolResult(success,content,errorMessage,retryable)`、`Profile.settings()`、ProfileRegistry 和 AgentLoader。全部保持签名。源码已核对，课件简写适配为这些真实类型。

Session/SessionManager、ToolRegistry、Sandbox、MemoryService 当前均不存在。Decision: 仅实现 Session 和保存所需契约，不补造完整后续模块；以 Map 和标准函数注入注册集合、检查策略与可选记忆。默认工具策略拒绝全部。

## 4. 依赖清单

| artifact | 版本来源 | 使用模块 | 必要性 |
| --- | --- | --- | --- |
| spring-ai-model 1.1.2 | 现有父 POM Spring AI BOM | core | Prompt/message/callback，已有直接依赖 |
| snakeyaml 2.4 | Spring Boot 3.5.8 管理 | core | Skill 安全 frontmatter，已有直接依赖 |
| spring-boot-starter-data-jpa | Boot BOM | storage | 审计和会话 JPA，已有 |
| jackson-databind | Boot BOM，现有依赖树 | storage/core 现有 Spring AI JSON 支撑 | 消息和结果 JSON，实施前检查编译依赖路径，不新增 artifact |
| flyway-core、sqlite-jdbc | 既有 POM 与 Boot BOM | storage/boot | 新迁移及 SQLite，已有 |
| spring-boot-starter-test | Boot BOM | core/storage/boot | 已有测试依赖 |

不新增第三方 artifact。若现有传递依赖不足，停止并更新 plan 后确认，不临时引包。

## 5. 数据与验证取舍

Decision: 新迁移 V2/V3，保留 V1；Session 使用显式消息 DTO 保存调用 ID、参数及结果，不使用任意多态反序列化。测试覆盖数据库文件跨上下文恢复。
Rationale: 满足课件 Session 保存及宪章 day-one 审计与恢复。
Alternatives considered: 内存会话和只记日志不能证明恢复；修改 V1 破坏已有 Flyway 历史。

Decision: Provider 超时沿用第 16 节；工具超时本节内部默认 60 秒、仅测试注入短时限，不加配置键；仅明确失败可重试，超时不重试。
Rationale: 超时可能已产生副作用，自动重试风险不可判定；有界等待不代表隔离。

所有研究项已处理；待用户批准 tasks 和必要支撑范围后开始实现。真实天气 Demo 留待 HTTP/CLI 具备并获真实调用授权后验证。

## 2026-10-27 已批准的绑定修订

Decision: 用普通UTF-8 `.link` 文件表示唯一绑定集合，统一Windows/Linux解析，公共实体不复制，不增加Profile字段。Prompt输出公共SKILL.md真实读取路径，文件工具未来复用该绑定解析结果。
Rationale: 消除原生链接创建权限及系统设置依赖，保留热读、最小元数据及真实路径边界。
Alternatives considered: 原生链接仍依赖环境，复制目录产生内容分叉，均不采用。不对旧业务文件自动迁移。
Validation: 新夹具在旧加载器上4个合法场景失败；改造后core51项全部通过，默认测试无跳过。原生链接攻击仍保留独立opt-in附加测试，不作为普通项目引用的依赖。
