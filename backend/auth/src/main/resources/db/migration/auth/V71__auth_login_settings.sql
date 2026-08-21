CREATE TABLE auth_login_settings (
  id SMALLINT PRIMARY KEY,
  account_registration_enabled BOOLEAN NOT NULL,
  password_login_enabled BOOLEAN NOT NULL,
  password_registration_enabled BOOLEAN NOT NULL,
  google_login_enabled BOOLEAN NOT NULL,
  github_login_enabled BOOLEAN NOT NULL,
  updated_by BIGINT NULL REFERENCES auth_users(id),
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT ck_auth_login_settings_singleton CHECK (id = 1)
);
