package io.oryxos.web.session;

import io.oryxos.core.session.SessionStatus;

public record SessionArchiveView(String sessionId, SessionStatus status) { }
