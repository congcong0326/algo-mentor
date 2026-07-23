CREATE TABLE generic_policy (
  id BIGSERIAL PRIMARY KEY,
  type_code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'DISABLED',
  priority INTEGER NOT NULL,
  subject_range JSONB NOT NULL,
  content JSONB NOT NULL,
  version BIGINT NOT NULL DEFAULT 1,
  created_by BIGINT NOT NULL REFERENCES auth_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_by BIGINT NOT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_by BIGINT NULL REFERENCES auth_users(id),
  deleted_at TIMESTAMPTZ NULL,
  CONSTRAINT ck_generic_policy_type_code
    CHECK (type_code ~ '^[a-z][a-z0-9_.-]{0,63}$'),
  CONSTRAINT ck_generic_policy_name
    CHECK (length(btrim(name)) > 0),
  CONSTRAINT ck_generic_policy_status
    CHECK (status IN ('ENABLED', 'DISABLED', 'DELETED')),
  CONSTRAINT ck_generic_policy_priority
    CHECK (priority > 0),
  CONSTRAINT ck_generic_policy_subject_range
    CHECK (jsonb_typeof(subject_range) = 'object'),
  CONSTRAINT ck_generic_policy_version
    CHECK (version > 0),
  CONSTRAINT ck_generic_policy_deleted_fields
    CHECK (
      (status = 'DELETED' AND deleted_by IS NOT NULL AND deleted_at IS NOT NULL)
      OR (status != 'DELETED' AND deleted_by IS NULL AND deleted_at IS NULL)
    )
);

CREATE UNIQUE INDEX uk_generic_policy_type_priority_live
  ON generic_policy(type_code, priority)
  WHERE status != 'DELETED';

CREATE INDEX idx_generic_policy_runtime
  ON generic_policy(type_code, priority, id)
  WHERE status = 'ENABLED';

CREATE INDEX idx_generic_policy_admin_list
  ON generic_policy(type_code, status, priority, id)
  WHERE status != 'DELETED';
