package io.oryxos.provider;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.oryxos.core.provider.ProviderErrorCode;
import io.oryxos.core.provider.ProviderException;
import org.springframework.ai.chat.model.ChatModel;
import io.oryxos.provider.dashscope.DashScopeChatModelFactory;
import io.oryxos.provider.openai.OpenAiCompatibleChatModelFactory;

public final class ProviderRegistry {

    private final Map<String, ProviderDefinition> definitions;

    private final Map<String, ChatModel> models;

    private final Set<String> configuredNames;

    public ProviderRegistry(List<ProviderDefinition> definitions, Map<String, ChatModel> models) {
        var byName = new LinkedHashMap<String, ProviderDefinition>();
        var firstIndexes = new LinkedHashMap<String, Integer>();
        var names = new LinkedHashSet<String>();
        for (int index = 0; index < definitions.size(); index++) {
            var definition = definitions.get(index);
            validate(definition, index);
            if (!names.add(definition.name())) {
                int firstIndex = firstIndexes.get(definition.name());
                throw new ProviderException(ProviderErrorCode.DUPLICATE_PROVIDER,
                        definition.name(), definition.model(),
                        "供应商名称重复 [" + definition.name() + "]，首次定义索引 "
                                + firstIndex + "，冲突定义索引 " + index,
                        null);
            }
            firstIndexes.put(definition.name(), index);
            byName.put(definition.name(), definition);
        }
        if (!names.containsAll(models.keySet())) {
            throw new ProviderException(ProviderErrorCode.INVALID_CONFIG, null, null,
                    "模型映射包含未配置的供应商名称", null);
        }
        this.definitions = Map.copyOf(byName);
        this.models = Map.copyOf(models);
        this.configuredNames = Set.copyOf(names);
    }

    public static ProviderRegistry build(ProviderProperties properties, Set<String> referencedNames,
            CredentialResolver credentialResolver, DashScopeChatModelFactory dashScopeFactory,
            OpenAiCompatibleChatModelFactory openAiFactory) {
        var definitions = properties.providers();
        new ProviderRegistry(definitions, Map.of());
        var models = new LinkedHashMap<String, ChatModel>();
        for (var definition : definitions) {
            String apiKey;
            try {
                apiKey = credentialResolver.resolve(definition.name(), definition.apiKey());
            }
            catch (ProviderException exception) {
                if (referencedNames.contains(definition.name())) {
                    throw exception;
                }
                continue;
            }
            var resolved = new ProviderDefinition(definition.name(), definition.type(), definition.model(),
                    definition.baseUrl(), apiKey);
            var model = switch (resolved.type()) {
                case DASHSCOPE -> dashScopeFactory.create(resolved);
                case OPENAI_COMPATIBLE -> openAiFactory.create(resolved);
            };
            models.put(resolved.name(), model);
        }
        return new ProviderRegistry(definitions, models);
    }

    public Set<String> configuredNames() {
        return configuredNames;
    }

    ProviderDefinition definition(String name) {
        return definitions.get(name);
    }

    ChatModel resolve(String name, String model) {
        if (!configuredNames.contains(name)) {
            throw new ProviderException(ProviderErrorCode.UNKNOWN_PROVIDER, name, model,
                    "未配置供应商 [" + name + "]", null);
        }
        var chatModel = models.get(name);
        if (chatModel == null) {
            throw new ProviderException(ProviderErrorCode.MISSING_CREDENTIAL, name, model,
                    "供应商 [" + name + "] 的凭据不可用", null);
        }
        return chatModel;
    }

    private static void validate(ProviderDefinition definition, int index) {
        if (definition == null || definition.name() == null || definition.name().isBlank()
                || definition.type() == null || definition.model() == null || definition.model().isBlank()) {
            throw new ProviderException(ProviderErrorCode.INVALID_CONFIG,
                    definition == null ? null : definition.name(),
                    definition == null ? null : definition.model(),
                    "供应商配置第 " + index + " 项缺少必填字段", null);
        }
        if (definition.baseUrl() != null && !definition.baseUrl().isBlank()) {
            try {
                var uri = java.net.URI.create(definition.baseUrl());
                int port = uri.getPort();
                if (!uri.isAbsolute() || uri.isOpaque()
                        || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                        || uri.getHost() == null || uri.getHost().isBlank()
                        || port > 65_535) {
                    throw new IllegalArgumentException("unsupported scheme");
                }
            }
            catch (IllegalArgumentException exception) {
                throw new ProviderException(ProviderErrorCode.INVALID_CONFIG,
                        definition.name(), definition.model(),
                        "供应商 [" + definition.name() + "] 的字段 [base-url] 非法", exception);
            }
        }
    }
}
