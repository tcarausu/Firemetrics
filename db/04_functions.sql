-- FHIR Patient functions for: put, search, get + public shims (same db)
\c fhir
SET ROLE fhir;

-- === Guard: ensure helper functions exist (idempotent) ===
CREATE OR REPLACE FUNCTION fhir_ext.birthdate_of(p jsonb)
RETURNS date LANGUAGE sql IMMUTABLE AS $$
SELECT CASE
           WHEN p ? 'birthDate' AND (p->>'birthDate') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
           THEN to_date(p->>'birthDate', 'YYYY-MM-DD')
         ELSE NULL::date
END
$$;

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
-- === End guard ===


-- PUT (insert/update) a Patient resource
CREATE OR REPLACE FUNCTION fhir_ext.fhir_put(p_resource_type text, p_resource jsonb)
RETURNS uuid LANGUAGE plpgsql VOLATILE AS $$
DECLARE
v_id        uuid;
  v_now       timestamptz := now();
  v_payload   jsonb := p_resource;
  v_existing  boolean;
  v_version   integer;
BEGIN
  IF p_resource_type IS DISTINCT FROM 'Patient' THEN
    RAISE EXCEPTION 'Only Patient supported (got: %)', p_resource_type;
END IF;

  IF (p_resource->>'resourceType') IS DISTINCT FROM 'Patient' THEN
    RAISE EXCEPTION 'Payload.resourceType must be Patient';
END IF;

  v_id := COALESCE(NULLIF(p_resource->>'id','')::uuid, gen_random_uuid());
SELECT EXISTS (SELECT 1 FROM fhir_ext.patient_store WHERE id = v_id) INTO v_existing;
IF v_existing THEN
SELECT version + 1 INTO v_version FROM fhir_ext.patient_store WHERE id = v_id;
ELSE
    v_version := 1;
END IF;

  v_payload := p_resource - 'meta';
  v_payload := jsonb_set(v_payload, '{id}', to_jsonb(v_id::text), true);
  v_payload := jsonb_set(v_payload, '{meta}', '{}'::jsonb, true);
  v_payload := jsonb_set(v_payload, '{meta,versionId}', to_jsonb(v_version::text), true);
  v_payload := jsonb_set(v_payload, '{meta,lastUpdated}', to_jsonb(to_char((v_now AT TIME ZONE 'UTC'), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')), true);

INSERT INTO fhir_ext.patient_store(id, resource, resource_type, created_at, updated_at, version)
VALUES (v_id, v_payload, 'Patient', v_now, v_now, v_version)
    ON CONFLICT (id) DO UPDATE SET
    updated_at = EXCLUDED.updated_at,
                            version    = fhir_ext.patient_store.version + 1,
                            resource   = jsonb_set(
                            jsonb_set(EXCLUDED.resource, '{meta,versionId}', to_jsonb((fhir_ext.patient_store.version + 1)::text), true),
                            '{meta,lastUpdated}', to_jsonb(to_char((EXCLUDED.updated_at AT TIME ZONE 'UTC'), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')), true
                            );

RETURN v_id;
END$$;

-- GET by id
CREATE OR REPLACE FUNCTION fhir_ext.fhir_get(p_resource_type text, p_id uuid)
RETURNS jsonb LANGUAGE sql STABLE AS $$
SELECT resource
FROM fhir_ext.patient_store
WHERE resource_type = p_resource_type AND id = p_id;
$$;

-- Search primitives
CREATE OR REPLACE FUNCTION fhir_ext.fhir_search_patient(
  p_name     text,
  p_birth_ge date,
  p_birth_le date,
  p_gender   text,
  p_limit    int,
  p_offset   int
) RETURNS TABLE(id uuid, total bigint) LANGUAGE sql STABLE AS $$
WITH base AS (
  SELECT id, created_at
  FROM fhir_ext.patient_store
  WHERE resource_type = 'Patient'
    AND (p_gender   IS NULL OR lower(resource->>'gender') = lower(p_gender))
    AND (p_birth_ge IS NULL OR fhir_ext.birthdate_of(resource) >= p_birth_ge)
    AND (p_birth_le IS NULL OR fhir_ext.birthdate_of(resource) <= p_birth_le)
    AND (p_name     IS NULL OR fhir_ext.flatten_name(resource) ILIKE '%'||lower(p_name)||'%')
), counted AS (
  SELECT count(*) AS total FROM base
), paged AS (
  SELECT id FROM base ORDER BY created_at, id
  LIMIT COALESCE(p_limit, 20) OFFSET COALESCE(p_offset, 0)
)
SELECT paged.id, counted.total FROM paged CROSS JOIN counted;
$$;

-- JSON params wrapper (supports birthdate=geYYYY-MM-DD / leYYYY-MM-DD too)
CREATE OR REPLACE FUNCTION fhir_ext.fhir_search(p_resource_type text, p_params jsonb)
RETURNS TABLE(id uuid, total bigint) LANGUAGE sql STABLE AS $$
WITH bd AS (
  SELECT
    p_params->>'birthdate'              AS raw,
    NULLIF(p_params->>'birthdate_ge','') AS ge_in,
    NULLIF(p_params->>'birthdate_le','') AS le_in
),
norm AS (
  SELECT
    COALESCE(
      ge_in,
      CASE WHEN raw ~ '^ge[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN substring(raw from 3) END
    )::date AS ge,
    COALESCE(
      le_in,
      CASE WHEN raw ~ '^le[0-9]{4}-[0-9]{2}-[0-9]{2}$' THEN substring(raw from 3) END
    )::date AS le
  FROM bd
)
SELECT * FROM fhir_ext.fhir_search_patient(
        NULLIF(p_params->>'name',''),
        (SELECT ge FROM norm),
        (SELECT le FROM norm),
        NULLIF(p_params->>'gender',''),
        COALESCE(LEAST(GREATEST((p_params->>'_count')::int, 1), 100), 20),
        COALESCE((p_params->>'_offset')::int, 0)
              ) WHERE p_resource_type = 'Patient';
$$;


-- Grants on schema-qualified functions
GRANT EXECUTE ON FUNCTION
fhir_ext.fhir_put(text, jsonb),
  fhir_ext.fhir_get(text, uuid),
  fhir_ext.fhir_search_patient(text, date, date, text, int, int),
  fhir_ext.fhir_search(text, jsonb)
TO fhir;

RESET ROLE;  -- create public shims as admin

-- Public shims
CREATE OR REPLACE FUNCTION public.fhir_get(p_resource_type text, p_id uuid)
RETURNS jsonb LANGUAGE sql STABLE AS $$
SELECT fhir_ext.fhir_get(p_resource_type, p_id);
$$;

CREATE OR REPLACE FUNCTION public.fhir_put(p_resource_type text, p_resource jsonb)
RETURNS uuid LANGUAGE sql VOLATILE AS $$
SELECT fhir_ext.fhir_put(p_resource_type, p_resource);
$$;

CREATE OR REPLACE FUNCTION fhir_ext.fhir_count(resource text, filters jsonb)
RETURNS bigint LANGUAGE sql STABLE AS $$
SELECT coalesce(max(s.total), 0)::bigint FROM fhir_ext.fhir_search(resource, filters) s;
$$;

CREATE OR REPLACE FUNCTION public.fhir_count(resource text, filters jsonb)
RETURNS bigint LANGUAGE sql STABLE AS $$
SELECT fhir_ext.fhir_count(resource, filters);
$$;


-- Restrict execution to app role
REVOKE EXECUTE ON FUNCTION public.fhir_get(text, uuid)  FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.fhir_put(text, jsonb) FROM PUBLIC;
REVOKE EXECUTE ON FUNCTION public.fhir_count(text, jsonb) FROM PUBLIC;

-- Grant execution to app role
GRANT EXECUTE ON FUNCTION public.fhir_get(text, uuid)  TO fhir;
GRANT EXECUTE ON FUNCTION public.fhir_put(text, jsonb) TO fhir;
GRANT EXECUTE ON FUNCTION public.fhir_count(text, jsonb) TO fhir;
