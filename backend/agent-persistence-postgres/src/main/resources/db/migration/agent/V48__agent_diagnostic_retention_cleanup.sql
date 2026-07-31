-- 由运维定时任务调用；清理顺序先断开外键，再删除正文快照和工具 blob，保留低敏运行统计。
CREATE OR REPLACE FUNCTION redact_expired_agent_diagnostics()
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
  affected_runs BIGINT;
BEGIN
  UPDATE agent_run_step
  SET request_snapshot_id = NULL,
      error = '{}'::jsonb,
      metadata = '{}'::jsonb
  WHERE run_id IN (
    SELECT id FROM agent_run
    WHERE diagnostic_redacted_at IS NULL
      AND diagnostic_retention_expires_at <= NOW());

  UPDATE agent_tool_call
  SET arguments_json = '{}'::jsonb,
      result_json = NULL,
      result_preview_json = NULL,
      result_ref = NULL,
      result_blob_id = NULL,
      error = '{}'::jsonb,
      metadata = '{}'::jsonb
  WHERE run_id IN (
    SELECT id FROM agent_run
    WHERE diagnostic_redacted_at IS NULL
      AND diagnostic_retention_expires_at <= NOW());

  DELETE FROM agent_context_snapshot
  WHERE run_id IN (
    SELECT id FROM agent_run
    WHERE diagnostic_redacted_at IS NULL
      AND diagnostic_retention_expires_at <= NOW());

  DELETE FROM agent_content_blob
  WHERE scope_type = 'tool_result'
    AND scope_id IN (
      SELECT id FROM agent_tool_call
      WHERE run_id IN (
        SELECT id FROM agent_run
        WHERE diagnostic_redacted_at IS NULL
          AND diagnostic_retention_expires_at <= NOW()));

  UPDATE agent_run
  SET diagnostic_redacted_at = NOW()
  WHERE diagnostic_redacted_at IS NULL
    AND diagnostic_retention_expires_at <= NOW();
  GET DIAGNOSTICS affected_runs = ROW_COUNT;
  RETURN affected_runs;
END;
$$;
