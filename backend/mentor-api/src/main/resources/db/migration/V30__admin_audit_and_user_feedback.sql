CREATE TABLE admin_operation_audit (
  id BIGSERIAL PRIMARY KEY,
  operator_user_id BIGINT NOT NULL REFERENCES auth_users(id),
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_ref VARCHAR(160) NULL,
  outcome VARCHAR(24) NOT NULL,
  request_id VARCHAR(128) NULL,
  metadata_json JSONB NOT NULL DEFAULT '{}'::JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_admin_operation_audit_outcome
    CHECK (outcome IN ('SUCCESS', 'FAILURE'))
);

CREATE INDEX idx_admin_operation_audit_operator_created
  ON admin_operation_audit(operator_user_id, created_at DESC);
CREATE INDEX idx_admin_operation_audit_target
  ON admin_operation_audit(target_type, target_ref, created_at DESC);

CREATE TABLE user_feedback_thread (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES auth_users(id),
  category VARCHAR(24) NOT NULL DEFAULT 'OTHER',
  status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
  subject VARCHAR(200) NULL,
  source_path VARCHAR(500) NULL,
  source_request_id VARCHAR(128) NULL,
  source_run_id VARCHAR(80) NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  closed_at TIMESTAMPTZ NULL,
  closed_by BIGINT NULL REFERENCES auth_users(id),
  CONSTRAINT ck_user_feedback_thread_category
    CHECK (category IN ('BUG', 'SUGGESTION', 'OTHER')),
  CONSTRAINT ck_user_feedback_thread_status
    CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE INDEX idx_user_feedback_thread_user_updated
  ON user_feedback_thread(user_id, updated_at DESC);
CREATE INDEX idx_user_feedback_thread_status_updated
  ON user_feedback_thread(status, updated_at DESC);

CREATE TABLE user_feedback_message (
  id BIGSERIAL PRIMARY KEY,
  thread_id BIGINT NOT NULL REFERENCES user_feedback_thread(id) ON DELETE CASCADE,
  sender_type VARCHAR(16) NOT NULL,
  sender_user_id BIGINT NOT NULL REFERENCES auth_users(id),
  content TEXT NOT NULL,
  read_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_user_feedback_message_sender_type
    CHECK (sender_type IN ('USER', 'ADMIN')),
  CONSTRAINT ck_user_feedback_message_content
    CHECK (char_length(content) BETWEEN 1 AND 4000)
);

CREATE INDEX idx_user_feedback_message_thread_created
  ON user_feedback_message(thread_id, created_at);
