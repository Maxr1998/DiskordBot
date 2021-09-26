package de.maxr1998.diskord.utils.diskord

import com.jessecorbett.diskord.api.common.Attachment
import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.common.User
import com.jessecorbett.diskord.api.exceptions.DiscordNotFoundException
import com.jessecorbett.diskord.api.gateway.events.MessageReactionAdd
import com.jessecorbett.diskord.bot.BotContext
import io.ktor.http.ContentType
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol

fun Message.getUrl() = URLBuilder(protocol = URLProtocol.HTTPS, host = "discord.com")
    .path(listOf("channels", guildId ?: "@me", channelId, id))
    .build()

suspend fun Message.getRepliedMessage(botContext: BotContext): Message? =
    reference?.messageId?.let { id -> with(botContext) { channel }.getMessage(id) }

val Attachment.parsedContentType: ContentType?
    get() = contentType?.let(ContentType.Companion::parse)

suspend fun MessageReactionAdd.getUser(context: BotContext): User? = try {
    context.global().getUser(userId)
} catch (e: DiscordNotFoundException) {
    null
}