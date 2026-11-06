package io.oryxos.web.agent;

import jakarta.validation.constraints.NotBlank;

public record InvokeAgentRequest(
        @NotBlank(message = "消息不能为空") String message) { }
