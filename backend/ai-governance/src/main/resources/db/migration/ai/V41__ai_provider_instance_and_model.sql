CREATE TABLE ai_provider_instance (
  id BIGSERIAL PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  provider_type VARCHAR(32) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  config JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_provider_instance_name UNIQUE (name),
  CONSTRAINT ck_ai_provider_instance_name_not_blank CHECK (btrim(name) <> ''),
  CONSTRAINT ck_ai_provider_instance_type_not_blank CHECK (btrim(provider_type) <> ''),
  CONSTRAINT ck_ai_provider_instance_config_object CHECK (jsonb_typeof(config) = 'object')
);

CREATE INDEX idx_ai_provider_instance_type_enabled
  ON ai_provider_instance(provider_type, enabled, id);

CREATE TABLE ai_model (
  id BIGSERIAL PRIMARY KEY,
  provider_instance_id BIGINT NOT NULL REFERENCES ai_provider_instance(id) ON DELETE RESTRICT,
  display_name VARCHAR(120) NOT NULL,
  model_id VARCHAR(160) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_ai_model_instance_model UNIQUE (provider_instance_id, model_id),
  CONSTRAINT ck_ai_model_display_name_not_blank CHECK (btrim(display_name) <> ''),
  CONSTRAINT ck_ai_model_model_id_not_blank CHECK (btrim(model_id) <> '')
);

CREATE INDEX idx_ai_model_instance_enabled
  ON ai_model(provider_instance_id, enabled, id);

ALTER TABLE ai_llm_call_usage
  ADD COLUMN provider_instance_id BIGINT NULL REFERENCES ai_provider_instance(id) ON DELETE RESTRICT,
  ADD COLUMN ai_model_id BIGINT NULL REFERENCES ai_model(id) ON DELETE RESTRICT;

CREATE INDEX idx_ai_llm_call_usage_configured_model_started
  ON ai_llm_call_usage(ai_model_id, started_at DESC);

CREATE INDEX idx_ai_llm_call_usage_provider_instance_started
  ON ai_llm_call_usage(provider_instance_id, started_at DESC);
