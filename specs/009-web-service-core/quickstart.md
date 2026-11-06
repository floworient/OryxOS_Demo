# Quickstart: Web Service 核心能力验证

## 前置条件

- JDK 21；优先使用仓库 Maven Wrapper。
- 不需要真实 Provider key、外网、Webhook 或企业 IM。
- 保留工作区既有未提交修改，不执行 reset/checkout 或清理用户文件。

## 1. Session 身份与迁移

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-storage -am -Dtest=SessionTest,SessionGetOrCreateTest,SessionArchiveTest,SessionMigrationV6Test test
```

预期：活动身份复用；归档后新 ID；同一身份最多一个 active；旧数据库无损升级；归档历史可查但不可执行或复活。

## 2. Web Session 与一次性调用

```powershell
.\mvnw.cmd -pl oryxos-web -am -Dtest=SessionWebServiceTest,SessionApiControllerTest,AgentCallExecutorTest,AgentApiControllerTest test
```

预期：消息按 UTF-8 32 KiB 校验；最近 100 条安全投影；同 Session 并发写返回 409；不同 Session 独立；invoke 不返回 Session ID；超时映射为 504。

## 3. 查询端点与 OpenAPI

```powershell
.\mvnw.cmd -pl oryxos-web -am -Dtest=ProfileApiControllerTest,MemoryApiControllerTest,ToolApiControllerTest,SystemApiControllerTest,OpenApiContractTest test
```

预期：Profile/Tool/Provider 稳定排序；Agent 可见性生效；Memory 双路由一致；敏感字段不在 JSON；核心 10 端点出现在 OpenAPI。

## 4. Boot 真实统一链路

```powershell
.\mvnw.cmd -pl oryxos-boot -am "-Dtest=__NoUnit__" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=WebServiceAgentIT,WebServiceSessionRestartIT" "-Dfailsafe.failIfNoSpecifiedTests=false" verify
```

预期：HTTP 经真实 `AgentService -> ReActLoop -> ProviderService -> ToolExecutor`；SQLite 中 Session/LLM/Tool 审计可查询；归档跨重启保持且新代可创建。

## 5. 模块与全仓门禁

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-provider,oryxos-storage,oryxos-web,oryxos-boot -am test
.\mvnw.cmd test
.\mvnw.cmd clean package
npm --prefix website run docs:build
git diff --check
```

所有命令预期退出码 0。若全仓因既有无关改动失败，记录原始失败与聚焦测试结果，不弱化测试或改动无关文件掩盖问题。

## 6. 可选人工调用

在隔离工作区配置可用 Provider 后启动：

```powershell
.\mvnw.cmd -pl oryxos-boot -am spring-boot:run
```

按 contracts/web-api.md 顺序调用 health、info、profiles、sessions、messages、GET history、DELETE、invoke、memory、tools。真实 Provider/公网验证若未执行，必须在 `verification.md` 明确标记未验证，不能用桩结果冒充。

## 范围核对

本阶段完成：6 个 Controller、核心 10 端点、统一信封、Session 代次与归档、最近 100 条安全历史、32 KiB、60 秒/504、单进程并发保护、OpenAPI/CORS、统一 AgentService 链路。

本阶段不完成：认证、RBAC、限流、SSE/WebSocket、Webhook、Agent 动态管理、Memory 写操作、Tool 执行 API、审计查询、多实例锁或事务级外部副作用回滚。

