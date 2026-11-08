# 010 Web 对话工作台验证

## 本轮结果

| 检查 | 结果 |
| --- | --- |
| `npm.cmd --prefix frontend test` | 8 个文件、47 项通过 |
| `npm.cmd --prefix frontend run build` | 类型检查和 Vite 生产构建通过 |
| 修复前 `./mvnw.cmd test` | 新增的 `ReActConfigurationTest.defaultPolicyLetsHttpGetReachItsDomainSandbox` 复现「工具安全检查拒绝」 |
| `./mvnw.cmd -pl oryxos-boot -am "-Dtest=ReActConfigurationTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | 2 项通过。内置 GET 访问白名单内的本地 HTTP 桩一次，白名单外的域名仍被沙箱拒绝 |

工具执行器的前置策略在没有自定义 Bean 时原本拒绝所有调用。本轮将默认策略的放行范围限定为内置 `http_get`，域名校验仍由现有沙箱执行。

修复后在 Windows PowerShell 重跑 `./mvnw.cmd test`，退出码 0，15 个 Reactor 项目全部通过，Boot 29 项测试无失败。

`./mvnw.cmd -pl oryxos-boot -am package -DskipTests` 退出码 0。重新打包后，Boot 的 `/console/index.html` 和 Vite 的 `/console/` 均返回 HTTP 200。Boot 的目录路径 `/console/` 返回 500，快速入门已改用实测可用的静态入口。

新 JAR 使用独立 SQLite 验收库。向真实 DeepSeek 天气 Agent 发送一条杭州天气请求，HTTP 返回 `SUCCESS`，历史包含 4 条消息和 `http_get` 工具标记，审计表确认工具调用成功。通过 Vite 代理可读取历史、归档会话并为同一身份创建不同 ID 的新会话。

## 待验证

- T042 的真实后端已通过。内置浏览器在导航时出错，页面点击、刷新恢复和新对话交互待验。
- T043 的浏览器错误态、空工作区和离线态。
