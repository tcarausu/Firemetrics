-- Drop/Cleanup; while on postgres or other db's. Requires superuser/owner.

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_database WHERE datname = 'fhir') THEN
    PERFORM pg_terminate_backend(pid)
    FROM pg_stat_activity
    WHERE datname = 'fhir' AND pid <> pg_backend_pid();
END IF;
END$$;

DROP DATABASE IF EXISTS fhir;
DROP ROLE IF EXISTS fhir;
