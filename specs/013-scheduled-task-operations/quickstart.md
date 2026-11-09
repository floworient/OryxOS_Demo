# 第 28 节验收

以下命令在实现完成后，从仓库根目录运行。验证使用独立临时工作区和 SQLite，避免触发现有工作区的定时任务。

## 默认门禁

```powershell
.\mvnw.cmd -pl oryxos-boot -am "-Dtest=ScheduledTaskE2ETest,AgentSchedulerTest,JpaScheduledTaskStoreTest,ScheduleApiControllerTest,ScheduleStabilityTest,MultiAgentScheduleTest,ScheduleRestartTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
npm --prefix frontend test
npm --prefix frontend run build
.\mvnw.cmd clean verify
```

检查启动登记 0 次、补跑增加到 1 次、执行历史与归档记忆可查、停用后自动触发不执行。核对隔离、超时、故障降级和两个应用上下文的恢复结果。

## 真实链路和进程恢复

在仓库根目录下的本地文件 `.oryxos/lesson28-acceptance.yaml` 填写 `oryxos.providers[0].api-key`。该文件位于 Git 忽略的目录，只用于本节验收。若需在新工作区重建文件，使用以下配置。

```yaml
oryxos:
  providers:
    - name: qwen
      type: DASHSCOPE
      model: qwen-plus
      api-key: ""
```

YAML 留空时可沿用环境变量 `ALIYUN_API_KEY`。两处都未配置时，用例按条件跳过。显式启用后，天气 IT 使用真实模型、天气源和进程内 webhook 接收端，恢复 IT 启动打包产物并终止原进程后从同一工作区再启动。

```powershell
.\mvnw.cmd -pl oryxos-boot -am "-Dit.test=SchedulerFlowIT,RestartRecoveryIT" "-Dfailsafe.failIfNoSpecifiedTests=false" "-Doryxos.scheduler.it=true" verify
```

天气 IT 断言每次成功执行各一次 `http_get`、`notify` 和实际通知接收。按同一 Session 的本次新增消息与审计对应模型回合。标准三轮脚本见默认验收。将通知域名换为未允许项后核对工具失败记录、零次实际通知及后续正常执行。

## Demo 前置

在测试工作区配置天气源 `api.open-meteo.com`、实际 webhook 的最小必要域名，以及仅在通过 HTTP 获取新闻时添加的新闻源域名。项目实际键为 `oryxos.http.allowed-domains`。通过现有通知渠道注册接口配置测试渠道，Agent 正文仅引用渠道名。检查可选新闻 MCP 的工具注册、偏好记忆、显式 `Asia/Shanghai` cron、重启后 Session 与任务状态。使用 README 的示例和 v2 端点核对，真实群送达由操作者确认。
