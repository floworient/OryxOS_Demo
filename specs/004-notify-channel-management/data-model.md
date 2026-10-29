# Notify 渠道管理数据模型

## NotifyChannel（core 领域记录）

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `name` | `String` | 1–64；`[A-Za-z0-9][A-Za-z0-9._-]*`；拒绝 `.`、`..`、设备名和路径形态；不可变主键 |
| `type` | `String` | 小写；当前仅 `webhook` |
| `url` | `String` | 绝对 HTTP(S) URI 或完整 `${ENV_VAR}`；内部真实值 |
| `description` | `String` | 可空；最大 500 字符 |
| `config` | `Map<String,String>` | 不可空、不可变；最多 32 项，键 1–64、值最大 4096 |
| `createdAt` | `Instant` | 创建时由服务时钟写入；更新保持 |
| `updatedAt` | `Instant` | 创建/更新时由服务时钟写入 |

不变量：所有集合防御性复制；`updatedAt >= createdAt`；`config` 不能覆盖保留键 `url`，转换目标时由 resolver 注入已解析 URL。

## NotifyChannelEntity（storage JPA）

表：`notify_channels`

| 列 | SQLite 类型 | 约束 |
| --- | --- | --- |
| `name` | `VARCHAR(64)` | PRIMARY KEY |
| `type` | `VARCHAR(32)` | NOT NULL |
| `url` | `TEXT` | NOT NULL |
| `description` | `VARCHAR(500)` | NULL |
| `config_json` | `TEXT` | NOT NULL，默认 `{}`，由应用严格解码为字符串 Map |
| `created_at` | `TIMESTAMP` | NOT NULL |
| `updated_at` | `TIMESTAMP` | NOT NULL |

索引：`idx_notify_channels_default(created_at, name)` 支持稳定缺省选择。

迁移：`oryxos-storage/src/main/resources/db/migration/sqlite/V4__create_notify_channels.sql`。Hibernate 继续使用 `ddl-auto=validate`。

## NotifyTarget（tool 发送快照）

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `channelType` | `String` | 来自已校验渠道类型 |
| `config` | `Map<String,String>` | 不可变；含规范化且已通过 Sandbox 的 `url` |

`NotifyTarget` 不包含渠道名称、时间戳或 JPA 信息；Adapter 只解释本类型所需配置。

## SandboxAction

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `type` | `ActionType` | 当前 Notify 使用 `HTTP_REQUEST` |
| `target` | `URI` | 由 Sandbox 规范化并批准的绝对 URI |

## API DTO

### CreateNotifyChannelRequest

- `name`: 必填，领域名称规则。
- `type`: 必填，当前为 `webhook`。
- `url`: 必填，HTTP(S) URI或 `${ENV_VAR}`。
- `description`: 可空，最大 500。
- `config`: 可空，缺省 `{}`，只允许字符串键值。

### UpdateNotifyChannelRequest

- `type`: 必填，当前为 `webhook`。
- `url`: 可空；空白、当前掩码表示保留旧值；其他值完整替换。
- `description`: 可空；完整替换。
- `config`: 可空；null 表示保留，Map 表示完整替换，其中掩码/空敏感值按键保留旧值。

路径中的 `name` 不可重命名；改名采用创建新渠道再删除旧渠道。

### NotifyChannelView

- `name`, `type`, `description`, `createdAt`, `updatedAt` 原样返回。
- `url`：环境变量占位符原样；真实 URL 为 `****` + 末 4 字符。
- `config`：敏感键为掩码，其他键返回原值。

## 状态转换

```text
不存在 --POST--> 已注册 --PUT--> 已注册（createdAt 不变） --DELETE--> 不存在
                  |                                      |
                  +---- notify 读取不可变快照 ------------+
```

并发语义：同名 POST 只有一个提交成功；读操作按事务快照返回；删除与发送竞争时，已解析快照可完成本次发送，后续解析失败。

## 校验顺序

1. Web DTO 结构校验。
2. core 领域名称、类型、URL/占位符与 config 校验。
3. storage 唯一约束。
4. 发送时环境变量解析。
5. Sandbox URI 和 allowlist 校验。
6. Adapter 类型解析与网络调用。

任何阶段失败都不得暴露真实 URL，也不得在 Sandbox 前发起网络请求。
