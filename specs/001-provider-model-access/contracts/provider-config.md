# 契约：部署侧 Provider 配置

**Type**: 配置文件契约（YAML） | **Consumers**: 运营人员 / 部署流水线

本文件定义 `application.yaml` 中 `oryxos.providers` 的形状与校验规则。这是本阶段唯一的 Provider 对外配置接口；Agent 定义只能按名称引用，不能新增供应商（FR-001）。Agent frontmatter 的完整契约见 [agent-profile.md](./agent-profile.md)。

## 配置形状

```yaml
oryxos:
  workspace: ./.oryxos          # 工作区根目录，默认 ./.oryxos
  providers:
    - name: qwen                # 必填，全局唯一
      type: DASHSCOPE           # 必填，DASHSCOPE | OPENAI_COMPATIBLE
      model: qwen-plus          # 必填
      base-url: https://dashscope.aliyuncs.com   # 可选
      api-key: ${ALIYUN_API_KEY}                 # 必填，见下
    - name: gpt
      type: OPENAI_COMPATIBLE
      model: gpt-4o-mini
      base-url: https://api.openai.com
      api-key: ${OPENAI_API_KEY}
```

`providers` 是**列表**而不是映射。这样两个同名供应商能被检出并报错，而不是被映射结构静默覆盖。

## 字段规则

| 字段 | 规则 | 违反时的表现 |
| --- | --- | --- |
| `name` | 非空；不忽略大小写与首尾空白；精确匹配 | 启动失败，指出该条定义 |
| `type` | 必须是 `DASHSCOPE` 或 `OPENAI_COMPATIBLE` | 启动失败，指出该条定义 |
| `model` | 非空 | 启动失败，指出该条定义 |
| `base-url` | 可选；存在时必须是 `http` / `https` 绝对地址 | 启动失败，区分「格式错误」与「不可达」 |
| `api-key` | 见「凭据解析」 | 见「凭据解析」 |

## 凭据解析

1. 只接受两种写法：`${ENV_VAR}` 占位符，或部署侧本地配置中的字面量。
2. 占位符在启动期从进程环境变量解析。
3. 解析结果为空、为空白、或仍包含未展开的 `${`，一律视为**凭据非法**，错误码 `MISSING_CREDENTIAL`。
4. 凭据原文**永远不出现**在日志、异常消息、内部 `ProviderDefinition.toString()`、审计表或接口响应中。错误信息只报「供应商名 + 字段名」，例如：
   `供应商 [qwen] 的字段 [api-key] 未配置或未解析（环境变量 ALIYUN_API_KEY 为空）`
   注意：可以报**变量名**，不能报**变量值**。

## 校验时机与作用范围

| 情况 | 行为 |
| --- | --- |
| 两条定义重名 | 启动失败（`DUPLICATE_PROVIDER`），列出冲突名称 |
| 被任一 Agent 引用的供应商缺凭据 | 启动失败（`MISSING_CREDENTIAL`），指出供应商与字段，且不发出任何对外请求 |
| 未被任何 Agent 引用且凭据缺失 | 仅记录 WARN，不阻断启动；保留配置名称但不构造 `ChatModel`，实际调用时在网络请求前返回 `MISSING_CREDENTIAL` |
| Agent 引用不存在的 Provider | 该 Agent 从 `ProfileRegistry` 移除并记录 Agent/字段错误；其他合法 Agent 继续可用 |
| `providers` 为空或缺省 | 允许启动；任何按名称解析的调用都返回 `UNKNOWN_PROVIDER` |

连接与读取超时均使用实现内部固定的 60 秒，不属于 `oryxos.providers` 配置形状；本节不得新增 timeout 配置键。

`ProviderRegistry` 同时维护只读的已配置名称清单和凭据合法 Provider 的 `name → ChatModel` 显式映射。名称已配置但没有可调用模型时，`ProviderService` 必须先返回对应配置错误，不得把它误报成 `UNKNOWN_PROVIDER`，也不得临时向其他 Provider 回退。

## 示例：最小可用配置

```yaml
oryxos:
  providers:
    - name: qwen
      type: DASHSCOPE
      model: qwen-plus
      api-key: ${ALIYUN_API_KEY}
```

配合 `.oryxos/agents/weather/AGENT.md`：

```yaml
name: weather
description: 天气助手
identity:
  agent_name: 天气助手
  prompt: 提供简洁、准确的天气信息
provider:
  name: qwen
  model: qwen-plus
```

此时 `qwen` 被视为「被引用」，`ALIYUN_API_KEY` 必须存在，否则启动失败。
