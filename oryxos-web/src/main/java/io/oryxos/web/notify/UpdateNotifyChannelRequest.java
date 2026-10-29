package io.oryxos.web.notify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record UpdateNotifyChannelRequest(
        @NotBlank @Size(max = 32) String type,
        String url,
        @Size(max = 500) String description,
        @Size(max = 32) Map<String, String> config) {

    public UpdateNotifyChannelRequest {
        config = config == null ? null : Map.copyOf(config);
    }
}
