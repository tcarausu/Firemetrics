EXTENSION = fhir_patient
DATA_built = fhir_patient--1.0.sql
PG_CONFIG = pg_config
PGXS := $(shell $(PG_CONFIG) --pgxs)
include $(PGXS)

# Build the extension script from functions ONLY (strip psql backslash cmds)
$(DATA_built): parts/04_functions.sql
	cat parts/04_functions.sql | grep -v '^[[:space:]]*\\' > $@

clean:
	rm -f $(DATA_built)
