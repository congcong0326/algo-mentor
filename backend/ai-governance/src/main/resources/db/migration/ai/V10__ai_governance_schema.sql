CREATE TABLE IF NOT EXISTS ai_run_admissions (
  id BIGSERIAL PRIMARY KEY,
  run_id VARCHAR(80) NOT NULL,
  user_id BIGINT,
  purpose VARCHAR(64) NOT NULL,
  source VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(160),
  request_size INTEGER NOT NULL DEFAULT 0,
  rejection_code VARCHAR(80),
  error_code VARCHAR(80),
  provider VARCHAR(80),
  model VARCHAR(160),
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  cached_tokens BIGINT NOT NULL DEFAULT 0,
  reasoning_tokens BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_run_admissions_run_id UNIQUE (run_id)
);

CREATE INDEX IF NOT EXISTS idx_ai_run_admissions_user_created_at
  ON ai_run_admissions (user_id, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_run_admissions_purpose_created_at
  ON ai_run_admissions (purpose, created_at);

CREATE INDEX IF NOT EXISTS idx_ai_run_admissions_status_created_at
  ON ai_run_admissions (status, created_at);

CREATE TABLE IF NOT EXISTS ai_daily_usage (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  quota_date DATE NOT NULL,
  scope VARCHAR(64) NOT NULL,
  request_count BIGINT NOT NULL DEFAULT 0,
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  cached_tokens BIGINT NOT NULL DEFAULT 0,
  reasoning_tokens BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  limit_count BIGINT NOT NULL,
  token_limit BIGINT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_ai_daily_usage_user_date_scope UNIQUE (user_id, quota_date, scope)
);

CREATE INDEX IF NOT EXISTS idx_ai_daily_usage_user_date
  ON ai_daily_usage (user_id, quota_date);
