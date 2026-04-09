package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceIpVariantsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    private fun redact(input: String): String {
        val service = filterService()
        return service.filterEvent(TestEventFactory.eventWithData(input)).payload.data!!.get("text").asString()
    }

    @Test
    fun `redacts ip basic`() {
        assertEquals("IP: [PROXY-IP]", redact("IP: 192.168.1.1"))
    }

    @Test
    fun `redacts ip after underscore delimiter`() {
        assertEquals("my_ip_[PROXY-IP]", redact("my_ip_192.168.1.1"))
    }

    @Test
    fun `redacts ip after colon delimiter`() {
        assertEquals("my-ip:[PROXY-IP] it's nice", redact("my-ip:192.168.1.1 it's nice"))
    }

    @Test
    fun `redacts ip after hyphen delimiter`() {
        assertEquals("my-ip-[PROXY-IP]", redact("my-ip-192.168.1.1"))
    }

    @Test
    fun `redacts ip after slash delimiter`() {
        assertEquals("ip/[PROXY-IP]", redact("ip/192.168.1.1"))
    }

    @Test
    fun `redacts ip after space delimiter`() {
        assertEquals("ip [PROXY-IP]", redact("ip 192.168.1.1"))
    }

    @Test
    fun `redacts ip after pipe delimiter`() {
        assertEquals("ip|[PROXY-IP]", redact("ip|192.168.1.1"))
    }

    @Test
    fun `redacts ip after equals delimiter`() {
        assertEquals("ip=[PROXY-IP]", redact("ip=192.168.1.1"))
    }

    @Test
    fun `redacts ip inside parentheses`() {
        assertEquals("([PROXY-IP])", redact("(192.168.1.1)"))
    }
}
