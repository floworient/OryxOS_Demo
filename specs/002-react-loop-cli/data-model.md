# Data Model: 第 17 节

## Session

core Session 持有 id、profileName、channel、userId、完整消息序列、status 和时间。身份按 (channel,user,profile) 生成稳定且无分隔歧义的 ID，例如对三字段长度编码后生成 name UUID。相同三元组恢复同一会话，不同三元组隔离。

`sessions` 列与需求文档第 10 章一致：

| 列 | 规则 |
| --- | --- |
| session_id | TEXT 主键，非空 |
| profile_name、channel、user_id | TEXT，非空，联合唯一 |
| messages_json | TEXT，非空，按显式消息 DTO 编码 |
| status | TEXT，非空，值为 active 或 archived，本节仅创建 active |
| created_at、last_active_at | TIMESTAMP，非空，保存使用统一 UTC Instant |
| archived_at | TIMESTAMP，可空，本节不提供归档操作 |

消息 DTO 保留 role、text、toolCalls(id/type/name/arguments)、toolResponses(id/name/responseData)。只允许 user/assistant/tool，不存系统上下文和 callback 对象。未知 role、损坏 JSON、断裂关联拒绝恢复。恢复后调用及结果顺序与保存前一致。创建 → active → 追加 → 保存 → 恢复；本节不实现自动超时归档。

## ToolInvocation

`tool_invocations` 列严格沿用需求文档第 10 章：

| 列 | 规则 |
| --- | --- |
| id | INTEGER 主键，自增 |
| session_id、tool_name | TEXT，非空 |
| input_json、result_json | TEXT，非空；result_json 为 ToolResult 的显式 JSON |
| success | BOOLEAN，非空 |
| error_message | TEXT，可空；失败必须有可操作原因 |
| duration_ms | BIGINT，非空且 >= 0 |
| created_at | TIMESTAMP，非空 |

每次尝试一行，包括拒绝和重试，不额外加 retry_count 或 tool_call_id 列；工具调用 ID 保留在 Session 消息中。未知工具仍记录请求名称。工具审计先于 Session 最终保存，不设要求 Session 已落盘的外键。拒绝与执行成功不能互相覆盖。

## Profile、工具与 Skill

沿用现有 Profile/OryxTool/ToolCallIntent/ToolResult，不新增 Profile 字段。工具可见性使用 Profile.tools；Agent skills/ 下的 .link 普通相对引用文件集合决定上下文资源可见性，二者不可混为一谈。Skill 元数据为 name/description/已校验的公共 SKILL.md 真实路径，不新增数据库表。

## 迁移

`V2__create_tool_invocations.sql` 和 `V3__create_sessions.sql` 放 `oryxos-storage/src/main/resources/db/migration/sqlite/`，禁止修改已执行 V1 或使用 ddl-auto=update。
