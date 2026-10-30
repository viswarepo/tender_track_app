create table case_documents (
  id uuid primary key,
  tenant_id uuid not null,
  case_id uuid not null,
  checklist_item text,
  file_name text not null,
  size_bytes bigint not null,
  storage_path text not null,
  uploaded_by text,
  uploaded_at timestamptz not null
);
create index docs_tenant_case on case_documents (tenant_id, case_id);
alter table case_documents enable row level security;
alter table case_documents force row level security;
create policy tenant_isolation on case_documents
  using (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid)
  with check (tenant_id = nullif(current_setting('app.tenant_id', true), '')::uuid);
grant select, insert, update, delete on case_documents to app_user;
