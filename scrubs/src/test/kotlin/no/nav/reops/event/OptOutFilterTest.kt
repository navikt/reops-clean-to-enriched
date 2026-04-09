package no.nav.reops.event

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OptOutFilterTest {

    @Test
    fun `parseHeader returns null for null input`() {
        assertNull(OptOutFilter.parseHeader(null))
    }

    @Test
    fun `parseHeader returns null for blank input`() {
        assertNull(OptOutFilter.parseHeader(""))
        assertNull(OptOutFilter.parseHeader("   "))
    }

    @Test
    fun `parseHeader returns null for unrecognised tokens`() {
        assertNull(OptOutFilter.parseHeader("stuff,unknown"))
    }

    @Test
    fun `parseHeader parses uuid`() {
        val result = OptOutFilter.parseHeader("uuid")
        assertEquals(listOf(OptOutFilter.UUID), result)
    }

    @Test
    fun `parseHeader is case insensitive`() {
        val result = OptOutFilter.parseHeader("UUID")
        assertEquals(listOf(OptOutFilter.UUID), result)
    }

    @Test
    fun `parseHeader ignores unrecognised tokens and returns known ones`() {
        val result = OptOutFilter.parseHeader("uuid,stuff")
        assertEquals(listOf(OptOutFilter.UUID), result)
    }

    @Test
    fun `parseHeader trims whitespace around tokens`() {
        val result = OptOutFilter.parseHeader(" uuid , stuff ")
        assertEquals(listOf(OptOutFilter.UUID), result)
    }

    @Test
    fun `parseHeader deduplicates`() {
        val result = OptOutFilter.parseHeader("uuid,uuid")
        assertEquals(listOf(OptOutFilter.UUID), result)
    }

    @Test
    fun `parseHeader throws when header exceeds max length`() {
        val longValue = "a".repeat(51)
        assertThrows<IllegalArgumentException> {
            OptOutFilter.parseHeader(longValue)
        }
    }

    @Test
    fun `parseHeader parses JSON array format`() {
        val result = OptOutFilter.parseHeader("""["uuid"]""")
        assertEquals(listOf(OptOutFilter.UUID), result)
    }

    @Test
    fun `parseHeader parses JSON array with multiple entries`() {
        val result = OptOutFilter.parseHeader("""["uuid", "stuff"]""")
        assertEquals(listOf(OptOutFilter.UUID), result)
    }

    @Test
    fun `parseHeader accepts header at max length`() {
        val value = "a".repeat(50)
        assertNull(OptOutFilter.parseHeader(value))
    }
}

