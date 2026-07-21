CREATE TABLE queue_message (
  id BIGSERIAL PRIMARY KEY,
  topic VARCHAR(128) NOT NULL,
  message_key VARCHAR(256) NOT NULL,
  message_value TEXT NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  succeeded_at TIMESTAMPTZ NULL,
  CONSTRAINT ck_queue_message_topic CHECK (btrim(topic) <> ''),
  CONSTRAINT ck_queue_message_key CHECK (btrim(message_key) <> ''),
  CONSTRAINT ck_queue_message_status CHECK (status IN ('PENDING', 'SUCCEEDED')),
  CONSTRAINT ck_queue_message_succeeded_at CHECK ((status = 'PENDING' AND succeeded_at IS NULL) OR (status = 'SUCCEEDED' AND succeeded_at IS NOT NULL))
);
CREATE INDEX idx_queue_message_pending_topic ON queue_message (topic, id) WHERE status = 'PENDING';
CREATE INDEX idx_queue_message_pending_topic_key ON queue_message (topic, message_key, id) WHERE status = 'PENDING';
CREATE INDEX idx_queue_message_succeeded_cleanup ON queue_message (succeeded_at, id) WHERE status = 'SUCCEEDED';
