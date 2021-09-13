package de.maxr1998.diskord.utils.diskord

import com.jessecorbett.diskord.api.common.Message
import de.maxr1998.diskord.config.Config

fun isOwner(config: Config, message: Message): Boolean {
    return message.author.id == config.ownerId
}

fun isAdmin(config: Config, message: Message): Boolean {
    val authorId = message.author.id
    return !(authorId != config.ownerId && authorId !in config.adminIds)
}

fun isManager(config: Config, message: Message): Boolean {
    val authorId = message.author.id
    return !(authorId != config.ownerId && authorId !in config.adminIds && authorId !in config.managerIds)
}