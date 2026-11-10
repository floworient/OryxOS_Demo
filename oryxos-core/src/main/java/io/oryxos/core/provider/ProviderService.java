package io.oryxos.core.provider;

import io.oryxos.core.audit.LlmCallPurpose;
import io.oryxos.core.profile.Profile;
import org.springframework.ai.chat.prompt.Prompt;

public interface ProviderService {
    ProviderResponse chat(String sessionId, Profile profile, Prompt prompt);

    default ProviderResponse chat(String sessionId, Profile profile, Prompt prompt,
            Profile.ProviderRef route, LlmCallPurpose purpose) {
        if (!profile.provider().equals(route)) {
            throw new UnsupportedOperationException("当前 ProviderService 不支持独立摘要路由");
        }
        return chat(sessionId, profile, prompt);
    }
}
