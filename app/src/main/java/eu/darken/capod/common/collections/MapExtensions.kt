package eu.darken.capod.common.collections

inline fun <K, V> Map<K, V>.mutate(block: MutableMap<K, V>.() -> Unit): Map<K, V> {
    return toMutableMap().apply(block).toMap()
}
