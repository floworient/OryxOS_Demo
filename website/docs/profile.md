# Profile Reference (`AGENT.md` frontmatter)

One `.oryxos/agents/<name>/` directory defines one Agent and must directly contain a regular `AGENT.md` file. Its YAML frontmatter derives the runtime `Profile`; the Markdown body after the closing boundary becomes task instructions in the system prompt. Agent and Skill directories are context sources, not Tools.

## Complete example

`.oryxos/agents/ops-agent/AGENT.md`:

```markdown
---
name: ops-agent
description: Operations-check assistant
identity:
  agent_name: Ops Assistant
  prompt: Be cautious and report only conclusions supported by evidence.
provider:
  name: deepseek
  model: deepseek-chat
  temperature: 0.2
tools:
  - read_file
  - grep
  - shell
  - http_get
  - ask_user
mcp_servers:
  - github-local
channels:
  - name: cli
    config: {}
bootstrap:
  - AGENTS.md
schedules:
  - key: morning-check
    cron: "0 0 8 * * *"
    zone: Asia/Shanghai
    message: Run the morning check.
settings:
  max_iterations: 10
  max_history_turns: 20
---

Inspect configuration and logs in the Agent directory. Use only approved tools for external data.
```

`name` must exactly match the directory name. Native symlinks for the Agent directory, `AGENT.md`, or public Skill paths are not treated as a compatibility mechanism.

## Fields

| Field | Required | Current contract |
| --- | --- | --- |
| `name` | yes | Non-blank and identical to the directory name |
| `description` | yes | Non-blank human-readable description |
| `identity` | yes | Non-blank `agent_name` and `prompt` |
| `provider` | yes | `name` and `model`; optional `temperature` from 0 through 2 |
| `tools` | no | Built-in and Java tool names; defaults to empty |
| `mcp_servers` | no | MCP server names; defaults to empty |
| `channels` | no | Each entry has `name`; `config` is an optional object |
| `bootstrap` | no | Ordered file-path list |
| `schedules` | no | Each entry has `key`, `cron`, `zone`, and `message`; keys are unique within the Agent |
| `settings` | no | Positive `max_iterations` (default 10) and `max_history_turns` (default 20) |

Provider credentials are not Agent frontmatter. Configure providers through workspace `application.yaml` plus environment variables; do not put API keys in `AGENT.md`.

## Scheduler behavior and boundaries

At application startup, OryxOS dynamically registers `schedules` from every loaded Profile. `cron` uses Spring's six-field format, and `zone` must be an explicit value accepted by Java `ZoneId` (an IANA region ID such as `Asia/Shanghai` is recommended; a fixed offset does not follow daylight-saving changes). An invalid cron or zone, a blank field, or a duplicate `key` within one Agent rejects that entire Agent during loading.

Each rule has a stable identity derived only from `(Agent name, schedule key)`. Changing its cron, zone, or message does not change that identity. On a trigger, the runtime obtains a deterministic Session with `channel=scheduler`, `user=scheduler`, and the target Profile name, then calls the same `AgentService` chain used by CLI and HTTP. It does not create a separate ReAct or Tool execution path.

All rules for one Profile share a single-process lock. If one execution is still running, a new trigger is skipped immediately, without queuing or a delayed catch-up run; different Profiles can execute independently. A failed execution is isolated and records only a safe error type. It is not retried automatically.

The current scheduler Store is an in-memory, thread-safe implementation. Active definitions and execution history do not survive a restart; definitions are loaded again from `AGENT.md`. This phase has no scheduler management REST/CLI, runtime cron changes, enable/disable or run-now controls, failure retries, or multi-instance coordination. With multiple instances, the same rule may run once on each instance.

## Tool visibility

`tools:` and `mcp_servers:` are separate authorization paths:

- Built-in and Java `@Tool` entries must be listed by tool name in `tools:`.
- MCP tools are authorized by source server. Put the server name in `mcp_servers:`; do not repeat every MCP tool name in `tools:`.
- Unlisted tools do not reach the model. A direct hallucinated call is rejected again by `ToolExecutor` and audited as a failure.
- `oryxos tool list` shows the global registry, not one Agent's effective subset.

The newly delivered built-ins are `read_file`, `write_file`, `list_dir`, `edit_file`, `grep`, `glob`, `shell`, `http_get`, `http_post`, `ask_user`, and `web_search`. `notify` already exists. This Tool delivery does not provide `save_memory` or `recall_memory`.

## Skill bindings

Public Skills live at `.oryxos/skills/<name>/`. An Agent selects visible Skills with ordinary UTF-8 `.link` files:

```text
.oryxos/
├── agents/ops-agent/
│   ├── AGENT.md
│   └── skills/deploy.link
└── skills/deploy/SKILL.md
```

`deploy.link` contains exactly one path relative to its parent directory:

```text
../../../skills/deploy
```

The real target must remain under this workspace's public `skills/` root. Absolute, drive, UNC, broken, and native-symlink escape paths are rejected. Bindings are re-resolved for every context build and file access. A bound Skill is readable but never writable. The legacy frontmatter `skills:` field is rejected.

## Interaction and external-service limits

`ask_user` works only during interactive CLI calls. HTTP, scheduler, and other unattended entries fail quickly without reading process stdin. `web_search`, HTTP, notify, and remote MCP remain subject to administrator allow lists and external availability; listing a name in the Profile never bypasses the Sandbox.
