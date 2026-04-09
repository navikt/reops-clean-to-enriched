package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceEmailVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts basic email`() {
        assertEquals("Contact: [PROXY-EMAIL]", redact("Contact: user@example.com"))
    }

    @Test
    fun `redacts email with underscore prefix as single email`() {
        assertEquals("[PROXY-EMAIL]", redact("my_email_user@example.com"))
    }

    @Test
    fun `redacts email after colon delimiter`() {
        assertEquals("my-email:[PROXY-EMAIL] it's nice", redact("my-email:user@example.com it's nice"))
    }

    @Test
    fun `redacts email with hyphen prefix as single email`() {
        assertEquals("[PROXY-EMAIL]", redact("my-email-user@example.com"))
    }

    @Test
    fun `redacts email with dot prefix as single email`() {
        assertEquals("[PROXY-EMAIL]", redact("email.is.user@example.com"))
    }

    @Test
    fun `redacts email after slash delimiter`() {
        assertEquals("email/[PROXY-EMAIL]", redact("email/user@example.com"))
    }

    @Test
    fun `redacts email after space delimiter`() {
        assertEquals("email [PROXY-EMAIL]", redact("email user@example.com"))
    }

    @Test
    fun `redacts email after pipe delimiter`() {
        assertEquals("email|[PROXY-EMAIL]", redact("email|user@example.com"))
    }

    @Test
    fun `redacts email with plus tag as single email`() {
        assertEquals("[PROXY-EMAIL]", redact("email+tag@example.com"))
    }

    @Test
    fun `redacts email after hash delimiter`() {
        assertEquals("email#[PROXY-EMAIL]", redact("email#user@example.com"))
    }

    @Test
    fun `redacts mailto email link completely`() {
        assertEquals("[PROXY-EMAIL]", redact("mailto:kake.bake.smake@nav.no"))
    }

    @Test
    fun `redacts mailto email with surrounding text`() {
        assertEquals("Contact: [PROXY-EMAIL] for info", redact("Contact: mailto:user@example.com for info"))
    }
}

