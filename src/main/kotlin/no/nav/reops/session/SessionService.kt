package no.nav.reops.session

import org.slf4j.LoggerFactory
import org.springframework.cache.CacheManager
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit
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

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun purgeExpiredSessions() {
        val cutoff = Instant.now().minus(SESSION_RETENTION_DAYS, ChronoUnit.DAYS)
        val deleted = sessionRepository.deleteOlderThan(cutoff)
        LOG.info("Purged {} expired sessions older than {} days", deleted, SESSION_RETENTION_DAYS)
    }

    private companion object {
        private const val SESSIONS_CACHE = "sessions"
        private const val SESSION_RETENTION_DAYS = 90L
        private val LOG = LoggerFactory.getLogger(SessionService::class.java)
    }
}
