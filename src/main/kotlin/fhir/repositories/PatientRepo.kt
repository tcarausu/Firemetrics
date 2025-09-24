package fhir.repositories

import fhir.models.SearchParams
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class PatientRepo(private val jdbc: NamedParameterJdbcTemplate) {
    data class SearchPage(val ids: List<UUID>, val total: Long)

    fun put(patientJson: String): UUID =
        jdbc.queryForObject(
            "select fhir_put('Patient', cast(:body as jsonb))::uuid",
            mapOf("body" to patientJson),
            UUID::class.java,
        )!!

    fun get(id: UUID): String? =
        jdbc.query(
            "select fhir_get('Patient', :id::uuid)::text",
            mapOf("id" to id),
        ) { rs, _ -> rs.getString(1) }
            .firstOrNull()

    fun search(params: SearchParams): SearchPage {
        val filters = buildFilterJson(params)

        val ids =
            jdbc.query(
                "select * from fhir_search('Patient', cast(:filters as jsonb))",
                mapOf("filters" to filters),
            ) { rs, _ -> UUID.fromString(rs.getString(1)) }

        val total =
            jdbc.queryForObject(
                "select fhir_count('Patient', cast(:filters as jsonb))",
                mapOf("filters" to filters),
                Long::class.java,
            ) ?: 0L

        val pageIds = ids
        return SearchPage(pageIds, total)
    }

    private fun buildFilterJson(params: SearchParams): String =
        buildString {
            append('{')
            var first = true

            fun add(
                k: String,
                v: Any,
            ) {
                if (!first) append(',') else first = false
                append('"').append(k).append('"').append(':')
                when (v) {
                    is Number, is Boolean -> append(v.toString())
                    else -> append('"').append(v.toString()).append('"')
                }
            }

            params.name?.let { add("name", it.lowercase()) }
            params.gender?.let { add("gender", it.toJson()) }
            params.birthdate_ge?.let { add("birthdate_ge", it) }
            params.birthdate_le?.let { add("birthdate_le", it) }

            // page hints for the extension
            add("_count", params._count.coerceAtLeast(1))
            add("_offset", params._offset.coerceAtLeast(0))
            append('}')

// also pass count/offset so the extension can choose to page there if desired
        }
}
