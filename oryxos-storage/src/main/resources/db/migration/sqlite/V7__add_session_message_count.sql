ALTER TABLE sessions ADD COLUMN message_count INTEGER NOT NULL DEFAULT 0;

UPDATE sessions
SET message_count = CASE
    WHEN json_valid(messages_json) AND json_type(messages_json) = 'array'
        THEN json_array_length(messages_json)
    ELSE 0
END;
