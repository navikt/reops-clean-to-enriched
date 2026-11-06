package no.nav.reops.event

import no.nav.reops.geo.GeoIpService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeoIpServiceTest {

    private lateinit var geoIpService: GeoIpService

    @BeforeAll
    fun setUp() {
        geoIpService = GeoIpService()
        geoIpService.init()
    }

    @Test
    fun `returns null for null ip`() {
        assertThat(geoIpService.lookupCountry(null)).isNull()
    }

    @Test
    fun `returns null for blank ip`() {
        assertThat(geoIpService.lookupCountry("  ")).isNull()
    }

    @Test
    fun `returns null for private ip`() {
        assertThat(geoIpService.lookupCountry("192.168.1.1")).isNull()
    }

    @Test
    fun `returns null for loopback ip`() {
        assertThat(geoIpService.lookupCountry("127.0.0.1")).isNull()
    }

    @Test
    fun `resolves country for known public ip`() {
        // 8.8.8.8 is Google DNS, expected to be in the US
        val country = geoIpService.lookupCountry("8.8.8.8")
        assertThat(country).isEqualTo("US")
    }

    @Test
    fun `handles X-Forwarded-For with multiple ips by using first`() {
        val country = geoIpService.lookupCountry("8.8.8.8, 10.0.0.1")
        assertThat(country).isEqualTo("US")
    }

    @Test
    fun `returns null for invalid ip`() {
        assertThat(geoIpService.lookupCountry("not-an-ip")).isNull()
    }

    @Test
    fun `rejects hostnames and non-canonical IPv4 addresses`() {
        for (ip in listOf("google.com", "localhost", "127.1", "134744072", "010.0.0.1", "8.8.8.256", "[::1]", "fe80::1%en0")) {
            assertThat(geoIpService.lookupCountry(ip)).describedAs(ip).isNull()
        }
    }

    @Test
    fun `returns null for non-public IPv6 addresses`() {
        for (ip in listOf("::", "::1", "fe80::1", "fc00::1", "fd12:3456::1", "ff02::1", "::ffff:192.168.1.1")) {
            assertThat(geoIpService.lookupCountry(ip)).describedAs(ip).isNull()
        }
    }

    @Test
    fun `supports public IPv6 and IPv4 mapped IPv6`() {
        assertThat(geoIpService.lookupCountry("::ffff:8.8.8.8")).isEqualTo(geoIpService.lookupCountry("8.8.8.8"))
        assertThat(geoIpService.lookupCountry("2001:4860:4860::8888")).matches("[A-Z]{2}")
    }
}
