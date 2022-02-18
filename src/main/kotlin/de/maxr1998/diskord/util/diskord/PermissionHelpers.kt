package de.maxr1998.diskord.util.diskord

import com.jessecorbett.diskord.api.common.Message
import com.jessecorbett.diskord.api.common.User
import de.maxr1998.diskord.config.Config

fun isOwner(config: Config, user: User): Boolean {
    return user.id == config.ownerId
}

fun isAdmin(config: Config, user: User): Boolean {
    return isOwner(config, user) || user.id in config.adminIds
}

fun isManager(config: Config, user: User): Boolean {
    return isAdmin(config, user) || user.id in config.managerIds
}

fun isOwner(config: Config, message: Message): Boolean {
    return isOwner(config, message.author)
}

fun isAdmin(config: Config, message: Message): Boolean {
    return isAdmin(config, message.author)
}

fun isManager(config: Config, message: Message): Boolean {
    return isManager(config, message.author)
}