package io.oryxos.core.tool;

import io.oryxos.core.profile.Profile;
import java.util.List;
import java.util.Optional;

public interface ToolRegistry {

    void register(ToolRegistration registration);

    Optional<ToolRegistration> find(String name);

    List<ToolRegistration> registrations();

    List<ToolRegistration> visibleTo(Profile profile);
}
