package de.maxr1998.diskord.util.extension

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext

suspend fun Message.getRepliedMessage(botContext: BotContext): Message? =
    reference?.messageId?.let { id -> with(botContext) { channel }.getMessage(id) }

fun Message.args(limit: Int): List<String> = content
    .splitWhitespaceNonEmpty(if (limit < Int.MAX_VALUE) limit + 1 else Int.MAX_VALUE)
    .drop(1)