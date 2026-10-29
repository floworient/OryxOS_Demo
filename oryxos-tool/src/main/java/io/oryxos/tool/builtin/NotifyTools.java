package io.oryxos.tool.builtin;

import com.fasterxml.jackson.databind.DeserializationFeature;
import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.core.notify.NotifyChannelService;
import io.oryxos.core.tool.OryxTool;
import io.oryxos.core.tool.ToolResult;
import io.oryxos.tool.notify.NotifyAdapterRegistry;
import io.oryxos.tool.notify.NotifyTarget;
import io.oryxos.tool.notify.NotifyTargetResolver;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.util.json.JsonParser;

public final class NotifyTools implements OryxTool {

    private static final int MAX_CONTENT_LENGTH = 32_768;

    private static final String SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false,"
            + "\"properties\":{\"content\":{\"type\":\"string\",\"minLength\":1,"
            + "\"maxLength\":32768,\"description\":\"要推送的纯文本内容\"},"
            + "\"channel\":{\"type\":\"string\",\"maxLength\":64,"
            + "\"description\":\"全局通知渠道名称；省略或空白时使用最早创建的渠道\"}},"
            + "\"required\":[\"content\"]}";

    private final NotifyChannelService channelService;

    private final NotifyTargetResolver targetResolver;

    private final NotifyAdapterRegistry adapterRegistry;

    public NotifyTools(NotifyChannelService channelService, NotifyTargetResolver targetResolver,
            NotifyAdapterRegistry adapterRegistry) {
        this.channelService = Objects.requireNonNull(channelService);
        this.targetResolver = Objects.requireNonNull(targetResolver);
        this.adapterRegistry = Objects.requireNonNull(adapterRegistry);
    }

    @Override
    public String getName() {
        return "notify";
    }

    @Override
    public String getDescription() {
        return "把纯文本消息推送到按名称注册的通知渠道";
    }

    @Override
    public String getInputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(String jsonInput) {
        try {
            NotifyInput input = parse(jsonInput);
            NotifyChannel channel = channelService.resolve(input.channel());
            NotifyTarget target = targetResolver.resolve(channel);
            adapterRegistry.require(channel.type()).send(target, input.content());
            return new ToolResult(true, "已推送", null, false);
        }
        catch (RuntimeException exception) {
            return new ToolResult(false, "", "通知推送失败，请检查渠道配置", false);
        }
    }

    private static NotifyInput parse(String jsonInput) {
        Object decoded;
        try {
            decoded = JsonParser.getObjectMapper().readerFor(Object.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(jsonInput);
        }
        catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("notify 输入不是合法 JSON", exception);
        }
        if (!(decoded instanceof Map<?, ?> values)
                || !Set.of("content", "channel").containsAll(values.keySet())) {
            throw new IllegalArgumentException("notify 输入字段非法");
        }
        Object rawContent = values.get("content");
        if (!(rawContent instanceof String content) || content.isBlank()
                || content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("notify content 非法");
        }
        Object rawChannel = values.get("channel");
        if (rawChannel != null && !(rawChannel instanceof String)) {
            throw new IllegalArgumentException("notify channel 非法");
        }
        String channel = (String) rawChannel;
        if (channel != null && channel.length() > 64) {
            throw new IllegalArgumentException("notify channel 过长");
        }
        return new NotifyInput(content, channel);
    }

    private record NotifyInput(String content, String channel) {
    }
}
