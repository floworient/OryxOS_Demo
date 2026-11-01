package io.oryxos.core.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class AgentNameTest {

    @ParameterizedTest
    @MethodSource("validNames")
    void acceptsPortableAgentNames(String value) {
        assertThat(AgentName.requireValid(value)).isEqualTo(value);
    }

    @Test
    void acceptsMaximumLengthName() {
        String value = "a".repeat(64);

        assertThat(AgentName.requireValid(value)).isEqualTo(value);
    }

    @ParameterizedTest
    @MethodSource("invalidNames")
    void rejectsUnsafeOrReservedNames(String value) {
        assertThatThrownBy(() -> AgentName.requireValid(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Agent 名称");
    }

    private static Stream<String> validNames() {
        return Stream.of("weather", "daily.tech-v2_1", "0-agent");
    }

    private static Stream<String> invalidNames() {
        return Stream.of("", " ", ".", "..", "../escape", "a/b", "a\\b",
                "a".repeat(65), "CON", "con.txt", "COM1", "lpt9.log");
    }
}
