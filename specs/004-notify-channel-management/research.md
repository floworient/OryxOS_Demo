# Notify 渠道管理研究与决策

## 1. 配置真相源

**Decision**: 使用 SQLite `notify_channels` 全局注册表；Agent 仅在正文和 `notify.channel` 参数中按名称引用。`Profile` 与 frontmatter 不增加 `notify_channels`。

**Rationale**: 当前 `TechnicalSolution.md` §6.8 与项目宪章已修订为全局注册表，避免每个 Agent 重复保存 URL，也允许渠道独立更新。第 19 节课件中的 Profile 字段仅代表旧授课版本。

**Alternatives considered**: 恢复 Profile 列表会制造第二真相源并违反 `AgentLoader` 已有废弃字段校验；静态 application.yaml 无法支持管理闭环。

## 2. 第 19 节“第一个渠道”在全局模型中的含义

**Decision**: `NotifyChannelRegistry.list()` 固定按 `createdAt ASC, name ASC`；缺省发送使用第一项，更新保留 `createdAt`。

**Rationale**: 保留课件的缺省行为，同时避免依赖数据库无序返回。同毫秒创建用名称稳定打破平局。

**Alternatives considered**: 新增 `is_default` 会扩张课件和当前技术方案的数据模型；按名称首项会让新增更小名称意外改变默认值；只允许单渠道削弱完整管理能力。

## 3. Adapter 契约与类型路由

**Decision**: `NotifyChannelAdapter` 保留课件核心签名 `send(NotifyTarget,String)`，另提供 `channelType()` 供 `NotifyAdapterRegistry` 建立不可变显式映射。核心阶段只有 `webhook`。

**Rationale**: `NotifyTools` 不需要 `if/switch` 识别渠道；新增 Adapter 只增加实现。重复类型可在构造时 fail-fast，符合 Provider 显式映射的项目风格。

**Alternatives considered**: 注入单一 Adapter 无法扩展；依赖 Spring Bean 名或扫描顺序会产生不确定路由。

## 4. HTTP 客户端与测试

**Decision**: `oryxos-tool` 使用 Spring `RestClient` 和默认不跟随重定向的 JDK `HttpClient` request factory；单元测试使用 JDK `HttpServer` 回环服务，不新增第三方 MockWebServer 依赖。

**Rationale**: 仓库已有 Spring Boot 3.5.8，`RestClient` 与 Web 模块版本一致；JDK 21 自带 `HttpServer`，可验证真实 method/header/body/状态码且无外网。

**Alternatives considered**: MockWebServer 符合课件示例但需要新增 OkHttp 测试依赖；mock `RestClient` 只能验证调用形状，不能验证真实 HTTP 边界。

## 5. Sandbox 边界

**Decision**: 在 `oryxos-tool` 定义 `Sandbox`、`SandboxAction`、`ActionType.HTTP_REQUEST` 与 `WhitelistSandbox`。`enforce` 返回规范化 URI，`NotifyTools` 把该 URI放入 `NotifyTarget` 后再发送，确保检查目标与执行目标一致。

**Rationale**: 宪章要求通知必须真实经过白名单。返回规范化 URI比“先检查字符串、随后重新解析字符串”更能避免校验/执行差异。

**Alternatives considered**: 空实现或 allow-all 违反宪章；只在 Adapter 内校验使调用顺序难以复用和审计；把 Sandbox 放 core 会让共享契约承载仅 Tool 使用的基础设施。

## 6. 域名匹配与重定向

**Decision**: allowlist 项以小写 ASCII host 表示；精确匹配放行，前缀 `*.` 表示标签边界子域，且根域本身不由通配项隐式放行。只允许绝对 http/https、有效 host/port、无 user-info/fragment。HTTP 客户端不自动跟随 3xx。

**Rationale**: 避免简单 `endsWith` 放行 `evil-example.com`；重定向目标没有经过原始检查，必须作为失败。

**Alternatives considered**: DNS/IP 级解析会引入 DNS rebinding、代理和容器网络策略等更大 Runner 范围；当前只承诺应用层 host 策略，不宣称网络隔离。

## 7. URL 与环境变量

**Decision**: URL 接受绝对 HTTP(S) URI或完整 `${ENV_VAR}`；后者由 `NotifyTargetResolver` 在发送时通过注入的 `Function<String,String>` 解析。真实值不写回实体。测试注入受控 Map。

**Rationale**: 满足第 19 节“URL 进环境变量”和项目密钥规则，同时保留 REST 管理体验和无外网回环测试。

**Alternatives considered**: 启动时解析会让环境变更必须重启并把真实值扩散到对象；支持任意字符串插值会增加语法和泄漏面，本阶段只支持整个值为占位符。

## 8. 敏感值展示与更新

**Decision**: `NotifyChannelView` 对真实 URL 返回 `****` 加末 4 个字符；`${ENV_VAR}` 原样显示。config 中键名大小写归一后命中 `password|secret|token|api_key|apikey|credential|webhook` 时同样掩码。PUT 的 URL 为 null、空白或当前掩码时保留旧值；其他值按新 URL 校验。

**Rationale**: 与仓库网站文档中 Provider/Notify 编辑范式一致，避免查询接口泄漏可直接调用凭据。

**Alternatives considered**: 永不返回 URL 会让运维无法识别配置；返回完整 URL 违反安全约束；在 core 做脱敏会污染内部真实领域模型。

## 9. 持久化与并发冲突

**Decision**: `NotifyChannelEntity.name` 为主键，`config_json` 使用受限 `Map<String,String>` JSON；Flyway V4 建表。JPA `saveAndFlush` 的唯一约束异常统一转换为 `NotifyChannelConflictException`。

**Rationale**: 名称就是全局稳定引用，无需额外 ID；flush 能在事务边界内可靠捕获并发冲突。

**Alternatives considered**: 自动递增 ID 会形成两个身份；`ddl-auto` 被宪章禁止；把整个实体序列化为 blob 不利于迁移和校验。

## 10. Tool 输入与失败

**Decision**: `NotifyTools` 直接实现现有 `OryxTool`；用 Jackson 严格读取 record `NotifyInput(String content,String channel)`，开启未知字段拒绝和 trailing token 拒绝。32 KiB 上限按 Unicode 字符数检查。业务/HTTP 异常转换为 `ToolResult(false,"",安全错误,false)`。

**Rationale**: 当前运行时不扫描 Spring AI `@Tool`，而是显式收集 `OryxTool` 并生成 schema-only callback。直接实现才能保持唯一执行链和审计。

**Alternatives considered**: 同时暴露 `@Tool` 会产生第二执行模型；Adapter 内吞异常会导致 Agent 误判成功；retryable=true 会使现有 ToolExecutor 自动重试并可能重复通知。

## 11. REST 层依赖方向

**Decision**: Web Controller 只依赖 core 的 `NotifyChannelRegistry`；存储实现由 boot 聚合上下文注入。Web 层负责 DTO、脱敏、HTTP 状态映射，不依赖 JPA repository。

**Rationale**: 保持 core 契约、storage 实现、web 入口的依赖倒置和模块独立测试。

**Alternatives considered**: web 直接依赖 storage 会固化基础设施；把 HTTP DTO 放 core 会污染领域契约。
