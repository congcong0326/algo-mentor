ALTER TABLE ai_llm_call_usage
  ADD COLUMN reasoning_effort VARCHAR(16) NULL;

ALTER TABLE ai_llm_call_usage
  ADD CONSTRAINT ck_ai_llm_call_usage_reasoning_effort
    CHECK (reasoning_effort IS NULL OR reasoning_effort IN (
      'none', 'minimal', 'low', 'medium', 'high', 'xhigh', 'max'
    ));
