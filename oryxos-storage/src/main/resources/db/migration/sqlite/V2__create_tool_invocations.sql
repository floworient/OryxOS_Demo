CREATE TABLE tool_invocations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id VARCHAR(255) NOT NULL,
    tool_name VARCHAR(255) NOT NULL,
    input_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    success INTEGER NOT NULL,
    error_message TEXT,
    duration_ms INTEGER NOT NULL CHECK (duration_ms >= 0),
    created_at TIMESTAMP NOT NULL,
    CHECK (success IN (0, 1)),
    CHECK (success = 1 OR (error_message IS NOT NULL AND length(error_message) > 0))
);
CREATE INDEX idx_tool_invocations_session ON tool_invocations (session_id);
