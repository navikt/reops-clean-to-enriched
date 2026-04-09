package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.reops.event.Event
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class FilterServiceStructuralTransformsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    @Test
    fun `filterEvent redacts and drops comprehensive umami event payload data`() {
        val service = filterService()

        val event = Event(
            type = "event", payload = Event.Payload(
                website = UUID.randomUUID(),
                hostname = "https://example.nav.no",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com/page",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "api_key" to "abc123",
                        "device_id" to "device-123",

                        "user_email" to "john.doe@example.com",
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
                            "regular_text" to "This is fine"
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        val expected = event.copy(
            payload = event.payload.copy(
                hostname = "https://example.nav.no", data = TestEventFactory.jsonNode(
                    mapOf(
                        "api_key" to "abc123",
                        "device_id" to "device-123",

                        "user_email" to "[PROXY-EMAIL]",
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
                            "regular_text" to "This is fine"
                        )
                    )
                )
            )
        )

        assertEquals(expected, out)
    }

    @Test
    fun `filterEvent redacts advertising identifiers but preserves allowed keys`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "idfa" to "12345678-1234-1234-1234-123456789102",
                        "idfv" to "ABCDEF01-2345-6789-ABCD-EF0123456789",
                        "gaid" to "38400000-8cf0-11bd-b23e-10b96e40000d",
                        "adid" to "38400000-8cf0-11bd-b23e-10b96e40000d",
                        "android_id" to "9774d56d682e549c",
                        "aaid" to "87654321-4321-4321-4321-876543210987",
                        "msai" to "A1B2C3D4-E5F6-7890-ABCD-EF1234567890",
                        "advertising_id" to "00000000-0000-0000-0000-000000000000",
                        "api_key" to "my-api-key",
                        "device_id" to "my-device-id",
                        "website" to "my-website",
                        "regular_field" to "This is normal text"
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        val expectedData = TestEventFactory.jsonNode(
            mapOf(
                "idfa" to "[PROXY]",
                "idfv" to "[PROXY]",
                "gaid" to "[PROXY]",
                "adid" to "[PROXY]",
                "android_id" to "[PROXY]",
                "aaid" to "[PROXY]",
                "msai" to "[PROXY]",
                "advertising_id" to "[PROXY]",
                "api_key" to "my-api-key",
                "device_id" to "my-device-id",
                "website" to "my-website",
                "regular_field" to "This is normal text"
            )
        )

        assertEquals(expectedData, out.payload.data)
    }
}