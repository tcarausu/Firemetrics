-- Using this to create role & database

CREATE ROLE fhir LOGIN PASSWORD 'fhir' INHERIT;
CREATE DATABASE fhir OWNER fhir;

-- DB-level default search_path; applied on new sessions connecting to `fhir`
ALTER DATABASE fhir SET search_path = fhir_ext, public;
