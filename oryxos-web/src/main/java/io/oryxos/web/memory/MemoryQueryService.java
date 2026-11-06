package io.oryxos.web.memory;

import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.web.api.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public final class MemoryQueryService {

    private final ProfileRegistry profiles;

    private final MemoryService memory;

    public MemoryQueryService(ProfileRegistry profiles, MemoryService memory) {
        this.profiles = profiles;
        this.memory = memory;
    }

    public MemoryView get(String name) {
        var profile = profiles.find(name).orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "AGENT_NOT_FOUND", "Agent 不存在"));
        return MemoryView.from(memory.loadLongTerm(profile));
    }
}
