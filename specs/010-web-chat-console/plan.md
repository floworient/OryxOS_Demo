# Implementation Plan: 第一版 Web 对话工作台

**Branch**: `main`（Spec-Kit feature 标识 `010-web-chat-console`） | **Date**: 2026-11-06 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification 与设计文档 `docs/superpowers/specs/2026-11-06-oryxos-web-chat-frontend-design.md`。

## Summary

新增独立前端工程 `frontend/`，使用 Vue 3 + TypeScript + Vite 交付一屏式对话工作台：左栏展示运行时真实 Agent，右侧完成会话创建、消息发送、历史恢复与工具调用展示。前端只消费 009 已冻结的 `/api/v1` 接口，不修改后端契约；会话标识按 Agent 保存在浏览器本地，刷新后可恢复。通过开发代理访问后端，生产构建产物输出到 `oryxos-web` 静态资源目录的 `console/` 子路径。

关键设计修正：后端 `POST /sessions` 对同一 `(channel,userId,profileName)` 会复用活动会话，因此「新对话」必须先归档当前活动会话再清空本地标识，否则会拿到同一个会话而不是新会话。

## Technical Context

**Language/Version**: TypeScript 5.9.3；Node 24.14.0（本机），npm 11.9.0

**Primary Dependencies**: Vue 3.5.45、Vite 8.3.6、`@vitejs/plugin-vue` 6.0.10；不引入 UI 组件库、路由或 Pinia；HTTP 使用浏览器原生 `fetch`

**Storage**: 浏览器 `localStorage` 保存匿名用户标识与按 Agent 区分的会话标识；不新增后端表，不改动 SQLite

**Testing**: Vitest 5.1.0、`@vue/test-utils` 2.5.1、jsdom 29.1.1；单元/组件测试用受控 `fetch` 桩；真实端到端验证按 quickstart 手工执行

**Target Platform**: 现代桌面 Chromium/Edge；开发于 Windows，生产由 Spring Boot 在 `/console/` 提供静态资源

**Project Type**: 独立前端 Web 应用 + 既有 Spring MVC 后端

**Performance Goals**: 本地首屏在 2 秒内完成 Agent/状态加载；消息请求受后端 60 秒上限约束；等待态每秒更新

**Constraints**: 不新增或修改 `/api/v1` 契约；无认证、无流式、无会话列表；不保存真实密钥；构建产物不入库；同一活动会话被复用时必须通过归档实现「新对话」

**Scale/Scope**: 单用户演示；工作区 1–5 个 Agent；单会话最多展示后端返回的最近 100 条消息

## Constitution Check

| 原则 | 设计前检查 | Phase 1 后检查 |
| --- | --- | --- |
| I 单一运行时内核 | PASS：前端只调用 `/api/v1`，消息经后端 AgentService，不复制 ReAct | PASS：契约层固定只消费公开 HTTP 接口，前端不含任何 Agent 编排逻辑 |
| II 工具调用统一受控 | PASS：只读取会话历史中的工具名称，不直接执行 Tool | PASS：工具 chips 来源于 `MessageView.toolCalls`，无前端 Tool 调用通道 |
| III 模块边界与依赖倒置 | PASS：`frontend/` 是独立前端工程，不新增 Maven 模块，不改核心边界 | PASS：构建产物仅作为静态资源，不引入 Java 依赖或反向依赖 |
| IV 安全边界真实生效 | PASS：不保存密钥；开发代理避免开放 CORS；错误提示不展示底层详情 | PASS：敏感字段负断言、`VITE_` 变量白名单与无密钥扫描纳入验证 |
| V 持久化与配置显式契约 | PASS：不新增数据库结构；会话沿用后端 Session，本地只保存标识 | PASS：本地存储键与恢复/失效规则写入 data-model 与测试 |
| VI 交付真实性与可验证性 | PASS：禁止假数据；无密钥/无网络场景标记未验证 | PASS：quickstart 覆盖失败与空工作区场景，验证记录区分真实调用与桩测试 |

无宪章例外。需要显式保留的限制：无认证、无流式、单实例演示、浏览器本地存储可能在隐私模式下不可用、真实模型调用依赖外部 Provider。

## Project Structure

### Documentation

```text
specs/010-web-chat-console/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── console-api-consumption.md
│   └── console-ui-contract.md
├── checklists/
│   └── requirements.md
└── tasks.md                 # /speckit-tasks 生成
```

### Source Code

```text
frontend/
├── package.json
├── package-lock.json
├── vite.config.ts
├── tsconfig.json
├── tsconfig.node.json
├── index.html
├── src/
│   ├── main.ts
│   ├── App.vue
│   ├── api/
│   │   ├── types.ts          # ApiEnvelope、ProfileView、SessionView 等镜像类型
│   │   ├── http.ts           # fetch 封装、信封解析、网络/业务错误归一化
│   │   └── oryxos.ts         # profiles/health/info/sessions/messages/archive
│   ├── state/
│   │   ├── useConsole.ts     # 首屏加载、Agent 选择、发送状态机、等待计时
│   │   └── localStore.ts     # userId 与按 Agent 的 sessionId 存取
│   ├── components/
│   │   ├── StatusBar.vue
│   │   ├── AgentSidebar.vue
│   │   ├── ConversationPanel.vue
│   │   ├── MessageBubble.vue
│   │   ├── ToolChips.vue
│   │   ├── ChatComposer.vue
│   │   └── EmptyState.vue
│   ├── utils/
│   │   ├── errors.ts         # 业务码到中文提示与 retryable 映射
│   │   └── timer.ts          # 等待秒数格式化
│   └── styles/
│       └── main.css
├── tests/
│   ├── http.test.ts
│   ├── errors.test.ts
│   ├── localStore.test.ts
│   └── useConsole.test.ts
└── demo/
    ├── README.md
    ├── agents/daily-weather/AGENT.md
    └── application.yaml.example
```

构建产物：`oryxos-web/src/main/resources/static/console/`（加入 `.gitignore`）。

**Structure Decision**: 采用独立 `frontend/` 工程，与 `website/`（VitePress 文档站）、`oryxos-web/`（后端模块）并列。前端不注册为 Maven 模块，避免把 Node 工具链引入现有构建；生产集成通过静态资源目录完成。

## Phase 0: Outline & Research

已产出 [research.md](research.md)，决策覆盖：框架与依赖版本、无 UI 库的样式策略、无 Pinia 的状态组织、原生 `fetch`、本地会话标识、测试分层、构建落点与「新对话」的归档语义。

## Phase 1: Design & Contracts

已产出：

- [data-model.md](data-model.md)：前端镜像类型、界面状态机、本地存储键与失效规则。
- [contracts/console-api-consumption.md](contracts/console-api-consumption.md)：消费的后端端点、请求/响应字段、错误码映射。
- [contracts/console-ui-contract.md](contracts/console-ui-contract.md)：界面状态、交互契约与展示约束。
- [quickstart.md](quickstart.md)：演示环境准备、启动、验证场景与命令。

## Complexity Tracking

无宪章违规。未引入路由、状态库、UI 组件库或额外后端层；复杂度仅来自同步请求等待态与会话恢复，这两项由 spec 的用户价值直接要求。

## Implementation Phases

1. **工程骨架与请求层**：`package.json`、Vite/TS 配置、`http.ts`、`oryxos.ts`、错误映射；先写失败测试。
2. **状态与本地存储**：`useConsole.ts`、`localStore.ts`；覆盖创建/复用/新对话/归档/刷新恢复。
3. **界面组件**：状态条、Agent 栏、消息列表、工具 chips、输入框、等待与错误状态；手写 CSS 对齐现有落地页配色。
4. **演示与验证**：`frontend/demo/` 示例工作区、quickstart 全流程、构建产物接入 `/console/`。
5. **交付记录**：验证结果、未验证项（真实 Provider/公网）、运行方式。
