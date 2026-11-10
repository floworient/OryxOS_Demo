# 任务：会话上下文压缩

依据 [实施计划](plan.md)、[功能规范](spec.md) 与 [预算契约](contracts/context-budget.md)。每个实现切片先运行行为测试并确认预期失败，再实施并复跑。

## 准备

- [x] T001 从 main 建立 codex/015-context-compaction 隔离工作树并复制已批准的 specs/015-context-compaction/。
- [x] T002 在隔离工作树用 mvnw.cmd 建立基线。

## US1 · 输入有界

- [x] T003 [US1] 在 oryxos-core/src/test/java/io/oryxos/core/context/PromptBuilderTest.java 验证预算、Unicode 工具结果裁剪、原文保留和工具配对。
- [x] T004 [US1] 在 oryxos-core/src/main/java/io/oryxos/core/context/PromptBuilder.java 和同包预算类型实现发送视图。在 oryxos-core/src/main/java/io/oryxos/core/agent/ReActLoop.java 接入每次调用前检查。
- [x] T005 [US1] 在 oryxos-boot/src/test/java/io/oryxos/boot/ContextBudgetPropertiesTest.java 验证默认配置和无效组合。在 oryxos-web/src/test/java/io/oryxos/web/api/ContextBudgetErrorTest.java 验证 HTTP 422。
- [x] T006 [US1] 实现 oryxos-boot/src/main/java/io/oryxos/boot/ContextBudgetProperties.java 及 oryxos-web/src/main/java/io/oryxos/web/api/GlobalExceptionHandler.java 的预算错误映射。

## US2 · 滚动摘要

- [x] T007 [US2] 在 oryxos-storage/src/test/java/io/oryxos/storage/SessionCompactionMigrationTest.java 与 oryxos-boot/src/test/java/io/oryxos/boot/ContextCompactionIT.java 验证 SQLite 迁移、原文与摘要恢复、旧审计用途默认值。
- [x] T008 [US2] 修改 oryxos-core/src/main/java/io/oryxos/core/session/Session.java、oryxos-storage/src/main/java/io/oryxos/storage/entity/SessionEntity.java 和 oryxos-storage/src/main/resources/db/migration/sqlite/V10__add_context_compaction.sql 持久化摘要状态及调用用途。
- [x] T009 [US2] 在 oryxos-provider/src/test/java/io/oryxos/provider/DefaultProviderServiceChatTest.java 验证独立路由、SUMMARY 审计、输出上限及关闭工具，再修改 oryxos-provider/src/main/java/io/oryxos/provider/DefaultProviderService.java 和相关 core 审计契约。
- [x] T010 [US2] 在 oryxos-core/src/test/java/io/oryxos/core/context/SessionSummarizerTest.java 与 oryxos-core/src/test/java/io/oryxos/core/agent/ReActLoopTest.java 验证阈值、游标、普通失败、取消和审计失败。
- [x] T011 [US2] 实现 oryxos-core/src/main/java/io/oryxos/core/context/SessionSummarizer.java，在 ReActLoop 首次主调用前接入并通过 T010。

## US3 · 稳定前缀

- [x] T012 [US3] 在 oryxos-core/src/test/java/io/oryxos/core/context/PromptBuilderTest.java 验证时间与记忆变化只影响动态区。
- [x] T013 [US3] 在 oryxos-core/src/main/java/io/oryxos/core/context/PromptBuilder.java 固定稳定前缀，时间仅添加在本轮用户消息副本。
- [x] T014 [US3] 在 oryxos-provider/src/test/java/io/oryxos/provider/OpenAiCompatibleProtocolTest.java 验证两种协议的实际请求中的稳定字段、工具顺序与最大输出参数。

## 交付验收

- [x] T015 在 oryxos-boot/src/test/java/io/oryxos/boot/ContextCompactionIT.java 实现 32 轮回放、费用比较和重启验收。
- [x] T016 更新 docs/TechnicalSolution.md 与配置示例，按 specs/015-context-compaction/quickstart.md 运行验证。

依赖顺序为 T001–T002、US1、US2、US3、交付验收。首个可验证增量为 US1。US2 用受控摘要模型独立验收，US3 用回环协议捕获独立验收。
