create table notification_settings (
  tenant_id uuid primary key,
  escalation_email text
);
alter table notification_settings enable row level security;
alter table notification_settings force row level security;
create policy tenant_isolation on notification_settings
  using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
grant select, insert, update, delete on notification_settings to app_user;
