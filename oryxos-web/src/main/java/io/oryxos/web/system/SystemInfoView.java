package io.oryxos.web.system;

import java.util.List;

public record SystemInfoView(String applicationName, String implementationVersion,
        String javaVersion, int profileCount, int toolCount, List<String> providers) {

    public SystemInfoView {
        providers = List.copyOf(providers);
    }
}
