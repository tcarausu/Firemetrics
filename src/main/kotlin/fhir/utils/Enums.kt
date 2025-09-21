package fhir.utils

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.util.Locale

class Enums {
    enum class AdministrativeGender(private val code: String) {
        MALE("male"),
        FEMALE("female"),
        OTHER("other"),
        UNKNOWN("unknown");

        @JsonValue
        fun toJson(): String = code

        companion object {
            @JsonCreator
            @JvmStatic
            fun fromJson(v: String?): AdministrativeGender =
                AdministrativeGender.entries.firstOrNull { it.code == v?.lowercase(Locale.ROOT) }
                    ?: throw IllegalArgumentException("Invalid gender '$v' (allowed: male|female|other|unknown)")


            private inline fun <reified T> Array<T>.firstOrDefault(pred: (T) -> Boolean): T? =
                this.firstOrNull(pred)
        }
    }
}
