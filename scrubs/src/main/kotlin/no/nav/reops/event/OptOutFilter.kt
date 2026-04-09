package no.nav.reops.event

import com.fasterxml.jackson.annotation.JsonValue

private const val MAX_HEADER_LENGTH = 50

enum class OptOutFilter(@get:JsonValue val value: String) {
    UUID("uuid");

    companion object {
        private val byValue = entries.associateBy { it.value.lowercase() }

        fun parseHeader(raw: String?): List<OptOutFilter>? {
            val trimmed = raw?.trim().takeUnless { it.isNullOrEmpty() } ?: return null

            require(trimmed.length <= MAX_HEADER_LENGTH) {
                "x-opt-out-filters header exceeds $MAX_HEADER_LENGTH characters"
            }

            val stripped = trimmed.removePrefix("[").removeSuffix("]")

            val filters = stripped.split(',')
                .asSequence()
                .map { it.trim().removeSurrounding("\"").trim().lowercase() }
                .filter { it.isNotEmpty() }
                .mapNotNull { byValue[it] }
                .distinct()
                .toList()

            return filters.ifEmpty { null }
        }
    }
}

