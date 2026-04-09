package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class FilterServiceExcludeFiltersTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    @Test
    fun `filterEvent applies default exclude filters`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "komponent" to "header",
                        "lenketekst" to "some link text"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!
        // Keys in defaultFilter should be preserved (excluded from redaction)
        assertEquals("header", data.get("komponent").asString())
        assertEquals("some link text", data.get("lenketekst").asString())
    }

    @Test
    fun `filterEvent redacts url by default`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(url = "/search?email=user@example.com&phone=98765432")
        )

        val out = service.filterEvent(event)

        assertNotNull(out.payload.url)
        // url is not in defaultFilter, so it should be redacted
    }
}