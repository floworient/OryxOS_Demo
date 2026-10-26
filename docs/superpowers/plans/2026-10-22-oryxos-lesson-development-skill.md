# OryxOS 节课驱动开发 Skill Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 创建一个单文件项目 Skill，根据现有课件和 OryxOS 文档执行第 16–31 节的 Spec-Kit 开发流程，并在材料缺失、契约冲突或硬门禁失败时停止。

**Architecture:** Skill 仅保存在 `.SKILLS/oryxos-lesson-development/SKILL.md`。它吸收现成 Claude Skill 的课程分流意识、阶段顺序、硬软门禁、测试先行和验收证据，但不引用当前项目不存在的文件，不硬编码未经本仓库材料核实的课程事实，也不新增脚本或辅助文档。

**Tech Stack:** Agent Skills Markdown、现有 `.claude/skills/speckit-*`、现有 `.specify` PowerShell 脚本、Maven Wrapper。

**Spec:** `docs/superpowers/specs/2026-10-22-oryxos-lesson-development-skill-design.md`

## Global Constraints

- 只创建 `.SKILLS/oryxos-lesson-development/SKILL.md`；不创建缺失的课件、Harness、reference、脚本或产品文件。
- 只接受第 16–31 节；对应课件不存在或不唯一时停止。
- 当前仓库课件可以是 PDF 或 Markdown；只读取实际存在的格式。
- tasks 生成并完成一致性检查后必须停等用户确认，未经确认不得 implement。
- 六类软门禁、测试硬门禁、反作弊、不自动 commit/push/package.sh 必须保留。
- 使用仓库 Maven Wrapper；不假设未配置的质量插件存在。
- 不自动创建或切换 Git 分支，不覆盖用户未提交修改。

## Review Focus

- 第 21–31 节课件缺失时必须停止，不能从旧 Skill 的硬编码映射推导交付物。
- 已存在 feature 与节号映射不唯一时必须停止，不能错误复用 `.specify/feature.json`。
- `clarify` 中材料无法回答的问题必须交给用户，不能以“合理默认”生成公共契约。
- 课件示例 API 与本地依赖不一致时必须触发软门禁，不能照抄示例。
- 验收失败时必须报告实际失败，不能通过改测试或降低阈值放行。

---

### Task 1: 建立失败基线并创建课程开发 Skill

**Files:**
- Create: `.SKILLS/oryxos-lesson-development/SKILL.md`
- Test: 使用只读 PowerShell 断言检查目标文件及关键纪律。

**Interfaces:**
- Consumes: 当前仓库 `AGENTS.md`、`docs/`、`.claude/skills/speckit-*`、`.specify/` 和附带的现成 Skill 作为参考材料。
- Produces: 可按自然语言触发或显式传入节号的 `oryxos-lesson-development` Skill。

- [x] **Step 1: 运行不存在目标文件的失败基线**

检查 `.SKILLS/oryxos-lesson-development/SKILL.md`，预期因文件不存在而失败；同时记录当前没有课程 Skill 路由、结构化门禁和 tasks 停点。

- [x] **Step 2: 编写最小单文件 Skill**

SKILL.md 必须包含：精确触发描述、输入校验、现有材料发现、备料、现有 Spec-Kit 步骤、tasks 固定停点、实现阶段、六类软门禁、硬门禁、反作弊和节级验收报告。

对于现成 Skill 的逐节课型、章节、模块、测试类或全局不变量，只允许在当节课件与当前技术方案能核实时采用；否则不得写成事实。

- [x] **Step 3: 运行结构断言**

检查名称、描述、16–31、PDF/Markdown、六类软门禁、tasks 停点、`speckit-analyze`、`speckit-implement`、`speckit-converge`、Maven Wrapper、反作弊和禁止外部动作均存在。

### Task 2: 验证 Skill 格式和压力场景

**Files:**
- Verify: `.SKILLS/oryxos-lesson-development/SKILL.md`

**Interfaces:**
- Consumes: Task 1 生成的 Skill。
- Produces: 格式有效且在关键压力场景下给出明确停止行为的最终 Skill。

- [x] **Step 1: 运行官方快速验证器**

执行 `skill-creator/scripts/quick_validate.py`，预期通过 frontmatter、名称和占位符检查。

- [x] **Step 2: 静态验证五个压力场景**

逐项确认 Skill 对以下情况只有一个允许结果：缺少第 21 节课件时停止；tasks 未确认时停止；本地依赖无法核实时停止；测试失败时不改测试放行；用户要求“先实现再补文档”时仍按阶段门禁执行。

- [x] **Step 3: 检查改动范围**

运行 `git diff --check` 和限定路径的 `git status --short`，确认除计划、设计和目标 Skill 外没有新增课程材料、脚本或产品改动。

- [x] **Step 4: 交付说明**

报告 Skill 路径、验证结果、当前可执行课件范围和缺失课件触发的停止行为；明确未 commit、未 push、未运行 `package.sh`。

