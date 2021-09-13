package de.maxr1998.diskord.utils.diskord

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext

fun Message.args(limit: Int) = content
    .split(' ', '\n', limit = limit)
    .drop(1)

val Message.attachmentUrlsOrNull: List<String>?
    get() = with(attachments) {
        if (isNotEmpty()) map(Attachment::url)
        else null
    }

private fun String.splitLinesIfNotBlank(): List<String>? = splitToSequence('\n')
    .map(String::trim)
    .filter(String::isNotEmpty)
    .toList()
    .takeUnless(List<String>::isEmpty)

suspend fun BotContext.extractEntries(args: List<String>, message: Message): List<String>? = when (args.size) {
    1 -> {
        // Handle message attachments or replied message attachments or content
        val repliedMessage = message.reference?.messageId?.let { id -> message.channel.getMessage(id) }
        message.attachmentUrlsOrNull
            ?: repliedMessage?.attachmentUrlsOrNull
            ?: repliedMessage?.content?.splitLinesIfNotBlank()
    }
    2 -> args[1].splitLinesIfNotBlank()
    else -> null
}