package no.nav.reops.website

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
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
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)

interface WebsiteRepository : JpaRepository<WebsiteEntity, UUID>
