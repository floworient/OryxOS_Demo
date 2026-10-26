---
name: "oryxos-init"
description: "当需要在五大核心能力开发前建立、补齐或检查 OryxOS 工程地基时使用，适用于日志、可观测性、HTTP Server、API 规范、Java 质量门禁、依赖安全、CI 或初始化验证。"
metadata:
  project: "OryxOS"
  scope: "engineering-baseline"
---

# OryxOS 工程地基初始化

在开发 OryxOS 五大核心能力之前，建立可重复执行、面向生产环境的工程地基。将本 Skill 视为幂等的初始化器和检查器：先检查现状，保留兼容的既有实现，只补充缺失能力，并把可执行规范固化到仓库，而不是只写在 Skill 中。

这是开发阶段使用的工程 Skill。禁止把它或它生成的治理文件放进 `.oryxos/skills/`；该目录存放的是供 OryxOS 业务 Agent 绑定的运行时公共 Skill。

## 交付目标

完成初始化的仓库应具备：

- 由 `oryxos-boot` 装配、可运行的 Spring Boot HTTP Server；
- `oryxos-web` 中唯一的 API 响应信封和异常映射链路；
- 不泄露敏感信息、支持请求关联的结构化日志；
- Actuator 健康检查和 Prometheus 兼容指标；
- 项目专用的 Java 格式、静态检查和架构约束；
- 接入 Maven 与 CI 的单元测试、集成测试、覆盖率和安全检查；
- 与实际命令、端点和行为一致的开发文档；
- 基于最新验证结果的交付报告。

本 Skill 只初始化工程基础设施，不实现 Provider 路由、ReAct、Memory、Plugin Tool、业务 Agent、多 IM 渠道、分布式追踪平台、服务发现、API 网关或微服务拆分。

## 依据与真相源

修改前按任务范围读取：

1. `AGENTS.md`
2. `docs/DemandAnalysis.md`
3. `docs/TechnicalSolution.md`
4. `docs/AiProgrammingGuide.md`
5. 存在时读取 `.specify/memory/constitution.md`
6. 根 `pom.xml` 和受影响模块的 `pom.xml`
7. 已有源码、资源、测试、CI 和质量配置

文档冲突按照 `AGENTS.md` 规定的优先级处理。不得根据示意代码推断依赖版本。Spring Boot BOM 已管理的依赖应复用 BOM；Maven 插件版本统一固定在父 POM。

## 不可破坏的项目边界

- OryxOS 保持模块化单体架构，最终部署为一个可执行 JAR。
- 共享契约放在 `oryxos-core`，HTTP 适配放在 `oryxos-web`，运行装配和部署默认配置放在 `oryxos-boot`。
- `oryxos-core` 不得依赖 `oryxos-web`、`oryxos-storage` 或具体渠道模块。
- HTTP、CLI、IM 和定时入口最终复用 `AgentService`；基础模块不得创建另一条 Agent 执行链路。
- `tool_invocations` 和 `llm_calls` 是持久化审计记录，日志和指标不能替代它们。
- Actuator 管理端点不遵循业务 API 的 `ApiResponse` 信封契约。
- 健康检查不得暴露凭证、文件路径、Prompt、记忆或 Provider 响应。
- 指标标签必须是有界集合。禁止使用 `sessionId`、请求文本、Prompt、URL、异常消息、用户输入标识或 Tool 输出作为标签。
- 默认禁止记录密钥、Authorization Header、API Key、Webhook URL、完整 Prompt、记忆正文或原始 Tool 结果。
- 未经单独需求批准，不添加 Spring Cloud、Nacos、Sentinel、Gateway、Kafka、Kubernetes 清单、Grafana 部署或 OpenTelemetry Collector。

## 触发方式

| 参数 | 行为 |
| --- | --- |
| 无参数或 `all` | 按依赖顺序检查并初始化完整工程地基。 |
| `http-api` | 只初始化 HTTP 运行时、参数校验、响应信封、异常处理和 OpenAPI。 |
| `observability` | 只初始化结构化日志、请求关联、Actuator、健康检查和指标。 |
| `quality` | 只初始化格式化、Checkstyle、SpotBugs、JaCoCo、架构测试和 Maven 门禁。 |
| `security` | 只初始化依赖与静态安全检查、密钥保护和相关 CI。 |
| `ci` | 只初始化或修复执行仓库 Maven 门禁的 CI。 |
| `verify` | 不做实现修改，只运行检查并用证据报告缺口。 |

指定范围缺少必要前置条件时，只添加最小前置能力，或停止并说明依赖关系。不得静默扩展到无关核心功能。

## 初始化流程

### 1. 修改前盘点

至少执行：

```powershell
git status --short
rg --files -g "pom.xml" -g "*.java" -g "*.yaml" -g "*.yml" -g "*.xml" -g "*.md"
rg -n "actuator|prometheus|ApiResponse|RestControllerAdvice|Checkstyle|SpotBugs|JaCoCo|ArchUnit|dependency-check|structured" .
```

记录必须保留的用户修改、已有依赖和插件、应用 Profile、环境变量契约、端点、响应类型、测试、CI，以及代码与文档契约的冲突。将每项能力标记为 `已具备`、`部分具备`、`缺失` 或 `冲突`。扩展兼容配置，不创建功能重复的并行机制。

### 2. 先定义验收检查

实现前先写出当前范围可观察、可执行的验收条件。适合自动化测试的生产行为，应先添加最小失败测试；纯配置变更必须有修改前失败的集成测试或检查命令。

最低验收契约：

- 打包后的 Boot 应用能够启动；
- `/actuator/health` 能报告健康状态且不泄露敏感详情；
- 仅在明确暴露时提供 `/actuator/prometheus`；
- 代表性成功响应和各类错误响应符合 `ApiResponse`；
- 非法参数返回约定的 HTTP 状态码和内部错误码；
- 日志是有效结构化输出，并在一次请求链路中携带关联 ID；
- Windows 使用 `mvnw.cmd verify`，其他系统使用 `./mvnw verify`，能够执行仓库质量门禁；
- 故意加入格式或静态检查违规时，相应门禁确实失败；
- 模块边界测试能够拒绝禁止的依赖。

### 3. 按依赖顺序初始化

执行 `all` 时按以下阶段推进。每完成一个阶段，先运行该阶段的聚焦测试。

#### 阶段 A：父工程构建基线

修改根 `pom.xml` 和仓库级配置。

| 能力 | 动作 |
| --- | --- |
| 工具链 | 使用 Maven Enforcer 保持 JDK 21 和 Maven 版本要求；只在验证兼容性后增加依赖收敛规则。 |
| 依赖版本 | 在 `dependencyManagement` 统一管理，优先复用 Spring Boot BOM。 |
| 插件版本 | 在 properties 或 `pluginManagement` 固定 Compiler、Surefire、Failsafe、Enforcer、Checkstyle、SpotBugs、JaCoCo 和安全插件版本。 |
| 单元测试 | 使用 Surefire 统一所有 Java 模块的测试行为。 |
| 集成测试 | 使用 Failsafe 和明确命名约定，例如 `*IT`；普通单元测试不得依赖外部服务。 |
| 格式和规范 | 采用一套格式化策略和一套 Checkstyle 配置，避免工具互相冲突。 |
| 覆盖率 | 为多模块 Reactor 生成正确合并或聚合的 JaCoCo 报告；设置当前可达到的初始门槛。 |
| 静态分析 | 将 SpotBugs `check` 绑定到 `verify`；排除项必须集中存储且理由明确。 |
| 架构约束 | 在负责模块边界的测试模块中引入 ArchUnit。 |

项目规则建议保存为：

```text
config/
├── checkstyle/
│   ├── checkstyle.xml
│   └── suppressions.xml
└── spotbugs/
    └── exclude.xml
```

以 Google Java Style 为起点，明确记录 OryxOS 差异。禁止把大规模格式化与功能修改混在同一个变更中；必须批量格式化时，应隔离变更并在交付中说明。

#### 阶段 B：HTTP 与 API 基线

主要模块为 `oryxos-web`，装配模块为 `oryxos-boot`。

| 文件或能力 | 职责 |
| --- | --- |
| `oryxos-web/pom.xml` | 声明 Spring MVC、Jakarta Validation 和 springdoc 依赖。 |
| `ApiResponse<T>` | 只包含 `code`、`message`、`data` 和 `timestamp`，全项目复用同一个实现。 |
| `GlobalExceptionHandler` | 统一映射参数错误、资源不存在、业务冲突、Provider 不可用和未知异常，不泄露内部实现。 |
| 错误码 | 使用稳定内部错误码，错误码与消息分离，并保留正确 HTTP 状态码。 |
| OpenAPI | 描述公开的 `/api/v1/**` 契约和代表性错误响应。 |
| MVC 配置 | 统一 JSON 和时间格式；只在存在明确调用方时配置请求限制和 CORS。 |
| 测试 | 使用 `MockMvc` 或等效方式覆盖成功、校验失败、不存在、依赖不可用和未知异常。 |

禁止使用 `ApiResponse` 包装 Actuator 响应。禁止为了显得完整而增加无业务意义的占位端点。

#### 阶段 C：日志基线

部署配置主要放在 `oryxos-boot/src/main/resources`。共享日志工具放在不造成反向依赖的最低层模块。

- 使用 SLF4J API 和 Spring Boot 支持的结构化日志格式。
- 通过 Spring Profile 区分本地可读输出和生产 JSON 输出。
- 添加 Filter 或 Interceptor：接收合法外部关联 ID 或生成新 ID，放入 MDC，通过响应 Header 返回，并在 `finally` 中清理。
- 约定稳定字段：`requestId`、`agentName`、`sessionId`、`provider`、`model`、`toolName`、`scheduleId`、`durationMs`、`outcome`。
- 只记录生命周期边界和结果，不完整输出 Prompt 或业务负载。
- 测试关联 ID 回传、MDC 清理和敏感信息脱敏。
- 运维日志与数据库持久化的调用审计记录必须分离。

#### 阶段 D：监控与健康检查基线

除非更低层模块直接拥有自定义指标，否则以下依赖放在 `oryxos-boot`：

- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`

配置放在 `oryxos-boot/src/main/resources/application*.yaml`：

- 默认只暴露 `health`、`info`，需要抓取时再明确暴露 `prometheus`；
- 明确管理端点是否与应用共用端口；
- 部署环境需要时启用 liveness 和 readiness；
- 谨慎配置 `show-details`；
- 应用名和版本等公共标签必须是有界值；
- 在声称达到生产要求前，为管理端点增加鉴权或网络隔离。

业务指标靠近实际拥有行为的组件。为后续能力预留稳定名称：

- `oryxos.agent.requests`
- `oryxos.agent.duration`
- `oryxos.llm.calls`
- `oryxos.llm.tokens`
- `oryxos.tool.invocations`
- `oryxos.react.iterations`
- `oryxos.schedule.executions`

真实行为不存在时禁止创建虚假计数器。本阶段只安装基础设施并建立规范。

#### 阶段 E：安全与供应链基线

- 使用 SpotBugs 检查常规缺陷。
- 确认版本兼容后启用 Find Security Bugs。
- 使用 OWASP Dependency-Check 或同类 SCA；明确漏洞阈值、排除责任人、漏洞库更新策略和 CI 缓存。
- 在 CI 中进行密钥扫描。
- 代码托管平台支持时增加自动依赖升级。
- 随 HTTP、文件等能力实现逐步增加日志脱敏和边界安全测试。

禁止仅为让 CI 变绿而排除漏洞。每条排除必须记录依赖或 CVE、理由、补偿措施、责任人和失效或复查日期。需要联网更新漏洞库的检查应放在 CI 或专用 Maven Profile，不能破坏普通离线测试的稳定性。

#### 阶段 F：CI 质量门禁

优先扩展现有工作流，不创建职责重叠的工作流。Pull Request 门禁应：

1. 检出仓库；
2. 安装受支持的 JDK 21 并启用 Maven 缓存；
3. 使用 Maven Wrapper；
4. 执行 `verify`；
5. 必要时归档测试、覆盖率和静态分析报告；
6. 把高网络开销的安全扫描放在独立步骤或定时工作流；
7. 必需检查不得无条件配置 `continue-on-error`。

除非基础工程改动确实要求，否则不得修改现有网站部署工作流。

#### 阶段 G：开发文档

只记录已经验证的命令和行为，覆盖：本地环境与 Wrapper 命令、运行 Profile、环境变量、HTTP/API 规范、错误码、日志字段、脱敏策略、健康与指标端点、质量门禁、报告位置、排除流程和故障排查。

README 保持简洁并链接详细文档。未经实现和测试，不得声称已具备鉴权、Dashboard、告警、SIEM 集成或生产级加固。

### 4. 分层验证

使用仓库自带 Maven Wrapper。Windows 优先使用 `mvnw.cmd`，其他系统使用 `./mvnw`。

```powershell
.\mvnw.cmd -pl oryxos-web -am test
.\mvnw.cmd -pl oryxos-boot -am verify
.\mvnw.cmd verify
.\mvnw.cmd clean package
```

使用测试 Profile 和无需真实 Provider 密钥的方式，对打包应用做运行时验证。必须访问外部依赖时使用可控桩。缺少凭证、网络或外部服务时，不得声称真实端到端验证成功。

除退出码外，还要检查 Surefire、Failsafe、JaCoCo、Checkstyle、SpotBugs、依赖漏洞、架构测试、可执行 JAR、健康检查、指标、代表性 API 响应和结构化日志样本。

### 5. 交付报告

报告必须包含：

1. 已完成的工程地基范围；
2. 修改的文件和模块；
3. 实际执行的验证命令与结果；
4. 当前可用端点和运行命令；
5. 已建立的质量门禁及执行位置；
6. 新增偏差或排除项及理由；
7. 因网络、凭证或外部服务缺失而未验证的项目；
8. 明确延后的范围。

## 模块与配置归属

| 能力 | 主要位置 | 禁止放置的位置 |
| --- | --- | --- |
| 共享领域和运行时契约 | `oryxos-core` | `oryxos-web` 实现包 |
| REST Controller 和 API 信封 | `oryxos-web` | `oryxos-core` |
| 异常到 HTTP 的映射 | `oryxos-web` | 每个 Controller 各自实现 |
| OpenAPI 配置 | `oryxos-web` | 无关核心模块 |
| 可执行应用装配 | `oryxos-boot` | 各功能模块单独启动 |
| Actuator、Prometheus 依赖和暴露 | `oryxos-boot` | `oryxos-core` |
| 部署日志默认配置 | `oryxos-boot/src/main/resources` | Agent 定义文件 |
| Maven 版本和公共门禁 | 根 `pom.xml` | 重复的子 POM 配置 |
| Checkstyle、SpotBugs 规则 | 根 `config/` | 用户主目录隐藏配置 |
| API 契约测试 | `oryxos-web/src/test` | 生产源码 |
| 完整应用冒烟测试 | `oryxos-boot/src/test` | 伪造的独立应用 |
| CI | `.github/workflows/` | 未被 CI 调用的本地脚本 |
| 开发治理规范 | `docs/`，批准时可更新 `AGENTS.md` | 运行时 `.oryxos/skills/` |

## 完成检查清单

### 仓库安全

- [ ] 已读取相关原始文档和既有配置。
- [ ] 已检查 `git status` 并保留无关用户修改。
- [ ] 未提交真实密钥、Token、Webhook、私有 Prompt 或运行时数据。
- [ ] 重复运行初始化不会重复添加插件、类或工作流。

### HTTP 与 API

- [ ] Boot 应用能够作为单一可执行 JAR 启动。
- [ ] 已按技术方案配置 Java 21 虚拟线程。
- [ ] 公开端点使用 `/api/v1/**`。
- [ ] 唯一的 `ApiResponse<T>` 包含 `code`、`message`、`data`、`timestamp`。
- [ ] 唯一的全局异常处理器保留正确 HTTP 状态码。
- [ ] Jakarta Validation 失败行为有契约测试。
- [ ] OpenAPI 根据实际实现生成。
- [ ] Actuator 响应未使用 `ApiResponse` 包装。

### 日志与可观测性

- [ ] 生产日志使用已记录的结构化格式。
- [ ] 关联 ID 能够安全传递、回传并清理。
- [ ] 敏感值和高体积正文已省略或脱敏。
- [ ] Actuator 健康检查可用且详情暴露安全。
- [ ] Prometheus 抓取方式已明确配置并验证。
- [ ] 指标只使用有界标签。
- [ ] 未把日志和指标描述为审计持久化的替代品。

### 质量与安全

- [ ] 已强制 JDK 和 Maven 版本。
- [ ] 格式和代码规范已自动化并记录。
- [ ] Checkstyle 已进入 Maven 生命周期。
- [ ] SpotBugs 已进入 `verify`。
- [ ] 单元测试和集成测试使用明确的不同约定。
- [ ] JaCoCo 能生成多模块报告并执行约定门槛。
- [ ] ArchUnit 能保护关键模块边界。
- [ ] 依赖漏洞扫描具备稳定的 CI 和漏洞库策略。
- [ ] 排除项范围小、理由明确、有责任人且可复查。
- [ ] 必需 CI 检查遇到违规时会使构建失败。

### 验证与交接

- [ ] 聚焦模块测试通过。
- [ ] 完整 `verify` 通过，或报告中逐项列出了失败。
- [ ] `clean package` 产生预期可执行产物。
- [ ] 冒烟测试覆盖健康检查、指标、API 错误和日志格式。
- [ ] 文档与实际验证过的命令和行为一致。
- [ ] 延后能力被明确列出，没有暗示已经完成。

## 常见错误

| 错误 | 正确处理 |
| --- | --- |
| 把本 Skill 当作微服务脚手架 | 保持模块化单体，不引入分布式基础设施。 |
| 把所有依赖放进父 POM | 父 POM 管版本；运行依赖只放到实际拥有它的模块。 |
| 立即增加 `oryxos-observability` 模块 | 先使用现有模块；只有证明存在独立边界时才新增模块。 |
| 记录完整请求、Prompt 或 Tool 结果 | 只记录稳定元数据和结果，审计数据走持久化契约。 |
| 用 Session 或用户值作为指标标签 | 改用 Provider、Tool 类型、结果和错误分类等有界标签。 |
| 所有 API 都返回 HTTP 200 | 同时使用正确 HTTP 状态码和统一响应体。 |
| 暴露所有 Actuator 端点 | 只暴露必要端点，并记录保护假设。 |
| 每次本地测试都联网更新漏洞库 | 使用专用 Maven Profile 或 CI 任务，同时保留发布门禁。 |
| 未评估现有代码就照搬 Google 规则 | 使用确定性格式，记录差异，逐步建立门禁且不掩盖违规。 |
| 只生成文件就报告成功 | 必须执行 Maven 与运行时验证并报告最新证据。 |
