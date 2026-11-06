# REST API

OryxOS Web Service exposes a JSON REST API under `/api/v1`. The current core release targets trusted enterprise networks and does not include authentication, authorization, or tenant isolation.

```bash
java -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar serve --port 8080
```

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Response envelope

Successes and errors use the same envelope:

```json
{
  "code": "SUCCESS",
  "message": "success",
  "data": {},
  "timestamp": "2026-11-06T08:30:00Z"
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `code` | string | `SUCCESS` on success; a stable error code otherwise |
| `message` | string | Short client-facing message without internal exceptions or secrets |
| `data` | any/null | Endpoint payload; `null` on errors |
| `timestamp` | string | ISO-8601 UTC timestamp |

Message bodies must not exceed 32,768 bytes after UTF-8 encoding. Synchronous Agent calls have a 60-second deadline.

## Core endpoints

### Create or reuse a continuous session

`POST /api/v1/sessions`

```json
{
  "profileName": "assistant",
  "userId": "customer-42"
}
```

Only the current active session is reused for the same `profileName`, `userId`, and `web` channel. A new session returns `201 Created`; a reused session returns `200 OK`. Both set `Location: /api/v1/sessions/{id}`. IDs are random UUIDs. Creating after archival produces a new generation with a new UUID instead of reviving the archived session.

### Send a message to a continuous session

`POST /api/v1/sessions/{id}/messages`

```json
{ "message": "Summarize today's work" }
```

The response contains `sessionId` and `reply`. Within one JVM, concurrent message or archival work on the same session returns `409 SESSION_BUSY`. Sessions created by channels other than `web` are not writable through this endpoint.

### Get a session

`GET /api/v1/sessions/{id}`

Returns identity, channel, status, timestamps, and a safe projection of message history. At most the latest 100 messages are returned and `historyTruncated` reports truncation. Tool arguments and tool-result bodies are not exposed.

### Archive a session

`DELETE /api/v1/sessions/{id}`

Archives an active `web` session without physically deleting it. Success returns `ARCHIVED`; an archived or unknown ID returns `404 SESSION_NOT_FOUND`.

### Invoke an Agent without a reusable session

`POST /api/v1/agents/{name}/invoke`

```json
{ "message": "Generate a daily report" }
```

Each invocation creates an independent internal `web-invoke` session for auditability. The response exposes only `reply`, never the internal session ID, and subsequent invocations do not reuse it.

### List Profiles

`GET /api/v1/profiles`

Returns registered Profile names, descriptions, Agent, Provider, model, and configured Tool/MCP metadata without secrets.

### Read Agent memory

`GET /api/v1/memory?agentName={name}`

Returns the Agent's core/archival snapshot and backend capabilities. `GET /api/v1/agents/{name}/memory` remains as a compatibility route, but is not the canonical new path.

### List Tools

- `GET /api/v1/tools`
- `GET /api/v1/tools?agentName={name}`

Without a parameter, returns global Tool registration metadata. With an Agent name, filters using the Profile's visibility. Executable code, parameter values, and credentials are never returned.

### Health

`GET /api/v1/health`

Returns `{ "status": "UP" }` while the process can respond. It is not a deep probe of external Providers, database capacity, or third-party networks.

### System information

`GET /api/v1/info`

Returns the application version, Java version, Profile/Tool counts, and registered Provider names without Provider configuration or secrets.

## Notify channels

Notify channels are managed dynamically through `/api/v1/notify-channels` and persisted in SQLite's `notify_channels` table. Only the `webhook` outbound adapter is supported. Returned `url` and sensitive `config` values are masked as `****` plus their last four characters, while `${ENV_VAR}` placeholders stay visible. On update, `null`, a blank value, or the current mask preserves the existing secret. This version does not claim database-field encryption; prefer `${ENV_VAR}` in production.

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/api/v1/notify-channels` | Creates a channel; returns `201` with `Location` |
| GET | `/api/v1/notify-channels` | Lists all channels |
| GET | `/api/v1/notify-channels/{name}` | Reads one channel |
| PUT | `/api/v1/notify-channels/{name}` | Updates a channel; masked values preserve the stored secret |
| DELETE | `/api/v1/notify-channels/{name}` | Deletes a channel; returns `204` |

Requests carry `name`, `type`, `url`, `description`, and `config`; responses also include `createdAt` and `updatedAt`. Errors use `NOTIFY_CHANNEL_NOT_FOUND` (404) and `NOTIFY_CHANNEL_CONFLICT` (409).

## Status and error codes

| HTTP | Common `code` | Meaning |
| --- | --- | --- |
| 400 | `VALIDATION_ERROR` | Missing, blank, oversized, or malformed input |
| 404 | `AGENT_NOT_FOUND`, `SESSION_NOT_FOUND` | Agent or session does not exist |
| 409 | `SESSION_BUSY`, `SESSION_CHANNEL_NOT_WRITABLE` | Session conflict or channel is not Web-writable |
| 500 | `STORAGE_ERROR`, `INTERNAL_ERROR` | Storage or unexpected internal failure |
| 503 | `PROVIDER_UNAVAILABLE`, `MEMORY_UNAVAILABLE` | Model or memory service is unavailable |
| 504 | `AGENT_TIMEOUT` | Synchronous Agent call exceeded 60 seconds |

## Current limitations and deployment boundary

- `/api/**` currently permits cross-origin requests and has no authentication, RBAC, rate limiting, or tenant isolation. Deploy only behind gateway and network controls on a trusted internal network.
- Session locking and active-session create-or-reuse semantics are single-JVM guarantees, not a multi-instance distributed lock.
- The 60-second timeout is a caller wait limit with best-effort cancellation. It does not roll back Tool side effects or audit records already produced.
- Inbound Webhook, SSE, WebSocket, dynamic Agent management, and schedule-management REST APIs are not implemented.
- Automated tests use controlled Providers, Tools, and local SQLite. Real model credentials and public-network dependencies require separate validation in the target environment.
