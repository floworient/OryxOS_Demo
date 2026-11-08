# Data Model: 人推 Agent 主流程串联

## SessionSummary

面向 CLI、REST 和控制台的轻量会话投影，不包含消息正文。

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `id` | string | yes | 既有规范 UUID；迁移保留旧主键 |
| `profileName` | string | yes | 对应 Agent/Profile 名称 |
| `channel` | string | yes | 如 `cli`、`web`、`web-invoke`、`scheduler` |
| `userId` | string | yes | 现阶段内网管理视图可见；不得扩展为凭证或通知目标 |
| `status` | `ACTIVE` / `ARCHIVED` | yes | 从持久化小写值安全映射为 core 枚举 |
| `createdAt` | instant | yes | 不晚于 `lastActiveAt` |
| `lastActiveAt` | instant | yes | 列表主排序倒序 |
| `messageCount` | integer | yes | `>= 0`；与保存时 Session 消息数一致 |

### Ordering and filtering

- 默认最多 100 条。
- 主排序 `lastActiveAt DESC`，次排序 `id ASC`。
- 未指定状态时包含活动与归档；公开 HTTP 过滤当前只接受 `active`。

## SessionEntity change

新增 `message_count INTEGER NOT NULL DEFAULT 0`。

- 新建空 Session 为 `0`。
- 每次保存以 `session.messages().size()` 覆盖。
- V7 对既有合法 JSON 数组回填实际元素数；无法安全识别的旧值保留 `0`，不阻断迁移。
- 摘要查询不反序列化 `messages_json`。

## Mock Provider Definition

| Field | Rule |
| --- | --- |
| `name` | 用户配置的唯一名称，示例为 `mock` |
| `type` | `MOCK` |
| `model` | 非空显示名称，示例为 `mock-memory` |
| `baseUrl` | 不使用 |
| `apiKey` | 不使用且不解析 |

Mock 必须由 Agent 的 provider name 显式引用。它不参与 fallback，也不会替代同名或失败的真实 Provider。

## Deterministic interaction state

状态不单独持久化，以当前 Prompt 中的消息序列派生：

```text
用户消息（含“记住：<事实>”）
  -> 无匹配 ToolResponse
  -> 返回 save_memory(content=<事实>, scope=archival)
  -> ReAct/ToolExecutor 写入 ToolResponse
  -> 下一轮 Prompt 存在匹配 ToolResponse
  -> 返回固定最终答复
```

非法状态包括：没有“记住”事实、没有可见 `save_memory` schema、工具结果名称或 callId 不匹配。非法状态明确抛出受控调用失败。

## Audit invariants

标准 Mock 对话完成后，同一 Session 必须满足：

- `llm_calls`: 2 行，均成功，provider/model 与配置一致，token known 为 true。
- `tool_invocations`: 1 行，`tool_name=save_memory`，成功。
- Session messages: 4 条，顺序为 user / assistant(tool call) / tool / assistant(final)。
- 长期记忆: archival 分区新增 1 条事实。

失败场景只要求已发生的调用留痕，不伪造未开始的后续调用。

