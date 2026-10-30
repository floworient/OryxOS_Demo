# Built-in Tool Input Contracts

所有 schema 均为 JSON object，`additionalProperties: false`，输入总大小上限 256 KiB。

| Tool | Required | Optional | Result summary |
| --- | --- | --- | --- |
| `read_file` | `path:string` | — | UTF-8 文本，有界 |
| `write_file` | `path:string`, `content:string` | — | 写入字节数 |
| `list_dir` | `path:string` | — | 稳定目录项列表 |
| `edit_file` | `path:string`, `old_text:string`, `new_text:string` | — | 唯一替换确认 |
| `grep` | `path:string`, `pattern:string` | — | `file:line:content`，最多 200 |
| `glob` | `path:string`, `pattern:string` | — | 稳定路径列表，最多 200 |
| `shell` | `executable:string`, `arguments:array<string>` | `working_directory:string`, `timeout_seconds:integer` | exit code、stdout、stderr、截断标记 |
| `http_get` | `url:string` | `headers:object<string,string>` | status 与有界 body |
| `http_post` | `url:string`, `body:string` | `headers:object<string,string>`, `content_type:string` | status 与有界 body |
| `ask_user` | `question:string` | — | 单行回答或不支持交互 |
| `web_search` | `query:string` | `limit:integer` | 标题/URL/摘要列表 |

## Security Rules

- 文件相对路径基于当前 Agent 目录；Agent 目录默认读写，绑定 Skill 只读。
- `edit_file` 的 `old_text` 必须恰好出现一次。
- Shell executable 是管理员配置 alias；arguments 从不经过 Shell 解释。
- HTTP 仅允许 Accept、Accept-Language、Content-Type 和 User-Agent headers；不自动跟随重定向，非 2xx 返回脱敏失败。
- grep 使用 RE2/J 线性时间正则语义，不支持反向引用和环视。
- ask_user 无人值守时 1 秒内失败且不读取 stdin。
- web_search 不得伪造结果；真实 Provider 请求先过 HTTP Sandbox。

