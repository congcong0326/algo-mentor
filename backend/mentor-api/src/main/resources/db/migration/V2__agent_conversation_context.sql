CREATE TABLE IF NOT EXISTS agent_task (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NULL,
  title VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL,
  system_prompt TEXT NULL,
  active_summary_artifact_id BIGINT NULL,
  context_policy JSONB NOT NULL DEFAULT '{}',
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_turn (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES agent_task(id),
  sequence_no BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  user_message_id BIGINT NULL,
  assistant_message_id BIGINT NULL,
  accepted_run_id BIGINT NULL,
  current_run_id BIGINT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS agent_message (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES agent_task(id),
  turn_id BIGINT NOT NULL REFERENCES agent_turn(id),
  run_id BIGINT NULL,
  role VARCHAR(32) NOT NULL,
  content TEXT NOT NULL,
  sequence_no BIGINT NOT NULL,
  status VARCHAR(32) NOT NULL,
  token_estimate INT NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_agent_message_role CHECK (role IN ('user', 'assistant'))
);

CREATE TABLE IF NOT EXISTS agent_run (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES agent_task(id),
  turn_id BIGINT NOT NULL REFERENCES agent_turn(id),
  run_uuid VARCHAR(64) NOT NULL,
  attempt_no INT NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  parent_run_id BIGINT NULL,
  retry_of_run_id BIGINT NULL,
  trigger_type VARCHAR(64) NOT NULL,
  retry_reason VARCHAR(255) NULL,
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(64) NULL,
  model VARCHAR(128) NULL,
  max_steps INT NOT NULL,
  finish_reason VARCHAR(64) NULL,
  input_token_estimate INT NULL,
  output_token_estimate INT NULL,
  usage JSONB NOT NULL DEFAULT '{}',
  error JSONB NOT NULL DEFAULT '{}',
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ NULL
);

ALTER TABLE agent_turn
  ADD CONSTRAINT fk_agent_turn_user_message
  FOREIGN KEY (user_message_id) REFERENCES agent_message(id);

ALTER TABLE agent_turn
  ADD CONSTRAINT fk_agent_turn_assistant_message
  FOREIGN KEY (assistant_message_id) REFERENCES agent_message(id);

ALTER TABLE agent_turn
  ADD CONSTRAINT fk_agent_turn_accepted_run
  FOREIGN KEY (accepted_run_id) REFERENCES agent_run(id);

ALTER TABLE agent_turn
  ADD CONSTRAINT fk_agent_turn_current_run
  FOREIGN KEY (current_run_id) REFERENCES agent_run(id);

ALTER TABLE agent_message
  ADD CONSTRAINT fk_agent_message_run
  FOREIGN KEY (run_id) REFERENCES agent_run(id);

ALTER TABLE agent_run
  ADD CONSTRAINT fk_agent_run_parent
  FOREIGN KEY (parent_run_id) REFERENCES agent_run(id);

ALTER TABLE agent_run
  ADD CONSTRAINT fk_agent_run_retry_of
  FOREIGN KEY (retry_of_run_id) REFERENCES agent_run(id);

CREATE TABLE IF NOT EXISTS agent_context_snapshot (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES agent_task(id),
  run_id BIGINT NOT NULL REFERENCES agent_run(id),
  step_index INT NOT NULL,
  request_id VARCHAR(128) NULL,
  provider VARCHAR(64) NULL,
  model VARCHAR(128) NULL,
  model_selector VARCHAR(128) NULL,
  policy_name VARCHAR(64) NOT NULL,
  policy_version VARCHAR(64) NOT NULL,
  token_budget INT NOT NULL,
  token_estimate INT NULL,
  reserved_output_tokens INT NULL,
  snapshot_storage_mode VARCHAR(32) NOT NULL DEFAULT 'inline',
  request_snapshot_json JSONB NOT NULL,
  request_snapshot_blob_ref VARCHAR(512) NULL,
  messages_json JSONB NOT NULL,
  tools_json JSONB NOT NULL DEFAULT '[]',
  tool_choice_json JSONB NULL,
  generation_options JSONB NOT NULL DEFAULT '{}',
  request_hash VARCHAR(128) NOT NULL,
  redaction_policy_version VARCHAR(64) NULL,
  retention_expires_at TIMESTAMPTZ NULL,
  metadata JSONB NOT NULL DEFAULT '{}',
  created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_turn_task_seq ON agent_turn(task_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_agent_turn_current_run ON agent_turn(current_run_id);
CREATE INDEX IF NOT EXISTS idx_agent_turn_accepted_run ON agent_turn(accepted_run_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_message_task_seq ON agent_message(task_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_agent_message_run ON agent_message(run_id);
CREATE INDEX IF NOT EXISTS idx_agent_run_task_turn ON agent_run(task_id, turn_id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_run_turn_attempt ON agent_run(turn_id, attempt_no);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_run_idempotency_key ON agent_run(idempotency_key);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_run_uuid ON agent_run(run_uuid);
CREATE INDEX IF NOT EXISTS idx_agent_run_retry_of ON agent_run(retry_of_run_id);
CREATE INDEX IF NOT EXISTS idx_agent_context_snapshot_run ON agent_context_snapshot(run_id, step_index);
