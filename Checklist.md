### This document represents the Checklist I created and used to track my progress

# Checklist (Kotlin 2.1 + PostgreSQL 17 + pgrx + FHIR R4B Patient)


## 0. Acceptance Criteria & Deliverables
- [ ] 0.1 All persistence and retrieval must go through the Postgres extension (no direct ad-hoc SQL in app).
- [ ] 0.2 Pagination must be stable and deterministic.
- [ ] 0.3 Tests must be automated and reproducible (CI green).
- [ ] 0.4 Deliverables:
    - [ ] 0.4.1 `server/` : FHIR REST API web service (Kotlin 2.1). <- server can't be used as that breaks gradle, it is essentially **src** folder.
    - [ ] 0.4.2 `db/` : Postgres extension alternative to pgrx. 
    - [ ] 0.4.3 `README` : overview, exact run/test commands, API overview (paths/params, expected headers, sample requests/responses).
    - [ ] 0.4.4 Example curl/Postman collection.
    - [ ] 0.4.5 License, `.editorconfig`, ktlint/spotless (linting tools), CI badge.

## 1. Environment & Tooling
- [ ] 1.1 Kotlin 2.1 project (Gradle Kotlin DSL, JDK 21): apply Kotlin JVM, kotlinx-serialization (JSON).
- [ ] 1.2 Configure Gradle build (Kotlin plugin, dependencies block).
- [ ] 1.3 Project structure (`server/`, `db/`). 
- [ ] 1.4 PostgreSQL 17 running locally (Docker or native).
- [ ] 1.5 Rust toolchain + `cargo-pgrx` installed; initialize for PG17 (`cargo pgrx init`) and scaffold extension (`cargo pgrx new pg_fhir`).
- [ ] 1.6 Add Docker/Docker Compose for Postgres 17 + service container.
- [ ] 1.7 Configure CI (build, test, lint; cache Gradle).
- [ ] 1.8 Ensure database lifecycle probes (`DbSanity`) check DB availability at startup/shutdown.

## 2. Understand the Spec Slice (FHIR R4B Patient)
- [ ] 2.1 Support core Patient fields: `name`, `birthDate`, `gender`.
- [ ] 2.2 Implement search parameters:
    - [ ] 2.2.1 `name` (string match across HumanName fields).
    - [ ] 2.2.2 `birthdate` (date; comparators `ge`, `le`).
    - [ ] 2.2.3 `gender` (exact token).
- [ ] 2.3 Support search result parameters: `_count` (page size), stable pagination via offsets/links.
- [ ] 2.4 Ignore unknown parameters unless strict handling is requested.
- [ ] 2.5 Validate inputs against FHIR R4B Patient schema.
- [ ] 2.6 Use HAPI FHIR context for R4B parsing/validation (Spring `FhirConfig`).
- [ ] 2.7 Document accepted `birthDate` format (`YYYY-MM-DD`); note that partial dates are not supported in this slice.

## 3. API Endpoints (server/)
- [ ] 3.1 `POST /fhir/Patient`
    - [ ] 3.1.1 Validate minimal Patient JSON (R4B shape).
    - [ ] 3.1.2 Call extension function to persist (`fhir_put`).
    - [ ] 3.1.3 Return `201 Created` with headers (`Location`, `ETag`, `Last-Modified`).
- [ ] 3.2 `GET /fhir/Patient/{id}`
    - [ ] 3.2.1 Fetch via extension (`fhir_get`).
    - [ ] 3.2.2 Return Patient JSON.
    - [ ] 3.2.3 Include `ETag` and `Last-Modified` headers.
- [ ] 3.3 `GET /fhir/Patient?name=&birthdate=&gender=&_count=&_offset=`
    - [ ] 3.3.1 Implement substring search for `name`.
    - [ ] 3.3.2 Implement range comparators for `birthdate` (`ge`, `le`).
    - [ ] 3.3.3 Implement exact token search for `gender`.
    - [ ] 3.3.4 Return a Bundle with entries and paging (`self`, maybe `next`) links, honoring `_count`.
    - [ ] 3.3.5 Cap `_count` to a safe maximum (e.g., 100) to prevent unbounded result sets.

## 4. Database Extension (db/ with pgrx)
- [ ] 4.1 Define schema owned by the extension (avoid app ad-hoc SQL).
- [ ] 4.2 Table `fhir_resource` (or `patient_store` in SQL impl):
    - [ ] 4.2.1 `id uuid primary key`.
    - [ ] 4.2.2 `resource_type text` (e.g., 'Patient').
    - [ ] 4.2.3 `resource jsonb` (raw Patient).
    - [ ] 4.2.4 Optional helpers: `birthdate`, `gender`, `name_text` (denormalized).
    - [ ] 4.2.5 Add CHECK constraint: enforce `resource->>'resourceType' = 'Patient'`.
- [ ] 4.3 Expose SQL functions (ensure alignment with expected signatures):
    - [ ] 4.3.1 `fhir_put('Patient', $jsonb) → uuid`.
    - [ ] 4.3.2 `fhir_get('Patient', $uuid) → jsonb`.
    - [ ] 4.3.3 `fhir_search('Patient', $param, $op, $value) → setof uuid`.
    - [ ] 4.3.4 `fhir_count('Patient', $filters) → bigint`.
- [ ] 4.4 Write SQL install/upgrade scripts and tests.
- [ ] 4.5 Document function signatures & constraints.
- [ ] 4.6 Provide public shims (`public.fhir_search`, `public.fhir_count`) to simplify app queries.
- [ ] 4.7 Add public shims for `fhir_get` and `fhir_put` for symmetry.

## 5. Indexing & Query Performance
- [ ] 5.1 Create GIN index on `resource` for JSONB operators (`@>`, `@?`, `@@`).
- [ ] 5.2 If frequent path lookups: consider `jsonb_path_ops` indexes.
- [ ] 5.3 For substring `name`, extract normalized `name_text` and index with `pg_trgm`.
- [ ] 5.4 Add helper indexes (birthdate, gender enum).
- [ ] 5.5 Verify index usage with `EXPLAIN`.
- [ ] 5.6 Enforce execution timeouts & reasonable `_count` limits.
- [ ] 5.7 Include `EXPLAIN` examples in README to demonstrate index usage.

## 6. Extension Functions (pgrx/Rust) — Behavior
- [ ] 6.1 `fhir_put`: validate type, generate UUID, set metadata, insert JSONB + helpers.
    - [ ] 6.1.1 Auto-generate and maintain `meta.versionId` and `meta.lastUpdated`.
- [ ] 6.2 `fhir_get`: primary key lookup, return JSONB.
- [ ] 6.3 `fhir_search`:
    - [ ] 6.3.1 `name`: JSONPath or trigram search.
    - [ ] 6.3.2 `birthdate`: comparators (`ge`, `le`).
    - [ ] 6.3.3 `gender`: exact match.

## 7. Paging & Result Assembly
- [ ] 7.1 Implement `_count` and `_offset` server-side.
- [ ] 7.2 Return FHIR Bundle with `total` and paging links (`self`, `next`).
- [ ] 7.3 Ensure pagination is stable with deterministic sort (`id`, `birthdate,id`).
- [ ] 7.4 Honor FHIR guidance: ignore unknown params unless strict.

## 8. "Server" Wiring (server/ == src/main/kotlin/ )
- [ ] 8.1 Define routes and DTOs with JSON serialization/validation.
- [ ] 8.2 DB access via JDBC pool (Hikari).
- [ ] 8.3 Map errors to FHIR `OperationOutcome`.

## 9. Error Handling & OperationOutcome
- [ ] 9.1 Unified error mapper → FHIR `OperationOutcome` with HTTP codes.
- [ ] 9.2 Helpful diagnostics (error code, field pointer).
- [ ] 9.3 Input validation errors return proper FHIR responses.
- [ ] 9.4 Document mapping table (error codes → HTTP status → example payloads).

## 10. Tests & Reproducibility
- [ ] 10.1 Unit tests for route validation and edge cases.
- [ ] 10.2 Integration tests with extension-loaded DB:
    - [ ] 10.2.1 Create → fetch round-trip.
    - [ ] 10.2.2 Search by `name` substring.
    - [ ] 10.2.3 Search by `birthdate` (`ge`, `le`).
    - [ ] 10.2.4 Search by `gender`.
    - [ ] 10.2.5 Pagination stability with `_count`, `_offset`.
    - [ ] 10.2.6 Validate headers (`Location`, `ETag`, `Last-Modified`).
- [ ] 10.3 Extension tests (`#[pg_test]` or SQL fixtures).
- [ ] 10.4 End-to-end tests for create/get/search/pagination.
- [ ] 10.5 Provide `Makefile`/Gradle + Docker Compose for local setup.
- [ ] 10.6 Add lightweight load-test baseline for paging and record latency targets.

## 11. README Essentials
- [ ] 11.1 Overview + architecture (conversion/solution similar to the Rust pgrx extension).
- [ ] 11.2 Exact run/test commands.
- [ ] 11.3 API overview with sample requests/responses.
- [ ] 11.4 Reproducible test command in README.
- [ ] 11.5 DB API section: function signatures, params, return shapes, constraints.
- [ ] 11.6 Document DB tuning defaults for local/dev (`statement_timeout`, `work_mem`, `shared_buffers`).

## 12. Optional Enhancements
- [ ] 12.1 Implement history endpoint `GET /fhir/Patient/{id}/_history`.
- [ ] 12.2 ETag/version conflict detection.
- [ ] 12.3 Add richer `OperationOutcome` details with error codes and field locations.
- [ ] 12.4 Persist prior versions in `patient_history` table on update.

## 13. Observability & Ops
- [ ] 13.1 Structured logging (request id, patient id, SQL timings).
- [ ] 13.2 Propagate `X-Request-ID` header and bind to logging context (MDC).
- [ ] 13.3 Health/readiness probes.
- [ ] 13.4 Basic metrics (requests, latencies, DB calls).

## 14. Security & Compliance
- [ ] 14.1 Least-privileged DB role for API.
- [ ] 14.2 Validate JSON size limits; reject oversized payloads.
- [ ] 14.3 Sanitize logs (no PHI leakage).
- [ ] 14.4 Split DB roles: admin owner vs. app role.
- [ ] 14.5 Lock down `public` schema (revoke CREATE from PUBLIC).
- [ ] 14.6 Set safe defaults on app role (e.g., `statement_timeout`, `idle_in_transaction_session_timeout`).

## 15. Runbook (Developer Quickstart)
- [ ] 15.1 `docker compose up` runs Postgres 17 & server.
- [ ] 15.2 One-liner extension migration/install script.
- [ ] 15.3 Seed script for sample Patients.  
