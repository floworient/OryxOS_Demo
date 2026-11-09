CREATE TABLE scheduled_tasks (
    schedule_id VARCHAR(36) PRIMARY KEY NOT NULL,
    profile_name VARCHAR(255) NOT NULL,
    schedule_key VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    cron VARCHAR(255) NOT NULL,
    zone VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE CHECK (enabled IN (0, 1)),
    retired BOOLEAN NOT NULL DEFAULT FALSE CHECK (retired IN (0, 1)),
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    last_status VARCHAR(16) CHECK (last_status IN ('success', 'failed')),
    run_count BIGINT NOT NULL DEFAULT 0 CHECK (run_count >= 0),
    updated_at TIMESTAMP NOT NULL,
    UNIQUE (profile_name, schedule_key)
);

CREATE TABLE task_executions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    schedule_id VARCHAR(36) REFERENCES scheduled_tasks (schedule_id),
    session_id VARCHAR(255),
    started_at TIMESTAMP NOT NULL,
    success BOOLEAN NOT NULL CHECK (success IN (0, 1)),
    error_message TEXT,
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    CHECK ((success = 1 AND error_message IS NULL)
        OR (success = 0 AND error_message IS NOT NULL AND length(trim(error_message)) > 0))
);

CREATE INDEX idx_task_executions_schedule_started
    ON task_executions (schedule_id, started_at DESC, id DESC);
