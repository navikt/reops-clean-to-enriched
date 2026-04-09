package no.nav.reops.event

import no.nav.reops.geo.GeoIpService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class GeoIpServiceTest {

    private lateinit var geoIpService: GeoIpService

    @BeforeEach
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
}

