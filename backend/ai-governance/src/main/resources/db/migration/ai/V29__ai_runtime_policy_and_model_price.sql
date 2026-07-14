CREATE TABLE ai_runtime_settings (
  id SMALLINT PRIMARY KEY,
  ai_enabled BOOLEAN NOT NULL DEFAULT TRUE,
  default_daily_request_limit INTEGER NOT NULL DEFAULT 50,
  updated_by BIGINT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ai_runtime_settings_singleton CHECK (id = 1),
  CONSTRAINT ck_ai_runtime_settings_daily_limit CHECK (default_daily_request_limit > 0)
);

INSERT INTO ai_runtime_settings (id, ai_enabled, default_daily_request_limit)
VALUES (1, TRUE, 50)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE ai_user_policy (
  user_id BIGINT PRIMARY KEY REFERENCES auth_users(id),
  ai_enabled_override BOOLEAN NULL,
  daily_request_limit_override INTEGER NULL,
  updated_by BIGINT NOT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_ai_user_policy_daily_limit
    CHECK (daily_request_limit_override IS NULL OR daily_request_limit_override > 0)
);

CREATE TABLE ai_model_price (
  id BIGSERIAL PRIMARY KEY,
  provider VARCHAR(80) NOT NULL,
  model VARCHAR(160) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  input_price_per_million NUMERIC(20, 8) NOT NULL,
  cached_input_price_per_million NUMERIC(20, 8) NOT NULL,
  output_price_per_million NUMERIC(20, 8) NOT NULL,
  cost_multiplier NUMERIC(12, 6) NOT NULL DEFAULT 1,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  updated_by BIGINT NOT NULL REFERENCES auth_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_model_price_provider_model UNIQUE (provider, model),
  CONSTRAINT ck_ai_model_price_currency CHECK (currency = 'USD'),
  CONSTRAINT ck_ai_model_price_non_negative CHECK (
    input_price_per_million >= 0
    AND cached_input_price_per_million >= 0
    AND output_price_per_million >= 0
    AND cost_multiplier > 0
  )
);

CREATE TABLE ai_llm_call_usage (
  id BIGSERIAL PRIMARY KEY,
  call_id VARCHAR(100) NOT NULL,
  run_id VARCHAR(80) NULL,
  user_id BIGINT NULL REFERENCES auth_users(id),
  purpose VARCHAR(64) NOT NULL,
  source VARCHAR(64) NOT NULL,
  call_kind VARCHAR(32) NOT NULL,
  step_index INTEGER NULL,
  provider VARCHAR(80) NULL,
  model VARCHAR(160) NULL,
  status VARCHAR(32) NOT NULL,
  error_code VARCHAR(80) NULL,
  input_tokens BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  cached_tokens BIGINT NOT NULL DEFAULT 0,
  reasoning_tokens BIGINT NOT NULL DEFAULT 0,
  total_tokens BIGINT NOT NULL DEFAULT 0,
  started_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_llm_call_usage_call_id UNIQUE (call_id),
  CONSTRAINT ck_ai_llm_call_usage_kind
    CHECK (call_kind IN ('AGENT_STEP', 'DIRECT', 'BACKGROUND', 'LEGACY_RUN_AGGREGATE')),
  CONSTRAINT ck_ai_llm_call_usage_status
    CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'))
);

CREATE INDEX idx_ai_llm_call_usage_user_started
  ON ai_llm_call_usage(user_id, started_at DESC);
CREATE INDEX idx_ai_llm_call_usage_model_started
  ON ai_llm_call_usage(provider, model, started_at DESC);
CREATE INDEX idx_ai_llm_call_usage_run
  ON ai_llm_call_usage(run_id, started_at);
