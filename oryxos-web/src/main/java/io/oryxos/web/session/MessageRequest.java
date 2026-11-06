package io.oryxos.web.session;

import jakarta.validation.constraints.NotBlank;

public record MessageRequest(
        @NotBlank(message = "消息不能为空") String message) { }
