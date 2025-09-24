CREATE ROLE fhir LOGIN PASSWORD 'fhir' INHERIT;
CREATE DATABASE fhir OWNER fhir;
ALTER DATABASE fhir SET search_path = fhir_ext, public;
