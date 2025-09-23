-- Direct db smoke tests, for quick sanity checks. can be run even on an empty DB.
\c fhir
-- Basic search calls
SELECT * FROM fhir_search('Patient', '{"name":"doe","_count":10,"_offset":0}'::jsonb);

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

-- Pagination demo
SELECT * FROM fhir_search('Patient', '{"name":"doe","_count":1,"_offset":1}'::jsonb);

-- Extra checks
SELECT * FROM fhir_search('Patient', '{}'::jsonb);
SELECT count(*) FROM fhir_search('Patient', '{}'::jsonb);
SELECT * FROM fhir_search('Patient', '{"name":"doe"}'::jsonb);
SELECT * FROM fhir_search('Patient', '{"gender":"female"}'::jsonb);
SELECT * FROM fhir_search('Patient', '{"birthdate_ge":"1980-01-01","birthdate_le":"1990-12-31"}'::jsonb);
SELECT * FROM fhir_search('Patient', '{"name":"doe","gender":"female","birthdate_ge":"1980-01-01","birthdate_le":"1990-12-31"}'::jsonb);
SELECT * FROM fhir_search('Patient', '{"birthdate":"ge1980-01-01","_count":1}'::jsonb);

-- Count wrappers
SELECT fhir_ext.fhir_count('Patient', '{}'::jsonb);
SELECT public.fhir_count('Patient', '{}'::jsonb);

-- PUT one Patient and GET it back
-- Created a short function to test the get function in this smoke environment
CREATE OR REPLACE FUNCTION fhir_ext.smoke_put_get_patient()
    RETURNS TABLE(captured_id uuid, resource text)
    LANGUAGE plpgsql AS $$
DECLARE
v uuid;
BEGIN
    v := fhir_ext.fhir_put('Patient', '{
      "resourceType":"Patient",
      "name":[{"family":"Smoke","given":["Test"]}],
      "gender":"female",
      "birthDate":"1985-04-12"
    }'::jsonb);

RETURN QUERY
SELECT v, fhir_ext.fhir_get('Patient', v)::text;
END $$;

-- “Normal” select output (two columns: captured_id, resource)
SELECT * FROM fhir_ext.smoke_put_get_patient();
