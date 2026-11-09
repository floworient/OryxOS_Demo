# OryxOS 工作台（第一版）

Vue 3 + TypeScript + Vite 实现的对话工作台。定时任务页使用 `/api/v2/schedules`，现有会话和管理查询继续使用 `/api/v1`。

## 开发

```bash
npm install
npm run dev
```

默认访问 `http://localhost:5173`，`/api` 代理到 `http://localhost:8080`。后端端口不同时：

```bash
VITE_API_PROXY_TARGET=http://localhost:9090 npm run dev
```

## 测试与类型检查

```bash
npm test
npm run typecheck
```

测试覆盖 API 信封解析、错误码映射、本地会话存储与 `useConsole` 会话状态机；真实模型调用需要按 `specs/010-web-chat-console/quickstart.md` 手工验证。

## 构建

```bash
npm run build
```

产物输出到 `oryxos-web/src/main/resources/static/console/`，后端会将页面与哈希资源打入 JAR。修改页面后重新构建前端和后端，并一并更新静态资源，通过 `/console/index.html` 访问。

## 范围

工作台提供 Agent、Provider、工具、白名单、记忆、运行状态及会话视图。定时任务支持查看状态、立即执行和启停。调度定义由 Agent 文件维护，修改后在服务启动时重新注册。
