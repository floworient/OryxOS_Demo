package io.oryxos.boot;

import io.oryxos.core.context.SkillBindingResolver;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolRegistration;
import io.oryxos.core.tool.ToolRegistry;
import io.oryxos.core.tool.ToolSource;
import io.oryxos.tool.adapter.AnnotatedToolDiscovery;
import io.oryxos.tool.builtin.FileTools;
import io.oryxos.tool.builtin.HttpTools;
import io.oryxos.tool.builtin.InteractionTools;
import io.oryxos.tool.builtin.ShellTools;
import io.oryxos.tool.builtin.WebSearchTools;
import io.oryxos.tool.mcp.McpClientService;
import io.oryxos.tool.mcp.McpConfigLoader;
import io.oryxos.tool.mcp.SpringMcpClientFactory;
import io.oryxos.tool.registry.DefaultToolRegistry;
import io.oryxos.tool.sandbox.Sandbox;
import io.oryxos.tool.sandbox.WhitelistSandbox;
import io.oryxos.tool.search.DuckDuckGoInstantAnswerSearchProvider;
import io.oryxos.tool.search.SearchProvider;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({HttpSandboxProperties.class, ToolProperties.class})
public class ToolConfiguration {

    @Bean("toolSandbox")
    Sandbox toolSandbox(HttpSandboxProperties http, ToolProperties tools,
            @org.springframework.beans.factory.annotation.Value("${oryxos.workspace:./.oryxos}")
            String workspace) {
        var commands = new java.util.LinkedHashMap<String, Path>();
        tools.getAllowedCommands().forEach((alias, executable) -> commands.put(alias, Path.of(executable)));
        return new WhitelistSandbox(http.getAllowedDomains(), commands,
                new SkillBindingResolver(Path.of(workspace)));
    }

    @Bean
    @ConditionalOnMissingBean(SearchProvider.class)
    SearchProvider searchProvider(@Qualifier("toolSandbox") Sandbox sandbox) {
        return new DuckDuckGoInstantAnswerSearchProvider(sandbox);
    }

    @Bean
    ToolRegistry runtimeTools(List<OryxTool> springTools,
            @Qualifier("toolSandbox") Sandbox sandbox, SearchProvider searchProvider) {
        var registry = new DefaultToolRegistry();
        springTools.forEach(tool -> registry.register(
                new ToolRegistration(tool, ToolSource.BUILTIN, "builtin")));
        var builtins = new ArrayList<OryxTool>();
        builtins.addAll(new FileTools(sandbox).all());
        builtins.add(new ShellTools(sandbox).tool());
        builtins.addAll(new HttpTools(sandbox).all());
        builtins.add(new InteractionTools().tool());
        builtins.add(new WebSearchTools(searchProvider).tool());
        builtins.forEach(tool -> registry.register(
                new ToolRegistration(tool, ToolSource.BUILTIN, "builtin")));
        return registry;
    }

    @Bean
    SmartInitializingSingleton annotatedToolRegistrar(ConfigurableListableBeanFactory beans,
            ToolRegistry registry) {
        return () -> {
            var discovery = new AnnotatedToolDiscovery();
            for (String beanName : beans.getBeanDefinitionNames()) {
                Class<?> type = beans.getType(beanName, false);
                if (type == null || !hasAnnotatedMethod(type)) {
                    continue;
                }
                for (OryxTool tool : discovery.discover(List.of(beans.getBean(beanName)))) {
                    registry.register(new ToolRegistration(tool, ToolSource.JAVA, beanName));
                }
            }
        };
    }

    @Bean(destroyMethod = "close")
    McpClientService mcpClientService(ToolRegistry registry,
            @Qualifier("toolSandbox") Sandbox sandbox, Environment environment,
            @org.springframework.beans.factory.annotation.Value("${oryxos.workspace:./.oryxos}")
            String workspace) {
        var service = new McpClientService(new SpringMcpClientFactory(), registry);
        var loader = new McpConfigLoader(Path.of(workspace), sandbox, environment::getProperty);
        service.initialize(loader.load());
        return service;
    }

    private static boolean hasAnnotatedMethod(Class<?> candidate) {
        Class<?> type = ClassUtils.getUserClass(candidate);
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(Tool.class));
    }
}
