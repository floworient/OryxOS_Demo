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
import java.util.function.BiConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReActConfiguration {

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
                // 内置 GET 在发出请求前仍会校验 HTTP 域名白名单。
                if (noCustomPolicy && "http_get".equals(call.name())
                        && tools.find("http_get").filter(tool -> tool.source() == ToolSource.BUILTIN).isPresent()) {
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
