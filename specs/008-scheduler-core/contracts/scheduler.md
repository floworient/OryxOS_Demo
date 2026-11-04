# Contract: Scheduler Core

## Profile 输入契约

`Profile.ScheduleRef(String key, String cron, String zone, String message)` 保持现有公共形状。

- key/cron/zone/message 均非空。
- key 在单个 Profile 内唯一。
- cron 在 Agent 加载阶段按当前 Spring 六段表达式解析。
- zone 在 Agent 加载阶段通过 `ZoneId.of` 解析。
- 任一失败沿用 AgentLoader 的整 Agent 拒绝行为。

## 稳定身份契约

```java
public record ScheduleId(String value) {
    public static ScheduleId from(String profileName, String scheduleKey);
}
```

`from` 对相同二元组稳定；输入任一为空时抛出 `IllegalArgumentException`。实现必须使用带长度边界的 UTF-8 身份，不能简单拼接。

## 定义与执行值对象

```java
public record ScheduledTaskDefinition(
        ScheduleId scheduleId,
        String profileName,
        String scheduleKey,
        String cron,
        ZoneId zoneId,
        String message) {

    public static ScheduledTaskDefinition from(Profile profile, Profile.ScheduleRef schedule);
}

public record TaskExecution(
        ScheduleId scheduleId,
        String sessionId,
        Instant startedAt,
        Instant finishedAt,
        boolean success,
        String errorType) {

    public static TaskExecution succeeded(
            ScheduleId scheduleId, String sessionId, Instant startedAt, Instant finishedAt);

    public static TaskExecution failed(
            ScheduleId scheduleId, String sessionId, Instant startedAt,
            Instant finishedAt, Throwable failure);
}
```

值对象执行 data-model.md 的空值、时间顺序和 errorType 约束。`failed` 只提取异常简单类名，不保留 message/cause/stack。

## Store 契约

```java
public interface ScheduledTaskStore {
    ScheduledTaskDefinition reconcile(ScheduledTaskDefinition definition);

    void retire(ScheduleId scheduleId);

    Optional<ScheduledTaskDefinition> find(ScheduleId scheduleId);

    void recordExecution(TaskExecution execution);

    List<TaskExecution> executions(ScheduleId scheduleId);
}
```

- `reconcile` 对同 ID 新定义做原子新增或替换并返回存储快照。
- `retire` 幂等；内存实现移除活动定义但不清空 execution。
- 所有输入拒绝 null。
- `find` 和 `executions` 返回不可变/防御快照。
- `InMemoryScheduledTaskStore` 必须支持并发 reconcile/record/read，不承诺跨重启恢复。
- 本阶段只保证默认内存实现；逐定义接口不宣称提供 Profile 级数据库事务。后续持久化适配器在被选为生产实现前，必须在实现内部提供批量事务或等价补偿，不能仅因实现了本接口就宣称注册回滚具备数据库原子性。

## AgentScheduler 契约

```java
public final class AgentScheduler implements AutoCloseable {
    public AgentScheduler(
            TaskScheduler taskScheduler,
            ProfileRegistry profiles,
            AgentService agents,
            SessionManager sessions,
            ScheduledTaskStore store,
            Clock clock);

    public synchronized void registerAll();

    public synchronized void registerProfile(Profile profile);

    public synchronized void unregisterProfile(String profileName);

    @Override
    public synchronized void close();
}
```

执行回调由 Scheduler 内部构造，测试通过捕获 `TaskScheduler.schedule` 的 Runnable 调用，不额外公开生产用 `runOnce` API。

### registerAll

- 遍历 `ProfileRegistry.all()` 并复用 `registerProfile`。
- 重复调用对完全相同定义幂等。
- 任何 Profile 首次注册失败时向调用方抛出安全异常，使 Boot 启动失败；已完成的其他 Profile 注册不回滚。

### registerProfile

- 先构造并验证该 Profile 全部 definition 与 CronTrigger。
- 若该 Profile 已注册且定义集合完全相同，直接返回；集合比较与 YAML 排列顺序无关。
- 若已注册但定义集合不同，抛出 `IllegalStateException`，不改变旧注册。
- 首次登记按“reconcile -> schedule -> 暂存 future”执行；任一步失败都 cancel 本次 futures、retire 本次 definitions，并抛出不含 message 的异常。
- `TaskScheduler.schedule` 返回 null 视为注册失败。
- 每次注册使用独立 generation；全部 future 与内部状态提交后才激活。提交前被底层调度器调用的回调必须空操作。

### unregisterProfile

- 对该 Profile 所有 future 调用 `cancel(false)`，retire 活动 definitions，清除句柄。
- 先失活当前 generation，再取消 future；已出队但尚未开始业务调用的旧回调必须空操作。
- 不移除 `profileName -> lock`，不等待或中断正在执行的 Agent。
- 未注册 Profile 为幂等 no-op。

### 调度回调

- 对 profile lock 使用 `tryLock`；失败时只写安全 skip 日志并返回。
- 取得锁后记录 startedAt，调用 `sessions.getOrCreate("scheduler", "scheduler", profileName)`，再调用 `agents.process(session, message)`。
- 取得锁后再次验证 generation 仍为活动代次；该验证是回调开始与注销之间的线性化点。
- 成功/失败都构造一条 TaskExecution 并尝试 `store.recordExecution`。
- 系统时钟回拨时，finishedAt 钳制为不早于 startedAt；匿名异常使用首个非空父类简单名作为安全 errorType。
- Agent/Session 异常不从 Runnable 外抛；Store 异常单独记录。
- 所有路径在 finally 释放锁。

### close

- 取消全部未来触发并 retire 活动 definitions。
- 幂等且为终态；关闭后注册会被拒绝，不强制中断运行中的 Agent。

## Boot 装配契约

- 唯一 `ThreadPoolTaskScheduler`：pool size 4、线程前缀 `oryxos-scheduler-`、remove-on-cancel、等待当前任务完成。
- 默认 `ScheduledTaskStore` 仅在没有自定义 Bean 时提供 `InMemoryScheduledTaskStore`。
- `AgentScheduler` Bean 初始化时调用 `registerAll`，销毁时调用 `close`。
- 不使用任何业务 `@Scheduled` 方法。

