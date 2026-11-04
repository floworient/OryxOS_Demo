# Tasks: 第 24 课 Sandbox 核心补全

**Input**: spec.md、plan.md、research.md、data-model.md、contracts/sandbox.md、quickstart.md 与已确认设计。
**Status**: 已完成 23/23，包括用户确认的两个审查修复；最终全仓门禁及收敛通过，未验证范围见 verification.md。
**授权记录**: 先完成两个 RED 测试，随后用户「ok确认方案，继续执行」批准完整任务及两项修复；checkbox 表示已取得对应测试证据。
**Tests**: 课件及 FR-013 明确要求 TDD 与拦截 harness。先测试 RED，后实现 GREEN；既有回归只记录真实结果，不人为制造失败。

所有路径均相对仓库根；测试与实现步骤的具体断言/命令见 `docs/superpowers/plans/2026-11-01-sandbox.md`。checkbox 以本文件为唯一完成状态来源，不自动 commit/push/切分支。

## Phase 1: Setup

- [x] T001 对照 `pom.xml`、`oryxos-boot/src/test/java/io/oryxos/boot/ToolRegistryAuditIT.java` 与 `specs/007-sandbox/quickstart.md` 检查 JDK 21、前序 Registry/审计/工具可用，运行聚焦基线并在 `specs/007-sandbox/verification.md` 记录现有修改和结果（FR-010、013；SC-005）。

## Phase 2: Foundational

- [x] T002 在 `oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java` 增加统一异常类型、SecurityException 兼容、固定文本、无目标/cause、默认文件/Shell 拒绝测试，先确认缺失行为 RED（FR-008、011、013）。
- [x] T003 新增 `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/SandboxViolationException.java`，修改同目录 `Sandbox.java` 和 `WhitelistSandbox.java` 统一运行期授权拒绝；仅使用 contracts 三种固定消息，不携带原始目标/cause；配置错误独立且脱敏，运行 T002 GREEN（FR-008、011）。

## Phase 3: US1 显式授权额外业务目录（P1，MVP）

独立验收：额外根普通文件可读写；Agent/绑定 Skill 默认权限不回退；额外根不能扩展公共 Skill 或 cwd。

- [x] T004 [US1] 在 `oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java` 先测新构造器及权限矩阵：根“存在、绝对、普通目录、无原生链接组件”、null/空白/相对/不存在根拒绝、重复根去重、空根、同名前缀、穿越、软链接/junction、公共根不存在/祖先授权、未绑定 Skill、绑定只读、cwd 不扩权、启动快照，记录 RED（FR-001～005、012、013；SC-001）。
- [x] T005 [P] [US1] 在 `oryxos-tool/src/test/java/io/oryxos/tool/builtin/FileToolsTest.java` 先测额外根六工具允许/拒绝，`list_dir/grep/glob` 从宽泛根不输出受保护 Skill 名称/元数据/内容，拒绝写入不改字节/存在性、遍历限额与排序不回退，记录 RED（FR-002～005、009、011；SC-001、002）。
- [x] T006 [P] [US1] 在 `oryxos-boot/src/test/java/io/oryxos/boot/ToolConfigurationTest.java` 先测文件根默认空、null 列表为空但 null/空白条目非法、防御快照、合法绑定、非法根脱敏启动失败及唯一 toolSandbox，记录 RED（FR-001、012；SC-001、004）。
- [x] T007 [US1] 在 `oryxos-tool/src/main/java/io/oryxos/tool/sandbox/WhitelistSandbox.java` 实现 contracts 完整构造器与旧构造器兼容、根不可变快照和真实路径校验、公共根优先保护及最近父目录 WRITE 验证，不创建业务目录；通过 T004（FR-001～005、012）。
- [x] T008 [US1] 在 `oryxos-tool/src/main/java/io/oryxos/tool/builtin/FileTools.java` 实现列举逐项审批、遍历 preVisitDirectory 剪枝和文件逐项审批、仅过滤明确拒绝、保留资源上限及原子写前复验；安全异常转换并同步描述；通过 T005（FR-002～005、008、009、011）。

T005/T008 补充：edit_file 必须先通过 READ/WRITE 授权再读取目标正文；观测实际正文读取次数为零，不能以目标未修改代替。允许路径、绑定和 Skill 元数据校验 IO。
- [x] T009 [US1] 新增 `oryxos-boot/src/main/java/io/oryxos/boot/FileSandboxProperties.java`，修改同目录 `ToolConfiguration.java` 与 `oryxos-boot/src/main/resources/application.yaml` 显式装配 oryxos.file.allowed-paths（默认 []），传入工作区公共 skills 路径，保留现有 HTTP/Shell 键及启动快照；通过 T006 与 US1 全部测试（FR-001、012）。

## Phase 4: US2 越界请求零 IO（P1）

独立验收：四类工具拒绝真实操作前置，成功仍用批准目标执行一次；无需公网。

- [x] T010 [P] [US2] 在 `oryxos-tool/src/test/java/io/oryxos/tool/builtin/ShellToolsTest.java` 先测 alias 大小写/前导空格拒绝、额外根 cwd 拒绝、安全原因不可重试；用当前 JDK 的 Java helper 配对证明授权一次启动、拒绝零标记/副作用，回归 argv 元字符/超时/双流/进程树，确认新行为 RED（FR-006、008、009；SC-002、005）。
- [x] T011 [P] [US2] 在 `oryxos-tool/src/test/java/io/oryxos/tool/builtin/HttpToolsTest.java` 先测 GET/POST 非授权及非法 URI 的固定失败与零回环请求，验证批准 URI、通配标签边界、默认拒绝、重定向关闭，确认新行为 RED（FR-007～009、011；SC-002、004）。
- [x] T012 [P] [US2] 在 `oryxos-tool/src/test/java/io/oryxos/tool/builtin/NotifyToolsTest.java` 先测 resolver 授权拒绝后 Adapter never、固定 HTTP 失败原因，成功发送一次且目标为批准 URI，确认新行为 RED（FR-007～009、011；SC-002、004）。
- [x] T013 [US2] 修改 `oryxos-tool/src/main/java/io/oryxos/tool/builtin/ShellTools.java`、同目录 `HttpTools.java`、`NotifyTools.java`，在通用 catch 前只识别 SandboxViolationException 并返回固定不可重试失败；保留全部 IO 限制和 resolver/Adapter 顺序，T010～T012 及四工具回归 GREEN（FR-006～009、011；SC-002、005）。

## Phase 5: US3 拒绝可审计且不泄密（P2）

独立验收：真实 ToolExecutor + 本地 SQLite/JPA，拒绝只记录一条、无重试、原因一致、秘密无明文。依赖 US1/US2 的统一结果，测试不能绕过真实持久化。

- [x] T014 [US3] 新增 `oryxos-boot/src/test/java/io/oryxos/boot/SandboxAuditIT.java`，复用既有 IT 装配，参数化四工具可见但越界调用、合法配对操作与 canary；断言失败不可重试、每调用一条 SQLite 记录、errorMessage 一致、目标副作用零及结果/日志/审计无秘密；使用 safe-java/helper 完成本地真实链路验收（FR-009～011、013；SC-002～004）。
- [x] T015 [US3] 按 `specs/007-sandbox/quickstart.md` 运行 `oryxos-tool/src/test/java/io/oryxos/tool/mcp/McpConfigLoaderTest.java`、同目录 `McpClientServiceTest.java`、`oryxos-tool/src/test/java/io/oryxos/tool/search/DuckDuckGoInstantAnswerSearchProviderTest.java` 和 core 绑定/上下文回归，记录配置/授权失败分类不变；失败仅修既定范围，范围外触发报告（FR-010、012、013；SC-005）。

T014 补充：先在既有 `oryxos-boot/src/test/java/io/oryxos/boot/ToolRegistryAuditIT.java` 固定 Shell arguments 泄密的 RED，再单独修正 ToolExecutor.auditInput，不改 recorder/次数/Schema；该修复现已获用户明确授权。

## Phase 6: Polish 与交付门禁

- [x] T016 同步 `README.md`、`website/zh/docs/tool.md`、`website/docs/tool.md` 的文件额外根、空白名单、Skill 优先级、解释器权限和重启生效说明；依 `specs/007-sandbox/contracts/sandbox.md` 解释课件映射，沿用中英文文件风格（FR-002、006、012、013）。
- [x] T017 运行 `specs/007-sandbox/quickstart.md` 的聚焦测试、IT、根目录 `mvnw.cmd clean verify`、网站 docs:build、git diff --check 和秘密扫描，在 `specs/007-sandbox/verification.md` 记录完整失败/成功与平台 skipped，禁止弱化门禁（FR-013；SC-001～005）。
- [x] T018 完整读取 `.claude/skills/speckit-converge/SKILL.md` 并执行收敛，核对 `specs/007-sandbox/spec.md`、`plan.md`、`tasks.md` 与真实交付物；若追加任务回到实施并重跑硬门禁（FR-010、013；SC-001～005）。
- [x] T019 在 `specs/007-sandbox/verification.md` 给出第 24 课逐项映射证据、接口中立性自查、实际命令、未验证外部/平台范围、tasks/checklist/converge 状态和 git status/diff reviewer 导读；只有无剩余工作才报告完成（FR-013；SC-005）。

## Dependencies & Execution Order

T001 → T002 → T003 → US1（T004～T009）→ US2（T010～T013）→ US3（T014～T015）→ T016～T019。

同文件修改串行。T004/T005/T006 在 T003 后可并行编写，T007 必须先于 T008/T009 的最终 GREEN；T010/T011/T012 在 US1 后可并行编写，T013 在三组新行为 RED 后实施。US3 是集成验收，不人为制造失败；若测试暴露缺口，补正确 RED/GREEN 证据并仅修既定行为。

## Parallel Examples

- US1：FileToolsTest 与 ToolConfigurationTest 文件独立，可并行；公共 Sandbox 的实现保持串行。
- US2：ShellToolsTest、HttpToolsTest、NotifyToolsTest 独立，可并行；各实现必须等待对应新测试 RED。
- US3：持久化 IT 依赖前序结果，不与前序实现并行；MCP/搜索回归可在实现稳定后分别运行，但 Maven target 输出避免并发竞争。

## Implementation Strategy

采用当前会话逐项执行方式，先交付 US1 最小增量，再补齐零 IO 和审计验证；[P] 只表明潜在并行能力，不自动授权启动实施代理。每个实现任务先运行对应聚焦测试，再勾选。19 项全部完成、converge 无追加任务才构成整课完成。

## Phase 7: Convergence

- [x] T020 修复 `oryxos-tool/src/main/java/io/oryxos/tool/builtin/FileTools.java` 的 grep 截断标志累积逻辑，先在 FileToolsTest 确认“超过遍历限额且含超大文件”的 RED，再迁至同模块 `src/test/java/io/oryxos/tool/builtin/FileToolsSearchLimitsIT.java` 由 Failsafe 独立 JVM 保留同一真实规模/断言，验证 GREEN 和重跑全仓 clean verify，保证截断不会被清零；来源 FR-013、SC-005、plan: 保留遍历上限与明确截断（partial，P2）。

## Phase 8: 用户确认的审查修复

- [x] T021 按用户“修复这两个问题”和 FR-007，在 `oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java` 先复现编码路径/查询被改写的 RED，修复同模块 `src/main/java/io/oryxos/tool/sandbox/WhitelistSandbox.java` 仅规范化 scheme/host 并保留 rawPath/rawQuery；在 HttpToolsTest 回环端点验证真实发出的编码一致。
- [x] T022 按用户确认的执行器取消问题，在 `oryxos-tool/src/test/java/io/oryxos/tool/builtin/FileToolsCancellationTest.java` 经真实 ToolExecutor 复现 grep/glob/list_dir 超时后仍发生后续授权检查的 RED；在同模块 `src/main/java/io/oryxos/tool/builtin/FileTools.java` 的授权前后、遍历/搜索循环、读写边界响应中断，保持中断标志，不扩展权限或修改审计机制。
- [x] T023 对 T021/T022 运行聚焦测试、相关回归与全仓 clean verify，记录 RED/GREEN、系统调用不可强制抢占的边界及未验证平台范围；重新执行收敛并更新 `specs/007-sandbox/verification.md`，有剩余问题不得宣称完成。
