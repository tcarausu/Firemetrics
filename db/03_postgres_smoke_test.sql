
-- Search Section, with mutliple examples

-- Should resolve without error (0 rows is fine if table empty)
SELECT * FROM fhir_search('Patient', '{"name":"doe","_count":10,"_offset":0}'::jsonb);

-- Minimal by name
SELECT * FROM fhir_search(
        'Patient',
        '{"name":"doe","_count":10,"_offset":0}'::jsonb
              );

-- SEARCH – gender + birthdate range
SELECT * FROM fhir_search(
        'Patient',
        '{
          "name":"doe",
          "gender":"female",
          "birthdate_ge":"1980-01-01",
          "birthdate_le":"1990-12-31",
          "_count":10,
          "_offset":0
        }'::jsonb
              );

-- 5) (Optional) Pagination demo: page 2 (offset 1) with same filter
SELECT * FROM fhir_search(
        'Patient',
        '{
          "name":"doe",
          "_count":1,
          "_offset":1
        }'::jsonb
              );


------------- Extra checks for search and PUT

-- Expect: setof uuid
select * from fhir_search('Patient', '{}'::jsonb);

-- For total without a special function:
select count(*) from fhir_search('Patient', '{}'::jsonb);


-- name substring (case-insensitive per your design)
select * from fhir_search('Patient', '{"name":"doe"}'::jsonb);

-- gender
select * from fhir_search('Patient', '{"gender":"female"}'::jsonb);

-- birthdate range
select * from fhir_search('Patient', '{"birthdate_ge":"1980-01-01","birthdate_le":"1990-12-31"}'::jsonb);

-- combined
select * from fhir_search('Patient', '{"name":"doe","gender":"female","birthdate_ge":"1980-01-01","birthdate_le":"1990-12-31"}'::jsonb);

--check for for fhir_count
select fhir_ext.fhir_count('Patient', '{}'::jsonb);  -- schema-qualified
select fhir_count('Patient', '{}'::jsonb);           -- unqualified via public shim

--PUT

-- Insert one Patient and test end-to-end
SELECT fhir_ext.fhir_put('Patient', '{
  "resourceType":"Patient",
  "name":[{"family":"Smoke","given":["Test"]}],
  "gender":"female",
  "birthDate":"1985-04-12"
}'::jsonb) AS id \gset

--needs example ('a2a88b93-044f-41b7-ba2a-8d288a1c466c') aka take any id created and with '' dump it in.
SELECT fhir_ext.fhir_get('Patient', CAST(? AS uuid));

-- get by uuid test: example: a2a88b93-044f-41b7-ba2a-8d288a1c466c (works with both formats)
select fhir_ext.fhir_get('Patient', :id::uuid)::text;
