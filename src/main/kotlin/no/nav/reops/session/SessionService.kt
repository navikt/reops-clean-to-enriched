package no.nav.reops.session

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class SessionService(
    private val sessionRepository: SessionRepository,
    cacheManager: CacheManager
) {
    private val sessionsCache = cacheManager.getCache(SESSIONS_CACHE)
        ?: error("Cache '$SESSIONS_CACHE' is not configured")

    fun checkAndRegister(sessionId: UUID, websiteId: UUID, createdAt: Instant): Boolean {
        if (sessionsCache.get(sessionId, Boolean::class.javaObjectType) == true) {
            return false
        }

        if (sessionRepository.existsById(sessionId)) {
            sessionsCache.put(sessionId, true)
            return false
        }

        sessionRepository.save(SessionEntity(sessionId = sessionId, websiteId = websiteId, createdAt = createdAt))
        sessionsCache.put(sessionId, true)
        LOG.debug("Registered new session sessionId={} websiteId={}", sessionId, websiteId)
        return true
    }

    private companion object {
        private const val SESSIONS_CACHE = "sessions"
        private val LOG = LoggerFactory.getLogger(SessionService::class.java)
    }
}
