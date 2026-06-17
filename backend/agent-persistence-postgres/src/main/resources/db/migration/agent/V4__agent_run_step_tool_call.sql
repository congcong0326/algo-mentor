CREATE TABLE IF NOT EXISTS agent_run_step (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES agent_task(id),
  run_id BIGINT NOT NULL REFERENCES agent_run(id),
  step_index INT NOT NULL,
  request_snapshot_id BIGINT NULL REFERENCES agent_context_snapshot(id),
  status VARCHAR(32) NOT NULL,
  provider VARCHAR(64) NULL,
  model VARCHAR(128) NULL,
  finish_reason VARCHAR(64) NULL,
  usage JSONB NOT NULL DEFAULT '{}',
  error JSONB NOT NULL DEFAULT '{}',
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ NULL,
  metadata JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id BIGSERIAL PRIMARY KEY,
  task_id BIGINT NOT NULL REFERENCES agent_task(id),
  run_id BIGINT NOT NULL REFERENCES agent_run(id),
  step_index INT NOT NULL,
  tool_call_id VARCHAR(128) NOT NULL,
  tool_name VARCHAR(128) NOT NULL,
  arguments_json JSONB NOT NULL DEFAULT '{}',
  result_json JSONB NULL,
  status VARCHAR(32) NOT NULL,
  duration_millis BIGINT NULL,
  argument_char_count INT NULL,
  result_char_count INT NULL,
  argument_token_estimate INT NULL,
  result_token_estimate INT NULL,
  error JSONB NOT NULL DEFAULT '{}',
  redaction_policy_version VARCHAR(64) NULL,
  started_at TIMESTAMPTZ NOT NULL,
  ended_at TIMESTAMPTZ NULL,
  metadata JSONB NOT NULL DEFAULT '{}'
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_run_step_run_step ON agent_run_step(run_id, step_index);
CREATE INDEX IF NOT EXISTS idx_agent_run_step_snapshot ON agent_run_step(request_snapshot_id);
CREATE INDEX IF NOT EXISTS idx_agent_run_step_run_status ON agent_run_step(run_id, status);
CREATE INDEX IF NOT EXISTS idx_agent_tool_call_run_step ON agent_tool_call(run_id, step_index);
CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_tool_call_run_step_call
  ON agent_tool_call(run_id, step_index, tool_call_id);
CREATE INDEX IF NOT EXISTS idx_agent_tool_call_name ON agent_tool_call(tool_name);
