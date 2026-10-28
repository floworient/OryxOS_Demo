# 第 18 节 CLI 命令设计

## 意图

交付课程第 18 节定义的 12 个 CLI 命令，让用户能够初始化和管理工作区、与 Agent 对话、启动当前已有的服务运行模式，并查询真实的 Provider、Tool 和 Session。CLI 是接入层，不复制 ReAct、模型调用、工具执行、Web 或持久化业务逻辑。

## 目标

- 交付 `init`、`status`、`chat`、`serve`、`gateway`、`profile list/create/show/delete`、`provider list`、`tool list`、`session list`。
- 轻命令不启动 Spring，重命令按需启动且正确关闭运行时。
- `chat`、HTTP 和 gateway 复用既有 `AgentService`、Session、Provider、ToolExecutor 和审计链路。
- 所有查询只展示真实状态，未注册能力显示空结果，不用示例或占位成功掩盖缺失。
- 保持 `oryxos-core` 不反向依赖 storage、provider、web 或具体渠道。

## 非目标与反目标

- 不在本 feature 补齐完整 Web API、MCP、内置 Tool、IM 渠道、Memory、调度或治理功能。
- 不以“12 个命令能显示 `--help`”替代功能实现。
- 不让 `oryxos-cli` 依赖聚合模块 `oryxos-boot`，也不形成 Maven 循环依赖。
- 不让 CLI 自己拼会话 ID、调用模型、执行工具或读写审计表。
- 不不可恢复地删除 Agent 目录，不输出 Provider 密钥。

## 约束

- JDK 21、Spring Boot 3.x、Picocli、Maven 多模块和单体可执行 JAR 保持不变。
- 文件访问必须防目录穿越和操作系统链接逃逸。
- 数据库只通过 Flyway 迁移，查询和会话恢复复用 storage 实现。
- 模型自动工具执行继续关闭，工具统一由 `ToolExecutor` 调度。
- 现有未提交文件属于用户；实现不得修改 `.claude/settings.local.json`。

## 方案比较

### 方案一：CLI 定义命令，Boot 提供惰性运行时桥接（已选择）

`oryxos-cli` 保存 Picocli 命令树、轻命令实现和重命令所需的窄接口；`oryxos-boot` 作为可执行聚合入口，构造 CLI 并仅在重命令执行时启动 Spring 上下文。`oryxos-channel-cli` 实现可注入输入输出的交互循环。查询能力通过现有模块或新增的只读契约暴露，CLI 不直连实现细节。

优点是依赖方向符合 14 模块边界、轻命令启动快、重命令可用真实运行时，而且不会让 CLI 与 Boot 循环依赖。代价是需要一个很小的运行时桥接契约，并调整最终可执行 JAR 的入口装配。

### 方案二：只注册命令，后续能力使用占位实现

所有命令都能解析，但 `serve`、`gateway` 或查询命令只打印“后续实现”。改动较少，却无法满足“完成相应 CLI 功能”，也会让文档和实际能力失真，因此拒绝。

### 方案三：在 CLI 内启动并实现所有下游模块

CLI 直接依赖 Web、storage、provider 和 tool，甚至补齐后续章节功能。这样会扩大范围、模糊模块边界，并容易与 Boot 形成依赖环，因此拒绝。

## 组件边界

- `oryxos-cli`：Picocli 命令树、工作区轻命令、输出与退出码、重命令所需的窄运行时接口。
- `oryxos-channel-cli`：可注入输入输出的 `CliChannel`，只做读取、转交、打印和退出判断。
- `oryxos-core`：只在现有契约不足时增加通用的会话获取/列表或工具目录读取契约，不出现 Picocli、JPA 或具体 Provider 实现。
- `oryxos-storage`：实现会话列表和稳定 get-or-create 语义，继续由 Flyway 管理表结构。
- `oryxos-provider` 与 `oryxos-tool`：分别提供真实的只读清单来源；没有注册项时返回空集合。
- `oryxos-boot`：最终运行装配点；轻命令不创建 Spring 上下文，`chat` 使用非 Web 上下文，`serve` 使用 Web 上下文，`gateway` 启动当前实际支持的常驻组件。

## 行为与错误

- 正常数据写 stdout，错误和用法写 stderr；成功为退出码 0，输入错误与运行时错误均为非零且可区分。
- `init` 只补缺不覆盖；Profile 创建不覆盖，Profile 删除移动整个目录到工作区归档区。
- 文件目标在操作前完成规范化和真实路径边界检查；失败不得留下部分结果。
- `chat` 在每轮调用前不自行修改 Session，处理和保存由既有服务链路负责；`/quit` 与 EOF 不产生调用。
- 启动失败或退出必须关闭应用上下文和 CLI 创建的资源；错误文本经过凭据脱敏。

## 验证

- 命令树和所有 `--help` 路径的自动化测试。
- 轻命令零 Spring 启动、工作区初始化幂等、Profile 路径攻击和归档恢复测试。
- 注入 Reader/Writer、AgentService 桩和 SessionManager 桩的 CLI Channel 测试。
- 重命令运行时桥接、端口失败、上下文清理及同一存储复用的集成测试。
- Provider/Tool/Session 真实清单、空状态、排序和凭据脱敏测试。
- 相关模块聚焦测试、全量 `mvn test`、`mvn clean package`、格式与静态门禁。

