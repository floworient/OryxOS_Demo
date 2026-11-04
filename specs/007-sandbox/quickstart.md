# 第 24 课 Sandbox 验证指南

状态：实现已完成，以下为可重复执行的验证指南；实际运行结果、失败修复过程与未验证项见 verification.md。

前提：仓库根目录、JDK 21、Maven Wrapper、当前 tasks 已批准且完成实现。无需云模型密钥/公网 Webhook。

## 聚焦测试

```powershell
.\mvnw.cmd -pl oryxos-tool,oryxos-boot -am '-Dtest=WhitelistSandboxTest,FileToolsTest,FileToolsCancellationTest,ShellToolsTest,HttpToolsTest,NotifyToolsTest,ToolConfigurationTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -pl oryxos-tool -am '-Dtest=SkillBindingResolverTest,ContextLoaderTest,McpConfigLoaderTest,McpClientServiceTest,DuckDuckGoInstantAnswerSearchProviderTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
.\mvnw.cmd -pl oryxos-boot -am '-Dit.test=SandboxAuditIT,ToolRegistryAuditIT,NotifyToolAuditIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
.\mvnw.cmd -pl oryxos-tool -am '-Dit.test=FileToolsSearchLimitsIT' '-Dfailsafe.failIfNoSpecifiedTests=false' verify
```

预期：配置/权限矩阵、公共 Skill、alias/URI、四工具拒绝零 IO、安全原因全部通过；IT 使用本地 SQLite/Flyway/JPA 与真实执行器，失败记录一条。链接测试缺少平台权限时单列未验证项，skipped 不等于覆盖。

## 真实本地链路

SandboxAuditIT 创建临时工作区、Agent、Registry、ProfileContext，允许 `safe-java` alias（当前 JDK 的绝对 java 路径）；经真实 ToolExecutor 调用未授权 alias。数据库恰好有一条 success=false、error_message=`Shell 命令被 Sandbox 拒绝` 的记录。授权配对用例启动安全 Java helper 一次并写标记文件；拒绝使用相同 helper/参数，标记不存在。此为本地链路验收，不代表云模型端到端。

四工具还验证文件原内容不变、HTTP 回环计数为零、Notify Adapter 未调用。使用非真实 canary 检查结果、日志和持久化记录中无秘密明文。

## 配置演示

在独立临时工作区经现有加载方式设置 oryxos.file.allowed-paths 为存在的绝对业务目录，读写成功。设置公共 Skill 祖先仍无法访问未绑定 Skill/写公共 Skill。空额外根仅保留内建权限。重启后更新，不修改用户 .oryxos 数据完成演示。

## 完整门禁

```powershell
.\mvnw.cmd clean verify
git diff --check
```

修改网站文档后在 website 执行现有 `npm run docs:build`；再核对课件交付物及已批准映射，更新 tasks/verification，执行 speckit-converge。converge 追加任务时必须实施并重新验证。
