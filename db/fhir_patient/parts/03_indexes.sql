-- Setting here the helper functions and  indecies for Patient search (similarly fhir db).
SET ROLE fhir;

-- Case-insensitive gender lookup
CREATE INDEX IF NOT EXISTS ix_patient_gender
    ON fhir_ext.patient_store ((lower(resource->>'gender')));

-- IMMUTABLE extractor for birthDate (YYYY-MM-DD only)
CREATE OR REPLACE FUNCTION fhir_ext.birthdate_of(p jsonb)
RETURNS date LANGUAGE sql IMMUTABLE AS $$
SELECT CASE
           WHEN p ? 'birthDate' AND (p->>'birthDate') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
    THEN to_date(p->>'birthDate', 'YYYY-MM-DD')
  ELSE NULL::date
END
$$;

-- Index using the immutable function
DROP INDEX IF EXISTS ix_patient_birthdate;
CREATE INDEX ix_patient_birthdate
    ON fhir_ext.patient_store ( fhir_ext.birthdate_of(resource) );

-- Name flattener for trigram search (family + given)
CREATE OR REPLACE FUNCTION fhir_ext.flatten_name(p jsonb)
RETURNS text LANGUAGE sql IMMUTABLE AS $$
WITH fam AS (
  SELECT string_agg(trim(both '"' FROM x::text), ' ')
  FROM jsonb_path_query_array(p, '$.name[*].family') AS t(x)
), giv AS (
  SELECT string_agg(trim(both '"' FROM x::text), ' ')
  FROM jsonb_path_query_array(p, '$.name[*].given[*]') AS t(x)
)
SELECT lower(coalesce((SELECT * FROM fam), '') || ' ' || coalesce((SELECT * FROM giv), ''));
$$;

-- Trigram index over flattened name
CREATE INDEX IF NOT EXISTS ix_patient_name_trgm
    ON fhir_ext.patient_store USING gin ( fhir_ext.flatten_name(resource) gin_trgm_ops );

-- Stable pagination support
CREATE INDEX IF NOT EXISTS ix_patient_created_id
    ON fhir_ext.patient_store (created_at, id);

-- Partial index for Patient only
CREATE INDEX IF NOT EXISTS ix_patient_created_id_partial
    ON fhir_ext.patient_store (created_at, id)
    WHERE resource_type = 'Patient';

RESET ROLE;
