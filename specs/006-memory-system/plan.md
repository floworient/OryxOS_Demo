# Implementation Plan: Memory 核心完整交付

**Branch**: `006-memory-system` | **Date**: 2026-10-31 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-memory-system/spec.md`

## Summary

实现 Memory 三层统一门面的核心阶段：复用已持久化的 Session 历史，提供按 Agent 隔离的 core/archival 长期记忆，并通过同一 `MemoryService` 接入 Prompt、`save_memory`/`recall_memory` 与只读 API。长期后端一次交付 Markdown、SQLite、自托管 Mem0 三档；共享契约位于 core，具体实现按模块边界落位。015 仅预留 `MemoryCapabilities` 与 `archivalEntries()`，不实现 embedding、RRF、异步索引或对账。

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.5.8、Spring AI 1.1.2、Spring Data JPA、Flyway、JDK `HttpClient`、Jackson（经 Spring AI/Boot）  
**Storage**: `.oryxos/agents/<name>/MEMORY.md`、SQLite `memory_entries`、自托管 Mem0 OSS REST  
**Testing**: JUnit 5、AssertJ、Mockito、Spring Boot Test、MockMvc、JDK 回环 HttpServer、Maven Surefire/Failsafe  
**Target Platform**: 企业私有化单体可执行 JAR；Windows/Linux；JDK 21  
**Project Type**: Maven 多模块 Java runtime + REST service  
**Performance Goals**: Memory 读取不缓存但只处理单 Agent 数据；API/Prompt archival 最多 4000 字符；外部响应最多 1 MiB  
**Constraints**: core 不依赖具体实现；所有工具经过 ToolExecutor；core 永不截断/检索；Mem0 写不重试；无真实密钥测试  
**Scale/Scope**: 三后端、2 个工具、1 个 API、1 个 Flyway migration、约 8 组契约/集成测试

## Constitution Check

*GATE: Phase 0 前检查并在 Phase 1 后复核。*

| 原则 | 计划符合性 | 证据 |
| --- | --- | --- |
| I 单一运行时内核 | PASS | PromptBuilder 只依赖 MemoryService；入口不拼装 Memory |
| II 工具调用统一受控 | PASS | MemoryTools 经 AnnotatedToolDiscovery → Registry → ToolExecutor → audit |
| III 模块边界与依赖倒置 | PASS | 契约在 core；Markdown/Mem0 在 memory；JPA/Flyway 在 storage；装配在 boot |
| IV 安全边界真实生效 | PASS | Markdown real-path/NOFOLLOW；Mem0 上限/禁重定向/脱敏；密钥只占位 |
| V 持久化与显式契约 | PASS | SQLite V5 Flyway；`ddl-auto=validate`；后端显式配置 |
| VI 真实性与可验证性 | PASS | 015 明确非目标；Mem0 使用 fake 自动测，真实服务单独报告 |

### Phase 1 复核

- `research.md` 未引入新模块或框架自动 Agent 循环：PASS。
- `data-model.md` 保持 core/archival 必选语义：PASS。
- `contracts/` 未提供绕过 ToolExecutor 的写 API：PASS。
- `quickstart.md` 未提交真实凭证或声称真实 Mem0 已验证：PASS。

## Architecture

```text
AgentService / ReActLoop
          |
     PromptBuilder -----------------------------+
          |                                     |
     MemoryService (core contract)              |
       /              \                         |
Session messages   LongTermMemoryStore          |
(SessionManager)      /      |       \          |
                 Markdown  SQLite   Mem0 OSS     |
                   |        |        |           |
               filesystem   JPA   HttpClient     |
                                                |
MemoryTools -> ToolRegistry -> ToolExecutor -----+
MemoryApiController -> MemoryService (read-only)
```

## Core Interface Decisions

- `MemoryService.load(Session, Profile)` 返回 `MemoryContext`，保留结构化消息；历史轮数仍由 PromptBuilder 裁剪。
- `LongTermMemoryStore` 的 `load` 返回 Prompt/API 有界视图，`archivalEntries` 返回 015 所需未裁剪本体视图。
- `MemoryOwner.agent(profile.name())` 使用共享 `AgentName` 校验，非法值 fail-loud；global 是保留 owner。
- Markdown/SQLite 返回 `KEYWORD`；Mem0 返回 `DELEGATED`；当前装配拒绝 `HYBRID_BUILTIN`。
- Store 失败统一为 `MemoryException`，Web 映射 503，Tool 映射稳定失败结果，Prompt 构建不静默忽略。

## Configuration

```yaml
oryxos:
  memory:
    backend: markdown
    archival-max-characters: 4000
    entry-max-characters: 4000
    recall-max-results: 20
    zone: ${TZ:Asia/Shanghai}
    mem0:
      base-url: ${MEM0_BASE_URL:}
      api-key: ${MEM0_API_KEY:}
      connect-timeout: 3s
      request-timeout: 10s
      max-response-bytes: 1048576
```

只在 `backend=mem0` 时要求 Mem0 字段并执行 OpenAPI 能力校验。Markdown/SQLite 启动不得触网。

## Project Structure

### Documentation (this feature)

```text
specs/006-memory-system/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── memory-api.md
│   ├── memory-backends.md
│   ├── memory-service.md
│   └── memory-tools.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
oryxos-core/src/main/java/io/oryxos/core/
├── memory/
│   ├── LongTermMemoryStore.java
│   ├── MemoryCapabilities.java
│   ├── MemoryContext.java
│   ├── MemoryEntry.java
│   ├── MemoryException.java
│   ├── MemoryOwner.java
│   ├── MemoryScope.java
│   ├── MemoryService.java
│   └── MemorySnapshot.java
├── profile/AgentName.java
├── context/PromptBuilder.java
└── tool/ToolExecutor.java

oryxos-memory/src/main/java/io/oryxos/memory/
├── DefaultMemoryService.java
├── MemoryOwnerResolver.java
├── MarkdownMemoryStore.java
├── MemoryTools.java
└── mem0/
    ├── Mem0Client.java
    ├── Mem0MemoryStore.java
    └── Mem0ProtocolValidator.java

oryxos-storage/src/main/
├── java/io/oryxos/storage/
│   ├── JpaMemoryStore.java
│   ├── entity/MemoryEntryEntity.java
│   └── repository/MemoryEntryRepository.java
└── resources/db/migration/sqlite/V5__create_memory_entries.sql

oryxos-boot/src/main/java/io/oryxos/boot/
├── MemoryConfiguration.java
└── MemoryProperties.java

oryxos-web/src/main/java/io/oryxos/web/memory/
├── MemoryApiController.java
└── MemoryView.java

oryxos-*/src/test/java/...  # 同包契约、单元与集成测试
```

**Structure Decision**: 沿用现有 14 模块。共享契约与安全 owner 校验归 core；默认/远端后端归 memory；JPA 后端归 storage；Spring 条件装配归 boot；只读 HTTP 适配归 web。没有新增 Maven 模块。

## Delivery Slices

### Slice A: 统一契约与默认 Markdown 闭环

先以测试定义 owner、scope、entry/snapshot、Store 与 Service；实现 Markdown、Prompt 注入和跨会话立即可见。该 slice 独立满足 US1 的默认后端价值。

### Slice B: 统一工具与审计

测试先行实现 MemoryTools、严格 schema、Profile 可见性与 content 脱敏，满足 US2。

### Slice C: SQLite 与 Mem0 后端

复用共享 StoreContract，分别实现 Flyway/JPA 与 OSS REST adapter；通过配置切换验证上层不变，满足 US3。

### Slice D: 只读 API 与整体装配

实现 ProfileRegistry 校验、ApiResponse/404/503、三后端启动测试和 quickstart，满足 US4。

## Verification Strategy

1. 每个新行为先写失败测试并记录预期失败原因。
2. 模块快速回路：`mvnw.cmd -pl <module> -am test -Dtest=<Test>`。
3. Slice 完成运行相关模块：`mvnw.cmd -pl oryxos-core,oryxos-memory,oryxos-storage,oryxos-web,oryxos-boot -am test`。
4. 最终运行 `mvnw.cmd test`。
5. 最终运行 `mvnw.cmd clean verify`，包含 Checkstyle、SpotBugs、JaCoCo、Failsafe 与 ArchUnit。
6. 真实 Mem0 仅在提供服务与密钥后单独验证，不影响 fake server 自动验收。

## Complexity Tracking

无宪章例外。三后端是用户确认的核心交付范围；通过共享 StoreContract 控制重复与行为漂移。

## Risks and Mitigations

| 风险 | 缓解 |
| --- | --- |
| Markdown TOCTOU/链接逃逸 | NOFOLLOW + real path 父目录校验 + 锁内复查 + 同目录临时文件 |
| 多后端语义漂移 | 抽象 StoreContract，三后端复用同一组断言 |
| Mem0 OSS API 版本变化 | 启动 OpenAPI 校验；DTO 私有；无 `/v1` 假设写入契约测试 |
| Mem0 写结果不确定导致重复 | 写请求零重试；失败不声明成功 |
| Memory 正文进入审计 | Memory tool 专用摘要审计；canary 测试 |
| Prompt 历史回归 | 保留 PromptBuilder 裁剪职责；现有测试加 MemoryService fake |
| 015 预留变成空实现 | 只保留 enum/条目接口；`HYBRID_BUILTIN` 当前 fail-fast |

