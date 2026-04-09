package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceOrgNumberVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts org number basic`() {
        assertEquals("Org: [PROXY-ORG-NUMBER]", redact("Org: 123456789"))
    }

    @Test
    fun `redacts org number after underscore delimiter`() {
        assertEquals("my_org_[PROXY-ORG-NUMBER]", redact("my_org_123456789"))
    }

    @Test
    fun `redacts org number after colon delimiter`() {
        assertEquals("my-org:[PROXY-ORG-NUMBER] it's nice", redact("my-org:123456789 it's nice"))
    }

    @Test
    fun `redacts org number after hyphen delimiter`() {
        assertEquals("my-org-[PROXY-ORG-NUMBER]", redact("my-org-123456789"))
    }

    @Test
    fun `redacts org number after dot delimiter`() {
        assertEquals("org.[PROXY-ORG-NUMBER]", redact("org.123456789"))
    }

    @Test
    fun `redacts org number after slash delimiter`() {
        assertEquals("org/[PROXY-ORG-NUMBER]", redact("org/123456789"))
    }

    @Test
    fun `redacts org number after space delimiter`() {
        assertEquals("org [PROXY-ORG-NUMBER]", redact("org 123456789"))
    }

    @Test
    fun `redacts org number after pipe delimiter`() {
        assertEquals("org|[PROXY-ORG-NUMBER]", redact("org|123456789"))
    }

    @Test
    fun `redacts org number after equals delimiter`() {
        assertEquals("org=[PROXY-ORG-NUMBER]", redact("org=123456789"))
    }

    @Test
    fun `redacts org number after hash delimiter`() {
        assertEquals("org#[PROXY-ORG-NUMBER]", redact("org#123456789"))
    }

    @Test
    fun `redacts uuid that contains org-number-like suffix`() {
        val input = "AD748BD6-484B-416C-B444-aaa123456789"
        assertEquals("[PROXY-UUID]", redact(input))
    }
}

