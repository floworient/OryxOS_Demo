package io.oryxos.memory;

import io.oryxos.core.memory.MemoryOwner;
import io.oryxos.core.profile.Profile;

/** 将已验证 Profile 映射为长期记忆隔离 owner。 */
public final class MemoryOwnerResolver {

    public MemoryOwner resolve(Profile profile) {
        return profile == null ? MemoryOwner.global() : MemoryOwner.agent(profile.name());
    }
}
