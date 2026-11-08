# OryxOS 控制台首版实施计划

> **For agentic workers:** Use superpowers:executing-plans for inline execution. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为现有工作台交付七个导航和可独立扩展的查询视图。

**Architecture:** Vue 工作台维护导航状态，现有会话逻辑保持挂载。领域查询复用统一请求客户端。Boot 将有效沙箱规则映射为只读契约，由 Web 模块输出。

**Tech Stack:** Vue 3、TypeScript、Vitest、Spring MVC、Maven。

**Spec:** `docs/superpowers/specs/2026-11-06-console-management-design.md`

## 全局约束

沿用统一 `ApiResponse`。现有 009 和 010 契约保持兼容。工作区现有未提交改动保留。

## 审查重点

- 切换菜单和从 Agent 页面进入会话时保留草稿及会话代次。
- 快速切换 Agent 后，慢请求不得覆盖当前筛选结果。
- 空数据、请求失败和重试状态可区分。
- Sandbox 摘要来自验证和规范化后的规则，仅返回允许字段。
- Provider 已配置与服务健康在 UI 上独立表达。

### T001：Spec-Kit 规格与导航

- [ ] 在 `specs/011-console-management/` 建立规格、实施计划引用、任务清单和 API 契约，依赖 009 与 010。
- [ ] 新建 `frontend/tests/managementNavigation.test.ts`，断言默认会话、七个入口、切换后输入草稿保留。先运行该测试，确认缺少导航导致失败。
- [ ] 在 `frontend/src/App.vue` 接入导航。新增 `frontend/src/components/ConsoleNavigation.vue` 及稳定的模块键 `agents | providers | tools | sandbox | memory | runtime | sessions`。会话区用显示状态控制，继续由原有状态实例驱动。
- [ ] 重跑导航测试。

### T002：有效 Sandbox 摘要

- [ ] 在 Web 模块添加 MockMvc 测试，断言 `GET /api/v1/sandbox/allowlist` 返回统一信封，`data` 精确包含 `httpDomains`、`fileRootCount`、`commandAliases`。先确认缺少该路由时失败。
- [ ] 在 Core 新增只读 `SandboxAllowlistReader` 及不可变摘要。由 `WhitelistSandbox` 基于已规范化的实际规则生成排序后的摘要，Boot 装配只读端口，Web 控制器只依赖该端口。
- [ ] 验证空规则、排序、文件根数量和字段白名单。运行相关 Maven 测试。

### T003：领域查询视图

- [ ] 在 `frontend/tests/managementViews.test.ts` 先覆盖 Agent 跳转、Provider 名称、Tool 筛选、记忆分区、Sandbox 字段和运行状态。增加错误后重试与旧请求晚返回场景。
- [ ] 在 `frontend/src/api/oryxos.ts` 添加 `fetchTools(agentName?: string)`、`fetchMemory(agentName: string)`、`fetchSandboxAllowlist()`，响应类型放入 `frontend/src/api/types.ts`。现有 Profile、Health、Info 客户端直接复用。
- [ ] 在 `frontend/src/components/` 按领域新增视图，Agent 和 Provider 各自保留独立组件。Agent 视图发出带名称的会话跳转事件。查询视图区分加载、空、失败，切换筛选后丢弃旧响应。
- [ ] 重跑前端管理视图与会话回归测试。

### T004：整体验证

- [ ] 运行 `npm.cmd --prefix frontend test` 和 `npm.cmd --prefix frontend run build`，确认全部通过。
- [ ] 运行 `./mvnw.cmd test` 及 `git diff --check`。
- [ ] 启动本地控制台，逐项检查七个入口、Agent 跳转、会话状态保留和空态。将实际结果写入规格验证记录并更新任务状态。
