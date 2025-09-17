# Firemetrics FHIR (R4B) – Patient (Kotlin + Python) 

The Goal for this project is/was to create a solution usable in the context of Firemetrics using the 2 languages I felt 
most comfortable with (Kotlin/Java and Python).

The task requires the use of FHIR (Healthcare data), the use of Postgres with personally created/developed Extensions. 
Hence I decided on the use of Kotlin/Spring Boot for the backend, and Python/FastAPI for the minimal frontend. With most early 
tests (FAST API) done using Postman for easier checks, data collection and debugging.

Minimal FHIR **Patient** service in two stacks (Kotlin/Spring Boot on Java 21, Python/FastAPI on 3.12/3.13), backed by
Postgres 17 (**via a custom extension**). All persistence and queries go through the extension (no direct ORM/Hibernate access).
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

---

## How to Run


### Start Postgres 17

```bash
docker compose up -d
```

### Run the server (Kotlin)

```bash
./gradlew bootRun
```

Server will start on `http://localhost:8080`.

### Run the server (Python/FastAPI)

```bash
uvicorn main:app --reload --port 8000
```

---

## How to Test

### Automated tests (Kotlin)

```bash
./gradlew test
```

### Python tests

```bash
pytest
```

---

## API Examples

### Create a patient

```bash
curl -i -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Patient",
    "name": [{ "family": "Doe", "given": ["Jane"] }],
    "gender": "female",
    "birthDate": "1990-05-01"
  }' \
  http://localhost:8080/fhir/Patient
```

**Response headers:**

```
HTTP/1.1 201 Created
Location: /fhir/Patient/550e8400-e29b-41d4-a716-446655440000
ETag: W/"1"
Last-Modified: Fri, 19 Sep 2025 10:15:30 GMT
```

### Get patient by ID

```bash
curl -s http://localhost:8080/fhir/Patient/550e8400-e29b-41d4-a716-446655440000
```

### Search patients

```bash
curl -s 'http://localhost:8080/fhir/Patient?name=doe&gender=female&birthdate:ge=1980-01-01&_count=10&_offset=0'
```

---

## Deliverables

* `server/` → Kotlin Spring Boot service
* `db/` → Postgres extension
* `frontend/` → Python FastAPI (optional UI/tests)
* README (this document)

---

## Spec References

* **FHIR R4B Patient (v4.3.0):** [https://hl7.org/fhir/R4B/patient.html](https://hl7.org/fhir/R4B/patient.html)
* **Postgres 17**
* **PGRX extension framework**

