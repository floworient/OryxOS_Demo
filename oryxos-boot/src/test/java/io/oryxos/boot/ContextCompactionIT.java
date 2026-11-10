package io.oryxos.boot;

import static org.assertj.core.api.Assertions.assertThat;

import io.oryxos.core.agent.AgentService;
import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.core.context.ContextBudget;
import io.oryxos.core.context.PromptInputEstimator;
import io.oryxos.core.profile.AgentLoader;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.profile.ProfileRegistry;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.ToolCallIntent;
import io.oryxos.core.session.Session;
import io.oryxos.core.session.SessionManager;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.provider.DefaultProviderService;
import io.oryxos.provider.ProviderDefinition;
import io.oryxos.provider.ProviderRegistry;
import io.oryxos.storage.config.StorageConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

class ContextCompactionIT {

    private static final String LARGE_RESULT = "a".repeat(6144);

    private static final String SUMMARY = "保留用户的报告约束、关键事实与待办。";

    private static final String ANSWER = "处理完成";

    @TempDir
    Path workspace;

    @Test
    void boundsThirtyTwoTurnsPersistsCursorAndReducesEstimatedSpend() throws Exception {
        var definition = Files.createDirectories(workspace.resolve("agents/replay")).resolve("AGENT.md");
        Files.writeString(definition, """
                ---
                name: replay
                description: 费用回放
                identity: {agent_name: 回放助手, prompt: 执行回放}
                provider: {name: stub, model: replay}
                tools: [lookup]
                ---
                根据当前输入查询数据。
                """);
        String id;
        int cursor;
        var actual = new Meter();
        long baseline = 0;
        try (var first = app().run()) {
            var sessions = first.getBean(SessionManager.class);
            var session = sessions.getOrCreate("cli", "test", "replay");
            id = session.id();
            baseline += replay(first.getBean(AgentService.class), session, first.getBean(ReplayModel.class),
                    0, 24, actual);
            cursor = session.compaction().orElseThrow().processedUntil();
            assertThat(first.getBean(AtomicInteger.class)).hasValue(48);
        }
        try (var second = app().run()) {
            var sessions = second.getBean(SessionManager.class);
            var session = sessions.find(id).orElseThrow();
            assertThat(session.compaction().orElseThrow().processedUntil()).isEqualTo(cursor);
            assertThat(session.messages()).hasSize(120);
            var model = second.getBean(ReplayModel.class);
            baseline += replay(second.getBean(AgentService.class), session, model, 24, 32, actual);
            assertThat(model.summaryInputs).isNotEmpty();
            assertThat(model.summaryInputs.getFirst()).doesNotContain("case-000");
            assertThat(second.getBean(AtomicInteger.class)).hasValue(16);
            assertThat(sessions.details(id).orElseThrow().messages()).hasSize(160)
                    .anySatisfy(message -> {
                        assertThat(message).isInstanceOf(ToolResponseMessage.class);
                        assertThat(((ToolResponseMessage) message).getResponses().getFirst().responseData())
                                .contains(LARGE_RESULT);
                    });
            var jdbc = second.getBean(JdbcTemplate.class);
            assertThat(jdbc.queryForObject("select count(*) from llm_calls where purpose='SUMMARY'", Integer.class))
                    .isEqualTo(actual.summaryCalls);
            assertThat(jdbc.queryForObject("select count(*) from llm_calls where purpose='CHAT'", Integer.class))
                    .isEqualTo(64);
        }
        long baselineCost = 2 * baseline + 8L * 32 * (outputUnits(ANSWER) + outputUnits("a lookup {} b lookup {}"));
        long actualCost = 2 * actual.input + 8 * actual.output;
        System.out.printf("context-replay baseline_input=%d actual_input=%d summary_calls=%d "
                        + "baseline_cost=%d actual_cost=%d%n",
                baseline, actual.input, actual.summaryCalls, baselineCost, actualCost);
        assertThat(actual.input).isLessThan(baseline);
        assertThat(actualCost).isLessThan(baselineCost);
        assertThat(actual.summaryCalls).isBetween(1, 16);
    }

    private static long replay(AgentService agents, Session session, ReplayModel model,
            int from, int to, Meter actual) {
        long baseline = 0;
        for (int i = from; i < to; i++) {
            model.meter = actual;
            model.chatPrompts.clear();
            assertThat(agents.process(session, "case-%03d 报告约束".formatted(i))).isEqualTo(ANSWER);
            var history = session.messages();
            int start = Math.max(0, i - 19) * 5;
            assertThat(model.chatPrompts).hasSize(2);
            for (int call = 0; call < 2; call++) {
                Prompt request = model.chatPrompts.get(call);
                var original = new ArrayList<Message>(request.getInstructions().subList(0, 2));
                original.set(1, new SystemMessage("<oryxos-memory>\n</oryxos-memory>"));
                original.addAll(history.subList(start, i * 5 + (call == 0 ? 1 : 4)));
                baseline += PromptInputEstimator.estimate(new Prompt(original, request.getOptions()));
            }
        }
        return baseline;
    }

    private SpringApplication app() {
        var application = new SpringApplication(Fixture.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of(
                "oryxos.workspace", workspace.toString(),
                "spring.datasource.url", "jdbc:sqlite:" + workspace.resolve("runtime.db"),
                "spring.datasource.driver-class-name", "org.sqlite.JDBC",
                "spring.jpa.hibernate.ddl-auto", "validate",
                "spring.jpa.properties.hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect",
                "spring.flyway.locations", "classpath:db/migration/sqlite"));
        return application;
    }

    private static int outputUnits(String text) {
        return text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    static class Meter {
        long input;

        long output;

        int summaryCalls;
    }

    static class ReplayModel {
        final List<String> summaryInputs = new ArrayList<>();

        final List<Prompt> chatPrompts = new ArrayList<>();

        Meter meter;

        ChatResponse call(Prompt prompt) {
            long units = PromptInputEstimator.estimate(prompt);
            meter.input += units;
            var options = (ToolCallingChatOptions) prompt.getOptions();
            if (options.getToolCallbacks().isEmpty()) {
                assertThat(units).isLessThanOrEqualTo(ContextBudget.DEFAULT.summaryInputBudget());
                meter.summaryCalls++;
                meter.output += outputUnits(SUMMARY);
                summaryInputs.add(prompt.getInstructions().getLast().getText());
                return new ChatResponse(List.of(new Generation(new AssistantMessage(SUMMARY))));
            }
            assertThat(units).isLessThanOrEqualTo(ContextBudget.DEFAULT.mainInputBudget());
            chatPrompts.add(prompt);
            var active = prompt.getInstructions().reversed().stream()
                    .takeWhile(message -> !(message instanceof UserMessage))
                    .filter(ToolResponseMessage.class::isInstance).map(ToolResponseMessage.class::cast)
                    .flatMap(message -> message.getResponses().stream()).toList();
            if (active.isEmpty()) {
                meter.output += outputUnits("a lookup {} b lookup {}");
                return new ChatResponse(List.of(new Generation(AssistantMessage.builder().content("")
                        .toolCalls(List.of(new AssistantMessage.ToolCall("a", "function", "lookup", "{}"),
                                new AssistantMessage.ToolCall("b", "function", "lookup", "{}"))).build())));
            }
            assertThat(active).extracting(ToolResponseMessage.ToolResponse::id).containsExactly("b", "a");
            assertThat(active).allMatch(result -> result.responseData().contains("\"truncated\":true"));
            meter.output += outputUnits(ANSWER);
            return new ChatResponse(List.of(new Generation(new AssistantMessage(ANSWER))));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({StorageConfiguration.class, ToolConfiguration.class, MemoryConfiguration.class, ReActConfiguration.class})
    static class Fixture {
        @Bean
        ProfileRegistry profiles(@Value("${oryxos.workspace}") String workspace) {
            var registry = new ProfileRegistry();
            new AgentLoader().loadAll(Path.of(workspace)).forEach(registry::register);
            return registry;
        }

        @Bean
        AtomicInteger executions() {
            return new AtomicInteger();
        }

        @Bean
        OryxTool lookup(AtomicInteger executions) {
            return new OryxTool() {
                public String getName() {
                    return "lookup";
                }

                public String getDescription() {
                    return "回放查询";
                }

                public String getInputSchema() {
                    return "{\"type\":\"object\"}";
                }

                public ToolResult execute(String input) {
                    executions.incrementAndGet();
                    return new ToolResult(true, LARGE_RESULT, null, false);
                }
            };
        }

        @Bean
        BiConsumer<Profile, ToolCallIntent> policy() {
            return (profile, call) -> { };
        }

        @Bean
        ReplayModel replayModel() {
            return new ReplayModel();
        }

        @Bean
        ProviderService providerService(ReplayModel model, LlmCallRecorder recorder) {
            var definition = new ProviderDefinition("stub", ProviderDefinition.Type.MOCK, "replay", null, null);
            var registry = new ProviderRegistry(List.of(definition), Map.of("stub", model::call));
            return new DefaultProviderService(registry, recorder);
        }
    }
}
