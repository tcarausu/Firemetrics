package fhir.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

object OperationOutcomeFactory {
    private val mapper = jacksonObjectMapper().findAndRegisterModules()
    data class Issue(val severity: String, val code: String, val diagnostics: String? = null, val location: List<String>? = null)
    private fun oo(vararg issues: Issue) = mapOf(
        "resourceType" to "OperationOutcome",
        "issue" to issues.map {
            mapOf("severity" to it.severity, "code" to it.code) +
                    (it.diagnostics?.let { d -> mapOf("diagnostics" to d) } ?: emptyMap()) +
                    (it.location?.let { l -> mapOf("location" to l) } ?: emptyMap())
        }
    )
    fun badRequestInvalid(msg: String, location: String? = null) =
        ResponseEntity.badRequest()
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(mapper.writeValueAsString(oo(Issue("error", "invalid", msg, location?.let { listOf(it) }))))
    fun notFound(resource: String) =
        ResponseEntity.status(404)
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(mapper.writeValueAsString(oo(Issue("error","not-found",resource))))
    fun conflict(msg: String) =
        ResponseEntity.status(409)
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(mapper.writeValueAsString(oo(Issue("error","conflict",msg))))
}
