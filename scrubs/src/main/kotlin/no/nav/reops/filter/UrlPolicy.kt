package no.nav.reops.filter

internal data class NodeContext(
    val depth: Int, val containerKey: String?, val key: String?
)

internal class UrlPolicy(
    private val isNestedUrlField: (NodeContext) -> Boolean,
    private val urlLikeKeys: Set<String>,
    private val pathExcludedLabels: Set<String>
) {
    fun isUrlField(ctx: NodeContext): Boolean = isNestedUrlField(ctx)

    fun isUrlLikeField(ctx: NodeContext): Boolean = ctx.key != null && ctx.key in urlLikeKeys

    fun redactUrl(url: String, redactor: Redactor, excludePathLabels: Boolean = true): String {
        val pathLabels = if (excludePathLabels) pathExcludedLabels else emptySet()
        val (path, queryOrEmpty) = splitPathAndQuery(url)
        val redactedPath = redactor.redact(input = path, excludedLabels = pathLabels, preserveUrls = false)

        if (queryOrEmpty.isEmpty()) return redactedPath

        val redactedQuery = redactor.redact(input = queryOrEmpty, excludedLabels = emptySet(), preserveUrls = false)
        return redactedPath + redactedQuery
    }

    private fun splitPathAndQuery(url: String): Pair<String, String> {
        val q = url.indexOf('?')
        return if (q < 0) url to "" else url.substring(0, q) to url.substring(q)
    }
}