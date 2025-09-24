package fhir

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import org.springframework.test.context.jdbc.SqlConfig
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Created test suite responsible for testing the POST/ insert functionality for Put /fhir/Patient
 * It contains the integration tests focused ONLY on POST /fhir/Patient
 * The working/happy path which returns the patient data; Location, ETag (W/"1"), Last-Modified,
 * meta.versionId=1, meta.lastUpdated present (ISO-8601)
 *
 * With the creation process cross checking/assert the HTTP status of 201 (Created)
 * 2nd: Test to specifically check the case of incorrect gender (Cross-check using validator)
 */
@Sql(
    scripts = ["classpath:test-clean.sql"],
    executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD,
    config = SqlConfig(errorMode = SqlConfig.ErrorMode.CONTINUE_ON_ERROR),
)
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientCreateTest {
    @Autowired lateinit var mvc: MockMvc
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    companion object {
        @Container
        private val pg =
            PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
                withDatabaseName("fhir")
                withUsername("fhir")
                withPassword("fhir")
                withInitScript("test-init.sql")
            }

        @JvmStatic
        @DynamicPropertySource
        fun register(reg: DynamicPropertyRegistry) {
            reg.add("spring.datasource.url") { pg.jdbcUrl }
            reg.add("spring.datasource.username") { pg.username }
            reg.add("spring.datasource.password") { pg.password }
            reg.add("spring.datasource.hikari.connectionInitSql") { "SET search_path = fhir_ext, public" }
        }
    }

    private fun newPatientJson(
        given: String = "Jane",
        family: String = "Doe",
        gender: String = "female",
        birthDate: String = "1985-02-17",
    ) = """
        {
          "resourceType": "Patient",
          "name": [{"family": "$family", "given": ["$given"]}],
          "gender": "$gender",
          "birthDate": "$birthDate"
        }
        """.trimIndent()

    // With the creation process cross-checking/assert the HTTP status of 201 (Created)
    @Test
    fun create_patient_assigns_id_and_sets_headers_and_meta() {
        val res =
            mvc.post("/fhir/Patient") {
                contentType = MediaType.valueOf("application/fhir+json")
                accept = MediaType.valueOf("application/fhir+json")
                content = newPatientJson()
            }.andReturn().response

        // HTTP status + headers
        assertEquals(201, res.status)
        val loc = res.getHeader("Location")
        val etag = res.getHeader("ETag")
        val lm = res.getHeader("Last-Modified")
        assertNotNull(loc)
        assertNotNull(etag)
        assertNotNull(lm)
        assertEquals("W/\"1\"", etag)

        // Location id is a UUID
        val id = UUID.fromString(loc!!.substringAfterLast('/'))
        assertNotNull(id)

        // Resource body basics
        val body: JsonNode = mapper.readTree(res.contentAsString)
        assertEquals("Patient", body["resourceType"].asText())
        assertEquals(id.toString(), body["id"].asText())

        // meta
        assertEquals("1", body["meta"]["versionId"].asText())
        val lastUpdated = OffsetDateTime.parse(body["meta"]["lastUpdated"].asText())
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(lastUpdated) // parse/format sanity
    }

    // Test to specifically check the case of incorrect gender (Cross-check using validator)
    @Test
    fun create_patient_rejects_invalid_gender() {
        val res =
            mvc.post("/fhir/Patient") {
                contentType = MediaType.valueOf("application/fhir+json")
                accept = MediaType.valueOf("application/fhir+json")
                content = newPatientJson(gender = "females")
            }.andReturn().response
        assertEquals(400, res.status)
    }

    // Test to specifically check the case of correct gender (Cross-check using validator)
    @Test
    fun create_patient_valid_gender() {
        val res =
            mvc.post("/fhir/Patient") {
                contentType = MediaType.valueOf("application/fhir+json")
                accept = MediaType.valueOf("application/fhir+json")
                content = newPatientJson(gender = "female")
            }.andReturn().response
        assertEquals(201, res.status)
    }
}
