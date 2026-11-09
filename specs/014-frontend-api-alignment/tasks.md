---

description: "Task list for frontend API alignment feature"
---

# Tasks: 前端 API 文档对齐与补齐

**Input**: specs/014-frontend-api-alignment/spec.md, plan.md, research.md, data-model.md
**Branch**: `feat/frontend-speckit-tasks` (从 main 创建)

## Phase 1: Setup & Foundation (Existing — no changes needed)

- Codebase already uses Vue 3 + TypeScript + Vite ✅
- `http.ts`, `oryxos.ts`, `types.ts` layering exists ✅
- `consoleSections.ts`, `ManagementPanel.vue` routing exist ✅

**Checkpoint**: Foundation ready — can begin US work immediately.

---

## Phase 2: User Story 1 - 定时任务执行历史 (P1) 🎯 MVP

**Goal**: 在控制台"定时任务"页面中，每行提供展开/收起操作以查看该任务的执行历史列表（对应 `GET /api/v2/schedules/{scheduleId}/executions`）。

**Independent Test**: 启动服务后进入定时任务页，点击某任务的"📋 历史"按钮，能看到按时间倒序执行的记录列表。

- [x] T001 [P] [US1] Add `fetchExecutionHistory` function in `frontend/src/api/oryxos.ts` calling `GET /api/v2/schedules/{scheduleId}/executions`
- [x] T002 [US1] Modify `ScheduleManagement.vue` to add expand/collapse button per row and inline execution history table showing `startedAt`, `success`, `errorMessage`, `durationMs`
- [x] T003 [US1] Handle empty history case with "暂无执行历史" display in `ScheduleManagement.vue`

**Checkpoint**: US1 complete — users can view execution history inline.

---

## Phase 3: User Story 2 - 通知渠道管理 (P2)

**Goal**: 新增"通知渠道"控制台页面，支持对 `/api/v1/notify-channels` 进行查看、新增、编辑和删除操作。

**Independent Test**: 进入通知渠道页 → 看到列表 → 新建一个渠道 → 编辑描述 → 删除确认 → 列表刷新。

- [x] T004 [P] [US2] Add `NotifyChannelSummary` / `NotifyChannelCreateInput` / `NotifyChannelUpdateInput` types in `frontend/src/api/types.ts`
- [x] T005 [US2] Implement notify-channel-specific delete wrapper in `frontend/src/api/http.ts`: `deleteNoContent(path): Promise<void>` that treats HTTP 204 as success without JSON parsing
- [x] T006 [P] [US2] Implement CRUD functions in `frontend/src/api/oryxos.ts`: `fetchNotifyChannels()`, `createNotifyChannel(input)`, `updateNotifyChannel(name, input)`, `deleteNotifyChannel(name)`
- [x] T007 [US2] Create `frontend/src/components/NotifyChannelManagement.vue` with table view + create/edit form modal
- [x] T008 [US2] Display channel name, type, redacted URL, description; handle deletion of 204 responses gracefully in component

**Checkpoint**: US2 complete — full CRUD for notify channels.

---

## Phase 4: User Story 3 - Agent 单次调用 (P3)

**Goal**: 新增"Agent 调用"控制台页面，允许用户选择 Agent 并发送消息获取单次回复（对应 `POST /api/v1/agents/{name}/invoke`）。

**Independent Test**: 进入 Agent 调用页 → 选 Agent → 输入文本 → 提交 → 展示 `{ reply }`。

- [x] T009 [P] [US3] Add `AgentSingleInvokeResponse` type `{ reply: string }` in `frontend/src/api/types.ts`
- [x] T010 [US3] Add `invokeAgent(agentName: string, message: string)` function in `frontend/src/api/oryxos.ts` calling `POST /api/v1/agents/{name}/invoke`
- [x] T011 [US3] Create `frontend/src/components/AgentInvokePage.vue` with agent selector dropdown + text input + reply display area

**Checkpoint**: US3 complete — single-shot agent invoke.

---

## Phase 5: User Story 4 - 按 Agent+Key 补跑 (P2)

**Goal**: 在定时任务页面添加通过下拉框选择 Agent 和任务键来触发补跑的入口。

**Independent Test**: 在定时任务页 → 选 Agent → 选 Schedule Key → 点击补跑 → 显示结果。

- [x] T012 [P] [US4] Add `agentRunSchedule` function in `frontend/src/api/oryxos.ts` calling `POST /api/v1/agents/{name}/schedules/{key}/run`
- [x] T013 [US4] Add Agent dropdown + Schedule key dropdown at top of `ScheduleManagement.vue` table, trigger `agentRunSchedule` on submit
- [x] T014 [US4] Show run result feedback in same row or toast-style inline message

**Checkpoint**: US4 complete — alternate run entry point.

---

## Phase 6: Polish & Cross-Cutting (Navigation + Compilation)

- [x] T015 [P] Add "通知渠道" and "Agent 调用" entries to `frontend/src/components/consoleSections.ts`
- [x] T016 [P] Wire new components into `frontend/src/components/ManagementPanel.vue` conditional rendering
- [x] T017 Verify TypeScript compilation passes: `cd frontend && npx tsc --noEmit`
- [x] T018 Verify build passes: `cd frontend && npm run build`

**Checkpoint**: Feature complete — compiles, builds, and navigates correctly.

---

## Dependencies & Execution Order

```
Phase 2 (US1) ─┐
Phase 3 (US2) ─┼── Can start after Phase 1 (foundation exists)
Phase 4 (US3) ─┘
Phase 5 (US4) ─┘
Phase 6 (Polish) ← Depends on all above completion
```

### Parallel Opportunities

- T001 (API types) and T004/T009 (new types) can proceed in parallel across US branches
- T005 (delete helper) is foundational for US2 but independent of US1/US3/US4
- US1 and US3 touch completely different pages — can implement in parallel
- T015 and T016 (navigation wiring) can be done together in final phase

### Implementation Strategy

MVP first: US1 (execution history) → validate → add remaining stories.

---

## Completion Summary

All 18 tasks completed. Branch: `feat/frontend-speckit-tasks` from `main`.
Build verified: ✓ TypeScript pass, ✓ Vite build pass.
