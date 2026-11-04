# Quickstart: 定时任务核心能力验证

## 前置条件

- JDK 21；优先使用仓库 Maven Wrapper。
- 不需要真实 Provider key、外网、Webhook 或企业 IM。
- 保留工作区既有未提交修改，不执行 reset/checkout/clean 文件操作。

## 1. 配置与值对象聚焦测试

```powershell
.\mvnw.cmd -pl oryxos-core -Dtest=AgentLoaderTest,ScheduleIdTest,InMemoryScheduledTaskStoreTest test
```

预期：合法 cron/zone 被接受；非法配置整 Agent 拒绝；稳定 ID、Store 线程安全与不可变快照测试通过。

## 2. 调度器行为测试

```powershell
.\mvnw.cmd -pl oryxos-core -Dtest=AgentSchedulerTest test
```

预期：注册 cron/zone 正确，幂等与首次失败回滚通过；同 Profile 重叠跳过、不同 Profile 独立；异常后锁释放，Session/Agent/Store 失败均被隔离。

## 3. Boot 装配与真实统一链路

```powershell
.\mvnw.cmd -pl oryxos-boot -am -Dtest=SchedulerConfigurationTest test
.\mvnw.cmd -pl oryxos-boot -am -Dit.test=SchedulerAgentServiceIT verify
```

预期：

- 默认 4 线程 Scheduler、内存 Store 和 AgentScheduler 生命周期装配存在。
- 捕获的调度回调使用固定 scheduler Session，并经过真实 `AgentService -> ReActLoop -> ProviderService -> ToolExecutor`。
- 临时 SQLite 中对应 Session、`llm_calls` 和 `tool_invocations` 可查询；不存在第二条调度专用执行链。

## 4. 模块与全仓门禁

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-boot -am test
.\mvnw.cmd clean verify
git diff --check
```

如网站文档发生变化：

```powershell
npm --prefix website run docs:build
```

预期：全部命令退出码为 0。若全仓门禁因既有、与本功能无关的工作区改动失败，必须记录原始失败及聚焦测试结果，不得弱化测试或修改无关代码掩盖问题。

## 5. 可选人工 cron 验证

在隔离测试工作区创建带每分钟规则的 Agent，配置可控 Provider/Tool，启动 OryxOS 并等待一次真实触发。确认：

1. 无人工输入也会创建 scheduler Session。
2. Agent 完整运行并产生既有 LLM/Tool 审计。
3. 修改 cron 后重启，按新规则登记。

真实等待只能作为补充体感验证，不能替代自动测试。缺少可控 Provider 或未实际等待时，在 `verification.md` 明确标记未验证。

## 范围核对

本阶段完成：启动动态注册、显式时区、稳定 ID、Profile 级单进程防重叠、失败隔离、内存定义/执行记录和运行时注册/注销方法。

本阶段不完成：Flyway 调度表、重启恢复、列表/启停/立即执行 API、cron 热修改、自动重试、失败告警、多实例去重或分布式锁。

