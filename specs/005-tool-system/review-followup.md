# 独立审查修复跟踪

审查基线：`05d974e..a4a5bc2`。审查方式：独立只读静态追踪，未调用真实外部服务。

## 已实现

- ToolExecutor 审计输入递归脱敏 headers、URL、token、secret、password、cookie、api-key 等字段；非法 JSON 不记录原文。
- 重试调用保留最多四次尝试和退避，每个模型工具调用只写一条最终审计。
- HTTP 仅允许 Accept、Accept-Language、Content-Type、User-Agent；非 2xx 返回脱敏失败。
- Java Adapter 增加输入上限、required/类型/数组/嵌套对象校验、拒绝未知字段并支持 Map 的 additionalProperties；拒绝 varargs、泛型方法、Future/Stream 返回签名。
- MCP Adapter 增加输入与结果上限；null 结果返回失败。
- MCP loader 校验 header 语法、控制字符、重复及受限名称，并校验 env key/NUL。
- web_search 的截断标记只留在 JSON，不再用作 retryable。
- Shell 异常/中断时先终止进程及关闭管道，再停止读取线程；新增中断清理回归。
- grep 使用 RE2/J 线性时间正则引擎，新增嵌套量词回归；不支持反向引用及环视。
- 写入前及原子替换前复验父目录 realPath/fileKey，发现身份变化即失败。
- 外部工具审计参数只记录字节数及 SHA-256 摘要；退避失败仍写最终审计。
- MCP 本地 IllegalArgumentException 配置错误阻止启动，不降级成 server 不可用。

## 交付边界

- 文件身份复验属于应用层竞态缓解，不承诺对抗能恶意并发修改同目录的本地进程；Windows/fileKey=null 时尤其不能视为容器级隔离。
- Java 参数支持生成 schema 中的 object、array、string、integer、number、boolean、null 及本地引用；其他类型结构默认拒绝。
- 管理员额外文件根按已批准范围保持为空；不宣称已具备额外根配置能力。

## 验证限制

修复阶段全仓 clean verify 退出 0；最后两项复审修复及 Map schema 兼容调整后，全仓 verify 再次退出 0（2026-10-30 16:09）。网站 docs:build、git diff --check 通过，凭证模式扫描未命中。
真实 MCP stdio/SSE、DuckDuckGo、DNS 重绑定、操作系统级进程隔离尚未验证。
