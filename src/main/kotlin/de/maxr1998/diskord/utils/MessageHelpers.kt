package de.maxr1998.diskord.utils

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext

fun Message.args(limit: Int) = content
    .replace("""\S+""", " ")
    .split(" ", limit = limit)
    .drop(1)

val Message.attachmentUrlsOrNull: List<String>?
    get() = with(attachments) {
        if (isNotEmpty()) map(Attachment::url)
        else null
    }

private fun wrapListIfNotEmpty(content: String): List<String>? = with(content.trim()) {
    if (isNotEmpty()) listOf(this) else null
}

suspend fun BotContext.extractEntries(args: List<String>, message: Message): List<String>? = when (args.size) {
    1 -> {
        // Handle message attachments or replied message attachments or content
        val repliedMessage = message.reference?.messageId?.let { id -> message.channel.getMessage(id) }
        message.attachmentUrlsOrNull
            ?: repliedMessage?.attachmentUrlsOrNull
            ?: repliedMessage?.let { msg ->
                wrapListIfNotEmpty(msg.content)
            }
    }
    2 -> wrapListIfNotEmpty(args[1])
    else -> null
}