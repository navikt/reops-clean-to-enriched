package no.nav.reops.filter

import no.nav.reops.event.Event
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.JsonNodeFactory
import java.util.UUID

internal object TestEventFactory {

    private val f = JsonNodeFactory.instance

    private fun json(value: Any?): JsonNode {
        return when (value) {
            null -> f.nullNode()
            is JsonNode -> value
            is String -> f.stringNode(value)
            is Boolean -> f.booleanNode(value)
            is Int -> f.numberNode(value)
            is Long -> f.numberNode(value)
            is Double -> f.numberNode(value)
            is Float -> f.numberNode(value)

            is Map<*, *> -> {
                val o = f.objectNode()
                value.forEach { (k, v) ->
                    if (k != null) o.set(k.toString(), json(v))
                }
                o
            }

            is List<*> -> {
                val a = f.arrayNode()
                value.forEach { a.add(json(it)) }
                a
            }

            is Set<*> -> {
                val a = f.arrayNode()
                value.forEach { a.add(json(it)) }
                a
            }

            is Array<*> -> {
                val a = f.arrayNode()
                value.forEach { a.add(json(it)) }
                a
            }

            else -> f.stringNode(value.toString())
        }
    }

    fun minimalEvent(): Event {
        return Event(
            type = "visit", payload = Event.Payload(
                website = UUID.randomUUID(),
                hostname = "localhost",
                screen = "12345678910",
                language = "nb",
                title = "john.doe@kake.no",
                url = "https://kake.no/12345678910",
                referrer = "https://kake.no/",
                data = json(mapOf("hest" to "er best", "antall" to 42, "liker-hest" to true))
            )
        )
    }

    fun eventWithData(text: String): Event {
        val base = minimalEvent()
        return base.copy(payload = base.payload.copy(data = json(mapOf("text" to text))))
    }

    fun jsonNode(value: Any?): JsonNode = json(value)
}