# Notify Channel Management Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付可通过 REST 管理、由 Agent 安全调用、经统一工具链审计的全局 Notify 渠道闭环，并满足第 19 节全部自动化验收。

**Architecture:** `oryxos-core` 定义通知渠道领域与注册表端口，`oryxos-storage` 用 SQLite/JPA/Flyway 实现，`oryxos-tool` 实现 Sandbox、目标解析、Adapter Registry、Webhook Adapter 和 `OryxTool`，`oryxos-web` 提供脱敏 CRUD，`oryxos-boot` 完成显式装配。发送始终从 `ToolExecutor` 进入，先解析渠道与环境变量，再由 Sandbox 返回批准 URI，最后调用类型 Adapter；审计复用现有 recorder。

**Tech Stack:** Java 21、Spring Boot 3.5.8、Spring `RestClient`、Spring Data JPA、Flyway、SQLite、Spring MVC/Validation、JUnit 5、AssertJ、JDK `HttpServer`

**Spec:** `specs/004-notify-channel-management/spec.md`

## Global Constraints

- Spring AI 自动工具执行保持关闭，真实发送只能经 `ToolExecutor`。
- `oryxos-core` 不依赖 storage、web、tool 或具体渠道模块。
- 数据结构只通过 `db/migration/sqlite/` Flyway 迁移，Hibernate 保持 `ddl-auto=validate`。
- HTTP Sandbox 默认拒绝；批准 URI 与 Adapter 实际使用 URI必须相同。
- 真实 URL、环境变量值、token、响应 body 和堆栈不得进入 API、Tool 结果、日志或 Agent Prompt。
- `Profile.notify_channels` 不恢复；核心阶段只实现 `webhook`。
- 测试必须保留 RED 证据，真实外部 Webhook 不作为自动验收前提。

## Review Focus

- 环境变量占位符缺失、空白或解析为恶意 URI时必须在网络前失败——由 `NotifyTargetResolverTest` 覆盖。
- `example.com` 与 `evil-example.com`、通配子域根域、IPv6/非法端口的匹配边界——由 `WhitelistSandboxTest` 参数化覆盖。
- API 掩码在 PUT 中必须保留原秘密而不是落库掩码——由 `NotifyChannelApiControllerTest` 覆盖。
- 500/重定向/连接失败不得被当作成功或自动重发——由 `WebhookNotifyAdapterTest` 覆盖请求计数。
- `ToolExecutor` 的 retryable 语义不得重复发送通知——由 `NotifyToolAuditIT` 覆盖成功/失败均单次调用。

---

**Branch**: `004-notify-channel-management` | **Date**: 2026-10-28 | **Spec**: `specs/004-notify-channel-management/spec.md`

**Input**: 已确认设计 `docs/superpowers/specs/2026-10-28-notify-channel-management-design.md` 与 feature specification。

## Summary

新增全局通知渠道领域、SQLite 注册表和 REST CRUD；新增最小 HTTP Sandbox 和通用 Webhook Adapter；将 `notify` 注册为现有 `OryxTool`，按渠道名或稳定缺省规则解析目标，经白名单后发送，并由现有工具审计落库。第 19 节旧 Profile 配置只迁移行为，不迁移数据位置。

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 3.5.8、Spring AI 1.1.2（仅复用既有 schema-only Tool 适配）、Spring Web `RestClient`、Spring Data JPA、Flyway

**Storage**: SQLite `notify_channels`，未来 vendor 迁移目录可扩展 PostgreSQL；本 feature 只交付 SQLite

**Testing**: JUnit 5、AssertJ、Spring Boot Test/MockMvc、JDK `HttpServer`、临时 SQLite

**Target Platform**: 企业私有化部署的单体可执行 JAR，Windows/Linux，Java 21

**Project Type**: Maven 多模块服务端运行时

**Performance Goals**: 本 feature 不声明新的性能达标值；只验证单次 Tool 调用最多发送一次。外部 HTTP 延迟不纳入本地功能验收

**Constraints**: 32 KiB 文本上限；HTTP 由既有 ToolExecutor 60 秒总超时约束；无外网测试；默认拒绝出站

**Scale/Scope**: 单实例、数百渠道、单次纯文本通知；不含分布式协调和批量发送

## Constitution Check

| Principle | Design response | Status |
| --- | --- | --- |
| I 单一运行时内核 | `notify` 是 `OryxTool`，只经 `ToolExecutor`/ReAct；不启用 Spring AI 自动执行 | PASS |
| II 工具调用统一受控 | runtimeTools 显式注册，Profile.tools 控制可见，Sandbox 先于 Adapter，审计复用 recorder | PASS |
| III 模块边界 | core 契约、storage JPA、tool 实现、web 入口、boot 装配；无 core 反向依赖 | PASS |
| IV 安全边界 | 默认拒绝 allowlist、URI 规范化、环境变量运行时解析、API/错误脱敏 | PASS |
| V 持久化显式契约 | Flyway V4 + JPA validate；名称主键与稳定排序 | PASS |
| VI 交付真实性 | 本地回环自动验收；专用 IM/真实外部渠道明确未交付 | PASS |

Phase 1 设计后复核：接口和数据模型未引入宪章例外，继续 PASS。

## File and Interface Structure

### `oryxos-core`

```text
oryxos-core/src/main/java/io/oryxos/core/notify/
├── NotifyChannel.java               # 不可变领域记录与基础不变量
├── NotifyChannelRegistry.java       # storage port
├── NotifyChannelService.java        # create/update/delete/default 的领域编排
├── NotifyChannelValidator.java      # 名称/type/url/config 纯校验
├── NotifyChannelNotFoundException.java
└── NotifyChannelConflictException.java
```

关键接口：

```java
interface NotifyChannelRegistry {
    NotifyChannel save(NotifyChannel channel);
    Optional<NotifyChannel> find(String name);
    List<NotifyChannel> list();
    void delete(String name);
}

final class NotifyChannelService {
    NotifyChannel create(CreateCommand command);
    NotifyChannel update(String name, UpdateCommand command);
    NotifyChannel get(String name);
    List<NotifyChannel> list();
    NotifyChannel resolve(String nullableName);
    void delete(String name);
}
```

`CreateCommand`/`UpdateCommand` 是 core 嵌套 record，Web DTO 转换后调用；`UpdateCommand.url` 与 config 的“保留旧值”由 Web 层先解析，core 只接收最终完整数据，避免领域层认识掩码。

### `oryxos-storage`

```text
oryxos-storage/src/main/java/io/oryxos/storage/
├── entity/NotifyChannelEntity.java
├── repository/NotifyChannelJpaRepository.java
└── JpaNotifyChannelRegistry.java
oryxos-storage/src/main/resources/db/migration/sqlite/V4__create_notify_channels.sql
```

`JpaNotifyChannelRegistry` 使用严格 Jackson reader/writer 编码 `Map<String,String>`；`list` 调用 `findAllByOrderByCreatedAtAscNameAsc()`；save 使用 `saveAndFlush` 并将唯一冲突映射为 core 异常。

### `oryxos-tool`

```text
oryxos-tool/src/main/java/io/oryxos/tool/
├── sandbox/ActionType.java
├── sandbox/SandboxAction.java
├── sandbox/Sandbox.java
├── sandbox/WhitelistSandbox.java
├── notify/NotifyTarget.java
├── notify/NotifyChannelAdapter.java
├── notify/NotifyAdapterRegistry.java
├── notify/NotifyTargetResolver.java
├── notify/WebhookNotifyAdapter.java
└── builtin/NotifyTools.java
```

关键接口：

```java
interface Sandbox {
    URI enforce(SandboxAction action);
}

record SandboxAction(ActionType type, URI target) {}

interface NotifyChannelAdapter {
    String channelType();
    void send(NotifyTarget target, String content);
}

final class NotifyTargetResolver {
    NotifyTarget resolve(NotifyChannel channel);
}
```

`NotifyTargetResolver` 注入 `Function<String,String> environment` 与 `Sandbox`；解析 `${ENV_VAR}` 后构造 URI，调用 `enforce`，只把返回 URI写入 target config。`NotifyTools` 注入 `NotifyChannelService`、resolver、adapter registry，所有异常转安全且 `retryable=false` 的 ToolResult。

`oryxos-tool/pom.xml` 增加 `spring-web` 与测试用 `spring-boot-starter-test`，不增加 OkHttp。

### `oryxos-web`

```text
oryxos-web/src/main/java/io/oryxos/web/notify/
├── CreateNotifyChannelRequest.java
├── UpdateNotifyChannelRequest.java
├── NotifyChannelView.java
├── NotifyChannelMasker.java
└── NotifyChannelApiController.java
```

Controller 依赖 core service；`NotifyChannelMasker` 负责 view 和 update 保留语义。`GlobalExceptionHandler` 增加领域异常映射；路径名进入 service 后复用领域校验。

### `oryxos-boot`

```text
oryxos-boot/src/main/java/io/oryxos/boot/NotifyConfiguration.java
oryxos-boot/src/main/java/io/oryxos/boot/HttpSandboxProperties.java
oryxos-boot/src/main/resources/application.yaml
```

`HttpSandboxProperties` 以 `@ConfigurationProperties("oryxos.http")` 绑定 `allowed-domains`，支持 YAML list 及环境变量逗号分隔值；环境解析使用 `System::getenv`。配置创建 RestClient、Sandbox、adapter registry、resolver、service 和 NotifyTools；Spring 已扫描 storage/web component。

### Tests

```text
oryxos-core/src/test/java/io/oryxos/core/notify/NotifyChannelServiceTest.java
oryxos-storage/src/test/java/io/oryxos/storage/NotifyChannelPersistenceTest.java
oryxos-tool/src/test/java/io/oryxos/tool/sandbox/WhitelistSandboxTest.java
oryxos-tool/src/test/java/io/oryxos/tool/notify/NotifyTargetResolverTest.java
oryxos-tool/src/test/java/io/oryxos/tool/notify/WebhookNotifyAdapterTest.java
oryxos-tool/src/test/java/io/oryxos/tool/notify/NotifyAdapterRegistryTest.java
oryxos-tool/src/test/java/io/oryxos/tool/builtin/NotifyToolsTest.java
oryxos-web/src/test/java/io/oryxos/web/notify/NotifyChannelApiControllerTest.java
oryxos-boot/src/test/java/io/oryxos/boot/NotifyConfigurationTest.java
oryxos-boot/src/test/java/io/oryxos/boot/NotifyToolAuditIT.java
```

## Implementation Phases

### Phase 1: Core domain and storage

以 `NotifyChannelServiceTest` 定义名称、类型、创建时间、稳定默认、缺失和冲突；实现 core 契约。随后以临时 SQLite 测试驱动 V4、实体和 JPA registry，覆盖重启恢复与并发冲突。

### Phase 2: Safe outbound runtime

先分别测试 Sandbox、环境占位解析和 Adapter Registry，再用真实回环 HTTP 测试 Webhook Adapter，最后用 fake registry/adapter 驱动 `NotifyTools`。每个行为保持独立 RED→GREEN，重点钉死 `enforce` 在 `send` 前。

### Phase 3: Management API

以 MockMvc/API 上下文测试驱动 DTO、脱敏器、CRUD 状态码和掩码保留语义；Controller 不直接访问 repository。API 写入后由 core/service/tool 读取同一记录。

### Phase 4: Boot and end-to-end audit

装配 `notify` 到 `runtimeTools`，配置默认拒绝 Sandbox。真实 ToolExecutor + 临时数据库 + 回环 server 验证成功/拒绝/500 均只产生一条审计且不重复发送。

### Phase 5: Documentation and gates

更新 README/website 中与当前实现冲突的 Notify 范围，只宣称通用 webhook。执行 quickstart、聚焦模块、全仓 test、clean verify、diff/secret scan 和 Spec-Kit 一致性检查。

## Risk Controls

- **重复通知**: 所有 Notify ToolResult `retryable=false`；回环测试断言请求计数。
- **URL 泄漏**: API view、异常、日志捕获和 ToolResult 使用固定 canary 扫描。
- **SSRF 绕过**: Sandbox 表驱动恶意 URI；批准 URI直接传 Adapter。
- **配置漂移**: Profile 旧字段继续由 AgentLoader 拒绝；唯一注册表是 core port 的 storage 实现。
- **模块耦合**: 扩展 ArchUnit 测试，禁止 web 依赖 storage、core 依赖 tool/web/storage。

## Complexity Tracking

无宪章例外。新增 core service 而非让 Controller/Tool 直接操作 repository，是为了让 Web 和 Tool 共享同一套缺省、校验和冲突语义。
