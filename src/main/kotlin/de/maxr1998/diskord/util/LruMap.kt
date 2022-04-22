package de.maxr1998.diskord.util

class LruMap<K, V>(private val maxCapacity: Int) : LinkedHashMap<K, V>(
    initialCapacity = (maxCapacity / LOAD_FACTOR).toInt() + 2,
    loadFactor = LOAD_FACTOR,
    accessOrder = true,
) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
        return size > maxCapacity
    }

    companion object {
        private const val LOAD_FACTOR = 0.8f
    }
}