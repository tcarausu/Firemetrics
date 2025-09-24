-- Explain plans to verify index usage and stable pagination
-- 1) Name search (trigram)
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id
FROM fhir_ext.patient_store
WHERE resource_type = 'Patient'
  AND fhir_ext.flatten_name(resource) ILIKE '%doe%'
ORDER BY created_at, id
  LIMIT 10 OFFSET 0;

-- 2) Birthdate range using immutable extractor
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id
FROM fhir_ext.patient_store
WHERE resource_type = 'Patient'
  AND fhir_ext.birthdate_of(resource) >= DATE '1980-01-01'
  AND fhir_ext.birthdate_of(resource) <= DATE '1990-12-31'
ORDER BY created_at, id
  LIMIT 10;

-- 3) Gender equality (case-insensitive)
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT id
FROM fhir_ext.patient_store
WHERE resource_type = 'Patient'
  AND lower(resource->>'gender') = 'female'
ORDER BY created_at, id
  LIMIT 10;

-- 4) End-to-end function call
EXPLAIN (ANALYZE, BUFFERS, TIMING)
SELECT * FROM fhir_ext.fhir_search(
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
