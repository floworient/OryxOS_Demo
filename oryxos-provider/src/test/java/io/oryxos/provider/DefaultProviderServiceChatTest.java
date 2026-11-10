package io.oryxos.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallPurpose;
import io.oryxos.core.profile.Profile;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;

class DefaultProviderServiceChatTest {

    @Test
    void sendsTheOutputLimitAsCompletionTokensForDirectOpenAi() {
        var definition = new ProviderDefinition("official", ProviderDefinition.Type.OPENAI_COMPATIBLE,
                "o3", "https://api.openai.com", "test-key");
        var model = StubChatModel.text("ok", "o3");
        var provider = new DefaultProviderService(new ProviderRegistry(List.of(definition), Map.of("official", model)),
                record -> record);
        var options = DefaultToolCallingChatOptions.builder().maxTokens(384).build();
        provider.chat("s", profile("agent", "official", "o3", null), new Prompt("输入", options));
        assertThat(model.lastPrompt().getOptions()).isInstanceOfSatisfying(
                org.springframework.ai.openai.OpenAiChatOptions.class, captured -> {
                    assertThat(captured.getMaxCompletionTokens()).isEqualTo(384);
                    assertThat(captured.getMaxTokens()).isNull();
                    assertThat(captured.getInternalToolExecutionEnabled()).isFalse();
                });
    }

    @Test
    void auditsExplicitModelCancellationAndPropagatesItToRuntime() {
        var cancellation = new CancellationException("stop");
        var audit = new ArrayList<LlmCallRecord>();
        var service = service(Map.of("alpha", request -> { throw cancellation; }), audit);
        var profile = profile("agent", "alpha", "model", null);
        assertThatThrownBy(() -> service.chat("session-1", profile, new Prompt("old history"),
                profile.provider(), LlmCallPurpose.SUMMARY)).isSameAs(cancellation);
        assertThat(audit).singleElement().satisfies(record -> {
            assertThat(record.purpose()).isEqualTo(LlmCallPurpose.SUMMARY);
            assertThat(record.errorCode()).isEqualTo("CANCELLED");
        });
    }

    @Test
    void mapsResponseAndWritesAudit() {
        var model = StubChatModel.text("你好", "stub-model");
        var audit = new ArrayList<LlmCallRecord>();
        var service = service(Map.of("alpha", model), audit);

        var result = service.chat("session-1", profile("agent-a", "alpha", "agent-model", 0.3),
                new Prompt("hello"));

        assertThat(result.providerName()).isEqualTo("alpha");
        assertThat(result.model()).isEqualTo("agent-model");
        assertThat(result.content()).isEqualTo("你好");
        assertThat(result.usage().promptTokens()).isEqualTo(7);
        assertThat(result.usage().completionTokens()).isEqualTo(5);
        assertThat(result.usage().totalTokens()).isEqualTo(12);
        assertThat(audit).singleElement().satisfies(record -> {
            assertThat(record.sessionId()).isEqualTo("session-1");
            assertThat(record.profileName()).isEqualTo("agent-a");
            assertThat(record.provider()).isEqualTo("alpha");
            assertThat(record.model()).isEqualTo("agent-model");
        });
    }

    @Test
    void requestOptionsDoNotLeakAcrossConcurrentAgents() {
        var alpha = StubChatModel.text("a", "alpha-default");
        var beta = StubChatModel.text("b", "beta-default");
        var audit = Collections.synchronizedList(new ArrayList<LlmCallRecord>());
        var service = service(Map.of("alpha", alpha, "beta", beta), audit);

        var first = CompletableFuture.supplyAsync(() -> service.chat(null,
                profile("agent-a", "alpha", "model-a", 0.1), new Prompt("a")));
        var second = CompletableFuture.supplyAsync(() -> service.chat(null,
                profile("agent-b", "beta", "model-b", 0.9), new Prompt("b")));

        assertThat(first.join().content()).isEqualTo("a");
        assertThat(second.join().content()).isEqualTo("b");
        assertThat(alpha.lastPrompt().getOptions().getModel()).isEqualTo("model-a");
        assertThat(alpha.lastPrompt().getOptions().getTemperature()).isEqualTo(0.1);
        assertThat(beta.lastPrompt().getOptions().getModel()).isEqualTo("model-b");
        assertThat(beta.lastPrompt().getOptions().getTemperature()).isEqualTo(0.9);
        assertThat(audit).hasSize(2);
        assertThat(alpha.lastPrompt().getOptions()).isInstanceOfSatisfying(
                DefaultToolCallingChatOptions.class,
                options -> assertThat(options.getInternalToolExecutionEnabled()).isFalse());
    }

    private static DefaultProviderService service(
            Map<String, org.springframework.ai.chat.model.ChatModel> models,
            List<LlmCallRecord> audit) {
        var definitions = models.keySet().stream()
                .map(name -> new ProviderDefinition(name, ProviderDefinition.Type.DASHSCOPE,
                        name + "-default", null, "test-key"))
                .toList();
        return new DefaultProviderService(new ProviderRegistry(definitions, models), record -> {
            audit.add(record);
            return record;
        });
    }

    static Profile profile(String name, String provider, String model, Double temperature) {
        return new Profile(name, "test", new Profile.Identity(name, "prompt"),
                new Profile.ProviderRef(provider, model, temperature), List.of(), List.of(), List.of(),
                List.of(), List.of(), new Profile.Settings(10, 20), Path.of(name, "AGENT.md"));
    }
}
