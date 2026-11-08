# 工作台演示工作区

把本目录的示例复制到仓库根目录的 `.oryxos/`（该目录已在 `.gitignore` 中忽略）：

```bash
mkdir -p .oryxos/agents
cp -r frontend/demo/agents/daily-weather .oryxos/agents/
cp -n frontend/demo/application.yaml.example .oryxos/application.yaml
```

前置条件：

- `config/application.yml` 已配置 `deepseek` Provider 与真实 API Key。
- 后端可访问 `api.open-meteo.com`。已有工作区请把该域名合并到 `.oryxos/application.yaml` 的 `oryxos.http.allowed-domains`。
- 修改工作区后需要重启后端，Agent 列表才会更新。

启动与验证步骤见 [quickstart.md](../../specs/010-web-chat-console/quickstart.md)。

`.oryxos/` 中会出现 `oryxos.db`、日志与本地配置，请不要提交到版本库。
