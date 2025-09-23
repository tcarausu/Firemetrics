-- Will use to store the schema, extensions, table, privileges (for cleaner setup)
-- Run against the fhir database; we just created.
\c fhir

-- Lock down public schema in this DB  -- (ONLY admin context)
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE USAGE  ON SCHEMA public FROM PUBLIC;   -- ADDED
GRANT  USAGE  ON SCHEMA public TO fhir;       -- ADDED

SET ROLE fhir;

-- Schema
CREATE SCHEMA IF NOT EXISTS fhir_ext AUTHORIZATION fhir;

-- Lock down public schema in this DB
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- Extensions
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS pg_trgm;   -- trigram search

-- Patient store table
CREATE TABLE IF NOT EXISTS fhir_ext.patient_store(
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    resource      jsonb       NOT NULL,
    resource_type text        NOT NULL DEFAULT 'Patient',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       integer     NOT NULL DEFAULT 1
);

ALTER TABLE fhir_ext.patient_store OWNER TO fhir;

-- Grants for app role
GRANT USAGE ON SCHEMA fhir_ext TO fhir;
GRANT INSERT, SELECT, UPDATE, DELETE ON fhir_ext.patient_store TO fhir;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA fhir_ext TO fhir;

RESET ROLE;
