package io.oryxos.boot;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.agent.ReActLoop;
import io.oryxos.core.audit.ToolInvocationRecorder;
import io.oryxos.core.context.ContextLoader;
import io.oryxos.core.context.PromptBuilder;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolExecutor;
import io.oryxos.provider.ToolSchemaAdapter;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ReActConfiguration {

    @Bean
    Map<String, OryxTool> runtimeTools(List<OryxTool> registered) {
        var tools = new LinkedHashMap<String, OryxTool>();
        for (OryxTool tool : registered) {
            if (tool.getName() == null || tool.getName().isBlank()
                    || tools.putIfAbsent(tool.getName(), tool) != null) {
                throw new IllegalArgumentException("工具名称为空或重复");
            }
        }
        return Map.copyOf(tools);
    }

    @Bean
    ContextLoader contextLoader(@Value("${oryxos.workspace:./.oryxos}") String workspace) {
        return new ContextLoader(Path.of(workspace));
    }

    @Bean
    PromptBuilder promptBuilder(ContextLoader loader, @Qualifier("runtimeTools") Map<String, OryxTool> tools) {
        return new PromptBuilder(loader, tools, new ToolSchemaAdapter()::adapt,
                (session, profile) -> "", Clock.systemDefaultZone());
    }

    @Bean(destroyMethod = "close")
    ToolExecutor toolExecutor(@Qualifier("runtimeTools") Map<String, OryxTool> tools,
            ObjectProvider<BiConsumer<Profile, ToolCallIntent>> policies, ToolInvocationRecorder recorder) {
        BiConsumer<Profile, ToolCallIntent> policy = policies.getIfUnique(() -> (profile, call) -> {
            throw new SecurityException("尚未配置工具安全策略");
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
