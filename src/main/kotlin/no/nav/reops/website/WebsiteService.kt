package no.nav.reops.website

import no.nav.reops.event.Website
import no.nav.reops.event.WebsiteChangeEvent
import no.nav.reops.event.WebsiteEventStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Thrown when the optimistic-lock version supplied by the authorized controller snapshot
 * no longer matches the persisted row — i.e. the row changed (owner/state) between
 * authorization and mutation. Mapped to 409 by the controller.
 */
class WebsiteConflictException(websiteId: UUID) :
    RuntimeException("Website was modified concurrently: $websiteId")

class WebsiteAlreadyExistsException : RuntimeException("Nettside-ID-en er allerede registrert. Bruk en annen ID.")

@Service
class WebsiteService(
    private val websiteRepository: WebsiteRepository,
    private val websiteEventKafkaTemplate: KafkaTemplate<String, WebsiteChangeEvent>,
    @param:Value("\${spring.kafka.website-topic}") private val websiteTopic: String
) {

    /**
     * Ingestion gate for the analytics pipeline. Soft-deleted registrations stop
     * ingestion immediately (on cache expiry/eviction) because only active rows count.
     */
    @Cacheable("websiteAllowed")
    fun isAllowed(websiteId: UUID): Boolean = websiteRepository.existsByWebsiteIdAndDeletedAtIsNull(websiteId)

    fun findById(websiteId: UUID): WebsiteRegistration? =
        websiteRepository.findById(websiteId).map { it.toRegistration() }.orElse(null)

    /** Entity read used by the controller for the authorized snapshot (carries version). */
    fun findEntityById(websiteId: UUID): WebsiteEntity? =
        websiteRepository.findById(websiteId).orElse(null)

    fun findByOwnerTeams(ownerTeamIds: Collection<UUID>, includeDeleted: Boolean): List<WebsiteRegistration> {
        if (ownerTeamIds.isEmpty()) return emptyList()
        val entities = if (includeDeleted) {
            websiteRepository.findByOwnerTeamIdIn(ownerTeamIds)
        } else {
            websiteRepository.findByOwnerTeamIdInAndDeletedAtIsNull(ownerTeamIds)
        }
        return entities.map { it.toRegistration() }
    }

    fun findAll(includeDeleted: Boolean): List<WebsiteRegistration> {
        val entities = if (includeDeleted) {
            websiteRepository.findAll()
        } else {
            websiteRepository.findByDeletedAtIsNull()
        }
        return entities.map { it.toRegistration() }
    }

    @Caching(
        evict = [
            CacheEvict("websites", allEntries = true),
            CacheEvict("website", key = "#result.websiteId"),
            CacheEvict("websiteAllowed", key = "#result.websiteId"),
        ]
    )
    fun create(
        name: String,
        domain: String,
        ownerTeamId: UUID?,
        actorNavIdent: String,
        websiteId: UUID? = null,
    ): WebsiteRegistration {
        val resolvedId = websiteId ?: UUID.randomUUID()
        // Includes soft-deleted rows: creation must never overwrite or revive them.
        if (websiteRepository.existsById(resolvedId)) throw WebsiteAlreadyExistsException()
        val now = Instant.now()
        val entity = WebsiteEntity(
            websiteId = resolvedId,
            name = name,
            domain = domain,
            updatedAt = now,
            createdAt = now,
            ownerTeamId = ownerTeamId,
        )
        // Null @Version makes this INSERT-only. The primary key also guards concurrent
        // creates after the existence check; translate that race without leaking SQL.
        val saved = try {
            websiteRepository.save(entity)
        } catch (ex: DataIntegrityViolationException) {
            if (websiteRepository.existsById(resolvedId)) throw WebsiteAlreadyExistsException()
            throw ex
        }
        audit(actorNavIdent, "CREATE", saved.websiteId)
        publishEvent(saved.toKafkaDto(), WebsiteEventStatus.CREATE)
        return saved.toRegistration()
    }

    /**
     * Update guarded by the authorized snapshot's [expectedVersion]. The row is re-read
     * and saved under optimistic locking; if it changed (e.g. a concurrent admin owner
     * transfer) since authorization, the version check fails with [WebsiteConflictException]
     * so a stale non-admin edit cannot undo the change.
     */
    @Caching(
        evict = [
            CacheEvict("websites", allEntries = true),
            CacheEvict("website", key = "#websiteId"),
            CacheEvict("websiteAllowed", key = "#websiteId"),
        ]
    )
    fun update(
        websiteId: UUID,
        name: String,
        domain: String,
        ownerTeamId: UUID?,
        expectedVersion: Long,
        actorNavIdent: String,
    ): WebsiteRegistration {
        val existing = websiteRepository.findById(websiteId)
            .orElseThrow { WebsiteNotFoundException(websiteId) }
        if (existing.version != expectedVersion) throw WebsiteConflictException(websiteId)
        if (existing.deletedAt != null) throw WebsiteConflictException(websiteId)

        val updated = existing.copy(
            name = name,
            domain = domain,
            ownerTeamId = ownerTeamId,
            updatedAt = Instant.now()
        )
        val saved = websiteRepository.save(updated)
        audit(actorNavIdent, "UPDATE", websiteId)
        // Ownership exists only in Postgres. Assigning it must not recreate metadata
        // that an operator may already have purged from BigQuery.
        if (existing.name != saved.name || existing.domain != saved.domain) {
            publishEvent(saved.toKafkaDto(), WebsiteEventStatus.UPDATE)
        }
        return saved.toRegistration()
    }

    /**
     * Soft delete: sets deletedAt and updatedAt to the same deletion timestamp, evicts
     * caches (stopping ingestion) and publishes a DELETE tombstone carrying deletedAt.
     * Idempotent — an already-deleted row is returned unchanged with no duplicate
     * publication. Guarded by [expectedVersion] against lost-update races.
     */
    @Caching(
        evict = [
            CacheEvict("websites", allEntries = true),
            CacheEvict("website", key = "#websiteId"),
            CacheEvict("websiteAllowed", key = "#websiteId"),
        ]
    )
    fun softDelete(websiteId: UUID, expectedVersion: Long, actorNavIdent: String): WebsiteRegistration {
        val existing = websiteRepository.findById(websiteId)
            .orElseThrow { WebsiteNotFoundException(websiteId) }
        if (existing.version != expectedVersion) throw WebsiteConflictException(websiteId)

        if (existing.deletedAt != null) {
            LOG.info("Website websiteId={} already soft-deleted; skipping duplicate publication", websiteId)
            return existing.toRegistration()
        }

        val deletionTime = Instant.now()
        val updated = existing.copy(
            deletedAt = deletionTime,
            updatedAt = deletionTime
        )
        val saved = websiteRepository.save(updated)
        audit(actorNavIdent, "SOFT_DELETE", websiteId)
        publishEvent(saved.toKafkaDto(), WebsiteEventStatus.DELETE)
        return saved.toRegistration()
    }

    /**
     * Hard delete: removes ONLY the Postgres website row (version-checked). Deliberately
     * publishes no Kafka event and touches no session/analytics data — BigQuery metadata
     * and analytics stay unchanged. Used by ReOps for already-purged registrations.
     */
    @Caching(
        evict = [
            CacheEvict("websites", allEntries = true),
            CacheEvict("website", key = "#websiteId"),
            CacheEvict("websiteAllowed", key = "#websiteId"),
        ]
    )
    fun hardDelete(websiteId: UUID, expectedVersion: Long, actorNavIdent: String) {
        val existing = websiteRepository.findById(websiteId)
            .orElseThrow { WebsiteNotFoundException(websiteId) }
        if (existing.version != expectedVersion) throw WebsiteConflictException(websiteId)
        websiteRepository.delete(existing) // version-aware delete (PG row only)
        audit(actorNavIdent, "HARD_DELETE", websiteId)
    }

    /**
     * Audit trail for mutations: server-derived actor NAVident + operation + website UUID
     * only. Never logs name/domain/email/token or request bodies.
     */
    private fun audit(actorNavIdent: String, operation: String, websiteId: UUID) {
        LOG.info("website-management actor={} operation={} websiteId={}", actorNavIdent, operation, websiteId)
    }

    /**
     * Residual limitation: delivery is best-effort async. A failed send is logged but not
     * retried/queued (no outbox). Recorded here as a known limitation; no automatic
     * historical purge or reconciliation is introduced.
     */
    private fun publishEvent(website: Website, status: WebsiteEventStatus) {
        val event = WebsiteChangeEvent(website = website, status = status)
        websiteEventKafkaTemplate.send(websiteTopic, website.websiteId.toString(), event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    LOG.error(
                        "Failed to publish website event (no retry/outbox) for websiteId={} status={}",
                        website.websiteId, status, ex
                    )
                } else {
                    LOG.info("Published website event for websiteId={} status={}", website.websiteId, status)
                }
            }
    }

    private fun WebsiteEntity.toKafkaDto() = Website(
        websiteId = websiteId,
        name = name,
        domain = domain,
        updatedAt = updatedAt,
        createdAt = createdAt,
        teamId = if (isProdCluster) PROD_TEAM_ID else null,
        deletedAt = deletedAt,
    )

    private companion object {
        private val LOG = LoggerFactory.getLogger(WebsiteService::class.java)

        // team_id is a legacy BigQuery column (from the old umami schema) with no real
        // Postgres equivalent. It's used purely to control whether a website shows up on
        // https://innblikk.ansatt.nav.no (which filters to team_id == PROD_TEAM_ID).
        // Since dev-cluster registrations write to an entirely separate BigQuery project
        // and never reach the prod table, "is this instance running in the prod cluster"
        // is a reliable proxy for "should this website be prod-visible" — no per-website
        // input needed. Note: this is unrelated to ownerTeamId, which is a Team Catalog id.
        private val PROD_TEAM_ID: UUID = UUID.fromString("aa113c34-e213-4ed6-a4f0-0aea8a503e6b")
        private val isProdCluster: Boolean = System.getenv("NAIS_CLUSTER_NAME") == "prod-gcp"
    }
}

class WebsiteNotFoundException(websiteId: UUID) : RuntimeException("Website not found: $websiteId")
