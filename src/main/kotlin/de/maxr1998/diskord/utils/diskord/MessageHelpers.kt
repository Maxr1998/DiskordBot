package de.maxr1998.diskord.utils.diskord

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext
import de.maxr1998.diskord.Constants

fun Message.args(limit: Int) = content
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
                val content = msg.content
                if (content.startsWith(Constants.LINE_SEPARATED_CONTENT_TAG)) {
                    content.removePrefix(Constants.LINE_SEPARATED_CONTENT_TAG).split("\n")
                } else wrapListIfNotEmpty(content)
            }
    }
    2 -> wrapListIfNotEmpty(args[1])
    else -> null
}