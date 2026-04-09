package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServicePhoneVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts phone basic`() {
        assertEquals("Call me at [PROXY-PHONE]", redact("Call me at 98765432"))
    }

    @Test
    fun `redacts uuid containing phone-like start`() {
        val input = "Do not call me at AD748BD6-484B-416C-B444-84EE98765432 that's not a phone number, but a UUID"
        assertEquals("Do not call me at [PROXY-UUID] that's not a phone number, but a UUID", redact(input))
    }

    @Test
    fun `redacts uuid starting with phone-like digits`() {
        val input = "Nor should you call me at 98765432-484B-416C-B444-84EE98765432 that's also not a phone number, still a UUID"
        assertEquals("Nor should you call me at [PROXY-UUID] that's also not a phone number, still a UUID", redact(input))
    }

    @Test
    fun `redacts phone after underscore delimiter`() {
        assertEquals("my_phone_[PROXY-PHONE]", redact("my_phone_98765432"))
    }

    @Test
    fun `redacts phone after colon delimiter`() {
        assertEquals("my-phone:[PROXY-PHONE] it's nice", redact("my-phone:98765432 it's nice"))
    }

    @Test
    fun `redacts phone after hyphen delimiter`() {
        assertEquals("my-phone-[PROXY-PHONE]", redact("my-phone-98765432"))
    }

    @Test
    fun `redacts phone after dot delimiter`() {
        assertEquals("phone.[PROXY-PHONE]", redact("phone.98765432"))
    }

    @Test
    fun `redacts phone after slash delimiter`() {
        assertEquals("phone/[PROXY-PHONE]", redact("phone/98765432"))
    }

    @Test
    fun `redacts phone after space delimiter`() {
        assertEquals("phone [PROXY-PHONE]", redact("phone 98765432"))
    }

    @Test
    fun `redacts phone after pipe delimiter`() {
        assertEquals("phone|[PROXY-PHONE]", redact("phone|98765432"))
    }

    @Test
    fun `redacts phone after plus delimiter`() {
        assertEquals("phone+[PROXY-PHONE]", redact("phone+98765432"))
    }

    @Test
    fun `redacts phone after hash delimiter`() {
        assertEquals("phone#[PROXY-PHONE]", redact("phone#98765432"))
    }

    @Test
    fun `redacts phone inside parentheses`() {
        assertEquals("([PROXY-PHONE])", redact("(98765432)"))
    }
}

