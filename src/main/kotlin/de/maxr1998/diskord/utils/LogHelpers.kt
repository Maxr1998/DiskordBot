package de.maxr1998.diskord.utils

import com.jessecorbett.diskord.api.common.User
import mu.KLogger

fun KLogger.logAdd(user: User, command: String, entries: List<String>) {
    val entriesString = entries.joinToString(separator = ",", prefix = "[", postfix = "]")
    debug("${user.username} added $entriesString to $command")
}

fun KLogger.logRemove(user: User, command: String, entries: List<String>) {
    val entriesString = entries.joinToString(separator = ",", prefix = "[", postfix = "]")
    debug("${user.username} removed $entriesString from $command")
}