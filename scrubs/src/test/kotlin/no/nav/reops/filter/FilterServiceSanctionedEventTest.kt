package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class FilterServiceSanctionedEventTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    @Test
    fun `filterEvent leaves sanctioned event unchanged`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                website = UUID.randomUUID(),
                hostname = "arbeidsplassen.nav.no",
                screen = "3440x1440",
                language = "en-GB",
                title = "Er du interessert i salg og interiør? - arbeidsplassen.no",
                url = "https://arbeidsplassen.nav.no/stillinger/stilling/fabaa3cc-90e7-4c00-88aa-ab8d2f9831e8",
                referrer = ""
            )
        )

        val out = service.filterEvent(event)
        val expected = event.copy(
            payload = event.payload.copy(
                url = "https://arbeidsplassen.nav.no/stillinger/stilling/[PROXY-UUID]"
            )
        )
        assertEquals(expected, out)
    }
}
