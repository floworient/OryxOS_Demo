package io.oryxos.web.notify;

import io.oryxos.core.notify.NotifyChannel;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class NotifyChannelMasker {

    private static final Pattern ENV = Pattern.compile("\\$\\{[A-Za-z_][A-Za-z0-9_]*}");

    private static final Pattern SENSITIVE = Pattern.compile(
            ".*(token|secret|password|credential|api[-_.]?key).*", Pattern.CASE_INSENSITIVE);

    public NotifyChannelView view(NotifyChannel channel) {
        Map<String, String> config = new LinkedHashMap<>();
        channel.config().forEach((key, value) ->
                config.put(key, sensitive(key) ? mask(value) : value));
        return new NotifyChannelView(channel.name(), channel.type(), maskUrl(channel.url()),
                channel.description(), config, channel.createdAt(), channel.updatedAt());
    }

    public MergedUpdate merge(NotifyChannel existing, String requestedUrl,
            Map<String, String> requestedConfig) {
        String url = shouldPreserve(requestedUrl, maskUrl(existing.url())) ? existing.url() : requestedUrl;
        if (requestedConfig == null) {
            return new MergedUpdate(url, existing.config());
        }
        Map<String, String> merged = new LinkedHashMap<>(requestedConfig);
        requestedConfig.forEach((key, value) -> {
            String current = existing.config().get(key);
            if (sensitive(key) && current != null && shouldPreserve(value, mask(current))) {
                merged.put(key, current);
            }
        });
        return new MergedUpdate(url, merged);
    }

    private static String maskUrl(String value) {
        return ENV.matcher(value).matches() ? value : mask(value);
    }

    private static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return "****" + value.substring(value.length() - 4);
    }

    private static boolean sensitive(String key) {
        return key != null && SENSITIVE.matcher(key.toLowerCase(Locale.ROOT)).matches();
    }

    private static boolean shouldPreserve(String candidate, String currentMask) {
        return candidate == null || candidate.isBlank() || candidate.equals(currentMask);
    }

    public record MergedUpdate(String url, Map<String, String> config) {
        public MergedUpdate {
            config = Map.copyOf(config);
        }
    }
}
