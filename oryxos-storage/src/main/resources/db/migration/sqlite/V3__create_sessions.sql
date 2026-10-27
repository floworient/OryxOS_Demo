CREATE TABLE sessions (
    session_id VARCHAR(255) PRIMARY KEY NOT NULL,
    profile_name VARCHAR(100) NOT NULL,
    channel VARCHAR(100) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    messages_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('active', 'archived')),
    created_at TIMESTAMP NOT NULL,
    last_active_at TIMESTAMP NOT NULL,
    archived_at TIMESTAMP,
    UNIQUE (channel, user_id, profile_name)
);
