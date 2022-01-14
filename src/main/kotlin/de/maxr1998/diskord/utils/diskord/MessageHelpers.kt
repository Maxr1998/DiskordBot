@file:Suppress("NOTHING_TO_INLINE")

package de.maxr1998.diskord.utils.diskord

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext
import de.maxr1998.diskord.utils.splitLinesIfNotBlank

inline fun Message.args(limit: Int): List<String> = content
    .splitWhitespaceNonEmpty(limit + 1)
    .drop(1)

fun String.splitWhitespaceNonEmpty(limit: Int): List<String> {
    require(limit >= 0) { "Limit must be non-negative, but was $limit" }

    val result = ArrayList<String>()
    var start = 0

    for (i in indices) {
        if (limit > 0 && result.size + 1 == limit) break
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

sealed class ExtractionResult {
    class Raw(val content: String) : ExtractionResult()
    class Lines(val content: List<String>) : ExtractionResult()
    class Attachments(val content: List<Attachment>) : ExtractionResult()
}

private inline val String.linesResult: ExtractionResult?
    get() = when {
        startsWith("raw::") -> ExtractionResult.Raw(substring(5))
        else -> splitLinesIfNotBlank()?.let(ExtractionResult::Lines)
    }

private inline val Message.attachmentsResultOrNull: ExtractionResult?
    get() = if (attachments.isNotEmpty()) ExtractionResult.Attachments(attachments) else null

suspend fun BotContext.extractEntries(args: List<String>, message: Message): ExtractionResult? = when (args.size) {
    1 -> {
        // Handle message attachments or replied message attachments or content
        val repliedMessage = message.getRepliedMessage(this)
        message.attachmentsResultOrNull
            ?: repliedMessage?.attachmentsResultOrNull
            ?: repliedMessage?.content?.linesResult
    }
    2 -> args[1].linesResult
    else -> null
}