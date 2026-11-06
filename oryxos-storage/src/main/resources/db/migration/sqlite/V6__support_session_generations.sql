CREATE TABLE sessions_v6 (
    session_id VARCHAR(255) PRIMARY KEY NOT NULL,
    profile_name VARCHAR(100) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    messages_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('active', 'archived')),
    created_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP
);

INSERT INTO sessions_v6 (
    session_id, profile_name, channel, user_id, messages_json,
    status, created_at, last_active_at, archived_at
)
SELECT
    session_id, profile_name, channel, user_id, messages_json,
    status, created_at, last_active_at, archived_at
FROM sessions;

DROP TABLE sessions;

ALTER TABLE sessions_v6 RENAME TO sessions;

CREATE UNIQUE INDEX ux_sessions_active_identity
    ON sessions(channel, user_id, profile_name)
    WHERE status = 'active';
