create table flow_deposits (
  id uuid primary key,
  tenant_id uuid not null,
  case_id uuid not null,
  kind text not null,
  amount numeric(18, 2) not null,
  currency text not null default 'INR',
  instrument text,
  reference text,
  status text not null default 'PLANNED',
  paid_on date,
  valid_until date,
  refund_due_on date,
  refunded_on date,
  notes text,
  created_by text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index flow_deposits_tenant_case on flow_deposits (tenant_id, case_id);

create table flow_reminders (
  id uuid primary key,
  tenant_id uuid not null,
  ref_type text not null,
  ref_id uuid not null,
  case_id uuid,
  kind text not null,
  threshold int not null,
  due_on date not null,
  level text not null,
  message text not null,
  created_at timestamptz not null default now(),
  read_at timestamptz
);
-- one reminder per item, kind, threshold and due date: a changed date earns a fresh reminder
create unique index flow_reminders_uq on flow_reminders (tenant_id, ref_id, kind, threshold, due_on);
create index flow_reminders_tenant_created on flow_reminders (tenant_id, created_at desc);

alter table flow_deposits enable row level security;
alter table flow_deposits force row level security;
create policy tenant_isolation on flow_deposits
  using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
grant select, insert, update, delete on flow_deposits to tender_flow_user;

alter table flow_reminders enable row level security;
alter table flow_reminders force row level security;
create policy tenant_isolation on flow_reminders
  using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
grant select, insert, update, delete on flow_reminders to tender_flow_user;

-- Returns only tenant ids, so the scheduled job can find work without bypassing row-level security.

revoke all on function tenants_with_reminder_work() from public;
grant execute on function tenants_with_reminder_work() to tender_flow_user;
