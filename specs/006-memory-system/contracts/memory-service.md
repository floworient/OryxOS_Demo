# Contract: MemoryService 与 LongTermMemoryStore

## Core types

```java
public enum MemoryScope { CORE, ARCHIVAL }

public enum MemoryCapabilities { KEYWORD, HYBRID_BUILTIN, DELEGATED }

public record MemoryOwner(String key, boolean agentScoped) {
    public static MemoryOwner agent(String profileName);
    public static MemoryOwner global();
}

public record MemoryEntry(String id, MemoryOwner owner, MemoryScope scope,
        String content, Instant createdAt) { }

public record MemorySnapshot(MemoryOwner owner, List<MemoryEntry> core,
        List<MemoryEntry> archival, boolean archivalTruncated,
        String backend, MemoryCapabilities capabilities) { }

public record MemoryContext(MemorySnapshot longTerm, List<Message> sessionMessages) { }
```

## Store

```java
public interface LongTermMemoryStore {
    void append(MemoryOwner owner, String content, MemoryScope scope);
    MemorySnapshot load(MemoryOwner owner);
    List<MemoryEntry> recallByKeyword(MemoryOwner owner, String keyword);
    MemoryCapabilities capabilities();
    List<MemoryEntry> archivalEntries(MemoryOwner owner);
}
```

## Facade

```java
public interface MemoryService {
    MemoryContext load(Session session, Profile profile);
    void save(Profile profile, String content, MemoryScope scope);
    List<MemoryEntry> recall(Profile profile, String keyword);
    MemorySnapshot loadLongTerm(Profile profile);
}
```

## Error contract

`MemoryException` 只携带稳定错误码与脱敏消息：`INVALID_OWNER`、`INVALID_CONTENT`、`INVALID_SCOPE`、`STORE_UNAVAILABLE`、`MALFORMED_STORE`、`UNSUPPORTED_CAPABILITY`。

## Behavioral invariants

- 所有返回集合不可变且稳定排序。
- core 不参与 `recallByKeyword`。
- `load` 的 archival 有界，`archivalEntries` 未裁剪。
- `DELEGATED.archivalEntries` 可为空。
- `HYBRID_BUILTIN` 在 015 前不得运行。
