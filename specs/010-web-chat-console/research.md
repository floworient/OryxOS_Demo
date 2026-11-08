# Research: 第一版 Web 对话工作台

**Date**: 2026-11-06 | **Plan**: [plan.md](plan.md)

## 1. 前端框架与构建工具

- **Decision**: Vue 3.5.45 + TypeScript + Vite 8.3.6，使用 `@vitejs/plugin-vue` 6.0.10。
- **Rationale**: `docs/TechnicalSolution.md` 与 `website/` 已明确设想 Vue 3 管理台；Vite 与仓库既有文档站同为 Vite 生态，团队无需引入第二套构建心智。Node 24.14.0 满足 Vite 8 的 `^20.19.0 || >=22.12.0` 要求。
- **Alternatives considered**: React（团队与文档无基础，增加迁移成本）；Svelte（生态与示例不足）；Nuxt（本版无 SSR/路由需求，过重）。

## 2. 依赖版本与兼容性

- **Decision**: 运行时依赖仅 `vue@3.5.45`；开发依赖 `vite@8.3.6`、`@vitejs/plugin-vue@6.0.10`、`typescript@5.9.3`、`vue-tsc@3.3.14`、`vitest@5.1.0`、`@vue/test-utils@2.5.1`、`jsdom@29.1.1`、`@types/node@24.x`。
- **Rationale**: 已通过 `npm view` 核对 peerDependencies；`@vitejs/plugin-vue` 6 支持 Vite 5–8；Vitest 5 支持 Vite 6–8；jsdom 29 的 engines 覆盖 Node 24.14.0，而 jsdom 31 要求 Node ≥24.15.0，当前环境不满足。TypeScript 停留在 5.9.3，避免 `vue-tsc` 对 TypeScript 7 的兼容风险。
- **Alternatives considered**: 最新 TypeScript 7（`vue-tsc` peer 虽标注 ≥5，但缺少验证，不作为首版基线）；jsdom 31（Node 版本不满足）；happy-dom（可用，但 jsdom 行为更接近浏览器）。

## 3. UI 呈现方式

- **Decision**: 手写 CSS 与少量语义化组件，不引入 UI 组件库、路由或状态管理库。
- **Rationale**: 本版只有一屏两栏对话界面，状态集中在单个 `useConsole` composable；引入 Element Plus/Pinia/Vue Router 会增加体积、视觉风格约束和与现有落地页的割裂。复用 `oryxos-web` 静态落地页的深色/中性配色变量。
- **Alternatives considered**: Element Plus（表单/弹窗能力本版用不到）；Naive UI（同上）；Pinia（状态量小，组合式函数足够）；Vue Router（无页面跳转）。

## 4. HTTP 客户端

- **Decision**: 浏览器原生 `fetch`，在 `src/api/http.ts` 中统一处理超时以外的网络错误、非 JSON 响应与 `ApiResponse` 信封。
- **Rationale**: 只有 6 个端点，无需 axios 的拦截器/取消生态；统一封装可以固定错误归一化行为，便于测试。
- **Alternatives considered**: axios（额外依赖，收益不足）；`ky`（同上）。

## 5. 会话标识与刷新恢复

- **Decision**: 浏览器 `localStorage` 保存两类键：`oryxos.console.userId`（首次生成随机匿名标识）与 `oryxos.console.session.<agentName>`（当前会话 ID）。加载或切换 Agent 时按会话 ID 拉取历史；会话不存在或已归档时清除该键并提示。
- **Rationale**: 后端没有会话列表接口，本地标识是唯一可行的刷新恢复方式；按 Agent 区分避免串会话；随机 userId 避免同一内网中多个浏览器共享活动会话。
- **Alternatives considered**: 固定 userId（多标签页/多浏览器会互相复用会话）；把完整历史存本地（与运行时真值脱节，违反「不做假数据」）；新增后端会话列表接口（本版明确不改契约）。

## 6. 「新对话」的语义修正

- **Decision**: 「新对话」在存在活动会话时先调用 `DELETE /sessions/{id}` 归档，再清除本地会话标识与视图；不存在会话时只清空视图。
- **Rationale**: 后端 `POST /sessions` 对相同 `(channel,userId,profileName)` 返回已有活动会话（200），仅清除 localStorage 会导致下一条消息复用旧会话。归档不物理删除历史，符合 spec 的「旧历史保留」要求。
- **Alternatives considered**: 只清本地标识（实际复用旧会话，语义错误）；为每次新对话生成新 userId（污染身份并绕过原有会话模型）；修改后端 get-or-create 语义（违反 009 冻结契约）。

## 7. 工具调用展示

- **Decision**: 发送成功后统一 `GET /sessions/{id}` 重建消息列表，将 `assistant` 消息上的 `toolCalls[].name` 渲染为工具 chips；不展示 `tool` 角色消息、参数或结果。
- **Rationale**: `POST /messages` 只返回 `{sessionId, reply}`，不包含工具信息；`SessionView` 已提供安全投影且与后端真值一致。
- **Alternatives considered**: 解析回复文本猜测工具（不可靠）；前端自行记录发送前的工具（无法获得真实执行结果）；新增工具详情接口（本版不改契约）。

## 8. 错误映射与等待态

- **Decision**: 已知业务码 `VALIDATION_ERROR`、`AGENT_NOT_FOUND`、`SESSION_NOT_FOUND`、`SESSION_BUSY`、`SESSION_CHANNEL_NOT_WRITABLE`、`PROVIDER_UNAVAILABLE`、`AGENT_TIMEOUT`、`STORAGE_ERROR`、`INTERNAL_ERROR` 映射为固定中文提示；未知码与网络失败使用通用提示。等待中每秒更新已等待秒数，55 秒起提示即将超时，等待期间禁用发送。
- **Rationale**: 错误提示是演示可诊断性的关键；固定文案避免回显底层异常。禁用发送同时规避 `SESSION_BUSY` 与重复消息。
- **Alternatives considered**: 直接展示后端 `message`（可能包含底层信息且不稳定）；只弹出通用 toast（无法区分 Provider 不可用与会话忙碌）。

## 9. 开发与生产接入

- **Decision**: 开发时 Vite dev server 将 `/api` 代理到 `http://localhost:8080`（可用 `VITE_API_PROXY_TARGET` 覆盖）；请求路径固定为绝对 `/api/v1/...`。生产构建 `base=/console/`，`outDir=../oryxos-web/src/main/resources/static/console` 并加入 `.gitignore`。
- **Rationale**: 同源代理避免依赖后端开放 CORS；绝对路径同时适配开发代理与生产 `/console/` 子路径。构建产物交由既有 Boot 静态资源提供，不需要额外进程。
- **Alternatives considered**: 前端独立部署 + CORS（演示多一个进程与安全暴露面）；Maven `frontend-maven-plugin`（把 Node 构建绑定进 Maven，超出本版范围）；`base=/` 覆盖根路径（会与现有落地页冲突）。

## 10. 测试分层

- **Decision**: Vitest + Vue Test Utils + jsdom 覆盖 `http` 信封/网络错误、错误码映射、本地存储、`useConsole` 的创建/复用/新对话/归档/刷新恢复状态机；真实浏览器与真实模型通过 quickstart 手工验证。
- **Rationale**: 设计文档只要求覆盖错误码映射与会话状态机，不追求快照；Playwright 需下载浏览器且本版无 CI，收益不足。
- **Alternatives considered**: Playwright/Cypress（成本高，演示项目无 CI）；仅手工测试（状态机回归风险高）。

## 11. 演示工作区

- **Decision**: 在 `frontend/demo/` 提交 `daily-weather` 的 `AGENT.md` 与 `application.yaml.example`，由 quickstart 指导复制到被 `.gitignore` 忽略的 `.oryxos/`；真实 API Key 只从环境变量或本地 `config/application.yml` 读取。
- **Rationale**: 演示资产可复制、可审查，同时不把运行时私有数据或密钥提交进仓库；与现有 `bin/start.sh` 的配置加载方式一致。
- **Alternatives considered**: 直接提交 `.oryxos/`（会带入数据库与密钥风险）；不提供演示资产（复现成本高）。
