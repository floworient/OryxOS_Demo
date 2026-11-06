# Implementation Plan: Web Service 核心能力

**Branch**: `main`（Spec-Kit feature 标识 `009-web-service-core`） | **Date**: 2026-11-04 | **Spec**: [spec.md](spec.md)

**Input**: 已批准设计 `docs/superpowers/specs/2026-11-04-web-service-core-design.md` 与 feature specification。

## Summary

在现有 `oryxos-web` 上完成 US-5 核心 6 个 Controller/10 个端点，以薄 Controller 和 Web 应用服务把 HTTP 适配到既有 AgentService。先在 core/storage 建立可保留归档历史的 Session 代次契约与 Flyway V6，再实现 32 KiB 校验、最近 100 条安全历史、单 Session 非阻塞互斥和 60 秒虚拟线程截止；查询端点通过显式 View 与 core ProviderCatalog 避免基础设施反向依赖。

## Technical Context

**Language/Version**: JDK 21，父 POM Enforcer `[21,22)`

**Primary Dependencies**: Spring Boot 3.5.8、Spring MVC/Jakarta Validation、Spring Data JPA、Flyway、SQLite JDBC 3.49.1.0、springdoc-openapi 2.8.8、Spring AI 1.1.2；不新增第三方依赖

**Storage**: 现有 SQLite；新增 `V6__support_session_generations.sql` 重建 sessions 约束，不新增业务表

**Testing**: JUnit 5、AssertJ、Mockito、MockMvc、Spring Boot test、Failsafe IT、临时 SQLite 与可控 Provider/Tool

**Target Platform**: Windows 开发机、Linux 单实例私有化部署

**Project Type**: Maven 14 模块单体可执行 JAR / Spring MVC Web Service

**Performance Goals**: 同 Session 冲突在 AgentService 前立即拒绝；不同 Session 不受全局锁阻塞；同步 Agent 调用最长等待 60 秒

**Constraints**: 核心 10 端点、统一 ApiResponse、32 KiB、最近 100 条、无认证、开放 CORS、Web 只依赖 core、Agent 执行只经 AgentService、无真实外部副作用回滚承诺

**Scale/Scope**: 单 JVM、当前已加载 Profile/Tool/Provider；会话与审计使用现有 SQLite，未定义高并发吞吐或集群一致性目标

## Constitution Check

| 原则 | 设计前检查 | Phase 1 后检查 |
| --- | --- | --- |
| I 单一运行时内核 | PASS：Session message 与 invoke 只调用 AgentService | PASS：contract 和 IT 固定真实统一链路，无 Web 专用 ReAct |
| II 工具调用统一受控 | PASS：无 Tool execute API，查询只返回摘要 | PASS：历史不返回 args/result，调用仍经 ToolExecutor/Sandbox/审计 |
| III 模块边界与依赖倒置 | PASS：Session/Provider 查询契约在 core，JPA 在 storage，Web 不依赖实现 | PASS：ProviderCatalog 避免 web -> provider，SessionDetails 避免 web -> repository |
| IV 安全边界真实生效 | PASS：显式 View 白名单、异常脱敏、开放 CORS 限制披露 | PASS：contracts/tests 固定敏感字段负断言，不声称超时回滚 |
| V 持久化与配置显式契约 | PASS：Flyway V6、归档状态、活动部分唯一、审计沿用既有表 | PASS：迁移与跨重启测试覆盖，无 ddl-auto/update 或手工升级 |
| VI 交付真实性与可验证性 | PASS：TDD、可控 Provider、真实 SQLite/AgentService IT、未验证项披露 | PASS：quickstart 与 tasks 包含 RED/GREEN、全仓/打包/文档门禁 |

无宪章例外。无认证、开放 CORS、单进程锁、尽力中断和外部副作用不回滚必须在公开文档中作为限制保留。

## Project Structure

### Documentation

```text
specs/009-web-service-core/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── web-api.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

实现完成后新增 `verification.md`；Superpowers 的逐步计划位于 `docs/superpowers/plans/2026-11-04-web-service-core.md`。

### Source Code

```text
oryxos-core/src/
├── main/java/io/oryxos/core/
│   ├── provider/ProviderCatalog.java
│   └── session/
│       ├── Session.java
│       ├── SessionDetails.java
│       ├── SessionManager.java
│       └── SessionStatus.java
└── test/java/io/oryxos/core/session/SessionTest.java

oryxos-provider/src/
├── main/java/io/oryxos/provider/ProviderRegistry.java
└── test/java/io/oryxos/provider/ProviderRoutingTest.java

oryxos-storage/src/
├── main/java/io/oryxos/storage/
│   ├── JpaSessionManager.java
│   ├── entity/SessionEntity.java
│   └── repository/SessionRepository.java
├── main/resources/db/migration/sqlite/V6__support_session_generations.sql
└── test/java/io/oryxos/storage/
    ├── SessionArchiveTest.java
    ├── SessionGetOrCreateTest.java
    ├── SessionMigrationV6Test.java
    └── SessionPersistenceTest.java

oryxos-web/src/
├── main/java/io/oryxos/web/
│   ├── agent/{AgentApiController,AgentInvocationWebService,InvokeAgentRequest,InvokeAgentView}.java
│   ├── api/{AgentCallExecutor,AgentCallTimeoutException,GlobalExceptionHandler,Utf8MessageValidator}.java
│   ├── config/{CorsConfiguration,WebExecutionConfiguration}.java
│   ├── memory/{MemoryApiController,MemoryQueryService}.java
│   ├── profile/{ProfileApiController,ProfileView}.java
│   ├── session/{SessionApiController,SessionWebService,CreateSessionRequest,MessageRequest,SessionView,MessageView,MessageReplyView,SessionArchiveView}.java
│   ├── system/{SystemApiController,SystemInfoView}.java
│   └── tool/{ToolApiController,ToolView}.java
└── test/java/io/oryxos/web/
    ├── agent/{AgentApiControllerTest,AgentInvocationWebServiceTest}.java
    ├── api/{AgentCallExecutorTest,Utf8MessageValidatorTest}.java
    ├── memory/MemoryApiControllerTest.java
    ├── profile/ProfileApiControllerTest.java
    ├── session/{SessionApiControllerTest,SessionWebServiceTest}.java
    ├── system/SystemApiControllerTest.java
    ├── tool/ToolApiControllerTest.java
    └── OpenApiContractTest.java

oryxos-boot/src/test/java/io/oryxos/boot/
├── WebServiceAgentIT.java
└── WebServiceSessionRestartIT.java

README.md
website/docs/api.md
website/zh/docs/api.md
```

**Structure Decision**: 沿用 14 模块边界。core 只增加共享只读/生命周期契约；provider/storage 实现各自契约；web 负责 HTTP 适配与公开 View；boot 只承担真实装配 IT。现有 Memory Controller 迁移到查询服务但保留兼容路由。

## Phase 0: Research

研究结论见 [research.md](research.md)。本地 POM、SQLite 版本、Session/Provider/Web 代码已核实，无外部网络依赖。关键结论是：归档代次需要 V6 表重建和 active partial unique index；Provider 列表通过独立 core Catalog；60 秒截止使用受管 Java 21 虚拟线程 Future；Tool 结果正文不进入公共历史。

## Phase 1: Design & Contracts

- [data-model.md](data-model.md) 固定 Session/SessionDetails 分离、代次身份、状态转换、锁与公开 View。
- [contracts/web-api.md](contracts/web-api.md) 固定核心 10 端点、状态码、业务码、公开字段及 core Java 契约。
- [quickstart.md](quickstart.md) 给出无需真实密钥的 core/storage/web/boot 聚焦测试、全仓测试与打包门禁。

设计后宪章复核全部通过。新增 V6 是修正现有 Schema 与已确认产品语义的必要迁移，不提前引入 Session 超时归档、分页、认证或集群协调。

## Implementation Order

1. 测试驱动 Session 随机代次、只读详情、归档契约和 SQLite V6 无损迁移。
2. 测试驱动 ProviderCatalog、UTF-8 校验、统一 60 秒执行器及具体异常映射。
3. 完成 Web Session 创建/消息/历史/归档应用服务与 Controller，并验证并发互斥。
4. 完成无状态 invoke 并用 Boot IT 证明统一 AgentService/审计链路。
5. 完成 Profile/Memory/Tool/System 查询、CORS 与 OpenAPI 10 端点契约。
6. 同步 README/网站，运行聚焦、全仓 test、clean package、网站构建与 Spec-Kit 收敛。

## Complexity Tracking

无宪章违规。V6 表重建是 SQLite 删除内联 UNIQUE 的必要方式；独立 `ProviderCatalog` 是维持 web/core/provider 依赖方向且不破坏函数式 ProviderService 的最小契约。

