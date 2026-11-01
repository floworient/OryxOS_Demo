package io.oryxos.core.memory;

import io.oryxos.core.profile.AgentName;

public record MemoryOwner(String key, boolean agentScoped) {

    private static final String GLOBAL_KEY = "__global__";

    public MemoryOwner {
        if (agentScoped) {
            AgentName.requireValid(key);
            if (GLOBAL_KEY.equals(key)) {
                throw new IllegalArgumentException("Agent 名称不能使用全局保留值");
            }
        }
        else if (!GLOBAL_KEY.equals(key)) {
            throw new IllegalArgumentException("非 Agent 记忆只能使用全局保留值");
        }
    }

    public static MemoryOwner agent(String profileName) {
        if (GLOBAL_KEY.equals(profileName)) {
            throw new IllegalArgumentException("Agent 名称不能使用全局保留值");
        }
        return new MemoryOwner(profileName, true);
    }

    public static MemoryOwner global() {
        return new MemoryOwner(GLOBAL_KEY, false);
    }
}
