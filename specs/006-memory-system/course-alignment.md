# Memory 第 21/22 节课程对齐

分支：`codex/memory-course-alignment`，基线：`5118151`。

## 实际改动

- SQLite core 独立全量读取；归档上下文每批 64 条倒序读取，遇到字符预算边界停止，输出按时间/id 升序。完整归档接口仍保留全量语义。
- SQLite 召回通过 instr/lower 参数化查询筛选字面候选，避免 LIKE 的百分号/下划线歧义；非 ASCII 正文保留候选，再按 Java Locale.ROOT 复核，保证既有 Unicode 行为。命中最新 N 条后停止。使用分页降低应用层峰值内存，未宣称全文索引或性能压测达标；大量非 ASCII 未命中仍可能扫描全部候选。
- 工具正文校验与后端共用 entry-max-characters，统一规范化换行；scope 允许大小写及首尾空白，审计同步规范化。
- 新增 oryxos.memory.mem0.archival-infer，默认 false；仅 archival 可显式开启，core 请求始终 infer=false。开关只控制协议请求，真实服务的提炼、冲突消解、metadata 分区及跨分区影响仍须部署验证。
- 修正文档中 Mem0 归档接口返回空列表、Agent 记忆初始化等旧说明。

## 保留的工程契约

结构化消息、Agent owner 隔离、core 完整注入、完整条目字符预算、稳定 ID、Flyway 迁移、安全路径与审计保持不变。015 向量/混合检索、自动保存触发、自动摘要和记忆压缩不在本次范围。

## 回归覆盖

- 工具可配置较大/较小上限、scope 大小写和空白。
- SQLite 字面百分号/下划线、Unicode 大小写、多页未命中后召回、字符预算与多页完整窗口。
- Mem0 默认 false、显式 archival true、core 强制 false；真实服务未验证。
- Spring 属性绑定及工具实际装配到后端的长度边界。

2026-11-01 20:09:02，`mvnw.cmd clean verify` 退出码 0，15 个 Reactor 模块全部 SUCCESS，总耗时 4 分 49 秒。单元/集成测试、Checkstyle、SpotBugs、JaCoCo、ArchUnit 和可执行 JAR 打包全部通过。此前定向 MemoryToolsTest、Mem0MemoryStoreTest、JpaMemoryStoreTest、JpaMemoryStoreFailureTest、MemoryConfigurationTest 也已通过。真实模型/Mem0 与性能压测未运行。
