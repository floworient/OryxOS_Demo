# Quickstart: 第 27 节人推主流程对账

## 1. 自动门禁（无密钥、无公网）

```powershell
.\mvnw.cmd -pl oryxos-provider,oryxos-storage,oryxos-web,oryxos-boot -am test
npm --prefix frontend test
npm --prefix frontend run build
.\mvnw.cmd clean verify
```

期望：Mock Provider、快速主流程、整机 HTTP、会话列表和前端状态测试通过；真实 `HumanTriggerFlowIT` 在没有真实 key 时明确跳过。

## 2. 准备本地无密钥 Agent

在本地（不提交真实运行数据）配置 `mock` Provider，并创建一个显式引用它且只允许 `save_memory` 的 Agent。示例字段见 [mock-provider.md](contracts/mock-provider.md)；工作区可通过 `-Doryxos.root=<临时目录>` 指向隔离目录。

## 3. 启动同一进程

```powershell
.\mvnw.cmd clean package -DskipTests
java -Doryxos.root=E:\tmp\oryxos-l27 -jar oryxos-boot\target\oryxos-boot-0.1.0-SNAPSHOT-exec.jar serve --port 8080
```

启动后 REST 和控制台来自同一进程：

- REST: `http://localhost:8080/api/v1/health`
- 控制台: `http://localhost:8080/console/`
- OpenAPI: `http://localhost:8080/swagger-ui`

## 4. REST 对账

```powershell
$base = 'http://localhost:8080/api/v1'
$created = Invoke-RestMethod -Method Post -Uri "$base/sessions" -ContentType 'application/json' -Body '{"profileName":"mock-agent","userId":"lesson27"}'
$sessionId = $created.data.id
Invoke-RestMethod -Method Post -Uri "$base/sessions/$sessionId/messages" -ContentType 'application/json' -Body '{"message":"记住：我喜欢喝美式咖啡"}'
Invoke-RestMethod -Uri "$base/sessions/$sessionId"
Invoke-RestMethod -Uri "$base/sessions"
Invoke-RestMethod -Uri "$base/memory?agentName=mock-agent"
Invoke-RestMethod -Uri "$base/tools?agentName=mock-agent"
```

期望：详情含 user / assistant tool-call / tool / assistant 四条消息；列表摘要 `messageCount=4`；记忆含“美式咖啡”；工具列表含 `save_memory`。

## 5. 控制台与审计对账

打开 `/console/` 的“会话”区域：最近会话应出现刚才记录；点击后显示同一 Session 详情。归档或非 Web 会话只读，页面不得生成模拟历史。

使用本地 SQLite 客户端按 `sessionId` 查询：模型成功 2 条、工具成功 1 条。数据库路径、用户工作区和密钥不得写入提交文件。

## 6. 真实模型验证（可选）

仅在本地提供真实 Provider key、允许访问天气 API 且 Sandbox 白名单已配置时运行 `HumanTriggerFlowIT` 或用真实 Agent 手动提问。该步骤验证真实协议与公网，不替代默认 Mock 门禁；未执行时在交付报告中标记“未验证”。

