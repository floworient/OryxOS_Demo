# CLI Reference

OryxOS currently exposes 12 business commands. The only complete executable artifact is the Boot exec JAR:

```bash
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar --help
```

The examples below use `oryxos` as shorthand. Every command supports `-h/--help`; the root also supports `-V/--version`.

## Workspace

Every command resolves the workspace in this order: `-Doryxos.root=<path>` → `ORYXOS_ROOT` → `.oryxos/` under the current directory.

`init`, `status`, and the four `profile` commands are light commands: they do not start Spring and need no model credentials. `init` safely fills in `agents/`, `skills/`, `output/`, `memory/`, `sessions/`, `logs/`, the three bootstrap files, and `memory/MEMORY.md`. Existing content is never overwritten and Flyway remains responsible for the database.

```bash
oryxos init
oryxos status
```

## The 12 commands

| Command | Behavior |
| --- | --- |
| `oryxos init` | Safely and idempotently initialize the workspace |
| `oryxos status` | Report root, initialization, agent count, and database state |
| `oryxos chat [--profile <name>]` | Multi-turn chat through `AgentService`; defaults to `default`; `/quit` or EOF exits |
| `oryxos serve [--port <1..65535>]` | Start the existing HTTP service; default port 8080 |
| `oryxos gateway` | Start the aggregate runtime and report components actually wired |
| `oryxos profile list` | List active agents in name order |
| `oryxos profile create <name>` | Create a minimal `AGENT.md` accepted by `AgentLoader` |
| `oryxos profile show <name>` | Print `AGENT.md` unchanged |
| `oryxos profile delete <name>` | Move the entire agent directory into `archive/`; no permanent deletion |
| `oryxos provider list` | Print `NAME/TYPE/MODEL/BASE_URL` without credentials |
| `oryxos tool list` | Print names and descriptions from the real `runtimeTools` map |
| `oryxos session list` | Print session summaries, newest activity first |

`profile`, `provider`, `tool`, and `session` are help groups, not additional business commands.

## Profile safety and archival

The only active definition is `<workspace>/agents/<name>/AGENT.md`. Names are 1–64 ASCII letters, digits, dots, underscores, or hyphens and must start with a letter or digit. Path traversal, absolute paths, Windows device names, and operating-system links are rejected.

```bash
oryxos profile create daily-weather
oryxos profile show daily-weather
oryxos profile delete daily-weather
# Prints a path such as archive/daily-weather-1793164800000
```

## Provider configuration and inspection

`provider list` uses a configuration-only context and does not construct a `ChatModel`, so inspection does not require credentials. Declarations may be placed in `application.yaml` at the workspace root:

```yaml
oryxos:
  providers:
    - name: qwen
      type: DASHSCOPE
      model: qwen-plus
      base-url: https://dashscope.aliyuncs.com
      api-key: ${ALIYUN_API_KEY:}
```

Output never includes `api-key` or its expanded value. `tool list` and `session list` lazily start the required runtime/storage context and close it after the query.

## Runtime boundaries

`serve` and `gateway` reuse the existing Spring Boot, Web, Session, and audit chain. The Feishu, WeCom, and DingTalk modules currently provide module boundaries only and have no runnable `ChannelAdapter` beans, so `gateway` does not claim those future channels are available. A shutdown hook closes contexts on `Ctrl-C`.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Success, including an empty list |
| `1` | Runtime, configuration, IO, or external-resource failure |
| `2` | Unknown command, missing argument, or invalid argument |

Normal results go to stdout; errors and usage failures go to stderr. Exception messages are redacted and stack traces are not printed by default.
