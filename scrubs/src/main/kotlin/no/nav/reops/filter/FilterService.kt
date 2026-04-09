package no.nav.reops.filter

import io.micrometer.core.instrument.MeterRegistry
import no.nav.reops.event.Event
import no.nav.reops.event.OptOutFilter
import org.springframework.stereotype.Service
import io.micrometer.core.instrument.Counter
import org.slf4j.LoggerFactory

@Service
class FilterService(meterRegistry: MeterRegistry) {
    private val rules: List<RedactionRule> = RuleSetFactory.create(meterRegistry)
    private val keyPolicy: KeyPolicy = DefaultPolicies.keyPolicy()
    private val urlPolicy: UrlPolicy = DefaultPolicies.urlPolicy()

    fun filterEvent(event: Event, optOutFilters: List<OptOutFilter>? = null): Event {
        val excludeKeys = DefaultPolicies.defaultFilter
        val excludedLabels = optOutFilters.toExcludedLabels()
        LOG.debug("Exclude filters for this event: {}, excluded labels: {}", excludeKeys, excludedLabels)

        val redactor = Redactor(rules, excludedLabels)
        val p = event.payload
        val traverser = Traverser(
            keyPolicy = keyPolicy, urlPolicy = urlPolicy, redactor = redactor, excludeKeys = excludeKeys
        )

        val sanitized = p.copy(
            website = p.website,
            id = p.id,
            hostname = p.hostname.redactedUnlessExcluded("hostname", excludeKeys, redactor),
            screen = p.screen.redactedUnlessExcluded("screen", excludeKeys, redactor),
            language = p.language.redactedUnlessExcluded("language", excludeKeys, redactor),
            title = p.title.redactedUnlessExcluded("title", excludeKeys, redactor),
            url = p.url.redactedUnlessExcluded("url", excludeKeys, redactor),
            referrer = p.referrer.redactedUnlessExcluded("referrer", excludeKeys, redactor),
            name = p.name.redactedUnlessExcluded("name", excludeKeys, redactor),
            data = if ("data" in excludeKeys) p.data else p.data?.let { traverser.transform(it) })

        return event.copy(payload = sanitized)
    }

    private fun String?.redactedUnlessExcluded(field: String, excludeKeys: Set<String>, redactor: Redactor): String? {
        if (field in excludeKeys) return this
        return this?.let { urlPolicy.redactUrl(it, redactor) }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(FilterService::class.java)

        private val OPT_OUT_LABEL_MAP: Map<OptOutFilter, String> = mapOf(
            OptOutFilter.UUID to "PROXY-UUID"
        )

        private fun List<OptOutFilter>?.toExcludedLabels(): Set<String> =
            this?.mapNotNull { OPT_OUT_LABEL_MAP[it] }?.toSet() ?: emptySet()
    }
}

internal object DefaultPolicies {
    fun keyPolicy(): KeyPolicy = KeyPolicy(
        preservedKeys = setOf("api_key", "device_id", "website"),
        droppedKeys = setOf("ip_address"),
        forcedValues = mapOf("ip" to "\$remote"),
        advertisingIdKeys = FilterPatterns.ADVERTISING_ID_KEYS
    )

    fun urlPolicy(): UrlPolicy = UrlPolicy(
        isNestedUrlField = { ctx ->
            ctx.depth == 2 && ctx.containerKey == "payload" && (ctx.key == "url" || ctx.key == "referrer")
        }, urlLikeKeys = setOf(
            "path",
            "href",
            "destinasjon",
            "url",
            "link",
            "pathname",
            "linkText",
            "destination",
            "url_path",
            "fra",
            "lenketekst",
            "lenkesti",
            "newLocation",
            "prevLocation"
        ), pathExcludedLabels = setOf("PROXY-FILEPATH")
    )

    val defaultFilter: Set<String> = setOf(
        "komponent",
        "lenketekst",
        "breadcrumbs",
        "pageType",
        "pageTheme",
        "employer",
        "seksjon",
        "valg",
        "jobTitle",
        "occupationLevel2",
        "enhet",
        "filter",
        "organisasjoner",
        "destinasjon",
        "location",
        "arbeidssted",
        "kilde",
        "skjemanavn",
        "lenkegruppe",
        "linkText",
        "descriptionId",
        "tema",
        "innholdstype",
        "yrkestittel",
        "tlbhrNavn",
    )
}

internal data class RedactionRule(
    val name: String, val label: String, val regex: Regex, val counter: Counter, val preserve: Boolean = false
)

internal object RuleSetFactory {
    fun create(meterRegistry: MeterRegistry): List<RedactionRule> = listOf(
        keepRule(meterRegistry),
        rule("uuid", "PROXY-UUID", FilterPatterns.UUID_REGEX, meterRegistry),
        rule("filepath", "PROXY-FILEPATH", FilterPatterns.FILEPATH_REGEX, meterRegistry),
        rule("fnr", "PROXY-FNR", FilterPatterns.FNR_REGEX, meterRegistry),
        rule("navident", "PROXY-NAVIDENT", FilterPatterns.NAVIDENT_REGEX, meterRegistry),
        rule("email", "PROXY-EMAIL", FilterPatterns.EMAIL_REGEX, meterRegistry),
        rule("ip", "PROXY-IP", FilterPatterns.IP_REGEX, meterRegistry),
        rule("phone", "PROXY-PHONE", FilterPatterns.PHONE_REGEX, meterRegistry),
        rule("name", "PROXY-NAME", FilterPatterns.NAME_REGEX, meterRegistry),
        rule("address", "PROXY-ADDRESS", FilterPatterns.ADDRESS_REGEX, meterRegistry),
        rule("secret_address", "PROXY-SECRET-ADDRESS", FilterPatterns.SECRET_ADDRESS_REGEX, meterRegistry),
        rule("account", "PROXY-ACCOUNT", FilterPatterns.ACCOUNT_REGEX, meterRegistry),
        rule("org_number", "PROXY-ORG-NUMBER", FilterPatterns.ORG_NUMBER_REGEX, meterRegistry),
        rule("license_plate", "PROXY-LICENSE-PLATE", FilterPatterns.LICENSE_PLATE_REGEX, meterRegistry),
        rule("search", "PROXY-SEARCH", FilterPatterns.SEARCH_REGEX, meterRegistry)
    )

    private fun keepRule(meterRegistry: MeterRegistry): RedactionRule = RedactionRule(
        name = "keep",
        label = "PROXY-KEEP",
        regex = FilterPatterns.KEEP_REGEX,
        counter = counter(meterRegistry, "keep"),
        preserve = true
    )

    private fun rule(name: String, label: String, regex: Regex, meterRegistry: MeterRegistry): RedactionRule =
        RedactionRule(
            name = name, label = label, regex = regex, counter = counter(meterRegistry, name), preserve = false
        )

    private fun counter(meterRegistry: MeterRegistry, ruleName: String): Counter =
        Counter.builder("redactions_total").tag("rule", ruleName).register(meterRegistry)
}
