<!--
Sync Impact Report
- Version change: 1.0.0 → 2.0.0
- Modified principles: II 工具调用统一受控（Skill绑定由原生软连接改为项目.link相对引用）
- Added sections: 修订记录
- Removed sections: 无
- Authorization: 用户于2026-10-27明确确认项目内部Skill引用设计及宪章同步修订
- Deferred TODOs: 无
-->

# OryxOS Constitution

## Core Principles

### I. 单一运行时内核（NON-NEGOTIABLE）

所有触发入口（CLI、HTTP、企业 IM、定时任务）MUST 复用 `AgentService` 的同一条执行链路；接入层只允许做协议、路由和输入输出适配，不得复制 ReAct 或工具编排逻辑。

`ReActLoop` MUST 由 OryxOS 自行实现，禁止改用框架的自动 Agent 循环替代内核；模型调用、工具结果回填、消息累积和终止判断统一由该循环负责，并 MUST 设置最大迭代次数、超时和异常处理。

`PromptBuilder` MUST 统一组装 Agent 指令、Bootstrap、记忆、历史和可用工具，各入口不得自行拼接 system prompt。

Spring AI 的自动工具执行 MUST 保持关闭，实际工具调用统一交给 `ToolExecutor` 调度。

**理由**：多个入口各自实现链路会导致行为漂移、重复执行和审计缺口，运行时内核是 OryxOS 的核心交付物。

### II. 工具调用统一受控

所有 Tool 调用，无论来自内置工具、Plugin Skill + MCP、自定义 MCP Server 还是 Java `@Tool`，MUST 经过统一的注册、可见性校验、执行编排和审计，不得建立绕过 `ToolExecutor` 的执行通道。

Skill 可见性 MUST 由 Agent 目录下 `skills/<binding>.link` 普通 UTF-8 相对引用文件集合唯一决定；每个文件仅含一条相对于其父目录的目标 Skill 目录路径，目标真实路径 MUST 位于当前工作区公共 `skills/` 根内。`AGENT.md` frontmatter 不得维护第二份绑定清单。函数工具仍按 Profile.tools 控制可见性。每轮只注入已绑定 Skill 的名称、描述和已校验的公共 SKILL.md 可读取路径，正文与附属资源由 `read_file` 按需读取。绑定解析 MUST NOT 依赖管理员权限、修改系统设置或复制公共 Skill；原生链接给出迁移提示，不隐式回退。

被引用的公共 Skill MUST NOT 被直接删除；删除请求返回引用它的 Agent 列表。

**理由**：工具是 Agent 接触真实系统的唯一通道，统一入口是审计、权限和安全边界的实现前提。

### III. 模块边界与依赖倒置

工程结构以 `docs/TechnicalSolution.md` 第 10 章列出的 14 个模块为目标，按当前实施阶段交付；禁止为凑数量提前填充无实际职责的实现。

共享契约 MUST 放在 `oryxos-core`；基础设施实现放在对应模块；`oryxos-core` MUST NOT 反向依赖 `oryxos-storage` 或具体渠道模块。Tool 相关能力统一在 `oryxos-tool`，不再拆分 builtin/skill/mcp 模块。

新增模块、拆分或合并模块 MUST 先更新技术方案并经过明确决策，不得在实现中临时改变边界。

**理由**：企业私有化部署要求长期可维护与可替换，边界一旦被临时依赖破坏，后续难以回退。

### IV. 安全边界必须真实生效

文件访问、HTTP 请求、通知发送和通用 Shell MUST 执行 Sandbox 白名单校验；路径校验 MUST 覆盖目录穿越和软链接逃逸。通用 Shell MUST 限定受控命令、参数和超时。

密钥 MUST 通过环境变量或独立本地配置加载，启动时校验；日志、API 响应和示例 MUST NOT 包含真实密钥、Webhook 或内部地址。

应用层白名单 MUST NOT 被描述为容器级沙箱；不可信代码和多租户隔离属于后续 Runner 能力，文档和接口不得宣称已具备。

**理由**：OryxOS 会执行模型发起的工具调用，安全边界的实际有效性和描述准确性同样重要。

### V. 持久化与配置的显式契约

数据库结构 MUST 通过 Flyway 的 `db/migration/{vendor}/` 管理，禁止以 `ddl-auto=update` 或手工 SchemaUpgrade 作为迁移方案。

Provider MUST 通过名称到 `ChatModel` 的显式映射解析，不得依赖扫描同类型 Bean 猜测模型归属。

Session 历史、`tool_invocations` 和 `llm_calls` MUST 在核心阶段即持久化，不得等审计查询页面上线后再补写。

定时任务 MUST 使用稳定的 `scheduleId` 和动态调度注册；调度定义与执行历史分离，单进程内禁止同一任务重叠执行。进程内约束不得表述为分布式锁。

**理由**：显式契约保证数据可恢复、审计可追溯，并避免跨 Agent 重名导致静默错误。

### VI. 交付真实性与可验证性

未实现的能力 MUST NOT 在文档、README 或接口中被描述为已经具备；占位类不等于能力完成。

每项交付 MUST 说明实际完成的行为、验证结果和未验证范围；不得用模拟结果冒充端到端通过，缺少密钥、网络或服务时明确报告未验证项。

行为变化 MUST 补充对应测试；核心链路重点验证 ReAct 终止与工具不重复执行、Provider 路由、记忆分区与恢复、工具可见性与路径边界、调用审计、调度不重叠，以及不同入口复用同一链路。

**理由**：项目处于工程引导阶段，夸大完成度会直接误导集成方和验收方。

## 技术基线与约束

- JDK 21、Spring Boot 3.x、Maven 多模块，核心部署形态为单体可执行 JAR。
- Spring AI Alibaba 负责模型协议适配、Provider 接入和工具 schema 生成；自实现 `ReActLoop`，不采用框架自动 Agent 循环。
- 默认 SQLite + Spring Data JPA；按技术方案后续修订支持 PostgreSQL 部署选项。
- CLI 使用 Picocli；HTTP 服务使用 Spring MVC，并按方案启用 Java 21 虚拟线程。
- 依赖版本 MUST 在验证兼容性后统一管理；不得从文档中的示意代码推断可用版本。
- 配置与密钥不得提交进仓库；示例配置 MUST 使用占位值。

## 开发工作流与质量门禁

- 主体开发遵循规范驱动流程：明确验收标准 → constitution/spec/plan/tasks → 实现 → 验证。宪章、spec、plan、tasks MUST 从 `docs/` 下的需求分析、技术方案和 AI 编程指南提炼，不得照搬其中过时的模块数量或旧示例。
- 实施依赖顺序：US-1 核心契约、工程骨架与 Provider → US-2 ReAct、基础 HTTP Tool、CLI 与 Session → US-3/US-4 Memory 与 Plugin Tool（契约稳定后可并行）→ US-5 Web Service → 持久化、审计、多 Agent、调度与端到端 Demo。
- 改动前 MUST 检查现有代码、配置和工作区状态，沿用已有约定并保留用户未提交的修改；改动保持聚焦。
- 验证 MUST 运行与新改动风险匹配的模块测试；工程骨架存在时使用 Maven Wrapper 执行 `mvn test` 和 `mvn clean package`。
- 小修复不强制走完整规范生成流程；但任何以简化实现为由削弱架构契约的做法 MUST 先获得明确决策。

## Governance

- 本宪章约束默认开发行为。用户明确指令优先；但削弱架构契约、模块边界或安全要求的决定 MUST 记录理由和影响范围。
- 文档冲突时：产品目标以 `docs/DemandAnalysis.md` 为准，架构和已明确修订的实现规则以 `docs/TechnicalSolution.md` 最新内容为准；本宪章与技术方案出现冲突时，先按本宪章执行，并立即评估修订宪章或技术方案。
- 修订 MUST 记录变更原因、版本号变更类型和影响范围；所有 PR/评审 MUST 检查是否符合本宪章；复杂度 MUST 有明确收益支撑。
- 版本策略：MAJOR 为治理原则删除或不兼容重定义；MINOR 为新增原则或实质性扩展；PATCH 为澄清、措辞修正等非语义调整。
- 宪章条款的例外 MUST 在对应 spec 或 plan 中显式记录，不得静默绕过。

## 修订记录

- 1.0.0（2026-10-21）：首次制定六项原则，原 Skill 绑定使用受控相对软连接。
- 2.0.0（2026-10-27）：经用户明确批准，改用统一跨平台的 .link 项目引用。此为绑定表示的不兼容变更，故提升 MAJOR；公共实体单一来源、唯一绑定源、渐进加载和真实路径边界均保留，其他原则不变。

**Version**: 2.0.0 | **Ratified**: 2026-10-21 | **Last Amended**: 2026-10-27
