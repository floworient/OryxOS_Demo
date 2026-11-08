# Tasks: 第一版 Web 对话工作台

**Input**: Design documents from `specs/010-web-chat-console/`

**Prerequisites**: [plan.md](plan.md)、[spec.md](spec.md)、[research.md](research.md)、[data-model.md](data-model.md)、[contracts/](contracts/)、[quickstart.md](quickstart.md)

**Tests**: 项目宪章与设计文档要求覆盖错误码映射与会话状态机，因此包含 Vitest 测试任务；真实模型与浏览器流程由 quickstart 手工验证。

**Organization**: 按用户故事分组，可独立实现与验证。

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 可并行（不同文件、无未完成依赖）
- **[Story]**: 对应 spec.md 的 US1/US2/US3
- 每个任务包含明确文件路径

## Path Conventions

- 前端工程：`frontend/`
- 测试：`frontend/tests/`
- 设计文档：`specs/010-web-chat-console/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 初始化独立前端工程与构建配置

- [ ] T001 创建 `frontend/package.json`，固定运行依赖 `vue@3.5.45` 与开发依赖 `vite@8.3.6`、`@vitejs/plugin-vue@6.0.10`、`typescript@5.9.3`、`vue-tsc@3.3.14`、`vitest@5.1.0`、`@vue/test-utils@2.5.1`、`jsdom@29.1.1`、`@types/node@24.x`，脚本包含 `dev`、`build`、`preview`、`test`、`typecheck`
- [ ] T002 [P] 创建 `frontend/index.html`、`frontend/tsconfig.json`、`frontend/tsconfig.node.json` 与 `frontend/src/env.d.ts`
- [ ] T003 [P] 创建 `frontend/vite.config.ts`：Vue 插件、`base=/console/`、`build.outDir=../oryxos-web/src/main/resources/static/console`、`emptyOutDir=true`、`/api` 代理到 `VITE_API_PROXY_TARGET`（默认 `http://localhost:8080`）、Vitest 使用 `jsdom`
- [ ] T004 [P] 在根 `.gitignore` 增加 `oryxos-web/src/main/resources/static/console/` 与 `frontend/node_modules/`
- [ ] T005 [P] 创建 `frontend/src/styles/main.css`：两栏布局、状态条、消息气泡、工具 chip、等待/错误态，配色对齐现有落地页
- [ ] T006 运行 `cd frontend && npm install` 生成并提交 `package-lock.json`

**Checkpoint**: `npm install` 成功，空工程可启动。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 类型、请求层、本地存储与基础状态，所有用户故事共同依赖

**⚠️ CRITICAL**: 本阶段完成前不能开始用户故事

- [ ] T007 [P] 在 `frontend/src/api/types.ts` 定义 `ApiEnvelope<T>`、`ProfileSummary`、`SystemInfo`、`SessionView`、`MessageView`、`ToolReferenceView`、`MessageReplyView`、`SessionArchiveView`，字段与 data-model.md 完全一致
- [ ] T008 [P] 在 `frontend/tests/errors.test.ts` 编写失败测试：已知业务码映射为固定中文提示且 `retryable` 正确，未知码与 `NETWORK_ERROR`/`INVALID_RESPONSE` 使用通用提示
- [ ] T009 在 `frontend/src/utils/errors.ts` 实现 `UiError` 与 `mapError`，覆盖 contracts/console-api-consumption.md 的全部错误码；不得拼接后端 `message` 原文
- [ ] T010 [P] 在 `frontend/tests/http.test.ts` 编写失败测试：成功信封返回 `data`、业务错误码抛出可映射错误、非 JSON/网络失败归一化为 `NETWORK_ERROR`/`INVALID_RESPONSE`
- [ ] T011 在 `frontend/src/api/http.ts` 实现 `getJson`/`postJson`/`deleteJson`，统一解析 `ApiResponse`，设置 `Content-Type: application/json`
- [ ] T012 [P] 在 `frontend/src/api/oryxos.ts` 实现 `fetchProfiles`、`fetchHealth`、`fetchInfo`、`createSession`、`sendMessage`、`fetchSession`、`archiveSession`，路径固定为 `/api/v1/...`
- [ ] T013 [P] 在 `frontend/tests/localStore.test.ts` 编写失败测试：匿名 `userId` 首建后复用、按 Agent 存/取/删会话标识、存储不可用时降级不崩溃
- [ ] T014 在 `frontend/src/state/localStore.ts` 实现 `oryxos.console.userId` 与 `oryxos.console.session.<agentName>` 读写，异常安全
- [ ] T015 [P] 在 `frontend/tests/useConsole.test.ts` 编写失败测试：首屏并行加载 profiles/health/info、profiles 为空进入 empty、health/info 失败仍展示 Agent 列表、默认选中第一个 Agent
- [ ] T016 在 `frontend/src/state/useConsole.ts` 实现 `ConsoleState` 与首屏加载、Agent 选择、按 Agent 建立 `AgentConversation` 的基础状态（不含发送/归档细节）

**Checkpoint**: 类型、请求、错误映射、本地存储与首屏状态测试通过。

---

## Phase 3: User Story 1 - 真实对话与工具调用 (Priority: P1) 🎯 MVP

**Goal**: 打开页面选择 Agent，发送消息获得真实回复，并在该轮展示工具名称。

**Independent Test**: 在已配置 Provider 与 `daily-weather` 的工作区发送天气问题，回复来自模型，工具 chip 与后端 `SessionView.messages[].toolCalls` 一致。

### Tests for User Story 1

- [ ] T017 [P] [US1] 在 `frontend/tests/useConsole.send.test.ts` 编写失败测试：首条消息先创建会话再发送、成功后重新拉取历史重建消息、assistant 的 `toolCalls[].name` 去重展示、`tool` 角色消息不渲染
- [ ] T018 [P] [US1] 在 `frontend/tests/components.test.ts` 编写失败测试：`ToolChips` 只渲染名称、`MessageBubble` 保留换行、`ChatComposer` 对空白输入不触发发送

### Implementation for User Story 1

- [ ] T019 [US1] 在 `frontend/src/state/useConsole.ts` 实现发送状态机：首次 `createSession` → `sendMessage` → `fetchSession`，维护 `messages`、`historyTruncated`、`waitingSeconds`
- [ ] T020 [P] [US1] 创建 `frontend/src/components/ToolChips.vue`，只展示工具名并按名称去重
- [ ] T021 [P] [US1] 创建 `frontend/src/components/MessageBubble.vue`，区分 user/assistant 气泡，保留文本换行，渲染工具 chips
- [ ] T022 [P] [US1] 创建 `frontend/src/components/ChatComposer.vue`，`Enter` 发送、`Shift+Enter` 换行，空白本地拦截
- [ ] T023 [P] [US1] 创建 `frontend/src/components/StatusBar.vue` 与 `frontend/src/components/AgentSidebar.vue`，展示健康、计数、Provider 与真实 Agent 列表
- [ ] T024 [US1] 创建 `frontend/src/components/ConversationPanel.vue` 与 `frontend/src/components/EmptyState.vue`，组合消息列表、输入区、标题与空/错误状态
- [ ] T025 [US1] 创建 `frontend/src/App.vue` 并接入 `useConsole`，完成一屏两栏布局
- [ ] T026 [P] [US1] 创建 `frontend/demo/agents/daily-weather/AGENT.md`，frontmatter 使用 `name`、`description`、`provider.name=deepseek`、`provider.model=deepseek-chat`、`tools=[http_get]`，正文说明查询 `api.open-meteo.com` 并输出中文天气与穿搭建议
- [ ] T027 [P] [US1] 创建 `frontend/demo/application.yaml.example`，只包含 `oryxos.http.allowed-domains: [api.open-meteo.com]`，并在注释中说明 Provider 配置位于 `config/application.yml`

**Checkpoint**: MVP 可演示；真实模型调用按 quickstart 验证。

---

## Phase 4: User Story 2 - 会话延续与隔离 (Priority: P2)

**Goal**: 刷新后恢复历史，切换 Agent 不串会话，新对话与归档行为正确。

**Independent Test**: 两轮对话后刷新恢复并可继续；切换 Agent 消息独立；新对话后会话 ID 改变，旧会话已归档。

### Tests for User Story 2

- [ ] T028 [P] [US2] 在 `frontend/tests/useConsole.session.test.ts` 编写失败测试：刷新按本地会话 ID 恢复 ACTIVE 历史、404 时清除本地键、ARCHIVED 进入只读、切换 Agent 读取各自会话、新对话先 DELETE 再清空、归档后状态为 idle

### Implementation for User Story 2

- [ ] T029 [US2] 在 `frontend/src/state/useConsole.ts` 实现 `restoreSession`、`selectAgent` 的会话切换、`loadHistory` 与失效清理
- [ ] T030 [US2] 在 `frontend/src/state/useConsole.ts` 实现 `newConversation`：存在活动会话时先 `archiveSession`，再清空消息与本地键；实现 `archiveConversation`
- [ ] T031 [US2] 在 `ConversationPanel.vue` 接入「新对话」「归档」按钮及归档只读态（禁用输入、展示开始新对话）
- [ ] T032 [US2] 在 `ConversationPanel.vue` 顶部展示 `historyTruncated` 提示「仅展示最近 100 条」

**Checkpoint**: 刷新、切换、新对话、归档均可独立验证。

---

## Phase 5: User Story 3 - 故障与状态反馈 (Priority: P3)

**Goal**: 后端不可达、Provider 不可用、忙碌、超时与空工作区都有明确可操作的提示。

**Independent Test**: 模拟后端不可达、Provider 错误、会话忙碌、超时与空工作区，界面均显示对应中文提示且不丢输入、不白屏。

### Tests for User Story 3

- [ ] T033 [P] [US3] 在 `frontend/tests/useConsole.errors.test.ts` 编写失败测试：网络失败进入 offline、发送失败保留 draft、等待 55 秒标记即将超时、发送中禁止重复提交、空工作区进入 empty

### Implementation for User Story 3

- [ ] T034 [US3] 在 `frontend/src/state/useConsole.ts` 实现等待计时器、发送互斥、错误写入 `UiError` 与重试入口、offline 状态
- [ ] T035 [US3] 在 `ConversationPanel.vue` 与 `ChatComposer.vue` 展示等待秒数、55 秒提示、错误条、禁用发送与重试
- [ ] T036 [US3] 在 `AgentSidebar.vue`/`EmptyState.vue` 实现空工作区引导文案与 Agent 刷新入口
- [ ] T037 [US3] 为等待态添加 `aria-live="polite"`，为按钮补充可读标签，满足 contracts/console-ui-contract.md 的可访问性要求

**Checkpoint**: 六类错误场景均有固定中文提示。

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 构建、文档、端到端验证与交付记录

- [X] T038 [P] 创建 `frontend/demo/README.md`，说明复制到 `.oryxos/`、Provider 配置、启动与清理步骤
- [X] T039 [P] 创建 `frontend/README.md`，说明工程结构、开发代理、生产构建落点与不做范围
- [X] T040 运行 `cd frontend && npm run typecheck && npm test`，修复全部失败
- [X] T041 运行 `cd frontend && npm run build`，确认产物位于 `oryxos-web/src/main/resources/static/console/` 且未进入 git 跟踪
- [ ] T042 按 [quickstart.md](quickstart.md) 执行真实后端端到端验证，记录真实模型/工具调用结果
- [ ] T043 执行错误与空工作区场景验证；无法使用的真实 Provider/外网/超时条件明确标记未验证
- [X] T044 创建 `specs/010-web-chat-console/verification.md`，记录命令、结果、通过/失败与未验证项
- [X] T045 全仓 `./mvnw.cmd test`（Windows PowerShell）（确认前端改动未影响后端）并记录结果

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 无依赖，可立即开始
- **Foundational (Phase 2)**: 依赖 Setup，阻塞所有用户故事
- **User Story 1 (Phase 3)**: 依赖 Foundational；MVP
- **User Story 2 (Phase 4)**: 依赖 US1 的状态机骨架；与 US1 串行修改 `useConsole.ts`
- **User Story 3 (Phase 5)**: 依赖 US1/US2 的错误与状态字段；串行修改 `useConsole.ts`
- **Polish (Phase 6)**: 依赖所有目标用户故事完成

### User Story Dependencies

- **US1**: Foundational 完成后可开始，无其他故事依赖
- **US2**: 依赖 US1 的消息与会话基础状态，但可通过自身测试独立验证
- **US3**: 依赖 US1/US2 的状态字段，但错误映射本身在 Foundational 已可独立测试

### Within Each User Story

- 测试先写并确认失败，再实现
- 类型/映射 → 组件 → 状态集成
- 每个故事完成后执行对应测试再进入下一优先级

### Parallel Opportunities

- T002–T005 可并行
- T007、T008、T010、T013、T015 可并行
- T020–T023 可并行
- T026、T027 可并行
- T038、T039 可并行

---

## Parallel Example: User Story 1

```bash
# 测试并行编写
Task: "useConsole.send.test.ts 发送与工具展示状态机"
Task: "components.test.ts ToolChips/MessageBubble/ChatComposer"

# 组件并行创建
Task: "ToolChips.vue"
Task: "MessageBubble.vue"
Task: "ChatComposer.vue"
Task: "StatusBar.vue + AgentSidebar.vue"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. 完成 Phase 1 Setup
2. 完成 Phase 2 Foundational
3. 完成 Phase 3 US1
4. 运行 `npm test` 与 quickstart 的真实对话验证
5. 演示通过后再进入 US2/US3

### Incremental Delivery

1. Setup + Foundational → 请求与状态地基
2. US1 → 可演示真实对话与工具调用（MVP）
3. US2 → 刷新恢复、Agent 隔离、新对话/归档
4. US3 → 错误、等待与空工作区体验
5. Polish → 构建接入 `/console/`、验证记录与交付说明

---

## Notes

- 后端 `/api/v1` 契约冻结，任何任务都不得修改 `oryxos-web` 接口或新增端点
- 「新对话」必须先归档活动会话，否则后端会复用旧会话
- `frontend/node_modules/`、`.oryxos/`、`config/application.yml`、构建产物不提交
- 真实 Provider 调用缺失时不得用桩结果冒充端到端通过
