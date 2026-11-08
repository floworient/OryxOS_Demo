package io.oryxos.core.tool;

import java.util.List;

/** 供控制台读取的沙箱有效规则摘要。 */
@FunctionalInterface
public interface SandboxAllowlistReader {

    Summary read();

    record Summary(List<String> httpDomains, int fileRootCount, List<String> commandAliases) {

        public Summary {
            httpDomains = List.copyOf(httpDomains);
            commandAliases = List.copyOf(commandAliases);
        }
    }
}
