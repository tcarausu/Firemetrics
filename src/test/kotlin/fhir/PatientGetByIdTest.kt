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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

/**
 * Created test suite responsible for testing Get by ID functionality.
 * It contains the integration tests focused ONLY on GET /fhir/Patient/{id}
 * The working/happy path which returns the patient data; including ETag + Last-Modified
 * The 2nd test case contains Mot-found, which then returns OperationOutcome with 404 Status code
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PatientGetByIdTest {

    @Autowired lateinit var mvc: MockMvc
    private val mapper = jacksonObjectMapper().findAndRegisterModules()

    companion object {
        @Container
        private val pg = PostgreSQLContainer<Nothing>("postgres:17-alpine").apply {
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

    private fun newPatientJson() = """
      {
        "resourceType": "Patient",
        "name": [{"family": "Smith", "given": ["John"]}],
        "gender": "male",
        "birthDate": "1979-09-01"
      }
    """.trimIndent()

    @Test
    fun get_by_id_returns_patient_with_headers() {
        val created = mvc.post("/fhir/Patient") {
            contentType = MediaType.valueOf("application/fhir+json")
            accept = MediaType.valueOf("application/fhir+json")
            content = newPatientJson()
        }.andReturn().response

        val id = created.getHeader("Location")!!.substringAfterLast('/')

        val res = mvc.get("/fhir/Patient/$id") {
            accept = MediaType.valueOf("application/fhir+json")
        }.andReturn().response

        assertEquals(200, res.status)
        assertEquals("W/\"1\"", res.getHeader("ETag"))
        assertNotNull(res.getHeader("Last-Modified"))

        val body: JsonNode = mapper.readTree(res.contentAsString)
        assertEquals("Patient", body["resourceType"].asText())
        assertEquals(id, body["id"].asText())
    }

    @Test
    fun get_missing_returns_operation_outcome_404() {
        val randomId = UUID.randomUUID()
        val res = mvc.get("/fhir/Patient/$randomId") {
            accept = MediaType.valueOf("application/fhir+json")
        }.andReturn().response

        assertEquals(404, res.status)
        val oo: JsonNode = mapper.readTree(res.contentAsString)
        assertEquals("OperationOutcome", oo["resourceType"].asText())
        assertEquals("not-found", oo["issue"][0]["code"].asText())
    }
}
