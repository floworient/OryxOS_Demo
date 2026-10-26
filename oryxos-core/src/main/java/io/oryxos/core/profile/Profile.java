package io.oryxos.core.profile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record Profile(
        String name,
        String description,
        Identity identity,
        ProviderRef provider,
        List<String> tools,
        List<String> mcpServers,
        List<ChannelRef> channels,
        List<ScheduleRef> schedules,
        List<String> bootstrap,
        Settings settings,
        Path sourcePath) {

    public Profile {
        tools = List.copyOf(tools);
        mcpServers = List.copyOf(mcpServers);
        channels = List.copyOf(channels);
        schedules = List.copyOf(schedules);
        bootstrap = List.copyOf(bootstrap);
        sourcePath = sourcePath.toAbsolutePath().normalize();
    }

    public record Identity(String agentName, String prompt) {
    }

    public record ProviderRef(String name, String model, Double temperature) {
    }

    public record ChannelRef(String name, Map<String, Object> config) {
        public ChannelRef {
            config = Map.copyOf(config);
        }
    }

    public record ScheduleRef(String key, String cron, String zone, String message) {
    }

    public record Settings(int maxIterations, int maxHistoryTurns) {
    }
}
