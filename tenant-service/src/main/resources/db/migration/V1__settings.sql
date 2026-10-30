create table tenant_settings (
  tenant_id uuid primary key,
  display_name text,
  time_zone text not null default 'UTC',
  queues text,
  sites text
);
alter table tenant_settings enable row level security;
alter table tenant_settings force row level security;
create policy tenant_isolation on tenant_settings
  using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
grant select, insert, update, delete on tenant_settings to app_user;
