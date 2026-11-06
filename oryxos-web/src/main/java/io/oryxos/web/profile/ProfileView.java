package io.oryxos.web.profile;

import io.oryxos.core.profile.Profile;
import java.util.List;

public record ProfileView(String name, String description, String agentName,
        String providerName, String model, List<String> tools, List<String> mcpServers) {

    public ProfileView {
        tools = List.copyOf(tools);
        mcpServers = List.copyOf(mcpServers);
    }

    static ProfileView from(Profile profile) {
        return new ProfileView(profile.name(), profile.description(), profile.identity().agentName(),
                profile.provider().name(), profile.provider().model(),
                profile.tools(), profile.mcpServers());
    }
}
