# Phase 0 Research: 人推 Agent 主流程串联

## R1 - 当前课件与仓库 Web 路径差异

**Decision**: 延续当前 `/console/`，不实现课件旧快照中的 `/admin/`。

**Rationale**: 010/011 已建立 Vue 3 + Vite 控制台，构建目录为 `static/console`，现有导航和测试均以 `/console/` 为真值。第 27 节核心意图是同进程托管与三面同源，不依赖路径字面值。

**Alternatives considered**: 新增 `/admin/` 别名（扩大公开面且无业务收益）；迁移回 `/admin/`（破坏已交付控制台契约）。

## R2 - SessionManager 兼容演进

**Decision**: 保留现有 `list()`，新增有界 `listRecent` 查询，并由旧方法兼容委托。

**Rationale**: CLI 已依赖 `list()`，直接替换会造成无必要破坏；Web 列表必须限制 100 条并支持状态过滤，不能继续取全集后在 Controller 截断。

**Alternatives considered**: 删除 `list()`（破坏前序能力）；Web 调用 `list()` 后截断/过滤（可能先截断再过滤而漏掉应返回的活动会话，且无界加载）。

## R3 - 消息数量的来源

**Decision**: 用 Flyway V7 增加 `sessions.message_count`，保存时随 Session 更新，摘要查询直接读取。

**Rationale**: 课件要求摘要包含消息数；当前 `SessionSummaryQueryTest` 特意证明摘要不解码 `messages_json`。持久化计数可保持有界查询轻量，并允许损坏历史的记录仍被发现和诊断。

**Alternatives considered**: 每行反序列化 JSON（违背现有轻量查询约束）；SQLite 专用 JSON 函数运行时投影（对损坏 JSON 脆弱）；省略消息数（不满足课件契约）。

## R4 - Mock Provider 的接入层级

**Decision**: 在 `oryxos-provider` 增加显式 `MOCK` 类型和正式 `ChatModel` 实现，但只由配置启用。

**Rationale**: 这样 `DefaultProviderService` 的路由、tool schema、自动执行关闭和 LLM 审计全部走正式路径；测试 Bean 替换只能证明测试装配，不能证明 Provider 配置和显式映射。

**Alternatives considered**: 仅测试源码中的 Stub（无法人工无 key 演示配置）；在 core 直接实现 ProviderService（绕过 provider 映射与审计）；真实模型录制回放（不稳定且可能包含敏感数据）。

## R5 - Mock 脚本状态

**Decision**: 从 Prompt 的消息历史识别阶段，不持有进程级会话状态。

**Rationale**: 第一轮无 ToolResponse，返回 `save_memory`；第二轮看到匹配 ToolResponse，返回最终回复。状态随 Session 历史天然隔离，避免并发 Map 清理和测试顺序污染。

**Alternatives considered**: `sessionId -> step` Map（ChatModel 接口拿不到 OryxOS sessionId）；全局计数器（并发和多会话错误）；按调用顺序轮换（测试顺序依赖）。

## R6 - 真实 Provider 集成测试门禁

**Decision**: `HumanTriggerFlowIT` 同时使用 `@Tag("integration")` 和环境条件；默认构建发现但在无 key 时跳过。

**Rationale**: 仓库当前没有 tag 排除配置。环境条件可保证 `clean verify` 在无密钥环境不失败，Tag 仍便于明确选择和报告真实集成测试。

**Alternatives considered**: 修改全局测试分组（影响全仓策略）；仅用 Tag（当前默认仍会执行）；提交测试密钥（禁止）。

## R7 - 前端会话列表的落点

**Decision**: 在现有会话/对话区域侧栏增加最近会话组件，不新增第八个导航入口。

**Rationale**: 当前“会话”导航已承载聊天工作台；在同一区域加入真实最近会话既保持用户心智，又避免把“聊天”和“历史”拆成重复顶级入口。

**Alternatives considered**: 用历史列表替换 AgentSidebar（失去新对话入口）；新增顶级入口（超出 011 七入口范围）；仅做独立管理卡片（无法自然打开详情对账）。

## R8 - 第三方 API 与依赖核实

**Decision**: 复用当前 Spring AI `ChatModel.call(Prompt)`、`AssistantMessage.ToolCall`、`ChatResponseMetadata/DefaultUsage` API；不新增依赖。

**Rationale**: 仓库现有 `StubChatModel` 和 Provider 测试已在锁定的 Spring AI 1.1.2 上编译使用这些 API。

**Alternatives considered**: 引入新的 Mock LLM 库或升级 Spring AI（没有必要且扩大兼容风险）。

