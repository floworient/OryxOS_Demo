# Tool Registry Contract

## Core API

```java
enum ToolSource { BUILTIN, JAVA, MCP }

record ToolRegistration(OryxTool tool, ToolSource source, String sourceName) {}

interface ToolRegistry {
    void register(ToolRegistration registration);
    Optional<ToolRegistration> find(String name);
    List<ToolRegistration> registrations();
    List<ToolRegistration> visibleTo(Profile profile);
}
```

## Invariants

- 名称、说明、input schema 必须非空，schema 必须是单个 JSON object。
- 名称在全部来源间全局唯一。
- `MCP` registration 必须有非空 `sourceName`；其他来源不得伪造 MCP 来源。
- `registrations()` 和 `visibleTo()` 均按工具名升序并返回不可变列表。
- BUILTIN/JAVA 只按 `Profile.tools`；MCP 只按 `Profile.mcpServers`。
- PromptBuilder 和 ToolExecutor 都调用 `visibleTo(profile)`；执行层不能只信任模型清单。

## Java Adapter

`AnnotatedToolAdapter(ToolCallback callback)` 映射 definition 三字段；`execute` 只调用 callback 一次。发现器只包装 Spring Bean 上显式标注的 `@Tool` 方法。

Spring AI `internalToolExecutionEnabled` 必须始终为 `false`。

