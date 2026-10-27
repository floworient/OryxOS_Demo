# Phase 1 Data Model: 核心契约与 Provider 接入

> 2026-10-27 绑定契约修订：用户批准以 Agent `skills/<binding>.link` 普通相对引用文件替代原生软连接。本文较早的软连接表述保留为历史记录，当前以宪章2.0.0及项目内部Skill引用设计为准；Skill仍不进入Profile字段。

**Date**: 2026-10-22 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

本 feature 只有一个持久化实体（`llm_calls`），另有 Provider、完整 Profile、OryxTool/Tool Schema 等内存态模型。配置契约见 [contracts/provider-config.md](./contracts/provider-config.md)。

---

## 1. 供应商定义（ProviderDefinition） — provider 模块内部内存态，不持久化

一个模型服务的部署侧定义。它只存在于 `oryxos-provider` 内部，不下沉为 core 公共类型。启动期由配置构建，之后不可变。

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `name` | String | 是 | 非空；按精确匹配，不做大小写或首尾空白归一化；在整个配置内唯一 |
| `type` | Enum `DASHSCOPE` / `OPENAI_COMPATIBLE` | 是 | 决定由哪个工厂构造 `ChatModel` |
| `model` | String | 是 | 非空；默认值由各工厂提供（DashScope 用配置中的模型名覆盖） |
| `baseUrl` | String / null | 否 | 若存在必须是 `http`/`https` 绝对地址；DashScope 缺省用官方地址 |
| `apiKey` | String | 是 | 由 `CredentialResolver` 从环境变量解析；**禁止进入日志、异常消息、`toString`**；对象持有的是解析后的明文，序列化时必须排除 |

**唯一性规则**：配置用 **List** 而非 Map 表达供应商，使重名可被检出而不是被 YAML/Map 静默覆盖。重名时启动失败，错误信息中包含冲突的名称与两条定义的位置（索引或行号）。

**校验时机**：启动期，`ProviderStartupValidator`。

**缺凭据但未被引用时**：定义名称仍保留在 `ProviderRegistry` 的只读配置名称清单中，但不构造 `ChatModel`、不进入可调用映射。若后续调用方尝试使用它，`ProviderService` 在网络请求前返回 `MISSING_CREDENTIAL`。这样既不阻止启动，也不会把空值或未解析占位符交给客户端工厂。

---

## 2. Agent Profile（Profile） — 内存态，不持久化

Profile 从 `.oryxos/agents/<name>/AGENT.md` frontmatter 完整派生；Agent 目录是唯一真相源。

| 字段 | 类型 | 必填 | 约束 |
| --- | --- | --- | --- |
| `name` | String | 是 | 必须与目录名完全相等；不允许覆盖或自动纠正 |
| `description` | String | 是 | 非空 |
| `identity` | Identity | 是 | `agentName`、`prompt` 均非空 |
| `provider` | ProviderRef | 是 | `name`、`model` 非空；`temperature` 可空且范围 0–2 |
| `tools` | List<String> | 否 | 缺省为空；去重后保持声明顺序 |
| `mcpServers` | List<String> | 否 | 缺省为空；去重后保持声明顺序 |
| `channels` | List<ChannelRef> | 否 | 每项含非空 `name` 与只读 `config` Map |
| `schedules` | List<ScheduleRef> | 否 | 每项含 Agent 内唯一 `key`、`cron`、`zone`、`message` |
| `bootstrap` | List<String> | 否 | 缺省为空；只保存相对路径声明，本节不读取文件 |
| `settings` | Settings | 否 | 缺省 `maxIterations=10`、`maxHistoryTurns=20`；均须为正整数 |
| `sourcePath` | Path | 是 | 规范化后的 `AGENT.md` 路径，仅用于定位与后续上下文读取 |

`skills` 与 `notify_channels` **不是 Profile 字段**。frontmatter 出现这两个课件旧字段时，该 Agent 加载失败；Agent 的 Skill 绑定由目录软连接表达，通知渠道由全局注册表表达。Persona 属于后续课程扩展，不在第 16 节提前实现。

### 2.1 加载与注册行为

- `AgentLoader.loadAll(workspace)` 返回成功派生的不可变 Profile 列表；失败原因通过结构化日志输出，错误模型保持实现内部，不新增公开契约。
- YAML 使用安全构造器，禁止重复键，并限制别名数、嵌套深度与输入字符数。
- 语法错误、缺字段、名称不一致和废弃字段由 `AgentLoader` 记录带 `agentName/sourcePath/field/reason` 的脱敏错误。未知 Provider 在全部 Profile 派生后由 `ProviderStartupValidator` 发现，并从 `ProfileRegistry` 移除该 Agent；其他合法 Agent 继续可用。
- 只有成功结果进入 `ProfileRegistry`；同名 Profile 冲突时报错，不采用 last-write-wins。
- `ProfileRegistry.referencedProviderNames()` 由所有已注册 Profile 的 `provider.name` 去重产生。

---

## 3. 模型调用记录（`llm_calls`） — 持久化到 SQLite

对应 spec 的「调用记录（Model Call Record）」与 FR-010。

### 表结构

```sql
CREATE TABLE llm_calls (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id        VARCHAR(255),                 -- 本阶段无 Session，允许为空
    profile_name      VARCHAR(100) NOT NULL,
    provider          VARCHAR(100) NOT NULL,
    model             VARCHAR(200) NOT NULL,
    prompt_tokens     INTEGER,                      -- NULL 表示未知
    completion_tokens INTEGER,                      -- NULL 表示未知
    total_tokens      INTEGER,                      -- NULL 表示未知
    usage_known       INTEGER NOT NULL DEFAULT 1,   -- 1=有用量数据，0=供应商未返回用量
    duration_ms       INTEGER NOT NULL,
    status            VARCHAR(20)  NOT NULL,        -- SUCCESS / EMPTY_RESPONSE / FAILED
    error_code        VARCHAR(64),                  -- 失败时的可判读错误码
    error_message     TEXT,                         -- 已脱敏
    created_at        TIMESTAMP    NOT NULL
);

CREATE INDEX idx_llm_calls_created_at ON llm_calls (created_at);
CREATE INDEX idx_llm_calls_provider   ON llm_calls (provider);
```

### 字段来源与规则

| 字段 | 来源 | 规则 |
| --- | --- | --- |
| `session_id` | 调用方上下文 | 本阶段无 Session，写 NULL；002 起写真实 Session 标识 |
| `profile_name` | `Profile.name` | 必填，使审计记录可关联具体 Agent |
| `provider` / `model` | `ProviderDefinition` | 与实际发出的调用一致，不允许写成「默认供应商」 |
| `prompt_tokens` / `completion_tokens` / `total_tokens` | `ChatResponse.getMetadata().getUsage()` | 供应商未返回时置 NULL 且 `usage_known=0`；三个字段要么都有值，要么都为空 |
| `duration_ms` | 调用前后计时 | 不含审计写入本身耗时 |
| `status` | 调用结果 | `SUCCESS`=有非空文本；`EMPTY_RESPONSE`=响应为空或只有空白字符（spec 边界案例要求作为可疑结果上报，不视为成功）；`FAILED`=认证/限流/超时/网络等错误 |
| `error_code` / `error_message` | 异常映射 | `error_message` 必须经过脱敏，不得包含凭据或完整请求头 |
| `created_at` | 调用结束时刻 | UTC 存储，展示层转换 |

### 不变量

1. 每次**发起了对外请求**的调用必须恰好产生一行记录；在参数校验阶段就被拒绝、未发出请求的调用不写记录。
2. 写入失败时调用失败关闭并抛 `AUDIT_PERSISTENCE_FAILED`；不得把模型原始成功结果返回给调用方。本 feature 不在写入路径上引入重试。
3. `status=SUCCESS` 时 `error_code` 必须为 NULL；`status=FAILED` 时 `error_code` 必须非空。

---

## 4. Tool Schema — 内存态，不持久化

`OryxTool` 的描述面由 `name`、`description`、`inputSchema`（JSON Schema）组成，执行面是 `execute(jsonInput)`。第 16 节只把描述面适配为 Spring AI `ToolDefinition`：

| 字段 | 来源 | 规则 |
| --- | --- | --- |
| `name` | `OryxTool.getName()` | 非空，原样保持 |
| `description` | `OryxTool.getDescription()` | 非空，原样保持 |
| `inputSchema` | `OryxTool.getInputSchema()` | 必须是 JSON object schema；必填项、枚举与嵌套结构不得丢失 |

适配结果是 schema-only `ToolCallback`：只暴露 `ToolDefinition`，其 `call` 固定拒绝并且永不委托 `OryxTool.execute`。调用方在构造 `Prompt` 前完成适配；`ProviderService` 保留 Prompt 已带的 callbacks，并把请求级 `internalToolExecutionEnabled` 强制设为 `false`，不根据 `Profile.tools` 自行查找工具。

---

## 5. 实体关系

```text
AGENT.md (文件)
   │ frontmatter.provider.name
   ▼
Profile（内存）── provider.name ──▶ ProviderDefinition（provider 内部）── name ──▶ ChatModel（显式可调用映射）
   │ tools                                                       │
   ▼                                                             ▼
OryxTool ──▶ ToolSchemaAdapter ──▶ schema-only ToolCallback      llm_calls（SQLite）
                                                                    │
                                                                    └─ profile_name 关联 Agent
```

`Profile` 与 `llm_calls` 之间没有数据库外键：本阶段没有 Session 表，`session_id` 留空；002 引入 Session 后再建立关联。

---

## 6. 状态与生命周期

| 对象 | 生命周期 |
| --- | --- |
| `ProviderDefinition` / `ChatModel` | 定义启动期构建；凭据合法时进入只读 ChatModel 映射，未引用且缺凭据时只保留定义状态 |
| `Profile` | 本节启动期扫描后只读；002 复用同一派生逻辑实现后续重载 |
| `OryxTool` / `ToolDefinition` | 请求前读取描述并转换；转换对象不可执行 |
| `llm_calls` 行 | 追加写，不更新、不删除（本阶段无归档接口） |

本 feature 不涉及任何状态机迁移。
