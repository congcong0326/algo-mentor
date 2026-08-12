-- 历史 trace 可能把 token 字段持久化为 [REDACTED]；审计查询必须将其视为未知而不是因类型转换失败。
CREATE OR REPLACE FUNCTION agent_audit_nonnegative_bigint(value TEXT)
RETURNS BIGINT
LANGUAGE SQL
IMMUTABLE
PARALLEL SAFE
AS $$
  SELECT CASE
    WHEN value ~ '^[0-9]+$' THEN value::BIGINT
    ELSE NULL
  END;
$$;
