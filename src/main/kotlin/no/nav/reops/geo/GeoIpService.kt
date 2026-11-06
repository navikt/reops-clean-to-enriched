package no.nav.reops.geo

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream
import java.math.BigInteger
import java.net.InetAddress
import java.util.zip.GZIPInputStream

@Service
class GeoIpService {
    private lateinit var ipv4: CountryRanges
    private lateinit var ipv6: CountryRanges

    @PostConstruct
    fun init() {
        ipv4 = load("geoip/ip2country-v4-u32.tsv.gz", ipv6 = false)
        ipv6 = load("geoip/ip2country-v6.tsv.gz", ipv6 = true)
        LOG.info("IPtoASN country datasets loaded: {} IPv4 ranges, {} IPv6 ranges", ipv4.size, ipv6.size)
    }

    private fun load(resource: String, ipv6: Boolean): CountryRanges {
        val stream = javaClass.classLoader.getResourceAsStream(resource)
            ?: error("$resource not found on classpath; build resources with fetchIpCountry")
        return GZIPInputStream(stream).use { CountryRanges.read(it, ipv6) }
    }

    /** Country-only, offline lookup. Never resolves DNS names or logs client IPs. */
    fun lookupCountry(ip: String?): String? {
        val clientIp = ip?.substringBefore(',')?.trim() ?: return null
        val address = parseIpLiteral(clientIp) ?: return null
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress ||
            address.isSiteLocalAddress || address.isMulticastAddress ||
            (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)
        ) return null
        val ranges = if (address.address.size == 4) ipv4 else ipv6
        return ranges.lookup(BigInteger(1, address.address))
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(GeoIpService::class.java)
    }
}

/** Sorted, disjoint inclusive ranges; binary search keeps per-event lookups logarithmic. */
internal class CountryRanges private constructor(private val ranges: List<CountryRange>) {
    val size: Int get() = ranges.size

    fun lookup(address: BigInteger): String? {
        var low = 0
        var high = ranges.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val range = ranges[middle]
            when {
                address < range.start -> high = middle - 1
                address > range.end -> low = middle + 1
                else -> return range.country
            }
        }
        return null
    }

    companion object {
        fun read(input: InputStream, ipv6: Boolean): CountryRanges {
            val ranges = ArrayList<CountryRange>()
            val countries = mutableMapOf<String, String>()
            input.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEachIndexed { index, line ->
                    val fields = line.split('\t')
                    require(fields.size == 3) { "Invalid IPtoASN row ${index + 1}" }
                    fun endpoint(value: String): BigInteger {
                        if (!ipv6) {
                            require(value.matches(Regex("[0-9]+"))) { "Invalid IPv4 range at row ${index + 1}" }
                            return value.toBigInteger().also {
                                require(it.bitLength() <= 32) { "IPv4 range exceeds address space at row ${index + 1}" }
                            }
                        }
                        val address = parseIpLiteral(value)
                        require(address != null && ':' in value) { "Invalid IPv6 range at row ${index + 1}" }
                        // Java normalizes IPv4-mapped IPv6 literals to four bytes.
                        val bytes = if (address.address.size == 4) {
                            ByteArray(10) + byteArrayOf(-1, -1) + address.address
                        } else address.address
                        return BigInteger(1, bytes)
                    }
                    val start = endpoint(fields[0])
                    val end = endpoint(fields[1])
                    require(start <= end && (ranges.isEmpty() || ranges.last().end < start)) {
                        "Unsorted, overlapping or reversed IPtoASN range at row ${index + 1}"
                    }
                    val country = when (val code = fields[2]) {
                        "None", "Unknown", "ZZ" -> null
                        else -> {
                            require(code.matches(Regex("[A-Z]{2}"))) { "Invalid country code at row ${index + 1}" }
                            countries.getOrPut(code) { code }
                        }
                    }
                    ranges.add(CountryRange(start, end, country))
                }
            }
            require(ranges.isNotEmpty()) { "Empty IPtoASN country dataset" }
            return CountryRanges(ranges)
        }
    }
}

private data class CountryRange(val start: BigInteger, val end: BigInteger, val country: String?)

/** Strict numeric addresses only: InetAddress must never receive a hostname. */
private fun parseIpLiteral(value: String): InetAddress? {
    if (value.isEmpty() || value.length > 45) return null
    if (':' in value) {
        if (value.any { it !in "0123456789abcdefABCDEF:." }) return null
        return runCatching { InetAddress.getByName(value) }.getOrNull()
    }
    val octets = value.split('.')
    if (octets.size != 4) return null
    val bytes = ByteArray(4)
    for ((index, octet) in octets.withIndex()) {
        if (octet.isEmpty() || octet.length > 3 || octet.any { it !in '0'..'9' }) return null
        if (octet.length > 1 && octet.startsWith('0')) return null
        val number = octet.toIntOrNull()?.takeIf { it in 0..255 } ?: return null
        bytes[index] = number.toByte()
    }
    return InetAddress.getByAddress(bytes)
}
