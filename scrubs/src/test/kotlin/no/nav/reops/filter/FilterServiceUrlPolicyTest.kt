package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceUrlPolicyTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    @Test
    fun `filterEvent keeps payload url path but redacts query filepath`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(payload = base.payload.copy(url = "/path/to/thing?file=/my/secret/file.txt"))

        val out = service.filterEvent(event)
        assertEquals("/path/to/thing?file=[PROXY-FILEPATH]", out.payload.url)
    }

    @Test
    fun `filterEvent redacts filepath in nested data payload url query`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "payload" to mapOf(
                            "url" to "/path/to/thing?file=/my/secret/file.txt"
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        val inner = out.payload.data!!.get("payload")
        assertEquals("/path/to/thing?file=[PROXY-FILEPATH]", inner.get("url").asString())
    }

    @Test
    fun `filterEvent does not redact filepath in top level url and referrer`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                url = "/home/user/documents/file.txt",
                referrer = "/var/www/html/site",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "data" to mapOf(
                            "page_path" to "/users/john/profile",
                            "file_path" to "C:\\Users\\Admin\\data",
                            "filepath" to "/home/user/doc.pdf",
                            "description" to "/etc/passwd"
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        assertEquals("/home/user/documents/file.txt", out.payload.url)
        assertEquals("/var/www/html/site", out.payload.referrer)

        val inner = out.payload.data!!.get("data")
        assertEquals("[PROXY-FILEPATH]", inner.get("page_path").asString())
        assertEquals("[PROXY-FILEPATH]", inner.get("file_path").asString())
        assertEquals("[PROXY-FILEPATH]", inner.get("filepath").asString())
        assertEquals("[PROXY-FILEPATH]", inner.get("description").asString())
    }

    @Test
    fun `filterEvent does not redact filepath for nested non payload url fields`() {
        val service = filterService()

        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                url = "/home/user/documents/file.txt", data = TestEventFactory.jsonNode(
                    mapOf(
                        "data" to mapOf(
                            "url" to "/var/www/html/index.php", "config" to mapOf(
                                "url" to "C:\\Users\\Admin\\file.exe"
                            )
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)

        assertEquals("/home/user/documents/file.txt", out.payload.url)

        val inner = out.payload.data!!.get("data")
        assertEquals("/var/www/html/index.php", inner.get("url").asString())

        val cfg = inner.get("config")
        assertEquals("C:\\Users\\Admin\\file.exe", cfg.get("url").asString())
    }

    @Test
    fun `filterEvent redacts filepath in payload url query string`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(url = "/some/path/page?file=/home/user/secret.txt")
        )

        val out = service.filterEvent(event)
        assertEquals("/some/path/page?file=[PROXY-FILEPATH]", out.payload.url)
    }

    @Test
    fun `filterEvent redacts pii in payload url query string`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(url = "/search?email=user@example.com&phone=98765432")
        )

        val out = service.filterEvent(event)
        assertEquals("/search?email=[PROXY-EMAIL]&phone=[PROXY-PHONE]", out.payload.url)
    }

    @Test
    fun `filterEvent redacts mixed pii and filepath in payload url query string`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event",
            payload = base.payload.copy(url = "/api/data?path=/var/log/app.log&ssn=12345678910&redirect=/home/user/file.pdf")
        )

        val out = service.filterEvent(event)
        assertEquals("/api/data?path=[PROXY-FILEPATH]&ssn=[PROXY-FNR]&redirect=[PROXY-FILEPATH]", out.payload.url)
    }

    @Test
    fun `filterEvent keeps payload url without query unchanged`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(url = "/home/user/documents/file.txt")
        )

        val out = service.filterEvent(event)
        assertEquals("/home/user/documents/file.txt", out.payload.url)
    }

    @Test
    fun `filterEvent keeps payload url path that only looks like windows path unchanged`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(url = "/C:/Users/Admin/page")
        )

        val out = service.filterEvent(event)
        assertEquals("/C:/Users/Admin/page", out.payload.url)
    }

    @Test
    fun `filterEvent redacts pii in payload url and referrer paths`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                referrer = "https://example.com/path/to/person/johndoe@example.com/mail/view",
                url = "https://example.com/path/to/person/johndoe@example.com/mail/view"
            )
        )

        val out = service.filterEvent(event)
        assertEquals("https://example.com/path/to/person/[PROXY-EMAIL]/mail/view", out.payload.referrer)
        assertEquals("https://example.com/path/to/person/[PROXY-EMAIL]/mail/view", out.payload.url)
    }

    @Test
    fun `filterEvent redacts dot-separated name in payload url path`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                url = "/Users/Kenneth.Bakke.Isaksen/dev/reops/reops-meta/"
            )
        )

        val out = service.filterEvent(event)
        assertEquals("/Users/[PROXY-NAME]/dev/reops/reops-meta/", out.payload.url)
    }
}