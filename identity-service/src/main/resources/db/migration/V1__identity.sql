create table organizations (
  id uuid primary key,
  name text not null,
  created_at timestamptz not null default now(),
  active boolean not null default true
);


create table user_accounts (
  id uuid primary key,
  org_id uuid not null references organizations (id),
  username text not null,
  email text not null,
  display_name text,
  password_hash text not null,
  role text not null check (role in ('ADMIN', 'COORDINATOR', 'VIEWER')),
  enabled boolean not null default true,
  must_change_password boolean not null default false,
  failed_attempts int not null default 0,
  locked_until timestamptz,
  created_at timestamptz not null default now(),
  last_login_at timestamptz
);
create unique index user_accounts_username_uq on user_accounts (lower(username));
create index user_accounts_org_idx on user_accounts (org_id);
