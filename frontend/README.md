# OryxOS 工作台（第一版）

Vue 3 + TypeScript + Vite 实现的对话工作台，只消费后端已冻结的 `/api/v1` 接口。

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

产物输出到 `oryxos-web/src/main/resources/static/console/`（已加入 `.gitignore`），随后重新打包后端即可通过 `/console/index.html` 访问。

## 范围

本版只做对话工作台：Agent 列表、真实对话、工具调用名称、刷新恢复、按 Agent 隔离、新对话、归档与错误提示。不包含管理台、认证、流式输出、Provider/Agent 在线配置、调度或审计查询。
