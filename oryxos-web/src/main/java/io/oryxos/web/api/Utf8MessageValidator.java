package io.oryxos.web.api;

import java.nio.charset.StandardCharsets;

/** 校验同步 Agent 调用消息的公共边界。 */
public final class Utf8MessageValidator {

    public static final int MAX_UTF8_BYTES = 32_768;

    public String validate(String message) {
        if (message == null) {
            throw new IllegalArgumentException("消息不能为空");
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("消息不能为空");
        }
        if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_UTF8_BYTES) {
            throw new IllegalArgumentException("消息不能超过 32768 UTF-8 字节");
        }
        return trimmed;
    }
}
