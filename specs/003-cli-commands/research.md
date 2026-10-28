# Research: 第 18 节 CLI 命令

## 决策 1：完整可执行物由 Boot 聚合

**Decision**: `oryxos-boot` 的可执行 JAR 作为唯一完整发行物；其 main 先执行 Picocli。`oryxos-cli` 保留命令树但不再单独打包含不完整依赖的 fat JAR。

**Rationale**: Boot 已依赖全部 14 模块并依赖 CLI；若 CLI 反向依赖 Boot 会形成 Maven 环。聚合入口放 Boot 能保证重命令拿到 Web、storage、provider 和 tool，同时轻命令仍可在启动 Spring 前执行。

**Alternatives considered**: CLI 依赖 Boot（循环依赖，拒绝）；ServiceLoader 从 CLI fat JAR 动态发现 Boot（嵌套 JAR 和服务元数据复杂度不必要）；保留两个可执行 JAR（能力不同、易误导，拒绝）。

## 决策 2：通过窄 `CliRuntime` 接口反转重命令依赖

**Decision**: 在 `oryxos-cli` 定义基础设施无关的 `CliRuntime` 及只读 DTO，由 `oryxos-boot` 实现。

**Rationale**: 命令可独立测试，CLI 不认识 Spring/JPA/Provider 实现；Boot 可为 chat、Web 模式和查询选择不同上下文。

**Alternatives considered**: 命令直接调用 SpringApplication（CLI 需要 Boot/Web 依赖）；所有命令先启动完整上下文（轻命令变慢且缺凭据会阻断文件操作）；打印占位提示（不满足真实交付）。

## 决策 3：轻重命令边界

**Decision**: `init`、`status`、四个 Profile 命令完全基于文件系统；`chat`、`serve`、`gateway` 和三个查询命令通过 `CliRuntime`。Provider 查询使用只绑定配置、不构造模型的最小上下文；Session 查询使用 storage 上下文；Tool 查询使用真实运行时工具 Map。

**Rationale**: 课程明确要求轻命令秒回，且查询必须来自真实来源。拆分最小上下文避免无关模型凭据影响 Provider/Session 查询。

**Alternatives considered**: 把所有 list 命令做成静态文件扫描（Tool 与 Session 没有可靠文件事实源）；全部启动聚合上下文（耦合无关启动校验）。

## 决策 4：统一工作区根

**Decision**: 对用户公开 `-Doryxos.root`、`ORYXOS_ROOT`、默认 `<user.dir>/.oryxos` 的优先级；Boot 运行时把解析结果注入既有内部键 `oryxos.workspace`。

**Rationale**: 轻命令不读取 Spring 配置，但重命令必须指向同一目录。单点解析后桥接内部键可兼容现有 ContextLoader、数据库 URL 和 ProviderBootstrapConfiguration。

**Alternatives considered**: 轻命令也启动 Spring 配置系统（违背性能目标）；同时公开 root/workspace 两套键（行为漂移）；修改所有既有配置键（无必要扩大范围）。

## 决策 5：Agent 删除使用可恢复归档

**Decision**: `profile delete` 将完整目录移动到 `.oryxos/archive/<name>-<UTC毫秒时间戳>[-N]`，不物理删除。

**Rationale**: 当前技术方案和 AGENTS.md 明确要求 Agent 删除归档整个目录。时间戳和冲突后缀允许同名 Agent 多次创建/归档并保持可恢复。

**Alternatives considered**: 递归删除（违反当前契约且不可恢复）；固定 `.oryxos/archive/<name>`（第二次归档冲突）；仅移动 AGENT.md（丢失 scripts/skills/REFERENCE）。

## 决策 6：Session 契约只做必要扩展

**Decision**: 给 `SessionManager` 增加 `getOrCreate` 和 `list`，新建 core `SessionSummary`；复用 V3 `sessions` 表，不加迁移。

**Rationale**: chat 需要稳定恢复同一三元组，session list 只需现有字段。core 契约允许未来 Web/IM 复用，storage 负责 JPA 排序和映射。

**Alternatives considered**: CLI 自己拼 Session ID（违反单点身份）；Boot 直接查询 repository（绕过通用契约）；为 CLI 新建表或文件（重复事实源）。

## 决策 7：Profile 最小模板

**Decision**: 创建完整、可被现有 AgentLoader 解析的模板；name/identity 使用命令参数，Provider 默认采用项目既有契约示例 `qwen/qwen-plus`，tools/mcp/schedules 为空，channel 为 cli，Bootstrap 为三个工作区文件，settings 使用 10/20。

**Rationale**: AgentLoader 的必填结构比课程伪代码更严格；模板必须可解析。Provider 默认只是一份可编辑起点，不尝试写密钥或探测网络。

**Alternatives considered**: `replace-me`（可解析但体验差）；从当前 Provider 清单猜第一个（轻命令需启动配置且结果不稳定）；把 apiKey 写入 AGENT.md（违反凭据契约）。

## 决策 8：gateway 的本阶段语义

**Decision**: gateway 启动完整聚合运行时（包含当前 HTTP 服务及实际存在的渠道 Bean），并只报告实际装配项；不为 IM marker 类伪造可用状态。

**Rationale**: 第 18 节要求守护进程入口，但完整 IM Adapter 属于后续阶段。启动真实聚合上下文既保留入口又不夸大能力。

**Alternatives considered**: 直接等同打印“未实现”（命令不真实）；在本 feature 实现三个 IM 渠道（越界）；让 gateway 只 sleep（无业务能力）。

