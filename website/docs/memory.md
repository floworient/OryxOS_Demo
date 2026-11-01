# Memory

OryxOS gives agents memory across three layers. Each layer has a different scope, lifetime, and storage backend. `MemoryService` is the unified facade that `ReActLoop` and `PromptBuilder` talk to — they do not access layers directly.

## Three layers

| Layer | Storage | Scope | In core phase |
| --- | --- | --- | --- |
| Session memory | SQLite `sessions.messages_json` | Current conversation only | Yes |
| Long-term memory | Markdown (default), SQLite, or self-hosted Mem0 | Persists across sessions, per agent | Yes |
| Episodic memory | Vector database (planned) | Semantic search across past sessions | No |

**Session memory** is the conversation history — the list of messages accumulated in the current session. It is serialized as JSON and stored in SQLite. `PromptBuilder` injects the last `max_history_turns` turns into every prompt. It accumulates automatically; agents do not write to it explicitly.

**Long-term memory** is persisted by the selected backend. Agents write it explicitly with `save_memory` and recall it with `recall_memory`. It is reloaded for every prompt. The default Markdown backend uses a timestamped file; SQLite and Mem0 preserve the same facade contract.

**Episodic memory** is planned for the extension phase. It will index past conversations in a vector database and retrieve semantically relevant episodes at prompt-build time.

## Long-term memory is per-agent

Long-term memory is isolated by the validated `Profile.name`. The Markdown backend stores it at `.oryxos/agents/<name>/MEMORY.md`, alongside `AGENT.md`; the reserved global owner uses `.oryxos/memory/MEMORY.md`. Invalid agent names or unsafe paths fail loudly and never silently fall back to global memory.

`profile create` prepares both `AGENT.md` and the partitioned `MEMORY.md` before publishing the agent directory. On Windows, manually installed agents must also include this file before startup; saving without it fails explicitly. Filesystems without secure directory replacement update an open file handle and cannot guarantee atomic replacement during a process crash; use SQLite when transactional persistence is required.

### Timestamps and two sections

Every entry is written as a **timestamped** line with an internal stable-ID comment, `- [yyyy-MM-dd HH:mm:ss] <!-- oryxos-memory-id:<id> --> <content>`. Existing lines without the comment remain readable and receive persistent IDs on the next save. A `MEMORY.md` file is organized into two sections:

- `## 核心记忆` (**core**) — always injected in full, never truncated
- `## 归档记忆` (**archival**) — entries the agent deliberately saves via `save_memory`; truncated when it grows too large

`save_memory` targets one of these sections (`core` or `archival`). Recall and truncation operate on the **archival** section only; the core section is physically isolated and always kept intact.

### No automatic trigger footprint

OryxOS does **not** auto-append a “user message ⇒ reply” line to archival memory after every trigger. Failed replies that echo the question would otherwise dominate semantic recall and lock the model into repeating the failure. Execution trails already live in `tool_invocations` / `agent_executions`; lasting facts should be written explicitly with `save_memory`.

## Long-term memory operations

`LongTermMemoryStore` (default backend: `MarkdownMemoryStore`) exposes these operations:

| Method | Behavior |
| --- | --- |
| `append(content, scope)` | Appends a timestamped entry to the core or archival section |
| `load()` | Returns both sections; archival is truncated if oversized, core is returned in full |
| `recallByKeyword(keyword)` | Returns archival lines containing the keyword |
| bounded view | If archival exceeds 4,000 chars, returns only the newest complete entries that fit, without rewriting the source |

The 4,000-character limit prevents archival memory from consuming a disproportionate share of the context window. Oversized reads omit the oldest entries from the view without truncating or rewriting the source. Core is always returned in full.

The interface is designed with an upgrade path to vector search in mind. `recallByKeyword` can be replaced with a semantic search implementation without changing `MemoryService` or any caller.

## Pluggable backends

The long-term memory backend is pluggable via `oryxos.memory.backend`. Swapping it only changes the injected `LongTermMemoryStore`; the `MemoryService` facade and callers stay unchanged.

| `oryxos.memory.backend` | Store | Notes |
| --- | --- | --- |
| `markdown` (default) | `MarkdownMemoryStore` | Zero-dependency, human-readable, git-trackable Markdown file |
| `sqlite` | `JpaMemoryStore` | Entries stored in the Flyway V5 `memory_entries` table, reusing the existing SQLite database |
| `mem0` | `Mem0MemoryStore` | Delegates to self-hosted Mem0 OSS; startup read-only validates `/openapi.json` and partition support |

Mem0 writes use `infer=false` and explicitly carry the owner and `metadata.scope`. Unknown backends, non-positive limits, missing Mem0 URL/key, or missing partition semantics fail startup. `HYBRID_BUILTIN` is reserved for 015 and fails fast today; embeddings, RRF, and asynchronous vector indexing are not part of this delivery.

```yaml
oryxos:
  memory:
    backend: mem0 # markdown | sqlite | mem0
    archival-max-characters: 4000
    recall-max-results: 20
    entry-max-characters: 4000
    zone: ${TZ:Asia/Shanghai}
    mem0:
      base-url: ${MEM0_BASE_URL:}
      api-key: ${MEM0_API_KEY:}
      connect-timeout: 3s
      request-timeout: 10s
      max-response-bytes: 1048576
```

## save_memory and recall_memory tools

Agents interact with the selected long-term-memory backend through two built-in tools rather than manipulating backend storage directly.

**`save_memory`** — appends text to the current agent's `core` or `archival` partition in the selected backend. The agent decides what to save, where, and when. There is no automatic summarization in core phase; only the Markdown backend writes `MEMORY.md`.

```text
Tool: save_memory
Input: { "content": "User prefers concise responses, no bullet lists.", "scope": "archival" }
Effect: appends an entry to the agent's archival partition in the selected backend
```

**`recall_memory`** — searches the archival section by keyword and returns matching lines. Useful when the agent needs to check whether it already knows something before saving a duplicate.

```text
Tool: recall_memory
Input:  { "keyword": "prefers" }
Output: "- [2026-07-22 09:30:00] User prefers concise responses, no bullet lists."
```

Both tools go through `ToolExecutor` and are recorded in `tool_invocations` like any other tool call.

## Reading memory over REST

The admin console reads a specific agent's long-term memory through:

```text
GET /api/v1/agents/{name}/memory
```

The unified `{ code, message, data, timestamp }` response returns owner, backend, capabilities, all core entries, and the same bounded archival view used by prompts. It is not a raw backend response or an unbounded export. Unknown agents return 404; backend failures return a sanitized 503.

## How memory is injected into prompts

`PromptBuilder` calls `MemoryService.load(session, profile)` on every loop iteration and inserts the result as the second block of the system prompt, after the Agent identity and Bootstrap files:

```text
[1] system prompt  (AGENT.md body + bootstrap)
[2] Long-term memory  ← injected here: core in full + archival truncated at 4 000 chars
[3] conversation history (last N turns)
[4] available tools (JSON Schema)
```

The memory block is always present, even when the selected backend has no long-term memory.

## MEMORY.md vs USER.md

These two files look similar but serve opposite purposes:

| | `MEMORY.md` | `USER.md` |
| --- | --- | --- |
| Who writes it | The agent (via `save_memory`) | The user (manually) |
| OryxOS behavior | Read + write | Read only |
| Contents | Preferences, facts, and long-lived context explicitly saved by the agent | User's initial preferences and context |
| Resets | Never (append-only; archival truncation only when too large) | Never (managed by user) |
| Location | `.oryxos/agents/<name>/MEMORY.md` (per-agent) | `.oryxos/USER.md` |

`USER.md` is part of the Bootstrap group injected at position [1] in the prompt. `MEMORY.md` is injected at position [2]. Both are visible to the agent; only `MEMORY.md` can be modified by the agent.

## What is planned

- **Episodic memory** — index full session transcripts in a vector database; retrieve the top-k relevant episodes at prompt-build time
- **015 hybrid retrieval** — add keyword, semantic, and recency fusion over archival memory; this release only reserves the capability
- **Memory summarization** — automatically compress old entries before truncation to preserve more signal in fewer characters
