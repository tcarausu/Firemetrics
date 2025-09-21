-- -- drop db and role if you want to start clean
DROP SCHEMA IF EXISTS fhir_ext CASCADE;
DROP DATABASE IF EXISTS fhir;
DROP ROLE IF EXISTS fhir;


-- Create role and database
CREATE ROLE fhir LOGIN PASSWORD 'fhir';
CREATE DATABASE fhir OWNER fhir;
SET search_path = fhir_ext, public;

-- -- TODO Move to fhir database, then proceed to create the proper schema, extensions and the Patient table.
-- Schema for our FHIR extension-like functions
CREATE SCHEMA IF NOT EXISTS fhir_ext AUTHORIZATION fhir;

-- Remove access to the schema from public.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

-- Needed for gen_random_uuid() and trigram for quicker search
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Patient_store table inside schema fhir_ext
CREATE TABLE IF NOT EXISTS fhir_ext.patient_store
(
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    resource      jsonb       NOT NULL,
    resource_type text        NOT NULL DEFAULT 'Patient',
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       integer     NOT NULL DEFAULT 1
    );


-- then we grant usage rights to the fhir user;
GRANT USAGE ON SCHEMA fhir_ext TO fhir;

GRANT INSERT, SELECT, UPDATE, DELETE ON fhir_ext.patient_store TO fhir;
GRANT USAGE, SELECT, UPDATE ON ALL SEQUENCES IN SCHEMA fhir_ext TO fhir;


