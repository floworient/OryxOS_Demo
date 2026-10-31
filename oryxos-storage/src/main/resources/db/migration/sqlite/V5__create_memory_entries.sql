CREATE TABLE memory_entries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_key VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL CHECK (scope IN ('CORE', 'ARCHIVAL')),
    content TEXT NOT NULL CHECK (length(trim(content)) > 0),
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_memory_entries_owner_scope_created
    ON memory_entries (owner_key, scope, created_at, id);
