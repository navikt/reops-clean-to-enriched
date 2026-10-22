package no.nav.reops.website

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

/**
 * Stable internal API representation (see website-management contract). Note this is
 * deliberately separate from [no.nav.reops.event.Website], the Kafka payload: the Kafka
 * payload must NOT gain ownerTeamId until a separate downstream schema change is made.
 *
 * The optimistic-locking version is intentionally NOT part of this public DTO — it is an
 * internal DB concern carried on the entity and passed from the authorized snapshot to
 * the service mutation methods.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
data class WebsiteRegistration(
    val websiteId: UUID,
    val name: String,
    val domain: String,
    val ownerTeamId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

fun WebsiteEntity.toRegistration() = WebsiteRegistration(
    websiteId = websiteId,
    name = name,
    domain = domain,
    ownerTeamId = ownerTeamId,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
