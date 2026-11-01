package io.oryxos.core.memory;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.session.Session;
import java.util.List;

public interface MemoryService {

    MemoryContext load(Session session, Profile profile);

    void save(Profile profile, String content, MemoryScope scope);

    List<MemoryEntry> recall(Profile profile, String keyword);

    MemorySnapshot loadLongTerm(Profile profile);
}
