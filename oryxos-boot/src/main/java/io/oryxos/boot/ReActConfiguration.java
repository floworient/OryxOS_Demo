package io.oryxos.boot;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.agent.ReActLoop;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.core.context.ContextLoader;
import io.oryxos.core.context.PromptBuilder;
import io.oryxos.core.memory.MemoryService;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.core.tool.ToolSource;
import io.oryxos.provider.ToolSchemaAdapter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReActConfiguration {

    private static final Set<String> DEFAULT_HTTP_TOOLS = Set.of("http_get", "notify");

    @Bean
    ContextLoader contextLoader(@Value("${oryxos.workspace:./.oryxos}") String workspace) {
        return new ContextLoader(Path.of(workspace));
    }

    @Bean
    PromptBuilder promptBuilder(ContextLoader loader, ToolRegistry tools, MemoryService memory) {
        return new PromptBuilder(loader, tools, new ToolSchemaAdapter()::adapt,
                memory, Clock.systemDefaultZone());
    }

    @Bean(destroyMethod = "close")
    ToolExecutor toolExecutor(ToolRegistry tools,
            ObjectProvider<BiConsumer<Profile, ToolCallIntent>> policies, ToolInvocationRecorder recorder) {
        BiConsumer<Profile, ToolCallIntent> policy = policies.getIfUnique(() -> {
            boolean noCustomPolicy = policies.stream().findAny().isEmpty();
            return (profile, call) -> {
                // 可见性由 ToolExecutor 先校验，HTTP 和通知仍执行目标白名单检查。
                if (noCustomPolicy && tools.find(call.name()).filter(tool ->
                        (tool.source() == ToolSource.BUILTIN && DEFAULT_HTTP_TOOLS.contains(call.name()))
                        || (tool.source() == ToolSource.JAVA && "memoryTools".equals(tool.sourceName())
                                && "save_memory".equals(call.name()))).isPresent()) {
                    return;
                }
                throw new SecurityException("尚未配置唯一的工具安全策略");
            };
        });
        return new ToolExecutor(tools, policy, recorder);
    }

    @Bean
    ReActLoop reActLoop(PromptBuilder prompts, ProviderService provider, ToolExecutor tools) {
        return new ReActLoop(prompts, provider, tools);
    }

    @Bean
    AgentService agentService(ProfileRegistry profiles, ReActLoop loop, SessionManager sessions) {
        return new AgentService(profiles, loop, sessions);
    }
}
