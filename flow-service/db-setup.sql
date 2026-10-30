-- Run once in psql as your Postgres admin user:  psql -U postgres -f db-setup.sql
-- DEVELOPMENT ONLY: it drops tender_flow_db, which removes any tables created earlier without row-level security.

do $$ begin
  if not exists (select from pg_roles where rolname = 'flow_care_user')   then create role flow_care_user; end if;
  if not exists (select from pg_roles where rolname = 'tender_flow_user') then create role tender_flow_user; end if;
end $$;
-- Migration role: owns the tables and the security-definer function, so it must be a superuser (or BYPASSRLS)
alter role flow_care_user with login password 'password' superuser;
-- Runtime role used by the running service: must NOT be a superuser, or row-level security is skipped
alter role tender_flow_user with login password 'password' nosuperuser nobypassrls;

drop database if exists tender_flow_db;
create database tender_flow_db owner flow_care_user;

-- Check: flow_care_user should show t, tender_flow_user should show f and f
select rolname, rolsuper, rolbypassrls from pg_roles where rolname in ('flow_care_user', 'tender_flow_user');
