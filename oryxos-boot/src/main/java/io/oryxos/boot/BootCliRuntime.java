package io.oryxos.boot;

import io.oryxos.OryxOsApplication;
import io.oryxos.channel.cli.CliChannel;
import io.oryxos.cli.CliRuntime;
import io.oryxos.cli.ProviderInfo;
import io.oryxos.cli.SessionInfo;
import io.oryxos.cli.ToolInfo;
import io.oryxos.core.agent.AgentService;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.provider.ProviderProperties;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

/** 使用 Spring Boot 真实装配实现 CLI 的运行时操作。 */
public final class BootCliRuntime implements CliRuntime, AutoCloseable {
    private final Class<?> source;

    private final Consumer<String> reporter;

    private final List<ConfigurableApplicationContext> activeContexts = new CopyOnWriteArrayList<>();

    public BootCliRuntime() {
        this(OryxOsApplication.class, System.out::println);
    }

    public BootCliRuntime(Class<?> source) {
        this(source, System.out::println);
    }

    public BootCliRuntime(Class<?> source, Consumer<String> reporter) {
        this.source = source;
        this.reporter = reporter;
    }

    @Override
    public void chat(Path workspace, String profileName, String userId, Reader input,
            PrintWriter output) {
        SpringApplication application = application(WebApplicationType.NONE, workspace);
        try (var context = application.run(workspaceArgument(workspace))) {
            var channel = new CliChannel(context.getBean(AgentService.class),
                    context.getBean(SessionManager.class), input, output, userId);
            channel.run(profileName);
        }
    }

    @Override
    public void serve(Path workspace, int port) {
        ConfigurableApplicationContext context = startServlet(workspace, port, true);
        reportOrClose(context, () -> "HTTP 服务已启动: port=" + port);
    }

    @Override
    public void gateway(Path workspace) {
        ConfigurableApplicationContext context = startServlet(workspace, 8080, false);
        reportOrClose(context, () -> "聚合运行时已启动，组件: "
                + String.join(", ", components(context)));
    }

    @Override
    public List<ProviderInfo> providers(Path workspace) {
        SpringApplication application = new SpringApplication(ProviderQueryConfiguration.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        configureQuery(application, workspace);
        try (var context = application.run()) {
            return context.getBean(ProviderProperties.class).providers().stream()
                    .map(definition -> new ProviderInfo(definition.name(), definition.type().name(),
                            definition.model(), definition.baseUrl()))
                    .sorted(java.util.Comparator.comparing(ProviderInfo::name))
                    .toList();
        }
    }

    @Override
    public List<ToolInfo> tools(Path workspace) {
        SpringApplication application = queryApplication(workspace);
        try (var context = application.run(workspaceArgument(workspace))) {
            Map<?, ?> registered = context.getBean("runtimeTools", Map.class);
            return registered.values().stream()
                    .map(OryxTool.class::cast)
                    .map(tool -> new ToolInfo(tool.getName(), tool.getDescription()))
                    .sorted(java.util.Comparator.comparing(ToolInfo::name))
                    .toList();
        }
    }

    @Override
    public List<SessionInfo> sessions(Path workspace) {
        SpringApplication application = queryApplication(workspace);
        try (var context = application.run(workspaceArgument(workspace))) {
            return context.getBean(SessionManager.class).list().stream()
                    .map(summary -> new SessionInfo(summary.id(), summary.profileName(),
                            summary.status(), summary.lastActiveAt()))
                    .toList();
        }
    }

    @Override
    public void close() {
        var contexts = new ArrayList<>(activeContexts);
        java.util.Collections.reverse(contexts);
        RuntimeException failure = null;
        for (ConfigurableApplicationContext context : contexts) {
            try {
                context.close();
            }
            catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                }
                else {
                    failure.addSuppressed(exception);
                }
            }
            finally {
                activeContexts.remove(context);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private SpringApplication application(WebApplicationType webType, Path workspace) {
        SpringApplication application = new SpringApplication(source);
        application.setWebApplicationType(webType);
        application.setRegisterShutdownHook(false);
        application.setDefaultProperties(baseProperties(workspace));
        return application;
    }

    private ConfigurableApplicationContext startServlet(Path workspace, int port, boolean explicitPort) {
        SpringApplication application = application(WebApplicationType.SERVLET, workspace);
        Map<String, Object> properties = baseProperties(workspace);
        properties.put("server.port", port);
        application.setDefaultProperties(properties);
        String[] arguments = explicitPort
                ? new String[] {workspaceArgument(workspace), "--server.port=" + port}
                : new String[] {workspaceArgument(workspace)};
        ConfigurableApplicationContext context = application.run(arguments);
        activeContexts.add(context);
        return context;
    }

    private SpringApplication queryApplication(Path workspace) {
        SpringApplication application = new SpringApplication(source);
        application.setWebApplicationType(WebApplicationType.NONE);
        configureQuery(application, workspace);
        return application;
    }

    private static void configureQuery(SpringApplication application, Path workspace) {
        application.setRegisterShutdownHook(false);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        Map<String, Object> properties = baseProperties(workspace);
        properties.put("logging.level.root", "ERROR");
        application.setDefaultProperties(properties);
    }

    private void reportOrClose(ConfigurableApplicationContext context, Supplier<String> message) {
        try {
            reporter.accept(message.get());
        }
        catch (RuntimeException exception) {
            activeContexts.remove(context);
            context.close();
            throw exception;
        }
    }

    private static Map<String, Object> baseProperties(Path workspace) {
        Path normalized = workspace.toAbsolutePath().normalize();
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("oryxos.workspace", normalized.toString());
        properties.put("spring.config.additional-location",
                "optional:" + normalized.resolve("application.yaml").toUri());
        return properties;
    }

    private static String workspaceArgument(Path workspace) {
        return "--oryxos.workspace=" + workspace.toAbsolutePath().normalize();
    }

    private static List<String> components(ConfigurableApplicationContext context) {
        var names = new ArrayList<String>();
        names.add("http");
        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> type = context.getType(beanName, false);
            if (type != null && type.getSimpleName().endsWith("ChannelAdapter")) {
                names.add(type.getSimpleName());
            }
        }
        return names.stream().distinct().sorted().toList();
    }

    int activeContextCount() {
        return activeContexts.size();
    }

    Optional<Path> activeWorkspace() {
        return activeContexts.stream().findFirst().map(context -> Path.of(
                context.getEnvironment().getRequiredProperty("oryxos.workspace")));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ProviderProperties.class)
    static class ProviderQueryConfiguration { }
}
