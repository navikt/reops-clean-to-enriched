package no.nav.reops.session

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
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

    /**
     * Atomic insert-if-absent. Returns 1 if this call inserted the row (new session),
     * 0 if it already existed (concurrent insert won, or already registered).
     * Avoids the check-then-insert race that check+save() has under concurrent consumers.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO session (session_id, website_id, created_at)
            VALUES (:sessionId, :websiteId, :createdAt)
            ON CONFLICT (session_id) DO NOTHING
        """,
        nativeQuery = true
    )
    fun insertIfAbsent(
        @Param("sessionId") sessionId: UUID,
        @Param("websiteId") websiteId: UUID,
        @Param("createdAt") createdAt: Instant
    ): Int
}
