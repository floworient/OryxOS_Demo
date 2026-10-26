CREATE TABLE llm_calls (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(255),
    profile_name VARCHAR(100) NOT NULL,
    provider VARCHAR(100) NOT NULL,
    model VARCHAR(200) NOT NULL,
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    total_tokens INTEGER,
    usage_known INTEGER NOT NULL DEFAULT 1,
    duration_ms INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_llm_calls_created_at ON llm_calls (created_at);
CREATE INDEX idx_llm_calls_provider ON llm_calls (provider);
