package no.nav.reops.filter

internal class KeyPolicy(
    private val preservedKeys: Set<String>,
    private val droppedKeys: Set<String>,
    private val forcedValues: Map<String, String>,
    private val advertisingIdKeys: Set<String>
) {
    sealed class Decision {
        data object Drop : Decision()
        data class Replace(val value: Any?) : Decision()
        data class Preserve(val value: Any?) : Decision()
        data object None : Decision()
    }

    fun decide(key: String, value: Any?): Decision = when {
        key in droppedKeys -> Decision.Drop
        key in preservedKeys -> Decision.Preserve(value)
        forcedValues.containsKey(key) -> Decision.Replace(forcedValues.getValue(key))
        key in advertisingIdKeys -> Decision.Replace("[PROXY]")
        else -> Decision.None
    }
}