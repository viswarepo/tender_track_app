-- Run once in psql as your Postgres admin user:  psql -U postgres -f db-setup-identity.sql
-- DEVELOPMENT ONLY: it drops tender_identity_db, which deletes all users and organizations.
do $$ begin
  if not exists (select from pg_roles where rolname = 'tender_identity_user') then create role tender_identity_user; end if;
end $$;
alter role tender_identity_user with login password 'password' nosuperuser;
drop database if exists tender_identity_db;
create database tender_identity_db owner tender_identity_user;
