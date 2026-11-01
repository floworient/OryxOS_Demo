# Contract: Memory Backends

## Markdown

- owner 路径：Agent → `.oryxos/agents/<name>/MEMORY.md`；global → `.oryxos/memory/MEMORY.md`。
- 拒绝原生链接、边界逃逸和畸形非空旧文件。
- 读写先绑定文件/目录句柄再复验目录身份；无安全目录移动能力的平台只更新已由 `oryxos init` 创建的普通文件，缺文件时 fail-closed。
- 每条新记忆持久化稳定 ID 注释；无注释旧条目可读，并在下次保存时迁移。
- 同 owner 进程内串行追加；不同 owner 独立。
- 上下文裁剪不改写文件。

## SQLite

- Flyway V5 创建 `memory_entries`。
- 写入单事务；读取 `(created_at,id)` 稳定升序。
- Java 执行 locale-independent 关键词匹配和最新 N 选择。

## Mem0 OSS

- 启动：`GET /openapi.json`，只读校验 `/memories` GET/POST、`/search` POST 和 metadata。
- 写：`POST /memories`，`infer=false`，携带 `agent_id`、`metadata.scope`。
- 写入只有在 2xx JSON 的非空 `results` 中取得有效 id 才视为成功；歧义响应 fail-loud。
- 读：`GET /memories?agent_id=<owner>`，本地再次核对 owner/scope。
- 搜：`POST /search`，携带 query/agent_id，本地只保留 archival。
- header：`Content-Type: application/json`、`X-API-Key: ...`。
- 不跟随重定向；写请求不重试；完整响应体受 request timeout 和最大 1 MiB 双重限制。
