package io.oryxos;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(classes = OryxOsApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OryxOsApplicationIT {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void healthEndpointIsAvailableWithoutSensitiveDetails() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
        assertThat(response.getBody()).doesNotContain("components", "details");
    }

    @Test
    void prometheusEndpointIsExposed() {
        ResponseEntity<String> discovery = restTemplate.getForEntity("/actuator", String.class);
        assertThat(discovery.getBody()).contains("prometheus");

        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/prometheus", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("jvm_memory_used_bytes");
    }
}
