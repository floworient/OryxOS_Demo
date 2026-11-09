# 研究与取舍

## 定义、稳定身份与存储

- **决定**：定义来自 `AGENT.md` 的 `schedules`。SQLite 用 V8 Flyway 脚本增加 `scheduled_tasks`、`task_executions`。用现有 `ScheduleId.from(profileName, scheduleKey)` 提供候选 UUID，数据库以组合唯一约束在首次登记时保存，在后续协调中复用。
- **依据**：技术方案 §8.5、§9.2，宪章 V，`008-scheduler-core` 的 SC-004 和现有 `ScheduleId`。技术方案把 ID 写成由 SQLite 生成，本计划选择应用派生、数据库落定，保留第 25 节已经验证的确定性身份。该取舍纳入本次任务评审。
- **其他方案**：数据库随机生成会改变既有 ID 与构造校验，内存登记则不能跨重启恢复。当前 POM、数据源和 Flyway 只有 SQLite，PostgreSQL 部署能力仍延期。

## 调度、超时与安全

- **决定**：复用 `CronTrigger` 的 lenient 计算及时钟。保留 Profile 级互斥，因为同一 Profile 的定时任务共用 Session。用受管虚拟线程增加钟推截止时间，退出前保持锁，避免取消返回时仍有两条任务并发修改会话。
- **依据**：本地 Spring 6.2.14 JAR 已核实 `CronTrigger(String, ZoneId)`、`nextExecution(TriggerContext)` 及 `JdkClientHttpRequestFactory.setReadTimeout(Duration)`。沿用 Provider 和 ToolExecutor 已有时限以及 Notify 的禁止重定向行为。当前普通 `spring-ai-openai` artifact 由项目显式构造模型，使用无 OpenAI key 启动回归来验证无自动装配冲突。
- **其他方案**：仅在 Web 层增加超时会遗漏 cron。以 `Future.cancel(true)` 的返回时刻释放锁会在下游忽略中断时放开并发。持久化异常原文可能泄漏 URL 或密钥。

## 管理和失败口径

- **决定**：技术方案 §8.5 的 v2 五个入口配合课件四个 v1 兼容入口。任务失败表示统一 Agent 调用异常结束或超时。Agent 消化工具错误并正常结束时，拒绝证据在工具审计中检查。
- **依据**：`AgentService.process` 目前返回答复，`ReActLoop` 把工具失败作为结果回填。修改这个既有公共语义会影响人工入口。
- **课件差异**：课件第 3/6 页示例写两次模型调用。技术方案 §12.1 与现有 ReAct 决定最短顺序是模型决定查天气、根据结果决定推送、生成最终答复三次。脚本化验证断言 3 次，真模型按对应 Session 新增的实际回合逐条对账，另断言两次工具调用和一次接收。只有模型完成回复才算成功执行。

## 验证边界

- **决定**：复用 `MockChatModel` 的「记住：」流程验证归档写入。核心记忆恢复另走可控核心写入或真实模型流程。默认恢复验收使用两个独立应用上下文，手动 IT 额外实际终止和重启打包进程。
- **依据**：`SchedulerAgentServiceIT`、`WebServiceSessionRestartIT`、`OryxOsCliPackagingIT`、`NotifyToolAuditIT`、`HumanTriggerFlowIT` 提供现有测试和 opt-in 约定。
- **取舍**：Demo 文档覆盖课件的天气和科技新闻环境前置项。GitHub 日报 Agent、对外 MCP 注册及真实通知凭证由后续 Demo 或操作者提供。
