package de.maxr1998.diskord.util

import com.jessecorbett.diskord.api.common.User
import de.maxr1998.diskord.command.dynamic.CommandEntryEntity
import org.slf4j.Logger
import kotlin.error as throwError

fun Logger.logAdd(user: User, commands: List<List<String>>, entries: List<CommandEntryEntity>) {
    when (commands.size) {
        1 -> {
            val entriesString = entries.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
                transform = CommandEntryEntity::content,
            )
            val commandSet = commands.first()
            val commandsString = commandSet.joinToString(
                separator = ",",
                prefix = "[",
                postfix = "]",
            )
            debug("${user.username} added $entriesString to $commandsString")
        }
        entries.size -> {
            commands.forEachIndexed { index, commandSet ->
                val entry = entries[index].content
                val commandsString = commandSet.joinToString(
                    separator = ",",
                    prefix = "[",
                    postfix = "]",
                )
                debug("${user.username} added $entry to $commandsString")
            }
        }
        else -> throwError("Mismatch in number of command sets and entries")
    }
}

fun Logger.logRemove(user: User, command: String, entries: List<String>) {
    val entriesString = entries.joinToString(separator = ",", prefix = "[", postfix = "]")
    debug("${user.username} removed $entriesString from $command")
}