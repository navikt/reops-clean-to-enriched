package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceSecretAddressTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts secret address hemmelig adresse`() {
        assertEquals("[PROXY-SECRET-ADDRESS]", redact("hemmelig adresse"))
    }

    @Test
    fun `redacts multiple patterns in same string`() {
        assertEquals(
            "Email [PROXY-EMAIL] with phone [PROXY-PHONE]",
            redact("Email user@test.com with phone 98765432")
        )
    }

    @Test
    fun `no redaction needed for normal string`() {
        val input = "This is a normal string with no PII"
        assertEquals(input, redact(input))
    }
}

