# Research: Memory 核心完整交付

## R1. Owner 隔离与名称校验

**Decision**: 长期记忆 owner 使用 `Profile.name()`；提取 `oryxos-core` 的共享 `AgentName` 值对象/校验器，统一 `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`、`.`/`..` 和 Windows 设备名规则。Markdown 另外验证 `Profile.sourcePath().getParent()` 与 `<workspace>/agents/<name>` 的真实路径关系。

**Rationale**: `WorkspaceService` 已有名称规则但位于 CLI 私有方法，`AgentLoader` 只校验目录边界与 frontmatter 一致。Memory 再复制规则会形成第三份安全契约。

**Alternatives rejected**:

- 只相信 `Profile.name()`：测试或未来 API 可构造非法 Profile，路径后端会受影响。
- 非法 owner 回退 global：会把隔离错误变成跨 Agent 数据泄漏。

## R2. Core 契约与模块边界

**Decision**: `MemoryService`、`LongTermMemoryStore`、`MemoryOwner`、`MemoryEntry`、`MemorySnapshot`、`MemoryContext`、`MemoryScope`、`MemoryCapabilities`、`MemoryException` 放 `oryxos-core/memory`。Markdown/Mem0/门面/工具放 `oryxos-memory`；SQLite 实现与实体放 `oryxos-storage`；装配放 `oryxos-boot`。

**Rationale**: core 只定义共享接口，具体存储实现通过依赖倒置接入，符合宪章 III。

**Alternative rejected**: 把 `LongTermMemoryStore` 放 memory 模块会迫使 storage 依赖 memory，模糊基础设施边界。

## R3. MemoryService 是否复制 Session 历史

**Decision**: `MemoryService.load(session, profile)` 返回 `MemoryContext(session.messages(), snapshot)` 的不可变快照；`PromptBuilder` 继续唯一负责 `maxHistoryTurns` 裁剪。

**Rationale**: 统一门面要真实包含三层视图，但历史窗口规则已经属于 Prompt 组装。门面不做第二次裁剪可避免行为漂移。

**Alternative rejected**: MemoryService 直接渲染 String 会丢失 ToolCall/ToolResponse 结构消息。

## R4. Markdown 格式、解析和裁剪

**Decision**: 文件固定为 `# MEMORY`、`## 核心记忆`、`## 归档记忆`；新条目格式 `- [yyyy-MM-dd HH:mm:ss] <content>`，时区由 `oryxos.memory.zone`（默认系统时区）控制。换行折叠为空格。缺文件时创建；仅有旧版 `# MEMORY` 且无非空正文时升级为空骨架；其他缺失/重复/乱序分区 fail-loud。

archival 按 `"- [timestamp] content\n"` 的渲染字符数计算，从最新向前选择完整条目；若最新单条自身超过 `archival-max-characters`，上下文不返回该条并报告 `truncated=true`，不返回半条。

**Rationale**: 兼容当前 `oryxos init` 创建的空旧文件，同时拒绝对不可判定用户内容做破坏性迁移。

## R5. 并发与原子写

**Decision**: Markdown 使用 `ConcurrentHashMap<MemoryOwner, ReentrantLock>` 进程内细粒度锁；锁内重新读取、解析、追加、写临时文件，再 `ATOMIC_MOVE + REPLACE_EXISTING`，不支持时退回 `REPLACE_EXISTING`。临时文件与目标同目录。

**Rationale**: 避免同 JVM 丢失更新并尽量保证目标文件不出现部分内容。Markdown 明确定位单进程/单机，不宣称跨进程锁。

## R6. SQLite schema 与查询语义

**Decision**: 新增 Flyway V5 表：`id INTEGER PRIMARY KEY AUTOINCREMENT`、`owner_key VARCHAR(64)`、`scope VARCHAR(16)`、`content TEXT`、`created_at TIMESTAMP`，并建立 `(owner_key, scope, created_at, id)` 索引。Repository 只按 owner/scope 稳定升序读取；大小写匹配与最近 N 选择在 Java 完成。

**Rationale**: 保持 SQLite/PostgreSQL 未来一致语义，不依赖 collation；id 解决同一时间戳稳定排序。

## R7. Mem0 OSS 协议

**Decision**: 使用 JDK `java.net.http.HttpClient`，不增加第三方 HTTP 依赖。仅适配自托管 OSS API：`GET /openapi.json`、`POST /memories`、`GET /memories?agent_id=...`、`POST /search`。认证 header 为 `X-API-Key`。所有记录携带 `agent_id` 与 `metadata.scope`。

启动校验只读取 OpenAPI，确认 `/memories` GET/POST、`/search` POST，以及 add/read/search schema 中可承载 `agent_id`、`metadata`/返回 metadata；不写探测数据。官方 OSS 文档明确说明自托管路径不带 `/v1`，与 Platform API 不同：<https://github.com/mem0ai/mem0/blob/main/docs/open-source/features/rest-api.mdx>。

**Rationale**: 避免启动探测污染业务记忆，同时在选择不支持分区的服务时 fail-fast。

**Alternative rejected**: 复用 hosted `/v1`/`/v3` API；这与自托管目标和认证协议不同。

## R8. Mem0 infer 行为

**Decision**: `save_memory` 对 Mem0 发送单条 user message、`infer=false`，将调用方 content 作为本体保存；不允许外部自动提炼改变 core/archival 明确写入语义。

**Rationale**: 本 feature 明确不做自动抽取，三后端应尽量保持可比较的内容语义。未来若允许 `infer=true`，需单独配置与 spec。

## R9. 配置默认值

**Decision**:

| 配置 | 默认值 |
| --- | --- |
| `oryxos.memory.backend` | `markdown` |
| `archival-max-characters` | `4000` |
| `entry-max-characters` | `4000` |
| `recall-max-results` | `20` |
| `zone` | 系统时区 |
| `mem0.connect-timeout` | `3s` |
| `mem0.request-timeout` | `10s` |
| `mem0.max-response-bytes` | `1048576` |

**Rationale**: 与现有文档 4000 字符契约一致；其他值形成明确的资源边界，且可在本地测试稳定验证。

## R10. Memory 工具接入方式

**Decision**: `MemoryTools` 使用 Spring AI `@Tool` 并由现有 `AnnotatedToolDiscovery` 包装为 `OryxTool`，名称固定 `save_memory`、`recall_memory`。执行仍由 `ToolExecutor`；工具必须在 `Profile.tools` 中显式声明。

**Rationale**: 复用刚完成的 Java Tool 适配器与 schema 生成，不再建立特例注册路径。

## R11. 审计脱敏

**Decision**: `ToolExecutor.redact` 增加精确敏感 key `content`，并对 Memory 工具的 input 只保留 scope、字节数和 SHA-256；`keyword` 可审计但设置长度上限。错误只返回稳定中文原因。

**Rationale**: 长期记忆经常包含个人偏好或企业事实，复制原文到审计表扩大数据暴露面。

## R12. API 语义

**Decision**: `GET /api/v1/agents/{name}/memory` 先通过 `ProfileRegistry` 查 Agent，再调用 `MemoryService.loadLongTerm(profile)`；返回有界 `MemorySnapshot`。未知 Agent → 404 `AGENT_NOT_FOUND`，`MemoryException` → 503 `MEMORY_UNAVAILABLE`。

**Rationale**: 查询必须无副作用，且管理端看到的是 Agent 实际 Prompt 可见视图，而非无上限导出。

## R13. 015 预留边界

**Decision**: `MemoryCapabilities` 是单值 enum：Markdown/SQLite 返回 `KEYWORD`，Mem0 返回 `DELEGATED`；保留 `HYBRID_BUILTIN`。`archivalEntries(owner)` 返回未裁剪本体；Mem0 返回空列表。当前门面遇到 `HYBRID_BUILTIN` 抛装配错误。

**Rationale**: 015 可在不破坏 Store/Service 调用方的情况下加入检索管线和索引对账，同时本 feature 不提供假混合检索。

