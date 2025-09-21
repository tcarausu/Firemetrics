CREATE SCHEMA IF NOT EXISTS fhir_ext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
SET search_path = fhir_ext, public;

CREATE TABLE IF NOT EXISTS fhir_store (
                                          id UUID PRIMARY KEY,
                                          resource_type TEXT NOT NULL,
                                          payload JSONB NOT NULL,
                                          created_at TIMESTAMPTZ NOT NULL DEFAULT now()
    );

CREATE INDEX IF NOT EXISTS ix_payload_gin ON fhir_store USING GIN (payload jsonb_path_ops);
CREATE INDEX IF NOT EXISTS ix_resource_type ON fhir_store(resource_type);

-- fhir_put(resourceType, jsonb) -> uuid
CREATE OR REPLACE FUNCTION fhir_put(rt TEXT, body JSONB)
RETURNS UUID AS $$
DECLARE
new_id UUID := gen_random_uuid();
BEGIN
INSERT INTO fhir_store(id, resource_type, payload)
VALUES (new_id, rt, body || jsonb_build_object('id', new_id::text));
RETURN new_id;
END;
$$ LANGUAGE plpgsql;

-- fhir_get(resourceType, uuid) -> jsonb
CREATE OR REPLACE FUNCTION fhir_get(rt TEXT, rid UUID)
RETURNS JSONB AS $$
DECLARE
doc JSONB;
BEGIN
SELECT payload INTO doc FROM fhir_store WHERE id = rid AND resource_type = rt;
RETURN doc;
END;
$$ LANGUAGE plpgsql;

-- fhir_search(resourceType, filters jsonb) -> setof uuid
CREATE OR REPLACE FUNCTION fhir_search(rt TEXT, filters JSONB)
RETURNS SETOF UUID AS $$
DECLARE
v_name TEXT := COALESCE(filters->>'name', NULL);
  v_gender TEXT := COALESCE(filters->>'gender', NULL);
  v_ge TEXT := COALESCE(filters->>'birthdate_ge', NULL);
  v_le TEXT := COALESCE(filters->>'birthdate_le', NULL);
  v_count INT := GREATEST(COALESCE((filters->>'_count')::INT, 20), 1);
  v_offset INT := GREATEST(COALESCE((filters->>'_offset')::INT, 0), 0);
BEGIN
RETURN QUERY
SELECT id FROM fhir_store
WHERE resource_type = rt
  AND (v_gender IS NULL OR payload->>'gender' = v_gender)
  AND (
    v_name IS NULL OR EXISTS (
        SELECT 1 FROM jsonb_path_query_array(payload, '$.name[*].family') AS fam
        WHERE fam::text ILIKE '%' || v_name || '%'
    ) OR EXISTS (
        SELECT 1 FROM jsonb_path_query_array(payload, '$.name[*].given[*]') AS giv
        WHERE giv::text ILIKE '%' || v_name || '%'
    )
    )
  AND (v_ge IS NULL OR (payload->>'birthDate')::date >= v_ge::date)
  AND (v_le IS NULL OR (payload->>'birthDate')::date <= v_le::date)
ORDER BY id  -- deterministic for pagination
OFFSET v_offset LIMIT v_count;
END;
$$ LANGUAGE plpgsql;

-- fhir_count(resourceType, filters jsonb) -> bigint
CREATE OR REPLACE FUNCTION fhir_count(rt TEXT, filters JSONB)
RETURNS BIGINT AS $$
DECLARE
v_name TEXT := COALESCE(filters->>'name', NULL);
  v_gender TEXT := COALESCE(filters->>'gender', NULL);
  v_ge TEXT := COALESCE(filters->>'birthdate_ge', NULL);
  v_le TEXT := COALESCE(filters->>'birthdate_le', NULL);
  total BIGINT;
BEGIN
SELECT COUNT(*) INTO total FROM fhir_store
WHERE resource_type = rt
  AND (v_gender IS NULL OR payload->>'gender' = v_gender)
  AND (
    v_name IS NULL OR EXISTS (
        SELECT 1 FROM jsonb_path_query_array(payload, '$.name[*].family') AS fam
        WHERE fam::text ILIKE '%' || v_name || '%'
    ) OR EXISTS (
        SELECT 1 FROM jsonb_path_query_array(payload, '$.name[*].given[*]') AS giv
        WHERE giv::text ILIKE '%' || v_name || '%'
    )
    )
  AND (v_ge IS NULL OR (payload->>'birthDate')::date >= v_ge::date)
  AND (v_le IS NULL OR (payload->>'birthDate')::date <= v_le::date);
RETURN total;
END;
$$ LANGUAGE plpgsql;
