DO $clean$
BEGIN
  IF to_regclass('patient_store') IS NOT NULL THEN
    EXECUTE 'TRUNCATE TABLE patient_store RESTART IDENTITY CASCADE';
END IF;
END
$clean$;
