package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceFnrHexStringTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    // --- test_fnr_regex_does_not_match_sha1_and_hex_strings ---

    @Test
    fun `standalone 11-digit FNR should be redacted`() {
        assertEquals("[PROXY-FNR]", redact("23031510135"))
    }

    @Test
    fun `FNR in text should be redacted`() {
        assertEquals("User SSN is [PROXY-FNR] here", redact("User SSN is 23031510135 here"))
    }

    @Test
    fun `SHA-1 hash should not be redacted`() {
        val input = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
        assertEquals(input, redact(input))
    }

    @Test
    fun `uppercase SHA-1 hash should not be redacted`() {
        val input = "A94A8FE5CCB19BA61C4C0873D391E987982FBBD3"
        assertEquals(input, redact(input))
    }

    @Test
    fun `SHA-256 hash should not be redacted`() {
        val input = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(input, redact(input))
    }

    @Test
    fun `git commit hash should not be redacted`() {
        val input = "1234567890abcdef1234567890abcdef12345678"
        assertEquals(input, redact(input))
    }

    @Test
    fun `40-digit string should not be redacted as FNR`() {
        val input = "1234567890123456789012345678901234567890"
        assertEquals(input, redact(input))
    }

    @Test
    fun `FNR with punctuation around it should still be redacted`() {
        assertEquals("fnr:[PROXY-FNR],", redact("fnr:23031510135,"))
    }

    @Test
    fun `hex string with letter prefix should not be redacted`() {
        val input = "f12345678901234567890"
        assertEquals(input, redact(input))
    }

    @Test
    fun `hex string with letter suffix should not be redacted`() {
        val input = "12345678901234567890a"
        assertEquals(input, redact(input))
    }

    @Test
    fun `in JSON context SHA-1 preserved and FNR redacted`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "commit" to "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3",
                        "user_ssn" to "23031510135",
                        "hash_value" to "1234567890abcdef1234567890abcdef12345678"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!

        assertEquals("a94a8fe5ccb19ba61c4c0873d391e987982fbbd3", data.get("commit").asString())
        assertEquals("[PROXY-FNR]", data.get("user_ssn").asString())
        assertEquals("1234567890abcdef1234567890abcdef12345678", data.get("hash_value").asString())
    }
}

