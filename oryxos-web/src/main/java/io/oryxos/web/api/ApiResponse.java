package io.oryxos.web.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** 对外 REST API 的统一响应信封。 */
public record ApiResponse<T>(String code, String message, T data, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("SUCCESS", "success", data, OffsetDateTime.now(ZoneOffset.UTC));
    }

    public static ApiResponse<Void> error(String code, String message) {
        return new ApiResponse<>(code, message, null, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
