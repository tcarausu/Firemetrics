# Firemetrics FHIR (R4B) – Patient (Kotlin)

The Goal for this project is/was to create a solution usable in the context of Firemetrics using the 2 languages I felt
most comfortable with (Kotlin/Java).

The task requires the use of FHIR (Healthcare data), the use of Postgres with personally created/developed Extensions.
Hence I decided on the use of Kotlin/Spring Boot for the backend. With most early
tests (FAST API) done using Postman for easier checks, data collection and debugging.

Minimal FHIR **Patient** service in Kotlin (Spring Boot, Java 21), backed by PostgreSQL 17 with custom SQL functions and indexes.
All persistence and queries go through the DB extension layer (no ORM/Hibernate).
This repo contains split, ordered SQL scripts to provision a clean PostgreSQL database for a minimal FHIR Patient store.
If I get there in due time I will create a **Rust** version/plugin.

## Task Objective:

Build a small FHIR server for the [**Patient**](https://hl7.org/fhir/R4B/patient.html) resource that showcases your full‑stack skills: a HTTP service,PostgreSQL
with a custom extension, FHIR REST API with search and pagination.

## Requirements

### Functional Scope:
* Implement a minimal FHIR R4B compliant API for Patient with:

  - `POST /fhir/Patient` — create **Patient**, assign `ID`, set `meta.versionId=1` & `meta.lastUpdated`, return `Location`,
`ETag`, `Last-Modified`.
  - `GET /fhir/Patient/{id}` — fetch by id.
  - `GET /fhir/Patient?name=...&birthdate=...&gender=...&_count=...&_offset=...` — search with stable pagination.

### Database & extension:
* Persist and retrieve resources through the Postgres extension, store payloads as jsonb, and add any helper tables/indexes
  you need for search.

```sql
-- Example interface shape (you may adapt names/signatures)
SELECT fhir_put('Patient', $jsonb)                -- → uuid (new id)
SELECT fhir_get('Patient', $uuid)                 -- → jsonb
SELECT fhir_search('Patient', $param, $op, $value) -- → setof uuid
SELECT fhir_count('Patient', $jsonb)               -- → long (for pagination) / added for count perhaps quicker

```
* You may explore [PGRX](https://github.com/pgcentralfoundation/pgrx) as a way to build Postgres extensions ergonomically.
  (Explored it for extra information)

### Nice‑to‑have (optional)

* History:
  `GET /fhir/Patient/{id}/_history`.
* Richer: `OperationOutcome` details (error codes, locations).

### Acceptance criteria

* Resources are persisted and queried via the extension, not direct ad‑hoc SQL against app tables.
* Search supports name substring, birthdate ranges (`ge/le`), and gender; pagination is stable.
* Tests are automated and reproducible.


### Notes

- **birthDate** must be a full date: `YYYY-MM-DD`. Partial dates are not accepted in this slice.

---
# Database/Postgresql Setup:
This repo contains ordered SQL scripts to provision a clean PostgreSQL database.

### Files & Order

1. `db/00_drop.sql` — terminate sessions, drop DB + role. **Destructive.**
2. `db/01_bootstrap.sql` — create role + DB, set default `search_path`.
3. `db/02_schema.sql` — connect to `fhir`, create schema, extensions, tables, grants.
4. `db/03_indexes.sql` — helper functions + indexes.
5. `db/04_functions.sql` — PUT/GET/Search functions, shims, grants.
6. `db/05_smoketests.sql` — quick sanity checks.

### Run Manually with `psql`

```bash
# Adjust env as needed (Assuming that the postgres port is 5432)
export PGHOST=localhost PGPORT=5432 PGUSER=postgres

psql -v ON_ERROR_STOP=1 -d postgres -f db/00_drop.sql
psql -v ON_ERROR_STOP=1 -d postgres -f db/01_bootstrap.sql
psql -v ON_ERROR_STOP=1 -d fhir     -f db/02_schema.sql
psql -v ON_ERROR_STOP=1 -d fhir     -f db/03_indexes.sql
psql -v ON_ERROR_STOP=1 -d fhir     -f db/04_functions.sql
psql -v ON_ERROR_STOP=1 -d fhir     -f db/05_smoketests.sql
```

### Notes & Choices

* **Ownership:** Objects under `fhir_ext` owned by role `fhir`. Public shims granted to `fhir`.
* **Security:** Avoid `public` schema. Use strong passwords instead of `fhir`.
* **Extensions:** `pgcrypto`, `pg_trgm` required.
* **Idempotency:** `IF EXISTS / IF NOT EXISTS` used.
* **Search Path:** Default `search_path = fhir_ext, public`.

---

## Quickstart (Docker)

### `docker-compose.yml`

```yaml
services:
  db:
    image: postgres:17-alpine
    environment:
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres -d postgres"]
      interval: 5s
      timeout: 3s
      retries: 20
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./db:/docker-entrypoint-initdb.d:ro

  app:
    build: .
    depends_on:
      db:
        condition: service_healthy
    ports:
      - "8081:8081"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/fhir
      SPRING_DATASOURCE_USERNAME: fhir
      SPRING_DATASOURCE_PASSWORD: fhir
      SPRING_DATASOURCE_HIKARI_CONNECTIONINITSQL: "SET search_path = fhir_ext, public"

volumes:
  pgdata:
```

### Run the Stack

```bash
docker compose up --build
```

* Check health:

  ```bash
  curl http://localhost:8081/actuator/health
  ```
* Check DB contents:

  ```bash
  docker compose exec -T db psql -U fhir -d fhir -c "select count(*) from fhir_ext.patient_store;"
  ```

Reset DB & re-seed:

```bash
docker compose down -v && docker compose up --build
```

---

## Testing

### Kotlin Tests

```bash
./gradlew test
```

### SQL Smoke Tests

```bash
docker compose exec -T db psql -U fhir -d fhir -f /docker-entrypoint-initdb.d/05_smoketests.sql
```

---

## API Examples

### Create a Patient

```bash
curl -i -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "name": [{ "family": "Doe", "given": ["Jane"] }],
    "gender": "female",
    "birthDate": "1990-05-01"
  }' \
  http://localhost:8081/fhir/Patient
```

### Get Patient by ID

```bash
curl -s http://localhost:8081/fhir/Patient/<uuid>
```

### Search Patients

```bash
curl -s 'http://localhost:8081/fhir/Patient?name=doe&gender=female&birthdate:ge=1980-01-01&_count=10&_offset=0'
```

---
## Working docker compose setup
```bash
docker compose down -v
docker compose up --build

docker compose exec -T db psql -U fhir -d fhir -f /docker-entrypoint-initdb.d/05_smoketests.sql

```
Upon completing this, you should see the following:
Run either through curl or Postman, as I prefer Postman and easier solution.
---

## CI/CD (GitHub Actions)

```yaml
name: CI (build + ephemeral run)

on:
    push:
        branches: [ master ]
    pull_request:
        branches: [ master ]

jobs:
    build-test:
        runs-on: ubuntu-latest
        timeout-minutes: 30

        steps:
            - uses: actions/checkout@v4

            - name: Set up JDK 21
              uses: actions/setup-java@v4
              with:
                  distribution: temurin
                  java-version: '21'

            - name: Cache Gradle
              uses: actions/cache@v4
              with:
                  path: |
                      ~/.gradle/caches
                      ~/.gradle/wrapper
                  key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
                  restore-keys: ${{ runner.os }}-gradle-

            - name: Make Gradlew executable
              run: chmod +x gradlew

            - name: Spotless check
              run: ./gradlew spotlessCheck --no-daemon

            - name: Clean + build (runs tests)
              run: |
                  chmod +x gradlew
                  ./gradlew clean build --no-daemon

    compose-smoke:
        needs: build-test
        runs-on: ubuntu-latest
        timeout-minutes: 20

        steps:
            - uses: actions/checkout@v4

            # build & run your stack on the runner using your docker-compose.yml
            - name: Start stack (detached)
              run: docker compose -f docker-compose.yml up -d --build

            - name: Health check
              run: |
                  for i in {1..30}; do
                    if curl -fsS http://localhost:8081/actuator/health | grep -q '"status":"UP"'; then
                      echo "App is UP"; exit 0
                    fi
                    echo "Waiting for app... ($i)"; sleep 5
                  done
                  echo "App did not become healthy"; docker compose logs; exit 1

            - name: Collect logs
              if: always()
              run: |
                  mkdir -p logs
                  docker compose logs > logs/compose.log || true

            - name: Upload logs artifact
              if: always()
              uses: actions/upload-artifact@v4
              with:
                  name: compose-logs
                  path: logs

            - name: Tear down
              if: always()
              run: docker compose -f docker-compose.yml down -v --remove-orphans

```
### Index verification (EXPLAIN)

Run:
```bash
docker compose exec -T db psql -U fhir -d fhir -f /docker-entrypoint-initdb.d/06_explain.sql
```
## EXPLAIN Examples

To verify index usage and stable pagination, you can run the following SQLs
against the `fhir` database (Postgres 17). These correspond to the indexes
defined in `03_indexes.sql`.

### Example of Explain forEnd to end search
```sql
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

```
---

## Deliverables

The server, written in Kotlin is located in src folder. Keeping it in server/ will not operate as I attempted multiple formats and permutations using gradle.
* `src/` → Kotlin Spring Boot service
* `db/` → Postgres extension scripts
* `Dockerfile`, `docker-compose.yml`
* `README.md` (this document)

---

## Spec References

* **FHIR R4B Patient (v4.3.0):** [https://hl7.org/fhir/R4B/patient.html](https://hl7.org/fhir/R4B/patient.html)
* **Postgres 17**
* **PGRX extension framework**


## PS - Upgrades
If the solution is later updated to release 1.1, I will have to update the format of the SQL scripts.:

* fhir_ext--1.0--1.1.sql → script that upgrades an existing 1.0 install to 1.1.
* fhir_ext--1.1.sql → full install script for a fresh DB.


## CI/CD Badge

[![CI][ci-badge]][ci-link] (missing deployment)
[![CI/CD][cicd-badge]][cicd-link]

[ci-badge]: https://github.com/tcarausu/Firemetrics/actions/workflows/ci.yml/badge.svg
[ci-link]: https://github.com/tcarausu/Firemetrics/actions/workflows/ci.yml

[cicd-badge]: https://github.com/tcarausu/Firemetrics/actions/workflows/main.yml/badge.svg
[cicd-link]: https://github.com/tcarausu/Firemetrics/actions/workflows/main.yml

