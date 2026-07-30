-- Local / RDS: create CRM database (same pattern as fieldforcedb / shopdb).
-- Run as postgres superuser, e.g.:
--   "C:\Program Files\PostgreSQL\17\bin\psql.exe" -U postgres -h localhost -f create-crmdb.sql

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'crmdb') THEN
    CREATE ROLE crmdb LOGIN PASSWORD 'crmdb';
  END IF;
END
$$;

SELECT 'CREATE DATABASE crmdb OWNER crmdb'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'crmdb')\gexec

GRANT ALL PRIVILEGES ON DATABASE crmdb TO crmdb;

\c crmdb
GRANT USAGE, CREATE ON SCHEMA public TO crmdb;
ALTER SCHEMA public OWNER TO crmdb;
