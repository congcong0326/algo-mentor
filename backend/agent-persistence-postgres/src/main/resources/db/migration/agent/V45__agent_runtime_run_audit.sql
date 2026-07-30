ALTER TABLE agent_run
  ADD COLUMN agent_key VARCHAR(128) NULL,
  ADD COLUMN parent_step_index INT NULL;

UPDATE agent_run
SET trigger_type = 'USER_ENTRY'
WHERE trigger_type = 'user_request';

ALTER TABLE agent_run
  ADD CONSTRAINT ck_agent_run_parent_link
  CHECK (
    (parent_run_id IS NULL AND parent_step_index IS NULL)
    OR (parent_run_id IS NOT NULL AND parent_step_index IS NOT NULL AND parent_step_index > 0)
  ),
  ADD CONSTRAINT ck_agent_run_trigger_type
  CHECK (trigger_type IN ('USER_ENTRY', 'CHILD', 'BACKGROUND'));

CREATE INDEX idx_agent_run_agent_key ON agent_run(agent_key) WHERE agent_key IS NOT NULL;
CREATE INDEX idx_agent_run_parent_run_step
  ON agent_run(parent_run_id, parent_step_index)
  WHERE parent_run_id IS NOT NULL;
