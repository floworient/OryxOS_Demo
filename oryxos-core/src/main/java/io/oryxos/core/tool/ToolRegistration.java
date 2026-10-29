package io.oryxos.core.tool;

import java.util.Objects;

public record ToolRegistration(OryxTool tool, ToolSource source, String sourceName) {

    public ToolRegistration {
        Objects.requireNonNull(tool, "tool");
        Objects.requireNonNull(source, "source");
        if (tool.getName() == null || tool.getName().isBlank()) {
            throw new IllegalArgumentException("工具名称不能为空");
        }
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("工具来源名称不能为空");
        }
    }

    public String name() {
        return tool.getName();
    }
}
