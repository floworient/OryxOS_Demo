package io.oryxos.boot;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("oryxos.tool")
public class ToolProperties {

    private Map<String, String> allowedCommands = new LinkedHashMap<>();

    public Map<String, String> getAllowedCommands() {
        return Map.copyOf(allowedCommands);
    }

    public void setAllowedCommands(Map<String, String> allowedCommands) {
        this.allowedCommands = allowedCommands == null
                ? new LinkedHashMap<>() : new LinkedHashMap<>(allowedCommands);
    }
}
