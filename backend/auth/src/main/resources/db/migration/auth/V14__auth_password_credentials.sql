create table if not exists auth_password_credentials (
  id bigserial primary key,
  user_id bigint not null references auth_users (id) on delete cascade,
  password_hash text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (user_id)
);

create index if not exists idx_auth_password_credentials_user_id
  on auth_password_credentials (user_id);
