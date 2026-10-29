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
    val createdAt: Instant? = null,
    val teamId: UUID? = null,
    // Additive tombstone timestamp. Only set on DELETE events for soft-deleted
    // registrations; omitted (null) for active records. ownerTeamId is deliberately
    // NOT part of this payload — that requires a separate downstream schema change.
    val deletedAt: Instant? = null
)

enum class WebsiteEventStatus {
    CREATE, UPDATE, DELETE
}

@JsonInclude(JsonInclude.Include.NON_NULL)
data class WebsiteChangeEvent(
    val website: Website,
    val status: WebsiteEventStatus
)
