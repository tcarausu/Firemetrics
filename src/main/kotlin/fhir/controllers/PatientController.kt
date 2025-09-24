@file:Suppress("ktlint:standard:no-wildcard-imports")

package fhir.controllers

import ca.uhn.fhir.context.FhirContext
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import fhir.models.SearchParams
import fhir.repositories.PatientRepo
import fhir.services.FhirValidationService
import fhir.services.OperationOutcomeFactory
import fhir.utils.Enums
import org.hl7.fhir.r4b.model.Meta
import org.hl7.fhir.r4b.model.Patient
import org.hl7.fhir.r4b.model.ResourceType
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.UUID

@RestController
@RequestMapping("/fhir/Patient")
class PatientController(
    private val repo: PatientRepo,
    private val fhirContext: FhirContext,
    // added a validator to data
    private val validator: FhirValidationService,
) {
    private val log = org.slf4j.LoggerFactory.getLogger(PatientController::class.java)

    private val mapper =
        jacksonObjectMapper()
            .findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)

    @PostMapping(
        consumes = ["application/fhir+json", MediaType.APPLICATION_JSON_VALUE],
        produces = ["application/fhir+json", MediaType.APPLICATION_JSON_VALUE],
    )
    fun create(
        @RequestBody raw: String,
    ): ResponseEntity<String> {
        val parser = fhirContext.newJsonParser().setPrettyPrint(false)
        validator.validatePatient(raw) // throws IllegalArgumentException on invalid

        val patient: Patient = parser.parseResource(Patient::class.java, raw)

        require(patient.resourceType == ResourceType.Patient) {
            "resourceType must be Patient"
        }

        // Persist raw payload; DB will normalize id/meta
        val id = repo.put(raw)
        log.info("Persisted patient $id")

        val storedJson = repo.get(id) ?: error("Persisted resource not found: $id")
        val stored: Patient = parser.parseResource(Patient::class.java, storedJson)
        log.info("Stored patient $id with patient data $storedJson")

        // Fallback if there is meta preset.
        if (stored.meta == null || stored.meta.versionId == null || stored.meta.lastUpdated == null) {
            val m = stored.meta ?: Meta()
            if (m.versionId == null) m.versionId = "1"
            if (m.lastUpdated == null) m.lastUpdated = Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant())
            stored.meta = m
        }
        val responseJson = parser.encodeResourceToString(stored)

        return ResponseEntity
            .created(URI.create("/fhir/Patient/$id"))
            .eTag("""W/"${stored.meta?.versionId ?: "1"}"""")
            .apply { stored.meta?.lastUpdated?.let { lastModified(it.time) } }
            .header(HttpHeaders.LOCATION, "/fhir/Patient/$id")
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(responseJson)
    }

    @GetMapping(
        value = ["/{id}"],
        produces = ["application/fhir+json", MediaType.APPLICATION_JSON_VALUE],
    )
    fun getPatient(
        @PathVariable id: UUID,
    ): ResponseEntity<String> {
        val json = repo.get(id) ?: return OperationOutcomeFactory.notFound("Patient/$id")

        val parser = fhirContext.newJsonParser().setPrettyPrint(false)
        val patient: Patient = parser.parseResource(Patient::class.java, json)

        if (patient.meta == null || patient.meta.versionId == null || patient.meta.lastUpdated == null) {
            val m = patient.meta ?: Meta()
            if (m.versionId == null) m.versionId = "1"
            if (m.lastUpdated == null) m.lastUpdated = Date.from(OffsetDateTime.now(ZoneOffset.UTC).toInstant())
            patient.meta = m
        }

        val versionId = patient.meta!!.versionId
        val lastUpdated = patient.meta!!.lastUpdated

        return ResponseEntity.ok()
            .eTag("""W/"$versionId"""")
            .lastModified(lastUpdated.time)
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(json)
    }

    @GetMapping(produces = ["application/fhir+json", MediaType.APPLICATION_JSON_VALUE])
    fun search(
        @RequestParam(required = false) name: String?,
        @RequestParam(required = false, name = "birthdate:ge") birthdate_ge: String?,
        @RequestParam(required = false, name = "birthdate:le") birthdate_le: String?,
        @RequestParam(required = false) gender: String?,
        @RequestParam(defaultValue = "20") _count: Int,
        @RequestParam(defaultValue = "0") _offset: Int,
    ): ResponseEntity<String> {
        val genderEnum = runCatching { gender?.let { Enums.AdministrativeGender.fromJson(it) } }.getOrNull()
        if (gender != null && genderEnum == null) {
            return OperationOutcomeFactory.badRequestInvalid(
                "Invalid gender '$gender' (allowed: male|female|other|unknown)",
                "Patient.gender",
            )
        }

        // makes sure it fits the 4 digit- YYYY, MM, DD
        val dateRE = Regex("""^\d{4}-\d{2}-\d{2}$""")
        if (birthdate_ge != null && !dateRE.matches(birthdate_ge)) {
            val oo =
                mapOf(
                    "resourceType" to "OperationOutcome",
                    "issue" to
                        listOf(
                            mapOf(
                                "severity" to "error",
                                "code" to "invalid",
                                "diagnostics" to "birthdate:ge must be YYYY-MM-DD",
                            ),
                        ),
                )
            return ResponseEntity.badRequest()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(mapper.writeValueAsString(oo))
        }
        if (birthdate_le != null && !dateRE.matches(birthdate_le)) {
            val oo =
                mapOf(
                    "resourceType" to "OperationOutcome",
                    "issue" to
                        listOf(
                            mapOf(
                                "severity" to "error",
                                "code" to "invalid",
                                "diagnostics" to "birthdate:le must be YYYY-MM-DD",
                            ),
                        ),
                )
            return ResponseEntity.badRequest()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(mapper.writeValueAsString(oo))
        }

        val params =
            SearchParams(
                name = name,
                birthdate_ge = birthdate_ge,
                birthdate_le = birthdate_le,
                gender = genderEnum,
                _count = _count.coerceIn(1, 100),
                _offset = _offset,
            )

        val page = repo.search(params)

        val selfUrl =
            "/fhir/Patient?" +
                (name?.let { "name=$it&" } ?: "") +
                (gender?.let { "gender=$it&" } ?: "") +
                (birthdate_ge?.let { "birthdate:ge=$it&" } ?: "") +
                (birthdate_le?.let { "birthdate:le=$it&" } ?: "") +
                "_count=$_count&_offset=$_offset"

        if (page.total == 0L) {
            val emptyBundle =
                mapOf(
                    "resourceType" to "Bundle",
                    "type" to "searchset",
                    "total" to 0,
                    "entry" to emptyList<Any>(),
                    "link" to listOf(mapOf("relation" to "self", "url" to selfUrl)),
                )
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/fhir+json"))
                .body(mapper.writeValueAsString(emptyBundle))
        }

        val entries = page.ids.mapNotNull { repo.get(it) }.map { mapper.readTree(it) }

        val nextOffset = _offset + _count
        val links =
            listOfNotNull(
                mapOf("relation" to "self", "url" to selfUrl),
                if (nextOffset < page.total) {
                    mapOf(
                        "relation" to "next",
                        "url" to selfUrl.replace("&_offset=$_offset", "&_offset=$nextOffset"),
                    )
                } else {
                    null
                },
            )

        val bundle =
            mapOf(
                "resourceType" to "Bundle",
                "type" to "searchset",
                "total" to page.total,
                "entry" to entries.map { mapOf("resource" to it) },
                "link" to links,
            )

        val json = mapper.writeValueAsString(bundle)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(json)
    }
}
