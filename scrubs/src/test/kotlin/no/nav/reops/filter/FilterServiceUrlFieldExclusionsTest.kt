package no.nav.reops.filter

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FilterServiceUrlFieldExclusionsTest {

    private fun filterService(): FilterService = FilterService(SimpleMeterRegistry())

    // --- test_breadcrumbs_url_not_redacted_as_filepath ---

    @Test
    fun `breadcrumbs url entries are not redacted as filepaths`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                website = base.payload.website,
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "parametre" to mapOf(
                            "breadcrumbs" to listOf(
                                mapOf(
                                    "title" to "Min side",
                                    "url" to "https://www.intern.dev.nav.no/minside/",
                                    "handleInApp" to false
                                ),
                                mapOf(
                                    "title" to "Ditt sykefravær",
                                    "url" to "/",
                                    "handleInApp" to true
                                )
                            )
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val parametre = out.payload.data!!.get("parametre")
        val breadcrumbs = parametre.get("breadcrumbs")

        assertEquals("https://www.intern.dev.nav.no/minside/", breadcrumbs.get(0).get("url").asString())
        assertEquals("/", breadcrumbs.get(1).get("url").asString())
        assertEquals("Min side", breadcrumbs.get(0).get("title").asString())
        assertEquals("Ditt sykefravær", breadcrumbs.get(1).get("title").asString())
    }

    // --- test_url_field_exclusions_simple_paths ---

    @Test
    fun `simple url paths in url-like fields are not redacted as filepaths`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "path" to "/syk/sykefravaer",
                        "href" to "/min/side/oversikt",
                        "pathname" to "/nav/tjenester",
                        "url_path" to "./dokumenter/viktige",
                        "link" to "/kontakt/oss",
                        "destination" to "/hjelp/sporsmal",
                        "destinasjon" to "/soknad/dagpenger",
                        "fra" to "./forrige/side",
                        "linkText" to "gå til /hjelp/sporsmal/side",
                        "lenketekst" to "gå til /hjelp/sporsmal/side",
                        "lenkesti" to "/norsk/sti",
                        "newLocation" to "/nav/tjenester",
                        "prevLocation" to "/nav/tjenester"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!

        assertEquals("/syk/sykefravaer", data.get("path").asString())
        assertEquals("/min/side/oversikt", data.get("href").asString())
        assertEquals("/nav/tjenester", data.get("pathname").asString())
        assertEquals("./dokumenter/viktige", data.get("url_path").asString())
        assertEquals("/kontakt/oss", data.get("link").asString())
        assertEquals("/hjelp/sporsmal", data.get("destination").asString())
        assertEquals("/soknad/dagpenger", data.get("destinasjon").asString())
        assertEquals("./forrige/side", data.get("fra").asString())
        assertEquals("gå til /hjelp/sporsmal/side", data.get("linkText").asString())
        assertEquals("gå til /hjelp/sporsmal/side", data.get("lenketekst").asString())
        assertEquals("/norsk/sti", data.get("lenkesti").asString())
        assertEquals("/nav/tjenester", data.get("newLocation").asString())
        assertEquals("/nav/tjenester", data.get("prevLocation").asString())
    }

    // --- test_url_field_exclusions_with_filepath_like_paths ---

    @Test
    fun `filepath-like paths in url-like fields are preserved`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "path" to "/home/user/documents",
                        "href" to "/var/www/html/page",
                        "pathname" to "/C:/Users/Admin/file",
                        "url_path" to "./etc/config/settings",
                        "link" to "~/Documents/file.txt",
                        "destination" to "/usr/local/bin/app",
                        "destinasjon" to "/tmp/upload/data",
                        "fra" to "/backup/files/user",
                        "lenkesti" to "/private/var/folders"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!

        assertEquals("/home/user/documents", data.get("path").asString())
        assertEquals("/var/www/html/page", data.get("href").asString())
        assertEquals("/C:/Users/Admin/file", data.get("pathname").asString())
        assertEquals("./etc/config/settings", data.get("url_path").asString())
        assertEquals("~/Documents/file.txt", data.get("link").asString())
        assertEquals("/usr/local/bin/app", data.get("destination").asString())
        assertEquals("/tmp/upload/data", data.get("destinasjon").asString())
        assertEquals("/backup/files/user", data.get("fra").asString())
        assertEquals("/private/var/folders", data.get("lenkesti").asString())
    }

    // --- test_url_field_exclusions_with_pii ---

    @Test
    fun `pii is still redacted in url-like fields even with filepath exclusion`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "path" to "/user/john.doe@example.com/profile",
                        "href" to "/people/12345678901/details",
                        "pathname" to "/contact/98765432",
                        "link" to "https://example.com/user@test.com",
                        "destination" to "/nav/X123456/dashboard"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!

        assertEquals("/user/[PROXY-EMAIL]/profile", data.get("path").asString())
        assertEquals("/people/[PROXY-FNR]/details", data.get("href").asString())
        assertEquals("/contact/[PROXY-PHONE]", data.get("pathname").asString())
        assertEquals("https://example.com/[PROXY-EMAIL]", data.get("link").asString())
        assertEquals("/nav/[PROXY-NAVIDENT]/dashboard", data.get("destination").asString())
    }

    // --- test_url_field_exclusions_nested_objects ---

    @Test
    fun `url field exclusions work at various nesting levels`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "navigation" to mapOf(
                            "current" to mapOf(
                                "path" to "/home/user/documents",
                                "href" to "/var/www/page"
                            ),
                            "items" to listOf(
                                mapOf(
                                    "link" to "/usr/local/app",
                                    "destination" to "/etc/config"
                                )
                            )
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val nav = out.payload.data!!.get("navigation")
        val current = nav.get("current")
        assertEquals("/home/user/documents", current.get("path").asString())
        assertEquals("/var/www/page", current.get("href").asString())

        val items = nav.get("items")
        assertEquals("/usr/local/app", items.get(0).get("link").asString())
        assertEquals("/etc/config", items.get(0).get("destination").asString())
    }

    // --- test_non_url_fields_still_redacted ---

    @Test
    fun `non-url fields still get filepath redaction while url fields do not`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "data" to mapOf(
                            // These should be redacted (not url fields)
                            "file_path" to "/home/user/documents/secret.txt",
                            "filepath" to "C:/Users/Admin/private.doc",
                            "document" to "/var/www/uploads/file.pdf",
                            "log_file" to "/usr/local/bin/app",
                            // These should NOT be redacted (url fields)
                            "path" to "/home/user/documents/secret.txt",
                            "url" to "/var/www/uploads/file.pdf"
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!.get("data")

        assertEquals("[PROXY-FILEPATH]", data.get("file_path").asString())
        assertEquals("[PROXY-FILEPATH]", data.get("filepath").asString())
        assertEquals("[PROXY-FILEPATH]", data.get("document").asString())
        assertEquals("[PROXY-FILEPATH]", data.get("log_file").asString())

        assertEquals("/home/user/documents/secret.txt", data.get("path").asString())
        assertEquals("/var/www/uploads/file.pdf", data.get("url").asString())
    }

    // --- test_mixed_url_and_non_url_fields ---

    @Test
    fun `mixed url and non-url fields with same values side by side`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "navigation" to mapOf(
                            "path" to "/home/user/docs",          // URL field - keep
                            "file_path" to "/home/user/docs",     // Non-URL field - redact
                            "href" to "/var/www/html",            // URL field - keep
                            "directory" to "/var/www/html",       // Non-URL field - redact
                            "url" to "/etc/config",               // URL field - keep
                            "config_file" to "/etc/config"        // Non-URL field - redact
                        )
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val nav = out.payload.data!!.get("navigation")

        assertEquals("/home/user/docs", nav.get("path").asString())
        assertEquals("[PROXY-FILEPATH]", nav.get("file_path").asString())
        assertEquals("/var/www/html", nav.get("href").asString())
        assertEquals("[PROXY-FILEPATH]", nav.get("directory").asString())
        assertEquals("/etc/config", nav.get("url").asString())
        assertEquals("[PROXY-FILEPATH]", nav.get("config_file").asString())
    }

    // --- test_url_fields_with_full_urls ---

    @Test
    fun `full urls with protocols are preserved in url-like fields`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "path" to "https://example.com/home/user/path",
                        "href" to "http://test.no/var/www/html",
                        "link" to "https://nav.no/C:/Windows/System32",
                        "destination" to "https://site.com/usr/local/bin"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!

        assertEquals("https://example.com/home/user/path", data.get("path").asString())
        assertEquals("http://test.no/var/www/html", data.get("href").asString())
        assertEquals("https://nav.no/C:/Windows/System32", data.get("link").asString())
        assertEquals("https://site.com/usr/local/bin", data.get("destination").asString())
    }

    // --- test_url_fields_with_query_strings ---

    @Test
    fun `url-like fields with query parameters get query parts redacted`() {
        val service = filterService()
        val base = TestEventFactory.minimalEvent()
        val event = base.copy(
            type = "event", payload = base.payload.copy(
                hostname = "localhost",
                screen = "ok",
                language = "nb",
                title = "ok",
                url = "https://example.com",
                referrer = "",
                data = TestEventFactory.jsonNode(
                    mapOf(
                        "path" to "/home/user?file=/var/log/app.log",
                        "href" to "/page?redirect=/usr/bin/app",
                        "link" to "/search?q=test&from=/home/docs"
                    )
                )
            )
        )

        val out = service.filterEvent(event)
        val data = out.payload.data!!

        assertEquals("/home/user?file=[PROXY-FILEPATH]", data.get("path").asString())
        assertEquals("/page?redirect=[PROXY-FILEPATH]", data.get("href").asString())
        // Search query params get redacted by PROXY-SEARCH pattern
        assertEquals("/search[PROXY-SEARCH]&from=[PROXY-FILEPATH]", data.get("link").asString())
    }
}


