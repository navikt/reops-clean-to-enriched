package no.nav.reops.useragent

/**
 * Lightweight user-agent parser that mirrors Umami's detection behavior.
 *
 * Browser detection is ported from the `detect-browser` npm package.
 * OS detection is ported from the `detect-browser` npm package (simplified to family names).
 * Device detection is ported from `ua-parser-js` device-type heuristics.
 */
object UserAgentParser {

    private data class BrowserRule(val name: String, val regex: Regex)

    private val browserRules: List<BrowserRule> = listOf(
        BrowserRule("aol", Regex("""AOLShield/[\d._]+""")),
        BrowserRule("edge", Regex("""Edge/[\d._]+""")),
        BrowserRule("edge-ios", Regex("""EdgiOS/[\d._]+""")),
        BrowserRule("yandexbrowser", Regex("""YaBrowser/[\d._]+""")),
        BrowserRule("kakaotalk", Regex("""KAKAOTALK\s[\d.]+""")),
        BrowserRule("samsung", Regex("""SamsungBrowser/[\d.]+""")),
        BrowserRule("silk", Regex("""\bSilk/[\d._-]+\b""")),
        BrowserRule("miui", Regex("""MiuiBrowser/[\d.]+$""")),
        BrowserRule("beaker", Regex("""BeakerBrowser/[\d.]+""")),
        BrowserRule("edge-chromium", Regex("""Edg[A]?/[\d.]+""")),
        BrowserRule("chromium-webview", Regex("""(?!Chrom.*OPR)wv\).*Chrom(?:e|ium)/[\d.]+(?:\s|$)""")),
        BrowserRule("chrome", Regex("""(?!Chrom.*OPR)Chrom(?:e|ium)/[\d.]+(?:\s|$)""")),
        BrowserRule("phantomjs", Regex("""PhantomJS/[\d.]+(?:\s|$)""")),
        BrowserRule("crios", Regex("""CriOS/[\d.]+(?:\s|$)""")),
        BrowserRule("firefox", Regex("""Firefox/[\d.]+(?:\s|$)""")),
        BrowserRule("fxios", Regex("""FxiOS/[\d.]+""")),
        BrowserRule("opera-mini", Regex("""Opera Mini.*Version/[\d.]+""")),
        BrowserRule("opera", Regex("""Opera/[\d.]+(?:\s|$)""")),
        BrowserRule("opera", Regex("""OPR/[\d.]+(?:\s|$)""")),
        BrowserRule("ie", Regex("""Trident/7\.0.*rv:[\d.]+.*\).*Gecko$""")),
        BrowserRule("ie", Regex("""MSIE\s[\d.]+;.*Trident/[4-7]\.0""")),
        BrowserRule("ie", Regex("""MSIE\s7\.0""")),
        BrowserRule("bb10", Regex("""BB10;\sTouch.*Version/[\d.]+""")),
        BrowserRule("android", Regex("""Android\s[\d.]+""")),
        BrowserRule("ios", Regex("""Version/[\d._]+.*Mobile.*Safari.*""")),
        BrowserRule("safari", Regex("""Version/[\d._]+.*Safari""")),
        BrowserRule("facebook", Regex("""FB[AS]V/[\d.]+""")),
        BrowserRule("instagram", Regex("""Instagram\s[\d.]+""")),
        BrowserRule("ios-webview", Regex("""AppleWebKit/[\d.]+.*Mobile""")),
        BrowserRule("ios-webview", Regex("""AppleWebKit/[\d.]+.*Gecko\)$""")),
    )

    /**
     * Detects the browser name from a user-agent string.
     * Returns null if no browser could be identified.
     */
    fun detectBrowser(ua: String): String? {
        for (rule in browserRules) {
            if (rule.regex.containsMatchIn(ua)) {
                return rule.name
            }
        }
        return null
    }

    private data class OsRule(val name: String, val regex: Regex)

    private val osRules: List<OsRule> = listOf(
        OsRule("iOS", Regex("""iP(hone|od|ad)""")),
        OsRule("Android", Regex("""Android""")),
        OsRule("BlackBerry OS", Regex("""BlackBerry|BB10""")),
        OsRule("Windows", Regex("""IEMobile""")),
        OsRule("Amazon OS", Regex("""Kindle""")),
        OsRule("Windows", Regex("""(Windows|Win16|Win95|Win98|WinCE|Windows CE)""")),
        OsRule("Open BSD", Regex("""OpenBSD""")),
        OsRule("Sun OS", Regex("""SunOS""")),
        OsRule("Chrome OS", Regex("""CrOS""")),
        OsRule("Linux", Regex("""(Linux|X11)""")),
        OsRule("Mac OS", Regex("""(Mac_PowerPC|Macintosh)""")),
        OsRule("QNX", Regex("""QNX""")),
        OsRule("BeOS", Regex("""BeOS""")),
    )

    /**
     * Detects the operating system family from a user-agent string.
     * Returns null if no OS could be identified.
     */
    fun detectOS(ua: String): String? {
        for (rule in osRules) {
            if (rule.regex.containsMatchIn(ua)) {
                return rule.name
            }
        }
        return null
    }

    private val tabletRegex = Regex(
        """(?i)(ipad|playbook|silk)|(android(?!.*mobi))|kindle|nexus\s?(7|9|10)|xoom|sm-t|gt-p|gt-n|sh-[0-9]{2}[a-z]|lenovo\s?tab"""
    )

    private val mobileRegex = Regex(
        """(?i)(iphone|ipod|mobile|android.*mobi|blackberry|bb10|windows phone|iemobile|opera\sMini|opera\sMobi|symbian|webos|palm|phone|nokia|samsung[- ]?g|lg[- ]?[a-z]|htc|huawei|pixel|oneplus|xiaomi|oppo|vivo|realme|zte|motorola|alcatel|sony.*xperia)"""
    )

    /**
     * Classifies the device type from a user-agent string and optional screen dimensions.
     * Mirrors Umami's getDevice(): returns "tablet", "mobile", "laptop", or "desktop".
     * When screen is provided (e.g. "1920x1080") and the device would be "desktop",
     * returns "laptop" if the screen width is <= 1920.
     */
    fun detectDevice(ua: String, screen: String? = null): String {
        if (tabletRegex.containsMatchIn(ua)) return "tablet"
        if (mobileRegex.containsMatchIn(ua)) return "mobile"

        if (!screen.isNullOrBlank()) {
            val width = screen.split("x").firstOrNull()?.toIntOrNull()
            if (width != null && width <= 1920) return "laptop"
        }

        return "desktop"
    }
}