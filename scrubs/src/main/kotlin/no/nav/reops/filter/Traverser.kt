package no.nav.reops.filter

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

internal class Traverser(
    private val keyPolicy: KeyPolicy,
    private val urlPolicy: UrlPolicy,
    private val redactor: Redactor,
    private val excludeKeys: Set<String>
) {
    fun transform(root: JsonNode): JsonNode =
        transformNode(root, NodeContext(depth = 0, containerKey = null, key = null))

    private fun transformNode(node: JsonNode, ctx: NodeContext): JsonNode = when {
        node.isNull -> node
        node.isString -> JsonNodeFactory.instance.stringNode(transformString(node.asString(), ctx))
        node.isNumber || node.isBoolean -> node
        node.isObject -> transformObject(node as ObjectNode, ctx)
        node.isArray -> transformArray(node as ArrayNode, ctx)
        else -> node
    }

    private fun transformString(raw: String, ctx: NodeContext): String {
        return when {
            urlPolicy.isUrlField(ctx) -> urlPolicy.redactUrl(raw, redactor, excludePathLabels = true)

            urlPolicy.isUrlLikeField(ctx) -> urlPolicy.redactUrl(raw, redactor, excludePathLabels = true)

            else -> redactor.redact(input = raw, excludedLabels = emptySet(), preserveUrls = true)
        }
    }

    private fun transformObject(obj: ObjectNode, ctx: NodeContext): JsonNode {
        val out = JsonNodeFactory.instance.objectNode()

        for ((key, value) in obj.properties()) {
            if (key in excludeKeys) {
                out.set(key, value)
                continue
            }

            when (val decision = keyPolicy.decide(key, value)) {
                is KeyPolicy.Decision.Drop -> {
                    // ignore
                }

                is KeyPolicy.Decision.Preserve -> {
                    out.set(key, decision.value as? JsonNode ?: value)
                }

                is KeyPolicy.Decision.Replace -> {
                    out.set(key, toJsonNode(decision.value))
                }

                is KeyPolicy.Decision.None -> {
                    out.set(key, transformNode(value, nextContextForChild(parent = ctx, key = key, value = value)))
                }
            }
        }

        return out
    }

    private fun nextContextForChild(parent: NodeContext, key: String, value: JsonNode): NodeContext {
        // Preserve original behavior:
        // - If the value is an object, we set containerKey=key and key=null for the nested object scope.
        // - Otherwise, we set key=key and keep containerKey as-is.
        val nextDepth = parent.depth + 1
        return if (value.isObject) {
            NodeContext(depth = nextDepth, containerKey = key, key = null)
        } else {
            NodeContext(depth = nextDepth, containerKey = parent.containerKey, key = key)
        }
    }

    private fun transformArray(arr: ArrayNode, ctx: NodeContext): JsonNode {
        val out = JsonNodeFactory.instance.arrayNode()
        for (child in arr) {
            // Same behavior: increment depth and reset containerKey/key
            out.add(transformNode(child, ctx.copy(depth = ctx.depth + 1, containerKey = null, key = null)))
        }
        return out
    }

    private fun toJsonNode(value: Any?): JsonNode {
        val f = JsonNodeFactory.instance
        return when (value) {
            null -> f.nullNode()
            is JsonNode -> value
            is String -> f.stringNode(value)
            is Boolean -> f.booleanNode(value)
            is Int -> f.numberNode(value)
            is Long -> f.numberNode(value)
            is Double -> f.numberNode(value)
            is Float -> f.numberNode(value)
            is java.math.BigInteger -> f.numberNode(value)
            is java.math.BigDecimal -> f.numberNode(value)
            else -> f.stringNode(value.toString())
        }
    }
}