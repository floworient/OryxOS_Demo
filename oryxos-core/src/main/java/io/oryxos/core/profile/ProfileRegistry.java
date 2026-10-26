package io.oryxos.core.profile;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ProfileRegistry {

    private final Map<String, Profile> profiles = new LinkedHashMap<>();

    public synchronized void register(Profile profile) {
        if (profiles.putIfAbsent(profile.name(), profile) != null) {
            throw new IllegalArgumentException("Agent 名称重复: " + profile.name());
        }
    }

    public synchronized Optional<Profile> remove(String name) {
        return Optional.ofNullable(profiles.remove(name));
    }

    public synchronized Optional<Profile> find(String name) {
        return Optional.ofNullable(profiles.get(name));
    }

    public synchronized Collection<Profile> all() {
        return java.util.List.copyOf(profiles.values());
    }

    public synchronized Set<String> referencedProviderNames() {
        Set<String> names = new LinkedHashSet<>();
        profiles.values().forEach(profile -> names.add(profile.provider().name()));
        return Set.copyOf(names);
    }
}
