@file:Suppress("MatchingDeclarationName")

package de.maxr1998.diskord.util.diskord

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext
import de.maxr1998.diskord.util.extension.getRepliedMessage
import de.maxr1998.diskord.util.extension.splitLinesIfNotBlank

sealed class ExtractionResult {
    class Raw(val content: String) : ExtractionResult()
    class Lines(val content: List<String>) : ExtractionResult()
    class Attachments(val content: List<Attachment>) : ExtractionResult()
}

private inline val String.linesResult: ExtractionResult?
    get() = when {
        startsWith("raw::") -> ExtractionResult.Raw(substring(@Suppress("MagicNumber") /* "raw::" length */ 5))
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