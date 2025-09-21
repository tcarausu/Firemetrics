package fhir.config

import ca.uhn.fhir.context.FhirContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class FhirConfig {
    @Bean
    fun fhirCtxR4B(): FhirContext = FhirContext.forR4B()
}
