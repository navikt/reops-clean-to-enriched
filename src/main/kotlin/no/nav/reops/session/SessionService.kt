package no.nav.reops.session

import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.CachePut
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SessionService(
    private val sessionRepository: SessionRepository
) {

    /**
     * Returns true if this session already exists (in cache or DB).
     * If the session is new, it is persisted and cached for future lookups.
     */
    fun checkAndRegister(sessionId: UUID, websiteId: UUID, createdAt: Instant): Boolean {
        if (isKnownSession(sessionId)) {
            return false
        }
        registerSession(sessionId, websiteId, createdAt)
        return true
    }

    @Cacheable("sessions")
    fun isKnownSession(sessionId: UUID): Boolean =
        sessionRepository.existsById(sessionId)

    @CachePut("sessions", key = "#sessionId")
    fun registerSession(sessionId: UUID, websiteId: UUID, createdAt: Instant): Boolean {
        sessionRepository.save(SessionEntity(sessionId = sessionId, websiteId = websiteId, createdAt = createdAt))
        LOG.debug("Registered new session sessionId={} websiteId={}", sessionId, websiteId)
        return true
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(SessionService::class.java)
    }
}
