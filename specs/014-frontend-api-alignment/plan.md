# Implementation Plan: 前端 API 文档对齐与补齐

**Branch**: `014-frontend-api-alignment` | **Date**: 2026-11-09 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/014-frontend-api-alignment/spec.md`

## Summary

当前前端已覆盖 `GET /api/v2/schedules`、`POST /api/v2/schedules/{id}/run`、`PUT /api/v2/schedules/{id}` 等定时任务端点，但缺少对以下端点的消费：

- **执行历史查询**：`GET /api/v2/schedules/{id}/executions`（已有类型定义 `ScheduledExecution`，但没有请求函数和页面展示）
- **按 Agent+key 补跑**：`POST /api/v1/agents/{name}/schedules/{key}/run`（无请求函数和入口）
- **通知渠道管理**：5 个端点全部未实现（CRUD + 删除返回 204）
- **Agent 单次调用**：`POST /api/v1/agents/{name}/invoke`（有类型但无请求函数）

本方案在前端新增以上能力，不修改后端代码，不涉及新第三方库。

## Technical Context

**Language/Version**: TypeScript 5.x，Vue 3 Composition API，Vite 构建

**Primary Dependencies**: 现有项目栈 — Vue 3、TypeScript、Vite、原生 `fetch`

**Storage**: N/A（纯前端展示层）

**Testing**: 手动验证 — 通过 Swagger UI 和浏览器控制台

**Target Platform**: 现代浏览器（Chrome/Edge/Firefox），运行在 Spring Boot 静态资源目录

**Project Type**: Web 前端 SPA（嵌入单体 JAR `oryxos-web/src/main/resources/static/`）

**Performance Goals**: 页面加载不影响现有启动性能；每次 API 调用响应超时不超过 30s

**Constraints**:
- 不得修改后端任何代码或配置
- 不得引入新第三方依赖
- 沿用现有的 `http.ts` / `oryxos.ts` / `types.ts` 分层模式
- 复用已有的 `consoleSections.ts` 导航体系和 `ManagementPanel.vue` 容器组件
- 204 空响应的删除场景需要特殊处理（后端不返回 JSON envelope）

**Scale/Scope**: ~400 行新增代码，4 个功能模块

## Constitution Check

| 原则 | 检查项 | 结论 |
|------|--------|------|
| III. 模块边界与依赖倒置 | 新增前端代码不会触及 core/storage 模块 | ✅ 通过 |
| VI. 交付真实性 | 所有新增端点对接均基于 docs/API.md 已有契约 | ✅ 通过 |
| 技术基线 | 使用 Vite + Vue 3 + TypeScript 现有栈 | ✅ 通过 |

**Decision**: 无宪法冲突。204 空响应处理属于前端兼容层问题，不涉及架构变更。

## Project Structure

```text
specs/014-frontend-api-alignment/
├── plan.md              # This file
├── spec.md              # Feature specification
├── research.md          # Phase 0 output (no unknowns)
├── data-model.md        # Phase 1 output (types additions)
├── contracts/           # Phase 1 output (endpoint mappings)
│   └── api-endpoints.mdx
└── quickstart.md        # Phase 1 output (validation guide)
```

**Source Code Changes**:

```text
frontend/
├── src/
│   ├── api/
│   │   ├── types.ts            # Add NotifyChannel, ExecutionHistoryEntry
│   │   └── oryxos.ts           # Add executeHistory, agentRunSchedule, notifyChannels CRUD, agentInvoke
│   ├── components/
│   │   ├── ScheduleManagement.vue    # Add execution history panel
│   │   ├── NotifyChannelManagement.vue # NEW: channel CRUD page
│   │   ├── AgentInvokePage.vue       # NEW: single-agent call page
│   │   └── consoleSections.ts        # Add notifyChannels & agentInvoke entries
│   └── utils/
│       └── errors.ts             # Map HTTP 204 to success for delete
```

## Complexity Tracking

Not applicable — no constitutional violations.
