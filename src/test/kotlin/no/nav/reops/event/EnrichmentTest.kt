package no.nav.reops.event

import no.nav.reops.useragent.UserAgentInfo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.databind.node.JsonNodeFactory
import java.time.Instant
import java.util.UUID

class EnrichmentTest {

    private val websiteId = UUID.fromString("1dbfe4e9-bf8b-45d9-9305-928f22200bc0")

    @Test
    fun `session ID is deterministic for the same inputs in the same month`() {
        val event = sampleEvent(url = "/test", referrer = "https://google.com/search?q=nav")
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        assertThat(e1.sessionId).isEqualTo(e2.sessionId)
    }

    @Test
    fun `session ID differs for different IPs`() {
        val event = sampleEvent(url = "/test")
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", now = now)
        val e2 = event.toEnrichedEvent(ip = "1.1.1.1", userAgent = "Mozilla/5.0", now = now)
        assertThat(e1.sessionId).isNotEqualTo(e2.sessionId)
    }

    @Test
    fun `session ID differs for different user agents`() {
        val event = sampleEvent(url = "/test")
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Chrome/120", now = now)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Firefox/115", now = now)
        assertThat(e1.sessionId).isNotEqualTo(e2.sessionId)
    }

    @Test
    fun `session ID differs for different salts`() {
        val event = sampleEvent(url = "/test")
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret-1", now = now)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret-2", now = now)
        assertThat(e1.sessionId).isNotEqualTo(e2.sessionId)
    }

    @Test
    fun `session ID is same on websiteEvent and session`() {
        val event = sampleEvent(url = "/test")
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val enriched = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", now = now)
        assertThat(enriched.sessionId).isEqualTo(enriched.sessionId)
    }

    @Test
    fun `session ID stays the same within the same month`() {
        val event = sampleEvent(url = "/test")
        val earlyInMonth = Instant.parse("2026-04-01T00:00:00Z")
        val lateInMonth = Instant.parse("2026-04-30T23:59:59Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = earlyInMonth)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = lateInMonth)
        assertThat(e1.sessionId).isEqualTo(e2.sessionId)
    }

    @Test
    fun `session ID rotates when the month changes`() {
        val event = sampleEvent(url = "/test")
        val april = Instant.parse("2026-04-30T23:59:59Z")
        val may = Instant.parse("2026-05-01T00:00:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = april)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = may)
        assertThat(e1.sessionId).isNotEqualTo(e2.sessionId)
    }

    @Test
    fun `session ID differs for different websiteIds`() {
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val event1 = Event(
            type = "event", payload = Event.Payload(
                website = UUID.fromString("1dbfe4e9-bf8b-45d9-9305-928f22200bc0"),
                hostname = "felgen.intern.nav.no", screen = "172x111", language = "en-US", url = "/test"
            )
        )
        val event2 = Event(
            type = "event", payload = Event.Payload(
                website = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"),
                hostname = "felgen.intern.nav.no", screen = "172x111", language = "en-US", url = "/test"
            )
        )
        val e1 = event1.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        val e2 = event2.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        assertThat(e1.sessionId).isNotEqualTo(e2.sessionId)
    }

    @Test
    fun `session ID differs for different hostnames`() {
        val now = Instant.parse("2026-04-14T10:00:00Z")
        val event1 = sampleEvent(url = "/test")
        val event2 = Event(
            type = "event", payload = Event.Payload(
                website = websiteId, hostname = "other.nav.no", screen = "172x111", language = "en-US", url = "/test"
            )
        )
        val e1 = event1.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        val e2 = event2.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        assertThat(e1.sessionId).isNotEqualTo(e2.sessionId)
    }

    @Test
    fun `eventId and visitId are generated and distinct`() {
        val event = sampleEvent()
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.eventId).isNotNull()
        assertThat(enriched.websiteEvent.visitId).isNotNull()
        assertThat(enriched.websiteEvent.eventId.toString()).isNotEqualTo(enriched.websiteEvent.visitId)
    }

    @Test
    fun `visitId is deterministic for the same session within the same hour`() {
        val event = sampleEvent()
        val now = Instant.parse("2026-04-14T10:30:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        assertThat(e1.websiteEvent.visitId).isEqualTo(e2.websiteEvent.visitId)
    }

    @Test
    fun `visitId changes when the hour changes`() {
        val event = sampleEvent()
        val hour1 = Instant.parse("2026-04-14T10:30:00Z")
        val hour2 = Instant.parse("2026-04-14T11:05:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = hour1)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = hour2)
        assertThat(e1.websiteEvent.visitId).isNotEqualTo(e2.websiteEvent.visitId)
    }

    @Test
    fun `visitId differs on different days at the same hour`() {
        val event = sampleEvent()
        val day1 = Instant.parse("2026-04-14T10:00:00Z")
        val day2 = Instant.parse("2026-04-15T10:00:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = day1)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = day2)
        assertThat(e1.websiteEvent.visitId).isNotEqualTo(e2.websiteEvent.visitId)
    }

    @Test
    fun `visitId stays the same within the same hour regardless of minute`() {
        val event = sampleEvent()
        val earlyInHour = Instant.parse("2026-04-14T10:01:00Z")
        val lateInHour = Instant.parse("2026-04-14T10:59:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = earlyInHour)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = lateInHour)
        assertThat(e1.websiteEvent.visitId).isEqualTo(e2.websiteEvent.visitId)
    }

    @Test
    fun `visitId differs for different sessions`() {
        val event = sampleEvent()
        val now = Instant.parse("2026-04-14T10:30:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        val e2 = event.toEnrichedEvent(ip = "1.1.1.1", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        assertThat(e1.sessionId).isNotEqualTo(e2.sessionId)
        assertThat(e1.websiteEvent.visitId).isNotEqualTo(e2.websiteEvent.visitId)
    }

    @Test
    fun `visitId is derived from sessionId, not raw inputs`() {
        val event = sampleEvent()
        val now = Instant.parse("2026-04-14T10:30:00Z")
        val e1 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        val e2 = event.toEnrichedEvent(ip = "8.8.8.8", userAgent = "Mozilla/5.0", salt = "secret", now = now)
        // same session → same visitId
        assertThat(e1.sessionId).isEqualTo(e2.sessionId)
        assertThat(e1.websiteEvent.visitId).isEqualTo(e2.websiteEvent.visitId)
    }

    @Test
    fun `URL query parameters are extracted separately`() {
        val event = sampleEvent(url = "https://example.no/page?foo=bar&secret=123")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.urlPath).isEqualTo("/page")
        assertThat(enriched.websiteEvent.urlQuery).isEqualTo("foo=bar&secret=123")
    }

    @Test
    fun `URL fragment is stripped from path`() {
        val event = sampleEvent(url = "https://example.no/page#section")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.urlPath).isEqualTo("/page")
    }

    @Test
    fun `relative URL query params are extracted`() {
        val event = sampleEvent(url = "/path/to/page?query=value")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.urlPath).isEqualTo("/path/to/page")
        assertThat(enriched.websiteEvent.urlQuery).isEqualTo("query=value")
    }

    @Test
    fun `URL without query params has null query`() {
        val event = sampleEvent(url = "https://example.no/page")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.urlPath).isEqualTo("/page")
        assertThat(enriched.websiteEvent.urlQuery).isNull()
    }

    @Test
    fun `null URL stays null`() {
        val event = sampleEvent(url = null)
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.urlPath).isNull()
        assertThat(enriched.websiteEvent.urlQuery).isNull()
    }

    @Test
    fun `referrer domain is extracted from absolute URL`() {
        val event = sampleEvent(referrer = "https://www.google.com/search?q=test")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.referrerDomain).isEqualTo("www.google.com")
    }

    @Test
    fun `referrer query params are extracted separately`() {
        val event = sampleEvent(referrer = "https://google.com/search?q=nav&lang=no")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.referrerPath).isEqualTo("/search")
        assertThat(enriched.websiteEvent.referrerQuery).isEqualTo("q=nav&lang=no")
    }

    @Test
    fun `referrer domain is null for empty referrer`() {
        val event = sampleEvent(referrer = "")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.referrerDomain).isNull()
    }

    @Test
    fun `referrer domain is null for null referrer`() {
        val event = sampleEvent(referrer = null)
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.referrerDomain).isNull()
    }

    @Test
    fun `referrer domain is null for relative path referrer`() {
        val event = sampleEvent(referrer = "/some/path")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.referrerDomain).isNull()
    }

    @Test
    fun `hostname is on websiteEvent`() {
        val event = sampleEvent()
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.hostname).isEqualTo("felgen.intern.nav.no")
    }

    @Test
    fun `browser, os, device from UserAgentInfo are set on session`() {
        val event = sampleEvent()
        val uaInfo = UserAgentInfo(browser = "chrome", os = "Windows", device = "desktop")
        val enriched = event.toEnrichedEvent(userAgentInfo = uaInfo)
        assertThat(enriched.session.browser).isEqualTo("chrome")
        assertThat(enriched.session.os).isEqualTo("Windows")
        assertThat(enriched.session.device).isEqualTo("desktop")
    }

    @Test
    fun `empty UserAgentInfo results in null browser os device`() {
        val event = sampleEvent()
        val enriched = event.toEnrichedEvent(userAgentInfo = UserAgentInfo())
        assertThat(enriched.session.browser).isNull()
        assertThat(enriched.session.os).isNull()
        assertThat(enriched.session.device).isNull()
    }

    @Test
    fun `enrichedEvent has createdAt timestamp`() {
        val event = sampleEvent()
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.createdAt).isNotNull()
    }

    @Test
    fun `distinctId comes from payload id`() {
        val event = sampleEvent(id = "user-abc-123")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.distinctId).isEqualTo("user-abc-123")
    }

    @Test
    fun `distinctId is null when payload id is null`() {
        val event = sampleEvent(id = null)
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.distinctId).isNull()
    }

    @Test
    fun `language en-US is normalized to en`() {
        val event = sampleEvent()
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.language).isEqualTo("en")
    }

    @Test
    fun `language nb-NO is normalized to nb`() {
        val event = sampleEvent(language = "nb-NO")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.language).isEqualTo("nb")
    }

    @Test
    fun `language zh-Hant-TW is normalized to zh`() {
        val event = sampleEvent(language = "zh-Hant-TW")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.language).isEqualTo("zh")
    }

    @Test
    fun `language with underscore pt_BR is normalized to pt`() {
        val event = sampleEvent(language = "pt_BR")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.language).isEqualTo("pt")
    }

    @Test
    fun `null language stays null`() {
        val event = sampleEvent(language = null)
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.language).isNull()
    }

    @Test
    fun `blank language becomes null`() {
        val event = sampleEvent(language = "  ")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.language).isNull()
    }

    @Test
    fun `simple language code is lowercased`() {
        val event = sampleEvent(language = "EN")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.session.language).isEqualTo("en")
    }

    @Test
    fun `page title is included in websiteEvent`() {
        val event = sampleEvent(title = "My Page Title")
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.pageTitle).isEqualTo("My Page Title")
    }

    @Test
    fun `null page title stays null in websiteEvent`() {
        val event = sampleEvent(title = null)
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.pageTitle).isNull()
    }

    private fun sampleEvent(
        url: String? = "/path/to/thing",
        referrer: String? = "",
        name: String? = null,
        language: String? = "en-US",
        title: String? = "Nav Webapps | Snarveier",
        id: String? = null
    ): Event = Event(
        type = "event", payload = Event.Payload(
            website = websiteId,
            id = id,
            hostname = "felgen.intern.nav.no",
            screen = "172x111",
            language = language,
            title = title,
            url = url,
            referrer = referrer,
            name = name
        )
    )

    private fun identifyEvent(
        id: String? = "1ba86cead5f21fbfa7d355fa482f5808bfd5460a",
        data: tools.jackson.databind.JsonNode? = null
    ): Event = Event(
        type = "identify", payload = Event.Payload(
            website = websiteId,
            id = id,
            hostname = "felgen.ansatt.nav.no",
            screen = "1280x800",
            language = "nb",
            title = "kake",
            url = "felgen.ansatt.nav.no/greier",
            data = data
        )
    )

    // ── identify event tests ────────────────────────────────────────

    @Test
    fun `identify event with data populates sessionData, not eventData`() {
        val data = JsonNodeFactory.instance.objectNode()
            .put("kake", "kake bake smake")
            .put("hest", "er best")
        val enriched = identifyEvent(data = data).toEnrichedEvent()

        assertThat(enriched.websiteEvent.eventData).isNull()
        assertThat(enriched.session.sessionData).isNotNull()
        assertThat(enriched.session.sessionData).hasSize(2)
    }

    @Test
    fun `identify event sessionData has correct keys and values`() {
        val data = JsonNodeFactory.instance.objectNode()
            .put("kake", "kake bake smake")
            .put("hest", "er best")
        val enriched = identifyEvent(data = data).toEnrichedEvent()

        val entries = enriched.session.sessionData!!
        val byKey = entries.associateBy { it.dataKey }
        assertThat(byKey["kake"]?.stringValue).isEqualTo("kake bake smake")
        assertThat(byKey["kake"]?.dataType).isEqualTo(1)
        assertThat(byKey["hest"]?.stringValue).isEqualTo("er best")
        assertThat(byKey["hest"]?.dataType).isEqualTo(1)
    }

    @Test
    fun `identify event sessionData includes numeric values`() {
        val data = JsonNodeFactory.instance.objectNode()
            .put("count", 42)
            .put("active", true)
        val enriched = identifyEvent(data = data).toEnrichedEvent()

        val entries = enriched.session.sessionData!!
        val byKey = entries.associateBy { it.dataKey }
        assertThat(byKey["count"]?.numberValue).isEqualTo(42.0)
        assertThat(byKey["count"]?.dataType).isEqualTo(2)
        assertThat(byKey["active"]?.numberValue).isEqualTo(1.0)
        assertThat(byKey["active"]?.dataType).isEqualTo(2)
    }

    @Test
    fun `identify event sessionData includes distinctId from payload id`() {
        val data = JsonNodeFactory.instance.objectNode().put("role", "admin")
        val enriched = identifyEvent(id = "user-abc-123", data = data).toEnrichedEvent()

        val entry = enriched.session.sessionData!!.first()
        assertThat(entry.distinctId).isEqualTo("user-abc-123")
        assertThat(enriched.session.distinctId).isEqualTo("user-abc-123")
    }

    @Test
    fun `identify event sessionData has matching sessionId`() {
        val data = JsonNodeFactory.instance.objectNode().put("role", "admin")
        val enriched = identifyEvent(data = data).toEnrichedEvent()

        val entry = enriched.session.sessionData!!.first()
        assertThat(entry.sessionId).isEqualTo(enriched.sessionId)
    }

    @Test
    fun `identify event without data produces null sessionData`() {
        val enriched = identifyEvent(data = null).toEnrichedEvent()
        assertThat(enriched.session.sessionData).isNull()
        assertThat(enriched.websiteEvent.eventData).isNull()
    }

    @Test
    fun `regular event with data populates eventData, not sessionData`() {
        val event = Event(
            type = "event", payload = Event.Payload(
                website = websiteId,
                hostname = "felgen.intern.nav.no",
                screen = "172x111",
                language = "en-US",
                url = "/test",
                data = JsonNodeFactory.instance.objectNode().put("color", "blue")
            )
        )
        val enriched = event.toEnrichedEvent()
        assertThat(enriched.websiteEvent.eventData).isNotNull()
        assertThat(enriched.websiteEvent.eventData).hasSize(1)
        assertThat(enriched.session.sessionData).isNull()
    }
}

