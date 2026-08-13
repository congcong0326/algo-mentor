ALTER TABLE queue_message
  DROP CONSTRAINT ck_queue_message_status,
  DROP CONSTRAINT ck_queue_message_succeeded_at;

ALTER TABLE queue_message
  ADD COLUMN delivery_attempt INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN available_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN lease_token UUID NULL,
  ADD COLUMN lease_expires_at TIMESTAMPTZ NULL,
  ADD COLUMN failed_at TIMESTAMPTZ NULL,
  ADD COLUMN last_error_type VARCHAR(128) NULL,
  ADD CONSTRAINT ck_queue_message_status CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCEEDED', 'FAILED')),
  ADD CONSTRAINT ck_queue_message_delivery_attempt CHECK (delivery_attempt >= 0),
  ADD CONSTRAINT ck_queue_message_succeeded_at CHECK (
    (status = 'SUCCEEDED' AND succeeded_at IS NOT NULL)
    OR (status <> 'SUCCEEDED' AND succeeded_at IS NULL)
  ),
  ADD CONSTRAINT ck_queue_message_processing_lease CHECK (
    (status = 'PROCESSING' AND lease_token IS NOT NULL AND lease_expires_at IS NOT NULL)
    OR (status <> 'PROCESSING' AND lease_token IS NULL AND lease_expires_at IS NULL)
  ),
  ADD CONSTRAINT ck_queue_message_failed_at CHECK (
    (status = 'FAILED' AND failed_at IS NOT NULL)
    OR (status <> 'FAILED' AND failed_at IS NULL)
  );

CREATE INDEX idx_queue_message_pending_available_topic_key
  ON queue_message (topic, available_at, message_key, id) WHERE status = 'PENDING';
CREATE INDEX idx_queue_message_processing_lease
  ON queue_message (lease_expires_at, id) WHERE status = 'PROCESSING';
CREATE INDEX idx_queue_message_failed_topic
  ON queue_message (topic, failed_at, id) WHERE status = 'FAILED';

COMMENT ON COLUMN queue_message.delivery_attempt IS '消息已被 worker 成功领取的次数；达到上限后标记 FAILED。';
COMMENT ON COLUMN queue_message.available_at IS 'PENDING 消息的最早可再次消费时间，用于退避重试。';
COMMENT ON COLUMN queue_message.lease_token IS 'PROCESSING 消息的领取令牌，仅拥有者可确认或失败回写。';
