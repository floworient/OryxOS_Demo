# 数据模型

## 定义

`ScheduledTaskDefinition` 保留现有 `ScheduleId`、`profileName`、`scheduleKey`、cron、ZoneId 和 message。展示名初始取任务键。Agent 文件格式和 Profile 字段保持现有结构。

## scheduled_tasks

| 列 | 规则 |
|---|---|
| `schedule_id` | 规范小写 UUID 文本，主键 |
| `profile_name`, `schedule_key` | 必填，组合唯一 |
| `display_name`, `cron`, `zone`, `message` | 必填，来自当前协调定义，展示名取任务键 |
| `enabled`, `retired` | 必填，初始 true / false |
| `next_run_at` | 可空，表示计划中的下个 cron 时间。退役置空 |
| `last_run_at`, `last_status` | 初始为空。状态仅为 `success` 或 `failed` |
| `run_count` | 非负整数，初始 0 |
| `updated_at` | 必填，记录更新时间 |

重协调同一键保持状态，刷新定义和下次时间。退役隐藏活动行并保留历史。重建同一个键复用原任务身份。

## task_executions

| 列 | 规则 |
|---|---|
| `id` | 自增主键 |
| `schedule_id` | 引用任务，技术方案允许无法关联的旧历史为空。本分支无旧任务表，新执行必有归属 |
| `session_id` | 可空，Session 获取失败时为空 |
| `started_at`, `duration_ms` | 必填，时间按 UTC 持久化，耗时非负 |
| `success`, `error_message` | 成功标记必填。成功无错误，失败记录可读安全原因 |

按 `(schedule_id, started_at, id)` 支持倒序查询，同一任务时间相同时按主键稳定排序。历史插入和任务运行状态增量更新在同一事务。分列更新避免覆盖并发启停或调度重算。
