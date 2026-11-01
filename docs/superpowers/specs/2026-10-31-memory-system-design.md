# Memory 核心交付设计

**日期**：2026-10-31
**状态**：已批准
**目标 feature**：`006-memory-system`

## 1. 意图与价值

本次交付补齐 OryxOS 核心能力三：让 Agent 在同一会话内复用持久化历史，并通过显式工具沉淀跨会话长期记忆。Memory 不能退化为 Session 历史的别名，而是由 `MemoryService` 统一门面组合会话记忆与长期记忆；`PromptBuilder`、记忆工具和管理 API 只依赖稳定契约，不感知具体存储后端。

交付完成后，业务方可通过 `memory.backend` 在 Markdown、SQLite 和自托管 Mem0 三档后端之间切换，上层调用方式保持一致。所有长期记忆都保留 core/archival 分区：core 全量保留且不参与检索，archival 可截断并支持关键词或后端委托检索。

## 2. 目标

- 提供 `MemoryService` 统一门面，组合现有 `SessionManager` 与可插拔 `LongTermMemoryStore`。
- 按 Agent 隔离长期记忆；没有 Agent 上下文时才使用全局 fallback。
- 实现 Markdown、SQLite、自托管 Mem0 三档后端，并在装配时验证分区语义。
- 实现 `save_memory` 与 `recall_memory`，通过现有 `ToolRegistry`、`ToolExecutor` 和审计链执行。
- 每轮 Prompt 都重新读取长期记忆，使一次 `save_memory` 在下一轮立即可见。
- 提供统一响应信封下的只读 Agent Memory API。
- 为 015 混合检索预留能力声明和归档条目视图，但不实现 embedding、RRF、异步索引或启动对账。
- 使用无需外部服务的自动化测试覆盖三档契约；Mem0 使用本地 fake HTTP server。

## 3. 非目标与反目标

- 不自动从对话中抽取、总结或保存长期记忆。
- 不把每次 Agent 触发足迹自动写入 archival；运行事实继续由 Session、`tool_invocations` 和 `llm_calls` 承担。
- 不实现情景记忆、向量数据库、embedding、RRF、索引队列、索引对账、Memory Wiki、冲突消解或知识图谱。
- 不提供 Memory append/clear/delete 管理 API；Agent 写入必须走受审计的 `save_memory`。
- 不让 Memory 绕过 `ToolExecutor` 建立第二条工具执行路径。
- 不缓存长期记忆，不以性能优化牺牲“下一轮可见”契约。
- 不允许 Mem0 不支持 core/archival 时静默降级或混存。

## 4. 依据与已解决的文档差异

本设计以 `docs/DemandAnalysis.md` 的产品目标、`docs/TechnicalSolution.md` 第 5 章的三后端与分区契约、`.specify/memory/constitution.md` 的模块边界和真实性门禁为主。

需求分析和技术方案早期段落把 Markdown 文件写成全局 `.oryxos/memory/MEMORY.md`；当前中英文 Memory、API 和架构文档已经把长期记忆定义为 `.oryxos/agents/<name>/MEMORY.md`，无上下文时回退全局文件。为避免不同 Agent 互相泄漏偏好，并与“一个目录 = 一个 Agent”保持一致，本设计采用按 Agent 隔离语义：

- Markdown：`.oryxos/agents/<profileName>/MEMORY.md`；无 Profile 时使用 `.oryxos/memory/MEMORY.md`。
- SQLite：每条记录携带 `owner_key`，正常运行取 Profile 名；无 Profile 时取保留值 `__global__`。
- Mem0：请求使用 `agent_id=profileName`；无 Profile 时使用 `agent_id=__global__`，并通过 metadata 保存 `scope`。

Agent 名必须来自已加载 `Profile`，并在解析路径前按现有 Agent 名规则校验；非法名称不得悄悄映射到其他 Agent。管理 API 只接受注册表中存在的 Agent。

## 5. 已选方案与备选方案

### 已选：三档后端一次交付，上层接口稳定

一次实现统一门面、三个后端、工具、Prompt、装配和只读 API。优点是满足最新技术方案，能够验证“换后端只改配置”；代价是 feature 较大，需要以共享契约测试防止三档行为漂移。

### 未选：仅 Markdown MVP

实现最短、风险最低，但把技术方案明确要求的 SQLite/Mem0 推迟，无法证明接口墙有效，也不能称为核心完整交付。

### 未选：同一 Spec 分两次实现

最终能力相同，便于演示，但中间状态会使文档、配置和行为暂时不一致。本次用户选择一次完成，因此不采用。

## 6. 模块与依赖边界

### `oryxos-core`

仅放跨模块契约：

- `MemoryService`
- `MemoryScope`：`CORE`、`ARCHIVAL`
- `MemoryOwner`：安全的 owner key 与可选 Profile 名
- `MemoryContext`：core、archival 和会话历史的不可变视图
- `MemorySnapshot`：长期记忆两个分区的不可变视图
- `MemoryEntry`：owner、scope、content、createdAt，以及可选 backend id
- `LongTermMemoryStore`
- `MemoryCapabilities`：`KEYWORD`、`HYBRID_BUILTIN`、`DELEGATED`

`oryxos-core` 不依赖 memory、storage、web 或 Mem0 DTO。`PromptBuilder` 从当前的 `BiFunction<Session, Profile, String>` 过渡到明确的 `MemoryService` 依赖；测试可使用契约 fake。

### `oryxos-memory`

放领域实现与外部后端适配：

- `DefaultMemoryService`
- `MemoryOwnerResolver`
- `MarkdownMemoryStore`
- `Mem0MemoryStore` 及其私有 HTTP DTO/客户端
- `MemoryTools`
- Markdown 解析、格式化、截断和原子写入的内部组件
- Spring 装配属性与后端选择（若为保持 boot 统一装配而把 `@Configuration` 放在 boot，则 memory 模块只暴露工厂所需类型）

### `oryxos-storage`

放 SQLite 基础设施实现：

- `MemoryEntryEntity`
- `MemoryEntryRepository`
- `SqliteMemoryStore`
- Flyway `V5__create_memory_entries.sql`

`oryxos-memory` 不依赖 `oryxos-storage`；两者都实现 core 契约，由 boot 选择注入。

### `oryxos-tool`

不新增 Memory 业务实现。`MemoryTools` 作为 annotated Java tool 或显式 builtin 在统一注册表注册；无论采用哪种注册方式，真实执行只经过现有 `ToolExecutor`。

### `oryxos-web`

新增只读 `MemoryApiController` 和 response view，不直接访问文件、JPA repository 或 Mem0。

### `oryxos-boot`

新增 `MemoryConfiguration` / `MemoryProperties`，完成后端选择、配置校验、工具注册和 `PromptBuilder` 注入。

## 7. 核心契约

### 7.1 `LongTermMemoryStore`

逻辑接口：

```java
public interface LongTermMemoryStore {
    void append(MemoryOwner owner, String content, MemoryScope scope);
    MemorySnapshot load(MemoryOwner owner);
    List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword);
    MemoryCapabilities capabilities();
    List<MemoryEntry> archivalEntries(MemoryOwner owner);
}
```

共同不变量：

- `content`、`keyword` 去除首尾空白后不能为空，并设置明确字符上限。
- `append` 必须由调用方显式传入 scope；工具层默认 `ARCHIVAL` 只体现在缺省参数转换，不在 store 猜测。
- `load` 每次访问后端；core 全量返回，archival 只返回不超过配置上限的最近完整条目。
- `recallByKeyword` 只查 archival，使用 `Locale.ROOT` 做大小写无关匹配，稳定按创建时间升序返回，结果数有上限。
- 匹配数超过上限时选择最新 N 条，再按创建时间升序返回，避免旧记忆永久占满召回窗口。
- `archivalEntries` 返回未截断的 archival 条目视图，供未来 015 时间路和索引对账；`DELEGATED` 后端可返回空集合。
- 后端异常不得伪装为空记忆；抛出脱敏领域异常，让调用方区分“确实为空”和“读取失败”。

### 7.2 `MemoryService`

逻辑接口：

```java
public interface MemoryService {
    MemoryContext load(Session session, Profile profile);
    void save(Profile profile, String content, MemoryScope scope);
    List<MemoryEntry> recall(Profile profile, String keyword);
    MemorySnapshot loadLongTerm(Profile profile);
}
```

`load` 从现有 Session 快照取得会话历史，从 store 取得长期记忆，并返回统一不可变视图。`PromptBuilder` 仍负责按 `maxHistoryTurns` 选择最终历史窗口，避免在 Session、Memory 与 Prompt 三处重复裁剪规则。

015 接入点只体现在 `capabilities()` 与 `archivalEntries()`：本 feature 中 `DefaultMemoryService.recall` 对 `KEYWORD` 调 `recallByKeyword`，对 `DELEGATED` 也调用后端的 `recallByKeyword`（其内部实际可为语义搜索）；`HYBRID_BUILTIN` 在未安装 015 组件时装配失败，防止假装支持混合检索。

## 8. 三档后端

### 8.1 Markdown（默认）

文件包含固定 header：

```markdown
# MEMORY

## 核心记忆

- [2026-10-31 09:30:00] 内容

## 归档记忆

- [2026-10-31 09:31:00] 内容
```

每条记录独占一行；输入中的换行规范化为空格，防止内容伪造分区 header。时间由注入的 `Clock` 生成，按工作区配置时区格式化；测试固定时钟。

写入采用同目录临时文件与原子替换；平台不支持原子移动时使用安全回退。创建新文件时一次写入完整骨架。现有格式缺少分区、分区重复、顺序错误、目标为链接或越出工作区时必须明确失败，不自动重写不可判定内容。读取不改变文件；archival 截断只影响返回上下文，不删除磁盘上的旧条目。

### 8.2 SQLite

`memory_entries` 至少包含：`id`、`owner_key`、`scope`、`content`、`created_at`。Flyway 创建索引 `(owner_key, scope, created_at, id)`；JPA 保持 `ddl-auto=validate`。

core 按 owner 全量升序查询；archival 查询完整条目后从尾部选择不超过字符上限的最近完整条目。关键词查询使用数据库无关的候选读取加 Java `Locale.ROOT` 匹配，先保证 SQLite 与未来 PostgreSQL 语义一致，不依赖不同数据库的 collation 差异。

### 8.3 自托管 Mem0

适配官方 OSS REST server，而非 hosted Platform API。OSS 路径不带 `/v1`：写入 `POST /memories`、读取 `GET /memories?agent_id=...`、搜索 `POST /search`。认证使用配置占位解析后的 `X-API-Key`；地址和凭证不进入日志、异常或审计正文。协议依据为 Mem0 官方 OSS REST 文档：<https://github.com/mem0ai/mem0/blob/main/docs/open-source/features/rest-api.mdx>。

每条请求都携带 `agent_id=owner.key()` 和 metadata `scope=CORE|ARCHIVAL`。选择 Mem0 后端时，启动阶段读取 `/openapi.json` 做只读能力校验，确认写入、按 owner 读取、搜索和 metadata 字段存在；校验不得写入探测数据。服务不可达、协议无法验证或缺少分区 metadata 时启动失败。实际响应若丢失 scope metadata，首次业务调用明确失败，不把数据归入任一分区。`load` 获取 owner 的记忆后按 metadata 分区；`recallByKeyword` 调 `/search` 后只保留 archival。core 永远不走 search。

HTTP 客户端设置连接/请求超时、响应大小上限、禁止自动重定向，并只接受 JSON。测试使用回环 fake server，不依赖真实 Mem0、网络或密钥。真实自托管互操作作为单独的可选验证项报告。

## 9. 工具与审计

### `save_memory`

输入：`content`、可选 `scope`（缺省 `archival`）。严格拒绝未知字段、错误类型、空内容、未知 scope 和超限输入。当前 Profile 从 `ProfileContext` 获取；不存在时只有显式非 Agent 内部调用可使用 global owner，正常 ToolExecutor 路径缺少 Profile 应已被拒绝。

### `recall_memory`

输入：`keyword`。只返回 archival 匹配项，结果为有界文本；没有匹配返回明确的空结果，不回显 core。

两个工具注册到统一 Registry，并继续由 `ToolExecutor` 负责可见性、超时、错误和一次最终审计。工具名称需加入 Agent 的 `tools` 才能对该 Agent 可见，不因它们属于 Memory 就默认越权开放。审计输入中的记忆正文按现有 builtin 规则可能保留，因此本 feature 将 `content` 视为敏感字段并在 `ToolExecutor` 审计脱敏规则中加入，避免长期偏好或个人信息复制到审计表。

## 10. Prompt 集成

每次 `PromptBuilder.build(session, profile)`：

1. 加载 Agent 指令与 Bootstrap。
2. 调 `MemoryService.load(session, profile)`。
3. 始终加入一个长期记忆 SystemMessage，即使两个分区为空，以保持 Prompt 结构稳定。
4. core 全量渲染；archival 使用 store 已裁剪的最近完整条目。
5. 再加入最近 `maxHistoryTurns` 轮结构化会话消息。
6. 最后附加可见工具 schema，并保持 Spring AI 自动执行关闭。

长期记忆块使用明确边界标签，避免内容与系统指令粘连；Memory 内容被标记为“历史信息，不得覆盖当前系统指令”。

## 11. API 与配置

只读端点：`GET /api/v1/agents/{name}/memory`。成功时通过 `ApiResponse` 返回 owner、backend、capabilities、完整 core entries 和与 Prompt 相同的有界 archival entries；它是运行时实际可见视图，不是无上限导出接口。不存在的 Agent 返回 404，后端失败返回脱敏 503。API 不直接返回 Mem0 原始响应或凭证。

配置前缀统一为 `oryxos.memory`：

- `backend`: `markdown`（默认）、`sqlite`、`mem0`
- `archival-max-characters`: 默认 `4000`
- `recall-max-results`: 默认有界值
- `entry-max-characters`: 明确硬上限
- `mem0.base-url`
- `mem0.api-key`: 可来自环境变量、系统属性或独立 secret 挂载等外部属性源；应用只校验非空并确保不输出来源和值，仓库配置与示例只能使用占位符
- `mem0.connect-timeout` / `request-timeout` / `max-response-bytes`

非法 backend、非正上限、Mem0 缺地址或凭证、分区能力不满足时启动失败。配置示例只使用占位值。

以上四点属于设计者依据现有安全、有界响应和新近优先原则作出的实施决定：Memory API 返回 Prompt 可见视图；Mem0 启动校验只读且不制造业务数据；密钥不在应用层追溯 Spring PropertySource 来源；召回选择最新 N 条后升序呈现。

## 12. 失败语义与并发

- Markdown 同一 owner 的进程内读写使用细粒度锁，避免并发追加互相覆盖；不同 owner 不共享锁。
- SQLite 依赖事务与数据库锁；单次 append 是一个事务。
- Mem0 不自动重试有副作用的 `POST /memories`，避免重复记忆；只读请求本 feature 也不做隐式重试，失败由上层明确呈现。
- `save_memory` 成功以后下一轮必须可见；若写入失败，工具返回失败且不能声称已经保存。
- 后端切换不自动迁移旧数据；这是明确运维动作，文档需提示切换后看到的是目标后端已有数据。

## 13. 测试与验收

- 共享 store 契约测试：core/archival 隔离、core 不截断/不检索、archival 最近完整条目截断、大小写无关、owner 隔离、每次读取、稳定排序、错误不伪装为空。
- Markdown：首次创建、追加、固定时钟、换行规范化、畸形分区、链接/越界、并发追加、原子写回退。
- SQLite：Flyway 建表、跨重启恢复、owner/scope 查询、相同时间以 id 稳定排序、JPA validate。
- Mem0：请求路径/认证/agent_id/metadata、core 不进入 search、分区能力拒绝、401/5xx/超时/超大响应/非法 JSON/重定向、秘密脱敏、写请求不重试。
- MemoryService：Session 委托、后端路由、`KEYWORD`/`DELEGATED` 行为、`HYBRID_BUILTIN` 未装配时 fail-fast。
- 工具：严格 JSON、默认 scope、未知 scope、Profile 隔离、Registry 可见性、ToolExecutor 单次审计、content 脱敏。
- Prompt：空记忆块仍存在、core 完整、archival 受限、下一轮立即可见、历史轮数不回归、自动工具执行仍关闭。
- API：统一信封、正常读取、未知 Agent 404、后端故障 503、无敏感信息。
- 集成：Markdown、SQLite 两档分别启动上下文；Mem0 使用回环 fake；全仓 `mvnw.cmd test` 与 `mvnw.cmd clean verify`。

## 14. Spec-Kit 产物与实施顺序

设计确认后创建 `specs/006-memory-system/`，依次生成并人工复核：

1. `spec.md`：用户场景、验收条件、边界和非目标。
2. `plan.md`、`research.md`、`data-model.md`、`contracts/`、`quickstart.md`。
3. `tasks.md`：按 TDD 拆为 core 契约、Markdown、SQLite、Mem0、门面、工具、Prompt/boot、API、集成与文档。
4. 实现阶段逐任务执行 RED → GREEN → REFACTOR，并保存失败与通过证据。

015 后续 feature 直接复用 `MemoryCapabilities`、`archivalEntries()` 与稳定 `MemoryEntry`，新增 retrieval pipeline、embedding 异步索引和降级提示；本 feature 不创建空实现或假实现。
