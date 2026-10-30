# Tool 体系验证矩阵

> 独立审查及修复记录见 `review-followup.md`。文件 realPath/fileKey 复验属于应用层竞态缓解，不承诺对抗恶意本地并发修改；管理员额外文件根当前为空属于已批准范围限制，不代表已实现额外根配置能力。

验证日期：2026-10-30。自动化验证不访问公网、不使用真实密钥或真实 MCP server。真实 DuckDuckGo、企业 MCP 与凭证调用仍是部署者的独立手工验证项。

审查修复后门禁：全仓 `clean verify` 退出 0；最后两项复审修复后全仓 `verify` 于 16:09 退出 0；`npm run docs:build`、`git diff --check` 通过，凭证模式扫描无命中。新增测试覆盖 Shell 中断退出、嵌套量词正则、Map 参数兼容、退避失败审计和参数脱敏。

## Functional Requirements

| Requirement | 状态 | 证据 |
| --- | --- | --- |
| FR-001 | 通过 | `ToolRegistry`/`ToolRegistration`/`ToolSource`；`DefaultToolRegistryTest` |
| FR-002 | 通过 | `DefaultToolRegistryTest` 覆盖名称、说明、schema 和重名拒绝 |
| FR-003 | 通过 | `PromptBuilder`、`ToolExecutor`、`BootCliRuntime.tools` 均注入/读取同一 Registry；相应测试通过 |
| FR-004 | 通过 | `DefaultToolRegistryTest` 覆盖 `tools` 与 `mcp_servers` 分源可见性 |
| FR-005 | 通过 | `ToolExecutorTest` 覆盖二次可见性检查、上下文传播、超时、单次审计 |
| FR-006 | 通过 | Provider/Prompt options 显式 `internalToolExecutionEnabled(false)`；`ReActConfigurationTest` 断言 |
| FR-007 | 通过 | `AnnotatedToolDiscovery`/`AnnotatedToolAdapterTest`；`ToolConfigurationTest` 启动发现 |
| FR-008 | 通过 | `AnnotatedToolAdapterTest` 覆盖 schema、对象参数、单次调用和脱敏失败 |
| FR-009 | 通过 | `FileTools.all()` 注册六个文件工具；`FileToolsTest` |
| FR-010 | 通过 | `WhitelistSandbox` 以当前 Profile 的 Agent 目录解析；无额外根默认放行 |
| FR-011 | 批准范围内通过 | Agent 目录读写、绑定 Skill 只读；额外普通根保持为空，未实现管理员额外根配置；`FileToolsTest` |
| FR-012 | 通过 | `SkillBindingResolverTest`、`ContextLoaderNativeLinksIT`、`WhitelistSandboxTest`、`FileToolsTest` |
| FR-013 | 通过 | `FileToolsTest` 验证零次/多次匹配不修改、唯一匹配成功 |
| FR-014 | 通过 | `FileToolsTest` 验证稳定顺序、文本过滤、搜索与 200 条上限 |
| FR-015 | 通过 | 同目录临时文件 + `ATOMIC_MOVE`；不支持原子移动时失败并清理临时文件，不回退为部分写入 |
| FR-016 | 通过 | `ShellTools` schema 只接受 alias、argv、工作目录和超时；`ShellToolsTest` |
| FR-017 | 通过 | `ProcessBuilder(List)`；元字符字面量测试通过 |
| FR-018 | 通过 | `WhitelistSandbox` + `ShellToolsTest` 覆盖 alias/cwd、双流上限、超时和进程树终止 |
| FR-019 | 通过 | `HttpTools` 对 GET/POST 使用 `Sandbox.enforce` 返回 URI；`HttpToolsTest` 各一次请求 |
| FR-020 | 通过 | `Redirect.NEVER`、请求/响应/时间/header 上限；仅允许四种安全 header，重定向及非 2xx 返回脱敏失败 |
| FR-021 | 通过 | `HttpToolsTest` 验证敏感 header 在网络请求前拒绝 |
| FR-022 | 通过 | `WhitelistSandboxTest` 覆盖默认拒绝、URI 规范化、域名边界；搜索 Provider 同样经过 Sandbox |
| FR-023 | 通过 | `McpConfigLoaderTest` 覆盖严格 YAML、stdio/SSE、未知字段、重复 server |
| FR-024 | 通过 | `McpConfigLoader` 对 command/URL 调用统一 Sandbox，并从环境解析占位符 |
| FR-025 | 通过 | 配置/授权异常由 loader fail-fast；`McpClientServiceTest` 验证纯连接失败只跳过单 server |
| FR-026 | 通过 | `McpClientServiceTest`、`McpToolAdapterTest` 覆盖发现、来源注册、对象参数和结果映射 |
| FR-027 | 通过 | Adapter 单次 callback；`McpClientService.close` 关闭连接；无 tools/call 自动重试 |
| FR-028 | 通过 | `InteractionTools` + `UserInteractionContext`；`InteractionToolsTest` |
| FR-029 | 通过 | `CliChannelTest` 覆盖问答、finally 清理和并发隔离；无人值守测试小于 1 秒 |
| FR-030 | 通过 | `SearchProvider`/`WebSearchTools`；DuckDuckGo 测试确认请求先经过 HTTP Sandbox |
| FR-031 | 通过 | Provider/白名单失败返回明确错误；`WebSearchToolsTest` 无模拟结果 |
| FR-032 | 通过 | 11 个工具的测试覆盖未知字段、类型、尾随 JSON 与有界输入 |
| FR-033 | 通过 | 错误统一泛化；canary 测试及最终敏感信息扫描 |
| FR-034 | 通过 | `BootCliRuntime.tools` 读 Registry 并排序；`BootCliQueryTest`/`ToolConfigurationTest` |
| FR-035 | 通过 | `.link` 由 `SkillBindingResolver` 单一解析；旧 `skills:` 被 `AgentLoaderTest` 拒绝 |
| FR-036 | 通过 | Tool 配置只注册 11 个新增工具；没有占位 Memory 工具；`ToolConfigurationTest` |
| FR-037 | 通过 | 测试使用 `@TempDir`、回环 `HttpServer`、受控 Java helper、fake MCP/Search |

## Success Criteria

| Criterion | 状态 | 证据 |
| --- | --- | --- |
| SC-001 | 通过 | `DefaultToolRegistryTest` 与 `ToolConfigurationTest` 覆盖全部注册元数据及跨来源重名失败 |
| SC-002 | 通过 | `PromptBuilderTest`、`ToolExecutorTest` 和 Registry 可见性测试使用同一规则，未授权执行数为 0 |
| SC-003 | 通过 | `FileToolsTest`、`ShellToolsTest`、`HttpToolsTest`、`InteractionToolsTest`、`WebSearchToolsTest` 覆盖 11 个工具成功/拒绝路径 |
| SC-004 | 通过 | 路径穿越、原生链接与未绑定 Skill 全部拒绝，失败写入不改目标 |
| SC-005 | 通过 | Shell 元字符保持 argv 字面值；超时后父子 PID 均不存活 |
| SC-006 | 通过 | 敏感 header/域名拒绝产生 0 请求，成功 GET/POST 各 1 请求 |
| SC-007 | 通过 | fake connection/callback 测试覆盖健康注册、连接跳过、fail-fast 与单次 call；stdio/SSE 传输构造通过 |
| SC-008 | 通过 | 无人值守小于 1 秒且无 stdin；CLI 恰好读取一行并返回 |
| SC-009 | 通过 | canary 异常测试不回显；仓库敏感模式扫描无新增真实秘密 |
| SC-010 | 通过 | `mvnw.cmd test`、`mvnw.cmd clean verify`、`git diff --check` 最终均以 exit 0 完成 |

## Quickstart 核对

- `.oryxos/application.yaml` 使用实际配置键 `oryxos.http.allowed-domains` 与 `oryxos.tool.allowed-commands`。
- `.oryxos/mcp_servers.yaml` 只演示当前实现的 `stdio`，且 `command` 使用管理员配置的 alias。
- `AGENT.md` 分别用 `tools` 选择内置/Java 工具、用 `mcp_servers` 选择 MCP 来源。
- `oryxos tool list` 从全局 Registry 读取；Agent 实际可见子集仍由 Profile 过滤。
- 构建与测试命令已按最终门禁执行；真实外部联通未纳入自动通过结论。
