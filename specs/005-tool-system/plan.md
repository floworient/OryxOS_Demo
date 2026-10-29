# Implementation Plan: Tool 体系完整交付

**Branch**: `005-tool-system` | **Date**: 2026-10-29 | **Spec**: `specs/005-tool-system/spec.md`

**Input**: 已批准设计 `docs/superpowers/specs/2026-10-29-tool-system-design.md` 与 feature specification。

## Summary

在保留现有 String JSON `OryxTool` 契约的前提下，增加统一 ToolRegistry 和三来源注册；补齐 6 个文件工具、Shell、GET/POST HTTP、ask_user、web_search；使用 Spring AI 1.1.2 的 callback/schema 能力适配 Java `@Tool`，使用 MCP SDK 0.17.0 接入 stdio/SSE server。PromptBuilder 与 ToolExecutor 共享同一可见性规则，全部真实执行继续经过 ToolExecutor、Sandbox 和 `tool_invocations` 审计。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.8、Spring AI 1.1.2、Spring AI Alibaba 1.1.2.2、Spring AI MCP 1.1.2 / MCP SDK 0.17.0、Spring Web、SnakeYAML 2.4

**Storage**: 不新增持久化；复用 SQLite `tool_invocations` 审计与工作区 `.oryxos/mcp_servers.yaml`

**Testing**: JUnit 5、AssertJ、Mockito、JDK `HttpServer`、临时目录、fake MCP connection/SearchProvider

**Target Platform**: 企业私有化部署的 Windows/Linux 单体可执行 JAR，JDK 21

**Project Type**: Maven 多模块服务端运行时 + Picocli CLI

**Performance Goals**: 工具结果有界；无人值守 ask_user 在 1 秒内失败；MCP 单 server 初始化最多 20 秒；不声明未经测量的吞吐指标

**Constraints**: Spring AI 自动执行关闭；无外网自动测试；文件链接逃逸防护；Shell argv 直传；HTTP/MCP 秘密不落日志/审计；一次副作用调用不自动重发

**Scale/Scope**: 11 个新增内置工具、既有 notify、三来源全局 Registry、单进程多 Agent、stdio/SSE MCP；不含 Tool Policy、管理 API、Runner、并行 Tool Call

## Constitution Check

| Principle | Design response | Status |
| --- | --- | --- |
| I 单一运行时内核 | PromptBuilder/ToolExecutor 共享 Registry；ReAct 与 AgentService 不复制；自动 Tool 执行保持关闭 | PASS |
| II 工具调用统一受控 | 三来源统一注册、Profile 可见性、ToolExecutor、Sandbox、单次最终审计；Skill 仍按 `.link` | PASS |
| III 模块边界 | core 放 Registry/Interaction/Binding 契约；tool 放实现/MCP/内置工具；boot 只装配 | PASS |
| IV 安全边界真实生效 | 文件真实路径、绑定 Skill 只读、Shell alias 到绝对路径、HTTP 规范化 URI、秘密脱敏 | PASS |
| V 持久化与配置显式契约 | 不新增 DB；MCP YAML 严格 schema；复用现有审计 | PASS |
| VI 交付真实性 | 回环/fake 自动验收；真实 MCP/Search 单独报告；Memory/Runner 等不冒充完成 | PASS |

Phase 1 设计后复核：research、contracts 与 data model 未引入宪章例外，继续 PASS。

## Project Structure

### Documentation

```text
specs/005-tool-system/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── builtin-tools.md
│   ├── mcp-config.md
│   └── tool-registry.md
├── checklists/requirements.md
└── tasks.md                 # plan review 通过后生成

docs/superpowers/
├── specs/2026-10-29-tool-system-design.md
└── plans/2026-10-29-tool-system.md  # tasks 阶段生成
```

### `oryxos-core`

```text
src/main/java/io/oryxos/core/
├── context/
│   ├── ContextLoader.java                 # 修改：复用 binding resolver
│   ├── SkillBinding.java                  # 新增：已校验绑定快照
│   └── SkillBindingResolver.java          # 新增：每轮解析 .link
├── interaction/
│   ├── UserInteraction.java               # 新增：中立交互契约
│   └── UserInteractionContext.java        # 新增：单次调用上下文
└── tool/
    ├── ToolRegistry.java                  # 新增：共享注册/可见性端口
    ├── ToolRegistration.java              # 新增
    ├── ToolSource.java                    # 新增
    └── ToolExecutor.java                  # 修改：Registry + 交互上下文传播

src/main/java/io/oryxos/core/context/PromptBuilder.java  # 修改：从 Registry 取可见工具

src/test/java/io/oryxos/core/
├── context/SkillBindingResolverTest.java
├── context/PromptBuilderTest.java
└── tool/ToolExecutorTest.java
```

### `oryxos-tool`

```text
src/main/java/io/oryxos/tool/
├── adapter/
│   ├── AnnotatedToolAdapter.java
│   └── AnnotatedToolDiscovery.java
├── builtin/
│   ├── FileTools.java
│   ├── HttpTools.java
│   ├── InteractionTools.java
│   ├── ShellTools.java
│   └── WebSearchTools.java
├── interaction/
│   └── UnsupportedUserInteraction.java
├── mcp/
│   ├── McpClientFactory.java
│   ├── McpClientService.java
│   ├── McpConfigLoader.java
│   ├── McpConnection.java
│   ├── McpServerDefinition.java
│   ├── McpToolAdapter.java
│   └── SpringMcpClientFactory.java
├── registry/
│   └── DefaultToolRegistry.java
├── sandbox/
│   ├── ActionType.java                    # 修改/扩展
│   ├── FileAccessAction.java
│   ├── FileAccessMode.java
│   ├── HttpRequestAction.java
│   ├── Sandbox.java                       # 修改为重载强类型契约
│   ├── SandboxAction.java                 # 修改为 sealed marker
│   ├── ShellExecuteAction.java
│   └── WhitelistSandbox.java              # 扩展文件/Shell/HTTP
└── search/
    ├── DuckDuckGoInstantAnswerSearchProvider.java
    ├── SearchProvider.java
    └── SearchResult.java

src/test/java/io/oryxos/tool/
├── adapter/AnnotatedToolAdapterTest.java
├── builtin/FileToolsTest.java
├── builtin/HttpToolsTest.java
├── builtin/InteractionToolsTest.java
├── builtin/ShellToolsTest.java
├── builtin/WebSearchToolsTest.java
├── mcp/McpClientServiceTest.java
├── mcp/McpConfigLoaderTest.java
├── mcp/McpToolAdapterTest.java
├── registry/DefaultToolRegistryTest.java
└── sandbox/WhitelistSandboxTest.java
```

现有 `notify/*` 与 `builtin/NotifyTools.java` 只修改 Sandbox 调用签名，不改变第 19 节行为。

### `oryxos-channel-cli`

```text
src/main/java/io/oryxos/channel/cli/
├── CliChannel.java                        # 修改：设置/清理 UserInteractionContext
└── ConsoleUserInteraction.java            # 新增：复用当前 reader/writer

src/test/java/io/oryxos/channel/cli/CliChannelTest.java
```

### `oryxos-boot`

```text
src/main/java/io/oryxos/boot/
├── BootCliRuntime.java                    # 修改：tool list 读取 Registry
├── ReActConfiguration.java                # 修改：Registry 装配
├── ToolConfiguration.java                 # 新增：内置/Java/MCP/搜索装配
├── ToolProperties.java                    # 新增：限制与白名单配置
└── NotifyConfiguration.java               # 修改：复用统一 Sandbox Bean

src/main/resources/application.yaml        # 新增 deny-by-default 配置

src/test/java/io/oryxos/boot/
├── ToolConfigurationTest.java
├── ToolRegistryAuditIT.java
└── BootCliRuntimeTest.java
```

### Build and docs

```text
oryxos-tool/pom.xml                         # 增加 spring-ai-mcp
README.md                                   # 只更新当前真实能力
website/docs/tool.md                        # 校正为本次实际范围
website/zh/docs/tool.md                     # 同步中文
website/docs/profile.md                     # 工具清单/可见性
website/zh/docs/profile.md                  # 同步中文
```

**Structure Decision**: 不新增 Maven 模块。共享契约进入 core，工具实现集中在 oryxos-tool，CLI 交互实现留在渠道模块，boot 仅做显式装配，保持现有 14 模块边界。

## Interfaces

```java
public interface ToolRegistry {
    void register(ToolRegistration registration);
    Optional<ToolRegistration> find(String name);
    List<ToolRegistration> registrations();
    List<ToolRegistration> visibleTo(Profile profile);
}

public record ToolRegistration(OryxTool tool, ToolSource source, String sourceName) {}

public interface UserInteraction {
    String ask(String question);
}

public interface Sandbox {
    URI enforce(HttpRequestAction action);
    Path enforce(FileAccessAction action);
    Path enforce(ShellExecuteAction action);
}

public interface SearchProvider {
    List<SearchResult> search(String query, int limit);
}

interface McpConnection extends AutoCloseable {
    List<OryxTool> listTools();
}

interface McpClientFactory {
    McpConnection connect(McpServerDefinition definition);
}
```

MCP 工具由 factory 返回的连接创建 `McpToolAdapter`，Service 添加 server 来源后注册；测试不暴露 SDK 具体类。

## Implementation Phases

### Phase 1: Registry 与共享上下文

以测试驱动 core ToolRegistry 契约与 tool 默认实现；迁移 PromptBuilder、ToolExecutor、Boot CLI 到 Registry。抽取 SkillBindingResolver 并保持现有 ContextLoader 行为全绿。加入 UserInteractionContext 捕获/传播，但尚不注册 ask_user。

可独立验收：三来源可见性、重复拒绝、Prompt/Executor 一致、既有 ReAct/Notify/审计不回归。

### Phase 2: 强类型 Sandbox 与文件工具

先扩展 Sandbox harness，再实现文件路径解析和绑定 Skill 边界；随后以单工具 RED→GREEN 完成 read/write/list/edit/grep/glob。所有写入测试先断言失败不改变原文件。

可独立验收：Agent 目录正常读写、绑定 Skill 只读、未绑定和链接逃逸拒绝、搜索硬上限。

### Phase 3: Shell 与 HTTP

实现 Shell alias 到绝对 executable、argv 直传、并发排水 stdout/stderr、超时进程树回收。扩展现有 HTTP Sandbox，使用不跟随重定向的 JDK HttpClient/RestClient 实现 GET/POST，禁止敏感 headers 并限制 body。

可独立验收：元字符不解释、超时无存活子进程；回环 GET/POST 单次请求，拒绝用例零请求；Notify 保持通过。

### Phase 4: Java `@Tool` 与 MCP

用 MethodToolCallbackProvider 驱动 AnnotatedToolAdapter；严格 YAML 与占位解析后手工创建 MCP SDK同步客户端。MCP 配置/授权 fail-fast、纯连接故障隔离、tool call 不自动重发。

可独立验收：Java schema/执行、stdio/SSE factory 构造、fake MCP list/call/close、Registry 来源与 Profile.mcpServers。

### Phase 5: ask_user、web_search 与 Boot 集成

CLI 每轮设置 UserInteractionContext，其他入口默认 unsupported；实现 WebSearchTools 与 DuckDuckGo Instant Answer provider。Boot 注册 11 个新工具和 notify，查询命令改读 Registry。

可独立验收：CLI 一问一答、无人值守不读 stdin、fake 搜索结果有界、全局 tool list 稳定。

### Phase 6: 文档与门禁

运行 quickstart 自动部分，更新 README/中英文网站，仅声明已实现的 11 个新增工具、stdio/SSE MCP 和 Instant Answer 限制。执行聚焦测试、全仓 test、clean verify、diff/secret scan，并运行 Spec-Kit analyze 一致性检查。

## Risk Controls

- **可见性漂移**: Registry 的 `visibleTo` 同时供 PromptBuilder 与 ToolExecutor，参数化测试比较集合。
- **文件 TOCTOU/链接逃逸**: 执行前解析真实父目录/目标；写入临时文件仍位于已批准目录；原生链接集成测试。
- **Shell 死锁/遗留进程**: stdout/stderr 并发排水；超时按 descendants 反向终止后终止根进程。
- **HTTP SSRF/凭证泄露**: 规范化 URI、精确/标签边界域名、敏感 header 黑名单、固定错误文本、canary 扫描。
- **MCP 副作用重复**: Adapter 不重试；ToolResult `retryable=false` 用于传输不确定错误；调用计数测试。
- **交互串线**: ThreadLocal 每次设置/finally 清理，并显式传播到工具线程；并发测试使用两套 reader。
- **文档超前**: 网站现有前瞻工具不作为实现依据；更新时区分本次已实现与 roadmap。

## Review Focus

- `Profile.tools` 同时包含某 MCP 工具名时，来源规则仍以 registration 为准，不得绕过 `mcp_servers`——由 Registry 参数化测试覆盖。
- 创建新文件时目标不存在，必须验证最近存在真实父目录且替换前不可被链接切换——由 FileTools/Sandbox 测试覆盖。
- Shell 子进程同时填满 stdout/stderr 时不得死锁，超时后 descendants 全部停止——由 ShellToolsTest helper 覆盖。
- MCP `${ENV_VAR}` 出现在 header 复合值、缺失或空白时必须 fail-fast 且 canary 不出现在异常——由 McpConfigLoaderTest 覆盖。
- UserInteractionContext 在线程池复用和并发 CLI Session 中必须 finally 清理、不串回答——由 ToolExecutorTest/CliChannelTest 覆盖。

## Complexity Tracking

无宪章例外。ToolRegistry 接口与实现分置两个模块，是为同时满足 core 调用方依赖倒置和 tool 模块集中实现；MCP 再包一层 `McpConnection` 是为隔离 SDK、实现无网络自动测试，不是另建执行通道。

