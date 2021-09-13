package de.maxr1998.diskord.utils

import com.jessecorbett.diskord.api.common.Emoji
import com.jessecorbett.diskord.api.common.stringified
import de.maxr1998.diskord.Constants
import de.maxr1998.diskord.config.Config
import io.ktor.http.URLParserException
import io.ktor.http.Url

fun Config.getAckEmoji(): String =
    ackEmojiId?.let { id -> Emoji(id = id).stringified } ?: Constants.DEFAULT_ACK_EMOJI

fun String.splitLinesIfNotBlank(): List<String>? = splitToSequence('\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toList()
    .takeUnless(List<String>::isEmpty)

fun String.isUrl(): Boolean = try {
    Url(this)
    true
} catch (e: URLParserException) {
    false
}