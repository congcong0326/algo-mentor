CREATE TABLE IF NOT EXISTS agent_content_blob (
  id BIGSERIAL PRIMARY KEY,
  scope_type VARCHAR(64) NOT NULL,
  scope_id BIGINT NOT NULL,
  content_type VARCHAR(128) NOT NULL,
  storage_mode VARCHAR(32) NOT NULL,
  content_text TEXT NULL,
  content_bytes BYTEA NULL,
  uri TEXT NULL,
  sha256 VARCHAR(128) NOT NULL,
  char_count INT NULL,
  byte_count BIGINT NULL,
  line_count INT NULL,
  redaction_policy_version VARCHAR(64) NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_agent_content_blob_scope
  ON agent_content_blob(scope_type, scope_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_content_blob_hash_scope
  ON agent_content_blob(scope_type, scope_id, sha256);

ALTER TABLE agent_tool_call
  ADD COLUMN IF NOT EXISTS result_storage_mode VARCHAR(32) NULL,
  ADD COLUMN IF NOT EXISTS result_blob_id BIGINT NULL REFERENCES agent_content_blob(id),
  ADD COLUMN IF NOT EXISTS result_preview_json JSONB NULL,
  ADD COLUMN IF NOT EXISTS result_ref VARCHAR(128) NULL,
  ADD COLUMN IF NOT EXISTS result_line_count INT NULL,
  ADD COLUMN IF NOT EXISTS result_sha256 VARCHAR(128) NULL;

CREATE INDEX IF NOT EXISTS idx_agent_tool_call_result_ref
  ON agent_tool_call(result_ref);
