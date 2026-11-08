# 011 验证记录

- 导航测试初次运行因导航不存在失败，接入后通过。
- 管理视图测试初次运行因页面不存在失败，接入后通过。
- Sandbox MockMvc 测试初次运行因路由不存在失败。
- 执行决定：010 前端还在工作区，011 在同一工作区切至新功能分支继续，以保留未提交依赖。
- 评审复现长期记忆误报空工作区。补充失败重试测试，先失败后通过，并修正异步 Agent 跳转覆盖后续导航的问题。
- `npm.cmd --prefix frontend test` 通过 51 项测试，`npm.cmd --prefix frontend run build` 通过类型检查和生产构建。
- `./mvnw.cmd test` 通过全部 Reactor 项目。随后执行 `./mvnw.cmd -pl oryxos-boot -am "-Dtest=WhitelistSandboxTest,OpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" package`，通过沙箱和 Web 契约聚焦测试并生成执行 JAR。
- 新 JAR 在本机 8081 端口启动，控制台静态入口返回 HTTP 200，Sandbox 接口返回实际启用的天气域名以及预期的文件根数量和命令别名字段。
- 内置浏览器在导航阶段出错。七个入口、草稿保留、筛选竞态和失败重试已有组件测试，浏览器手工交互仍待验。
