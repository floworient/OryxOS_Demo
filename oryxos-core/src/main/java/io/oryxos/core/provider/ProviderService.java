package io.oryxos.core.provider;

import io.oryxos.core.profile.Profile;
import org.springframework.ai.chat.prompt.Prompt;

public interface ProviderService {
    ProviderResponse chat(String sessionId, Profile profile, Prompt prompt);
}
