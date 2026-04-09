package no.nav.reops.useragent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UserAgentServiceTest {

    private lateinit var service: UserAgentService

    @BeforeEach
    fun setUp() {
        service = UserAgentService()
    }

    @Test
    fun `returns empty info for null user agent`() {
        val info = service.parse(null)
        assertThat(info.browser).isNull()
        assertThat(info.os).isNull()
        assertThat(info.device).isNull()
    }

    @Test
    fun `returns empty info for blank user agent`() {
        val info = service.parse("  ")
        assertThat(info.browser).isNull()
        assertThat(info.os).isNull()
        assertThat(info.device).isNull()
    }

    @Test
    fun `parses Chrome on Windows as desktop`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val info = service.parse(ua)
        assertThat(info.browser).isEqualTo("chrome")
        assertThat(info.os).isEqualTo("Windows")
        assertThat(info.device).isEqualTo("desktop")
    }

    @Test
    fun `parses Safari on iPhone as mobile`() {
        val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        val info = service.parse(ua)
        assertThat(info.browser).isEqualTo("ios")
        assertThat(info.os).isEqualTo("iOS")
        assertThat(info.device).isEqualTo("mobile")
    }

    @Test
    fun `parses Safari on iPad as tablet`() {
        val ua = "Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        val info = service.parse(ua)
        assertThat(info.browser).isEqualTo("ios")
        assertThat(info.os).isEqualTo("iOS")
        assertThat(info.device).isEqualTo("tablet")
    }

    @Test
    fun `parses Firefox on Linux as desktop`() {
        val ua = "Mozilla/5.0 (X11; Linux x86_64; rv:109.0) Gecko/20100101 Firefox/115.0"
        val info = service.parse(ua)
        assertThat(info.browser).isEqualTo("firefox")
        assertThat(info.os).isEqualTo("Linux")
        assertThat(info.device).isEqualTo("desktop")
    }

    @Test
    fun `parses Chrome on Android tablet as tablet`() {
        val ua = "Mozilla/5.0 (Linux; Android 13; SM-X800) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val info = service.parse(ua)
        assertThat(info.browser).isEqualTo("chrome")
        assertThat(info.os).isEqualTo("Android")
        assertThat(info.device).isEqualTo("tablet")
    }

    @Test
    fun `parses Chrome on Android phone as mobile`() {
        val ua = "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val info = service.parse(ua)
        assertThat(info.browser).isEqualTo("chrome")
        assertThat(info.os).isEqualTo("Android")
        assertThat(info.device).isEqualTo("mobile")
    }

    @Test
    fun `parses Edge Chromium on macOS as desktop`() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
        val info = service.parse(ua)
        assertThat(info.browser).isEqualTo("edge-chromium")
        assertThat(info.os).isEqualTo("Mac OS")
        assertThat(info.device).isEqualTo("desktop")
        assertThat(info.isBot).isFalse()
    }

    @Test
    fun `parses Edge Chromium on macOS with screen as laptop`() {
        val ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0"
        val info = service.parse(ua, "420x69")
        assertThat(info.browser).isEqualTo("edge-chromium")
        assertThat(info.os).isEqualTo("Mac OS")
        assertThat(info.device).isEqualTo("laptop")
        assertThat(info.isBot).isFalse()
    }

    @Test
    fun `desktop with small screen is laptop`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val info = service.parse(ua, "1920x1080")
        assertThat(info.device).isEqualTo("laptop")
    }

    @Test
    fun `desktop with large screen stays desktop`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val info = service.parse(ua, "2560x1440")
        assertThat(info.device).isEqualTo("desktop")
    }

    @Test
    fun `desktop without screen stays desktop`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val info = service.parse(ua)
        assertThat(info.device).isEqualTo("desktop")
    }

    @Test
    fun `mobile device ignores screen for laptop detection`() {
        val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        val info = service.parse(ua, "390x844")
        assertThat(info.device).isEqualTo("mobile")
    }

    @Test
    fun `detects Googlebot as bot`() {
        val ua = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"
        val info = service.parse(ua)
        assertThat(info.isBot).isTrue()
    }

    @Test
    fun `detects Bingbot as bot`() {
        val ua = "Mozilla/5.0 (compatible; bingbot/2.0; +http://www.bing.com/bingbot.htm)"
        val info = service.parse(ua)
        assertThat(info.isBot).isTrue()
    }

    @Test
    fun `detects curl as bot`() {
        val ua = "curl/7.88.1"
        val info = service.parse(ua)
        assertThat(info.isBot).isTrue()
    }

    @Test
    fun `detects python-requests as bot`() {
        val ua = "python-requests/2.31.0"
        val info = service.parse(ua)
        assertThat(info.isBot).isTrue()
    }

    @Test
    fun `detects GPTBot as bot`() {
        val ua = "Mozilla/5.0 AppleWebKit/537.36 (KHTML, like Gecko; compatible; GPTBot/1.0; +https://openai.com/gptbot)"
        val info = service.parse(ua)
        assertThat(info.isBot).isTrue()
    }

    @Test
    fun `detects Puppeteer as bot`() {
        val ua = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) HeadlessChrome/120.0.0.0 Safari/537.36 Puppeteer"
        val info = service.parse(ua)
        assertThat(info.isBot).isTrue()
    }

    @Test
    fun `real Chrome browser is not a bot`() {
        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val info = service.parse(ua)
        assertThat(info.isBot).isFalse()
    }

    @Test
    fun `real Safari mobile is not a bot`() {
        val ua = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1"
        val info = service.parse(ua)
        assertThat(info.isBot).isFalse()
    }

    @Test
    fun `null user agent is not a bot`() {
        val info = service.parse(null)
        assertThat(info.isBot).isFalse()
    }
}

