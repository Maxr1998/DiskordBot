package de.maxr1998.diskord.model.database

import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.ReferenceOption

private const val MAX_ID_LENGTH = 20 // ceil(log(2^64))
private const val MAX_COMMAND_LENGTH = 32
private const val MAX_CONTENT_LENGTH = 1024

object Commands : IntIdTable("commands") {
    val guild = varchar("server", MAX_ID_LENGTH).index()
    val command = varchar("command", MAX_COMMAND_LENGTH)

    init {
        uniqueIndex(guild, command)
    }
}

object CommandEntries : LongIdTable("command_entries") {
    val command = reference("command", Commands, ReferenceOption.CASCADE, ReferenceOption.CASCADE).index()
    val content = varchar("content", MAX_CONTENT_LENGTH)

    init {
        uniqueIndex(command, content)
    }
}