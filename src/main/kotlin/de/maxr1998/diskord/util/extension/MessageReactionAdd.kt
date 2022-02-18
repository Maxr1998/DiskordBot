package de.maxr1998.diskord.util.extension

import com.jessecorbett.diskord.api.common.User
import com.jessecorbett.diskord.api.exceptions.DiscordNotFoundException
import com.jessecorbett.diskord.api.gateway.events.MessageReactionAdd
import com.jessecorbett.diskord.bot.BotContext

suspend fun MessageReactionAdd.getUser(context: BotContext): User? = try {
    context.global().getUser(userId)
} catch (e: DiscordNotFoundException) {
    null
}