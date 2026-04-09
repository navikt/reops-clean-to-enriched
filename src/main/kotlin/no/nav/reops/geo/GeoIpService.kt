package no.nav.reops.geo

import com.maxmind.geoip2.DatabaseReader
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.InetAddress

@Service
class GeoIpService {

    private lateinit var reader: DatabaseReader

    @PostConstruct
    fun init() {
        val stream = javaClass.classLoader.getResourceAsStream("dbip-country-lite.mmdb")
            ?: throw IllegalStateException("dbip-country-lite.mmdb not found on classpath")
        reader = DatabaseReader.Builder(stream).build()
        LOG.info("GeoIP database loaded successfully")
    }

    @PreDestroy
    fun close() {
        reader.close()
    }

    /**
     * Resolves the country ISO code (e.g. "NO", "SE") from an IP address string.
     * Handles `X-Forwarded-For` format by taking the first (client) IP.
     * Returns null if the lookup fails or the IP is private/invalid.
     */
    fun lookupCountry(ip: String?): String? {
        if (ip.isNullOrBlank()) return null
        return try {
            val clientIp = ip.split(",").first().trim()
            val address = InetAddress.getByName(clientIp)
            reader.country(address).country.isoCode
        } catch (ex: Exception) {
            LOG.debug("GeoIP lookup failed for ip={}: {}", ip, ex.message)
            null
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(GeoIpService::class.java)
    }
}