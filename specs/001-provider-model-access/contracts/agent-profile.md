# 契约：AGENT.md frontmatter → Profile

**Type**: 工作区文件契约 | **Consumer**: `AgentLoader`

第 16 节采用当前架构修订：`.oryxos/agents/<name>/AGENT.md` 是 Agent 配置与指令的唯一真相源，不创建 `.oryxos/profiles/*.yaml`。

## Frontmatter 形状

```yaml
---
name: weather
description: 每日天气助手
identity:
  agent_name: 天气助手
  prompt: 提供准确、简洁的天气信息
provider:
  name: qwen
  model: qwen-plus
  temperature: 0.2
tools:
  - http_get
mcp_servers:
  - weather-mcp
channels:
  - name: cli
    config: {}
schedules:
  - key: daily-weather
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: 查询并发送今天的天气
bootstrap:
  - AGENTS.md
  - SOUL.md
  - USER.md
settings:
  max_iterations: 10
  max_history_turns: 20
---
这里是 Agent 的任务指令正文。本节只派生 frontmatter，正文由后续 `ContextLoader` 读取。
```

## 字段规则

| 字段 | 规则 |
| --- | --- |
| `name` | 必填，且必须与 `<name>` 目录名完全相等 |
| `description` | 必填、非空 |
| `identity.agent_name` / `identity.prompt` | 必填、非空 |
| `provider.name` / `provider.model` | 必填、非空；Provider 名称精确匹配 |
| `provider.temperature` | 可选；存在时范围为 0–2 |
| `tools` / `mcp_servers` | 可选字符串列表；缺省为空，去重后保留声明顺序 |
| `channels` | 可选；每项必须有非空 `name`，`config` 缺省为空 Map |
| `schedules` | 可选；`key` 在同一 Agent 内唯一，`cron`、`zone`、`message` 非空。本节只派生，不注册调度 |
| `bootstrap` | 可选相对路径列表；本节只派生，不校验文件存在性 |
| `settings` | 可选；缺省 `max_iterations=10`、`max_history_turns=20`，两者必须为正整数 |

以下字段明确非法：

- `skills`：Skill 绑定只来自 Agent 目录 `skills/<name>` 的受控相对软连接。
- `notify_channels`：通知渠道由 SQLite 全局注册表管理，不属于 Profile。

Persona 为后续课程扩展，第 16 节不提前增加相关字段或公共类型。

## 加载结果

- 工作区或 `agents/` 目录不存在：返回空结果，不抛异常。
- 每个直接子目录独立处理；缺少 `AGENT.md`、frontmatter 边界错误、YAML 错误或字段校验失败时，仅该 Agent 失败。
- 结构错误至少包含 Agent 目录、源文件、字段路径和已脱敏原因；未知 Provider 在后续 `ProviderStartupValidator` 阶段移出 Registry 并记录同等粒度错误。
- 合法 Agent 注册到 `ProfileRegistry`；失败 Agent 不注册。
- 同名 Agent 必须报错，不得按扫描顺序覆盖；本节不额外引入大小写归一化规则。

## YAML 安全约束

- 使用 SnakeYAML `SafeConstructor`，禁止任意类型构造。
- 禁止重复键。
- 显式设置 `maxAliasesForCollections=50`、`nestingDepthLimit=50`、`codePointLimit=3145728`；这些是 SnakeYAML 2.4 已核实默认安全上限，由边界测试锁定且不新增用户配置键。
- 只读取 `---` 之间的 frontmatter；正文不得被 YAML 解析器消费。
