# Implementation Plan: 第 17 节 ReAct 运行内核

**Branch**: `feature/react` | **Date**: 2026-10-26 | **Spec**: [spec.md](spec.md)
**Input**: `specs/002-react-loop-cli/spec.md`

## Summary

复用第 16 节契约，自实现 ReAct 循环、上下文组装、工具编排与统一入口。通过 core 存储契约将 Session 与工具审计写入 storage 的 SQLite/Flyway/JPA。用户已批准实现；2026-10-27进一步确认以 `.link` 项目引用替代原生软连接，并同步规范。

## Technical Context

- **Language/Version**: JDK 21；Spring Boot 3.5.8；Maven Wrapper。
- **Primary Dependencies**: 现有 Spring AI 1.1.2、Spring AI Alibaba 1.1.2.2、SnakeYAML 2.4、Spring Data JPA、Jackson、Flyway、SQLite；不新增第三方 artifact，不修改版本。
- **Storage**: 复用 SQLite 配置；新增 V2 工具审计与 V3 会话迁移，不改 V1 校验和；PostgreSQL 适配不属于本节。
- **Testing**: JUnit 5、Mockito、临时目录、SQLite 文件与 Boot 集成测试；五个课件测试类保留场景语义。
- **Target Platform**: JDK 21 Windows/Linux；统一使用普通 .link 相对引用，不需要管理员权限或系统设置，不复制Skill。
- **Project Type**: 多模块单体 JAR 的内部运行时服务，无新增 REST/CLI。
- **Performance Goals**: 恰好达到迭代上限停止、历史按完整轮次裁剪；不宣称需求第 8 章吞吐/延迟目标已测量。
- **Constraints**: 禁止框架自动循环及自动工具执行；core 不依赖 storage/provider 实现；不使用真实密钥。
- **Scale/Scope**: 单进程最小会话保存/读取；不支持并行写同一 Session 的治理能力。

## Constitution Check

| 原则 | 研究前 | 设计后及证据 |
| --- | --- | --- |
| I 单一运行时 | PASS | AgentService → ReActLoop；PromptBuilder 唯一拼装；Provider 固定自动执行 false |
| II 工具统一受控 | PASS | ToolExecutor 唯一执行点；Profile.tools 控制函数工具，Skill 绑定只控制上下文资源 |
| III 依赖倒置 | PASS | SessionManager、ToolInvocationRecorder 为 core 契约；JPA 实现在 storage；装配在 boot |
| IV 安全 | PASS | 路径真实边界；执行前可见性和策略检查；默认工具策略全拒绝，无外部工具实现 |
| V 持久化 | PASS | V2/V3 迁移；审计与会话跨上下文重建测试；沿用显式 Provider 路由 |
| VI 真实性 | PASS | 自动验证和真实 Demo 分开，未实现后续能力不宣称可用 |

## Project Structure

```text
specs/002-react-loop-cli/
  spec.md、plan.md、research.md、data-model.md、quickstart.md、tasks.md
  checklists/requirements.md
  contracts/react-runtime.md
oryxos-core/src/main/java/io/oryxos/core/
  session/Session.java、SessionManager.java
  context/ContextLoader.java、PromptBuilder.java
  tool/ToolExecutor.java
  audit/ToolInvocationRecord.java、ToolInvocationRecorder.java
  profile/ProfileContext.java
  agent/ReActLoop.java、AgentService.java
oryxos-storage/src/main/java/io/oryxos/storage/
  entity/ToolInvocation.java、SessionEntity.java
  repository/ToolInvocationRepository.java、SessionRepository.java
  JpaToolInvocationRecorder.java、JpaSessionManager.java
oryxos-storage/src/main/resources/db/migration/sqlite/
  V2__create_tool_invocations.sql、V3__create_sessions.sql
oryxos-boot/src/main/java/io/oryxos/boot/ReActConfiguration.java
```

测试目录与上述包结构对应。现有 Provider、Profile、OryxTool、ToolResult、ToolCallIntent、ToolSchemaAdapter 均保持签名。

## Design

### 最小契约与课件简写映射

- `ReActLoop.run(Session, String, Profile): String` 和 `AgentService.process(Session, String): String` 保持课件入口。
- 课件 `Response` 对应已有 `ProviderResponse`，`ToolCall` 对应已有 `ToolCallIntent`；使用 `content()`、`toolCalls()`、`settings().maxIterations()`，不添加重复类型。
- `Session` 保存 Spring AI 消息；`appendToolResult(ToolCallIntent, ToolResult)` 关联 ID 和名称，不改 ToolResult。消息持久化采用显式 DTO，恢复 user/assistant/tool 及 toolCalls，不能依赖多态类名反序列化。
- core 的 `SessionManager` 定义 save/find；其最小 JPA 实现是课件要求的 Session 保存支撑，含恢复，不提前增加归档管理 API。
- core 的 `ToolInvocationRecord/Recorder` 是课件审计实体的依赖倒置支撑；JPA `ToolInvocation` 和 `ToolInvocationRepository` 在 storage。
- 不提前新建 ToolRegistry、Sandbox、MemoryService 产品体系。工具池以不可变 `Map<String,OryxTool>` 注入，安全检查以 `BiConsumer<Profile,ToolCallIntent>` 注入；boot 默认全拒绝，测试显式放行无副作用工具。后续接入 Sandbox 时替换注入策略，不能绕过执行器。
- PromptBuilder 注入 `Function<OryxTool,ToolCallback>`，boot 复用 `ToolSchemaAdapter::adapt`；可选记忆文本使用 `BiFunction<Session,Profile,String>`，默认空字符串。此处只是注入槽，不等同 Memory 后端。

### 循环与失败

追加用户输入，每次 build → Provider.chat(session.id(), profile, prompt) → 追加 assistant。无工具则返回；否则逐一 execute 并追加关联结果；达到上限追加并返回终止说明。Provider 超时沿用既有固定 60 秒及错误映射，不增加运行时配置。ToolExecutor 每次尝试使用固定 60 秒有界执行、超时取消且不重试（不能证明副作用未发生）；测试注入短时限。该取消不宣称能隔离恶意代码。

ToolExecutor 先解析注册表，再校验当前 Profile.tools 与安全策略；执行 JSON 对象参数校验，拒绝无效参数。业务失败作为 ToolResult 回填，异常写审计并转换为明确失败结果；审计保存失败作为基础设施异常向外抛，不可吞掉。只对 retryable=true 的失败最多额外重试三次（最多四次尝试），退避 100/200/400 ms，每次尝试单独审计；测试注入等待函数避免实际等待。正常调用无重试且只执行一次。

AgentService 查 Profile 并 set，try 内执行循环及保存，finally clear。异常路径不宣称完成会话保存；本节只保证成功和上限结束时保存。持久化失败向外抛，必须清理上下文。ThreadLocal 不使用 InheritableThreadLocal；有界工具执行线程需显式设置同一 Profile 并 finally clear，避免工具内读取不到当前 Agent。

### 上下文与边界

ContextLoader 每次读取 AGENT.md 正文、Bootstrap 和 Agent skills/ 下的普通 .link 文件；每个文件只含一条相对于父目录的 Skill 目录路径（最多4096字节）。统一拒绝绝对、盘符、UNC、多行、断引用、未知格式和原生绑定链接，先检查公共根词法边界，再检查目标及 SKILL.md 的真实路径边界。只输出 name/description 和公共 SKILL.md 的真实可读取路径，不输出不存在的Agent目录别名，不预载正文。原生链接只提示迁移，不自动修改；文件工具未来复用同一解析结果限制未绑定资源。

PromptBuilder 用注入 Clock 格式化当前日期时间，追加到 system 末尾；可选记忆单独作为后续消息，再追加最近 N 个由 user 消息划分的完整轮次，包括当前轮，最后将可见工具 callback 放 Prompt options。裁剪仅生成视图，不修改 Session。工具列表由 Profile.tools 与注册池交集产生；未注册声明报可定位错误。

### 审批范围

本次 tasks 批准同时覆盖上述课件必要支撑类型与 sessions 表，不增加本节之外产品能力；若实现需新增此处未列 public 类型、配置键或修改前序接口，重新触发软门禁。用户已批准的冲突处理仅限 Flyway 和 Skill 加载方式，不改宪章。

## Complexity Tracking

无宪章例外。相比直接在 core 调 JPA，增加最小存储契约是维护依赖倒置所必需；相比提前实现整套 Tool/Sandbox/Memory 模块，使用受控注入点缩小本节范围。
