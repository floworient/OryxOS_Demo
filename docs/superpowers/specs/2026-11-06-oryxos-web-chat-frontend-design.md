# OryxOS Web 对话前端（每日天气演示）设计

- 日期：2026-11-06
- 分支：`codex/web-service-core`
- 状态：待评审

## 1. 背景与目的

Spec 009 已经交付了可运行的 Web Service：会话创建/发消息/查历史/归档、无状态调用、Profile、Tool、Memory、Health/Info 共 10 个核心端点，外加通知渠道 5 个端点。但目前唯一的交互界面是 Swagger UI，演示时只能靠 curl 或接口调试面板，看不出"这是一个能自主调工具的 Agent 运行时"。

本设计交付一个**演示用对话前端**，让人在一屏内看到三件事：选中的 Agent 是真注册的、回复是模型真跑出来的、这一轮真的执行了工具调用。叙事完全对齐 README：目录即 Agent → 多入口复用同一条 ReAct 链路 → Web API 对外。

## 2. 目标

- 打开页面 → 选"每日天气" → 提问 → 看到回答和本轮调用的工具 → 刷新后历史仍在。
- 只调用已经实现并验证过的接口，不新增后端 API。
- 演示前 5 分钟内可启动（一个 `npm run dev` 加一个已配置好的工作区）。
- 前端工程可独立演进，后续加"通知渠道""只读控制台"等页面不需要重构。

## 3. 非目标与反目标

非目标（本阶段不做）：

- 管理台：Agent/Provider 的增删改、调度、审计查询。
- 网页配置 Provider 或 API Key（用户已确认走 `application.yaml`）。
- 认证、多用户、权限。
- 流式输出（后端明确没有 SSE/WebSocket）。
- 移动端适配、国际化、暗色主题。

反目标（做到这些就算失败）：

- 展示写死的假数据或假回复。
- 为了让前端好做而修改 009 已冻结的接口契约。
- 把 DeepSeek Key 写进前端代码、localStorage 或仓库。
- 做成一屏放不下的"仪表盘"，演示时要点很多次才看到对话。

## 4. 现状与硬约束

| 约束 | 说明 |
| --- | --- |
| 无流式 | `POST /sessions/{id}/messages` 同步返回，最长 60 秒后 504 `AGENT_TIMEOUT` |
| 无会话列表 | 只能靠前端自己记住 sessionId |
| 无 Provider 管理 | Provider 启动时从 `application.yaml`/环境变量构建，运行时不可变 |
| 无认证 | 接口对 `/api/**` 全开放 CORS，只能部署在可信内网 |
| 无 Agent 管理 | Agent 来自工作区 `agents/<name>/AGENT.md`，改目录需重启或重新触发加载 |
| 统一信封 | 所有响应 `{code, message, data, timestamp}`，成功 `code=SUCCESS` |

用到的接口：

| 方法 | 路径 | 用途 | 关键返回 |
| --- | --- | --- | --- |
| `GET` | `/api/v1/profiles` | 左栏 Agent 列表 | `name`、`description`、`providerName`、`model`、`tools` |
| `GET` | `/api/v1/health` | 顶部状态 | `{status:"UP"}` |
| `GET` | `/api/v1/info` | 顶部状态 | `profileCount`、`toolCount`、`providers[]` |
| `POST` | `/api/v1/sessions` | 建会话 | 201 新建 / 200 复用，`data` 为 SessionView |
| `POST` | `/api/v1/sessions/{id}/messages` | 发消息 | `{sessionId, reply}` |
| `GET` | `/api/v1/sessions/{id}` | 拉历史（含工具调用） | SessionView |
| `DELETE` | `/api/v1/sessions/{id}` | 归档 | `{sessionId, status:"ARCHIVED"}` |

错误码映射：

| HTTP | code | 界面表现 |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | 输入框下方红字，不发请求 |
| 404 | `AGENT_NOT_FOUND` | 提示 Agent 已被移除，刷新列表 |
| 409 | `SESSION_BUSY` | "上一轮还没结束"，禁用输入 |
| 503 | `PROVIDER_UNAVAILABLE` | "模型服务不可用，检查 Provider 配置" |
| 504 | `AGENT_TIMEOUT` | "超过 60 秒，本轮已取消" |
| 500 | `INTERNAL_ERROR` / `STORAGE_ERROR` | 通用错误条，可重试 |

## 5. 已定决策

| 决策 | 选择 | 理由 |
| --- | --- | --- |
| 框架 | Vue 3 + TypeScript + Vite | 仓库文档（`TechnicalSolution.md`、`website/`）明确设想管理台是 Vue 3；VitePress 也是 Vue 生态 |
| 位置 | 仓库根目录 `frontend/` | 与 `website/`（文档站）、`oryxos-web`（后端模块）并列，互不干扰 |
| UI 库 | 不引入（手写 CSS） | 对话界面本来就自定义；避免引入整套组件库的视觉风格和体积，配色对齐现有落地页 |
| 开发连接 | Vite dev server + `/api` 代理到 `http://localhost:8080` | 同源请求，不依赖 CORS；后端端口可通过环境变量覆盖 |
| 生产形态 | `npm run build` 产物输出到 `oryxos-web/src/main/resources/static/console/`（该目录加入 `.gitignore`） | 重新打包后 `serve` 可直接访问 `/console/`；本阶段演示以 dev server 为主 |
| 会话记忆 | sessionId 按 Agent 名存 localStorage | 后端没有会话列表，这是唯一能"刷新后接着聊"的方式 |
| 工具展示 | 每轮回复后 `GET /sessions/{id}` 重建历史，渲染工具调用 chips | `messages[].toolCalls[]` 只有工具名和 id，不暴露参数与结果 |
| Provider | 只读展示 `providers[]` 与模型名 | 用户已决定 key 走 `application.yaml`，前端不做配置 |
| 演示 Agent | `daily-weather`，天气数据走 `api.open-meteo.com`（免 key） | README 的验收场景之一，且不依赖第三方密钥 |

## 6. 页面与交互

一屏两栏，无路由跳转：

```text
┌─────────────────────────────────────────────────────────────┐
│ OryxOS  ● 健康  Agents 1 · Tools 14 · Provider deepseek     │  ← 状态条
├──────────────┬──────────────────────────────────────────────┤
│ Agent        │  daily-weather                    [新对话][归档]│
│ ┌──────────┐ │ ┌──────────────────────────────────────────┐ │
│ │每日天气 ✓│ │ │ user  今天杭州天气怎么样？                │ │
│ └──────────┘ │ │ assistant 今天杭州 18~27℃，多云…          │ │
│              │ │   ⚙ http_get                             │ │
│              │ │ user  那明天呢？                          │ │
│              │ │ assistant …（等待中 · 已 12s）            │ │
│              │ └──────────────────────────────────────────┘ │
│              │ ┌──────────────────────────────────────────┐ │
│              │ │ 输入消息…                          [发送] │ │
│              │ └──────────────────────────────────────────┘ │
└──────────────┴──────────────────────────────────────────────┘
```

状态与行为：

- **首屏**：并行请求 profiles / health / info。profiles 为空时显示"工作区没有 Agent，先运行 profile create"的引导，而不是空白页。
- **选 Agent**：默认选中第一个；切换 Agent 时切换对应 localStorage 里的会话，不串会话。
- **首条消息**：先 `POST /sessions`，记下 id，再发消息。
- **等待态**：气泡内显示"已等待 Ns"，超过 55s 提示即将超时；期间禁用发送按钮，避免撞上 409。
- **成功**：用 `GET /sessions/{id}` 整体重建消息列表，保证与后端一致（包括工具调用）。
- **失败**：按错误码映射成中文提示，保留用户输入，允许重试。
- **新对话**：清掉 localStorage 里的 sessionId，下一条消息重新建会话。
- **归档**：调用 DELETE，清 localStorage，界面回到空对话。

## 7. 演示环境准备（属于本次交付）

演示工作区（不进版本库，或放在 `frontend/demo/` 且 `.gitignore`）需要：

```text
.oryxos/
├── agents/daily-weather/AGENT.md
├── application.yaml          # deepseek + 天气域名白名单
└── oryxos.db                 # init 后自动生成
```

`AGENT.md` 关键字段：

```markdown
---
name: daily-weather
description: 每日天气与穿搭建议
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

`.oryxos/application.yaml`：

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

启动：`java -jar oryxos-boot-...-exec.jar serve --port 8080`，前端 `npm run dev`。

## 8. 验收标准

- [ ] 页面加载后左栏列出真实 Agent（数量与 `/api/v1/profiles` 一致），顶部显示健康与 Provider 名。
- [ ] 选择 `daily-weather` 提问，能拿到真实模型回复。
- [ ] 回复下方出现 `http_get` 工具标记，且与 `GET /sessions/{id}` 返回的 `toolCalls` 一致。
- [ ] 刷新浏览器后历史仍在，继续提问能延续上下文。
- [ ] 停掉后端时页面给出明确错误，不白屏、不静默失败。
- [ ] 仓库中不存在任何真实 Key（前端、配置示例、代码注释）。
- [ ] 不修改 `oryxos-web` 的任何接口契约。

## 9. 留给实现者的决定

- 组件拆分粒度、是否引入 Pinia（数据量小，可能不需要）、CSS 变量命名。
- 前端测试程度（建议至少覆盖错误码映射与会话状态机，不追求组件快照）。
- 构建产物落点的具体脚本（是否用 npm script 复制、是否在 Maven 里挂 frontend-maven-plugin）。
- 等待计时器与轮询细节。
