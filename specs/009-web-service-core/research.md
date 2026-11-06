# Research: Web Service 核心能力

## Decision 1: Web 应用服务隔离 HTTP 与 core

**Decision**: 六个 Controller 只处理路由、校验、状态码与 `ApiResponse`；Session 与 invoke 使用专用 Web 应用服务，查询端点使用小型 View 映射器/服务。

**Rationale**: Web DTO、HTTP 状态和历史裁剪不应污染 core；同时避免 Controller 直接承载身份、并发和超时策略。现有 Memory/Notify Controller 模式可演进而无需引入新的“总门面”模块。

**Alternatives considered**:

- Controller 直接调用全部 core 服务：文件少，但业务适配散落且难以测试并发/超时。
- core 新增完整管理 Facade：扩大共享契约并提前引入扩展阶段职责。

## Decision 2: Session 使用代次 UUID + 活动部分唯一索引

**Decision**: 新 Session 使用随机 UUID；`getOrCreate` 按 `(channel,userId,profileName,status=active)` 查找。SQLite V6 重建 sessions 表并创建 `WHERE status='active'` 的部分唯一索引。

**Rationale**: 需求要求归档历史可查且无活动会话时可重新创建。当前确定性 ID 和永久三元组 UNIQUE 无法同时保留旧代并创建新代。SQLite 3.49.1 支持 partial index；表重建是移除内联 UNIQUE 的可靠 Flyway 路径。

**Alternatives considered**:

- 归档后拒绝重建：违反需求流程。
- 覆盖旧行并复用 ID：丢失归档历史。
- 物理删除旧行：违反归档与审计要求。
- 在三元组加入 generation 列：可行但增加冗余；随机 ID + active 部分索引已表达所需不变量。

## Decision 3: 活动执行对象与只读历史快照分离

**Decision**: `SessionManager.find(id)` 继续只返回活动 `Session`；新增 `details(id)` 返回可包含 archived 状态的不可变 `SessionDetails`；新增 `archive(id)` 完成状态转换。

**Rationale**: 让已归档 Session 重新成为可变执行对象会产生误续写风险。只读快照可以满足 GET 历史，同时保持 AgentService 只接收活动 Session。

**Alternatives considered**:

- 给 Session 增加可变 status：执行内核需要到处防御 archived 状态。
- Web 直接读 SessionRepository：违反模块边界和依赖倒置。

## Decision 4: Web 写操作按 Session ID 非阻塞互斥

**Decision**: `SessionWebService` 使用 `ConcurrentHashMap<String, ReentrantLock>` 和 `tryLock` 保护 message/archive；竞争返回 409，不排队。锁保留到进程结束，避免 remove/recreate 竞态。

**Rationale**: 当前 Session 是可变列表，JPA 按完整快照保存；并发 ReAct 会导致覆盖或消息顺序破坏。非阻塞拒绝与 Scheduler 既有重叠策略一致，且不会让 HTTP 请求隐式排长队。

**Alternatives considered**:

- 不加锁依赖数据库：无法保护 ReAct 期间的内存 Session 和 Tool 副作用。
- 排队锁：调用方无法区分执行时间与排队时间，容易越过 60 秒截止。
- 全局锁：无关 Session 被长任务阻塞。

## Decision 5: 60 秒截止使用 Java 21 虚拟线程 Future

**Decision**: Boot/Web 配置受管 `Executors.newVirtualThreadPerTaskExecutor()`；invoke 与 Session message 均通过同一 `AgentCallExecutor` 执行，`Future.get(60, SECONDS)` 超时后 `cancel(true)` 并抛出 Web 可映射的超时异常。

**Rationale**: 技术方案要求 Agent 调用最长 60 秒且返回 504。单一执行适配器保证两个 Agent 调用入口语义一致；虚拟线程适合阻塞 Provider/Tool 调用且与项目 Java 21 基线一致。

**Alternatives considered**:

- 仅配置 Spring MVC async timeout：不能统一同步 Controller 内的执行与中断请求。
- `CompletableFuture.orTimeout` 使用公共池：线程归属和关闭不可控。
- 修改 ReActLoop 传播硬 deadline：能更强控制，但会扩大本期内核改动；底层外部调用仍未必可取消。

`cancel(true)` 只是尽力中断。已完成的 Tool 副作用和审计不回滚，文档必须准确披露。

## Decision 6: Tool 历史只公开元数据

**Decision**: user/assistant 文本可返回；Tool call/result 只返回 role、toolName 与 callId，不返回 arguments 或 responseData。

**Rationale**: `responseData` 可包含文件、Shell 和 HTTP 内容，无法用通用规则可靠脱敏；查询历史的核心目的不需要重放完整工具载荷。

**Alternatives considered**:

- 返回完整结果后做关键词脱敏：容易漏掉业务敏感内容。
- 完全隐藏工具消息：不利于理解对话过程和审计定位。

## Decision 7: Provider 名单通过 core Catalog 契约

**Decision**: 新增 `ProviderCatalog` 只读接口，`ProviderRegistry` 实现并返回不可变配置名称集合；System 查询只依赖该 core 契约。

**Rationale**: info 需要配置 Provider 名称，但 Web 不应依赖 provider 实现包。单独只读契约保持 `ProviderService` 的函数式调用接口不变，避免破坏现有 lambda 测试桩。

**Alternatives considered**:

- Web 依赖 ProviderRegistry：扩大模块耦合。
- 给 ProviderService 增加方法：破坏函数式接口和现有轻量桩。
- 从 Profile 推断：会漏掉已配置但尚未引用的 Provider。

## Decision 8: 查询与错误响应最小暴露

**Decision**: 所有列表稳定按 name 排序；Profile、Tool、System 使用显式 View 白名单；具体 Provider/Memory/Storage/Timeout 异常先映射，未知异常最后兜底。

**Rationale**: 显式投影比黑名单更能防止未来字段自动泄漏。稳定排序让契约测试和集成方行为可预测。

**Alternatives considered**:

- 直接序列化 core record/entity：会暴露 prompt、path、schema 或内部类型。
- 返回底层异常 message：可能泄露密钥、URL、SQL 或本地路径。

