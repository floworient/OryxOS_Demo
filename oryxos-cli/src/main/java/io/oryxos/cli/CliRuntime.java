package io.oryxos.cli;

import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Path;
import java.util.List;

/** CLI 与 Spring 运行时之间的窄接口。 */
public interface CliRuntime {
    void chat(Path workspace, String profileName, String userId, Reader input, PrintWriter output);

    void serve(Path workspace, int port);

    void gateway(Path workspace);

    List<ProviderInfo> providers(Path workspace);

    List<ToolInfo> tools(Path workspace);

    List<SessionInfo> sessions(Path workspace);
}
