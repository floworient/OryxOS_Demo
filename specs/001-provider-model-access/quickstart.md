# Quickstart: 验证核心契约与 Provider 接入

**Date**: 2026-10-22 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

本 feature 没有面向最终用户的命令入口。验证分两条路径：**无密钥路径**（默认，CI 可跑）和**真实调用路径**（需要 DashScope 凭据与网络）。

## 前置条件

- JDK 21
- 仓库根目录执行，使用仓库自带的 Maven Wrapper（`./mvnw`）
- 首次构建需要网络（新增 `hibernate-community-dialects` 与 `spring-ai-openai`）

```bash
./mvnw -q -DskipTests package
```

预期：14 个模块构建成功。若此步失败，后续验证都不成立，先修构建。

---

## 路径 A：无密钥验证

### A1. 全量测试

```bash
./mvnw -q test
```

预期：全部通过；不发起任何对外网络请求。`ProviderSmokeIT` 以 `*IT` 命名并标记 `integration`；即使在默认 `verify` 中被发现，没有显式 `oryxos.provider.smoke=true` 与凭据双条件也不得联网。

### A2. 完整 Profile 加载与失败隔离（FR-013～FR-015、SC-008）

用临时工作区准备两个合法 Agent 和一个非法 Agent，执行 `AgentLoaderTest`。

预期：
- 两个合法 Profile 的全部当前版字段与源文件路径一致，可按名称查询
- 非法 Agent 不进入 Registry，错误精确定位目录与字段
- `skills` / `notify_channels` 被拒绝，Agent 本地 `skills/` 软连接不进入 Profile
- 工作区为空时返回空结果

### A3. 单供应商单轮调用（SC-001、FR-004）

计时前提：仓库已检出、JDK 21 与 Maven Wrapper 可用、依赖已完成首次下载。计时从输入下列命令开始，到命令成功退出结束：

```bash
./mvnw -q -pl oryxos-provider -am -Dtest=Lesson16QuickstartTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`Lesson16QuickstartTest` 在临时目录写入最小 Provider 配置和 Agent 定义，用 `StubChatModel` 走 `AgentLoader → ProfileRegistry → ProviderService.chat` 无网络闭环。

预期：
- 总耗时不超过 300 秒，并在节级验收报告记录开始时间、结束时间和实测秒数
- 返回内容非空
- `ProviderResponse` 中 `providerName`、`model`、`usage`、`durationMs` 均按桩设定返回
- `llm_calls` 表新增一行，`status=SUCCESS`

### A4. 双供应商按名称路由（SC-002、FR-002）

注册 `alpha`、`beta` 两个桩，分别调用。

预期：`alpha` 的请求只命中 `alpha` 的桩，`beta` 同理；两行 `llm_calls` 的 `provider` 字段与实际调用方一致，无交叉。

并发子场景：两个 Agent 并发使用不同 model/temperature 发起受控调用。预期参数串线次数为 0；即时桩下 Provider 内部 p95 开销不超过 50 ms。

### A5. 配置错误（SC-003、FR-005/FR-008）

分别构造五种配置：重名供应商、缺 `model`、`api-key` 为未解析的 `${ALIYUN_API_KEY}`、`base-url` 写成 `not-a-url`、Agent 引用未知 Provider。

预期：前四种按契约在启动期失败或告警；未知 Provider Agent 从 Registry 移除并记录 Agent/字段错误，其他合法 Agent 保留，且不发出任何对外请求。

### A6. 凭据不泄露（SC-004、FR-007）

用一个可识别的哨兵值（如 `sk-TEST-SENTINEL-DO-NOT-LEAK`）作为凭据，触发一次 `AUTH_FAILED`。

预期：在标准输出、日志文件、异常堆栈、`llm_calls.error_message` 中搜索该哨兵值，命中次数为 **0**。

### A7. 审计记录可追溯且重启后仍在（SC-005、FR-010）

1. 跑 A3，记录返回内容
2. 关闭进程
3. 用同一 SQLite 文件重新启动，查询 `llm_calls`

预期：重启前后记录条数一致，字段完整且 `profile_name` 可关联 Agent；`usage_known=0` 时三个 token 字段均为 NULL。

### A8. Tool Schema 保真且不执行（SC-006/SC-009、FR-011/FR-016/FR-017）

把含必填参数、枚举和嵌套对象的 `OryxTool` 交给 `ToolSchemaAdapter`，再让桩返回一个 `toolCalls` 非空的响应。

预期：
- schema-only callback 的 `ToolDefinition` 与原 JSON Schema 语义一致
- 请求 options 的 `internalToolExecutionEnabled=false`
- `ProviderResponse.toolCalls` 含该意图
- `OryxTool.execute` 调用次数为 0；直接误调用 callback 也只能失败

### A9. 空响应按可疑结果处理

让桩返回 `"   "`。

预期：不抛异常；`llm_calls.status=EMPTY_RESPONSE`；调用方拿到空内容后可以自行决定如何上报。

### A10. 审计失败关闭

让 `LlmCallRecorder` 在模型返回成功响应后抛出持久化异常。

预期：`ProviderService.chat` 抛 `AUDIT_PERSISTENCE_FAILED`，调用方拿不到成功响应；日志和异常中不泄露 Prompt 或凭据。

### A11. 固定超时与 OpenAI 兼容协议

用 JDK `HttpServer` 启动回环服务：一条路径延迟超过测试注入的短 read timeout；另一条按 OpenAI Chat Completions 返回最小响应。

预期：延迟路径映射为 `TIMEOUT` 且不 fallback；协议路径收到正确的 Authorization、model 和 messages，并由真实 `OpenAiChatModel.call` 解析为非空响应。生产工厂的 connect/read timeout 固定为 60 秒，不存在新增配置键。

### A12. 硬门禁

```bash
./mvnw test
./mvnw clean verify
```

预期：两条命令退出码均为 0。任一失败都不得进入完成报告；不得删除断言、添加 `@Disabled` 或放宽质量阈值。

---

## 路径 B：真实 DashScope 调用（FR-003）

### B1. 准备凭据

```bash
export ALIYUN_API_KEY=<你的 DashScope API Key>
```

不要把它写进任何配置文件、`AGENT.md` 或提交到仓库。

### B2. 配置一个 DashScope 供应商

在 `oryxos-boot/src/main/resources/application.yaml`（或外部 `--spring.config.additional-location` 指定的文件）中加入：

```yaml
oryxos:
  providers:
    - name: qwen
      type: DASHSCOPE
      model: qwen-plus
      api-key: ${ALIYUN_API_KEY}
```

### B3. 发起一次单轮调用

```bash
./mvnw -q -pl oryxos-boot -am -Dgroups=integration -Dit.test=ProviderSmokeIT -Doryxos.provider.smoke=true -Dfailsafe.failIfNoSpecifiedTests=false verify
```

预期：
- 终端输出模型返回的非空文本
- `llm_calls` 新增一行，`status=SUCCESS`，`usage_known=1`
- 输出中不出现 `ALIYUN_API_KEY` 的值

### B4. OpenAI 兼容端点（第二类接入）

把 `type` 改为 `OPENAI_COMPATIBLE`、`base-url` 指向任意兼容服务（可用本地 Ollama / vLLM 的 `/v1` 地址），重复 B3。

预期：与 B3 相同的验收结果。

---

## 交付时必须报告的未验证项

按 AGENTS.md 要求，缺少凭据或网络时**必须显式说明**，不得用桩结果代替：

| 情况 | 报告措辞 |
| --- | --- |
| 无 `ALIYUN_API_KEY` | 「ProviderSmokeIT 未运行；DashScope 真实调用未验证，其余行为已用 `ChatModel` 桩验证」 |
| 无可用 OpenAI 兼容端点 | 「OpenAI 兼容协议接入未验证」 |
| 首次构建无网络 | 「依赖未解析，构建未通过」 |

---

## 常见失败

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `Unsupported Database: SQLite` | 误降级到 Flyway 9 或未用 `flyway-core` 11.x | 确认走 Boot 管理的 11.7.2 |
| `Unable to determine Dialect` | 缺 `hibernate-community-dialects` | 补依赖并配置 `hibernate.dialect` |
| 启动即报 `MISSING_CREDENTIAL` | Agent 引用了该供应商但环境变量为空 | 导出环境变量，或删除引用它的 Agent 目录 |
| `schema validation failed` | 迁移脚本与实体定义不一致 | 修 `V1__create_llm_calls.sql`，不要开 `ddl-auto=update` |
