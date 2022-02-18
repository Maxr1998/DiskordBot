package de.maxr1998.diskord.util.extension

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

fun Message.getUrl() = URLBuilder(protocol = URLProtocol.HTTPS, host = "discord.com")
    .path(listOf("channels", guildId ?: "@me", channelId, id))
    .build()

suspend fun Message.getRepliedMessage(botContext: BotContext): Message? =
    reference?.messageId?.let { id -> with(botContext) { channel }.getMessage(id) }

fun Message.args(limit: Int): List<String> = content
    .splitWhitespaceNonEmpty(if (limit < Int.MAX_VALUE) limit + 1 else Int.MAX_VALUE)
    .drop(1)