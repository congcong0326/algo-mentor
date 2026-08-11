CREATE TABLE IF NOT EXISTS learning_plan_daily_draft_usage (
  user_id BIGINT NOT NULL,
  quota_date DATE NOT NULL,
  draft_count INTEGER NOT NULL,
  applied_limit INTEGER NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (user_id, quota_date),
  CONSTRAINT ck_learning_plan_daily_draft_usage_count CHECK (draft_count > 0),
  CONSTRAINT ck_learning_plan_daily_draft_usage_limit CHECK (applied_limit > 0),
  CONSTRAINT ck_learning_plan_daily_draft_usage_within_limit CHECK (draft_count <= applied_limit)
);

CREATE INDEX IF NOT EXISTS idx_learning_plan_daily_draft_usage_date
  ON learning_plan_daily_draft_usage(quota_date, user_id);

CREATE INDEX IF NOT EXISTS idx_learning_plan_draft_expiry
  ON learning_plan_draft(expires_at, id);
