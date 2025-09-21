package fhir.utils

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import ca.uhn.fhir.parser.DataFormatException
import org.springframework.http.HttpStatus

@ControllerAdvice
class ManualErrorHandling {
    private val mapper = jacksonObjectMapper()
        .findAndRegisterModules()
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun badJson(ex: HttpMessageNotReadableException): ResponseEntity<String> {
        val diag = ex.mostSpecificCause.message ?: "Invalid request payload"
        val oo = mapOf(
            "resourceType" to "OperationOutcome",
            "issue" to listOf(
                mapOf("severity" to "error", "code" to "invalid", "diagnostics" to diag)
            )
        )
        return ResponseEntity.badRequest()
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(mapper.writeValueAsString(oo))
    }

    @ExceptionHandler(DataFormatException::class)
    fun fhirParseError(ex: DataFormatException): ResponseEntity<String> {
        val diag = ex.message ?: "Invalid FHIR payload"
        val oo = mapOf(
            "resourceType" to "OperationOutcome",
            "issue" to listOf(
                mapOf("severity" to "error", "code" to "invalid", "diagnostics" to diag)
            )
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(mapper.writeValueAsString(oo))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun badArgument(ex: IllegalArgumentException): ResponseEntity<String> {
        val oo = mapOf(
            "resourceType" to "OperationOutcome",
            "issue" to listOf(
                mapOf("severity" to "error", "code" to "invalid", "diagnostics" to ex.message)
            )
        )
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.parseMediaType("application/fhir+json"))
            .body(mapper.writeValueAsString(oo))
    }

}
