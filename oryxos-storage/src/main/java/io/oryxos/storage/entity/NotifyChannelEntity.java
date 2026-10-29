package io.oryxos.storage.entity;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.notify.NotifyChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.ai.util.json.JsonParser;

@Entity
@Table(name = "notify_channels")
public class NotifyChannelEntity {

    @Id
    @Column(length = 64)
    private String name;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Column(length = 500)
    private String description;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotifyChannelEntity() {
    }

    public NotifyChannelEntity(NotifyChannel channel) {
        this.name = channel.name();
        this.type = channel.type();
        this.url = channel.url();
        this.description = channel.description();
        this.configJson = JsonParser.toJson(channel.config());
        this.createdAt = channel.createdAt();
        this.updatedAt = channel.updatedAt();
    }

    public NotifyChannel toDomain() {
        return new NotifyChannel(name, type, url, description, decodeConfig(configJson), createdAt, updatedAt);
    }

    private static Map<String, String> decodeConfig(String json) {
        Object value;
        try {
            value = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(json);
        }
        catch (IOException exception) {
            throw new IllegalStateException("通知渠道配置不是完整合法的 JSON", exception);
        }
        if (!(value instanceof Map<?, ?> entries)) {
            throw new IllegalStateException("通知渠道配置必须是对象");
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : entries.entrySet()) {
            if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String item)) {
                throw new IllegalStateException("通知渠道配置键和值必须是字符串");
            }
            result.put(key, item);
        }
        return result;
    }
}
