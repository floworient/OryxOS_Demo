# 数据模型：会话上下文压缩

| 实体 | 字段与约束 | 持久化 |
| --- | --- | --- |
| 原始 Session | 现有会话 ID 和有序消息。轮次从用户消息开始，工具回复以调用 ID 配对。原文只追加。 | 现有 `sessions.messages_json` |
| SessionCompaction | `text` 为非空摘要。 `processedUntil` 是从 0 起的排他消息下标，必须大于 0、未越过原始历史且位于轮次边界。 | `sessions.context_summary TEXT NULL`、`context_summary_until INTEGER NOT NULL DEFAULT 0` |
| PreparedPrompt | `prompt` 和 `windowStart`，后者为仍纳入的最早用户消息的原始索引。 | 单次模型调用视图 |
| 模型调用 | 既有审计字段加 `purpose`，值为 CHAT 或 SUMMARY。 | `llm_calls.purpose TEXT NOT NULL DEFAULT 'CHAT'` |

SQLite 迁移文件为 `oryxos-storage/src/main/resources/db/migration/sqlite/V10__add_context_compaction.sql`。无摘要状态由 NULL 与 0 共同表示。恢复时校验配对和游标范围。归档、恢复和重命名保留派生字段。Session 历史查询与消息计数继续以原始消息为准。

摘要有效时游标单调递增。先在当前 Session 内更新，整轮成功后随原文持久化。普通摘要失败沿用旧状态。扩大窗口导致覆盖范围重叠时暂不注入旧摘要，游标保持。处理位置可跨过已跳过的未完成旧轮，因此它表示扫描边界。
