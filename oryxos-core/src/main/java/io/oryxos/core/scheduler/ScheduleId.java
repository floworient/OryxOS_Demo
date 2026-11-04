package io.oryxos.core.scheduler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record ScheduleId(String value) {

    public ScheduleId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("scheduleId 不能为空");
        }
        UUID parsed = UUID.fromString(value);
        if (!parsed.toString().equals(value)) {
            throw new IllegalArgumentException("scheduleId 必须是规范的小写 UUID 文本");
        }
    }

    public static ScheduleId from(String profileName, String scheduleKey) {
        byte[] profile = required(profileName, "profileName").getBytes(StandardCharsets.UTF_8);
        byte[] key = required(scheduleKey, "scheduleKey").getBytes(StandardCharsets.UTF_8);
        ByteBuffer identity = ByteBuffer.allocate(Integer.BYTES * 2 + profile.length + key.length);
        identity.putInt(profile.length).put(profile).putInt(key.length).put(key);
        return new ScheduleId(UUID.nameUUIDFromBytes(identity.array()).toString());
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }
}
