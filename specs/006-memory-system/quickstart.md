# Quickstart: Memory 核心能力

## 1. 默认 Markdown 后端

`application.yaml` 无需额外配置，默认：

```yaml
oryxos:
  memory:
    backend: markdown
    archival-max-characters: 4000
    entry-max-characters: 4000
    recall-max-results: 20
```

在 Agent `AGENT.md` 的 `tools` 中显式加入：

```yaml
tools:
  - save_memory
  - recall_memory
```

运行后，长期记忆写入 `.oryxos/agents/<agent-name>/MEMORY.md`。

## 2. SQLite 后端

```yaml
oryxos:
  memory:
    backend: sqlite
```

启动时 Flyway V5 创建 `memory_entries`。不要启用 `ddl-auto=update`。

## 3. 自托管 Mem0

```yaml
oryxos:
  memory:
    backend: mem0
    mem0:
      base-url: ${MEM0_BASE_URL}
      api-key: ${MEM0_API_KEY}
      connect-timeout: 3s
      request-timeout: 10s
      max-response-bytes: 1048576
```

Mem0 必须是官方 OSS REST server，路径不带 `/v1`，并能够在 metadata 中往返保存 `scope`。启动会只读检查 `/openapi.json`，不会写探测数据。

## 4. 验证跨会话

1. 让 Agent 调用 `save_memory` 保存 archival 偏好。
2. 检查该调用在 `tool_invocations` 中只有一条审计且 content 已脱敏。
3. 新建/恢复另一个 Session，再次调用 Agent。
4. 验证 Prompt 记忆块包含该偏好。
5. 调用 `GET /api/v1/agents/<name>/memory` 查看同一有界视图。

## 5. 自动验证

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-memory,oryxos-storage,oryxos-web,oryxos-boot -am test
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

真实 Mem0 互操作需要用户提供服务和凭证，未提供时只报告 fake server 集成结果。
