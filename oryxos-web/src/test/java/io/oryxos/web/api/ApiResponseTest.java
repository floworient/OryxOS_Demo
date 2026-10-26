package io.oryxos.web.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ApiResponseTest {

    @Test
    void successResponseUsesStableEnvelope() {
        ApiResponse<String> response = ApiResponse.success("ready");

        assertThat(response.code()).isEqualTo("SUCCESS");
        assertThat(response.message()).isEqualTo("success");
        assertThat(response.data()).isEqualTo("ready");
        assertThat(response.timestamp()).isBeforeOrEqualTo(OffsetDateTime.now());
    }
}
