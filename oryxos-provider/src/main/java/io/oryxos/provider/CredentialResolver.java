package io.oryxos.provider;

import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;

public final class CredentialResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([A-Za-z_][A-Za-z0-9_]*)}$");

    private final Function<String, String> environment;

    public CredentialResolver() {
        this(System::getenv);
    }

    CredentialResolver(Function<String, String> environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public String resolve(String providerName, String configuredValue) {
        if (configuredValue == null || configuredValue.isBlank()) {
            throw missing(providerName, null);
        }
        var matcher = PLACEHOLDER.matcher(configuredValue);
        if (matcher.matches()) {
            var variable = matcher.group(1);
            var resolved = environment.apply(variable);
            if (resolved == null || resolved.isBlank() || resolved.contains("${")) {
                throw missing(providerName, variable);
            }
            return resolved;
        }
        if (configuredValue.contains("${")) {
            throw missing(providerName, null);
        }
        return configuredValue;
    }

    private static ProviderException missing(String providerName, String variable) {
        var detail = variable == null ? "" : "（环境变量 " + variable + " 为空）";
        return new ProviderException(ProviderErrorCode.MISSING_CREDENTIAL, providerName, null,
                "供应商 [" + providerName + "] 的字段 [api-key] 未配置或未解析" + detail, null);
    }
}
