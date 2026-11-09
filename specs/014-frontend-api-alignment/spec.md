# Feature Specification: 前端 API 文档对齐与补齐

**Feature Directory**: `014-frontend-api-alignment`

**Created**: 2026-11-09

**Status**: Draft

**Input**: 根据 docs/API.md 更新前端现有页面，覆盖 API 中已有但未在前端暴露的端点。先不改后端。

## User Scenarios & Testing

### User Story 1 - 查看定时任务执行历史 (Priority: P1)

运维人员在控制台"定时任务"页面点击查看某任务的执行历史，了解每次触发的结果、关联会话和耗时。

**Why this priority**: 当前只能补跑和启停，无法回溯执行记录，是任务管理的最小可用闭环中缺失的一环。

**Independent Test**: 启动一个有执行历史的 Agent，进入定时任务列表，点击后能看到该任务的所有执行记录。

**Acceptance Scenarios**:

1. **Given** 某个定时任务至少有一条执行记录，**When** 用户在任务行展开执行历史面板，**Then** 列表按时间倒序展示每次执行的开始时间、是否成功、错误信息（如有）和耗时。
2. **Given** 任务没有执行记录，**When** 用户展开执行历史，**Then** 显示"暂无执行历史"。
3. **Given** 已退役的任务仍有历史记录，**When** 用户查询，**Then** 依然能返回执行历史。

### User Story 2 - 按 Agent + 任务键补跑 (Priority: P2)

运维人员希望通过选择 Agent 和任务键来手动触发补跑，而不是从列表复制 scheduleId。

**Why this priority**: 提供更便捷的补跑入口，尤其适用于任务较多时定位特定任务的场景。

**Independent Test**: 在定时任务页面通过下拉框选择 Agent 和任务，点击补跑按钮，验证触发了一次新的执行。

**Acceptance Scenarios**:

1. **Given** 已加载 Agent 列表和任务，**When** 用户从下拉框中选择 Agent 和对应的任务键，**Then** 系统调用按 Agent + 任务键的补跑接口并返回执行结果。

### User Story 3 - 通知渠道管理 (Priority: P2)

管理员需要在控制台查看、新增、编辑和删除通知渠道。

**Why this priority**: 通知渠道是系统基础设施的一部分，缺少管理界面使得集成方无法配置消息推送目标。

**Independent Test**: 启动服务后进入通知渠道页，新建一个渠道，编辑描述，然后删除确认。

**Acceptance Scenarios**:

1. **Given** 系统中已有一些通知渠道，**When** 用户打开通知渠道页，**Then** 列表展示所有渠道的名称、类型、描述和脱敏后的 URL。
2. **Given** 用户填写了渠道名称、类型和必要配置，**When** 用户提交创建表单，**Then** 后端返回创建成功的渠道且前端刷新列表。
3. **Given** 用户修改了某个渠道的描述或 URL，**When** 用户保存修改，**Then** 后端返回更新后的渠道数据。
4. **Given** 用户确认删除一个渠道，**When** 触发删除请求（期望 204），**Then** 列表移除该渠道并刷新。

### User Story 4 - Agent 单次调用 (Priority: P3)

用户可以在控制台中直接向指定 Agent 发起一次性调用获取回复，而不需要创建会话。

**Why this priority**: 提供快速测试 Agent 能力的入口，无需进入完整会话流程。

**Independent Test**: 选择一个 Agent，输入消息，查看返回的回复。

**Acceptance Scenarios**:

1. **Given** 已加载 Agent 列表，**When** 用户选择 Agent 并发送消息，**Then** 后端返回 `{ reply }` 并在界面上展示。

---

## Requirements

### Functional Requirements

- **FR-001**: 前端定时任务页面 MUST 在每个任务行提供展开/收起操作以查看该任务的执行历史列表。
- **FR-002**: 执行历史列表 MUST 显示 `startedAt`、`success`、`errorMessage`（如有）、`durationMs` 字段。
- **FR-003**: 前端定时任务页面 MUST 支持通过选择 Agent + 任务键的方式触发补跑（对应 `POST /api/v1/agents/{name}/schedules/{key}/run`）。
- **FR-004**: 前端 MUST 新增"通知渠道"控制台页面，支持对 `/api/v1/notify-channels` 进行查看、新增、编辑和删除操作。
- **FR-005**: 通知渠道列表 MUST 显示渠道名称、类型、URL（脱敏）和描述。敏感配置项必须在接口响应层面脱敏后再展示。
- **FR-006**: 通知渠道的新建/编辑表单 MUST 包含 `name`、`type`、`url`、`description` 等可编辑字段。
- **FR-007**: 删除通知渠道时前端必须接收 204 空响应的成功状态，并自动刷新列表。
- **FR-008**: 前端 MUST 新增"Agent 调用"控制台页面或通过现有页面入口，支持直接调用 `POST /api/v1/agents/{name}/invoke`。
- **FR-009**: Agent 调用页面 MUST 允许用户输入文本消息并展示 Agent 返回的单次回复。
- **FR-010**: 前端导航菜单 MUST 包含新增的"通知渠道"和"Agent 调用"两个入口。

### Key Entities

- **NotifyChannel**: 通知渠道实体，属性含 `name`、`type`、`url`、`description`、`config`（脱敏）、`createdAt`、`updatedAt`。
- **ScheduledExecution**: 定时任务执行记录，属性含 `scheduleId`、`sessionId`、`startedAt`、`success`、`errorMessage`、`durationMs`。

---

## Success Criteria

### Measurable Outcomes

- **SC-001**: 用户在控制台能够一次点击完成查看所有端点覆盖情况，未覆盖端点数降至 0。
- **SC-002**: 通知渠道管理页面的 CRUD 操作成功率 ≥ 95%（排除网络故障）。
- **SC-003**: 新增端点对接后，前端的 TypeScript 类型编译无报错。
- **SC-004**: 用户可在 3 分钟内完成从进入控制台到查看通知渠道列表的全部操作。

---

## Assumptions

- 后端 API 文档（docs/API.md）中的端点是最新有效版本，前端只做消费方改动，不调整后端契约。
- 通知渠道管理的 CRD 接口已在后端实现，本次只补齐前端交互。
- Agent 单次调用（invoke）只需要传递用户输入的文本消息，不需要维护上下文历史。
- 敏感配置（如 token、密码）在后端 `config` 字段中已做脱敏处理，前端直接使用即可。
- 不使用任何新第三方库，沿用现有的 Vue 3 + TypeScript + Vite 技术栈。
