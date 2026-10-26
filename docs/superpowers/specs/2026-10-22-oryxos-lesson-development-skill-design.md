# OryxOS 节课驱动开发 Skill 设计

## 1. 目标

创建项目级 Skill `oryxos-lesson-development`。用户给出第 16–31 节中的节号，或使用“开发第 N 节”“实现第 N 节课的模块”“用 spec kit 做第 N 节”等表达时，Skill 按课件驱动 OryxOS 当节模块开发。

Skill 固定执行以下阶段：

`备料 → speckit-specify → clarify → plan → tasks → 停等确认 → implement → 节级验收报告`

核心纪律是：能由命令和结构化数据判断的条件全部自动检查；无法可靠判断的契约、冲突或范围选择必须停止并请求用户确认。

## 2. 存放与发现

- 主体位于 `.SKILLS/oryxos-lesson-development/`。
- `AGENTS.md` 增加一条最小路由规则：命中课程开发触发表达时，完整读取该 Skill 的 `SKILL.md` 后执行。
- Skill 名称固定为 `oryxos-lesson-development`，同时支持显式调用和自然语言触发。
- 不复制 `.claude/skills/speckit-*` 的正文；运行时按阶段读取对应的现有 Spec-Kit Skill，避免两份流程漂移。

## 3. 真相源与优先级

每次执行至少读取：

1. 仓库 `AGENTS.md`；
2. 对应 `docs/class/第N节*.pdf`；
3. `docs/TechnicalSolution.md`；
4. `docs/oryxos.md`；
5. `docs/DemandAnalysis.md`；
6. `docs/AiProgrammingGuide.md`；
7. `.specify/memory/constitution.md`；
8. 当前代码、测试、POM、配置和相关既有 Spec-Kit 工件。

课件定义当节范围和“本节交付物”；`TechnicalSolution.md` 定义架构、模块归属和既定字面量；`oryxos.md` 定义项目边界。两者或课件之间出现冲突时，不选择一方继续，而是触发软门禁。

仓库实际文件名以 `docs/TechnicalSolution.md` 和 `docs/oryxos.md` 为准。用户输入中的 `TechnoclaSOltuion.md`、`oryx.md` 视为对这两个文件的指代，不创建同名替代文档。

## 4. Skill 结构

```text
.SKILLS/oryxos-lesson-development/
├── SKILL.md
├── agents/
│   └── openai.yaml
├── references/
│   ├── workflow.md
│   ├── gates.md
│   └── artifact-contract.md
└── scripts/
    └── lesson-gate.ps1
```

- `SKILL.md`：触发条件、总纪律、阶段路由和停止条件。
- `workflow.md`：备料至验收报告的详细状态机和 Spec-Kit 对接方式。
- `gates.md`：硬门禁、软门禁、反作弊和授权边界。
- `artifact-contract.md`：`lesson-contract.json` 与节级验收报告的结构约定。
- `lesson-gate.ps1`：执行可机械判断的前置、任务和验收检查，输出 JSON，非零退出码阻止继续。

## 5. 备料阶段

调用前先解析节号，只接受 16–31。备料阶段不得修改产品代码，执行以下动作：

1. 检查工作区状态并记录现有未提交修改；
2. 精确定位唯一的 `docs/class/第N节*.pdf`；缺失或多份均停止；
3. 检查必需文档、Spec-Kit 脚本和模板是否存在；
4. 读取完整课件并提取：范围、前置依赖、本节交付物、明确改造点、类名、方法签名、配置键、表与列、REST 路径、第三方 API/依赖、测试与验收命令；
5. 对照技术方案、项目定义、需求分析、宪章和当前代码；
6. 生成当节 `lesson-contract.json`，作为后续存在性核对与范围审计的机器输入；
7. 对不确定项、冲突项或越界项触发软门禁，不进入 specify。

当前仓库仅存在第 16–20 节课件；第 21–31 节在对应 PDF 加入前会因输入不存在而停止。

## 6. Spec-Kit 阶段

### 6.1 Feature 隔离

每节使用独立 `specs/<序号>-lesson-<N>-<slug>/`。不得依赖当前 `.specify/feature.json` 猜测活动 feature；每次调用脚本均显式设置 `SPECIFY_FEATURE_DIRECTORY`。

若已有 feature 可能对应该节，先根据 lesson contract、spec 标题和范围匹配：

- 唯一且完整匹配：复用；
- 无匹配：创建新 feature；
- 多个匹配或范围交叉：软门禁停等用户选择。

### 6.2 阶段规则

- `specify`：只描述当节 WHAT/WHY、用户故事、可验证要求和明确非目标，不提前引入实现概念。
- `clarify`：按现有 `speckit-clarify` 一次一个问题，最多五个；机器无法判断且会改变契约的事项必须询问。
- `plan`：从课件、技术方案和本地代码确定实现；依赖、公共契约和既定字面量全部进入 Constitution Check 与 lesson contract 追踪。
- `tasks`：每个任务必须带精确文件路径，并能追踪到课件交付物、FR/SC 或 plan 决策；测试任务置于实现任务之前。
- `analyze`：tasks 生成后执行只读一致性分析；存在 CRITICAL/HIGH 缺口时先修正文档，不请求实现确认。
- 停等点：tasks 和 analyze 均通过后，展示范围、文件、依赖、测试与门禁摘要，明确停止，等待用户批准。
- `implement`：仅在用户明确批准当前 tasks 后开始；按任务顺序和 TDD 实现，完成后勾选任务。
- `converge`：实现和硬门禁后对代码与 spec/plan/tasks 做差距检查；若追加任务，回到 implement 并重新执行验收。

## 7. 门禁

### 7.1 硬门禁

以下条件由脚本或命令判定，任何一项失败都不得宣称完成：

- 节号范围、唯一课件、必需文档及 Spec-Kit 基础设施存在；
- Spec-Kit 阶段工件和质量 checklist 完整，无未解决占位符；
- `tasks.md` 格式有效，进入实现前已获得用户确认，验收时全部完成；
- 课件交付物清单中的路径、类型、配置、迁移、测试或文档按 `lesson-contract.json` 存在；
- 聚焦测试通过；
- Windows 执行 `./mvnw.cmd clean verify`，其他系统执行 `./mvnw clean verify`，退出码为 0；
- 最新验证输出无被忽略的失败。

硬门禁不允许使用删除断言、增加 `@Disabled`、减少测试范围、放宽覆盖率或静态检查阈值等方式变绿。

### 7.2 软门禁

发现以下任一情况立即停止，报告证据、影响和可选处理，等待用户决定：

1. 需要创建本节交付物之外的 public 类型、配置键、数据表、REST 路径或 Profile 字段；
2. 需要修改已定类名、方法签名、配置键、表列名或端点路径；
3. 课件与 `docs/TechnicalSolution.md` 冲突；
4. 需要修改前序节公共接口，且本节未明确列为改造点；
5. 第三方 API 或 artifact 无法从当前代码、锁定 BOM、本地 Maven 依赖或官方本地资料核实；
6. 需要新增 plan 未列明的第三方依赖。

用户确认只放行报告中列明的具体决定，不自动放宽其他门禁。

## 8. 第三方依赖核验

优先执行本地、离线核验，包括父 POM/BOM、现有依赖树、本地 Maven 仓库和已锁定版本。不得因课件示例而推断 artifact、类或 API 一定存在。

若本地无法核实，触发软门禁。未经用户明确决定，不联网拉取、不新增依赖、不根据记忆改写 API。

## 9. 节级验收报告

完成后在对应 feature 目录生成 `acceptance-report.md`，至少包含：

- 节号、课件与 feature 路径；
- 本节交付物逐项证据；
- spec/plan/tasks 追踪摘要；
- 修改文件和模块；
- 聚焦测试与 `clean verify` 的实际命令、时间和结果；
- 存在性核对结果；
- 软门禁及用户决策记录；
- 未运行的真实外部调用及原因；
- 明确未完成范围。

只有硬门禁全部通过、converge 无剩余任务时，报告状态才允许写“完成”。

## 10. 禁止的外部动作

全流程不得自动：

- `git commit`；
- `git push`；
- 创建或修改远程 PR/Issue；
- 执行 `package.sh`；
- 使用真实密钥调用外部服务；
- 删除或覆盖用户无关的未提交修改。

## 11. 验证 Skill 本身

创建 Skill 前先执行无 Skill 基线检查，确认当前仓库不存在课程号路由、门禁脚本和结构化 lesson contract。创建后验证：

1. Skill frontmatter、名称和目录结构通过官方 `quick_validate.py`；
2. `lesson-gate.ps1` 对非法节号、缺失课件、缺失工件、未完成任务和失败构建返回非零；
3. 对现有第 16 节只读准备检查成功；
4. 对缺失的第 21 节准备检查失败且错误可操作；
5. 静态检查确认 Skill 明确包含 tasks 停等点、六类软门禁、反作弊和禁止外部动作；
6. 不运行会修改产品代码的课程实施测试。

