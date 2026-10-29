CREATE TABLE notify_channels (
    name VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    url TEXT NOT NULL,
    description VARCHAR(500),
    config_json TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CHECK (updated_at >= created_at)
);

CREATE INDEX idx_notify_channels_default
    ON notify_channels (created_at, name);
