# Web Service 核心能力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 通过核心 10 个 REST 端点让业务系统连续或一次性调用 Agent、查询历史与运行能力，同时保持统一 AgentService 链路、可恢复 Session、严格公开字段和稳定错误契约。

**Architecture:** `oryxos-web` 使用薄 Controller 与小型应用服务适配 core；`oryxos-core` 增加 SessionDetails/归档和 ProviderCatalog 契约，`oryxos-storage` 用 Flyway V6 支持 Session 代次。两个 Agent HTTP 入口复用 UTF-8 校验和受管虚拟线程执行器，按 Session ID 的单进程 `tryLock` 防止并发覆盖。

**Tech Stack:** JDK 21、Spring Boot 3.5.8、Spring MVC、Jakarta Validation、Spring Data JPA、Flyway、SQLite 3.49.1、springdoc-openapi 2.8.8、JUnit 5、AssertJ、Mockito、MockMvc

**Spec:** `docs/superpowers/specs/2026-11-04-web-service-core-design.md`、`specs/009-web-service-core/spec.md`

## Global Constraints

- 核心端点固定为 `specs/009-web-service-core/contracts/web-api.md` 的 10 个；兼容 Memory 路由不算新增核心范围。
- Session message 与 invoke 只能调用 `AgentService.process(Session,String)`，不得直接调用 Provider、ReActLoop 或 Tool。
- `oryxos-web` 不依赖 `oryxos-storage` 或 `oryxos-provider` 实现包；共享契约放 core。
- 消息按 trim 后非空、UTF-8 `<=32768` 字节校验；历史最多最近 100 条。
- Tool arguments/responseData、Profile prompt/sourcePath、凭证、URL、数据库路径和底层异常不得出现在 API 响应。
- 60 秒截止是尽力中断，已发生的外部 Tool 副作用和审计不回滚；不得描述为事务取消。
- 锁与 Session get-or-create 只承诺单 JVM；不宣称多实例一致性或分布式锁。
- 不实现认证、RBAC、限流、SSE/WebSocket、Webhook、动态 Agent/调度管理、Memory 写操作、Tool 执行或审计查询。
- 保留用户未提交的 `.claude/settings.local.json` 及无关改动。
- 每项行为先运行新增测试得到正确 RED，再写最小 GREEN；没有用户明确授权时不执行 git commit，仅提供建议提交信息。

## Review Focus

- V5 数据库升级：已有 active/archived 行的 ID、消息和时间必须原样保留；由 Task 1 的 `SessionMigrationV6Test` 固定。
- 同身份并发创建和归档后重建：活动行始终最多一条，新代 ID 不同；由 Task 1 的存储并发测试固定。
- 同 Session 消息/归档竞争：只有一个进入，第二个不调用 Agent；由 Task 3 的 latch 测试固定。
- UTF-8 多字节边界与历史泄密：32768/32769 精确判断，Tool args/result 不出 JSON；由 Task 2/3 固定。
- 60 秒超时后的真实语义：返回 504、请求中断但不承诺副作用回滚；由 Task 2/4 的可控时钟/阻塞测试固定。

---

### Task 1: Session 代次、归档详情与 Flyway V6

**Files:**
- Modify: `oryxos-core/src/main/java/io/oryxos/core/session/Session.java`
- Modify: `oryxos-core/src/main/java/io/oryxos/core/session/SessionManager.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/session/SessionStatus.java`
- Create: `oryxos-core/src/main/java/io/oryxos/core/session/SessionDetails.java`
- Modify: `oryxos-storage/src/main/java/io/oryxos/storage/JpaSessionManager.java`
- Modify: `oryxos-storage/src/main/java/io/oryxos/storage/entity/SessionEntity.java`
- Modify: `oryxos-storage/src/main/java/io/oryxos/storage/repository/SessionRepository.java`
- Create: `oryxos-storage/src/main/resources/db/migration/sqlite/V6__support_session_generations.sql`
- Modify/Create tests listed in `specs/009-web-service-core/plan.md`

**Interfaces:**
- Consumes: existing Session messages and V3 `sessions` rows
- Produces: `SessionStatus`、`SessionDetails`、`SessionManager.details(String)`、`SessionManager.archive(String)`；`getOrCreate` now persists/reuses the unique ACTIVE generation

- [x] **Step 1: 写 core RED 测试**

在 `SessionTest` 固定：

```java
assertThat(new Session("web", "u", "a").id())
        .isNotEqualTo(new Session("web", "u", "a").id());
assertThat(new Session(id, "web", "u", "a", created, active, messages).id())
        .isEqualTo(id);
```

补充非法 UUID、`lastActiveAt < createdAt`、SessionDetails ACTIVE/ARCHIVED 不变量和消息防御副本测试。

- [x] **Step 2: 运行 core RED**

Run: `.\mvnw.cmd -pl oryxos-core -Dtest=SessionTest test`

Expected: FAIL，当前 Session 仍确定性生成 ID，目标类型/构造器不存在。

- [x] **Step 3: 写 storage/migration RED 测试**

在 `SessionArchiveTest`/`SessionMigrationV6Test` 断言：active 复用；archive 后 `find` empty、`details` ARCHIVED；同身份新代 ID 不同；save 旧对象不能复活；V5 fixture 升级后字段逐项相等；8 线程同身份创建最终 active count 为 1。

- [x] **Step 4: 运行 storage RED**

Run: `.\mvnw.cmd -pl oryxos-storage -am -Dtest=SessionArchiveTest,SessionMigrationV6Test,SessionGetOrCreateTest test`

Expected: FAIL，archive/details/V6 不存在或永久 UNIQUE 阻止新代。

- [x] **Step 5: 实现 core 最小 GREEN**

```java
public enum SessionStatus { ACTIVE, ARCHIVED }

public record SessionDetails(String id, String channel, String userId,
        String profileName, SessionStatus status, Instant createdAt,
        Instant lastActiveAt, Instant archivedAt, List<Message> messages) { }

public interface SessionManager {
    Session getOrCreate(String channel, String userId, String profileName);
    void save(Session session);
    Optional<Session> find(String sessionId);
    Optional<SessionDetails> details(String sessionId);
    boolean archive(String sessionId);
    List<SessionSummary> list();
}
```

Session 默认构造器用 `UUID.randomUUID()`；恢复构造器接收 id 并验证。消息快照复用现有复制规则。

- [x] **Step 6: 实现 V6 与 JPA GREEN**

V6 关闭外键、创建新表、复制全部列、替换旧表、重建 `status` CHECK 与 `CREATE UNIQUE INDEX ... WHERE status='active'`，最后恢复外键。`getOrCreate` 在单 JVM 内同步查询/创建/flush；`save` 只 update ACTIVE；`details` 从 entity 生成快照；`archive` 条件更新 ACTIVE 并设置时间。

- [x] **Step 7: 运行 GREEN 与回归**

Run:

```powershell
.\mvnw.cmd -pl oryxos-core -Dtest=SessionTest test
.\mvnw.cmd -pl oryxos-storage -am -Dtest=SessionArchiveTest,SessionMigrationV6Test,SessionGetOrCreateTest,SessionPersistenceTest,SessionSummaryQueryTest test
```

Expected: PASS。

- [x] **Step 8: 检查点**

Suggested commit: `feat(session): support archived session generations`

---

### Task 2: Provider Catalog、UTF-8 校验、统一截止与错误映射

**Files:**
- Create: `oryxos-core/src/main/java/io/oryxos/core/provider/ProviderCatalog.java`
- Modify: `oryxos-provider/src/main/java/io/oryxos/provider/ProviderRegistry.java`
- Create: `oryxos-web/src/main/java/io/oryxos/web/api/Utf8MessageValidator.java`
- Create: `oryxos-web/src/main/java/io/oryxos/web/api/AgentCallExecutor.java`
- Create: `oryxos-web/src/main/java/io/oryxos/web/api/AgentCallTimeoutException.java`
- Create: `oryxos-web/src/main/java/io/oryxos/web/config/WebExecutionConfiguration.java`
- Modify: `oryxos-web/src/main/java/io/oryxos/web/api/GlobalExceptionHandler.java`
- Test: corresponding provider/web tests from plan.md

**Interfaces:**
- Produces: `ProviderCatalog.configuredNames() -> Set<String>`；`Utf8MessageValidator.validate(String) -> String`（返回 trimmed 值）；`AgentCallExecutor.execute(Callable<T>) -> T`

- [x] **Step 1: 写 ProviderCatalog RED**

断言 `ProviderRegistry` 可赋值给 `ProviderCatalog`，names 包含配置但无凭证、返回集合不可修改；现有 `ProviderService` lambda 编译测试保留。

- [x] **Step 2: 写 UTF-8 与 Executor RED**

```java
assertThat(validator.validate("a".repeat(32768))).hasSize(32768);
assertThatThrownBy(() -> validator.validate("你".repeat(10923)))
        .isInstanceOf(IllegalArgumentException.class);
```

Executor 测试用可注入 Duration/ExecutorService（生产默认 60 秒）：成功透传；ProviderException 原样抛；阻塞 Callable 在截止时收到 interrupt 并抛 timeout，不泄露 Callable exception message。

- [x] **Step 3: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-provider,oryxos-web -am -Dtest=ProviderRoutingTest,Utf8MessageValidatorTest,AgentCallExecutorTest test`

Expected: FAIL，目标类型不存在。

- [x] **Step 4: 实现 Catalog、validator 与受管虚拟线程 executor**

`ProviderRegistry implements ProviderCatalog`；validator 使用 `trim().getBytes(UTF_8).length`。配置 Bean 使用 `Executors.newVirtualThreadPerTaskExecutor()` 且 destroy 时 close。Executor 捕获 `TimeoutException` 后 `future.cancel(true)` 并抛固定超时类型；InterruptedException 恢复中断标记。

- [x] **Step 5: 写异常映射 RED 并实现 GREEN**

为 GlobalExceptionHandler 增加 ProviderException、AgentCallTimeoutException、Session storage wrapper 的测试；固定 503/504/500 和公开中文消息，response body 不含 canary secret。保留既有 ApiException/Validation/Memory/Notify 优先级。

- [x] **Step 6: 运行 GREEN**

Run: `.\mvnw.cmd -pl oryxos-provider,oryxos-web -am -Dtest=ProviderRoutingTest,Utf8MessageValidatorTest,AgentCallExecutorTest,GlobalExceptionHandlerTest,MemoryApiControllerTest,NotifyChannelApiControllerTest test`

Expected: PASS。

- [x] **Step 7: 检查点**

Suggested commit: `feat(web): add safe agent call foundation`

---

### Task 3: 连续 Web Session 四端点

**Files:**
- Create: `oryxos-web/src/main/java/io/oryxos/web/session/*.java` listed in plan.md
- Create: `oryxos-web/src/test/java/io/oryxos/web/session/SessionWebServiceTest.java`
- Create: `oryxos-web/src/test/java/io/oryxos/web/session/SessionApiControllerTest.java`
- Create: `oryxos-boot/src/test/java/io/oryxos/boot/WebServiceSessionRestartIT.java`

**Interfaces:**
- Consumes: ProfileRegistry、SessionManager、AgentService、AgentCallExecutor、Utf8MessageValidator
- Produces: create/send/get/archive Web service methods and four `/api/v1/sessions` routes

- [x] **Step 1: 写 SessionWebService RED**

固定创建新/复用标记、未知 Agent、web-only 写权限、最近 100 条、`historyTruncated`、Tool args/result 不出 View。用 latch 占住首次 send，第二次 send 与 archive 必须抛 SESSION_BUSY 且 AgentService 调用次数不增加；不同 ID 的 send 能进入。

- [x] **Step 2: 运行 service RED**

Run: `.\mvnw.cmd -pl oryxos-web -am -Dtest=SessionWebServiceTest test`

Expected: FAIL，service/DTO 不存在。

- [x] **Step 3: 实现应用服务最小 GREEN**

Service 按 ID 长期保留 `ReentrantLock` 并使用 `tryLock`；create 先验证 Profile，再调用已持久化语义的 getOrCreate；send/get/archive 严格执行 channel/status 规则。MessageView 对 tool 只映射 name/id。

- [x] **Step 4: 写 Controller RED**

MockMvc 覆盖 POST create 的 200/201、POST message、GET active/archived、DELETE、Bean Validation、统一 envelope 和 404/409/503/504。断言响应 JSON 不含 `arguments`、`responseData`、`sourcePath`。

- [x] **Step 5: 实现 Controller GREEN**

Controller 只调用 SessionWebService；用 `ResponseEntity<ApiResponse<...>>` 表达 200/201，所有异常交给 advice，不拼业务逻辑。

- [x] **Step 6: 写并运行真实 Boot IT**

使用可控 Provider 与临时 SQLite，执行创建→两轮消息→GET→DELETE→关闭/重启 context→GET archived→相同身份 POST 新建。断言两代 ID 不同、历史/llm_calls 保留、每条消息一次 AgentService 链路。

Run: `.\mvnw.cmd -pl oryxos-boot -am -Dit.test=WebServiceSessionRestartIT verify`

Expected: PASS。

- [x] **Step 7: 回归与检查点**

Run: `.\mvnw.cmd -pl oryxos-storage,oryxos-web,oryxos-boot -am test`

Suggested commit: `feat(web): expose persistent session api`

---

### Task 4: 一次性 Agent invoke

**Files:**
- Create: `oryxos-web/src/main/java/io/oryxos/web/agent/AgentInvocationWebService.java`
- Create: `oryxos-web/src/main/java/io/oryxos/web/agent/AgentApiController.java`
- Create: request/view records from plan.md
- Create: corresponding web tests
- Create: `oryxos-boot/src/test/java/io/oryxos/boot/WebServiceAgentIT.java`

**Interfaces:**
- Consumes: SessionManager、ProfileRegistry、AgentService、AgentCallExecutor、Utf8MessageValidator
- Produces: `invoke(String agentName,String message) -> InvokeAgentView` and POST `/api/v1/agents/{name}/invoke`

- [x] **Step 1: 写 service RED**

两次 invoke 必须调用 `getOrCreate("web-invoke", randomUserId, name)` 得到不同 ID；每个 Session 在 `AgentService.process` 前已 save；返回仅 reply。未知 Agent/超时/Provider 失败按合同传播。

- [x] **Step 2: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-web -am -Dtest=AgentInvocationWebServiceTest test`

Expected: FAIL，service 不存在。

- [x] **Step 3: 实现 service GREEN**

内部 userId 使用随机 UUID；不把 Session ID 放入 View 或日志；先持久化再通过 AgentCallExecutor 调 AgentService。

- [x] **Step 4: 写/实现 Controller RED→GREEN**

MockMvc 断言 200 envelope 只有 reply；32 KiB/未知 Agent/Provider/timeout 状态正确，JSONPath `$.data.sessionId` 不存在。

- [x] **Step 5: 写/运行 Boot IT**

可控 Provider 返回一次 tool call + 最终 reply；连续两次 HTTP invoke 后查询 SQLite，断言 2 个 web-invoke Session、各自 LLM/Tool audit、Tool 总执行 2 次、响应均无 ID。

Run: `.\mvnw.cmd -pl oryxos-boot -am -Dit.test=WebServiceAgentIT verify`

Expected: PASS。

- [x] **Step 6: 检查点**

Suggested commit: `feat(web): add stateless agent invocation`

---

### Task 5: Profile/Memory/Tool/System、CORS 与 OpenAPI

**Files:**
- Create/Modify: query Controller/services/views and tests listed in `specs/009-web-service-core/plan.md`
- Create: `oryxos-web/src/main/java/io/oryxos/web/config/CorsConfiguration.java`
- Create: `oryxos-web/src/test/java/io/oryxos/web/OpenApiContractTest.java`

**Interfaces:**
- Consumes: ProfileRegistry、MemoryService、ToolRegistry、ProviderCatalog、Package implementation metadata
- Produces: remaining six core query routes plus memory compatibility route

- [x] **Step 1: 写三个查询组 RED 测试**

Profile：name 排序与 prompt/path/channel 缺失；Tool：全局/visibleTo 排序与 schema 缺失；System：health 不触发 Provider，info 计数/providers 排序、未知版本为 `dev`。Memory：query 与 path 兼容路由返回同 View，未知 Agent/503 一致。

- [x] **Step 2: 运行 RED**

Run: `.\mvnw.cmd -pl oryxos-web -am -Dtest=ProfileApiControllerTest,MemoryApiControllerTest,ToolApiControllerTest,SystemApiControllerTest test`

Expected: FAIL，目标 Controller/路由或安全投影不存在。

- [x] **Step 3: 实现查询 GREEN**

所有 View 显式列字段并 `List.copyOf`；排序在 Web 映射层完成；Memory 双路由委托同一 MemoryQueryService；health 返回静态 UP，不做外部探测。

- [x] **Step 4: 写 CORS/OpenAPI RED**

MockMvc OPTIONS `/api/v1/sessions` 断言开放 origin/method/header；OpenAPI JSON 断言核心 10 path/method 全部存在，兼容 Memory 路由可存在但不替代规范路由，响应引用统一 ApiResponse schema。

- [x] **Step 5: 实现 CORS/OpenAPI GREEN**

CORS 只匹配 `/api/**` 并开放调试所需 origin/method/header；用 springdoc annotation 补足 DTO/response 描述，不新增第二套 schema。

- [x] **Step 6: 运行 web 全测**

Run: `.\mvnw.cmd -pl oryxos-web -am test`

Expected: PASS。

- [x] **Step 7: 检查点**

Suggested commit: `feat(web): expose runtime discovery api`

---

### Task 6: 文档、完整门禁与 Spec-Kit 收敛

**Files:**
- Modify: `README.md`
- Modify: `website/zh/docs/api.md`
- Modify: `website/docs/api.md`
- Create: `specs/009-web-service-core/verification.md`
- Modify: `specs/009-web-service-core/tasks.md`（只更新真实完成状态）

**Interfaces:**
- Consumes: 所有已验证端点与边界
- Produces: 可集成文档、真实验证证据和收敛后的 Spec-Kit 状态

- [x] **Step 1: 更新中英文 API 文档**

列出请求/响应示例、10 端点、32 KiB/100 条/60 秒、归档代次；突出无认证、开放 CORS、单实例锁、尽力中断、无流式/动态管理，避免宣称扩展能力已完成。

- [x] **Step 2: 运行聚焦与全仓门禁**

Run sequentially:

```powershell
.\mvnw.cmd -pl oryxos-core,oryxos-provider,oryxos-storage,oryxos-web,oryxos-boot -am test
.\mvnw.cmd test
.\mvnw.cmd clean package
npm --prefix website run docs:build
git diff --check
```

Expected: 全部 exit 0；任何既有/无关失败原样记录，不弱化门禁。

- [x] **Step 3: 写 verification.md**

逐项记录 RED/GREEN、命令/退出码、FR/SC 证据、Flyway 升级、真实 AgentService/审计链路、OpenAPI 10 端点、未执行真实 Provider/公网验证。

- [x] **Step 4: Spec-Kit analyze/converge 等价检查**

对照 spec/plan/tasks/contracts 与真实文件、测试、文档。发现缺口追加任务并回到对应 RED→GREEN；没有剩余工作才勾选 T021。

- [x] **Step 5: 最终检查点**

Run: `git status --short` 与 `git diff --stat`

Suggested commit: `feat: complete core web service`

