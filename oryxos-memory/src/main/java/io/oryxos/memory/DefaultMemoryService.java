package io.oryxos.memory;

import io.oryxos.core.memory.LongTermMemoryStore;
import io.oryxos.core.memory.MemoryCapabilities;
import io.oryxos.core.memory.MemoryContext;
import io.oryxos.core.memory.MemoryEntry;
import io.oryxos.core.memory.MemoryException;
import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.memory.MemoryScope;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.memory.MemorySnapshot;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import java.util.List;
import java.util.Objects;

/** 默认 Memory 门面，统一 Session 快照与长期记忆后端路由。 */
public final class DefaultMemoryService implements MemoryService {

    private final LongTermMemoryStore store;

    private final MemoryOwnerResolver owners;

    public DefaultMemoryService(LongTermMemoryStore store, MemoryOwnerResolver owners) {
        this.store = Objects.requireNonNull(store, "长期记忆后端不能为空");
        this.owners = Objects.requireNonNull(owners, "记忆 owner 解析器不能为空");
        if (store.capabilities() == MemoryCapabilities.HYBRID_BUILTIN) {
            throw new MemoryException(MemoryException.Code.UNSUPPORTED_CAPABILITY,
                    "内置混合检索需要安装 015 Memory Retrieval 组件");
        }
    }

    @Override
    public MemoryContext load(Session session, Profile profile) {
        Objects.requireNonNull(session, "Session 不能为空");
        return new MemoryContext(store.load(owner(profile)), session.messages());
    }

    @Override
    public void save(Profile profile, String content, MemoryScope scope) {
        store.append(owner(profile), content, scope);
    }

    @Override
    public List<MemoryEntry> recall(Profile profile, String keyword) {
        return store.recallByKeyword(owner(profile), keyword);
    }

    @Override
    public MemorySnapshot loadLongTerm(Profile profile) {
        return store.load(owner(profile));
    }

    private MemoryOwner owner(Profile profile) {
        return owners.resolve(profile);
    }
}
