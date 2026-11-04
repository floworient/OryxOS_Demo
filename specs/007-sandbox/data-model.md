# Sandbox 数据与状态

无新增数据库表、迁移或 Profile 字段。

## 配置快照

`oryxos.file.allowed-paths` → `List<String> allowedPaths`；默认空列表；null 列表按空集合处理，列表内 null/空白非法。根必须存在、绝对、普通目录，拒绝任一原生链接组件或真实路径偏移。重复真实根去重，getter/内部根为防御性快照。

WhitelistSandbox 内部持有既有 allowedDomains Set、allowedCommands Map、skillBindings，以及新增的 `List<Path> allowedFileRoots` 和 `Path publicSkillRoot`。公共根为工作区 skills 词法绝对路径，存在时叠加真实边界。绑定每次调用解析，不缓存。

旧构造器空额外根；无 resolver 时文件动作拒绝。FileAccessAction 仍为 Profile、Path、FileAccessMode，返回批准 Path；Shell 返回 executable Path；HTTP 返回批准 URI。

## 权限矩阵

| 目标 | READ/LIST/SEARCH | WRITE | WORKING_DIRECTORY |
| --- | --- | --- | --- |
| 当前 Agent | 允许 | 允许 | 仅目录允许 |
| 已绑定公共 Skill | 允许 | 拒绝 | 拒绝 |
| 未绑定 Skill/公共根本身 | 拒绝 | 拒绝 | 拒绝 |
| 管理员额外普通根 | 允许 | 允许 | 拒绝 |
| 其他路径 | 拒绝 | 拒绝 | 拒绝 |

公共根规则优先于覆盖它的管理员根。从宽泛父目录遇到公共根时剪枝；绑定 Skill 可通过其已知真实路径直接使用工具。

## 拒绝与审计

`输入解析 → 目标授权 → 批准目标 IO → 安全 ToolResult → 现有最终审计`。授权拒绝跳过 IO，产生 success=false、content=""、retryable=false 和固定类别 errorMessage。

统一异常 `SandboxViolationException extends SecurityException`；消息固定为文件/Shell/HTTP 拒绝之一，Notify 使用 HTTP 类别。异常不保留含原始目标的 cause。

审计沿用 ToolInvocationRecord/JPA：sessionId、toolName、原有脱敏参数、序列化结果、success、errorMessage、duration、timestamp；不新增另一条记录或事件表。

参数脱敏和 errorMessage 独立验收：Shell arguments 中的凭证必须在 inputJson 持久化前移除/脱敏，不能依赖安全错误文本。校验元数据 IO 与业务正文读取/变更、进程启动、外发分别计量；edit_file 拒绝写权限时正文读取次数应为零。
