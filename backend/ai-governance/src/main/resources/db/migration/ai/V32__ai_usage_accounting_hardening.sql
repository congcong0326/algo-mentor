ALTER TABLE ai_llm_call_usage
  ADD CONSTRAINT ck_ai_llm_call_usage_input_tokens_non_negative
    CHECK (input_tokens >= 0),
  ADD CONSTRAINT ck_ai_llm_call_usage_output_tokens_non_negative
    CHECK (output_tokens >= 0),
  ADD CONSTRAINT ck_ai_llm_call_usage_cached_tokens_non_negative
    CHECK (cached_tokens >= 0),
  ADD CONSTRAINT ck_ai_llm_call_usage_reasoning_tokens_non_negative
    CHECK (reasoning_tokens >= 0),
  ADD CONSTRAINT ck_ai_llm_call_usage_total_tokens_non_negative
    CHECK (total_tokens >= 0),
  ADD CONSTRAINT ck_ai_llm_call_usage_step_index_positive
    CHECK (step_index IS NULL OR step_index > 0);

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM ai_model_price
    GROUP BY lower(provider), model
    HAVING count(*) > 1
  ) THEN
    RAISE EXCEPTION 'Cannot normalize duplicate AI model price providers';
  END IF;
END $$;

UPDATE ai_model_price
SET provider = lower(provider)
WHERE provider <> lower(provider);

ALTER TABLE ai_model_price
  ADD CONSTRAINT ck_ai_model_price_provider_lower
    CHECK (provider = lower(provider));

CREATE INDEX idx_ai_llm_call_usage_source_started
  ON ai_llm_call_usage(source, started_at DESC);

CREATE INDEX idx_ai_llm_call_usage_started
  ON ai_llm_call_usage(started_at DESC);

INSERT INTO ai_llm_call_usage (
  call_id,
  run_id,
  user_id,
  purpose,
  source,
  call_kind,
  step_index,
  provider,
  model,
  status,
  error_code,
  input_tokens,
  output_tokens,
  cached_tokens,
  reasoning_tokens,
  total_tokens,
  started_at,
  completed_at
)
SELECT
  'legacy-run-' || id,
  run_id,
  user_id,
  purpose,
  source,
  'LEGACY_RUN_AGGREGATE',
  NULL,
  provider,
  model,
  CASE status
    WHEN 'COMPLETED' THEN 'COMPLETED'
    WHEN 'CANCELLED' THEN 'CANCELLED'
    ELSE 'FAILED'
  END,
  error_code,
  input_tokens,
  output_tokens,
  cached_tokens,
  reasoning_tokens,
  total_tokens,
  coalesce(started_at, created_at),
  coalesce(completed_at, updated_at, created_at)
FROM ai_run_admissions
WHERE input_tokens <> 0
   OR output_tokens <> 0
   OR cached_tokens <> 0
   OR reasoning_tokens <> 0
   OR total_tokens <> 0
ON CONFLICT (call_id) DO NOTHING;
