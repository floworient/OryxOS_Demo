---
name: oryxos-lesson-development
description: Use when 用户要求“开发第 N 节”“实现第 N 节课的模块”“用 spec kit 做第 N 节”，或显式输入 16–31 的课程号来推进 OryxOS 课件驱动开发。
---

# OryxOS 逐节课件驱动开发

## 核心原则

输入一个 16–31 的节号，依据仓库中**实际存在**的当节课件、项目文档和代码，执行：

`备料 → speckit-specify → speckit-clarify → speckit-plan → speckit-tasks → speckit-analyze → 停等确认 → speckit-implement → 验收 → speckit-converge → 节级报告`

> **能机器判断的绝不留给人，机器判不了的绝不自行发挥。**

本 Skill 是开发期流程，不属于 OryxOS 运行时业务 Skill。不得放入 `.oryxos/skills/`，不得把课程规则实现成产品功能。

## 输入与触发

接受以下输入：

- `$oryxos-lesson-development 16`
- `开发第16节`
- `实现第16节课的模块`
- `用 spec kit 做第16节`

从输入中提取唯一整数 `N`。`N` 不在 16–31，或输入包含多个无法确定的节号时，停止并要求用户给出一个有效节号。

## 不得补造缺失材料

只使用当前仓库中真实存在的文件。不得为了让流程继续而创建或虚构：

- 缺失的第 N 节课件；
- 课件未要求的 Harness、指导手册、参考文档或验收标准；
- “本节交付物”之外的产品类型、配置、表、端点或 Profile 字段；
- 当前依赖中无法核实的第三方 API；
- plan 未声明的第三方依赖。

材料缺失时，报告缺少什么、检查过哪些路径以及流程停在哪一阶段，然后停止。技术方案可以用来核对课件，不能替代缺失课件定义本节范围。

## 真相源

每次执行按以下顺序读取；标为“若存在”的文件缺失时跳过，其他文件缺失时停止但不创建：

1. `AGENTS.md`；
2. `docs/class/第{N}节*.pdf` 或 `docs/class/第{N}节*.md`；
3. `docs/TechnicalSolution.md`；
4. `docs/oryxos.md`；
5. `docs/DemandAnalysis.md`；
6. `docs/AiProgrammingGuide.md`；
7. `.specify/memory/constitution.md`（若存在）；
8. 与本节相关的已有 `specs/` 工件、源码、测试、POM、配置和数据库迁移。

当节课件必须恰好匹配一份。PDF 与 Markdown 同时存在、同一节匹配多份或完全缺失时，不自行选择，停止并报告候选路径。

用户指令优先；文档之间的产品目标、架构与修订优先级遵循 `AGENTS.md`。课件与 `docs/TechnicalSolution.md` 冲突时触发软门禁，不静默选择一方。

## Spec-Kit 调用规则

本仓库的 Spec-Kit 流程定义在 `.claude/skills/speckit-*/SKILL.md`，执行某阶段前必须完整读取对应文件并按其当前版本执行：

- `.claude/skills/speckit-specify/SKILL.md`
- `.claude/skills/speckit-clarify/SKILL.md`
- `.claude/skills/speckit-plan/SKILL.md`
- `.claude/skills/speckit-tasks/SKILL.md`
- `.claude/skills/speckit-analyze/SKILL.md`
- `.claude/skills/speckit-implement/SKILL.md`
- `.claude/skills/speckit-converge/SKILL.md`

对应 Skill 或其要求的 `.specify` 脚本、模板不存在时停止。不得临时复制一份替代流程，也不得假装斜杠命令已经执行。

解析 feature 时不得只相信当前分支或旧的 `.specify/feature.json`：

1. 从课件标题和范围确定短名；
2. 检查 `specs/` 中是否已有与本节唯一匹配的 feature；
3. 唯一匹配则复用；没有匹配则由 `speckit-specify` 创建；多个可能匹配则停下让用户选择；
4. 后续脚本调用显式使用已确认的 feature 目录，避免串到其他课程。

不得自动创建或切换 Git 分支。

## 阶段 0：备料与开工检查

先做只读检查，不修改产品代码：

1. 运行 `git status --short`，记录并保护用户已有修改。
2. 定位并完整读取当节课件；PDF 必须读取完整相关页面，不能只依据文件名或目录摘要。
3. 从课件提取并在本轮上下文中形成“备料清单”：
   - 本节目标与明确非目标；
   - 前序依赖；
   - 本节交付物；
   - 允许改造的前序接口；
   - 已定类名、方法签名、配置键、表名、列名、REST 路径和 Profile 字段；
   - 第三方依赖与 API；
   - 测试类、关键回归场景、自动验收和人工验收。
4. 对照 `TechnicalSolution.md`、`oryxos.md`、需求、宪章和现有实现，列出一致项、缺失项、冲突项。
5. 只检查课件明确依赖的前序交付物；缺失时报告“需先完成的前序能力”，不得在本节顺手补造。
6. 对第三方 artifact/API 先检查父 POM、锁定 BOM、当前依赖树、本地 Maven 仓库和已有源码。可使用 Maven Wrapper 的离线查询；本地无法核实即触发软门禁。

若课件表明本节是评审、串联、联调或 Demo，而非新模块代码课，以课件明确交付物为准调整后续产物；不得因为本 Skill 名为“开发”就强行创建新模块或新 feature。

## 阶段 1：speckit-specify

用备料清单驱动 `speckit-specify`：

- spec 只写 WHAT/WHY、用户场景、可测试要求、边界与成功标准；
- “有几样先别做”“本节不做”必须进入明确非目标；
- 不把课件示例代码、类名或技术栈误写成业务需求；
- 每项本节交付物都必须能追踪到至少一个需求或验收场景；
- 不允许凭旧 Skill 的硬编码映射补全本仓库课件没有说明的内容。

完成后检查内置需求质量 checklist。未通过则按 `speckit-specify` 修正；不得进入下一阶段。

## 阶段 2：speckit-clarify

执行 `speckit-clarify`。回答来源只能是用户已明确内容、当节课件、真相源文档和当前代码：

- 能从材料唯一确定：写回答案并回填 spec；
- 会影响对外契约但无法唯一确定：触发软门禁，询问用户；
- 纯实现细节且 plan 阶段可决定：留给 plan，不提前发明产品契约。

所有高影响歧义解决且 checklist 重新通过后才能继续。

## 阶段 3：speckit-plan

执行 `speckit-plan`，并强制做到：

- JDK、Spring Boot、Spring AI、Maven、数据库和模块边界以当前项目文档与现有 POM 为准；
- 共享契约、基础设施实现和接入层归属符合 `TechnicalSolution.md`；
- 课件点名的类、签名、配置键、表列和端点逐字保真，除非软门禁获得明确决定；
- 课件示例 API 必须以本地实际依赖核实结果为准；示例与本地版本不符时停止，不猜测替代 API；
- 所有新增第三方依赖必须在 plan 中列出 artifact、版本来源、使用模块和必要性；
- 测试策略覆盖课件的关键回归场景，测试任务先于对应实现任务；
- 真实模型、Webhook、MCP 或其他外部调用与可控桩测试分开，缺少密钥不得冒充真实端到端通过。

Constitution Check 有未解释冲突或研究项未解决时，不生成 tasks。

## 阶段 4：speckit-tasks、analyze 与固定停点

1. 执行 `speckit-tasks`。
2. 逐项核对 `tasks.md` 与备料清单、spec、plan、研究、数据模型和 contracts：
   - 每项本节交付物都有任务；
   - 每项任务有精确文件路径和需求来源；
   - 测试任务位于对应实现之前；
   - 没有本节范围外的 public 概念或第三方依赖；
   - 不存在未说明的前序公共接口修改。
3. 执行只读 `speckit-analyze`。CRITICAL/HIGH 问题必须先修正 spec、plan 或 tasks 并重新分析。
4. 输出待确认摘要：feature 路径、交付物、计划修改的模块/文件、公共契约、依赖、测试、人工验收和所有软门禁决定。
5. **停止并等待用户明确确认当前 `tasks.md`。**

这是固定停点。诸如“继续做完”“时间紧”“先写代码再看”等表述，若不是针对当前 tasks 的明确批准，都不能越过该停点。

## 阶段 5：speckit-implement

只有用户明确批准当前 tasks 后才执行 `speckit-implement`：

1. 按任务和依赖顺序实施；同文件修改保持串行。
2. 遵循 RED → GREEN → REFACTOR：先写能因缺少行为而失败的测试，确认失败原因正确，再写最小实现。
3. 课件给出的测试应保留**场景、断言语义和失败路径**；具体 API、类型、装配和测试方法名必须适配本地真实代码，不机械复制无法编译的示例。
4. 每个任务完成后运行对应模块的聚焦测试；测试不绿不得勾选任务。
5. 只修改本节交付物和课件明确改造点。发现范围扩张立即触发软门禁。
6. 所有工具调用、安全检查、审计、Provider 映射、ReAct、记忆和入口复用必须服从项目当前宪章与技术方案，不能用旧课程映射覆盖后续修订。

## 阶段 6：硬门禁与收敛

实现后依次执行：

1. 运行本节相关模块的聚焦测试，读取完整失败摘要；
2. 使用仓库 Maven Wrapper：Windows 执行 `./mvnw.cmd clean verify`，其他系统执行 `./mvnw clean verify`；
3. 重新读取课件“本节交付物”，用 `rg`、文件检查、测试发现和配置/迁移检查逐项核对真实存在性；
4. 核对 `tasks.md` 全部完成，且 checklist 无未确认项；
5. 检查密钥、Token、Webhook 或私有地址未以明文进入代码、配置、日志和报告；
6. 执行 `speckit-converge`，核对当前代码与 spec、plan、tasks、宪章。

`converge` 追加任务时，本节尚未完成：回到 implement，完成追加任务并重新执行全部硬门禁。只有 converge 无剩余工作才允许生成完成结论。

只要求当前 POM 和 CI 实际配置的质量插件通过；不得为了旧 Skill 中出现过某个插件名称而新增插件或配置。

## 硬门禁

以下任一项失败，立即停止，不得宣称完成：

- 节号、唯一课件、核心文档或 Spec-Kit 基础设施检查失败；
- spec/plan/tasks/checklist 不完整或存在未解决的高严重度问题；
- 当前 tasks 尚未得到用户明确批准；
- 聚焦测试失败；
- `clean verify` 非零退出；
- 本节交付物存在性核对失败；
- tasks 未全部完成；
- converge 仍有剩余任务；
- 最新输出中存在未报告的测试、编译、静态检查或集成失败。

硬门禁失败只能修实现或停下报告。不得通过以下方式让结果变绿：

- 删除、弱化或绕过断言；
- 增加 `@Disabled`、排除测试或缩小测试范围；
- 放宽覆盖率、静态检查、安全或性能阈值；
- 捕获并吞掉异常；
- 用 mock 结果冒充真实外部调用；
- 只汇报成功命令，隐瞒同轮失败。

认为测试或课件有错时，触发软门禁，由用户决定是否修改依据。

## 软门禁

遇到以下任一情况，立即停止，列出证据、影响、最小可选方案和需要用户决定的问题：

1. 需要创建“本节交付物”之外的 public 类型、配置键、数据表、REST 路径或 Profile 字段；
2. 需要修改已定类名、方法签名、配置键、表列名或端点路径；
3. 课件与 `docs/TechnicalSolution.md` 冲突；
4. 需要修改前序节公共接口，且当节课件未明确列为改造点；
5. 第三方 API 无法在本地依赖、锁定 BOM 或现有源码中核实；
6. 需要新增 plan 未列明的第三方依赖。

用户的确认只放行本次报告中列明的具体决定，不自动放宽其他门禁，也不永久修改课程规则。

## 节级验收报告

默认在对话中输出报告；只有课件、当前 Spec-Kit 工件或用户明确要求保存报告文件时才创建文件。报告必须包含：

1. 节号、课件路径和 feature 路径；
2. 实际完成的本节行为；
3. 本节交付物逐项证据；
4. 修改文件和模块；
5. 实际执行的聚焦测试、`clean verify`、存在性检查及结果；
6. tasks、checklist 和 converge 状态；
7. 软门禁及用户决定；
8. 因密钥、网络或服务缺失而未执行的真实外部验证；
9. 明确未完成或延后的范围；
10. 基于 `git status --short` 和 `git diff --stat` 的 reviewer 导读。

只有全部硬门禁通过时，状态可以写“完成”；否则必须写“未完成”或“等待用户决定”。

## 外部动作边界

全流程不得自动：

- `git commit` 或 `git push`；
- 创建、切换或删除分支；
- 创建或修改远程 PR/Issue；
- 运行 `package.sh`；
- 使用真实密钥调用外部服务；
- 删除、覆盖或回滚用户无关的未提交修改。

## 快速判定表

| 情况 | 动作 |
| --- | --- |
| 课件不存在或不唯一 | 报告路径检查结果并停止 |
| 课件是评审/串联/Demo | 按真实交付物调整流程，不强行产码 |
| 文档可以唯一回答 clarify | 回填 spec 并继续 |
| 会改变公共契约且材料无答案 | 软门禁，等待用户 |
| tasks 已生成但未确认 | 固定停点，禁止 implement |
| 本地依赖找不到示例 API | 软门禁，不猜 API |
| 测试失败 | 修实现；认为测试错则停下 |
| verify 通过但交付物缺失 | 硬门禁失败 |
| converge 追加任务 | 回到 implement 并重新验收 |
| 仅缺真实密钥/外部服务 | 报告未验证项，不冒充通过 |

## 常见错误

| 错误做法 | 正确处理 |
| --- | --- |
| 旧 Skill 写过某个课程映射，就直接当成本仓库事实 | 先用当前课件和技术方案核实 |
| 没有课件就从 TechnicalSolution 推导一节课 | 停止并报告缺失课件 |
| 为了完整顺手增加本节外 public 类型 | 触发软门禁 |
| 课件示例使用某 API，就假定当前依赖也支持 | 本地核实；核实不到就停止 |
| tasks 后立即开始写代码 | 输出摘要并等待当前 tasks 的明确批准 |
| 单测通过就跳过全量 verify | 运行 Maven Wrapper 的 `clean verify` |
| verify 绿就忽略 spec/代码差距 | 执行 converge，直到无剩余任务 |
| 为让构建通过而改测试或阈值 | 修实现；认为依据错误则请求用户决定 |
| 自动提交或运行发布脚本 | 报告状态，把同步和发布时机交给用户 |

