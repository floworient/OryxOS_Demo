# Memory 核心交付验证记录

验证日期：2026-10-31；评审复验日期：2026-11-01

## 交付范围

- 已交付统一 `MemoryService` 与 `LongTermMemoryStore` 契约，按 Agent/global owner 和 core/archival scope 隔离。
- 已交付 Markdown 默认后端、SQLite/Flyway V5 后端和 Mem0 OSS REST 后端；后端通过 `oryxos.memory.backend` 切换。
- core 完整注入且不截断、不参与召回；archival 有界注入并支持关键词召回。
- 已交付 `save_memory`、`recall_memory`，统一经过 `ToolExecutor`、可见性策略和审计；保存正文仅以 scope、字节数和 SHA-256 摘要进入审计。
- 已交付 Prompt、CLI 初始化骨架、只读 `GET /api/v1/agents/{name}/memory` 和跨重启恢复链路。
- `HYBRID_BUILTIN` 能力只作为 015 混合检索预留；当前装配会明确失败，不静默启用未完成能力。

## TDD 证据摘要

实现过程按任务清单逐项执行 RED → GREEN。关键 RED 包括：Memory 契约和实现类缺失、Prompt 构造器尚未接入 Memory、保存正文进入审计、SQLite 缓存表缺失、Mem0 协议客户端缺失、后端选择不能装配、Memory API Controller 缺失，以及 CLI 旧骨架不含双分区。对应实现后均由同名定向测试转为 GREEN。

最终门禁还真实发现并修复了以下问题：

1. Markdown 原子写路径的父目录空值静态告警；改为显式校验父目录。
2. Web `MemoryView` 返回可变集合；改为防御性 `List.copyOf`，容器托管的 Registry 依赖按既有 SpotBugs 规则精确排除。
3. 精简的 `ToolRegistryAuditIT` 上下文遗漏 `MemoryConfiguration`；补齐真实生产配置导入后回归测试通过。
4. `MemoryProperties.mem0` 暴露可变嵌套配置；改为不可变 record 构造器绑定，并重跑全部七个后端装配测试。
5. Mem0 OSS 实际读/搜响应使用 `results` envelope；修正解析器，并让 Mem0 运行完整 StoreContract。
6. 三个后端的正文规范化、稳定排序、关键词结果截取和 archival Prompt 字符预算存在差异；提取 `MemoryEntryPolicy` 统一规则。
7. Markdown 写入在父目录校验与替换之间存在目录替换窗口；加入目录身份复验，并优先使用 `SecureDirectoryStream` 的相对句柄操作。
8. Markdown ID 依赖物理行号，core 追加会改变 archival ID；改为分区内序号与内容摘要组成的稳定 ID。
9. Mem0 客户端补齐 base URL、正超时、JSON Content-Type、响应流关闭和响应大小边界校验。
10. Mem0 OpenAPI 校验由全局 schema 文本搜索改为实际 operation/request/query 校验；对 FastAPI 未声明响应模型保持兼容，已声明响应 schema 时继续严格校验。
11. JPA 后端将数据库故障映射为 `STORE_UNAVAILABLE`，并验证 Web 层返回 503；无效 scope 审计值改为 `<invalid>`。
12. 全特性分支的 Spec-Kit 文档尾随空白和多余 EOF 空行已清理，`git diff --check 68d190d` 无输出。
13. 最终 SpotBugs 门禁发现 Markdown 目标父目录的空值路径和 Mem0 解析异常捕获范围过宽；补齐显式父目录守卫并收窄异常类型。
14. 最终 Checkstyle 门禁发现 Boot/OpenAPI 测试夹具行长超限；仅调整夹具排版后重新执行全仓门禁。

## 最终验证命令

```powershell
.\mvnw.cmd -pl oryxos-boot -am '-Dtest=MemoryConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -pl oryxos-core,oryxos-memory,oryxos-storage,oryxos-web,oryxos-boot -am '-Dtest=MemoryEntryPolicyTest,ToolExecutorTest,MarkdownMemoryStoreTest,MarkdownMemoryStoreConcurrencyTest,Mem0MemoryStoreTest,Mem0MemoryStoreContractTest,Mem0ProtocolValidatorTest,JpaMemoryStoreTest,JpaMemoryStoreFailureTest,MemoryApiControllerTest,MemoryConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd clean verify
.\mvnw.cmd -pl oryxos-memory,oryxos-storage,oryxos-cli,oryxos-boot -am '-Dtest=WorkspaceServiceTest,MarkdownMemoryStoreTest,JpaMemoryStoreTest,MemoryPersistenceAcrossRestartTest,MemoryConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：定向评审回归命令以退出码 0 完成；Memory 相关新增/受影响测试 68 项通过，1 项 Windows 原生链接测试按能力检查跳过。2026-11-01 最终 `clean verify` 以退出码 0 完成，15 个 Reactor 模块全部 `SUCCESS`，总耗时 4 分 24 秒；Surefire/Failsafe 为 0 failure、0 error，Checkstyle 和 SpotBugs 为 0 violation，JaCoCo、ArchUnit、集成测试与可执行 JAR 打包门禁全部通过。

Quickstart 使用 JUnit 临时工作区和临时 SQLite 文件验证：`WorkspaceServiceTest` 15 项通过；Markdown StoreContract、SQLite StoreContract/Flyway/跨重启测试通过；`MemoryConfigurationTest` 7 项通过。Mem0 使用本地回环 `HttpServer` 验证 OpenAPI、请求协议、超时、错误映射、响应上限和秘密脱敏。

## 运行方式

- 默认 Markdown：保持 `oryxos.memory.backend=markdown`，在 Agent 工具清单中显式加入 `save_memory`、`recall_memory`。
- SQLite：设置 `oryxos.memory.backend=sqlite`，由 Flyway V5 创建并校验 `memory_entries`。
- Mem0：设置 `oryxos.memory.backend=mem0`、`oryxos.memory.mem0.base-url` 和 `api-key`；base URL 不含 `/v1`，启动时只读校验 `/openapi.json`。

完整示例见 `quickstart.md`，配置与行为说明见中英文 Memory 文档。

## 未验证与明确非目标

- 未提供真实 Mem0 OSS 服务和凭证，因此未验证真实部署互操作；回环 fake server 结果不冒充真实服务验证。
- 015 混合检索、向量索引和异步索引补齐未实现，只有能力枚举与 fail-fast 装配边界。
- 未进行需求文档中的性能压测，因此不声明性能指标达标。
