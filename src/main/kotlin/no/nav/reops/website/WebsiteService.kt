package no.nav.reops.website

import no.nav.reops.event.Website
import no.nav.reops.event.WebsiteChangeEvent
import no.nav.reops.event.WebsiteEventStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class WebsiteService(
    private val websiteRepository: WebsiteRepository,
    private val websiteEventKafkaTemplate: KafkaTemplate<String, WebsiteChangeEvent>,
    @Value("\${spring.kafka.website-topic}") private val websiteTopic: String
) {

    @Cacheable("websiteAllowed")
    fun isAllowed(websiteId: UUID): Boolean = websiteRepository.existsById(websiteId)

    @Cacheable("websites")
    fun findAll(): List<Website> = websiteRepository.findAll().map { it.toDto() }

    @Cacheable("website", key = "#websiteId")
    fun findById(websiteId: UUID): Website? =
        websiteRepository.findById(websiteId).map { it.toDto() }.orElse(null)

    fun findAllFresh(): List<Website> = websiteRepository.findAll().map { it.toDto() }

    fun findByIdFresh(websiteId: UUID): Website? =
        websiteRepository.findById(websiteId).map { it.toDto() }.orElse(null)

    fun create(websiteId: UUID? = null, name: String, domain: String): Website {
        val now = Instant.now()
        val entity = WebsiteEntity(
            websiteId = websiteId ?: UUID.randomUUID(),
            name = name,
            domain = domain,
            updatedAt = now,
            createdAt = now
        )
        val saved = websiteRepository.save(entity)
        val website = saved.toDto()
        publishEvent(website, WebsiteEventStatus.CREATE)
        return website
    }

    fun update(websiteId: UUID, name: String, domain: String): Website {
        val existing = websiteRepository.findById(websiteId)
            .orElseThrow { WebsiteNotFoundException(websiteId) }

        val updated = existing.copy(
            name = name,
            domain = domain,
            updatedAt = Instant.now()
        )
        val saved = websiteRepository.save(updated)
        val website = saved.toDto()
        publishEvent(website, WebsiteEventStatus.UPDATE)
        return website
    }

    fun delete(websiteId: UUID) {
        val entity = websiteRepository.findById(websiteId)
            .orElseThrow { WebsiteNotFoundException(websiteId) }
        websiteRepository.deleteById(websiteId)
        publishEvent(entity.toDto(), WebsiteEventStatus.DELETE)
    }

    private fun publishEvent(website: Website, status: WebsiteEventStatus) {
        val event = WebsiteChangeEvent(website = website, status = status)
        websiteEventKafkaTemplate.send(websiteTopic, website.websiteId.toString(), event)
            .whenComplete { _, ex ->
                if (ex != null) {
                    LOG.error("Failed to publish website event for websiteId={} status={}", website.websiteId, status, ex)
                } else {
                    LOG.info("Published website event for websiteId={} status={}", website.websiteId, status)
                }
            }
    }

    private fun WebsiteEntity.toDto() = Website(
        websiteId = websiteId,
        name = name,
        domain = domain,
        updatedAt = updatedAt,
        createdAt = createdAt,
        teamId = if (isProdCluster) PROD_TEAM_ID else null
    )

    private companion object {
        private val LOG = LoggerFactory.getLogger(WebsiteService::class.java)

        // team_id is a legacy BigQuery column (from the old umami schema) with no real
        // Postgres equivalent. It's used purely to control whether a website shows up on
        // https://innblikk.ansatt.nav.no (which filters to team_id == PROD_TEAM_ID).
        // Since dev-cluster registrations write to an entirely separate BigQuery project
        // and never reach the prod table, "is this instance running in the prod cluster"
        // is a reliable proxy for "should this website be prod-visible" — no per-website
        // input needed.
        private val PROD_TEAM_ID: UUID = UUID.fromString("aa113c34-e213-4ed6-a4f0-0aea8a503e6b")
        private val isProdCluster: Boolean = System.getenv("NAIS_CLUSTER_NAME") == "prod-gcp"
    }
}

class WebsiteNotFoundException(websiteId: UUID) : RuntimeException("Website not found: $websiteId")
