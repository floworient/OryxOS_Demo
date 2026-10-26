# 契约：ProviderService（内部调用入口）

**Type**: Java 接口契约 | **Location**: `oryxos-core` | **Implementation**: `oryxos-provider`

本阶段没有 HTTP / CLI 入口，`ProviderService` 是模型调用的唯一对外形态。单元测试、`ProviderSmokeIT` 与后续 `ReActLoop` 都只通过它调模型。

## 接口

```java
package io.oryxos.core.provider;

public interface ProviderService {

    ProviderResponse chat(String sessionId, Profile profile, Prompt prompt)
            throws ProviderException;
}
```

`Profile` 提供 Agent 名称、Provider 名称、模型、温度和可见工具名；`Prompt` 使用本地已核实的 Spring AI 1.1.2 类型。调用方在构造 Prompt 前通过 `ToolSchemaAdapter` 放入当前可见工具的 schema-only callbacks；`ProviderService` 不负责从工具名称查找实例。`sessionId` 在第 16 节允许为 null，002 引入 Session 后传真实值。Provider 清单查询与启动校验分别属于 `ProviderRegistry`、`ProviderStartupValidator`，不扩张此接口。

### 响应

```java
public record ProviderResponse(
        String providerName,
        String model,
        String content,                  // 可能为空字符串
        TokenUsage usage,                // 可能为 TokenUsage.UNKNOWN
        List<ToolCallIntent> toolCalls,  // 原样透传，永不被执行
        long durationMs
) {}

public record TokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {
    public static final TokenUsage UNKNOWN = new TokenUsage(null, null, null);
    public boolean isKnown() { return promptTokens != null || completionTokens != null || totalTokens != null; }
}

public record ToolCallIntent(String id, String name, String argumentsJson) {}
```

## 行为约定

| 编号 | 约定 | 对应需求 |
| --- | --- | --- |
| C1 | 按 `profile.provider.name` 精确查表；查不到抛 `UNKNOWN_PROVIDER`，不回退到任何默认供应商 | FR-002、FR-009 |
| C2 | `content` 为空或只有空白字符时**不抛异常**，返回 `ProviderResponse`，由调用方按「可疑结果」处理；审计记为 `EMPTY_RESPONSE` | 边界案例、FR-010 |
| C3 | 保留 Prompt 已携带的 schema-only `ToolCallback`，并强制 `internalToolExecutionEnabled=false`；不根据 `Profile.tools` 查找实例；`toolCalls` 非空时原样返回 | FR-011、FR-016 |
| C4 | 供应商返回认证失败 / 限流 / 网络错误，或连接/读取超过固定 60 秒时抛 `ProviderException`，异常必须携带 `providerName` 与 `model` | FR-009、US-5 |
| C5 | 单次调用不重试、不切换供应商 | FR-009 |
| C6 | 每次真正发出请求的调用，无论模型成功失败，都写一行 `llm_calls`；写入失败抛 `AUDIT_PERSISTENCE_FAILED`，不得返回成功结果 | FR-010 |
| C7 | `profile.provider.model/temperature` 按请求复制进 options，不修改共享 `ChatModel`，避免并发请求互相污染 | FR-012 |
| C8 | 异常消息与日志不含凭据；provider 模块内部的 `ProviderDefinition` 覆写 `toString()` 时省略 `apiKey` | FR-007、SC-004 |
| C9 | `llm_calls.profile_name` 必须取 `profile.name`；`session_id` 原样记录，可空 | FR-010、FR-013 |

## 错误码

```java
public enum ProviderErrorCode {
    UNKNOWN_PROVIDER,     // 引用了未配置的供应商
    DUPLICATE_PROVIDER,   // 配置里出现重名
    INVALID_CONFIG,       // 字段缺失/格式错误/base-url 非法
    MISSING_CREDENTIAL,   // 凭据为空、未解析、仍含 ${...}
    AUTH_FAILED,          // 401/403
    RATE_LIMITED,         // 429
    TIMEOUT,              // 超过配置时限
    NETWORK_ERROR,        // 连接失败/DNS/服务不可达
    EMPTY_RESPONSE,       // 响应为空（仅用于审计状态，不作为抛出的异常）
    INVALID_TOOL_SCHEMA,  // 工具 Schema 缺失或无法无损表达
    AUDIT_PERSISTENCE_FAILED, // 模型调用已发生，但审计持久化失败
    INTERNAL              // 其余未归类错误
}
```

`ProviderException` 持有 `errorCode`、`providerName`、`model`、可空的 `cause`；`getMessage()` 已脱敏，可安全输出到终端与日志。

## Schema-only callback

`ToolSchemaAdapter` 为调用方已经选择的每个 `OryxTool` 构造一个 `ToolDefinition`，再放入仅提供 Schema 的 `ToolCallback`。该 callback 的两个 `call` 入口都必须固定拒绝执行，且测试断言 `OryxTool.execute` 调用次数为 0。不得把真实 `OryxTool` 的执行函数交给 Spring AI。本节测试直接调用适配器；002 的 `PromptBuilder`/`ToolRegistry` 负责按 Profile 工具名选择实例。

## 映射表暴露（仅测试可见）

`oryxos-provider` 内部维护 `Map<String, ChatModel>`。测试通过 `@TestConfiguration` 注入 `StubChatModel` 覆盖某个名称，从而在不联网的前提下驱动 C1–C7 的全部行为。
