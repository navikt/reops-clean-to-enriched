package no.nav.reops.geo

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.net.InetAddress

class CountryRangesTest {
    private fun read(data: String, ipv6: Boolean = false): CountryRanges =
        CountryRanges.read(data.trimIndent().byteInputStream(), ipv6)

    @Test
    fun `IPv4 lookup includes endpoints and returns null for gaps and unknown countries`() {
        val ranges = read("""
            1	10	NO
            12	20	SE
            21	30	None
            31	40	Unknown
            41	50	ZZ
            4294967294	4294967295	US
        """)
        for (ip in listOf(1L, 10L)) assertThat(ranges.lookup(BigInteger.valueOf(ip))).isEqualTo("NO")
        for (ip in listOf(12L, 20L)) assertThat(ranges.lookup(BigInteger.valueOf(ip))).isEqualTo("SE")
        for (ip in listOf(0L, 11L, 21L, 30L, 40L, 50L, 51L)) {
            assertThat(ranges.lookup(BigInteger.valueOf(ip))).isNull()
        }
        assertThat(ranges.lookup(BigInteger("4294967295"))).isEqualTo("US")
    }

    @Test
    fun `IPv6 lookup handles unsigned 128 bit addresses and mapped ranges`() {
        val ranges = read("""
            ::	::1	None
            ::ffff:0:0	::ffff:ffff:ffff	None
            2001:4860::	2001:4860:ffff:ffff:ffff:ffff:ffff:ffff	US
            8000::	ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff	NO
        """, ipv6 = true)
        fun lookup(ip: String) = ranges.lookup(BigInteger(1, InetAddress.getByName(ip).address))
        assertThat(lookup("2001:4860::")).isEqualTo("US")
        assertThat(lookup("2001:4860:ffff:ffff:ffff:ffff:ffff:ffff")).isEqualTo("US")
        assertThat(lookup("2001:4861::")).isNull()
        assertThat(lookup("8000::")).isEqualTo("NO")
        assertThat(lookup("ffff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")).isEqualTo("NO")
    }

    @Test
    fun `invalid datasets fail closed`() {
        for (data in listOf(
            "",
            "1\t2",
            "1\t2\tNorway",
            "-1\t2\tNO",
            "1\t4294967296\tNO",
            "2\t1\tNO",
            "1\t10\tNO\n10\t20\tSE",
            "11\t20\tSE\n1\t10\tNO",
        )) {
            assertThatIllegalArgumentException().describedAs(data).isThrownBy { read(data) }
        }
        assertThatIllegalArgumentException().isThrownBy { read("localhost\t::1\tNO", ipv6 = true) }
        assertThatIllegalArgumentException().isThrownBy { read("1.2.3.4\t1.2.3.5\tNO", ipv6 = true) }
    }
}
