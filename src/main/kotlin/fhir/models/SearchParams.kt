package fhir.models

import fhir.utils.Enums

data class SearchParams(
    val name: String? = null,
    val birthdate_ge: String? = null,
    val birthdate_le: String? = null,
    val gender: Enums.AdministrativeGender? = null,
    val _count: Int = 20,
    val _offset: Int = 0,
)
