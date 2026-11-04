# Implementation Plan: 第 24 课 Sandbox 核心补全

**Branch**: `main` | **Date**: 2026-11-01 | **Spec**: [spec.md](spec.md)

**Input**: 已确认设计 `docs/superpowers/specs/2026-11-01-sandbox-design.md`。

## Summary

扩展既有 WhitelistSandbox 的额外普通文件根，同时保留 Agent、Skill、Shell 工作目录边界。tool 层统一安全拒绝结果，boot 绑定配置，真实 ToolExecutor 与 SQLite 验证单次失败审计。

## Technical Context

**Language/Version**: JDK 21，父 POM Enforcer `[21,22)`
**Primary Dependencies**: Spring Boot 3.5.8、Spring AI 1.1.2、Spring AI Alibaba 1.1.2.2；JDK NIO/HTTP/ProcessBuilder
**Storage**: 已有 SQLite/JPA/Flyway，无新增 Schema
**Testing**: 现有 JUnit 5、AssertJ、Mockito、Spring Boot test、Failsafe IT
**Target Platform**: Windows 开发机、Linux 私有化部署
**Project Type**: Maven 多模块运行时
**Performance Goals**: 沿用已有资源上限，目标 IO 前拒绝且不可重试；无新增吞吐指标
**Constraints**: 不改 core 公共接口或审计写入/重试机制；审计参数脱敏如有失败证据可单独修正，无新第三方依赖，不修改质量阈值
**Scale/Scope**: tool、boot、core 审计输入脱敏及文档；无管理 API、热更新、容器实现

## Constitution Check

| 原则 | 设计前/后检查 |
| --- | --- |
| I 单一内核 | PASS：真实 ToolExecutor，不另建调用链 |
| II 统一受控 | PASS：同一 Registry 与 `.link` resolver；额外根不绕过 Skill |
| III 依赖倒置 | PASS：具体异常在 tool，配置在 boot，core 不反依赖 |
| IV 安全生效 | PASS：真实路径、逐项授权、零 IO、秘密脱敏 |
| V 显式配置与持久化 | PASS：启动快照和现有审计，无新表 |
| VI 真实可验证 | PASS：TDD、完整 verify、存在性核对和 converge |

无宪章例外。用户确认了相对于课件的强类型 Action/批准结果、配置前缀和脱敏适配；不修改原课件或 005 历史验收。

## Project Structure

### Documentation

本目录包含 spec、plan、research、data-model、quickstart、contracts/sandbox、tasks 和 requirements checklist；实施时新增 verification。Superpowers 步骤在 `docs/superpowers/plans/2026-11-01-sandbox.md`，实施完成状态仅在 tasks 勾选。

### Source Code

| 文件（仓库相对路径） | 责任 |
| --- | --- |
| `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/SandboxViolationException.java`（新增） | 统一安全拒绝 |
| `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/Sandbox.java` | 默认拒绝统一异常，签名保持 |
| `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/WhitelistSandbox.java` | 根配置、路径保护与强类型授权 |
| `oryxos-tool/src/main/java/io/oryxos/tool/builtin/FileTools.java` | 逐项授权、遍历剪枝、拒绝结果与描述 |
| `oryxos-tool/src/main/java/io/oryxos/tool/builtin/ShellTools.java` | 已知安全异常转换，保留进程限制 |
| `oryxos-tool/src/main/java/io/oryxos/tool/builtin/HttpTools.java` | 已知安全异常转换，保留批准 URI |
| `oryxos-tool/src/main/java/io/oryxos/tool/builtin/NotifyTools.java` | 已知安全异常转换，保留 resolver/Adapter 顺序 |
| `oryxos-boot/src/main/java/io/oryxos/boot/FileSandboxProperties.java`（新增） | 配置绑定与防御快照 |
| `oryxos-boot/src/main/java/io/oryxos/boot/ToolConfiguration.java` | 显式注入同一 Sandbox |
| `oryxos-boot/src/main/resources/application.yaml` | 空文件根配置与说明 |
| `oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java` | 三类授权、配置与绕过矩阵 |
| `oryxos-tool/src/test/java/io/oryxos/tool/builtin/FileToolsTest.java` | 六个文件工具、零副作用和遍历保护 |
| `oryxos-tool/src/test/java/io/oryxos/tool/builtin/FileToolsSearchLimitsIT.java` | T020 真实一万项目录与超大文件混合回归，Failsafe 独立 JVM 执行 |
| `oryxos-tool/src/test/java/io/oryxos/tool/builtin/ShellToolsTest.java` | 零进程副作用、alias/cwd/argv |
| `oryxos-tool/src/test/java/io/oryxos/tool/builtin/HttpToolsTest.java` | GET/POST 零请求 |
| `oryxos-tool/src/test/java/io/oryxos/tool/builtin/NotifyToolsTest.java` | Adapter never |
| `oryxos-boot/src/test/java/io/oryxos/boot/ToolConfigurationTest.java` | 绑定、启动失败、唯一 Bean |
| `oryxos-boot/src/test/java/io/oryxos/boot/SandboxAuditIT.java`（新增） | SQLite 单条拒绝审计与 canary |
| `oryxos-boot/src/test/java/io/oryxos/boot/ToolRegistryAuditIT.java` | 本轮先增加真实 SQLite 的 Shell 参数凭证泄露回归 |
| `oryxos-core/src/main/java/io/oryxos/core/tool/ToolExecutor.java` | 已有 RED 支撑的 Shell 输入脱敏：仅存 bytes/sha256，不改变审计写入/重试 |
| `README.md`、`website/zh/docs/tool.md`、`website/docs/tool.md` | 真实配置和能力边界，沿用文件语言 |

NotifyTargetResolver 当前已先授权再 Adapter，保留逻辑并测试。搜索/MCP 仅回归授权行为，不扩大产品范围。

## Phase 0: Research

详见 research.md。现有 POM 与源码已核实所需基础类型，无第三方 API 未决项，无技术替换，因而无需研究代理。当前 Files.walk 未逐文件授权，必须在加入宽泛根时补齐；当前通用 catch 抹平安全原因，应在 tool 层处理。

## Phase 1: Design & Contracts

具体方法签名、配置键、异常文本和权限矩阵分别见 contracts/sandbox.md 与 data-model.md。

额外根校验每个路径组件，拒绝符号链接、junction/重解析导致的真实路径偏移；公共根不存在时保留词法保护，存在时叠加真实路径保护，不自动创建业务目录。无 resolver 时文件动作拒绝。旧构造器保持空额外根；Boot 使用完整构造器并显式传工作区 skills。绑定每次解析，不缓存。

FileTools 的 list 在元数据提取前逐项授权；searchablePaths 改用可剪枝的 NIO visitor，在 preVisitDirectory 对未授权目录 SKIP_SUBTREE，逐文件授权后再读取/匹配。只跳过明确 Sandbox 拒绝，不吞未知异常。从普通祖先到公共根时剪枝，直接指定绑定 Skill 仍可读取。维持排序与文件数/字节数/结果数上限；计数包含尝试访问以限制大量拒绝项开销。

统一异常继承 SecurityException，消息仅允许 contracts 的三种固定文本，不保留含原始目标的 cause。Tool 在通用 catch 前转换为 `new ToolResult(false, "", exception.getMessage(), false)`；未知异常仍走现有脱敏兜底。ToolExecutor 不识别具体 Sandbox 异常，审计输入脱敏可独立修正，写入通道、记录次数和重试机制不变。

### IO 与审计职责澄清（先 RED，后已批准修复）

区分授权校验 IO 与业务 IO：允许必要路径元数据、绑定及 Skill 元数据读取；拒绝时禁止目标正文读取/变更、启动进程或发送网络请求。FileTools.edit 必须在正文读取前通过 READ/WRITE 授权。测试通过观测真实 Files.readAllBytes 调用确认正文读取次数，而不是仅断言无修改。

错误消息固定文本不能证明审计参数无秘密。ToolRegistryAuditIT 通过真实 Registry、ShellTools、ToolExecutor、JPA/SQLite 验证 input_json 不含分离参数和 --key=value 中的假凭证。两个 RED 已记录，用户随后批准继续实施，对应修复进入 GREEN 验证。

## Phase 2: 实施顺序与验证

先基线与统一异常，再 US1 配置/边界/遍历、US2 四工具零 IO、US3 SQLite 审计，最后文档与完整门禁。先 RED 后 GREEN，详见 tasks 和 Superpowers 计划；当前 tasks 未获批准前不得实施。

聚焦测试、IT、本地真实链路和完整门禁命令见 quickstart。所有测试无需真实密钥；链接平台能力缺失必须披露。课件交付物按已确认映射核对，不机械新增废弃枚举/重复配置类。

## Complexity Tracking

无未解释的宪章冲突。新增异常和配置类直接对应课程目标；文件遍历适配是维护现有可见性所必需，不引入新框架或模块。
