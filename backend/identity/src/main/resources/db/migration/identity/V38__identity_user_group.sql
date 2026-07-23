CREATE TABLE identity_user_group (
  id BIGSERIAL PRIMARY KEY,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(500) NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at TIMESTAMPTZ NULL,
  deleted_by BIGINT NULL REFERENCES auth_users(id),
  CONSTRAINT uk_identity_user_group_code UNIQUE (code),
  CONSTRAINT ck_identity_user_group_code
    CHECK (code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
  CONSTRAINT ck_identity_user_group_name
    CHECK (length(btrim(name)) > 0),
  CONSTRAINT ck_identity_user_group_status
    CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),
  CONSTRAINT ck_identity_user_group_deleted_fields
    CHECK (
      (status = 'DELETED' AND deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
      OR (status != 'DELETED' AND deleted_at IS NULL AND deleted_by IS NULL)
    )
);

CREATE TABLE identity_user_group_membership (
  user_id BIGINT NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
  group_id BIGINT NOT NULL REFERENCES identity_user_group(id) ON DELETE RESTRICT,
  joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, group_id),
  CONSTRAINT ck_identity_user_group_membership_expiry
    CHECK (expires_at IS NULL OR expires_at > joined_at)
);

CREATE INDEX idx_identity_user_group_membership_group
  ON identity_user_group_membership(group_id, user_id);

CREATE INDEX idx_identity_user_group_membership_user_expiry
  ON identity_user_group_membership(user_id, expires_at);
