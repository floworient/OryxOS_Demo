package io.oryxos.tool.sandbox;

import java.net.URI;
import java.nio.file.Path;

@FunctionalInterface
public interface Sandbox {
    URI enforce(HttpRequestAction action);

    default Path enforce(FileAccessAction action) {
        throw new SecurityException("Sandbox 未配置文件访问能力");
    }

    default Path enforce(ShellExecuteAction action) {
        throw new SecurityException("Sandbox 未配置 Shell 执行能力");
    }
}
