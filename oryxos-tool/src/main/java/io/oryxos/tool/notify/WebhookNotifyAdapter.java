package io.oryxos.tool.notify;

import java.util.Map;
import java.util.Objects;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public final class WebhookNotifyAdapter implements NotifyChannelAdapter {

    private final RestClient restClient;

    public WebhookNotifyAdapter(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient);
    }

    @Override
    public String channelType() {
        return "webhook";
    }

    @Override
    public void send(NotifyTarget target, String content) {
        String url = target.config().get("url");
        if (url == null || url.isBlank()) {
            throw new NotifyDeliveryException("Webhook 目标未配置", null);
        }
        try {
            restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                    .body(JsonParser.toJson(Map.of("content", content)))
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new NotifyDeliveryException("Webhook 推送失败", null);
                        }
                        return null;
                    });
        }
        catch (RestClientException exception) {
            throw new NotifyDeliveryException("Webhook 推送失败", exception);
        }
    }
}
