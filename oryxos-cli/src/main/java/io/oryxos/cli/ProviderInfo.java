package io.oryxos.cli;

/** 不含凭据的 Provider 展示信息。 */
public record ProviderInfo(String name, String type, String model, String baseUrl) { }
