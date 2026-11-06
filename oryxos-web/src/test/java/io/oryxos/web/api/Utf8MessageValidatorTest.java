package io.oryxos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class Utf8MessageValidatorTest {

    private final Utf8MessageValidator validator = new Utf8MessageValidator();

    @Test
    void trimsAndAcceptsTheExactUtf8ByteLimit() {
        assertThat(validator.validate("  问题  ")).isEqualTo("问题");
        assertThat(validator.validate("a".repeat(32_768))).hasSize(32_768);
    }

    @Test
    void rejectsNullBlankAndValuesBeyondTheUtf8ByteLimit() {
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(" \t\r\n "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("你".repeat(10_923)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
