package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.reops.event.OptOutFilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class FilterServiceRedactionRulesTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    @Test
    fun `filterEvent redacts non preserved patterns in data`() {
        val service = filterService()

        val event = TestEventFactory.eventWithData("fnr=12345678910")
        val out = service.filterEvent(event)

        val outText = out.payload.data.toString()
        assertNotEquals(event.payload.data.toString(), outText)
        assertEquals(true, outText.contains("[PROXY-FNR]"))
    }

    @Test
    fun `filterEvent redacts uuid and preserves other allowed values in data`() {
        val service = filterService()

        val keep = "nav123456 test654321"
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        val url1 = "https://example.com/path?q=1"
        val url2 = "example.com/path"

        val value = "keep=$keep uuid=$uuid url1=$url1 url2=$url2"
        val event = TestEventFactory.eventWithData(value)

        val out = service.filterEvent(event)
        val outText = out.payload.data!!.get("text").asString()

        assertFalse(outText.contains(uuid))
        assertTrue(outText.contains("[PROXY-UUID]"))
        assertTrue(outText.contains("url1="))
        assertTrue(outText.contains("https://example.com/path"))
        assertTrue(outText.contains("[PROXY-SEARCH]"))
        assertTrue(outText.contains("url2=$url2"))
    }

    @Test
    fun `filterEvent preserves keep patterns navident like values`() {
        val service = filterService()

        val out1 = service.filterEvent(TestEventFactory.eventWithData("nav123456"))
        assertEquals("nav123456", out1.payload.data!!.get("text").asString())

        val out2 = service.filterEvent(TestEventFactory.eventWithData("test654321"))
        assertEquals("test654321", out2.payload.data!!.get("text").asString())
    }

    @Test
    fun `filterEvent redacts fnr in string value`() {
        val service = filterService()

        val out = service.filterEvent(TestEventFactory.eventWithData("23031510135"))
        assertEquals("[PROXY-FNR]", out.payload.data!!.get("text").asString())
    }

    @Test
    fun `filterEvent redacts fnr variants inside text`() {
        val service = filterService()

        val out1 = service.filterEvent(TestEventFactory.eventWithData("my_fnr_23031510135"))
        assertEquals("my_fnr_[PROXY-FNR]", out1.payload.data!!.get("text").asString())

        val out2 = service.filterEvent(TestEventFactory.eventWithData("my-fnr:23031510135 it's nice"))
        assertEquals("my-fnr:[PROXY-FNR] it's nice", out2.payload.data!!.get("text").asString())

        val out3 = service.filterEvent(TestEventFactory.eventWithData("my-fnr-23031510135"))
        assertEquals("my-fnr-[PROXY-FNR]", out3.payload.data!!.get("text").asString())
    }

    @Test
    fun `filterEvent keeps non matching strings unchanged`() {
        val service = filterService()

        val out1 = service.filterEvent(TestEventFactory.eventWithData("regularstring"))
        assertEquals("regularstring", out1.payload.data!!.get("text").asString())

        val out2 = service.filterEvent(TestEventFactory.eventWithData("anotherString"))
        assertEquals("anotherString", out2.payload.data!!.get("text").asString())

        val out3 = service.filterEvent(TestEventFactory.eventWithData("12345"))
        assertEquals("12345", out3.payload.data!!.get("text").asString())
    }

    @Test
    fun `filterEvent redacts pii fields recursively in data`() {
        val service = filterService()

        val website = UUID.randomUUID()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                website = website, data = TestEventFactory.jsonNode(
                    mapOf(
                        "user_email" to "user@example.com",
                        "user_id" to "550e8400-e29b-41d4-a716-446655440000",
                        "ssn" to "12345678910",
                        "phone" to "98765432",
                        "ip_address" to "192.168.1.1",
                        "event_properties" to mapOf(
                            "card_number" to "1234 5678 9012 3456",
                            "account" to "1234.56.78901",
                            "navident" to "X123456",
                            "regular_field" to "This is normal text"
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        val expected = event.copy(
            payload = event.payload.copy(
                website = website,
                url = "https://kake.no/[PROXY-FNR]",
                screen = "[PROXY-FNR]",
                title = "[PROXY-EMAIL]",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "user_email" to "[PROXY-EMAIL]",
                        "user_id" to "[PROXY-UUID]",
                        "ssn" to "[PROXY-FNR]",
                        "phone" to "[PROXY-PHONE]",
                        "event_properties" to mapOf(
                            "card_number" to "1234 5678 9012 3456",
                            "account" to "[PROXY-ACCOUNT]",
                            "navident" to "[PROXY-NAVIDENT]",
                            "regular_field" to "This is normal text"
                        )
                    )
                )
            )
        )

        assertEquals(expected, out)
    }

    @Test
    fun `filterEvent redacts comprehensive umami payload data`() {
        val service = filterService()

        val website = UUID.randomUUID()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                website = website,
                hostname = "https://kake.no/12345678910",
                screen = "https://kake.no/12345678910",
                language = "https://kake.no/12345678910",
                title = "https://kake.no/12345678910",
                url = "https://kake.no/12345678910",
                referrer = "https://kake.no/12345678910",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "api_key" to "abc123",
                        "device_id" to "device-123",
                        "user_email" to "john.doe@example.com",
                        "user_id" to "550e8400-e29b-41d4-a716-446655440000",
                        "user_ssn" to "12345678910",
                        "phone" to "98765432",
                        "navident" to "X123456",
                        "ip_address" to "192.168.1.100",
                        "ip" to "10.0.0.1",
                        "idfa" to "8D8AC610-566D-4EF0-9C22-186B2A5ED793",
                        "idfv" to "550E8400-E29B-41D4-A716-446655440000",
                        "adid" to "38400000-8cf0-11bd-b23e-10b96e40000d",
                        "gaid" to "12345678-90ab-cdef-1234-567890abcdef",
                        "android_id" to "9774d56d682e549c",
                        "aaid" to "df07c7dc-cea7-4a89-b328-810ff5acb15d",
                        "msai" to "6F9619FF-8B86-D011-B42D-00C04FC964FF",
                        "advertising_id" to "00000000-0000-0000-0000-000000000000",
                        "account_number" to "1234.56.78901",
                        "license_plate" to "AB12345",
                        "org_number" to "123456789",
                        "file_path" to "/home/john/Documents/secret.pdf",
                        "name" to "John Doe",
                        "address" to "0123 Oslo",
                        "uuid" to "550e8400-e29b-41d4-a716-446655440000",
                        "website_url" to "https://example.com/page",
                        "event_properties" to mapOf(
                            "card_number" to "1234 5678 9012 3456", "regular_text" to "This is fine"
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        val expected = event.copy(
            payload = event.payload.copy(
                website = website,
                hostname = "https://kake.no/[PROXY-FNR]",
                screen = "https://kake.no/[PROXY-FNR]",
                language = "https://kake.no/[PROXY-FNR]",
                title = "https://kake.no/[PROXY-FNR]",
                url = "https://kake.no/[PROXY-FNR]",
                referrer = "https://kake.no/[PROXY-FNR]",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "api_key" to "abc123",
                        "device_id" to "device-123",
                        "user_email" to "[PROXY-EMAIL]",
                        "user_id" to "[PROXY-UUID]",
                        "user_ssn" to "[PROXY-FNR]",
                        "phone" to "[PROXY-PHONE]",
                        "navident" to "[PROXY-NAVIDENT]",
                        "ip" to "\$remote",
                        "idfa" to "[PROXY]",
                        "idfv" to "[PROXY]",
                        "adid" to "[PROXY]",
                        "gaid" to "[PROXY]",
                        "android_id" to "[PROXY]",
                        "aaid" to "[PROXY]",
                        "msai" to "[PROXY]",
                        "advertising_id" to "[PROXY]",
                        "account_number" to "[PROXY-ACCOUNT]",
                        "license_plate" to "[PROXY-LICENSE-PLATE]",
                        "org_number" to "[PROXY-ORG-NUMBER]",
                        "file_path" to "[PROXY-FILEPATH]",
                        "name" to "[PROXY-NAME]",
                        "address" to "[PROXY-ADDRESS]",
                        "uuid" to "[PROXY-UUID]",
                        "website_url" to "https://example.com/page",
                        "event_properties" to mapOf(
                            "card_number" to "1234 5678 9012 3456", "regular_text" to "This is fine"
                        )
                    )
                )
            )
        )

        assertEquals(expected, out)
    }

    @Test
    fun `filterEvent removes ip_address field from payload data`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "ip_address" to "192.168.1.1", "ip" to "10.0.0.1"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        assertFalse(out.payload.data!!.has("ip_address"))
    }

    @Test
    fun `filterEvent should exclude name redaction for selected keys`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event",
            payload = base.payload.copy(
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "sidetittel" to "Ola Nordmann",
                        "komponent" to "Ola Nordmann", // exclude
                        "title" to "Ola Nordmann",
                        "text" to "Ola Nordmann",
                        "tekst" to "Ola Nordmann",
                        "lenketekst" to "Ola Nordmann", // exclude
                        "parametre" to mapOf(
                            "breadcrumbs" to "Ola Nordmann", // exclude
                            "pageType" to "Ola Nordmann", // exclude
                            "pageTheme" to "Ola Nordmann" // exclude
                        ),
                        "employer" to "Ola Nordmann", // exclude
                        "seksjon" to "Ola Nordmann", // exclude
                        "tittel" to "Ola Nordmann",
                        "valg" to "Ola Nordmann", // exclude
                        "jobTitle" to "Ola Nordmann", // exclude
                        "searchParams" to mapOf(
                            "q" to "Ola Nordmann",
                            "occupationLevel2" to "Ola Nordmann" // exclude
                        ),
                        "enhet" to "Ola Nordmann", // exclude
                        "filter" to "Ola Nordmann", // exclude
                        "organisasjoner" to "Ola Nordmann", // exclude
                        "destinasjon" to "Ola Nordmann", // exclude
                        "location" to "Ola Nordmann", // exclude
                        "arbeidssted" to "Ola Nordmann", // exclude
                        "kilde" to "Ola Nordmann", // exclude
                        "skjemanavn" to "Ola Nordmann", // exclude
                        "lenkegruppe" to "Ola Nordmann", // exclude
                        "linkText" to "Ola Nordmann", // exclude
                        "descriptionId" to "Ola Nordmann", // exclude
                        "tema" to "Ola Nordmann", // exclude
                        "innholdstype" to "Ola Nordmann", // exclude
                        "yrkestittel" to "Ola Nordmann", // exclude
                        "tlbhrNavn" to "Ola Nordmann" // exclude
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!

        // Redacted (not excluded)
        assertEquals("[PROXY-NAME]", data.get("sidetittel").asString())
        assertEquals("[PROXY-NAME]", data.get("title").asString())
        assertEquals("[PROXY-NAME]", data.get("text").asString())
        assertEquals("[PROXY-NAME]", data.get("tekst").asString())
        assertEquals("[PROXY-NAME]", data.get("tittel").asString())
        assertEquals("[PROXY-NAME]", data.get("searchParams").get("q").asString())

        // Preserved (excluded)
        assertEquals("Ola Nordmann", data.get("komponent").asString())
        assertEquals("Ola Nordmann", data.get("lenketekst").asString())
        assertEquals("Ola Nordmann", data.get("parametre").get("breadcrumbs").asString())
        assertEquals("Ola Nordmann", data.get("parametre").get("pageType").asString())
        assertEquals("Ola Nordmann", data.get("parametre").get("pageTheme").asString())
        assertEquals("Ola Nordmann", data.get("employer").asString())
        assertEquals("Ola Nordmann", data.get("seksjon").asString())
        assertEquals("Ola Nordmann", data.get("valg").asString())
        assertEquals("Ola Nordmann", data.get("jobTitle").asString())
        assertEquals("Ola Nordmann", data.get("searchParams").get("occupationLevel2").asString())
        assertEquals("Ola Nordmann", data.get("enhet").asString())
        assertEquals("Ola Nordmann", data.get("filter").asString())
        assertEquals("Ola Nordmann", data.get("organisasjoner").asString())
        assertEquals("Ola Nordmann", data.get("destinasjon").asString())
        assertEquals("Ola Nordmann", data.get("location").asString())
        assertEquals("Ola Nordmann", data.get("arbeidssted").asString())
        assertEquals("Ola Nordmann", data.get("kilde").asString())
        assertEquals("Ola Nordmann", data.get("skjemanavn").asString())
        assertEquals("Ola Nordmann", data.get("lenkegruppe").asString())
        assertEquals("Ola Nordmann", data.get("linkText").asString())
        assertEquals("Ola Nordmann", data.get("descriptionId").asString())
        assertEquals("Ola Nordmann", data.get("tema").asString())
        assertEquals("Ola Nordmann", data.get("innholdstype").asString())
        assertEquals("Ola Nordmann", data.get("yrkestittel").asString())
        assertEquals("Ola Nordmann", data.get("tlbhrNavn").asString())
    }

    @Test
    fun `filterEvent redacts uuid on all fields except website and id`() {
        val service = filterService()
        val websiteId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                website = websiteId,
                id = uuid,
                hostname = uuid,
                screen = uuid,
                language = uuid,
                title = uuid,
                url = "https://example.com/page/$uuid",
                referrer = "https://example.com/ref/$uuid",
                name = uuid,
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "some_field" to uuid,
                        "nested" to mapOf("deep" to uuid),
                        "website" to uuid
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        // website field is preserved as-is (UUID type, not redacted)
        assertEquals(websiteId, out.payload.website)

        // all other top-level string fields have the uuid redacted
        assertEquals(uuid, out.payload.id)  // id is preserved as-is (not redacted)
        assertEquals("[PROXY-UUID]", out.payload.hostname)
        assertEquals("[PROXY-UUID]", out.payload.screen)
        assertEquals("[PROXY-UUID]", out.payload.language)
        assertEquals("[PROXY-UUID]", out.payload.title)
        assertEquals("https://example.com/page/[PROXY-UUID]", out.payload.url)
        assertEquals("https://example.com/ref/[PROXY-UUID]", out.payload.referrer)
        assertEquals("[PROXY-UUID]", out.payload.name)

        // uuid inside data fields is redacted
        val data = out.payload.data!!
        assertEquals("[PROXY-UUID]", data.get("some_field").asString())
        assertEquals("[PROXY-UUID]", data.get("nested").get("deep").asString())

        // "website" key in data is preserved (via KeyPolicy preservedKeys)
        assertEquals(uuid, data.get("website").asString())
    }

    @Test
    fun `filterEvent preserves uuid everywhere when opt-out uuid is active`() {
        val service = filterService()
        val websiteId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        val uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                website = websiteId,
                hostname = uuid,
                screen = uuid,
                language = uuid,
                title = uuid,
                url = "https://example.com/page/$uuid",
                referrer = "https://example.com/ref/$uuid",
                name = uuid,
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "some_field" to uuid,
                        "nested" to mapOf("deep" to uuid),
                        "website" to uuid
                    )
                )
            )
        )

        val out = service.filterEvent(event, listOf(OptOutFilter.UUID))

        // website field is always preserved
        assertEquals(websiteId, out.payload.website)

        // all top-level string fields preserve their uuid values
        assertEquals(uuid, out.payload.hostname)
        assertEquals(uuid, out.payload.screen)
        assertEquals(uuid, out.payload.language)
        assertEquals(uuid, out.payload.title)
        assertEquals("https://example.com/page/$uuid", out.payload.url)
        assertEquals("https://example.com/ref/$uuid", out.payload.referrer)
        assertEquals(uuid, out.payload.name)

        // uuid inside data fields is preserved
        val data = out.payload.data!!
        assertEquals(uuid, data.get("some_field").asString())
        assertEquals(uuid, data.get("nested").get("deep").asString())
        assertEquals(uuid, data.get("website").asString())
    }

    @Test
    fun `filterEvent still redacts non-uuid patterns when opt-out uuid is active`() {
        val service = filterService()

        val event = TestEventFactory.eventWithData("fnr=12345678910 email=john@example.com")
        val out = service.filterEvent(event, listOf(OptOutFilter.UUID))
        val outText = out.payload.data!!.get("text").asString()

        assertTrue(outText.contains("[PROXY-FNR]"))
        assertTrue(outText.contains("[PROXY-EMAIL]"))
    }
}