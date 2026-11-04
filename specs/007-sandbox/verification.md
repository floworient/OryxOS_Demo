# 第 24 课 Sandbox：验证与交付记录

日期：2026-11-01～2026-11-02（Asia/Shanghai）。状态：已完成本节应用层 Sandbox 交付，通过最终本地门禁；平台原生链接及真实外部服务保留明确未验证范围。下面 RED 章节为历史证据，现状见文末。

## 本轮授权与边界

用户要求：区分上述问题，并先写相应失败测试。仅修改规范/计划说明和两个现有测试文件；没有修改生产代码、质量阈值、依赖或审计 Schema，未提交/推送。

1. 校验 IO：必要的路径元数据、真实路径、`.link` 与 Skill 元数据读取允许。
2. 业务 IO：目标正文读取、文件变更、进程启动、HTTP/通知发送；被拒绝操作应在这些动作之前停止。
3. 固定安全错误文本保护错误结果；审计 input_json 的参数脱敏单独保护持久化输入，两者不可替代。

## RED-1：编辑只读 Skill 在写权限批准前读取正文

测试：`oryxos-tool/src/test/java/io/oryxos/tool/builtin/FileToolsTest.java` 的 `deniedSkillEditChecksWritePermissionBeforeReadingContent`。

使用真实 Agent、普通 `.link`、公共 Skill 和 UTF-8 正文文件。仅对 Files.readAllBytes 的目标路径做观测并委托真实读取；所有路径/绑定校验依旧运行，未模拟授权或文件内容。预期拒绝写入且目标正文读取 0 次，实际读取 1 次。

```powershell
.\mvnw.cmd -pl oryxos-tool -am '-Dtest=FileToolsTest#deniedSkillEditChecksWritePermissionBeforeReadingContent' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

首次沙箱执行：JUnit 临时目录 toRealPath 触发 AccessDeniedException，1 error，不作为业务 RED。随后请求并获准沙箱外重跑相同命令：退出 1，Tests run: 1, Failures: 1, Errors: 0, Skipped: 0。

失败断言：`[写权限被拒绝时不得先读取文件正文] expected: 0 but was: 1`。

报告：`oryxos-tool/target/surefire-reports/io.oryxos.tool.builtin.FileToolsTest.txt`。

## RED-2：拒绝 Shell 的请求凭证仍明文进入 SQLite

测试：`oryxos-boot/src/test/java/io/oryxos/boot/ToolRegistryAuditIT.java` 的 `deniedShellArgumentsDoNotLeakCredentialsIntoPersistedAudit`。

使用真实 Boot ToolConfiguration、Registry、ProfileContext、ShellTools、ToolExecutor、JPA/Flyway/SQLite；未授权 alias 在启动进程前拒绝，无公网调用。输入含非真实密码的 `--password VALUE` 和令牌 `--token=VALUE`。本测试不替代完整四工具 IT。

```powershell
.\mvnw.cmd -pl oryxos-boot -am '-Dtest=ToolRegistryAuditIT#deniedShellArgumentsDoNotLeakCredentialsIntoPersistedAudit' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

为访问 JUnit 临时目录，获准沙箱外执行。退出 1，Tests run: 1, Failures: 1, Errors: 0, Skipped: 0。

已通过前置断言：失败不可重试、记录恰好 1 条、数据库 success=false、error_message 与结果一致、result_json 不含假凭证。失败发生在 input_json：两种假凭证仍明文存在。

报告：`oryxos-boot/target/surefire-reports/io.oryxos.boot.ToolRegistryAuditIT.txt`。本轮使用 Surefire 的显式方法选择运行既有 IT 类，以便只执行此失败场景；完整验收仍须 Failsafe verify。

## RED 阶段的未完成项（历史状态，已由后续授权推进）

- edit_file 提前批准 READ/WRITE 的修复、审计输入脱敏修复均未实施，测试保留 RED。
- 完整 19 项 tasks 仍未批准/勾选；本轮失败测试授权不等于全面实现授权。
- 未运行全量 test/clean verify、网站构建、converge 或真实外部模型/Webhook；不声称功能完成或全仓通过。
- 下一阶段应分别对正文授权顺序和参数脱敏做最小修复，再运行对应测试 GREEN 及相关回归。

## 实施与完整门禁的阶段结果

用户于 RED 阶段后明确「ok确认方案，继续执行」，2026-11-02 再次要求继续。现有改动保留在 main，没有 commit/push/切分支。

| 阶段 | 证据 | 结果 |
| --- | --- | --- |
| 两个已确认缺陷修复 | `.tmp/sandbox-first-green.log` | 两个目标测试分别运行并通过；edit 提前 READ/WRITE；Shell 请求只存 bytes/sha256 |
| 根配置/Skill 边界 | `.tmp/sandbox-red.log` → `.tmp/sandbox-roots-green.log` | 新类型/构造器先 RED；实现后 23 项、零失败、2 平台跳过 |
| 四工具固定拒绝 | `.tmp/sandbox-io-red.log` → `.tmp/sandbox-io-green.log` | 4 新断言先 RED，之后 40 项聚焦测试零失败，2 平台跳过 |
| 搜索/MCP/Skill 回归 | `.tmp/sandbox-audit.log` | core 22 项、tool 6 项全部通过；同次新增集成夹具出现参数长度失败，见下文 |
| 网站文档 | `.tmp/sandbox-docs-build.log` | npm run docs:build 退出 0 |
| 首次完整门禁 | `.tmp/sandbox-clean-verify.log` | SpotBugs 报告祖先路径可空，未跳过门禁，增加显式父路径检查 |
| 修正后完整门禁 | `.tmp/sandbox-final-verify.log` | clean verify 退出 0；全部 15 个 reactor 项目成功，耗时 3:28；SandboxAuditIT 1 项、ToolRegistryAuditIT 2 项通过 |

新增 SandboxAuditIT 的第一次执行因使用完整 Boot classpath（16,430 字符）触发现有 argv 单参数 8,192 字符上限，返回泛化参数错误而非预期授权错误。根因确认为测试夹具；改用 helper 自身 test-classes，不调整生产限制。后续完整 verify 已执行并通过该真实 SQLite/回环 HTTP/Java 子进程链路。

## 课件交付物的兼容映射与实证

课件：`docs/class/第24节：Sandbox 实现与代码讲解.pdf`（完整 10 页）。Feature：`specs/007-sandbox`。采用用户确认的兼容设计，不回退既有 005 契约。

| 交付物 | 真实实现/验证 |
| --- | --- |
| Sandbox 与 Action | tool/sandbox 中保留三种强类型重载及 sealed Action，返回唯一批准 Path/URI；不添加无调用方的旧 ActionType 枚举 |
| 统一异常 | SandboxViolationException 继承 SecurityException，仅允许固定消息，无原始 cause |
| WhitelistSandbox | 三类授权；额外普通根、真实路径、公共 Skill 优先保护、cwd 不扩权 |
| 三块配置 | Boot FileSandboxProperties、ToolProperties、HttpSandboxProperties；oryxos.file.allowed-paths / tool.allowed-commands / http.allowed-domains，启动快照 |
| 四类 Tool 接线 | FileTools/ShellTools/HttpTools/NotifyTools，批准后业务 IO；Notify 保留 resolver → Adapter 顺序 |
| Harness | WhitelistSandboxTest、FileToolsTest、ShellToolsTest、HttpToolsTest、NotifyToolsTest、ToolConfigurationTest |
| 本地真实链路 | SandboxAuditIT：5 个拒绝调用各一条安全审计；6 个成功配对操作各一条审计，HTTP 实际计数 3，拒绝时计数 0，Java helper 标记精确一次 |
| 审计与保密 | 复用现有 ToolExecutor/recorder；错误消息和 input_json 分别验证；Shell 任意 argv 不保存原文 |
| 接口中立性 | 接口不含白名单、容器镜像、VM 配置；扩展可新增实现。现有重载表达动作意图与批准对象，不承诺仅替换实现就具备 whole-process 隔离 |

## 独立审查与追加收敛

独立只读审查完成：未发现 Critical/Important 授权绕过，指出 grep 在遍历限额后跳过超大文件可能清除截断标记的 P2 正确性问题。按实际影响纳入 T020，而非仅当风格建议搁置；先写真实 10,002 文件的黑盒回归，再累积截断状态并重跑完整门禁。

T020 RED 已于 2026-11-02 实际确认：`FileToolsTest#grepKeepsTraversalTruncationWhenOversizedFilesAreSkipped` 运行 1 项，Failures=1、Errors=0、Skipped=0；实际 `{"matches":[],"truncated":false}`，期望 truncated=true。夹具含十个同目录硬链接指向同一个 2MiB 真实文件和 9,992 个空普通文件，保证任意遍历顺序下采集部分都含超大文件；硬链接仅用于降低测试磁盘占用，不是符号链接权限测试。该测试约耗时三分钟，不将其作为性能达标证据。修复采用 OR 累积，最终全仓验证日志为 `.tmp/sandbox-delivery-verify.log`。

OR 修复后的首次完整运行（sandbox-delivery-verify.log）中，大目录断言已 GREEN，FileToolsTest 10 项零失败/1 平台跳过；随后既有 HttpToolsTest.neverFollowsRedirectAndNeverRetries 因 300ms 请求超时未返回 302 而失败，该轮完整门禁未通过。未放宽生产限制或测试断言；单独重跑 HttpToolsTest 5/5 通过（sandbox-http-recheck.log）。为隔离重型文件系统测试与短超时网络单测，保持同一夹具规模及断言，将回归迁到 FileToolsSearchLimitsIT，由已有 Failsafe 生命周期独立 JVM 执行；完整 verify 仍包含该回归，不新增排除/skip。最终重跑日志改为 `.tmp/sandbox-release-verify.log`。

后续 sandbox-release-verify.log 在大目录 IT 之前仍复现同一 HTTP 300ms 超时，故撤回“大目录负载足以解释失败”的推断。测试夹具增加独立 `/ready` 请求预热同一 HTTP client，既有工具请求仍为 300ms，302/不重定向/业务端点计数/慢请求超时断言均未改变，生产代码无改动。FileToolsTest 与 HttpToolsTest 同 JVM 复测 14 项、零失败/1 平台跳过（sandbox-http-fixture-green.log）。最终完整门禁日志为 `.tmp/sandbox-verified.log`，其结果作为最终交付依据。

在 sandbox-verified.log 中，FileToolsSearchLimitsIT 已由 Failsafe 实际执行 1 项并通过（无失败/错误/跳过），截断累积修复的 RED→GREEN 证据完整；该轮后续模块门禁仍需最终退出码确认。

审查明确不代替原生链接平台执行验证，不评估容器/microVM/恶意本地并发/多租户、解释器内部隔离、运行时管理 API/热更新或公网模型/Webhook。这些均为已确认非目标，无额外产品能力宣称。

## 运行与保留范围

需要额外文件根时，在既有本地配置中设置 `oryxos.file.allowed-paths` 为存在的绝对普通目录列表后重启；默认空列表只保留 Agent 读写和绑定 Skill 只读。HTTP/Shell 空白名单默认拒绝。用仓库 Wrapper 按 quickstart 运行本地验收。

用户原有课件、本地配置和其他未跟踪文件保持原状。生产改动集中在 tool/boot 及 core 的 Shell 审计参数摘要，未新增依赖、表、端点或 Profile 字段。原生 symlink 与 junction 缺少实际平台验证时必须单列，不能用静态检查推断已通过；真实云模型、公网 Webhook 未执行。

## 本次实施决定与影响

- 沿用用户批准的 main 原地实施，不新建 worktree/分支、不提交；结果以工作区未提交文件交付，后续由用户安排版本集成。
- Windows 使用任务清单、ledger 和真实日志记录执行过程，替代 Unix task-start/task-done 脚本；不以不存在的提交历史作为证据。
- 先修复已有 RED 支撑的正文读取和审计泄密，再补全统一异常和配置；完整门禁覆盖最终组合，未跳过后续依赖验证。
- Shell 完整请求仅保留 UTF-8 字节数与 SHA-256，避免无法可靠识别的任意 argv 秘密；代价是无法从审计还原原始 argv。
- 将审查的截断问题纳入 T020 必修，以免模型误认为结果完整；代价是增加真实一万项规模回归的运行时间，未增加产品能力或更改质量阈值。
- 仅搁置已批准的非目标和平台未验证范围，没有把未执行的原生链接、公网服务或容器隔离描述为通过。

## Reviewer 导读

建议按以下顺序阅读工作区改动；完整规模以最终 `git diff --stat` 加新增文件为准，未跟踪文件不出现在普通 diff 统计中。

1. `oryxos-tool/.../sandbox/WhitelistSandbox.java`、`SandboxViolationException.java`：固定安全拒绝、额外根真实路径快照、公共 Skill 优先保护和旧构造器兼容。
2. `oryxos-tool/.../builtin/FileTools.java`：edit 提前 READ/WRITE、列举逐项授权、visitor 剪枝、遍历与字节上限、截断状态累积；Shell/HTTP/Notify 各补已知安全异常转换。
3. `oryxos-core/.../tool/ToolExecutor.java`：唯一增量为 Shell 输入复用已有摘要分支；无审计 Schema、写入次数、超时或重试机制更改。
4. `oryxos-boot/.../FileSandboxProperties.java`、`ToolConfiguration.java` 和 `application.yaml`：显式装配单一 Sandbox，新增文件根默认 []。
5. 四工具/配置/Sandbox 单测、`SandboxAuditIT`、`ToolRegistryAuditIT` 和 `FileToolsSearchLimitsIT`：分别查看 RED/GREEN、零业务 IO、SQLite/canary 和真实遍历截断回归。
6. README、网站中英文工具页及 `specs/007-sandbox`：配置语义、课件映射、假设/非目标、任务与本验证记录。

没有创建/切换 Git 分支或提交/推送；用户原有课件与未跟踪文件不属于本次待提交代码，不应误纳入后续版本集成。

## 最终门禁与收敛结果

- **2026-11-02 12:00:33 +08:00**，根目录 `mvnw.cmd clean verify` 最终退出 **0**，15 个 reactor 项目全部 SUCCESS，耗时 **9:42**；日志 `.tmp/sandbox-verified.log`。
- 从各模块 Surefire/Failsafe XML 汇总 **338 项：333 通过、5 skipped、0 failures、0 errors**。大目录 FileToolsSearchLimitsIT **1/1** 通过（237.2 秒），HttpToolsTest **5/5**，SandboxAuditIT **1/1**，ToolRegistryAuditIT **2/2**。
- 全量门禁实际执行测试、Failsafe 集成、可执行 JAR 打包、Checkstyle、SpotBugs/FindSecBugs、JaCoCo；未修改阈值、抑制检查、禁用测试或使用跳过选项。
- `npm run docs:build` 退出 0；`git diff --check` 通过；生产源码凭证模式扫描未命中。模式扫描不等于覆盖所有可能秘密，敏感输入的行为保证另由 canary/真实审计测试提供。
- Spec-Kit converge 对 **13 FR、5 SC、13 个用户故事验收场景**、计划中的模块/配置/批准值/异常/资源限制/审计约束，以及 **6 项宪章原则**完成核对，无剩余 missing/partial/contradicts/unrequested，未追加空收敛阶段。先前 T020 已完成；收敛过程未改写 tasks，后续勾选由 implement 完成状态记录负责。
- 最终任务 **20/20** 完成（原 19 项及追加 T020），requirements checklist **16/16**；checklist 的 Notes 是 specify/clarify 当时的快照，最终实施结果以本记录为准。
- 独立审查发现的唯一 P2 已完成 RED→GREEN 及完整门禁。没有遗留未处理代码问题；明确非目标与未执行平台行为不以“审查通过”冒充运行验证。

### 跳过与未验证范围

1. `ContextLoaderNativeLinksIT`：未设置显式系统属性 `oryxos.tests.native-links`，不声称该 IT 已运行。
2. `MarkdownMemoryStoreTest.rejectsSymbolicLinkTargetWhenPlatformSupportsLinks`：当前 Windows 不能创建测试原生符号链接。
3. `FileToolsTest.nativeLinkCannotEscapeAgentBoundary`：同上。
4. `WhitelistSandboxTest.linkedRootOrLinkedTargetCannotExpandFileBoundary`：同上。
5. `ProviderSmokeIT`：未设置 `oryxos.provider.smoke`，不执行真实模型调用。

Windows junction 未做独立运行验证；现有真实路径偏移检查和静态审查不能代替该平台验证。真实云模型、Webhook、公网搜索/MCP 服务与容器/microVM 隔离均未作为本次端到端成功宣称。全部本节本地能力在上述已披露限制下交付。

## 2026-11-02：用户确认的两个审查修复（T021～T023）

用户请求检查实现后，确认修复 URI 编码改变及文件遍历未响应中断两项问题。本节前述 338 项结果为此前版本记录；本次修复的最终证据以本节新增日志为准。

- **URI RED**：`WhitelistSandboxTest.normalizingHostPreservesEncodedPathAndQuerySemantics` 原 `/a%2Fb?q=a%26b%3Dc` 实际变为 `/a/b?q=a&b=c`。修复改用已校验 scheme/host 和 rawPath/rawQuery 构造批准 URI，不解码再编码；补充真实回环 HTTP 的 requestURI 断言。
- **取消 RED**：新增 `FileToolsCancellationTest`，使用真实 FileTools/WhitelistSandbox/Registry/ToolExecutor；用同步点模拟在途且暂时不响应中断的路径校验。执行器超时并发出中断后，释放在途校验，grep/glob/list_dir 仍有后续检查。初次 grep 夹具使用非法正则，修正为合法 needle 后，`.tmp/sandbox-review-red-confirmed.log` 中 4 项全部以预期断言失败（0 errors/0 skipped）。
- **最小修复**：文件执行入口、授权前后、遍历与搜索循环、读写边界检查 isInterrupted，抛出内部取消并返回固定不可重试失败，不清除中断标志，不改变权限、审计/重试或 ToolExecutor 接口。
- **聚焦 GREEN**：`.tmp/sandbox-review-green.log`，URI/取消/文件/HTTP/MCP/搜索共 32 项，0 failures/0 errors/2 平台 skipped。
- **取消保证边界**：协作式取消不能强制抢占已经进入的原生系统调用；在该调用返回后停止后续操作。测试明确在途校验返回后才检查后续访问次数，不把整个 IT 耗时当作搜索本身耗时。
- **完整门禁**：`.tmp/sandbox-review-verify.log` 运行中，包含新增真实 HTTP 编码用例、三种取消场景及既有全部回归。通过后再完成 T021～T023 和收敛。

### 本次修复最终结果

2026-11-02 17:56:10 +08:00，`.tmp/sandbox-review-verify.log` 全仓 `mvnw.cmd clean verify` 退出 **0**，15 个 reactor 项目全部成功，耗时 12:55。从 Surefire/Failsafe XML 汇总 **343 项：338 通过、5 skipped、0 failures、0 errors**。跳过项与上一版相同，原生链接/junction 及真实外部验证范围不变。

新增 URI 编码单测、真实回环 HTTP requestURI 用例、grep/glob/list_dir 三种真实执行器取消场景全部通过；既有大目录、MCP/搜索、SQLite 审计、启动与打包、Checkstyle/SpotBugs/JaCoCo 均通过。取消测试固定了后续授权检查次数为 0，并等待后台操作实际结束；没有将返回超时等同于后台已停止。

按当前 spec/plan/contracts/tasks 重新收敛，两项问题均有 RED→GREEN 与完整门禁，未发现剩余缺口，不追加空收敛阶段；任务 **23/23**。本轮生产增量仅 WhitelistSandbox 与 FileTools，ToolExecutor 的执行、超时、重试和审计机制均未改动；其余工作区已有改动保留。`git diff --check` 通过，未提交或推送。
