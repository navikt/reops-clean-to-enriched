package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceNavidentVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts navident basic`() {
        assertEquals("User: [PROXY-NAVIDENT]", redact("User: X123456"))
    }

    @Test
    fun `redacts navident after underscore delimiter`() {
        assertEquals("my_navident_[PROXY-NAVIDENT]", redact("my_navident_X123456"))
    }

    @Test
    fun `redacts navident after colon delimiter`() {
        assertEquals("my-navident:[PROXY-NAVIDENT] it's nice", redact("my-navident:X123456 it's nice"))
    }

    @Test
    fun `redacts navident after hyphen delimiter`() {
        assertEquals("my-navident-[PROXY-NAVIDENT]", redact("my-navident-X123456"))
    }

    @Test
    fun `redacts navident after dot delimiter`() {
        assertEquals("navident.[PROXY-NAVIDENT]", redact("navident.X123456"))
    }

    @Test
    fun `redacts navident after slash delimiter`() {
        assertEquals("navident/[PROXY-NAVIDENT]", redact("navident/X123456"))
    }

    @Test
    fun `redacts navident after space delimiter`() {
        assertEquals("navident [PROXY-NAVIDENT]", redact("navident X123456"))
    }

    @Test
    fun `redacts navident after pipe delimiter`() {
        assertEquals("navident|[PROXY-NAVIDENT]", redact("navident|X123456"))
    }

    @Test
    fun `redacts navident after plus delimiter`() {
        assertEquals("navident+[PROXY-NAVIDENT]", redact("navident+X123456"))
    }

    @Test
    fun `redacts navident inside parentheses`() {
        assertEquals("([PROXY-NAVIDENT])", redact("(X123456)"))
    }
}

