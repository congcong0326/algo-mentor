alter table auth_users
  add column if not exists deleted_at timestamptz,
  add column if not exists deleted_by bigint;

do $$
begin
  if not exists (
    select 1
    from pg_constraint
    where conname = 'fk_auth_users_deleted_by'
  ) then
    alter table auth_users
      add constraint fk_auth_users_deleted_by
        foreign key (deleted_by) references auth_users (id);
  end if;
end $$;

create index if not exists idx_auth_users_status
  on auth_users (status);

create index if not exists idx_auth_users_deleted_at
  on auth_users (deleted_at);

create index if not exists idx_auth_users_email_display_name
  on auth_users (email_normalized, display_name);
