-- 会话名：默认取首条用户消息（截断 30 字符），可由用户重命名。
ALTER TABLE sessions ADD COLUMN title VARCHAR(200);

-- 回填存量会话：从 messages_json 提取第一条 user 消息文本作为会话名。
UPDATE sessions
SET title = trim(replace(substr(
    (SELECT json_extract(value, '$.text')
     FROM json_each(sessions.messages_json)
     WHERE json_extract(value, '$.role') = 'user'
       AND json_extract(value, '$.text') IS NOT NULL
     LIMIT 1),
    1, 30), char(10), ' '))
WHERE title IS NULL
  AND json_valid(messages_json)
  AND json_type(messages_json) = 'array';
