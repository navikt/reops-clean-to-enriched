package no.nav.reops.website

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "website")
data class WebsiteEntity(
    @Id @Column(name = "website_id") val websiteId: UUID = UUID.randomUUID(),
    val name: String = "",
    val domain: String = "",
    @Column(name = "updated_at") val updatedAt: Instant = Instant.now(),
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "owner_team_id") val ownerTeamId: UUID? = null,
    @Column(name = "deleted_at") val deletedAt: Instant? = null,
    // Internal optimistic-lock column; NOT exposed on the public registration DTO.
    @Version @Column(name = "version") val version: Long? = null,
)

interface WebsiteRepository : JpaRepository<WebsiteEntity, UUID> {

    fun existsByWebsiteIdAndDeletedAtIsNull(websiteId: UUID): Boolean

    fun findByDeletedAtIsNull(): List<WebsiteEntity>

    fun findByOwnerTeamIdInAndDeletedAtIsNull(ownerTeamIds: Collection<UUID>): List<WebsiteEntity>

    fun findByOwnerTeamIdIn(ownerTeamIds: Collection<UUID>): List<WebsiteEntity>
}
