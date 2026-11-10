package io.oryxos.core.context;

import org.springframework.ai.chat.prompt.Prompt;

public record PreparedPrompt(Prompt prompt, int windowStart) {
}
