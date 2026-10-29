# Notify 渠道管理 Quickstart

## 前置条件

- JDK 21。
- 使用仓库 `mvnw.cmd`。
- 不需要真实 Provider、外网或 Webhook 密钥完成自动验收。

## 1. 聚焦验证

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-storage,oryxos-tool,oryxos-web,oryxos-boot -am test
```

预期：渠道领域、Flyway/JPA、Sandbox、Adapter、Tool、API 和 Boot 集成测试全部通过。

## 2. 启动本地接收端

自动化测试使用 JDK `HttpServer` 随机回环端口。人工本地验证可启动只记录 method、path 和 JSON body 的临时 HTTP 服务；禁止使用真实企业 Webhook。

设置允许域名和占位 URL：

```powershell
$env:TEAM_WEBHOOK_URL='http://127.0.0.1:18080/hook'
$env:ORYXOS_HTTP_ALLOWED_DOMAINS='127.0.0.1'
```

## 3. 创建渠道

启动服务后：

```powershell
Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/api/v1/notify-channels' -ContentType 'application/json' -Body '{"name":"team-webhook","type":"webhook","url":"${TEAM_WEBHOOK_URL}","description":"本地验收","config":{}}'
```

预期：HTTP 201；响应显示 `${TEAM_WEBHOOK_URL}`，不出现解析后的回环 URL。

## 4. 查询与更新

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/v1/notify-channels'
Invoke-RestMethod -Method Put -Uri 'http://localhost:8080/api/v1/notify-channels/team-webhook' -ContentType 'application/json' -Body '{"type":"webhook","url":"","description":"更新描述","config":null}'
```

预期：顺序稳定；空 URL 保留原配置；响应不泄漏真实值。

## 5. Agent 调用

Agent 的 `AGENT.md` 仅保留：

```yaml
tools: [notify]
```

正文写“发送到名为 team-webhook 的通知渠道”。不得增加 `notify_channels` frontmatter。模型产生：

```json
{"content":"测试消息","channel":"team-webhook"}
```

预期：本地接收端恰好收到一次 POST，body 为 `{"content":"测试消息"}`；`tool_invocations` 有一条成功记录。省略 `channel` 时使用最早创建渠道。

## 6. 安全失败验证

- 移除 `127.0.0.1` 白名单后重试：零 HTTP 请求，一条失败审计。
- 删除 `TEAM_WEBHOOK_URL` 后重试：零 HTTP 请求，错误不包含变量值或 URL。
- 假服务返回 500：Tool 失败，不静默成功，不自动重试。

## 7. 全量门禁

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
git diff --check
```

真实企业 Webhook 验收为可选人工步骤；若未提供密钥或网络，交付报告明确标记“未验证”，不得以本地回环结果冒充。
