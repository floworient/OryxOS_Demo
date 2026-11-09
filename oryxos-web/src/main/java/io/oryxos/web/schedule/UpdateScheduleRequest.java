package io.oryxos.web.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import io.oryxos.web.api.ApiException;
import org.springframework.http.HttpStatus;

public record UpdateScheduleRequest(JsonNode enabled) {

    boolean enabledValue() {
        if (enabled == null || !enabled.isBoolean()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "enabled 必须是布尔值");
        }
        return enabled.booleanValue();
    }
}
