#!/bin/sh
set -euo pipefail
DB=fhir
SU="${POSTGRES_USER:-postgres}"

# 1) Schema/table/grants
psql -v ON_ERROR_STOP=1 -U "$SU" -d "$DB" -f /usr/src/fhir_patient/parts/02_schema.sql

# 2) Contrib extensions (safe if already created in step 1)
psql -v ON_ERROR_STOP=1 -U "$SU" -d "$DB" -c "CREATE EXTENSION IF NOT EXISTS pgcrypto;"
psql -v ON_ERROR_STOP=1 -U "$SU" -d "$DB" -c "CREATE EXTENSION IF NOT EXISTS pg_trgm;"

# 3) Helper funcs + indexes
psql -v ON_ERROR_STOP=1 -U "$SU" -d "$DB" -f /usr/src/fhir_patient/parts/03_indexes.sql

# 4) Our extension (functions from 04_functions.sql; it NO LONGER contains the two helpers)
psql -v ON_ERROR_STOP=1 -U "$SU" -d "$DB" -c "CREATE EXTENSION IF NOT EXISTS fhir_patient;"
