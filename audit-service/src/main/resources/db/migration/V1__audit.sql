create table audit_log (
  id uuid primary key,
  tenant_id uuid not null,
  actor text,
  action text not null,
  task_id uuid,
  at timestamptz not null
);
create index audit_tenant_at on audit_log (tenant_id, at desc);
alter table audit_log enable row level security;
alter table audit_log force row level security;
create policy tenant_isolation on audit_log
  using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
-- append-only: the app role can read and insert, never update or delete
grant select, insert on audit_log to app_user;
