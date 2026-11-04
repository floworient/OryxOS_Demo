# Sandbox 兼容契约

## 接口与构造器

```java
URI Sandbox.enforce(HttpRequestAction action);
Path Sandbox.enforce(FileAccessAction action);
Path Sandbox.enforce(ShellExecuteAction action);

WhitelistSandbox(Set<String> allowedDomains);
WhitelistSandbox(Set<String> allowedDomains, Map<String, Path> allowedCommands,
    SkillBindingResolver skillBindings);
WhitelistSandbox(Set<String> allowedDomains, Map<String, Path> allowedCommands,
    SkillBindingResolver skillBindings, List<Path> allowedFileRoots, Path publicSkillRoot);

SandboxViolationException(String message); // extends SecurityException
```

无 ActionType 兼容枚举或字符串 action 旁路。旧构造器默认无额外根；Boot 使用完整构造器。旧 3 参数构造器在文件授权时从已经 resolver 校验的 Profile sourcePath 确定工作区 skills 边界；Boot 显式传入。批准值为唯一 IO 目标；工具输入 schema 不变。

## 配置

```yaml
oryxos:
  file:
    allowed-paths: []
  tool:
    allowed-commands: {}
  http:
    allowed-domains: []
```

新增 Boot 类 `io.oryxos.boot.FileSandboxProperties`，方法 `List<String> getAllowedPaths()` / `void setAllowedPaths(List<String>)`。校验与默认值见 data-model；配置错误使用固定 IllegalArgumentException，不附原始路径或外部 cause。运行期授权拒绝使用统一异常。启动快照，重启才更新。

课件 file.allowed_paths 映射到 oryxos.file.allowed-paths，shell.allowed_commands 映射到现有 alias Map，http.allowed_domains 映射到现有 HTTP Set；不支持两套前缀并存。

## 固定失败结果

| 操作 | errorMessage |
| --- | --- |
| 文件或 Shell 工作目录拒绝 | 文件访问被 Sandbox 拒绝 |
| executable alias 拒绝 | Shell 命令被 Sandbox 拒绝 |
| HTTP/通知目标拒绝 | HTTP 请求被 Sandbox 拒绝 |

共同字段：success=false、content=""、retryable=false。异常只允许固定类别文本，不附目标或 cause。Tool 层仅在已知异常分支使用安全消息，其他异常保留现有兜底。ToolExecutor 写入一次最终审计，不添加专用审计/重试。

错误消息与审计 input_json 分别验收：Shell arguments 中的凭证不得明文持久化，即使调用被拒绝。已有审计参数脱敏允许单独修正，不改变写入机制。

## 文件规则

权限矩阵见 data-model。新增根不改变相对路径基准、Skill 只读与 cwd 限制。验证最近存在父目录不意味着自动创建目录，write_file 仍要求父目录存在。原子替换与 parent realPath/fileKey 复验保留。直接未授权目标返回失败；枚举遇到未授权项过滤/剪枝，不输出名称、元数据或内容。

零业务 IO 允许必要的路径/绑定/Skill 元数据校验，但禁止被拒绝操作读取目标正文或产生目标副作用。edit_file 必须先通过 READ 与 WRITE 授权再读取正文；单次成功可包含其必要的读取和写入，不将“一次调用”误解为“一次底层文件系统调用”。

NotifyTargetResolver 已批准 URL 继续传给 Adapter；搜索/MCP 使用同一 Sandbox。未授权 MCP 启动失败，授权后纯远端连接故障仍可降级；无新增 REST 端点/状态码/响应格式。

## 审查修复补充

HTTP 规范化仅调整 scheme/host，路径和查询保留原始 percent 编码、编码大小写与空查询；禁止通过解码再构造把 %2F/%26/%3D 变为分隔符。

文件工具在执行入口、授权前后、遍历/搜索循环与业务读写前检查线程中断，返回安全的不可重试取消失败，保持中断标志。ToolExecutor 超时后的既有失败审计仍只写一次。在途操作系统调用可能无法被立即抢占；必须在它返回后停止后续授权和业务 IO，而不宣称强制终止任意文件系统调用。
