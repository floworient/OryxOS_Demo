# Data Model: 定时任务核心能力

本阶段所有实体均位于当前 JVM 内存；不新增数据库表。定义来自 `AGENT.md`，不是 Store 的反向配置源。

## ScheduleId

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `value` | String | 必填；标准 UUID 文本；由 profileName 与 schedule key 的带长度边界 UTF-8 身份确定性派生 |

不变量：

- 相同 profileName/key 总是相同。
- cron、zone、message 不参与身份。
- profileName 或 key 任一不同，测试矩阵不得出现冲突。

## ScheduledTaskDefinition

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `scheduleId` | ScheduleId | 必填；必须与 profileName/key 派生值相同 |
| `profileName` | String | 必填、非空 |
| `scheduleKey` | String | 必填、非空；同 Profile 内唯一 |
| `cron` | String | 必填、非空；符合当前 Spring 六段 cron 语法 |
| `zoneId` | ZoneId | 必填；IANA/Java 可解析时区 |
| `message` | String | 必填、非空；不得写入调度日志 |

状态：

- **active**：`reconcile` 后可被调度。
- **retired**：`retire` 后不再是活动定义；内存实现直接从活动集合移除，历史 execution 可继续保留到进程结束。

## TaskExecution

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `scheduleId` | ScheduleId | 必填 |
| `sessionId` | String | 可空；取得 Session 后必填，Session 获取前/期间失败时为空 |
| `startedAt` | Instant | 必填 |
| `finishedAt` | Instant | 必填；不得早于 startedAt |
| `success` | boolean | 必填 |
| `errorType` | String | 成功时为空；失败时为非空异常类名，不含原始消息/堆栈 |

状态转换：

```text
LOCK_ACQUIRED -> STARTED -> SESSION_READY? -> SUCCEEDED | FAILED -> RECORDED?
```

- `LOCK_ACQUIRED` 前的 overlap skip 不产生 TaskExecution。
- `RECORDED` 失败不改变 `SUCCEEDED/FAILED` 的事实，也不能阻止释放锁。

## Scheduled Handle

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `scheduleId` | ScheduleId | Map key，当前进程唯一 |
| `future` | ScheduledFuture | 非空；注销/关闭调用 `cancel(false)` |
| `definition` | ScheduledTaskDefinition | 注册时不可变快照，用于幂等比较 |

同一 Profile 已注册定义集合完全相等时重复注册为 no-op；不同则拒绝，必须显式注销。

## Profile Execution Lock

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `profileName` | String | Map key |
| `lock` | ReentrantLock | 进程内、非分布式；使用 `tryLock`，不得排队 |

锁在 Profile 注销后仍保留到进程结束，防止正在执行的旧任务与同名 Profile 新任务使用两把锁。锁数量受当前进程见过的 Profile 名数量限制。

## Store 关系

```text
ScheduledTaskDefinition 1 --- 0..* TaskExecution
          |
          +--- identified by ScheduleId
```

`ScheduledTaskStore` 是聚合边界：活动定义可退役，execution 按 ScheduleId 追加并以不可变列表读取。本阶段无跨重启恢复承诺。

