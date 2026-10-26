# OryxOS 项目协作指南

本文件面向在此仓库工作的编码 Agent，约束需求理解、架构设计、实现和验证。它不是 `.oryxos/agents/<name>/AGENT.md` 业务 Agent 定义文件。

## 沟通与语言

- 默认使用简体中文与用户沟通，包括过程更新、问题澄清、设计说明、验证结果和交付总结；用户明确要求其他语言时遵循用户要求。
- 新增或修改项目文档、代码注释和面向用户的文本时默认使用简体中文，但应沿用目标文件已有语言风格，避免在同一文档中无必要地混用语言。
- 代码标识符、API 字段、协议名、命令、配置键、日志原文、异常信息和行业通用英文术语保持原样；必要时在中文说明中补充含义，不做生硬翻译。

## 1. 项目目标与依据

OryxOS 是面向企业私有化部署的 Java Agent OS 运行时底座。业务方通过 Agent 目录、指令和工具配置定义业务能力，多个 Agent 共享模型调用、ReAct、记忆、工具、消息接入、调度和持久化基础设施。

核心交付是可运行、可演示、可集成的 Agent 运行时，不是可视化工作流平台，也不是另造一个通用 LLM 开发框架。

实施前阅读与当前任务相关的原始文档：

| 文档 | 用途 |
| --- | --- |
| `docs/IndustryResearch.md` | 项目定位、行业背景和企业需求 |
| `docs/DemandAnalysis.md` | 功能范围、非功能需求、验收标准 |
| `docs/TechnicalSolution.md` | 架构、模块职责、技术决策和运行契约 |
| `docs/AiProgrammingGuide.md` | 开发方法、依赖顺序和任务拆解 |

本文件是执行摘要，不能代替原文。用户明确指令优先；文档冲突时，需求目标参考需求分析，架构和已明确修订的实现规则以技术方案为准。同一文档中明确标注的后续修订优先于旧示例、实施节奏和总结。无法据此确定的产品行为应记录并澄清，不静默发明契约。

已知版本差异：AI 编程指南及技术方案部分旧段落仍写 9 个模块，技术方案第 10 章现列 14 个模块；旧验收案例与最新三个每日任务 Demo 也有差异。不要照搬旧数量或把旧示例当成完整范围。

## 2. 技术基线

- JDK 21、Spring Boot 3.x、Maven 多模块，核心部署形态为单体可执行 JAR。
- Spring AI Alibaba 用于模型协议适配、Provider 接入和工具 schema 生成。
- 自实现 `ReActLoop`，不采用框架自动 Agent 循环替代项目内核。
- 必须禁用 Spring AI 自动工具执行；实际工具调用统一由 `ToolExecutor` 调度，避免重复执行和绕过审计。
- Provider 使用名称到 `ChatModel` 的显式映射，不依赖扫描同类型 Bean 猜测模型归属。
- 默认 SQLite + Spring Data JPA；按技术方案后续修订支持 PostgreSQL 部署选项。
- 数据库结构通过 Flyway 的 `db/migration/{vendor}/` 管理，不以 `ddl-auto=update` 或手工 SchemaUpgrade 作为迁移方案。
- CLI 使用 Picocli；HTTP 服务使用 Spring MVC，按方案启用 Java 21 虚拟线程。
- 具体依赖版本须验证兼容性后统一管理，不从文档中的示意代码推断可用版本。

## 3. 模块边界

以技术方案第 10 章的 14 模块为目标结构，按当前实施阶段交付，禁止为了凑数量提前填充无用实现。

| 模块 | 职责 |
| --- | --- |
| `oryxos-core` | 核心契约、Profile、Session、Agent 加载、上下文、ReAct、工具执行编排、调度及扩展契约 |
| `oryxos-provider` | 模型接入、显式 Provider 映射、Function Calling 适配 |
| `oryxos-memory` | 统一记忆门面、长期记忆后端、记忆工具 |
| `oryxos-tool` | 内置工具、MCP Client、工具注册、Sandbox、通知适配器 |
| `oryxos-storage` | 数据源、JPA 实现、Session 和调用审计持久化、迁移 |
| `oryxos-web` | REST API、统一响应与异常处理、OpenAPI |
| `oryxos-channel-cli` | 终端消息接入与交互 |
| `oryxos-cli` | Picocli 命令入口、配置和密钥加载 |
| `oryxos-boot` | 启动、自动配置与依赖装配 |
| `oryxos-persona` | 内置及自定义人格模板库，以 copy-in 方式应用 |
| `oryxos-knowledge` | 知识解析、切分、索引、检索及本地知识后端 |
| `oryxos-channel-feishu` | 飞书入站渠道适配 |
| `oryxos-channel-wecom` | 企业微信入站渠道适配 |
| `oryxos-channel-dingtalk` | 钉钉入站渠道适配 |

共享契约放 core，基础设施实现放对应模块，通过依赖倒置保持边界，避免 core 反向依赖 storage 或具体渠道。Tool 相关能力统一在 `oryxos-tool`，不再拆 builtin/skill/mcp 模块。`AgentLoader` 与 `ContextLoader` 属于上下文层，不属于工具体系。

CLI、HTTP、IM 和定时触发最终复用 `AgentService` 处理链路；接入层仅做协议、路由和输入输出适配，不复制 ReAct 业务逻辑。进程内入口与外部 HTTP 接入的调用方式按对应技术章节落实。

## 4. Agent 与 Skill 契约

- 一个 `.oryxos/agents/<name>/` 目录定义一个业务 Agent，至少包含 `AGENT.md`。
- `AGENT.md` 的 YAML frontmatter 派生 `Profile`，Markdown 正文作为任务指令注入 system prompt。
- Agent 目录是运行定义和上下文来源，不是可执行 Tool；OryxOS 不把自然语言正文解析为固定工作流。
- 公共 Skill 实体位于 `.oryxos/skills/<name>/`。Agent 通过自身 `skills/<name>` 中受控的相对软连接选择可见 Skill。
- 软连接集合是 Skill 绑定的唯一真相源，不再使用 frontmatter `skills:` 维护另一份绑定。
- 每轮只注入已绑定 Skill 的名称、描述和本地路径；完整 `SKILL.md`、参考资料与脚本不预载，正文由 `read_file` 按需读取。
- Bootstrap 与 Agent 正文按方案全量加载；`ContextLoader` 每次读取，不自行增加缓存。修改正文应在后续上下文构建时生效。
- 校验真实路径和软连接目标的访问边界；兼容 Windows 环境时不能通过复制目录或放宽路径校验偷偷改变绑定语义。
- 删除被引用的公共 Skill 默认拒绝，并返回引用它的 Agent。
- `.oryxos/agents/` 是 Agent 定义的唯一真相源。实现生命周期 API 和监听器时复用同一套校验、派生、注册和注销逻辑。
- Agent 删除按技术方案归档整个目录；生成 API 返回可预览草稿，不直接落盘注册。

## 5. 运行时、记忆与审计

- `ReActLoop` 负责模型调用、工具调用结果回填、消息累积和终止判断，必须限制最大迭代次数并处理超时和异常。
- `PromptBuilder` 统一组装 Agent 指令、Bootstrap、记忆、历史和可用工具；不在各入口各自拼装。
- `MemoryService` 提供三层统一门面，会话记忆可委托 Session 管理，不能把整个 Memory 退化为 Session 历史。
- 默认长期记忆使用 `MEMORY.md`。按技术方案修订保留 core/archival 分区，写入由 `save_memory` 的 `scope` 显式指定。
- 核心记忆区不截断、不参与检索或向量索引；截断只作用于归档区。后端必须支持分区语义，不支持时在装配期明确报错。
- 默认走关键词检索；向量检索及后端升级按当前任务范围实施，预留契约不等于提前实现全部扩展。
- 实现向量化时遵循本体先持久化、索引异步补齐、失败可降级的规则，不让索引故障阻断记忆写入。
- Session 历史需要可持久化和跨重启恢复。`tool_invocations` 与 `llm_calls` 在核心阶段就写入，不能等完整审计查询页面上线后再补。
- 定时任务用动态调度注册，配置来自 Agent 的 `schedules`，不使用静态 `@Scheduled` 写死业务任务。
- 同一任务在单进程内禁止重叠执行，重叠触发跳过；不把进程锁描述为分布式锁。
- 调度定义与状态、执行历史分离。实现调度管理时使用稳定的 `scheduleId`，不按可能跨 Agent 重名的 key 静默选择任务。

## 6. 工具、安全与 API

- Plugin Tool 支持 SKILL.md + MCP、自定义 MCP Server、Java `@Tool` 三档接入，优先支持业务方零代码扩展。
- 所有工具调用统一经过注册、可见性控制、执行编排和审计；不能另建绕过 `ToolExecutor` 的执行通道。
- 文件访问、HTTP 请求和通知发送按方案执行 Sandbox 白名单校验；文件边界需要考虑目录穿越和软连接逃逸。
- 通用 shell 使用受控命令及参数、超时限制。允许解释器意味着授予 OryxOS 进程用户的代码执行权限，argv 直传不构成文件或网络隔离。
- 应用层白名单不能宣称为容器级沙箱；不可信和多租户代码执行隔离属于后续 Runner 能力。
- `notify` 通过全局通知渠道注册表解析渠道和适配器，发送前同样校验目标地址，不把凭证或 webhook URL 写进 Agent 指令。
- 密钥通过环境变量或独立本地配置加载，启动时校验，日志及接口不泄露敏感值；不提交真实密钥和运行时私有数据。
- REST 路径、参数、状态码以文档契约为准；统一使用 `ApiResponse` 的 `code`、`message`、`data`、`timestamp`，不另造错误响应格式。
- 对外管理端点、鉴权、审计查询和企业治理按阶段交付，不把尚未实现的能力描述为已经具备。

## 7. 实施方式

主体开发遵循规范驱动流程：明确验收标准，形成设计和任务，再实现与验证。使用 Spec-Kit 时，宪章、spec、plan、tasks 应由四份文档提炼，不能照搬其中过时的模块数量。

推进依赖顺序：

1. US-1：核心契约、工程骨架、Provider，先跑通一个模型。
2. US-2：自实现 ReAct、基础 HTTP Tool、CLI、初始化和 Session 基础能力。
3. US-3 与 US-4：Memory 与 Plugin Tool，在契约稳定后可并行。
4. US-5：Web Service，打通外部系统调用。
5. 完成持久化、审计、多 Agent、调度和端到端 Demo，补齐核心交付。

人格、知识库、多 IM 渠道、向量检索、完整治理和集群能力按明确任务逐步交付。14 模块目标不代表首次迭代必须完成所有扩展。文档中的课程编号和工时是背景，不是完成质量的替代标准。

改动前检查现有代码、配置和工作区状态，沿用已有约定，保留用户的未提交修改。保持改动聚焦；未经明确决策不修改项目宪章或以简化实现为由削弱架构契约。小修复不强制走完整规范生成流程。

## 8. 验证与交付

根据改动风险运行相关模块测试及集成检查。工程初始化后以 `mvn test` 验证测试、`mvn clean package` 验证打包；存在 Maven Wrapper 时优先使用仓库提供的脚本。当前仓库未建立构建骨架前，不声称这些命令已经可用。

重点验证 ReAct 终止和工具不会重复执行、Provider 路由、记忆分区及恢复、工具可见性和路径边界、调用审计、调度不重叠，以及不同入口复用同一链路。

核心端到端场景：

| 场景 | 必须体现的行为 |
| --- | --- |
| 每日天气 | 光杆 `AGENT.md`，定时查天气并通知；涉外调用经过白名单且有审计，可查询完整 Session，也可手动补跑 |
| 每日科技日报 | 绑定公共 Skill，只预载元数据、正文按需读取；未绑定 Skill 不可见；日报体现跨会话记忆偏好 |
| 每日 GitHub 日报 | 专用 Tool 或 MCP 封装脚本，JSON 结果进入上下文、代码不预载；生成日报并通知，修改 Agent 正文后生效 |

保留需求文档中的基础能力验收：跨会话偏好恢复、同步 Web 调用、多 Agent 并存和多入口联动。性能目标引用需求文档第 8 章，只有实际测量后才能报告达标。

无需真实密钥的测试使用可控的 Provider、MCP 和 HTTP 桩；真实外部调用单独验证。缺少密钥、网络或服务时明确报告未验证项，不用模拟结果冒充端到端通过。

每次交付说明实际完成的行为、验证结果、未完成范围和必要的运行方式。代码、配置示例和文档应与最终实现一致。
