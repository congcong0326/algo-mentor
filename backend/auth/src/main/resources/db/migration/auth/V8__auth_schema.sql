create table if not exists auth_users (
  id bigserial primary key,
  email text,
  email_normalized text unique,
  display_name text,
  avatar_url text,
  status text not null,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  last_login_at timestamptz
);

create table if not exists auth_user_roles (
  user_id bigint not null references auth_users (id) on delete cascade,
  role text not null,
  created_at timestamptz not null,
  primary key (user_id, role)
);

create table if not exists auth_oauth_accounts (
  id bigserial primary key,
  user_id bigint not null references auth_users (id) on delete cascade,
  provider text not null,
  provider_subject text not null,
  email_at_provider text,
  display_name_at_provider text,
  avatar_url_at_provider text,
  created_at timestamptz not null,
  updated_at timestamptz not null,
  unique (provider, provider_subject)
);

create index if not exists idx_auth_oauth_accounts_user_id
  on auth_oauth_accounts (user_id);

create table if not exists SPRING_SESSION (
  PRIMARY_ID char(36) not null,
  SESSION_ID char(36) not null,
  CREATION_TIME bigint not null,
  LAST_ACCESS_TIME bigint not null,
  MAX_INACTIVE_INTERVAL int not null,
  EXPIRY_TIME bigint not null,
  PRINCIPAL_NAME varchar(100),
  constraint SPRING_SESSION_PK primary key (PRIMARY_ID)
);

create unique index if not exists SPRING_SESSION_IX1
  on SPRING_SESSION (SESSION_ID);

create index if not exists SPRING_SESSION_IX2
  on SPRING_SESSION (EXPIRY_TIME);

create index if not exists SPRING_SESSION_IX3
  on SPRING_SESSION (PRINCIPAL_NAME);

create table if not exists SPRING_SESSION_ATTRIBUTES (
  SESSION_PRIMARY_ID char(36) not null,
  ATTRIBUTE_NAME varchar(200) not null,
  ATTRIBUTE_BYTES bytea not null,
  constraint SPRING_SESSION_ATTRIBUTES_PK primary key (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
  constraint SPRING_SESSION_ATTRIBUTES_FK foreign key (SESSION_PRIMARY_ID)
    references SPRING_SESSION (PRIMARY_ID) on delete cascade
);
