package no.nav.reops.event

import tools.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import java.util.UUID

class EventSerializationTest {

    private val mapper = jacksonObjectMapper()

    @Test
    fun `event payload omits id when null`() {
        val event = Event(
            type = "visit",
            payload = Event.Payload(website = UUID.randomUUID(), id = null)
        )

        val node = mapper.readTree(mapper.writeValueAsString(event))
        val payload = node["payload"]

        assertFalse(payload.has("id"))
    }

    @Test
    fun `event payload includes id when set`() {
        val event = Event(
            type = "visit",
            payload = Event.Payload(website = UUID.randomUUID(), id = "event-123")
        )

        val node = mapper.readTree(mapper.writeValueAsString(event))
        val payload = node["payload"]

        assertEquals("event-123", payload["id"].asString())
    }
}
