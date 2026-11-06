package io.oryxos.core.session;

import java.util.UUID;

/** 原子获取活动会话的结果，用于区分新建与复用。 */
public record SessionAcquisition(String sessionId, boolean created) {

    public SessionAcquisition {
        String canonical = UUID.fromString(sessionId).toString();
        if (!canonical.equals(sessionId)) {
            throw new IllegalArgumentException("会话标识必须是规范 UUID");
        }
    }
}
