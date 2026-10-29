package io.oryxos.tool.registry;

import io.oryxos.core.profile.Profile;
import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolSource;
import com.fasterxml.jackson.databind.DeserializationFeature;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.util.json.JsonParser;

public final class DefaultToolRegistry implements ToolRegistry {

    private final Map<String, ToolRegistration> registrations = new LinkedHashMap<>();

    @Override
    public synchronized void register(ToolRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        validateMetadata(registration);
        var previous = registrations.putIfAbsent(registration.name(), registration);
        if (previous != null) {
            throw new IllegalStateException("工具名称重复注册: " + registration.name());
        }
    }

    @Override
    public synchronized Optional<ToolRegistration> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(registrations.get(name));
    }

    @Override
    public synchronized List<ToolRegistration> registrations() {
        return registrations.values().stream()
                .sorted(java.util.Comparator.comparing(ToolRegistration::name))
                .toList();
    }

    @Override
    public synchronized List<ToolRegistration> visibleTo(Profile profile) {
        Objects.requireNonNull(profile, "profile");
        return registrations().stream()
                .filter(registration -> visible(registration, profile))
                .toList();
    }

    private static void validateMetadata(ToolRegistration registration) {
        var tool = registration.tool();
        if (tool.getDescription() == null || tool.getDescription().isBlank()) {
            throw new IllegalArgumentException("工具说明不能为空: " + registration.name());
        }
        try {
            Object schema = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(tool.getInputSchema());
            if (!(schema instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("工具 input schema 必须是 JSON object: " + registration.name());
            }
        }
        catch (java.io.IOException | RuntimeException exception) {
            if (exception instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("工具 input schema 非法: " + registration.name(), exception);
        }
    }

    private static boolean visible(ToolRegistration registration, Profile profile) {
        if (registration.source() == ToolSource.MCP) {
            return profile.mcpServers().contains(registration.sourceName());
        }
        return profile.tools().contains(registration.name());
    }
}
