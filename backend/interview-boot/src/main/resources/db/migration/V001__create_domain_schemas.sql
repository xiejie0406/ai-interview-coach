-- Phase 01 baseline: schema namespaces only. Domain tables are owned by later phases.
-- DDL ownership is assigned to a separately managed migrator role; this migration
-- intentionally does not create roles or grant production privileges.
create schema if not exists identity;
create schema if not exists catalog;
create schema if not exists practice;
create schema if not exists interview;
create schema if not exists voice;
create schema if not exists agent;
create schema if not exists evaluation;
create schema if not exists learning;
create schema if not exists billing;
create schema if not exists governance;
create schema if not exists operations;
create schema if not exists platform;
