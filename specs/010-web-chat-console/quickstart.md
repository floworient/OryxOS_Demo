# Quickstart: 第一版 Web 对话工作台

**Date**: 2026-11-06 | **Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## 1. 前置条件

- JDK 21。本机使用 `D:/IDE/JDK/JDK21`，执行 Maven 时需 `env -u TERM`。
- Node.js ≥ 22.12（本机 24.14.0），npm ≥ 10（本机 11.9.0）。
- 已构建后端可执行 JAR；本机可运行 `bin/start.sh`。
- `config/application.yml` 已配置 `deepseek` Provider 与 API Key（该文件不纳入版本管理）。
- 天气工具需要外网访问 `api.open-meteo.com`。

## 2. 准备演示工作区

```bash
# 1. 复制演示 Agent
mkdir -p .oryxos/agents
cp -r frontend/demo/agents/daily-weather .oryxos/agents/

# 2. 新工作区可直接复制白名单示例
cp -n frontend/demo/application.yaml.example .oryxos/application.yaml
```

> `config/application.yml` 与 `.oryxos/application.yaml` 都会加载；后者可用于补充演示专用配置。若两处都定义 `oryxos.providers`，请注意 Spring 对列表是整体替换。

已有工作区请将 `api.open-meteo.com` 合并到 `.oryxos/application.yaml` 的 `oryxos.http.allowed-domains`。修改后重启后端。看到「工具安全检查拒绝」时，先核对运行的 JAR 是否包含内置 GET 的策略装配修复，以及是否装配了自定义工具策略。看到「HTTP 请求被 Sandbox 拒绝」时，检查实际生效的域名白名单和请求地址。

## 3. 启动后端

```bash
# 首次或后端代码变化后
env -u TERM ./mvnw -pl oryxos-boot -am package -DskipTests

# 启动（端口默认 8080）
JAVA_HOME=D:/IDE/JDK/JDK21 bin/start.sh 8080

# 确认健康
curl http://localhost:8080/api/v1/health
```

预期：健康接口返回 `{"code":"SUCCESS","data":{"status":"UP"}}`；日志位于 `logs/oryxos.log`。

## 4. 启动工作台

```bash
cd frontend
npm install
npm run dev
```

打开终端提示的地址（默认 `http://localhost:5173`）。开发代理将 `/api` 转发到 `http://localhost:8080`；如端口不同：

```bash
VITE_API_PROXY_TARGET=http://localhost:9090 npm run dev
```

## 5. 验证场景

### 5.1 首屏与真实 Agent

- 左栏 Agent 数量与 `curl http://localhost:8080/api/v1/profiles` 返回数量一致。
- 顶部显示健康状态、Agent 数、Tool 数与 Provider 名称。
- 清空 `.oryxos/agents` 后重启后端，页面显示「创建工作区 Agent」引导。

### 5.2 真实对话与工具调用

1. 选择 `daily-weather`，输入「今天杭州天气怎么样？」并发送。
2. 等待期间显示已等待秒数，发送按钮禁用。
3. 回复到达后，确认内容来自真实模型。
4. 回复下方出现 `http_get` 工具 chip；与 `GET /api/v1/sessions/{id}` 返回的 `toolCalls` 名称集合一致。

### 5.3 刷新恢复与 Agent 隔离

1. 完成两轮对话后刷新浏览器，历史仍在。
2. 继续提问，确认延续上下文（会话 ID 不变）。
3. 准备第二个 Agent 并重启后端，在两个 Agent 间切换，确认消息不串。
4. 点击「新对话」：当前活动会话被归档（`DELETE` 成功），页面清空；下一条消息创建新会话，新会话 ID 与旧 ID 不同。

### 5.4 错误与边界

| 场景 | 操作 | 预期 |
| --- | --- | --- |
| 后端不可达 | 停止后端后刷新页面或发送 | 5 秒内显示「无法连接运行时」，不白屏 |
| 消息为空 | 输入空格后发送 | 不发起请求，输入区提示 |
| 会话失效 | 在另一个终端 `DELETE` 当前会话后发送 | 提示会话失效并引导新对话 |
| Provider 不可用 | 临时使用错误 API Key 启动后端 | 显示「模型服务不可用」固定提示 |
| 超时 | 使用慢 Provider 或桩模拟 | 显示「超过 60 秒已取消」 |
| 会话忙碌 | 自动化测试中用双请求模拟 | 显示「上一轮还在处理中」 |

> 真实 Provider、外网和超时场景若缺少条件，必须在交付记录中标记为未验证；不得用桩结果冒充端到端通过。

### 5.5 自动化与构建

```bash
cd frontend
npm test          # Vitest：信封解析、错误映射、本地存储、会话状态机
npm run typecheck # vue-tsc
npm run build     # 产物输出到 oryxos-web/src/main/resources/static/console/
```

构建后重新打包并启动后端，访问 `http://localhost:8080/console/index.html` 验证生产静态资源。

## 6. 清理

```bash
# 停止后端
bin/stop.sh

# 演示数据（可选，确认不需要后再删）
rm -rf .oryxos/agents/daily-weather .oryxos/application.yaml
```

不要提交 `.oryxos/`、`config/application.yml`、`logs/` 或 `oryxos-web/src/main/resources/static/console/`。
