package io.oryxos.tool.notify;

import io.oryxos.core.notify.NotifyChannel;
import io.oryxos.tool.sandbox.ActionType;
import io.oryxos.tool.sandbox.Sandbox;
import io.oryxos.tool.sandbox.SandboxAction;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NotifyTargetResolver {

    private static final Pattern ENV = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private final Function<String, String> environment;

    private final Sandbox sandbox;

    public NotifyTargetResolver(Function<String, String> environment, Sandbox sandbox) {
        this.environment = Objects.requireNonNull(environment);
        this.sandbox = Objects.requireNonNull(sandbox);
    }

    public NotifyTarget resolve(NotifyChannel channel) {
        String value = channel.url();
        Matcher matcher = ENV.matcher(value);
        if (matcher.matches()) {
            value = environment.apply(matcher.group(1));
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("通知渠道环境变量未配置");
            }
        }
        URI requested;
        try {
            requested = URI.create(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("通知渠道目标地址非法", exception);
        }
        URI approved = sandbox.enforce(new SandboxAction(ActionType.HTTP_REQUEST, requested));
        Map<String, String> config = new LinkedHashMap<>(channel.config());
        config.put("url", approved.toASCIIString());
        return new NotifyTarget(channel.type(), config);
    }
}
