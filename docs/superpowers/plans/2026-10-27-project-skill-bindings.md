# 项目内部 Skill 引用 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [X]`) syntax for tracking.

**Goal:** 用普通 UTF-8 `.link` 项目引用文件替换 Windows/Linux 原生软连接绑定，完成普通权限下的第 17 节验收。

**Architecture:** ContextLoader 统一解析 Agent skills/ 下的引用文件；公共 Skill 不复制，元数据仍按需热读。文件工具未来复用绑定解析结果，不将公共根视为全体 Skill 可见的许可。

**Tech Stack:** JDK21、现有 Spring Boot/Maven/JUnit/SnakeYAML，不新增第三方依赖。

**Spec:** `docs/superpowers/specs/2026-10-27-project-skill-bindings-design.md`，用户已确认。

## Global Constraints

- 不使用管理员权限，不修改 Windows 设置，不创建系统级绑定，不自动迁移或删除业务资源。
- `.link` 文件仅含一条相对于其父 skills/ 目录的路径；目标必须位于当前工作区公共 skills 根内，必须是目录且有合法 SKILL.md。
- 拒绝绝对路径、盘符/UNC 路径、断引用、未知格式、复制目录、引用文件自身为原生链接及真实目标逃逸。
- 唯一绑定源仍为 Agent skills/ 目录；不新增 frontmatter skills:、数据库表或产品配置键。
- 保留元数据限定、热读和边界断言；原生文件系统攻击场景保留独立可选集成测试，默认套件通过真实引用文件验证新契约。
- 保留用户修改，不提交、不切换分支。

## Review Focus

- 在 Linux 上读入 Windows 盘符/UNC/反斜线路径时，也必须拒绝外部目标。
- 工程目录移动后，引用仍有效，Prompt 路径必须为可读取的公共实体真实路径。
- 超大、多行、空、目录或非 .link 文件不得被静默接受。
- 公共根、目标目录、SKILL.md 或绑定目录经现存操作系统链接逃逸时仍拒绝。
- 旧原生链接只能给迁移提示，不能隐式双轨绑定或修改用户文件。

## Task 1: 契约与真实文件 RED

**Files:** `oryxos-core/src/test/java/io/oryxos/core/context/ContextLoaderTest.java`、`ContextLoaderNativeLinksIT.java`；现有第17节spec/plan/contracts/tasks及项目规范。

**Interfaces:** 保持 ContextLoader(Path).load(Profile): String；新增绑定语义仅由已批准设计定义。

- [X] 写入 .link 元数据/重新绑定/删除/移动/不预载正文/无效路径/元数据错误测试，全部使用真实普通文件。
- [X] 将确需原生链接的攻击测试保留为 opt-in `ContextLoaderNativeLinksIT`，测试开关 `oryxos.tests.native-links=true` 仅影响附加测试，不属于产品配置。
- [X] 运行 ContextLoaderTest，确认合法引用因旧实现只认软连接而失败。

## Task 2: 解析与 GREEN

**Files:** `oryxos-core/src/main/java/io/oryxos/core/context/ContextLoader.java`。

**Interfaces:** 相对路径基于引用文件父目录；Prompt 输出真实公共 SKILL.md 路径，不输出不存在的目录别名。

- [X] 限制引用文件为小于等于4096字节的普通 .link 文件，仅一条非空路径，允许UTF-8 BOM及末尾换行。
- [X] 统一处理斜线，先拒绝绝对/盘符/UNC，再做公共根词法边界及真实路径边界检查。
- [X] 保留每次元数据读取及字段安全解析；仅替换绑定定位逻辑。
- [X] 运行完整 core 测试，检查没有依赖管理员权限的默认用例，没有默认必测场景被跳过。

## Task 3: 规范、完整验收与收敛

**Files:** `AGENTS.md`、`.specify/memory/constitution.md`、`docs/{TechnicalSolution,DemandAnalysis,oryxos}.md`、`specs/002-react-loop-cli/`。

- [X] 同步唯一绑定源与读取路径，记录用户授权的宪章不兼容契约修订及版本，保留历史测试失败证据。
- [X] 普通权限执行 Wrapper test、clean verify；不改质量阈值或排除项，不使用真实密钥。
- [X] 独立只读审查路径边界及规范一致性，重要问题补红绿测试修复。
- [X] 更新第17节旧任务和Convergence任务的完成证据，运行收敛检查及git diff --check；交付实际结果。

## 执行记录

- 用户已明确确认设计并要求直接实施；本计划沿用现有工作区，任务在当前会话连续执行，不再重复请求设计批准。

- Task1 RED：旧加载器在4个合法普通引用场景失败；Task2 GREEN：core51项全部通过（无默认跳过），日志 project-links-red.log / project-links-core-green.log。
- 独立只读审查未发现Critical/Important或明确路径绕过；当前Demo示例仍用旧Agent目录别名，已按本次文档同步范围改为公共SKILL.md真实路径，避免不可读取示例。原生攻击测试未动态执行，保持附加opt-in说明。

- Task3完整验收通过：test87项通过，clean verify汇总91项、89通过/2可选验证未执行、0失败/错误；全部15项目SUCCESS。普通权限执行，未修改系统、依赖或质量阈值。
