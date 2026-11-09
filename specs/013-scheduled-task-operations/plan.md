# Implementation Plan: 定时任务管理与运行恢复

> 实施者按课程 Skill 使用 `speckit-implement`，以 [tasks.md](tasks.md) 跟踪逐项 RED → GREEN。用户确认任务清单后开始实施。

**Date**: 2026-11-09 | **Spec**: [spec.md](spec.md)

## Summary

延续第 25 节调度器，以 SQLite 保存任务运行状态与历史。自动和手动调用共享定时会话与统一 Agent 链路，Web 和管理台按稳定身份管理。通过隔离、重启及超时场景检验底座行为。

## Technical Context

- JDK 21、Spring Boot 3.5.8、Spring Framework 6.2.14、Spring AI 1.1.2 / Alibaba 1.1.2.2。沿用仓库 Maven Wrapper 和依赖管理。
- Spring Data JPA 3.5.6、Hibernate 6.6.36.Final、SQLite JDBC 3.49.1.0、Flyway 11.7.2。当前运行档仅 SQLite，增加 SQLite V8。记忆默认 Markdown，主验收显式选择现有 SQLite 档。
- Web 使用 Spring MVC、既有 OpenAPI 与 `ApiResponse`。前端沿用 Vue 3、Vite、Vitest 和当前管理台组件。新增第三方依赖为零。
- 调度运行上限 60 秒。HTTP 通知连接 3 秒、读取 10 秒，采用现有 HTTP 客户端的超时配置习惯，使用内部常量。
- 默认构建执行无需密钥的机制验收。两个命名 IT 通过既有 Failsafe 和显式选项手动运行。

## Constitution Check

| 原则 | 设计核对 |
|---|---|
| I 统一内核 | 自动、手动均使用 `AgentService.process`。保留自实现 ReAct、PromptBuilder 和自动 Tool 执行关闭状态。 |
| II 工具统一受控 | 不改变工具注册、过滤、执行、Skill 绑定和审计路径。 |
| III 模块边界 | Core 契约，storage JPA，web 协议和 DTO，boot 负责装配。 |
| IV 安全 | 沿用 Sandbox，通知先校验。记录预定义安全原因与关联 ID，配置示例用占位值。 |
| V 持久化 | 使用 Flyway 和稳定 `scheduleId`，定义源仍为 Agent，进程内保留 Profile 锁保护共享定时会话。 |
| VI 交付真实性 | 测试先行。真实 IT、Demo 环境实际连通性与默认构建分别报告。 |

设计复核通过。课件差异与需评审的实现取舍见 [research.md](research.md)。

## Project Structure

| 路径 | 职责 |
|---|---|
| `oryxos-core/src/main/java/io/oryxos/core/scheduler/` | 扩充现有 Store，增加运行状态/执行视图，统一触发、互斥、取消及生命周期 |
| `oryxos-storage/src/main/java/io/oryxos/storage/` | JPA Store、实体与仓库 |
| `oryxos-storage/src/main/resources/db/migration/sqlite/` | SQLite 向前迁移 |
| `oryxos-web/src/main/java/io/oryxos/web/schedule/` | 两版管理路由、输入校验和协议映射 |
| `oryxos-boot/src/main/java/io/oryxos/boot/` | 调度执行资源和通知超时装配 |
| `frontend/src/components/` 与 `frontend/src/api/` | 定时任务页和 v2 API 客户端 |
| `oryxos-boot/src/test/java/io/oryxos/boot/` | 默认门禁、稳定性/隔离及手动 IT |
| `README.md` | 接口、Demo 前置配置和验证入口 |

## Design

1. 启动扫描 Agent，按 `(profileName, scheduleKey)` 协调任务，复用现有确定性 ID。创建运行行时默认启用。更新定义不覆盖启停、计数或历史。全量登记成功后退役不存在的定义，显式注销退役对应任务，正常关机仅取消句柄。
2. 将 Spring CronTrigger 计算的下一触发点写入运行行，使用相同 CronTrigger 与时钟初始化和续排。停用、重叠跳过仍更新计划触发点，手动运行不移动 cron。触发前在真正执行的 worker 中检查活动代次和启用条件，再取得/复核共享定时会话的 Profile 锁。
3. 钟推和补跑均使用受管虚拟线程执行单次调用。调用方在截止时取消 worker，并以每次执行唯一的完成权记录失败。worker 持锁直到实际退出，迟到返回不得覆盖超时记录。记录历史与计数在一个短事务内提交，Agent/外部 IO 在事务外。
4. 通过 v2 稳定 ID 管理并提供精确 Agent/key 补跑。v1 根据活动 key 解析，匹配多条返回 409。管理台只用 v2，展示操作结果后重新读取状态。合同详见 [contracts/schedules.md](contracts/schedules.md)。
5. 在默认门禁执行实际 Spring Boot + SQLite + Mock Provider 链路，并用可控故障验收重启、隔离与稳定性。真实 Provider、外部天气、接收端通知及进程重启有独立执行步骤。

## Review Focus

- 老库 V7 升级保留已有业务记录，V8 新任务启停与历史原子更新。
- 配置重命名、删除与进程关闭分开处理，跨重启查询旧历史。
- 超时后 worker 未退出时禁止同 Profile 再入，其他 Profile 不受影响。
- 停用及重叠跳过后继续调度，手动补跑不会改变下一次自动触发。
- v1 同键返回 409，v2 定位准确，异常、日志、接口和前端不泄露通知地址及凭证。
