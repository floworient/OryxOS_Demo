# Contract: Explicit Mock Provider

## Configuration

```yaml
oryxos:
  providers:
    - name: mock
      type: mock
      model: mock-memory
```

无需 `api-key` 或 `base-url`。Agent 必须显式声明同名 Provider，并把 `save_memory` 放入可见工具：

```yaml
provider:
  name: mock
  model: mock-memory
tools: [save_memory]
```

## Interaction

输入：`记住：我喜欢喝美式咖啡`

第一轮模型输出恰好一个 Function Calling：

- name: `save_memory`
- arguments: 包含 `content=我喜欢喝美式咖啡` 与 `scope=archival`

第二轮在收到匹配工具结果后返回固定非空答复。两轮均提供非零 token usage，供正式审计链记录。

## Safety and failure

- 未显式配置 `type: mock` 时不注册。
- 真实 Provider 缺凭证或调用失败时不得回退到 Mock。
- 输入格式、工具可见性或工具结果不符合脚本时明确失败。
- Mock 只证明运行时链路，不证明真实模型质量、协议兼容或公网可用性。

