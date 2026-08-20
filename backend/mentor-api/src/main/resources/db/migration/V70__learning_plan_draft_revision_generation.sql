ALTER TABLE learning_plan_draft_revision
  ADD COLUMN generation_request_key VARCHAR(128),
  ADD COLUMN generation_request_fingerprint CHAR(64),
  ADD COLUMN generation_run_id VARCHAR(128),
  ADD COLUMN generation_started_at TIMESTAMPTZ,
  ADD COLUMN generation_completed_at TIMESTAMPTZ;

CREATE UNIQUE INDEX uk_learning_plan_draft_revision_user_draft_generation_request_key
  ON learning_plan_draft_revision(user_id, draft_id, generation_request_key)
  WHERE generation_request_key IS NOT NULL;

CREATE INDEX idx_learning_plan_draft_revision_generation_status
  ON learning_plan_draft_revision(user_id, draft_id, status, generation_started_at)
  WHERE generation_request_key IS NOT NULL;
