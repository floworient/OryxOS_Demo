# Implementation Plan: 第 18 节 CLI 命令

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax in `tasks.md` for tracking.

**Goal:** 交付第 18 节定义的 12 个真实可执行 CLI 命令，同时保持轻重命令分流和单一 Agent 运行链路。

**Architecture:** `oryxos-cli` 定义 Picocli 命令树、轻命令与 `CliRuntime` 窄接口，`oryxos-boot` 实现该接口并惰性启动所需 Spring 上下文。`oryxos-channel-cli` 只适配终端输入输出，Session 通用读取契约留在 core、JPA 实现在 storage。

**Tech Stack:** Java 21、Spring Boot 3.5.8、Picocli 4.7.7、Spring Data JPA、Flyway、JUnit 5、AssertJ

**Spec:** `specs/003-cli-commands/spec.md`

## Global Constraints

- 最终完整发行物是 `oryxos-boot-*-exec.jar`，不得保留能力不完整但同名可执行的 CLI fat JAR。
- 所有消息入口只调用既有 `AgentService`，不得复制 ReAct、Prompt、Provider 或 Tool 执行逻辑。
- `oryxos-core` 不得依赖 storage、web、provider 实现或具体 channel。
- `init`/Profile 文件操作不得覆盖用户文件、跟随操作系统链接或越出工作区。
- Provider 输出不得含 apiKey；无密钥测试不得触发真实外呼。
- 数据库结构只由既有 Flyway 迁移管理，本 feature 不修改 V1–V3。
- 保留用户未跟踪的 `.claude/settings.local.json`，不把它加入提交或测试夹具。

## Review Focus

- 部分初始化工作区出现“文件占目录位”或链接时必须失败且零覆盖；由 US1 的 `WorkspaceServiceTest` 覆盖。
- `-Doryxos.root`、`ORYXOS_ROOT` 与 Boot 内部 `oryxos.workspace` 必须指向同一绝对目录；由 US1/US3 的 resolver 与 Boot 集成测试覆盖。
- `provider list` 在凭据缺失时仍应列出声明且绝不展开密钥；由 US4 的配置清单测试覆盖。
- chat 的空白行、`/quit`、EOF 和单轮异常不得造成额外模型调用或 Session 串号；由 US2 的 `CliChannelTest` 覆盖。
- `serve/gateway` 端口冲突或启动中途失败必须关闭已创建上下文且返回非零；由 US3 的 `BootCliRuntimeTest` 覆盖。

---

**Branch**: `003-cli-commands` | **Date**: 2026-10-28 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/003-cli-commands/spec.md`

## Summary

实现第 18 节定义的 12 个 Picocli 命令，并把最终可执行 JAR 入口收敛到 `oryxos-boot`。`oryxos-cli` 负责命令树、轻量文件命令和窄运行时接口；`oryxos-boot` 实现该接口并按命令惰性启动 Spring 上下文；`oryxos-channel-cli` 只实现可测试的 stdin → `AgentService` → stdout 循环。Session 查询和 get-or-create 通过 core 契约及 storage 实现复用现有 V3 表，不新增数据库迁移。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.8、Picocli 4.7.7、Spring Data JPA、Flyway、SnakeYAML 2.4（由现有依赖管理锁定）

**Storage**: 文件工作区 + SQLite；复用 `sessions`、`llm_calls`、`tool_invocations`，本 feature 不新增表

**Testing**: JUnit 5、AssertJ、Spring Boot Test、Picocli in-process command tests、临时目录/文件 SQLite 集成测试

**Target Platform**: Java 21 可用的 Windows/Linux/macOS 本地或服务器环境，UTF-8 终端

**Project Type**: Maven 多模块 CLI + 单体 Spring Boot 可执行 JAR

**Performance Goals**: `init`、`status`、Profile 管理本地 p95 < 1 秒且零 Spring 上下文；重命令只启动一次所需上下文

**Constraints**: 12 条命令真实可执行；CLI 不复制 Agent/ReAct/Tool/Web 逻辑；无密钥测试可完成；文件操作拒绝穿越与链接逃逸；不修改用户未跟踪文件

**Scale/Scope**: 12 条业务命令、4 个命令组、1 个 CLI Channel；单机工作区，典型数百 Agent/Session，查询列表不引入分页 API

## Constitution Check

*GATE: Phase 0 前及 Phase 1 后均检查。*

| 原则 | 设计检查 | 结果 |
| --- | --- | --- |
| I 单一运行时内核 | `chat` 仅调用 `AgentService.process`；`serve/gateway` 启动既有聚合运行时；CLI 不实现 ReAct 或 Tool 调度 | PASS |
| II 工具调用统一受控 | `tool list` 只读真实注册清单，不增加执行入口；工具调用仍只经 `ToolExecutor` | PASS |
| III 模块边界与依赖倒置 | CLI 定义 `CliRuntime` 窄接口，Boot 实现；core 只增加通用 Session 读取契约，不依赖基础设施 | PASS |
| IV 安全边界真实生效 | 工作区和 Agent 路径做词法/真实路径校验；拒绝链接；Provider 输出不含凭据 | PASS |
| V 持久化与配置显式契约 | 复用 V3 sessions 表与 Flyway；Session ID 继续由 `Session` 单点生成 | PASS |
| VI 交付真实性与可验证性 | 空 Provider/Tool/Session 如实输出；gateway 只报告已装配能力；所有命令有自动测试 | PASS |

Phase 1 复查：命令契约、数据模型和 quickstart 未引入例外或新增模块，全部 PASS。

## Project Structure

### Documentation (this feature)

```text
specs/003-cli-commands/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── cli-command-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
oryxos-core/
└── src/main/java/io/oryxos/core/session/
    ├── SessionManager.java               # 增加 getOrCreate/list 契约
    └── SessionSummary.java               # 基础设施无关的会话摘要

oryxos-storage/
├── src/main/java/io/oryxos/storage/
│   └── JpaSessionManager.java            # 实现稳定恢复/创建和摘要查询
├── src/main/java/io/oryxos/storage/entity/
│   └── SessionEntity.java                # 提供安全摘要映射
└── src/main/java/io/oryxos/storage/repository/
    └── SessionRepository.java            # lastActiveAt 倒序读取

oryxos-channel-cli/
└── src/main/java/io/oryxos/channel/cli/
    └── CliChannel.java                    # 可注入 Reader/Writer 的交互循环

oryxos-cli/
├── src/main/java/io/oryxos/cli/
│   ├── OryxOsCli.java                    # 根命令与依赖装配
│   ├── CliRuntime.java                   # Boot 实现的重命令窄接口
│   ├── WorkspaceRootResolver.java        # root 属性/环境变量/默认值
│   ├── WorkspaceService.java             # init/status/Profile 文件操作
│   └── command/                          # 12 个业务命令 + 4 个分组命令
└── src/test/java/io/oryxos/cli/           # 命令树、工作区、安全、输出测试

oryxos-boot/
├── src/main/java/io/oryxos/
│   └── OryxOsApplication.java            # 最终可执行入口先分派 CLI
├── src/main/java/io/oryxos/boot/
│   └── BootCliRuntime.java               # 惰性 Spring 上下文和真实清单适配
└── src/test/java/io/oryxos/boot/          # 重命令装配、资源清理、可执行入口测试
```

**Structure Decision**: 保持既有 14 模块，不新增模块。命令定义与文件轻命令归 `oryxos-cli`，终端协议归 `oryxos-channel-cli`，基础设施查询归原模块，最终启动装配归 `oryxos-boot`；删除 `oryxos-cli` 的独立 fat JAR，唯一完整可执行物为 `oryxos-boot-*-exec.jar`。

## Design

### 入口与惰性运行时

- `OryxOsApplication.main(String[])` 构造 `BootCliRuntime` 和 `OryxOsCli`；仅非零结果调用 `System.exit`，成功轻命令靠 main 返回自然退出，`serve/gateway` 的非 daemon 服务器线程维持进程，shutdown hook 关闭上下文。
- `OryxOsCli.execute(String...)` 便于进程内测试；命令通过构造注入 `CliRuntime`、`WorkspaceService`、Reader/Writer 和当前用户提供者，不读取全局单例。
- `CliRuntime` 只暴露 `chat`、`serve`、`gateway`、Provider/Tool/Session 只读清单；接口 DTO 不包含 Spring、JPA 或密钥类型。
- `chat` 使用 `WebApplicationType.NONE`；`serve/gateway` 使用 servlet Web 上下文；查询命令使用最小必要上下文，Provider 清单不要求解析凭据。

### 工作区与 Agent 生命周期

- `WorkspaceRootResolver` 顺序为 JVM `oryxos.root` → `ORYXOS_ROOT` → `<user.dir>/.oryxos`，Boot 将结果作为 `oryxos.workspace` 传给既有配置，消除两套根路径。
- `WorkspaceService.init()` 用 create-if-absent 语义创建六个目录、`memory/MEMORY.md` 和三个 Bootstrap 文件；现存类型冲突直接失败，不覆盖、不跟随链接。
- Agent 名只允许 ASCII 字母数字开头，后续为字母数字、`.`、`_`、`-`，长度 1–64；额外拒绝 `.`、`..`、Windows 保留设备名和任何路径形式。
- `profile create` 生成能被当前 `AgentLoader` 解析的最小模板，默认 Provider 为既有文档基线 `qwen/qwen-plus`，用户可随后编辑。
- `profile delete` 将整个目录移动到 `.oryxos/archive/<name>-<UTC毫秒时间戳>`；同毫秒冲突按 `-1`、`-2` 递增，移动前后均验证真实边界。

### Session 与真实清单

- `SessionManager.getOrCreate(channel,userId,profileName)` 以新建 `Session` 得到稳定 ID，再 `find(id).orElse(candidate)`；ID 拼接仍只存在 `Session`。
- `SessionManager.list()` 返回 `SessionSummary(id, profileName, status, lastActiveAt)`；storage 通过 repository 倒序查询并映射，不反序列化完整消息。
- Provider 清单来自绑定后的 `ProviderProperties`，输出 name/type/model/baseUrl，不读取或输出 apiKey。
- Tool 清单来自 Boot 已装配的 `runtimeTools` Map，输出 name/description；空 Map 输出明确空状态。

### 命令与错误

- 完整命令、参数、输出列和退出码见 [contracts/cli-command-contract.md](./contracts/cli-command-contract.md)。
- Picocli 用法错误使用退出码 2；业务/运行时失败统一映射为 1；成功和空清单为 0。
- 正常输出走 commandLine out，错误走 err；异常处理器做凭据模式脱敏且不打印堆栈到普通用户终端。

## Complexity Tracking

无宪章例外。运行时桥接是解除既有 Boot → CLI 依赖方向下的最小方案，不新增模块或第二条 Agent 执行链路。

