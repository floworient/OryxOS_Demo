# 验收记录：会话上下文压缩

日期为 2026-11-10。验收在 `codex/015-context-compaction` 隔离工作树完成。

```powershell
./mvnw.cmd -o "-Dsurefire.useManifestOnlyJar=false" "-Dit.test=ContextCompactionIT" "-Dfailsafe.failIfNoSpecifiedTests=false" clean verify
```

15 个 Reactor 项目均通过，测试报告合计 457 项，其中 454 通过、3 跳过、0 失败。Checkstyle、SpotBugs 和 JaCoCo 校验均通过。生成 `oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar`。

| 32 轮固定回放 | 旧窗口基线 | 新实现（含摘要） | 降幅 |
| --- | ---: | ---: | ---: |
| 累计输入估算单位 | 11,151,156 | 207,127 | 98.14% |
| 按测试费率计算的总费用单位 | 22,311,272 | 426,238 | 98.09% |

回放包含 64 次主模型请求、7 次摘要请求和 64 次工具执行。第 24 轮后重新启动，检查原始消息、摘要游标和审计用途。回环协议测试覆盖 OpenAI 兼容和 DashScope 的工具 ID 配对、稳定首条消息和输出配额。SQLite V9 旧记录迁移、摘要失败降级、取消和预算错误均有对应测试。

金额采用 [验收指南](quickstart.md) 的固定测试费率，不代表真实 Token 计费或缓存命中率。此次未调用真实供应商，也未测量实际账单。超限如果发生在工具已经执行之后，外部操作与审计遵循现有语义，Session 仍沿用成功处理后保存。
