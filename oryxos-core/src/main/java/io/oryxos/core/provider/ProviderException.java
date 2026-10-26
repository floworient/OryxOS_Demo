package io.oryxos.core.provider;

public final class ProviderException extends RuntimeException {

    private final ProviderErrorCode errorCode;

    private final String providerName;

    private final String model;

    public ProviderException(ProviderErrorCode errorCode, String providerName, String model,
            String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.providerName = providerName;
        this.model = model;
    }

    public ProviderErrorCode errorCode() {
        return errorCode;
    }

    public String providerName() {
        return providerName;
    }

    public String model() {
        return model;
    }
}
