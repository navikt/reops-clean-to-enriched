package no.nav.reops.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "session")
data class SessionEntity(
    @Id @Column(name = "session_id") val sessionId: UUID,
    @Column(name = "website_id") val websiteId: UUID,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)

interface SessionRepository : JpaRepository<SessionEntity, UUID>
