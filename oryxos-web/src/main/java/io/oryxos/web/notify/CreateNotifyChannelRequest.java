package io.oryxos.web.notify;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Map;

public record CreateNotifyChannelRequest(
        @NotBlank @Size(max = 64) String name,
        @NotBlank @Size(max = 32) String type,
        @NotBlank String url,
        @Size(max = 500) String description,
        @Size(max = 32) Map<String, String> config) {

    public CreateNotifyChannelRequest {
        config = config == null ? null : Map.copyOf(config);
    }
}
