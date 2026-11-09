# 会话体验综合修复设计（归档删除 / Markdown 渲染 / Toast 通知 / UI 美化）

- 日期：2026-11-09（修订版，覆盖当日下午全部反馈）
- 分支：`main`（改动范围：`frontend/`，零后端改动）
- 状态：已实现（2026-11-09），全部批次完成
- 决策结论：D1-A、D2-A、D3-A；Markdown 采用方案 A（成熟库）；Toast 采用自定义轻量组件

## 1. 背景与问题定位

用户累计反馈 5 个问题，均已完成根因调研：

### 问题 1：会话删除"失效" + 无确认弹窗（三个叠加根因）

**根因一：删除按钮对所有会话可见，但后端只允许归档一部分。**
后端 `DELETE /api/v1/sessions/{id}`（`SessionWebService.archive`）有两个硬约束：

| 约束 | 违反时返回 | 说明 |
| --- | --- | --- |
| 会话必须是 `web` 渠道 | 409 `SESSION_CHANNEL_NOT_WRITABLE` | CLI / 调度渠道会话不允许从 Web 归档 |
| 会话必须是 `ACTIVE` 状态 | 404 `SESSION_NOT_FOUND` | 已归档会话重复归档返回 404 |

会话列表按 `lastActiveAt` 倒序，**列表第一个会话很可能是 CLI 渠道会话**（演示场景 CLI 高频使用）。点击其删除按钮 → 409 → 仅右侧面板顶部 banner 显示一行小字 → 用户感知为"点了没反应"。

**根因二：归档成功后会话仍留在列表里，且无状态标记。**
`GET /api/v1/sessions` 默认返回全部状态（spec 012 FR-009）。归档成功后 `refreshSessions()` 拉回全量，会话原样显示 → "删了还在"。

**根因三：确认弹窗从未实现**，点按钮直接调接口。

### 问题 2：Agent 回复是纯文本，Markdown 原样输出

`MessageBubble.vue` 用 `{{ message.text }}` 纯文本插值，`# 标题`、`**粗体**`、代码块、表格全部显示为原始符号，可读性差。

### 问题 3：刷新等操作无成功/失败提醒

「刷新列表」按钮点击后无论成功失败都无任何反馈，用户无法确认操作是否生效。产品惯例：成功提示在页面中上方短暂显示。

### 问题 4：会话列表 / Agent 名称字号偏小，收展下拉标太小

侧边栏 Agent 名称与会话条目使用 12px/11px 小字；会话组收展 chevron 是 11px 文字字符 `▸/▾`，无背景块、无过渡，视觉上不像可点击控件。

### 问题 5：会话页面整体美观度不足

user/assistant 气泡区分度弱；会话条目时间戳显示完整日期时间过长；分组信息层级不清晰。

## 2. 目标

1. 删除按钮行为与后端能力一致：能删的显示且点击有效，不能删的不误导。
2. 删除前有确认弹窗；归档结果用户可明确感知（徽标 + 默认隐藏）。
3. assistant 回复按 Markdown 渲染（含代码块、表格），且无 XSS 风险。
4. 刷新等操作有成功/失败 Toast 反馈（页面中上方，自动消失）。
5. 会话区可读性提升：字号放大、chevron 明显、相对时间、气泡层次。

## 3. 非目标与反目标

非目标：

- 物理删除会话数据（后端仅软归档，数据保留可审计）。
- 不修改 spec 012 已冻结契约（FR-006~FR-009、归档渠道约束）。
- 会话管理台（批量操作、恢复归档、归档筛选器 UI）。
- 引入 UI 组件库（项目零依赖组件库现状保持）。

反目标：

- 静默吞掉 409/404 让按钮"看起来生效"。
- 用无实际拦截的假确认糊弄交互。
- Markdown 渲染不经消毒直接 `v-html`（XSS 风险）。
- 为美化而引入图片/图标字体资源（保持零静态资源依赖，chevron 用 CSS 实现）。

## 4. 硬约束（来自既有契约与代码）

| 约束 | 来源 |
| --- | --- |
| 归档接口仅支持 `web` + `ACTIVE` 会话 | `SessionWebService.archive`；spec 012 渠道写权限语义 |
| 会话列表默认含活动与归档会话，必须能发现 | spec 012 FR-009 |
| `SessionSummary` 已含 `status`、`channel` 字段 | 前端 `api/types.ts`，无需后端改动 |
| 项目无 UI 组件库、无既有 modal/toast 样式 | `frontend/` 现状 |
| 用户消息必须保持纯文本（防注入） | 仅 assistant 走 markdown 渲染 |
| CSS 变量：`--ink/--navy/--teal/--paper/--danger` 等 | `main.css` :root，新样式必须沿用 |

## 5. 方案

### 5.1 删除按钮按可归档性区分（D1-A）

`SessionHistory.vue` 按 `session.channel === 'web' && session.status === 'ACTIVE'` 判断：

- 可删除：显示删除按钮 → 点击弹确认框（5.2）。
- 不可删除（CLI / 调度 / 已归档）：不渲染按钮；已归档会话显示灰色「已归档」徽标。
- 请求失败兜底：错误信息仍走 banner + Toast（5.4），会话保留可重试。

### 5.2 确认弹窗（D3-A，自定义 `ConfirmDialog.vue`）

半透明遮罩 + 居中卡片 + 标题/描述 + 取消/确认，点击遮罩取消。确认按钮 danger 红色。归档确认文案含会话渠道与消息数，说明"归档后不再出现在默认列表"。组件通用（props：title/description/confirmLabel/cancelLabel/danger），后续危险操作可复用。

### 5.3 归档后的可见反馈（D2-A）

归档成功、列表刷新后：该会话带「已归档」徽标、弱化样式；侧边栏提供「显示已归档会话」开关，**默认隐藏**（前端过滤，接口仍拉全量，兼容 FR-009 发现能力）；若被归档会话正被打开，对话区重置为欢迎态。状态层 `archiveSessionById` 对 409 `SESSION_CHANNEL_NOT_WRITABLE` 映射明确提示"该渠道会话不支持在控制台删除"。

### 5.4 操作结果 Toast 通知

新建 `ToastStack.vue`：固定于页面中上方（`position: fixed; top: 16px` 居中），成功 teal/绿、失败 danger/红，约 3 秒自动消失，支持手动关闭。状态层加轻量 `toasts` 数组与 `pushToast(kind, message)`；接入点：刷新列表（成功"刷新成功"/失败显示原因）、会话重试加载、归档成功/失败。现有顶部 banner 保留（承载持久性错误详情），Toast 只做瞬时反馈。

### 5.5 Markdown 渲染（方案 A：marked + DOMPurify）

新增依赖 `marked`、`dompurify`（+ `@types/dompurify` 如需）。新建 `utils/markdown.ts`：`marked.parse(text)` → `DOMPurify.sanitize(html)` 输出。`MessageBubble.vue`：assistant 消息 `v-html` 渲染结果；user 消息维持纯文本插值（天然转义）。`main.css` 新增 `.markdown-body` 排版：标题层级、代码块（深色块 + 等宽字体）、行内代码、列表缩进、引用条、表格边框、链接色。体积代价约 +60KB（可接受）。

### 5.6 会话区 UI 美化

- **字号**：Agent 名称（agent-title）12px→14px、agent-name 11px→12px；会话条目正文 12px→13px；组标题 12px→13px。
- **chevron**：`▸/▾` 字符改为 CSS 三角/chevron 图标，16~18px，带 90° 旋转过渡（`transition: transform .2s`）；组头整行加背景色块、内边距与 hover 高亮，展开态 teal 强调。
- **相对时间**：会话条目 `lastActiveAt` 显示"5 分钟前 / 3 小时前 / 2 天前"，超过 7 天显示日期。
- **气泡**：user 气泡 navy 深底白字右对齐、assistant 气泡白底左对齐，圆角与内边距统一；配合 5.5 的 markdown 排版。

### 5.7 撤销归档（恢复会话，2026-11-09 追加实现）

后端新增 `POST /api/v1/sessions/{id}/restore`：
- `SessionEntity.unarchive()`（仅 archived 可恢复，status→active、archivedAt 清空）；
- `SessionManager.restore(sessionId)` 契约（不存在或非归档返回 false）+ `JpaSessionManager.restore` 实现；
- `SessionWebService.restore`：锁与归档对称；非归档状态返回 409 `SESSION_NOT_ARCHIVED`；**不限渠道**（仅元数据操作，不涉及渠道写权限）；
- 成功返回恢复后的 `SessionView`。

前端：`SessionHistory` 已归档会话行显示 ↩️ 恢复按钮（teal hover，无确认框——可逆操作）；`useConsole.restoreSessionById` 成功/失败 Toast + 列表刷新；`errors.ts` 增加 `SESSION_NOT_ARCHIVED` 映射。

## 6. 已确认的决策记录

| # | 决策 | 结论 |
| --- | --- | --- |
| D1 | 非 web 会话能否删 | **A：前端隐藏删除按钮**（零后端改动，遵守契约） |
| D2 | 归档后列表表现 | **A：徽标 + 默认隐藏开关**（兼容 FR-009） |
| D3 | 弹窗形式 | **A：自定义 ConfirmDialog**（观感统一可复用） |
| MD | Markdown 实现方式 | **方案 A：marked + DOMPurify**（用户明确选定） |

## 7. 任务拆分（四个批次按序执行）

**批次一：删除修复**
1. `ConfirmDialog.vue` 组件 + dialog 样式 + 测试（确认/取消/遮罩关闭、emits）。
2. `SessionHistory.vue`：可归档性判断、已归档徽标、显示归档开关、确认弹窗串联；更新组件测试。
3. `useConsole.ts`：409 语义拆分与 Toast 触发；更新状态层测试。

**批次二：Markdown**
4. 安装 `marked` + `dompurify`；新建 `utils/markdown.ts`。
5. `MessageBubble.vue` assistant 渲染 + `main.css` markdown 排版样式。
6. 新增 `messageBubble` 测试：标题/粗体/代码块渲染 + 恶意脚本消毒。

**批次三：Toast**
7. `ToastStack.vue` + 样式 + toast 状态管理；接入刷新/重试/归档入口；测试自动消失与关闭。

**批次四：UI 美化**
8. 字号放大、chevron 放大与过渡、组头按钮化。
9. 相对时间工具 + 会话条目应用。
10. 气泡配色层次优化。

**收尾**
11. 全量 `npm test`（预期仅剩预先失败的 `managementNavigation`）、`npm run typecheck`、`npm run build`（产物输出 `oryxos-web/static/console/`）。
12. 更新 `.workbuddy/memory/2026-11-09.md` 工作日志。

## 8. 验收场景

1. 列表第一个会话为 CLI 渠道：**无删除按钮**，无 409 静默失败。
2. web ACTIVE 会话点删除：弹确认框 → 取消不动 → 确认后会话转「已归档」（默认隐藏，开关可查），对话区重置，Toast 显示归档结果。
3. 断网归档：Toast 显示失败原因，会话保留可重试。
4. 点「刷新列表」：成功 → 中上方绿色 Toast"刷新成功"；失败 → 红色 Toast 显示原因。
5. assistant 回复含 `## 标题`、`**粗体**`、代码块、表格：正确渲染为排版元素；含 `<script>` 的内容被消毒不执行；user 消息纯文本不解析。
6. 会话组收展：chevron 清晰可见带旋转动画；Agent 名称与会话条目字号明显放大；会话时间显示相对时间。
7. 归档会话（开关开启后）显示 ↩️ 恢复按钮：点击后会话回到活动状态、历史消息保留、`archivedAt` 清空；对非归档会话调用恢复返回 409 明确提示。

## 9. 会话名功能（后续追加，已实现）

**需求**：会话默认以第一条提问为名显示在侧边栏列表；可重命名；可按 Agent 定制默认会话名（如 `{date}` 当天日期）。

**决策**：T1-A 后端持久化 title 列；T2 自动名截断 30 字符；T3 模板存前端 localStorage。

**实现**：
- `V9__add_session_title.sql`：`sessions.title VARCHAR(200)` 可空；回填存量会话首条 user 消息（截 30 字符）。
- `SessionSummary` / `SessionSummaryView` / `SessionEntity` 增加 `title`；前端 `SessionSummary` 类型同步。
- `JpaSessionManager.save()`：已有会话名保留；无会话名且存在用户消息时自动取首条（换行折叠、30 字符截断）。`rename()` 持久化重命名（任意渠道、任意状态）。
- `PATCH /api/v1/sessions/{id}` + `RenameSessionRequest`（NotBlank、≤200 字符）→ `SessionRenameView`；`SessionWebService.rename` 复用会话锁。
- 前端：列表显示 title（无 title 回退「渠道 · N 条消息」）+ ✏️ 行内重命名（空名/未变化/Escape 均不提交）+ Toast 反馈。
- 按 Agent 默认名：组头 ⚙️ 打开模板弹窗，存 localStorage（`oryxos.console.titleTemplate.<agent>`），`{date}` 渲染为 `YYYY-MM-DD`；`send()` 新建会话首条消息后自动重命名（失败静默），仅首次生效。

**验收**：
8. 新会话发出第一条消息后，列表条目显示该问题前 30 字符；改名后持久保留。
9. ⚙️ 设置 `{date} 每日简报` 后，该 Agent 新会话自动命名为「2026-11-09 每日简报」。
10. 存量会话（迁移回填）直接显示首问，无需新消息。
