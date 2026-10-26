package io.oryxos.provider;

import static io.oryxos.provider.DefaultProviderServiceChatTest.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.oryxos.core.audit.LlmCallRecord;
import io.oryxos.core.audit.LlmCallStatus;
import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import io.oryxos.provider.support.StubChatModel;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class ProviderFailureMappingTest {

    @ParameterizedTest
    @MethodSource("failures")
    void mapsFailureAndWritesFailedAudit(RuntimeException failure, ProviderErrorCode expected) {
        var audit = new ArrayList<LlmCallRecord>();
        var service = service(failure, audit, record -> record);

        assertThatThrownBy(() -> service.chat("s", profile("a", "alpha", "m", null), new Prompt("x")))
                .isInstanceOfSatisfying(ProviderException.class,
                        error -> assertThat(error.errorCode()).isEqualTo(expected));
        assertThat(audit).singleElement().satisfies(record -> {
            assertThat(record.status()).isEqualTo(LlmCallStatus.FAILED);
            assertThat(record.errorCode()).isEqualTo(expected.name());
        });
    }

    @org.junit.jupiter.api.Test
    void auditFailureAlsoClosesModelFailure() {
        var service = service(new ResourceAccessException("network", new ConnectException()),
                new ArrayList<>(), record -> { throw new IllegalStateException("db down"); });
        assertThatThrownBy(() -> service.chat(null, profile("a", "alpha", "m", null), new Prompt("x")))
                .isInstanceOfSatisfying(ProviderException.class,
                        error -> assertThat(error.errorCode())
                                .isEqualTo(ProviderErrorCode.AUDIT_PERSISTENCE_FAILED));
    }

    private static java.util.stream.Stream<Arguments> failures() {
        return java.util.stream.Stream.of(
                Arguments.of(http(HttpStatus.UNAUTHORIZED), ProviderErrorCode.AUTH_FAILED),
                Arguments.of(http(HttpStatus.FORBIDDEN), ProviderErrorCode.AUTH_FAILED),
                Arguments.of(http(HttpStatus.TOO_MANY_REQUESTS), ProviderErrorCode.RATE_LIMITED),
                Arguments.of(new ResourceAccessException("network", new ConnectException()),
                        ProviderErrorCode.NETWORK_ERROR));
    }

    private static RuntimeException http(HttpStatus status) {
        return HttpClientErrorException.create(status, status.getReasonPhrase(),
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
    }

    private static DefaultProviderService service(RuntimeException failure, List<LlmCallRecord> audit,
            io.oryxos.core.audit.LlmCallRecorder recorder) {
        var definition = new ProviderDefinition("alpha", ProviderDefinition.Type.DASHSCOPE,
                "m", null, "key");
        return new DefaultProviderService(new ProviderRegistry(List.of(definition),
                Map.of("alpha", StubChatModel.failure(failure))), record -> {
                    audit.add(record);
                    return recorder.record(record);
                });
    }
}
