# 第 24 课 Sandbox 兼容性补全设计

日期：2026-11-01；2026-11-02 交付。状态：设计与任务已确认，已实现并通过最终本地完整门禁，未验证范围见 specs/007-sandbox/verification.md。

## 意图与依据

完成第 24 课的应用层 Sandbox 目标，在第 20 课已交付的强类型接口、安全路径与统一工具审计基础上补齐缺口。用户已选择核心 Sandbox 补全，并要求使用 Superpowers 解决课件与现有契约之间的差异。

依据为 `docs/class/第24节：Sandbox 实现与代码讲解.pdf` 全部 10 页、`AGENTS.md` 第 4、6、8 节、宪章 II/IV/VI，以及 `specs/005-tool-system/{spec,data-model,verification}.md`。第 24 课要求的核心是三类白名单、四个工具的执行前校验、拒绝时零 IO、复用已有审计及可读的失败原因。

## 目标、非目标与约束

- 补齐管理员额外文件根配置，保持当前 Agent 目录读写和当前绑定 Skill 只读的内建权限。
- 引入 `SandboxViolationException`，让拒绝具有统一类型和安全、可理解的原因。
- 验证文件、Shell、HTTP、Notify 的拒绝均发生在实际 IO 前，且通过真实 `ToolExecutor` 产生一次失败审计。
- 保留三类强类型 Action 与批准值返回，沿用 executable alias、argv、超时、输出上限和 HTTP URI 规范化。
- 交付配置说明及课件要求的测试；无需增加第三方依赖。
- 不增加白名单管理 API、热更新、数据库表、Profile 字段、容器或 microVM；不修改宪章，不修改课件原件。
- 应用层检查只提供当前进程内的约束与竞态缓解，不承诺对抗恶意本地并发修改或隔离解释器代码。

## 已考虑的方案

### A：在现有强类型接口上补全（推荐）

保留 `Sandbox.enforce(HttpRequestAction/FileAccessAction/ShellExecuteAction)` 的批准结果与调用方，补齐缺失行为。优点是满足课程验收意图并延续 005 的安全契约；代价是类形态与课件示例不同，需要明确记录映射。

### B：回退为课件的字符串 Action 与 void 接口

可逐字贴近示例，但会失去已批准路径/URI 的返回契约，引发文件、Shell、HTTP、Notify、搜索及 MCP 的公共接口迁移。字符串接口本身并非安全漏洞；这里不选择它的理由是无必要的兼容性破坏，以及示例无法覆盖既有安全要求。

### C：新增课件兼容接口，与强类型接口并存

能保留旧调用方，但会形成两套授权与执行值解析机制，增加绕过与行为漂移机会。当前交付没有这项兼容需求，不采用。

用户已选择核心阶段范围，并确认本设计的方案 A。

## 课件差异的处理

| 课件示例 | 本次适配 | 理由 |
| --- | --- | --- |
| `SandboxAction(type, String target)`、`ActionType` | 保留 sealed Action 与现有强类型 record，不增加未使用的兼容枚举 | 005 已定义明确的文件访问模式与批准结果 |
| `void enforce(...)` | 保留返回批准的 `Path` / `URI` | 调用方直接使用批准值，满足 FR-019 与 Action/Decision 契约 |
| `normalize().toAbsolutePath().startsWith(...)` | 验证真实路径、最近存在父目录和链接，复用 `.link` 绑定 | 满足 FR-010～012；相对路径仍基于当前 Agent |
| 文件读写共用额外根 | 普通额外根可读写；公共 Skill 保护优先于额外根 | 管理员根不能绕过未绑定不可见、绑定只读 |
| 完整 Shell 字符串、首 token | 保留 alias 到绝对 executable 的映射及 argv 参数 | 满足 FR-016～018；不把首 token 检查等同于进程隔离 |
| 只提取 host 比对白名单 | 保留绝对 HTTP(S) URI 校验与规范化 | 满足 FR-019、022；保持重定向关闭 |
| 异常消息原样存审计 | 在工具边界转换已知 Sandbox 异常为安全失败原因 | 满足 FR-033，保留已有审计写入路径 |
| `file/shell/http` 配置前缀 | 保留既有 `oryxos.tool.allowed-commands`、`oryxos.http.allowed-domains`，新增文件配置见下文 | 避免两份授权配置，文档解释课程配置语义映射 |

上述配置和接口调整应在后续 Spec-Kit research/contracts 中记录为本节适配决定；不得据此声称课件中的接口形式天然不安全。

## 文件权限与配置

提出新增 `oryxos.file.allowed-paths`，默认空列表；由 Boot 显式绑定、注入同一个 `toolSandbox`。配置在启动时解析为不可变快照，不做热更新。

额外根只接受存在的绝对普通目录，拒绝空值、相对路径、包含原生链接的根及无法解析的路径；启动校验失败使用脱敏原因。路径判断使用 `Path` 分段和真实路径，不用字符串前缀。具体配置类位于 Boot，Sandbox 实现仍留在 tool，避免让校验实现承担装配职责。

授权优先顺序：

1. 公共 Skill 根始终受保护；只有当前 `.link` 绑定的 Skill 可读，所有公共 Skill 不可写。额外根即使是公共 Skill 根的祖先也不能扩大权限。
2. 当前 Agent 自有目录按现有规则可读写，且是唯一 Shell 工作目录边界。
3. 其他普通路径只有落入管理员额外根才可读写；额外根不增加 Shell 工作目录权限。
4. 其他路径默认拒绝。

空 HTTP/Shell 白名单意味着对应动作全部拒绝；空额外文件根意味着不新增普通目录权限，仍保留 Agent/绑定 Skill 内建边界。这是 005 已确定的语义，不直接套用课件“所有文件动作均拒绝”的简写。

文件输入继续拒绝 `..`、非法路径和未授权的绝对路径；合法批准根内绝对路径按现有契约使用。盘符或 UNC 不得成为绕过权限的手段，不将所有绝对路径一概视为违规。

## 异常、接线与审计

在 `oryxos-tool/sandbox` 增加 `SandboxViolationException`，继承 `SecurityException` 以兼容既有捕获与断言。消息限定为固定的拒绝类别，例如文件边界拒绝、Shell executable 未授权、HTTP 目标未授权；不得拼入 URL、查询串、命令参数、凭证或外部异常文本。

校验配置错误继续属于启动参数错误，与运行时授权拒绝分开。FileTools、ShellTools、HttpTools 识别统一异常并返回不可重试的安全失败；Notify 依照当前 `NotifyTargetResolver` 的授权位置完成校验，再调用 Adapter。不得为贴近“execute 首行”字面形式而在解析参数前校验未知目标，或重复请求/发送。

`ToolExecutor` 的当前兜底会将未捕获异常转换为泛化错误。为了同时满足可读原因与 core 不依赖 tool，已知异常应由 tool 层转换为 `ToolResult`；维持原有 recorder、重试和一次最终审计行为，core 无需识别具体异常类型。

### 后续澄清：校验 IO、业务 IO与两类脱敏

“零 IO”精确定义为零业务 IO：目标正文读取、文件变更、进程启动、HTTP 和通知发送。允许授权必需的路径元数据、真实路径解析、绑定引用与 Skill 元数据读取。edit_file 需先批准 READ/WRITE，再读取正文；只验证文件未修改不足以证明零正文读取。

固定安全错误消息与审计输入脱敏是不同责任。前者由 tool 层映射，后者须覆盖 Shell arguments 中的凭证；维持既有审计写入/次数/Schema 的约束，不禁止修正现有审计参数脱敏。先以 RED 确认缺口，现已获用户批准实施：Shell 输入只存字节数与 SHA-256，避免猜测参数名遗漏秘密。

搜索和 MCP 复用同一 Sandbox 的现有调用点需要回归，避免统一异常或装配变化破坏其启动失败分类。

## 验收与实施约束

- Sandbox 单测覆盖三类允许/拒绝、空白名单、额外根、非法配置、普通目录同名前缀、穿越、链接、绑定和未绑定 Skill、公共根祖先授权、Shell alias 大小写/前导空格、HTTP 标签边界与非法 URI。
- 四个工具的拦截测试必须证明拒绝后零 IO：文件内容/存在性不变，进程执行器或安全测试进程标记为零，HTTP 回环请求计数为零，Notify Adapter 调用为零。
- 使用真实 Registry、ProfileContext、ToolExecutor 和数据库审计实现验证：可见但 Sandbox 拒绝的调用失败、不重试、恰好持久化一条记录、原因可读、canary 秘密不出现在失败结果与审计中。
- 使用本地可控环境完成真实执行链路验收，无需真实模型密钥或公网 Webhook；不把测试桩当作公网端到端通过。
- 先写行为测试并确认 RED，再做最小实现与 GREEN；最后运行相关模块测试和 `mvnw.cmd clean verify`，检查任务、课件交付物和 converge。
- Windows 无法创建原生链接时必须报告对应未验证范围；不能把跳过等同于该场景通过。

## 后续产物与交付状态

设计确认后使用 Superpowers writing-plans 与仓库 Spec-Kit，先检查是否已有唯一对应 feature，再以 `specs/007-sandbox` 为预期路径生成 spec、clarify、plan、research、data-model、contracts、quickstart、tasks 与 analyze。编号以实际目录扫描结果为准。

课程技能规定：当前 tasks 必须得到明确批准才能 implement。设计确认和 tasks 确认是不同门禁。不得自动创建/切换分支、提交、推送，用户现有课件和本地配置均保留。

由实施者在 plan 决定：配置类具体名称、内部校验函数拆分、测试夹具与审计集成测试所在模块；不得改变这里的授权优先级、输入形式与单次审计要求。
