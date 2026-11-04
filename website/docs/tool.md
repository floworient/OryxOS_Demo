# Tool System

OryxOS wraps built-ins, Java `@Tool` methods, and MCP tools as `OryxTool` instances in one `ToolRegistry`. `PromptBuilder` and `ToolExecutor` share the same visibility rules. Spring AI automatic tool execution is disabled; real calls go through `ToolExecutor` and are recorded in `tool_invocations`.

```java
interface OryxTool {
    String getName();
    String getDescription();
    String getInputSchema();
    ToolResult execute(String jsonInput);
}
```

Names are globally unique. Blank metadata, a non-object JSON schema, or a duplicate name fails registration rather than silently shadowing another tool.

## Available built-ins

This delivery adds 11 built-ins. The existing `notify` tool uses the same registry. Memory tools are outside this delivery; do not configure placeholder `save_memory` or `recall_memory` tools.

| Tool | Implemented behavior |
| --- | --- |
| `read_file` | Read bounded UTF-8 text from the Agent, approved ordinary roots, or a bound public Skill |
| `write_file` | Atomically write UTF-8 text inside the Agent or administrator-approved ordinary roots |
| `list_dir` | Deterministically list an approved directory, up to 200 entries |
| `edit_file` | Replace only when `old_text` occurs exactly once |
| `grep` | Linear-time RE2/J regex search with file, byte, and 200-result limits; no backreferences or lookaround |
| `glob` | Stable glob search, up to 200 results |
| `shell` | Run an approved executable alias with raw argv, bounded cwd, timeout, and output |
| `http_get` | Send one GET to an allow-listed host; never follow redirects automatically |
| `http_post` | Send one POST to an allow-listed host; never follow redirects automatically |
| `ask_user` | Available only in interactive CLI calls; unattended calls fail immediately without reading stdin |
| `web_search` | Return bounded title, URL, and snippet entries through a replaceable `SearchProvider` |
| `notify` | Send through a named notify channel; webhook is the currently implemented outbound adapter |

HTTP tools allow only Accept, Accept-Language, Content-Type, and User-Agent headers. Only 2xx responses succeed; 3xx/4xx/5xx return a redacted failure without response bodies. There is no redirect following or retry. Wrap authenticated enterprise APIs in an administrator-configured MCP server.

The default search adapter uses DuckDuckGo Instant Answer/Related Topics, not a complete web SERP. Its host must be explicitly allow-listed; otherwise search fails without fabricated results.

## Sandbox configuration

Example `.oryxos/application.yaml`:

```yaml
oryxos:
  file:
    allowed-paths: []
  http:
    allowed-domains:
      - api.duckduckgo.com
      - api.example.com
  tool:
    allowed-commands:
      node: C:/Program Files/nodejs/node.exe
      git: C:/Program Files/Git/cmd/git.exe
```

Every shell alias must map to an existing absolute regular file. Allow-listing an interpreter such as Node, Python, or PowerShell grants code-execution authority equivalent to the OryxOS process user. Passing argv directly is not file-system or network isolation.

Relative paths resolve from the current Agent directory, which is readable and writable. `oryxos.file.allowed-paths` grants read/write access to additional ordinary directories. Each root must be an existing absolute ordinary directory without native link components; blank, relative, missing, or linked roots fail startup. Configuration is a startup snapshot: restart after changing it. An empty list grants no additional roots but retains Agent access and bound-Skill reads. Empty HTTP and Shell allow lists deny their respective operations.

A public Skill is readable only through a validated `skills/*.link` binding and is always read-only. Granting its ancestor does not expose unbound Skills or allow Skill writes. Listing or searching from an ancestor prunes the public Skill root; a bound Skill remains directly readable/searchable at its context-provided real path. Extra file roots do not grant Shell working-directory access, which remains limited to the current Agent. Traversal, drive/UNC authorization bypasses, and native link escapes are rejected.

Necessary path, binding, and Skill-metadata checks may perform validation IO. A denied operation cannot read its target body, modify files, start a process, or send a request. `edit_file` approves read and write access before reading the body. Sandbox denials return fixed category messages, are not retryable, and use the existing single final audit record. Shell audit inputs retain only UTF-8 byte count and SHA-256, keeping arbitrary argv credentials out of storage. Error-message redaction and audit-input redaction are verified separately.

Lesson 24's `file.allowed_paths`, `shell.allowed_commands`, and `http.allowed_domains` map to `oryxos.file.allowed-paths`, `oryxos.tool.allowed-commands`, and `oryxos.http.allowed-domains`. Shell retains its alias-to-absolute-executable map; there is no second configuration source.

These controls are application-level allow lists, not container, MicroVM, or multi-tenant isolation.

## Java `@Tool`

Explicit `@Tool` methods on Spring beans are discovered at startup. Spring AI generates their schemas, and OryxOS registers the adapters with source `JAVA`. The Agent must still list the Java tool in `tools:` before the model can see or execute it.

## MCP

The current runtime supports `stdio` and `sse` transports. Example `.oryxos/mcp_servers.yaml`:

```yaml
servers:
  - name: github-local
    transport: stdio
    command: node
    args: ["C:/tools/github-mcp/server.js"]
    request_timeout: 30
    env:
      GITHUB_TOKEN: ${GITHUB_TOKEN}

  - name: internal-search
    transport: sse
    url: https://mcp.example.com/sse
    request_timeout: 30
    headers:
      Authorization: Bearer ${MCP_TOKEN}
```

`command` is an alias from `oryxos.tool.allowed-commands`, not an arbitrary executable. SSE URLs must pass the HTTP allow list. Missing environment values, unknown fields, duplicate servers, unauthorized commands/URLs, and duplicate tool names fail startup without exposing secrets. If configuration and authorization are valid but one server cannot connect or complete `tools/list`, OryxOS logs a redacted warning and skips only that server.

Agents select MCP sources with `mcp_servers:`. They do not add MCP tool names to `tools:`. MCP tool calls are not automatically retried by the adapter.

## Visibility and discovery

- `tools:` selects built-in and Java tools exactly.
- `mcp_servers:` selects all tools from the named MCP servers.
- A hallucinated unauthorized call is rejected again by `ToolExecutor` and audited as a failure.
- `oryxos tool list` shows the globally registered, name-sorted registry. It is not an Agent-specific effective-tool list.

Real MCP connectivity, public web search, and credentialed external calls require deployment-specific configuration and separate verification. Automated tests use temporary directories, loopback HTTP, controlled processes, and fake providers; they do not claim real external connectivity.
