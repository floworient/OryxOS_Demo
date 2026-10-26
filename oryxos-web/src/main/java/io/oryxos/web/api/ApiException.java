package io.oryxos.web.api;

import org.springframework.http.HttpStatus;

/** 可安全映射为公开 HTTP 响应的应用异常。 */
public class ApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;

    private final HttpStatus status;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }
}
