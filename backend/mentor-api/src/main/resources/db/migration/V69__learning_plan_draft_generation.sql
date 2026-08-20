ALTER TABLE learning_plan_draft
  ADD COLUMN IF NOT EXISTS generation_request_key VARCHAR(128),
  ADD COLUMN IF NOT EXISTS generation_request_fingerprint CHAR(64),
  ADD COLUMN IF NOT EXISTS generation_run_id VARCHAR(128),
  ADD COLUMN IF NOT EXISTS generation_error_code VARCHAR(128),
  ADD COLUMN IF NOT EXISTS generation_error_message VARCHAR(500),
  ADD COLUMN IF NOT EXISTS generation_started_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS generation_completed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uk_learning_plan_draft_user_generation_request_key
  ON learning_plan_draft(user_id, generation_request_key)
  WHERE generation_request_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_learning_plan_draft_generation_status
  ON learning_plan_draft(user_id, status, generation_started_at)
  WHERE generation_request_key IS NOT NULL;
