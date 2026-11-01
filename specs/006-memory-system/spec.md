# Feature Specification: Memory 核心完整交付

**Feature Branch**: `006-memory-system`
**Created**: 2026-10-31
**Status**: Approved
**Input**: User description: "先完成 Memory 核心交付，采用三档后端一次交付方案，并为 015 混合检索预留接口"
**Design Source**: `docs/superpowers/specs/2026-10-31-memory-system-design.md`

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Agent 跨会话记住重要信息 (Priority: P1)

作为 Agent 使用者，我可以让当前 Agent 显式保存用户偏好、项目事实或关键决策；结束会话并重新启动后，该 Agent 仍能在后续 Prompt 中看到这些记忆，而其他 Agent 看不到它们。

**Why this priority**: 跨会话连续性是 Memory 区别于普通 Session 历史的核心价值；默认后端必须先可靠保存、隔离并重新注入记忆。

**Independent Test**: 使用临时工作区和默认 Markdown 后端，让 Agent A 分别保存 core 与 archival 记忆，重建服务并开启新 Session，验证 Agent A 的 Prompt 包含完整 core 与有界 archival，Agent B 和全局 owner 均看不到 Agent A 的内容。

**Acceptance Scenarios**:

1. **Given** Agent A 保存一条 archival 记忆，**When** 下一轮构建 Prompt，**Then** 新记忆立即出现，无需重启或刷新缓存。
2. **Given** Agent A 已保存 core 和 archival，**When** 进程重启并创建新 Session，**Then** core 全量恢复，archival 在字符上限内按完整条目恢复。
3. **Given** Agent A 与 Agent B 均有长期记忆，**When** 分别构建 Prompt，**Then** 各自只能看到自己的长期记忆。
4. **Given** archival 超过上限，**When** 加载长期记忆，**Then** 保留最新完整条目且不切断条目，后端本体不被删除。
5. **Given** core 超过 archival 上限，**When** 加载或关键词召回，**Then** core 仍完整注入且从不参与关键词结果。
6. **Given** 当前 Agent 没有长期记忆，**When** 构建 Prompt，**Then** 仍存在明确的空记忆区块。

---

### User Story 2 - Agent 通过统一工具管理长期记忆 (Priority: P1)

作为 Agent 开发者，我可以把 `save_memory` 和 `recall_memory` 加入 Agent 的可见工具；模型只能通过统一工具执行链保存和召回长期记忆，所有成功与失败调用都留下单次审计，未授权 Agent 无法调用。

**Why this priority**: Memory 写入会改变持久状态，必须与其他 Tool 共享注册、授权、超时、错误和审计边界。

**Independent Test**: 为两个 Profile 配置不同的 `tools`，通过真实 `ToolExecutor` 调用两个 Memory 工具，验证授权、严格参数、默认 scope、Profile 隔离、审计脱敏和失败无副作用。

**Acceptance Scenarios**:

1. **Given** Agent 声明 `save_memory`，**When** 以 content 和 scope 调用，**Then** 内容只写入当前 Agent 的指定分区并产生一条成功审计。
2. **Given** `save_memory` 省略 scope，**When** 调用成功，**Then** 内容写入 archival。
3. **Given** archival 中存在大小写不同的匹配条目，**When** 调用 `recall_memory`，**Then** 返回最新 N 条并按时间升序呈现，不包含 core。
4. **Given** Agent 未声明 Memory 工具，**When** 模型尝试调用，**Then** 工具不执行并产生一条失败审计。
5. **Given** 参数为空、类型错误、scope 未知、含未知字段或超过上限，**When** 调用工具，**Then** 在写入前失败且记忆不变。
6. **Given** content 含个人偏好或敏感文本，**When** 查看审计与错误，**Then** 原始 content 不出现。

---

### User Story 3 - 运维人员按规模切换长期记忆后端 (Priority: P2)

作为私有化部署运维人员，我可以仅修改 `oryxos.memory.backend`，在 Markdown、SQLite 和自托管 Mem0 之间选择长期记忆后端；三档后端保持相同的 owner 隔离、core/archival 分区和失败语义，上层无需修改。

**Why this priority**: 可插拔后端是技术方案“接口墙”的核心承诺，使单机文件、结构化本地存储和外部智能记忆服务可按部署需要选择。

**Independent Test**: 对三个 store 运行同一套契约测试；Markdown 使用临时文件，SQLite 使用临时数据库并跨重启，Mem0 使用回环 fake HTTP server，全程不依赖公网或真实密钥。

**Acceptance Scenarios**:

1. **Given** 未配置 backend，**When** 应用启动，**Then** 选择 Markdown 并使用当前 Agent 的 `MEMORY.md`。
2. **Given** backend 为 SQLite，**When** 写入两个分区并重启，**Then** 记忆按 Agent 与分区恢复，schema 由 Flyway 管理。
3. **Given** backend 为 Mem0 且 OSS 协议满足要求，**When** 保存、加载和召回，**Then** 请求携带 owner 和 scope metadata，core 不进入 search。
4. **Given** Mem0 不可达、认证失败或 OpenAPI 无法证明分区能力，**When** 选择 Mem0 启动，**Then** 应用以脱敏错误 fail-fast，不降级。
5. **Given** 任一后端读写失败，**When** MemoryService 调用它，**Then** 失败不被伪装为空记忆或保存成功。
6. **Given** 管理员切换 backend，**When** 重启应用，**Then** 上层契约不变，但系统不自动迁移旧后端数据。

---

### User Story 4 - 管理端查看运行时实际记忆视图 (Priority: P2)

作为平台管理员，我可以通过只读 REST API 查看某个已注册 Agent 当前实际会注入 Prompt 的长期记忆，包括后端、能力、完整 core 和有界 archival，用于排查行为而不暴露凭证或无限量数据。

**Why this priority**: Memory 属于隐式上下文，只读有界视图能满足核心排障需求，同时不提前实现治理写 API。

**Independent Test**: 使用受控 MemoryService 和 Web MVC 测试已知/未知 Agent、空记忆、后端故障与大 archival，验证统一响应信封、状态码、边界和敏感信息。

**Acceptance Scenarios**:

1. **Given** 已注册 Agent 存在长期记忆，**When** 请求 `GET /api/v1/agents/{name}/memory`，**Then** 返回 owner、backend、capabilities、完整 core 与有界 archival。
2. **Given** Agent 不存在，**When** 查询，**Then** 返回 404 和统一错误信封，不创建数据。
3. **Given** 后端读取失败，**When** 查询，**Then** 返回脱敏 503，不泄漏堆栈、地址、API key 或响应正文。
4. **Given** archival 远超 Prompt 上限，**When** 查询，**Then** API 返回与 Prompt 相同的有界视图。

### Edge Cases

- Agent 名为空、非法、为 `.`/`..`、Windows 设备名、含盘符/UNC/路径分隔符，或 Profile 路径与名称不一致。
- Markdown 文件不存在、为空、只有旧版 `# MEMORY`、分区缺失/重复/乱序、条目损坏、为原生链接，或父目录在检查后被替换。
- 内容含换行、分区 header、Markdown 控制文本、Unicode、大小写特殊字符、极长单条或只有空白。
- 多线程向同一 Markdown owner 并发追加，以及不同 owner 同时追加。
- archival 上限小于单条记忆、多个条目恰好等于上限，或多条记录时间戳相同。
- SQLite 迁移重复运行、数据库重启、事务失败、未知 scope 数据或相同时间戳稳定排序。
- Mem0 base URL 含 user-info/fragment、重定向、OpenAPI 过大或非法、非 JSON、缺 metadata、混入其他 owner、401/403/5xx、超时或响应超限。
- Mem0 写请求结果不确定时不得自动重试。
- recall 无匹配、匹配超过上限、只匹配 core，或不同 locale 下大小写行为不一致。
- Tool 成功但审计持久化失败时保持现有 fail-loud 语义。
- Session 历史为空、超过 `maxHistoryTurns` 或含工具消息时，Memory 不得破坏结构化消息或历史窗口。
- 后端在 Prompt 构建期间失败时不得静默删除记忆区块并继续产生看似正常的回答。

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系统 MUST 在 `oryxos-core` 提供 `MemoryService`，将会话记忆委托给 Session 契约、长期记忆委托给 `LongTermMemoryStore`。
- **FR-002**: `oryxos-core` MUST NOT 依赖 `oryxos-memory`、`oryxos-storage`、`oryxos-web` 或 Mem0 DTO。
- **FR-003**: 系统 MUST 定义 `CORE` 与 `ARCHIVAL`；写入 scope 必须显式传递，工具缺省值仅为 `ARCHIVAL`。
- **FR-004**: 所有后端 MUST 按 Agent owner 隔离；无 Agent 时使用独立 global owner，非法 Agent 名不得映射为其他 owner。
- **FR-005**: 长期后端 MUST 每次重新访问，不缓存；成功保存的记忆须在下一轮 Prompt 可见。
- **FR-006**: core MUST 全量加载、永不截断、不参与关键词召回，也不进入未来向量索引入口。
- **FR-007**: archival MUST 按字符上限选择最新完整条目；运行时裁剪不得删除本体。
- **FR-008**: 关键词召回 MUST 只查 archival，使用 locale-independent 大小写比较；超限时选最新 N 条后升序返回。
- **FR-009**: 后端异常 MUST 以脱敏失败呈现，不得伪装为空或成功。
- **FR-010**: Store MUST 暴露能力声明和未截断 archival 条目视图，为 015 保留稳定接口。
- **FR-011**: 本 feature 只路由 `KEYWORD` 与 `DELEGATED`；未实现的 `HYBRID_BUILTIN` MUST 在装配期失败。
- **FR-012**: Markdown MUST 默认使用 `.oryxos/agents/<name>/MEMORY.md`，无 Agent 时使用 `.oryxos/memory/MEMORY.md`。
- **FR-013**: Markdown MUST 使用 `## 核心记忆` 与 `## 归档记忆` 固定分区和带时间戳单行条目。
- **FR-014**: Markdown 写入 MUST 规范化换行、防止伪造 header，并采用临时文件加原子替换或可验证安全回退。
- **FR-015**: Markdown 路径 MUST 拒绝遍历、绝对/盘符/UNC、原生链接逃逸、非法 owner 和非普通文件。
- **FR-016**: Markdown MUST 防止同一进程内同 owner 并发追加丢失更新。
- **FR-017**: SQLite MUST 持久化 owner、scope、content、createdAt，并使用 Flyway vendor 目录管理 schema。
- **FR-018**: SQLite MUST 跨重启恢复，JPA 继续使用 `ddl-auto=validate`，不得自动 update。
- **FR-019**: SQLite 关键词行为 MUST 与 Markdown 一致，不依赖数据库 collation。
- **FR-020**: Mem0 MUST 只适配自托管 OSS REST API，不混用 hosted Platform 协议。
- **FR-021**: 选择 Mem0 时 MUST 只读检查 OpenAPI 中的 create、owner read、search 和 metadata 能力，不写探测数据。
- **FR-022**: Mem0 请求 MUST 使用 `agent_id` 作为 owner 并以 metadata 保存 scope；缺失 scope 的响应必须失败。
- **FR-023**: Mem0 core MUST 通过 owner read 加 metadata 加载，绝不能通过 search；search 结果必须过滤为当前 owner 的 archival。
- **FR-024**: Mem0 客户端 MUST 设置连接/请求/响应上限、禁重定向、只收 JSON，且写请求不得自动重试。
- **FR-025**: Mem0 地址、API key、认证 header、响应正文和堆栈 MUST NOT 出现在日志、API、工具结果或审计中。
- **FR-026**: Mem0 密钥 MAY 来自环境变量、系统属性或 secret 挂载；仓库配置与示例 MUST 只含占位符。
- **FR-027**: `memory.backend` MUST 支持 `markdown`（默认）、`sqlite`、`mem0`；非法值或缺配置必须阻止启动。
- **FR-028**: 后端切换 MUST NOT 要求修改上层，且本 feature 不自动迁移后端数据。
- **FR-029**: 系统 MUST 将 `save_memory`、`recall_memory` 注册到唯一 ToolRegistry，真实执行必须经过 ToolExecutor。
- **FR-030**: Memory 工具 MUST 服从 `Profile.tools`；未声明、未注册或缺 ProfileContext 时不得执行。
- **FR-031**: 工具 MUST 拒绝非对象 JSON、尾随值、未知字段、错误类型、空内容、未知 scope 和超限输入，失败无副作用。
- **FR-032**: Memory 工具每次 MUST 写入一次最终审计；`save_memory.content` 必须脱敏。
- **FR-033**: PromptBuilder MUST 通过 MemoryService 获取记忆，按 Agent 指令、长期记忆、Session 历史、工具 schema 组装，并保持自动工具执行关闭。
- **FR-034**: Prompt 长期记忆块 MUST 始终存在，并标记为历史信息、不得覆盖当前系统指令。
- **FR-035**: Memory 集成 MUST 保留 `maxHistoryTurns` 和结构化 Session 消息语义。
- **FR-036**: 系统 MUST 提供 `GET /api/v1/agents/{name}/memory`，用 ApiResponse 返回 owner、backend、capabilities、完整 core 与有界 archival。
- **FR-037**: API MUST 只接受已注册 Agent；未知 Agent 返回 404，后端不可用返回脱敏 503，查询无副作用。
- **FR-038**: 自动测试 MUST 使用临时文件/SQLite 和回环 fake Mem0，不依赖公网、真实服务、密钥或模型凭证。
- **FR-039**: 本 feature MUST NOT 实现自动抽取、触发足迹、embedding、RRF、异步索引、对账、情景记忆、摘要压缩或治理写 API。

### Key Entities

- **MemoryOwner**: 长期记忆隔离边界；正常来自已验证 Profile 名，无 Agent 时为 global owner。
- **MemoryEntry**: 不可变长期记忆，包含 owner、scope、content、createdAt 和可选 backend id。
- **MemorySnapshot**: 某 owner 当前运行时可见的完整 core 与有界 archival。
- **MemoryContext**: MemoryService 统一视图，组合长期 MemorySnapshot 与 Session 历史快照。
- **MemoryCapabilities**: 后端能力声明；核心 feature 支持 `KEYWORD` 与 `DELEGATED`，为 `HYBRID_BUILTIN` 保留值。
- **Memory Entry Row**: SQLite 中保存 owner、scope、content 与稳定创建顺序的本体。
- **Mem0 Memory Document**: 自托管 Mem0 中携带 agent_id 与 scope metadata 的外部记录。

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Markdown 场景中，成功保存后下一轮 Prompt 100% 可见；重启后新 Session 仍恢复。
- **SC-002**: 两个 Agent 与 global owner 的隔离测试中，跨 owner 泄漏次数为 0。
- **SC-003**: Markdown、SQLite、Mem0 fake 通过同一套 store 契约测试。
- **SC-004**: 任意 archival 存量下，Prompt/API archival 不超过上限且无半条记录；core 保留率 100%。
- **SC-005**: core 关键词命中和 Mem0 core search 请求次数均为 0；archival 大小写差异命中率 100%。
- **SC-006**: 未授权、非法参数、非法路径和后端失败用例的本体修改次数为 0。
- **SC-007**: 每次 Memory 工具调用恰好 1 条审计；canary content 在审计、日志、API 与工具错误中明文出现次数为 0。
- **SC-008**: SQLite 经过 Spring 上下文和数据库重建后，owner/scope 条目完整恢复。
- **SC-009**: Mem0 fake 覆盖正常、不可达、401、5xx、重定向、非法/超大 JSON、缺 metadata 与超时；写操作最多 1 个请求。
- **SC-010**: API 正常、空数据、未知 Agent、后端不可用分别返回约定 200/404/503，且查询副作用为 0。
- **SC-011**: `mvnw.cmd test` 与 `mvnw.cmd clean verify` 全部通过，ReAct、Session、Tool、审计和模块边界无回归。

## Assumptions

- Session 持久化已由 `SessionManager` / SQLite 完成，本 feature 复用而不重写其 schema。
- Agent owner 采用 `Profile.name()`；ProfileRegistry 与 AgentLoader 仍是注册 Agent 的唯一来源。
- 默认 archival 上限为 4000 字符；其他安全上限在 plan 中给出明确可测试值。
- Markdown 运行时裁剪不等同于磁盘压缩，不物理删除旧 archival。
- Mem0 采用官方 OSS 无 `/v1` 前缀 REST server 并启用 API key；真实互操作需另行提供服务和凭证。
- Mem0 自身可做语义提炼，但 OryxOS 只视其为 `DELEGATED`，不宣称实现 015 内置混合检索。
- Memory API 是排障视图，不是完整导出；写管理 API 属后续治理能力。
- 后端间数据迁移由单独运维任务处理。
