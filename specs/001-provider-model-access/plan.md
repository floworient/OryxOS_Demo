# Implementation Plan: 核心契约与 Provider 接入

**Branch**: `001-provider-model-access` | **Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/001-provider-model-access/spec.md`

## Summary

在已有 14 模块 Maven 骨架上交付第 16 节：从 `.oryxos/agents/<name>/AGENT.md` 派生当前版完整 `Profile` 并注册到 `ProfileRegistry`；把「Provider 名称 → ChatModel」建成显式映射表，跑通一次真实的阿里云百炼（DashScope）单轮调用，同时支持任意 OpenAI 兼容端点；把 `OryxTool` 转成只携带 Schema、不可执行的 Spring AI `ToolCallback` 并强制关闭内部工具执行；每次调用写入 SQLite 的 `llm_calls` 表，审计写入失败时调用失败关闭。

核心契约（`Profile`、`AgentLoader`、`ProfileRegistry`、`OryxTool`、`ProviderService`、`LlmCallRecord`）放 `oryxos-core`，`AGENT.md` frontmatter YAML 解析也在 core；DashScope/OpenAI 适配、显式映射与 `ToolSchemaAdapter` 放 `oryxos-provider`；JPA 实体与 Flyway 迁移放 `oryxos-storage`；启动装配放 `oryxos-boot`。

本阶段**不实现** ReAct、工具执行、多轮对话、工作区初始化与 CLI 入口；验收通过内部调用入口、自动化测试与受控 `ChatModel` 桩完成，真实 DashScope 调用作为可选的联网验证项。

## Technical Context

**Language/Version**: Java 21（`maven.compiler.release=21`，enforcer 锁定 `[21,22)`）

**Primary Dependencies**:
- Spring Boot 3.5.8（已由父 POM 管理）
- Spring AI Alibaba 1.1.2.2（`spring-ai-alibaba-bom` 已导入）
- Spring AI 1.1.2（**需新增** `org.springframework.ai:spring-ai-bom` 导入；`spring-ai-model` 供 core 使用 `Prompt`/`ToolDefinition` 契约，`spring-ai-openai` 供兼容端点适配）
- SnakeYAML 2.4（**需新增** `org.yaml:snakeyaml` 到 `oryxos-core`，版本由 Spring Boot 3.5.8 管理；本地 JAR 已核实）
- Spring Data JPA + Hibernate 6.6.36.Final（Boot 3.5.8 管理）
- `org.hibernate.orm:hibernate-community-dialects`（**需新增**，提供 SQLite 方言；版本由 Boot 的 `hibernate.version` 管理，不写版本号）
- Flyway 11.7.2（`flyway-core` 已含 SQLite 支持，无需额外数据库模块）
- `org.xerial:sqlite-jdbc` 3.49.1.0（已管理）
- Picocli 4.7.7、springdoc 2.8.8（本阶段不使用）

**Storage**: SQLite，文件位于 `.oryxos/oryxos.db`；结构由 Flyway `db/migration/sqlite/` 管理，`ddl-auto=validate`，禁止自动建表

**Testing**: JUnit 5 + AssertJ（`spring-boot-starter-test`）；`AgentLoaderTest` 覆盖完整字段、名称一致性与坏 Agent 隔离；`ToolSchemaAdapterTest` 覆盖 Schema 保真与零执行；Provider 行为用受控 `ChatModel` 桩验证并覆盖并发参数隔离与 50 ms 内部开销；`Lesson16QuickstartTest` 提供可计时的无网络闭环；OpenAI 兼容协议用 JDK `HttpServer` 回环验证；`LlmCallRepositoryTest` 覆盖成功、失败和失败关闭；boot 层 `ProviderSmokeIT` 由显式系统属性与凭据双门禁保护，默认测试不调用真实网络

**Target Platform**: JVM 21，Windows / Linux 单机部署；核心形态为单体可执行 JAR

**Project Type**: Maven 多模块库与运行时（非 Web 服务入口）

**Performance Goals**: 系统内部转发开销 ≤ 50 ms（SC-007，不含模型自身响应时间）

**Constraints**:
- 同步阻塞调用，不做流式响应
- 不做 fallback、熔断、hedge racing
- Spring AI 自动工具执行保持关闭；本阶段完全不构造 `ChatClient`，避免任何隐式工具调用路径
- 单实例；凭据仅来自环境变量或部署侧配置
- `Profile` 不含 `skills` / `notify_channels`；发现课件旧字段时该 Agent 加载失败
- 审计持久化是调用完成条件；写入失败不得返回成功结果
- DashScope 与 OpenAI 兼容客户端的连接/读取超时固定为 60 秒，作为内部常量，不新增对外配置键
- Tool Schema 适配发生在调用方构造 `Prompt` 之前；`ProviderService` 只复制已有 schema-only callbacks 并强制关闭内部执行，本节不提前实现 `ToolRegistry`
- `ProviderDefinition` 仅是 `oryxos-provider` 内部配置模型，不新增第二套 core 公共供应商描述类型；缺凭据但未被 Agent 引用的定义保留在配置清单中，但不进入可调用的 `name → ChatModel` 映射，后续调用在发出网络请求前返回 `MISSING_CREDENTIAL`
- 真实 Provider Smoke IT 放在 `oryxos-boot`，只有同时显式设置 `-Doryxos.provider.smoke=true` 且存在 `ALIYUN_API_KEY` 时才允许联网；默认 `test`/`verify` 不发起真实模型请求

**Scale/Scope**: 单节点，供应商数量个位数，Agent 数量个位数；本阶段无并发压测目标

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 宪章条款 | 本 feature 的落实 | 结论 |
| --- | --- | --- |
| I. 单一运行时内核 | 本阶段不实现 ReAct；`ProviderService` 是模型调用的唯一入口，后续 ReActLoop 只通过它调模型。请求级选项显式设置 `internalToolExecutionEnabled=false`。 | PASS |
| II. 工具调用统一受控 | `ToolSchemaAdapter` 创建的 `ToolCallback` 只携带 `ToolDefinition`，其 `call` 固定拒绝执行；请求同时设置 `internalToolExecutionEnabled=false`，工具意图只交给后续 `ToolExecutor`。Skill 绑定不进入 Profile。 | PASS |
| III. 模块边界与依赖倒置 | 契约（`Profile`、`AgentLoader`、`OryxTool`、`ProviderService`、`LlmCallRecord`）在 `oryxos-core`；协议适配在 `oryxos-provider`；JPA 实现在 `oryxos-storage`；装配在 `oryxos-boot`。`oryxos-core` 不依赖 provider/storage。 | PASS |
| IV. 安全边界 | 凭据只从环境变量/部署侧配置读取；未解析占位符在启动期报错；日志与错误信息统一脱敏。本阶段无文件/HTTP 工具，不需要 Sandbox。 | PASS |
| V. 持久化与配置显式契约 | Flyway 迁移管理 `llm_calls` 表；Provider 用启动期构建的 `Map<String, ChatModel>` 显式解析，不扫描同类型 Bean；审计写入失败时调用失败关闭。 | PASS |
| VI. 交付真实性 | 真实 DashScope 调用需要有效凭据与网络；缺少时明确报告未验证项，不以桩结果冒充端到端通过。 | PASS |

**结论**：无违反项，Complexity Tracking 留空。

## Project Structure

### Documentation (this feature)

```text
specs/001-provider-model-access/
├── plan.md              # 本文件
├── research.md          # Phase 0：技术未知项与决策
├── data-model.md        # Phase 1：实体与表结构
├── quickstart.md        # Phase 1：验证指南
├── contracts/           # Phase 1：配置契约与内部服务契约
│   ├── provider-config.md
│   ├── provider-service.md
│   └── agent-profile.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 输出（/speckit-tasks 生成）
```

### Source Code (repository root)

沿用仓库既有的 Maven 多模块布局，不新增模块。本 feature 触及以下模块：

```text
oryxos-core/src/main/java/io/oryxos/core/
├── profile/                     # Profile 契约、ProfileRegistry
│   ├── Profile.java
│   ├── ProfileRegistry.java
│   └── AgentLoader.java         # 扫描 Agent 目录并派生完整 Profile；内部隔离单 Agent 错误
├── tool/
│   ├── OryxTool.java            # name/description/inputSchema/execute 核心契约
│   └── ToolResult.java
├── provider/
│   ├── ProviderService.java     # chat(sessionId, Profile, Prompt) 唯一入口
│   ├── ProviderResponse.java
│   ├── TokenUsage.java
│   ├── ToolCallIntent.java
│   ├── ProviderErrorCode.java
│   └── ProviderException.java
└── audit/
    ├── LlmCallStatus.java
    ├── LlmCallRecord.java       # 调用记录契约
    └── LlmCallRecorder.java     # 写入契约（core 定义，storage 实现）

oryxos-provider/src/main/java/io/oryxos/provider/
├── ProviderProperties.java      # oryxos.providers 配置绑定（List，便于检出重名）
├── ProviderDefinition.java      # provider 模块内部配置模型，不是 core 公共契约
├── ProviderRegistry.java        # 配置名称清单 + 可调用的 name → ChatModel 显式映射
├── DefaultProviderService.java  # 实现 core 的 ProviderService
├── ProviderAutoConfiguration.java
├── ProviderStartupValidator.java # 移除未知 Provider 的 Profile；校验被引用凭据
├── dashscope/DashScopeChatModelFactory.java
├── openai/OpenAiCompatibleChatModelFactory.java
├── ToolSchemaAdapter.java       # OryxTool → schema-only ToolCallback；call 固定拒绝
└── CredentialResolver.java      # 环境变量解析与未解析占位符检测

oryxos-storage/src/main/java/io/oryxos/storage/
├── entity/LlmCallEntity.java
├── repository/LlmCallRepository.java
├── JpaLlmCallRecorder.java      # 实现 core 的 LlmCallRecorder；失败向上传播
└── config/StorageConfiguration.java

oryxos-storage/src/main/resources/db/migration/sqlite/
└── V1__create_llm_calls.sql

oryxos-boot/src/main/java/io/oryxos/boot/
└── ProviderBootstrapConfiguration.java # 确定加载、注册、校验顺序

oryxos-boot/src/test/java/io/oryxos/boot/
└── ProviderSmokeIT.java         # 显式开关 + 凭据双门禁的真实联网验证

oryxos-boot/src/main/resources/
└── application.yaml              # 放开 JPA/Flyway，声明 oryxos.providers 示例
```

**Structure Decision**: 采用既有 14 模块布局。共享契约下沉到 `oryxos-core`，模型适配放 `oryxos-provider`，持久化放 `oryxos-storage`，启动校验与装配放 `oryxos-boot`。`oryxos-cli` 与 `oryxos-channel-cli` 本阶段不改动（CLI 入口属 002）。

### 与 002 的范围边界

| 能力 | 001（本 feature） | 002 |
| --- | --- | --- |
| `AGENT.md` frontmatter 解析 | 完整字段派生；名称/语法/废弃字段校验；Provider 引用校验 | 复用 Profile，补 Tool/Bootstrap 已注册或存在校验 |
| Provider 校验 | 名称唯一、被引用供应商凭据合法 | 复用同一实现 |
| 工作区 `init`、`chat` | 不实现 | 实现 |
| `llm_calls` 写入 | 实现 | 复用 |
| `tool_invocations` | 不涉及 | 实现 |
| Tool Schema | `OryxTool` → schema-only callback，自动执行关闭 | ReAct 使用 Schema，并由 `ToolExecutor` 执行意图 |

## Constitution Check（Phase 1 设计后复检）

设计产物（[data-model.md](./data-model.md)、[contracts/](./contracts/)、[quickstart.md](./quickstart.md)）落地后逐条复检，结论仍为全部 PASS：

| 条款 | 设计层面的证据 |
| --- | --- |
| I / II | 契约不使用 `ChatClient`；`ToolSchemaAdapter` 生成不可执行的 schema-only callback，请求选项显式关闭内部工具执行，`ToolCallIntent` 是纯数据记录 |
| III | `oryxos-core` 只声明接口与数据，`oryxos-storage` 通过实现 `LlmCallRecorder` 反向接入，未出现 core → storage 的编译依赖 |
| IV | 凭据解析规则集中在 `CredentialResolver`，内部 `ProviderDefinition.toString()` 省略 `apiKey`，错误信息只报变量名不报值 |
| V | `V1__create_llm_calls.sql` 是唯一建表路径，`ddl-auto=validate`；`ProviderRegistry` 是显式 Map；`LlmCallRecorder` 写入异常转成审计失败并向上传播 |
| VI | quickstart 明确区分无密钥路径与真实调用路径，并规定未验证项的报告措辞 |

设计阶段未引入新的架构例外。

## Complexity Tracking

> 无宪章违反项，本节留空。
