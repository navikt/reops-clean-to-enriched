package no.nav.reops.event

import no.nav.reops.useragent.UserAgentInfo
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeType
import java.net.URI
import java.security.MessageDigest
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID


/**
 * Maps the raw input [Event] to an [EnrichedEvent] that mirrors Umami's database schema.
 *
 * @param country  ISO country code resolved from IP (e.g. "NO")
 * @param userAgentInfo  parsed browser/OS/device from the User-Agent header
 * @param ip  client IP address (first entry from X-Forwarded-For)
 * @param userAgent  raw User-Agent header string (used for session fingerprinting)
 * @param salt  server-side secret used in session ID hashing (APP_SECRET)
 */
fun Event.toEnrichedEvent(
    country: String? = null,
    userAgentInfo: UserAgentInfo = UserAgentInfo(),
    ip: String? = null,
    userAgent: String? = null,
    salt: String? = null,
    now: Instant = Instant.now()
): EnrichedEvent {
    val eventId = UUID.randomUUID()
    val sessionId = generateSessionId(this.payload.website, this.payload.hostname, ip, userAgent, salt)
    val visitId = generateVisitId(sessionId, now)
    val eventName = this.payload.name?.trim().takeUnless { it.isNullOrEmpty() }
    val eventType = if (eventName != null) 2 else 1

    val isIdentify = this.type == "identify"

    return EnrichedEvent( // todo: later change createdAt to be created in proxy
        websiteId = this.payload.website, sessionId = sessionId, createdAt = Instant.now(), websiteEvent = WebsiteEvent(
            eventId = eventId,
            urlPath = extractPath(this.payload.url),
            urlQuery = extractQuery(this.payload.url),
            referrerPath = extractPath(this.payload.referrer),
            referrerQuery = extractQuery(this.payload.referrer),
            referrerDomain = extractDomain(this.payload.referrer),
            pageTitle = this.payload.title,
            eventType = eventType,
            eventName = eventName,
            visitId = visitId,
            hostname = this.payload.hostname,
            eventData = if (isIdentify) null else this.payload.data?.toEventDataList()
        ), session = Session(
            browser = userAgentInfo.browser,
            os = userAgentInfo.os,
            device = userAgentInfo.device,
            screen = this.payload.screen,
            language = normalizeLanguage(this.payload.language),
            country = country,
            distinctId = this.payload.id,
            sessionData = if (isIdentify) this.payload.data?.toSessionDataList(sessionId, this.payload.id) else null
        )
    )
}

/**
 * Generates a deterministic visit ID by hashing the session ID together with the
 * start-of-hour timestamp (UTC).
 *
 * A visit represents a single browsing session within an hour. The same user gets
 * the same visitId throughout that hour, but a new visitId when the hour changes.
 * This mirrors Umami's approach: `visitId = uuid(sessionId, visitSalt)` where
 * `visitSalt = hash(startOfHour(createdAt))`.
 */
private fun generateVisitId(
    sessionId: String, now: Instant = Instant.now()
): String {
    val startOfHour = now.truncatedTo(ChronoUnit.HOURS).toString()
    val input = "$sessionId$startOfHour"
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

/**
 * Generates a deterministic session ID by hashing website ID, hostname, IP, User-Agent,
 * and a server-side secret salt.
 *
 * The same user (same IP + browser) always gets the same sessionId, making it
 * possible to track returning users across days for retention analysis.
 * The salt (APP_SECRET) prevents session IDs from being reconstructable by third parties.
 */
private fun generateSessionId(
    websiteId: UUID, hostname: String?, ip: String?, userAgent: String?, salt: String?
): String {
    val input = "$websiteId${hostname.orEmpty()}${ip.orEmpty()}${userAgent.orEmpty()}${salt.orEmpty()}"
    val digest = MessageDigest.getInstance("SHA-256")
    val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
    return hash.joinToString("") { "%02x".format(it) }
}

/**
 * Normalizes a language tag to just the primary subtag, lowercased and truncated to 35 chars.
 * For example: "en-US" → "en", "nb-NO" → "nb", "zh-Hant-TW" → "zh".
 * Returns null if the input is blank.
 */
private fun normalizeLanguage(language: String?): String? {
    if (language.isNullOrBlank()) return null
    return language.split("-", "_").first().trim().lowercase().take(35).ifEmpty { null }
}

/**
 * Extracts the path component from a URL, stripping scheme, host, query parameters and fragments.
 * Matches standard Umami behavior: stores just the pathname (e.g. "/no/person"), not the full URL.
 * Returns the original string (minus query/fragment) if parsing fails.
 */
private fun extractPath(url: String?): String? {
    if (url.isNullOrBlank()) return url
    return try {
        val uri = URI(url)
        if (uri.scheme != null) {
            uri.path?.ifEmpty { "/" } ?: "/"
        } else {
            url.substringBefore("?").substringBefore("#")
        }
    } catch (_: Exception) {
        url.substringBefore("?").substringBefore("#")
    }
}

/**
 * Extracts the query string from a URL (without the leading '?').
 * Returns null if the URL has no query component.
 */
private fun extractQuery(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        val uri = URI(url)
        uri.query
    } catch (_: Exception) {
        val qIdx = url.indexOf('?')
        if (qIdx >= 0) {
            val fIdx = url.indexOf('#', qIdx)
            if (fIdx >= 0) url.substring(qIdx + 1, fIdx) else url.substring(qIdx + 1)
        } else null
    }
}

/**
 * Extracts the domain (host) from a URL string.
 * Returns null if the URL is blank or unparseable.
 */
private fun extractDomain(url: String?): String? {
    if (url.isNullOrBlank()) return null
    return try {
        URI(url).host
    } catch (_: Exception) {
        null
    }
}

/**
 * Converts a freeform JSON object into a list of [EventDataEntry],
 * matching Umami's event_data table structure:
 *  - data_type 1 = string
 *  - data_type 2 = number
 *  - data_type 4 = date (not inferred here; stored as string)
 */
private fun JsonNode.toEventDataList(): List<EventDataEntry>? {
    if (!this.isObject) return null
    val result = mutableListOf<EventDataEntry>()
    for ((key, value) in this.properties()) {
        val entry = when (value.nodeType) {
            JsonNodeType.NUMBER -> EventDataEntry(
                eventDataId = UUID.randomUUID(),
                dataKey = key,
                dataType = 2,
                numberValue = value.asDouble(),
            )

            JsonNodeType.BOOLEAN -> EventDataEntry(
                eventDataId = UUID.randomUUID(),
                dataKey = key,
                dataType = 2,
                numberValue = if (value.asBoolean()) 1.0 else 0.0,
            )

            else -> EventDataEntry(
                eventDataId = UUID.randomUUID(),
                dataKey = key,
                dataType = 1,
                stringValue = value.asString(),
            )
        }
        result.add(entry)
    }
    return result.ifEmpty { null }
}

/**
 * Converts a freeform JSON object into a list of [SessionDataEntry],
 * matching Umami's session_data table structure (used by `umami.identify()`):
 *  - data_type 1 = string
 *  - data_type 2 = number
 *  - data_type 4 = date (not inferred here; stored as string)
 */
private fun JsonNode.toSessionDataList(sessionId: String, distinctId: String?): List<SessionDataEntry>? {
    if (!this.isObject) return null
    val result = mutableListOf<SessionDataEntry>()
    for ((key, value) in this.properties()) {
        val entry = when (value.nodeType) {
            JsonNodeType.NUMBER -> SessionDataEntry(
                sessionDataId = UUID.randomUUID(),
                sessionId = sessionId,
                dataKey = key,
                dataType = 2,
                numberValue = value.asDouble(),
                distinctId = distinctId,
            )

            JsonNodeType.BOOLEAN -> SessionDataEntry(
                sessionDataId = UUID.randomUUID(),
                sessionId = sessionId,
                dataKey = key,
                dataType = 2,
                numberValue = if (value.asBoolean()) 1.0 else 0.0,
                distinctId = distinctId,
            )

            else -> SessionDataEntry(
                sessionDataId = UUID.randomUUID(),
                sessionId = sessionId,
                dataKey = key,
                dataType = 1,
                stringValue = value.asString(),
                distinctId = distinctId,
            )
        }
        result.add(entry)
    }
    return result.ifEmpty { null }
}