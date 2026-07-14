CREATE TABLE auth_beta_access_settings (
  id SMALLINT PRIMARY KEY,
  email_allowlist_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  updated_by BIGINT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_auth_beta_access_settings_singleton CHECK (id = 1)
);

INSERT INTO auth_beta_access_settings (id, email_allowlist_enabled)
VALUES (1, FALSE)
ON CONFLICT (id) DO NOTHING;

CREATE TABLE auth_beta_allowed_email (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(320) NOT NULL,
  email_normalized VARCHAR(320) NOT NULL,
  created_by BIGINT NOT NULL REFERENCES auth_users(id),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uk_auth_beta_allowed_email_normalized UNIQUE (email_normalized)
);

CREATE INDEX idx_auth_beta_allowed_email_created_at
  ON auth_beta_allowed_email(created_at DESC);

ALTER TABLE auth_password_credentials
  ADD COLUMN reset_required BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN temporary_password_expires_at TIMESTAMPTZ NULL,
  ADD COLUMN temporary_password_consumed_at TIMESTAMPTZ NULL,
  ADD COLUMN password_changed_at TIMESTAMPTZ NULL,
  ADD COLUMN reset_by BIGINT NULL REFERENCES auth_users(id);
