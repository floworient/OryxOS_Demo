package io.oryxos.core.provider;

import java.util.Set;

/** 对外提供已配置 Provider 名称的只读契约。 */
public interface ProviderCatalog {

    Set<String> configuredNames();
}
