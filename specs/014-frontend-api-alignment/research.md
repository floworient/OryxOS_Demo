# Research: 前端 API 对齐技术方案

## 决策记录

### 决策 1: 采用 `deleteJson` 包装 DELETE + 特殊处理 204

**方案**: 现有 `http.ts` 的 `request<T>()` 统一包裹在 JSON envelope 中，而 `DELETE /api/v1/notify-channels/{name}` 返回 204 空响应，不走 JSON envelope。

**选择**: 为通知渠道删除创建专用函数 `deleteNotifyChannel(name): Promise<void>`，内部用原生 `fetch()` 发送请求，检查 `response.ok` 即视为成功，不尝试解析 body。

**理由**: 
- 避免修改通用 `request<T>()` 增加复杂性
- 204 空响应是 HTTP 标准用法，单独处理符合职责分离原则
- `http.ts` 已有 `deleteJson`，但不做空体处理

**备选**: 修改 `request<T>()` 支持自动识别 204 — 会增加通用层变更，风险高于局部处理。

### 决策 2: Agent Invoke 返回结构确认

**发现**: `docs/API.md` 描述 `POST /api/v1/agents/{name}/invoke` 返回 `{ reply }`，不含 `sessionId`。现有类型 `MessageReplyView` 包含 `sessionId`，与该接口返回值不一致。

**选择**: 新增独立类型 `AgentSingleInvokeResponse = { reply: string }`，不与 `MessageReplyView` 混用。

**待确认**: 实际请求体格式 — 是否传 `{ message: string }` 或其他字段名。

### 决策 3: 执行历史面板交互模式

**发现**: `ScheduleManagement.vue` 当前每行只有"立即执行"和"启用/停用"按钮，无展开操作。

**选择**: 在操作列添加"📋 历史"按钮，点击后通过 inline expand/collapse 显示该任务的所有执行记录，复用已有的 `pending` / `errors` reactive Map。

**理由**:
- 保持单页面，不需要跳转
- 与当前表格布局一致
- 执行历史数据量通常不大（几百条以内）

### 决策 4: Agent+Key 补跑下拉选择

**发现**: `ScheduleManagement.vue` 已有 `profiles` 来源（从父组件 `ManagementPanel.vue` 传入）。

**选择**: 在表格上方添加一行下拉选择器，先选 Agent → 再选其下的 schedule key → 触发补跑。复用现有 profiles 列表数据源。

**理由**:
- profiles 已通过 props 传入，无额外 API 调用
- 比复制 scheduleId 更友好
- 可与现有运行状态按钮并存
