package de.maxr1998.diskord.util.extension

import io.ktor.http.URLParserException
import io.ktor.http.Url

fun String.splitLinesIfNotBlank(): List<String>? = splitToSequence('\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toList()
    .takeUnless(List<String>::isEmpty)

fun String.splitWhitespaceNonEmpty(limit: Int = Int.MAX_VALUE): List<String> {
    require(limit > 0) { "Limit must be greater than zero, but was $limit" }

    val result = ArrayList<String>()
    var start = 0

    for (i in indices) {
        if (result.size + 1 == limit) break
        val c = this[i]
        if (c.isWhitespace()) {
            val item = substring(start, i)
            if (item.isNotEmpty()) {
                result.add(item)
            }
            start = i + 1
        }
    }

    if (start < length) {
        result.add(substring(start))
    }

    return result
}

fun String.toUrlOrNull(): Url? = when {
    any(Char::isWhitespace) -> null
    else -> try {
        Url(this)
    } catch (e: URLParserException) {
        null
    }
}

fun String.isUrl(): Boolean = try {
    Url(this)
    true
} catch (e: URLParserException) {
    false
}