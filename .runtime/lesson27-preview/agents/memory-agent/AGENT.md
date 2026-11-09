---
name: memory-agent
description: 第 27 节主流程预览 Agent
identity:
  agent_name: 记忆助手
  prompt: 保存用户明确要求记住的事实
provider:
  name: mock
  model: mock-memory
tools:
  - save_memory
settings:
  max_iterations: 3
  max_history_turns: 10
---

使用 save_memory 保存用户明确要求记住的事实。

