package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceFnrVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts fnr basic`() {
        assertEquals("SSN: [PROXY-FNR]", redact("SSN: 12345678901"))
    }

    @Test
    fun `redacts fnr after underscore delimiter`() {
        assertEquals("my_fnr_[PROXY-FNR]", redact("my_fnr_12345678901"))
    }

    @Test
    fun `redacts fnr after colon delimiter`() {
        assertEquals("my-fnr:[PROXY-FNR] it's nice", redact("my-fnr:12345678901 it's nice"))
    }

    @Test
    fun `redacts fnr after hyphen delimiter`() {
        assertEquals("my-fnr-[PROXY-FNR]", redact("my-fnr-12345678901"))
    }

    @Test
    fun `redacts fnr after dot delimiter`() {
        assertEquals("fnr.[PROXY-FNR]", redact("fnr.12345678901"))
    }

    @Test
    fun `redacts fnr after slash delimiter`() {
        assertEquals("fnr/[PROXY-FNR]", redact("fnr/12345678901"))
    }

    @Test
    fun `redacts fnr after space delimiter`() {
        assertEquals("fnr [PROXY-FNR]", redact("fnr 12345678901"))
    }

    @Test
    fun `redacts fnr after pipe delimiter`() {
        assertEquals("fnr|[PROXY-FNR]", redact("fnr|12345678901"))
    }

    @Test
    fun `redacts fnr after plus delimiter`() {
        assertEquals("fnr+[PROXY-FNR]", redact("fnr+12345678901"))
    }

    @Test
    fun `redacts fnr after hash delimiter`() {
        assertEquals("fnr#[PROXY-FNR]", redact("fnr#12345678901"))
    }

    @Test
    fun `redacts fnr after at delimiter`() {
        assertEquals("fnr@[PROXY-FNR]", redact("fnr@12345678901"))
    }

    @Test
    fun `redacts fnr after parenthesis`() {
        assertEquals("(fnr)[PROXY-FNR]", redact("(fnr)12345678901"))
    }

    @Test
    fun `redacts fnr after square brackets`() {
        assertEquals("[fnr][PROXY-FNR]", redact("[fnr]12345678901"))
    }

    @Test
    fun `redacts fnr after curly braces`() {
        assertEquals("{fnr}[PROXY-FNR]", redact("{fnr}12345678901"))
    }

    @Test
    fun `redacts uuid that contains fnr-like suffix`() {
        val input = "AD748BD6-484B-416C-B444-a12345678901"
        assertEquals("[PROXY-UUID]", redact(input))
    }
}

