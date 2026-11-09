package io.oryxos.web.session;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameSessionRequest(
        @NotBlank(message = "会话名不能为空")
        @Size(max = 200, message = "会话名不能超过 200 个字符") String title) { }
