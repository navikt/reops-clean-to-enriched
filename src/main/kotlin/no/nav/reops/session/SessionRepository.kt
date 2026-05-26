package no.nav.reops.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "session")
data class SessionEntity(
    @Id @Column(name = "session_id") val sessionId: UUID,
    @Column(name = "website_id") val websiteId: UUID,
    @Column(name = "created_at") val createdAt: Instant = Instant.now()
)

interface SessionRepository : JpaRepository<SessionEntity, UUID> {
    @Modifying
    @Query("DELETE FROM SessionEntity s WHERE s.createdAt < :before")
    fun deleteOlderThan(@Param("before") before: Instant): Int
}
