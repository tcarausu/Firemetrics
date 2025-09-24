@file:Suppress("ktlint:standard:no-wildcard-imports")

package fhir

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.*
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
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID.randomUUID

/**
 * Created test suite responsible for testing Search functionality.
 * It contains the integration tests focused ONLY on GET /fhir/Patient/? with params
 * The solution collects all filters (happy path)
 * Example with 400 return for invalid parameters.
 * Setup for Offset data; 0 offset and higher, collecting the rest of the elements.
 * Setup for Search which Ignores Unexpected parameters
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
class PatientSearchTest {
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
        given: String,
        family: String,
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

    private fun create(vararg bodies: String) {
        bodies.forEach { body ->
            val res =
                mvc.post("/fhir/Patient") {
                    contentType = MediaType.valueOf("application/fhir+json")
                    accept = MediaType.valueOf("application/fhir+json")
                    content = body
                }.andReturn().response
            assertEquals(201, res.status)
        }
    }

    @Test
    fun search_happy_path_all_filters() {
        // seed
        create(
            newPatientJson("Jane", "Alpha", "female", "1980-01-02"),
            newPatientJson("Jane", "Beta", "female", "1982-03-04"),
            newPatientJson("John", "Gamma", "male", "1979-09-01"),
        )

        val res =
            mvc.get("/fhir/Patient") {
                accept = MediaType.valueOf("application/fhir+json")
                param("name", "Jane")
                param("gender", "female")
                param("birthdate:ge", "1970-01-01")
                param("birthdate:le", "2025-12-31")
                param("_count", "5")
                param("_offset", "0")
            }.andReturn().response

        assertEquals(200, res.status)
        val bundle: JsonNode = mapper.readTree(res.contentAsString)
        assertEquals("Bundle", bundle["resourceType"].asText())
        assertEquals("searchset", bundle["type"].asText())
        // only the two Janes should match
        assertTrue(bundle["total"].asLong() >= 2)
        assertTrue(bundle["entry"].size() in 1..5)
        // links: self must exist
        assertTrue(bundle["link"].any { it["relation"].asText() == "self" })
    }

    @Test
    fun search_invalid_params_return_400() {
        // invalid gender (controller guards it)
        var res =
            mvc.get("/fhir/Patient") {
                accept = MediaType.valueOf("application/fhir+json")
                param("gender", "females")
            }.andReturn().response
        assertEquals(400, res.status)

        // invalid date format
        res =
            mvc.get("/fhir/Patient") {
                accept = MediaType.valueOf("application/fhir+json")
                param("birthdate:ge", "1985/01/01")
            }.andReturn().response
        assertEquals(400, res.status)
    }

    @Test
    fun search_pagination_offset_behavior() {
        val tag = "Smoke-" + randomUUID().toString().substring(0, 8)
        // seed 6 matching patients
        create(
            newPatientJson("Jane1", tag, "female", "1980-01-02"),
            newPatientJson("Jane2", tag, "female", "1980-01-02"),
            newPatientJson("Jane3", tag, "female", "1980-01-02"),
            newPatientJson("Jane4", tag, "female", "1980-01-02"),
            newPatientJson("Jane5", tag, "female", "1980-01-02"),
            newPatientJson("Jane6", tag, "female", "1980-01-02"),
        )

        // page 1: _count=5, _offset=0 => 5 entries, total=6, has next link
        var res =
            mvc.get("/fhir/Patient") {
                accept = MediaType.valueOf("application/fhir+json")
                param("name", tag)
                param("gender", "female")
                param("birthdate:ge", "1970-01-01")
                param("birthdate:le", "2025-12-31")
                param("_count", "5")
                param("_offset", "0")
            }.andReturn().response
        assertEquals(200, res.status)
        var bundle: JsonNode = mapper.readTree(res.contentAsString)
        assertTrue(bundle["entry"].all { it["resource"]["name"][0]["family"].asText() == tag })
        assertEquals(6L, bundle["total"].asLong())
        assertEquals(5, bundle["entry"].size())
        assertTrue(bundle["link"].any { it["relation"].asText() == "next" })

        // page 2: _offset=5 => 1 entry, no next link
        res =
            mvc.get("/fhir/Patient") {
                accept = MediaType.valueOf("application/fhir+json")
                param("name", tag)
                param("gender", "female")
                param("birthdate:ge", "1970-01-01")
                param("birthdate:le", "2025-12-31")
                param("_count", "5")
                param("_offset", "5")
            }.andReturn().response
        assertEquals(200, res.status)
        bundle = mapper.readTree(res.contentAsString)
        assertEquals(6L, bundle["total"].asLong())
        assertEquals(1, bundle["entry"].size())
        assertTrue(bundle["entry"].all { it["resource"]["name"][0]["family"].asText() == tag })
        assertFalse(bundle["link"].any { it["relation"].asText() == "next" })
    }

    @Test
    fun search_ignores_unexpected_params() {
        create(newPatientJson("Jane", "Q", "female", "1980-01-02"))

        val res =
            mvc.get("/fhir/Patient") {
                accept = MediaType.valueOf("application/fhir+json")
                param("name", "Jane")
                // not handled by controller → should be ignored
                param("unknown", "123")
                param("_count", "5")
                param("_offset", "0")
            }.andReturn().response

        assertEquals(200, res.status)
        val bundle: JsonNode = mapper.readTree(res.contentAsString)
        assertEquals("Bundle", bundle["resourceType"].asText())
        assertTrue(bundle["total"].asLong() >= 1L)
        assertTrue(bundle["entry"].size() >= 1)
    }
//
//    // From here onwards we have Test that are specifically tailored to the Patient resource parameters (during search)
//    @Test
//    fun search_by_name_substring() {
//        val res = mvc.get("/fhir/Patient?name=doe&_count=50&_offset=0") {
//            accept = MediaType.valueOf("application/fhir+json")
//        }.andReturn().response
//        assertEquals(200, res.status)
//        val json = mapper.readTree(res.contentAsString)
//        assertEquals("Bundle", json["resourceType"].asText())
//        // two Does
//        assertEquals(2, json["total"].asInt())
//        assertEquals(2, json["entry"].size())
//    }
//
//    @Test
//    fun search_by_birthdate_range() {
//        val res = mvc.get("/fhir/Patient?birthdate:ge=1980-01-01&birthdate:le=1989-12-31&_count=50&_offset=0") {
//            accept = MediaType.valueOf("application/fhir+json")
//        }.andReturn().response
//        assertEquals(200, res.status)
//        val json = mapper.readTree(res.contentAsString)
//        // Jane(1985) + John(1982) -> 2
//        assertEquals(2, json["total"].asInt())
//    }
//
//    @Test
//    fun search_by_gender() {
//        val res = mvc.get("/fhir/Patient?gender=female&_count=50&_offset=0") {
//            accept = MediaType.valueOf("application/fhir+json")
//        }.andReturn().response
//        assertEquals(200, res.status)
//        val json = mapper.readTree(res.contentAsString)
//        // Jane + Janet
//        assertEquals(2, json["total"].asInt())
//    }
//
//    @Test
//    fun pagination_next_link_and_offset() {
//        val res1 = mvc.get("/fhir/Patient?name=doe&_count=1&_offset=0") {
//            accept = MediaType.valueOf("application/fhir+json")
//        }.andReturn().response
//        assertEquals(200, res1.status)
//        val b1 = mapper.readTree(res1.contentAsString)
//        assertEquals(2, b1["total"].asInt())
//        // should have a "next" link when total > count + offset
//        val links = b1["link"].map { it["relation"].asText() }
//        assert(links.contains("next"))
//
//        val res2 = mvc.get("/fhir/Patient?name=doe&_count=1&_offset=1") {
//            accept = MediaType.valueOf("application/fhir+json")
//        }.andReturn().response
//        val b2 = mapper.readTree(res2.contentAsString)
//        assertEquals(2, b2["total"].asInt())
//        // page 2 => no next
//        val links2 = b2["link"].map { it["relation"].asText() }
//        assert(!links2.contains("next"))
//    }
}
