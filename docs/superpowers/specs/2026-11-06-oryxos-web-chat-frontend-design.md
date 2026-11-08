# OryxOS Web 对话前端与 Agent 新建（每日天气演示）设计

- 日期：2026-11-06
- 分支：`codex/web-service-core`
- 状态：待评审

## 1. 背景与目的

Spec 009 已经交付了可运行的 Web Service：会话创建/发消息/查历史/归档、无状态调用、Profile、Tool、Memory、Health/Info 共 10 个核心端点，外加通知渠道 5 个端点。但唯一的交互界面是 Swagger UI，演示时只能靠 curl，看不出"这是一个能自主调工具的 Agent 运行时"。

本设计交付一个演示用对话前端，让人在一屏内看到：Agent 是现场新建的、回复是模型真跑出来的、这一轮真的执行了工具调用。叙事对齐 README：目录即 Agent → 多入口复用同一条 ReAct 链路 → Web API 对外。

新建 Agent 需要后端新增接口，**这超出 009 的 FR-023**（本阶段不做动态 Agent 管理）。经确认按演示扩展实现，走 TDD，不新开 010 规格；产品化时再补规格来源。

## 2. 目标

- 网页上填表新建一个 Agent，保存后立刻出现在左栏、能直接对话，不需要重启。
- 选中"每日天气"提问，能看到回答和本轮调用的工具。
- 刷新后历史仍在，继续提问能延续上下文。
- 只新增一个后端端点，其余全部复用已实现接口。
- 演示前 5 分钟内可启动。

## 3. 非目标与反目标

非目标：

- Agent 的编辑、删除（只做新建）。
- 管理台：Provider 增删改、调度、审计查询。
- 网页配置 Provider 或 API Key（用户已确认走 `application.yaml`）。
- 认证、多用户、权限（新建 Agent 接口因此在可信内网内等同于"任何人可写 Agent 目录"）。
- 流式输出（后端没有 SSE/WebSocket）。
- 移动端适配、国际化、暗色主题。

反目标：

- 展示写死的假数据或假回复。
- 为了让前端好做而修改 009 已冻结的接口契约。
- 把 DeepSeek Key 写进前端代码、localStorage 或仓库。
- 做成一屏放不下的仪表盘，演示时要点击很多次才看到对话。

## 4. 现状与硬约束

| 约束 | 说明 |
| --- | --- |
| 无流式 | `POST /sessions/{id}/messages` 同步返回，最长 60 秒后 504 `AGENT_TIMEOUT` |
| 无会话列表 | 只能靠前端自己记 sessionId |
| 会话按身份复用 | `(channel=web, userId, profileName)` 同时最多一个 ACTIVE 会话（V6 唯一索引） |
| 无 Provider 管理 | Provider 启动时构建，运行时不可变；新建 Agent 只能引用已配置的 Provider |
| 无认证 | `/api/**` 全开放 CORS，只能部署在可信内网 |
| 统一信封 | 所有响应 `{code, message, data, timestamp}`，成功 `code=SUCCESS` |
| Agent 加载有强校验 | 目录名必须等于 frontmatter `name`，必须有 `identity.agent_name` / `identity.prompt`，frontmatter 外不得越界 |

用到的接口：

| 方法 | 路径 | 用途 | 关键返回 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/profiles` | 左栏 Agent 列表 | `name`、`description`、`agentName`、`providerName`、`model`、`tools` |
| `GET` | `/api/v1/health` | 顶部状态 | `{status:"UP"}` |
| `GET` | `/api/v1/info` | 顶部状态 | `profileCount`、`toolCount`、`providers[]` |
| `GET` | `/api/v1/tools` | 新建表单的工具多选 | 工具名与描述 |
| `POST` | `/api/v1/agents` | **新增**：新建 Agent | 201 + `Location` + ProfileView |
| `POST` | `/api/v1/sessions` | 建会话 | 201 新建 / 200 复用 |
| `POST` | `/api/v1/sessions/{id}/messages` | 发消息 | `{sessionId, reply}` |
| `GET` | `/api/v1/sessions/{id}` | 拉历史（含工具调用） | SessionView |
| `DELETE` | `/api/v1/sessions/{id}` | 归档 | `{sessionId, status:"ARCHIVED"}` |

错误码映射：

| HTTP | code | 界面表现 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 表单字段红字，不发请求 |
| 404 | `AGENT_NOT_FOUND` | 提示 Agent 已被移除，刷新列表 |
| 404 | `SESSION_NOT_FOUND` | 会话已归档，清 id 重开 |
| 409 | `AGENT_EXISTS` | "同名 Agent 已存在" |
| 409 | `SESSION_BUSY` | "上一轮还没结束"，禁用输入 |
| 503 | `PROVIDER_UNAVAILABLE` | "模型服务不可用，检查 Provider 配置" |
| 504 | `AGENT_TIMEOUT` | 进入对账流程（见第 6 节） |
| 500 | `INTERNAL_ERROR` / `STORAGE_ERROR` | 通用错误条，可重试 |

## 5. 已定决策

| 决策 | 选择 |
| --- | --- |
| 框架 | Vue 3 + TypeScript + Vite（与仓库文档设想的 Vue 3 管理台一致） |
| 位置 | 仓库根目录 `frontend/`，与 `website/`、`oryxos-web/` 并列 |
| UI 库 | 不引入，手写 CSS，配色对齐现有落地页 |
| 开发连接 | Vite dev server，`/api` 代理到 `http://localhost:8080` |
| 生产形态 | `npm run build` 输出到 `oryxos-web/src/main/resources/static/console/`（加入 `.gitignore`）；本次验收只要求 dev server |
| 用户身份 | 浏览器首次访问生成 UUID 存 localStorage，作为 `userId` |
| "新对话" | 先 `DELETE` 归档当前会话，再建新；不归档则后端会复用旧会话 |
| 归档会话 | `GET` 仍返回 200 + `ARCHIVED`，发消息 404；前端见到 `ARCHIVED` 自动清 id 重开 |
| 504 之后 | 不立即重发。锁定输入，每 3 秒 `GET` 对账，最多 20 秒；对账到回复就显示，对账不到才解锁重发 |
| 工具展示 | 每轮回复后 `GET /sessions/{id}` 重建历史，渲染工具名 chips |
| Provider | 只读展示；新建 Agent 时只能从 `info.providers` 里选 |
| 演示 Agent | 老规矩手工建 `daily-weather`，天气走 `api.open-meteo.com`（免 key）；同时演示网页新建一个 Agent |
| 演示工作区 | 提交到 `frontend/demo/.oryxos/`，key 用 `${DEEPSEEK_API_KEY}` 占位，只 ignore `oryxos.db` |

## 6. 页面与交互

一屏两栏，无路由跳转：

```text
┌─────────────────────────────────────────────────────────────┐
│ OryxOS  ● 健康  Agents 1 · Tools 14 · Provider deepseek     │
├──────────────┬──────────────────────────────────────────────┤
│ Agent   [+新建]│  daily-weather                    [新对话][归档]│
│ ┌──────────┐ │ ┌──────────────────────────────────────────┐ │
│ │每日天气 ✓│ │ │ user  今天杭州天气怎么样？                │ │
│ └──────────┘ │ │ assistant 今天杭州 18~27℃，多云…          │ │
│              │ │   ⚙ http_get                             │ │
│              │ │ assistant …（等待中 · 已 12s）            │ │
│              │ └──────────────────────────────────────────┘ │
│              │ ┌──────────────────────────────────────────┐ │
│              │ │ 输入消息…                          [发送] │ │
│              │ └──────────────────────────────────────────┘ │
└──────────────┴──────────────────────────────────────────────┘
```

发送状态机：

```text
idle ──发送──> creating ──> sending ──成功──> idle（重建历史）
                   │            │
                   │            ├─409──> busy（等待上一轮结束）
                   │            ├─504──> reconciling ──对账到回复──> idle
                   │            │                     └─20s 仍无──> failed（可重发）
                   │            └─其他──> failed（保留输入，可重试）
                   └─错误──> failed
```

行为要点：

- 首屏并行请求 profiles / health / info。profiles 为空时显示"先新建一个 Agent"的引导。
- 新建 Agent 弹窗：`name`、`description`、`providerName`（下拉）、`model`、`tools`（多选）、`instructions`（正文，Markdown）。提交成功后刷新 profiles 并自动选中新 Agent。
- 切换 Agent 时切换对应 localStorage 里的会话，不串会话。
- 等待态显示"已等待 Ns"，55s 起提示即将超时；期间禁用发送。
- 成功或对账成功后用 `GET /sessions/{id}` 整体重建消息列表，保证与后端一致。

## 7. 新建 Agent 接口（新增后端能力）

### 7.1 契约

`POST /api/v1/agents`，请求：

```json
{
  "name": "daily-weather",
  "description": "每日天气与穿搭建议",
  "providerName": "deepseek",
  "model": "deepseek-chat",
  "tools": ["http_get"],
  "instructions": "# 每日天气助手\n\n用户问天气时调用 http_get 查询 api.open-meteo.com……"
}
```

- 201，`Location: /api/v1/agents/{name}`，`data` 为现有 `ProfileView`。
- `identity.agent_name` 默认取 `name`，`identity.prompt` 默认取 `description`，表单不单独暴露这两个字段。

### 7.2 校验与实现

| 规则 | 失败码 |
| --- | --- |
| 名字符合 `AgentName`（长度、字符、Windows 保留名、`.`/`..`） | 400 `VALIDATION_ERROR` |
| `providerName` 在已配置 Provider 中 | 400 `VALIDATION_ERROR` |
| `model` 非空 | 400 `VALIDATION_ERROR` |
| 每个 tool 在 `ToolRegistry` 中存在 | 400 `VALIDATION_ERROR` |
| `instructions` 非空且有长度上限 | 400 `VALIDATION_ERROR` |
| 目录已存在 | 409 `AGENT_EXISTS` |

写入流程：在 `agents/` 下建临时目录 → 写 `AGENT.md` → 用 `AgentLoader` 按目录加载并校验 → 原子移动到 `agents/<name>/` → `ProfileRegistry.register`。任一步失败回滚临时目录并返回错误，不留下半成品。写盘与注册复用 `AgentLoader` 的既有校验，不复制解析逻辑。

新增：core 里补一个"按单个目录加载 Agent"的公开入口（复用现有私有 `load` 的校验）。

### 7.3 安全说明

该接口在无认证的当前阶段等于"内网任何调用方都能往 Agent 目录写文件并使其可执行工具"。这与 009 已经声明的部署前提一致（受网关和网络策略保护的可信内网），但必须在 README / API 文档中写明，不淡化。

### 7.4 TDD 顺序

1. RED：`AgentOnboardingServiceTest` —— 生成合法 `AGENT.md`、重名冲突、非法名字、未知 Provider、未知 Tool、写盘失败回滚。
2. GREEN：最小实现，使上述测试通过。
3. RED：`AgentOnboardingControllerTest` —— 201 + `Location` + 统一信封、400/409、字段校验。
4. GREEN：Controller。
5. RED：`OpenApiContractTest` 扩展到 17 个操作，新端点必须为 `application/json` + `ApiResponse` 信封。
6. GREEN：补齐注解。
7. 前端：`vitest` 覆盖错误码映射与会话状态机（含 504 对账），再实现界面。

## 8. 演示工作区（作为交付物提交）

```text
frontend/demo/.oryxos/
├── agents/daily-weather/AGENT.md
├── application.yaml          # deepseek + 天气域名白名单
└── oryxos.db                 # 运行时生成，已 ignore
```

`AGENT.md`（结构必须能被 `AgentLoader` 加载）：

```markdown
---
name: daily-weather
description: 每日天气与穿搭建议
identity:
  agent_name: 每日天气助手
  prompt: 你是一个简洁、可靠的天气助手
provider:
  name: deepseek
  model: deepseek-chat
tools:
  - http_get
---

# 每日天气助手

用户问天气时，调用 http_get 查询 api.open-meteo.com 的当前天气与预报，
用中文给出温度、降水和风力，并附一句简洁的穿搭建议。
```

`application.yaml`：

```yaml
oryxos:
  providers:
    - name: deepseek
      type: OPENAI_COMPATIBLE
      model: deepseek-chat
      base-url: https://api.deepseek.com
      api-key: ${DEEPSEEK_API_KEY}
  http:
    allowed-domains:
      - api.open-meteo.com
```

启动：`java -jar oryxos-boot-...-exec.jar serve --port 8080`（工作区用 `ORYXOS_ROOT=frontend/demo/.oryxos`），前端 `npm run dev`。

## 9. 验收标准

- [ ] 页面加载后左栏列出真实 Agent，顶部显示健康状态与 Provider 名。
- [ ] 网页新建一个 Agent，保存后无需重启即出现在左栏，并能直接对话。
- [ ] 新建产生的 `AGENT.md` 通过 `profile show` 可读，重启后仍在。
- [ ] 同名新建返回 409 并在表单内提示；非法名字、未知 Provider、未知 Tool 均返回 400。
- [ ] 选择 `daily-weather` 提问，能拿到真实模型回复。
- [ ] 回复下方出现 `http_get` 工具标记，与 `GET /sessions/{id}` 一致。
- [ ] 刷新浏览器后历史仍在，继续提问能延续上下文。
- [ ] 停掉后端、504 超时、会话被归档三种异常都有明确提示，不白屏。
- [ ] 仓库中不存在任何真实 Key。
- [ ] 不修改 009 已冻结的接口契约；OpenAPI 覆盖 17 个操作。

## 10. 留给实现者的决定

- 组件拆分粒度、是否引入 Pinia、CSS 变量命名。
- 临时目录命名与清理细节、`instructions` 长度上限的具体数值。
- 前端测试的具体覆盖点与测试框架配置。
- 是否用 npm script 复制构建产物，以及在 Maven 里挂 `frontend-maven-plugin` 的时机。
