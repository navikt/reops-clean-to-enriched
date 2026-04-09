package no.nav.reops.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import tools.jackson.databind.JsonNode
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
data class Event(
    val type: String, val payload: Payload
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    data class Payload(
        val website: UUID,
        val id: String? = null,
        val hostname: String? = null,
        val screen: String? = null,
        val language: String? = null,
        val title: String? = null,
        val url: String? = null,
        val referrer: String? = null,
        val name: String? = null,
        val data: JsonNode? = null
    )
}
