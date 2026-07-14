ALTER TABLE agent_run
  ADD COLUMN diagnostic_retention_expires_at TIMESTAMPTZ NULL,
  ADD COLUMN diagnostic_redacted_at TIMESTAMPTZ NULL;

UPDATE agent_run
SET diagnostic_retention_expires_at = COALESCE(ended_at, started_at) + INTERVAL '30 days'
WHERE diagnostic_retention_expires_at IS NULL;

CREATE INDEX idx_agent_run_diagnostic_retention
  ON agent_run(diagnostic_retention_expires_at)
  WHERE diagnostic_redacted_at IS NULL;
