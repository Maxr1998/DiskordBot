package de.maxr1998.diskord.utils

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.bot.BotContext
import de.maxr1998.diskord.config.Config

suspend fun BotContext.checkOwner(config: Config, message: Message): Boolean {
    if (message.author.id != config.ownerId) {
        message.reply("Insufficient permissions")
        return false
    }
    return true
}

suspend fun BotContext.checkAdmin(config: Config, message: Message): Boolean {
    val authorId = message.author.id
    if (authorId != config.ownerId && authorId !in config.adminIds) {
        message.reply("Insufficient permissions")
        return false
    }
    return true
}

suspend fun BotContext.checkManager(config: Config, message: Message): Boolean {
    val authorId = message.author.id
    if (authorId != config.ownerId && authorId !in config.adminIds && authorId !in config.managerIds) {
        message.reply("Insufficient permissions")
        return false
    }
    return true
}