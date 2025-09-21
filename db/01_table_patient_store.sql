-- Indexes to support Patient search filters

-- Index for quicker look-up (case-insensitive)
CREATE INDEX IF NOT EXISTS ix_patient_gender
    ON fhir_ext.patient_store ((lower(resource->>'gender')));


-- 1) Safe, IMMUTABLE extractor for birthDate (YYYY-MM-DD only)
CREATE OR REPLACE FUNCTION fhir_ext.birthdate_of(p jsonb)
    RETURNS date
    LANGUAGE sql
    IMMUTABLE
AS $$
SELECT CASE
           WHEN p ? 'birthDate'
           AND (p->>'birthDate') ~ '^\d{4}-\d{2}-\d{2}$'
               THEN to_date(p->>'birthDate', 'YYYY-MM-DD')
           ELSE NULL::date
END
$$;

-- 2) Use the IMMUTABLE function in the index
DROP INDEX IF EXISTS ix_patient_birthdate;
CREATE INDEX ix_patient_birthdate
    ON fhir_ext.patient_store ( fhir_ext.birthdate_of(resource) );


-- Trigram index for "name contains ..." (family + given merged)
-- We'll index a deterministic, immutable function that flattens names.
CREATE OR REPLACE FUNCTION fhir_ext.flatten_name(p jsonb)
    RETURNS text
    LANGUAGE sql
    IMMUTABLE
AS $$
WITH fam AS (
    SELECT string_agg(trim(both '"' FROM x::text), ' ')
    FROM jsonb_path_query_array(p, '$.name[*].family') AS t(x)
),
     giv AS (
         SELECT string_agg(trim(both '"' FROM x::text), ' ')
         FROM jsonb_path_query_array(p, '$.name[*].given[*]') AS t(x)
     )
SELECT lower(coalesce((SELECT * FROM fam), '') || ' ' || coalesce((SELECT * FROM giv), ''));
$$;

-- name indexing
CREATE INDEX IF NOT EXISTS ix_patient_name_trgm
    ON fhir_ext.patient_store
    USING gin ( fhir_ext.flatten_name(resource) gin_trgm_ops );

-- stable pagination support
CREATE INDEX IF NOT EXISTS ix_patient_created_id
    ON fhir_ext.patient_store (created_at, id);

-- Index for faster creation/id look-up
CREATE INDEX IF NOT EXISTS ix_patient_created_id_partial
    ON fhir_ext.patient_store (created_at, id)
    WHERE resource_type = 'Patient';
