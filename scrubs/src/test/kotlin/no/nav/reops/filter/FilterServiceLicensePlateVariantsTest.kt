package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceLicensePlateVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts license plate basic`() {
        assertEquals("Plate: [PROXY-LICENSE-PLATE]", redact("Plate: AB12345"))
    }

    @Test
    fun `redacts license plate after underscore delimiter`() {
        assertEquals("my_plate_[PROXY-LICENSE-PLATE]", redact("my_plate_AB12345"))
    }

    @Test
    fun `redacts license plate after colon delimiter`() {
        assertEquals("my-plate:[PROXY-LICENSE-PLATE] it's nice", redact("my-plate:AB12345 it's nice"))
    }

    @Test
    fun `redacts license plate after hyphen delimiter`() {
        assertEquals("my-plate-[PROXY-LICENSE-PLATE]", redact("my-plate-AB12345"))
    }

    @Test
    fun `redacts license plate after dot delimiter`() {
        assertEquals("plate.[PROXY-LICENSE-PLATE]", redact("plate.AB12345"))
    }

    @Test
    fun `redacts license plate after slash delimiter`() {
        assertEquals("plate/[PROXY-LICENSE-PLATE]", redact("plate/AB12345"))
    }

    @Test
    fun `redacts license plate after space delimiter`() {
        assertEquals("plate [PROXY-LICENSE-PLATE]", redact("plate AB12345"))
    }

    @Test
    fun `redacts license plate after pipe delimiter`() {
        assertEquals("plate|[PROXY-LICENSE-PLATE]", redact("plate|AB12345"))
    }

    @Test
    fun `redacts license plate after equals delimiter`() {
        assertEquals("plate=[PROXY-LICENSE-PLATE]", redact("plate=AB12345"))
    }

    @Test
    fun `redacts license plate with space between letters and digits`() {
        assertEquals("plate:[PROXY-LICENSE-PLATE]", redact("plate:AB 12345"))
    }
}

