-- app_user is NOT a superuser, so row-level security applies to it.
create role app_user login password 'app_user';
create database audit;
create database notification;
create database tenant;
create database flow;
