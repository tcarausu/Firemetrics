package fhir.services

import org.springframework.stereotype.Service
import org.hl7.fhir.r4b.model.Patient
import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.validation.FhirValidator
import ca.uhn.fhir.validation.ValidationResult

import ca.uhn.fhir.context.support.DefaultProfileValidationSupport
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport

@Service
class FhirValidationService(private val ctx: FhirContext) {
    private val validator: FhirValidator = run {
        val chain = ValidationSupportChain(
            DefaultProfileValidationSupport(ctx),
            CommonCodeSystemsTerminologyService(ctx),
            InMemoryTerminologyServerValidationSupport(ctx)
        )
        val instance = FhirInstanceValidator(chain)
        ctx.newValidator().apply {
            registerValidatorModule(instance)
            setValidateAgainstStandardSchema(false)
            setValidateAgainstStandardSchematron(false)
        }
    }

    fun validatePatient(resource: String) {
        val parser = ctx.newJsonParser()
        val patient = parser.parseResource(Patient::class.java, resource)
        val result: ValidationResult = validator.validateWithResult(patient)
        if (!result.isSuccessful) {
            val issues = result.messages.joinToString("; ") { "[${it.severity}] ${it.locationString} - ${it.message}" }
            throw IllegalArgumentException("FHIR Validation failed: $issues")
        }
    }
}
