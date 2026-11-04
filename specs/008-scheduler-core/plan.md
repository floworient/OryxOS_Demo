# Implementation Plan: 定时任务核心能力

**Branch**: `main`（Spec-Kit feature 标识 `008-scheduler-core`） | **Date**: 2026-11-03 | **Spec**: [spec.md](spec.md)

**Input**: 已确认设计 `docs/superpowers/specs/2026-11-03-scheduler-core-design.md` 与 feature specification。

## Summary

在 `oryxos-core` 增加配置驱动的 `AgentScheduler`、稳定任务身份、最小 `ScheduledTaskStore` 契约和线程安全内存实现，在 `oryxos-boot` 使用 Spring 动态调度能力完成启动注册。触发时固定使用 scheduler Session 并调用既有 `AgentService`；按 Profile 的进程内锁保护共享 Session，失败隔离且记录当前进程执行结果。本阶段不新增数据库表或管理 API。

## Technical Context

**Language/Version**: JDK 21，父 POM Enforcer `[21,22)`

**Primary Dependencies**: Spring Boot 3.5.8、Spring Framework 6.2.14 scheduling API、现有 Spring AI 1.1.2；`oryxos-core` 显式增加 `spring-context` 依赖，不新增第三方库

**Storage**: 默认线程安全内存 Store；既有 SQLite/JPA/Flyway 仅用于 Session、LLM 与 Tool 审计，不新增调度 Schema

**Testing**: JUnit 5、AssertJ、Mockito、Spring Boot test、Failsafe IT；使用可控 TaskScheduler/Provider/Tool 和临时 SQLite

**Target Platform**: Windows 开发机、Linux 单实例私有化部署

**Project Type**: Maven 14 模块单体运行时

**Performance Goals**: 同 Profile 重叠触发在调用 Agent 前立即跳过；不同 Profile 可由 4 线程池并行；不新增吞吐或延迟承诺

**Constraints**: 动态注册、显式时区、稳定 ID、Profile 级进程锁、统一 AgentService 链路；日志不含 schedule message/凭证/完整异常；无自动重试、多实例去重、管理 API 或持久化历史

**Scale/Scope**: 当前进程已加载的 Agent 与 schedules；锁集合最多随当前进程见过的 Profile 数增长，定义与执行快照仅驻留当前 JVM

## Constitution Check

| 原则 | 设计前检查 | Phase 1 后检查 |
| --- | --- | --- |
| I 单一运行时内核 | PASS：调度入口只调用 `AgentService.process`，不复制 ReAct/Prompt/Tool | PASS：contract 和 quickstart 均以真实统一链路为验收 |
| II 工具调用统一受控 | PASS：定时入口不直接执行 Tool，仍由 ReAct + ToolExecutor | PASS：无新工具通道或可见性旁路 |
| III 模块边界与依赖倒置 | PASS：契约/编排在 core，Spring 装配在 boot，不依赖 storage | PASS：内存 Store 实现也在 core，后续 JPA 实现可位于 storage |
| IV 安全边界真实生效 | PASS：调度不绕过 Sandbox，日志禁止消息和原始异常 | PASS：失败契约只记录异常类型，真实 Tool 审计 IT 保留 |
| V 持久化与配置显式契约 | PASS：动态注册、稳定 scheduleId、定义与执行分离；本阶段明确非持久化 | PASS：data model 与 Store contract 分离，未新增假持久化能力 |
| VI 交付真实性与可验证性 | PASS：TDD、无公网桩、真实 AgentService/SQLite IT、限制披露 | PASS：quickstart 含聚焦、全仓、文档及未验证范围 |

无宪章例外。宪章要求稳定 `scheduleId` 和定义/历史分离，本计划在 core 层建立契约；第 28 节的持久化状态与管理能力仍明确延期。

## Project Structure

### Documentation

```text
specs/008-scheduler-core/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── scheduler.md
├── checklists/
│   └── requirements.md
└── tasks.md
```

实现后新增 `verification.md`；Superpowers 的逐步 TDD 计划位于 `docs/superpowers/plans/2026-11-03-scheduler-core.md`。

### Source Code

```text
oryxos-core/
├── pom.xml
└── src/
    ├── main/java/io/oryxos/core/
    │   ├── profile/AgentLoader.java
    │   └── scheduler/
    │       ├── ScheduleId.java
    │       ├── ScheduledTaskDefinition.java
    │       ├── TaskExecution.java
    │       ├── ScheduledTaskStore.java
    │       ├── InMemoryScheduledTaskStore.java
    │       └── AgentScheduler.java
    └── test/java/io/oryxos/core/
        ├── profile/AgentLoaderTest.java
        └── scheduler/
            ├── ScheduleIdTest.java
            ├── InMemoryScheduledTaskStoreTest.java
            └── AgentSchedulerTest.java

oryxos-boot/src/
├── main/java/io/oryxos/boot/SchedulerConfiguration.java
└── test/java/io/oryxos/boot/
    ├── SchedulerConfigurationTest.java
    └── SchedulerAgentServiceIT.java

README.md
website/zh/docs/profile.md
website/docs/profile.md
```

**Structure Decision**: 沿用现有多模块边界。core 包含共享调度契约、纯内存实现和编排；boot 只负责 Spring Bean 生命周期。storage/web 均不修改，避免提前实现后续阶段。

## Phase 0: Research

研究结论见 [research.md](research.md)。本地依赖已确认 Spring Framework 6.2.14 提供 `CronTrigger(String, ZoneId)` 与 `TaskScheduler.schedule(Runnable, Trigger)`；现有 Profile、Session、AgentService、Boot 与测试装配均已读取，没有未解决的 `NEEDS CLARIFICATION`，无需外部网络研究。

## Phase 1: Design & Contracts

- [data-model.md](data-model.md) 固定稳定 ID、定义、执行结果、句柄和 Profile 锁的字段/状态约束。
- [contracts/scheduler.md](contracts/scheduler.md) 固定 core public API、注册事务边界和 Boot 装配契约。
- [quickstart.md](quickstart.md) 给出无需真实密钥的 RED/GREEN、模块测试、真实统一链路 IT、全仓门禁与可选人工 cron 验证。

设计后宪章复核全部通过。Store 只提供本阶段真实使用的方法，不预先声明启停/分页等未实现 API；后续持久化实现通过同一接口替换，不改变 `AgentScheduler` 主链路。

## Implementation Order

1. 先补 schedule 配置校验与稳定身份值对象。
2. 定义 Store 契约并以测试驱动完成内存实现。
3. 测试驱动 `AgentScheduler` 注册/回滚/注销与执行互斥/失败隔离。
4. 完成 Boot 线程池与生命周期装配。
5. 通过真实 AgentService + Provider/Tool + SQLite IT 证明统一链路。
6. 同步文档并运行完整门禁与 Spec-Kit 收敛。

## Complexity Tracking

无未解释的宪章冲突。Profile 级锁比课件示意的 task 级锁更强，是固定 scheduler Session 身份与当前非线程安全 Session 模型共同要求；不同 Profile 仍可并行。

