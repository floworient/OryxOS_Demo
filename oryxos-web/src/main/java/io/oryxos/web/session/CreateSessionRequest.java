package io.oryxos.web.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateSessionRequest(
        @NotBlank(message = "Agent 名称不能为空") String profileName,
        @NotBlank(message = "用户标识不能为空")
        @Size(max = 255, message = "用户标识不能超过 255 个字符") String userId) { }
