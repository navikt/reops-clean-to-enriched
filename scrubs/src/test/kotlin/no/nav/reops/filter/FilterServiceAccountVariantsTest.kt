package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FilterServiceAccountVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts bank account basic`() {
        assertEquals("Account: [PROXY-ACCOUNT]", redact("Account: 1234.56.78901"))
    }

    @Test
    fun `redacts bank account after underscore delimiter`() {
        assertEquals("my_account_[PROXY-ACCOUNT]", redact("my_account_1234.56.78901"))
    }

    @Test
    fun `redacts bank account after colon delimiter`() {
        assertEquals("my-account:[PROXY-ACCOUNT] it's nice", redact("my-account:1234.56.78901 it's nice"))
    }

    @Test
    fun `redacts bank account after hyphen delimiter`() {
        assertEquals("my-account-[PROXY-ACCOUNT]", redact("my-account-1234.56.78901"))
    }

    @Test
    fun `redacts bank account after slash delimiter`() {
        assertEquals("account/[PROXY-ACCOUNT]", redact("account/1234.56.78901"))
    }

    @Test
    fun `redacts bank account after space delimiter`() {
        assertEquals("account [PROXY-ACCOUNT]", redact("account 1234.56.78901"))
    }

    @Test
    fun `redacts bank account after pipe delimiter`() {
        assertEquals("account|[PROXY-ACCOUNT]", redact("account|1234.56.78901"))
    }

    @Test
    fun `redacts bank account after equals delimiter`() {
        assertEquals("account=[PROXY-ACCOUNT]", redact("account=1234.56.78901"))
    }

    @Test
    fun `redacts bank account after hash delimiter`() {
        assertEquals("account#[PROXY-ACCOUNT]", redact("account#1234.56.78901"))
    }

    @Test
    fun `redacts 11 digit account without dots as fnr or account`() {
        val result = redact("account:12345678901")
        assertTrue(result == "account:[PROXY-ACCOUNT]" || result == "account:[PROXY-FNR]")
    }
}

