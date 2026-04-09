package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceSanctioningTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    @Test
    fun `filepath exclusion redacts uuid in behandling path`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val input = "/behandling/__a50e8400-e29b-41d4-a716-a4665544000a/brev"
        val event = base.copy(
            type = "event",
            payload = base.payload.copy(url = input)
        )
        val out = service.filterEvent(event)
        assertEquals("/behandling/__[PROXY-UUID]/brev", out.payload.url)
    }

    @Test
    fun `filepath exclusion redacts uuid in arbeidsplassen url`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val input = "https://arbeidsplassen.nav.no/stillinger/stilling/fabaa3cc-90e7-4c00-88aa-ab8d2f9831e8"
        val event = base.copy(
            type = "event",
            payload = base.payload.copy(url = input)
        )
        val out = service.filterEvent(event)
        assertEquals("https://arbeidsplassen.nav.no/stillinger/stilling/[PROXY-UUID]", out.payload.url)
    }
}

