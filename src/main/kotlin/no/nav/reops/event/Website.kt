package no.nav.reops.event

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class Website(
    val websiteId: UUID,
    val name: String,
    val domain: String,
    val updatedAt: Instant,
    val teamId: UUID? = null
)

enum class WebsiteEventStatus {
    CREATE, UPDATE, DELETE
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WebsiteChangeEvent(
    val website: Website,
    val status: WebsiteEventStatus
)
