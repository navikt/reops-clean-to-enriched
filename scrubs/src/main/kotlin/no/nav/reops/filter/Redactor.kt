package no.nav.reops.filter

internal class Redactor(
    private val rules: List<RedactionRule>,
    private val globalExcludedLabels: Set<String> = emptySet()
) {
    fun redact(
        input: String, excludedLabels: Set<String> = emptySet(), preserveUrls: Boolean = true
    ): String {
        val allExcluded = if (globalExcludedLabels.isEmpty()) excludedLabels else globalExcludedLabels + excludedLabels
        var result = input

        // 1) Optionally preserve URL-like substrings in free-text
        val preservedUrls = PlaceholderStore(prefix = "__PRESERVED_URL_", suffix = "__")
        if (preserveUrls) {
            result = preservedUrls.preserveAll(result, FilterPatterns.URL_REGEX)
        }

        // 2) Apply rules (same order, same checks)
        for (rule in rules) {
            if (rule.label in allExcluded) continue
            if (!rule.regex.containsMatchIn(result)) continue

            rule.counter.increment()

            if (!rule.preserve) {
                result = rule.regex.replace(result) { "[${rule.label}]" }
            }
        }

        // 3) Restore URLs
        result = preservedUrls.restoreAll(result)


        return result
    }

    private class PlaceholderStore(
        private val prefix: String, private val suffix: String
    ) {
        private val values = mutableListOf<String>()

        fun preserveAll(input: String, regex: Regex): String {
            var out = input
            regex.findAll(out).forEachIndexed { i, match ->
                values.add(match.value)
                out = out.replace(match.value, placeholder(i))
            }
            return out
        }

        fun restoreAll(input: String): String {
            var out = input
            values.forEachIndexed { i, original ->
                out = out.replace(placeholder(i), original)
            }
            return out
        }

        private fun placeholder(i: Int): String = "$prefix${i}$suffix"
    }
}