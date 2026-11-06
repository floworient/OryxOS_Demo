# Verification: Web Service 核心能力

- 验证日期：2026-11-06
- 分支：`codex/web-service-core`
- 工作区：`E:/Program/OryxOS_Demo/.worktrees/web-service-core`

## TDD 证据

实现按计划分六批完成。每批先运行只包含新增目标测试的命令，确认失败原因是目标类型、路由、迁移或契约尚不存在，再实现最小行为并运行 GREEN。最终 GREEN 输出保存在 `.superpowers/sdd/2026-11-04-web-service-core/task-1-tests.log` 至 `task-5-tests.log`。

| 批次 | RED 命令与预期失败 | GREEN 证据 |
| --- | --- | --- |
| Session 与 V6 | `-pl oryxos-core -Dtest=SessionTest test`：随机代次/恢复构造器缺失；`-pl oryxos-storage -am -Dtest=SessionArchiveTest,SessionMigrationV6Test,SessionGetOrCreateTest test`：archive/details/V6 缺失 | core 6 tests；storage 6 tests；exit 0 |
| Provider/Web 执行基座 | `-pl oryxos-provider,oryxos-web -am -Dtest=ProviderRoutingTest,Utf8MessageValidatorTest,AgentCallExecutorTest test`：目标类型缺失 | Provider/Web 聚焦 13 tests；exit 0 |
| 连续 Session | `-pl oryxos-web -am -Dtest=SessionWebServiceTest test`：service/DTO 缺失；随后 Controller/Boot IT 先失败于路由或装配缺失 | Web/Storage/Boot 模块回归通过；重启 IT 通过 |
| 无状态 invoke | `-pl oryxos-web -am -Dtest=AgentInvocationWebServiceTest test`：service 缺失；Controller/Boot IT 先失败于目标路由 | `WebServiceAgentIT` 通过，两个独立 Session 及 LLM/Tool 审计可查 |
| 查询与 OpenAPI | Profile/Memory/Tool/System/OpenAPI 测试先失败于 Controller、规范路由或 CORS 缺失 | Web 全测 39 tests；exit 0 |
| 收敛补强 | OpenAPI schema 测试先证明成功响应媒体类型为 `*/*`，不满足 JSON 契约 | 六个核心 Controller 显式发布 `application/json`；`OpenApiContractTest` 2 tests 通过 |

收敛期间还真实触发并修复了两个质量门：Checkstyle 的测试方法空行规则，以及 SpotBugs 对 Spring 注入 Registry 和明确开放 CORS 的误报。SpotBugs 排除仅匹配具体类和具体 bug pattern，并记录了设计理由。

## Quickstart 聚焦验证

以下命令均在最新源码上运行：

| 命令 | 结果 |
| --- | --- |
| `./mvnw.cmd -pl oryxos-core,oryxos-storage -am "-Dtest=SessionTest,SessionGetOrCreateTest,SessionArchiveTest,SessionMigrationV6Test" "-Dsurefire.failIfNoSpecifiedTests=false" test` | exit 0；4 个存储测试及 6 个 core Session 测试通过；V5 fixture 成功迁移至 V6 |
| `./mvnw.cmd -pl oryxos-web -am "-Dtest=SessionWebServiceTest,SessionApiControllerTest,AgentCallExecutorTest,AgentApiControllerTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | exit 0；13 tests，通过 32 KiB、并发、超时和响应契约 |
| `./mvnw.cmd -pl oryxos-web -am "-Dtest=ProfileApiControllerTest,MemoryApiControllerTest,ToolApiControllerTest,SystemApiControllerTest,OpenApiContractTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` | exit 0；10 tests；10 个核心操作及统一 JSON 信封 schema 通过 |
| `./mvnw.cmd -pl oryxos-boot "-Dtest=__NoUnit__" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dit.test=WebServiceAgentIT,WebServiceSessionRestartIT" verify` | exit 0；2 个真实 HTTP/SQLite IT 通过；Checkstyle、SpotBugs、JaCoCo 通过 |
| `./mvnw.cmd -pl oryxos-web -am verify` | exit 0；39 个 Web tests、131 个 core tests；Checkstyle 0、SpotBugs 0、覆盖率门通过 |

Boot IT 使用真实 Spring Boot HTTP Server、`AgentService -> ReActLoop -> ProviderService -> ToolExecutor` 调用链和临时 SQLite。Provider 与 Tool 是可控桩，但 Session、Flyway、JPA、LLM/Tool audit 均为真实实现。`WebServiceSessionRestartIT` 关闭并重建 ApplicationContext 后仍能读取归档 Session，并为相同身份创建新一代 UUID。

## 全仓与交付门禁

| 命令 | 结果 |
| --- | --- |
| `./mvnw.cmd -pl oryxos-core,oryxos-provider,oryxos-storage,oryxos-web,oryxos-boot -am test` | exit 0；15/15 Reactor 模块成功；1:58 |
| `./mvnw.cmd test` | exit 0；15/15 Reactor 模块成功；2:28 |
| `./mvnw.cmd clean package` | exit 0；15/15 Reactor 模块成功；生成 Boot exec JAR；2:55。收敛修订后再次运行的结果见下方最终检查 |
| `npm --prefix website ci` | exit 0；按 lockfile 安装 127 个包 |
| `npm --prefix website run docs:build` | 首次在并行创建输出目录时遇到 Windows `EPERM`；不改权限直接重试 exit 0，VitePress 1.6.4 完成全部页面渲染 |

最终检查记录在本文件写入后执行；以最后一次 `clean package`、网站构建和 `git diff --check` 的 exit 0 为交付结论。

## 最终一致性核对（2026-11-06 收敛轮）

### 10 端点对照

| 端点 | 契约 | 实现 | 自动化测试 | 中文文档 | 英文文档 |
| --- | --- | --- | --- | --- | --- |
| `POST /api/v1/sessions` | contracts:13 | SessionApiController:26 | SessionApiControllerTest:43 | zh:38 | en:38 |
| `POST /api/v1/sessions/{id}/messages` | contracts:25 | SessionApiController:38 | SessionApiControllerTest:63 | zh:51 | en:51 |
| `GET /api/v1/sessions/{id}` | contracts:33 | SessionApiController:44 | SessionApiControllerTest:63 | zh:61 | en:61 |
| `DELETE /api/v1/sessions/{id}` | contracts:39 | SessionApiController:49 | SessionApiControllerTest:63 | zh:67 | en:67 |
| `POST /api/v1/agents/{name}/invoke` | contracts:47 | AgentApiController:22 | AgentApiControllerTest:37 | zh:73 | en:73 |
| `GET /api/v1/profiles` | contracts:57 | ProfileApiController:21 | ProfileApiControllerTest:20 | zh:83 | en:83 |
| `GET /api/v1/memory` | contracts:61 | MemoryApiController:18 | MemoryApiControllerTest | zh:89 | en:89 |
| `GET /api/v1/tools` | contracts:65 | ToolApiController:29 | ToolApiControllerTest:28 | zh:95 | en:95 |
| `GET /api/v1/health` | contracts:69 | SystemApiController:27 | SystemApiControllerTest:21 | zh:102 | en:102 |
| `GET /api/v1/info` | contracts:73 | SystemApiController:32 | SystemApiControllerTest:21 | zh:108 | en:108 |
| `GET /api/v1/agents/{name}/memory`（兼容） | contracts:61 | MemoryApiController:23 | MemoryApiControllerTest | zh:91 | en:91 |

`OpenApiContractTest` 对 10 个核心操作和兼容路由逐一断言存在性及统一信封 schema；6 个 Controller 的组织方式与 FR-001 一致。

### 收敛修复

- F1（partial）：重写网站文档时删除了已实现的 `/api/v1/notify-channels` 5 个端点章节；已在中英文文档恢复精简章节，并说明掩码、204 删除与无字段加密限制，同时把“没有 Webhook”澄清为“没有入站 Webhook”。
- F2（contradicts）：中英文文档把 OpenAPI JSON 写成 `/v3/api-docs`，与实际 `springdoc.api-docs.path: /api-docs` 不符；已改为 `/api-docs`，与 `docs/EngineeringBaseline.md` 一致。
- F3（partial）：`contracts/web-api.md` 的 `SessionManager` 契约缺少实现使用的 `acquire`/`SessionAcquisition`；已补充并说明默认实现与 200/201 语义。
- F4（partial）：实测 `/api-docs` 发现通知渠道的 4 个 JSON 操作媒体类型是 `*/*`，而核心端点已是 `application/json`；创建端点也只登记 200。已给 `NotifyChannelApiController` 补 `produces=application/json`，登记创建 201 与 `Location`、删除 204；`SessionApiController` 登记复用 200 与创建 201。`OpenApiContractTest` 新增 16 个操作的统一信封断言，先 RED 于 `POST /api/v1/notify-channels`，修复后 GREEN。
- 格式门：`git diff --check` 初次 exit 2，`website/*/docs/api.md` 有 4 处 Markdown 硬换行行尾空格，`verification.md` 头部也有行尾空格；已全部改为列表或普通换行。

### 文档重写删除内容核对

以 `git show HEAD:website/docs/api.md` 的全部章节逐项对照当前 Controller（`oryxos-web` 仅有 8 个 `@RestController`/Advice）：

| 旧章节 | 实现状态 | 处理 |
| --- | --- | --- |
| Agent 创建/列表/查询/更新/删除、控制台会话、generate-files、保存文件 | 无对应 Controller，属 FR-023 排除的动态 Agent 管理 | 删除正确 |
| Provider 创建/列表/查询/更新/删除 | 无对应 Controller | 删除正确 |
| 通知渠道 5 个端点 | `NotifyChannelApiController` 真实实现 | 已恢复文档（F1） |
| 会话 SSE、会话列表 | 无实现；本阶段明确不做 SSE | 删除正确 |
| 调度、沙箱白名单、工具策略、审计轨迹、工作区文件浏览 | 无对应 Controller | 删除正确 |
| 健康、运行信息、Profile、Tool、无状态调用、Agent 记忆、会话创建/发消息/历史/归档 | 已实现 | 保留并重写 |

结论：文档重写删掉的内容里，只有通知渠道对应真实端点；其余均无实现，移除符合交付真实性要求。

### OpenAPI 实测（真实进程）

用打包后的 exec JAR 在临时工作区启动 `serve --port 18080` 后直接抓取：

- `/api-docs` → 200（确认 F2 的路径修正），`/swagger-ui/index.html` → 200，`/actuator/health` → 200。
- `/v3/api-docs` → 500：未知路径落到兜底 `Exception` 处理返回 `INTERNAL_ERROR`，属既有行为，本功能未引入，未在本次修改。
- 16 个操作的成功响应均为 `application/json` + `ApiResponse*` schema；`POST /api/v1/sessions` 同时登记 200/201 且 201 带 `Location`；`POST /api/v1/notify-channels` 为 201；`DELETE /api/v1/notify-channels/{name}` 为 204 无响应体。

### 最终门禁结果

| 命令 | 结果 |
| --- | --- |
| `./mvnw.cmd -pl oryxos-web -am test` | exit 0；Web 模块 39 个测试通过（12:05） |
| `env -u TERM ./mvnw.cmd clean package` | exit 0；15/15 Reactor 模块 SUCCESS；`oryxos-boot-0.1.0-SNAPSHOT-exec.jar` 已生成；02:17（12:29，OpenAPI 收敛修订后的最终构建） |
| `env -u TERM ./mvnw.cmd -pl oryxos-web -am verify` | exit 0；Web 模块 40 tests、core 131 tests 全通过；Checkstyle 0、SpotBugs 0、JaCoCo 覆盖率门通过；48.9s（12:32） |
| `npm --prefix website run docs:build` | exit 0；VitePress 1.6.4 渲染 3.04s，产物包含新增通知渠道章节 |
| `git diff --check` | exit 0；`git ls-files --others` 扫描无行尾空白 |

`clean package` 使用 pom 中的 `project.build.outputTimestamp` 可复现时间戳，因此 exec JAR 的 mtime 显示为固定值，不代表旧产物。

### 本地启动与手工验证

构建产物：`oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar`（`clean package` 生成）。

```bash
mkdir demo && cd demo
java -jar ../oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar init
java -jar ../oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar profile create demo
java -jar ../oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT-exec.jar serve --port 8080
```

`init` 在当前目录创建 `.oryxos/` 工作区；`serve` 的 `--port` 默认 8080。启动后打开 `http://localhost:8080/swagger-ui/index.html`，OpenAPI JSON 在 `http://localhost:8080/api-docs`。

必须先声明 Provider：新建 Agent 的 `AGENT.md` 默认引用 `provider.name: qwen`，启动时 `ProviderStartupValidator` 会剔除引用未配置供应商的 Agent，此时 `/api/v1/profiles` 为空、`POST /api/v1/sessions` 返回 404。在工作区 `.oryxos/application.yaml`（由 `spring.config.additional-location` 加载）声明即可：

```yaml
oryxos:
  providers:
    - name: qwen
      type: OPENAI_COMPATIBLE
      model: qwen-plus
      base-url: http://localhost:11434/v1   # 任意 OpenAI 兼容端点，本地 Ollama 也可
      api-key: local-no-auth                # 字面值可用，也可写 ${ENV_VAR}
```

无需真实密钥即可验证的部分（2026-11-06 以打包 JAR 在 18080 端口实测）：

| 请求 | 实际结果 |
| --- | --- |
| `GET /api/v1/health` | 200，`{"status":"UP"}` |
| `GET /api/v1/info` | 200，`profileCount=1`、`providers=["qwen"]` |
| `GET /api/v1/profiles` | 200，返回 `demo` |
| `POST /api/v1/sessions`（首次） | 201 + `Location: /api/v1/sessions/<uuid>` |
| `POST /api/v1/sessions`（同身份重复） | 200，复用同一活动会话 |
| `POST /api/v1/sessions`（未知 Agent） | 404 `AGENT_NOT_FOUND` |
| `GET /api/v1/tools` | 200，14 个内置工具 |
| `POST /api/v1/notify-channels` | 201 + `Location`（`type` 必须是小写 `webhook`，大写返回 400） |
| `GET /api/v1/notify-channels` | 200，URL 掩码为 `****` 加末 4 位 |
| `DELETE /api/v1/notify-channels/{name}` | 204，无响应体 |
| `/api-docs`、`/swagger-ui/index.html`、`/actuator/health` | 均 200 |

真实进程 `/api-docs` 的 16 个操作状态码与 schemas 与 `OpenApiContractTest` 断言一致：`POST /api/v1/sessions` 为 200/201，`POST /api/v1/notify-channels` 为 201，`DELETE /api/v1/notify-channels/{name}` 为 204，其余为 200。

`POST /api/v1/sessions/{id}/messages` 与 `POST /api/v1/agents/{name}/invoke` 会真正调用模型，需要可用的 Provider 端点与凭据；本地未接入模型时这两条只能验证参数校验、会话锁和错误信封（未配置可用模型时返回 Provider 错误），不能替代真实模型端到端验证。

### 环境相关的既有失败

`TERM=xterm-256color` 下执行 `./mvnw.cmd clean package` 时，`oryxos-cli` 的 `OryxOsCliCommandTreeTest#bareGroupsPrintTheirOwnHelpAndVersionDoesNotStartRuntime` 失败：picocli 输出 `Usage: \e[1moryxos profile\e[21m\e[0m`，而断言要求原始字符串包含 `Usage: oryxos profile`。该测试与 CLI 源码在本分支均未改动，`env -u TERM` 或 `-Dpicocli.ansi=false` 下通过，属于既有测试对终端颜色的环境敏感性，不是本功能的回归。按 AGENTS.md 不做弱化断言或改无关文件掩盖；本文件如实记录两次运行结果，建议后续单独修复该断言（例如先剥离 ANSI 再比较）。

## FR/SC 覆盖摘要

- FR-001/002/020：Controller 与 `GlobalExceptionHandlerTest` 验证统一信封、固定状态码/业务码和脱敏消息。
- FR-003：所有 Agent HTTP 执行只调用 `AgentService`；Boot IT 验证真实 ReAct/Tool/审计链路且工具不重复执行。
- FR-004 至 FR-011：Session 创建/复用、随机代次、web-only 写、最近 100 条、安全 Tool 投影、归档和单 JVM `tryLock` 均有单元或集成测试。
- FR-012/013：每次 invoke 创建独立 `web-invoke` Session，响应无 ID；60 秒截止由可注入短 Duration 的测试验证取消和中断。
- FR-014 至 FR-018：Profile、Memory、Tool、Health、Info 的排序、可见性和无敏感字段由 MockMvc 测试覆盖。
- FR-019/SC-003/SC-009：V6 从 V5 fixture 无损升级；活动代唯一索引、归档后新代及跨重启读取通过。
- FR-021/022/SC-001：OpenAPI 包含 10 个核心操作和兼容 Memory 路由；每个成功响应为 `application/json` 且 schema 含统一四字段；`/api/**` CORS 测试通过。
- FR-023：中英文文档明确无认证、开放 CORS、单 JVM 锁、尽力取消和未实现范围。
- FR-024/SC-006/SC-010：随机 UUID、真实 SQLite/审计链路和全仓/打包/文档门均有可复核命令。

## 未验证与明确边界

- 未使用真实 Provider 密钥，也未调用公网模型、Webhook 或企业 IM；自动化结果不能视为真实外部服务端到端通过。
- 未做多 JVM/多实例并发验证；当前会话锁和 active get-or-create 仅声明单实例语义。
- 未做生产性能、容量、长时间稳定性或安全渗透测试。
- 60 秒超时只验证等待上限与尽力中断，不承诺回滚已经发生的 Tool 副作用或审计写入。
