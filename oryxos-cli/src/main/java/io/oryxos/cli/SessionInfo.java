package io.oryxos.cli;

import java.time.Instant;

/** Session 展示信息；不包含消息正文。 */
public record SessionInfo(String id, String profileName, String status, Instant lastActiveAt) { }
