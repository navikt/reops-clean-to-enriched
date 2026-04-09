package no.nav.reops.useragent

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

data class UserAgentInfo(
    val browser: String? = null,
    val os: String? = null,
    val device: String? = null,
    val isBot: Boolean = false
)

@Service
class UserAgentService {

    /**
     * Parses a User-Agent string and returns browser, OS, device type, and bot detection.
     * Returns a [UserAgentInfo] with all null fields if the input is blank.
     *
     * @param screen optional screen dimensions (e.g. "1920x1080") used for laptop detection
     */
    fun parse(userAgent: String?, screen: String? = null): UserAgentInfo {
        if (userAgent.isNullOrBlank()) return UserAgentInfo()

        return try {
            if (isBot(userAgent)) {
                return UserAgentInfo(isBot = true)
            }

            val browser = UserAgentParser.detectBrowser(userAgent)
            val os = UserAgentParser.detectOS(userAgent)
            val device = UserAgentParser.detectDevice(userAgent, screen)

            UserAgentInfo(browser = browser, os = os, device = device, isBot = false)
        } catch (ex: Exception) {
            LOG.debug("User-Agent parsing failed for ua={}: {}", userAgent, ex.message)
            UserAgentInfo()
        }
    }

    /**
     * Detects bots/crawlers/spiders using common bot keywords in the UA string,
     * similar to Umami's isbot detection.
     */
    private fun isBot(userAgent: String): Boolean {
        val ua = userAgent.lowercase()
        return BOT_PATTERNS.any { ua.contains(it) }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(UserAgentService::class.java)

        /**
         * Common bot/crawler keywords matching the isbot library patterns.
         * This covers major search engine crawlers, social media bots, monitoring tools, etc.
         */
        private val BOT_PATTERNS = listOf(
            "bot", "crawler", "spider", "headless", "slurp", "mediapartners",
            "googlebot", "bingbot", "yandexbot", "baiduspider", "duckduckbot",
            "facebookexternalhit", "facebot", "twitterbot", "linkedinbot",
            "whatsapp", "telegrambot", "slackbot", "discordbot", "applebot",
            "semrushbot", "ahrefsbot", "mj12bot", "dotbot", "petalbot",
            "bytespider", "gptbot", "chatgpt", "claudebot", "anthropic",
            "uptimerobot", "pingdom", "sitemonitor", "nagios", "zabbix",
            "lighthouse", "pagespeed", "gtmetrix", "webpagetest",
            "phantomjs", "selenium", "puppeteer", "playwright", "cypress",
            "wget", "curl", "httpie", "python-requests", "java/", "go-http-client",
            "okhttp", "axios", "node-fetch", "postman", "insomnia",
            "feedfetcher", "feedparser", "rss", "atom",
            "archive.org_bot", "ia_archiver", "wayback",
            "scrapy", "nutch", "heritrix",
        )
    }
}

