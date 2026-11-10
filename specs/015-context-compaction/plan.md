# 会话上下文压缩实施计划

> 执行时按 superpowers:executing-plans 或 superpowers:subagent-driven-development 逐项实施。计划评审后生成 Spec-Kit tasks.md，每项行为先写失败测试。

**目标**：限制长会话与单轮工具链的模型输入，使固定样本的累计输入量和估算费用下降。

**架构**：core 选择有界上下文并编排摘要。provider 沿用显式路由和审计，storage 同步持久化完整原文与摘要游标，boot 装配预算和路由配置。

**技术栈**：JDK 21、Maven Wrapper、Spring Boot 3.x、仓库锁定的 Spring AI 1.1.2 / Spring AI Alibaba 1.1.2.2、JPA、Flyway、SQLite、JUnit 5。

**功能**：015-context-compaction · **日期**：2026-11-10 · **状态**：待计划评审

**依据**：[规范](spec.md) · [设计](design.md) · [宪章](../../.specify/memory/constitution.md)

## 全局约束与宪章检查

- I / II：CLI、HTTP 和调度均经 AgentService。PromptBuilder 统一构建主请求。结构化工具链继续由 ReActLoop 和 ToolExecutor 执行，Spring AI 自动执行关闭。
- III / IV：复用现有模块和配置装配。摘要只有显式模型配置才换路由，摘要历史视为数据，预算错误与诊断不输出提示正文或密钥。
- V：SQLite 新增 Flyway 迁移，摘要和原文同事务保存。当前仓库已有可运行迁移仅覆盖 SQLite。两类调用均写原有审计链。
- VI：固定样本和临时 SQLite 无需密钥。供应商缓存和账单单独测量。
- 设计后复核：模块依赖方向、统一入口、Skill 可见性、核心记忆全量、Session 原文和现有工具审计均可保留。无宪章例外。

## 配额与适用范围

预算是本地估算单位。首版按 UTF-8 字节加固定封装余量计算，见[运行契约](contracts/context-budget.md)。主输入默认 8,192，摘要输入默认 4,096，摘要正文 768 字节。配置下限与互斥校验在契约中定义。摘要积攒满四个完整旧轮，或旧历史估算单位达到 2,048 时尝试。一次用户轮最多一次。短会话主要收益来自预算和稳定前缀。

## 实施切片

路径缩写用于下文任务定位。core、provider、storage、boot、web 分别指 oryxos-core、oryxos-provider、oryxos-storage、oryxos-boot、oryxos-web 下标准 Maven 源目录。完整文件路径由模块、源集和 Java 包名组合。

| 切片 | 生产入口与契约 | 首个失败测试及预期 |
| --- | --- | --- |
| 1 · US1、US3（FR-001/002/003/007） | core `io.oryxos.core.context.PromptBuilder#prepare(Session, Profile, Instant): PreparedPrompt`。返回最终 Prompt 和最近历史起始下标。新增同包 `ContextBudget`、`PromptInputEstimator` 和 `ContextBudgetExceededException`。保留 `build(Session, Profile)` 兼容入口。修改 `io.oryxos.core.agent.ReActLoop`，入口捕获一次时间，模型发送前每次重建视图。 | `PromptBuilderTest` 增加 `keepsStablePrefixAndBoundsActiveToolPayload`，比较不同时间和记忆的稳定首消息、断言两个工具 ID 完整、原文保留、发送副本有标记。新增 `rejectsOversizeRequiredContextBeforeAnyProviderCall`，完整用户或核心记忆超限时模型调用为 0。 |
| 2 · US1 配置与错误（FR-003/008） | boot `io.oryxos.boot.ContextBudgetProperties` 绑定 `oryxos.context` 并由 `ReActConfiguration` 注入。web `io.oryxos.web.api.GlobalExceptionHandler` 将预算异常映射为 422 `CONTEXT_BUDGET_EXCEEDED`。CLI 沿用既有退出路径，调度沿用失败执行记录。 | `ReActConfigurationTest` 验证合法覆盖、部分路由配置启动失败、无效配额启动失败。web 增加 `ContextBudgetErrorTest`，422 及统一响应体，内容不含原始输入。 |
| 3 · US2 派生状态（FR-004/008） | core `io.oryxos.core.session.SessionCompaction(String text, int processedUntil)`。Session 暴露 `Optional<SessionCompaction> compaction()` 和 `void updateCompaction(SessionCompaction)`，旧构造入口委托无摘要版本。storage `io.oryxos.storage.entity.SessionEntity` 还原派生状态，`JpaSessionManager` 复用整快照更新。 | storage `SessionCompactionMigrationTest` 用真实 V9 库迁移，断言旧会话无摘要。写入后重新打开并断言摘要、游标、完整工具消息及消息数恢复，历史详情无摘要伪消息。 |
| 4 · US2 摘要调用（FR-003/005/006） | core `io.oryxos.core.provider.ProviderService#chat(String, Profile, Prompt, Profile.ProviderRef, LlmCallPurpose)`；`io.oryxos.core.audit.LlmCallPurpose` 为 CHAT / SUMMARY。provider `io.oryxos.provider.DefaultProviderService` 共用路由、选项构造及审计。storage `JpaLlmCallRecorder`、`entity.LlmCallEntity` 落用途。 | provider `DefaultProviderServiceChatTest` 验证主路由保留、摘要用实际配置模型、工具关闭、输入输出用量和 SUMMARY 审计。storage `LlmCallRepositoryTest` 验证迁移前记录为 CHAT、新记录可按用途区别。 |
| 5 · US2 编排与降级（FR-004/005/006/008） | core `io.oryxos.core.context.SessionSummarizer#compact(Session, Profile, int windowStart): boolean`。由 ReActLoop 在首次主调用前执行一次。返回 true 时使用新摘要与相同预算重建。由 boot `ReActConfiguration` 装配。 | core `SessionSummarizerTest` 验证阈值、预算、增量、重叠、未完成旧轮、过大单轮、空白或含工具意图响应、摘要失败后的游标。 `ReActLoopTest` 验证多迭代只尝试一次、取消与审计失败阻止主调用。 |
| 6 · US1–US3 验收（SC-001–004） | boot `io.oryxos.boot.ContextCompactionIT` 使用真实 core / 存储和可控 Provider。provider 添加回环请求序列化测试，覆盖启用的真实协议适配路径。同步技术方案 §4 与配置示例。 | `ContextCompactionIT` 至少 30 轮，比较累计输入与总费用，断言恢复、工具次数及原文。协议测试检查实际发送 JSON 的消息前缀、工具定义、工具配对和摘要输出限制。 |

每个切片先运行对应测试得到预期 RED，最小实现后同命令得到 GREEN。模块定向命令使用 `./mvnw.cmd -pl <模块> -am "-Dtest=<类名>" "-Dsurefire.failIfNoSpecifiedTests=false" test`。集成和最终回归见 [quickstart](quickstart.md)。

## 评审重点

1. 大量工具 ID、参数或核心记忆单独超限。切片 1 的超限测试证明不会误发主请求。
2. 工具裁剪跨中文或补充平面 Unicode。切片 1 加入边界输入并校验有效 JSON 和 ID。
3. SQLite 旧会话未结束轮后仍有完整轮。切片 3/5 以旧记录验证可加载且摘要继续推进。
4. 摘要已成功但主调用失败。切片 5/6 验证已持久化游标不变，原文与失败审计可追溯。
5. 摘要模型返回工具意图或审计写入失败。切片 4/5 验证工具零执行，审计异常不降级为普通摘要失败。

## 阶段产物

- Phase 0：[研究决策](research.md)。
- Phase 1：[数据模型](data-model.md)、[预算与错误契约](contracts/context-budget.md)、[验收指南](quickstart.md)。
- 计划批准后进入 Phase 2，生成 tasks.md，按上表补齐准确路径、RED/GREEN 命令和需求追踪。
