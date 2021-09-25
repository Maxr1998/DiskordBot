package de.maxr1998.diskord.utils.diskord

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext
import de.maxr1998.diskord.utils.splitLinesIfNotBlank

fun Message.args(limit: Int) = content
    .split(' ', '\n', limit = limit + 1)
    .drop(1)

sealed class ExtractionResult {
    class Lines(val content: List<String>) : ExtractionResult()
    class Attachments(val content: List<Attachment>) : ExtractionResult()
}

private inline val String.linesResult: ExtractionResult?
    get() = splitLinesIfNotBlank()?.let(ExtractionResult::Lines)

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