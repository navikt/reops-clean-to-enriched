rootProject.name = "reops-user-events"

listOf("contract", "scrubs", "umami", "opensearch", "bigquery").forEach {
    if (file(it).isDirectory) include(it)
}
