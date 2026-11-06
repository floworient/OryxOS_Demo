package io.oryxos.web.tool;

import io.oryxos.core.tool.ToolRegistration;

public record ToolView(String name, String description, String source, String sourceName) {

    static ToolView from(ToolRegistration registration) {
        return new ToolView(registration.name(), registration.tool().getDescription(),
                registration.source().name(), registration.sourceName());
    }
}
