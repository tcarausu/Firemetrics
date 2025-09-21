-- Functions for the FHIR API

-- Function for the put/Insert/Create a new entry in the patient store
CREATE OR REPLACE FUNCTION fhir_ext.fhir_put(p_resource_type text, p_resource jsonb)
    RETURNS uuid
    LANGUAGE plpgsql
    VOLATILE
AS $$
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

    -- prefer payload id; else generate
    v_id := COALESCE(NULLIF(p_resource->>'id','')::uuid, gen_random_uuid());

    -- detect update vs insert
SELECT EXISTS (SELECT 1 FROM fhir_ext.patient_store WHERE id = v_id) INTO v_existing;

-- next version
IF v_existing THEN
SELECT version + 1 INTO v_version FROM fhir_ext.patient_store WHERE id = v_id;
ELSE
        v_version := 1;
END IF;

    -- normalize id + meta into JSON
    v_payload := p_resource - 'meta';
    v_payload := jsonb_set(v_payload, '{id}', to_jsonb(v_id::text), true);
    v_payload := jsonb_set(v_payload, '{meta}', '{}'::jsonb, true);
    v_payload := jsonb_set(v_payload, '{meta,versionId}', to_jsonb(v_version::text), true);
    v_payload := jsonb_set(
        v_payload, '{meta,lastUpdated}',
        to_jsonb(to_char((v_now AT TIME ZONE 'UTC'), 'YYYY-MM-DD"T"HH24:MI:SS"Z"')),
        true
     );

    -- UPSERT with bookkeeping
INSERT INTO fhir_ext.patient_store(id, resource, resource_type, created_at, updated_at, version)
VALUES (v_id, v_payload, 'Patient', v_now, v_now, v_version)
    ON CONFLICT (id) DO UPDATE
                            SET
                                updated_at = EXCLUDED.updated_at,
                            version    = fhir_ext.patient_store.version + 1,
                            resource   = jsonb_set(
                            jsonb_set(
                            EXCLUDED.resource,
                            '{meta,versionId}',
                            to_jsonb((fhir_ext.patient_store.version + 1)::text),
                            true
                            ),
                            '{meta,lastUpdated}',
                            to_jsonb(to_char((EXCLUDED.updated_at AT TIME ZONE 'UTC'),
                            'YYYY-MM-DD"T"HH24:MI:SS"Z"')),
                            true
                            );
RETURN v_id;
END$$;

-- Function for the Get by id; retrieving the exact entry for the Id
CREATE OR REPLACE FUNCTION fhir_ext.fhir_get(p_resource_type text, p_id uuid)
    RETURNS jsonb
    LANGUAGE sql
    STABLE
AS $$
SELECT resource
FROM fhir_ext.patient_store
WHERE resource_type = p_resource_type AND id = p_id
    $$;

-- Function for the Search functionality. retrieving the exact entry based on the filters
-- such as name, gender, birthdate, etc.
CREATE OR REPLACE FUNCTION fhir_ext.fhir_search_patient(
    p_name     text,
    p_birth_ge date,   -- birthDate status, if it's bigger then (>=)
    p_birth_le date,   -- birthDate <=
    -- checking gender equality; filtering done on the app layer namely ("male"),"female"),("other"), ("unknown");
    p_gender   text,
    p_limit    int,    -- page size (default 20) as part of the parameters
    p_offset   int     -- page offset (default 0) as part of the parameters
)
    RETURNS TABLE(id uuid, total bigint)
    LANGUAGE sql
    STABLE
AS $$
WITH base AS (
    SELECT id, created_at
    FROM fhir_ext.patient_store
    WHERE resource_type = 'Patient'
      AND (p_gender   IS NULL OR lower(resource->>'gender') = lower(p_gender))
      AND (p_birth_ge IS NULL OR fhir_ext.birthdate_of(resource) >= p_birth_ge)
      AND (p_birth_le IS NULL OR fhir_ext.birthdate_of(resource) <= p_birth_le)
      AND (p_name     IS NULL OR fhir_ext.flatten_name(resource) ILIKE '%'||lower(p_name)||'%')
),
     counted AS (SELECT count(*) AS total FROM base),
     paged AS (
         SELECT id
         FROM base
         ORDER BY created_at, id
         LIMIT COALESCE(p_limit, 20)
             OFFSET COALESCE(p_offset, 0)
     )
SELECT paged.id, counted.total FROM paged CROSS JOIN counted;
$$;

-- Parsable JSON search function, as a Wrapper around the above function.
-- We parse the JSON and delegate to fhir_search_patient.
CREATE OR REPLACE FUNCTION fhir_ext.fhir_search(p_resource_type text, p_params jsonb)
    RETURNS TABLE(id uuid, total bigint)
    LANGUAGE sql
    STABLE
AS $$
SELECT *
FROM fhir_ext.fhir_search_patient(
        NULLIF(p_params->>'name',''),
        NULLIF(p_params->>'birthdate_ge','')::date,
        NULLIF(p_params->>'birthdate_le','')::date,
        NULLIF(p_params->>'gender',''),
        -- this count has a limit of 100; and defaults to 20
        COALESCE(LEAST(GREATEST((p_params->>'_count')::int, 1), 100), 20),
        COALESCE((p_params->>'_offset')::int, 0)
     )
WHERE p_resource_type = 'Patient';
$$;

CREATE OR REPLACE FUNCTION public.fhir_search(p_resource_type text, p_params jsonb)
    RETURNS TABLE(id uuid, total bigint)
    LANGUAGE sql
    STABLE
AS $$
SELECT * FROM fhir_ext.fhir_search(p_resource_type, p_params);
$$;


-- Represents execution access to what function he can use
GRANT EXECUTE ON FUNCTION
fhir_ext.fhir_put(text, jsonb),
    fhir_ext.fhir_get(text, uuid),
    fhir_ext.fhir_search_patient(text, date, date, text, int, int),
    fhir_ext.fhir_search(text, jsonb)
    TO fhir;


-- Count wrapper function for easier and quicker access to the count.
create or replace function fhir_ext.fhir_count(resource text, filters jsonb)
    returns bigint
    language sql
    stable
as $$
select coalesce(max(s.total), 0)::bigint
from fhir_ext.fhir_search(resource, filters) as s;
$$;

CREATE OR REPLACE FUNCTION public.fhir_get(p_resource_type text, p_id uuid)
RETURNS jsonb LANGUAGE sql STABLE AS $$
SELECT fhir_ext.fhir_get(p_resource_type, p_id);
$$;

CREATE OR REPLACE FUNCTION public.fhir_put(p_resource_type text, p_resource jsonb)
RETURNS uuid LANGUAGE sql VOLATILE AS $$
SELECT fhir_ext.fhir_put(p_resource_type, p_resource);
$$;


-- Public shim so unqualified calls resolve even if search_path isn't set
create or replace function public.fhir_count(resource text, filters jsonb)
    returns bigint
    language sql
    stable
as $$
select fhir_ext.fhir_count(resource, filters);
$$;
grant execute on function fhir_ext.fhir_count(text, jsonb) to fhir;

-- Permissions for the app role (adjust 'fhir' if your DB user differs)
grant execute on function public.fhir_get(text, uuid) to fhir;
grant execute on function public.fhir_put(text, jsonb) to fhir;
grant execute on function public.fhir_count(text, jsonb) to fhir;