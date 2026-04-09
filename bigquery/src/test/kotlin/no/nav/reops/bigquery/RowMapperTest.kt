package no.nav.reops.bigquery

import no.nav.reops.event.*
import org.assertj.core.api.Assertions.assertThat
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class RowMapperTest {

    private val websiteId = UUID.fromString("1dbfe4e9-bf8b-45d9-9305-928f22200bc0")
    private val eventId = UUID.fromString("aaaaaaaa-1111-2222-3333-444444444444")
    private val visitId = UUID.fromString("bbbbbbbb-1111-2222-3333-444444444444")
    private val createdAt = Instant.parse("2026-03-30T12:00:00Z")

    private val event = EnrichedEvent(
        websiteId = websiteId,
        sessionId = "session-abc",
        createdAt = createdAt,
        websiteEvent = WebsiteEvent(
            eventId = eventId,
            urlPath = "/page",
            urlQuery = "?q=test",
            referrerPath = "/home",
            referrerQuery = null,
            referrerDomain = "example.com",
            pageTitle = "Test Page",
            eventType = 2,
            eventName = "click",
            visitId = visitId,
            hostname = "app.nav.no",
            eventData = listOf(
                EventDataEntry(
                    eventDataId = UUID.fromString("cccccccc-1111-2222-3333-444444444444"),
                    dataKey = "color",
                    stringValue = "blue",
                    numberValue = null,
                    dateValue = null,
                    dataType = 1
                ),
                EventDataEntry(
                    eventDataId = UUID.fromString("dddddddd-1111-2222-3333-444444444444"),
                    dataKey = "count",
                    stringValue = null,
                    numberValue = 42.0,
                    dateValue = null,
                    dataType = 2
                )
            )
        ),
        session = Session(
            browser = "Chrome",
            os = "macOS",
            device = "desktop",
            screen = "1920x1080",
            language = "nb",
            country = "NO",
            distinctId = "user-123",
            sessionData = listOf(
                SessionDataEntry(
                    sessionDataId = UUID.fromString("eeeeeeee-1111-2222-3333-444444444444"),
                    sessionId = "session-abc",
                    dataKey = "role",
                    stringValue = "admin",
                    numberValue = null,
                    dateValue = null,
                    dataType = 1,
                    distinctId = "user-123"
                )
            )
        )
    )

    @Test
    fun `toWebsiteEventRow maps flat fields correctly`() {
        val row = RowMapper.toWebsiteEventRow(event)

        assertThat(row.getString("event_id")).isEqualTo(eventId.toString())
        assertThat(row.getString("website_id")).isEqualTo(websiteId.toString())
        assertThat(row.getString("session_id")).isEqualTo("session-abc")
        assertThat(row.getString("created_at")).isEqualTo(createdAt.toString())
        assertThat(row.getString("url_path")).isEqualTo("/page")
        assertThat(row.getString("url_query")).isEqualTo("?q=test")
        assertThat(row.getString("referrer_path")).isEqualTo("/home")
        assertThat(row.getString("referrer_domain")).isEqualTo("example.com")
        assertThat(row.getString("page_title")).isEqualTo("Test Page")
        assertThat(row.getInt("event_type")).isEqualTo(2)
        assertThat(row.getString("event_name")).isEqualTo("click")
        assertThat(row.getString("visit_id")).isEqualTo(visitId.toString())
        assertThat(row.getString("hostname")).isEqualTo("app.nav.no")
    }

    @Test
    fun `toEventDataRows produces one row per entry`() {
        val rows = RowMapper.toEventDataRows(event)

        assertThat(rows).hasSize(2)

        val first = rows[0]
        assertThat(first.getString("event_data_id")).isEqualTo("cccccccc-1111-2222-3333-444444444444")
        assertThat(first.getString("website_event_id")).isEqualTo(eventId.toString())
        assertThat(first.getString("website_id")).isEqualTo(websiteId.toString())
        assertThat(first.getString("data_key")).isEqualTo("color")
        assertThat(first.getString("string_value")).isEqualTo("blue")
        assertThat(first.getInt("data_type")).isEqualTo(1)

        val second = rows[1]
        assertThat(second.getString("data_key")).isEqualTo("count")
        assertThat(second.getDouble("number_value")).isEqualTo(42.0)
        assertThat(second.getInt("data_type")).isEqualTo(2)
    }

    @Test
    fun `toEventDataRows returns empty list when no eventData`() {
        val noData = event.copy(websiteEvent = event.websiteEvent.copy(eventData = null))
        assertThat(RowMapper.toEventDataRows(noData)).isEmpty()
    }

    @Test
    fun `toSessionRow maps flat fields correctly`() {
        val row = RowMapper.toSessionRow(event)

        assertThat(row.getString("session_id")).isEqualTo("session-abc")
        assertThat(row.getString("website_id")).isEqualTo(websiteId.toString())
        assertThat(row.getString("browser")).isEqualTo("Chrome")
        assertThat(row.getString("os")).isEqualTo("macOS")
        assertThat(row.getString("device")).isEqualTo("desktop")
        assertThat(row.getString("screen")).isEqualTo("1920x1080")
        assertThat(row.getString("language")).isEqualTo("nb")
        assertThat(row.getString("country")).isEqualTo("NO")
        assertThat(row.getString("distinct_id")).isEqualTo("user-123")
        assertThat(row.getString("hostname")).isEqualTo("app.nav.no")
        assertThat(row.getString("created_at")).isEqualTo(createdAt.toString())
    }

    @Test
    fun `toSessionDataRows produces one row per entry`() {
        val rows = RowMapper.toSessionDataRows(event)

        assertThat(rows).hasSize(1)
        val row = rows[0]
        assertThat(row.getString("session_data_id")).isEqualTo("eeeeeeee-1111-2222-3333-444444444444")
        assertThat(row.getString("session_id")).isEqualTo("session-abc")
        assertThat(row.getString("website_id")).isEqualTo(websiteId.toString())
        assertThat(row.getString("data_key")).isEqualTo("role")
        assertThat(row.getString("string_value")).isEqualTo("admin")
        assertThat(row.getInt("data_type")).isEqualTo(1)
        assertThat(row.getString("distinct_id")).isEqualTo("user-123")
    }

    @Test
    fun `toSessionDataRows returns empty list when no sessionData`() {
        val noData = event.copy(session = event.session.copy(sessionData = null))
        assertThat(RowMapper.toSessionDataRows(noData)).isEmpty()
    }

    @Test
    fun `toWebsiteRow maps all fields correctly`() {
        val websiteChangeEvent = WebsiteChangeEvent(
            website = Website(
                websiteId = UUID.fromString("ca2a6d29-686c-428b-9d80-651eac925bdb"),
                name = "Test Website",
                domain = "test.nav.no",
                updatedAt = Instant.parse("2026-03-30T14:00:00Z")
            ),
            status = WebsiteEventStatus.CREATE
        )

        val row = RowMapper.toWebsiteRow(websiteChangeEvent)

        assertThat(row.getString("website_id")).isEqualTo("ca2a6d29-686c-428b-9d80-651eac925bdb")
        assertThat(row.getString("name")).isEqualTo("Test Website")
        assertThat(row.getString("domain")).isEqualTo("test.nav.no")
        assertThat(row.get("share_id")).isEqualTo(JSONObject.NULL)
        assertThat(row.get("reset_at")).isEqualTo(JSONObject.NULL)
        assertThat(row.get("user_id")).isEqualTo(JSONObject.NULL)
        assertThat(row.get("created_at")).isEqualTo(JSONObject.NULL)
        assertThat(row.getString("updated_at")).isEqualTo("2026-03-30T14:00:00Z")
        assertThat(row.get("deleted_at")).isEqualTo(JSONObject.NULL)
        assertThat(row.get("created_by")).isEqualTo(JSONObject.NULL)
        assertThat(row.get("team_id")).isEqualTo(JSONObject.NULL)
    }
}
