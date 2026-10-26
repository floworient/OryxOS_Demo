package io.oryxos.provider;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallRecorder;
import io.oryxos.core.audit.LlmCallStatus;
import io.oryxos.core.profile.Profile;
import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.core.provider.ProviderResponse;
import io.oryxos.core.provider.ProviderService;
import io.oryxos.core.provider.TokenUsage;
import io.oryxos.core.provider.ToolCallIntent;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.DefaultToolCallingChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

public final class DefaultProviderService implements ProviderService {

    private final ProviderRegistry registry;

    private final LlmCallRecorder recorder;

    public DefaultProviderService(ProviderRegistry registry, LlmCallRecorder recorder) {
        this.registry = registry;
        this.recorder = recorder;
    }

    @Override
    public ProviderResponse chat(String sessionId, Profile profile, Prompt prompt) {
        var providerName = profile.provider().name();
        var modelName = profile.provider().model();
        var chatModel = registry.resolve(providerName, modelName);
        var request = requestPrompt(prompt, modelName, profile.provider().temperature());
        var started = System.nanoTime();
        try {
            var response = chatModel.call(request);
            var durationMs = elapsedMillis(started);
            var generation = response == null ? null : response.getResult();
            var output = generation == null ? null : generation.getOutput();
            var responseText = output == null ? null : output.getText();
            var content = responseText == null ? "" : responseText;
            var usage = response == null || response.getMetadata() == null
                    ? TokenUsage.UNKNOWN : tokenUsage(response.getMetadata().getUsage());
            var calls = output == null ? List.<ToolCallIntent>of() : output.getToolCalls().stream()
                    .map(call -> new ToolCallIntent(call.id(), call.name(), call.arguments()))
                    .toList();
            var status = content.isBlank() ? LlmCallStatus.EMPTY_RESPONSE : LlmCallStatus.SUCCESS;
            persist(new LlmCallRecord(null, sessionId, profile.name(), providerName, modelName,
                    usage.promptTokens(), usage.completionTokens(), usage.totalTokens(), usage.isKnown(),
                    durationMs, status,
                    status == LlmCallStatus.EMPTY_RESPONSE ? ProviderErrorCode.EMPTY_RESPONSE.name() : null,
                    null, Instant.now()), providerName, modelName);
            return new ProviderResponse(providerName, modelName, content, usage, calls, durationMs);
        }
        catch (ProviderException exception) {
            if (exception.errorCode() == ProviderErrorCode.AUDIT_PERSISTENCE_FAILED) {
                throw exception;
            }
            var durationMs = elapsedMillis(started);
            persist(new LlmCallRecord(null, sessionId, profile.name(), providerName, modelName,
                    null, null, null, false, durationMs, LlmCallStatus.FAILED,
                    exception.errorCode().name(), exception.getMessage(), Instant.now()),
                    providerName, modelName);
            throw exception;
        }
        catch (RuntimeException exception) {
            var durationMs = elapsedMillis(started);
            var mapped = mapFailure(providerName, modelName, exception);
            persist(new LlmCallRecord(null, sessionId, profile.name(), providerName, modelName,
                    null, null, null, false, durationMs, LlmCallStatus.FAILED,
                    mapped.errorCode().name(), mapped.getMessage(), Instant.now()), providerName, modelName);
            throw mapped;
        }
    }

    private static Prompt requestPrompt(Prompt prompt, String model, Double temperature) {
        ChatOptions source = prompt.getOptions();
        ChatOptions copied = source == null ? null : source.copy();
        var builder = DefaultToolCallingChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .internalToolExecutionEnabled(false);
        if (copied != null) {
            builder.frequencyPenalty(copied.getFrequencyPenalty())
                    .maxTokens(copied.getMaxTokens())
                    .presencePenalty(copied.getPresencePenalty())
                    .stopSequences(copied.getStopSequences())
                    .topK(copied.getTopK())
                    .topP(copied.getTopP());
        }
        if (copied instanceof ToolCallingChatOptions tools) {
            builder.toolCallbacks(tools.getToolCallbacks())
                    .toolNames(tools.getToolNames())
                    .toolContext(tools.getToolContext());
        }
        return new Prompt(prompt.getInstructions(), builder.build());
    }

    private void persist(LlmCallRecord record, String providerName, String model) {
        try {
            recorder.record(record);
        }
        catch (RuntimeException exception) {
            throw new ProviderException(ProviderErrorCode.AUDIT_PERSISTENCE_FAILED,
                    providerName, model, "模型调用审计持久化失败", exception);
        }
    }

    private static TokenUsage tokenUsage(Usage usage) {
        if (usage == null || usage.getPromptTokens() == null || usage.getCompletionTokens() == null
                || usage.getTotalTokens() == null) {
            return TokenUsage.UNKNOWN;
        }
        return new TokenUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    private static ProviderException mapFailure(String provider, String model, RuntimeException failure) {
        var root = rootCause(failure);
        ProviderErrorCode code;
        String message;
        if (failure instanceof HttpClientErrorException clientError
                && (clientError.getStatusCode().value() == 401 || clientError.getStatusCode().value() == 403)) {
            code = ProviderErrorCode.AUTH_FAILED;
            message = "供应商认证失败";
        }
        else if (failure instanceof HttpClientErrorException clientError
                && clientError.getStatusCode().value() == 429) {
            code = ProviderErrorCode.RATE_LIMITED;
            message = "供应商请求受到限流";
        }
        else if (root instanceof SocketTimeoutException || root instanceof TimeoutException) {
            code = ProviderErrorCode.TIMEOUT;
            message = "供应商请求超时";
        }
        else if (failure instanceof ResourceAccessException || root instanceof ConnectException
                || root instanceof UnknownHostException) {
            code = ProviderErrorCode.NETWORK_ERROR;
            message = "供应商网络请求失败";
        }
        else {
            code = ProviderErrorCode.INTERNAL;
            message = "供应商调用失败";
        }
        return new ProviderException(code, provider, model, message, failure);
    }

    private static Throwable rootCause(Throwable failure) {
        var current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static long elapsedMillis(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000);
    }
}
