@file:Suppress("MatchingDeclarationName")

package de.maxr1998.diskord.util.diskord

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext
import de.maxr1998.diskord.util.extension.getRepliedMessage
import de.maxr1998.diskord.util.extension.splitLinesIfNotBlank

sealed class ExtractionResult {
    class Raw(val content: String) : ExtractionResult()
    class Lines(val content: List<String>) : ExtractionResult()

    /**
     * Messages with attachments are not supported anymore.
     * Result type is only kept to show a warning message and abort.
     */
    data object UnsupportedAttachments : ExtractionResult()
}

private inline val String.linesResult: ExtractionResult?
    get() = when {
        startsWith("raw::") -> ExtractionResult.Raw(substring(@Suppress("MagicNumber") /* "raw::" length */ 5))
        else -> splitLinesIfNotBlank()?.let(ExtractionResult::Lines)
    }

suspend fun BotContext.extractEntries(args: List<String>, message: Message): ExtractionResult? = when (args.size) {
    1 -> {
        // Handle (unsupported) message attachments or replied message content
        val repliedMessage = message.getRepliedMessage(this)
        when {
            repliedMessage != null -> when {
                repliedMessage.attachments.isNotEmpty() -> ExtractionResult.UnsupportedAttachments
                else -> repliedMessage.content.linesResult
            }
            message.attachments.isNotEmpty() -> ExtractionResult.UnsupportedAttachments
            else -> null
        }
    }
    2 -> args[1].linesResult
    else -> null
}