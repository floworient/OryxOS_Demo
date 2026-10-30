package io.oryxos.tool.mcp;

import io.oryxos.core.tool.OryxTool;
import java.util.List;

public interface McpConnection extends AutoCloseable {
    List<OryxTool> listTools();

    @Override
    void close();
}
